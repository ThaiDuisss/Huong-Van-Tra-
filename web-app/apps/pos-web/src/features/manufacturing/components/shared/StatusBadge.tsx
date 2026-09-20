import type { ProductionOrderStatus } from "../../types/production-order.types";

const statusCopy: Record<
    ProductionOrderStatus,
    {
        label: string;
        className: string;
    }
> = {
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
    status: ProductionOrderStatus;
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
