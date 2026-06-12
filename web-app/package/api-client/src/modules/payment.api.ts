import type { ApiClient } from "../core/types";

export type PaymentMethod =
    | "CASH"
    | "BANK_TRANSFER"
    | "QR_CODE"
    | "CARD";

export type PaymentStatus =
    | "PENDING"
    | "PROCESSING"
    | "COMPLETED"
    | "FAILED"
    | "REFUNDED";

export interface Payment {
    id: string;
    orderId: string;
    amount: number;
    method: PaymentMethod;
    status: PaymentStatus;
    provider?: string;
    transactionReference?: string;
    paidAt?: string;
}

export interface CreatePaymentInput {
    orderId: string;
    amount: number;
    method: PaymentMethod;
}

export interface CreateQrPaymentResponse {
    paymentId: string;
    qrCode: string;
    checkoutUrl?: string;
    expiresAt: string;
}

export function createPaymentApi(
    apiClient: ApiClient
) {
    return {
        createPayment(input: CreatePaymentInput) {
            return apiClient.post<
                Payment,
                CreatePaymentInput
            >("/payments", input);
        },

        createQrPayment(input: {
            orderId: string;
            amount: number;
        }) {
            return apiClient.post<
                CreateQrPaymentResponse,
                {
                    orderId: string;
                    amount: number;
                }
            >("/payments/qr", input);
        },

        getPaymentById(id: string) {
            return apiClient.get<Payment>(
                `/payments/${encodeURIComponent(id)}`
            );
        },

        refundPayment(
            id: string,
            input: {
                amount: number;
                reason: string;
            }
        ) {
            return apiClient.post<
                Payment,
                {
                    amount: number;
                    reason: string;
                }
            >(
                `/payments/${encodeURIComponent(id)}/refund`,
                input
            );
        }
    };
}

export type PaymentApi =
    ReturnType<typeof createPaymentApi>;