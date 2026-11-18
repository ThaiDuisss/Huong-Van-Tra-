import { apiClient } from "../../../api/api-client";
import type {
    ChangeCustomerStatusRequest,
    ChangeTierStatusRequest,
    CreateCustomerRequest,
    CustomerImportRequest,
    CustomerImportResponse,
    CustomerQuery,
    CustomerResponse,
    CustomerTierCountResponse,
    CustomerTierRequest,
    CustomerTierResponse,
    LoyaltyAdjustmentRequest,
    LoyaltyTransactionResponse,
    PageResponse
} from "../types/customer.types";

export const customerApi = {
    list(query: CustomerQuery = {}) {
        return apiClient.get<PageResponse<CustomerResponse>>("/customer", {
            query
        });
    },
    detail(id: string) {
        return apiClient.get<CustomerResponse>(`/customer/${encodeURIComponent(id)}`);
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
    changeStatus(id: string, input: ChangeCustomerStatusRequest) {
        return apiClient.patch<CustomerResponse, ChangeCustomerStatusRequest>(
            `/customer/${encodeURIComponent(id)}/status`,
            input
        );
    },
    importCustomers(input: CustomerImportRequest) {
        return apiClient.post<CustomerImportResponse, CustomerImportRequest>(
            "/customer/import",
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
    },
    tierCounts() {
        return apiClient.get<CustomerTierCountResponse[]>("/customer/tiers/counts");
    },
    createTier(input: CustomerTierRequest) {
        return apiClient.post<CustomerTierResponse, CustomerTierRequest>(
            "/customer/tiers",
            input
        );
    },
    updateTier(id: string, input: CustomerTierRequest) {
        return apiClient.put<CustomerTierResponse, CustomerTierRequest>(
            `/customer/tiers/${encodeURIComponent(id)}`,
            input
        );
    },
    changeTierStatus(id: string, input: ChangeTierStatusRequest) {
        return apiClient.patch<void, ChangeTierStatusRequest>(
            `/customer/tiers/${encodeURIComponent(id)}/status`,
            input
        );
    },
    loyaltyTransactions(customerId: string, page = 0, size = 10) {
        return apiClient.get<PageResponse<LoyaltyTransactionResponse>>(
            `/customer/${encodeURIComponent(customerId)}/loyalty-transactions`,
            {
                query: { page, size }
            }
        );
    },
    adjustPoints(customerId: string, input: LoyaltyAdjustmentRequest) {
        return apiClient.post<LoyaltyTransactionResponse, LoyaltyAdjustmentRequest>(
            `/customer/${encodeURIComponent(customerId)}/loyalty-adjustments`,
            input
        );
    }
};
