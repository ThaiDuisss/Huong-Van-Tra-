import { ApiError } from "@hvt/api-client";

import { posOrderApi } from "./posOrder.api";
import type { OfflinePosCheckoutRequestBody } from "./posOrder.api";
import type { SalesOrderDetail } from "../../order/types/sales-history.types";
import type { OfflineOrderRecord } from "../offline/offline-order-types";
import {
    buildOfflineTempCode,
    createClientOrderId
} from "../offline/offline-order-queue";
import type {
    SaleCartItem,
    SaleDiscountOption,
    SaleManualDiscount,
    SaleOrderType,
    SalePaymentMethod,
    SaleTotals
} from "../types/sale.types";
import type { PosDiscountRequest } from "./posOrder.api";
import type { SaleCustomer } from "../types/sale.types";

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
    /** Khách đồng ý chờ hàng cho mặt hàng đang thiếu. */
    allowBackorder?: boolean;
    /** Tiền cọc thu trước của đơn chờ hàng (chỉ tiền mặt). 0 hoặc bỏ trống = thu đủ. */
    depositAmount?: number;
}

interface PosCheckoutResult {
    orderCode: string;
    payment: PosCheckoutPaymentResult | null;
    /** Đơn được ghi nhận là đơn chờ hàng: chưa giao, chưa trừ tồn. */
    backorder: boolean;
    expectedAvailableAt: string | null;
    debtAmount: number;
}

/** Số tiền phải thu ở lần thanh toán này: tiền cọc nếu đơn chờ hàng đặt cọc, ngược lại là tổng tiền. */
export function getPosAmountDue(
    input: Pick<PosCheckoutInput, "paymentMethod" | "totals" | "allowBackorder" | "depositAmount">
) {
    const deposit = input.depositAmount ?? 0;

    return input.allowBackorder &&
        input.paymentMethod === "CASH" &&
        deposit > 0 &&
        deposit < input.totals.total
        ? deposit
        : input.totals.total;
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
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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

function getOrderDiscounts(
    input: Pick<PosCheckoutInput, "selectedDiscount" | "manualOrderDiscount" | "totals"> & {
        couponUsageId?: string | null;
    }
): PosDiscountRequest[] {
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
        | "orderId"
        | "cartItems"
        | "orderType"
        | "paymentMethod"
        | "cashReceived"
        | "totals"
        | "allowBackorder"
        | "depositAmount"
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

    if ((input.depositAmount ?? 0) > 0) {
        if (!input.allowBackorder) {
            return "Chỉ đơn chờ hàng mới được đặt cọc.";
        }

        if (input.paymentMethod !== "CASH") {
            return "Đặt cọc hiện chỉ hỗ trợ tiền mặt.";
        }

        if ((input.depositAmount ?? 0) >= input.totals.total) {
            return "Tiền cọc phải nhỏ hơn tổng tiền của đơn.";
        }
    }

    if (
        input.paymentMethod === "CASH" &&
        input.cashReceived < getPosAmountDue(input)
    ) {
        return "Tiền khách đưa chưa đủ để thanh toán.";
    }

    return unsupportedPaymentMessages[input.paymentMethod] ?? "";
}

async function checkoutPosOrder(input: PosCheckoutInput) {
    if (input.orderId === null) {
        throw new Error("Chưa có sản phẩm trong đơn.");
    }

    const amountDue = getPosAmountDue(input);
    const depositAmount = amountDue < input.totals.total ? amountDue : undefined;
    const receivedAmount =
        input.paymentMethod === "CASH"
            ? Math.max(input.cashReceived, amountDue)
            : undefined;
    const changeAmount =
        receivedAmount === undefined
            ? undefined
            : Math.max(0, receivedAmount - amountDue);

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
            paidAmount: amountDue,
            receivedAmount,
            changeAmount,
            allowBackorder: input.allowBackorder,
            depositAmount
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
        payment: toPaymentResult(order.payment),
        backorder: Boolean(order.backorder),
        expectedAvailableAt: order.expectedAvailableAt ?? null,
        debtAmount: Number(order.debtAmount ?? 0)
    };
}

export interface LocalPosSaleInput {
    cartItems: SaleCartItem[];
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    cashReceived: number;
    totals: SaleTotals;
    originalSubtotal: number;
    selectedDiscount: SaleDiscountOption;
    manualOrderDiscount: SaleManualDiscount | null;
    selectedCustomer: SaleCustomer | null;
    /** Thu ngân đang bán (UUID identity) để máy chủ ghi đúng người bán khi đồng bộ muộn. */
    cashierId: string | null;
    cashierName: string | null;
    ownerId: string | null;
    branchId: string | null;
    /** Đơn nháp đã tạo trên máy chủ trước khi mất mạng; huỷ sau khi đồng bộ. */
    staleDraftOrderId: string | null;
    /** true khi đang mất mạng: đơn vào hàng chờ, giữ giá đã in. */
    offline: boolean;
    now?: Date;
}

/**
 * Kiểm tra giỏ cục bộ trước khi lưu/gửi một phát. Khác với checkout đơn nháp:
 * không cần orderId, nhưng chỉ tiền mặt và không dùng mã giảm giá (cần máy chủ giữ mã).
 */
export function getLocalPosSaleValidationMessage(
    input: Pick<
        LocalPosSaleInput,
        "cartItems" | "orderType" | "paymentMethod" | "cashReceived" | "totals" | "selectedDiscount" | "offline"
    >
) {
    if (input.cartItems.length === 0) {
        return "Giỏ hàng đang rỗng.";
    }

    if (input.orderType !== "QUICK_SALE") {
        return "Vui lòng chọn tab Bán nhanh để thanh toán.";
    }

    if (input.paymentMethod !== "CASH") {
        return input.offline
            ? "Mất kết nối máy chủ: chỉ thu tiền mặt được."
            : "Giỏ có mặt hàng thêm khi mất mạng: vui lòng thu tiền mặt, hoặc đóng hoá đơn và tạo lại.";
    }

    if (input.selectedDiscount.couponCode) {
        return "Mã giảm giá cần máy chủ xác nhận, không dùng được cho đơn này.";
    }

    if (input.cashReceived < input.totals.total) {
        return "Tiền khách đưa chưa đủ để thanh toán.";
    }

    return "";
}

function toUnitPrice(value: number) {
    return Math.round(value * 100) / 100;
}

/**
 * Dựng bản ghi đơn bán offline: body gửi máy chủ + hoá đơn in tại máy với mã tạm.
 * Tổng tiền lấy từ máy POS (đã hiện cho khách), máy chủ tính lại khi đồng bộ.
 */
export function buildLocalPosSale(input: LocalPosSaleInput): OfflineOrderRecord {
    const now = input.now ?? new Date();
    const clientOrderId = createClientOrderId();
    const tempCode = buildOfflineTempCode(clientOrderId, now);
    const soldAt = now.toISOString();
    const total = input.totals.total;
    const paidAmount = total;
    const receivedAmount = Math.max(input.cashReceived, total);
    const changeAmount = Math.max(0, receivedAmount - total);
    const customerId = getCustomerId(input.selectedCustomer?.id ?? null);

    const request: OfflinePosCheckoutRequestBody = {
        clientOrderId,
        soldAt,
        offlineSale: input.offline,
        branchId: input.branchId && uuidPattern.test(input.branchId) ? input.branchId : undefined,
        cashierId: input.cashierId && uuidPattern.test(input.cashierId) ? input.cashierId : undefined,
        cashierNameSnapshot: input.cashierName ?? undefined,
        customerId: customerId ?? undefined,
        orderType: "POS",
        orderDiscounts: getOrderDiscounts({
            selectedDiscount: input.selectedDiscount,
            manualOrderDiscount: input.manualOrderDiscount,
            totals: input.totals
        }),
        shippingFee: 0,
        paidAmount,
        paymentMethod: "CASH",
        items: input.cartItems.map((item) => ({
            productVariantId: item.productVariantId ?? item.productId,
            quantity: item.quantity,
            note: item.note?.trim() || undefined,
            unitPriceSnapshot: toUnitPrice(item.unitPrice),
            itemDiscounts:
                item.manualDiscount && item.manualDiscount.discountValue > 0
                    ? [
                          {
                              discountType: item.manualDiscount.discountType,
                              discountValue: item.manualDiscount.discountValue,
                              source: "MANUAL" as const,
                              reasonCode: item.manualDiscount.reasonCode,
                              note: item.manualDiscount.note
                          }
                      ]
                    : []
        }))
    };

    const receipt: SalesOrderDetail = {
        orderCode: tempCode,
        soldAt,
        createdAt: soldAt,
        cashierName: input.cashierName,
        customerName: input.selectedCustomer?.name ?? null,
        customerPhone: input.selectedCustomer?.phone ?? null,
        paymentMethod: "CASH",
        paymentMethodLabel: "Tiền mặt",
        originalAmount: input.originalSubtotal,
        subTotal: input.totals.subtotal,
        discountAmount: input.totals.discountAmount,
        finalAmount: total,
        paidAmount: receivedAmount,
        changeAmount,
        debtAmount: 0,
        totalItems: input.cartItems.reduce((sum, item) => sum + item.quantity, 0),
        items: input.cartItems.map((item) => ({
            productName: item.name,
            variantName: item.variant || null,
            sku: item.sku,
            quantity: item.quantity,
            unitPrice: item.unitPrice,
            lineTotal: toUnitPrice(item.unitPrice * item.quantity)
        }))
    };

    return {
        clientOrderId,
        tempCode,
        ownerId: input.ownerId,
        cashierName: input.cashierName,
        soldAt,
        status: "PENDING",
        attempts: 0,
        lastError: null,
        lastAttemptAt: null,
        totalAmount: total,
        itemCount: input.cartItems.length,
        request,
        receipt,
        staleDraftOrderId: input.staleDraftOrderId,
        result: null,
        syncedAt: null
    };
}
