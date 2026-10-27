import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    StockDocumentDetail,
    StockDocumentLine,
    AppRole,
    StockDocumentStatus,
    StockDocumentType,
    UserSnapshot
} from "../types/stock-document.types";

const users: UserSnapshot[] = [
    { id: "user-admin", name: "Phạm Thu Hương", role: "ADMIN" },
    { id: "user-warehouse", name: "Nguyễn Minh Anh", role: "INVENTORY_STAFF" },
    { id: "user-branch", name: "Trần Bảo Ngọc", role: "MANAGER" },
    { id: "user-accountant", name: "Lê Hoài Nam", role: "ACCOUNTANT" },
    { id: "user-director", name: "Vũ Thanh Hà", role: "DIRECTOR" },
    { id: "user-qc", name: "Đặng Mai Chi", role: "QC_STAFF" }
];

const locations: InventoryLocationSummary[] = [
    {
        branchCode: "CENTRAL",
        code: "WH-CTR",
        id: "loc-central",
        isCentral: true,
        name: "Kho trung tâm"
    },
    {
        branchCode: "Q1",
        code: "BR-Q1",
        id: "loc-q1",
        isCentral: false,
        name: "Chi nhánh Quận 1"
    },
    {
        branchCode: "Q7",
        code: "BR-Q7",
        id: "loc-q7",
        isCentral: false,
        name: "Chi nhánh Quận 7"
    },
    {
        branchCode: "QC",
        code: "QC-HOLD",
        id: "loc-qc",
        isCentral: true,
        name: "Khu cách ly QC"
    }
];

const now = new Date("2026-08-02T10:30:00+07:00").getTime();

function dateHoursAgo(hours: number) {
    return new Date(now - hours * 60 * 60 * 1000).toISOString();
}

function makeAudit(
    type: InventoryAuditEvent["type"],
    actor: UserSnapshot,
    hoursAgo: number,
    note?: string,
    reason?: string
): InventoryAuditEvent {
    return {
        actor,
        happenedAt: dateHoursAgo(hoursAgo),
        id: `${type}-${actor.id}-${hoursAgo}`,
        note,
        reason,
        roleSnapshot: actor.role,
        type
    };
}

function line(
    index: number,
    skuCode: string,
    skuName: string,
    quantity: number,
    unitCost: number,
    status: StockDocumentLine["status"],
    location: InventoryLocationSummary
): StockDocumentLine {
    return {
        allocations: [
            {
                allocatedQuantity: quantity,
                batchCode: `BATCH-${skuCode}-${String(index).padStart(2, "0")}`,
                batchQuantityAfter: 120 + quantity,
                batchQuantityBefore: 120,
                expiryDate: dateHoursAgo(-24 * (80 + index)),
                id: `alloc-${skuCode}-${index}`,
                manufacturedAt: dateHoursAgo(24 * (20 + index)),
                skuCode,
                status: index % 3 === 0 ? "NEAR_EXPIRY" : "ALLOCATED",
                supplierLot: `SUP-${2600 + index}`
            }
        ],
        id: `line-${skuCode}-${index}`,
        itemType: skuCode.startsWith("NL")
            ? "INGREDIENT"
            : skuCode.startsWith("BB")
              ? "PACKAGING"
              : "PRODUCT",
        location,
        quantity,
        quantityAfter: 240 + quantity,
        quantityBefore: 240,
        skuCode,
        skuId: `sku-${skuCode}`,
        skuName,
        status,
        unit: skuCode.startsWith("NL") ? "kg" : "gói",
        unitCost
    };
}

const typeCycle: StockDocumentType[] = [
    "GOODS_RECEIPT",
    "PRODUCTION_RECEIPT",
    "TRANSFER_RECEIPT",
    "ADJUSTMENT_IN",
    "SALE_ISSUE",
    "PRODUCTION_ISSUE",
    "CUSTOM_BUNDLE_ISSUE",
    "TRANSFER_ISSUE",
    "SUPPLIER_RETURN",
    "WRITE_OFF",
    "CUSTOMER_RETURN_RECEIPT",
    "ADJUSTMENT_OUT"
];

const statusCycle: StockDocumentStatus[] = [
    "DRAFT",
    "SUBMITTED",
    "APPROVED",
    "POSTED",
    "REJECTED",
    "CANCELLED",
    "REVERSED"
];

function creatorFor(index: number) {
    return users[index % 4] ?? users[0];
}

function documentDetail(index: number): StockDocumentDetail {
    const type = typeCycle[index % typeCycle.length];
    const status = statusCycle[index % statusCycle.length];
    const createdBy = creatorFor(index);
    const approvedBy =
        status === "APPROVED" || status === "POSTED" || status === "REVERSED"
            ? users[0]
            : undefined;
    const postedBy =
        status === "POSTED" || status === "REVERSED" ? users[1] : undefined;
    const sourceLocation =
        type.includes("ISSUE") ||
        type === "SUPPLIER_RETURN" ||
        type === "WRITE_OFF" ||
        type === "ADJUSTMENT_OUT"
            ? locations[index % locations.length]
            : type === "TRANSFER_RECEIPT"
              ? locations[0]
              : undefined;
    const destinationLocation =
        type.includes("RECEIPT") || type === "ADJUSTMENT_IN"
            ? locations[(index + 1) % locations.length]
            : type === "TRANSFER_ISSUE"
              ? locations[(index + 2) % locations.length]
              : undefined;
    const createdAt = dateHoursAgo(index * 8 + 2);
    const postedAt =
        status === "POSTED" || status === "REVERSED"
            ? dateHoursAgo(index * 7 + 1)
            : undefined;
    const documentLines = [
        line(
            index,
            index % 2 === 0 ? "NL-MATCHA-01" : "TRA-SEN-100",
            index % 2 === 0 ? "Bột matcha nguyên chất" : "Trà sen Tây Hồ",
            12 + index * 3,
            180000 + index * 9000,
            status === "POSTED" ? "POSTED" : "VALID",
            destinationLocation ?? sourceLocation ?? locations[0]
        ),
        line(
            index + 10,
            index % 2 === 0 ? "BB-HOP-QUA" : "TRA-OLONG-250",
            index % 2 === 0 ? "Hộp giấy quà tặng" : "Trà ô long rang",
            6 + index,
            45000 + index * 4500,
            index % 4 === 0 ? "QC_HOLD" : "VALID",
            destinationLocation ?? sourceLocation ?? locations[1]
        )
    ];
    const totalQuantity = documentLines.reduce(
        (sum, item) => sum + item.quantity,
        0
    );
    const auditEvents = [
        makeAudit("CREATE_DRAFT", createdBy, index * 8 + 2, "Tạo draft từ nghiệp vụ gốc."),
        ...(status !== "DRAFT"
            ? [
                  makeAudit(
                      "SUBMIT",
                      createdBy,
                      index * 8 + 1.5,
                      "Submit chứng từ chờ duyệt."
                  )
              ]
            : []),
        ...(approvedBy
            ? [
                  makeAudit(
                      "APPROVE",
                      approvedBy,
                      index * 8 + 1,
                      "Duyệt chứng từ đủ điều kiện."
                  )
              ]
            : []),
        ...(status === "REJECTED"
            ? [
                  makeAudit(
                      "REJECT",
                      users[0],
                      index * 8 + 1,
                      undefined,
                      "Sai batch hoặc thiếu bằng chứng QC."
                  )
              ]
            : []),
        ...(postedBy
            ? [
                  makeAudit(
                      "POST",
                      postedBy,
                      index * 7 + 1,
                      "Post chứng từ và ghi nhận biến động tồn kho."
                  )
              ]
            : []),
        ...(status === "REVERSED"
            ? [
                  makeAudit(
                      "REVERSE",
                      users[3],
                      index * 6 + 1,
                      undefined,
                      "Đảo chứng từ do sai kho đích."
                  )
              ]
            : []),
        ...(status === "CANCELLED"
            ? [
                  makeAudit(
                      "CANCEL",
                      users[0],
                      index * 8 + 1,
                      undefined,
                      "Hủy trước khi post theo yêu cầu nghiệp vụ."
                  )
              ]
            : [])
    ];

    return {
        approvedBy,
        auditEvents,
        code: `INV-${new Date(createdAt).getFullYear()}-${String(index + 1).padStart(5, "0")}`,
        correlationId: `corr-${260000 + index}`,
        createdAt,
        createdBy,
        destinationLocation,
        id: `stock-doc-${index + 1}`,
        lines: documentLines,
        note: "Chứng từ mock phục vụ luồng tra cứu, phê duyệt, post và audit.",
        postedAt,
        postedBy,
        reasonCode:
            type === "WRITE_OFF"
                ? "EXPIRED_OR_DAMAGED"
                : type === "SUPPLIER_RETURN"
                  ? "SUPPLIER_QUALITY"
                  : "NORMAL_OPERATION",
        referenceCode: `REF-${String(5400 + index)}`,
        referenceId: `ref-${index + 1}`,
        referenceType: type.includes("PRODUCTION")
            ? "PRODUCTION_ORDER"
            : type.includes("TRANSFER")
              ? "STOCK_TRANSFER"
              : "WAREHOUSE_OPERATION",
        reversedBy: status === "REVERSED" ? users[3] : undefined,
        reversedDocumentCode:
            status === "REVERSED" ? `REV-${String(index + 1).padStart(5, "0")}` : undefined,
        reversedDocumentId:
            status === "REVERSED" ? `stock-doc-reversal-${index + 1}` : undefined,
        sourceLocation,
        status,
        totalLines: documentLines.length,
        totalQuantity,
        type
    };
}

export const stockDocumentMockOptions = {
    locations,
    users
};

export const stockDocumentMockData = Array.from(
    { length: 28 },
    (_, index) => documentDetail(index)
);

export function actorForRole(role: AppRole) {
    return users.find((user) => user.role === role) ?? users[0];
}
