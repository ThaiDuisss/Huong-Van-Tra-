import type {
    FinishedSkuOption,
    MaterialSkuOption,
    ProductionBatchAllocation,
    ProductionOrder,
    ProductionOrderMaterial,
    ProductionOrderOutput,
    ProductionOrderStatus,
    ProductionPriority,
    ProductionRole
} from "../types/production-order.types";
import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "../types/stock-document.types";

const now = new Date("2026-08-02T12:00:00+07:00").getTime();

function dateHoursAgo(hours: number) {
    return new Date(now - hours * 60 * 60 * 1000).toISOString();
}

function dateHoursAhead(hours: number) {
    return new Date(now + hours * 60 * 60 * 1000).toISOString();
}

export const productionUsers: UserSnapshot[] = [
    { id: "po-admin", name: "Phạm Thu Hương", role: "ADMIN" },
    { id: "po-warehouse", name: "Nguyễn Minh Anh", role: "INVENTORY_STAFF" },
    { id: "po-accountant", name: "Lê Hoài Nam", role: "ACCOUNTANT" },
    { id: "po-director", name: "Vũ Thanh Hà", role: "DIRECTOR" },
    { id: "po-qc", name: "Đặng Mai Chi", role: "QC_STAFF" }
];

export const productionLocations: InventoryLocationSummary[] = [
    {
        branchCode: "CENTRAL",
        code: "MFG-CTR",
        id: "loc-mfg-central",
        isCentral: true,
        name: "Xưởng sản xuất trung tâm"
    },
    {
        branchCode: "Q1",
        code: "MFG-Q1",
        id: "loc-mfg-q1",
        isCentral: false,
        name: "Xưởng chi nhánh Quận 1"
    }
];

export const finishedSkus: FinishedSkuOption[] = [
    {
        activeBomVersionId: "bom-sen-100-v3",
        activeBomVersionLabel: "BOM Trà sen 100g v3",
        id: "fg-sen-100",
        packageSize: "100g",
        skuCode: "TRA-SEN-100",
        skuName: "Trà sen 100g",
        unit: "hộp"
    },
    {
        activeBomVersionId: "bom-sen-250-v2",
        activeBomVersionLabel: "BOM Trà sen 250g v2",
        id: "fg-sen-250",
        packageSize: "250g",
        skuCode: "TRA-SEN-250",
        skuName: "Trà sen 250g",
        unit: "hộp"
    },
    {
        id: "fg-no-bom",
        packageSize: "500g",
        skuCode: "TRA-NEW-500",
        skuName: "Trà mới chưa có BOM",
        unit: "hộp"
    }
];

export const materialSkus: MaterialSkuOption[] = [
    {
        conversionFactor: 1,
        id: "mat-olong",
        name: "Trà Ô Long thô",
        skuCode: "NL-OLONG-01",
        specification: "kg"
    },
    {
        conversionFactor: 1,
        id: "mat-sen",
        name: "Hoa sen sấy",
        skuCode: "NL-SEN-02",
        specification: "kg"
    },
    {
        conversionFactor: 100,
        id: "mat-box",
        name: "Bao bì hộp trà",
        skuCode: "BB-HOP-03",
        specification: "thùng 100 hộp"
    }
];

function user(role: ProductionRole) {
    return productionUsers.find((item) => item.role === role) ?? productionUsers[0];
}

function audit(
    type: InventoryAuditEvent["type"],
    role: ProductionRole,
    hoursAgo: number,
    note?: string
): InventoryAuditEvent {
    const actor = user(role);
    return {
        actor,
        happenedAt: dateHoursAgo(hoursAgo),
        id: `${type}-${role}-${hoursAgo}`,
        note,
        roleSnapshot: actor.role,
        type
    };
}

function allocation(index: number, allocatedQuantity: number): ProductionBatchAllocation[] {
    return [
        {
            allocatedQuantity,
            availableQuantity: allocatedQuantity + 24,
            batchCode: `BATCH-FEFO-${2600 + index}`,
            blocked: false,
            expiryDate: dateHoursAhead(24 * (30 + index)),
            id: `alloc-${index}-1`,
            locationName: "Kho nguyên liệu A",
            remainingAfterAllocation: 24,
            warning: index % 3 === 0 ? "Gần hết hạn" : undefined
        },
        {
            allocatedQuantity: Math.round(allocatedQuantity * 0.35),
            availableQuantity: Math.round(allocatedQuantity * 0.5),
            batchCode: `BATCH-FEFO-${2700 + index}`,
            blocked: index % 5 === 0,
            expiryDate: dateHoursAhead(24 * (90 + index)),
            id: `alloc-${index}-2`,
            locationName: "Kho nguyên liệu B",
            remainingAfterAllocation: Math.round(allocatedQuantity * 0.15)
        }
    ];
}

function outputs(index: number): ProductionOrderOutput[] {
    return [
        {
            bomVersionId: "bom-sen-100-v3",
            bomVersionLabel: "BOM Trà sen 100g v3",
            completedQuantity: index > 3 ? 60 : index === 2 ? 40 : 0,
            finishedSkuId: "fg-sen-100",
            packageSize: "100g",
            plannedExpiryAt: dateHoursAhead(24 * 180),
            plannedQuantity: 100 + index * 10,
            rejectedQuantity: index === 2 ? 5 : 0,
            skuCode: "TRA-SEN-100",
            skuName: "Trà sen 100g",
            unit: "hộp"
        },
        {
            bomVersionId: "bom-sen-250-v2",
            bomVersionLabel: "BOM Trà sen 250g v2",
            completedQuantity: index > 4 ? 25 : 0,
            finishedSkuId: "fg-sen-250",
            packageSize: "250g",
            plannedExpiryAt: dateHoursAhead(24 * 200),
            plannedQuantity: 50,
            rejectedQuantity: 0,
            skuCode: "TRA-SEN-250",
            skuName: "Trà sen 250g",
            unit: "hộp"
        }
    ];
}

function materials(index: number, shortage: boolean): ProductionOrderMaterial[] {
    return materialSkus.map((material, materialIndex) => {
        const required = materialIndex === 0 ? 36 + index : materialIndex === 1 ? 12 + index : 2;
        const available = shortage && materialIndex === 1 ? required - 4 : required + 30;
        return {
            allocations: allocation(index + materialIndex, Math.min(required, available)),
            availableQuantity: available,
            baseUnit: material.specification.includes("kg") ? "kg" : "cái",
            bomVersionSource:
                materialIndex === 0
                    ? "BOM Trà sen 100g v3, BOM Trà sen 250g v2"
                    : "BOM Trà sen 100g v3",
            consumedBaseQuantity: index > 4 ? Math.round(required * 0.7) : 0,
            conversionFactor: material.conversionFactor,
            hasAlternative: materialIndex !== 2,
            materialProductId: material.id,
            materialProductName: material.name,
            preferredSkuCode: material.skuCode,
            requiredBaseQuantity: required,
            requiredSkuQuantity: required / material.conversionFactor,
            reservedBaseQuantity: shortage ? Math.max(0, available) : required,
            selectedSkuCode: material.skuCode,
            selectedSkuId: material.id,
            specification: material.specification,
            usedByOutputs: ["TRA-SEN-100", "TRA-SEN-250"],
            wasteTolerancePercent: materialIndex === 2 ? 2 : 5
        };
    });
}

function order(
    index: number,
    status: ProductionOrderStatus,
    priority: ProductionPriority,
    shortage = false
): ProductionOrder {
    const orderOutputs = outputs(index);
    const plannedTotal = orderOutputs.reduce(
        (sum, item) => sum + item.plannedQuantity,
        0
    );
    const completedTotal = orderOutputs.reduce(
        (sum, item) => sum + item.completedQuantity,
        0
    );
    const costGoodOutput = Math.max(1, completedTotal || plannedTotal);

    return {
        auditEvents: [
            audit("CREATE_DRAFT", "INVENTORY_STAFF", 72 - index, "Tạo lệnh sản xuất."),
            ...(status !== "DRAFT"
                ? [audit("SUBMIT", "INVENTORY_STAFF", 68 - index, "Submit for Approval.")]
                : []),
            ...(status !== "DRAFT" && status !== "PENDING_APPROVAL"
                ? [audit("APPROVE", "DIRECTOR", 60 - index, "Duyệt lệnh sản xuất.")]
                : []),
            ...(status === "COMPLETED"
                ? [audit("POST", "INVENTORY_STAFF", 12 - index, "Hoàn thành sản xuất.")]
                : [])
        ],
        code: `LSX-2026-${String(index + 1).padStart(5, "0")}`,
        completions:
            status === "PARTIALLY_COMPLETED" || status === "COMPLETED"
                ? [
                      {
                          completedAt: dateHoursAgo(10 - index),
                          completedBy: user("INVENTORY_STAFF"),
                          completionNumber: 1,
                          expiryDate: dateHoursAhead(24 * 180),
                          goodQuantity: completedTotal || 50,
                          id: `completion-${index}-1`,
                          manufacturingDate: dateHoursAgo(12 - index),
                          materialConsumedQuantity: 28,
                          newBatchCode: `FG-BATCH-${index + 1}`,
                          operationId: `PRODUCTION_COMPLETE:production-order-${index + 1}:1`,
                          qcNote: "Thành phẩm đạt cảm quan.",
                          rejectedQuantity: index === 2 ? 5 : 0,
                          wasteQuantity: 1.2
                      }
                  ]
                : [],
        cost: {
            actualGoodOutput: costGoodOutput,
            allocatedOverhead: 1200000 + index * 50000,
            directProductionCost: 2200000 + index * 70000,
            materialCost: 8200000 + index * 120000,
            packagingCost: 1400000 + index * 30000,
            totalProductionCost: 13000000 + index * 250000,
            unitProductionCost:
                (13000000 + index * 250000) / costGoodOutput
        },
        createdAt: dateHoursAgo(80 - index),
        createdBy: user("INVENTORY_STAFF"),
        dueAt: index === 6 ? dateHoursAgo(2) : dateHoursAhead(24 * (index + 2)),
        finishedReceiptDocumentCode:
            status === "COMPLETED" ? `PNK-SX-${index + 1}` : undefined,
        finishedReceiptDocumentId:
            status === "COMPLETED" ? `stock-doc-prod-receipt-${index + 1}` : undefined,
        hasShortage: shortage,
        id: `production-order-${index + 1}`,
        location: productionLocations[index % productionLocations.length],
        materialIssueDocumentCode:
            status === "COMPLETED" ? `PXK-SX-${index + 1}` : undefined,
        materialIssueDocumentId:
            status === "COMPLETED" ? `stock-doc-prod-issue-${index + 1}` : undefined,
        materials: materials(index, shortage),
        note: "Make-to-Stock từ BOM active, giữ nguyên liệu theo FEFO.",
        outputs: orderOutputs,
        owner: user("INVENTORY_STAFF"),
        plannedStartAt: dateHoursAgo(24 - index),
        priority,
        progressPercent: Math.min(100, Math.round((completedTotal / plannedTotal) * 100)),
        replenishmentReference: index % 2 === 0 ? `REP-${3200 + index}` : undefined,
        status,
        title: `Sản xuất trà sen đợt ${index + 1}`
    };
}

export const productionOrderMockData: ProductionOrder[] = [
    order(0, "DRAFT", "NORMAL"),
    order(1, "PENDING_APPROVAL", "HIGH"),
    order(2, "RELEASED", "URGENT", true),
    order(3, "IN_PROGRESS", "NORMAL"),
    order(4, "PARTIALLY_COMPLETED", "HIGH"),
    order(5, "COMPLETED", "NORMAL"),
    order(6, "IN_PROGRESS", "URGENT", true),
    order(7, "CANCELLED", "LOW")
];

export const productionOrderMockOptions = {
    finishedSkus,
    locations: productionLocations,
    materialSkus,
    users: productionUsers
};

export function productionActor(role: ProductionRole) {
    return user(role);
}
