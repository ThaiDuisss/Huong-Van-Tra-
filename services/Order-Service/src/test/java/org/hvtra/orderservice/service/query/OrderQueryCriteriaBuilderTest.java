package org.hvtra.orderservice.service.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderQueryCriteriaBuilderTest {

    private final OrderQueryCriteriaBuilder criteriaBuilder =
            new OrderQueryCriteriaBuilder(new OrderDateTimeHelper());

    @Test
    void keepsExistingDefaultSortForNullBlankAndUnsupportedValues() {
        assertEquals("businessStatus", criteriaBuilder.normalizeSort(null));
        assertEquals("businessStatus", criteriaBuilder.normalizeSort(" "));
        assertEquals("businessStatus", criteriaBuilder.normalizeSort("createdAt"));
    }

    @Test
    void keepsExistingInvoicePaymentSortCaseInsensitive() {
        assertEquals("invoicePayment", criteriaBuilder.normalizeSort("invoicePayment"));
        assertEquals("invoicePayment", criteriaBuilder.normalizeSort("INVOICEPAYMENT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsKeywordUnpaidAliasAndOrderTypeNormalization() {
        Root<Order> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder jpaCriteriaBuilder = mock(CriteriaBuilder.class);
        Path<Boolean> isDeletedPath = mock(Path.class);
        Path<String> orderCodePath = mock(Path.class);
        Path<String> customerNamePath = mock(Path.class);
        Path<String> customerPhonePath = mock(Path.class);
        Path<PaymentStatus> paymentStatusPath = mock(Path.class);
        Path<String> orderTypePath = mock(Path.class);
        Expression<String> lowerOrderCode = mock(Expression.class);
        Expression<String> lowerCustomerName = mock(Expression.class);
        Expression<String> lowerCustomerPhone = mock(Expression.class);
        Predicate isDeletedPredicate = mock(Predicate.class);
        Predicate orderCodePredicate = mock(Predicate.class);
        Predicate customerNamePredicate = mock(Predicate.class);
        Predicate customerPhonePredicate = mock(Predicate.class);
        Predicate keywordPredicate = mock(Predicate.class);
        Predicate paymentPredicate = mock(Predicate.class);
        Predicate orderTypePredicate = mock(Predicate.class);
        Predicate combinedPredicate = mock(Predicate.class);

        when(root.<Boolean>get("isDeleted")).thenReturn(isDeletedPath);
        when(root.<String>get("orderCode")).thenReturn(orderCodePath);
        when(root.<String>get("customerNameSnapshot")).thenReturn(customerNamePath);
        when(root.<String>get("customerPhoneSnapshot")).thenReturn(customerPhonePath);
        when(root.<PaymentStatus>get("paymentStatus")).thenReturn(paymentStatusPath);
        when(root.<String>get("orderType")).thenReturn(orderTypePath);
        when(jpaCriteriaBuilder.isFalse(isDeletedPath)).thenReturn(isDeletedPredicate);
        when(jpaCriteriaBuilder.lower(orderCodePath)).thenReturn(lowerOrderCode);
        when(jpaCriteriaBuilder.lower(customerNamePath)).thenReturn(lowerCustomerName);
        when(jpaCriteriaBuilder.lower(customerPhonePath)).thenReturn(lowerCustomerPhone);
        when(jpaCriteriaBuilder.like(lowerOrderCode, "%tra%")).thenReturn(orderCodePredicate);
        when(jpaCriteriaBuilder.like(lowerCustomerName, "%tra%")).thenReturn(customerNamePredicate);
        when(jpaCriteriaBuilder.like(lowerCustomerPhone, "%tra%")).thenReturn(customerPhonePredicate);
        when(jpaCriteriaBuilder.or(
                orderCodePredicate,
                customerNamePredicate,
                customerPhonePredicate
        )).thenReturn(keywordPredicate);
        when(paymentStatusPath.in(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT))
                .thenReturn(paymentPredicate);
        when(jpaCriteriaBuilder.equal(orderTypePath, "POS")).thenReturn(orderTypePredicate);
        when(jpaCriteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Specification<Order> specification = criteriaBuilder.build(
                "  TrA  ",
                null,
                PaymentStatus.UNPAID,
                " pos ",
                null,
                null
        );

        Predicate result = specification.toPredicate(root, query, jpaCriteriaBuilder);

        assertSame(combinedPredicate, result);
    }
}
