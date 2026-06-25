package org.hvtra.customer.specification;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.model.entity.Customer;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    public static Specification<Customer> from(CustomerFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
            }

            if (hasText(filter.q())) {
                String pattern = "%" + filter.q().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern)
                ));
            }

            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            if (hasValues(filter.tierIds())) {
                predicates.add(root.get("tier").get("id").in(filter.tierIds()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
            }
            if (filter.lastPurchaseFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("lastPurchaseDate"), filter.lastPurchaseFrom()));
            }
            if (filter.lastPurchaseTo() != null) {
                Predicate purchasedBefore = criteriaBuilder.lessThanOrEqualTo(
                        root.get("lastPurchaseDate"), filter.lastPurchaseTo());
                predicates.add(Boolean.TRUE.equals(filter.includeNeverPurchased())
                        ? criteriaBuilder.or(purchasedBefore, criteriaBuilder.isNull(root.get("lastPurchaseDate")))
                        : purchasedBefore);
            }
            if (filter.minTotalSpent() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("totalSpent"), filter.minTotalSpent()));
            }
            if (filter.maxTotalSpent() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("totalSpent"), filter.maxTotalSpent()));
            }
            if (filter.hasPurchased() != null) {
                Predicate hasPurchase = criteriaBuilder.or(
                        criteriaBuilder.isNotNull(root.get("lastPurchaseDate")),
                        criteriaBuilder.greaterThan(root.get("totalSpent"), BigDecimal.ZERO)
                );
                predicates.add(filter.hasPurchased() ? hasPurchase : criteriaBuilder.not(hasPurchase));
            }
            if (filter.birthMonth() != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.function("month", Integer.class, root.get("dateOfBirth")),
                        filter.birthMonth()
                ));
            }
            if (filter.birthYear() != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.function("year", Integer.class, root.get("dateOfBirth")),
                        filter.birthYear()
                ));
            }

            if (hasValues(filter.vipTierIds())) {
                predicates.add(root.get("tier").get("id").in(filter.vipTierIds()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }
}
