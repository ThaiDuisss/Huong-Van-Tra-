package org.hvtra.paymentservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.paymentservice.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** QR VietQR chỉ có hiệu lực 15 phút: quá hạn chưa có tiền thì đóng phiên và báo Order-Service. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirationScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.expiration.delay-ms:60000}",
            initialDelayString = "${payment.expiration.initial-delay-ms:30000}")
    public void expireOverduePayments() {
        int expired = paymentService.expireOverduePayments();
        if (expired > 0) {
            log.info("Đã đóng {} phiên chuyển khoản quá hạn", expired);
        }
    }
}
