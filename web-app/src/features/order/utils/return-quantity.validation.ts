import type { OrderItemResponse } from "../types/order.types";
import type {
    CreateOrderReturnPayload,
    OrderReturnItemEligibility,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason
} from "../types/order-return.types";
import { calculateAvailableReturnQuantity, formatReturnQuantity } from "./order-return.utils";

export interface ReturnDraftItem {
    selected: boolean;
    quantity: string;
    reason: ReturnReason | "";
    condition: ReturnCondition;
    note: string;
}

type RemainingQuantity = Pick<OrderReturnItemEligibility, "orderItemId" | "remainingQuantity">;

export function getReturnItemKey(item: OrderItemResponse, index: number) {
    return item.id ?? item.productVariantId ?? `item-${index}`;
}

export function validateReturnQuantity(value: string, remainingQuantity: number):
    { quantity: number; error: null } | { quantity: null; error: string } {
    const quantity = Number(value);
    if (!/^\d+$/.test(value.trim()) || !Number.isSafeInteger(quantity)
        || !Number.isFinite(remainingQuantity) || quantity < 1 || quantity > remainingQuantity) {
        return {
            quantity: null,
            error: remainingQuantity < 1
                ? "Sản phẩm không còn số lượng có thể hoàn."
                : `Số lượng hoàn phải là số nguyên từ 1 đến ${formatReturnQuantity(remainingQuantity)}.`
        };
    }
    return { quantity, error: null };
}

export function validateReturnDraftQuantities(
    items: OrderItemResponse[],
    drafts: Record<string, ReturnDraftItem>,
    returns: OrderReturnResponse[],
    latestRemaining: RemainingQuantity[] = []
) {
    const errors: Record<string, string> = {};
    const remainingQuantities: Record<string, number> = {};
    const selected: Array<{ orderItemId: string; quantity: number; draft: ReturnDraftItem }> = [];
    let selectedCount = 0;

    items.forEach((item, index) => {
        const key = getReturnItemKey(item, index);
        const draft = drafts[key];
        const available = calculateAvailableReturnQuantity(item, returns);
        const latest = latestRemaining.find(entry => entry.orderItemId === item.id);
        const remaining = latest ? Math.min(available, latest.remainingQuantity) : available;
        remainingQuantities[key] = remaining;
        if (!draft?.selected) return;
        selectedCount += 1;
        const result = validateReturnQuantity(draft.quantity, remaining);
        if (result.error !== null) {
            errors[key] = result.error;
        } else if (!item.id) {
            errors[key] = "Không xác định được sản phẩm cần hoàn. Vui lòng chọn lại đơn hàng.";
        } else {
            selected.push({ orderItemId: item.id, quantity: result.quantity, draft });
        }
    });

    const error = Object.values(errors)[0]
        ?? (selectedCount === 0 ? "Vui lòng chọn ít nhất một sản phẩm cần hoàn." : null);
    // Never build even a partial request when one selected row is invalid.
    return { error, errors, remainingQuantities, items: error ? [] : selected };
}

export function buildReturnPayloadItems(
    items: OrderItemResponse[],
    drafts: Record<string, ReturnDraftItem>,
    returns: OrderReturnResponse[],
    latestRemaining: RemainingQuantity[] = []
): CreateOrderReturnPayload["items"] {
    const validation = validateReturnDraftQuantities(items, drafts, returns, latestRemaining);
    if (validation.error) throw new Error(validation.error);
    return validation.items.map(({ orderItemId, quantity, draft }) => {
        if (!draft.reason) throw new Error("Vui lòng chọn lý do trả hàng cho từng sản phẩm.");
        return {
            orderItemId,
            quantity,
            reason: draft.reason,
            condition: draft.condition,
            note: draft.note.trim() || null
        };
    });
}
