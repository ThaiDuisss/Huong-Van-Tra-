import { AlertTriangle, CheckCircle2, Clock3 } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import {
    supplierReturnReasonLabels,
    supplierReturnReasons,
    supplierReturnResolutionLabels,
    supplierReturnStatusLabels,
    supplierReturnStatuses
} from "../constants/supplier-return.constants";
import type {
    SupplierReturnListQuery,
    SupplierReturnOptions,
    SupplierReturnStatus,
    SupplierReturnSummary
} from "../types/supplier-return.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

const tone: Record<SupplierReturnStatus, string> = {
    APPROVED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    COMPLETED: "border-green-200 bg-green-50 text-green-700",
    CREDIT_PENDING: "border-amber-200 bg-amber-50 text-amber-700",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    PREPARING: "border-blue-200 bg-blue-50 text-blue-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    REPLACEMENT_PENDING: "border-purple-200 bg-purple-50 text-purple-700",
    SHIPPED: "border-sky-200 bg-sky-50 text-sky-700",
    SUBMITTED: "border-amber-200 bg-amber-50 text-amber-700"
};

export function SupplierReturnStatusBadge({ status }: { status: SupplierReturnStatus }) {
    const icon = status === "COMPLETED" ? <CheckCircle2 size={14} /> : status === "REJECTED" ? <AlertTriangle size={14} /> : <Clock3 size={14} />;
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone[status]}`}>{icon}{supplierReturnStatusLabels[status]}</span>;
}

export function SupplierReturnSummaryCard({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
    return <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex items-center gap-2 text-sm font-black text-[#315f3c]"><span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{icon}</span>{label}</div><strong className="text-2xl font-black text-[#172317]">{formatVietnamNumber(value)}</strong></article>;
}

export function SupplierReturnFilterBar({ onChange, onReset, options, query }: { onChange: (patch: Partial<SupplierReturnListQuery>) => void; onReset: () => void; options: SupplierReturnOptions; query: SupplierReturnListQuery }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4"><Text label="Return code" onChange={(code) => onChange({ code, page: 0 })} value={query.code} /><Select label="Supplier" onChange={(supplierId) => onChange({ page: 0, supplierId })} options={[{ label: "Tất cả", value: "ALL" }, ...options.suppliers.map((item) => ({ label: item.name, value: item.id }))]} value={query.supplierId} /><Text label="Receipt code" onChange={(receiptCode) => onChange({ page: 0, receiptCode })} value={query.receiptCode} /><Text label="Batch" onChange={(batchCode) => onChange({ batchCode, page: 0 })} value={query.batchCode} /><Select label="Status" onChange={(status) => onChange({ page: 0, status: status as SupplierReturnListQuery["status"] })} options={[{ label: "Tất cả", value: "ALL" }, ...supplierReturnStatuses.map((status) => ({ label: supplierReturnStatusLabels[status], value: status }))]} value={query.status} /><Select label="Reason" onChange={(reason) => onChange({ page: 0, reason: reason as SupplierReturnListQuery["reason"] })} options={[{ label: "Tất cả", value: "ALL" }, ...supplierReturnReasons.map((reason) => ({ label: supplierReturnReasonLabels[reason], value: reason }))]} value={query.reason} /><Text label="Date từ" onChange={(createdFrom) => onChange({ createdFrom, page: 0 })} type="date" value={query.createdFrom} /><Text label="Date đến" onChange={(createdTo) => onChange({ createdTo, page: 0 })} type="date" value={query.createdTo} /></div><div className="flex justify-end"><button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button></div></section>;
}

export function SupplierReturnTable({ returns }: { returns: SupplierReturnSummary[] }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[1100px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Mã trả hàng</th><th>Supplier</th><th>Receipt gốc</th><th>Tổng SKU</th><th>Tổng quantity</th><th>Giá trị</th><th>Status</th><th>Ngày tạo</th><th>Credit/replacement</th></tr></thead><tbody>{returns.map((item) => <tr className="hover:bg-[#f8fbf8]" key={item.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/supplier-returns/${item.id}`}>{item.code}</Link></td><td className="border-t border-[#edf0f4]">{item.supplier.name}</td><td className="border-t border-[#edf0f4]">{item.receiptCode}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(item.totalSku)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(item.totalQuantity)}</td><td className="border-t border-[#edf0f4] font-bold">{formatVnd(item.totalValue)}</td><td className="border-t border-[#edf0f4]"><SupplierReturnStatusBadge status={item.status} /></td><td className="border-t border-[#edf0f4]">{formatVietnamDateTime(item.createdAt)}</td><td className="border-t border-[#edf0f4]">{item.creditNoteCode ?? item.replacementReceiptCode ?? supplierReturnResolutionLabels[item.resolutionRequested]}</td></tr>)}</tbody></table></div>;
}

function Text({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} type={type} value={value} /></label>;
}

function Select({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} value={value}>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
