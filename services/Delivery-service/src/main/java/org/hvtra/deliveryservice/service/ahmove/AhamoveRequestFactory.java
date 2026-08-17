package org.hvtra.deliveryservice.service.ahmove;

import org.hvtra.deliveryservice.carrier.model.CarrierCreateCommand;
import org.hvtra.deliveryservice.carrier.model.CarrierPaymentMethod;
import org.hvtra.deliveryservice.carrier.model.CarrierQuoteCommand;
import org.hvtra.deliveryservice.carrier.model.CarrierSpecialRequest;
import org.hvtra.deliveryservice.carrier.support.CarrierTextUtils;
import org.hvtra.deliveryservice.carrier.support.CarrierUnitUtils;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentItem;
import org.hvtra.deliveryservice.model.enums.DeliveryMode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.request.ahmove.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AhamoveRequestFactory {

    public AhamoveEstimateOrderRequest estimateRequest(Shipment shipment, CarrierQuoteCommand command) {
        validateShipment(shipment);
        validateQuoteCapabilities(command);
        List<String> serviceCodes = (command.serviceCodes() == null ? List.<String>of() : command.serviceCodes()).stream()
                .map(code -> CarrierTextUtils.required(code, "serviceCodes", "Ahamove"))
                .distinct()
                .toList();
        if (serviceCodes.isEmpty()) {
            throw new IllegalArgumentException("At least one Ahamove serviceCode is required");
        }
        List<AhamoveSpecialRequest> requests = requests(command.resolvedSpecialRequests());
        long orderTime = epochSeconds(command.scheduledAt());
        return new AhamoveEstimateOrderRequest(
                orderTime,
                idleUntil(orderTime),
                paths(shipment, command.pickupNote(), command.deliveryNote()),
                serviceCodes.stream().map(code -> new AhamoveServiceRequest(code, requests)).toList(),
                paymentMethod(command.resolvedPaymentMethod()),
                CarrierTextUtils.blankToNull(command.note()),
                CarrierTextUtils.blankToNull(command.promoCode()),
                null,
                packageDetail(shipment),
                Boolean.TRUE.equals(command.routeOptimized()) ? Boolean.TRUE : null
        );


    }

    public AhamoveCreateOrderRequest createRequest(Shipment shipment, CarrierCreateCommand command) {
        validateShipment(shipment);
        validateCapabilities(command);
        long orderTime = epochSeconds(command.scheduledAt());
        return new AhamoveCreateOrderRequest(
                orderTime,
                idleUntil(orderTime),
                paths(shipment, command.pickupNote(), command.deliveryNote()),
                CarrierTextUtils.required(command.serviceCode(), "serviceCode", "Ahamove"),
                requests(command.resolvedSpecialRequests()),
                paymentMethod(command.resolvedPaymentMethod()),
                CarrierTextUtils.blankToNull(command.note()),
                CarrierTextUtils.blankToNull(command.promoCode()),
                null,
                packageDetail(shipment),
                Boolean.TRUE.equals(command.routeOptimized()) ? Boolean.TRUE : null
        );
    }

    private List<AhamovePathRequest> paths(Shipment shipment, String pickupNote, String deliveryNote) {
        AhamovePathRequest pickup = new AhamovePathRequest(
                shipment.getPickupLatitude(),
                shipment.getPickupLongitude(),
                CarrierTextUtils.required(shipment.getPickupAddressSnapshot(), "pickupAddress", "Ahamove"),
                null,
                CarrierTextUtils.required(shipment.getPickupNameSnapshot(), "pickupName", "Ahamove"),
                CarrierTextUtils.normalizePhone(shipment.getPickupPhoneSnapshot(), "pickupPhone", "Ahamove"),
                CarrierTextUtils.blankToNull(pickupNote),
                null,
                null,
                null
        );
        String resolvedDeliveryNote = CarrierTextUtils.blankToNull(deliveryNote);
        if (resolvedDeliveryNote == null) {
            resolvedDeliveryNote = CarrierTextUtils.blankToNull(shipment.getDeliveryNote());
        }
        AhamovePathRequest delivery = new AhamovePathRequest(
                shipment.getDeliveryLatitude(),
                shipment.getDeliveryLongitude(),
                CarrierTextUtils.required(shipment.getDeliveryAddress(), "deliveryAddress", "Ahamove"),
                null,
                CarrierTextUtils.required(shipment.getRecipientName(), "recipientName", "Ahamove"),
                CarrierTextUtils.normalizePhone(shipment.getRecipientPhone(), "recipientPhone", "Ahamove"),
                resolvedDeliveryNote,
                shipment.getCodAmount() > 0 ? shipment.getCodAmount() : null,
                shipment.getDeclaredValue() > 0 ? shipment.getDeclaredValue() : null,
                shipment.getShipmentCode()
        );
        return List.of(pickup, delivery);
    }

//Hàm này dùng để chuyển danh sách yêu cầu đặc biệt dạng dùng chung CarrierSpecialRequest
// sang DTO riêng của Ahamove là AhamoveSpecialRequest
    private List<AhamoveSpecialRequest> requests(List<CarrierSpecialRequest> requests) {
        return requests.stream()
                .map(request -> {
                    List<AhamoveSpecialRequestOption> options = request.resolvedOptions().stream()
                            .map(option -> new AhamoveSpecialRequestOption(
                                    CarrierTextUtils.required(option.code(), "specialRequests.options.code", "Ahamove"),
                                    CarrierTextUtils.blankToNull(option.selectedValue())
                            ))
                            .toList();
                    return new AhamoveSpecialRequest(
                            CarrierTextUtils.required(request.code(), "specialRequests.code", "Ahamove"),
                            request.quantity(),
                            CarrierTextUtils.blankToNull(request.tierCode()),
                            options.isEmpty() ? null : options,
                            request.pathIndex()
                    );
                })
                .toList();
    }

    private List<AhamovePackageDetailRequest> packageDetail(Shipment shipment) {
        if (shipment.getTotalWeightGram() <= 0
                && shipment.getTotalLengthCm() == null
                && shipment.getTotalWidthCm() == null
                && shipment.getTotalHeightCm() == null) {
            return null;
        }
        if (shipment.getTotalWeightGram() <= 0) {
            throw new IllegalStateException(
                    "Ahamove package_detail requires totalWeightGram when package dimensions are provided"
            );
        }
        return List.of(new AhamovePackageDetailRequest(
                CarrierUnitUtils.gramsToKilograms(shipment.getTotalWeightGram()),
                CarrierUnitUtils.positiveCentimeters(shipment.getTotalLengthCm()),
                CarrierUnitUtils.positiveCentimeters(shipment.getTotalWidthCm()),
                CarrierUnitUtils.positiveCentimeters(shipment.getTotalHeightCm()),
                packageDescription(shipment)
        ));
    }

    private String packageDescription(Shipment shipment) {
        String description = shipment.getItems().stream()
                .map(ShipmentItem::getProductName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(8)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Order " + shipment.getOrderCode());
        return description.length() <= 255 ? description : description.substring(0, 255);
    }

    private long epochSeconds(Instant scheduledAt) {
        return scheduledAt == null ? 0 : scheduledAt.getEpochSecond();
    }

    //Hàm này kiểm tra yêu cầu báo giá (CarrierQuoteCommand) trước khi gửi sang Ahamove
    private void validateQuoteCapabilities(CarrierQuoteCommand command) {
        if (command.serviceTypeId() != null) {
            throw new IllegalArgumentException("Ahamove requires serviceCodes, not serviceTypeId");
        }
        if (command.codFailedAmount() != null && command.codFailedAmount() != 0) {
            throw new IllegalArgumentException("codFailedAmount is only supported by compatible carriers such as GHN");
        }
    }

    private Long idleUntil(long orderTime) {
        return orderTime > 0 ? orderTime : null;
    }


    private void validateCapabilities(CarrierCreateCommand command) {
        if (command.serviceTypeId() != null && (command.serviceCode() == null || command.serviceCode().isBlank())) {
            throw new IllegalArgumentException("Ahamove requires serviceCode, not serviceTypeId");
        }
        if (command.resolvedInspectionPolicy() != org.hvtra.deliveryservice.carrier.model.CarrierInspectionPolicy.NO_INSPECTION) {
            throw new IllegalArgumentException(
                    "Ahamove inspection must be configured through specialRequests from Ahamove master data"
            );
        }
        if (command.pickupShifts() != null && !command.pickupShifts().isEmpty()) {
            throw new IllegalArgumentException("pickupShifts is not supported by Ahamove");
        }
        if (Boolean.TRUE.equals(command.requireProofOfDelivery())
                || Boolean.TRUE.equals(command.requireReceiverVerification())) {
            throw new IllegalArgumentException(
                    "Ahamove proof/verification must be configured through specialRequests from Ahamove master data"
            );
        }
    }

    private String paymentMethod(CarrierPaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case SENDER_CASH -> "CASH";
            case RECIPIENT_CASH -> "CASH_BY_RECIPIENT";
            case BALANCE -> "BALANCE";
        };
    }

    private void validateShipment(Shipment shipment) {
        if (shipment.getProvider() != DeliveryProvider.AHAMOVE) {
            throw new IllegalStateException("Shipment provider must be AHAMOVE");
        }
        if (shipment.getDeliveryMode() != DeliveryMode.EXTERNAL) {
            throw new IllegalStateException("Ahamove shipment must use EXTERNAL delivery mode");
        }
    }
}

