package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentCreateRequest {

    @NotNull(message = "orderId không được null")
    private UUID orderId;

    @NotBlank(message = "orderCode không được trống")
    private String orderCode;

    private UUID customerId;

//    @NotNull(message = "sagaId không được null")
    private UUID sagaId;

    @NotNull(message = "paymentMethod không được null")
    private PaymentMethod paymentMethod;

    private BigDecimal goodsAmount;
    private BigDecimal discountAmount;
    private BigDecimal deliveryFee;

    @NotNull(message = "finalAmount không được null")
    @DecimalMin(value = "0.01", message = "finalAmount phải lớn hơn 0")
    private BigDecimal finalAmount;

    /**
     * Chỉ dùng cho CASH.
     */
    private BigDecimal receivedAmount;

    /**
     * Chỉ dùng cho CASH: lần này chỉ thu tiền cọc của đơn đặt trước.
     */
    private BigDecimal depositAmount;

    private Boolean deliveryRequired;
    private UUID deliveryId;
}
