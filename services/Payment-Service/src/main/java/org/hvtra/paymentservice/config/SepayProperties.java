package org.hvtra.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment.sepay")
public class SepayProperties {

    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String storeName;
    private String qrTemplate = "compact";
    private int paymentTimeoutMinutes = 15;
}