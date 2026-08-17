import type { InventoryAuditEvent, UserSnapshot } from "../types/stock-document.types";
import type {
    SupplierReturnDetail,
    SupplierReturnOptions,
    SupplierReturnRole
} from "../types/supplier-return.types";

const users: Record<SupplierReturnRole, UserSnapshot> = {
    ACCOUNTANT: { id: "sr-accountant", name: "Lan Kế toán", role: "ACCOUNTANT" },
    ADMIN: { id: "sr-admin", name: "Admin Hương Vân", role: "ADMIN" },
    DIRECTOR: { id: "sr-director", name: "Hà Giám đốc", role: "DIRECTOR" },
    QC_STAFF: { id: "sr-qc", name: "Vy QC", role: "QC_STAFF" },
    WAREHOUSE: { id: "sr-warehouse", name: "Kho Trung tâm", role: "WAREHOUSE" }
};

const suppliers = [
    { code: "SUP-DL", id: "sup-dl", name: "Đồi Lộc Tea Farm" },
    { code: "SUP-MC", id: "sup-mc", name: "Mộc Châu Herbs" }
];

const batches: SupplierReturnOptions["batches"] = [
    { batchCode: "BATCH-SEN-2606", consumedQuantity: 12, currentAvailableQuantity: 50, currentQuarantineQuantity: 8, id: "sr-batch-1", qcResult: "FAILED", receivedQuantity: 80, receiptCode: "PNK-20260720-001", receiptId: "receipt-1", skuCode: "TEA-SEN-RAW", skuId: "sku-sen-raw", skuName: "Nguyên liệu trà sen", supplierId: "sup-dl", supplierInvoiceCode: "INV-DL-772", unit: "kg", unitCost: 180000 },
    { batchCode: "BATCH-LAI-2607", consumedQuantity: 20, currentAvailableQuantity: 35, currentQuarantineQuantity: 5, id: "sr-batch-2", qcResult: "PARTIALLY_PASSED", receivedQuantity: 70, receiptCode: "PNK-20260725-004", receiptId: "receipt-2", skuCode: "TEA-LAI-RAW", skuId: "sku-lai-raw", skuName: "Nguyên liệu trà lài", supplierId: "sup-dl", supplierInvoiceCode: "INV-DL-781", unit: "kg", unitCost: 155000 },
    { batchCode: "BATCH-OLG-2609", consumedQuantity: 4, currentAvailableQuantity: 60, currentQuarantineQuantity: 0, id: "sr-batch-3", qcResult: "FAILED", receivedQuantity: 64, receiptCode: "PNK-20260728-002", receiptId: "receipt-3", skuCode: "TEA-OLG-RAW", skuId: "sku-olg-raw", skuName: "Nguyên liệu ô long", supplierId: "sup-mc", supplierInvoiceCode: "INV-MC-221", unit: "kg", unitCost: 210000 }
];

export const supplierReturnOptions: SupplierReturnOptions = {
    batches,
    suppliers,
    users: Object.values(users)
};

export function supplierReturnActor(role: SupplierReturnRole) {
    return users[role];
}

export function supplierReturnAudit(type: InventoryAuditEvent["type"], role: SupplierReturnRole, note: string): InventoryAuditEvent {
    const actor = supplierReturnActor(role);
    return { actor, happenedAt: new Date().toISOString(), id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`, note, roleSnapshot: actor.role, type };
}

export const supplierReturnMockData: SupplierReturnDetail[] = [
    {
        auditEvents: [supplierReturnAudit("SUBMIT", "WAREHOUSE", "Tạo yêu cầu trả NCC từ batch QC không đạt.")],
        code: "SRT-20260802-001",
        createdAt: "2026-08-02T09:00:00.000Z",
        createdBy: users.WAREHOUSE,
        id: "supplier-return-1",
        lines: [
            { batchCode: "BATCH-SEN-2606", batchId: "sr-batch-1", currentAvailableQuantity: 50, currentQuarantineQuantity: 8, estimatedValue: 1800000, evidence: ["qc-mau-am-moc.jpg"], id: "sr-line-1", preparedQuantity: 0, qcResult: "FAILED", reason: "SUP-01", receivedQuantity: 80, returnQuantity: 10, returnableQuantity: 58, skuCode: "TEA-SEN-RAW", skuId: "sku-sen-raw", skuName: "Nguyên liệu trà sen", unit: "kg", unitCost: 180000 }
        ],
        note: "Mẫu có dấu hiệu mốc.",
        pickupMethod: "NCC đến lấy",
        reason: "SUP-01",
        receiptCode: "PNK-20260720-001",
        receiptId: "receipt-1",
        resolutionRequested: "CREDIT_NOTE",
        status: "SUBMITTED",
        supplier: suppliers[0],
        supplierInvoiceCode: "INV-DL-772",
        totalQuantity: 10,
        totalSku: 1,
        totalValue: 1800000
    },
    {
        auditEvents: [supplierReturnAudit("POST", "WAREHOUSE", "Đã ship trả NCC, chờ hàng thay thế.")],
        approvedAt: "2026-08-01T10:00:00.000Z",
        approvedBy: users.ADMIN,
        carrier: "Giao nhận nội bộ",
        code: "SRT-20260801-002",
        createdAt: "2026-08-01T08:00:00.000Z",
        createdBy: users.WAREHOUSE,
        id: "supplier-return-2",
        lines: [
            { batchCode: "BATCH-OLG-2609", batchId: "sr-batch-3", currentAvailableQuantity: 60, currentQuarantineQuantity: 0, estimatedValue: 1050000, evidence: ["sai-lot.jpg"], id: "sr-line-2", preparedQuantity: 5, qcResult: "FAILED", reason: "SUP-06", receivedQuantity: 64, returnQuantity: 5, returnableQuantity: 60, skuCode: "TEA-OLG-RAW", skuId: "sku-olg-raw", skuName: "Nguyên liệu ô long", unit: "kg", unitCost: 210000 }
        ],
        packageCount: 1,
        pickupMethod: "Gửi xe tải",
        reason: "SUP-06",
        receiptCode: "PNK-20260728-002",
        receiptId: "receipt-3",
        resolutionRequested: "REPLACEMENT",
        shippedAt: "2026-08-01T14:00:00.000Z",
        shippingAttachment: "bien-ban-ban-giao.pdf",
        status: "REPLACEMENT_PENDING",
        stockDocumentCode: "PXK-SRT-20260801-002",
        stockDocumentId: "doc-supplier-return-2",
        supplier: suppliers[1],
        supplierInvoiceCode: "INV-MC-221",
        supplierReceiver: "Anh Tâm",
        totalQuantity: 5,
        totalSku: 1,
        totalValue: 1050000,
        trackingCode: "TRK-SRT-002"
    }
];
