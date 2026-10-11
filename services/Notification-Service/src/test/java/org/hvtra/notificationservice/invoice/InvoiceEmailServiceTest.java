package org.hvtra.notificationservice.invoice;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceEmailServiceTest {

    private JavaMailSender mailSender;
    private InvoiceEmailProperties properties;
    private InvoiceEmailService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        properties = new InvoiceEmailProperties();
        properties.setEnabled(true);
        properties.setFrom("shop@example.com");
        service = new InvoiceEmailService(provider, properties, new InvoiceEmailContentBuilder(properties));
    }

    @Test
    @DisplayName("Đơn hoàn tất có email khách thì gửi hóa đơn tới đúng địa chỉ")
    void sendsInvoiceToCustomerEmail() throws Exception {
        service.sendAfterCommit(InvoiceEmailContentBuilderTest.sampleEvent());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertEquals("khach@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("[Hương Vân Trà] Hóa đơn ORD20261004", message.getSubject());
        assertTrue(message.getFrom()[0].toString().contains("shop@example.com"));
    }

    @Test
    @DisplayName("Không gửi khi tính năng đang tắt")
    void doesNotSendWhenDisabled() {
        properties.setEnabled(false);

        service.sendAfterCommit(InvoiceEmailContentBuilderTest.sampleEvent());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Không gửi khi khách không có email hoặc email không hợp lệ")
    void doesNotSendWithoutValidEmail() {
        OrderNotificationEventPayload noEmail = InvoiceEmailContentBuilderTest.sampleEvent();
        noEmail.setCustomerEmail(" ");
        OrderNotificationEventPayload badEmail = InvoiceEmailContentBuilderTest.sampleEvent();
        badEmail.setCustomerEmail("khong-phai-email");

        service.sendAfterCommit(noEmail);
        service.sendAfterCommit(badEmail);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Chỉ gửi cho event ORDER_COMPLETED")
    void doesNotSendForOtherEvents() {
        OrderNotificationEventPayload event = InvoiceEmailContentBuilderTest.sampleEvent();
        event.setEventType("ORDER_PAYMENT_SUCCEEDED");

        service.sendAfterCommit(event);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Lỗi SMTP không ném ra ngoài để không làm hỏng xử lý thông báo")
    void smtpFailureIsSwallowed() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertFalse(service.send(InvoiceEmailContentBuilderTest.sampleEvent()));
    }

    @Test
    void masksEmailInLogs() {
        assertEquals("k***@example.com", InvoiceEmailService.mask("khach@example.com"));
    }
}
