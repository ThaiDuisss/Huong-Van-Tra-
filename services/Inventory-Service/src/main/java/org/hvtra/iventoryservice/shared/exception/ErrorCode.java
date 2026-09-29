package org.hvtra.iventoryservice.shared.exception;

import lombok.Getter;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mã lỗi nghiệp vụ tập trung của Inventory-Service (dải 10xxx). Ném bằng {@code throw ErrorCode.X.exception()};
 * {@link GlobalException} trả về đúng {@link #httpStatus} kèm {@code code}/{@code message} trong body.
 */
@Getter
public enum ErrorCode {

    // ===== Phiếu nhập kho (inventory_documents) =====
    INVENTORY_DOCUMENT_NOT_FOUND(10001, "Phiếu nhập kho không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
    INVENTORY_DOCUMENT_NOT_EDITABLE(10002, "Chỉ có thể chỉnh sửa phiếu nhập kho ở trạng thái Bản nháp hoặc Từ chối", HttpStatus.BAD_REQUEST),
    INVENTORY_DOCUMENT_EDIT_FORBIDDEN(10003, "Chỉ ADMIN hoặc người tạo phiếu mới được chỉnh sửa phiếu nhập kho này", HttpStatus.FORBIDDEN),
    INVENTORY_DOCUMENT_NOT_DELETABLE(10004, "Chỉ có thể xóa phiếu nhập kho ở trạng thái Bản nháp", HttpStatus.BAD_REQUEST),
    INVENTORY_DOCUMENT_DELETE_FORBIDDEN(10005, "Chỉ ADMIN hoặc người tạo phiếu mới được xóa phiếu nhập kho này", HttpStatus.FORBIDDEN),

    // ===== Danh mục kho =====
    INVENTORY_LOCATION_NOT_FOUND(10101, "Kho nhận không tồn tại", HttpStatus.NOT_FOUND),
    INVENTORY_ITEM_NOT_FOUND(10102, "Nguyên liệu không tồn tại trong kho", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public AppException exception() {
        return new AppException(code, message);
    }

    public static Optional<ErrorCode> fromCode(int code) {
        return Arrays.stream(values()).filter(errorCode -> errorCode.code == code).findFirst();
    }
}
