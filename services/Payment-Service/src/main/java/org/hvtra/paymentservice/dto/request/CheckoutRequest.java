package org.hvtra.paymentservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CheckoutRequest {

    @NotNull(message = "orderId không được null")
    private UUID orderId;

    @NotBlank(message = "orderCode không được trống")
    private String orderCode;

    @NotNull(message = "customerId không được null")
    private UUID customerId;

    private UUID sagaId;

    @NotNull(message = "finalAmount không được null")
    @DecimalMin(value = "0.00", inclusive = false, message = "finalAmount phải lớn hơn 0")
    private BigDecimal finalAmount;

    @NotNull(message = "paymentMethod không được null")
    private PaymentMethod paymentMethod;

    /**
     * Chỉ bắt buộc khi paymentMethod = CASH.
     * BANK_TRANSFER không cần truyền receivedAmount.
     */
    private BigDecimal receivedAmount;

    @AssertTrue(message = "receivedAmount bắt buộc và phải đủ tiền khi paymentMethod là CASH")
    @JsonIgnore
    public boolean isValidReceivedAmountByMethod() {
        if (paymentMethod == null) {
            return true;
        }

        if (paymentMethod != PaymentMethod.CASH) {
            return true;
        }

        return receivedAmount != null
                && finalAmount != null
                && receivedAmount.compareTo(finalAmount) >= 0;
    }
}
