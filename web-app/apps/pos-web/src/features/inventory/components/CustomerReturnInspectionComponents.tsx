import { AlertTriangle, CheckCircle2, Clock3, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";

import type {
    CustomerReturnInspection,
    CustomerReturnInspectionListQuery,
    CustomerReturnInspectionStatus
} from "../types/customer-return-inspection.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const inspectionStatusLabels: Record<CustomerReturnInspectionStatus, string> = {
    CANCELLED: "Đã hủy",
    CREATED: "Chờ xử lý",
    POSTED: "Đã xử lý"
};

const statusTone: Record<CustomerReturnInspectionStatus, string> = {
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    CREATED: "border-amber-200 bg-amber-50 text-amber-700",
    POSTED: "border-green-200 bg-green-50 text-green-700"
};

export function InspectionStatusBadge({ status }: { status: CustomerReturnInspectionStatus }) {
    const Icon = status === "POSTED" ? CheckCircle2 : status === "CANCELLED" ? AlertTriangle : Clock3;
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}><Icon aria-hidden="true" size={14} />{inspectionStatusLabels[status]}</span>;
}

export function InspectionFilterBar({ onChange, onReset, query }: { onChange: (patch: Partial<CustomerReturnInspectionListQuery>) => void; onReset: () => void; query: CustomerReturnInspectionListQuery }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="grid gap-3 md:grid-cols-3"><TextField label="Mã trả hàng" onChange={(returnCode) => onChange({ page: 0, returnCode })} value={query.returnCode} /><TextField label="Mã đơn hàng" onChange={(orderCode) => onChange({ orderCode, page: 0 })} value={query.orderCode} /><label className="grid gap-1.5 text-xs font-black text-[#536078]">Trạng thái<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange({ page: 0, status: event.currentTarget.value as CustomerReturnInspectionListQuery["status"] })} value={query.status}><option value="ALL">Tất cả</option><option value="CREATED">Chờ xử lý</option><option value="POSTED">Đã xử lý</option><option value="CANCELLED">Đã hủy</option></select></label></div><div className="flex justify-end"><button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button></div></section>;
}

export function InspectionTable({ inspections }: { inspections: CustomerReturnInspection[] }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[880px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Phiếu kiểm định</th><th>Mã trả hàng</th><th>Đơn hàng</th><th>Số dòng</th><th>Tổng số lượng</th><th>Trạng thái</th><th>Ngày tạo</th></tr></thead><tbody>{inspections.map((inspection) => <tr className="hover:bg-[#f8fbf8]" key={inspection.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/customer-returns/${inspection.id}`}>{inspection.inspectionCode}</Link></td><td className="border-t border-[#edf0f4]">{inspection.returnCode}</td><td className="border-t border-[#edf0f4]">{inspection.orderCode ?? "-"}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(inspection.lines.length)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(inspection.lines.reduce((sum, line) => sum + Number(line.quantity), 0))}</td><td className="border-t border-[#edf0f4]"><InspectionStatusBadge status={inspection.status} /></td><td className="border-t border-[#edf0f4]">{formatVietnamDateTime(inspection.createdAt)}</td></tr>)}</tbody></table></div>;
}

export function InspectionEmpty() {
    return <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center"><div className="grid max-w-sm gap-2"><RotateCcw className="mx-auto text-[#159734]" /><strong className="text-base font-black text-[#172317]">Không có phiếu kiểm định trả hàng phù hợp</strong></div></div>;
}

function TextField({ label, onChange, value }: { label: string; onChange: (value: string) => void; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} value={value} /></label>;
}
