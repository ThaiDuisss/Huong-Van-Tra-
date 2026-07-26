package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderQueryService;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private static final String INVALID_DATE_RANGE_MESSAGE = "fromDate không được lớn hơn toDate";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderQueryCriteriaBuilder orderQueryCriteriaBuilder;
    private final OrderResponseAssembler orderResponseAssembler;

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        return orderResponseAssembler.toResponse(getExistingOrder(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentInfoResponse getPaymentInfo(UUID orderId) {
        return orderMapper.toPaymentInfoResponse(getExistingOrder(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        validateDateRange(fromDate, toDate);

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200)
        );

        Specification<Order> filters = orderQueryCriteriaBuilder.build(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate
        );

        return orderRepository.findAll(
                orderQueryCriteriaBuilder.withBusinessOrder(
                        filters,
                        orderQueryCriteriaBuilder.normalizeSort(sort)
                ),
                pageRequest
        ).map(orderMapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);

        Specification<Order> dateRange = orderQueryCriteriaBuilder.build(
                null,
                null,
                null,
                null,
                fromDate,
                toDate
        );

        return OrderStatsResponse.builder()
                .total(orderRepository.count(dateRange))
                .pending(orderRepository.count(orderQueryCriteriaBuilder.byStatuses(
                        fromDate,
                        toDate,
                        List.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT)
                )))
                .processing(orderRepository.count(orderQueryCriteriaBuilder.byStatuses(
                        fromDate,
                        toDate,
                        List.of(OrderStatus.PAYMENT_CONFIRMED)
                )))
                .shipping(0)
                .completed(orderRepository.count(orderQueryCriteriaBuilder.byStatuses(
                        fromDate,
                        toDate,
                        List.of(OrderStatus.COMPLETED)
                )))
                .returnedOrRefunded(orderRepository.count(orderQueryCriteriaBuilder.byPaymentStatuses(
                        fromDate,
                        toDate,
                        List.of(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED)
                )))
                .unpaidInvoices(orderRepository.count(orderQueryCriteriaBuilder.byPaymentStatuses(
                        fromDate,
                        toDate,
                        List.of(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT)
                )))
                .paidInvoices(orderRepository.count(orderQueryCriteriaBuilder.byPaymentStatuses(
                        fromDate,
                        toDate,
                        List.of(PaymentStatus.PAID)
                )))
                .build();
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", INVALID_DATE_RANGE_MESSAGE);
        }
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }
}
