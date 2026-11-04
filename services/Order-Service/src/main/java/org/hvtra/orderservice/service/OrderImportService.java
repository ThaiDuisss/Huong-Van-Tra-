package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.OrderImportRequest;
import org.hvtra.orderservice.dto.response.OrderImportResponse;

public interface OrderImportService {

    OrderImportResponse importOrders(OrderImportRequest request);
}
