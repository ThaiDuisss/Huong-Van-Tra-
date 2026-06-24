import type { SaleCustomer } from "../types/sale.types";

export interface CustomerInfoResponse {
    id: string;
    customerCode: string;
    fullName: string;
    phone: string;
    email: string;
    dateOfBirth: string;
    tierId: string;
    tierCode: string;
    tierName: string;
    creditLimit: number;
    status: string;
}

function toSaleCustomer(customer: CustomerInfoResponse): SaleCustomer {
    return {
        id: customer.id,
        customerCode: customer.customerCode,
        name: customer.fullName,
        phone: customer.phone,
        tier: customer.tierName,
        points: 0,
        orderCount: 0,
        totalSpent: 0,
        avatarUrl: ""
    };
}

export const posCustomerApi = {
    async listCustomerInfo() {
        const baseUrl =
            import.meta.env.VITE_API_BASE_URL ??
            "http://localhost:8080/api";
        const response = await fetch(
            `${baseUrl}/orders/customer-info`,
            {
                headers: {
                    Accept: "application/json",
                    "X-Client-Type": "POS_WEB"
                }
            }
        );

        if (!response.ok) {
            throw new Error("Không thể tải danh sách khách hàng");
        }

        const customers =
            (await response.json()) as CustomerInfoResponse[];

        return customers.map(toSaleCustomer);
    }
};
