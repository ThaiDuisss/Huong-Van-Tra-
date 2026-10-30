import { describe, expect, it } from "vitest";

import {
    INVOICE_TABS_STORAGE_KEY,
    loadInvoiceTabs,
    nextInvoiceLabel,
    saveInvoiceTabs,
    type InvoiceTabsState
} from "./invoice-tabs";
import { SALE_SNAPSHOT_TTL_MS, type PersistedSaleInput } from "./sale-persistence";

function createMemoryStorage() {
    const map = new Map<string, string>();

    return {
        getItem: (key: string) => map.get(key) ?? null,
        setItem: (key: string, value: string) => {
            map.set(key, value);
        },
        removeItem: (key: string) => {
            map.delete(key);
        }
    };
}

function sale(status: string, withItem = true): PersistedSaleInput {
    return {
        userId: "cashier-1",
        draftOrder: { id: `order-${status}`, orderCode: "POS-1", status, items: [] } as PersistedSaleInput["draftOrder"],
        cartItemsById: withItem
            ? {
                  a: { productId: "a", productVariantId: "v1", name: "Trà sen", sku: "TS", variant: "", quantity: 1, unitPrice: 1 }
              }
            : {},
        note: "",
        orderType: "QUICK_SALE",
        paymentMethod: "CASH",
        cashReceived: 0,
        selectedDiscountId: "none",
        manualOrderDiscount: null,
        selectedCustomer: null
    };
}

const state: InvoiceTabsState = {
    activeId: "t1",
    tabs: [
        { id: "t1", label: 1, sale: null },
        { id: "t2", label: 2, sale: sale("DRAFT") },
        { id: "t3", label: 3, sale: sale("PAID") }
    ]
};

describe("invoice tabs persistence", () => {
    it("restores the active tab and parked drafts, dropping parked orders that are no longer drafts", () => {
        const storage = createMemoryStorage();
        saveInvoiceTabs("cashier-1", state, 1_000, storage);

        const loaded = loadInvoiceTabs("cashier-1", 2_000, storage);

        expect(loaded.activeId).toBe("t1");
        expect(loaded.tabs.map((tab) => tab.id)).toEqual(["t1", "t2"]);
    });

    it("starts fresh for another cashier or an expired snapshot", () => {
        const storage = createMemoryStorage();
        saveInvoiceTabs("cashier-1", state, 1_000, storage);

        expect(loadInvoiceTabs("cashier-2", 2_000, storage).tabs).toHaveLength(1);
        saveInvoiceTabs("cashier-1", state, 1_000, storage);
        expect(loadInvoiceTabs("cashier-1", 1_000 + SALE_SNAPSHOT_TTL_MS + 1, storage).tabs).toHaveLength(1);
        expect(storage.getItem(INVOICE_TABS_STORAGE_KEY)).toBeNull();
    });

    it("reuses the smallest free label", () => {
        expect(nextInvoiceLabel([{ id: "a", label: 1, sale: null }, { id: "c", label: 3, sale: null }])).toBe(2);
    });
});
