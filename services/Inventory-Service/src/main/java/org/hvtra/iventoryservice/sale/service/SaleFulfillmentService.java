package org.hvtra.iventoryservice.sale.service;

import org.hvtra.iventoryservice.sale.dto.FulfillSaleRequest;
import org.hvtra.iventoryservice.sale.dto.FulfillSaleResponse;

public interface SaleFulfillmentService {
    /**
     * Trong một transaction: (1) kệ thiếu thì xuất kho lên kệ ngay bằng phiếu nhập kệ, manager duyệt sau;
     * (2) trừ thành phẩm trên kệ theo FEFO; (3) giữ chỗ nguyên liệu theo BOM cho việc sản xuất bù và cảnh báo
     * khi nguyên liệu chạm ngưỡng. Kệ + kho không đủ thành phẩm thì không ghi gì và báo lỗi.
     * Gọi lại cho cùng một đơn không trừ lần hai.
     */
    FulfillSaleResponse fulfill(FulfillSaleRequest request);
}
