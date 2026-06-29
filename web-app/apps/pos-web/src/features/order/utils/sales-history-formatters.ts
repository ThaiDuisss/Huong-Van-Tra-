import type {
    SalesHistoryOrder,
    SalesPaymentMethod,
    SalesPaymentStatus
} from "../types/sales-history.types";

const paymentMethodLabels: Record<SalesPaymentMethod, string> = {
    CASH: "Tiền mặt",
    BANK_TRANSFER: "Chuyển khoản",
    TRANSFER: "Chuyển khoản",
    DEBT: "Công nợ"
};

const paymentStatusLabels: Record<SalesPaymentStatus, string> = {
    PAID: "Đã thanh toán",
    UNPAID: "Chưa thanh toán",
    PENDING: "Chưa thanh toán",
    PENDING_PAYMENT: "Chưa thanh toán",
    CANCELLED: "Đã hủy"
};

function pad(value: number) {
    return String(value).padStart(2, "0");
}

export function formatCurrency(value?: number | string | null) {
    const numericValue =
        typeof value === "number" ? value : Number(value ?? Number.NaN);

    if (!Number.isFinite(numericValue)) {
        return "--";
    }

    return `${numericValue.toLocaleString("vi-VN")} đ`;
}

export function formatSaleTime(value?: string | null) {
    if (!value) {
        return "--";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "--";
    }

    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formatSaleDateTime(value?: string | null) {
    if (!value) {
        return "--";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "--";
    }

    return `${pad(date.getDate())}/${pad(
        date.getMonth() + 1
    )}/${date.getFullYear()} ${pad(date.getHours())}:${pad(
        date.getMinutes()
    )}`;
}

export function getSalesPaymentMethodLabel(method: SalesPaymentMethod) {
    return paymentMethodLabels[method] ?? method;
}

export function getSalesPaymentStatusLabel(status: SalesPaymentStatus) {
    return paymentStatusLabels[status] ?? status;
}

export function getSalesOrderCustomer(order: SalesHistoryOrder) {
    return order.customerName.trim() || "Khách lẻ";
}
