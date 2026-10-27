package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;

/** Phiếu nhập nguyên liệu từ nhà cung cấp (document_type = GOODS_RECEIPT). */
@Entity
@DiscriminatorValue("GOODS_RECEIPT")
public class GoodsReceipt extends InventoryDocument {

    public GoodsReceipt() {
        super(InventoryDocumentType.GOODS_RECEIPT);
    }
}
