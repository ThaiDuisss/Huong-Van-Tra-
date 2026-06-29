import type {
    SalesHistoryOrder,
    SalesOrderDetail,
    SalesOrderItemDetail,
    SalesPaymentMethod
} from "../types/sales-history.types";

const paymentMethodLabels: Record<string, string> = {
    CASH: "Tiền mặt",
    BANK_TRANSFER: "Chuyển khoản",
    TRANSFER: "Chuyển khoản",
    QR_CODE: "Chuyển khoản",
    DEBT: "Công nợ"
};

function pad(value: number) {
    return String(value).padStart(2, "0");
}

export function toNumber(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return 0;
    }

    const numericValue =
        typeof value === "number" ? value : Number(value);

    return Number.isFinite(numericValue) ? numericValue : 0;
}

export function formatCurrency(value?: number | string | null) {
    return `${toNumber(value).toLocaleString("vi-VN")} đ`;
}

export function formatQuantity(value?: number | string | null) {
    const numericValue = toNumber(value);

    return Number.isInteger(numericValue)
        ? numericValue.toLocaleString("vi-VN")
        : numericValue.toLocaleString("vi-VN", {
              maximumFractionDigits: 3
          });
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

export function getOrderSaleTime(
    order: Pick<SalesHistoryOrder, "soldAt" | "createdAt">
) {
    return order.soldAt || order.createdAt || null;
}

export function getDetailSaleTime(order: SalesOrderDetail) {
    return order.soldAt || order.createdAt || null;
}

export function getSalesPaymentMethodLabel(
    method?: SalesPaymentMethod | null,
    label?: string | null
) {
    return label?.trim() || (method ? paymentMethodLabels[method] ?? method : "Chưa xác định");
}

export function getSalesStatusLabel(
    status?: string | null,
    label?: string | null
) {
    return label?.trim() || status || "--";
}

export function getSalesOrderCustomer(
    order: Pick<SalesHistoryOrder, "customerName">
) {
    return order.customerName?.trim() || "Khách lẻ";
}

export function getDetailCustomer(order: SalesOrderDetail) {
    return order.customerName?.trim() || "Khách lẻ";
}

export function getOrderId(order: SalesOrderDetail) {
    return order.orderId || order.id || "";
}

export function getItemProductName(item: SalesOrderItemDetail) {
    return (
        item.productName?.trim() ||
        item.productNameSnapshot?.trim() ||
        item.variantName?.trim() ||
        item.variantNameSnapshot?.trim() ||
        item.name?.trim() ||
        "--"
    );
}

export function getItemSku(item: SalesOrderItemDetail) {
    return item.sku?.trim() || item.skuSnapshot?.trim() || "--";
}

export function getItemUnitPrice(item: SalesOrderItemDetail) {
    return item.unitPrice ?? item.actualUnitPrice ?? item.price ?? 0;
}
