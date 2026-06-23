package org.hvtra.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.service.impl.OrderCustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders/customers-info")
@RequiredArgsConstructor
public class OrderCustomerController {

    private final OrderCustomerService orderCustomerService;

    @GetMapping
    public List<CustomerResponse> getCustomers() {
        return orderCustomerService.getCustomers();
    }
}