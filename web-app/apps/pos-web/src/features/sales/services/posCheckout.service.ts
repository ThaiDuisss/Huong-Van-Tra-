import { ApiError } from "@hvt/api-client";

import { posOrderApi } from "./posOrder.api";
import type {
    SaleCartItem,
    SaleDiscountOption,
    SaleManualDiscount,
    SaleOrderType,
    SalePaymentMethod,
    SaleTotals
} from "../types/sale.types";
import type { PosDiscountRequest } from "./posOrder.api";

interface PosCheckoutInput {
    orderId: string | null;
    cartItems: SaleCartItem[];
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    cashReceived: number;
    totals: SaleTotals;
    originalSubtotal: number;
    selectedDiscount: SaleDiscountOption;
    manualOrderDiscount: SaleManualDiscount | null;
    couponUsageId?: string | null;
    selectedCustomerId: string | null;
}

interface PosCheckoutResult {
    orderCode: string;
    payment: PosCheckoutPaymentResult | null;
}

export interface PosCheckoutPaymentResult {
    paymentId?: string;
    attemptId?: string;
    paymentCode?: string;
    orderId?: string;
    orderCode?: string;
    amountRequired: number;
    amountPaid: number;
    receivedAmount?: number;
    changeAmount?: number;
    status: string;
    method: string;
    methodLabel?: string;
    provider?: string;
    transferContent?: string;
    qrUrl?: string;
    bankCode?: string;
    bankAccountNumber?: string;
    bankAccountName?: string;
    expiresAt?: string;
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

function getOrderDiscounts(input: PosCheckoutInput): PosDiscountRequest[] {
    const discounts: PosDiscountRequest[] = [];

    if (
        input.selectedDiscount.id !== "none" &&
        input.totals.orderDiscountAmount > 0
    ) {
        const discountType =
            input.selectedDiscount.type === "PERCENT"
                ? "PERCENTAGE"
                : "FIXED_AMOUNT";

        discounts.push({
            discountType,
            discountValue: input.selectedDiscount.value,
            source: input.selectedDiscount.couponId ? "COUPON" : "PROMOTION",
            reasonCode:
                input.selectedDiscount.couponCode ||
                input.selectedDiscount.code ||
                input.selectedDiscount.label,
            usageId: input.selectedDiscount.couponId
                ? input.couponUsageId ?? undefined
                : undefined,
            note: input.selectedDiscount.description
        });
    }

    if (
        input.manualOrderDiscount &&
        input.manualOrderDiscount.discountValue > 0
    ) {
        discounts.push({
            discountType: input.manualOrderDiscount.discountType,
            discountValue: input.manualOrderDiscount.discountValue,
            source: "MANUAL",
            reasonCode: input.manualOrderDiscount.reasonCode,
            note: input.manualOrderDiscount.note
        });
    }

    return discounts;
}

function toPaymentResult(
    payment: Awaited<ReturnType<typeof checkoutPosOrder>>["payment"]
): PosCheckoutPaymentResult | null {
    if (!payment) {
        return null;
    }

    return {
        paymentId: payment.paymentId,
        attemptId: payment.attemptId,
        paymentCode: payment.paymentCode,
        orderId: payment.orderId,
        orderCode: payment.orderCode,
        amountRequired: payment.amountRequired,
        amountPaid: payment.amountPaid,
        receivedAmount: payment.receivedAmount,
        changeAmount: payment.changeAmount,
        status: payment.status,
        method: payment.method,
        methodLabel: payment.methodLabel,
        provider: payment.provider,
        transferContent: payment.transferContent ?? payment.qrContent,
        qrUrl: payment.qrUrl ?? payment.qrCodeUrl,
        bankCode: payment.bankCode,
        bankAccountNumber: payment.bankAccountNumber,
        bankAccountName: payment.bankAccountName,
        expiresAt: payment.expiresAt
    };
}

export function getPosCheckoutValidationMessage(
    input: Pick<
        PosCheckoutInput,
        "orderId" | "cartItems" | "orderType" | "paymentMethod" | "cashReceived" | "totals"
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

    return unsupportedPaymentMessages[input.paymentMethod] ?? "";
}

async function checkoutPosOrder(input: PosCheckoutInput) {
    if (input.orderId === null) {
        throw new Error("Chưa có sản phẩm trong đơn.");
    }

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
            orderDiscounts: getOrderDiscounts(input),
            items: input.cartItems,
            paymentMethod: input.paymentMethod,
            paidAmount: input.totals.total,
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
        orderCode: order.orderCode,
        payment: toPaymentResult(order.payment)
    };
}
