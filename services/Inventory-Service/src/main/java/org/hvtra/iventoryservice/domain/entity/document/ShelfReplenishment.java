package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;

/** Phiếu nhập kệ: chuyển hàng từ kho lên kệ (document_type = TRANSFER_RECEIPT). */
@Entity
@DiscriminatorValue("TRANSFER_RECEIPT")
public class ShelfReplenishment extends InventoryDocument {

    public ShelfReplenishment() {
        super(InventoryDocumentType.TRANSFER_RECEIPT);
    }
}
