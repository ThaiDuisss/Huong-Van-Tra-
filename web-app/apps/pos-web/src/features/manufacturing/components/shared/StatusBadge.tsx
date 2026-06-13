import type { BomStatus } from "../../types/bom.types";
import type { ProductionOrderStatus } from "../../types/production-order.types";

const statusCopy: Record<
    BomStatus | ProductionOrderStatus,
    {
        label: string;
        className: string;
    }
> = {
    ACTIVE: {
        label: "Đang áp dụng",
        className: "bg-emerald-50 text-emerald-700"
    },
    PENDING_APPROVAL: {
        label: "Chờ duyệt",
        className: "bg-blue-50 text-blue-700"
    },
    MATERIAL_SHORTAGE: {
        label: "Thiếu nguyên liệu",
        className: "bg-orange-50 text-orange-700"
    },
    NEEDS_UPDATE: {
        label: "Sắp cập nhật",
        className: "bg-amber-50 text-amber-700"
    },
    INACTIVE: {
        label: "Ngừng sử dụng",
        className: "bg-neutral-100 text-neutral-600"
    },
    DRAFT: {
        label: "Mới tạo",
        className: "bg-neutral-100 text-neutral-700"
    },
    IN_PROGRESS: {
        label: "Đang sản xuất",
        className: "bg-emerald-50 text-emerald-700"
    },
    WAITING_MATERIAL: {
        label: "Chờ nguyên liệu",
        className: "bg-orange-50 text-orange-700"
    },
    NEAR_COMPLETION: {
        label: "Sắp hoàn thành",
        className: "bg-emerald-50 text-emerald-700"
    },
    COMPLETED: {
        label: "Hoàn thành",
        className: "bg-neutral-100 text-neutral-700"
    },
    OVERDUE: {
        label: "Quá hạn 1 ngày",
        className: "bg-red-50 text-red-700"
    },
    CANCELLED: {
        label: "Hủy",
        className: "bg-red-50 text-red-700"
    }
};

interface StatusBadgeProps {
    status: BomStatus | ProductionOrderStatus;
}

export function StatusBadge({ status }: StatusBadgeProps) {
    const statusInfo = statusCopy[status];

    return (
        <span
            className={`inline-flex h-7 items-center rounded-lg px-2.5 text-xs font-bold ${statusInfo.className}`}
        >
            {statusInfo.label}
        </span>
    );
}
