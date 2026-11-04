import { apiClient } from "../../../api/api-client";
import type {
    CustomerResponse,
    PageResponse
} from "../../customer/types/customer.types";
import type { SaleCustomer } from "../types/sale.types";

export function toSaleCustomer(customer: CustomerResponse): SaleCustomer {
    return {
        id: customer.id,
        customerCode: customer.customerCode,
        name: customer.fullName,
        phone: customer.phone ?? "",
        email: customer.email,
        tier: customer.tierName ?? "Khách thường",
        points: 0,
        orderCount: 0,
        totalSpent: Number(customer.totalSpent ?? 0),
        avatarUrl: ""
    };
}

function getCustomers(
    response: CustomerResponse[] | PageResponse<CustomerResponse>
) {
    return Array.isArray(response) ? response : response.content;
}

export const posCustomerApi = {
    async listCustomerInfo() {
        const response = await apiClient.get<
            CustomerResponse[] | PageResponse<CustomerResponse>
        >("/customer", {
            query: {
                status: "ACTIVE",
                page: 0,
                size: 100
            }
        });

        return getCustomers(response).map(toSaleCustomer);
    }
};
