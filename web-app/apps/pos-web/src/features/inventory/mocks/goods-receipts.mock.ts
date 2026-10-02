import {
    calculateFinalUnitCost,
    calculateLineNetAmount
} from "../constants/goods-receipt.constants";
import type {
    GoodsReceiptDetail,
    GoodsReceiptLine,
    GoodsReceiptMaterialOption,
    AppRole,
    SupplierSummary
} from "../types/goods-receipt.types";
import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "../types/stock-document.types";

const now = new Date("2026-08-02T11:00:00+07:00").getTime();

function dateHoursAgo(hours: number) {
    return new Date(now - hours * 60 * 60 * 1000).toISOString();
}

export const goodsReceiptUsers: UserSnapshot[] = [
    { id: "gr-admin", name: "Phạm Thu Hương", role: "ADMIN" },
    { id: "gr-warehouse", name: "Nguyễn Minh Anh", role: "INVENTORY_STAFF" },
    { id: "gr-qc", name: "Đặng Mai Chi", role: "QC_STAFF" },
    { id: "gr-accountant", name: "Lê Hoài Nam", role: "ACCOUNTANT" },
    { id: "gr-branch", name: "Trần Bảo Ngọc", role: "MANAGER" },
    { id: "gr-director", name: "Vũ Thanh Hà", role: "DIRECTOR" }
];

export const goodsReceiptLocations: InventoryLocationSummary[] = [
    {
        branchCode: "CENTRAL",
        code: "WH-CTR",
        id: "loc-central",
        isCentral: true,
        name: "Kho trung tâm - AWAITING_QC"
    },
    {
        branchCode: "Q1",
        code: "BR-Q1",
        id: "loc-q1",
        isCentral: false,
        name: "Chi nhánh Quận 1"
    },
    {
        branchCode: "QC",
        code: "QC-HOLD",
        id: "loc-qc",
        isCentral: true,
        name: "Khu cách ly QC"
    }
];

export const goodsReceiptSuppliers: SupplierSummary[] = [
    { code: "SUP-TRA-01", id: "sup-tay-bac", name: "Trà Tây Bắc" },
    { code: "SUP-HUE-02", id: "sup-hue", name: "Nông sản Huế" },
    { code: "SUP-BOX-03", id: "sup-packaging", name: "Bao bì An Phát" }
];

export const goodsReceiptMaterials: GoodsReceiptMaterialOption[] = [
    {
        id: "mat-matcha",
        name: "Bột matcha nguyên chất",
        shelfLifeWarningDays: 45,
        skuCode: "NL-MATCHA-01",
        unit: "kg"
    },
    {
        id: "mat-sen",
        name: "Trà sen sấy",
        shelfLifeWarningDays: 60,
        skuCode: "NL-SEN-02",
        unit: "kg"
    },
    {
        id: "mat-olong",
        name: "Trà ô long thô",
        shelfLifeWarningDays: 50,
        skuCode: "NL-OLONG-03",
        unit: "kg"
    },
    {
        id: "mat-box",
        name: "Hộp thiếc trà",
        shelfLifeWarningDays: 365,
        skuCode: "BB-HOP-04",
        unit: "cái"
    }
];

function actor(role: AppRole) {
    return goodsReceiptUsers.find((user) => user.role === role) ?? goodsReceiptUsers[0];
}

function audit(
    type: InventoryAuditEvent["type"],
    role: AppRole,
    hoursAgo: number,
    note?: string,
    reason?: string
): InventoryAuditEvent {
    const auditActor = actor(role);

    return {
        actor: auditActor,
        happenedAt: dateHoursAgo(hoursAgo),
        id: `${type}-${role}-${hoursAgo}`,
        note,
        reason,
        roleSnapshot: auditActor.role,
        type
    };
}

function cost(
    acceptedQuantity: number,
    purchaseUnitPrice: number,
    status: GoodsReceiptLine["cost"]["status"]
) {
    const input = {
        acceptedQuantity,
        allocatedShippingCost: acceptedQuantity * 1200,
        discount: acceptedQuantity * 500,
        otherCost: acceptedQuantity * 300,
        purchaseUnitPrice,
        tax: acceptedQuantity * purchaseUnitPrice * 0.08
    };

    return {
        ...input,
        currency: "VND" as const,
        finalUnitCost: calculateFinalUnitCost(input),
        lineNetAmount: calculateLineNetAmount(input),
        status
    };
}

function line(
    index: number,
    material: GoodsReceiptMaterialOption,
    receivedQuantity: number,
    orderedQuantity: number | undefined,
    passedQuantity: number,
    quarantinedQuantity: number,
    failedQuantity: number,
    status: GoodsReceiptLine["batchStatus"],
    costStatus: GoodsReceiptLine["cost"]["status"]
): GoodsReceiptLine {
    const shortageQuantity = orderedQuantity
        ? Math.max(0, orderedQuantity - receivedQuantity)
        : 0;
    const surplusQuantity = orderedQuantity
        ? Math.max(0, receivedQuantity - orderedQuantity)
        : 0;

    return {
        batchStatus: status,
        conversionPreview: `1 ${material.unit} = 1 ${material.unit} nhập kho`,
        cost: cost(passedQuantity, 120000 + index * 17000, costStatus),
        countryOfOrigin: index % 2 === 0 ? "Việt Nam" : "Nhật Bản",
        expiryDate: dateHoursAgo(-24 * (120 - index * 5)),
        id: `gr-line-${index}-${material.id}`,
        initialDamagedQuantity: failedQuantity,
        internalBatchCode: `HVT-${material.skuCode}-${String(index + 1).padStart(3, "0")}`,
        manufacturedAt: dateHoursAgo(24 * (20 + index)),
        materialId: material.id,
        materialName: material.name,
        orderedQuantity,
        qc: {
            failedQuantity,
            passedQuantity,
            quarantinedQuantity,
            reasonCode: failedQuantity > 0 ? "QLT-03" : undefined,
            receivedQuantity,
            result:
                passedQuantity === 0 && failedQuantity > 0
                    ? "FAILED"
                    : quarantinedQuantity > 0 || failedQuantity > 0
                      ? "PARTIALLY_PASSED"
                      : passedQuantity > 0
                        ? "PASSED"
                        : "PENDING"
        },
        qualityCertificate: index % 2 === 0 ? `CERT-${2600 + index}` : undefined,
        receivedQuantity,
        shortageQuantity,
        skuCode: material.skuCode,
        storageCondition: "Khô ráo, tránh ánh sáng trực tiếp",
        supplierLot: `LOT-${2600 + index}`,
        surplusQuantity,
        unit: material.unit
    };
}

function detail(
    index: number,
    status: GoodsReceiptDetail["status"],
    qcResult: GoodsReceiptDetail["qcResult"],
    costStatus: GoodsReceiptDetail["costStatus"]
): GoodsReceiptDetail {
    const supplier = goodsReceiptSuppliers[index % goodsReceiptSuppliers.length];
    const location = goodsReceiptLocations[index % goodsReceiptLocations.length];
    const posted = status === "POSTED";
    const rejected = status === "REJECTED";
    const lines = [
        line(
            index,
            goodsReceiptMaterials[index % goodsReceiptMaterials.length],
            24 + index * 3,
            26 + index * 3,
            qcResult === "PENDING" || qcResult === "FAILED" ? 0 : 20 + index,
            qcResult === "PARTIALLY_PASSED" ? 3 : 0,
            qcResult === "FAILED" ? 10 : rejected ? 6 : 1,
            posted
                ? "AVAILABLE"
                : qcResult === "FAILED"
                  ? "RETURN_TO_SUPPLIER"
                  : qcResult === "PARTIALLY_PASSED"
                    ? "QUARANTINED"
                    : "AWAITING_QC",
            costStatus
        ),
        line(
            index + 10,
            goodsReceiptMaterials[(index + 1) % goodsReceiptMaterials.length],
            12 + index,
            12 + index,
            qcResult === "PENDING" || qcResult === "FAILED" ? 0 : 12 + index,
            0,
            qcResult === "FAILED" ? 12 + index : 0,
            posted ? "AVAILABLE" : "AWAITING_QC",
            costStatus
        )
    ];
    const totalReceivedQuantity = lines.reduce(
        (sum, item) => sum + item.receivedQuantity,
        0
    );

    return {
        auditEvents: [
            audit("CREATE_DRAFT", "INVENTORY_STAFF", 72 - index, "Tạo phiếu nhập nguyên liệu."),
            ...(status !== "DRAFT"
                ? [audit("SUBMIT", "INVENTORY_STAFF", 70 - index, "Submit phiếu nhập.")]
                : []),
            ...(qcResult !== "PENDING"
                ? [audit("APPROVE", "QC_STAFF", 48 - index, "Hoạt tất kiểm tra QC.")]
                : []),
            ...(costStatus !== "PENDING"
                ? [audit("APPROVE", "ACCOUNTANT", 30 - index, "Xác nhận giá nhập.")]
                : []),
            ...(posted
                ? [audit("POST", "INVENTORY_STAFF", 8 - index, "Post receipt và tạo chứng từ PNK.")]
                : []),
            ...(rejected
                ? [audit("REJECT", "QC_STAFF", 46 - index, undefined, "Nguyên liệu ẩm/mốc.")]
                : [])
        ],
        code: `GR-2026-${String(index + 1).padStart(5, "0")}`,
        costStatus,
        createdBy: actor("INVENTORY_STAFF"),
        deliveryPerson: index % 2 === 0 ? "Nguyễn Văn Giao" : "Trần Văn Lâm",
        evidence: [
            {
                fileName: `anh-hang-${index + 1}.jpg`,
                id: `ev-goods-${index}`,
                type: "GOODS_PHOTO",
                uploadedAt: dateHoursAgo(71 - index),
                uploadedBy: "Nguyễn Minh Anh"
            }
        ],
        hasVariance: lines.some(
            (item) =>
                item.shortageQuantity > 0 ||
                item.surplusQuantity > 0 ||
                item.initialDamagedQuantity > 0
        ),
        id: `goods-receipt-${index + 1}`,
        invoiceNumber: `INV-SUP-${String(7000 + index)}`,
        lines,
        movementSummary: posted
            ? {
                  availableQuantity: lines.reduce(
                      (sum, item) => sum + item.qc.passedQuantity,
                      0
                  ),
                  quarantineQuantity: lines.reduce(
                      (sum, item) => sum + item.qc.quarantinedQuantity,
                      0
                  ),
                  returnToSupplierQuantity: lines.reduce(
                      (sum, item) => sum + item.qc.failedQuantity,
                      0
                  ),
                  totalInventoryValue: lines.reduce(
                      (sum, item) => sum + item.cost.lineNetAmount,
                      0
                  )
              }
            : undefined,
        note: "Hàng vật lý ghi nhận tại AWAITING_QC, available chỉ tăng sau QC và post.",
        outsidePo: index % 3 === 0,
        outsidePoReason:
            index % 3 === 0 ? "Nhập bổ sung do nhu cầu sản xuất gấp." : undefined,
        purchaseOrderCode: index % 3 === 0 ? undefined : `PO-2026-${String(4100 + index)}`,
        qcResult,
        receivedAt: dateHoursAgo(72 - index),
        receivingLocation: location,
        status,
        stockDocumentCode: posted ? `PNK-2026-${String(index + 1).padStart(5, "0")}` : undefined,
        stockDocumentId: posted ? `stock-doc-${index + 1}` : undefined,
        supplier,
        totalLines: lines.length,
        totalReceivedQuantity,
        vehiclePlate: index % 2 === 0 ? "51C-268.88" : undefined
    };
}

export const goodsReceiptMockData: GoodsReceiptDetail[] = [
    detail(0, "DRAFT", "PENDING", "PENDING"),
    detail(1, "PENDING_APPROVAL", "PENDING", "PENDING"),
    detail(2, "APPROVED", "PASSED", "PENDING"),
    detail(3, "RECEIVING", "PASSED", "PENDING"),
    detail(4, "POSTED", "PASSED", "FINAL"),
    detail(5, "REJECTED", "FAILED", "PENDING"),
    detail(6, "PENDING_EXCEPTION_APPROVAL", "PASSED", "FINAL"),
    detail(7, "CANCELLED", "PENDING", "PENDING")
];

export const goodsReceiptMockOptions = {
    locations: goodsReceiptLocations,
    materials: goodsReceiptMaterials,
    suppliers: goodsReceiptSuppliers
};

export function goodsReceiptActorForRole(role: AppRole) {
    return actor(role);
}
