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

export interface InventoryStatusMeta {
    description: string;
    icon: LucideIcon;
    label: string;
    tone: string;
}

export const inventoryStatusMeta = {
    ACKNOWLEDGED: { description: "Người phụ trách đã xác nhận đã xem.", icon: CheckCircle2, label: "Đã xác nhận", tone: "border-blue-200 bg-blue-50 text-blue-700" },
    ACTION_CREATED: { description: "Đã tạo action xử lý liên quan.", icon: CheckCircle2, label: "Đã tạo action", tone: "border-green-200 bg-green-50 text-green-700" },
    APPROVED: { description: "Đã được duyệt để xử lý tiếp.", icon: CheckCircle2, label: "Đã duyệt", tone: "border-green-200 bg-green-50 text-green-700" },
    AVAILABLE: { description: "Tồn có thể sử dụng.", icon: CheckCircle2, label: "Khả dụng", tone: "border-green-200 bg-green-50 text-green-700" },
    BLOCKED: { description: "Bị khóa khỏi available.", icon: Ban, label: "Đã block", tone: "border-amber-200 bg-amber-50 text-amber-700" },
    CANCELLED: { description: "Nghiệp vụ đã hủy.", icon: XCircle, label: "Đã hủy", tone: "border-slate-200 bg-slate-50 text-slate-600" },
    COMPLETED: { description: "Nghiệp vụ đã hoàn tất.", icon: CheckCircle2, label: "Hoàn tất", tone: "border-green-200 bg-green-50 text-green-700" },
    DAMAGED: { description: "Hàng hỏng, không đưa vào available.", icon: PackageX, label: "Hàng hỏng", tone: "border-red-200 bg-red-50 text-red-700" },
    DRAFT: { description: "Bản nháp chưa submit.", icon: PauseCircle, label: "Nháp", tone: "border-slate-200 bg-slate-50 text-slate-600" },
    DISMISSED: { description: "Đã bỏ qua có lý do, không xóa cảnh báo.", icon: Ban, label: "Đã bỏ qua", tone: "border-slate-200 bg-slate-50 text-slate-600" },
    DISPOSED: { description: "Đã tiêu hủy qua chứng từ hợp lệ.", icon: PackageX, label: "Đã tiêu hủy", tone: "border-slate-200 bg-slate-50 text-slate-600" },
    EXPIRED: { description: "Hết hạn, bị block khỏi available nhưng không tự biến mất.", icon: AlertTriangle, label: "Hết hạn", tone: "border-red-200 bg-red-50 text-red-700" },
    NEAR_EXPIRY: { description: "Sắp hết hạn, cần theo dõi.", icon: AlertTriangle, label: "Sắp hết hạn", tone: "border-amber-200 bg-amber-50 text-amber-700" },
    OPEN: { description: "Đang mở và cần xử lý.", icon: AlertTriangle, label: "Đang mở", tone: "border-red-200 bg-red-50 text-red-700" },
    POSTED: { description: "Đã post chứng từ vào tồn kho.", icon: CheckCircle2, label: "Đã post", tone: "border-green-200 bg-green-50 text-green-700" },
    QUARANTINE: { description: "Hàng đang ở khu quarantine chờ QC/quyết định.", icon: ShieldAlert, label: "Quarantine", tone: "border-amber-200 bg-amber-50 text-amber-700" },
    REJECTED: { description: "Bị từ chối và cần lý do.", icon: XCircle, label: "Từ chối", tone: "border-red-200 bg-red-50 text-red-700" },
    RESOLVED: { description: "Đã xử lý xong.", icon: CheckCircle2, label: "Đã xử lý", tone: "border-green-200 bg-green-50 text-green-700" },
    SUBMITTED: { description: "Đã submit chờ duyệt.", icon: Clock3, label: "Chờ duyệt", tone: "border-amber-200 bg-amber-50 text-amber-700" }
} satisfies Record<string, InventoryStatusMeta>;
