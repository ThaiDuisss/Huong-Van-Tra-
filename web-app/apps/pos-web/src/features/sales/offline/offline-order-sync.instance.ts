import { useSyncExternalStore } from "react";

import { networkMonitor } from "../../../app/network/network-status";
import { posOrderApi } from "../services/posOrder.api";
import { getOfflineOrderDb } from "./offline-order-db";
import { createOfflineOrderSync, type OfflineOrderSync } from "./offline-order-sync";
import type { OfflineOrderRecord, OfflineOrderSyncResult } from "./offline-order-types";

async function sendOfflineOrder(record: OfflineOrderRecord): Promise<OfflineOrderSyncResult> {
    const order = await posOrderApi.checkoutOffline(record.request);

    return {
        orderId: order.id,
        orderCode: order.orderCode,
        status: order.status,
        inventoryProcessingStatus: order.inventoryProcessingStatus
    };
}

let instance: OfflineOrderSync | null = null;

/** Một bộ đồng bộ cho cả app; tạo lazy để không chạm IndexedDB khi import. */
export function getOfflineOrderSync(): OfflineOrderSync {
    if (!instance) {
        instance = createOfflineOrderSync({
            db: getOfflineOrderDb(),
            networkMonitor,
            send: sendOfflineOrder,
            cancelStaleDraft: (draftOrderId) => posOrderApi.cancelDraftOrder(draftOrderId)
        });
    }

    return instance;
}

export function useOfflineOrderSync() {
    const sync = getOfflineOrderSync();

    return useSyncExternalStore(sync.subscribe, sync.getSnapshot, sync.getSnapshot);
}
