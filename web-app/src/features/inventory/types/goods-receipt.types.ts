import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type GoodsReceiptStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "REJECTED"
    | "RECEIVING"
    | "PENDING_EXCEPTION_APPROVAL"
    | "POSTED"
    | "CANCELLED";

export type QcResult =
    | "PENDING"
    | "PASSED"
    | "PARTIALLY_PASSED"
    | "FAILED";

export type CostStatus = "PENDING" | "PROVISIONAL" | "FINAL";

export type GoodsReceiptRole =
    | "ADMIN"
    | "INVENTORY_STAFF"
    | "QC_STAFF"
    | "ACCOUNTANT"
    | "MANAGER"
    | "DIRECTOR";

export type GoodsReceiptBatchStatus =
    | "AWAITING_QC"
    | "AVAILABLE"
    | "QUARANTINED"
    | "RETURN_TO_SUPPLIER"
    | "POSTED";

export interface SupplierSummary {
    id: string;
    code: string;
    name: string;
}

export interface GoodsReceiptSummary {
    id: string;
    code: string;
    supplier: SupplierSummary;
    purchaseOrderCode?: string;
    receivingLocation: InventoryLocationSummary;
    totalLines: number;
    totalReceivedQuantity: number;
    qcResult: QcResult;
    costStatus: CostStatus;
    status: GoodsReceiptStatus;
    receivedAt: string;
    createdBy: UserSnapshot;
    hasVariance: boolean;
    stockDocumentId?: string;
    stockDocumentCode?: string;
}

export interface GoodsReceiptDetail extends GoodsReceiptSummary {
    expectedDate?: string;
    invoiceNumber?: string;
    deliveryPerson: string;
    vehiclePlate?: string;
    note?: string;
    outsidePo: boolean;
    outsidePoReason?: string;
    lines: GoodsReceiptLine[];
    evidence: GoodsReceiptEvidence[];
    auditEvents: InventoryAuditEvent[];
    movementSummary?: GoodsReceiptMovementSummary;
}

export interface GoodsReceiptLine {
    id: string;
    materialId: string;
    skuCode: string;
    materialName: string;
    unit: string;
    orderedQuantity?: number;
    receivedQuantity: number;
    initialDamagedQuantity: number;
    shortageQuantity: number;
    surplusQuantity: number;
    conversionPreview: string;
    supplierLot: string;
    internalBatchCode: string;
    itemType?: string;
    manufacturedAt: string;
    expiryDate: string;
    countryOfOrigin?: string;
    qualityCertificate?: string;
    storageCondition?: string;
    qc: GoodsReceiptQcLine;
    cost: GoodsReceiptCostLine;
    batchStatus: GoodsReceiptBatchStatus;
}

export interface GoodsReceiptQcLine {
    result: QcResult;
    receivedQuantity: number;
    passedQuantity: number;
    quarantinedQuantity: number;
    failedQuantity: number;
    reasonCode?: GoodsReceiptQcReasonCode;
    inspectionNote?: string;
}

export type GoodsReceiptQcReasonCode =
    | "QLT-01"
    | "QLT-02"
    | "QLT-03"
    | "QLT-04"
    | "QLT-05"
    | "QLT-06"
    | "QLT-99";

export interface GoodsReceiptCostLine {
    purchaseUnitPrice: number;
    acceptedQuantity: number;
    discount: number;
    tax: number;
    allocatedShippingCost: number;
    otherCost: number;
    finalUnitCost: number;
    lineNetAmount: number;
    status: CostStatus;
    currency: "VND";
}

export interface GoodsReceiptEvidence {
    id: string;
    type:
    | "GOODS_PHOTO"
    | "BATCH_LABEL"
    | "DELIVERY_NOTE"
    | "INVOICE"
    | "CERTIFICATE"
    | "VARIANCE_NOTE";
    fileName: string;
    uploadedAt: string;
    uploadedBy: string;
}

export interface GoodsReceiptMovementSummary {
    availableQuantity: number;
    quarantineQuantity: number;
    returnToSupplierQuantity: number;
    totalInventoryValue: number;
}

export interface GoodsReceiptListQuery {
    page: number;
    size: number;
    receiptCode: string;
    supplierId: "ALL" | string;
    purchaseOrderCode: string;
    status: "ALL" | GoodsReceiptStatus;
    qcResult: "ALL" | QcResult;
    costStatus: "ALL" | CostStatus;
    receivingLocationId: "ALL" | string;
    receivedFrom: string;
    receivedTo: string;
    hasVariance: "ALL" | "YES" | "NO";
}

export interface GoodsReceiptDashboardSummary {
    draft: number;
    pendingApproval: number;
    approved: number;
    receiving: number;
    pendingExceptionApproval: number;
    awaitingQc: number;
    awaitingCost: number;
    readyToPost: number;
    posted: number;
    postedToday: number;
    rejected: number;
    cancelled: number;
    total: number;
}

/** Một dòng trong danh sách phiếu nhập (phân trang) — chỉ các trường hiển thị trên bảng. */
export interface GoodsReceiptListItem {
    id: string;
    code: string;
    status: GoodsReceiptStatus;
    supplierName: string;
    receivingLocationName: string;
    totalReceivedQuantity: number;
    receivedAt?: string;
    /** Keycloak subject của người tạo — dùng để xét quyền xóa. */
    createdById?: string;
}

/** Một dòng dữ liệu xuất Excel danh sách phiếu nhập (toàn bộ phiếu khớp bộ lọc). */
export interface GoodsReceiptExportRow {
    code: string;
    purchaseOrderCode?: string;
    supplierName: string;
    receivingLocationName: string;
    status: GoodsReceiptStatus;
    qcResult: QcResult;
    totalLines: number;
    totalReceivedQuantity: number;
    receivedAt?: string;
    createdByName: string;
}

export interface GoodsReceiptListResult {
    content: GoodsReceiptListItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    summary: GoodsReceiptDashboardSummary;
}

export interface GoodsReceiptOptions {
    suppliers: SupplierSummary[];
    locations: InventoryLocationSummary[];
    materials: GoodsReceiptMaterialOption[];
}

export interface GoodsReceiptMaterialOption {
    id: string;
    skuCode: string;
    name: string;
    unit: string;
    shelfLifeWarningDays: number;
}

export interface GoodsReceiptCreateInput {
    receivingLocationId: string;
    supplierId: string;
    purchaseOrderCode?: string;
    invoiceNumber?: string;
    receivedAt: string;
    deliveryPerson: string;
    vehiclePlate?: string;
    note?: string;
    outsidePo: boolean;
    outsidePoReason?: string;
    lines: GoodsReceiptCreateLineInput[];
    evidence: GoodsReceiptEvidence[];
    submit: boolean;
}

export interface GoodsReceiptCreateLineInput {
    materialId: string;
    orderedQuantity?: number;
    receivedQuantity: number;
    initialDamagedQuantity: number;
    supplierLot: string;
    manufacturedAt: string;
    expiryDate: string;
    countryOfOrigin?: string;
    qualityCertificate?: string;
    storageCondition?: string;
    duplicateReason?: string;
}

export interface GoodsReceiptQcInput {
    receiptId: string;
    lines: Array<{
        lineId: string;
        receivedQuantity: number;
        passedQuantity: number;
        quarantinedQuantity: number;
        failedQuantity: number;
        reasonCode?: GoodsReceiptQcReasonCode;
        inspectionNote?: string;
    }>;
}

export interface GoodsReceiptCostInput {
    receiptId: string;
    finalize: boolean;
    lines: Array<{
        lineId: string;
        purchaseUnitPrice: number;
        acceptedQuantity: number;
        discount: number;
        tax: number;
        allocatedShippingCost: number;
        otherCost: number;
    }>;
}

export interface GoodsReceiptActionInput {
    receiptId: string;
    note?: string;
}

export interface GoodsReceiptApi {
    getGoodsReceiptSummaryCounts(): Promise<GoodsReceiptDashboardSummary>;
    listGoodsReceipts(
        query: GoodsReceiptListQuery
    ): Promise<GoodsReceiptListResult>;
    exportGoodsReceipts(query: GoodsReceiptListQuery): Promise<GoodsReceiptExportRow[]>;
    getGoodsReceipt(receiptId: string): Promise<GoodsReceiptDetail>;
    deleteGoodsReceipt(receiptId: string): Promise<void>;
    createGoodsReceipt(
        input: GoodsReceiptCreateInput
    ): Promise<GoodsReceiptDetail>;
    updateGoodsReceipt(
        receiptId: string,
        input: GoodsReceiptCreateInput
    ): Promise<GoodsReceiptDetail>;
    submitGoodsReceipt(
        input: GoodsReceiptActionInput
    ): Promise<GoodsReceiptDetail>;
    approveGoodsReceipt(
        input: GoodsReceiptActionInput
    ): Promise<GoodsReceiptDetail>;
    rejectGoodsReceipt(
        input: GoodsReceiptActionInput & { reason: string }
    ): Promise<GoodsReceiptDetail>;
    startReceivingGoodsReceipt(
        input: GoodsReceiptActionInput
    ): Promise<GoodsReceiptDetail>;
    confirmReceivingGoodsReceipt(
        receiptId: string,
        items: Array<{ lineId: string; receivedQuantity: number; note?: string }>
    ): Promise<GoodsReceiptDetail>;
    performGoodsReceiptQc(
        input: GoodsReceiptQcInput
    ): Promise<GoodsReceiptDetail>;
    completeGoodsReceiptInspection(
        receiptId: string,
        receiveItems: Array<{ lineId: string; receivedQuantity: number; note?: string }>,
        input: GoodsReceiptQcInput
    ): Promise<GoodsReceiptDetail>;
    saveGoodsReceiptCost(
        input: GoodsReceiptCostInput
    ): Promise<GoodsReceiptDetail>;
    finalizeGoodsReceiptCost(
        input: GoodsReceiptCostInput
    ): Promise<GoodsReceiptDetail>;
    postGoodsReceipt(
        input: GoodsReceiptActionInput
    ): Promise<GoodsReceiptDetail>;
    cancelGoodsReceipt(
        input: GoodsReceiptActionInput
    ): Promise<GoodsReceiptDetail>;
    getGoodsReceiptOptions(supplierId?: string): Promise<GoodsReceiptOptions>;
}

export type AppRole = GoodsReceiptRole;
