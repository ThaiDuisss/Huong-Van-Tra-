import { ApiError } from "@hvt/api-client";

import { posOrderApi } from "./posOrder.api";
import type {
    SaleCartItem,
    SaleOrderType,
    SalePaymentMethod,
    SaleTotals
} from "../types/sale.types";

interface PosCheckoutInput {
    orderId: string | null;
    cartItems: SaleCartItem[];
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    cashReceived: number;
    totals: SaleTotals;
    originalSubtotal: number;
    subtotalOverride: number | null;
    subtotalOverrideNote: string;
    selectedCustomerId: string | null;
}

interface PosCheckoutResult {
    orderCode: string;
}

const unsupportedPaymentMessages: Partial<Record<SalePaymentMethod, string>> = {
    COMBINED:
        "Payment-Service hiện chưa có API thanh toán tổng hợp."
};

const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function getErrorMessage(error: unknown) {
    if (error instanceof ApiError) {
        return error.message;
    }

    if (error instanceof Error) {
        return error.message;
    }

    return "Có lỗi xảy ra. Vui lòng thử lại.";
}

function getCustomerId(value: string | null) {
    return value && uuidPattern.test(value) ? value : null;
}

export function getPosCheckoutValidationMessage(
    input: Pick<
        PosCheckoutInput,
        "orderId" | "cartItems" | "orderType" | "paymentMethod" | "cashReceived" | "totals"
        | "subtotalOverride" | "subtotalOverrideNote"
    >
) {
    if (input.cartItems.length === 0) {
        return "Giỏ hàng đang rỗng.";
    }

    if (input.orderId === null) {
        return "Chưa có sản phẩm trong đơn.";
    }

    if (input.orderType !== "QUICK_SALE") {
        return "Vui lòng chọn tab Bán nhanh để thanh toán.";
    }

    if (
        input.paymentMethod === "CASH" &&
        input.cashReceived < input.totals.total
    ) {
        return "Tiền khách đưa chưa đủ để thanh toán.";
    }

    if (
        input.subtotalOverride !== null &&
        input.subtotalOverrideNote.trim().length === 0
    ) {
        return "Vui lòng nhập ghi chú khi chỉnh sửa tổng tiền hàng.";
    }

    return unsupportedPaymentMessages[input.paymentMethod] ?? "";
}

async function checkoutPosOrder(input: PosCheckoutInput) {
    if (input.orderId === null) {
        throw new Error("Chưa có sản phẩm trong đơn.");
    }

    const manualAdjustment =
        input.subtotalOverride === null
            ? undefined
            : {
                originalAmount: input.originalSubtotal,
                adjustedAmount: input.subtotalOverride,
                changeAmount:
                    input.subtotalOverride - input.originalSubtotal,
                note: input.subtotalOverrideNote
            };
    const receivedAmount =
        input.paymentMethod === "CASH"
            ? Math.max(input.cashReceived, input.totals.total)
            : undefined;
    const changeAmount =
        receivedAmount === undefined
            ? undefined
            : Math.max(0, receivedAmount - input.totals.total);

    try {
        return await posOrderApi.checkout({
            customerId: getCustomerId(input.selectedCustomerId),
            orderId: input.orderId,
            orderType: input.orderType,
            originalAmount: input.originalSubtotal,
            discountAmount: input.totals.discountAmount,
            finalAmount: input.totals.total,
            manualAdjustment,
            items: input.cartItems,
            paymentMethod: input.paymentMethod,
            paymentAmount: input.totals.total,
            receivedAmount,
            changeAmount
        });
    } catch (error) {
        throw new Error(
            `Hoàn tất thanh toán thất bại: ${getErrorMessage(error)}`,
            {
                cause: error
            }
        );
    }
}

export async function completePosCheckout(
    input: PosCheckoutInput
): Promise<PosCheckoutResult> {
    const validationMessage = getPosCheckoutValidationMessage(input);

    if (validationMessage) {
        throw new Error(validationMessage);
    }

    const order = await checkoutPosOrder(input);

    return {
        orderCode: order.orderCode
    };
}
