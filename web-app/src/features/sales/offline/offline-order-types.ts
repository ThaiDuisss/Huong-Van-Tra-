import type { SalesOrderDetail } from "../../order/types/sales-history.types";
import type { OfflinePosCheckoutRequestBody } from "../services/posOrder.api";

export type OfflineOrderStatus = "PENDING" | "SYNCING" | "SYNCED" | "FAILED";

export interface OfflineOrderSyncResult {
    orderId: string;
    orderCode: string;
    status?: string;
    /** FAILED = máy chủ nhận đơn nhưng chưa trừ được kho (thiếu tồn): cần kiểm tra. */
    inventoryProcessingStatus?: string;
}

export interface OfflineOrderRecord {
    /** UUID sinh tại POS, cũng là khoá idempotency gửi lên máy chủ. */
    clientOrderId: string;
    /** Mã tạm in trên hoá đơn, vd OFF-241009-3F2A. */
    tempCode: string;
    ownerId: string | null;
    cashierName: string | null;
    /** Giờ bán thật (ISO). */
    soldAt: string;
    status: OfflineOrderStatus;
    attempts: number;
    lastError: string | null;
    lastAttemptAt: string | null;
    totalAmount: number;
    itemCount: number;
    request: OfflinePosCheckoutRequestBody;
    /** Hoá đơn dựng tại máy để in lại khi cần, kể cả chưa đồng bộ. */
    receipt: SalesOrderDetail;
    /** Đơn nháp đã tạo trên máy chủ trước khi mất mạng; huỷ sau khi đồng bộ xong. */
    staleDraftOrderId: string | null;
    result: OfflineOrderSyncResult | null;
    syncedAt: string | null;
}
