package org.hvtra.orderservice.service.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OrderQueryCriteriaBuilder {

    private static final String SORT_BUSINESS_STATUS = "businessStatus";
    private static final String SORT_INVOICE_PAYMENT = "invoicePayment";
    private static final String SORT_CREATED_AT_DESC = "createdAt,desc";

    private final OrderDateTimeHelper orderDateTimeHelper;

    public Specification<Order> build(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("orderCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerNameSnapshot")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerPhoneSnapshot")), pattern)
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                if (PaymentStatus.UNPAID == paymentStatus) {
                    predicates.add(root.get("paymentStatus").in(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT));
                } else if (PaymentStatus.REFUNDED == paymentStatus) {
                    predicates.add(root.get("paymentStatus").in(
                            PaymentStatus.REFUNDED,
                            PaymentStatus.PARTIALLY_REFUNDED
                    ));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("paymentStatus"), paymentStatus));
                }
            }
            if (orderType != null && !orderType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("orderType"), normalizeOrderType(orderType)));
            }
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(fromDate)
                ));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(toDate.plusDays(1))
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public Specification<Order> withBusinessOrder(Specification<Order> filters, String sort) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = filters.toPredicate(root, query, criteriaBuilder);
            applyDefaultOrder(root, query, criteriaBuilder, sort);
            return predicate;
        };
    }

    public String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_BUSINESS_STATUS;
        }

        if (SORT_INVOICE_PAYMENT.equalsIgnoreCase(sort)) {
            return SORT_INVOICE_PAYMENT;
        }

        if (SORT_CREATED_AT_DESC.equalsIgnoreCase(sort)) {
            return SORT_CREATED_AT_DESC;
        }

        return SORT_BUSINESS_STATUS;
    }

    public Specification<Order> byStatuses(
            LocalDate fromDate,
            LocalDate toDate,
            List<OrderStatus> statuses
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            predicates.add(root.get("status").in(statuses));
            addDateRange(predicates, root, criteriaBuilder, fromDate, toDate);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public Specification<Order> byPaymentStatuses(
            LocalDate fromDate,
            LocalDate toDate,
            List<PaymentStatus> statuses
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            predicates.add(root.get("paymentStatus").in(statuses));
            addDateRange(predicates, root, criteriaBuilder, fromDate, toDate);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void applyDefaultOrder(
            Root<Order> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            String sort
    ) {
        if (sort == null || sort.isBlank() || isCountQuery(query)) {
            return;
        }

        if (SORT_CREATED_AT_DESC.equalsIgnoreCase(sort)) {
            query.orderBy(
                    criteriaBuilder.desc(root.get("createdAt")),
                    criteriaBuilder.desc(root.get("id"))
            );
            return;
        }

        Expression<Integer> priority = SORT_INVOICE_PAYMENT.equalsIgnoreCase(sort)
                ? invoicePaymentPriority(root, criteriaBuilder)
                : orderStatusPriority(root, criteriaBuilder);

        query.orderBy(
                criteriaBuilder.asc(priority),
                criteriaBuilder.desc(root.get("createdAt")),
                criteriaBuilder.desc(root.get("id"))
        );
    }

    private boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    private Expression<Integer> orderStatusPriority(Root<Order> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PENDING_PAYMENT), 1)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PAYMENT_CONFIRMED), 2)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.COMPLETED), 3)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.REFUNDED), 4)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.PARTIALLY_REFUNDED), 4)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PAYMENT_FAILED), 5)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.EXPIRED), 6)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.CANCELLED), 7)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.DRAFT), 8)
                .otherwise(99);
    }

    private Expression<Integer> invoicePaymentPriority(Root<Order> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.UNPAID), 1)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.WAITING_PAYMENT), 1)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.PAID), 2)
                .otherwise(99);
    }

    private void addDateRange(
            List<Predicate> predicates,
            Root<Order> root,
            CriteriaBuilder criteriaBuilder,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    orderDateTimeHelper.startOfDay(fromDate)
            ));
        }
        if (toDate != null) {
            predicates.add(criteriaBuilder.lessThan(
                    root.get("createdAt"),
                    orderDateTimeHelper.startOfDay(toDate.plusDays(1))
            ));
        }
    }

    private String normalizeOrderType(String orderType) {
        return orderType.trim().toUpperCase(Locale.ROOT);
    }
}
