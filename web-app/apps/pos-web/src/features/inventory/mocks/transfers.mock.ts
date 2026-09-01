import type { InventoryAuditEvent, UserSnapshot } from "../types/stock-document.types";
import type { TransferDetail, TransferRole, TransferOptions } from "../types/transfer.types";
import {
    inventoryMasterFinishedProducts,
    inventoryMasterLocations,
    inventoryMasterMaterials,
    inventoryMasterUsers
} from "./inventory-master-data.mock";

const users: Record<TransferRole, UserSnapshot> = {
    ACCOUNTANT: inventoryMasterUsers.accountant,
    ADMIN: inventoryMasterUsers.admin,
    BRANCH_MANAGER: inventoryMasterUsers.branchManager,
    DIRECTOR: inventoryMasterUsers.director,
    INVENTORY_STAFF: inventoryMasterUsers.warehouse,
    SALES_STAFF: inventoryMasterUsers.sales
};

export const transferLocations = [
    inventoryMasterLocations.central,
    inventoryMasterLocations.branchCauGiay,
    inventoryMasterLocations.branchTayMo
];

export const transferSkus: TransferOptions["skus"] = [
    {
        batches: [
            { availableQuantity: 80, batchCode: "BATCH-SEN-2606", blocked: false, expired: false, expiryDate: "2026-11-18T00:00:00.000Z", id: "batch-sen-1" },
            { availableQuantity: 60, batchCode: "BATCH-SEN-2608", blocked: false, expired: false, expiryDate: "2027-01-15T00:00:00.000Z", id: "batch-sen-2" }
        ],
        category: inventoryMasterFinishedProducts[0].category,
        destinationAvailableQuantity: 8,
        destinationMinimumStock: 30,
        id: inventoryMasterFinishedProducts[0].id,
        name: inventoryMasterFinishedProducts[0].name,
        skuCode: inventoryMasterFinishedProducts[0].skuCode,
        sourceAvailableQuantity: 140,
        unit: "gói",
        unitCost: 98000
    },
    {
        batches: [
            { availableQuantity: 55, batchCode: "BATCH-LAI-2607", blocked: false, expired: false, expiryDate: "2026-12-05T00:00:00.000Z", id: "batch-lai-1" },
            { availableQuantity: 20, batchCode: "BATCH-LAI-BLOCK", blocked: true, expired: false, expiryDate: "2027-02-02T00:00:00.000Z", id: "batch-lai-2" }
        ],
        category: inventoryMasterMaterials[2].category,
        destinationAvailableQuantity: 12,
        destinationMinimumStock: 25,
        id: inventoryMasterMaterials[2].id,
        name: inventoryMasterMaterials[2].name,
        skuCode: inventoryMasterMaterials[2].skuCode,
        sourceAvailableQuantity: 75,
        unit: "gói",
        unitCost: 76000
    },
    {
        batches: [
            { availableQuantity: 120, batchCode: "BATCH-OLG-2609", blocked: false, expired: false, expiryDate: "2027-03-20T00:00:00.000Z", id: "batch-olg-1" }
        ],
        category: inventoryMasterMaterials[0].category,
        destinationAvailableQuantity: 18,
        destinationMinimumStock: 40,
        id: inventoryMasterMaterials[0].id,
        name: inventoryMasterMaterials[0].name,
        skuCode: inventoryMasterMaterials[0].skuCode,
        sourceAvailableQuantity: 120,
        unit: "gói",
        unitCost: 115000
    }
];

export function transferActor(role: TransferRole) {
    return users[role];
}

export function transferAudit(
    type: InventoryAuditEvent["type"],
    role: TransferRole,
    note: string
): InventoryAuditEvent {
    const actor = transferActor(role);
    return {
        actor,
        happenedAt: new Date().toISOString(),
        id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        note,
        roleSnapshot: actor.role,
        type
    };
}

export const transferMockOptions: TransferOptions = {
    locations: transferLocations,
    skus: transferSkus,
    users: Object.values(users)
};

export const transferMockData: TransferDetail[] = [
    {
        auditEvents: [transferAudit("SUBMIT", "BRANCH_MANAGER", "Chi nhánh tạo yêu cầu chuyển kho.")],
        code: "TRF-20260802-001",
        destinationLocation: transferLocations[1],
        discrepancies: [],
        expectedReceiveAt: "2026-08-03T10:00:00.000Z",
        hasDiscrepancy: false,
        id: "transfer-1",
        lines: [
            {
                allocations: [],
                approvedQuantity: 0,
                damagedQuantity: 0,
                destinationAvailableQuantity: 8,
                destinationMinimumStock: 30,
                extraQuantity: 0,
                id: "trf-line-1",
                missingQuantity: 0,
                note: "Bổ sung theo cảnh báo tồn thấp",
                receivedGoodQuantity: 0,
                requestedQuantity: 40,
                shippedQuantity: 0,
                skuCode: inventoryMasterFinishedProducts[0].skuCode,
                skuId: inventoryMasterFinishedProducts[0].id,
                skuName: inventoryMasterFinishedProducts[0].name,
                sourceAvailableQuantity: 140,
                unit: "gói"
            }
        ],
        priority: "HIGH",
        reason: "Chi nhánh Q1 dưới tồn tối thiểu.",
        requestedAt: "2026-08-02T08:15:00.000Z",
        requestedBy: users.BRANCH_MANAGER,
        sourceLocation: transferLocations[0],
        status: "REQUESTED",
        totalQuantity: 40,
        totalSku: 1,
        totalValue: 3920000
    },
    {
        auditEvents: [transferAudit("POST", "INVENTORY_STAFF", "Đã xuất kho nguồn, hàng đang IN_TRANSIT.")],
        code: "TRF-20260801-004",
        destinationLocation: transferLocations[2],
        discrepancies: [],
        expectedReceiveAt: "2026-08-02T17:30:00.000Z",
        hasDiscrepancy: false,
        id: "transfer-2",
        issueDocumentCode: "PXK-TRF-20260801-004",
        issueDocumentId: "doc-transfer-issue-2",
        lines: [
            {
                allocations: [
                    { batchCode: "BATCH-OLG-2609", batchId: "batch-olg-1", expiryDate: "2027-03-20T00:00:00.000Z", id: "alloc-2-1", picked: true, pickedQuantity: 25, reservedQuantity: 25 }
                ],
                approvedQuantity: 25,
                damagedQuantity: 0,
                destinationAvailableQuantity: 18,
                destinationMinimumStock: 40,
                extraQuantity: 0,
                id: "trf-line-2",
                missingQuantity: 0,
                receivedGoodQuantity: 0,
                requestedQuantity: 25,
                shippedQuantity: 25,
                skuCode: inventoryMasterMaterials[0].skuCode,
                skuId: inventoryMasterMaterials[0].id,
                skuName: inventoryMasterMaterials[0].name,
                sourceAvailableQuantity: 120,
                unit: "gói"
            }
        ],
        priority: "NORMAL",
        reason: "Bổ sung tuần.",
        requestedAt: "2026-08-01T07:00:00.000Z",
        requestedBy: users.BRANCH_MANAGER,
        shipment: {
            carrierName: "Tài xế nội bộ",
            expectedArrivalAt: "2026-08-02T17:30:00.000Z",
            internalTrackingCode: "INT-TRK-004",
            packageCount: 2,
            shippedAt: "2026-08-02T09:20:00.000Z",
            vehiclePlate: "51A-888.88"
        },
        sourceLocation: transferLocations[0],
        status: "SHIPPED",
        totalQuantity: 25,
        totalSku: 1,
        totalValue: 2875000
    },
    {
        auditEvents: [transferAudit("POST", "BRANCH_MANAGER", "Nhận hàng thiếu 2 gói và hỏng 1 gói.")],
        code: "TRF-20260731-002",
        destinationLocation: transferLocations[1],
        discrepancies: [
            { id: "disc-3-1", lineId: "trf-line-3", quantity: 2, responsibleParty: "Đơn vị vận chuyển nội bộ", resolutionStatus: "OPEN", type: "SHORTAGE" },
            { evidence: "anh-hop-bi-mop.jpg", id: "disc-3-2", lineId: "trf-line-3", quantity: 1, responsibleParty: "Kho nguồn", resolutionStatus: "INVESTIGATING", type: "DAMAGE" }
        ],
        expectedReceiveAt: "2026-08-01T12:00:00.000Z",
        hasDiscrepancy: true,
        id: "transfer-3",
        issueDocumentCode: "PXK-TRF-20260731-002",
        issueDocumentId: "doc-transfer-issue-3",
        lines: [
            {
                allocations: [
                    { batchCode: "BATCH-LAI-2607", batchId: "batch-lai-1", expiryDate: "2026-12-05T00:00:00.000Z", id: "alloc-3-1", picked: true, pickedQuantity: 30, reservedQuantity: 30 }
                ],
                approvedQuantity: 30,
                damagedQuantity: 1,
                destinationAvailableQuantity: 12,
                destinationMinimumStock: 25,
                extraQuantity: 0,
                id: "trf-line-3",
                missingQuantity: 2,
                receivedGoodQuantity: 27,
                requestedQuantity: 30,
                shippedQuantity: 30,
                skuCode: inventoryMasterMaterials[2].skuCode,
                skuId: inventoryMasterMaterials[2].id,
                skuName: inventoryMasterMaterials[2].name,
                sourceAvailableQuantity: 75,
                unit: "gói"
            }
        ],
        priority: "HIGH",
        reason: "Bù hàng sự kiện cuối tuần.",
        receiptDocumentCode: "PNK-TRF-20260731-002",
        receiptDocumentId: "doc-transfer-receipt-3",
        requestedAt: "2026-07-31T06:30:00.000Z",
        requestedBy: users.BRANCH_MANAGER,
        shipment: {
            carrierName: "Tài xế nội bộ",
            expectedArrivalAt: "2026-08-01T12:00:00.000Z",
            internalTrackingCode: "INT-TRK-002",
            packageCount: 3,
            shippedAt: "2026-07-31T15:30:00.000Z",
            vehiclePlate: "51B-222.11"
        },
        sourceLocation: transferLocations[0],
        status: "PARTIALLY_RECEIVED",
        totalQuantity: 30,
        totalSku: 1,
        totalValue: 2280000
    }
];
