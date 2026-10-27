import { apiClient } from "../../../api/api-client";
import type {
    ConfirmCustomBundlePackingInput,
    CustomBundlePacking,
    CustomBundlePackingPage,
    CustomBundlePackingQuery
} from "../types/custom-bundle.types";

export const customBundleApi = {
    listPackings(query: CustomBundlePackingQuery) {
        return apiClient.get<CustomBundlePackingPage>("/inventory/custom-bundles/packings", {
            query: {
                status: query.status,
                search: query.search?.trim() || undefined,
                page: query.page,
                size: query.size
            }
        });
    },

    getPacking(id: string) {
        return apiClient.get<CustomBundlePacking>(`/inventory/custom-bundles/packings/${encodeURIComponent(id)}`);
    },

    /** Đơn chờ nguyên liệu: kiểm tra lại, đủ thì giữ nguyên liệu và chuyển sang chờ đóng gói. */
    recheck(id: string) {
        return apiClient.post<CustomBundlePacking, Record<string, never>>(
            `/inventory/custom-bundles/packings/${encodeURIComponent(id)}/recheck`,
            {}
        );
    },

    /** Xác nhận đã đóng gói: trừ nguyên liệu đã giữ và tạo giao dịch xuất kho CUSTOM_BUNDLE_ISSUE. */
    confirm(id: string, input: ConfirmCustomBundlePackingInput) {
        return apiClient.post<CustomBundlePacking, ConfirmCustomBundlePackingInput>(
            `/inventory/custom-bundles/packings/${encodeURIComponent(id)}/confirm`,
            input
        );
    }
};
