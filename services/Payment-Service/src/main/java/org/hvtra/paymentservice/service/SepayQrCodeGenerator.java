package org.hvtra.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.config.SepayProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SepayQrCodeGenerator {

    private final SepayProperties properties;

    public String generate(String transferContent, BigDecimal amount) {
        return UriComponentsBuilder
                .fromUriString("https://vietqr.app/img")
                .queryParam("acc", properties.getAccountNumber())
                .queryParam("bank", properties.getBankCode())
                .queryParam("amount", amount.setScale(0, RoundingMode.HALF_UP).toPlainString())
                .queryParam("des", transferContent)
                .queryParam("template", properties.getQrTemplate())
                .queryParam("showinfo", "true")
                .queryParam("fullacc", "true")
                .queryParam("holder", properties.getAccountName())
                .queryParam("store", properties.getStoreName())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }
}