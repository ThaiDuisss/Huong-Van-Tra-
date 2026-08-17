package org.hvtra.orderservice.service;

import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.time.LocalDate;

public interface OrderExportService {
    byte[] exportOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    );

    byte[] exportInvoices(
            String keyword,
            PaymentStatus paymentStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    );
}
