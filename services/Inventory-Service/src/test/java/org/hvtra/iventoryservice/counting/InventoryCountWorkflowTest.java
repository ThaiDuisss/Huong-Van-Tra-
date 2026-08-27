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
    void countRequiresFreezeBeforeStartAndApprovalBeforeClose() {
        InventoryCount count = new InventoryCount();
        InventoryCountLine line = new InventoryCountLine();
        line.takeSnapshot(new BigDecimal("5.00"));
        count.addLine(line);

        count.freeze();
        count.start();
        line.recordCount(new BigDecimal("5.00"), null, null);
        count.submitForReconciliation();
        count.submitForApproval(false, UUID.randomUUID());
        count.close(UUID.randomUUID());

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.COMPLETED);
        assertThat(count.getClosedAt()).isNotNull();
    }

    @Test
    void varianceRequiresApprovalAndPostingBeforeClose() {
        InventoryCount count = new InventoryCount();
        InventoryCountLine line = new InventoryCountLine();
        line.takeSnapshot(new BigDecimal("5.00"));
        count.addLine(line);

        count.freeze();
        count.start();
        line.recordCount(new BigDecimal("4.00"), "COUNT", null);
        count.submitForReconciliation();
        count.submitForApproval(true, UUID.randomUUID());
        count.approve(UUID.randomUUID());
        count.markPosted();
        count.close(UUID.randomUUID());

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.COMPLETED);
    }

    @Test
    void cancelStopsAnOpenCountAndRecordsTheReason() {
        InventoryCount count = new InventoryCount();
        count.freeze();

        count.cancel(UUID.randomUUID(), "Không còn nhu cầu kiểm kê");

        assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.CANCELLED);
        assertThat(count.getClosedAt()).isNotNull();
        assertThat(count.getNote()).contains("Không còn nhu cầu kiểm kê");
    }

    @Test
    void approvedCountCannotBeCancelled() {
        InventoryCount count = new InventoryCount();
        InventoryCountLine line = new InventoryCountLine();
        line.takeSnapshot(new BigDecimal("5.00"));
        count.addLine(line);
        count.freeze();
        count.start();
        line.recordCount(new BigDecimal("5.00"), null, null);
        count.submitForReconciliation();
        count.submitForApproval(false, UUID.randomUUID());

        assertThatThrownBy(() -> count.cancel(UUID.randomUUID(), "Hủy muộn"))
                .hasMessageContaining("Không thể hủy");
    }
}
