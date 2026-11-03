import { describe, expect, it } from "vitest";
import type { OrderItemResponse } from "../types/order.types";
import type { OrderReturnResponse } from "../types/order-return.types";
import {
    buildReturnPayloadItems,
    validateReturnDraftQuantities,
    validateReturnQuantity,
    type ReturnDraftItem
} from "./return-quantity.validation";

const items: OrderItemResponse[] = [{ id: "item-a", quantity: 3 }, { id: "item-b", quantity: 3 }];
function draft(quantity: string, selected = true): ReturnDraftItem {
    return { selected, quantity, reason: "OTHER", condition: "SEALED", note: "Kiểm thử" };
}

describe("return quantity validation and payload guard", () => {
    it.each(["-1", "0", "4", "1.5", "", "   ", "abc", "NaN", "Infinity", "1e0", "0x1"])(
        "blocks %j at the selection gate and refuses to build submit items", quantity => {
            const drafts = { "item-a": draft(quantity) };
            expect(validateReturnQuantity(quantity, 3).error).not.toBeNull();
            const validation = validateReturnDraftQuantities(items, drafts, []);
            expect(validation.error).not.toBeNull();
            expect(validation.errors["item-a"]).toBeTruthy();
            expect(validation.items).toEqual([]);
            expect(() => buildReturnPayloadItems(items, drafts, [])).toThrow();
            expect(drafts["item-a"].quantity).toBe(quantity);
        }
    );

    it.each(["1", "3"])("accepts %j and builds a numeric quantity", quantity => {
        const drafts = { "item-a": draft(quantity) };
        expect(validateReturnDraftQuantities(items, drafts, []).error).toBeNull();
        expect(buildReturnPayloadItems(items, drafts, [])).toEqual([{
            orderItemId: "item-a", quantity: Number(quantity), reason: "OTHER", condition: "SEALED", note: "Kiểm thử"
        }]);
    });

    it("blocks the whole selection when only one of multiple selected rows is invalid", () => {
        const drafts = { "item-a": draft("1"), "item-b": draft("-1") };
        expect(validateReturnDraftQuantities(items, drafts, []).items).toEqual([]);
        expect(() => buildReturnPayloadItems(items, drafts, [])).toThrow();
    });

    it("ignores an unselected invalid row, then removes the error when corrected", () => {
        const drafts = { "item-a": draft("1"), "item-b": draft("-1", false) };
        expect(validateReturnDraftQuantities(items, drafts, []).error).toBeNull();
        expect(buildReturnPayloadItems(items, drafts, [])).toHaveLength(1);
        drafts["item-b"] = draft("3");
        expect(validateReturnDraftQuantities(items, drafts, []).errors).toEqual({});
        expect(buildReturnPayloadItems(items, drafts, [])).toHaveLength(2);
    });

    it("blocks an empty selection even if unselected quantities are valid", () => {
        const drafts = { "item-a": draft("1", false) };
        expect(validateReturnDraftQuantities(items, drafts, []).error).toBeTruthy();
        expect(() => buildReturnPayloadItems(items, drafts, [])).toThrow();
    });

    it("rechecks remaining against newly fetched returns without changing the entered value", () => {
        const drafts = { "item-a": draft("3") };
        const freshReturns: OrderReturnResponse[] = [{
            id: "new-return", status: "CREATED", items: [{ orderItemId: "item-a", quantity: 1 }]
        }];
        expect(buildReturnPayloadItems(items, drafts, [])[0].quantity).toBe(3);
        const validation = validateReturnDraftQuantities(items, drafts, freshReturns);
        expect(validation.remainingQuantities["item-a"]).toBe(2);
        expect(validation.errors["item-a"]).toContain("1 đến 2");
        expect(() => buildReturnPayloadItems(items, drafts, freshReturns)).toThrow();
        expect(drafts["item-a"].quantity).toBe("3");
    });

    it("rechecks a changed purchased quantity from fresh Order detail", () => {
        expect(() => buildReturnPayloadItems([{ id: "item-a", quantity: 2 }], { "item-a": draft("3") }, [])).toThrow();
    });

    it("honors lower remaining returned by the final eligibility check", () => {
        const drafts = { "item-a": draft("3") };
        const latest = [{ orderItemId: "item-a", remainingQuantity: 1 }];
        expect(validateReturnDraftQuantities(items, drafts, [], latest).errors["item-a"]).toBeTruthy();
        expect(() => buildReturnPayloadItems(items, drafts, [], latest)).toThrow();
        drafts["item-a"] = draft("1");
        expect(buildReturnPayloadItems(items, drafts, [], latest)[0].quantity).toBe(1);
    });

    it("blocks a selected row whose remaining is now zero, and allows deselecting it", () => {
        const drafts = { "item-a": draft("1"), "item-b": draft("1") };
        const latest = [{ orderItemId: "item-b", remainingQuantity: 0 }];
        expect(validateReturnDraftQuantities(items, drafts, [], latest).errors["item-b"]).toBeTruthy();
        drafts["item-b"].selected = false;
        expect(buildReturnPayloadItems(items, drafts, [], latest).map(item => item.orderItemId)).toEqual(["item-a"]);
    });

    it("does not let a stale larger eligibility limit override fresh remaining", () => {
        const freshReturns: OrderReturnResponse[] = [{
            id: "new-return", status: "RECEIVED", items: [{ orderItemId: "item-a", quantity: 2 }]
        }];
        expect(() => buildReturnPayloadItems(items, { "item-a": draft("3") }, freshReturns,
            [{ orderItemId: "item-a", remainingQuantity: 3 }])).toThrow();
    });
});
