package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.TodaySalesService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TodaySalesServiceImpl implements TodaySalesService {

    private final OrderRepository orderRepository;
    private final OrderQueryCriteriaBuilder orderQueryCriteriaBuilder;
    private final OrderDateTimeHelper orderDateTimeHelper;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderResponseAssembler orderResponseAssembler;

    @Override
    @Transactional(readOnly = true)
    public Page<TodaySalesItemResponse> getTodaySales(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethod,
            int page,
            int size
    ) {
        LocalDate today = orderDateTimeHelper.today();
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        List<TodaySalesItemResponse> filteredItems = orderRepository.findAll(
                        orderQueryCriteriaBuilder.build(null, status, paymentStatus, null, today, today),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(orderResponseAssembler::toTodaySalesItemResponse)
                .filter(item -> orderResponseAssembler.paymentMethodMatches(item, paymentMethod))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), filteredItems.size());
        int end = Math.min(start + pageable.getPageSize(), filteredItems.size());

        return new PageImpl<>(filteredItems.subList(start, end), pageable, filteredItems.size());
    }

    @Override
    @Transactional(readOnly = true)
    public TodaySalesSummaryResponse getTodaySalesSummary() {
        LocalDate today = orderDateTimeHelper.today();
        List<Order> todayOrders = orderRepository.findAll(
                orderQueryCriteriaBuilder.build(null, null, null, null, today, today)
        );
        return orderAmountCalculator.calculateTodaySalesSummary(todayOrders);
    }
}
