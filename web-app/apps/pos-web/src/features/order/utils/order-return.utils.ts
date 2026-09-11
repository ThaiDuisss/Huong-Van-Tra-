import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type {
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason,
    ReturnStatus
} from "../types/order-return.types";

export const RETURN_STATUS_LABELS: Record<ReturnStatus, string> = {
    CREATED: "Đã tạo",
    RECEIVED: "Đã nhận hàng",
    COMPLETED: "Hoàn tất",
    CANCELLED: "Đã hủy"
};

export const RETURN_REASON_LABELS: Record<ReturnReason, string> = {
    DEFECTIVE: "Sản phẩm bị lỗi",
    DAMAGED: "Sản phẩm bị hư hỏng",
    WRONG_ITEM: "Bán/giao sai sản phẩm",
    QUALITY_ISSUE: "Vấn đề chất lượng",
    EXPIRED: "Sản phẩm hết hạn",
    CHANGE_OF_MIND: "Khách đổi ý",
    OTHER: "Lý do khác"
};

export const RETURN_CONDITION_LABELS: Record<ReturnCondition, string> = {
    SEALED: "Còn nguyên seal",
    OPENED: "Đã mở bao bì",
    DAMAGED: "Bị hư hỏng",
    SPOILED: "Mốc / biến chất",
    USED: "Đã sử dụng"
};

export const RETURN_REASONS = Object.keys(
    RETURN_REASON_LABELS
) as ReturnReason[];

export const RETURN_CONDITIONS = Object.keys(
    RETURN_CONDITION_LABELS
) as ReturnCondition[];

export function toReturnNumber(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return 0;
    }

    const numericValue = typeof value === "number" ? value : Number(value);

    return Number.isFinite(numericValue) ? numericValue : 0;
}

export function formatReturnQuantity(value?: number | string | null) {
    const numericValue = toReturnNumber(value);

    return Number.isInteger(numericValue)
        ? numericValue.toLocaleString("vi-VN")
        : numericValue.toLocaleString("vi-VN", {
              maximumFractionDigits: 3
          });
}

export function getReturnCode(returnItem?: {
    returnCode?: string | null;
    createdAt?: string | null;
}) {
    if (returnItem?.returnCode?.trim()) {
        return returnItem.returnCode;
    }

    const date = returnItem?.createdAt ? new Date(returnItem.createdAt) : null;

    if (date && !Number.isNaN(date.getTime())) {
        const datePart = [
            date.getFullYear(),
            String(date.getMonth() + 1).padStart(2, "0"),
            String(date.getDate()).padStart(2, "0")
        ].join("");

        return `PH-${datePart}`;
    }

    return "Phiếu hoàn";
}

export function getReturnStatusLabel(status?: ReturnStatus | null) {
    return status ? RETURN_STATUS_LABELS[status] ?? status : "--";
}

export function getReturnReasonLabel(reason?: ReturnReason | null) {
    return reason ? RETURN_REASON_LABELS[reason] ?? reason : "--";
}

export function getReturnConditionLabel(condition?: ReturnCondition | null) {
    return condition ? RETURN_CONDITION_LABELS[condition] ?? condition : "--";
}

export function isActiveReturnStatus(status?: ReturnStatus | null) {
    return status === "CREATED" || status === "RECEIVED";
}

export function canCreateReturn(order?: OrderDetailResponse | null) {
    return order?.status === "COMPLETED" && order.paymentStatus === "PAID";
}

export function calculateReturnedQuantity(
    orderItemId: string,
    returns: OrderReturnResponse[]
) {
    return returns.reduce((total, returnItem) => {
        if (!isActiveReturnStatus(returnItem.status)) {
            return total;
        }

        return (
            total +
            (returnItem.items ?? []).reduce((itemTotal, item) => {
                if (item.orderItemId !== orderItemId) {
                    return itemTotal;
                }

                return itemTotal + toReturnNumber(item.quantity);
            }, 0)
        );
    }, 0);
}

export function calculateAvailableReturnQuantity(
    item: OrderItemResponse,
    returns: OrderReturnResponse[]
) {
    if (!item.id) {
        return 0;
    }

    return Math.max(
        0,
        toReturnNumber(item.quantity) -
            calculateReturnedQuantity(item.id, returns)
    );
}

export function getReturnErrorMessage(error: unknown) {
    const message =
        error instanceof Error && error.message
            ? error.message
            : "Không thể xử lý phiếu hoàn. Vui lòng thử lại.";

    if (message.includes("Order is not eligible for return")) {
        return "Đơn hàng không đủ điều kiện hoàn.";
    }

    if (
        message.includes(
            "Return quantity exceeds remaining returnable quantity"
        )
    ) {
        return "Số lượng hoàn vượt quá số lượng còn có thể hoàn.";
    }

    if (message.includes("Change-of-mind return requires sealed product")) {
        return "Khách đổi ý chỉ áp dụng với sản phẩm còn nguyên seal.";
    }

    if (message.includes("Return window has expired")) {
        return "Đơn hàng đã quá thời hạn hoàn.";
    }

    if (message.includes("Note is required when reason is OTHER")) {
        return "Vui lòng nhập ghi chú cho lý do khác.";
    }

    if (
        message.includes("Only CREATED return can be received") ||
        message.includes("Only created returns can be received")
    ) {
        return "Chỉ phiếu hoàn đang tạo mới được xác nhận nhận hàng.";
    }

    if (message.includes("Only CREATED return can be cancelled")) {
        return "Chỉ phiếu hoàn đang tạo mới được hủy.";
    }

    return message;
}
