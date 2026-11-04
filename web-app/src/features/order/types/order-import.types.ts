export type OrderImportStatus = "VALID" | "IMPORTED" | "DUPLICATE" | "INVALID" | "FAILED";

/** Một dòng hàng đọc từ file Excel; các dòng cùng mã đơn tham chiếu được gộp thành một đơn. */
export interface OrderImportRow {
    rowNumber: number;
    orderReference: string;
    /** yyyy-MM-ddTHH:mm nếu ô Excel kiểu ngày, ngược lại giữ nguyên chữ để server kiểm tra. */
    soldAt: string;
    customerPhone: string;
    customerName: string;
    sku: string;
    quantity: string;
    unitPrice: string;
    note: string;
}

export interface OrderImportRequest {
    rows: OrderImportRow[];
    dryRun: boolean;
}

export interface OrderImportOrderResult {
    orderReference: string | null;
    rowNumbers: number[];
    status: OrderImportStatus;
    messages: string[];
    soldAt: string | null;
    customerName: string | null;
    customerPhone: string | null;
    itemCount: number;
    totalQuantity: number;
    totalAmount: number;
    orderId: string | null;
    orderCode: string | null;
}

export interface OrderImportResponse {
    dryRun: boolean;
    totalRows: number;
    totalOrders: number;
    successCount: number;
    duplicateCount: number;
    failedCount: number;
    results: OrderImportOrderResult[];
}
