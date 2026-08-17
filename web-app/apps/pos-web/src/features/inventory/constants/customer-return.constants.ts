import type {
    CustomerReturnCondition,
    CustomerReturnReason,
    CustomerReturnRefundStatus,
    CustomerReturnResolution,
    CustomerReturnRole,
    CustomerReturnStatus
} from "../types/customer-return.types";

export const customerReturnStatusLabels: Record<CustomerReturnStatus, string> = {
    ACCEPTED: "Đã chấp nhận",
    CANCELLED: "Đã hủy",
    COMPLETED: "Hoàn tất",
    DISPOSAL_REQUIRED: "Chờ tiêu hủy",
    DRAFT: "Nháp",
    PENDING_MANAGER_REVIEW: "Chờ Manager",
    PENDING_QC: "Chờ QC",
    RECEIVED_IN_QUARANTINE: "Đã nhận vào quarantine",
    REJECTED: "Từ chối",
    REQUESTED: "Đã yêu cầu",
    RESTOCK_APPROVED: "Được nhập lại kho"
};

export const customerReturnReasonLabels: Record<CustomerReturnReason, string> = {
    CHANGE_MIND: "Đổi ý",
    DAMAGED: "Hàng hỏng",
    NEAR_EXPIRY: "Gần hết hạn",
    OTHER: "Khác",
    PACKAGING_DEFECT: "Bao bì lỗi",
    QUALITY: "Chất lượng",
    WRONG_ITEM: "Giao sai"
};

export const customerReturnConditionLabels: Record<CustomerReturnCondition, string> = {
    DAMAGED: "Hỏng",
    MOULDY: "Mốc",
    NEAR_EXPIRY: "Gần hết hạn",
    OPENED: "Đã mở",
    SEALED: "Còn niêm phong",
    WET: "Ướt"
};

export const customerReturnResolutionLabels: Record<CustomerReturnResolution, string> = {
    EXCHANGE: "Đổi hàng",
    REFUND: "Hoàn tiền",
    STORE_CREDIT: "Store credit"
};

export const customerReturnRefundStatusLabels: Record<CustomerReturnRefundStatus, string> = {
    EXCHANGE_ORDER_CREATED: "Đã tạo đơn đổi hàng",
    NOT_REQUESTED: "Chưa yêu cầu",
    PROCESSING: "Đang xử lý",
    REFUNDED: "Đã hoàn tiền",
    REQUESTED: "Đã gửi yêu cầu"
};

export const customerReturnRoleLabels: Record<CustomerReturnRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    QC_STAFF: "Nhân viên QC",
    SALES_STAFF: "Nhân viên bán hàng",
    WAREHOUSE: "Nhân viên kho"
};

export const customerReturnReasons = Object.keys(customerReturnReasonLabels) as CustomerReturnReason[];
export const customerReturnConditions = Object.keys(customerReturnConditionLabels) as CustomerReturnCondition[];
export const customerReturnStatuses = Object.keys(customerReturnStatusLabels) as CustomerReturnStatus[];
