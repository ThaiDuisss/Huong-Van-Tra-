package org.hvtra.iventoryservice.counting;

import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryCountWorkflowTest {

    @Test
    void softLockMovementChangesExpectedQuantityAndVariance() {
        InventoryCountLine line = new InventoryCountLine();
        line.takeSnapshot(new BigDecimal("10.00"));
        line.recordCount(new BigDecimal("12.00"), null, null);

        line.reconcileMovements(new BigDecimal("3.00"));

        assertThat(line.getExpectedQuantity()).isEqualByComparingTo("13.00");
        assertThat(line.getVarianceQuantity()).isEqualByComparingTo("-1.00");
    }

    @Test
    void countWithoutVarianceCompletesOnFinish() {
        UUID counterId = UUID.randomUUID();
        InventoryCount count = countWithLine("5.00");

        count.start();
        count.getLines().get(0).recordCount(new BigDecimal("5.00"), null, null);
        count.finish(counterId);

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.COMPLETED);
        assertThat(count.getStartedAt()).isNotNull();
        assertThat(count.getCompletedBy()).isEqualTo(counterId);
    }

    @Test
    void varianceWaitsForApprovalThenCompletes() {
        UUID managerId = UUID.randomUUID();
        InventoryCount count = countWithLine("5.00");

        count.start();
        count.getLines().get(0).recordCount(new BigDecimal("4.00"), "COUNT", null);
        count.finish(UUID.randomUUID());

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.PENDING_APPROVAL);

        count.approve(managerId);

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.COMPLETED);
        assertThat(count.getCompletedBy()).isEqualTo(managerId);
    }

    @Test
    void returnForRecountGoesBackToCountingAndLineBecomesUncounted() {
        InventoryCount count = countWithLine("5.00");
        count.start();
        InventoryCountLine line = count.getLines().get(0);
        line.recordCount(new BigDecimal("4.00"), null, null);
        count.finish(UUID.randomUUID());

        count.returnForRecount(" Lệch 1 hộp ");
        line.resetForRecount();

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.COUNTING);
        assertThat(count.getReturnReason()).isEqualTo("Lệch 1 hộp");
        assertThat(line.hasBeenCounted()).isFalse();
    }

    @Test
    void startRequiresDraft() {
        InventoryCount count = countWithLine("5.00");
        count.start();

        assertThatThrownBy(count::start)
                .hasMessage("Chỉ có thể bắt đầu kiểm kê khi kỳ kiểm kê ở trạng thái DRAFT");
    }

    @Test
    void cancelStopsAnOpenCountAndRecordsTheReason() {
        UUID managerId = UUID.randomUUID();
        InventoryCount count = new InventoryCount();

        count.cancel(managerId, " Không còn nhu cầu kiểm kê ");

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.CANCELLED);
        assertThat(count.getCancelledAt()).isNotNull();
        assertThat(count.getCancelledBy()).isEqualTo(managerId);
        assertThat(count.getCancelReason()).isEqualTo("Không còn nhu cầu kiểm kê");
    }

    @Test
    void completedCountCannotBeCancelled() {
        InventoryCount count = countWithLine("5.00");
        count.start();
        count.getLines().get(0).recordCount(new BigDecimal("5.00"), null, null);
        count.finish(UUID.randomUUID());

        assertThatThrownBy(() -> count.cancel(UUID.randomUUID(), "Hủy muộn"))
                .hasMessageContaining("Không thể hủy");
    }

    private InventoryCount countWithLine(String snapshot) {
        InventoryCount count = new InventoryCount();
        InventoryCountLine line = new InventoryCountLine();
        line.takeSnapshot(new BigDecimal(snapshot));
        count.addLine(line);
        return count;
    }
}
