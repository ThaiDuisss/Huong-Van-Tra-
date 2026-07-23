package org.hvtra.deliveryservice.service.ahmove;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.carrier.model.CarrierWebhookAck;
import org.hvtra.deliveryservice.carrier.support.CarrierPersistenceSupport;
import org.hvtra.deliveryservice.carrier.support.CarrierWebhookSupport;
import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.entity.CarrierWebhookEvent;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveOrderDetailResponse;
import org.hvtra.deliveryservice.repository.CarrierShipmentRepository;
import org.hvtra.deliveryservice.repository.CarrierWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AhamoveWebhookService {

    private final AhamoveWebhookAuthenticator authenticator;
    private final CarrierWebhookSupport webhookSupport;
    private final CarrierWebhookEventRepository webhookEventRepository;
    private final CarrierShipmentRepository carrierShipmentRepository;
    private final CarrierPersistenceSupport persistenceSupport;
    private final AhamoveStatusMapper statusMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CarrierWebhookAck process(JsonNode payload, HttpHeaders headers) {
        authenticator.validate(headers, payload);
        String callbackId = webhookSupport.text(payload, "_id", "order_id");
        String orderId = webhookSupport.ahamoveBaseOrderId(callbackId);
        String trackingNumber = webhookSupport.ahamoveTrackingNumber(payload);
        String providerStatus = webhookSupport.text(payload, "status");
        String subStatus = webhookSupport.text(payload, "sub_status");
        JsonNode deliveryPath = webhookSupport.lastDeliveryPath(payload);
        String deliveryStatus = webhookSupport.text(deliveryPath, "status");
        String deduplicationKey = webhookSupport.deduplicationKey(
                orderId,
                callbackId,
                trackingNumber,
                providerStatus,
                subStatus,
                deliveryStatus,
                webhookSupport.text(deliveryPath, "complete_time", "fail_time", "return_time"),
                webhookSupport.text(payload, "accept_time", "pickup_time", "complete_time", "cancel_time")
        );

        try {
            Boolean duplicate = transactionTemplate.execute(status -> {
                if (webhookEventRepository.existsByProviderAndDeduplicationKey(
                        DeliveryProvider.AHAMOVE,
                        deduplicationKey
                )) {
                    return Boolean.TRUE;
                }
                CarrierWebhookEvent event = CarrierWebhookEvent.receive(
                        DeliveryProvider.AHAMOVE,
                        callbackId,
                        deduplicationKey,
                        orderId,
                        trackingNumber,
                        eventType(providerStatus, subStatus, deliveryStatus),
                        providerStatus,
                        payload,
                        objectMapper.valueToTree(webhookSupport.toHeaderMap(headers)),
                        Instant.now()
                );
                webhookEventRepository.saveAndFlush(event);

                Optional<CarrierShipment> carrierOptional = findCarrier(orderId, trackingNumber);
                if (carrierOptional.isEmpty()) {
                    event.markIgnored("Carrier shipment not found", Instant.now());
                    return Boolean.FALSE;
                }

                CarrierShipment carrier = carrierOptional.get();
                Shipment shipment = persistenceSupport.getShipmentForUpdate(carrier.getShipment().getId());
                event.bindShipment(shipment);
                event.markProcessing();

                AhamoveOrderDetailResponse detail = objectMapper.convertValue(payload, AhamoveOrderDetailResponse.class);
                Long totalFee = firstNonNull(
                        webhookSupport.longValue(payload, "total_fee", "total_price", "total_pay"),
                        detail.resolvedTotalFee()
                );
                Long distanceFee = webhookSupport.longValue(payload, "distance_fee", "distance_price");
                Long codAmount = webhookSupport.longValue(deliveryPath, "cod");
                carrier.updateFees(distanceFee, null, null, totalFee);
                carrier.updateCodAmount(codAmount);
                carrier.updateTrackingUrl(webhookSupport.text(payload, "shared_link"));
                carrier.updateProviderMetadata(payload);
                carrier.markSynced(Instant.now());
                shipment.updateActualShippingCost(totalFee);
                shipment.synchronizeCodAmount(codAmount);

                ShipmentStatus target = statusMapper.toShipmentStatus(detail);
                persistenceSupport.applyStatus(
                        shipment,
                        carrier,
                        providerStatus,
                        target,
                        occurredAt(payload, deliveryPath, target),
                        failureCode(providerStatus, subStatus, deliveryStatus),
                        failureReason(payload, deliveryPath),
                        ShipmentStatusSource.CARRIER_WEBHOOK,
                        callbackId,
                        payload
                );
                event.markProcessed(Instant.now());
                return Boolean.FALSE;
            });
            return Boolean.TRUE.equals(duplicate) ? CarrierWebhookAck.duplicate() : CarrierWebhookAck.accepted();
        } catch (DataIntegrityViolationException exception) {
            return CarrierWebhookAck.duplicate();
        }
    }

    private Optional<CarrierShipment> findCarrier(String orderId, String trackingNumber) {
        if (orderId != null && !orderId.isBlank()) {
            Optional<CarrierShipment> byOrder = carrierShipmentRepository
                    .findByProviderAndExternalOrderCode(DeliveryProvider.AHAMOVE, orderId);
            if (byOrder.isPresent()) {
                return byOrder;
            }
        }
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            Optional<CarrierShipment> byTracking = carrierShipmentRepository
                    .findByProviderAndTrackingCode(DeliveryProvider.AHAMOVE, trackingNumber);
            if (byTracking.isPresent()) {
                return byTracking;
            }
            return carrierShipmentRepository
                    .findByProviderAndClientOrderCode(DeliveryProvider.AHAMOVE, trackingNumber);
        }
        return Optional.empty();
    }

    private Instant occurredAt(JsonNode payload, JsonNode deliveryPath, ShipmentStatus target) {
        if (target == ShipmentStatus.DELIVERED) {
            return firstNonNull(
                    webhookSupport.epochSeconds(deliveryPath, "complete_time"),
                    webhookSupport.epochSeconds(payload, "complete_time"),
                    Instant.now()
            );
        }
        if (target == ShipmentStatus.DELIVERY_FAILED) {
            return firstNonNull(
                    webhookSupport.epochSeconds(deliveryPath, "fail_time"),
                    webhookSupport.epochSeconds(payload, "complete_time"),
                    Instant.now()
            );
        }
        if (target == ShipmentStatus.RETURNED) {
            return firstNonNull(
                    webhookSupport.epochSeconds(deliveryPath, "return_time"),
                    Instant.now()
            );
        }
        if (target == ShipmentStatus.CANCELLED) {
            return firstNonNull(webhookSupport.epochSeconds(payload, "cancel_time"), Instant.now());
        }
        if (target == ShipmentStatus.IN_TRANSIT || target == ShipmentStatus.PICKED_UP) {
            return firstNonNull(webhookSupport.epochSeconds(payload, "pickup_time"), Instant.now());
        }
        return firstNonNull(
                webhookSupport.epochSeconds(payload, "accept_time", "order_time", "create_time"),
                Instant.now()
        );
    }

    //Hàm eventType() tạo một chuỗi mô tả loại sự kiện webhook bằng cách ghép ba trạng thái:
    private String eventType(String status, String subStatus, String deliveryStatus) {
        return String.join(":",
                status == null ? "UNKNOWN" : status,
                subStatus == null ? "" : subStatus,
                deliveryStatus == null ? "" : deliveryStatus
        );
    }

    private String failureCode(String status, String subStatus, String deliveryStatus) {
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return "CANCELLED";
        }
        if ("FAILED".equalsIgnoreCase(deliveryStatus)) {
            return "DELIVERY_FAILED";
        }
        return subStatus;
    }

    private String failureReason(JsonNode payload, JsonNode deliveryPath) {
        String pathReason = webhookSupport.text(deliveryPath, "fail_comment", "complete_comment");
        return pathReason != null
                ? pathReason
                : webhookSupport.text(payload, "cancel_comment");
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
