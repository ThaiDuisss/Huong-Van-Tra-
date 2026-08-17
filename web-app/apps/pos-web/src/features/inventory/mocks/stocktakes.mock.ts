import type { InventoryAuditEvent, UserSnapshot } from "../types/stock-document.types";
import type { StocktakeDetail, StocktakeLine, StocktakeOptions, StocktakeRole } from "../types/stocktake.types";

const users: Record<StocktakeRole, UserSnapshot> = {
    ACCOUNTANT: { id: "st-user-accountant", name: "Lan Kế toán", role: "ACCOUNTANT" },
    ADMIN: { id: "st-user-admin", name: "Admin Hương Vân", role: "ADMIN" },
    BRANCH_MANAGER: { id: "st-user-branch", name: "Minh Quản lý CN", role: "BRANCH_MANAGER" },
    DIRECTOR: { id: "st-user-director", name: "Hà Giám đốc", role: "DIRECTOR" },
    SALES_STAFF: { id: "st-user-sales", name: "An Bán hàng", role: "SALES_STAFF" },
    WAREHOUSE: { id: "st-user-warehouse", name: "Kho Trung tâm", role: "WAREHOUSE" }
};

const locations = [
    { branchCode: "WH", code: "WH-CEN", id: "loc-central", isCentral: true, name: "Kho trung tâm" },
    { branchCode: "Q1", code: "BR-Q1", id: "loc-q1", isCentral: false, name: "Chi nhánh Quận 1" },
    { branchCode: "Q7", code: "BR-Q7", id: "loc-q7", isCentral: false, name: "Chi nhánh Quận 7" }
];

const categories = [
    { id: "cat-green", name: "Trà xanh" },
    { id: "cat-flower", name: "Trà ướp hoa" },
    { id: "cat-olong", name: "Ô long" }
];

const skus: StocktakeOptions["skus"] = [
    { batchCode: "BATCH-SEN-2606", bin: "A-01-01", category: "Trà xanh", expiryDate: "2026-11-18T00:00:00.000Z", id: "sku-sen", name: "Trà sen Tây Hồ 100g", skuCode: "TEA-SEN-100", systemQuantity: 42, unit: "gói", unitCost: 98000 },
    { batchCode: "BATCH-LAI-2607", bin: "A-02-03", category: "Trà ướp hoa", expiryDate: "2026-12-05T00:00:00.000Z", id: "sku-lai", name: "Trà lài đặc biệt 80g", skuCode: "TEA-LAI-080", systemQuantity: 30, unit: "gói", unitCost: 76000 },
    { batchCode: "BATCH-OLG-2609", bin: "B-01-02", category: "Ô long", expiryDate: "2027-03-20T00:00:00.000Z", id: "sku-olong", name: "Ô long rang nhẹ 120g", skuCode: "TEA-OLG-120", systemQuantity: 55, unit: "gói", unitCost: 115000 }
];

export const stocktakeMockOptions: StocktakeOptions = {
    categories,
    locations,
    skus,
    users: Object.values(users)
};

export function stocktakeActor(role: StocktakeRole) {
    return users[role];
}

export function stocktakeAudit(
    type: InventoryAuditEvent["type"],
    role: StocktakeRole,
    note: string
): InventoryAuditEvent {
    const actor = stocktakeActor(role);
    return {
        actor,
        happenedAt: new Date().toISOString(),
        id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        note,
        roleSnapshot: actor.role,
        type
    };
}

function lineFromSku(sku: StocktakeOptions["skus"][number], index: number): StocktakeLine {
    const movementAfterSnapshot = index === 1 ? -2 : index === 2 ? 3 : 0;
    return {
        adjustmentSelected: false,
        batchCode: sku.batchCode,
        bin: sku.bin,
        category: sku.category,
        expectedCurrentQuantity: sku.systemQuantity + movementAfterSnapshot,
        expiryDate: sku.expiryDate,
        financialImpact: 0,
        id: `st-line-${sku.id}`,
        issueFlag: false,
        movementAfterSnapshot,
        rounds: [
            { condition: "GOOD" as const, round: 1 as const }
        ],
        skuCode: sku.skuCode,
        skuId: sku.id,
        skuName: sku.name,
        snapshotQuantity: sku.systemQuantity,
        unit: sku.unit,
        variancePercent: 0,
        varianceQuantity: 0
    };
}

export const stocktakeMockData: StocktakeDetail[] = [
    {
        auditEvents: [stocktakeAudit("APPROVE", "WAREHOUSE", "Đã bắt đầu kiểm kê và đóng băng snapshot.")],
        blindCount: true,
        code: "STK-20260802-001",
        countedSku: 1,
        counterRound1: users.WAREHOUSE,
        counterRound2: users.BRANCH_MANAGER,
        hasVariance: false,
        id: "stocktake-1",
        lines: skus.map(lineFromSku),
        location: locations[0],
        note: "Cycle count nhóm trà bán chạy.",
        owner: users.WAREHOUSE,
        scheduledStartAt: "2026-08-02T08:00:00.000Z",
        scope: { batchCodes: [], categoryIds: ["cat-green"], skuIds: [] },
        scopeLabel: "Category: Trà xanh",
        snapshotFrozenAt: "2026-08-02T08:05:00.000Z",
        startedAt: "2026-08-02T08:05:00.000Z",
        status: "IN_PROGRESS",
        totalSku: 3,
        type: "CATEGORY",
        varianceLevel: "SMALL",
        varianceValue: 0
    },
    {
        adjustmentRequest: {
            createdBy: users.WAREHOUSE,
            decreaseValue: 7220000,
            evidence: "bien-ban-kiem-ke-002.jpg",
            id: "adj-stocktake-2",
            increaseValue: 0,
            note: "Mất/thất thoát sau đối chiếu camera.",
            reason: "ADJ-03 Mất/thất thoát",
            status: "PENDING_APPROVAL"
        },
        auditEvents: [stocktakeAudit("SUBMIT", "WAREHOUSE", "Tạo adjustment request từ chênh lệch lớn.")],
        blindCount: true,
        code: "STK-20260801-002",
        countedSku: 3,
        counterRound1: users.WAREHOUSE,
        counterRound2: users.BRANCH_MANAGER,
        hasVariance: true,
        id: "stocktake-2",
        lines: skus.map((sku, index) => {
            const line = lineFromSku(sku, index);
            line.rounds = [
                { condition: "GOOD", countedAt: "2026-08-01T09:00:00.000Z", countedBy: users.WAREHOUSE, countedQuantity: index === 0 ? 35 : sku.systemQuantity, round: 1 },
                { condition: "GOOD", countedAt: "2026-08-01T10:20:00.000Z", countedBy: users.BRANCH_MANAGER, countedQuantity: index === 0 ? 34 : sku.systemQuantity, note: index === 0 ? "Đếm lại vẫn thiếu" : "", round: 2 }
            ];
            line.finalPhysicalQuantity = index === 0 ? 34 : line.expectedCurrentQuantity;
            line.varianceQuantity = line.finalPhysicalQuantity - line.expectedCurrentQuantity;
            line.variancePercent = line.expectedCurrentQuantity === 0 ? 0 : (line.varianceQuantity / line.expectedCurrentQuantity) * 100;
            line.financialImpact = line.varianceQuantity * sku.unitCost;
            line.reasonCode = index === 0 ? "ADJ-03" : undefined;
            line.reasonNote = index === 0 ? "Không tìm thấy trong bin sau đối chiếu." : undefined;
            line.adjustmentSelected = index === 0;
            line.issueFlag = Math.abs(line.varianceQuantity) > 0;
            return line;
        }),
        location: locations[0],
        note: "Kiểm kê nghi ngờ chênh lệch lớn.",
        owner: users.WAREHOUSE,
        scheduledStartAt: "2026-08-01T08:00:00.000Z",
        scope: { batchCodes: [], categoryIds: [], skuIds: [] },
        scopeLabel: "Toàn kho",
        snapshotFrozenAt: "2026-08-01T08:05:00.000Z",
        startedAt: "2026-08-01T08:05:00.000Z",
        status: "PENDING_APPROVAL",
        totalSku: 3,
        type: "FULL",
        varianceLevel: "LARGE",
        varianceValue: 7220000
    },
    {
        adjustmentInDocumentCode: "DCK-IN-STK-20260710-003",
        adjustmentInDocumentId: "doc-stk-in-3",
        adjustmentOutDocumentCode: "DCK-OUT-STK-20260710-003",
        adjustmentOutDocumentId: "doc-stk-out-3",
        auditEvents: [stocktakeAudit("POST", "ADMIN", "Đã post adjustment và khóa kỳ kiểm kê.")],
        blindCount: false,
        code: "STK-20260710-003",
        countedSku: 2,
        counterRound1: users.BRANCH_MANAGER,
        hasVariance: true,
        id: "stocktake-3",
        lines: skus.slice(0, 2).map((sku, index) => {
            const line = lineFromSku(sku, index);
            line.finalPhysicalQuantity = line.expectedCurrentQuantity + (index === 0 ? 1 : -1);
            line.varianceQuantity = line.finalPhysicalQuantity - line.expectedCurrentQuantity;
            line.variancePercent = (line.varianceQuantity / line.expectedCurrentQuantity) * 100;
            line.financialImpact = line.varianceQuantity * sku.unitCost;
            line.reasonCode = index === 0 ? "ADJ-06" : "ADJ-02";
            line.adjustmentSelected = true;
            line.issueFlag = true;
            line.rounds = [{ condition: index === 0 ? "GOOD" : "DAMAGED", countedQuantity: line.finalPhysicalQuantity, countedBy: users.BRANCH_MANAGER, countedAt: "2026-07-10T09:30:00.000Z", round: 1 }];
            return line;
        }),
        location: locations[1],
        owner: users.BRANCH_MANAGER,
        scheduledStartAt: "2026-07-10T08:00:00.000Z",
        scope: { batchCodes: [], categoryIds: [], skuIds: ["sku-sen", "sku-lai"] },
        scopeLabel: "2 SKU được chọn",
        snapshotFrozenAt: "2026-07-10T08:00:00.000Z",
        startedAt: "2026-07-10T08:00:00.000Z",
        status: "POSTED",
        totalSku: 2,
        type: "SKU_SELECTED",
        varianceLevel: "SMALL",
        varianceValue: 174000
    }
];
