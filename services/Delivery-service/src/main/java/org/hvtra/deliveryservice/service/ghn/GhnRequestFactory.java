package org.hvtra.deliveryservice.service.ghn;

import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentItem;
import org.hvtra.deliveryservice.model.request.ghn.*;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.List;

@Component

public class GhnRequestFactory {

    public GhnFeeRequest feeRequest(Shipment shipment, GhnQuoteCommand command) {
        validateShipment(shipment);
        return new GhnFeeRequest(
                parseDistrict(shipment.getPickupDistrictCode(), "pickupDistrictCode"),
                required(shipment.getPickupWardCode(), "pickupWardCode"),
                parseDistrict(shipment.getDeliveryDistrictCode(), "deliveryDistrictCode"),
                required(shipment.getDeliveryWardCode(), "deliveryWardCode"),
                command.serviceId(),
                command.serviceTypeId(),
                normalizedWeight(shipment),
                normalizedDimension(shipment.getTotalLengthCm()),
                normalizedDimension(shipment.getTotalWidthCm()),
                normalizedDimension(shipment.getTotalHeightCm()),
                toInt(shipment.getDeclaredValue(), 5_000_000L, "declaredValue"),
                toInt(shipment.getCodAmount(), 10_000_000L, "codAmount"),
                command.codFailedAmount(),
                blankToNull(command.coupon()),
                items(shipment)
        );
    }

    public GhnCreateOrderRequest createRequest(
            Shipment shipment,
            String clientOrderCode,
            GhnCreateShipmentCommand command
    ) {
        validateShipment(shipment);
        String requiredNote = command.requiredNote() == null || command.requiredNote().isBlank()
                ? "KHONGCHOXEMHANG"
                : command.requiredNote();
        if (!List.of("CHOTHUHANG", "CHOXEMHANGKHONGTHU", "KHONGCHOXEMHANG").contains(requiredNote)) {
            throw new IllegalArgumentException("Unsupported GHN requiredNote: " + requiredNote);
        }
        Integer paymentType = command.paymentTypeId() == null ? 1 : command.paymentTypeId();
        return new GhnCreateOrderRequest(
                clientOrderCode,
                required(shipment.getPickupNameSnapshot(), "pickupName"),
                required(shipment.getPickupPhoneSnapshot(), "pickupPhone"),
                required(shipment.getPickupAddressSnapshot(), "pickupAddress"),
                required(shipment.getPickupWardCode(), "pickupWardCode"),
                parseDistrict(shipment.getPickupDistrictCode(), "pickupDistrictCode"),
                required(shipment.getPickupNameSnapshot(), "returnName"),
                required(shipment.getPickupPhoneSnapshot(), "returnPhone"),
                required(shipment.getPickupAddressSnapshot(), "returnAddress"),
                required(shipment.getPickupWardCode(), "returnWardCode"),
                parseDistrict(shipment.getPickupDistrictCode(), "returnDistrictCode"),
                required(shipment.getRecipientName(), "recipientName"),
                required(shipment.getRecipientPhone(), "recipientPhone"),
                required(shipment.getDeliveryAddress(), "deliveryAddress"),
                required(shipment.getDeliveryWardCode(), "deliveryWardCode"),
                parseDistrict(shipment.getDeliveryDistrictCode(), "deliveryDistrictCode"),
                toInt(shipment.getCodAmount(), 10_000_000L, "codAmount"),
                toInt(shipment.getDeclaredValue(), 5_000_000L, "declaredValue"),
                command.serviceId(),
                command.serviceTypeId(),
                paymentType,
                requiredNote,
                buildContent(shipment),
                blankToNull(command.note()),
                normalizedWeight(shipment),
                normalizedDimension(shipment.getTotalLengthCm()),
                normalizedDimension(shipment.getTotalWidthCm()),
                normalizedDimension(shipment.getTotalHeightCm()),
                blankToNull(command.coupon()),
                command.pickShift(),
                items(shipment)
        );
    }

    private String buildContent(Shipment shipment) {
        String content = shipment.getItems().stream()
                .map(ShipmentItem::getProductName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(10)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Order " + shipment.getOrderCode());
        return content.length() <= 2000 ? content : content.substring(0, 2000);
    }

    private void validateShipment(Shipment shipment) {
        if (shipment.getProvider() != org.hvtra.deliveryservice.model.enums.DeliveryProvider.GHN) {
            throw new IllegalStateException("Shipment provider must be GHN");
        }
        if (shipment.getDeliveryMode() != org.hvtra.deliveryservice.model.enums.DeliveryMode.EXTERNAL) {
            throw new IllegalStateException("GHN shipment must use EXTERNAL delivery mode");
        }
        normalizedWeight(shipment);
        normalizedDimension(shipment.getTotalLengthCm());
        normalizedDimension(shipment.getTotalWidthCm());
        normalizedDimension(shipment.getTotalHeightCm());
    }

    private List<GhnItemRequest> items(Shipment shipment) {
        return shipment.getItems().stream().map(this::item).toList();
    }

    private GhnItemRequest item(ShipmentItem item) {
        int quantity = item.getQuantity() == null
                ? 1
                : Math.max(1, item.getQuantity().setScale(0, RoundingMode.CEILING).intValue());
        return new GhnItemRequest(
                required(item.getProductName(), "item.productName"),
                blankToNull(item.getSku()),
                quantity,
                null,
                null,
                null,
                null,
                requiredItemWeight(item)
        );
    }

    private Integer requiredItemWeight(ShipmentItem item) {
        Integer weight = item.getWeightGram();
        if (weight == null || weight <= 0) {
            throw new IllegalArgumentException("item.weightGram must be greater than zero for GHN");
        }
        return weight;
    }
    private int normalizedWeight(Shipment shipment) {
        int weight = shipment.getTotalWeightGram();
        if (weight <= 0 || weight > 30_000) {
            throw new IllegalArgumentException("GHN weight must be between 1 and 30000 grams");
        }
        return weight;
    }

    private int normalizedDimension(Integer value) {
        int dimension = value == null || value <= 0 ? 1 : value;
        if (dimension > 150) {
            throw new IllegalArgumentException("GHN package dimensions must not exceed 150 cm");
        }
        return dimension;
    }

    private Integer parseDistrict(String value, String field) {
        try {
            return Integer.valueOf(required(value, field));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must contain a GHN district id", exception);
        }
    }

    private Integer toInt(long value, long max, String field) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(field + " must be between 0 and " + max);
        }
        return Math.toIntExact(value);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for GHN");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
