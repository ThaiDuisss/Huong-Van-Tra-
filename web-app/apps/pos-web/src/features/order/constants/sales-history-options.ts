import type {
    SalesHistoryOrder,
    SalesHistoryPaymentFilter,
    SalesHistoryStatusFilter,
    SalesPaymentMethod,
    SalesPaymentStatus
} from "../types/sales-history.types";

export const SALES_PAYMENT_STATUS_OPTIONS: {
    key: SalesHistoryStatusFilter;
    label: string;
    paymentStatus: SalesPaymentStatus | null;
}[] = [
    { key: "ALL", label: "Tất cả", paymentStatus: null },
    { key: "UNPAID", label: "Chưa thanh toán", paymentStatus: "UNPAID" },
    { key: "PAID", label: "Đã thanh toán", paymentStatus: "PAID" },
    { key: "CANCELLED", label: "Đã hủy", paymentStatus: "CANCELLED" }
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

const paymentStatusPriority: Record<SalesHistoryStatusFilter, number> = {
    ALL: -1,
    UNPAID: 0,
    PAID: 1,
    CANCELLED: 2
};

export function getSalesStatus(
    order: Pick<SalesHistoryOrder, "paymentStatus" | "orderStatus">
): Exclude<SalesHistoryStatusFilter, "ALL"> {
    if (order.orderStatus === "CANCELLED" || order.paymentStatus === "CANCELLED") {
        return "CANCELLED";
    }

    if (order.paymentStatus === "PAID" || order.orderStatus === "COMPLETED") {
        return "PAID";
    }

    return "UNPAID";
}

export function getSalesPaymentMethod(
    order: Pick<
        SalesHistoryOrder,
        "paymentMethod" | "paymentStatus" | "debtAmount"
    >
): Exclude<SalesHistoryPaymentFilter, "ALL"> {
    if (
        order.paymentMethod === "DEBT" ||
        (order.paymentStatus !== "PAID" && order.debtAmount > 0)
    ) {
        return "DEBT";
    }

    if (
        order.paymentMethod === "BANK_TRANSFER" ||
        order.paymentMethod === "TRANSFER"
    ) {
        return "BANK_TRANSFER";
    }

    return "CASH";
}

export function sortSalesOrdersByBusinessPriority<
    T extends {
        paymentStatus: SalesPaymentStatus;
        orderStatus: SalesHistoryOrder["orderStatus"];
        createdAt: string;
    }
>(orders: T[]) {
    return [...orders].sort((first, second) => {
        const statusDiff =
            paymentStatusPriority[getSalesStatus(first)] -
            paymentStatusPriority[getSalesStatus(second)];

        if (statusDiff !== 0) {
            return statusDiff;
        }

        return (
            new Date(second.createdAt).getTime() -
            new Date(first.createdAt).getTime()
        );
    });
}
