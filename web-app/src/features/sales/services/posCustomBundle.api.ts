import { apiClient } from "../../../api/api-client";
import type { BackorderPage, FulfillBackorderResponse } from "./posBackorder.api";
import type { PosOrderDto } from "./posOrder.api";

/** Nguyên liệu bán lẻ cho trà đóng theo yêu cầu (Catalog bundle-lookup). */
export interface BundleIngredientOption {
    id: string;
    ingredientCode: string;
    name: string;
    imageUrl?: string | null;
    unitSymbol?: string | null;
    retailPricePerUnit: number;
}

export interface BundleLineInput {
    ingredientId: string;
    ingredientName?: string;
    quantity: number;
}

export interface BundleMaterialAvailability {
    ingredientId: string;
    ingredientCode?: string | null;
    ingredientName?: string | null;
    requiredQuantity: number;
    availableQuantity: number;
    missingQuantity: number;
    expectedArrivalAt?: string | null;
    arrivalSource?: string | null;
}

export interface BundleAvailability {
    enough: boolean;
    estimatedAvailableAt?: string | null;
    estimatedWaitDays?: number | null;
    lines: BundleMaterialAvailability[];
}

export type BundlePackingStatus = "WAITING_MATERIAL" | "AWAITING_PACKING" | "PACKED" | "CANCELLED";

interface IngredientPage {
    content: BundleIngredientOption[];
}

const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function optionalUuid(value?: string | null) {
    return value && uuidPattern.test(value) ? value : undefined;
}

/** Giới hạn mặc định của backend (order.custom-bundle.*), dùng để báo lỗi sớm trên POS. */
export const bundleLimits = {
    minTotalQuantity: 50,
    maxTotalQuantity: 1000,
    minLineQuantity: 5,
    maxIngredients: 10
};

export const posCustomBundleApi = {
    searchIngredients(search: string) {
        return apiClient
            .get<IngredientPage>("/catalog/ingredients/bundle-lookup", {
                query: { search: search.trim() || undefined, size: 50 }
            })
            .then((page) => page.content ?? []);
    },

    /** Xem trước tồn nguyên liệu; quyết định chờ hàng vẫn tính ở máy chủ khi thanh toán. */
    checkAvailability(input: { branchId?: string | null; lines: BundleLineInput[] }) {
        return apiClient.post<BundleAvailability, { branchId?: string; lines: BundleLineInput[] }>(
            "/inventory/custom-bundles/availability",
            { branchId: optionalUuid(input.branchId), lines: input.lines }
        );
    },

    addDraftBundleItem(input: {
        orderId: string | null;
        branchId?: string | null;
        customerId: string | null;
        bundleName?: string;
        note?: string;
        lines: BundleLineInput[];
    }) {
        return apiClient.post<PosOrderDto, Record<string, unknown>>("/orders/pos/draft/bundle-items", {
            orderId: input.orderId ?? undefined,
            branchId: optionalUuid(input.branchId),
            customerId: optionalUuid(input.customerId),
            bundleName: input.bundleName?.trim() || undefined,
            note: input.note?.trim() || undefined,
            lines: input.lines.map((line) => ({ ingredientId: line.ingredientId, quantity: line.quantity }))
        });
    },

    updateDraftBundleItem(
        orderId: string,
        itemId: string,
        input: { bundleName?: string; note?: string; lines: BundleLineInput[] }
    ) {
        return apiClient.put<PosOrderDto, Record<string, unknown>>(
            `/orders/pos/draft/${encodeURIComponent(orderId)}/bundle-items/${encodeURIComponent(itemId)}`,
            {
                bundleName: input.bundleName?.trim() || undefined,
                note: input.note?.trim() || undefined,
                lines: input.lines.map((line) => ({ ingredientId: line.ingredientId, quantity: line.quantity }))
            }
        );
    },

    /** Đơn đã thu tiền có trà đóng theo yêu cầu, chưa giao. */
    getAwaitingHandover(params: { keyword?: string; packingStatus?: BundlePackingStatus; page?: number; size?: number }) {
        return apiClient.get<BackorderPage>("/orders/custom-bundles", {
            query: {
                keyword: params.keyword?.trim() || undefined,
                packingStatus: params.packingStatus,
                page: params.page ?? 0,
                size: params.size ?? 20
            }
        });
    },

    refreshPacking(orderId: string) {
        return apiClient.post<PosOrderDto, Record<string, never>>(
            `/orders/${encodeURIComponent(orderId)}/custom-bundle/refresh`,
            {}
        );
    },

    handover(orderId: string, receivedAmount?: number) {
        return apiClient.post<FulfillBackorderResponse, { receivedAmount?: number }>(
            `/orders/${encodeURIComponent(orderId)}/custom-bundle/handover`,
            receivedAmount === undefined ? {} : { receivedAmount }
        );
    }
};

export const bundlePackingStatusLabels: Record<BundlePackingStatus, string> = {
    WAITING_MATERIAL: "Chờ nguyên liệu",
    AWAITING_PACKING: "Chờ đóng gói",
    PACKED: "Đã đóng gói",
    CANCELLED: "Đã huỷ"
};
