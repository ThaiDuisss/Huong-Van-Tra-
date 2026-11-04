package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.response.CustomerAddressResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerAddress;
import org.hvtra.customer.repository.CustomerAddressRepository;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.impl.CustomerAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerAddressServiceTest {

    private CustomerAddressRepository addressRepository;
    private CustomerRepository customerRepository;
    private CustomerAddressServiceImpl addressService;
    private Customer customer;

    @BeforeEach
    void setUp() {
        addressRepository = mock(CustomerAddressRepository.class);
        customerRepository = mock(CustomerRepository.class);
        addressService = new CustomerAddressServiceImpl(addressRepository, customerRepository, new CustomerMapper());
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(invocation -> {
            CustomerAddress address = invocation.getArgument(0);
            if (address.getId() == null) {
                address.setId(UUID.randomUUID());
            }
            return address;
        });
    }

    @Test
    void getAllWithoutCustomerIdReturnsEveryActiveAddress() {
        when(addressRepository.findAllByIsDeletedFalse()).thenReturn(List.of(address(false), address(true)));

        List<CustomerAddressResponse> responses = addressService.getAll(null);

        assertThat(responses).hasSize(2);
        verify(addressRepository, never()).findAllByCustomerIdAndIsDeletedFalse(any());
    }

    @Test
    void getAllWithCustomerIdFiltersByCustomer() {
        when(addressRepository.findAllByCustomerIdAndIsDeletedFalse(customer.getId())).thenReturn(List.of(address(true)));

        List<CustomerAddressResponse> responses = addressService.getAll(customer.getId());

        assertThat(responses).singleElement()
                .extracting(CustomerAddressResponse::customerId)
                .isEqualTo(customer.getId());
        verify(addressRepository, never()).findAllByIsDeletedFalse();
    }

    @Test
    void createDefaultAddressClearsOtherDefaultsOfCustomer() {
        CustomerAddressResponse response = addressService.create(request(true));

        assertThat(response.isDefault()).isTrue();
        assertThat(response.recipientName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.customerId()).isEqualTo(customer.getId());
        verify(addressRepository).clearDefaultForCustomer(customer.getId(), response.id());
    }

    @Test
    void createNonDefaultAddressKeepsOtherDefaults() {
        CustomerAddressResponse response = addressService.create(request(null));

        assertThat(response.isDefault()).isFalse();
        verify(addressRepository, never()).clearDefaultForCustomer(any(), any());
    }

    @Test
    void createRejectsUnknownCustomer() {
        UUID unknownCustomerId = UUID.randomUUID();
        when(customerRepository.findByIdAndIsDeletedFalse(unknownCustomerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.create(new CustomerAddressRequest(
                unknownCustomerId, "A", "0901234567", "1 Lê Lợi", "HCM", "Q1", "Bến Nghé", false)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_NOT_FOUND);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void updateOverwritesAddressFields() {
        CustomerAddress existing = address(false);
        when(addressRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        CustomerAddressResponse response = addressService.update(existing.getId(), request(true));

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(existing.getAddressLine()).isEqualTo("12 Nguyễn Huệ");
        assertThat(existing.getIsDefault()).isTrue();
        verify(addressRepository).clearDefaultForCustomer(customer.getId(), existing.getId());
    }

    @Test
    void getByIdReturnsAddress() {
        CustomerAddress existing = address(true);
        when(addressRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        CustomerAddressResponse response = addressService.getById(existing.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.addressLine()).isEqualTo("1 Lê Lợi");
    }

    @Test
    void getByIdRejectsMissingAddress() {
        UUID id = UUID.randomUUID();
        when(addressRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getById(id))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_ADDRESS_NOT_FOUND)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSoftDeletesAddress() {
        CustomerAddress existing = address(false);
        when(addressRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        addressService.delete(existing.getId());

        assertThat(existing.getIsDeleted()).isTrue();
        verify(addressRepository).save(existing);
    }

    private CustomerAddressRequest request(Boolean isDefault) {
        return new CustomerAddressRequest(customer.getId(), "Nguyễn Văn A", "0901234567",
                "12 Nguyễn Huệ", "Hồ Chí Minh", "Quận 1", "Bến Nghé", isDefault);
    }

    private CustomerAddress address(boolean isDefault) {
        CustomerAddress address = new CustomerAddress();
        address.setId(UUID.randomUUID());
        address.setCustomer(customer);
        address.setRecipientName("Nguyễn Văn A");
        address.setRecipientPhone("0901234567");
        address.setAddressLine("1 Lê Lợi");
        address.setProvince("Hồ Chí Minh");
        address.setDistrict("Quận 1");
        address.setWard("Bến Thành");
        address.setIsDefault(isDefault);
        return address;
    }
}
