package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.ChangeCustomerStatusRequest;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.infrastructure.client.OrderClient;
import org.hvtra.customer.infrastructure.gateway.OrderGateway;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerType;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.*;
import org.hvtra.customer.specification.CustomerSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hvtra.commonservice.common_core.util.CodeUtils;

import static org.hvtra.customer.exception.CustomerServiceException.notFound;
import static org.hvtra.customer.exception.CustomerServiceException.conflict;
import static org.hvtra.customer.exception.CustomerServiceException.invalidRequest;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerTierRepository customerTierRepository;
    private final CustomerMapper customerMapper;
    private final LoyaltyAccountService loyaltyAccountService;
    private final LoyaltyPointRuleService loyaltyPointRuleService;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final OrderGateway orderGateway;
    private final TierQualificationService  tierQualificationService;
    private final TierAssignmentService  tierAssignmentService;
    private final LoyaltyTierService loyaltyTierService;

    // Tìm kiếm khách hàng theo bộ lọc.
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(
            CustomerFilterRequest filter,
            Pageable pageable) {
        return PageResponse.from(
                customerRepository.findAll(CustomerSpecification.from(filter), pageable)
                        .map(customerMapper::toResponse)
        );
    }

    // Lấy thông tin một khách hàng chưa bị xóa theo ID.
    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    // Chuẩn hóa, kiểm tra và tạo khách hàng mới.
    // Sau đó khởi tạo tài khoản tích điểm với số điểm ban đầu bằng 0.
    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        CustomerRequest normalizedRequest = normalizeCreateRequest(request);
        ensureUniqueFields(normalizedRequest, null);
        Customer customer = new Customer();

        CustomerTier initialTier = tierQualificationService.resolveQualifiedTier(BigDecimal.ZERO, 0);
        customerMapper.update(customer, normalizedRequest, initialTier);

        Customer savedCustomer = customerRepository.save(customer);
        tierAssignmentService.assignInitialTier(savedCustomer, initialTier, LocalDateTime.now());

        LoyaltyAccountRequest loyaltyAccountRequest = new LoyaltyAccountRequest(savedCustomer.getId(), 0);
        loyaltyAccountService.create(loyaltyAccountRequest);

        return customerMapper.toResponse(savedCustomer);
    }

    // Cập nhật khách hàng sau khi chuẩn hóa và kiểm tra tính duy nhất
    // của mã khách hàng, số điện thoại và email.
    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findCustomer(id);
        CustomerRequest normalizedRequest = normalizeUpdateRequest(request, customer);
        ensureUniqueFields(normalizedRequest, id);
        customerMapper.update(customer, normalizedRequest, findTier(normalizedRequest.tierId()));
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    // Cập nhật ngày mua gần nhất và tổng chi tiêu từ giá trị đơn hàng.
    // Sau đó tự động điều chỉnh hạng thành viên phù hợp.
    @Override
    @Transactional
    public CustomerResponse updatePurchase(UUID orderId) {
        OrderClient.OrderResponse res = orderGateway.getOrderById(orderId);

        if (res.customerId() == null || res.finalAmount() == null || res.finalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw invalidRequest("Thông tin đơn hàng không hợp lệ");
        }

        OrderCompletedEvent event = new OrderCompletedEvent(
                orderId,
                res.orderCode(),
                res.customerId(),
                res.finalAmount(),
                res.soldAt()
        );

        // recordPurchase đã cộng tổng chi tiêu, số đơn, ngày mua gần nhất và xét hạng; idempotent theo orderId.
        loyaltyTierService.recordPurchase(event);

        Customer cus = customerRepository.findByIdAndIsDeletedFalse(res.customerId())
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));

        UUID tierId = cus.getTier() == null ? null : cus.getTier().getId();
        LoyaltyPointRule pointRule = loyaltyPointRuleService.findApplicableRule(tierId, res.finalAmount(), Instant.now())
                .orElse(null);

        int point = pointRule != null ?

                (((res.finalAmount().multiply(BigDecimal.valueOf( pointRule.getPointsPerBlock()))).multiply(pointRule.getPointMultiplier())).divide(pointRule.getSpendAmountPerBlock())).intValue()
                : 0;

        boolean alreadyEarnedPoints = loyaltyTransactionRepository.existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.EARN,
                LoyaltyReferenceType.ORDER,
                orderId
        );

        if (point != 0 && !alreadyEarnedPoints){
            loyaltyAccountService.updateBalance(
                    new LoyaltyTransactionRequest(
                            loyaltyAccountService.getByCustomerId(res.customerId()).id(),
                            LoyaltyTransactionType.EARN,
                            point,
                            LoyaltyReferenceType.ORDER,
                            orderId
                            )
            );
        }

        // Không cộng lại totalSpent / totalOrderCount ở đây: recordPurchase đã làm, cộng thêm là nhân đôi doanh số.
        Customer customer = findCustomer(res.customerId());
        return customerMapper.toResponse(customer);
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

    //======================================= Helper classes =======================================

    // Tìm khách hàng chưa bị xóa hoặc ném lỗi nếu không tồn tại.
    private Customer findCustomer(UUID id) {
        return customerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
    }

    // Tìm hạng thành viên chưa bị xóa; cho phép không truyền hạng.
    private CustomerTier findTier(UUID tierId) {
        if (tierId == null) {
            return null;
        }
        return customerTierRepository.findByIdAndIsDeletedFalse(tierId)
                .orElseThrow(() -> notFound(Message.CUSTOMER_TIER_NOT_FOUND));
    }

    // Kiểm tra tính duy nhất của mã khách hàng, số điện thoại và email.
    private void ensureUniqueFields(CustomerRequest request, UUID currentId) {
        if (hasText(request.customerCode()) && existsCustomerCode(request.customerCode(), currentId)) {
            throw conflict(Message.CUSTOMER_CODE_EXISTS);
        }
        if (existsPhone(request.phone(), currentId)) {
            throw conflict(Message.CUSTOMER_PHONE_EXISTS);
        }
        if (request.email() != null && !request.email().isBlank() && existsEmail(request.email(), currentId)) {
            throw conflict(Message.CUSTOMER_EMAIL_EXISTS);
        }
    }

    // Kiểm tra mã khách hàng đã được sử dụng bởi khách hàng khác hay chưa.
    private boolean existsCustomerCode(String customerCode, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByCustomerCode(customerCode)
                : customerRepository.existsByCustomerCodeAndIdNot(customerCode, currentId);
    }

    // Kiểm tra số điện thoại đã được sử dụng bởi khách hàng khác hay chưa.
    private boolean existsPhone(String phone, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByPhone(phone)
                : customerRepository.existsByPhoneAndIdNot(phone, currentId);
    }

    // Kiểm tra email đã được sử dụng bởi khách hàng khác hay chưa.
    private boolean existsEmail(String email, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByEmail(email)
                : customerRepository.existsByEmailAndIdNot(email, currentId);
    }

    // Bổ sung mã khách hàng và trạng thái mặc định khi tạo mới nếu còn thiếu.
    private CustomerRequest normalizeCreateRequest(CustomerRequest request) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode() : generateCustomerCode(request.fullName()),
                request.fullName(),
                request.phone(),
                request.email(),
                request.dateOfBirth(),
                request.customerType() != null ? request.customerType() : CustomerType.INDIVIDUAL,
                request.tierId(),
                request.creditLimit(),
                request.lastPurchaseDate(),
                request.totalSpent(),
                request.totalOrderCount(),
                request.status() != null ? request.status() : CustomerStatus.ACTIVE
        );
    }

    // Giữ lại mã, trạng thái và số liệu mua hàng hiện tại khi dữ liệu cập nhật không cung cấp giá trị mới.
    private CustomerRequest normalizeUpdateRequest(CustomerRequest request, Customer customer) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode() : customer.getCustomerCode(),
                request.fullName(),
                request.phone(),
                request.email(),
                request.dateOfBirth(),
                request.customerType() != null ? request.customerType() : customer.getCustomerType(),
                request.tierId(),
                request.creditLimit(),
                request.lastPurchaseDate() != null ? request.lastPurchaseDate() : customer.getLastPurchaseDate(),
                request.totalSpent() != null ? request.totalSpent() : customer.getTotalSpent(),
                request.totalOrderCount() != null ? request.totalOrderCount() : customer.getTotalOrderCount(),
                request.status() != null ? request.status() : customer.getStatus()
        );
    }

    // Sinh mã khách hàng tự động: {Viết tắt họ tên}-{4 số ngẫu nhiên duy nhất}.
    // Ví dụ: NVA-1024, LTH-8492
    private String generateCustomerCode(String fullName) {
        String prefix = CodeUtils.getInitials(fullName, "KH");
        String customerCode;
        do {
            int randomNum = ThreadLocalRandom.current().nextInt(1000, 10000);
            customerCode = String.format("%s-%04d", prefix, randomNum);
        } while (customerRepository.existsByCustomerCode(customerCode));
        return customerCode;
    }

    // Kiểm tra chuỗi có giá trị và không chỉ chứa khoảng trắng.
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
