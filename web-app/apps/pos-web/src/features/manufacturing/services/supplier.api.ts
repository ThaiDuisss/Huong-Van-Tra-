import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../../product/types/product.types";
import type {
    SupplierFilterParams,
    SupplierRequest,
    SupplierResponse,
    SupplierStatus
} from "../types/supplier.types";

export const supplierApi = {
    list: (params?: SupplierFilterParams) => {
        return apiClient.get<PageResult<SupplierResponse>>("/catalog/suppliers", {
            query: {
                page: params?.page,
                size: params?.size,
                search: params?.search,
                status: params?.status === "ALL" ? undefined : params?.status,
                ingredientId: params?.ingredientId === "ALL" ? undefined : params?.ingredientId
            }
        });
    },

    getById: (id: string) => {
        return apiClient.get<SupplierResponse>(`/catalog/suppliers/${encodeURIComponent(id)}`);
    },

    create: (data: SupplierRequest) => {
        return apiClient.post<SupplierResponse, SupplierRequest>("/catalog/suppliers", data);
    },

    update: (id: string, data: SupplierRequest) => {
        return apiClient.put<SupplierResponse, SupplierRequest>(`/catalog/suppliers/${encodeURIComponent(id)}`, data);
    },

    updateStatus: (id: string, status: SupplierStatus) => {
        return apiClient.patch<SupplierResponse, { status: SupplierStatus }>(
            `/catalog/suppliers/${encodeURIComponent(id)}/status`,
            { status }
        );
    },

    delete: (id: string) => {
        return apiClient.delete<void>(`/catalog/suppliers/${encodeURIComponent(id)}`);
    }
};
