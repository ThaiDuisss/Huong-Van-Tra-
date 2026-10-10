package org.hvtra.notificationservice.invoice;

import lombok.RequiredArgsConstructor;
import org.hvtra.notificationservice.dto.event.OrderEventItemPayload;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Dựng tiêu đề và nội dung HTML của email hóa đơn từ event ORDER_COMPLETED.
 */
@Component
@RequiredArgsConstructor
public class InvoiceEmailContentBuilder {

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvoiceEmailProperties properties;

    public String subject(OrderNotificationEventPayload event) {
        return "[" + properties.getStoreName() + "] Hóa đơn " + orderCode(event);
    }

    public String html(OrderNotificationEventPayload event) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"></head>")
                .append("<body style=\"margin:0;padding:24px;background:#f4f6f4;font-family:Arial,Helvetica,sans-serif;color:#1f2933;\">")
                .append("<div style=\"max-width:600px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e3e8e3;\">");

        html.append("<div style=\"background:#315f3c;color:#ffffff;padding:20px 24px;\">")
                .append("<div style=\"font-size:20px;font-weight:bold;\">").append(esc(properties.getStoreName())).append("</div>");
        if (hasText(properties.getStoreAddress())) {
            html.append("<div style=\"font-size:13px;margin-top:4px;\">").append(esc(properties.getStoreAddress())).append("</div>");
        }
        if (hasText(properties.getStoreHotline())) {
            html.append("<div style=\"font-size:13px;margin-top:2px;\">Hotline: ").append(esc(properties.getStoreHotline())).append("</div>");
        }
        html.append("</div>");

        html.append("<div style=\"padding:24px;\">")
                .append("<p style=\"margin:0 0 12px;\">Xin chào ").append(esc(customerName(event))).append(",</p>")
                .append("<p style=\"margin:0 0 16px;\">Cảm ơn bạn đã mua hàng tại ").append(esc(properties.getStoreName()))
                .append(". Dưới đây là hóa đơn của bạn.</p>");

        html.append("<table style=\"width:100%;font-size:14px;margin-bottom:16px;\">");
        infoRow(html, "Mã hóa đơn", orderCode(event));
        infoRow(html, "Thời gian", formatTime(event.getOccurredAt()));
        infoRow(html, "Thu ngân", event.getCashierName());
        infoRow(html, "Thanh toán", paymentMethodLabel(event.getPaymentMethod()));
        html.append("</table>");

        html.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px;\">")
                .append("<thead><tr style=\"background:#eef3ee;\">")
                .append(th("Sản phẩm", "left")).append(th("SL", "right")).append(th("Đơn giá", "right")).append(th("Thành tiền", "right"))
                .append("</tr></thead><tbody>");
        List<OrderEventItemPayload> items = event.getItems() == null ? List.of() : event.getItems();
        for (OrderEventItemPayload item : items) {
            if (item == null) {
                continue;
            }
            html.append("<tr>")
                    .append(td(esc(itemName(item)), "left"))
                    .append(td(formatQuantity(item.getQuantity()), "right"))
                    .append(td(formatMoney(item.getUnitPrice()), "right"))
                    .append(td(formatMoney(lineTotal(item)), "right"))
                    .append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<table style=\"width:100%;font-size:14px;margin-top:16px;\">");
        BigDecimal original = event.getOriginalAmount();
        BigDecimal discount = event.getDiscountAmount();
        if (original != null) {
            totalRow(html, "Tổng tiền hàng", formatMoney(original), false);
        }
        if (discount != null && discount.signum() > 0) {
            totalRow(html, "Giảm giá", "-" + formatMoney(discount), false);
        }
        totalRow(html, "Khách phải trả", formatMoney(event.getFinalAmount()), true);
        if (event.getPaidAmount() != null) {
            totalRow(html, "Đã thanh toán", formatMoney(event.getPaidAmount()), false);
        }
        html.append("</table>");

        html.append("<p style=\"margin:24px 0 0;font-size:13px;color:#52606d;\">")
                .append("Email này được gửi tự động, vui lòng không trả lời. Hẹn gặp lại bạn!</p>")
                .append("</div></div></body></html>");

        return html.toString();
    }

    static String formatMoney(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(amount) + " đ";
    }

    static String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static String paymentMethodLabel(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        return switch (method.trim().toUpperCase(Locale.ROOT)) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER", "TRANSFER", "QR_CODE" -> "Chuyển khoản";
            default -> method;
        };
    }

    private BigDecimal lineTotal(OrderEventItemPayload item) {
        if (item.getLineTotal() != null) {
            return item.getLineTotal();
        }
        if (item.getQuantity() == null || item.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return item.getQuantity().multiply(item.getUnitPrice());
    }

    private String itemName(OrderEventItemPayload item) {
        if (hasText(item.getVariantName())) {
            return item.getVariantName();
        }
        if (hasText(item.getProductName())) {
            return item.getProductName();
        }
        return hasText(item.getSku()) ? item.getSku() : "Sản phẩm";
    }

    private String customerName(OrderNotificationEventPayload event) {
        return hasText(event.getCustomerName()) ? event.getCustomerName() : "Quý khách";
    }

    private String orderCode(OrderNotificationEventPayload event) {
        return hasText(event.getOrderCode()) ? event.getOrderCode() : String.valueOf(event.getOrderId());
    }

    private String formatTime(Instant instant) {
        return DATE_TIME.format((instant == null ? Instant.now() : instant).atZone(STORE_ZONE));
    }

    private void infoRow(StringBuilder html, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        html.append("<tr><td style=\"padding:2px 0;color:#52606d;width:120px;\">").append(esc(label))
                .append("</td><td style=\"padding:2px 0;font-weight:bold;\">").append(esc(value)).append("</td></tr>");
    }

    private void totalRow(StringBuilder html, String label, String value, boolean emphasised) {
        String weight = emphasised ? "font-weight:bold;font-size:16px;color:#315f3c;" : "";
        html.append("<tr><td style=\"padding:3px 0;").append(weight).append("\">").append(esc(label))
                .append("</td><td style=\"padding:3px 0;text-align:right;").append(weight).append("\">").append(esc(value))
                .append("</td></tr>");
    }

    private String th(String text, String align) {
        return "<th style=\"padding:8px;text-align:" + align + ";border-bottom:1px solid #d5ddd5;\">" + esc(text) + "</th>";
    }

    private String td(String escapedText, String align) {
        return "<td style=\"padding:8px;text-align:" + align + ";border-bottom:1px solid #eef1ee;\">" + escapedText + "</td>";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
