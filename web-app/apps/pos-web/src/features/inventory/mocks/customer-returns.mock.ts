import type { InventoryAuditEvent, UserSnapshot } from "../types/stock-document.types";
import type {
    CustomerReturnDetail,
    CustomerReturnRole,
    ReturnableOrder
} from "../types/customer-return.types";

const users: Record<CustomerReturnRole, UserSnapshot> = {
    ACCOUNTANT: { id: "cr-accountant", name: "Lan Kế toán", role: "ACCOUNTANT" },
    ADMIN: { id: "cr-admin", name: "Admin Hương Vân", role: "ADMIN" },
    BRANCH_MANAGER: { id: "cr-branch", name: "Minh Quản lý CN", role: "BRANCH_MANAGER" },
    QC_STAFF: { id: "cr-qc", name: "Vy QC", role: "QC_STAFF" },
    SALES_STAFF: { id: "cr-sales", name: "An Thu ngân", role: "SALES_STAFF" },
    INVENTORY_STAFF: { id: "cr-warehouse", name: "Kho Trung tâm", role: "INVENTORY_STAFF" }
};

export const customerReturnBranches = [
    { branchCode: "Q1", code: "BR-Q1", id: "loc-q1", isCentral: false, name: "Chi nhánh Quận 1" },
    { branchCode: "Q7", code: "BR-Q7", id: "loc-q7", isCentral: false, name: "Chi nhánh Quận 7" },
    { branchCode: "WH", code: "WH-CEN", id: "loc-central", isCentral: true, name: "Kho trung tâm" }
];

export const returnableOrders: ReturnableOrder[] = [
    {
        branch: customerReturnBranches[0],
        code: "ORD-20260802-018",
        customerName: "Nguyễn Thu Hà",
        customerPhone: "0909123456",
        id: "order-return-1",
        invoiceCode: "INV-20260802-018",
        lines: [
            { batchCode: "BATCH-SEN-2606", expiryDate: "2026-11-18T00:00:00.000Z", id: "order-line-1", previouslyReturnedQuantity: 0, productId: "sku-sen", productName: "Trà sen Tây Hồ 100g", purchasedQuantity: 2, skuCode: "TEA-SEN-100", unit: "gói", unitPrice: 128000 },
            { batchCode: "BATCH-LAI-2607", expiryDate: "2026-12-05T00:00:00.000Z", id: "order-line-2", previouslyReturnedQuantity: 1, productId: "sku-lai", productName: "Trà lài đặc biệt 80g", purchasedQuantity: 3, skuCode: "TEA-LAI-080", unit: "gói", unitPrice: 96000 }
        ],
        paymentStatus: "PAID",
        purchasedAt: "2026-08-02T08:45:00.000Z"
    },
    {
        branch: customerReturnBranches[1],
        code: "ORD-20260801-041",
        customerName: "Trần Quốc Bảo",
        customerPhone: "0911555666",
        id: "order-return-2",
        invoiceCode: "INV-20260801-041",
        lines: [
            { id: "order-line-3", previouslyReturnedQuantity: 0, productId: "sku-olong", productName: "Ô long rang nhẹ 120g", purchasedQuantity: 1, skuCode: "TEA-OLG-120", unit: "gói", unitPrice: 145000 }
        ],
        paymentStatus: "PAID",
        purchasedAt: "2026-08-01T12:15:00.000Z"
    }
];

export function customerReturnActor(role: CustomerReturnRole) {
    return users[role];
}

export function customerReturnAudit(
    type: InventoryAuditEvent["type"],
    role: CustomerReturnRole,
    note: string
): InventoryAuditEvent {
    const actor = customerReturnActor(role);
    return {
        actor,
        happenedAt: new Date().toISOString(),
        id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        note,
        roleSnapshot: actor.role,
        type
    };
}

export const customerReturnMockData: CustomerReturnDetail[] = [
    {
        auditEvents: [customerReturnAudit("SUBMIT", "SALES_STAFF", "Cashier tiếp nhận yêu cầu trả hàng.")],
        branch: customerReturnBranches[0],
        code: "CRT-20260802-001",
        customerName: "Nguyễn Thu Hà",
        customerNote: "Khách báo bao bì bị bung mép.",
        customerPhone: "0909123456",
        desiredResolution: "REFUND",
        evidence: ["anh-bao-bi-loi.jpg"],
        id: "customer-return-1",
        invoiceCode: "INV-20260802-018",
        lines: [
            {
                acceptedQuantity: 0,
                batchCode: "BATCH-SEN-2606",
                condition: "OPENED",
                customerNote: "Bao bì lỗi",
                expiryDate: "2026-11-18T00:00:00.000Z",
                failedQuantity: 0,
                id: "cr-line-1",
                orderLineId: "order-line-1",
                passedQuantity: 0,
                previouslyReturnedQuantity: 0,
                productId: "sku-sen",
                productName: "Trà sen Tây Hồ 100g",
                purchasedQuantity: 2,
                reason: "PACKAGING_DEFECT",
                receivedQuantity: 0,
                requestedQuantity: 1,
                skuCode: "TEA-SEN-100",
                unit: "gói",
                unitPrice: 128000,
                unknownBatch: false
            }
        ],
        order: returnableOrders[0],
        orderCode: "ORD-20260802-018",
        paymentReference: "PAY-20260802-018",
        reason: "PACKAGING_DEFECT",
        refundStatus: "REQUESTED",
        requestedAt: "2026-08-02T09:30:00.000Z",
        requestedBy: users.SALES_STAFF,
        status: "PENDING_MANAGER_REVIEW",
        totalQuantity: 1,
        totalValue: 128000
    },
    {
        auditEvents: [customerReturnAudit("APPROVE", "QC_STAFF", "QC không đạt, yêu cầu tiêu hủy.")],
        branch: customerReturnBranches[1],
        code: "CRT-20260801-002",
        customerName: "Trần Quốc Bảo",
        customerPhone: "0911555666",
        desiredResolution: "EXCHANGE",
        disposalRequestCode: "DSP-CRT-20260801-002",
        disposalRequestId: "disposal-return-2",
        evidence: ["anh-hop-uot.jpg"],
        id: "customer-return-2",
        invoiceCode: "INV-20260801-041",
        lines: [
            {
                acceptedQuantity: 1,
                condition: "WET",
                failedQuantity: 1,
                id: "cr-line-2",
                orderLineId: "order-line-3",
                passedQuantity: 0,
                previouslyReturnedQuantity: 0,
                productId: "sku-olong",
                productName: "Ô long rang nhẹ 120g",
                purchasedQuantity: 1,
                qcCondition: "WET",
                reason: "DAMAGED",
                receivedCondition: "WET",
                receivedQuantity: 1,
                requestedQuantity: 1,
                skuCode: "TEA-OLG-120",
                unit: "gói",
                unitPrice: 145000,
                unknownBatch: true
            }
        ],
        managerReview: { accepted: true, reviewedAt: "2026-08-01T13:10:00.000Z", reviewedBy: users.BRANCH_MANAGER },
        order: returnableOrders[1],
        orderCode: "ORD-20260801-041",
        qc: { evidence: "qc-hop-uot.jpg", inspectedAt: "2026-08-01T15:00:00.000Z", inspectedBy: users.QC_STAFF, note: "Không đủ điều kiện nhập lại.", remainingShelfLifeDays: 0, sealStatus: "BROKEN" },
        reason: "DAMAGED",
        receiving: { receivedAt: "2026-08-01T14:00:00.000Z", receivedBy: users.BRANCH_MANAGER, note: "Đã nhận hàng trả, chờ kiểm định." },
        refundStatus: "EXCHANGE_ORDER_CREATED",
        exchangeOrderCode: "ORD-EX-20260801-002",
        requestedAt: "2026-08-01T12:40:00.000Z",
        requestedBy: users.SALES_STAFF,
        status: "DISPOSAL_REQUIRED",
        totalQuantity: 1,
        totalValue: 145000
    }
];

export const customerReturnOptions = {
    branches: customerReturnBranches,
    users: Object.values(users)
};
