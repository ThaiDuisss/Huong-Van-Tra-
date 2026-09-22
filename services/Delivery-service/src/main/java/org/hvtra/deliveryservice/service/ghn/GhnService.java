package org.hvtra.deliveryservice.service.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.client.GhnClient;
import org.hvtra.deliveryservice.config.GhnProperties;
import org.hvtra.deliveryservice.exception.GhnIntegrationException;
import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentStatusHistory;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.model.request.ghn.*;
import org.hvtra.deliveryservice.model.response.ghn.*;
import org.hvtra.deliveryservice.repository.CarrierShipmentRepository;
import org.hvtra.deliveryservice.repository.ShipmentRepository;
import org.hvtra.deliveryservice.repository.ShipmentStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GhnService {
    private final GhnClient ghnClient;
    private final GhnProperties properties;
    private final ShipmentRepository shipmentRepository;
    private final CarrierShipmentRepository carrierShipmentRepository;
    private final ShipmentStatusHistoryRepository statusHistoryRepository;
    private final GhnRequestFactory requestFactory;
    private final GhnStatusMapper statusMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;


    public List<GhnServiceResponse> getAvailableServices(UUID shipmentId) {
        properties.validate();
        GhnAvailableServicesRequest request = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipment(shipmentId);
            return new GhnAvailableServicesRequest(
                    properties.shopId(),
                    parseDistrict(shipment.getPickupDistrictCode(), "pickupDistrictCode"),
                    parseDistrict(shipment.getDeliveryDistrictCode(), "deliveryDistrictCode")
            );
        }));
        return ghnClient.getAvailableServices(request)
                .requireData("Get GHN available services");
    }

    public GhnQuoteResponse calculateFee(UUID shipmentId, GhnQuoteCommand command) {
        properties.validate();
        GhnFeeRequest request = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipment(shipmentId);
            return requestFactory.feeRequest(shipment, command);
        }));
        GhnFeeResponse fee = ghnClient.calculateFee( properties.shopId(), request)
                .requireData("Calculate GHN fee");
        return new GhnQuoteResponse(
                shipmentId,
                command.serviceId(),
                command.serviceTypeId(),
                fee.total(),
                fee.serviceFee(),
                fee.insuranceFee(),
                fee.codFee(),
                fee
        );
    }

    public GhnShipmentResponse createShipment(UUID shipmentId, GhnCreateShipmentCommand command) {
        properties.validate();
        CreatePreparation preparation = prepareCreate(shipmentId, command);
        if (preparation.existingOrderCode() != null) {
            return getShipmentResponse(shipmentId);
        }

        GhnOrderDetailResponse recovered = recoverByClientCode(preparation.clientOrderCode());
        if (recovered != null && recovered.orderCode() != null) {
            return finalizeRecovered(preparation, recovered);
        }

        try {
            GhnCreateOrderResponse response = ghnClient.createOrder(
                            properties.shopId(),
                            preparation.request()
                    )
                    .requireData("Create GHN order");
            return finalizeCreated(preparation, response);
        } catch (FeignException | GhnIntegrationException exception) {
            GhnOrderDetailResponse afterFailure = recoverByClientCode(preparation.clientOrderCode());
            if (afterFailure != null && afterFailure.orderCode() != null) {
                return finalizeRecovered(preparation, afterFailure);
            }
            throw new GhnIntegrationException("Create GHN order failed");
        }
    }
    public GhnShipmentResponse refreshShipment(UUID shipmentId) {
        properties.validate();
        String externalOrderCode = getExternalOrderCode(shipmentId, DeliveryProvider.GHN);

        GhnOrderDetailResponse detail = requireOrderDetail(
                ghnClient.getOrderDetail(
                        new GhnOrderDetailRequest(externalOrderCode)
                ),
                "Get GHN order detail"
        );
        applyDetail(shipmentId, detail, ShipmentStatusSource.RECONCILIATION_JOB, objectMapper.valueToTree(detail));
        return getShipmentResponse(shipmentId);
    }

    public GhnShipmentResponse cancelShipment(UUID shipmentId, String reason) {
        properties.validate();

        String externalOrderCode = getExternalOrderCode(shipmentId, DeliveryProvider.GHN);

        List<GhnCancelOrderResponse> results = ghnClient.cancelOrder(
                        properties.shopId(),
                        new GhnCancelOrderRequest(List.of(externalOrderCode))
                )
                .requireData("Cancel GHN order");
        GhnCancelOrderResponse result = results.stream()
                .filter(item -> externalOrderCode.equals(item.orderCode()))
                .findFirst()
                .orElseThrow(() -> new GhnIntegrationException("GHN did not return cancellation result"));
        if (!result.result()) {
            throw new GhnIntegrationException("GHN cancellation failed: " + result.message());
        }

        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = getShipmentForUpdate(shipmentId);
            CarrierShipment carrier1 = carrierShipmentRepository
                    .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.GHN)
                    .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
            ShipmentStatus previous = shipment.getStatus();
            carrier1.updateProviderStatus("cancel");
            boolean changed = shipment.synchronizeCarrierStatus(
                    ShipmentStatus.CANCELLED,
                    Instant.now(),
                    null,
                    reason == null || reason.isBlank() ? "Cancelled on GHN" : reason
            );
            if (changed) {
                saveHistory(shipment, previous, ShipmentStatusSource.SYSTEM, externalOrderCode, null, reason, null);
            }
        });
        return getShipmentResponse(shipmentId);
    }

    public String getExternalOrderCode(UUID shipmentId, DeliveryProvider provider) {
        CarrierShipment carrier = carrierShipmentRepository
                .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.GHN)
                .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
        if (carrier.getExternalOrderCode() == null || carrier.getExternalOrderCode().isBlank()) {
            throw new IllegalStateException("GHN order has not been created yet");
        }
        String externalOrderCode = carrier.getExternalOrderCode();

        if (externalOrderCode.isBlank()) {
            throw new IllegalStateException("GHN order has not been created yet");
        }
        return  externalOrderCode;
    }

    public GhnShipmentResponse getShipmentResponse(UUID shipmentId) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipment(shipmentId);
            CarrierShipment carrier = carrierShipmentRepository
                    .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.GHN)
                    .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
            return mapResponse(shipment, carrier);
        }));
    }

    private CreatePreparation prepareCreate(UUID shipmentId, GhnCreateShipmentCommand command) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipmentForUpdate(shipmentId);
            String clientOrderCode = shipment.getShipmentCode();
            GhnCreateOrderRequest request = requestFactory.createRequest(shipment, clientOrderCode, command);
            CarrierShipment carrier = carrierShipmentRepository
                    .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.GHN)
                    .orElseGet(() -> carrierShipmentRepository.save(CarrierShipment.create(
                            shipment,
                            DeliveryProvider.GHN,
                            clientOrderCode,
                            shipment.getCodAmount(),
                            objectMapper.valueToTree(request)
                    )));
            carrier.updateCreateRequest(objectMapper.valueToTree(request));
            return new CreatePreparation(
                    shipmentId,
                    shipment.getShipmentCode(),
                    clientOrderCode,
                    request,
                    carrier.getExternalOrderCode()
            );
        }));
    }

    private GhnShipmentResponse finalizeCreated(CreatePreparation preparation, GhnCreateOrderResponse response) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipmentForUpdate(preparation.shipmentId());
            CarrierShipment carrier = carrierShipmentRepository
                    .findByShipmentIdAndProvider(preparation.shipmentId(), DeliveryProvider.GHN)
                    .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
            GhnCreateFeeResponse fee = response.fee();
            Long totalFee = response.totalFee();
            Long serviceFee = fee == null ? null : fee.mainService();
            Long insuranceFee = fee == null ? null : fee.insurance();
            Long codFee = fee == null ? null : fee.codFee();
            ShipmentStatus previous = shipment.getStatus();
            carrier.recordCreatedShipment(
                    String.valueOf(properties.shopId()),
                    serviceCode(preparation.request().serviceId(), preparation.request().serviceTypeId()),
                    serviceName(preparation.request().serviceId(), preparation.request().serviceTypeId()),
                    response.orderCode(),
                    response.orderCode(),
                    "ready_to_pick",
                    serviceFee,
                    insuranceFee,
                    codFee,
                    totalFee,
                    null,
                    response.expectedDeliveryTime(),
                    objectMapper.valueToTree(response)
            );
            shipment.attachCarrierReference(
                    serviceCode(preparation.request().serviceId(), preparation.request().serviceTypeId()),
                    response.orderCode(),
                    response.orderCode(),
                    totalFee
            );
            boolean changed = shipment.synchronizeCarrierStatus(
                    ShipmentStatus.READY_FOR_PICKUP,
                    Instant.now(),
                    null,
                    null
            );
            if (changed) {
                saveHistory(shipment, previous, ShipmentStatusSource.SYSTEM, response.orderCode(), null, null, objectMapper.valueToTree(response));
            }
            return mapResponse(shipment, carrier);
        }));
    }

    private GhnShipmentResponse finalizeRecovered(CreatePreparation preparation, GhnOrderDetailResponse detail) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Shipment shipment = getShipmentForUpdate(preparation.shipmentId());
            CarrierShipment carrier = carrierShipmentRepository
                    .findByShipmentIdAndProvider(preparation.shipmentId(), DeliveryProvider.GHN)
                    .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
            carrier.recordCreatedShipment(
                    String.valueOf(properties.shopId()),
                    serviceCode(detail.serviceId(), detail.serviceTypeId()),
                    serviceName(detail.serviceId(), detail.serviceTypeId()),
                    detail.orderCode(),
                    detail.orderCode(),
                    detail.status(),
                    detail.totalFee(),
                    null,
                    null,
                    detail.totalFee(),
                    null,
                    parseNullableInstant(detail.leadtime()),
                    objectMapper.valueToTree(detail)
            );
            shipment.attachCarrierReference(
                    serviceCode(detail.serviceId(), detail.serviceTypeId()),
                    detail.orderCode(),
                    detail.orderCode(),
                    detail.totalFee()
            );
            synchronizeCod(shipment, carrier, detail);
            applyStatus(shipment, carrier, detail.status(), detail.reasonCode(), detail.reason(),
                    detailOccurredAt(detail),
                    ShipmentStatusSource.RECONCILIATION_JOB,
                    detail.orderCode(),
                    objectMapper.valueToTree(detail));
            return mapResponse(shipment, carrier);
        }));
    }

    private void applyDetail(UUID shipmentId, GhnOrderDetailResponse detail, ShipmentStatusSource source, JsonNode metadata) {
        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = getShipmentForUpdate(shipmentId);
            CarrierShipment carrier = carrierShipmentRepository
                    .findByShipmentIdAndProvider(shipmentId, DeliveryProvider.GHN)
                    .orElseThrow(() -> new ResourceNotFoundException("GHN carrier shipment not found"));
            shipment.updateActualShippingCost(detail.totalFee());
            carrier.updateFees(null, null, null, detail.totalFee());
            synchronizeCod(shipment, carrier, detail);
            applyStatus(
                    shipment,
                    carrier,
                    detail.status(),
                    detail.reasonCode(),
                    detail.reason(),
                    detailOccurredAt(detail),
                    source,
                    detail.orderCode(),
                    metadata
            );
        });
    }
    private void applyStatus(
            Shipment shipment,
            CarrierShipment carrier,
            String providerStatus,
            String reasonCode,
            String reason,
            Instant occurredAt,
            ShipmentStatusSource source,
            String sourceReference,
            JsonNode metadata
    ) {
        ShipmentStatus target =
                statusMapper.toShipmentStatus(providerStatus);

        if (!statusMapper.canApply(shipment.getStatus(), target)) {
            return;
        }

        ShipmentStatus previous = shipment.getStatus();

        boolean changed = shipment.synchronizeCarrierStatus(
                target,
                occurredAt,
                reasonCode,
                reason
        );

        if (!changed) {
            return;
        }

        carrier.updateProviderStatus(providerStatus);

        if (target == ShipmentStatus.DELIVERED
                && carrier.getCodAmount() > 0
                && !carrier.isCodCollected()) {
            carrier.markCodCollected();
        }

        saveHistory(
                shipment,
                previous,
                source,
                sourceReference,
                reasonCode,
                reason,
                metadata
        );
    }

    private void synchronizeCod(Shipment shipment, CarrierShipment carrier, GhnOrderDetailResponse detail) {
        carrier.updateCodAmount(detail.codAmount());
        shipment.synchronizeCodAmount(detail.codAmount());
        if (Boolean.TRUE.equals(detail.codCollected()) && !carrier.isCodCollected()) {
            carrier.markCodCollected();
        }
        if (Boolean.TRUE.equals(detail.codTransferred())
                && carrier.isCodCollected()
                && !carrier.isCodRemitted()) {
            carrier.markCodRemitted(parseInstantOrNow(detail.codTransferDate()));
        }
    }
    private Instant detailOccurredAt(GhnOrderDetailResponse detail) {
        if ("delivered".equalsIgnoreCase(detail.status())) {
            Instant finish = parseNullableInstant(detail.finishDate());
            if (finish != null) {
                return finish;
            }
        }
        return parseInstantOrNow(detail.updatedDate());
    }

    private GhnOrderDetailResponse recoverByClientCode(String clientOrderCode) {
        try {
            return parseOrderDetail(ghnClient.getOrderByClientCode(
                    new GhnOrderByClientCodeRequest(clientOrderCode)
            ).data());
        } catch (FeignException | IllegalArgumentException exception) {
            return null;
        }
    }

    private GhnOrderDetailResponse requireOrderDetail(
            GhnResponse<JsonNode> response,
            String operation
    ) {
        if (response.code() == null || response.code() != 200) {
            throw new GhnIntegrationException(operation + " failed: " + response.message());
        }
        GhnOrderDetailResponse detail = parseOrderDetail(response.data());
        if (detail == null) {
            throw new GhnIntegrationException(operation + " failed: empty response data");
        }
        return detail;
    }


    private GhnOrderDetailResponse parseOrderDetail(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        JsonNode detailNode = data.isArray() ? data.path(0) : data;
        if (detailNode.isMissingNode() || detailNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(detailNode, GhnOrderDetailResponse.class);
    }

    private void saveHistory(
            Shipment shipment,
            ShipmentStatus previous,
            ShipmentStatusSource source,
            String reference,
            String reasonCode,
            String reason,
            JsonNode metadata
    ) {
        statusHistoryRepository.save(ShipmentStatusHistory.record(
                shipment,
                previous,
                shipment.getStatus(),
                source,
                reference,
                reasonCode,
                reason,
                "delivery-service",
                Instant.now(),
                metadata
        ));
    }

    private GhnShipmentResponse mapResponse(Shipment shipment, CarrierShipment carrier) {
        return new GhnShipmentResponse(
                shipment.getId(),
                shipment.getShipmentCode(),
                carrier.getClientOrderCode(),
                carrier.getExternalOrderCode(),
                carrier.getTrackingCode(),
                carrier.getProviderStatus(),
                shipment.getStatus(),
                carrier.getServiceCode(),
                carrier.getTotalFee(),
                carrier.getExpectedDeliveryTo()
        );
    }

    private Shipment getShipment(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
    }

    private Shipment getShipmentForUpdate(UUID shipmentId) {
        return shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
    }
    private Instant parseNullableInstant(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }


    private Instant parseInstantOrNow(String value) {
        Instant parsed = parseNullableInstant(value);
        return parsed == null ? Instant.now() : parsed;
    }

    private Integer parseDistrict(String value, String field) {
        try {
            return Integer.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must contain a GHN district id", exception);
        }
    }
    private String serviceCode(Integer serviceId, Integer serviceTypeId) {
        if (serviceId != null) {
            return String.valueOf(serviceId);
        }
        return serviceTypeId != null ? "TYPE_" + serviceTypeId : "GHN";
    }

    private String serviceName(Integer serviceId, Integer serviceTypeId) {
        if (serviceId != null) {
            return "GHN service " + serviceId;
        }
        return serviceTypeId != null ? "GHN service type " + serviceTypeId : "GHN";
    }

    private record CreatePreparation(
            UUID shipmentId,
            String shipmentCode,
            String clientOrderCode,
            GhnCreateOrderRequest request,
            String existingOrderCode
    ) {
    }

    private record CarrierReference(String orderCode) {
    }
}
