import { apiClient } from "../../../api/api-client";
import type { InventoryItemType, PageResponse } from "../types/inventory.types";
import type {
    ReplenishmentPolicy,
    ReplenishmentPolicyMatrixQuery,
    ReplenishmentPolicyMatrixRow,
    ReplenishmentPolicyQuery,
    ReplenishmentPolicyRequest
} from "../types/replenishment-policy.types";

export const replenishmentPolicyApi = {
    list(query: ReplenishmentPolicyQuery = {}) {
        return apiClient.get<PageResponse<ReplenishmentPolicy>>(
            "/inventory/replenishment-policies",
            { query }
        );
    },
    matrix(query: ReplenishmentPolicyMatrixQuery = {}) {
        return apiClient.get<PageResponse<ReplenishmentPolicyMatrixRow>>(
            "/inventory/replenishment-policies/matrix",
            { query }
        );
    },
    detail(id: string) {
        return apiClient.get<ReplenishmentPolicy>(
            `/inventory/replenishment-policies/${id}`
        );
    },
    create(input: ReplenishmentPolicyRequest) {
        return apiClient.post<ReplenishmentPolicy, ReplenishmentPolicyRequest>(
            "/inventory/replenishment-policies",
            input
        );
    },
    update(id: string, input: ReplenishmentPolicyRequest) {
        return apiClient.put<ReplenishmentPolicy, ReplenishmentPolicyRequest>(
            `/inventory/replenishment-policies/${id}`,
            input
        );
    },
    toggleEnabled(id: string, enabled: boolean) {
        return apiClient.patch<ReplenishmentPolicy>(
            `/inventory/replenishment-policies/${id}/enabled`,
            undefined,
            { query: { enabled } }
        );
    },
    remove(id: string) {
        return apiClient.delete<void>(`/inventory/replenishment-policies/${id}`);
    },
    searchInventoryItems(
        search: string = "",
        page: number = 0,
        size: number = 20,
        itemType?: InventoryItemType
    ) {
        return apiClient.get<PageResponse<{ id: string; name: string }>>(
            "/inventory/inventory-items/search-lookup",
            {
                query: {
                    search: search || undefined,
                    itemType,
                    page,
                    size
                }
            }
        );
    }
};
