import { Clock3 } from "lucide-react";

import { type BadgeTone, StatusBadge } from "@/components/ui/StatusBadge";

import {
    disposalStatusLabels
} from "../constants/quality.constants";
import { inventoryStatusMeta } from "../constants/inventory-status-meta.constants";
import type {
    DisposalStatus,
    QualityBatchStatus
} from "../types/quality.types";

export function QualityStatusBadge({ status }: { status: QualityBatchStatus }) {
    const meta = inventoryStatusMeta[status];
    const Icon = meta.icon;
    return (
        <StatusBadge icon={<Icon size={14} />} title={meta.description} tone={meta.tone}>
            {meta.label}
        </StatusBadge>
    );
}

export function DisposalStatusBadge({ status, customerReturn = false }: { status: DisposalStatus; customerReturn?: boolean }) {
    const tone: BadgeTone = status === "APPROVED" || status === "COMPLETED" ? "success" : status === "REJECTED" ? "danger" : "warning";
    const label = customerReturn && status === "PENDING_APPROVAL" ? "Chờ xác nhận xử lý"
        : customerReturn && (status === "APPROVED" || status === "COMPLETED") ? "Đã tiêu hủy"
        : disposalStatusLabels[status];
    return (
        <StatusBadge icon={<Clock3 size={14} />} tone={tone}>
            {label}
        </StatusBadge>
    );
}
