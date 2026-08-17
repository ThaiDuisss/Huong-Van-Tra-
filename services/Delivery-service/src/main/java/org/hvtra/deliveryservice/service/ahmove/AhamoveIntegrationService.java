package org.hvtra.deliveryservice.service.ahmove;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.carrier.support.CarrierPersistenceSupport;
import org.hvtra.deliveryservice.client.AhmoveClient;
import org.hvtra.deliveryservice.config.AhamoveProperties;
import org.hvtra.deliveryservice.exception.AhamoveIntegrationException;
import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveCancelOrderRequest;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveCreateOrderRequest;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveEstimateOrderRequest;
import org.hvtra.deliveryservice.model.response.ahmove.*;
import org.hvtra.deliveryservice.repository.CarrierShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AhamoveIntegrationService {

    private final AhmoveClient client;
    private final AhamoveProperties properties;
    private final CarrierShipmentRepository carrierShipmentRepository;
    private final CarrierPersistenceSupport persistenceSupport;
    private final AhamoveRequestFactory requestFactory;
    private final AhamoveStatusMapper statusMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;


    public List<CarrierQuoteResult> quote(UUID shipmentId, CarrierQuoteCommand command) {
        properties.validate();
        AhamoveEstimateOrderRequest request = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = persistenceSupport.getShipment(shipmentId);
            return requestFactory.estimateRequest(shipment, command);
        }));
        try {
            List<AhamoveEstimateResponse> estimates = client.estimate(request);
            if (estimates == null || estimates.isEmpty()) {
                throw new AhamoveIntegrationException("Ahamove estimate returned no service");
            }
            return estimates.stream().map(estimate -> toQuoteResult(shipmentId, estimate)).toList();
        } catch (FeignException exception) {
            throw integrationException("Estimate Ahamove order fee failed", exception);
        }
    }

    public CarrierShipmentResult create(UUID shipmentId, CarrierCreateCommand command) {
        properties.validate();
        CreatePreparation preparation = prepareCreate(shipmentId, command);
        if (preparation.existingExternalOrderCode() != null) {
            return get(shipmentId);
        }

        AhamoveOrderDetailResponse recovered = recoverByTrackingNumber(preparation.clientOrderCode(), false);
        if (recovered != null && recovered.id() != null) {
            return finalizeRecovered(preparation, recovered);
        }

        try {
            AhamoveCreateOrderResponse response = client.createOrder(preparation.request());
            if (response == null || (response.orderId() == null && response.order() == null)) {
                throw new AhamoveIntegrationException("Create Ahamove order returned empty data");
            }
            return finalizeCreated(preparation, response);
        } catch (FeignException | AhamoveIntegrationException exception) {
            AhamoveOrderDetailResponse afterFailure = recoverByTrackingNumber(preparation.clientOrderCode(), true);
            if (afterFailure != null && afterFailure.id() != null) {
                return finalizeRecovered(preparation, afterFailure);
            }
            if (exception instanceof AhamoveIntegrationException integrationException) {
                throw integrationException;
            }
            throw integrationException("Create Ahamove order failed", (FeignException) exception);
        }
    }

    public CarrierShipmentResult refresh(UUID shipmentId) {
        properties.validate();
        String externalOrderCode = requireExternalOrderCode(shipmentId);

        try {
            AhamoveOrderDetailResponse detail = client.getOrderDetail(externalOrderCode);
            String trackingUrl = fetchTrackingUrl(externalOrderCode);
            applyDetail(shipmentId, detail, trackingUrl, ShipmentStatusSource.RECONCILIATION_JOB);
            return get(shipmentId);
        } catch (FeignException exception) {
            throw integrationException("Refresh Ahamove order failed", exception);
        }
    }

    public CarrierShipmentResult cancel(UUID shipmentId, CarrierCancelCommand command) {
        properties.validate();
        String externalOrderCode = requireExternalOrderCode(shipmentId);
        String reason = command == null || command.reason() == null || command.reason().isBlank()
                ? "Khách hàng muốn hủy đơn"
                : command.reason().trim();
        try {
            client.cancelOrder(externalOrderCode, new AhamoveCancelOrderRequest(reason));
            try {
                AhamoveOrderDetailResponse detail = client.getOrderDetail(externalOrderCode);
                applyDetail(shipmentId, detail, fetchTrackingUrl(externalOrderCode), ShipmentStatusSource.CARRIER_API);
            } catch (FeignException ignored) {
                applyCancelledLocally(shipmentId, externalOrderCode, reason);
            }
            return get(shipmentId);
        } catch (FeignException exception) {
            throw integrationException("Cancel Ahamove order failed", exception);
        }
    }
    private String requireExternalOrderCode(UUID shipmentId) {
        CarrierShipment carrier =
                persistenceSupport.getCarrier(
                        shipmentId,
                        DeliveryProvider.AHAMOVE
                );

        String externalOrderCode = carrier.getExternalOrderCode();

        if (externalOrderCode == null || externalOrderCode.isBlank()) {
            throw new IllegalStateException(
                    "Ahamove order has not been created yet"
            );
        }

        return externalOrderCode;
    }

    @Transactional(readOnly = true)
    public CarrierShipmentResult get(UUID shipmentId) {
            Shipment shipment = persistenceSupport.getShipment(shipmentId);
            CarrierShipment carrier = persistenceSupport.getCarrier(shipmentId, DeliveryProvider.AHAMOVE);
            return persistenceSupport.toResult(shipment, carrier, carrier.getProviderMetadata());
    }

    private CreatePreparation prepareCreate(UUID shipmentId, CarrierCreateCommand command) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = persistenceSupport.getShipmentForUpdate(shipmentId);
            AhamoveCreateOrderRequest request = requestFactory.createRequest(shipment, command);
            Optional<CarrierShipment> existing = carrierShipmentRepository
                    .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.AHAMOVE);
            if (existing.isPresent()) {
                CarrierShipment carrier = existing.get();
                carrier.updateCreateRequest(objectMapper.valueToTree(request));
                return new CreatePreparation(
                        shipmentId,
                        carrier.getClientOrderCode(),
                        request,
                        carrier.getExternalOrderCode()
                );
            }
            CarrierShipment carrier = CarrierShipment.create(
                    shipment,
                    DeliveryProvider.AHAMOVE,
                    shipment.getShipmentCode(),
                    shipment.getCodAmount(),
                    objectMapper.valueToTree(request)
            );
            carrierShipmentRepository.saveAndFlush(carrier);
            return new CreatePreparation(shipmentId, shipment.getShipmentCode(), request, null);
        }));
    }

    private CarrierShipmentResult finalizeCreated(
            CreatePreparation preparation,
            AhamoveCreateOrderResponse response
    ) {
        AhamoveOrderDetailResponse detail = response.order();
        String orderId = response.orderId();
        if (orderId == null && detail != null) {
            orderId = detail.id();
        }
        if (orderId != null && (detail == null || detail.id() == null)) {
            try {
                detail = client.getOrderDetail(orderId);
            } catch (FeignException ignored) {
                String fallbackStatus = detail == null ? response.status() : detail.status();
                String fallbackService = detail == null || detail.serviceId() == null
                        ? preparation.request().serviceId()
                        : detail.serviceId();
                detail = minimalDetail(orderId, fallbackStatus, fallbackService);
            }
        }
        if (detail == null || detail.id() == null || orderId == null) {
            throw new AhamoveIntegrationException("Ahamove create response does not contain order id");
        }
        String trackingUrl = response.sharedLink() == null ? fetchTrackingUrl(orderId) : response.sharedLink();
        return persistCreated(preparation, detail, trackingUrl, objectMapper.valueToTree(response), ShipmentStatusSource.CARRIER_API);
    }

    private CarrierShipmentResult finalizeRecovered(
            CreatePreparation preparation,
            AhamoveOrderDetailResponse detail
    ) {
        String trackingUrl = detail.sharedLink() == null ? fetchTrackingUrl(detail.id()) : detail.sharedLink();
        return persistCreated(
                preparation,
                detail,
                trackingUrl,
                objectMapper.valueToTree(detail),
                ShipmentStatusSource.RECONCILIATION_JOB
        );
    }

    private CarrierShipmentResult persistCreated(
            CreatePreparation preparation,
            AhamoveOrderDetailResponse detail,
            String trackingUrl,
            JsonNode rawResponse,
            ShipmentStatusSource source
    ) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = persistenceSupport.getShipmentForUpdate(preparation.shipmentId());
            CarrierShipment carrier = persistenceSupport.getCarrier(preparation.shipmentId(), DeliveryProvider.AHAMOVE);
            Long totalFee = detail.resolvedTotalFee();
            carrier.recordCreatedShipment(
                    null,
                    detail.serviceId() == null ? preparation.request().serviceId() : detail.serviceId(),
                    detail.serviceId() == null ? preparation.request().serviceId() : detail.serviceId(),
                    detail.id(),
                    preparation.clientOrderCode(),
                    detail.status(),
                    detail.distanceFee(),
                    null,
                    null,
                    totalFee,
                    null,
                    expectedDeliveryAt(detail),
                    rawResponse
            );
            carrier.updateTrackingUrl(trackingUrl);
            carrier.updateProviderMetadata(objectMapper.valueToTree(detail));
            carrier.markSynced(Instant.now());
            synchronizeCod(shipment, carrier, detail);
            shipment.attachCarrierReference(
                    detail.serviceId() == null ? preparation.request().serviceId() : detail.serviceId(),
                    preparation.clientOrderCode(),
                    detail.id(),
                    totalFee
            );
            shipment.updateActualShippingCost(totalFee);
            persistenceSupport.applyStatus(
                    shipment,
                    carrier,
                    detail.status(),
                    statusMapper.toShipmentStatus(detail),
                    occurredAt(detail),
                    failureCode(detail),
                    failureReason(detail),
                    source,
                    detail.id(),
                    objectMapper.valueToTree(detail)
            );
            return persistenceSupport.toResult(shipment, carrier, objectMapper.valueToTree(detail));
        }));
    }

    private void applyDetail(
            UUID shipmentId,
            AhamoveOrderDetailResponse detail,
            String trackingUrl,
            ShipmentStatusSource source
    ) {
        if (detail == null || detail.id() == null) {
            throw new AhamoveIntegrationException("Ahamove order detail is empty");
        }
        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = persistenceSupport.getShipmentForUpdate(shipmentId);
            CarrierShipment carrier = persistenceSupport.getCarrier(shipmentId, DeliveryProvider.AHAMOVE);
            Long totalFee = detail.resolvedTotalFee();
            carrier.updateFees(detail.distanceFee(), null, null, totalFee);
            carrier.updateTrackingUrl(trackingUrl);
            carrier.updateProviderMetadata(objectMapper.valueToTree(detail));
            carrier.markSynced(Instant.now());
            shipment.updateActualShippingCost(totalFee);
            synchronizeCod(shipment, carrier, detail);
            persistenceSupport.applyStatus(
                    shipment,
                    carrier,
                    detail.status(),
                    statusMapper.toShipmentStatus(detail),
                    occurredAt(detail),
                    failureCode(detail),
                    failureReason(detail),
                    source,
                    detail.id(),
                    objectMapper.valueToTree(detail)
            );
        });
    }

    private void applyCancelledLocally(UUID shipmentId, String externalOrderCode, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = persistenceSupport.getShipmentForUpdate(shipmentId);
            CarrierShipment carrier = persistenceSupport.getCarrier(shipmentId, DeliveryProvider.AHAMOVE);
            persistenceSupport.applyStatus(
                    shipment,
                    carrier,
                    "CANCELLED",
                    ShipmentStatus.CANCELLED,
                    Instant.now(),
                    "USER_CANCELLED",
                    reason,
                    ShipmentStatusSource.CARRIER_API,
                    externalOrderCode,
                    objectMapper.createObjectNode().put("reason", reason)
            );
        });
    }

    private CarrierQuoteResult toQuoteResult(UUID shipmentId, AhamoveEstimateResponse estimate) {
        AhamoveEstimateData data = estimate.data();
        if (data == null) {
            throw new AhamoveIntegrationException("Ahamove estimate data is empty for service " + estimate.serviceId());
        }
        return new CarrierQuoteResult(
                shipmentId,
                DeliveryProvider.AHAMOVE,
                estimate.serviceId(),
                data.resolvedTotalFee(),
                data.distanceFee(),
                null,
                null,
                data.distance(),
                data.duration(),
                objectMapper.valueToTree(estimate)
        );
    }

    private AhamoveOrderDetailResponse recoverByTrackingNumber(String trackingNumber, boolean tolerateLookupFailure) {
        try {
            List<AhamoveOrderDetailResponse> orders = client.getOrdersByTrackingNumber(trackingNumber);
            if (orders == null || orders.isEmpty()) {
                return null;
            }
            AhamoveOrderDetailResponse selected = orders.stream()
                    .filter(order -> order.id() != null)
                    .filter(order -> !"CANCELLED".equalsIgnoreCase(order.status()))
                    .max(Comparator.comparing(
                            order -> order.createTime() == null ? 0D : order.createTime()
                    ))
                    .orElseGet(() -> orders.stream()
                            .filter(order -> order.id() != null)
                            .max(Comparator.comparing(order -> order.createTime() == null ? 0D : order.createTime()))
                            .orElse(null));
            if (selected == null) {
                return null;
            }
            try {
                return client.getOrderDetail(selected.id());
            } catch (FeignException ignored) {
                return selected;
            }
        } catch (FeignException exception) {
            if (exception.status() == 404 || tolerateLookupFailure) {
                return null;
            }
            throw integrationException("Lookup Ahamove order by tracking number failed", exception);
        }
    }

    private String fetchTrackingUrl(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        try {
            AhamoveTrackingLinkResponse response = client.getTrackingLink(orderId);
            return response == null ? null : response.sharedLink();
        } catch (FeignException ignored) {
            return null;
        }
    }

    private void synchronizeCod(Shipment shipment, CarrierShipment carrier, AhamoveOrderDetailResponse detail) {
        Long cod = deliveryPath(detail).map(AhamovePathResponse::cod).orElse(null);
        carrier.updateCodAmount(cod);
        shipment.synchronizeCodAmount(cod);
    }

    private Optional<AhamovePathResponse> deliveryPath(AhamoveOrderDetailResponse detail) {
        if (detail.path() == null || detail.path().size() < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(detail.path().getLast());
    }

    private String failureCode(AhamoveOrderDetailResponse detail) {
        if ("CANCELLED".equalsIgnoreCase(detail.status())) {
            return Boolean.TRUE.equals(detail.cancelByUser()) ? "USER_CANCELLED" : "CARRIER_CANCELLED";
        }
        return deliveryPath(detail)
                .filter(path -> "FAILED".equalsIgnoreCase(path.status()))
                .map(path -> "DELIVERY_FAILED")
                .orElse(detail.subStatus());
    }

    private String failureReason(AhamoveOrderDetailResponse detail) {
        if ("CANCELLED".equalsIgnoreCase(detail.status())) {
            return detail.cancelComment();
        }
        return deliveryPath(detail).map(AhamovePathResponse::failComment).orElse(null);
    }

    private Instant occurredAt(AhamoveOrderDetailResponse detail) {
        if ("CANCELLED".equalsIgnoreCase(detail.status())) {
            return firstNonNull(epoch(detail.cancelTime()), Instant.now());
        }
        Optional<AhamovePathResponse> delivery = deliveryPath(detail);
        if (delivery.isPresent()) {
            AhamovePathResponse path = delivery.get();
            if ("COMPLETED".equalsIgnoreCase(path.status())) {
                return firstNonNull(epoch(path.completeTime()), epoch(detail.completeTime()), Instant.now());
            }
            if ("FAILED".equalsIgnoreCase(path.status())) {
                return firstNonNull(epoch(path.failTime()), epoch(detail.completeTime()), Instant.now());
            }
        }
        if ("IN PROCESS".equalsIgnoreCase(detail.status())) {
            return firstNonNull(epoch(detail.pickupTime()), Instant.now());
        }
        return firstNonNull(
                epoch(detail.acceptTime()),
                epoch(detail.orderTime()),
                epoch(detail.createTime()),
                Instant.now()
        );
    }
    private Instant expectedDeliveryAt(AhamoveOrderDetailResponse detail) {
        Instant base = epoch(detail.orderTime());
        if (base == null || detail.duration() == null || detail.duration() < 0) {
            return null;
        }
        return base.plusSeconds(detail.duration());
    }

    private Instant epoch(Double value) {
        if (value == null || value <= 0) {
            return null;
        }
        long seconds = value.longValue();
        long nanos = Math.round((value - seconds) * 1_000_000_000D);
        return Instant.ofEpochSecond(seconds, Math.max(0, Math.min(nanos, 999_999_999L)));
    }

    private Instant firstNonNull(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return Instant.now();
    }

    private AhamoveOrderDetailResponse minimalDetail(String orderId, String status, String serviceId) {
        return new AhamoveOrderDetailResponse(
                orderId,
                status,
                null,
                serviceId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private AhamoveIntegrationException integrationException(String operation, FeignException exception) {
        String body = exception.contentUTF8();
        String detail = body == null || body.isBlank() ? exception.getMessage() : body;
        return new AhamoveIntegrationException(operation + " (HTTP " + exception.status() + "): " + detail, exception);
    }

    private record CreatePreparation(
            UUID shipmentId,
            String clientOrderCode,
            AhamoveCreateOrderRequest request,
            String existingExternalOrderCode
    ) {
    }
}
