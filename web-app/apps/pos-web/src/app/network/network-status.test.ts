import { afterEach, describe, expect, it, vi } from "vitest";

import { createNetworkMonitor, resolveHealthUrl } from "./network-status";

describe("network monitor", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.useRealTimers();
    });

    it("suy ra URL health từ origin của API", () => {
        expect(resolveHealthUrl("http://localhost:8080/api")).toBe(
            "http://localhost:8080/actuator/health"
        );
        expect(resolveHealthUrl("https://api.hvtra.id.vn/api/")).toBe(
            "https://api.hvtra.id.vn/actuator/health"
        );
    });

    it("mọi phản hồi HTTP, kể cả 401/404, đều tính là còn kết nối", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue(new Response("", { status: 404 }))
        );
        const monitor = createNetworkMonitor({ healthUrl: "http://x/h" });

        await expect(monitor.check()).resolves.toBe(true);
        expect(monitor.getSnapshot().online).toBe(true);
        expect(monitor.getSnapshot().lastOnlineAt).not.toBeNull();
    });

    it("fetch ném lỗi thì chuyển offline và báo cho listener", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn().mockRejectedValue(new TypeError("Failed to fetch"))
        );
        const monitor = createNetworkMonitor({ healthUrl: "http://x/h" });
        const listener = vi.fn();
        monitor.subscribe(listener);

        await expect(monitor.check()).resolves.toBe(false);

        const snapshot = monitor.getSnapshot();
        expect(snapshot.online).toBe(false);
        expect(snapshot.lastOfflineAt).not.toBeNull();
        expect(listener).toHaveBeenCalled();
        monitor.stop();
    });

    it("reportNetworkError chuyển offline ngay, reportSuccess đưa về online", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn().mockRejectedValue(new TypeError("Failed to fetch"))
        );
        const monitor = createNetworkMonitor({ healthUrl: "http://x/h" });

        monitor.reportNetworkError();
        expect(monitor.getSnapshot().online).toBe(false);

        // Chờ lượt ping kèm theo kết thúc để không rò promise.
        await monitor.check();

        monitor.reportSuccess();
        expect(monitor.getSnapshot().online).toBe(true);
        monitor.stop();
    });

    it("ping quá hạn pingTimeoutMs thì coi là mất mạng", async () => {
        vi.useFakeTimers();
        vi.stubGlobal(
            "fetch",
            vi.fn().mockImplementation(
                (_url: string, init: RequestInit) =>
                    new Promise<Response>((_resolve, reject) => {
                        init.signal?.addEventListener("abort", () =>
                            reject(new DOMException("aborted", "AbortError"))
                        );
                    })
            )
        );
        const monitor = createNetworkMonitor({
            healthUrl: "http://x/h",
            pingTimeoutMs: 1_000
        });

        const pending = monitor.check();
        await vi.advanceTimersByTimeAsync(1_000);

        await expect(pending).resolves.toBe(false);
        expect(monitor.getSnapshot().online).toBe(false);
    });

    it("hai lượt check chồng nhau chỉ gọi fetch một lần", async () => {
        const fetchMock = vi
            .fn()
            .mockResolvedValue(new Response("", { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);
        const monitor = createNetworkMonitor({ healthUrl: "http://x/h" });

        await Promise.all([monitor.check(), monitor.check()]);

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});
