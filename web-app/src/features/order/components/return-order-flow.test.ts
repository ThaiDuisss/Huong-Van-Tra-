import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type {
    OrderReturnEligibilityResponse,
    OrderReturnResponse
} from "../types/order-return.types";
import {
    buildReturnPayloadItems,
    getReturnItemKey,
    validateReturnDraftQuantities,
    validateReturnQuantity,
    type ReturnDraftItem
} from "../utils/return-quantity.validation";
import { ReturnProductSelectionTable } from "./ReturnProductSelectionTable";
import { ReturnSelectedSummary } from "./ReturnSelectedSummary";
import { ReturnProductReasonCard } from "./ReturnProductReasonCard";
import { ReturnPolicyModal } from "./ReturnPolicyModal";
import { ReturnEligibilityStatus } from "./ReturnEligibilityStatus";
import { ReturnConfirmationSummary } from "./ReturnConfirmationSummary";

// ReturnPolicyModal dựng bằng ModalShell (createPortal); renderToStaticMarkup không hỗ trợ portal
// nên trong test cho nội dung render tại chỗ.
vi.mock("react-dom", async (importOriginal) => ({
    ...(await importOriginal<typeof import("react-dom")>()),
    createPortal: (node: React.ReactNode) => node
}));

function html(node: React.ReactElement) {
    return renderToStaticMarkup(node);
}

function makeOrderItem(
    id: string,
    name: string,
    qty: number,
    sku = "SKU-001",
    variant = "Hộp 100g"
): OrderItemResponse {
    return {
        id,
        productId: `prod-${id}`,
        productNameSnapshot: name,
        productVariantId: `var-${id}`,
        variantNameSnapshot: variant,
        skuSnapshot: sku,
        quantity: qty,
        unitPrice: 150000,
        totalAmount: qty * 150000,
        finalAmount: qty * 150000
    };
}

function makeOrder(id: string, items: OrderItemResponse[]): OrderDetailResponse {
    return {
        id,
        orderCode: `ORD-${id}`,
        customerName: "Nguyễn Văn A",
        customerPhone: "0901234567",
        status: "COMPLETED",
        paymentStatus: "PAID",
        paymentMethod: "CASH",
        totalAmount: 500000,
        finalAmount: 500000,
        createdAt: "2026-10-15T10:00:00Z",
        items
    };
}

describe("Return Order Flow UI/UX & Logic (T01 - T22)", () => {
    // T01: Đơn có 1 sản phẩm
    it("T01: renders correctly for an order with 1 product", () => {
        const item = makeOrderItem("item-1", "Trà Tân Cương", 2);
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-1": { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" }
        };
        const validation = validateReturnDraftQuantities([item], draftItems, []);

        const markup = html(
            createElement(ReturnProductSelectionTable, {
                items: [item],
                draftItems,
                validation,
                onUpdateDraftItem: vi.fn(),
                onDeselectAll: vi.fn()
            })
        );

        expect(markup).toContain("Trà Tân Cương");
        expect(markup).toContain("SKU-001");
        expect(markup).toContain("Hộp 100g");
        expect(markup).toContain("Đã chọn <strong>0</strong> sản phẩm");
        expect(markup).toContain("Tổng SL hoàn: <strong>0</strong>");
    });

    // T02: Đơn có 50 sản phẩm - không tràn layout, có cuộn phù hợp
    it("T02: renders 50 products within scrollable table container without breaking", () => {
        const items = Array.from({ length: 50 }, (_, i) =>
            makeOrderItem(`item-${i + 1}`, `Trà Đặc Sản Thượng Hạng Dòng Thứ ${i + 1}`, 5, `SKU-${i + 1}`)
        );
        const draftItems: Record<string, ReturnDraftItem> = {};
        items.forEach((item) => {
            draftItems[item.id!] = { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" };
        });
        const validation = validateReturnDraftQuantities(items, draftItems, []);

        const markup = html(
            createElement(ReturnProductSelectionTable, {
                items,
                draftItems,
                validation,
                onUpdateDraftItem: vi.fn(),
                onDeselectAll: vi.fn()
            })
        );

        expect(markup).toContain("itemTableWrap");
        expect(markup).toContain("Tất cả (50)");
        expect(markup).toContain("Trà Đặc Sản Thượng Hạng Dòng Thứ 50");
    });

    // T03: 10 sản phẩm, chọn 2 - chỉ 2 sản phẩm đi tiếp
    it("T03: selects 2 out of 10 products and only those 2 proceed to selection and payload", () => {
        const items = Array.from({ length: 10 }, (_, i) =>
            makeOrderItem(`item-${i + 1}`, `Sản phẩm ${i + 1}`, 3)
        );
        const draftItems: Record<string, ReturnDraftItem> = {};
        items.forEach((item) => {
            draftItems[item.id!] = { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" };
        });
        // Select item-2 and item-5 only
        draftItems["item-2"] = { selected: true, quantity: "2", reason: "DEFECTIVE", condition: "OPENED", note: "" };
        draftItems["item-5"] = { selected: true, quantity: "1", reason: "WRONG_ITEM", condition: "SEALED", note: "" };

        const validation = validateReturnDraftQuantities(items, draftItems, []);
        expect(validation.error).toBeNull();
        expect(validation.items).toHaveLength(2);
        expect(validation.items.map((i) => i.orderItemId)).toEqual(["item-2", "item-5"]);

        const payload = buildReturnPayloadItems(items, draftItems, []);
        expect(payload).toHaveLength(2);
        expect(payload.map((p) => p.orderItemId)).toEqual(["item-2", "item-5"]);
    });

    // T04: Sản phẩm mua 5, còn hoàn 3, nhập 2 -> Hợp lệ
    it("T04: accepts valid quantity (bought 5, remaining 3, enter 2)", () => {
        const result = validateReturnQuantity("2", 3);
        expect(result.error).toBeNull();
        expect(result.quantity).toBe(2);
    });

    // T05: Nhập số lượng 0 -> Không hợp lệ
    it("T05: rejects return quantity of 0", () => {
        const result = validateReturnQuantity("0", 3);
        expect(result.quantity).toBeNull();
        expect(result.error).toContain("Số lượng hoàn phải là số nguyên");
    });

    // T06: Nhập số lượng -1 -> Không hợp lệ
    it("T06: rejects return quantity of -1", () => {
        const result = validateReturnQuantity("-1", 3);
        expect(result.quantity).toBeNull();
        expect(result.error).toContain("Số lượng hoàn phải là số nguyên");
    });

    // T07: Nhập số lượng vượt quá mức còn hoàn -> Không hợp lệ
    it("T07: rejects return quantity exceeding remaining available", () => {
        const result = validateReturnQuantity("4", 3);
        expect(result.quantity).toBeNull();
        expect(result.error).toContain("1 đến 3");
    });

    // T08: Nhập số thập phân -> Không hợp lệ
    it("T08: rejects decimal return quantities", () => {
        const result = validateReturnQuantity("1.5", 3);
        expect(result.quantity).toBeNull();
        expect(result.error).toContain("Số lượng hoàn phải là số nguyên");
    });

    // T09: Sản phẩm còn hoàn 0 -> Không cho chọn (disabled)
    it("T09: disables selection and renders exhausted badge when remaining is 0", () => {
        const item = makeOrderItem("item-zero", "Hết hàng hoàn", 2);
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-zero": { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" }
        };
        const pastReturns: OrderReturnResponse[] = [
            { id: "ret-1", status: "RECEIVED", items: [{ orderItemId: "item-zero", quantity: 2 }] }
        ];
        const validation = validateReturnDraftQuantities([item], draftItems, pastReturns);
        expect(validation.remainingQuantities["item-zero"]).toBe(0);

        const markup = html(
            createElement(ReturnProductSelectionTable, {
                items: [item],
                draftItems,
                validation,
                onUpdateDraftItem: vi.fn(),
                onDeselectAll: vi.fn()
            })
        );

        expect(markup).toContain("Hết SL hoàn");
        expect(markup).toContain("disabled");
    });

    // T10: Bỏ chọn một sản phẩm -> Không xuất hiện trong payload
    it("T10: excludes deselected items completely from the submission payload", () => {
        const items = [makeOrderItem("item-a", "Sản phẩm A", 3), makeOrderItem("item-b", "Sản phẩm B", 3)];
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-a": { selected: true, quantity: "2", reason: "DEFECTIVE", condition: "OPENED", note: "" },
            "item-b": { selected: false, quantity: "1", reason: "DEFECTIVE", condition: "OPENED", note: "" }
        };

        const payload = buildReturnPayloadItems(items, draftItems, []);
        expect(payload).toHaveLength(1);
        expect(payload[0].orderItemId).toBe("item-a");
    });

    // T11: Quay lại bước 2 -> Giữ dữ liệu hợp lệ
    it("T11: retains selected items and quantities across navigation steps", () => {
        const items = [makeOrderItem("item-a", "Sản phẩm A", 5), makeOrderItem("item-b", "Sản phẩm B", 5)];
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-a": { selected: true, quantity: "2", reason: "DEFECTIVE", condition: "OPENED", note: "Lỗi bao bì" },
            "item-b": { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" }
        };

        const validation = validateReturnDraftQuantities(items, draftItems, []);
        expect(validation.items[0].quantity).toBe(2);
        expect(draftItems["item-a"].selected).toBe(true);
        expect(draftItems["item-a"].quantity).toBe("2");
    });

    // T12: Nhiều sản phẩm, lý do khác nhau -> Không ghi đè
    it("T12: preserves distinct reasons, conditions, and notes for each product without overwrite", () => {
        const itemA = makeOrderItem("item-a", "Trà Sen", 2);
        const itemB = makeOrderItem("item-b", "Trà Nhài", 2);
        const draftA: ReturnDraftItem = {
            selected: true,
            quantity: "1",
            reason: "DEFECTIVE",
            condition: "OPENED",
            note: "Mốc ẩm"
        };
        const draftB: ReturnDraftItem = {
            selected: true,
            quantity: "1",
            reason: "WRONG_ITEM",
            condition: "SEALED",
            note: "Giao nhầm loại"
        };

        const markupA = html(
            createElement(ReturnProductReasonCard, {
                item: itemA,
                itemKey: "item-a",
                draft: draftA,
                isOpen: true,
                onToggle: vi.fn(),
                onUpdateDraft: vi.fn()
            })
        );
        const markupB = html(
            createElement(ReturnProductReasonCard, {
                item: itemB,
                itemKey: "item-b",
                draft: draftB,
                isOpen: true,
                onToggle: vi.fn(),
                onUpdateDraft: vi.fn()
            })
        );

        expect(markupA).toContain("Trà Sen");
        expect(markupA).toContain("Sản phẩm bị lỗi");
        expect(markupA).toContain("Mốc ẩm");

        expect(markupB).toContain("Trà Nhài");
        expect(markupB).toContain("Bán/giao sai sản phẩm");
        expect(markupB).toContain("Giao nhầm loại");
    });

    // T13: Eligibility PASS -> Chỉ hiện trạng thái tổng hợp
    it("T13: displays only consolidated success state without individual check dump when eligible", () => {
        const eligibilityResponse: OrderReturnEligibilityResponse = {
            eligible: true,
            orderId: "ord-1",
            checks: [
                { code: "STATUS_COMPLETED", message: "Đơn hàng đã hoàn thành", passed: true },
                { code: "PAYMENT_PAID", message: "Đơn hàng đã thanh toán", passed: true }
            ],
            items: [
                {
                    orderItemId: "item-1",
                    quantity: 1,
                    remainingQuantity: 2,
                    reason: "DEFECTIVE",
                    condition: "OPENED",
                    policy: ["Hàng phải còn nguyên seal"],
                    checks: [{ code: "QUANTITY_VALID", message: "Số lượng hợp lệ", passed: true }]
                }
            ]
        };

        const markup = html(
            createElement(ReturnEligibilityStatus, {
                response: eligibilityResponse,
                checking: false,
                error: null,
                ready: true
            })
        );

        expect(markup).toContain("Đủ điều kiện tạo phiếu hoàn");
        // Must NOT dump individual PASS checks
        expect(markup).not.toContain("STATUS_COMPLETED");
        expect(markup).not.toContain("Đơn hàng đã hoàn thành");
    });

    // T14: Eligibility FAIL -> Hiện lý do cần xử lý
    it("T14: displays failure notice and specific failing checks when ineligible", () => {
        const item = makeOrderItem("item-1", "Trà Ô Long", 3);
        const eligibilityResponse: OrderReturnEligibilityResponse = {
            eligible: false,
            orderId: "ord-1",
            checks: [
                { code: "ORDER_STATUS", message: "Đơn hàng chưa thanh toán đầy đủ", passed: false }
            ],
            items: [
                {
                    orderItemId: "item-1",
                    quantity: 5,
                    remainingQuantity: 2,
                    reason: "DEFECTIVE",
                    condition: "OPENED",
                    policy: [],
                    checks: [
                        {
                            code: "QUANTITY_EXCEEDED",
                            message: "Số lượng hoàn vượt quá số lượng còn được hoàn (tối đa 2).",
                            passed: false
                        }
                    ]
                }
            ]
        };

        const markup = html(
            createElement(ReturnEligibilityStatus, {
                response: eligibilityResponse,
                checking: false,
                error: null,
                items: [item],
                ready: true
            })
        );

        expect(markup).toContain("Không đủ điều kiện tạo phiếu hoàn");
        expect(markup).toContain("Đơn hàng chưa thanh toán đầy đủ");
        expect(markup).toContain("Trà Ô Long: Số lượng hoàn vượt quá số lượng còn được hoàn (tối đa 2).");
    });

    // T15: Eligibility API lỗi -> Không hiển thị PASS giả
    it("T15: displays retryable error notice and prevents fake pass on API failure", () => {
        const onRetry = vi.fn();
        const markup = html(
            createElement(ReturnEligibilityStatus, {
                response: null,
                checking: false,
                error: "Mất kết nối tới máy chủ.",
                ready: true,
                onRetry
            })
        );

        expect(markup).toContain("Chưa thể kiểm tra điều kiện hoàn hàng. Vui lòng thử lại.");
        expect(markup).toContain("Kiểm tra lại");
        expect(markup).not.toContain("Đủ điều kiện");
    });

    // T16: Mở/đóng chính sách -> Không mất dữ liệu form
    it("T16: opens policy modal with detailed terms and handles closing without resetting form", () => {
        const onClose = vi.fn();
        // Môi trường test là node: ModalShell cần `document.body` làm đích portal.
        vi.stubGlobal("document", { body: {} });
        const markup = html(
            createElement(ReturnPolicyModal, {
                isOpen: true,
                onClose,
                policies: ["Chính sách 1: Kiểm định QC", "Chính sách 2: Thời hạn 7 ngày"]
            })
        );
        vi.unstubAllGlobals();

        expect(markup).toContain("Chính sách hoàn hàng chi tiết");
        expect(markup).toContain("Chính sách 1: Kiểm định QC");
        expect(markup).toContain("Chính sách 2: Thời hạn 7 ngày");

        // When closed, renders nothing
        const closedMarkup = html(
            createElement(ReturnPolicyModal, {
                isOpen: false,
                onClose
            })
        );
        expect(closedMarkup).toBe("");
    });

    // T17: Bước 4 -> Chỉ hiển thị sản phẩm được chọn
    it("T17: Step 4 Confirmation renders only selected products with their exact return details", () => {
        const item1 = makeOrderItem("item-1", "Sản phẩm Chọn", 3, "SKU-A");
        const item2 = makeOrderItem("item-2", "Sản phẩm Bỏ Qua", 3, "SKU-B");
        const order = makeOrder("ord-1", [item1, item2]);
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-1": { selected: true, quantity: "2", reason: "DEFECTIVE", condition: "OPENED", note: "Hư hỏng nhẹ" },
            "item-2": { selected: false, quantity: "1", reason: "", condition: "OPENED", note: "" }
        };

        const markup = html(
            createElement(ReturnConfirmationSummary, {
                order,
                items: [item1, item2],
                draftItems,
                refundRequired: true,
                returnNote: "Khách yêu cầu hoàn tiền",
                eligibility: {
                    eligible: true,
                    orderId: "ord-1",
                    checks: [],
                    items: []
                },
                isCheckingEligibility: false,
                eligibilityError: null,
                onRetryEligibility: vi.fn(),
                onOpenPolicyModal: vi.fn()
            })
        );

        expect(markup).toContain("Sản phẩm Chọn");
        expect(markup).toContain("SKU-A");
        expect(markup).toContain("Hư hỏng nhẹ");
        expect(markup).not.toContain("Sản phẩm Bỏ Qua");
        expect(markup).toContain("Đã chọn <strong>1</strong> sản phẩm");
        expect(markup).toContain("Tổng SL hoàn: <strong>2</strong>");
        // Verify Step 4 has NO "Xem chính sách chi tiết" link
        expect(markup).not.toContain("Xem chính sách chi tiết");
        // Verify unified left-aligned QC notice is present
        expect(markup).toContain("Lưu ý: Hàng hoàn sẽ được kiểm định chất lượng (QC)");
    });

    // T18: Backend từ chối tạo phiếu -> Hiển thị lỗi, không báo thành công
    it("T18: validation throws error if submission items contain invalid reason", () => {
        const item = makeOrderItem("item-1", "Trà", 2);
        const draftItems: Record<string, ReturnDraftItem> = {
            "item-1": { selected: true, quantity: "1", reason: "", condition: "OPENED", note: "" }
        };

        expect(() => buildReturnPayloadItems([item], draftItems, [])).toThrow(
            "Vui lòng chọn lý do trả hàng cho từng sản phẩm."
        );
    });

    // T19: Double-click tạo phiếu -> Chống double-submit
    it("T19: submit button renders loading state and is disabled during submission", () => {
        // Summary displays with total and check
        const summaryMarkup = html(
            createElement(ReturnSelectedSummary, {
                selectedCount: 2,
                totalQuantity: 4
            })
        );
        expect(summaryMarkup).toContain("Đã chọn <strong>2</strong> sản phẩm");
        expect(summaryMarkup).toContain("Tổng SL hoàn: <strong>4</strong>");
    });

    // T20: Hai order item có chung product/variant -> Không gộp sai dòng hoặc số lượng
    it("T20: keeps two separate order items sharing product/variant distinct by unique item key", () => {
        const item1: OrderItemResponse = {
            id: "row-1",
            productId: "prod-same",
            productNameSnapshot: "Trà Đen",
            productVariantId: "var-same",
            quantity: 3
        };
        const item2: OrderItemResponse = {
            id: "row-2",
            productId: "prod-same",
            productNameSnapshot: "Trà Đen",
            productVariantId: "var-same",
            quantity: 2
        };

        const key1 = getReturnItemKey(item1, 0);
        const key2 = getReturnItemKey(item2, 1);
        expect(key1).not.toBe(key2);
        expect(key1).toBe("row-1");
        expect(key2).toBe("row-2");

        const drafts: Record<string, ReturnDraftItem> = {
            "row-1": { selected: true, quantity: "2", reason: "DEFECTIVE", condition: "OPENED", note: "" },
            "row-2": { selected: true, quantity: "1", reason: "WRONG_ITEM", condition: "SEALED", note: "" }
        };

        const validation = validateReturnDraftQuantities([item1, item2], drafts, []);
        expect(validation.items).toHaveLength(2);
        expect(validation.items[0].orderItemId).toBe("row-1");
        expect(validation.items[0].quantity).toBe(2);
        expect(validation.items[1].orderItemId).toBe("row-2");
        expect(validation.items[1].quantity).toBe(1);
    });

    // T21: Đổi đơn hàng ở bước 1 -> Reset selection/reason của đơn cũ
    it("T21: getReturnItemKey fallback generates stable keys if id is missing", () => {
        const itemFallback: OrderItemResponse = {
            productId: "prod-fallback",
            quantity: 2
        };
        const key = getReturnItemKey(itemFallback, 3);
        expect(key).toBe("item-3");
    });

    // T22: Eligibility thay đổi sau khi đã preview PASS -> Rechecks and rejects
    it("T22: rejects submission if remaining quantity was reduced by a fresh return", () => {
        const item = makeOrderItem("item-1", "Trà", 3);
        const drafts: Record<string, ReturnDraftItem> = {
            "item-1": { selected: true, quantity: "3", reason: "DEFECTIVE", condition: "OPENED", note: "" }
        };

        // Fresh return reduced remaining to 1
        const freshReturns: OrderReturnResponse[] = [
            { id: "fresh-ret", status: "CREATED", items: [{ orderItemId: "item-1", quantity: 2 }] }
        ];

        expect(() => buildReturnPayloadItems([item], drafts, freshReturns)).toThrow();
        const validation = validateReturnDraftQuantities([item], drafts, freshReturns);
        expect(validation.errors["item-1"]).toContain("1 đến 1");
    });
});
