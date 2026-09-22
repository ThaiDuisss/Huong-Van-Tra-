package org.hvtra.commonservice.common_importing.handler;

import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mỗi loại import (PRODUCT, sau này PRODUCT_VARIANT, GOODS_RECEIPT...) triển khai
 * interface này. Engine chung ({@code ImportBatchService}) không biết gì về field
 * nghiệp vụ cụ thể — chỉ gọi qua 3 hàm dưới, để việc thêm loại import mới không cần
 * đụng vào engine.
 */
public interface ImportTypeHandler {
    ImportType type();

    /** Đọc file Excel, KHÔNG ghi DB — chỉ trả về danh sách dòng đã parse (có thể còn thiếu/sai). */
    List<org.hvtra.commonservice.common_importing.handler.ParsedImportRow> parseExcel(MultipartFile file);

    /** Validate lại 1 payload, trả về danh sách lỗi (rỗng nếu hợp lệ). */
    List<ValidationErrorResponse> validate(Map<String, Object> payload);

    /**
     * Validate nhiều payload cùng lúc (khi tạo batch từ Excel, hoặc duyệt cả batch) — mặc định gọi
     * {@link #validate} lặp lại từng cái, nhưng handler nào có tra cứu DB theo id (productId, unitId,
     * ingredientId...) NÊN override để gộp thành vài query {@code findAllById}/tập hợp id thay vì
     * N query {@code existsById} riêng lẻ (tránh N+1 khi file có hàng trăm/nghìn dòng).
     */
    default List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        return payloads.stream().map(this::validate).toList();
    }

    /** Ghi dữ liệu thật vào bảng nghiệp vụ (gọi service.create() hiện có), trả về id entity vừa tạo. */
    UUID commit(Map<String, Object> payload);
}
