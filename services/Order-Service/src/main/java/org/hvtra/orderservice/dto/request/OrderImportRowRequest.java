package org.hvtra.orderservice.dto.request;

/**
 * Một dòng hàng trong file nhập đơn. Các trường số/ngày giữ dạng chuỗi để lỗi định dạng
 * được báo lại theo từng dòng thay vì làm hỏng cả request.
 */
public record OrderImportRowRequest(
        Integer rowNumber,
        String orderReference,
        /** yyyy-MM-dd'T'HH:mm, dd/MM/yyyy HH:mm hoặc dd/MM/yyyy (giờ Việt Nam). */
        String soldAt,
        String customerPhone,
        String customerName,
        String sku,
        String quantity,
        String unitPrice,
        String note
) {
}
