package org.hvtra.iventoryservice.domain.entity.customerreturn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "customer_return_inspection_lines",
        indexes = {
                @Index(name = "idx_customer_return_inspection_lines_inspection", columnList = "inspection_id"),
                @Index(name = "idx_customer_return_inspection_lines_return_item", columnList = "return_item_id"),
                @Index(name = "idx_customer_return_inspection_lines_product_variant", columnList = "product_variant_id"),
                @Index(name = "idx_customer_return_inspection_lines_status", columnList = "posted")
        }
)
@Getter
@Setter
public class CustomerReturnInspectionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private CustomerReturnInspection inspection;

    @Column(name = "return_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID returnItemId;

    @Column(name = "order_item_id", columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CustomerReturnQcResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private InventoryLocation destinationLocation;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Boolean posted = false;

    @Column(name = "transaction_id", columnDefinition = "BINARY(16)")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_request_id")
    private DisposalRequest disposalRequest;
}
