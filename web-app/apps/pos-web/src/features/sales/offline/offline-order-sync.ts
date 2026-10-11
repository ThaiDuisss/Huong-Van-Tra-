import type { NetworkMonitor } from "../../../app/network/network-status";
import type { OfflineOrderDb } from "./offline-order-db";
import {
    classifySyncError,
    countOfflineOrders,
    discardOfflineOrder,
    getSyncErrorMessage,
    listPendingOfflineOrders,
    markOfflineOrderDeferred,
    markOfflineOrderFailed,
    markOfflineOrderSynced,
    markOfflineOrderSyncing,
    pruneSyncedOfflineOrders,
    recoverStuckOfflineOrders,
    requeueOfflineOrder
} from "./offline-order-queue";
import type {
    OfflineOrderRecord,
    OfflineOrderSyncResult
} from "./offline-order-types";

export interface OfflineOrderSyncSnapshot {
    /** Đơn đang chờ hoặc đang gửi. */
    pending: number;
    /** Đơn máy chủ từ chối, cần thu ngân xử lý. */
    failed: number;
    /** Đơn đã lên máy chủ, còn giữ trên máy để in lại. */
    synced: number;
    syncing: boolean;
    lastSyncAt: number | null;
    lastError: string | null;
    /** Tăng mỗi khi dữ liệu trong IndexedDB đổi, để danh sách trong hộp thoại tải lại. */
    revision: number;
}

export interface OfflineOrderSyncOptions {
    db: OfflineOrderDb;
    /** Gửi một đơn lên máy chủ. Replay cùng clientOrderId phải trả về cùng đơn. */
    send: (record: OfflineOrderRecord) => Promise<OfflineOrderSyncResult>;
    /** Huỷ đơn nháp cũ đã tạo trước khi mất mạng; lỗi được bỏ qua. */
    cancelStaleDraft?: (draftOrderId: string) => Promise<unknown>;
    networkMonitor: NetworkMonitor;
    /** Chu kỳ thử lại khi còn đơn chờ mà vẫn online (lỗi tạm phía máy chủ). */
    retryIntervalMs?: number;
    now?: () => Date;
}

const INITIAL_SNAPSHOT: OfflineOrderSyncSnapshot = {
    pending: 0,
    failed: 0,
    synced: 0,
    syncing: false,
    lastSyncAt: null,
    lastError: null,
    revision: 0
};

/**
 * Bộ đồng bộ đơn bán offline: chạy nền, replay lần lượt theo thứ tự bán khi có mạng.
 * Một đơn lỗi mạng thì dừng cả vòng (mạng vừa rớt lại), lỗi nghiệp vụ thì đánh FAILED và đi tiếp.
 */
export function createOfflineOrderSync(options: OfflineOrderSyncOptions) {
    const {
        db,
        send,
        cancelStaleDraft,
        networkMonitor,
        retryIntervalMs = 30_000,
        now = () => new Date()
    } = options;

    let snapshot: OfflineOrderSyncSnapshot = INITIAL_SNAPSHOT;
    const listeners = new Set<() => void>();
    let running: Promise<void> | null = null;
    let started = false;
    let unsubscribeNetwork: (() => void) | null = null;
    let retryTimer: ReturnType<typeof setInterval> | null = null;
    let wasOnline = networkMonitor.getSnapshot().online;

    function emit() {
        listeners.forEach((listener) => listener());
    }

    function patch(partial: Partial<OfflineOrderSyncSnapshot>) {
        snapshot = { ...snapshot, ...partial };
        emit();
    }

    async function refreshCounts(bumpRevision = true) {
        const counts = await countOfflineOrders(db);
        patch({
            ...counts,
            revision: bumpRevision ? snapshot.revision + 1 : snapshot.revision
        });
    }

    async function syncOnce(): Promise<void> {
        if (!networkMonitor.getSnapshot().online) {
            return;
        }

        const pending = await listPendingOfflineOrders(db);

        if (pending.length === 0) {
            return;
        }

        patch({ syncing: true, lastError: null });

        try {
            for (const record of pending) {
                if (!networkMonitor.getSnapshot().online) {
                    break;
                }

                await markOfflineOrderSyncing(db, record.clientOrderId, now());

                try {
                    const result = await send(record);
                    await markOfflineOrderSynced(db, record.clientOrderId, result, now());
                    networkMonitor.reportSuccess();

                    if (record.staleDraftOrderId && cancelStaleDraft) {
                        try {
                            await cancelStaleDraft(record.staleDraftOrderId);
                        } catch {
                            // Nháp cũ có thể đã hết hạn / bị huỷ: không ảnh hưởng đơn đã đồng bộ.
                        }
                    }
                } catch (error) {
                    const kind = classifySyncError(error);
                    const message = getSyncErrorMessage(error);

                    if (kind === "rejected") {
                        await markOfflineOrderFailed(db, record.clientOrderId, message);
                        await refreshCounts();
                        continue;
                    }

                    await markOfflineOrderDeferred(db, record.clientOrderId, message);

                    if (kind === "network") {
                        networkMonitor.reportNetworkError();
                    }

                    patch({ lastError: message });
                    break;
                }

                await refreshCounts();
            }
        } finally {
            await refreshCounts();
            patch({ syncing: false, lastSyncAt: now().getTime() });
        }
    }

    /** Gọi khi có đơn mới hoặc mạng vừa về. Không chạy chồng: lượt sau xếp sau lượt đang chạy. */
    function trigger(): Promise<void> {
        if (running) {
            return running.then(() => {
                if (!running) {
                    return trigger();
                }

                return running;
            });
        }

        running = syncOnce()
            .catch((error) => {
                patch({ lastError: getSyncErrorMessage(error) });
            })
            .finally(() => {
                running = null;
            });

        return running;
    }

    function start() {
        if (started) {
            return;
        }

        started = true;

        void (async () => {
            await recoverStuckOfflineOrders(db);
            await pruneSyncedOfflineOrders(db, now());
            await refreshCounts();
            void trigger();
        })();

        unsubscribeNetwork = networkMonitor.subscribe(() => {
            const { online } = networkMonitor.getSnapshot();

            if (online && !wasOnline) {
                void trigger();
            }

            wasOnline = online;
        });

        retryTimer = setInterval(() => {
            if (snapshot.pending > 0 && !snapshot.syncing) {
                void trigger();
            }
        }, retryIntervalMs);
    }

    function stop() {
        started = false;
        unsubscribeNetwork?.();
        unsubscribeNetwork = null;

        if (retryTimer) {
            clearInterval(retryTimer);
            retryTimer = null;
        }
    }

    return {
        getSnapshot: () => snapshot,
        subscribe(listener: () => void) {
            listeners.add(listener);

            return () => {
                listeners.delete(listener);
            };
        },
        start,
        stop,
        /** Sau khi thêm đơn vào hàng chờ. */
        async notifyEnqueued() {
            await refreshCounts();
            void trigger();
        },
        trigger,
        async retry(clientOrderId: string) {
            await requeueOfflineOrder(db, clientOrderId);
            await refreshCounts();
            void trigger();
        },
        async discard(clientOrderId: string) {
            await discardOfflineOrder(db, clientOrderId);
            await refreshCounts();
        },
        refresh: refreshCounts
    };
}

export type OfflineOrderSync = ReturnType<typeof createOfflineOrderSync>;
