import { ROLE_LABELS } from "../../../auth/roles";
import type {
    DisposalMethod,
    DisposalStatus,
    QualityBatchStatus,
    QualityTab
} from "../types/quality.types";

export const qualityBatchStatusLabels: Record<QualityBatchStatus, string> = {
    AVAILABLE: "Khả dụng",
    BLOCKED: "Đã block",
    DAMAGED: "Hàng hỏng",
    DISPOSED: "Đã tiêu hủy",
    EXPIRED: "Hết hạn",
    NEAR_EXPIRY: "Sắp hết hạn",
    QUARANTINE: "Cách ly kiểm định"
};

export const qualityTabLabels: Record<QualityTab, string> = {
    DAMAGED: "Hàng hỏng",
    DISPOSAL_PENDING: "Chờ tiêu hủy",
    DISPOSED: "Đã tiêu hủy",
    EXPIRED: "Đã hết hạn",
    NEAR_EXPIRY: "Sắp hết hạn",
    QUARANTINE: "Cách ly kiểm định"
};

export const disposalStatusLabels: Record<DisposalStatus, string> = {
    APPROVED: "Đã duyệt",
    PENDING_APPROVAL: "Chờ duyệt",
    CANCELLED: "Đã hủy",
    COMPLETED: "Đã tiêu hủy",
    DRAFT: "Nháp",
    REJECTED: "Từ chối",
    SCHEDULED: "Đã lên lịch",
    SUBMITTED: "Chờ duyệt"
};

export const disposalMethodLabels: Record<DisposalMethod, string> = {
    DESTROY: "Tiêu hủy",
    OTHER: "Khác",
    RETURN_TO_VENDOR: "Trả NCC"
};

/** Nhãn vai trò dùng chung của hệ thống. */
export const qualityRoleLabels: Record<string, string> = ROLE_LABELS;

export const qualityTabs: QualityTab[] = [
    "NEAR_EXPIRY",
    "EXPIRED",
    "QUARANTINE",
    "DAMAGED",
    "DISPOSAL_PENDING",
    "DISPOSED"
];

export const qualityBatchStatuses = Object.keys(qualityBatchStatusLabels) as QualityBatchStatus[];
export const disposalStatuses = Object.keys(disposalStatusLabels) as DisposalStatus[];
