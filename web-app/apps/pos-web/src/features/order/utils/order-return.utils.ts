import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type {
    CustomerReturnInspectionStatus,
    CustomerReturnQcResult,
    CustomerReturnQcStatus,
    CustomerReturnQcSummaryResponse,
    OrderRefundStatus,
    OrderReturnResponse,
    ReturnCondition,
    ReturnDisplayStatus,
    ReturnReason,
    ReturnStatus
} from "../types/order-return.types";

export const RETURN_STATUS_LABELS: Record<ReturnStatus, string> = {
    CREATED: "Đã tạo",
    RECEIVED: "Đã nhận hàng",
    COMPLETED: "Hoàn tất",
    CANCELLED: "Đã hủy"
};

export type { ReturnDisplayStatus };

export const RETURN_DISPLAY_STATUS_LABELS: Record<ReturnDisplayStatus, string> = {
    CREATED: "Đã tạo",
    RECEIVED: "Đã nhận hàng",
    PROCESSING: "Đang xử lý hàng trả",
    PROCESSING_FAILED: "Xử lý kho thất bại",
    PENDING_REFUND: "Chờ hoàn tiền",
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
    COMPLETED: "Đã kiểm tra xong",
    UNAVAILABLE: "Không xác định",
    UNKNOWN: "Không xác định"
};

export const CUSTOMER_RETURN_QC_RESULT_LABELS: Record<
    CustomerReturnQcResult,
    string
> = {
    RESTOCK: "Nhập lại kho",
    DAMAGED: "Hàng hỏng",
    QUARANTINE: "Cách ly",
    WRITE_OFF: "Tiêu hủy"
};

export const CUSTOMER_RETURN_QC_RESULT_EFFECTS: Record<
    CustomerReturnQcResult,
    string
> = {
    RESTOCK: "Sản phẩm sẽ được nhập lại kho bán được.",
    DAMAGED: "Sản phẩm sẽ được đưa vào khu hàng hỏng và không thể bán.",
    QUARANTINE: "Sản phẩm sẽ được đưa vào khu cách ly và không thể bán.",
    WRITE_OFF: "Sản phẩm sẽ bị tiêu hủy và không tăng tồn kho."
};

export const CUSTOMER_RETURN_QC_RESULTS: CustomerReturnQcResult[] = [
    "RESTOCK",
    "DAMAGED",
    "WRITE_OFF"
];

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

export function getReturnDisplayStatusLabel(displayStatus?: ReturnDisplayStatus | null) {
    return displayStatus ? RETURN_DISPLAY_STATUS_LABELS[displayStatus] ?? displayStatus : "--";
}

export const ORDER_REFUND_STATUS_LABELS: Record<OrderRefundStatus, string> = {
    PENDING: "Chờ hoàn tiền",
    REFUNDED: "Đã hoàn tiền",
    NOT_READY: "Chưa sẵn sàng",
    FAILED: "Hoàn tiền thất bại",
    NOT_REQUIRED: "Không phát sinh"
};

export function getOrderRefundStatusLabel(status?: OrderRefundStatus | null) {
    return status ? ORDER_REFUND_STATUS_LABELS[status] ?? status : "--";
}

export interface ResolveReturnDisplayStatusParams {
    status?: ReturnStatus | null;
    refundRequired?: boolean | null;
    qcSummary?: CustomerReturnQcSummaryResponse | null;
    refundStatus?: OrderRefundStatus | null;
    isPendingDisposition?: boolean | null;
    hasFailedDisposition?: boolean | null;
    qcStatus?: CustomerReturnQcStatus | string | null;
    inspectionStatus?: CustomerReturnInspectionStatus | string | null;
    postedQcQuantity?: number | string | null;
    remainingQcQuantity?: number | string | null;
}

export function resolveReturnDisplayStatus({
    status,
    refundRequired,
    qcSummary,
    refundStatus,
    isPendingDisposition = false,
    hasFailedDisposition = false,
    qcStatus,
    inspectionStatus,
    postedQcQuantity,
    remainingQcQuantity
}: ResolveReturnDisplayStatusParams): ReturnDisplayStatus {
    if (status === "CREATED") {
        return "CREATED";
    }

    if (status === "CANCELLED") {
        return "CANCELLED";
    }

    if (status === "COMPLETED") {
        return "COMPLETED";
    }

    if (status === "RECEIVED") {
        // Ưu tiên kiểm tra failed disposition trước:
        // FAILED: không được map thành "Đang xử lý hàng trả"
        if (hasFailedDisposition) {
            return "PROCESSING_FAILED";
        }

        const lines = qcSummary?.lines ?? [];
        const hasQcLines = lines.length > 0;

        const effectiveRemaining: number | null = hasQcLines
            ? lines.reduce((sum, line) => sum + toReturnNumber(line.remainingQcQuantity), 0)
            : remainingQcQuantity !== undefined && remainingQcQuantity !== null
              ? toReturnNumber(remainingQcQuantity)
              : null;

        const effectivePosted: number | null = hasQcLines
            ? lines.reduce((sum, line) => sum + toReturnNumber(line.postedQcQuantity), 0)
            : postedQcQuantity !== undefined && postedQcQuantity !== null
              ? toReturnNumber(postedQcQuantity)
              : null;

        const effectiveQcStatus = qcSummary?.qcStatus ?? qcStatus ?? null;
        const effectiveInspectionStatus = qcSummary?.inspectionStatus ?? inspectionStatus ?? null;

        if (
            !qcSummary &&
            effectiveQcStatus === null &&
            effectiveRemaining === null &&
            effectiveInspectionStatus === null
        ) {
            return "RECEIVED";
        }

        // Đang có quá trình kiểm tra hoặc xử lý kho (lệnh pending, inspection CREATED, hoặc QC một phần)
        const isInspectionPending =
            Boolean(isPendingDisposition) ||
            effectiveInspectionStatus === "CREATED" ||
            effectiveQcStatus === "PARTIAL" ||
            (effectivePosted !== null && effectiveRemaining !== null && effectivePosted > 0 && effectiveRemaining > 0);

        if (isInspectionPending) {
            return "PROCESSING";
        }

        // Rule 2: QC chỉ được coi hoàn tất khi:
        // remainingQcQuantity == 0 AND (qcStatus == "COMPLETED" OR inspectionStatus == "POSTED")
        // Không được chỉ dựa vào inspectionStatus == POSTED hoặc qcStatus == COMPLETED nếu remaining vẫn > 0.
        const isQcFullyCompleted =
            effectiveRemaining !== null &&
            effectiveRemaining === 0 &&
            (effectiveQcStatus === "COMPLETED" || effectiveInspectionStatus === "POSTED");

        if (isQcFullyCompleted) {
            const isRefundNeeded = refundRequired !== false;

            if (isRefundNeeded) {
                // Nếu đã hoàn tiền xong
                if (refundStatus === "REFUNDED") {
                    return "COMPLETED";
                }
                // Chưa hoàn tiền hoặc đang chờ hoàn tiền
                return "PENDING_REFUND";
            }

            // Không cần hoàn tiền (refundRequired === false)
            return "COMPLETED";
        }

        // Còn sản phẩm chưa kiểm tra và chưa có tiến trình nào
        return "RECEIVED";
    }

    return "RECEIVED";
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

export function getReturnErrorMessage(error: unknown, fallbackMessage?: string) {
    const defaultMsg = fallbackMessage || "Không thể xử lý phiếu hoàn. Vui lòng thử lại.";
    const message =
        error instanceof Error && error.message
            ? error.message
            : defaultMsg;

    if (
        message.includes("Order is not eligible for return") ||
        message.includes("Order is not completed") ||
        message.includes("Order payment status is not eligible for return") ||
        message.includes("Order has already been fully returned")
    ) {
        return "Đơn hàng không đủ điều kiện hoàn.";
    }

    if (
        message.includes(
            "Return quantity exceeds remaining returnable quantity"
        ) ||
        message.includes("No remaining quantity available for return")
    ) {
        return "Số lượng hoàn vượt quá số lượng còn có thể hoàn.";
    }

    if (message.includes("Change-of-mind return requires sealed product")) {
        return "Khách đổi ý chỉ áp dụng với sản phẩm còn nguyên seal.";
    }

    // TEMPORARILY DISABLED:
    // Return requests currently have no maximum age.
    // Re-enable this mapping when the business restores the return-window policy.
    //
    // if (message.includes("Return window has expired")) {
    //     return "Đơn hàng đã quá thời hạn hoàn.";
    // }

    if (message.includes("Note is required when reason is OTHER")) {
        return "Vui lòng nhập ghi chú cho lý do khác.";
    }

    if (
        message.includes("Downstream service authentication failed") ||
        message.includes("REMOTE_SERVICE_AUTH_ERROR") ||
        message.includes("Missing internal authentication headers") ||
        message.includes("Invalid internal authentication signature")
    ) {
        return "Lỗi xác thực nội bộ giữa các service. Vui lòng liên hệ quản trị hệ thống.";
    }

    if (
        message.includes("Upstream service error") ||
        message.includes("REMOTE_SERVICE_ERROR")
    ) {
        return "Lỗi kết nối tới dịch vụ phụ thuộc (Inventory-Service). Vui lòng thử lại sau.";
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
        message.includes("Only RECEIVED order returns can be QC'd") ||
        message.includes("Only received returns can request inventory disposition")
    ) {
        return "Chỉ phiếu hoàn ở trạng thái đã nhận hàng mới được xử lý kho.";
    }

    if (
        message.includes("No remaining quantity available for inventory disposition") ||
        message.includes("Inventory disposition quantity exceeds return item quantity")
    ) {
        return "Sản phẩm này đã được xử lý kho hoặc không còn số lượng cần xử lý. Vui lòng tải lại dữ liệu.";
    }

    if (
        message.includes("QC quantity exceeds returned quantity") ||
        message.includes("Inspection quantity exceeds remaining returned quantity") ||
        message.includes("Total command quantity exceeds remaining return item quantity") ||
        message.includes("Disposition quantity exceeds remaining return quantity")
    ) {
        return "Số lượng kiểm tra vượt quá số lượng còn lại.";
    }

    if (message.includes("Duplicate return item in inventory disposition request")) {
        return "Sản phẩm hoàn bị trùng lặp trong yêu cầu xử lý.";
    }

    if (message.includes("Inventory disposition commandId was already used differently")) {
        return "Mã lệnh xử lý kho đã được sử dụng với nội dung khác.";
    }

    if (message.includes("Inventory disposition lines are required")) {
        return "Vui lòng chọn ít nhất một sản phẩm để xử lý kho.";
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
        message.includes("DAMAGED destination must be a DAMAGED location") ||
        message.includes("Destination location must be DAMAGED")
    ) {
        return "Vui lòng chọn vị trí kho hàng hỏng.";
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

    if (message.includes("DAMAGED for SEALED return item requires a reason note")) {
        return "Vui lòng nhập lý do khi đánh dấu hàng nguyên niêm phong là hàng hỏng.";
    }

    if (message.includes("WRITE_OFF for SEALED return item requires a reason note")) {
        return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
    }

    return message;
}
