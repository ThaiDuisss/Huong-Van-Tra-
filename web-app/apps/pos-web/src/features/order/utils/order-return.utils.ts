import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type {
    CustomerReturnQcResult,
    CustomerReturnQcStatus,
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

export const CUSTOMER_RETURN_QC_STATUS_LABELS: Record<
    CustomerReturnQcStatus,
    string
> = {
    PENDING: "Chưa kiểm tra",
    PARTIAL: "Đang kiểm tra một phần",
    COMPLETED: "Đã kiểm tra xong"
};

export const CUSTOMER_RETURN_QC_RESULT_LABELS: Record<
    CustomerReturnQcResult,
    string
> = {
    RESTOCK: "Nhập lại tồn bán được",
    QUARANTINE: "Cách ly chờ xử lý",
    WRITE_OFF: "Loại bỏ / ghi giảm"
};

export const CUSTOMER_RETURN_QC_RESULT_EFFECTS: Record<
    CustomerReturnQcResult,
    string
> = {
    RESTOCK: "Sản phẩm sẽ được nhập lại tồn bán được.",
    QUARANTINE: "Sản phẩm sẽ được đưa vào khu cách ly và không thể bán.",
    WRITE_OFF: "Sản phẩm sẽ bị loại bỏ và không được cộng lại tồn bán được."
};

export const CUSTOMER_RETURN_QC_RESULTS = Object.keys(
    CUSTOMER_RETURN_QC_RESULT_LABELS
) as CustomerReturnQcResult[];

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

export function getCustomerReturnQcStatusLabel(
    status?: CustomerReturnQcStatus | null
) {
    return status ? CUSTOMER_RETURN_QC_STATUS_LABELS[status] ?? status : "--";
}

export function getCustomerReturnQcResultLabel(
    result?: CustomerReturnQcResult | null
) {
    return result ? CUSTOMER_RETURN_QC_RESULT_LABELS[result] ?? result : "--";
}

export function getAllowedCustomerReturnQcResults(
    condition?: ReturnCondition | null
) {
    if (condition === "SEALED") {
        return CUSTOMER_RETURN_QC_RESULTS;
    }

    return CUSTOMER_RETURN_QC_RESULTS.filter((result) => result !== "RESTOCK");
}

export function isActiveReturnStatus(status?: ReturnStatus | null) {
    return status === "CREATED" || status === "RECEIVED" || status === "COMPLETED";
}

export function canCreateReturn(order?: OrderDetailResponse | null) {
    return (
        order?.status === "COMPLETED" &&
        (order.paymentStatus === "PAID" || order.paymentStatus === "PARTIALLY_REFUNDED")
    );
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

    if (
        message.includes("Only RECEIVED order returns can be QC") ||
        message.includes("Only RECEIVED order returns can be QC'd")
    ) {
        return "Phiếu hoàn chưa ở trạng thái đã nhận hàng.";
    }

    if (
        message.includes("QC quantity exceeds returned quantity") ||
        message.includes("Inspection quantity exceeds remaining returned quantity")
    ) {
        return "Số lượng kiểm tra vượt quá số lượng còn lại.";
    }

    if (
        message.includes("RESTOCK destination must be an active saleable location") ||
        message.includes("Destination location is not saleable")
    ) {
        return "Vị trí nhập lại không phải vị trí có thể bán.";
    }

    if (
        message.includes("QUARANTINE destination must be a QUARANTINE location") ||
        message.includes("Destination location must be QUARANTINE")
    ) {
        return "Vui lòng chọn vị trí cách ly.";
    }

    if (
        message.includes("Inventory item not found") ||
        message.includes("Active inventory item not found")
    ) {
        return "Không tìm thấy sản phẩm tương ứng trong kho.";
    }

    if (
        message.includes("Customer return inspection is already posted") ||
        message.includes("Inspection already posted")
    ) {
        return "Phiếu kiểm tra này đã được xử lý.";
    }

    if (message.includes("destinationLocationId is required")) {
        return "Vui lòng chọn vị trí đích.";
    }

    if (message.includes("RESTOCK is not allowed for return condition")) {
        return "Tình trạng hàng này không được nhập lại tồn bán được.";
    }

    if (message.includes("WRITE_OFF for SEALED return item requires a reason note")) {
        return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
    }

    return message;
}
