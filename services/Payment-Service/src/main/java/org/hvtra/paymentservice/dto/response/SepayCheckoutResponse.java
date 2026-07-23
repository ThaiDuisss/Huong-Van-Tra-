package org.hvtra.paymentservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SepayCheckoutResponse {
    private UUID paymentId;
    private UUID orderId;
    private UUID sagaId;
    private String paymentCode;
    private BigDecimal amountRequired;
    private String currency;
    private String qrUrl;
    private LocalDateTime expiresAt;
    private String status;
    private String method;
    private String provider;
}