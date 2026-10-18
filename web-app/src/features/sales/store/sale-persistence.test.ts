import { describe, expect, it } from "vitest";

import {
    SALE_SNAPSHOT_TTL_MS,
    SALE_STORAGE_KEY,
    loadPersistedSale,
    savePersistedSale,
    type PersistedSaleInput
} from "./sale-persistence";

function createMemoryStorage() {
    const map = new Map<string, string>();

    return {
        getItem: (key: string) => map.get(key) ?? null,
        setItem: (key: string, value: string) => {
            map.set(key, value);
        },
        removeItem: (key: string) => {
            map.delete(key);
        },
        size: () => map.size
    };
}

const baseInput: PersistedSaleInput = {
    userId: "cashier-1",
    draftOrder: {
        id: "order-1",
        orderCode: "POS-001",
        status: "DRAFT",
        items: []
    } as PersistedSaleInput["draftOrder"],
    cartItemsById: {
        "item-1": {
            itemId: "item-1",
            productId: "item-1",
            productVariantId: "variant-1",
            name: "Trà sen",
            sku: "TS-01",
            variant: "100g",
            quantity: 2,
            unitPrice: 50_000
        }
    },
    note: "khách quen",
    orderType: "QUICK_SALE",
    paymentMethod: "CASH",
    cashReceived: 100_000,
    selectedDiscountId: "none",
    manualOrderDiscount: null,
    selectedCustomer: null
};

describe("sale persistence", () => {
    it("lưu rồi đọc lại đúng giỏ của cùng thu ngân", () => {
        const storage = createMemoryStorage();
        const now = 1_000_000;

        savePersistedSale(baseInput, now, storage);
        const restored = loadPersistedSale("cashier-1", now + 1_000, storage);

        expect(restored).not.toBeNull();
        expect(restored?.draftOrder?.id).toBe("order-1");
        expect(restored?.cartItemsById["item-1"]?.quantity).toBe(2);
        expect(restored?.cashReceived).toBe(100_000);
        expect(restored?.note).toBe("khách quen");
    });

    it("giỏ rỗng thì xoá khỏi storage thay vì lưu", () => {
        const storage = createMemoryStorage();

        savePersistedSale(baseInput, 1, storage);
        savePersistedSale(
            { ...baseInput, draftOrder: null, cartItemsById: {} },
            2,
            storage
        );

        expect(storage.getItem(SALE_STORAGE_KEY)).toBeNull();
    });

    it("không trả giỏ của người khác đăng nhập trên cùng máy", () => {
        const storage = createMemoryStorage();

        savePersistedSale(baseInput, 1, storage);

        expect(loadPersistedSale("cashier-2", 2, storage)).toBeNull();
        expect(storage.getItem(SALE_STORAGE_KEY)).toBeNull();
    });

    it("bỏ snapshot quá hạn 12 giờ", () => {
        const storage = createMemoryStorage();
        const savedAt = 10_000;

        savePersistedSale(baseInput, savedAt, storage);

        expect(
            loadPersistedSale(
                "cashier-1",
                savedAt + SALE_SNAPSHOT_TTL_MS - 1,
                storage
            )
        ).not.toBeNull();
        expect(
            loadPersistedSale(
                "cashier-1",
                savedAt + SALE_SNAPSHOT_TTL_MS + 1,
                storage
            )
        ).toBeNull();
    });

    it("không khôi phục đơn nháp đã rời trạng thái DRAFT", () => {
        const storage = createMemoryStorage();

        savePersistedSale(
            {
                ...baseInput,
                draftOrder: {
                    ...baseInput.draftOrder!,
                    status: "PAID"
                }
            },
            1,
            storage
        );

        expect(loadPersistedSale("cashier-1", 2, storage)).toBeNull();
    });

    it("dữ liệu hỏng trong storage không làm crash và được dọn", () => {
        const storage = createMemoryStorage();
        storage.setItem(SALE_STORAGE_KEY, "{not json");

        expect(loadPersistedSale("cashier-1", 1, storage)).toBeNull();
        expect(storage.getItem(SALE_STORAGE_KEY)).toBeNull();

        storage.setItem(SALE_STORAGE_KEY, JSON.stringify({ version: 99 }));
        expect(loadPersistedSale("cashier-1", 1, storage)).toBeNull();
    });

    it("storage bị chặn (null) thì không ném lỗi", () => {
        expect(() => savePersistedSale(baseInput, 1, null)).not.toThrow();
        expect(loadPersistedSale("cashier-1", 1, null)).toBeNull();
    });
});
