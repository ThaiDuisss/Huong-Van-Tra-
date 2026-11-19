package org.hvtra.customer.taste;

import org.hvtra.customer.exception.CustomerServiceException;
import org.springframework.http.HttpStatus;

final class TasteSurveyErrors {
    private TasteSurveyErrors() {
    }

    static CustomerServiceException invalid(String message) {
        return new CustomerServiceException("TASTE_SURVEY_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    static CustomerServiceException invalidCriteria(String message) {
        return new CustomerServiceException("TASTE_CRITERIA_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    static CustomerServiceException notFound() {
        return new CustomerServiceException("TASTE_SURVEY_NOT_FOUND", "Không tìm thấy khảo sát khẩu vị", HttpStatus.NOT_FOUND);
    }

    static CustomerServiceException conflict(String code, String message) {
        return new CustomerServiceException(code, message, HttpStatus.CONFLICT);
    }

    static CustomerServiceException stale() {
        return new CustomerServiceException("TASTE_SURVEY_VERSION_MISMATCH",
                "Khảo sát đã thay đổi. Vui lòng tải lại.", HttpStatus.PRECONDITION_FAILED);
    }

    static CustomerServiceException unavailableTaxonomy() {
        return new CustomerServiceException("TASTE_TAXONOMY_UNAVAILABLE",
                "Chưa xác minh được phiên bản tiêu chí khẩu vị từ Catalog", HttpStatus.SERVICE_UNAVAILABLE);
    }

    static CustomerServiceException unsupportedTaxonomy() {
        return conflict("TASTE_TAXONOMY_UNSUPPORTED", "Phiên bản tiêu chí khẩu vị không còn được hỗ trợ");
    }

    static CustomerServiceException expired() {
        return new CustomerServiceException("TASTE_SURVEY_EXPIRED", "Phiên khảo sát đã hết hạn", HttpStatus.GONE);
    }
}
