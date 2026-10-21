package org.hvtra.iventoryservice.availability;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Giá trị mặc định khi chưa có lịch sử để suy ra thời gian sản xuất / nhập hàng. */
@Getter
@Setter
@ConfigurationProperties(prefix = "inventory.availability")
public class AvailabilityProperties {

    /** Số ngày sản xuất cộng thêm khi chưa có lệnh sản xuất nào hoàn tất để tham chiếu. */
    private int defaultProductionDays = 1;

    /** Số ngày nhập nguyên liệu khi chưa có phiếu nhập nào đã ghi sổ để tham chiếu. */
    private int defaultReceiptLeadDays = 3;

    /** Số lệnh / phiếu gần nhất dùng để tính trung bình. */
    private int historySampleSize = 20;
}
