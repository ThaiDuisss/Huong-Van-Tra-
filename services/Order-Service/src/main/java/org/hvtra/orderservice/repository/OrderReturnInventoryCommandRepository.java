package org.hvtra.orderservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
              and inventoryCommand.status in (
                  org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus.PENDING,
                  org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus.PROCESSED,
                  org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus.FAILED
              )
            """)
    BigDecimal sumCommandedQuantityByReturnItemId(@Param("returnItemId") UUID returnItemId);

    @Query("""
            select c.commandId
            from OrderReturnInventoryCommand c
            where c.status = :status
              and c.isDeleted = false
              and c.createdAt <= :threshold
            order by c.createdAt asc
            """)
    List<UUID> findStaleCommandIds(
            @Param("status") OrderReturnInventoryCommandStatus status,
            @Param("threshold") Instant threshold,
            Pageable pageable
    );

    @Query("""
            select line.returnItem.id, coalesce(sum(line.quantity), 0)
            from OrderReturnInventoryCommandLine line
            join line.command inventoryCommand
            where inventoryCommand.orderReturn.id = :returnId
              and inventoryCommand.status = :status
              and line.isDeleted = false
              and inventoryCommand.isDeleted = false
            group by line.returnItem.id
            """)
    List<Object[]> sumQuantityByReturnIdAndCommandStatus(
            @Param("returnId") UUID returnId,
            @Param("status") OrderReturnInventoryCommandStatus status
    );

    boolean existsByOrderReturnIdAndStatusAndIsDeletedFalse(
            UUID returnId,
            OrderReturnInventoryCommandStatus status
    );

    @Query("""
            select c.orderReturn.id, c.status
            from OrderReturnInventoryCommand c
            where c.orderReturn.id in :returnIds
              and c.isDeleted = false
            """)
    List<Object[]> findCommandStatusesByReturnIds(@Param("returnIds") Collection<UUID> returnIds);

    @Query("""
            select inventoryCommand.orderReturn.id, line.returnItem.id, coalesce(sum(line.quantity), 0)
            from OrderReturnInventoryCommandLine line
            join line.command inventoryCommand
            where inventoryCommand.orderReturn.id in :returnIds
              and inventoryCommand.status = :status
              and line.isDeleted = false
              and inventoryCommand.isDeleted = false
            group by inventoryCommand.orderReturn.id, line.returnItem.id
            """)
    List<Object[]> sumQuantityByReturnIdsAndCommandStatus(
            @Param("returnIds") Collection<UUID> returnIds,
            @Param("status") OrderReturnInventoryCommandStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventoryCommand
            from OrderReturnInventoryCommand inventoryCommand
            join fetch inventoryCommand.orderReturn
            where inventoryCommand.orderReturn.id in :returnIds
              and inventoryCommand.status = :status
              and inventoryCommand.isDeleted = false
            """)
    List<OrderReturnInventoryCommand> findByOrderReturnIdsAndStatusForUpdate(
            @Param("returnIds") Collection<UUID> returnIds,
            @Param("status") OrderReturnInventoryCommandStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct inventoryCommand
            from OrderReturnInventoryCommand inventoryCommand
            left join fetch inventoryCommand.orderReturn orderReturn
            where inventoryCommand.commandId = :commandId
              and inventoryCommand.isDeleted = false
            """)
    Optional<OrderReturnInventoryCommand> findByCommandIdForUpdate(
            @Param("commandId") UUID commandId
    );

    @Query("""
            select distinct inventoryCommand
            from OrderReturnInventoryCommand inventoryCommand
            left join fetch inventoryCommand.lines lines
            left join fetch lines.returnItem
            where inventoryCommand.orderReturn.id = :returnId
              and inventoryCommand.isDeleted = false
            order by inventoryCommand.createdAt desc
            """)
    List<OrderReturnInventoryCommand> findByOrderReturnIdWithLines(@Param("returnId") UUID returnId);
}
