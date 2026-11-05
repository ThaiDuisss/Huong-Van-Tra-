import { Clock3 } from "lucide-react";

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
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${meta.tone}`} title={meta.description}><Icon size={14} />{meta.label}</span>;
}

export function DisposalStatusBadge({ status, customerReturn = false }: { status: DisposalStatus; customerReturn?: boolean }) {
    const tone = status === "APPROVED" || status === "COMPLETED" ? "border-green-200 bg-green-50 text-green-700" : status === "REJECTED" ? "border-red-200 bg-red-50 text-red-700" : "border-amber-200 bg-amber-50 text-amber-700";
    const label = customerReturn && status === "PENDING_APPROVAL" ? "Chờ xác nhận xử lý"
        : customerReturn && (status === "APPROVED" || status === "COMPLETED") ? "Đã tiêu hủy"
        : disposalStatusLabels[status];
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}><Clock3 size={14} />{label}</span>;
}

