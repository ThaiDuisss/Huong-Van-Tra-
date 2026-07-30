import { ApiError } from "./api-error";
import { appendQueryParams } from "./query-params";
import { createRequestId } from "./request-id";
import { createTimeoutController } from "./timeout";

import type {
    ApiClient,
    ApiClientOptions,
    ApiResponse,
    RequestOptions,
    ResponseType
} from "./types";

interface ParsedErrorBody {
    code?: string;
    message?: string;
    details?: unknown;
}

function normalizeBaseUrl(baseUrl: string): string {
    return baseUrl.endsWith("/")
        ? baseUrl.slice(0, -1)
        : baseUrl;
}

function normalizePath(path: string): string {
    return path.startsWith("/") ? path : `/${path}`;
}

function isFormData(value: unknown): value is FormData {
    return (
        typeof FormData !== "undefined" &&
        value instanceof FormData
    );
}

function isBlob(value: unknown): value is Blob {
    return (
        typeof Blob !== "undefined" &&
        value instanceof Blob
    );
}

function isArrayBuffer(
    value: unknown
): value is ArrayBuffer {
    return (
        typeof ArrayBuffer !== "undefined" &&
        value instanceof ArrayBuffer
    );
}

function isUrlSearchParams(
    value: unknown
): value is URLSearchParams {
    return (
        typeof URLSearchParams !== "undefined" &&
        value instanceof URLSearchParams
    );
}

function shouldJsonStringifyBody(
    body: unknown
): boolean {
    if (body === null || body === undefined) {
        return false;
    }

    if (typeof body === "string") {
        return false;
    }

    if (isFormData(body)) {
        return false;
    }

    if (isBlob(body)) {
        return false;
    }

    if (isArrayBuffer(body)) {
        return false;
    }

    if (isUrlSearchParams(body)) {
        return false;
    }

    return true;
}

function prepareRequestBody(
    body: unknown,
    headers: Headers
): BodyInit | null | undefined {
    if (body === null || body === undefined) {
        return body;
    }

    if (shouldJsonStringifyBody(body)) {
        if (!headers.has("Content-Type")) {
            headers.set(
                "Content-Type",
                "application/json"
            );
        }

        return JSON.stringify(body);
    }

    if (isFormData(body)) {
        /*
         * Để browser tự thêm multipart boundary.
         */
        headers.delete("Content-Type");
    }

    return body as BodyInit;
}

async function parseJsonSafely(
    response: Response
): Promise<unknown> {
    const text = await response.text();

    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

function responseFilename(response: Response): string | null {
    const contentDisposition =
        response.headers.get("Content-Disposition");
    if (!contentDisposition) {
        return null;
    }

    const encodedMatch =
        contentDisposition.match(
            /filename\*\s*=\s*UTF-8''([^;]+)/i
        );
    const plainMatch =
        contentDisposition.match(
            /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i
        );
    const rawFilename =
        encodedMatch?.[1] ??
        plainMatch?.[1] ??
        plainMatch?.[2];
    if (!rawFilename) {
        return null;
    }

    let filename = rawFilename.trim();
    try {
        filename = decodeURIComponent(filename);
    } catch {
        // Keep a valid non-percent-encoded filename.
    }

    return filename.split(/[\\/]/).pop()?.trim() || null;
}

async function parseDownloadBlob(
    response: Response
): Promise<Blob> {
    const blob = await response.blob();
    const filename = responseFilename(response);
    if (!filename) {
        return blob;
    }

    if (typeof File !== "undefined") {
        return new File([blob], filename, {
            type: blob.type,
            lastModified: Date.now()
        });
    }

    Object.defineProperty(blob, "name", {
        configurable: false,
        enumerable: true,
        value: filename,
        writable: false
    });
    return blob;
}

async function parseResponse(
    response: Response,
    responseType: ResponseType
): Promise<unknown> {
    if (responseType === "raw") {
        return response;
    }

    if (
        response.status === 204 ||
        response.status === 205
    ) {
        return undefined;
    }

    switch (responseType) {
        case "blob":
            return parseDownloadBlob(response);

        case "text":
            return response.text();

        case "arrayBuffer":
            return response.arrayBuffer();

        case "json":
        default:
            return parseJsonSafely(response);
    }
}

function parseErrorBody(
    body: unknown
): ParsedErrorBody {
    if (
        typeof body === "object" &&
        body !== null
    ) {
        const record =
            body as Record<string, unknown>;

        const parsed: ParsedErrorBody = {};

        if (typeof record.code === "string") {
            parsed.code = record.code;
        }

        if (typeof record.message === "string") {
            parsed.message = record.message;
        }

        if (record.details !== undefined) {
            parsed.details = record.details;
        }

        return parsed;
    }

    if (typeof body === "string") {
        return {
            message: body
        };
    }

    return {};
}

async function parseBlobErrorBody(
    body: Blob
): Promise<unknown> {
    const text = await body.text();

    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

function unwrapApiResponse<T>(
    body: unknown,
    unwrapData: boolean
): T {
    if (!unwrapData) {
        return body as T;
    }

    if (
        typeof body === "object" &&
        body !== null &&
        "data" in body
    ) {
        return (body as ApiResponse<T>).data;
    }

    return body as T;
}

export function createApiClient(
    options: ApiClientOptions
): ApiClient {
    const baseUrl = normalizeBaseUrl(
        options.baseUrl
    );

    let refreshPromise:
        | Promise<string | null>
        | null = null;

    async function doRefreshToken():
        Promise<string | null> {
        const refresh =
            options.tokenProvider?.refreshAccessToken;

        if (!refresh) {
            return null;
        }

        if (!refreshPromise) {
            refreshPromise = Promise.resolve()
                .then(() => refresh())
                .finally(() => {
                    refreshPromise = null;
                });
        }

        return refreshPromise;
    }

    async function handleUnauthorized():
        Promise<void> {
        await options.tokenProvider?.clearTokens?.();
        await options.onUnauthorized?.();
    }

    async function request<T>(
        path: string,
        requestOptions: RequestOptions = {}
    ): Promise<T> {
        const {
            method = "GET",
            body,
            query,
            timeoutMs = options.timeoutMs ?? 15_000,
            responseType = "json",
            retryOnUnauthorized = true,
            unwrapData = options.unwrapData ?? true,
            authenticated = true,
            headers: requestHeaders,
            signal: externalSignal,
            ...fetchOptions
        } = requestOptions;

        const requestId =
            (options.createRequestId ??
                createRequestId)();

        const headers = new Headers(
            options.defaultHeaders
        );

        new Headers(requestHeaders).forEach(
            (value, key) => {
                headers.set(key, value);
            }
        );

        headers.set("X-Request-Id", requestId);

        if (
            responseType === "json" &&
            !headers.has("Accept")
        ) {
            headers.set("Accept", "application/json");
        }

        /*
         * TokenProvider của Keycloak sẽ:
         * 1. Kiểm tra token.
         * 2. Refresh nếu token sắp hết hạn.
         * 3. Trả access token mới nhất.
         */
        if (authenticated) {
            const accessToken =
                await options.tokenProvider
                    ?.getAccessToken();

            if (accessToken) {
                headers.set(
                    "Authorization",
                    `Bearer ${accessToken}`
                );
            }
        }

        const requestBody =
            prepareRequestBody(body, headers);

        const requestPath =
            appendQueryParams(path, query);

        const url =
            `${baseUrl}${normalizePath(requestPath)}`;

        const timeoutController =
            createTimeoutController(
                timeoutMs,
                externalSignal ?? undefined
            );

        try {
            const requestInit: RequestInit = {
                ...fetchOptions,
                method,
                headers,
                signal: timeoutController.signal,
            };

            if (
                method !== "GET" &&
                method !== "HEAD" &&
                requestBody !== undefined
            ) {
                requestInit.body = requestBody;
            }

            const response = await fetch(url, requestInit);


            /*
             * Trường hợp token bị backend từ chối:
             * - ép Keycloak refresh token;
             * - retry request đúng một lần.
             */
            if (
                response.status === 401 &&
                authenticated &&
                retryOnUnauthorized
            ) {
                const newAccessToken =
                    await doRefreshToken();

                if (newAccessToken) {
                    return request<T>(path, {
                        ...requestOptions,
                        retryOnUnauthorized: false
                    });
                }

                await handleUnauthorized();

                throw new ApiError({
                    status: 401,
                    code: "SESSION_EXPIRED",
                    message:
                        "Phiên đăng nhập đã hết hạn",
                    requestId
                });
            }

            const parsedBody =
                await parseResponse(
                    response,
                    responseType
                );

            if (!response.ok) {
                const errorBody = isBlob(parsedBody)
                    ? await parseBlobErrorBody(parsedBody)
                    : parsedBody;
                const parsedError =
                    parseErrorBody(errorBody);

                throw new ApiError({
                    status: response.status,
                    code:
                        parsedError.code ??
                        `HTTP_${response.status}`,

                    message:
                        parsedError.message ??
                        response.statusText ??
                        "Có lỗi xảy ra",

                    details: parsedError.details,
                    requestId
                });
            }

            if (responseType !== "json") {
                return parsedBody as T;
            }

            return unwrapApiResponse<T>(
                parsedBody,
                unwrapData
            );
        } catch (error) {
            if (error instanceof ApiError) {
                throw error;
            }

            if (
                error instanceof DOMException &&
                (
                    error.name === "AbortError" ||
                    error.name === "TimeoutError"
                )
            ) {
                if (externalSignal?.aborted) {
                    throw new ApiError({
                        status: 499,
                        code: "REQUEST_CANCELLED",
                        message: "Yêu cầu đã bị hủy",
                        requestId,
                        cause: error
                    });
                }

                throw new ApiError({
                    status: 408,
                    code: "REQUEST_TIMEOUT",
                    message:
                        "Yêu cầu quá thời gian xử lý",
                    requestId,
                    cause: error
                });
            }

            if (error instanceof TypeError) {
                throw new ApiError({
                    status: 0,
                    code: "NETWORK_ERROR",
                    message:
                        "Không thể kết nối đến máy chủ",
                    requestId,
                    cause: error
                });
            }

            throw error;
        } finally {
            timeoutController.clear();
        }
    }

    return {
        request,

        get<T>(
            path: string,
            requestOptions: RequestOptions = {}
        ) {
            return request<T>(path, {
                ...requestOptions,
                method: "GET"
            });
        },

        post<TResponse, TBody = unknown>(
            path: string,
            body?: TBody,
            requestOptions: RequestOptions = {}
        ) {
            return request<TResponse>(path, {
                ...requestOptions,
                method: "POST",
                body
            });
        },

        put<TResponse, TBody = unknown>(
            path: string,
            body?: TBody,
            requestOptions: RequestOptions = {}
        ) {
            return request<TResponse>(path, {
                ...requestOptions,
                method: "PUT",
                body
            });
        },

        patch<TResponse, TBody = unknown>(
            path: string,
            body?: TBody,
            requestOptions: RequestOptions = {}
        ) {
            return request<TResponse>(path, {
                ...requestOptions,
                method: "PATCH",
                body
            });
        },

        delete<T>(
            path: string,
            requestOptions: RequestOptions = {}
        ) {
            return request<T>(path, {
                ...requestOptions,
                method: "DELETE"
            });
        },

        upload<T>(
            path: string,
            formData: FormData,
            requestOptions: RequestOptions = {}
        ) {
            return request<T>(path, {
                ...requestOptions,
                method: "POST",
                body: formData
            });
        },

        download(
            path: string,
            requestOptions: RequestOptions = {}
        ) {
            return request<Blob>(path, {
                ...requestOptions,
                method: "GET",
                responseType: "blob",
                unwrapData: false
            });
        }
    };
}
