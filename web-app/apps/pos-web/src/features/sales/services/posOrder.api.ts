import { apiClient } from "../../../api/api-client";
import type {
    SaleBackendDiscountType,
    SaleCartItem,
    SaleDiscountSource,
    SaleOrderType,
    SalePaymentMethod
} from "../types/sale.types";

export interface CreatePosOrderInput {
    customerId: string | null;
    orderType: SaleOrderType;
    discountAmount: number;
    items: SaleCartItem[];
}

export interface CheckoutPosOrderInput extends CreatePosOrderInput {
    orderId: string | null;
    originalAmount: number;
    finalAmount: number;
    orderDiscounts: PosDiscountRequest[];
    paymentMethod: SalePaymentMethod;
    paidAmount: number;
    receivedAmount?: number;
    changeAmount?: number;
    /** Khách đồng ý chờ hàng: đơn thiếu hàng được ghi nhận là đơn chờ hàng. */
    allowBackorder?: boolean;
    /** Đơn chờ hàng trả tiền mặt: chỉ thu tiền cọc, phần còn lại thu khi giao. */
    depositAmount?: number;
}

interface PosOrderItemRequest {
    productVariantId: string;
    quantity: number;
    bomVersionId?: string;
    note?: string;
    itemDiscounts: PosDiscountRequest[];
}

export interface PosDiscountRequest {
    discountType: SaleBackendDiscountType;
    discountValue: number;
    source: SaleDiscountSource;
    reasonCode: string;
    usageId?: string;
    note?: string;
}

export interface OfflinePosCheckoutItemBody {
    productVariantId: string;
    quantity: number;
    note?: string;
    /** Giá đã chốt với khách tại quầy; máy chủ dùng khi offlineSale = true. */
    unitPriceSnapshot: number;
    itemDiscounts: PosDiscountRequest[];
}

/**
 * Body của POST /orders/pos/offline-checkout: cả giỏ hàng trong một request,
 * clientOrderId là khoá idempotency nên gửi lại bao nhiêu lần cũng chỉ ra một đơn.
 */
export interface OfflinePosCheckoutRequestBody {
    clientOrderId: string;
    /** ISO, giờ bán thật tại quầy. */
    soldAt: string;
    /** true = bán khi mất mạng (giữ giá đã in); false = giỏ cục bộ nhưng đang online. */
    offlineSale: boolean;
    branchId?: string;
    cashierId?: string;
    cashierNameSnapshot?: string;
    customerId?: string;
    orderType: string;
    orderDiscounts: PosDiscountRequest[];
    shippingFee: 0;
    paidAmount: number;
    paymentMethod: string;
    items: OfflinePosCheckoutItemBody[];
}

interface CreatePosOrderRequestBody {
    orderId?: string;
    customerId?: string;
    orderType: string;
    orderDiscounts?: PosDiscountRequest[];
    shippingFee: number;
    paidAmount?: number;
    paymentMethod?: string;
    paymentCode?: string;
    allowBackorder?: boolean;
    depositAmount?: number;
    items: PosOrderItemRequest[];
}

interface AddDraftOrderItemRequestBody {
    orderId?: string;
    branchId?: string;
    customerId?: string;
    orderType: string;
    productVariantId: string;
    quantity: number;
    discountAmount: number;
    inventoryLocationId?: string;
    note?: string;
    allowBackorder?: boolean;
}

interface UpdateDraftOrderItemQuantityRequestBody {
    quantity: number;
    allowBackorder?: boolean;
}

export interface PosCheckoutPaymentResponse {
    paymentId?: string;
    attemptId?: string;
    paymentCode?: string;
    orderId?: string;
    orderCode?: string;
    method: string;
    methodLabel?: string;
    provider?: string;
    status: string;
    amountRequired: number;
    amountPaid: number;
    receivedAmount?: number;
    changeAmount?: number;
    transferContent?: string;
    qrUrl?: string;
    bankCode?: string;
    bankAccountNumber?: string;
    bankAccountName?: string;
    referenceCode?: string;
    qrCodeUrl?: string;
    qrContent?: string;
    expiresAt?: string;
}

export interface PosOrderDto {
    id: string;
    orderCode: string;
    status?: string;
    paymentStatus?: string;
    expiresAt?: string;
    originalAmount?: number;
    discountAmount?: number;
    finalAmount?: number;
    paidAmount?: number;
    debtAmount?: number;
    /** Đơn chờ hàng: đã thu tiền (đủ hoặc cọc), giao sau khi có hàng. */
    backorder?: boolean;
    expectedAvailableAt?: string | null;
    depositAmount?: number;
    inventoryProcessingStatus?: string;
    items?: PosOrderItemDto[];
}

export interface PosOrderItemDto {
    id: string;
    productVariantId: string;
    skuSnapshot?: string;
    sku?: string;
    productNameSnapshot?: string;
    productName?: string;
    variantNameSnapshot?: string;
    quantity: number;
    actualUnitPrice?: number;
    unitPrice?: number;
    lineTotal?: number;
    note?: string | null;
}

export interface PosCheckoutResponse extends PosOrderDto {
    payment?: PosCheckoutPaymentResponse;
}

const orderTypeMap: Record<SaleOrderType, string> = {
    QUICK_SALE: "POS",
    DELIVERY: "DELIVERY"
};

const paymentMethodMap: Record<SalePaymentMethod, string> = {
    CASH: "CASH",
    BANK_TRANSFER: "BANK_TRANSFER",
    QR_CODE: "QR_CODE",
    E_WALLET: "E_WALLET",
    CARD: "CARD",
    COMBINED: "COMBINED"
};

// Chỉ kiểm tra định dạng: ID seed (vd b1000000-0000-0000-0000-000000000001) không theo version RFC
// nhưng backend vẫn chấp nhận.
const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function getProductVariantId(item: SaleCartItem) {
    const productVariantId = item.productVariantId ?? item.productId;

    if (!uuidPattern.test(productVariantId)) {
        throw new Error(
            `Sản phẩm ${item.sku} chưa có productVariantId dạng UUID. Vui lòng kiểm tra Catalog API.`
        );
    }

    return productVariantId;
}

function getCustomerId(value: string | null) {
    return value && uuidPattern.test(value) ? value : undefined;
}

function getCartItemDiscounts(item: SaleCartItem): PosDiscountRequest[] {
    if (!item.manualDiscount || item.manualDiscount.discountValue <= 0) {
        return [];
    }

    return [
        {
            discountType: item.manualDiscount.discountType,
            discountValue: item.manualDiscount.discountValue,
            source: "MANUAL",
            reasonCode: item.manualDiscount.reasonCode,
            note: item.manualDiscount.note
        }
    ];
}

export const posOrderApi = {
    create(input: CreatePosOrderInput) {
        return apiClient.post<PosOrderDto, CreatePosOrderRequestBody>("/orders", {
            customerId: input.customerId ?? undefined,
            orderType: orderTypeMap[input.orderType],
            orderDiscounts: [],
            shippingFee: 0,
            items: input.items.map((item) => ({
                productVariantId: getProductVariantId(item),
                quantity: item.quantity,
                note: item.note?.trim() || undefined,
                itemDiscounts: getCartItemDiscounts(item)
            }))
        });
    },

    addDraftItem(input: {
        orderId: string | null;
        branchId?: string | null;
        customerId: string | null;
        orderType: SaleOrderType;
        productVariantId: string;
        inventoryLocationId?: string | null;
        quantity?: number;
        note?: string;
        allowBackorder?: boolean;
    }) {
        if (!uuidPattern.test(input.productVariantId)) {
            throw new Error("Không xác định được productVariantId dạng UUID.");
        }

        return apiClient.post<PosOrderDto, AddDraftOrderItemRequestBody>(
            "/orders/pos/draft/items",
            {
                orderId: input.orderId ?? undefined,
                branchId:
                    input.branchId && uuidPattern.test(input.branchId)
                        ? input.branchId
                        : undefined,
                customerId: getCustomerId(input.customerId),
                orderType: orderTypeMap[input.orderType],
                productVariantId: input.productVariantId,
                quantity: input.quantity ?? 1,
                discountAmount: 0,
                inventoryLocationId: input.inventoryLocationId ?? undefined,
                note: input.note?.trim() || undefined,
                allowBackorder: input.allowBackorder || undefined
            }
        );
    },

    updateDraftItemQuantity(
        orderId: string,
        itemId: string,
        quantity: number,
        allowBackorder = false
    ) {
        return apiClient.put<PosOrderDto, UpdateDraftOrderItemQuantityRequestBody>(
            `/orders/pos/draft/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}`,
            { quantity, allowBackorder: allowBackorder || undefined }
        );
    },

    removeDraftItem(orderId: string, itemId: string) {
        return apiClient.delete<PosOrderDto>(
            `/orders/pos/draft/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}`
        );
    },

    cancelDraftOrder(orderId: string) {
        return apiClient.post<PosOrderDto, Record<string, never>>(
            `/orders/pos/draft/${encodeURIComponent(orderId)}/cancel`,
            {}
        );
    },

    /**
     * Đồng bộ đơn bán offline (hoặc giỏ cục bộ). Không retry tự động ở lớp HTTP:
     * bộ đồng bộ offline tự quyết định thử lại, và replay cùng clientOrderId là an toàn.
     */
    checkoutOffline(body: OfflinePosCheckoutRequestBody) {
        return apiClient.post<PosOrderDto, OfflinePosCheckoutRequestBody>(
            "/orders/pos/offline-checkout",
            body,
            {
                headers: { "Idempotency-Key": body.clientOrderId },
                timeoutMs: 60_000,
                retry: false
            }
        );
    },

    checkout(input: CheckoutPosOrderInput) {
        return apiClient.post<
            PosCheckoutResponse,
            CreatePosOrderRequestBody
        >("/orders/pos/checkout", {
            orderId: input.orderId ?? undefined,
            customerId: input.customerId ?? undefined,
            orderType: orderTypeMap[input.orderType],
            orderDiscounts: input.orderDiscounts,
            shippingFee: 0,
            paidAmount: input.paidAmount,
            paymentMethod: paymentMethodMap[input.paymentMethod],
            allowBackorder: input.allowBackorder || undefined,
            depositAmount: input.depositAmount,
            items: input.items.map((item) => ({
                productVariantId: getProductVariantId(item),
                quantity: item.quantity,
                note: item.note?.trim() || undefined,
                itemDiscounts: getCartItemDiscounts(item)
            }))
        });
    }
};
