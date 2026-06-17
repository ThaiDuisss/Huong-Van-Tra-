package org.hvtra.customer.mapper;

import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.CustomerAddressResponse;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerAddress;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CustomerMapper {
    public CustomerTierResponse toResponse(CustomerTier tier) {
        return new CustomerTierResponse(tier.getId(), tier.getCode(), tier.getName(),
                tier.getMinSpending(), tier.getDiscountRate(), tier.getStatus());
    }

    public CustomerResponse toResponse(Customer customer) {
        CustomerTier tier = customer.getTier();
        return new CustomerResponse(customer.getId(), customer.getCustomerCode(), customer.getFullName(),
                customer.getPhone(), customer.getEmail(), customer.getDateOfBirth(),
                tier == null ? null : tier.getId(), tier == null ? null : tier.getCode(),
                tier == null ? null : tier.getName(), customer.getCreditLimit(), customer.getStatus());
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

    public void update(CustomerTier tier, CustomerTierRequest request) {
        tier.setCode(request.code());
        tier.setName(request.name());
        tier.setMinSpending(defaultMoney(request.minSpending()));
        tier.setDiscountRate(defaultMoney(request.discountRate()));
        tier.setStatus(request.status());
    }

    public void update(Customer customer, CustomerRequest request, CustomerTier tier) {
        customer.setCustomerCode(request.customerCode());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setTier(tier);
        customer.setCreditLimit(defaultMoney(request.creditLimit()));
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

    public void update(LoyaltyTransaction transaction, LoyaltyTransactionRequest request, LoyaltyAccount account) {
        transaction.setLoyaltyAccount(account);
        transaction.setType(request.type());
        transaction.setPoints(request.points());
        transaction.setReferenceType(request.referenceType());
        transaction.setReferenceId(request.referenceId());
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
