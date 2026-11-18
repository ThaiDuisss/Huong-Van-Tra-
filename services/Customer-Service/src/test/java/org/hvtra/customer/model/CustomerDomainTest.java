package org.hvtra.customer.model;

import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.util.AmountUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerDomainTest {

    @Test
    void tierComparisonUsesSpendingThenOrderCount() {
        CustomerTier member = tier(0, 0);
        CustomerTier silver = tier(1_000_000, 3);
        CustomerTier silverPlus = tier(1_000_000, 5);

        assertThat(silver.isHigherThan(member)).isTrue();
        assertThat(member.isHigherThan(silver)).isFalse();
        assertThat(silverPlus.isHigherThan(silver)).isTrue();
        assertThat(silver.isHigherThan(silverPlus)).isFalse();
        assertThat(silver.isHigherThan(tier(1_000_000, 3))).isFalse();
        assertThat(member.isHigherThan(null)).isTrue();
    }

    @Test
    void customerAddPurchaseAccumulatesTotalsAndLastPurchaseDate() {
        Customer customer = new Customer();
        customer.setTotalSpent(null);
        customer.setTotalOrderCount(null);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 15, 10, 0);

        customer.addPurchase(BigDecimal.valueOf(200_000), occurredAt);
        customer.addPurchase(BigDecimal.valueOf(50_000), occurredAt.plusDays(1));

        assertThat(customer.getTotalSpent()).isEqualByComparingTo("250000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(2);
        assertThat(customer.getLastPurchaseDate()).isEqualTo(occurredAt.plusDays(1));
    }

    @Test
    void customerRefundNeverGoesNegative() {
        Customer customer = new Customer();
        customer.setTotalSpent(BigDecimal.valueOf(100_000));
        customer.setTotalOrderCount(1);

        customer.applyRefund(BigDecimal.valueOf(40_000), false);
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("60000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(1);

        customer.applyRefund(BigDecimal.valueOf(500_000), true);
        customer.applyRefund(BigDecimal.ONE, true);
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("0");
        assertThat(customer.getTotalOrderCount()).isZero();
    }

    @Test
    void customerPointBalanceCannotGoNegative() {
        Customer customer = new Customer();

        assertThat(customer.changePoints(30)).isEqualTo(30);
        assertThat(customer.changePoints(-10)).isEqualTo(20);
        assertThatThrownBy(() -> customer.changePoints(-21)).isInstanceOf(IllegalStateException.class);
        assertThat(customer.getPointBalance()).isEqualTo(20);
    }

    @Test
    void amountUtilsHandlesNullAndNegativeValues() {
        assertThat(AmountUtils.zeroIfNull((BigDecimal) null)).isEqualByComparingTo("0");
        assertThat(AmountUtils.zeroIfNull(BigDecimal.TEN)).isEqualByComparingTo("10");
        assertThat(AmountUtils.zeroIfNull((Integer) null)).isZero();
        assertThat(AmountUtils.zeroIfNull(7)).isEqualTo(7);
        assertThat(AmountUtils.nonNegative(BigDecimal.valueOf(-1))).isEqualByComparingTo("0");
        assertThat(AmountUtils.nonNegative(BigDecimal.ONE)).isEqualByComparingTo("1");
    }

    private CustomerTier tier(int minSpending, int minOrderCount) {
        CustomerTier tier = new CustomerTier();
        tier.setMinSpending(BigDecimal.valueOf(minSpending));
        tier.setMinOrderCount(minOrderCount);
        return tier;
    }
}
