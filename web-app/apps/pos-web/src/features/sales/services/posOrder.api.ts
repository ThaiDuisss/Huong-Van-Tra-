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

interface CreatePosOrderRequestBody {
    orderId?: string;
    customerId?: string;
    orderType: string;
    orderDiscounts?: PosDiscountRequest[];
    shippingFee: number;
    paidAmount?: number;
    paymentMethod?: string;
    paymentCode?: string;
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
}

interface UpdateDraftOrderItemQuantityRequestBody {
    quantity: number;
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
    }) {
        if (!uuidPattern.test(input.productVariantId)) {
            throw new Error("Không xác định được productVariantId dạng UUID.");
        }

        if (
            !input.orderId &&
            (!input.branchId || !uuidPattern.test(input.branchId))
        ) {
            throw new Error(
                "POS chưa được cấu hình chi nhánh để tạo đơn hàng."
            );
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
                note: input.note?.trim() || undefined
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
            CreatePosOrderRequestBody
        >("/orders/pos/checkout", {
            orderId: input.orderId ?? undefined,
            customerId: input.customerId ?? undefined,
            orderType: orderTypeMap[input.orderType],
            orderDiscounts: input.orderDiscounts,
            shippingFee: 0,
            paidAmount: input.paidAmount,
            paymentMethod: paymentMethodMap[input.paymentMethod],
            items: input.items.map((item) => ({
                productVariantId: getProductVariantId(item),
                quantity: item.quantity,
                note: item.note?.trim() || undefined,
                itemDiscounts: getCartItemDiscounts(item)
            }))
        });
    }
};
