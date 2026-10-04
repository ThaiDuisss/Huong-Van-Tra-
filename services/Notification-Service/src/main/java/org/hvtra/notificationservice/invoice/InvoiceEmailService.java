package org.hvtra.notificationservice.invoice;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Gửi hóa đơn qua email cho khách hàng khi đơn hàng hoàn tất (event ORDER_COMPLETED).
 *
 * <p>Việc gửi là "best effort": lỗi gửi mail chỉ được ghi log, không làm hỏng việc xử lý
 * thông báo. Event trùng đã bị chặn bởi inbox nên mỗi đơn chỉ gửi một lần.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final InvoiceEmailProperties properties;
    private final InvoiceEmailContentBuilder contentBuilder;

    /** Gửi sau khi transaction xử lý event đã commit để không gửi mail cho event bị rollback. */
    public void sendAfterCommit(OrderNotificationEventPayload event) {
        if (!shouldSend(event)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    boolean shouldSend(OrderNotificationEventPayload event) {
        if (event == null || !ORDER_COMPLETED.equals(event.getEventType())) {
            return false;
        }
        if (!properties.isEnabled()) {
            return false;
        }
        String email = event.getCustomerEmail();
        if (email == null || email.isBlank()) {
            log.debug("Đơn {} không có email khách hàng, bỏ qua gửi hóa đơn.", event.getOrderCode());
            return false;
        }
        if (!EMAIL.matcher(email.trim()).matches()) {
            log.warn("Email khách hàng của đơn {} không hợp lệ, bỏ qua gửi hóa đơn.", event.getOrderCode());
            return false;
        }
        return true;
    }

    /** @return true nếu đã gửi thành công. */
    boolean send(OrderNotificationEventPayload event) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Chưa cấu hình spring.mail.host nên không gửi được hóa đơn của đơn {}.", event.getOrderCode());
            return false;
        }
        String to = event.getCustomerEmail().trim();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (properties.getFrom() != null && !properties.getFrom().isBlank()) {
                helper.setFrom(properties.getFrom().trim(), properties.getFromName());
            }
            helper.setTo(to);
            helper.setSubject(contentBuilder.subject(event));
            helper.setText(contentBuilder.html(event), true);
            mailSender.send(message);
            log.info("Đã gửi hóa đơn của đơn {} tới {}.", event.getOrderCode(), mask(to));
            return true;
        } catch (Exception ex) {
            log.error("Gửi hóa đơn của đơn {} tới {} thất bại: {}", event.getOrderCode(), mask(to), ex.getMessage(), ex);
            return false;
        }
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
