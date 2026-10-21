package org.hvtra.notificationservice.invoice;

import org.hvtra.notificationservice.dto.event.OrderEventItemPayload;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceEmailContentBuilderTest {

    private final InvoiceEmailProperties properties = new InvoiceEmailProperties();
    private final InvoiceEmailContentBuilder builder = new InvoiceEmailContentBuilder(properties);

    @Test
    @DisplayName("Tiêu đề chứa tên cửa hàng và mã đơn")
    void subjectContainsStoreNameAndOrderCode() {
        assertEquals("[Hương Vân Trà] Hóa đơn ORD20261004", builder.subject(sampleEvent()));
    }

    @Test
    @DisplayName("Nội dung có dòng hàng, tổng tiền, giảm giá và phương thức thanh toán")
    void htmlContainsInvoiceLinesAndTotals() {
        String html = builder.html(sampleEvent());

        assertTrue(html.contains("Xin chào Nguyễn Văn A"));
        assertTrue(html.contains("ORD20261004"));
        assertTrue(html.contains("Trà Ô Long 100 g"));
        assertTrue(html.contains("120.000 đ"));
        assertTrue(html.contains("240.000 đ"));
        assertTrue(html.contains("-20.000 đ"));
        assertTrue(html.contains("220.000 đ"));
        assertTrue(html.contains("Tiền mặt"));
        // 03/10/2026 17:30 UTC = 04/10/2026 00:30 giờ Việt Nam
        assertTrue(html.contains("04/10/2026 00:30"));
    }

    @Test
    @DisplayName("Dữ liệu từ đơn hàng được escape để không chèn được HTML vào email")
    void htmlEscapesOrderData() {
        OrderNotificationEventPayload event = sampleEvent();
        event.setCustomerName("<script>alert(1)</script>");
        event.getItems().get(0).setVariantName("Trà <b>đặc biệt</b>");

        String html = builder.html(event);

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>đặc biệt</b>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("Khách không có tên thì dùng lời chào chung, không có giảm giá thì không hiện dòng giảm giá")
    void htmlHandlesMissingOptionalData() {
        OrderNotificationEventPayload event = sampleEvent();
        event.setCustomerName(null);
        event.setDiscountAmount(BigDecimal.ZERO);

        String html = builder.html(event);

        assertTrue(html.contains("Xin chào Quý khách"));
        assertFalse(html.contains("Giảm giá"));
    }

    @Test
    void formatsMoneyAndQuantity() {
        assertEquals("1.250.000 đ", InvoiceEmailContentBuilder.formatMoney(new BigDecimal("1250000.00")));
        assertEquals("0 đ", InvoiceEmailContentBuilder.formatMoney(null));
        assertEquals("2", InvoiceEmailContentBuilder.formatQuantity(new BigDecimal("2.000")));
        assertEquals("1,5", InvoiceEmailContentBuilder.formatQuantity(new BigDecimal("1.500")));
    }

    static OrderNotificationEventPayload sampleEvent() {
        OrderEventItemPayload item = new OrderEventItemPayload();
        item.setSku("TOL-100G-00001");
        item.setProductName("Trà Ô Long");
        item.setVariantName("Trà Ô Long 100 g");
        item.setQuantity(new BigDecimal("2.000"));
        item.setUnitPrice(new BigDecimal("120000.00"));
        item.setLineTotal(new BigDecimal("240000.00"));

        OrderNotificationEventPayload event = new OrderNotificationEventPayload();
        event.setEventType("ORDER_COMPLETED");
        event.setOrderId("0b1f3c5e-0000-0000-0000-000000000001");
        event.setOrderCode("ORD20261004");
        event.setCustomerName("Nguyễn Văn A");
        event.setCustomerEmail("khach@example.com");
        event.setCashierName("Thu ngân 1");
        event.setPaymentMethod("CASH");
        event.setOriginalAmount(new BigDecimal("240000.00"));
        event.setDiscountAmount(new BigDecimal("20000.00"));
        event.setFinalAmount(new BigDecimal("220000.00"));
        event.setPaidAmount(new BigDecimal("220000.00"));
        event.setOccurredAt(Instant.parse("2026-10-03T17:30:00Z"));
        event.setItems(new java.util.ArrayList<>(List.of(item)));
        return event;
    }
}
