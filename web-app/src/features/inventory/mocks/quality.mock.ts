import type { InventoryAuditEvent, UserSnapshot } from "../types/stock-document.types";
import type { BatchQualityDetail, DisposalRequest, AppRole } from "../types/quality.types";
import {
    inventoryMasterBatches,
    inventoryMasterDocuments,
    inventoryMasterLocations,
    inventoryMasterMaterials,
    inventoryMasterUsers
} from "./inventory-master-data.mock";

const users: Record<AppRole, UserSnapshot> = {
    SUPER_ADMIN: inventoryMasterUsers.admin,
    MANAGER: inventoryMasterUsers.manager,
    INVENTORY_STAFF: inventoryMasterUsers.warehouse,
    SALE_STAFF: inventoryMasterUsers.sales,
    QC: inventoryMasterUsers.qc
};

export const qualityLocations = [
    inventoryMasterLocations.central,
    inventoryMasterLocations.centralQuarantine,
    inventoryMasterLocations.centralDamaged,
    inventoryMasterLocations.branchCauGiay
];

export function qualityActor(role: AppRole) {
    return users[role];
}

export function qualityAudit(type: InventoryAuditEvent["type"], role: AppRole, note: string): InventoryAuditEvent {
    const actor = qualityActor(role);
    return { actor, happenedAt: new Date().toISOString(), id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`, note, roleSnapshot: actor.role, type };
}

export const qualityBatches: BatchQualityDetail[] = [
    {
        auditEvents: [qualityAudit("APPROVE", "QC", "Batch hết hạn, hệ thống block khỏi available nhưng không xóa tồn vật lý.")],
        availableQuantity: 0,
        batchCode: inventoryMasterBatches.expired.batchCode,
        blockedQuantity: 18,
        damagedQuantity: 0,
        documentLinks: [{ code: inventoryMasterDocuments.goodsReceipt.code, id: "receipt-1", type: "GOODS_RECEIPT" }],
        estimatedValue: 3240000,
        expiryDate: "2026-07-30T00:00:00.000Z",
        id: inventoryMasterBatches.expired.id,
        location: qualityLocations[0],
        locationBalances: [{ blockedQuantity: 18, location: qualityLocations[0], quantity: 18 }],
        manufacturingDate: "2026-01-30T00:00:00.000Z",
        movements: [{ happenedAt: "2026-07-30T00:00:00.000Z", id: "mv-1", quantity: 18, referenceCode: "EXPIRY-SCAN", type: "BLOCK_EXPIRED" }],
        onHandQuantity: 18,
        productType: "Nguyên liệu",
        qcResult: "FAILED",
        qualityEvents: [{ assignee: users.QC, batchCode: inventoryMasterBatches.expired.batchCode, batchId: inventoryMasterBatches.expired.id, code: "QCASE-001", evidence: ["expired-label.jpg"], id: "case-1", quantityAffected: 18, reason: "Batch hết hạn", recommendedAction: "DISPOSAL", referenceCode: "EXPIRY-SCAN", severity: "CRITICAL", slaDueAt: "2026-08-03T09:00:00.000Z", sourceType: "EXPIRY_SCAN", status: "OPEN" }],
        reason: "Hết hạn",
        receiptCode: inventoryMasterDocuments.goodsReceipt.code,
        receiptId: "receipt-1",
        reservations: [],
        reservedQuantity: 0,
        skuCode: inventoryMasterMaterials[1].skuCode,
        skuName: inventoryMasterMaterials[1].name,
        status: "EXPIRED",
        supplier: "Đồi Lộc Tea Farm",
        supplierLot: "DL-SEN-LOT-07",
        unit: "kg",
        unitCost: 180000
    },
    {
        auditEvents: [qualityAudit("SUBMIT", "QC", "QC fail, đưa vào quarantine.")],
        availableQuantity: 0,
        batchCode: inventoryMasterBatches.quarantine.batchCode,
        blockedQuantity: 8,
        damagedQuantity: 0,
        documentLinks: [{ code: "PNK-20260725-004", id: "receipt-2", type: "GOODS_RECEIPT" }],
        estimatedValue: 1240000,
        expiryDate: "2026-08-25T00:00:00.000Z",
        id: inventoryMasterBatches.quarantine.id,
        location: qualityLocations[1],
        locationBalances: [{ blockedQuantity: 8, location: qualityLocations[1], quantity: 8 }],
        manufacturingDate: "2026-02-25T00:00:00.000Z",
        movements: [{ happenedAt: "2026-08-01T08:00:00.000Z", id: "mv-2", quantity: 8, referenceCode: "QC-GR", type: "MOVE_QUARANTINE" }],
        onHandQuantity: 8,
        productType: "Nguyên liệu",
        qcResult: "PENDING",
        qualityEvents: [],
        quarantineSince: "2026-07-25T08:00:00.000Z",
        reason: "QC nhập hàng fail",
        receiptCode: "PNK-20260725-004",
        receiptId: "receipt-2",
        reservations: [],
        reservedQuantity: 0,
        skuCode: inventoryMasterMaterials[3].skuCode,
        skuName: inventoryMasterMaterials[3].name,
        status: "QUARANTINE",
        supplier: "Đồi Lộc Tea Farm",
        supplierLot: "DL-LAI-LOT-08",
        unit: "kg",
        unitCost: 155000
    },
    {
        auditEvents: [qualityAudit("APPROVE", "INVENTORY_STAFF", "Phát hiện bao rách ẩm, mark damaged.")],
        availableQuantity: 0,
        batchCode: inventoryMasterBatches.damaged.batchCode,
        blockedQuantity: 6,
        damagedQuantity: 6,
        documentLinks: [],
        estimatedValue: 1260000,
        expiryDate: "2027-03-20T00:00:00.000Z",
        id: inventoryMasterBatches.damaged.id,
        location: qualityLocations[2],
        locationBalances: [{ blockedQuantity: 6, location: qualityLocations[2], quantity: 6 }],
        manufacturingDate: "2026-03-20T00:00:00.000Z",
        movements: [],
        onHandQuantity: 6,
        productType: "Nguyên liệu",
        qualityEvents: [],
        reason: "Ẩm/mốc",
        receiptCode: "PNK-20260728-002",
        receiptId: "receipt-3",
        reservations: [{ id: "res-ol-1", quantity: 2, referenceCode: "ORDER-HOLD" }],
        reservedQuantity: 2,
        skuCode: inventoryMasterMaterials[5].skuCode,
        skuName: inventoryMasterMaterials[5].name,
        status: "DAMAGED",
        supplier: "Mộc Châu Herbs",
        supplierLot: "MC-OLG-LOT-09",
        unit: "kg",
        unitCost: 210000
    },
    {
        auditEvents: [qualityAudit("SUBMIT", "QC", "Batch gần hết hạn được đưa vào dashboard cảnh báo.")],
        availableQuantity: 14,
        batchCode: inventoryMasterBatches.nearExpiry.batchCode,
        blockedQuantity: 0,
        damagedQuantity: 0,
        documentLinks: [{ code: inventoryMasterDocuments.goodsReceipt.code, id: "receipt-1", type: "GOODS_RECEIPT" }],
        estimatedValue: 3640000,
        expiryDate: inventoryMasterBatches.nearExpiry.expiryDate,
        id: inventoryMasterBatches.nearExpiry.id,
        location: qualityLocations[0],
        locationBalances: [{ blockedQuantity: 0, location: qualityLocations[0], quantity: 16 }],
        manufacturingDate: "2026-02-08T00:00:00.000Z",
        movements: [{ happenedAt: "2026-08-02T08:00:00.000Z", id: "mv-near-1", quantity: 16, referenceCode: inventoryMasterDocuments.goodsReceipt.code, type: "RECEIPT" }],
        onHandQuantity: 16,
        productType: "Nguyên liệu",
        qualityEvents: [],
        reason: "Sắp hết hạn",
        receiptCode: inventoryMasterDocuments.goodsReceipt.code,
        receiptId: "receipt-1",
        reservations: [{ id: "res-near-1", quantity: 2, referenceCode: "ORDER-HOLD-CG" }],
        reservedQuantity: 2,
        skuCode: inventoryMasterMaterials[2].skuCode,
        skuName: inventoryMasterMaterials[2].name,
        status: "NEAR_EXPIRY",
        supplier: "NCC Đà Lạt",
        supplierLot: "DL-JAS-LOT-08",
        unit: "kg",
        unitCost: 260000
    },
    {
        auditEvents: [qualityAudit("POST", "INVENTORY_STAFF", "Batch available sau QC passed.")],
        availableQuantity: 44,
        batchCode: inventoryMasterBatches.available.batchCode,
        blockedQuantity: 0,
        damagedQuantity: 0,
        documentLinks: [{ code: inventoryMasterDocuments.goodsReceipt.code, id: "receipt-1", type: "GOODS_RECEIPT" }],
        estimatedValue: 9240000,
        expiryDate: inventoryMasterBatches.available.expiryDate,
        id: inventoryMasterBatches.available.id,
        location: qualityLocations[0],
        locationBalances: [{ blockedQuantity: 0, location: qualityLocations[0], quantity: 44 }],
        manufacturingDate: "2026-03-20T00:00:00.000Z",
        movements: [{ happenedAt: "2026-08-01T09:00:00.000Z", id: "mv-avl-1", quantity: 44, referenceCode: inventoryMasterDocuments.goodsReceipt.code, type: "RECEIPT" }],
        onHandQuantity: 44,
        productType: "Nguyên liệu",
        qcResult: "PASSED",
        qualityEvents: [],
        receiptCode: inventoryMasterDocuments.goodsReceipt.code,
        receiptId: "receipt-1",
        reservations: [],
        reservedQuantity: 0,
        skuCode: inventoryMasterMaterials[0].skuCode,
        skuName: inventoryMasterMaterials[0].name,
        status: "AVAILABLE",
        supplier: "NCC Mộc Châu",
        supplierLot: "MC-OLG-LOT-09",
        unit: "kg",
        unitCost: 210000
    }
];

export const disposalRequests: DisposalRequest[] = [
    {
        auditEvents: [qualityAudit("SUBMIT", "INVENTORY_STAFF", "Tạo yêu cầu tiêu hủy batch hết hạn.")],
        code: "DSP-20260802-001",
        id: "disposal-1",
        lines: [{ actualDisposedQuantity: 0, batchCode: inventoryMasterBatches.expired.batchCode, batchId: inventoryMasterBatches.expired.id, estimatedValue: 1600000, evidence: ["expired-label.jpg"], id: "dsp-line-1", quantity: 5, reason: "Hết hạn", reservedQuantity: 0, skuCode: inventoryMasterMaterials[1].skuCode, skuName: inventoryMasterMaterials[1].name, unit: "kg", unitCost: 320000 }],
        location: qualityLocations[0],
        method: "DESTROY",
        note: "Tiêu hủy theo quy trình.",
        reason: "Hết hạn",
        requestedAt: "2026-08-02T08:00:00.000Z",
        requestedBy: users.INVENTORY_STAFF,
        scheduledAt: "2026-08-04T09:00:00.000Z",
        status: "SUBMITTED",
        witnesses: ["QC", "Kho"]
    }
];
