import {
    SALE_SNAPSHOT_TTL_MS,
    isSaleSnapshotEmpty,
    type PersistedSaleInput
} from "./sale-persistence";

/**
 * Nhiều hoá đơn bán song song trên POS. Hoá đơn đang mở dùng state của PosSalePage (và snapshot ở
 * sale-persistence); các hoá đơn còn lại được "gác" kèm ảnh chụp đơn để chuyển qua lại và không mất khi reload.
 */
export interface InvoiceTab {
    id: string;
    /** Số hiển thị "Hóa đơn N". */
    label: number;
    /** Ảnh chụp đơn của hoá đơn đang gác; null với hoá đơn đang mở. */
    sale: PersistedSaleInput | null;
}

export interface InvoiceTabsState {
    activeId: string;
    tabs: InvoiceTab[];
}

interface StoredInvoiceTabs extends InvoiceTabsState {
    version: 1;
    savedAt: number;
    userId: string | null;
}

interface StorageLike {
    getItem(key: string): string | null;
    setItem(key: string, value: string): void;
    removeItem(key: string): void;
}

export const INVOICE_TABS_STORAGE_KEY = "hvt.pos.invoice-tabs.v1";
/** Giới hạn để thanh hoá đơn không dài vô tận và localStorage không phình. */
export const MAX_INVOICE_TABS = 10;

function getStorage(): StorageLike | null {
    try {
        return typeof window === "undefined" ? null : window.localStorage;
    } catch {
        return null;
    }
}

export function newInvoiceTabId() {
    return typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `invoice-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function createInitialInvoiceTabs(): InvoiceTabsState {
    const id = newInvoiceTabId();
    return { activeId: id, tabs: [{ id, label: 1, sale: null }] };
}

/** Số nhỏ nhất chưa dùng, để đóng "Hóa đơn 2" rồi tạo mới thì lấy lại số 2. */
export function nextInvoiceLabel(tabs: readonly InvoiceTab[]) {
    const used = new Set(tabs.map((tab) => tab.label));
    let label = 1;

    while (used.has(label)) {
        label += 1;
    }

    return label;
}

export function loadInvoiceTabs(
    userId: string | null,
    now: number = Date.now(),
    storage: StorageLike | null = getStorage()
): InvoiceTabsState {
    if (!storage) {
        return createInitialInvoiceTabs();
    }

    try {
        const raw = storage.getItem(INVOICE_TABS_STORAGE_KEY);
        const parsed = raw ? (JSON.parse(raw) as Partial<StoredInvoiceTabs>) : null;

        if (
            !parsed ||
            parsed.version !== 1 ||
            typeof parsed.savedAt !== "number" ||
            (parsed.userId ?? null) !== userId ||
            now - parsed.savedAt > SALE_SNAPSHOT_TTL_MS ||
            !Array.isArray(parsed.tabs) ||
            typeof parsed.activeId !== "string"
        ) {
            storage.removeItem(INVOICE_TABS_STORAGE_KEY);
            return createInitialInvoiceTabs();
        }

        // Hoá đơn gác đã thanh toán/huỷ ở nơi khác hoặc rỗng thì bỏ; luôn giữ hoá đơn đang mở.
        const tabs = parsed.tabs.filter(
            (tab) =>
                tab.id === parsed.activeId ||
                (tab.sale !== null &&
                    !isSaleSnapshotEmpty(tab.sale) &&
                    (tab.sale.draftOrder?.status === undefined || tab.sale.draftOrder.status === "DRAFT"))
        );
        const active = tabs.find((tab) => tab.id === parsed.activeId);

        if (!active) {
            return createInitialInvoiceTabs();
        }

        return {
            activeId: active.id,
            tabs: tabs.map((tab) => (tab.id === active.id ? { ...tab, sale: null } : tab))
        };
    } catch {
        return createInitialInvoiceTabs();
    }
}

export function saveInvoiceTabs(
    userId: string | null,
    state: InvoiceTabsState,
    now: number = Date.now(),
    storage: StorageLike | null = getStorage()
): void {
    if (!storage) {
        return;
    }

    try {
        const stored: StoredInvoiceTabs = { ...state, version: 1, savedAt: now, userId };
        storage.setItem(INVOICE_TABS_STORAGE_KEY, JSON.stringify(stored));
    } catch {
        // Hết quota hoặc storage bị chặn: vẫn bán được, chỉ không giữ hoá đơn gác qua reload.
    }
}
