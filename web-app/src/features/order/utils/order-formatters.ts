import type { DateFilterKey } from "../../../components/shared/dateFilters";
import type {
    OrderDetailResponse,
    OrderStatusHistoryResponse,
    OrderSummaryResponse
} from "../types/order.types";

const ORDER_STATUS_LABELS: Record<string, string> = {
    DRAFT: "Nháp",
    PENDING: "Chờ duyệt",
    PENDING_PAYMENT: "Chờ thanh toán",
    PAYMENT_CONFIRMED: "Chờ duyệt",
    PROCESSING: "Đang xử lý",
    SHIPPING: "Đang giao hàng",
    COMPLETED: "Đã hoàn thành",
    CANCELLED: "Đã hủy",
    RETURNED: "Đã hoàn",
    REFUNDED: "Đã hoàn tiền",
    EXPIRED: "Đã hết hạn",
    PAYMENT_FAILED: "Thanh toán thất bại"
};

const PAYMENT_STATUS_LABELS: Record<string, string> = {
    PAID: "Đã thanh toán",
    UNPAID: "Chưa thanh toán",
    WAITING_PAYMENT: "Chưa thanh toán",
    PENDING: "Chưa thanh toán",
    PARTIAL: "Thanh toán một phần",
    FAILED: "Thanh toán thất bại",
    REFUNDED: "Đã hoàn tiền",
    PARTIALLY_REFUNDED: "Hoàn tiền một phần",
    UNAVAILABLE: "Không xác định",
    UNKNOWN: "Không xác định"
};

const ORDER_TYPE_LABELS: Record<string, string> = {
    POS: "Bán tại quầy",
    ONLINE: "Đơn online",
    RETURN: "Hoàn hàng",
    REFUND: "Hoàn tiền",
    EXCHANGE: "Đổi hàng"
};

function toNumber(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return null;
    }

    const numericValue =
        typeof value === "number" ? value : Number(value);

    return Number.isFinite(numericValue) ? numericValue : null;
}

function pad(value: number) {
    return value.toString().padStart(2, "0");
}

function toDateInputValue(date: Date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
        date.getDate()
    )}`;
}

export function formatCurrency(value?: number | string | null) {
    const numericValue = toNumber(value);

    if (numericValue === null) {
        return "--";
    }

    return `${numericValue.toLocaleString("vi-VN")}đ`;
}

export function formatQuantity(value?: number | string | null) {
    const numericValue = toNumber(value);

    if (numericValue === null) {
        return "0";
    }

    return Number.isInteger(numericValue)
        ? numericValue.toLocaleString("vi-VN")
        : numericValue.toLocaleString("vi-VN", {
            maximumFractionDigits: 3
        });
}

export function formatDateTime(value?: string | null) {
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

export function formatDateOnly(value?: string | null) {
    if (!value) {
        return "--";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "--";
    }

    return `${pad(date.getDate())}/${pad(
        date.getMonth() + 1
    )}/${date.getFullYear()}`;
}

export function getOrderStatusLabel(status?: string | null) {
    if (!status) {
        return "--";
    }

    return ORDER_STATUS_LABELS[status] ?? status;
}

export function getPaymentStatusLabel(status?: string | null) {
    if (!status) {
        return "--";
    }

    return PAYMENT_STATUS_LABELS[status] ?? status;
}

export function getOrderTypeLabel(orderType?: string | null) {
    if (!orderType) {
        return "--";
    }

    return ORDER_TYPE_LABELS[orderType] ?? orderType;
}

export function getCustomerName(
    order?: Pick<OrderSummaryResponse, "customerName"> | null
) {
    return order?.customerName?.trim() || "Khách lẻ";
}

export function getStaffName(
    order?:
        | Pick<OrderSummaryResponse, "cashierName" | "createdByName">
        | null
) {
    return (
        order?.cashierName?.trim() ||
        order?.createdByName?.trim() ||
        "--"
    );
}

export function getOrderCode(order?: OrderSummaryResponse | null) {
    return order?.orderCode?.trim() || "--";
}

export function getOrderStatusText(order?: OrderSummaryResponse | null) {
    return order?.statusLabel?.trim() || getOrderStatusLabel(order?.status);
}

export function getPaymentStatusText(order?: OrderSummaryResponse | null) {
    return (
        order?.paymentStatusLabel?.trim() ||
        getPaymentStatusLabel(order?.paymentStatus)
    );
}

export function getOrderTypeText(order?: OrderSummaryResponse | null) {
    return order?.orderTypeLabel?.trim() || getOrderTypeLabel(order?.orderType);
}

export function getHistoryTargetStatus(
    history: OrderStatusHistoryResponse
) {
    return (
        history.toStatusLabel?.trim() ||
        history.newStatusLabel?.trim() ||
        history.statusLabel?.trim() ||
        getOrderStatusLabel(
            history.toStatus || history.newStatus || history.status
        )
    );
}

export function buildDateRangeFilter(key: DateFilterKey) {
    const today = new Date();
    const fromDate = new Date(today);
    const toDate = new Date(today);

    if (key === "ALL") {
        return {};
    }

    if (key === "YESTERDAY") {
        fromDate.setDate(today.getDate() - 1);
        toDate.setDate(today.getDate() - 1);
    }

    if (key === "LAST_7_DAYS") {
        fromDate.setDate(today.getDate() - 6);
    }

    if (key === "THIS_MONTH") {
        fromDate.setDate(1);
    }

    return {
        fromDate: toDateInputValue(fromDate),
        toDate: toDateInputValue(toDate)
    };
}

export function buildInvoiceCode(
    order: Pick<
        OrderSummaryResponse,
        "invoiceCode" | "orderCode" | "createdAt" | "id"
    >,
    index = 0
) {
    if (order.invoiceCode?.trim()) {
        return order.invoiceCode;
    }

    if (order.orderCode?.trim()) {
        return `HD-${order.orderCode}`;
    }

    const date = order.createdAt ? new Date(order.createdAt) : null;
    const datePart =
        date && !Number.isNaN(date.getTime())
            ? `${String(date.getFullYear()).slice(-2)}${pad(
                date.getMonth() + 1
            )}${pad(date.getDate())}`
            : "ORDER";
    const idPart = order.id ? order.id.slice(0, 4).toUpperCase() : pad(index + 1);

    // TODO: thay bằng payments.invoiceCode từ Payment-Service khi có invoice API chính thức.
    return `HD-${datePart}-${idPart}`;
}

export function getRecipientName(order?: OrderDetailResponse | null) {
    return order?.recipientName?.trim() || getCustomerName(order);
}

export function getRecipientPhone(order?: OrderDetailResponse | null) {
    return order?.recipientPhone?.trim() || order?.customerPhone?.trim() || "--";
}
