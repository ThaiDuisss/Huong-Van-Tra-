export interface ApiErrorOptions {
    status: number;
    code: string;
    message: string;
    details?: unknown;
    requestId?: string;
    cause?: unknown;
}

export class ApiError extends Error {
    readonly status: number;
    readonly code: string;
    readonly details?: unknown;
    readonly requestId?: string;

    constructor(options: ApiErrorOptions) {
        super(options.message, {
            cause: options.cause
        });

        this.name = "ApiError";
        this.status = options.status;
        this.code = options.code;
        this.details = options.details;
        if (options.requestId !== undefined) {
            this.requestId = options.requestId;
        }

        Object.setPrototypeOf(this, ApiError.prototype);
    }

    get isUnauthorized(): boolean {
        return this.status === 401;
    }

    get isForbidden(): boolean {
        return this.status === 403;
    }

    get isNotFound(): boolean {
        return this.status === 404;
    }

    get isValidationError(): boolean {
        return this.status === 400 || this.status === 422;
    }

    get isServerError(): boolean {
        return this.status >= 500;
    }

    /**
     * Không nhận được phản hồi HTTP nào: mất mạng, DNS lỗi, gateway tắt,
     * hoặc request chờ quá lâu. Khác với lỗi 5xx là backend đã nhận request.
     */
    get isNetworkError(): boolean {
        return (
            this.code === "NETWORK_ERROR" ||
            this.code === "REQUEST_TIMEOUT"
        );
    }

    get isTimeout(): boolean {
        return this.code === "REQUEST_TIMEOUT";
    }
}
