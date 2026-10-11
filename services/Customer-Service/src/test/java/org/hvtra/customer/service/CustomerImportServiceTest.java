package org.hvtra.customer.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.impl.CustomerImportServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerImportServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private CustomerRepository customerRepository;
    private CustomerService customerService;
    private CustomerImportServiceImpl importService;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        customerService = mock(CustomerService.class);
        importService = new CustomerImportServiceImpl(customerRepository, customerService, validator);
        when(customerService.create(any(CustomerRequest.class))).thenAnswer(invocation -> {
            CustomerRequest request = invocation.getArgument(0);
            return response(request);
        });
    }

    @Test
    void importsValidRowsAndNormalizesValues() {
        CustomerImportResponse response = importService.importCustomers(request(false,
                row(2, null, "  Nguyễn Văn A ", "090 123-4567", " a@example.com ", "15/08/1990", "Doanh nghiệp"),
                row(3, "KH-0002", "Trần Thị B", "+84912345678", null, "1995-01-31", null)
        ));

        assertThat(response.dryRun()).isFalse();
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results()).extracting(CustomerImportRowResult::status)
                .containsExactly(CustomerImportRowStatus.IMPORTED, CustomerImportRowStatus.IMPORTED);
        assertThat(response.results()).extracting(CustomerImportRowResult::rowNumber).containsExactly(2, 3);
        assertThat(response.results().get(0).customerId()).isNotNull();

        ArgumentCaptor<CustomerRequest> captor = ArgumentCaptor.forClass(CustomerRequest.class);
        verify(customerService, times(2)).create(captor.capture());
        CustomerRequest first = captor.getAllValues().get(0);
        assertThat(first.customerCode()).isNull();
        assertThat(first.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(first.phone()).isEqualTo("0901234567");
        assertThat(first.email()).isEqualTo("a@example.com");
        assertThat(first.dateOfBirth()).isEqualTo(LocalDate.of(1990, 8, 15));
        assertThat(first.customerType()).isEqualTo(CustomerType.BUSINESS);
        assertThat(first.status()).isNull();
        assertThat(first.tierId()).isNull();
        assertThat(first.totalSpent()).isNull();
        CustomerRequest second = captor.getAllValues().get(1);
        assertThat(second.customerCode()).isEqualTo("KH-0002");
        assertThat(second.dateOfBirth()).isEqualTo(LocalDate.of(1995, 1, 31));
        assertThat(second.customerType()).isEqualTo(CustomerType.INDIVIDUAL);
    }

    @Test
    void dryRunValidatesWithoutCreating() {
        CustomerImportResponse response = importService.importCustomers(request(true,
                row(2, null, "Nguyễn Văn A", "0901234567", null, null, null),
                row(3, null, "", "0901234568", null, null, null)
        ));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results()).extracting(CustomerImportRowResult::status)
                .containsExactly(CustomerImportRowStatus.VALID, CustomerImportRowStatus.INVALID);
        verify(customerService, never()).create(any());
    }

    @Test
    void reportsRequiredAndFormatErrorsPerRow() {
        CustomerImportResponse response = importService.importCustomers(request(false,
                row(2, null, null, "0901234567", null, null, null),
                row(3, null, "Thiếu số", "  ", null, null, null),
                row(4, null, "Sai số", "09abc", null, null, null),
                row(5, null, "Sai email", "0901234568", "not-an-email", null, null),
                row(6, null, "Sai ngày", "0901234569", null, "31/02/2000", null),
                row(7, null, "Ngày tương lai", "0901234570", null, LocalDate.now().plusDays(1).toString(), null),
                row(8, null, "Sai loại", "0901234571", null, null, "VIP"),
                row(9, null, "x".repeat(151), "0901234572", null, null, null),
                row(10, null, null, null, null, null, null)
        ));

        assertThat(response.successCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(9);
        assertThat(response.results()).allMatch(result -> result.status() == CustomerImportRowStatus.INVALID);
        assertThat(messageOf(response, 2)).contains("Tên khách hàng là bắt buộc");
        assertThat(messageOf(response, 3)).contains("Số điện thoại là bắt buộc");
        assertThat(messageOf(response, 4)).contains(Message.CUSTOMER_IMPORT_PHONE_INVALID);
        assertThat(messageOf(response, 5)).contains(Message.CUSTOMER_IMPORT_EMAIL_INVALID);
        assertThat(messageOf(response, 6)).contains(Message.CUSTOMER_IMPORT_DOB_INVALID);
        assertThat(messageOf(response, 7)).contains(Message.CUSTOMER_IMPORT_DOB_FUTURE);
        assertThat(messageOf(response, 8)).contains(Message.CUSTOMER_IMPORT_TYPE_INVALID);
        assertThat(messageOf(response, 9)).contains("Tên khách hàng vượt quá 150 ký tự");
        assertThat(messageOf(response, 10)).isEqualTo(Message.CUSTOMER_IMPORT_EMPTY_ROW);
        verify(customerService, never()).create(any());
    }

    @Test
    void flagsDuplicatesInsideFileButKeepsFirstOccurrence() {
        CustomerImportResponse response = importService.importCustomers(request(false,
                row(2, "KH-1", "A", "0901234567", "Dup@Example.com", null, null),
                row(3, null, "B", "090.123.4567", null, null, null),
                row(4, null, "C", "0901234568", "dup@example.com", null, null),
                row(5, "kh-1", "D", "0901234569", null, null, null)
        ));

        assertThat(response.results()).extracting(CustomerImportRowResult::status).containsExactly(
                CustomerImportRowStatus.IMPORTED,
                CustomerImportRowStatus.INVALID,
                CustomerImportRowStatus.INVALID,
                CustomerImportRowStatus.INVALID
        );
        assertThat(messageOf(response, 3)).isEqualTo("Số điện thoại trùng với dòng 2 trong file");
        assertThat(messageOf(response, 4)).isEqualTo("Email trùng với dòng 2 trong file");
        assertThat(messageOf(response, 5)).isEqualTo("Mã khách hàng trùng với dòng 2 trong file");
        verify(customerService, times(1)).create(any());
    }

    @Test
    void flagsDuplicatesAgainstDatabase() {
        when(customerRepository.findExistingPhones(anyCollection())).thenReturn(List.of("0901234567"));
        when(customerRepository.findExistingEmailsLowerCase(anyCollection())).thenReturn(List.of("b@example.com"));
        when(customerRepository.findExistingCustomerCodesLowerCase(anyCollection())).thenReturn(List.of("kh-3"));

        CustomerImportResponse response = importService.importCustomers(request(false,
                row(2, null, "A", "0901234567", null, null, null),
                row(3, null, "B", "0901234568", "B@example.com", null, null),
                row(4, "KH-3", "C", "0901234569", null, null, null),
                row(5, null, "D", "0901234570", null, null, null)
        ));

        assertThat(messageOf(response, 2)).isEqualTo(Message.CUSTOMER_PHONE_EXISTS);
        assertThat(messageOf(response, 3)).isEqualTo(Message.CUSTOMER_EMAIL_EXISTS);
        assertThat(messageOf(response, 4)).isEqualTo(Message.CUSTOMER_CODE_EXISTS);
        assertThat(response.results().get(3).status()).isEqualTo(CustomerImportRowStatus.IMPORTED);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(3);
    }

    @Test
    void oneFailingCreateDoesNotStopRemainingRows() {
        when(customerService.create(any(CustomerRequest.class))).thenAnswer(invocation -> {
            CustomerRequest request = invocation.getArgument(0);
            if (request.phone().equals("0901234567")) {
                throw new CustomerServiceException(
                        ErrorCode.CUSTOMER_CONFLICT, Message.CUSTOMER_PHONE_EXISTS, HttpStatus.CONFLICT);
            }
            if (request.phone().equals("0901234568")) {
                throw new IllegalStateException("db down");
            }
            return response(request);
        });

        CustomerImportResponse response = importService.importCustomers(request(false,
                row(2, null, "A", "0901234567", null, null, null),
                row(3, null, "B", "0901234568", null, null, null),
                row(4, null, "C", "0901234569", null, null, null)
        ));

        assertThat(response.results()).extracting(CustomerImportRowResult::status).containsExactly(
                CustomerImportRowStatus.FAILED,
                CustomerImportRowStatus.FAILED,
                CustomerImportRowStatus.IMPORTED
        );
        assertThat(messageOf(response, 2)).isEqualTo(Message.CUSTOMER_PHONE_EXISTS);
        assertThat(messageOf(response, 3)).isEqualTo(Message.CUSTOMER_IMPORT_ROW_FAILED);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(2);
    }

    @Test
    void usesPositionWhenRowNumberIsMissing() {
        CustomerImportResponse response = importService.importCustomers(request(true,
                row(null, null, "A", "0901234567", null, null, null),
                row(null, null, "B", "0901234568", null, null, null)
        ));

        assertThat(response.results()).extracting(CustomerImportRowResult::rowNumber).containsExactly(2, 3);
    }

    @Test
    void rejectsEmptyAndOversizedRequests() {
        assertThatThrownBy(() -> importService.importCustomers(new CustomerImportRequest(List.of(), false)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_IMPORT_EMPTY);

        List<CustomerImportRowRequest> rows = new ArrayList<>();
        for (int index = 0; index <= CustomerImportRequest.MAX_ROWS; index++) {
            rows.add(row(index + 2, null, "KH " + index, "09" + String.format("%08d", index), null, null, null));
        }
        assertThatThrownBy(() -> importService.importCustomers(new CustomerImportRequest(rows, false)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessageContaining(String.valueOf(CustomerImportRequest.MAX_ROWS));
        verifyNoInteractions(customerService);
        verifyNoInteractions(customerRepository);
    }

    @Test
    void requestDtoCapsRowCount() {
        List<CustomerImportRowRequest> rows = new ArrayList<>();
        for (int index = 0; index <= CustomerImportRequest.MAX_ROWS; index++) {
            rows.add(row(index + 2, null, "KH", "0901234567", null, null, null));
        }

        assertThat(validator.validate(new CustomerImportRequest(rows, false))).hasSize(1);
        assertThat(validator.validate(new CustomerImportRequest(List.of(), false))).hasSize(1);
        assertThat(validator.validate(new CustomerImportRequest(rows.subList(0, CustomerImportRequest.MAX_ROWS), false)))
                .isEmpty();
    }

    private static CustomerImportRequest request(boolean dryRun, CustomerImportRowRequest... rows) {
        return new CustomerImportRequest(Arrays.asList(rows), dryRun);
    }

    private static CustomerImportRowRequest row(
            Integer rowNumber,
            String code,
            String fullName,
            String phone,
            String email,
            String dateOfBirth,
            String customerType
    ) {
        return new CustomerImportRowRequest(rowNumber, code, fullName, phone, email, dateOfBirth, customerType);
    }

    private static String messageOf(CustomerImportResponse response, int rowNumber) {
        return response.results().stream()
                .filter(result -> result.rowNumber() == rowNumber)
                .findFirst()
                .orElseThrow()
                .message();
    }

    private static CustomerResponse response(CustomerRequest request) {
        return new CustomerResponse(
                UUID.randomUUID(),
                request.customerCode() == null ? "AUTO-1234" : request.customerCode(),
                request.fullName(),
                request.phone(),
                request.email(),
                request.dateOfBirth(),
                request.customerType(),
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                0,
                CustomerStatus.ACTIVE,
                Instant.now()
        );
    }
}
