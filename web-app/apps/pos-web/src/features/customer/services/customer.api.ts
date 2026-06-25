import { apiClient } from "../../../api/api-client";
import type {
    CreateCustomerRequest,
    CustomerQuery,
    CustomerResponse,
    CustomerTierResponse,
    PageResponse
} from "../types/customer.types";

export const customerApi = {
    list(query: CustomerQuery = {}) {
        return apiClient.get<PageResponse<CustomerResponse>>("/customer", {
            query
        });
    },
    detail(id: string) {
        return apiClient.get<CustomerResponse>(`/customer/${id}`);
    },
    create(input: CreateCustomerRequest) {
        return apiClient.post<CustomerResponse, CreateCustomerRequest>(
            "/customer",
            input
        );
    },
    update(id: string, input: CreateCustomerRequest) {
        return apiClient.put<CustomerResponse, CreateCustomerRequest>(
            `/customer/${encodeURIComponent(id)}`,
            input
        );
    },
    remove(id: string) {
        return apiClient.delete<void>(
            `/customer/${encodeURIComponent(id)}`
        );
    },
    tiers() {
        return apiClient.get<CustomerTierResponse[]>("/customer/tiers");
    }
};
