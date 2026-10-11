package org.hvtra.customer.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerImportRequest;
import org.hvtra.customer.dto.request.CustomerImportRowRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerImportResponse;
import org.hvtra.customer.dto.response.CustomerImportRowResult;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.model.enums.CustomerImportRowStatus;
import org.hvtra.customer.model.enums.CustomerType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.CustomerImportService;
import org.hvtra.customer.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nhập khách hàng hàng loạt từ dữ liệu đã được client đọc ra từ file Excel.
 *
 * <p>Cố ý KHÔNG đặt @Transactional ở đây: mỗi dòng hợp lệ được tạo qua
 * {@link CustomerService#create(CustomerRequest)} trong transaction riêng, nên một dòng lưu lỗi
 * (ví dụ trùng do thao tác đồng thời) không làm hỏng các dòng khác và luôn được báo lại theo dòng.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerImportServiceImpl implements CustomerImportService {

    private static final int FIRST_DATA_ROW = 2;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{8,15}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final DateTimeFormatter VN_DATE =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "customerCode", "Mã khách hàng",
            "fullName", "Tên khách hàng",
            "phone", "Số điện thoại",
            "email", "Email"
    );

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final Validator validator;

    @Override
    public CustomerImportResponse importCustomers(CustomerImportRequest request) {
        List<CustomerImportRowRequest> rows = request == null ? null : request.rows();
        if (rows == null || rows.isEmpty()) {
            throw invalidRequest(Message.CUSTOMER_IMPORT_EMPTY);
        }
        if (rows.size() > CustomerImportRequest.MAX_ROWS) {
            throw invalidRequest(Message.CUSTOMER_IMPORT_TOO_MANY_ROWS.formatted(CustomerImportRequest.MAX_ROWS));
        }
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());

        List<Candidate> candidates = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            candidates.add(parse(rows.get(index), index));
        }
        flagDuplicatesInFile(candidates);
        flagDuplicatesInDatabase(candidates);

        List<CustomerImportRowResult> results = new ArrayList<>(candidates.size());
        int successCount = 0;
        for (Candidate candidate : candidates) {
            CustomerImportRowResult result = candidate.errors.isEmpty()
                    ? (dryRun ? valid(candidate) : create(candidate))
                    : failure(candidate, CustomerImportRowStatus.INVALID, String.join("; ", candidate.errors));
            if (result.status() == CustomerImportRowStatus.VALID
                    || result.status() == CustomerImportRowStatus.IMPORTED) {
                successCount++;
            }
            results.add(result);
        }
        return new CustomerImportResponse(
                dryRun,
                results.size(),
                successCount,
                results.size() - successCount,
                results
        );
    }

    // ======================================= Parse & validate từng dòng =======================================

    private Candidate parse(CustomerImportRowRequest row, int index) {
        Candidate candidate = new Candidate();
        candidate.rowNumber = row == null || row.rowNumber() == null ? index + FIRST_DATA_ROW : row.rowNumber();
        if (row == null || isBlankRow(row)) {
            candidate.errors.add(Message.CUSTOMER_IMPORT_EMPTY_ROW);
            return candidate;
        }

        candidate.customerCode = trimToNull(row.customerCode());
        candidate.fullName = trimToNull(row.fullName());
        candidate.phone = normalizePhone(row.phone());
        candidate.email = trimToNull(row.email());
        candidate.dateOfBirth = parseDateOfBirth(row.dateOfBirth(), candidate.errors);
        candidate.customerType = parseCustomerType(row.customerType(), candidate.errors);

        // Cùng bộ ràng buộc với API tạo đơn lẻ (CustomerRequest).
        for (ConstraintViolation<CustomerRequest> violation : validator.validate(candidate.toRequest())) {
            candidate.errors.add(describe(violation));
        }
        if (candidate.phone != null && !PHONE_PATTERN.matcher(candidate.phone).matches()) {
            candidate.errors.add(Message.CUSTOMER_IMPORT_PHONE_INVALID);
        }
        return candidate;
    }

    private boolean isBlankRow(CustomerImportRowRequest row) {
        return trimToNull(row.customerCode()) == null
                && trimToNull(row.fullName()) == null
                && trimToNull(row.phone()) == null
                && trimToNull(row.email()) == null
                && trimToNull(row.dateOfBirth()) == null
                && trimToNull(row.customerType()) == null;
    }

    private LocalDate parseDateOfBirth(String raw, List<String> errors) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        LocalDate date;
        try {
            date = value.contains("/") ? LocalDate.parse(value, VN_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            errors.add(Message.CUSTOMER_IMPORT_DOB_INVALID);
            return null;
        }
        if (date.isAfter(LocalDate.now())) {
            errors.add(Message.CUSTOMER_IMPORT_DOB_FUTURE);
            return null;
        }
        return date;
    }

    private CustomerType parseCustomerType(String raw, List<String> errors) {
        String value = trimToNull(raw);
        if (value == null) {
            return CustomerType.INDIVIDUAL;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "individual", "cá nhân", "ca nhan" -> CustomerType.INDIVIDUAL;
            case "business", "doanh nghiệp", "doanh nghiep" -> CustomerType.BUSINESS;
            default -> {
                errors.add(Message.CUSTOMER_IMPORT_TYPE_INVALID);
                yield CustomerType.INDIVIDUAL;
            }
        };
    }

    private String describe(ConstraintViolation<CustomerRequest> violation) {
        String field = violation.getPropertyPath().toString();
        String label = FIELD_LABELS.getOrDefault(field, field);
        Class<? extends Annotation> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
        if (constraint == NotBlank.class) {
            return label + " là bắt buộc";
        }
        if (constraint == Size.class) {
            return label + " vượt quá " + violation.getConstraintDescriptor().getAttributes().get("max") + " ký tự";
        }
        if (constraint == Email.class) {
            return Message.CUSTOMER_IMPORT_EMAIL_INVALID;
        }
        return label + ": " + violation.getMessage();
    }

    // ======================================= Kiểm tra trùng =======================================

    private void flagDuplicatesInFile(List<Candidate> candidates) {
        Map<String, Integer> phones = new HashMap<>();
        Map<String, Integer> emails = new HashMap<>();
        Map<String, Integer> codes = new HashMap<>();
        for (Candidate candidate : candidates) {
            flagDuplicateInFile(candidate, phones, candidate.phone, "Số điện thoại");
            flagDuplicateInFile(candidate, emails, lower(candidate.email), "Email");
            flagDuplicateInFile(candidate, codes, lower(candidate.customerCode), "Mã khách hàng");
        }
    }

    private void flagDuplicateInFile(Candidate candidate, Map<String, Integer> seen, String key, String label) {
        if (key == null) {
            return;
        }
        Integer firstRow = seen.putIfAbsent(key, candidate.rowNumber);
        if (firstRow != null) {
            candidate.errors.add(Message.CUSTOMER_IMPORT_DUPLICATE_IN_FILE.formatted(label, firstRow));
        }
    }

    private void flagDuplicatesInDatabase(List<Candidate> candidates) {
        Set<String> phones = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (Candidate candidate : candidates) {
            addIfPresent(phones, candidate.phone);
            addIfPresent(emails, lower(candidate.email));
            addIfPresent(codes, lower(candidate.customerCode));
        }
        Set<String> existingPhones = phones.isEmpty()
                ? Set.of() : new HashSet<>(customerRepository.findExistingPhones(phones));
        Set<String> existingEmails = emails.isEmpty()
                ? Set.of() : new HashSet<>(customerRepository.findExistingEmailsLowerCase(emails));
        Set<String> existingCodes = codes.isEmpty()
                ? Set.of() : new HashSet<>(customerRepository.findExistingCustomerCodesLowerCase(codes));

        for (Candidate candidate : candidates) {
            if (candidate.phone != null && existingPhones.contains(candidate.phone)) {
                candidate.errors.add(Message.CUSTOMER_PHONE_EXISTS);
            }
            if (candidate.email != null && existingEmails.contains(lower(candidate.email))) {
                candidate.errors.add(Message.CUSTOMER_EMAIL_EXISTS);
            }
            if (candidate.customerCode != null && existingCodes.contains(lower(candidate.customerCode))) {
                candidate.errors.add(Message.CUSTOMER_CODE_EXISTS);
            }
        }
    }

    // ======================================= Kết quả từng dòng =======================================

    private CustomerImportRowResult valid(Candidate candidate) {
        return new CustomerImportRowResult(
                candidate.rowNumber,
                CustomerImportRowStatus.VALID,
                Message.CUSTOMER_IMPORT_ROW_VALID,
                null,
                candidate.customerCode,
                candidate.fullName,
                candidate.phone
        );
    }

    private CustomerImportRowResult create(Candidate candidate) {
        try {
            CustomerResponse created = customerService.create(candidate.toRequest());
            return new CustomerImportRowResult(
                    candidate.rowNumber,
                    CustomerImportRowStatus.IMPORTED,
                    Message.CUSTOMER_IMPORT_ROW_IMPORTED,
                    created.id(),
                    created.customerCode(),
                    created.fullName(),
                    created.phone()
            );
        } catch (CustomerServiceException exception) {
            return failure(candidate, CustomerImportRowStatus.FAILED, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Không thể nhập khách hàng ở dòng {}: {}", candidate.rowNumber, exception.getMessage(), exception);
            return failure(candidate, CustomerImportRowStatus.FAILED, Message.CUSTOMER_IMPORT_ROW_FAILED);
        }
    }

    private CustomerImportRowResult failure(Candidate candidate, CustomerImportRowStatus status, String message) {
        return new CustomerImportRowResult(
                candidate.rowNumber,
                status,
                message,
                null,
                candidate.customerCode,
                candidate.fullName,
                candidate.phone
        );
    }

    // ======================================= Helpers =======================================

    private String normalizePhone(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : trimToNull(PHONE_SEPARATORS.matcher(value).replaceAll(""));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private void addIfPresent(Set<String> target, String value) {
        if (value != null) {
            target.add(value);
        }
    }

    private CustomerServiceException invalidRequest(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_INVALID_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    private static final class Candidate {
        private int rowNumber;
        private String customerCode;
        private String fullName;
        private String phone;
        private String email;
        private LocalDate dateOfBirth;
        private CustomerType customerType = CustomerType.INDIVIDUAL;
        private final List<String> errors = new ArrayList<>();

        private CustomerRequest toRequest() {
            return new CustomerRequest(
                    customerCode,
                    fullName,
                    phone,
                    email,
                    dateOfBirth,
                    Objects.requireNonNullElse(customerType, CustomerType.INDIVIDUAL),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
