package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "order_return_inventory_command_lines",
        indexes = {
                @Index(
                        name = "idx_order_return_inventory_command_lines_command",
                        columnList = "inventory_command_id"
                ),
                @Index(
                        name = "idx_order_return_inventory_command_lines_return_item",
                        columnList = "return_item_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnInventoryCommandLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_command_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderReturnInventoryCommand command;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderReturnItem returnItem;

    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 50)
    private ReturnInventoryDispositionResult result;

    @Column(name = "destination_location_id", columnDefinition = "BINARY(16)")
    private UUID destinationLocationId;

    @Column(name = "note", length = 500)
    private String note;
}
