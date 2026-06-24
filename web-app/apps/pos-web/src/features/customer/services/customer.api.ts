import { apiClient } from "../../../api/api-client";
import type {
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
    tiers() {
        return apiClient.get<CustomerTierResponse[]>("/customer/tiers");
    }
};
