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

interface CheckoutPosOrderRequestBody extends CreatePosOrderRequestBody {
    originalAmount: number;
    finalAmount: number;
    manualAdjustment?: PosManualAdjustmentInput;
    payment: {
        method: string;
        amount: number;
        receivedAmount?: number;
        changeAmount?: number;
    };
}

export interface PosCheckoutPaymentResponse {
    paymentId?: string;
    paymentCode?: string;
    method: string;
    status: string;
    amountRequired: number;
    amountPaid: number;
    receivedAmount?: number;
    changeAmount?: number;
    referenceCode?: string;
    qrCodeUrl?: string;
    qrContent?: string;
    expiresAt?: string;
}

export interface PosOrderDto {
    id: string;
    orderCode: string;
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
    BANK_TRANSFER: "QR_CODE",
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

    checkout(input: CheckoutPosOrderInput) {
        return apiClient.post<
            PosCheckoutResponse,
            CheckoutPosOrderRequestBody
        >("/orders/pos/checkout", {
            customerId: input.customerId ?? undefined,
            orderType: orderTypeMap[input.orderType],
            originalAmount: input.originalAmount,
            discountAmount: input.discountAmount,
            shippingFee: 0,
            finalAmount: input.finalAmount,
            manualAdjustment: input.manualAdjustment,
            payment: {
                method: paymentMethodMap[input.paymentMethod],
                amount: input.paymentAmount,
                receivedAmount: input.receivedAmount,
                changeAmount: input.changeAmount
            },
            items: input.items.map((item) => ({
                productVariantId: getProductVariantId(item),
                quantity: item.quantity,
                discountAmount: 0
            }))
        });
    }
};
