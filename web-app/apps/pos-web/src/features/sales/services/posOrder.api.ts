import { apiClient } from "../../../api/api-client";
import type { SaleCartItem, SaleOrderType } from "../types/sale.types";

export interface CreatePosOrderInput {
    customerId: string | null;
    orderType: SaleOrderType;
    discountAmount: number;
    items: SaleCartItem[];
}

export interface PosOrderResponse {
    id: string;
    orderCode: string;
    customerId: string | null;
    orderType: string;
    status: string;
    paymentStatus: string;
    originalAmount: number;
    discountAmount: number;
    shippingFee: number;
    finalAmount: number;
    paidAmount: number;
    debtAmount: number;
}

const orderTypeMap: Record<SaleOrderType, string> = {
    QUICK_SALE: "POS",
    DELIVERY: "DELIVERY"
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
        return apiClient.post<
            PosOrderResponse,
            {
                customerId?: string;
                orderType: string;
                discountAmount: number;
                shippingFee: number;
                items: Array<{
                    productVariantId: string;
                    quantity: number;
                    discountAmount: number;
                }>;
            }
        >("/orders", {
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
    }
};
