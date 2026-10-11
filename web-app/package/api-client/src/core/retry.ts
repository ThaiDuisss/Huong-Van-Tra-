import { ApiError } from "./api-error";

/**
 * Chính sách tự thử lại khi request thất bại vì mạng.
 *
 * Mặc định chỉ thử lại các method không làm thay đổi dữ liệu (GET, HEAD)
 * và chỉ khi lỗi là mất kết nối hoặc gateway tạm thời không phản hồi.
 * Request ghi dữ liệu (POST, PUT, ...) không được tự thử lại vì backend
 * chưa có khoá idempotency, thử lại có thể tạo đơn hàng trùng.
 */
export interface RetryPolicy {
    /** Số lần thử lại thêm sau lần gọi đầu. 0 = không thử lại. */
    retries: number;

    /** Thời gian chờ trước lần thử lại đầu tiên (ms). */
    baseDelayMs: number;

    /** Giới hạn thời gian chờ giữa hai lần thử (ms). */
    maxDelayMs: number;

    /** Các HTTP method được phép thử lại. */
    methods: readonly string[];

    /** Các HTTP status được coi là lỗi tạm thời. */
    retryableStatuses: readonly number[];
}

export type RetryOption = boolean | Partial<RetryPolicy>;

export const DEFAULT_RETRY_POLICY: RetryPolicy = {
    retries: 2,
    baseDelayMs: 300,
    maxDelayMs: 3_000,
    methods: ["GET", "HEAD"],
    retryableStatuses: [502, 503, 504]
};

const NO_RETRY_POLICY: RetryPolicy = {
    ...DEFAULT_RETRY_POLICY,
    retries: 0
};

export function resolveRetryPolicy(
    clientOption: RetryOption | undefined,
    requestOption: RetryOption | undefined
): RetryPolicy {
    const base = mergeRetryPolicy(DEFAULT_RETRY_POLICY, clientOption);

    return mergeRetryPolicy(base, requestOption);
}

function mergeRetryPolicy(
    base: RetryPolicy,
    option: RetryOption | undefined
): RetryPolicy {
    if (option === undefined || option === true) {
        return base;
    }

    if (option === false) {
        return NO_RETRY_POLICY;
    }

    return {
        ...base,
        ...option
    };
}

export function isRetryableError(error: unknown): boolean {
    if (!(error instanceof ApiError)) {
        return false;
    }

    return error.isNetworkError;
}

export function shouldRetry(
    policy: RetryPolicy,
    method: string,
    attempt: number,
    error: unknown
): boolean {
    if (attempt >= policy.retries) {
        return false;
    }

    if (!policy.methods.includes(method.toUpperCase())) {
        return false;
    }

    if (!(error instanceof ApiError)) {
        return false;
    }

    if (error.code === "REQUEST_CANCELLED") {
        return false;
    }

    return (
        error.isNetworkError ||
        policy.retryableStatuses.includes(error.status)
    );
}

/**
 * Exponential backoff có jitter: 300ms, 600ms, 1200ms... cộng ngẫu nhiên
 * tối đa 25% để nhiều tab POS không dồn request cùng một lúc khi mạng về.
 */
export function getRetryDelayMs(
    policy: RetryPolicy,
    attempt: number,
    random: () => number = Math.random
): number {
    const exponential = policy.baseDelayMs * 2 ** attempt;
    const capped = Math.min(exponential, policy.maxDelayMs);
    const jitter = capped * 0.25 * random();

    return Math.round(capped + jitter);
}

export function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => {
        setTimeout(resolve, ms);
    });
}
