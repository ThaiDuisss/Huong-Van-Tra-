import { Clock3 } from "lucide-react";
import { Link } from "react-router-dom";

import {
    disposalStatusLabels,
    disposalMethodLabels
} from "../constants/quality.constants";
import { inventoryStatusMeta } from "../constants/inventory-status-meta.constants";
import type {
    DisposalRequest,
    DisposalStatus,
    QualityBatchStatus
} from "../types/quality.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function QualityStatusBadge({ status }: { status: QualityBatchStatus }) {
    const meta = inventoryStatusMeta[status];
    const Icon = meta.icon;
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${meta.tone}`} title={meta.description}><Icon size={14} />{meta.label}</span>;
}

export function DisposalStatusBadge({ status }: { status: DisposalStatus }) {
    const tone = status === "COMPLETED" ? "border-green-200 bg-green-50 text-green-700" : status === "REJECTED" ? "border-red-200 bg-red-50 text-red-700" : "border-amber-200 bg-amber-50 text-amber-700";
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}><Clock3 size={14} />{disposalStatusLabels[status]}</span>;
}

export function DisposalTable({ disposals }: { disposals: DisposalRequest[] }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[980px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Mã</th><th>Location</th><th>Lý do</th><th>Phương thức</th><th>Trạng thái</th><th>Số lượng</th><th>Giá trị</th><th>Lịch xử lý</th><th>Người tạo</th></tr></thead><tbody>{disposals.map((item) => <tr className="hover:bg-[#f8fbf8]" key={item.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/disposals/${item.id}`}>{item.code}</Link></td><td className="border-t border-[#edf0f4]">{item.location.name}</td><td className="border-t border-[#edf0f4]">{item.reason}</td><td className="border-t border-[#edf0f4]">{disposalMethodLabels[item.method]}</td><td className="border-t border-[#edf0f4]"><DisposalStatusBadge status={item.status} /></td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(item.lines.reduce((sum, line) => sum + line.quantity, 0))}</td><td className="border-t border-[#edf0f4]">{formatVnd(item.lines.reduce((sum, line) => sum + line.estimatedValue, 0))}</td><td className="border-t border-[#edf0f4]">{formatVietnamDateTime(item.scheduledAt)}</td><td className="border-t border-[#edf0f4]">{item.requestedBy.name}</td></tr>)}</tbody></table></div>;
}
