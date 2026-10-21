import { ApiError } from "@/lib/api-client";

import type { OfflineOrderDb } from "./offline-order-db";
import type {
    OfflineOrderRecord,
    OfflineOrderSyncResult
} from "./offline-order-types";

/** Đơn đã đồng bộ được giữ lại 7 ngày để in lại / đối soát rồi dọn. */
export const SYNCED_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;

export function createClientOrderId(): string {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
        return crypto.randomUUID();
    }

    // Fallback cho WebView cũ: đủ ngẫu nhiên để không trùng trong một cửa hàng.
    const hex = () => Math.floor(Math.random() * 0x10000).toString(16).padStart(4, "0");
    return `${hex()}${hex()}-${hex()}-4${hex().slice(1)}-a${hex().slice(1)}-${hex()}${hex()}${hex()}`;
}

/** Mã tạm in trên hoá đơn, dễ đọc và truy lại được clientOrderId qua 4 ký tự đầu. */
export function buildOfflineTempCode(clientOrderId: string, soldAt: Date): string {
    const yy = String(soldAt.getFullYear()).slice(-2);
    const mm = String(soldAt.getMonth() + 1).padStart(2, "0");
    const dd = String(soldAt.getDate()).padStart(2, "0");
    const suffix = clientOrderId.replace(/-/g, "").slice(0, 4).toUpperCase();

    return `OFF-${yy}${mm}${dd}-${suffix}`;
}

export type SyncErrorKind = "network" | "transient" | "rejected";

/**
 * Phân loại lỗi khi replay:
 * - network: mất mạng / timeout → giữ PENDING, dừng vòng đồng bộ, thử lại khi mạng về.
 * - transient: 401/403 (token chưa sẵn), 408/429, 5xx → giữ PENDING, thử lại sau.
 * - rejected: 4xx nghiệp vụ → FAILED, thu ngân xem và xử lý tay.
 */
export function classifySyncError(error: unknown): SyncErrorKind {
    if (error instanceof ApiError) {
        if (error.isNetworkError || error.status === 0) {
            return "network";
        }

        if (
            error.status === 401 ||
            error.status === 403 ||
            error.status === 408 ||
            error.status === 429 ||
            error.status >= 500
        ) {
            return "transient";
        }

        return "rejected";
    }

    if (error instanceof TypeError) {
        // fetch ném TypeError khi không kết nối được.
        return "network";
    }

    return "rejected";
}

export function getSyncErrorMessage(error: unknown): string {
    if (error instanceof ApiError || error instanceof Error) {
        return error.message || "Lỗi không xác định";
    }

    return "Lỗi không xác định";
}

export async function enqueueOfflineOrder(
    db: OfflineOrderDb,
    record: OfflineOrderRecord
): Promise<void> {
    await db.offlineOrders.add(record);
}

export async function listOfflineOrders(db: OfflineOrderDb): Promise<OfflineOrderRecord[]> {
    const records = await db.offlineOrders.toArray();

    return records.sort((a, b) => b.soldAt.localeCompare(a.soldAt));
}

/** Đơn chờ đồng bộ theo thứ tự bán, để mã đơn trên máy chủ cũng theo đúng thứ tự. */
export async function listPendingOfflineOrders(
    db: OfflineOrderDb
): Promise<OfflineOrderRecord[]> {
    const records = await db.offlineOrders
        .where("status")
        .anyOf(["PENDING", "SYNCING"])
        .toArray();

    return records.sort((a, b) => a.soldAt.localeCompare(b.soldAt));
}

export interface OfflineOrderCounts {
    pending: number;
    failed: number;
    synced: number;
}

export async function countOfflineOrders(db: OfflineOrderDb): Promise<OfflineOrderCounts> {
    const [pending, syncing, failed, synced] = await Promise.all([
        db.offlineOrders.where("status").equals("PENDING").count(),
        db.offlineOrders.where("status").equals("SYNCING").count(),
        db.offlineOrders.where("status").equals("FAILED").count(),
        db.offlineOrders.where("status").equals("SYNCED").count()
    ]);

    return { pending: pending + syncing, failed, synced };
}

export async function markOfflineOrderSyncing(
    db: OfflineOrderDb,
    clientOrderId: string,
    now = new Date()
): Promise<void> {
    await db.offlineOrders.update(clientOrderId, {
        status: "SYNCING",
        lastAttemptAt: now.toISOString()
    });
}

export async function markOfflineOrderSynced(
    db: OfflineOrderDb,
    clientOrderId: string,
    result: OfflineOrderSyncResult,
    now = new Date()
): Promise<void> {
    const current = await db.offlineOrders.get(clientOrderId);

    if (!current) {
        return;
    }

    await db.offlineOrders.update(clientOrderId, {
        status: "SYNCED",
        result,
        lastError: null,
        syncedAt: now.toISOString(),
        attempts: current.attempts + 1,
        // Hoá đơn in lại sau này mang mã chính thức.
        receipt: {
            ...current.receipt,
            id: result.orderId,
            orderId: result.orderId,
            orderCode: result.orderCode
        }
    });
}

/** Lỗi tạm: giữ PENDING để lần sau thử lại, chỉ ghi nhận số lần và thông điệp. */
export async function markOfflineOrderDeferred(
    db: OfflineOrderDb,
    clientOrderId: string,
    message: string
): Promise<void> {
    const current = await db.offlineOrders.get(clientOrderId);

    if (!current) {
        return;
    }

    await db.offlineOrders.update(clientOrderId, {
        status: "PENDING",
        attempts: current.attempts + 1,
        lastError: message
    });
}

export async function markOfflineOrderFailed(
    db: OfflineOrderDb,
    clientOrderId: string,
    message: string
): Promise<void> {
    const current = await db.offlineOrders.get(clientOrderId);

    if (!current) {
        return;
    }

    await db.offlineOrders.update(clientOrderId, {
        status: "FAILED",
        attempts: current.attempts + 1,
        lastError: message
    });
}

/** Thu ngân bấm thử lại một đơn lỗi. */
export async function requeueOfflineOrder(
    db: OfflineOrderDb,
    clientOrderId: string
): Promise<void> {
    await db.offlineOrders.update(clientOrderId, {
        status: "PENDING",
        lastError: null
    });
}

export async function discardOfflineOrder(
    db: OfflineOrderDb,
    clientOrderId: string
): Promise<void> {
    await db.offlineOrders.delete(clientOrderId);
}

/** Đơn SYNCING kẹt lại (tab đóng giữa chừng) thì trả về PENDING khi khởi động. */
export async function recoverStuckOfflineOrders(db: OfflineOrderDb): Promise<number> {
    return db.offlineOrders
        .where("status")
        .equals("SYNCING")
        .modify({ status: "PENDING" });
}

export async function pruneSyncedOfflineOrders(
    db: OfflineOrderDb,
    now = new Date(),
    retentionMs = SYNCED_RETENTION_MS
): Promise<number> {
    const cutoff = new Date(now.getTime() - retentionMs).toISOString();
    const stale = await db.offlineOrders
        .where("status")
        .equals("SYNCED")
        .filter((record) => (record.syncedAt ?? record.soldAt) < cutoff)
        .primaryKeys();

    if (stale.length === 0) {
        return 0;
    }

    await db.offlineOrders.bulkDelete(stale);

    return stale.length;
}
