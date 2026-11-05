import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type {
    CustomerReturnInspectionStatus,
    CustomerReturnDisposition,
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
    CREATED: "Chờ nhận hàng",
    RECEIVED: "Chờ kiểm hàng",
    COMPLETED: "Hoàn thành",
    CANCELLED: "Đã hủy"
};

export type { ReturnDisplayStatus };

export const RETURN_DISPLAY_STATUS_LABELS: Record<ReturnDisplayStatus, string> = {
    CREATED: "Chờ nhận hàng",
    RECEIVED: "Chờ xử lý kho",
    PROCESSING: "Đang xử lý kho",
    PROCESSING_FAILED: "Xử lý kho thất bại",
    PENDING_REFUND: "Chờ hoàn tiền",
    COMPLETED: "Hoàn thành",
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
    QUARANTINE: "Cách ly (lịch sử)",
    WRITE_OFF: "Tiêu hủy"
};

export const CUSTOMER_RETURN_QC_RESULT_EFFECTS: Record<
    CustomerReturnDisposition,
    string
> = {
    RESTOCK: "Sản phẩm sẽ được nhập lại kho bán được.",
    DAMAGED: "Sản phẩm sẽ được đưa vào khu hàng hỏng và không thể bán.",
    WRITE_OFF: "Sản phẩm sẽ bị tiêu hủy và không tăng tồn kho."
};

export const CUSTOMER_RETURN_QC_RESULTS: CustomerReturnDisposition[] = [
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
    displayStatus?: ReturnDisplayStatus | string | null;
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
    displayStatus,
    status
}: ResolveReturnDisplayStatusParams): ReturnDisplayStatus {
    if (displayStatus && displayStatus in RETURN_DISPLAY_STATUS_LABELS) {
        return displayStatus as ReturnDisplayStatus;
    }

    if (status === "CREATED") {
        return "CREATED";
    }
    if (status === "CANCELLED") {
        return "CANCELLED";
    }
    if (status === "COMPLETED") {
        return "COMPLETED";
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
    actualCondition?: ReturnCondition | null
) {
    if (!actualCondition) {
        return [];
    }
    if (actualCondition === "SEALED") {
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

    if (message.includes("requires reconciliation") || message.includes("reconciliation required")) {
        return "Yêu cầu xử lý kho cần được đối soát trước khi tiếp tục. Vui lòng liên hệ quản lý.";
    }
    if (message.includes("authorization is unavailable")) {
        return "Chưa xác minh được yêu cầu xử lý kho. Vui lòng làm mới và thử lại sau.";
    }

    if (message.includes("Order not found")) {
        return "Không tìm thấy đơn hàng. Vui lòng chọn lại đơn hàng.";
    }
    if (message.includes("Order is not completed")) {
        return "Đơn hàng chưa hoàn thành.";
    }
    if (message.includes("Order payment status is not eligible for return")) {
        return "Thanh toán phải ở trạng thái đã thanh toán hoặc đã hoàn tiền một phần.";
    }
    if (message.includes("Order has already been fully returned")) {
        return "Đơn hàng đã được hoàn toàn bộ.";
    }
    if (message.includes("Order is not eligible for return")) {
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
        message.includes("QUARANTINE is no longer supported for new customer returns")
    ) {
        return "Vui lòng chọn Hàng hỏng cho hàng trả không bán được.";
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

    if (message.includes("RESTOCK is not allowed for QC actual condition")) {
        return "Không được nhập lại kho với tình trạng thực tế này. QC phải xác nhận hàng còn nguyên seal.";
    }

    if (message.includes("QC actual condition is required")) {
        return "Vui lòng chọn tình trạng thực tế trước khi xác nhận kiểm định.";
    }

    if (message.includes("Posted inspection lines cannot be changed")) {
        return "Không thể sửa kết quả kiểm định của dòng đã hạch toán.";
    }

    if (message.includes("DAMAGED for SEALED return item requires a reason note")) {
        return "Vui lòng nhập lý do khi đánh dấu hàng nguyên niêm phong là hàng hỏng.";
    }

    if (message.includes("WRITE_OFF for SEALED return item requires a reason note")) {
        return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
    }

    return message;
}

export interface CustomerReturnQuantitySource {
    itemCount?: number | string | null;
    requiredQuantity?: number | string | null;
    processedQuantity?: number | string | null;
    remainingQuantity?: number | string | null;
    remainingQcQuantity?: number | string | null;
    postedQcQuantity?: number | string | null;
    physicalDone?: boolean | null;
    qcStatus?: string | null;
    items?: Array<{ quantity?: number | string | null }> | null;
}

export interface CustomerReturnQuantitiesResult {
    required: number;
    processed: number;
    remaining: number;
    source: "INVENTORY" | "ORDER_FALLBACK" | "UNAVAILABLE";
    hasDiscrepancy: boolean;
    orderProcessedQuantity?: number;
    inventoryPostedQuantity?: number;
}

export function resolveCustomerReturnQuantities(
    data?: CustomerReturnQuantitySource | null
): CustomerReturnQuantitiesResult {
    if (!data) {
        return {
            required: 0,
            processed: 0,
            remaining: 0,
            source: "UNAVAILABLE",
            hasDiscrepancy: false
        };
    }

    const required = data.requiredQuantity != null
        ? toReturnNumber(data.requiredQuantity)
        : (data.items ?? []).reduce((sum, item) => sum + toReturnNumber(item.quantity), 0);

    const hasInventoryQuantities = data.postedQcQuantity != null || data.remainingQcQuantity != null;
    const hasOrderProcessed = data.processedQuantity != null;

    let processed: number;
    let remaining: number;
    let source: "INVENTORY" | "ORDER_FALLBACK" | "UNAVAILABLE";

    if (hasInventoryQuantities) {
        // Authoritative QC source: Inventory-Service
        source = "INVENTORY";
        processed = toReturnNumber(data.postedQcQuantity);
        if (data.remainingQcQuantity != null) {
            remaining = toReturnNumber(data.remainingQcQuantity);
        } else if (data.qcStatus === "COMPLETED") {
            remaining = 0;
        } else if (data.remainingQuantity != null) {
            remaining = toReturnNumber(data.remainingQuantity);
        } else {
            remaining = Math.max(0, required - processed);
        }
    } else if (hasOrderProcessed) {
        // Fallback: Order-Service recorded progress
        source = "ORDER_FALLBACK";
        processed = toReturnNumber(data.processedQuantity);
        if (data.remainingQuantity != null) {
            remaining = toReturnNumber(data.remainingQuantity);
        } else {
            remaining = Math.max(0, required - processed);
        }
    } else {
        source = "UNAVAILABLE";
        processed = 0;
        remaining = data.remainingQuantity != null ? toReturnNumber(data.remainingQuantity) : required;
    }

    // Detect discrepancy between Order-Service recorded processing and Inventory-Service QC
    let hasDiscrepancy = false;
    let orderProcessedQuantity: number | undefined;
    let inventoryPostedQuantity: number | undefined;

    if (hasOrderProcessed && data.postedQcQuantity != null) {
        orderProcessedQuantity = toReturnNumber(data.processedQuantity);
        inventoryPostedQuantity = toReturnNumber(data.postedQcQuantity);
        if (orderProcessedQuantity !== inventoryPostedQuantity) {
            hasDiscrepancy = true;
        }
    }

    return {
        required,
        processed,
        remaining,
        source,
        hasDiscrepancy,
        orderProcessedQuantity,
        inventoryPostedQuantity
    };
}

export function canInspectCustomerReturn(data?: (CustomerReturnQuantitySource & {
    status?: string | null;
    displayStatus?: string | null;
    inventoryProcessingStatus?: string | null;
    physicalDone?: boolean | null;
    isPendingDisposition?: boolean | null;
    hasFailedDisposition?: boolean | null;
    qcStatus?: string | null;
}) | null): boolean {
    if (!data) return false;
    if (data.status !== "RECEIVED") return false;
    if (data.physicalDone) return false;
    if (data.isPendingDisposition) return false;
    if (data.hasFailedDisposition || data.inventoryProcessingStatus === "FAILED" || data.displayStatus === "PROCESSING_FAILED") return false;
    if (data.qcStatus === "COMPLETED") return false;
    if (data.remainingQuantity != null && toReturnNumber(data.remainingQuantity) <= 0) return false;
    if (data.remainingQcQuantity != null && toReturnNumber(data.remainingQcQuantity) <= 0) return false;
    // PROCESSING also describes a partially posted return. Explicitly confirmed
    // absence of a pending command permits the next partial QC action.
    if (data.isPendingDisposition == null && (data.inventoryProcessingStatus === "PROCESSING" || data.displayStatus === "PROCESSING")) return false;

    const { remaining, required } = resolveCustomerReturnQuantities(data);
    return required > 0 && remaining > 0;
}

export interface CustomerReturnUiStatusSource extends CustomerReturnQuantitySource {
    status?: string | null;
    displayStatus?: string | null;
    isPendingDisposition?: boolean | null;
    hasFailedDisposition?: boolean | null;
    qcStatus?: string | null;
    physicalDone?: boolean | null;
}

export function getCustomerReturnUiStatus(data?: CustomerReturnUiStatusSource | null): {
    label: string;
    status: string;
} {
    if (!data) {
        return { label: "--", status: "DRAFT" };
    }

    if (data.status === "CREATED") {
        return { label: "Chờ nhận hàng", status: "PENDING" };
    }
    if (data.displayStatus === "CANCELLED" || data.status === "CANCELLED") {
        return { label: "Đã hủy", status: "CANCELLED" };
    }
    if (data.displayStatus === "PROCESSING_FAILED" || data.hasFailedDisposition) {
        return { label: "Xử lý kho thất bại", status: "FAILED" };
    }

    const isQcCompleted = data.qcStatus === "COMPLETED";
    const isPhysicalDone = Boolean(data.physicalDone);

    if (data.isPendingDisposition && !isPhysicalDone && !isQcCompleted) {
        return { label: "Đang xử lý", status: "PROCESSING" };
    }
    if (data.displayStatus === "COMPLETED" || data.status === "COMPLETED") {
        return { label: "Hoàn thành", status: "COMPLETED" };
    }
    if (data.displayStatus === "PENDING_REFUND") {
        return { label: "Chờ hoàn tiền", status: "PENDING" };
    }

    if (data.status === "RECEIVED") {
        if (isQcCompleted || isPhysicalDone) {
            return { label: "Đã kiểm hàng", status: "QC_COMPLETED" };
        }
        if (data.qcStatus === "PARTIAL") {
            return { label: "Đang kiểm hàng", status: "PROCESSING" };
        }
        const { processed, remaining } = resolveCustomerReturnQuantities(data);
        if (processed > 0 && remaining > 0) {
            return { label: "Đang kiểm hàng", status: "PROCESSING" };
        }
        return { label: "Chờ kiểm hàng", status: "PENDING" };
    }

    return { label: data.status ?? "--", status: data.status ?? "DRAFT" };
}

export function getCustomerReturnQcProgressStatus(data?: (CustomerReturnQuantitySource & {
    status?: string | null;
    qcStatus?: string | null;
    physicalDone?: boolean | null;
}) | null): { label: string; status: string } {
    if (!data || data.status === "CREATED") {
        return { label: "Chưa nhận hàng", status: "PENDING" };
    }

    // 1. Authoritative Inventory QC status takes precedence over Order physicalDone
    if (data.qcStatus === "COMPLETED") {
        return { label: "Đã kiểm hàng", status: "QC_COMPLETED" };
    }
    if (data.qcStatus === "PARTIAL") {
        return { label: "Đang kiểm hàng", status: "PROCESSING" };
    }
    if (data.qcStatus === "PENDING") {
        return { label: "Chờ kiểm hàng", status: "PENDING" };
    }

    // 2. Fallback when Inventory qcStatus is not provided
    const { required, processed, remaining } = resolveCustomerReturnQuantities(data);
    if (required > 0 && remaining === 0 && Boolean(data.physicalDone)) {
        return { label: "Đã kiểm hàng", status: "QC_COMPLETED" };
    }
    if (processed > 0 && remaining > 0) {
        return { label: "Đang kiểm hàng", status: "PROCESSING" };
    }

    return { label: "Chờ kiểm hàng", status: "PENDING" };
}

export const REFUND_BLOCK_REASON_LABELS: Record<string, string> = {
    RETURN_NOT_RECEIVED: "Chưa nhận hàng trả lại",
    PHYSICAL_DISPOSITION_NOT_COMPLETED: "Chưa hoàn tất kiểm hàng/nhập kho",
    REFUND_NOT_REQUIRED: "Phiếu không yêu cầu hoàn tiền",
    ALREADY_REFUNDED: "Đã hoàn tiền đầy đủ",
    REFUND_FAILED: "Lần hoàn tiền trước đó thất bại",
    PAYMENT_SERVICE_UNAVAILABLE: "Dịch vụ thanh toán tạm thời gián đoạn",
    PAYMENT_NOT_FOUND: "Không tìm thấy giao dịch thanh toán",
    PAYMENT_NOT_REFUNDABLE: "Thanh toán chưa ở trạng thái hợp lệ để hoàn",
    UNSUPPORTED_PAYMENT_METHOD: "Phương thức thanh toán chưa hỗ trợ hoàn tự động",
    NO_REFUND_AMOUNT: "Số tiền hoàn bằng 0",
    REFUND_AMOUNT_EXCEEDS_REMAINING: "Số tiền hoàn vượt quá số tiền còn lại của thanh toán",
};

export function getBlockReasonLabel(reason?: string | null) {
    return reason ? (REFUND_BLOCK_REASON_LABELS[reason] ?? reason) : "--";
}
