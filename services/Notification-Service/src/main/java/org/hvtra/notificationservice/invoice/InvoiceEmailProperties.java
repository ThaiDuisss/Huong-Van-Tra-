package org.hvtra.notificationservice.invoice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình gửi hóa đơn qua email cho khách hàng sau khi đơn hoàn tất.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notification.invoice-email")
public class InvoiceEmailProperties {

    /** Bật/tắt gửi hóa đơn qua email. Mặc định tắt cho tới khi cấu hình tài khoản gửi. */
    private boolean enabled = false;

    /** Địa chỉ gửi (thường trùng với tài khoản SMTP). */
    private String from;

    /** Tên hiển thị của người gửi. */
    private String fromName = "Hương Vân Trà";

    private String storeName = "Hương Vân Trà";
    private String storeAddress = "";
    private String storeHotline = "";
}
