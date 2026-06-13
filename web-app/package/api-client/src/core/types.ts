import type { QueryParams } from "./query-params";
import type { TokenProvider } from "./token-provider";

export type ResponseType =
    | "json"
    | "blob"
    | "text"
    | "arrayBuffer"
    | "raw";

export interface ApiResponse<T> {
    data: T;
    message?: string;
    code?: string;
    details?: unknown;
    timestamp?: string;
}

export interface ApiClientOptions {
    baseUrl: string;

    tokenProvider?: TokenProvider;

    /**
     * Timeout mặc định cho mỗi request.
     */
    timeoutMs?: number;

    /**
     * Header mặc định gửi trong tất cả request.
     */
    defaultHeaders?: HeadersInit;

    /**
     * Mặc định backend trả về dạng:
     *
     * {
     *   "data": {},
     *   "message": "...",
     *   "code": "SUCCESS"
     * }
     */
    unwrapData?: boolean;

    /**
     * Cho phép custom cách tạo request ID.
     */
    createRequestId?: () => string;

    /**
     * Callback khi không thể refresh token.
     * Có thể dùng để redirect sang login.
     */
    onUnauthorized?: () => void | Promise<void>;
}

export interface RequestOptions
    extends Omit<RequestInit, "body" | "method"> {
    method?: string;

    /**
     * Body dạng object sẽ tự JSON.stringify.
     */
    body?: unknown;

    /**
     * Query parameter.
     */
    query?: QueryParams;

    /**
     * Timeout riêng của request.
     */
    timeoutMs?: number;

    /**
     * Kiểu response muốn nhận.
     */
    responseType?: ResponseType;

    /**
     * Có tự refresh token khi 401 hay không.
     */
    retryOnUnauthorized?: boolean;

    /**
     * Có unwrap trường data không.
     */
    unwrapData?: boolean;

    /**
     * Có gửi access token không.
     *
     * false dùng cho login, register, refresh.
     */
    authenticated?: boolean;
}

export interface ApiClient {
    request<T>(
        path: string,
        options?: RequestOptions
    ): Promise<T>;

    get<T>(
        path: string,
        options?: RequestOptions
    ): Promise<T>;

    post<TResponse, TBody = unknown>(
        path: string,
        body?: TBody,
        options?: RequestOptions
    ): Promise<TResponse>;

    put<TResponse, TBody = unknown>(
        path: string,
        body?: TBody,
        options?: RequestOptions
    ): Promise<TResponse>;

    patch<TResponse, TBody = unknown>(
        path: string,
        body?: TBody,
        options?: RequestOptions
    ): Promise<TResponse>;

    delete<T>(
        path: string,
        options?: RequestOptions
    ): Promise<T>;

    upload<T>(
        path: string,
        formData: FormData,
        options?: RequestOptions
    ): Promise<T>;

    download(
        path: string,
        options?: RequestOptions
    ): Promise<Blob>;
}