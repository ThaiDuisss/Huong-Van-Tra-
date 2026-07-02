package org.hvtra.paymentservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayWebhookRequest {
    private Long id;

    private String gateway;

    /**
     * Format SePay: yyyy-MM-dd HH:mm:ss
     */
    private String transactionDate;

    private String accountNumber;

    private String subAccount;

    /**
     * Mã thanh toán SePay extract từ nội dung chuyển khoản.
     */
    private String code;

    private String content;

    /**
     * in / out
     */
    private String transferType;

    private BigDecimal transferAmount;

    private BigDecimal accumulated;

    private String referenceCode;

    private String description;
}