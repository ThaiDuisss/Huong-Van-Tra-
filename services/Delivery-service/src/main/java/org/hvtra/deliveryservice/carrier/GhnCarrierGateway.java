package org.hvtra.deliveryservice.carrier;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.request.ghn.GhnCreateShipmentCommand;
import org.hvtra.deliveryservice.model.request.ghn.GhnQuoteCommand;
import org.hvtra.deliveryservice.model.response.ghn.GhnQuoteResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnShipmentResponse;
import org.hvtra.deliveryservice.service.ghn.GhnService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GhnCarrierGateway implements CarrierGateWay {

    private final GhnService service;
    private final ObjectMapper objectMapper;


    @Override
    public DeliveryProvider provider() {
        return DeliveryProvider.GHN;
    }

    @Override
    public CarrierCapabilities capabilities() {
        return new CarrierCapabilities(
                provider(),
                false,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                false
        );
    }

    @Override
    public List<CarrierQuoteResult> quote(UUID shipmentId, CarrierQuoteCommand command) {
        String serviceCode = singleServiceCode(command.serviceCodes());
        GhnQuoteResponse response = service.calculateFee(shipmentId, new GhnQuoteCommand(
                parseOptionalPositiveInt(serviceCode, "serviceCode"),
                command.serviceTypeId(),
                command.codFailedAmount(),
                command.promoCode()
        ));
        return List.of(new CarrierQuoteResult(
                shipmentId,
                DeliveryProvider.GHN,
                serviceCode(response.serviceId(), response.serviceTypeId()),
                response.totalFee(),
                response.serviceFee(),
                response.insuranceFee(),
                response.codFee(),
                null,
                null,
                objectMapper.valueToTree(response.detail())
        ));
    }

    @Override
    public CarrierShipmentResult create(UUID shipmentId, CarrierCreateCommand command) {
        GhnShipmentResponse response = service.createShipment(shipmentId, new GhnCreateShipmentCommand(
                parseOptionalPositiveInt(command.serviceCode(), "serviceCode"),
                command.serviceTypeId(),
                paymentType(command.resolvedPaymentMethod()),
                requiredNote(command.resolvedInspectionPolicy()),
                command.note(),
                command.promoCode(),
                command.pickupShifts()
        ));
        return map(response);
    }

    @Override
    public CarrierShipmentResult refresh(UUID shipmentId) {
        return map(service.refreshShipment(shipmentId));
    }

    @Override
    public CarrierShipmentResult cancel(UUID shipmentId, CarrierCancelCommand command) {
        return map(service.cancelShipment(shipmentId, command == null ? null : command.reason()));
    }

    @Override
    public CarrierShipmentResult get(UUID shipmentId) {
        return map(service.getShipmentResponse(shipmentId));
    }

    private CarrierShipmentResult map(GhnShipmentResponse response) {
        return new CarrierShipmentResult(
                response.shipmentId(),
                response.shipmentCode(),
                DeliveryProvider.GHN,
                response.clientOrderCode(),
                response.orderCode(),
                response.trackingCode(),
                null,
                response.providerStatus(),
                response.shipmentStatus(),
                response.serviceCode(),
                response.totalFee(),
                response.expectedDeliveryAt(),
                null,
                objectMapper.valueToTree(response)
        );
    }

    private String singleServiceCode(List<String> serviceCodes) {
        if (serviceCodes == null || serviceCodes.isEmpty()) {
            return null;
        }
        List<String> selected = serviceCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .toList();
        if (selected.size() > 1) {
            throw new IllegalArgumentException("GHN quote supports at most one serviceCode");
        }
        return selected.isEmpty() ? null : selected.getFirst();
    }

    private Integer parseOptionalPositiveInt(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must contain a GHN numeric service id", exception);
        }
    }

    private Integer paymentType(CarrierPaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case SENDER_CASH, BALANCE -> 1;
            case RECIPIENT_CASH -> 2;
        };
    }

    private String requiredNote(CarrierInspectionPolicy policy) {
        return switch (policy) {
            case NO_INSPECTION -> "KHONGCHOXEMHANG";
            case VIEW_ONLY -> "CHOXEMHANGKHONGTHU";
            case TRY_ON -> "CHOTHUHANG";
        };
    }

    private String serviceCode(Integer serviceId, Integer serviceTypeId) {
        if (serviceId != null) {
            return String.valueOf(serviceId);
        }
        return serviceTypeId == null ? null : "type:" + serviceTypeId;
    }
}
