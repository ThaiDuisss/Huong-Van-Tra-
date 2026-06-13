export {
    ApiError
} from "./core/api-error";

export {
    createApiClient
} from "./core/client";

export {
    buildQueryString,
    appendQueryParams
} from "./core/query-params";

export {
    createRequestId
} from "./core/request-id";


export {
    createProductApi
} from "./modules/product.api";

export {
    createOrderApi
} from "./modules/order.api";

export {
    createPaymentApi
} from "./modules/payment.api";

export {
    createInventoryApi
} from "./modules/inventory.api"

export type {
    TokenProvider
} from "./core/token-provider";

export type {
    ApiClient,
    ApiClientOptions,
    ApiResponse,
    RequestOptions,
    ResponseType
} from "./core/types";

export type {
    QueryParams,
    QueryParamValue,
    QueryParamPrimitive
} from "./core/query-params";


export type {
    Product,
    ProductDetail,
    ProductVariant,
    ProductQuery,
    CreateProductInput,
    UpdateProductInput,
    PageResponse,
    ProductApi
} from "./modules/product.api";

export type {
    Order,
    OrderItem,
    OrderStatus,
    CreateOrderInput,
    OrderApi
} from "./modules/order.api";

export type {
    Payment,
    PaymentMethod,
    PaymentStatus,
    PaymentApi
} from "./modules/payment.api";

export type {
    InventoryItem,
    InventoryQuery,
    AdjustInventoryInput,
    InventoryApi
} from "./modules/inventory.api";