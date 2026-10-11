package org.hvtra.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Một dòng hàng trong event của Order-Service. Event hoàn hàng cũng có mảng {@code items}
 * với các trường khác, nên bỏ qua trường không biết.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderEventItemPayload {
    String sku;
    String productName;
    String variantName;
    BigDecimal quantity;
    BigDecimal unitPrice;
    BigDecimal discountAmount;
    BigDecimal lineTotal;
}
