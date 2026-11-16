package org.hvtra.notificationservice.invoice;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Delivery-side branches not covered by InvoiceEmailServiceTest; the mail sender is always a mock. */
class InvoiceEmailServiceDeliveryTest {

    private JavaMailSender mailSender;
    private ObjectProvider<JavaMailSender> provider;
    private InvoiceEmailProperties properties;
    private InvoiceEmailService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        properties = new InvoiceEmailProperties();
        properties.setEnabled(true);
        properties.setFrom("shop@example.com");
        service = new InvoiceEmailService(provider, properties, new InvoiceEmailContentBuilder(properties));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void invoiceIsOnlySentAfterTheSurroundingTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        service.sendAfterCommit(InvoiceEmailContentBuilderTest.sampleEvent());

        verify(mailSender, never()).send(any(MimeMessage.class));
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void rolledBackTransactionNeverSendsInvoice() {
        TransactionSynchronizationManager.initSynchronization();

        service.sendAfterCommit(InvoiceEmailContentBuilderTest.sampleEvent());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void eventsThatMustNotBeMailedRegisterNoSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        OrderNotificationEventPayload cancelled = InvoiceEmailContentBuilderTest.sampleEvent();
        cancelled.setEventType("ORDER_CANCELLED");

        service.sendAfterCommit(cancelled);
        service.sendAfterCommit(null);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void missingMailSenderIsReportedAsNotSent() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertFalse(service.send(InvoiceEmailContentBuilderTest.sampleEvent()));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void recipientIsTrimmedAndSenderCarriesDisplayName() throws Exception {
        OrderNotificationEventPayload event = InvoiceEmailContentBuilderTest.sampleEvent();
        event.setCustomerEmail("  khach@example.com  ");
        properties.setFrom("  shop@example.com ");
        properties.setFromName("Fixture Shop");

        assertTrue(service.shouldSend(event));
        assertTrue(service.send(event));

        MimeMessage message = sentMessage();
        assertEquals("khach@example.com", message.getAllRecipients()[0].toString());
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals("shop@example.com", from.getAddress());
        assertEquals("Fixture Shop", from.getPersonal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankFromAddressLeavesSenderToMailServerDefault(String from) throws Exception {
        properties.setFrom(from);

        assertTrue(service.send(InvoiceEmailContentBuilderTest.sampleEvent()));

        assertNull(sentMessage().getFrom());
    }

    @ParameterizedTest
    @ValueSource(strings = {"khach@example", "@example.com", "khach@", "khach example@x.com", "a@b@c.com",
            "khach@exa mple.com"})
    void malformedCustomerEmailIsNeverMailed(String email) {
        OrderNotificationEventPayload event = InvoiceEmailContentBuilderTest.sampleEvent();
        event.setCustomerEmail(email);

        assertFalse(service.shouldSend(event));
    }

    @Test
    void missingCustomerEmailIsNeverMailed() {
        OrderNotificationEventPayload event = InvoiceEmailContentBuilderTest.sampleEvent();
        event.setCustomerEmail(null);

        assertFalse(service.shouldSend(event));
    }

    @ParameterizedTest
    @CsvSource({
            "khach@example.com, k***@example.com",
            "ab@example.com, a***@example.com",
            "a@example.com, ***@example.com",
            "@example.com, ***@example.com"
    })
    void maskHidesLocalPart(String email, String masked) {
        assertEquals(masked, InvoiceEmailService.mask(email));
    }
}
