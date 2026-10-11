package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {
    private UUID orderId;

    private UUID tenantId;
    private UUID branchId;
    private UUID terminalId;
    private UUID shiftId;
    private UUID customerId;

    private String customerNameSnapshot;
    private String customerPhoneSnapshot;
    private String customerEmailSnapshot;

    private String branchNameSnapshot;
    private String terminalNameSnapshot;
    private String shiftCodeSnapshot;

    private UUID cashierId;
    private String cashierNameSnapshot;

    private String recipientNameSnapshot;
    private String recipientPhoneSnapshot;
    private String shippingAddressSnapshot;
    private String shippingProviderSnapshot;

    private String orderType = "POS";

    @Valid
    private List<CreateDiscountRequest> orderDiscounts =
            new ArrayList<>();

    @DecimalMin(
            value = "0.00",
            message = "shippingFee phải lớn hơn hoặc bằng 0"
    )
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @DecimalMin(
            value = "0.00",
            message = "paidAmount phải lớn hơn hoặc bằng 0"
    )
    private BigDecimal paidAmount;

    private String paymentMethod;
    private String paymentCode;

    /** Khách đồng ý chờ hàng: đơn thiếu hàng sẽ được ghi nhận là đơn chờ hàng thay vì bị từ chối. */
    private Boolean allowBackorder;

    /** Chỉ dùng cho đơn chờ hàng trả tiền mặt: thu trước tiền cọc, phần còn lại thu khi giao hàng. */
    private BigDecimal depositAmount;

    /** Khoá idempotency của đơn bán offline (POS sinh UUID). Bắt buộc với /pos/offline-checkout. */
    private UUID clientOrderId;

    /** Giờ bán thật tại quầy của đơn offline. Null = giờ đồng bộ. */
    private Instant soldAt;

    /** Đơn được bán khi mất mạng, đồng bộ lại sau. */
    private Boolean offlineSale;

    @Valid
    @NotEmpty(message = "items không được rỗng")
    private List<CreateOrderItemRequest> items =
            new ArrayList<>();
}