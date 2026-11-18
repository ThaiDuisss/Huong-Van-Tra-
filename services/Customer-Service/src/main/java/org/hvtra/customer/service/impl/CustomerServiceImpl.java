package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.util.CodeUtils;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.ChangeCustomerStatusRequest;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerLookupResponse;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.CustomerService;
import org.hvtra.customer.service.TierQualificationService;
import org.hvtra.customer.specification.CustomerSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hvtra.customer.exception.CustomerServiceException.conflict;
import static org.hvtra.customer.exception.CustomerServiceException.notFound;
import static org.hvtra.customer.util.AmountUtils.nonNegative;
import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final TierQualificationService tierQualificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(CustomerFilterRequest filter, Pageable pageable) {
        return PageResponse.from(
                customerRepository.findAll(CustomerSpecification.from(filter), pageable)
                        .map(customerMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerLookupResponse> lookup(String keyword, int limit) {
        String normalized = hasText(keyword) ? keyword.trim() : null;
        return customerRepository.lookup(normalized, CustomerStatus.ACTIVE, PageRequest.of(0, limit))
                .stream()
                .map(customerMapper::toLookupResponse)
                .toList();
    }

    // Chi tiết khách kèm hạng kế tiếp và phần chi tiêu / số đơn còn thiếu để lên hạng.
    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer customer = findCustomer(id);
        CustomerTier nextTier = tierQualificationService.findNextTier(customer).orElse(null);
        if (nextTier == null) {
            return customerMapper.toResponse(customer);
        }
        BigDecimal remainingSpending = nonNegative(
                zeroIfNull(nextTier.getMinSpending()).subtract(zeroIfNull(customer.getTotalSpent()))
        );
        int remainingOrderCount = Math.max(
                zeroIfNull(nextTier.getMinOrderCount()) - zeroIfNull(customer.getTotalOrderCount()),
                0
        );
        return customerMapper.toResponse(customer, nextTier, remainingSpending, remainingOrderCount);
    }

    // Tạo khách mới với chi tiêu, số đơn và điểm bằng 0; hạng khởi đầu là hạng cao nhất khách đạt (thường là hạng thấp nhất).
    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        CustomerRequest normalizedRequest = normalizeCreateRequest(request);
        ensureUniqueFields(normalizedRequest, null);

        Customer customer = new Customer();
        customerMapper.updateProfile(customer, normalizedRequest);
        tierQualificationService.upgradeIfQualified(customer);

        return customerMapper.toResponse(customerRepository.save(customer));
    }

    // Chỉ cập nhật hồ sơ; hạng, chi tiêu, số đơn và điểm giữ nguyên.
    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findCustomer(id);
        CustomerRequest normalizedRequest = normalizeUpdateRequest(request, customer);
        ensureUniqueFields(normalizedRequest, id);
        customerMapper.updateProfile(customer, normalizedRequest);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    // Đổi trạng thái khách hàng (kích hoạt / ngừng hoạt động / tạm khóa) mà không xóa dữ liệu.
    @Override
    @Transactional
    public CustomerResponse changeStatus(UUID id, ChangeCustomerStatusRequest request) {
        Customer customer = findCustomer(id);
        customer.setStatus(request.status());
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    // Xóa mềm khách hàng.
    @Override
    @Transactional
    public void delete(UUID id) {
        Customer customer = findCustomer(id);
        customer.setIsDeleted(true);
        customerRepository.save(customer);
    }

    //======================================= Helpers =======================================

    private Customer findCustomer(UUID id) {
        return customerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
    }

    // Kiểm tra tính duy nhất của mã khách hàng, số điện thoại và email.
    private void ensureUniqueFields(CustomerRequest request, UUID currentId) {
        if (hasText(request.customerCode()) && existsCustomerCode(request.customerCode(), currentId)) {
            throw conflict(Message.CUSTOMER_CODE_EXISTS);
        }
        if (existsPhone(request.phone(), currentId)) {
            throw conflict(Message.CUSTOMER_PHONE_EXISTS);
        }
        if (hasText(request.email()) && existsEmail(request.email(), currentId)) {
            throw conflict(Message.CUSTOMER_EMAIL_EXISTS);
        }
    }

    private boolean existsCustomerCode(String customerCode, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByCustomerCode(customerCode)
                : customerRepository.existsByCustomerCodeAndIdNot(customerCode, currentId);
    }

    private boolean existsPhone(String phone, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByPhone(phone)
                : customerRepository.existsByPhoneAndIdNot(phone, currentId);
    }

    private boolean existsEmail(String email, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByEmail(email)
                : customerRepository.existsByEmailAndIdNot(email, currentId);
    }

    // Bổ sung mã khách hàng, chuẩn hóa email rỗng và trạng thái mặc định khi tạo mới.
    private CustomerRequest normalizeCreateRequest(CustomerRequest request) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode().trim() : generateCustomerCode(request.fullName()),
                request.fullName().trim(),
                request.phone().trim(),
                hasText(request.email()) ? request.email().trim() : null,
                request.dateOfBirth(),
                request.status() != null ? request.status() : CustomerStatus.ACTIVE
        );
    }

    // Giữ lại mã và trạng thái hiện tại khi request không gửi giá trị mới.
    private CustomerRequest normalizeUpdateRequest(CustomerRequest request, Customer customer) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode().trim() : customer.getCustomerCode(),
                request.fullName().trim(),
                request.phone().trim(),
                hasText(request.email()) ? request.email().trim() : null,
                request.dateOfBirth(),
                request.status() != null ? request.status() : customer.getStatus()
        );
    }

    // Sinh mã khách hàng tự động: {Viết tắt họ tên}-{4 số ngẫu nhiên duy nhất}. Ví dụ: NVA-1024.
    private String generateCustomerCode(String fullName) {
        String prefix = CodeUtils.getInitials(fullName, "KH");
        String customerCode;
        do {
            int randomNum = ThreadLocalRandom.current().nextInt(1000, 10000);
            customerCode = String.format("%s-%04d", prefix, randomNum);
        } while (customerRepository.existsByCustomerCode(customerCode));
        return customerCode;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
