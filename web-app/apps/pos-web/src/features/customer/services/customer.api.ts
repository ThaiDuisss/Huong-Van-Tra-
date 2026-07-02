import { apiClient } from "../../../api/api-client";
import type {
    ChangeTierStatusRequest,
    CreateCustomerRequest,
    CustomerQuery,
    CustomerPurchaseTransactionResponse,
    CustomerResponse,
    CustomerTierAssignmentResponse,
    CustomerTierCountResponse,
    CustomerTierRequest,
    CustomerTierResponse,
    CustomerTierYearlyMetricResponse,
    ManualTierAdjustmentRequest,
    PageResponse,
    TierProgressResponse
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
    tierProgress(customerId: string) {
        return apiClient.get<TierProgressResponse>(
            `/customer/${encodeURIComponent(customerId)}/tier-progress`
        );
    },
    tierHistory(customerId: string) {
        return apiClient.get<CustomerTierAssignmentResponse[]>(
            `/customer/${encodeURIComponent(customerId)}/tier-history`
        );
    },
    tierMetric(customerId: string, year?: number) {
        return apiClient.get<CustomerTierYearlyMetricResponse>(
            `/customer/${encodeURIComponent(customerId)}/tier-metrics`,
            {
                query: year ? { year } : undefined
            }
        );
    },
    purchaseTransactions(customerId: string, page = 0, size = 10) {
        return apiClient.get<PageResponse<CustomerPurchaseTransactionResponse>>(
            `/customer/${encodeURIComponent(customerId)}/purchase-transactions`,
            {
                query: { page, size }
            }
        );
    },
    adjustTier(customerId: string, input: ManualTierAdjustmentRequest) {
        return apiClient.post<
            CustomerTierAssignmentResponse,
            ManualTierAdjustmentRequest
        >(
            `/customer/${encodeURIComponent(customerId)}/tier-adjustments`,
            input
        );
    }
};
