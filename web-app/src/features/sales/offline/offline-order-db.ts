import Dexie, { type EntityTable } from "dexie";

import type { OfflineOrderRecord } from "./offline-order-types";

export const OFFLINE_ORDER_DB_NAME = "hvt-pos-offline";

/**
 * IndexedDB giữ các đơn đã bán khi mất mạng. Dùng IndexedDB thay localStorage vì
 * mỗi đơn mang cả giỏ hàng lẫn hoá đơn đã in, và cần truy vấn theo trạng thái.
 */
export class OfflineOrderDb extends Dexie {
    offlineOrders!: EntityTable<OfflineOrderRecord, "clientOrderId">;

    constructor(name = OFFLINE_ORDER_DB_NAME) {
        super(name);
        this.version(1).stores({
            offlineOrders: "clientOrderId, status, soldAt, ownerId"
        });
    }
}

export function createOfflineOrderDb(name?: string) {
    return new OfflineOrderDb(name);
}

let sharedDb: OfflineOrderDb | null = null;

/** Lazy để module import được trong môi trường không có IndexedDB (test, SSR). */
export function getOfflineOrderDb() {
    if (!sharedDb) {
        sharedDb = createOfflineOrderDb();
    }

    return sharedDb;
}
