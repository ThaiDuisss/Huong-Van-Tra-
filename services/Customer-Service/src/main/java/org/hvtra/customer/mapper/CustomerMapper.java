package org.hvtra.customer.mapper;

import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyPointRuleRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.*;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerAddress;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Component
public class CustomerMapper {
    public CustomerTierResponse toResponse(CustomerTier tier) {
        return new CustomerTierResponse(tier.getId(), tier.getCode(), tier.getName(),
                tier.getMinSpending(), tier.getMinOrderCount(), tier.getStatus(), tier.getColorCode());
    }

    public CustomerResponse toResponse(Customer customer) {
        CustomerTier tier = customer.getTier();
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getDateOfBirth(),
                customer.getCustomerType(),
                tier == null ? null : tier.getId(),
                tier == null ? null : tier.getCode(),
                tier == null ? null : tier.getName(),
                tier == null ? null : tier.getColorCode(),
                customer.getLastPurchaseDate(),
                customer.getTotalSpent(),
                customer.getTotalOrderCount(),
                customer.getStatus(),
                customer.getCreatedAt());
    }

    public CustomerAddressResponse toResponse(CustomerAddress address) {
        return new CustomerAddressResponse(address.getId(), address.getCustomer().getId(),
                address.getRecipientName(), address.getRecipientPhone(), address.getAddressLine(),
                address.getProvince(), address.getDistrict(), address.getWard(), address.getIsDefault());
    }

    public LoyaltyAccountResponse toResponse(LoyaltyAccount account) {
        Customer customer = account.getCustomer();
        return new LoyaltyAccountResponse(account.getId(), customer.getId(), customer.getCustomerCode(),
                customer.getFullName(), account.getPointBalance());
    }

    public LoyaltyTransactionResponse toResponse(LoyaltyTransaction transaction) {
        LoyaltyAccount account = transaction.getLoyaltyAccount();
        return new LoyaltyTransactionResponse(transaction.getId(), account.getId(), account.getCustomer().getId(),
                transaction.getType(), transaction.getPoints(), transaction.getReferenceType(),
                transaction.getReferenceId());
    }

    public LoyaltyPointRuleResponse toResponse(LoyaltyPointRule rule) {
        CustomerTier tier = rule.getTier();
        return new LoyaltyPointRuleResponse(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                tier == null ? null : tier.getId(),
                tier == null ? null : tier.getCode(),
                tier == null ? null : tier.getName(),
                rule.getSpendAmountPerBlock(),
                rule.getPointsPerBlock(),
                rule.getMinOrderAmount(),
                rule.getEarnAfterDiscount(),
                rule.getAllowCouponUsage(),
                rule.getPointMultiplier(),
                rule.getPriority(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.getStatus());
    }

    public CustomerTierYearlyMetricResponse toResponse(CustomerTierYearlyMetric metric) {
        return new CustomerTierYearlyMetricResponse(
                metric.getId(),
                metric.getCustomerId(),
                metric.getTierYear(),
                metric.getTotalSpent(),
                metric.getTotalOrderCount(),
                metric.getQualifiedTier() == null ? null : toResponse(metric.getQualifiedTier()),
                metric.getCalculatedAt()
        );
    }

    public CustomerTierAssignmentResponse toResponse(CustomerTierAssignment assignment) {
        return new CustomerTierAssignmentResponse(
                assignment.getId(),
                assignment.getCustomer().getId(),
                toResponse(assignment.getTier()),
                assignment.getQualificationYear(),
                assignment.getAssignmentReason(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveUntil(),
                assignment.getStatus(),
                assignment.getNote()
        );
    }

    public CustomerPurchaseTransactionResponse toResponse(CustomerPurchaseTransaction transaction) {
        return new CustomerPurchaseTransactionResponse(
                transaction.getId(),
                transaction.getCustomerId(),
                transaction.getOrderId(),
                transaction.getReferenceId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getOrderCountDelta(),
                transaction.getOccurredAt(),
                transaction.getReason(),
                transaction.getMetadata()
        );
    }

    public void update(CustomerTier tier, CustomerTierRequest request) {
        tier.setCode(request.code());
        tier.setName(request.name());
        tier.setMinSpending(zeroIfNull(request.minSpending()));
        tier.setMinOrderCount(zeroIfNull(request.minOrderCount()));
        tier.setStatus(request.status());
        tier.setColorCode(request.colorCode());
    }

    public void update(Customer customer, CustomerRequest request, CustomerTier tier) {
        customer.setCustomerCode(request.customerCode());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setCustomerType(request.customerType());
        customer.setTier(tier);
        customer.setLastPurchaseDate(request.lastPurchaseDate());
        customer.setTotalSpent(zeroIfNull(request.totalSpent()));
        customer.setTotalOrderCount(zeroIfNull(request.totalOrderCount()));
        customer.setStatus(request.status());
    }

    public void update(CustomerAddress address, CustomerAddressRequest request, Customer customer) {
        address.setCustomer(customer);
        address.setRecipientName(request.recipientName());
        address.setRecipientPhone(request.recipientPhone());
        address.setAddressLine(request.addressLine());
        address.setProvince(request.province());
        address.setDistrict(request.district());
        address.setWard(request.ward());
        address.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
    }

    public void update(LoyaltyAccount account, LoyaltyAccountRequest request, Customer customer) {
        account.setCustomer(customer);
        account.setPointBalance(request.pointBalance() == null ? 0 : request.pointBalance());
    }

    // Giá trị mặc định khớp với DEFAULT của bảng loyalty_point_rules (V7).
    public void update(LoyaltyPointRule rule, LoyaltyPointRuleRequest request, CustomerTier tier) {
        rule.setCode(request.code());
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setTier(tier);
        rule.setSpendAmountPerBlock(request.spendAmountPerBlock());
        rule.setPointsPerBlock(request.pointsPerBlock());
        rule.setMinOrderAmount(zeroIfNull(request.minOrderAmount()));
        rule.setEarnAfterDiscount(request.earnAfterDiscount() == null || request.earnAfterDiscount());
        rule.setAllowCouponUsage(request.allowCouponUsage() == null || request.allowCouponUsage());
        rule.setPointMultiplier(request.pointMultiplier() == null ? BigDecimal.ONE : request.pointMultiplier());
        rule.setPriority(request.priority() == null ? 100 : request.priority());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setStatus(request.status());
    }

    public void update(LoyaltyTransaction transaction, LoyaltyTransactionRequest request, LoyaltyAccount account) {
        transaction.setLoyaltyAccount(account);
        transaction.setType(request.type());
        transaction.setPoints(request.points());
        transaction.setReferenceType(request.referenceType());
        transaction.setReferenceId(request.referenceId());
    }
}
