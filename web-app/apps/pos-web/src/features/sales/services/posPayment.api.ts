import { apiClient } from "../../../api/api-client";

export interface CashPaymentInput {
    orderId: string;
    receivedAmount: number;
}

export interface CashPaymentResponse {
    paymentId: string;
    paymentCode: string;
    orderId: string;
    amountRequired: number;
    amountPaid: number;
    receivedAmount: number;
    changeAmount: number;
    status: string;
    method: "CASH";
}

export const posPaymentApi = {
    payByCash(input: CashPaymentInput) {
        return apiClient.post<CashPaymentResponse, CashPaymentInput>(
            "/payments/cash",
            input
        );
    }
};
