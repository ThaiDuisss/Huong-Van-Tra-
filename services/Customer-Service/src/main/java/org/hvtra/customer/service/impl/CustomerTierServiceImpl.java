package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerTierCountResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerTierStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.CustomerTierService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerTierServiceImpl implements CustomerTierService {
    private final CustomerTierRepository customerTierRepository;
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTierResponse> getAll() {
        return customerTierRepository.findAllByIsDeletedFalse().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTierCountResponse> getCounts() {
        List<CustomerTierCountResponse> tierCounts = customerTierRepository
                .findAllByStatusAndIsDeletedFalseOrderByMinSpendingDesc(CustomerTierStatus.ACTIVE)
                .stream()
                .map(tier -> new CustomerTierCountResponse(
                        tier.getId(),
                        tier.getCode(),
                        tier.getName(),
                        tier.getColorCode(),
                        customerRepository.countByTier_IdAndIsDeletedFalse(tier.getId())
                ))
                .toList();

        List<CustomerTierCountResponse> result = new java.util.ArrayList<>();
        result.add(new CustomerTierCountResponse(
                null,
                "ALL",
                "Tất cả",
                null,
                customerRepository.countByIsDeletedFalse()
        ));
        result.addAll(tierCounts);
        result.add(new CustomerTierCountResponse(
                null,
                "UNASSIGNED",
                "Chưa phân hạng",
                null,
                customerRepository.countByTierIsNullAndIsDeletedFalse()
        ));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerTierResponse getById(UUID id) {
        return customerMapper.toResponse(findTier(id));
    }

    @Override
    @Transactional
    public CustomerTierResponse create(CustomerTierRequest request) {
        ensureCodeAvailable(request.code(), null);
        CustomerTier tier = new CustomerTier();
        customerMapper.update(tier, request);
        return customerMapper.toResponse(customerTierRepository.save(tier));
    }

    @Override
    @Transactional
    public CustomerTierResponse update(UUID id, CustomerTierRequest request) {
        CustomerTier tier = findTier(id);
        ensureCodeAvailable(request.code(), id);
        customerMapper.update(tier, request);
        return customerMapper.toResponse(customerTierRepository.save(tier));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomerTier tier = findTier(id);
        tier.setIsDeleted(true);
        customerTierRepository.save(tier);
    }

    //======================================= Helper classes =======================================

    private CustomerTier findTier(UUID id) {
        return customerTierRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_TIER_NOT_FOUND));
    }

    private void ensureCodeAvailable(String code, UUID currentId) {
        boolean exists = currentId == null
                ? customerTierRepository.existsByCode(code)
                : customerTierRepository.existsByCodeAndIdNot(code, currentId);
        if (exists) {
            throw conflict(Message.CUSTOMER_TIER_CODE_EXISTS);
        }
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private CustomerServiceException conflict(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_CONFLICT, message, HttpStatus.CONFLICT);
    }
}
