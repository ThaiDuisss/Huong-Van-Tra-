import "fake-indexeddb/auto";

import { ApiError } from "@/lib/api-client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { NetworkMonitor, NetworkStatus } from "../../../app/network/network-status";
import { createOfflineOrderDb, type OfflineOrderDb } from "./offline-order-db";
import {
    buildOfflineTempCode,
    classifySyncError,
    countOfflineOrders,
    enqueueOfflineOrder,
    listOfflineOrders,
    pruneSyncedOfflineOrders,
    recoverStuckOfflineOrders
} from "./offline-order-queue";
import { createOfflineOrderSync } from "./offline-order-sync";
import type { OfflineOrderRecord } from "./offline-order-types";

function createFakeNetwork(online = true) {
    let status: NetworkStatus = {
        online,
        checking: false,
        changedAt: 1,
        lastOnlineAt: online ? 1 : null,
        lastOfflineAt: online ? null : 1
    };
    const listeners = new Set<() => void>();
    const monitor: NetworkMonitor = {
        getSnapshot: () => status,
        subscribe(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        check: async () => status.online,
        reportNetworkError: vi.fn(),
        reportSuccess: vi.fn(),
        start: () => undefined,
        stop: () => undefined
    };

    return {
        monitor,
        setOnline(next: boolean) {
            status = { ...status, online: next, changedAt: status.changedAt + 1 };
            listeners.forEach((listener) => listener());
        }
    };
}

function createRecord(
    clientOrderId: string,
    soldAt: string,
    overrides: Partial<OfflineOrderRecord> = {}
): OfflineOrderRecord {
    return {
        clientOrderId,
        tempCode: buildOfflineTempCode(clientOrderId, new Date(soldAt)),
        ownerId: "user-1",
        cashierName: "Thu ngân",
        soldAt,
        status: "PENDING",
        attempts: 0,
        lastError: null,
        lastAttemptAt: null,
        totalAmount: 50_000,
        itemCount: 1,
        request: {
            clientOrderId,
            soldAt,
            offlineSale: true,
            orderType: "POS",
            orderDiscounts: [],
            shippingFee: 0,
            paidAmount: 50_000,
            paymentMethod: "CASH",
            items: [
                {
                    productVariantId: "b1000000-0000-0000-0000-000000000001",
                    quantity: 1,
                    unitPriceSnapshot: 50_000,
                    itemDiscounts: []
                }
            ]
        },
        receipt: { orderCode: "OFF-TEST", finalAmount: 50_000, items: [] },
        staleDraftOrderId: null,
        result: null,
        syncedAt: null,
        ...overrides
    };
}

let db: OfflineOrderDb;
let dbCounter = 0;

beforeEach(() => {
    dbCounter += 1;
    db = createOfflineOrderDb(`test-offline-${dbCounter}`);
});

afterEach(async () => {
    await db.delete();
});

describe("offline order queue", () => {
    it("builds a readable temp code from sale date and client id", () => {
        expect(
            buildOfflineTempCode(
                "3f2a9c10-1111-2222-3333-444444444444",
                new Date(2026, 9, 9, 10, 30)
            )
        ).toBe("OFF-261009-3F2A");
    });

    it("classifies errors into network, transient and rejected", () => {
        expect(
            classifySyncError(new ApiError({ code: "NETWORK_ERROR", message: "x", status: 0 }))
        ).toBe("network");
        expect(
            classifySyncError(new ApiError({ code: "SERVER", message: "x", status: 503 }))
        ).toBe("transient");
        expect(
            classifySyncError(new ApiError({ code: "UNAUTHORIZED", message: "x", status: 401 }))
        ).toBe("transient");
        expect(
            classifySyncError(new ApiError({ code: "INVALID_ORDER_STATUS", message: "x", status: 400 }))
        ).toBe("rejected");
        expect(classifySyncError(new TypeError("Failed to fetch"))).toBe("network");
    });

    it("recovers stuck SYNCING records and prunes old synced ones", async () => {
        await enqueueOfflineOrder(db, createRecord("a", "2026-10-01T01:00:00.000Z", { status: "SYNCING" }));
        await enqueueOfflineOrder(
            db,
            createRecord("b", "2026-09-01T01:00:00.000Z", {
                status: "SYNCED",
                syncedAt: "2026-09-01T02:00:00.000Z"
            })
        );
        await enqueueOfflineOrder(
            db,
            createRecord("c", "2026-10-08T01:00:00.000Z", {
                status: "SYNCED",
                syncedAt: "2026-10-08T02:00:00.000Z"
            })
        );

        expect(await recoverStuckOfflineOrders(db)).toBe(1);
        expect(await pruneSyncedOfflineOrders(db, new Date("2026-10-09T00:00:00.000Z"))).toBe(1);

        const counts = await countOfflineOrders(db);
        expect(counts).toEqual({ pending: 1, failed: 0, synced: 1 });
    });
});

describe("offline order sync", () => {
    it("replays pending orders in sale order and keeps server code on the receipt", async () => {
        const network = createFakeNetwork(true);
        const sent: string[] = [];
        await enqueueOfflineOrder(db, createRecord("second", "2026-10-09T03:00:00.000Z"));
        await enqueueOfflineOrder(
            db,
            createRecord("first", "2026-10-09T02:00:00.000Z", { staleDraftOrderId: "draft-1" })
        );
        const cancelStaleDraft = vi.fn().mockResolvedValue(undefined);

        const sync = createOfflineOrderSync({
            db,
            networkMonitor: network.monitor,
            cancelStaleDraft,
            send: async (record) => {
                sent.push(record.clientOrderId);
                return { orderId: `srv-${record.clientOrderId}`, orderCode: `ORD-${record.clientOrderId}` };
            }
        });

        await sync.trigger();

        expect(sent).toEqual(["first", "second"]);
        expect(cancelStaleDraft).toHaveBeenCalledWith("draft-1");
        const records = await listOfflineOrders(db);
        expect(records.every((record) => record.status === "SYNCED")).toBe(true);
        expect(records.find((record) => record.clientOrderId === "first")?.receipt.orderCode).toBe("ORD-first");
        expect(sync.getSnapshot()).toMatchObject({ pending: 0, failed: 0, synced: 2, syncing: false });
    });

    it("stops the round on a network error and keeps the order pending", async () => {
        const network = createFakeNetwork(true);
        await enqueueOfflineOrder(db, createRecord("a", "2026-10-09T02:00:00.000Z"));
        await enqueueOfflineOrder(db, createRecord("b", "2026-10-09T03:00:00.000Z"));
        const send = vi.fn().mockRejectedValue(
            new ApiError({ code: "NETWORK_ERROR", message: "Không kết nối được", status: 0 })
        );

        const sync = createOfflineOrderSync({ db, networkMonitor: network.monitor, send });
        await sync.trigger();

        expect(send).toHaveBeenCalledTimes(1);
        expect(network.monitor.reportNetworkError).toHaveBeenCalled();
        const counts = await countOfflineOrders(db);
        expect(counts.pending).toBe(2);
        expect(sync.getSnapshot().lastError).toBe("Không kết nối được");
    });

    it("marks business rejections as FAILED and continues with the next order", async () => {
        const network = createFakeNetwork(true);
        await enqueueOfflineOrder(db, createRecord("bad", "2026-10-09T02:00:00.000Z"));
        await enqueueOfflineOrder(db, createRecord("good", "2026-10-09T03:00:00.000Z"));

        const sync = createOfflineOrderSync({
            db,
            networkMonitor: network.monitor,
            send: async (record) => {
                if (record.clientOrderId === "bad") {
                    throw new ApiError({
                        code: "INVALID_ORDER_STATUS",
                        message: "Không tìm thấy giá bán",
                        status: 400
                    });
                }

                return { orderId: "srv", orderCode: "ORD-good" };
            }
        });
        await sync.trigger();

        const records = await listOfflineOrders(db);
        expect(records.find((record) => record.clientOrderId === "bad")).toMatchObject({
            status: "FAILED",
            lastError: "Không tìm thấy giá bán"
        });
        expect(records.find((record) => record.clientOrderId === "good")?.status).toBe("SYNCED");

        await sync.retry("bad");
        expect((await countOfflineOrders(db)).failed).toBe(0);
    });

    it("does nothing while offline and syncs when the network comes back", async () => {
        const network = createFakeNetwork(false);
        await enqueueOfflineOrder(db, createRecord("a", "2026-10-09T02:00:00.000Z"));
        const send = vi.fn().mockResolvedValue({ orderId: "srv", orderCode: "ORD" });

        const sync = createOfflineOrderSync({
            db,
            networkMonitor: network.monitor,
            send,
            retryIntervalMs: 60_000
        });
        sync.start();
        await sync.trigger();
        expect(send).not.toHaveBeenCalled();

        network.setOnline(true);
        await vi.waitFor(() => {
            expect(send).toHaveBeenCalledTimes(1);
        });
        await vi.waitFor(() => {
            expect(sync.getSnapshot().synced).toBe(1);
        });
        sync.stop();
    });
});
