package org.hvtra.iventoryservice.dashboard.repository;

import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardExpiryQualityRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardExpiringBatchRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardKpiRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardLocationRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardTopSkuRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.VariantOnHandRow;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryDashboardRepository extends JpaRepository<InventoryBalance, UUID> {

    @Query(value = """
            SELECT
                COALESCE(COUNT(DISTINCT b.inventory_item_id), 0) AS skuCount,
                COALESCE(SUM(b.quantity_on_hand), 0) AS totalOnHand,
                COALESCE(SUM(b.quantity_reserved), 0) AS totalReserved,
                COALESCE(SUM(b.quantity_blocked), 0) AS totalBlocked,
                COALESCE(SUM(b.quantity_on_hand - b.quantity_reserved - b.quantity_blocked), 0) AS totalAvailable,
                COALESCE((
                    SELECT COUNT(*) FROM (
                        SELECT sub_b.inventory_item_id
                        FROM inventory_balances sub_b
                        JOIN inventory_items sub_i ON sub_b.inventory_item_id = sub_i.id
                        JOIN inventory_locations sub_l ON sub_b.location_id = sub_l.id
                        WHERE (:locationId IS NULL OR sub_b.location_id = :locationId)
                          AND (:branchId IS NULL OR sub_l.branch_id = :branchId)
                          AND sub_i.reorder_point > 0
                        GROUP BY sub_b.inventory_item_id, sub_i.reorder_point
                        HAVING (SUM(sub_b.quantity_on_hand) - SUM(sub_b.quantity_reserved) - SUM(sub_b.quantity_blocked)) <= sub_i.reorder_point
                    ) AS under_reorder
                ), 0) AS belowReorderCount,
                COALESCE((
                    SELECT COUNT(*)
                    FROM inventory_documents d
                    WHERE d.is_deleted = false
                      AND d.status IN ('DRAFT', 'PENDING_APPROVAL', 'RECEIVING')
                      AND (:branchId IS NULL OR d.branch_id = :branchId)
                      AND (:locationId IS NULL OR d.location_id = :locationId)
                ), 0) AS pendingDocumentsCount
            FROM inventory_balances b
            JOIN inventory_locations l ON b.location_id = l.id
            WHERE (:locationId IS NULL OR b.location_id = :locationId)
              AND (:branchId IS NULL OR l.branch_id = :branchId)
            """, nativeQuery = true)
    DashboardKpiRow getOverviewKpi(
            @Param("branchId") UUID branchId,
            @Param("locationId") UUID locationId
    );

    @Query(value = """
            SELECT
                BIN_TO_UUID(i.reference_id) AS referenceId,
                COALESCE(SUM(b.quantity_on_hand), 0) AS totalOnHand
            FROM inventory_balances b
            JOIN inventory_items i ON b.inventory_item_id = i.id
            JOIN inventory_locations l ON b.location_id = l.id
            WHERE i.item_type = 'PRODUCT_VARIANT'
              AND i.reference_id IS NOT NULL
              AND (:locationId IS NULL OR b.location_id = :locationId)
              AND (:branchId IS NULL OR l.branch_id = :branchId)
            GROUP BY i.reference_id
            """, nativeQuery = true)
    List<VariantOnHandRow> getVariantOnHandQuantities(
            @Param("branchId") UUID branchId,
            @Param("locationId") UUID locationId
    );

    @Query(value = """
            SELECT
                BIN_TO_UUID(l.id) AS locationId,
                l.code AS locationCode,
                l.name AS locationName,
                COALESCE(SUM(b.quantity_on_hand), 0) AS totalOnHand,
                COALESCE(SUM(b.quantity_on_hand - b.quantity_reserved - b.quantity_blocked), 0) AS totalAvailable,
                COALESCE(SUM(b.quantity_reserved), 0) AS totalReserved
            FROM inventory_locations l
            LEFT JOIN inventory_balances b ON b.location_id = l.id
            WHERE (:branchId IS NULL OR l.branch_id = :branchId)
              AND l.is_deleted = false
            GROUP BY l.id, l.code, l.name
            ORDER BY totalOnHand DESC
            """, nativeQuery = true)
    List<DashboardLocationRow> getLocationBreakdown(
            @Param("branchId") UUID branchId
    );

    @Query(value = """
            SELECT
                BIN_TO_UUID(i.id) AS inventoryItemId,
                COALESCE(i.reference_code, 'N/A') AS skuCode,
                COALESCE(i.name, 'Sản phẩm') AS productName,
                l.name AS locationName,
                b.quantity_on_hand AS quantityOnHand,
                (b.quantity_on_hand - b.quantity_reserved - b.quantity_blocked) AS availableQuantity,
                'Cái' AS unitSymbol
            FROM inventory_balances b
            JOIN inventory_items i ON b.inventory_item_id = i.id
            JOIN inventory_locations l ON b.location_id = l.id
            WHERE (:locationId IS NULL OR b.location_id = :locationId)
              AND (:branchId IS NULL OR l.branch_id = :branchId)
            ORDER BY b.quantity_on_hand DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopSkuRow> getTopSkus(
            @Param("branchId") UUID branchId,
            @Param("locationId") UUID locationId,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT
                COALESCE(COUNT(CASE WHEN b.expiry_date > CURRENT_DATE AND b.expiry_date <= DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY) THEN 1 END), 0) AS expiry7Days,
                COALESCE(COUNT(CASE WHEN b.expiry_date > CURRENT_DATE AND b.expiry_date <= DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY) THEN 1 END), 0) AS expiry30Days,
                COALESCE(COUNT(CASE WHEN b.expiry_date < CURRENT_DATE THEN 1 END), 0) AS expiredUnhandled,
                COALESCE(COUNT(CASE WHEN b.status IN ('AWAITING_QC', 'QUARANTINED') AND b.received_at < DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 END), 0) AS quarantineOverSla,
                COALESCE((
                    SELECT COUNT(*)
                    FROM inventory_documents d
                    WHERE d.is_deleted = false
                      AND d.document_type = 'WRITE_OFF'
                      AND d.status = 'PENDING_APPROVAL'
                      AND (:branchId IS NULL OR d.branch_id = :branchId)
                ), 0) AS pendingDisposalApprovals
            FROM inventory_batches b
            WHERE b.is_deleted = false
            """, nativeQuery = true)
    DashboardExpiryQualityRow getExpiryQualitySummary(
            @Param("branchId") UUID branchId
    );

    @Query(value = """
            SELECT
                BIN_TO_UUID(b.id) AS id,
                b.batch_code AS batchCode,
                BIN_TO_UUID(i.id) AS inventoryItemId,
                COALESCE(i.reference_code, 'N/A') AS skuCode,
                COALESCE(i.name, 'Sản phẩm') AS productName,
                BIN_TO_UUID(l.id) AS locationId,
                COALESCE(l.name, 'Kho trung tâm') AS locationName,
                COALESCE(SUM(bal.quantity_on_hand), 0) AS onHandQuantity,
                COALESCE(SUM(bal.quantity_on_hand - bal.quantity_reserved - bal.quantity_blocked), 0) AS availableQuantity,
                'đơn vị' AS unit,
                b.expiry_date AS expiryDate,
                DATEDIFF(b.expiry_date, CURRENT_DATE) AS daysRemaining,
                b.status AS status
            FROM inventory_batches b
            JOIN inventory_items i ON b.inventory_item_id = i.id
            LEFT JOIN inventory_balances bal ON bal.inventory_batch_id = b.id
            LEFT JOIN inventory_locations l ON bal.location_id = l.id
            WHERE b.is_deleted = false
              AND b.expiry_date IS NOT NULL
              AND b.expiry_date <= DATE_ADD(CURRENT_DATE, INTERVAL :daysThreshold DAY)
              AND (:locationId IS NULL OR bal.location_id = :locationId)
              AND (:branchId IS NULL OR l.branch_id = :branchId)
            GROUP BY b.id, b.batch_code, i.id, i.reference_code, i.name, l.id, l.name, b.expiry_date, b.status
            ORDER BY b.expiry_date ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardExpiringBatchRow> getExpiringBatches(
            @Param("branchId") UUID branchId,
            @Param("locationId") UUID locationId,
            @Param("daysThreshold") int daysThreshold,
            @Param("limit") int limit
    );
}
