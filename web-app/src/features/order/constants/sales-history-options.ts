import type {
    SalesHistoryPaymentFilter,
    SalesHistoryStatusFilter,
    SalesPaymentMethod
} from "../types/sales-history.types";

export const SALES_STATUS_FILTER_OPTIONS: {
    key: SalesHistoryStatusFilter;
    label: string;
    status: string | null;
}[] = [
    { key: "ALL", label: "Tất cả", status: null },
    {
        key: "PENDING_PAYMENT",
        label: "Chưa thanh toán",
        status: "PENDING_PAYMENT"
    },
    { key: "COMPLETED", label: "Đã thanh toán", status: "COMPLETED" },
    { key: "CANCELLED", label: "Đã hủy", status: "CANCELLED" }
];

export const SALES_PAYMENT_METHOD_OPTIONS: {
    key: SalesHistoryPaymentFilter;
    label: string;
    paymentMethod: SalesPaymentMethod | null;
}[] = [
    { key: "ALL", label: "Tất cả", paymentMethod: null },
    { key: "CASH", label: "Tiền mặt", paymentMethod: "CASH" },
    {
        key: "BANK_TRANSFER",
        label: "Chuyển khoản",
        paymentMethod: "BANK_TRANSFER"
    },
    { key: "DEBT", label: "Công nợ", paymentMethod: "DEBT" }
];
