import type { PosOrderDto } from "../services/posOrder.api";
import type {
    SaleCartItem,
    SaleCustomer,
    SaleManualDiscount,
    SaleOrderType,
    SalePaymentMethod
} from "../types/sale.types";

/**
 * Ảnh chụp đơn đang bán, lưu trong localStorage để không mất giỏ hàng khi
 * mất mạng, lỡ tay reload hoặc trình duyệt bị tắt.
 *
 * Giỏ hàng thật nằm ở đơn nháp trên máy chủ (draftOrder.id). Backend chưa
 * có API lấy lại đơn nháp theo id, nên ta lưu cả bản PosOrderDto cuối cùng
 * nhận được để dựng lại giỏ ngay cả khi đang offline. Khi có mạng, lần gọi
 * thêm/sửa/thanh toán kế tiếp vẫn đi đúng vào đơn nháp cũ nhờ draftOrder.id.
 */
export interface PersistedSaleSnapshot {
    version: 1;
    savedAt: number;
    userId: string | null;
    draftOrder: PosOrderDto | null;
    cartItemsById: Record<string, SaleCartItem>;
    note: string;
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    cashReceived: number;
    selectedDiscountId: string;
    manualOrderDiscount: SaleManualDiscount | null;
    selectedCustomer: SaleCustomer | null;
}

export type PersistedSaleInput = Omit<
    PersistedSaleSnapshot,
    "version" | "savedAt"
>;

export const SALE_STORAGE_KEY = "hvt.pos.current-sale.v1";

/** Đơn nháp để quá lâu coi như bỏ: không khôi phục đơn của ca hôm trước. */
export const SALE_SNAPSHOT_TTL_MS = 12 * 60 * 60 * 1000;

interface StorageLike {
    getItem(key: string): string | null;
    setItem(key: string, value: string): void;
    removeItem(key: string): void;
}

function getStorage(): StorageLike | null {
    try {
        if (typeof window === "undefined" || !window.localStorage) {
            return null;
        }

        return window.localStorage;
    } catch {
        return null;
    }
}

export function isSaleSnapshotEmpty(input: PersistedSaleInput): boolean {
    return (
        input.draftOrder === null &&
        Object.keys(input.cartItemsById).length === 0
    );
}

export function loadPersistedSale(
    userId: string | null,
    now: number = Date.now(),
    storage: StorageLike | null = getStorage()
): PersistedSaleSnapshot | null {
    if (!storage) {
        return null;
    }

    try {
        const raw = storage.getItem(SALE_STORAGE_KEY);

        if (!raw) {
            return null;
        }

        const parsed = JSON.parse(raw) as Partial<PersistedSaleSnapshot>;

        if (
            parsed.version !== 1 ||
            typeof parsed.savedAt !== "number" ||
            !parsed.cartItemsById ||
            typeof parsed.cartItemsById !== "object"
        ) {
            storage.removeItem(SALE_STORAGE_KEY);
            return null;
        }

        // Giỏ của người khác trên cùng máy, hoặc đã quá hạn: bỏ.
        if (
            (parsed.userId ?? null) !== userId ||
            now - parsed.savedAt > SALE_SNAPSHOT_TTL_MS
        ) {
            storage.removeItem(SALE_STORAGE_KEY);
            return null;
        }

        const snapshot = parsed as PersistedSaleSnapshot;

        // Đơn đã thanh toán/huỷ trên máy chủ (reload đúng lúc đang reset): bỏ.
        if (
            snapshot.draftOrder?.status !== undefined &&
            snapshot.draftOrder.status !== "DRAFT"
        ) {
            storage.removeItem(SALE_STORAGE_KEY);
            return null;
        }

        if (isSaleSnapshotEmpty(snapshot)) {
            storage.removeItem(SALE_STORAGE_KEY);
            return null;
        }

        return snapshot;
    } catch {
        try {
            storage.removeItem(SALE_STORAGE_KEY);
        } catch {
            // Storage bị khoá (private mode, hết quota): bỏ qua.
        }
        return null;
    }
}

export function savePersistedSale(
    input: PersistedSaleInput,
    now: number = Date.now(),
    storage: StorageLike | null = getStorage()
): void {
    if (!storage) {
        return;
    }

    try {
        if (isSaleSnapshotEmpty(input)) {
            storage.removeItem(SALE_STORAGE_KEY);
            return;
        }

        const snapshot: PersistedSaleSnapshot = {
            ...input,
            version: 1,
            savedAt: now
        };

        storage.setItem(SALE_STORAGE_KEY, JSON.stringify(snapshot));
    } catch {
        // Hết quota hoặc storage bị chặn: không làm hỏng luồng bán hàng.
    }
}

export function clearPersistedSale(
    storage: StorageLike | null = getStorage()
): void {
    try {
        storage?.removeItem(SALE_STORAGE_KEY);
    } catch {
        // Bỏ qua.
    }
}
