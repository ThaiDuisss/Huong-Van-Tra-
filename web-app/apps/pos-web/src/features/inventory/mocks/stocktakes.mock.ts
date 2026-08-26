import { inventoryMasterBatches, inventoryMasterLocationList, inventoryMasterLocations, inventoryMasterUsers } from "./inventory-master-data.mock";
import type { InventoryAuditEvent, StockDocumentRole, UserSnapshot } from "../types/stock-document.types";
import type { AdjustmentDocumentSummary, StocktakeDetail, StocktakeLine, StocktakeOptions, StocktakeRole } from "../types/stocktake.types";

export const stocktakeMockOptions: StocktakeOptions = {
    batches: Object.values(inventoryMasterBatches),
    branches: inventoryMasterLocationList,
    items: [
        { hasBatches: false, id: "item-tea-box", itemCode: "TEA-BOX", itemName: "Trà đóng hộp", trackingBatch: false, unit: "hộp" },
        { hasBatches: true, id: "item-olong-100", itemCode: "OLG-100", itemName: "Ô long 100g", trackingBatch: true, unit: "gói" },
        { hasBatches: true, id: "item-jasmine-100", itemCode: "JAS-100", itemName: "Trà nhài 100g", trackingBatch: true, unit: "gói" }
    ],
    locations: inventoryMasterLocationList.map((location) => ({ branchId: location.id, id: location.id, name: location.name })),
    users: Object.values(inventoryMasterUsers)
};

export function stocktakeActor(role: StocktakeRole): UserSnapshot {
    const map: Record<StocktakeRole, UserSnapshot> = {
        ACCOUNTANT: inventoryMasterUsers.accountant,
        ADMIN: inventoryMasterUsers.admin,
        BRANCH_MANAGER: inventoryMasterUsers.branchManager,
        DIRECTOR: inventoryMasterUsers.director,
        SALES_STAFF: inventoryMasterUsers.sales,
        WAREHOUSE: inventoryMasterUsers.warehouse
    };
    return map[role];
}

export function stocktakeAudit(type: InventoryAuditEvent["type"], role: StocktakeRole, note: string): InventoryAuditEvent {
    const actor = stocktakeActor(role);
    return {
        actor,
        happenedAt: new Date().toISOString(),
        id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        note,
        roleSnapshot: role as StockDocumentRole,
        type
    };
}

function line(id: string, itemCode: string, itemName: string, locationId: string, locationName: string, batchCode: string | null, expiryDate: string | null, snapshotQuantity: number): StocktakeLine {
    return {
        batchCode,
        countedQuantity: null,
        expiryDate,
        id,
        inventoryBatchId: batchCode ? `batch-${batchCode.toLowerCase()}` : null,
        inventoryItemCode: itemCode,
        inventoryItemId: id,
        inventoryItemName: itemName,
        locationId,
        locationName,
        note: null,
        reasonCode: null,
        snapshotQuantity,
        unit: "gói",
        varianceQuantity: null
    };
}

export const stocktakeMockData: StocktakeDetail[] = [
    {
        adjustmentDocument: null,
        approvedAt: null,
        approvedBy: null,
        auditEvents: [stocktakeAudit("CREATE_DRAFT", "WAREHOUSE", "Tạo phiên kiểm kê.")],
        branch: inventoryMasterLocations.branchCauGiay,
        countCode: "CT-20260801-001",
        countedLines: 0,
        countingCompletedAt: null,
        countingStartedAt: null,
        createdBy: inventoryMasterUsers.warehouse,
        id: "stocktake-1",
        lines: [
            line("line-1", "TEA-BOX", "Trà đóng hộp", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, null, null, 10),
            line("line-2", "OLG-100", "Ô long 100g", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, inventoryMasterBatches.available.batchCode, inventoryMasterBatches.available.expiryDate, 15)
        ],
        note: "Kiểm kê đầu tháng.",
        scopeLabel: "Kiểm kê toàn kho",
        scopeType: "FULL",
        snapshotAt: null,
        status: "DRAFT",
        totalLines: 2,
        varianceLines: 0
    },
    {
        adjustmentDocument: null,
        approvedAt: null,
        approvedBy: null,
        auditEvents: [stocktakeAudit("APPROVE", "WAREHOUSE", "Bắt đầu kiểm kê và chụp snapshot.")],
        branch: inventoryMasterLocations.branchCauGiay,
        countCode: "CT-20260802-002",
        countedLines: 2,
        countingCompletedAt: null,
        countingStartedAt: "2026-08-02T08:05:00.000Z",
        createdBy: inventoryMasterUsers.warehouse,
        id: "stocktake-2",
        lines: [
            line("line-3", "TEA-BOX", "Trà đóng hộp", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, null, null, 10),
            line("line-4", "OLG-100", "Ô long 100g", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, inventoryMasterBatches.available.batchCode, inventoryMasterBatches.available.expiryDate, 15)
        ],
        note: "Kiểm kê theo vị trí.",
        scopeLabel: "Kiểm kê theo vị trí",
        scopeType: "LOCATION",
        snapshotAt: "2026-08-02T08:05:00.000Z",
        status: "COUNTING",
        totalLines: 2,
        varianceLines: 1
    },
    {
        adjustmentDocument: {
            approvedAt: "2026-08-03T10:00:00.000Z",
            approvedBy: inventoryMasterUsers.admin,
            documentCode: "DCK-CT-20260803-003",
            documentType: "ADJUSTMENT_OUT",
            id: "adj-1",
            postedAt: null,
            referenceId: "stocktake-3",
            referenceType: "STOCK_COUNT",
            status: "APPROVED"
        } as AdjustmentDocumentSummary,
        approvedAt: "2026-08-03T09:50:00.000Z",
        approvedBy: inventoryMasterUsers.director,
        auditEvents: [stocktakeAudit("APPROVE", "WAREHOUSE", "Hoàn tất kiểm kê và tạo phiếu điều chỉnh.")],
        branch: inventoryMasterLocations.branchCauGiay,
        countCode: "CT-20260803-003",
        countedLines: 2,
        countingCompletedAt: "2026-08-03T09:30:00.000Z",
        countingStartedAt: "2026-08-03T08:05:00.000Z",
        createdBy: inventoryMasterUsers.warehouse,
        id: "stocktake-3",
        lines: [
            { ...line("line-5", "TEA-BOX", "Trà đóng hộp", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, null, null, 10), countedQuantity: 10, varianceQuantity: 0 },
            { ...line("line-6", "JAS-100", "Trà nhài 100g", inventoryMasterLocations.branchCauGiay.id, inventoryMasterLocations.branchCauGiay.name, inventoryMasterBatches.nearExpiry.batchCode, inventoryMasterBatches.nearExpiry.expiryDate, 12), countedQuantity: 9, note: "Thiếu 3 gói", reasonCode: "ADJ-03", varianceQuantity: -3 }
        ],
        note: "Kỳ đã hoàn tất.",
        scopeLabel: "Kiểm kê theo danh sách (2 dòng)",
        scopeType: "PARTIAL",
        snapshotAt: "2026-08-03T08:05:00.000Z",
        status: "COMPLETED",
        totalLines: 2,
        varianceLines: 1
    }
];
