package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.CustomerPurchaseResponse;
import org.hvtra.orderservice.dto.response.CustomerResponse;

import java.util.List;
import java.util.UUID;

public interface OrderCustomerService {

    List<CustomerResponse> getCustomers(String q);

    CustomerPurchaseResponse updateCustomerPurchase(UUID orderId);
}