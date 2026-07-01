package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.response.CustomerAddressResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerAddress;
import org.hvtra.customer.repository.CustomerAddressRepository;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.CustomerAddressService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAddressServiceImpl implements CustomerAddressService {
    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> getAll(UUID customerId) {
        List<CustomerAddress> addresses = customerId == null
                ? customerAddressRepository.findAllByIsDeletedFalse()
                : customerAddressRepository.findAllByCustomerIdAndIsDeletedFalse(customerId);
        return addresses.stream().map(customerMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAddressResponse getById(UUID id) {
        return customerMapper.toResponse(findAddress(id));
    }

    @Override
    @Transactional
    public CustomerAddressResponse create(CustomerAddressRequest request) {
        CustomerAddress address = new CustomerAddress();
        customerMapper.update(address, request, findCustomer(request.customerId()));
        CustomerAddress savedAddress = customerAddressRepository.save(address);
        clearOtherDefaults(savedAddress);
        return customerMapper.toResponse(savedAddress);
    }

    @Override
    @Transactional
    public CustomerAddressResponse update(UUID id, CustomerAddressRequest request) {
        CustomerAddress address = findAddress(id);
        customerMapper.update(address, request, findCustomer(request.customerId()));
        CustomerAddress savedAddress = customerAddressRepository.save(address);
        clearOtherDefaults(savedAddress);
        return customerMapper.toResponse(savedAddress);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomerAddress address = findAddress(id);
        address.setIsDeleted(true);
        customerAddressRepository.save(address);
    }


    //======================================= Helper classes =======================================

    private void clearOtherDefaults(CustomerAddress address) {
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            customerAddressRepository.clearDefaultForCustomer(address.getCustomer().getId(), address.getId());
        }
    }

    private CustomerAddress findAddress(UUID id) {
        return customerAddressRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_ADDRESS_NOT_FOUND));
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }
}
