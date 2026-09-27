package org.hvtra.iventoryservice.alert;

import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InventoryReplenishmentAlertRepository extends JpaRepository<InventoryReplenishmentAlert, UUID> {
    @Query("""
            select (count(a) > 0) from InventoryReplenishmentAlert a
            where a.inventoryItem.id = :itemId and a.location.id = :locationId
              and a.alertType = org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType.PRODUCTION_REQUIRED
              and a.status in (org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertStatus.OPEN,
                               org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertStatus.ACKNOWLEDGED,
                               org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertStatus.ORDERED)
            """)
    boolean existsOpenProductionAlert(@Param("itemId") UUID itemId, @Param("locationId") UUID locationId);
}
