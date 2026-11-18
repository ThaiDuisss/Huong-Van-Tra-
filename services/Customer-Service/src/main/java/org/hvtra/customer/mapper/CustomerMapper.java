package org.hvtra.customer.mapper;

import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerLookupResponse;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Component
public class CustomerMapper {

    public CustomerTierResponse toResponse(CustomerTier tier) {
        return new CustomerTierResponse(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getMinSpending(),
                tier.getMinOrderCount(),
                tier.getPointMultiplier(),
                tier.getStatus(),
                tier.getColorCode()
        );
    }

    /** Dùng cho danh sách: không kèm thông tin hạng kế tiếp. */
    public CustomerResponse toResponse(Customer customer) {
        return toResponse(customer, null, null, null);
    }

    /** Dùng cho chi tiết: kèm hạng kế tiếp và phần còn thiếu để lên hạng. */
    public CustomerResponse toResponse(
            Customer customer,
            CustomerTier nextTier,
            BigDecimal remainingSpending,
            Integer remainingOrderCount
    ) {
        CustomerTier tier = customer.getTier();
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getDateOfBirth(),
                tier == null ? null : tier.getId(),
                tier == null ? null : tier.getCode(),
                tier == null ? null : tier.getName(),
                tier == null ? null : tier.getColorCode(),
                customer.getLastPurchaseDate(),
                customer.getTotalSpent(),
                customer.getTotalOrderCount(),
                customer.getPointBalance(),
                customer.getStatus(),
                customer.getCreatedAt(),
                nextTier == null ? null : toResponse(nextTier),
                remainingSpending,
                remainingOrderCount
        );
    }

    public CustomerLookupResponse toLookupResponse(Customer customer) {
        return new CustomerLookupResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getCustomerCode(),
                customer.getPhone()
        );
    }

    public LoyaltyTransactionResponse toResponse(LoyaltyTransaction transaction) {
        return new LoyaltyTransactionResponse(
                transaction.getId(),
                transaction.getCustomer().getId(),
                transaction.getType(),
                transaction.getPoints(),
                transaction.getBalanceAfter(),
                transaction.getReferenceType(),
                transaction.getReferenceCode(),
                transaction.getReason(),
                transaction.getCreatedAt()
        );
    }

    public void update(CustomerTier tier, CustomerTierRequest request) {
        tier.setCode(request.code());
        tier.setName(request.name());
        tier.setMinSpending(zeroIfNull(request.minSpending()));
        tier.setMinOrderCount(zeroIfNull(request.minOrderCount()));
        tier.setPointMultiplier(request.pointMultiplier() == null ? BigDecimal.ONE : request.pointMultiplier());
        tier.setStatus(request.status());
        if (request.colorCode() != null && !request.colorCode().isBlank()) {
            tier.setColorCode(request.colorCode());
        } else if (tier.getColorCode() == null) {
            tier.setColorCode("#6B7280");
        }
    }

    /** Chỉ cập nhật thông tin hồ sơ; hạng, chi tiêu, số đơn và điểm do hệ thống quản lý. */
    public void updateProfile(Customer customer, CustomerRequest request) {
        customer.setCustomerCode(request.customerCode());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setStatus(request.status());
    }
}
