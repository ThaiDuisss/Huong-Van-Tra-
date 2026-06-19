package org.hvtra.paymentservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CashPaymentRequest {

        private UUID orderId;

        private BigDecimal receivedAmount;

        private UUID createdBy;
}
