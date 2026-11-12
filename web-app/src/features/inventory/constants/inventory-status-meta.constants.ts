import {
    AlertTriangle,
    Ban,
    CheckCircle2,
    Clock3,
    PackageX,
    PauseCircle,
    ShieldAlert,
    XCircle,
    type LucideIcon
} from "lucide-react";

import type { BadgeTone } from "../../../components/ui/StatusBadge";

export interface InventoryStatusMeta {
    description: string;
    icon: LucideIcon;
    label: string;
    tone: BadgeTone;
}

export const inventoryStatusMeta = {
    ACKNOWLEDGED: { description: "Người phụ trách đã xác nhận đã xem.", icon: CheckCircle2, label: "Đã xác nhận", tone: "info" },
    ACTION_CREATED: { description: "Đã tạo action xử lý liên quan.", icon: CheckCircle2, label: "Đã tạo action", tone: "success" },
    APPROVED: { description: "Đã được duyệt để xử lý tiếp.", icon: CheckCircle2, label: "Đã duyệt", tone: "success" },
    AVAILABLE: { description: "Tồn có thể sử dụng.", icon: CheckCircle2, label: "Khả dụng", tone: "success" },
    BLOCKED: { description: "Bị khóa khỏi available.", icon: Ban, label: "Đã block", tone: "warning" },
    CANCELLED: { description: "Nghiệp vụ đã hủy.", icon: XCircle, label: "Đã hủy", tone: "neutral" },
    COMPLETED: { description: "Nghiệp vụ đã hoàn tất.", icon: CheckCircle2, label: "Hoàn tất", tone: "success" },
    DAMAGED: { description: "Hàng hỏng, không đưa vào available.", icon: PackageX, label: "Hàng hỏng", tone: "danger" },
    DRAFT: { description: "Bản nháp chưa submit.", icon: PauseCircle, label: "Nháp", tone: "neutral" },
    DISMISSED: { description: "Đã bỏ qua có lý do, không xóa cảnh báo.", icon: Ban, label: "Đã bỏ qua", tone: "neutral" },
    DISPOSED: { description: "Đã tiêu hủy qua chứng từ hợp lệ.", icon: PackageX, label: "Đã tiêu hủy", tone: "neutral" },
    EXPIRED: { description: "Hết hạn, bị block khỏi available nhưng không tự biến mất.", icon: AlertTriangle, label: "Hết hạn", tone: "danger" },
    NEAR_EXPIRY: { description: "Sắp hết hạn, cần theo dõi.", icon: AlertTriangle, label: "Sắp hết hạn", tone: "warning" },
    OPEN: { description: "Đang mở và cần xử lý.", icon: AlertTriangle, label: "Đang mở", tone: "danger" },
    POSTED: { description: "Đã post chứng từ vào tồn kho.", icon: CheckCircle2, label: "Đã post", tone: "success" },
    QUARANTINE: { description: "Hàng đang ở khu quarantine chờ QC/quyết định.", icon: ShieldAlert, label: "Quarantine", tone: "warning" },
    REJECTED: { description: "Bị từ chối và cần lý do.", icon: XCircle, label: "Từ chối", tone: "danger" },
    RESOLVED: { description: "Đã xử lý xong.", icon: CheckCircle2, label: "Đã xử lý", tone: "success" },
    SUBMITTED: { description: "Đã submit chờ duyệt.", icon: Clock3, label: "Chờ duyệt", tone: "warning" }
} satisfies Record<string, InventoryStatusMeta>;
