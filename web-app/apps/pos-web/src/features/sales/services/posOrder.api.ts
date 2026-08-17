import { apiClient } from "../../../api/api-client";
import type {
    SaleCartItem,
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
    orderId: string;
    originalAmount: number;
    finalAmount: number;
    manualAdjustment?: PosManualAdjustmentInput;
    paymentMethod: SalePaymentMethod;
    paymentAmount: number;
    receivedAmount?: number;
    changeAmount?: number;
}

export interface PosManualAdjustmentInput {
    originalAmount: number;
    adjustedAmount: number;
    changeAmount: number;
    note: string;
}

interface PosOrderItemRequest {
    productVariantId: string;
    quantity: number;
    discountAmount: number;
}

interface CreatePosOrderRequestBody {
    customerId?: string;
    orderType: string;
    discountAmount: number;
    shippingFee: number;
    items: PosOrderItemRequest[];
}

interface AddDraftOrderItemRequestBody {
    orderId?: string;
    customerId?: string;
    orderType: string;
    productVariantId: string;
    quantity: number;
    discountAmount: number;
    inventoryLocationId?: string;
}

interface UpdateDraftOrderItemQuantityRequestBody {
    quantity: number;
}

interface CheckoutDraftOrderRequestBody {
    paidAmount: number;
    discountAmount: number;
    paymentMethod: string;
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

const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

export const posOrderApi = {
    create(input: CreatePosOrderInput) {
        return apiClient.post<PosOrderDto, CreatePosOrderRequestBody>("/orders", {
            customerId: input.customerId ?? undefined,
            orderType: orderTypeMap[input.orderType],
            discountAmount: input.discountAmount,
            shippingFee: 0,
            items: input.items.map((item) => ({
                productVariantId: getProductVariantId(item),
                quantity: item.quantity,
                discountAmount: 0
            }))
        });
    },

    addDraftItem(input: {
        orderId: string | null;
        customerId: string | null;
        orderType: SaleOrderType;
        productVariantId: string;
        inventoryLocationId?: string | null;
    }) {
        if (!uuidPattern.test(input.productVariantId)) {
            throw new Error("Không xác định được productVariantId dạng UUID.");
        }

        return apiClient.post<PosOrderDto, AddDraftOrderItemRequestBody>(
            "/orders/pos/draft/items",
            {
                orderId: input.orderId ?? undefined,
                customerId: getCustomerId(input.customerId),
                orderType: orderTypeMap[input.orderType],
                productVariantId: input.productVariantId,
                quantity: 1,
                discountAmount: 0,
                inventoryLocationId: input.inventoryLocationId ?? undefined
            }
        );
    },

    updateDraftItemQuantity(orderId: string, itemId: string, quantity: number) {
        return apiClient.put<PosOrderDto, UpdateDraftOrderItemQuantityRequestBody>(
            `/orders/pos/draft/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}`,
            { quantity }
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

    checkout(input: CheckoutPosOrderInput) {
        return apiClient.post<
            PosCheckoutResponse,
            CheckoutDraftOrderRequestBody
        >(`/orders/pos/${encodeURIComponent(input.orderId)}/checkout`, {
            paidAmount: input.paymentAmount,
            discountAmount: input.discountAmount,
            paymentMethod: paymentMethodMap[input.paymentMethod]
        });
    }
};
