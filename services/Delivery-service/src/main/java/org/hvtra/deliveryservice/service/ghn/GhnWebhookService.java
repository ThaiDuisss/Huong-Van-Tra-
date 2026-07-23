package org.hvtra.deliveryservice.service.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.config.GhnProperties;
import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.entity.CarrierWebhookEvent;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentStatusHistory;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.model.response.ghn.GhnWebhookAck;
import org.hvtra.deliveryservice.repository.CarrierShipmentRepository;
import org.hvtra.deliveryservice.repository.CarrierWebhookEventRepository;
import org.hvtra.deliveryservice.repository.ShipmentRepository;
import org.hvtra.deliveryservice.repository.ShipmentStatusHistoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GhnWebhookService {
    private final GhnProperties properties;
    private final CarrierWebhookEventRepository webhookEventRepository;
    private final CarrierShipmentRepository carrierShipmentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final GhnStatusMapper statusMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public GhnWebhookAck process(JsonNode payload, HttpHeaders headers, String suppliedSecret) {
        validateSecret(suppliedSecret);
        String orderCode = text(payload, "OrderCode", "order_code");
        String clientOrderCode = text(payload, "ClientOrderCode", "client_order_code");
        String providerStatus = text(payload, "Status", "status");
        String eventType = text(payload, "Type", "type");
        String eventTime = text(payload, "Time", "time");
        String reasonCode = text(payload, "ReasonCode", "reason_code");
        String reason = text(payload, "Reason", "reason");
        String deduplicationKey = sha256(String.join("|",
                nullToEmpty(orderCode),
                nullToEmpty(clientOrderCode),
                nullToEmpty(providerStatus),
                nullToEmpty(eventType),
                nullToEmpty(eventTime),
                nullToEmpty(reasonCode),
                nullToEmpty(text(payload, "TotalFee", "total_fee")),
                nullToEmpty(text(payload, "CODAmount", "cod_amount"))
        ));
        JsonNode headerJson = objectMapper.valueToTree(toHeaderMap(headers));
        Instant occurredAt = parseInstant(eventTime);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (webhookEventRepository.existsByProviderAndDeduplicationKey(DeliveryProvider.GHN, deduplicationKey)) {
                    return;
                }
                CarrierWebhookEvent event = CarrierWebhookEvent.receive(
                        DeliveryProvider.GHN,
                        null,
                        deduplicationKey,
                        orderCode,
                        orderCode,
                        eventType,
                        providerStatus,
                        payload,
                        headerJson,
                        Instant.now()
                );
                webhookEventRepository.saveAndFlush(event);

                Optional<CarrierShipment> carrierOptional = findCarrier(orderCode, clientOrderCode);
                if (carrierOptional.isEmpty()) {
                    event.markIgnored("Carrier shipment not found", Instant.now());
                    return;
                }

                CarrierShipment carrier = carrierOptional.get();
                Shipment shipment = shipmentRepository.findByIdForUpdate(carrier.getShipment().getId())
                        .orElseThrow(() -> new IllegalStateException("Shipment linked to GHN order was not found"));
                event.bindShipment(shipment);
                event.markProcessing();
                carrier.updateProviderStatus(providerStatus);

                Long totalFee = longValue(payload, "TotalFee", "total_fee");
                Long shippingFee = nestedLongValue(payload, "Fee", "MainService", "main_service");
                Long insuranceFee = nestedLongValue(payload, "Fee", "Insurance", "insurance");
                Long codFee = nestedLongValue(payload, "Fee", "CODFee", "cod_fee");
                Long codAmount = longValue(payload, "CODAmount", "cod_amount");
                carrier.updateFees(shippingFee, insuranceFee, codFee, totalFee);
                carrier.updateCodAmount(codAmount);
                shipment.updateActualShippingCost(totalFee);
                shipment.synchronizeCodAmount(codAmount);

                ShipmentStatus target = statusMapper.toShipmentStatus(providerStatus);
                if (statusMapper.canApply(shipment.getStatus(), target)) {
                    ShipmentStatus previous = shipment.getStatus();
                    boolean changed = shipment.synchronizeCarrierStatus(
                            target,
                            occurredAt,
                            reasonCode,
                            reason
                    );
                    if (target == ShipmentStatus.DELIVERED) {
                        carrier.markCodCollected();
                    }
                    if (changed) {
                        historyRepository.save(ShipmentStatusHistory.record(
                                shipment,
                                previous,
                                shipment.getStatus(),
                                ShipmentStatusSource.CARRIER_WEBHOOK,
                                orderCode,
                                reasonCode,
                                reason,
                                "ghn-webhook",
                                occurredAt,
                                payload
                        ));
                    }
                }

                String codTransferDate = text(payload, "CODTransferDate", "cod_transfer_date");
                if (codTransferDate != null && carrier.isCodCollected() && !carrier.isCodRemitted()) {
                    carrier.markCodRemitted(parseInstant(codTransferDate));
                }
                event.markProcessed(Instant.now());
            });
        } catch (DataIntegrityViolationException ignored) {
            return GhnWebhookAck.success();
        }
        return GhnWebhookAck.success();
    }
    private Optional<CarrierShipment> findCarrier(String orderCode, String clientOrderCode) {
        if (orderCode != null) {
            Optional<CarrierShipment> byExternal = carrierShipmentRepository
                    .findByProviderAndExternalOrderCode(DeliveryProvider.GHN, orderCode);
            if (byExternal.isPresent()) {
                return byExternal;
            }
            Optional<CarrierShipment> byTracking = carrierShipmentRepository
                    .findByProviderAndTrackingCode(DeliveryProvider.GHN, orderCode);
            if (byTracking.isPresent()) {
                return byTracking;
            }
        }
        if (clientOrderCode != null) {
            return carrierShipmentRepository.findByProviderAndClientOrderCode(DeliveryProvider.GHN, clientOrderCode);
        }
        return Optional.empty();
    }

    private void validateSecret(String suppliedSecret) {
        String configuredSecret = properties.webhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return;
        }
        byte[] expected = configuredSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = nullToEmpty(suppliedSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Invalid GHN webhook secret");
        }
    }

    private Map<String, Object> toHeaderMap(HttpHeaders headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach(result::put);
        return result;
    }

    private String text(JsonNode payload, String... names) {
        for (String name : names) {
            JsonNode node = payload.get(name);
            if (node != null && !node.isNull()) {
                String value = node.asText();
                if (!value.isBlank() && !"null".equalsIgnoreCase(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private Long longValue(JsonNode payload, String... names) {
        String value = text(payload, names);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long nestedLongValue(JsonNode payload, String parentName, String... childNames) {
        JsonNode parent = payload.get(parentName);
        if (parent == null || parent.isNull()) {
            parent = payload.get(parentName.toLowerCase());
        }
        return parent == null || parent.isNull() ? null : longValue(parent, childNames);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate webhook deduplication key", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }


}
