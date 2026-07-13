package org.hvtra.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    private UUID paymentId;
    private UUID attemptId;

    private String paymentCode;
    private UUID orderId;
    private String orderCode;

    private BigDecimal amountRequired;
    private BigDecimal amountPaid;

    /**
     * Chỉ dùng cho CASH.
     */
    private BigDecimal receivedAmount;
    private BigDecimal changeAmount;

    private String status;
    private String method;
    private String methodLabel;
    private String provider;

    /**
     * Chỉ dùng cho BANK_TRANSFER/SEPAY.
     */
    private String transferContent;
    private String qrUrl;
    private String bankCode;
    private String bankAccountNumber;
    private String bankAccountName;
    private LocalDateTime expiresAt;

}
