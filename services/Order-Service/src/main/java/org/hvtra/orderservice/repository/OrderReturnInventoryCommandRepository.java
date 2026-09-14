package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface OrderReturnInventoryCommandRepository
        extends JpaRepository<OrderReturnInventoryCommand, UUID> {

    @Query("""
            select distinct inventoryCommand
            from OrderReturnInventoryCommand inventoryCommand
            left join fetch inventoryCommand.orderReturn orderReturn
            left join fetch orderReturn.order
            left join fetch inventoryCommand.lines lines
            left join fetch lines.returnItem returnItem
            left join fetch returnItem.orderItem
            where inventoryCommand.commandId = :commandId
              and inventoryCommand.isDeleted = false
            """)
    Optional<OrderReturnInventoryCommand> findByCommandIdAndIsDeletedFalse(
            @Param("commandId") UUID commandId
    );

    @Query("""
            select coalesce(sum(line.quantity), 0)
            from OrderReturnInventoryCommandLine line
            join line.command inventoryCommand
            where line.returnItem.id = :returnItemId
              and line.isDeleted = false
              and inventoryCommand.isDeleted = false
            """)
    BigDecimal sumCommandedQuantityByReturnItemId(@Param("returnItemId") UUID returnItemId);
}
