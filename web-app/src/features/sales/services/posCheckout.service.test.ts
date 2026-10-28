import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./posOrder.api", () => ({ posOrderApi: { checkout: vi.fn() } }));

import { ApiError } from "@/lib/api-client";

import type { SaleCartItem, SaleDiscountOption, SaleTotals } from "../types/sale.types";
import {
    buildLocalPosSale,
    completePosCheckout,
    getLocalPosSaleValidationMessage,
    getPosAmountDue,
    getPosCheckoutValidationMessage
} from "./posCheckout.service";
import { posOrderApi } from "./posOrder.api";
import { formatCurrency, parseCurrencyInput } from "./money";

const CUSTOMER_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
const CASHIER_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
const BRANCH_ID = "16fd2706-8baf-433b-82eb-8c7fada847da";

const noDiscount: SaleDiscountOption = {
    id: "none",
    label: "Không áp dụng",
    code: "",
    type: "NONE",
    value: 0,
    description: "",
    status: "AVAILABLE",
    condition: "",
    minSubtotal: null,
    expiresIn: null
};

const cartItem: SaleCartItem = {
    productId: "p-1",
    productVariantId: "v-1",
    name: "Trà sen Tây Hồ",
    sku: "TS-100",
    variant: "Hộp 100g",
    quantity: 2,
    unitPrice: 150000
};

function totals(total: number, discount = 0): SaleTotals {
    return {
        subtotal: total + discount,
        itemDiscountAmount: 0,
        orderDiscountAmount: discount,
        discountAmount: discount,
        total
    };
}

function checkoutInput(overrides: Partial<Parameters<typeof completePosCheckout>[0]> = {}) {
    return {
        orderId: "order-1",
        cartItems: [cartItem],
        orderType: "QUICK_SALE" as const,
        paymentMethod: "CASH" as const,
        cashReceived: 300000,
        totals: totals(300000),
        originalSubtotal: 300000,
        selectedDiscount: noDiscount,
        manualOrderDiscount: null,
        selectedCustomerId: null,
        ...overrides
    };
}

describe("getPosAmountDue", () => {
    it("collects the full total for a normal sale", () => {
        expect(getPosAmountDue({ paymentMethod: "CASH", totals: totals(300000) })).toBe(300000);
    });

    it("collects only the deposit for a cash backorder with a deposit below the total", () => {
        expect(
            getPosAmountDue({ paymentMethod: "CASH", totals: totals(300000), allowBackorder: true, depositAmount: 100000 })
        ).toBe(100000);
    });

    it("ignores the deposit when it is not a cash backorder or not below the total", () => {
        expect(
            getPosAmountDue({ paymentMethod: "BANK_TRANSFER", totals: totals(300000), allowBackorder: true, depositAmount: 100000 })
        ).toBe(300000);
        expect(
            getPosAmountDue({ paymentMethod: "CASH", totals: totals(300000), allowBackorder: false, depositAmount: 100000 })
        ).toBe(300000);
        expect(
            getPosAmountDue({ paymentMethod: "CASH", totals: totals(300000), allowBackorder: true, depositAmount: 300000 })
        ).toBe(300000);
    });
});

describe("getPosCheckoutValidationMessage", () => {
    it("accepts a valid cash checkout with exact cash", () => {
        expect(getPosCheckoutValidationMessage(checkoutInput())).toBe("");
    });

    it("rejects an empty cart, a missing draft order and the delivery tab", () => {
        expect(getPosCheckoutValidationMessage(checkoutInput({ cartItems: [] }))).toMatch(/rỗng/);
        expect(getPosCheckoutValidationMessage(checkoutInput({ orderId: null }))).toMatch(/Chưa có sản phẩm/);
        expect(getPosCheckoutValidationMessage(checkoutInput({ orderType: "DELIVERY" }))).toMatch(/Bán nhanh/);
    });

    it("rejects cash one dong below the amount due", () => {
        expect(getPosCheckoutValidationMessage(checkoutInput({ cashReceived: 299999 }))).toMatch(/chưa đủ/);
    });

    it("checks deposit rules: backorder only, cash only and below the total", () => {
        expect(getPosCheckoutValidationMessage(checkoutInput({ depositAmount: 50000 }))).toMatch(/đơn chờ hàng/);
        expect(
            getPosCheckoutValidationMessage(
                checkoutInput({ depositAmount: 50000, allowBackorder: true, paymentMethod: "BANK_TRANSFER" })
            )
        ).toMatch(/tiền mặt/);
        expect(
            getPosCheckoutValidationMessage(checkoutInput({ depositAmount: 300000, allowBackorder: true }))
        ).toMatch(/nhỏ hơn tổng tiền/);
        expect(
            getPosCheckoutValidationMessage(
                checkoutInput({ depositAmount: 100000, allowBackorder: true, cashReceived: 100000 })
            )
        ).toBe("");
    });

    it("blocks the combined payment method that the backend does not support", () => {
        expect(getPosCheckoutValidationMessage(checkoutInput({ paymentMethod: "COMBINED" }))).toMatch(/tổng hợp/);
    });

    it("does not require cash for a bank transfer", () => {
        expect(
            getPosCheckoutValidationMessage(checkoutInput({ paymentMethod: "BANK_TRANSFER", cashReceived: 0 }))
        ).toBe("");
    });
});

describe("completePosCheckout", () => {
    beforeEach(() => {
        vi.mocked(posOrderApi.checkout).mockReset();
    });

    it("throws the validation message without calling the API", async () => {
        await expect(completePosCheckout(checkoutInput({ cartItems: [] }))).rejects.toThrow(/rỗng/);
        expect(posOrderApi.checkout).not.toHaveBeenCalled();
    });

    it("sends received cash, change and discounts, and maps the result", async () => {
        vi.mocked(posOrderApi.checkout).mockResolvedValue({
            orderCode: "POS-00012",
            payment: { amountRequired: 270000, amountPaid: 270000, status: "PAID", method: "CASH", qrContent: "HVTRA1" },
            backorder: false,
            debtAmount: "0"
        } as never);

        const result = await completePosCheckout(
            checkoutInput({
                cashReceived: 500000,
                totals: totals(270000, 30000),
                selectedCustomerId: CUSTOMER_ID,
                selectedDiscount: { ...noDiscount, id: "promo-1", type: "PERCENT", value: 10, code: "KM10" },
                manualOrderDiscount: { discountType: "FIXED_AMOUNT", discountValue: 0, reasonCode: "X" }
            })
        );

        const body = vi.mocked(posOrderApi.checkout).mock.calls[0][0];
        expect(body).toMatchObject({
            customerId: CUSTOMER_ID,
            finalAmount: 270000,
            paidAmount: 270000,
            receivedAmount: 500000,
            changeAmount: 230000,
            depositAmount: undefined
        });
        expect(body.orderDiscounts).toEqual([
            expect.objectContaining({ discountType: "PERCENTAGE", source: "PROMOTION", reasonCode: "KM10" })
        ]);
        expect(result).toEqual({
            orderCode: "POS-00012",
            payment: expect.objectContaining({ status: "PAID", transferContent: "HVTRA1" }),
            backorder: false,
            expectedAvailableAt: null,
            debtAmount: 0
        });
    });

    it("sends a deposit for a cash backorder and drops a non-UUID customer id", async () => {
        vi.mocked(posOrderApi.checkout).mockResolvedValue({
            orderCode: "POS-00013",
            payment: null,
            backorder: true,
            expectedAvailableAt: "2026-10-30",
            debtAmount: 200000
        } as never);

        const result = await completePosCheckout(
            checkoutInput({ allowBackorder: true, depositAmount: 100000, cashReceived: 100000, selectedCustomerId: "walk-in" })
        );

        expect(vi.mocked(posOrderApi.checkout).mock.calls[0][0]).toMatchObject({
            customerId: null,
            paidAmount: 100000,
            depositAmount: 100000,
            changeAmount: 0,
            allowBackorder: true
        });
        expect(result).toMatchObject({ backorder: true, expectedAvailableAt: "2026-10-30", debtAmount: 200000, payment: null });
    });

    it("leaves cash fields empty for a bank transfer and marks coupons", async () => {
        vi.mocked(posOrderApi.checkout).mockResolvedValue({ orderCode: "POS-1", payment: null } as never);

        await completePosCheckout(
            checkoutInput({
                paymentMethod: "BANK_TRANSFER",
                cashReceived: 0,
                totals: totals(250000, 50000),
                couponUsageId: "usage-1",
                selectedDiscount: { ...noDiscount, id: "c-1", type: "AMOUNT", value: 50000, couponId: "c-1", couponCode: "GIAM50" }
            })
        );

        const body = vi.mocked(posOrderApi.checkout).mock.calls[0][0];
        expect(body.receivedAmount).toBeUndefined();
        expect(body.changeAmount).toBeUndefined();
        expect(body.orderDiscounts).toEqual([
            expect.objectContaining({ discountType: "FIXED_AMOUNT", source: "COUPON", reasonCode: "GIAM50", usageId: "usage-1" })
        ]);
    });

    it("wraps API failures in a readable message", async () => {
        vi.mocked(posOrderApi.checkout).mockRejectedValue(
            new ApiError({ status: 409, code: "OUT_OF_STOCK", message: "Hết hàng trên kệ" })
        );

        await expect(completePosCheckout(checkoutInput())).rejects.toThrow(
            "Hoàn tất thanh toán thất bại: Hết hàng trên kệ"
        );
    });
});

describe("local (offline) sale", () => {
    const localInput = {
        cartItems: [cartItem, { ...cartItem, productId: "p-2", productVariantId: undefined, quantity: 1, unitPrice: 99999.999, note: "  ít đá " }],
        orderType: "QUICK_SALE" as const,
        paymentMethod: "CASH" as const,
        cashReceived: 500000,
        totals: totals(400000),
        originalSubtotal: 400000,
        selectedDiscount: noDiscount,
        manualOrderDiscount: null,
        selectedCustomer: null,
        cashierId: CASHIER_ID,
        cashierName: "Thu ngân 01",
        ownerId: "owner-1",
        branchId: "not-a-uuid",
        staleDraftOrderId: "draft-9",
        offline: true,
        now: new Date(2026, 9, 26, 10, 0, 0)
    };

    it("validates cash only, no coupon and enough cash", () => {
        expect(getLocalPosSaleValidationMessage(localInput)).toBe("");
        expect(getLocalPosSaleValidationMessage({ ...localInput, cartItems: [] })).toMatch(/rỗng/);
        expect(getLocalPosSaleValidationMessage({ ...localInput, paymentMethod: "BANK_TRANSFER" })).toMatch(/chỉ thu tiền mặt/);
        expect(
            getLocalPosSaleValidationMessage({ ...localInput, paymentMethod: "BANK_TRANSFER", offline: false })
        ).toMatch(/mất mạng/);
        expect(
            getLocalPosSaleValidationMessage({ ...localInput, selectedDiscount: { ...noDiscount, couponCode: "GIAM50" } })
        ).toMatch(/Mã giảm giá/);
        expect(getLocalPosSaleValidationMessage({ ...localInput, cashReceived: 399999 })).toMatch(/chưa đủ/);
    });

    it("builds a pending offline record with a temp receipt code and change", () => {
        const record = buildLocalPosSale({ ...localInput, branchId: BRANCH_ID });

        expect(record.status).toBe("PENDING");
        expect(record.attempts).toBe(0);
        expect(record.tempCode).toMatch(/^OFF-261026-[0-9A-F]{4}$/);
        expect(record.totalAmount).toBe(400000);
        expect(record.itemCount).toBe(2);
        expect(record.staleDraftOrderId).toBe("draft-9");
        expect(record.request).toMatchObject({
            clientOrderId: record.clientOrderId,
            offlineSale: true,
            branchId: BRANCH_ID,
            cashierId: CASHIER_ID,
            paymentMethod: "CASH",
            paidAmount: 400000,
            orderType: "POS"
        });
        expect(record.receipt).toMatchObject({ orderCode: record.tempCode, paidAmount: 500000, changeAmount: 100000, totalItems: 3 });
    });

    it("rounds unit prices to cents, falls back to productId and trims notes", () => {
        const record = buildLocalPosSale(localInput);
        const second = record.request.items[1];

        expect(record.request.branchId).toBeUndefined();
        expect(second.productVariantId).toBe("p-2");
        expect(second.unitPriceSnapshot).toBe(100000);
        expect(second.note).toBe("ít đá");
        expect(record.request.items[0].note).toBeUndefined();
    });

    it("copies a manual item discount into the request", () => {
        const record = buildLocalPosSale({
            ...localInput,
            cartItems: [{ ...cartItem, manualDiscount: { discountType: "PERCENTAGE", discountValue: 5, reasonCode: "VIP" } }]
        });

        expect(record.request.items[0].itemDiscounts).toEqual([
            { discountType: "PERCENTAGE", discountValue: 5, source: "MANUAL", reasonCode: "VIP", note: undefined }
        ]);
    });
});

describe("money helpers", () => {
    it("formats VND without decimals and never negative", () => {
        expect(formatCurrency(1250000).replace(/\s/g, " ")).toBe("1.250.000 ₫");
        expect(formatCurrency(-5000).replace(/\s/g, " ")).toBe("0 ₫");
    });

    it("parses typed currency by keeping digits only", () => {
        expect(parseCurrencyInput("1.250.000 ₫")).toBe(1250000);
        expect(parseCurrencyInput("")).toBe(0);
        expect(parseCurrencyInput("abc")).toBe(0);
    });
});
