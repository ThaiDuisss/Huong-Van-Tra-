package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;

import java.math.BigDecimal;

/**
 * Phiếu điều chỉnh tồn sau kiểm kê. document_type lưu chiều điều chỉnh: ADJUSTMENT_IN (tăng) hoặc
 * ADJUSTMENT_OUT (giảm); cả hai cùng là lớp này (xem công thức phân biệt ở {@link InventoryDocument}).
 * Chiều điều chỉnh phụ thuộc tổng các dòng nên tạo bằng {@link #forNetQuantity(BigDecimal)}.
 */
@Entity
@DiscriminatorValue("ADJUSTMENT")
public class InventoryAdjustment extends InventoryDocument {

    protected InventoryAdjustment() {
    }

    private InventoryAdjustment(InventoryDocumentType documentType) {
        super(documentType);
    }

    /** Tổng điều chỉnh ≥ 0 là phiếu tăng, ngược lại là phiếu giảm. */
    public static InventoryAdjustment forNetQuantity(BigDecimal netQuantity) {
        return new InventoryAdjustment(netQuantity.compareTo(BigDecimal.ZERO) >= 0
                ? InventoryDocumentType.ADJUSTMENT_IN
                : InventoryDocumentType.ADJUSTMENT_OUT);
    }

    public boolean isIncrease() {
        return getDocumentType() == InventoryDocumentType.ADJUSTMENT_IN;
    }
}
