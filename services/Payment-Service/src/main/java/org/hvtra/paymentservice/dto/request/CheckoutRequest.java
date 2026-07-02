package org.hvtra.paymentservice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CheckoutRequest {

    @NotNull(message = "orderId không được null")
    private UUID orderId;

    @NotNull(message = "receivedAmount không được null")
    @DecimalMin(value = "0.00", inclusive = false, message = "receivedAmount phải lớn hơn 0")
    private BigDecimal receivedAmount;

    @NotNull(message = "Payment method not null")
    private PaymentMethod paymentMethod;

    @AssertTrue(message = "receivedAmount bắt buộc khi paymentMethod là CASH")
    public boolean isValidReceivedAmountByMethod() {
        if (paymentMethod == null) {
            return true;
        }

        if (paymentMethod == PaymentMethod.CASH) {
            return receivedAmount != null && receivedAmount.compareTo(BigDecimal.ZERO) > 0;
        }

        return true;
    }
}
