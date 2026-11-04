package org.hvtra.orderservice.dto.response;

import org.hvtra.orderservice.domain.enums.OrderImportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Kết quả kiểm tra / nhập của một đơn (gộp từ nhiều dòng cùng mã tham chiếu). */
public record OrderImportOrderResult(
        String orderReference,
        List<Integer> rowNumbers,
        OrderImportStatus status,
        List<String> messages,
        Instant soldAt,
        String customerName,
        String customerPhone,
        int itemCount,
        BigDecimal totalQuantity,
        /** Tạm tính theo đơn giá trong file hoặc giá bán hiện tại; thành tiền thật lấy từ đơn đã tạo. */
        BigDecimal totalAmount,
        UUID orderId,
        String orderCode
) {
}
