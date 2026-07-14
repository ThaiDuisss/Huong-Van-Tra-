package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.SoftDeleteAuditableEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryMode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.PaymentType;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "shipments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shipments_tenant_code", columnNames = {"tenant_id", "shipment_code"}),
                @UniqueConstraint(name = "uk_shipments_order_number", columnNames = {"order_id", "shipment_no"})
        })
@Where(clause = "is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends SoftDeleteAuditableEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "tenant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID tenantId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "shipment_code", nullable = false, length = 50)
    private String shipmentCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_id", columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "payment_code", length = 50)
    private String paymentCode;

    @Column(name = "shipment_no", nullable = false)
    private int shipmentNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 30)
    private DeliveryMode deliveryMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private DeliveryProvider provider;

    @Column(name = "service_code", length = 100)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ShipmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @Column(name = "cod_amount", nullable = false)
    private long codAmount;

    @Column(name = "cod_collected_amount", nullable = false)
    private long codCollectedAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "actual_shipping_cost")
    private Long actualShippingCost;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_address_id", columnDefinition = "BINARY(16)")
    private UUID customerAddressId;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "delivery_province_code", length = 50)
    private String deliveryProvinceCode;

    @Column(name = "delivery_district_code", length = 50)
    private String deliveryDistrictCode;

    @Column(name = "delivery_ward_code", length = 50)
    private String deliveryWardCode;

    @Column(name = "delivery_latitude", precision = 10, scale = 7)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 7)
    private BigDecimal deliveryLongitude;

    @Column(name = "pickup_name_snapshot", length = 255)
    private String pickupNameSnapshot;

    @Column(name = "pickup_phone_snapshot", length = 30)
    private String pickupPhoneSnapshot;

    @Column(name = "pickup_address_snapshot", nullable = false, length = 500)
    private String pickupAddressSnapshot;

    @Column(name = "pickup_province_code", length = 50)
    private String pickupProvinceCode;

    @Column(name = "pickup_district_code", length = 50)
    private String pickupDistrictCode;

    @Column(name = "pickup_ward_code", length = 50)
    private String pickupWardCode;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Column(name = "package_count", nullable = false)
    private int packageCount = 1;

    @Column(name = "total_weight_gram", nullable = false)
    private int totalWeightGram;

    @Column(name = "total_length_cm")
    private Integer totalLengthCm;

    @Column(name = "total_width_cm")
    private Integer totalWidthCm;

    @Column(name = "total_height_cm")
    private Integer totalHeightCm;

    @Column(name = "declared_value", nullable = false)
    private long declaredValue;

    @Column(name = "requested_from")
    private Instant requestedFrom;

    @Column(name = "requested_to")
    private Instant requestedTo;

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "courier_employee_id", columnDefinition = "BINARY(16)")
    private UUID courierEmployeeId;

    @Column(name = "courier_name_snapshot", length = 255)
    private String courierNameSnapshot;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "external_order_code", length = 100)
    private String externalOrderCode;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "delivery_note", length = 1000)
    private String deliveryNote;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ShipmentItem> items = new ArrayList<>();

    public static Shipment create(
            UUID tenantId,
            UUID branchId,
            String shipmentCode,
            UUID orderId,
            String orderCode,
            int shipmentNo,
            DeliveryMode deliveryMode,
            DeliveryProvider provider,
            PaymentType paymentType,
            long codAmount,
            long shippingFee,
            UUID customerAddressId,
            String recipientName,
            String recipientPhone,
            String deliveryAddress,
            String pickupAddressSnapshot
    ) {
        Shipment shipment = new Shipment();
        shipment.tenantId = tenantId;
        shipment.branchId = branchId;
        shipment.shipmentCode = shipmentCode;
        shipment.orderId = orderId;
        shipment.orderCode = orderCode;
        shipment.shipmentNo = shipmentNo;
        shipment.deliveryMode = deliveryMode;
        shipment.provider = provider;
        shipment.paymentType = paymentType;
        shipment.codAmount = codAmount;
        shipment.shippingFee = shippingFee;
        shipment.customerAddressId = customerAddressId;
        shipment.recipientName = recipientName;
        shipment.recipientPhone = recipientPhone;
        shipment.deliveryAddress = deliveryAddress;
        shipment.pickupAddressSnapshot = pickupAddressSnapshot;
        shipment.status = ShipmentStatus.PENDING;
        shipment.currency = "VND";
        shipment.packageCount = 1;
        return shipment;
    }

    public void attachPayment(UUID paymentId, String paymentCode) {
        this.paymentId = paymentId;
        this.paymentCode = paymentCode;
    }

    public void configureDeliveryLocation(
            String provinceCode,
            String districtCode,
            String wardCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.deliveryProvinceCode = provinceCode;
        this.deliveryDistrictCode = districtCode;
        this.deliveryWardCode = wardCode;
        this.deliveryLatitude = latitude;
        this.deliveryLongitude = longitude;
    }

    public void configurePickupSnapshot(
            String name,
            String phone,
            String provinceCode,
            String districtCode,
            String wardCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.pickupNameSnapshot = name;
        this.pickupPhoneSnapshot = phone;
        this.pickupProvinceCode = provinceCode;
        this.pickupDistrictCode = districtCode;
        this.pickupWardCode = wardCode;
        this.pickupLatitude = latitude;
        this.pickupLongitude = longitude;
    }

    public void configurePackage(int packageCount, int weightGram, Integer lengthCm, Integer widthCm, Integer heightCm, long declaredValue) {
        if (packageCount <= 0 || weightGram < 0 || declaredValue < 0) {
            throw new IllegalArgumentException("Invalid package information");
        }
        this.packageCount = packageCount;
        this.totalWeightGram = weightGram;
        this.totalLengthCm = lengthCm;
        this.totalWidthCm = widthCm;
        this.totalHeightCm = heightCm;
        this.declaredValue = declaredValue;
    }

    public void schedule(Instant requestedFrom, Instant requestedTo, Instant estimatedDeliveryAt) {
        if (requestedFrom != null && requestedTo != null && requestedTo.isBefore(requestedFrom)) {
            throw new IllegalArgumentException("requestedTo must not be before requestedFrom");
        }
        this.requestedFrom = requestedFrom;
        this.requestedTo = requestedTo;
        this.estimatedDeliveryAt = estimatedDeliveryAt;
    }

    public void addItem(ShipmentItem item) {
        item.attachTo(this);
        this.items.add(item);
    }

    public void markReadyForPlanning() {
        requireStatus(ShipmentStatus.PENDING);
        this.status = ShipmentStatus.READY_FOR_PLANNING;
    }

    public void markPlanned() {
        requireStatus(ShipmentStatus.READY_FOR_PLANNING);
        this.status = ShipmentStatus.PLANNED;
    }

    public void assignCourier(UUID employeeId, String courierName) {
        requireStatus(ShipmentStatus.READY_FOR_PLANNING, ShipmentStatus.PLANNED, ShipmentStatus.ASSIGNED);
        this.courierEmployeeId = employeeId;
        this.courierNameSnapshot = courierName;
        this.status = ShipmentStatus.ASSIGNED;
    }

    public void markReadyForPickup() {
        requireStatus(ShipmentStatus.ASSIGNED, ShipmentStatus.PLANNED);
        this.status = ShipmentStatus.READY_FOR_PICKUP;
    }

    public void markPickedUp(Instant pickedUpAt) {
        requireStatus(ShipmentStatus.READY_FOR_PICKUP, ShipmentStatus.ASSIGNED);
        this.pickedUpAt = pickedUpAt;
        this.status = ShipmentStatus.PICKED_UP;
    }

    public void markInTransit() {
        requireStatus(ShipmentStatus.PICKED_UP);
        this.status = ShipmentStatus.IN_TRANSIT;
    }

    public void markDelivered(Instant deliveredAt, long collectedCodAmount) {
        requireStatus(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT);
        if (collectedCodAmount < 0 || collectedCodAmount > codAmount) {
            throw new IllegalArgumentException("Invalid collected COD amount");
        }
        this.codCollectedAmount = collectedCodAmount;
        this.deliveredAt = deliveredAt;
        this.failureCode = null;
        this.failureReason = null;
        this.status = ShipmentStatus.DELIVERED;
    }

    public void markDeliveryFailed(String code, String reason) {
        requireStatus(ShipmentStatus.ASSIGNED, ShipmentStatus.READY_FOR_PICKUP, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT);
        this.failureCode = code;
        this.failureReason = reason;
        this.status = ShipmentStatus.DELIVERY_FAILED;
    }

    public void requestReturn() {
        requireStatus(ShipmentStatus.DELIVERY_FAILED);
        this.status = ShipmentStatus.RETURN_REQUESTED;
    }

    public void markReturning() {
        requireStatus(ShipmentStatus.RETURN_REQUESTED);
        this.status = ShipmentStatus.RETURNING;
    }

    public void markReturned() {
        requireStatus(ShipmentStatus.RETURNING);
        this.status = ShipmentStatus.RETURNED;
    }

    public void cancel(String reason) {
        if (status == ShipmentStatus.DELIVERED || status == ShipmentStatus.RETURNED || status == ShipmentStatus.CANCELLED) {
            throw new IllegalStateException("Shipment cannot be cancelled in status " + status);
        }
        this.failureReason = reason;
        this.status = ShipmentStatus.CANCELLED;
    }

    public void attachCarrierReference(String serviceCode, String trackingCode, String externalOrderCode, Long actualShippingCost) {
        this.serviceCode = serviceCode;
        this.trackingCode = trackingCode;
        this.externalOrderCode = externalOrderCode;
        this.actualShippingCost = actualShippingCost;
    }

    private void requireStatus(ShipmentStatus... allowedStatuses) {
        for (ShipmentStatus allowed : allowedStatuses) {
            if (status == allowed) {
                return;
            }
        }
        throw new IllegalStateException("Invalid shipment status transition from " + status);
    }
}
