import type { InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";
import type {
    InventoryBalanceSnapshot,
    ReorderPolicy,
    StockAlert,
    StockAlertDetail,
    StockAlertRole,
    StockSkuSummary
} from "../types/stock-alert.types";
import {
    inventoryMasterFinishedProducts,
    inventoryMasterLocations,
    inventoryMasterMaterials,
    inventoryMasterUsers
} from "./inventory-master-data.mock";

export const stockAlertUsers: Record<StockAlertRole, UserSnapshot> = {
    ACCOUNTANT: inventoryMasterUsers.accountant,
    ADMIN: inventoryMasterUsers.admin,
    BRANCH_MANAGER: inventoryMasterUsers.branchManager,
    DIRECTOR: inventoryMasterUsers.director,
    SALES_STAFF: inventoryMasterUsers.sales,
    WAREHOUSE: inventoryMasterUsers.warehouse
};

export const alertLocations: InventoryLocationSummary[] = [
    inventoryMasterLocations.central,
    inventoryMasterLocations.branchCauGiay,
    inventoryMasterLocations.branchTayMo
];

export const alertSkus: StockSkuSummary[] = [
    { category: inventoryMasterMaterials[0].category, id: inventoryMasterMaterials[0].id, preferredSupplier: "NCC Mộc Châu", skuCode: inventoryMasterMaterials[0].skuCode, skuName: inventoryMasterMaterials[0].name, unit: inventoryMasterMaterials[0].unit, unitCost: inventoryMasterMaterials[0].unitCost },
    { category: inventoryMasterMaterials[2].category, id: inventoryMasterMaterials[2].id, preferredSupplier: "NCC Đà Lạt", skuCode: inventoryMasterMaterials[2].skuCode, skuName: inventoryMasterMaterials[2].name, unit: inventoryMasterMaterials[2].unit, unitCost: inventoryMasterMaterials[2].unitCost },
    { category: inventoryMasterFinishedProducts[0].category, id: inventoryMasterFinishedProducts[0].id, preferredSupplier: "Xưởng trung tâm", skuCode: inventoryMasterFinishedProducts[0].skuCode, skuName: inventoryMasterFinishedProducts[0].name, unit: inventoryMasterFinishedProducts[0].unit, unitCost: inventoryMasterFinishedProducts[0].unitCost }
];

export const reorderPolicies: ReorderPolicy[] = [
    { averageDailyConsumption: 2, enabled: true, id: "policy-cg-olong", locationId: inventoryMasterLocations.branchCauGiay.id, maximumStock: 80, minimumStock: 12, reorderPoint: 18, safetyStock: 4, skuId: inventoryMasterMaterials[0].id, standardReorderQuantity: 30, supplierLeadTimeDays: 7 },
    { averageDailyConsumption: 1.5, enabled: true, id: "policy-cg-jasmine", locationId: inventoryMasterLocations.branchCauGiay.id, maximumStock: 60, minimumStock: 8, reorderPoint: 13, safetyStock: 3, skuId: inventoryMasterMaterials[2].id, standardReorderQuantity: 20, supplierLeadTimeDays: 6 },
    { averageDailyConsumption: 12, enabled: true, id: "policy-central-lotus-100", locationId: inventoryMasterLocations.central.id, maximumStock: 1000, minimumStock: 160, reorderPoint: 220, safetyStock: 40, skuId: inventoryMasterFinishedProducts[0].id, standardReorderQuantity: 400, supplierLeadTimeDays: 15 }
];

export const balances: Array<{ locationId: string; skuId: string; balance: InventoryBalanceSnapshot }> = [
    { balance: { available: 4, availableToPromise: 2, blocked: 1, inTransitQuantity: 12, onHand: 10, reserved: 5 }, locationId: inventoryMasterLocations.branchCauGiay.id, skuId: inventoryMasterMaterials[0].id },
    { balance: { available: 0, availableToPromise: -3, blocked: 0, inTransitQuantity: 0, onHand: 3, reserved: 3 }, locationId: inventoryMasterLocations.branchCauGiay.id, skuId: inventoryMasterMaterials[2].id },
    { balance: { available: 95, availableToPromise: 90, blocked: 0, inTransitQuantity: 0, onHand: 100, reserved: 5 }, locationId: inventoryMasterLocations.central.id, skuId: inventoryMasterMaterials[0].id },
    { balance: { available: 130, availableToPromise: 125, blocked: 0, inTransitQuantity: 0, onHand: 140, reserved: 10 }, locationId: inventoryMasterLocations.central.id, skuId: inventoryMasterMaterials[2].id },
    { balance: { available: 120, availableToPromise: 110, blocked: 10, inTransitQuantity: 80, onHand: 170, reserved: 40 }, locationId: inventoryMasterLocations.central.id, skuId: inventoryMasterFinishedProducts[0].id }
];

export const stockAlerts: StockAlert[] = [];

export const purchaseRequestDrafts: Array<{ alertId: string; code: string; id: string; status: "DRAFT" }> = [];

export function makeAlertDetail(alert: StockAlert): StockAlertDetail {
    const existingPurchase = purchaseRequestDrafts.find((item) => item.alertId === alert.id);
    return {
        ...alert,
        batchBreakdown: [
            { batchCode: `${alert.sku.skuCode}-B1`, blocked: alert.balance.blocked, expiryDate: "2026-08-28T00:00:00.000Z", onHand: Math.max(0, alert.balance.onHand - 2), reserved: alert.balance.reserved, status: alert.balance.blocked > 0 ? "BLOCKED" : "AVAILABLE" },
            { batchCode: `${alert.sku.skuCode}-B2`, blocked: 0, expiryDate: "2026-09-14T00:00:00.000Z", onHand: 2, reserved: 0, status: "AVAILABLE" }
        ],
        consumptionTrend: {
            averageDailyConsumption: alert.policy.averageDailyConsumption ?? 0,
            last7Days: Math.round((alert.policy.averageDailyConsumption ?? 0) * 7),
            last30Days: Math.round((alert.policy.averageDailyConsumption ?? 0) * 30),
            note: "Mock trend tính từ sales/production movements gần nhất."
        },
        existingPurchaseRequest: existingPurchase ? { code: existingPurchase.code, estimatedValue: alert.suggestedQuantity * alert.sku.unitCost, id: existingPurchase.id, status: existingPurchase.status } : undefined,
        existingTransferRequest: alert.actionReference?.type === "TRANSFER_REQUEST" ? { code: alert.actionReference.code, id: alert.actionReference.id, status: alert.actionReference.status } : undefined,
        internalSource: alert.suggestedAction === "TRANSFER" ? {
            expectedArrival: "2026-08-04T10:00:00.000Z",
            sourceAvailableAfterSafetyStock: 70,
            sourceLocation: alertLocations[0],
            suggestedTransferQuantity: alert.suggestedQuantity
        } : undefined,
        openProductionOrders: [{ code: "LSX-20260802-001", dueAt: "2026-08-05T08:00:00.000Z", id: "po-alert-1", quantity: 8 }],
        openReservations: [{ code: "RSV-CB-001", expiresAt: "2026-08-02T18:00:00.000Z", id: "rsv-alert-1", quantity: alert.balance.reserved }]
    };
}
