export type ImportBatchStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type ImportType = "PRODUCT" | "PRODUCT_VARIANT" | "INGREDIENT" | "GOODS_RECEIPT";

export interface ValidationError {
    field: string;
    message: string;
}

export interface ImportBatchItem {
    id: string;
    rowIndex: number;
    payload: Record<string, unknown>;
    validationErrors: ValidationError[];
    /** Số lần dòng này xuất hiện y hệt (mọi trường) trong file Excel gốc — các dòng trùng đã được gộp thành 1 item. */
    occurrenceCount: number;
    createdEntityId: string | null;
}

export interface ImportBatch {
    id: string;
    importType: ImportType;
    batchCode: string;
    status: ImportBatchStatus;
    version: number;
    sourceFileName: string | null;
    note: string | null;
    createdBy: string | null;
    submittedBy: string | null;
    submittedAt: string | null;
    approvedBy: string | null;
    approvedAt: string | null;
    rejectedBy: string | null;
    rejectedAt: string | null;
    rejectionReason: string | null;
    items: ImportBatchItem[];
    createdAt: string;
    updatedAt: string | null;
}

export interface ImportBatchQuery {
    type?: ImportType;
    status?: ImportBatchStatus;
    page?: number;
    size?: number;
    [key: string]: string | number | undefined;
}

export interface ImportPreviewItem {
    id: string;
    rowIndex: number;
    payload: Record<string, unknown>;
    validationErrors: ValidationError[];
    occurrenceCount: number;
}

export interface ImportPreviewResponse {
    importType: ImportType;
    sourceFileName: string | null;
    totalRows: number;
    validRows: number;
    errorRows: number;
    items: ImportPreviewItem[];
}

export interface SaveImportDraftItemRequest {
    rowIndex?: number;
    payload: Record<string, unknown>;
    occurrenceCount?: number;
}

export interface SaveImportDraftRequest {
    type: ImportType;
    sourceFileName?: string | null;
    items: SaveImportDraftItemRequest[];
}

