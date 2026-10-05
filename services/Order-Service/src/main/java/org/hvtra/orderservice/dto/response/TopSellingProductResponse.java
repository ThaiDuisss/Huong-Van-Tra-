package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopSellingProductResponse {
    private int rank;
    private UUID productVariantId;
    private String sku;
    private String productName;
    private String variantName;
    /** Số lượng bán gộp (chưa trừ hàng trả). */
    private BigDecimal quantitySold;
    /** Doanh thu gộp: tổng lineTotal của các dòng hàng (sau giảm giá dòng, trước giảm giá cấp đơn). */
    private BigDecimal revenue;
    private long orderCount;
    /** Số lượng khách đã trả và được hoàn tiền, tính trên các đơn bán trong kỳ. */
    private BigDecimal returnedQuantity;
}
