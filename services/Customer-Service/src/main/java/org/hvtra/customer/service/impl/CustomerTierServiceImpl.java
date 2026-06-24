package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.CacheName;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.CustomerTierService;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerTierServiceImpl implements CustomerTierService {
    private final CustomerTierRepository customerTierRepository;
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
    public CustomerTierResponse getById(UUID id) {
        return customerMapper.toResponse(findTier(id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CUSTOMER_FULL_LIST, allEntries = true)
    public CustomerTierResponse create(CustomerTierRequest request) {
        ensureCodeAvailable(request.code(), null);
        CustomerTier tier = new CustomerTier();
        customerMapper.update(tier, request);
        return customerMapper.toResponse(customerTierRepository.save(tier));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CUSTOMER_FULL_LIST, allEntries = true)
    public CustomerTierResponse update(UUID id, CustomerTierRequest request) {
        CustomerTier tier = findTier(id);
        ensureCodeAvailable(request.code(), id);
        customerMapper.update(tier, request);
        return customerMapper.toResponse(customerTierRepository.save(tier));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CUSTOMER_FULL_LIST, allEntries = true)
    public void delete(UUID id) {
        CustomerTier tier = findTier(id);
        tier.setIsDeleted(true);
        customerTierRepository.save(tier);
    }

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
