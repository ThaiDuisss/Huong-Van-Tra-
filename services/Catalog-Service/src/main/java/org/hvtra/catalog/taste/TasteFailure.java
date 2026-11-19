package org.hvtra.catalog.taste;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

final class TasteFailure extends BaseException {
    private TasteFailure(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    static TasteFailure invalid(String message) {
        return new TasteFailure("TASTE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    static TasteFailure conflict(String message) {
        return new TasteFailure("TASTE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    static TasteFailure notFound(String code, String message) {
        return new TasteFailure(code, message, HttpStatus.NOT_FOUND);
    }

    static long requireVersion(String value) {
        if (value == null) {
            throw new TasteFailure("TASTE_VERSION_REQUIRED", "Thiếu If-Match", HttpStatus.PRECONDITION_REQUIRED);
        }
        if (value.matches("\"(?:0|[1-9][0-9]{0,18})\"")) {
            try {
                return Long.parseLong(value.substring(1, value.length() - 1));
            } catch (NumberFormatException ignored) {
                // Versions must fit the signed Java value used by JPA.
            }
        }
        throw new TasteFailure("TASTE_VERSION_INVALID", "If-Match không hợp lệ", HttpStatus.BAD_REQUEST);
    }

    static void matchVersion(TasteRecord record, long version) {
        if (record.getRowVersion() != version) {
            throw new TasteFailure("TASTE_VERSION_MISMATCH", "Dữ liệu đã thay đổi", HttpStatus.PRECONDITION_FAILED);
        }
    }
}
