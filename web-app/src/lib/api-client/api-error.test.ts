import { describe, expect, it, vi, afterEach } from "vitest";

import { ApiError } from "./api-error";
import { createRequestId } from "./request-id";
import { createTimeoutController } from "./timeout";

function apiError(status: number, code = "X") {
    return new ApiError({ status, code, message: "m" });
}

describe("ApiError", () => {
    it("is an Error and an ApiError with the given fields", () => {
        const cause = new Error("root");
        const error = new ApiError({
            status: 409,
            code: "CONFLICT",
            message: "Trùng",
            details: { field: "username" },
            requestId: "req-1",
            cause
        });
        expect(error).toBeInstanceOf(Error);
        expect(error).toBeInstanceOf(ApiError);
        expect(error.name).toBe("ApiError");
        expect(error.message).toBe("Trùng");
        expect(error.details).toEqual({ field: "username" });
        expect(error.requestId).toBe("req-1");
        expect(error.cause).toBe(cause);
    });

    it("leaves requestId undefined when it is not given", () => {
        expect(apiError(500).requestId).toBeUndefined();
    });

    it("classifies HTTP statuses", () => {
        expect(apiError(401).isUnauthorized).toBe(true);
        expect(apiError(403).isForbidden).toBe(true);
        expect(apiError(404).isNotFound).toBe(true);
        expect(apiError(400).isValidationError).toBe(true);
        expect(apiError(422).isValidationError).toBe(true);
        expect(apiError(409).isValidationError).toBe(false);
        expect(apiError(499).isServerError).toBe(false);
        expect(apiError(500).isServerError).toBe(true);
    });

    it("treats network and timeout codes as network errors", () => {
        expect(apiError(0, "NETWORK_ERROR").isNetworkError).toBe(true);
        expect(apiError(0, "REQUEST_TIMEOUT").isNetworkError).toBe(true);
        expect(apiError(0, "REQUEST_TIMEOUT").isTimeout).toBe(true);
        expect(apiError(503, "SERVICE_UNAVAILABLE").isNetworkError).toBe(false);
    });
});

describe("createTimeoutController", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("aborts with a TimeoutError after the timeout", () => {
        vi.useFakeTimers();
        const timeout = createTimeoutController(1000);
        vi.advanceTimersByTime(999);
        expect(timeout.signal.aborted).toBe(false);
        vi.advanceTimersByTime(1);
        expect(timeout.signal.aborted).toBe(true);
        expect((timeout.signal.reason as DOMException).name).toBe("TimeoutError");
    });

    it("does not abort once cleared", () => {
        vi.useFakeTimers();
        const timeout = createTimeoutController(1000);
        timeout.clear();
        vi.advanceTimersByTime(5000);
        expect(timeout.signal.aborted).toBe(false);
    });

    it("follows an external abort signal, including one already aborted", () => {
        const external = new AbortController();
        const timeout = createTimeoutController(10_000, external.signal);
        external.abort("user cancelled");
        expect(timeout.signal.aborted).toBe(true);
        expect(timeout.signal.reason).toBe("user cancelled");
        timeout.clear();

        const preAborted = new AbortController();
        preAborted.abort("before");
        const second = createTimeoutController(10_000, preAborted.signal);
        expect(second.signal.aborted).toBe(true);
        second.clear();
    });
});

describe("createRequestId", () => {
    it("returns a unique id per request", () => {
        const ids = new Set(Array.from({ length: 50 }, () => createRequestId()));
        expect(ids.size).toBe(50);
    });
});
