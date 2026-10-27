import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "./api-error";
import { createApiClient } from "./client";
import {
    DEFAULT_RETRY_POLICY,
    getRetryDelayMs,
    resolveRetryPolicy,
    shouldRetry
} from "./retry";

const networkFailure = () =>
    Promise.reject(new TypeError("Failed to fetch"));

const okJson = (data: unknown) =>
    Promise.resolve(
        new Response(JSON.stringify({ data }), {
            status: 200,
            headers: { "Content-Type": "application/json" }
        })
    );

function createClient(
    fetchMock: ReturnType<typeof vi.fn>,
    extra: Parameters<typeof createApiClient>[0] extends infer O
        ? Partial<O>
        : never = {}
) {
    vi.stubGlobal("fetch", fetchMock);

    return createApiClient({
        baseUrl: "http://localhost/api",
        retry: { baseDelayMs: 0, maxDelayMs: 0 },
        ...extra
    });
}

describe("api client retry khi mất mạng", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("tự thử lại GET khi fetch ném TypeError rồi trả kết quả khi mạng về", async () => {
        const fetchMock = vi
            .fn()
            .mockImplementationOnce(networkFailure)
            .mockImplementationOnce(networkFailure)
            .mockImplementationOnce(() => okJson({ id: 1 }));
        const onRequestError = vi.fn();
        const client = createClient(fetchMock, { onRequestError });

        const result = await client.get<{ id: number }>("/orders");

        expect(result).toEqual({ id: 1 });
        expect(fetchMock).toHaveBeenCalledTimes(3);
        expect(onRequestError).toHaveBeenCalledTimes(2);
        expect(onRequestError.mock.calls[0]?.[1]).toMatchObject({
            method: "GET",
            path: "/orders",
            attempt: 0,
            willRetry: true
        });
    });

    it("ném NETWORK_ERROR sau khi hết số lần thử lại", async () => {
        const fetchMock = vi.fn().mockImplementation(networkFailure);
        const onRequestError = vi.fn();
        const client = createClient(fetchMock, { onRequestError });

        const error = await client.get("/orders").catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe("NETWORK_ERROR");
        expect((error as ApiError).isNetworkError).toBe(true);
        // 1 lần gọi đầu + 2 lần thử lại mặc định.
        expect(fetchMock).toHaveBeenCalledTimes(3);
        expect(onRequestError).toHaveBeenLastCalledWith(
            expect.any(ApiError),
            expect.objectContaining({ attempt: 2, willRetry: false })
        );
    });

    it("không thử lại POST vì có thể tạo đơn hàng trùng", async () => {
        const fetchMock = vi.fn().mockImplementation(networkFailure);
        const client = createClient(fetchMock);

        await expect(
            client.post("/orders/pos/checkout", { total: 1 })
        ).rejects.toMatchObject({ code: "NETWORK_ERROR" });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("không thử lại lỗi 4xx/5xx thường của GET", async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ message: "Không tìm thấy" }), {
                status: 404
            })
        );
        const client = createClient(fetchMock);

        await expect(client.get("/orders/x")).rejects.toMatchObject({
            status: 404
        });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("thử lại GET khi gateway trả 503", async () => {
        const fetchMock = vi
            .fn()
            .mockResolvedValueOnce(new Response("", { status: 503 }))
            .mockImplementationOnce(() => okJson([]));
        const client = createClient(fetchMock);

        await expect(client.get("/orders")).resolves.toEqual([]);
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it("tắt thử lại cho một request bằng retry: false", async () => {
        const fetchMock = vi.fn().mockImplementation(networkFailure);
        const client = createClient(fetchMock);

        await expect(
            client.get("/health", { retry: false })
        ).rejects.toMatchObject({ code: "NETWORK_ERROR" });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("không thử lại khi người gọi đã hủy request", async () => {
        const controller = new AbortController();
        const fetchMock = vi.fn().mockImplementation(() => {
            controller.abort();
            return Promise.reject(
                new DOMException("aborted", "AbortError")
            );
        });
        const client = createClient(fetchMock);

        await expect(
            client.get("/orders", { signal: controller.signal })
        ).rejects.toMatchObject({ code: "REQUEST_CANCELLED" });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});

describe("timeout dài cho upload/download", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("download không bị timeout 30s mặc định cắt ngang", async () => {
        vi.useFakeTimers();
        try {
            let capturedSignal: AbortSignal | undefined;
            const fetchMock = vi.fn().mockImplementation(
                (_url: string, init: RequestInit) =>
                    new Promise<Response>((resolve) => {
                        capturedSignal = init.signal ?? undefined;
                        setTimeout(
                            () => resolve(new Response("x", { status: 200 })),
                            120_000
                        );
                    })
            );
            vi.stubGlobal("fetch", fetchMock);
            const client = createApiClient({
                baseUrl: "http://localhost/api",
                timeoutMs: 30_000
            });

            const pending = client.download("/orders/export");
            await vi.advanceTimersByTimeAsync(120_000);

            await expect(pending).resolves.toBeInstanceOf(Blob);
            expect(capturedSignal?.aborted).toBe(false);
        } finally {
            vi.useRealTimers();
        }
    });
});

describe("retry policy helpers", () => {
    it("merge client option và request option theo đúng thứ tự", () => {
        const policy = resolveRetryPolicy(
            { retries: 5 },
            { methods: ["GET", "POST"] }
        );

        expect(policy.retries).toBe(5);
        expect(policy.methods).toEqual(["GET", "POST"]);
        expect(resolveRetryPolicy(true, false).retries).toBe(0);
        expect(resolveRetryPolicy(undefined, undefined)).toEqual(
            DEFAULT_RETRY_POLICY
        );
    });

    it("backoff tăng theo luỹ thừa 2 và không vượt maxDelayMs", () => {
        const policy = { ...DEFAULT_RETRY_POLICY, baseDelayMs: 300, maxDelayMs: 1_000 };
        const noJitter = () => 0;

        expect(getRetryDelayMs(policy, 0, noJitter)).toBe(300);
        expect(getRetryDelayMs(policy, 1, noJitter)).toBe(600);
        expect(getRetryDelayMs(policy, 2, noJitter)).toBe(1_000);
        expect(getRetryDelayMs(policy, 2, () => 1)).toBe(1_250);
    });

    it("shouldRetry dừng khi hết lượt hoặc method không an toàn", () => {
        const networkError = new ApiError({
            status: 0,
            code: "NETWORK_ERROR",
            message: ""
        });

        expect(shouldRetry(DEFAULT_RETRY_POLICY, "GET", 0, networkError)).toBe(true);
        expect(shouldRetry(DEFAULT_RETRY_POLICY, "GET", 2, networkError)).toBe(false);
        expect(shouldRetry(DEFAULT_RETRY_POLICY, "PUT", 0, networkError)).toBe(false);
        expect(shouldRetry(DEFAULT_RETRY_POLICY, "GET", 0, new Error("x"))).toBe(false);
    });
});
