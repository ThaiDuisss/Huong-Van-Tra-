import { AlertTriangle, CheckCircle2, Clock3, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";

import {
    customerReturnReasonLabels,
    customerReturnReasons,
    customerReturnRefundStatusLabels,
    customerReturnStatusLabels,
    customerReturnStatuses
} from "../constants/customer-return.constants";
import type {
    CustomerReturnListQuery,
    CustomerReturnStatus,
    CustomerReturnSummary
} from "../types/customer-return.types";
import type { InventoryLocationSummary } from "../types/stock-document.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const statusTone: Record<CustomerReturnStatus, string> = {
    ACCEPTED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    COMPLETED: "border-green-200 bg-green-50 text-green-700",
    DISPOSAL_REQUIRED: "border-red-200 bg-red-50 text-red-700",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    PENDING_MANAGER_REVIEW: "border-amber-200 bg-amber-50 text-amber-700",
    PENDING_QC: "border-purple-200 bg-purple-50 text-purple-700",
    RECEIVED_IN_QUARANTINE: "border-sky-200 bg-sky-50 text-sky-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    REQUESTED: "border-amber-200 bg-amber-50 text-amber-700",
    RESTOCK_APPROVED: "border-blue-200 bg-blue-50 text-blue-700"
};

function statusIcon(status: CustomerReturnStatus) {
    if (status === "COMPLETED" || status === "RESTOCK_APPROVED") return <CheckCircle2 size={14} aria-hidden="true" />;
    if (status === "DISPOSAL_REQUIRED" || status === "REJECTED") return <AlertTriangle size={14} aria-hidden="true" />;
    return <Clock3 size={14} aria-hidden="true" />;
}

export function CustomerReturnStatusBadge({ status }: { status: CustomerReturnStatus }) {
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}>{statusIcon(status)}{customerReturnStatusLabels[status]}</span>;
}

export function CustomerReturnFilterBar({ branches, onChange, onReset, query }: { branches: InventoryLocationSummary[]; onChange: (patch: Partial<CustomerReturnListQuery>) => void; onReset: () => void; query: CustomerReturnListQuery }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextField label="Return code" onChange={(code) => onChange({ code, page: 0 })} value={query.code} />
                <TextField label="Order code" onChange={(orderCode) => onChange({ orderCode, page: 0 })} value={query.orderCode} />
                <TextField label="Customer" onChange={(customer) => onChange({ customer, page: 0 })} value={query.customer} />
                <SelectField label="Branch" onChange={(branchId) => onChange({ branchId, page: 0 })} options={[{ label: "Tất cả", value: "ALL" }, ...branches.map((branch) => ({ label: branch.name, value: branch.id }))]} value={query.branchId} />
                <SelectField label="Status" onChange={(status) => onChange({ page: 0, status: status as CustomerReturnListQuery["status"] })} options={[{ label: "Tất cả", value: "ALL" }, ...customerReturnStatuses.map((status) => ({ label: customerReturnStatusLabels[status], value: status }))]} value={query.status} />
                <SelectField label="Reason" onChange={(reason) => onChange({ page: 0, reason: reason as CustomerReturnListQuery["reason"] })} options={[{ label: "Tất cả", value: "ALL" }, ...customerReturnReasons.map((reason) => ({ label: customerReturnReasonLabels[reason], value: reason }))]} value={query.reason} />
                <TextField label="Request date từ" onChange={(requestedFrom) => onChange({ page: 0, requestedFrom })} type="date" value={query.requestedFrom} />
                <TextField label="Request date đến" onChange={(requestedTo) => onChange({ page: 0, requestedTo })} type="date" value={query.requestedTo} />
            </div>
            <div className="flex justify-end"><button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button></div>
        </section>
    );
}

export function CustomerReturnTable({ returns }: { returns: CustomerReturnSummary[] }) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1080px] text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                    <tr><th className="px-4 py-3">Return code</th><th>Order</th><th>Customer</th><th>Branch</th><th>Reason</th><th>Status</th><th>Refund</th><th>Qty</th><th>Value</th><th>Request date</th></tr>
                </thead>
                <tbody>
                    {returns.map((item) => (
                        <tr className="hover:bg-[#f8fbf8]" key={item.id}>
                            <td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/customer-returns/${item.id}`}>{item.code}</Link></td>
                            <td className="border-t border-[#edf0f4]">{item.orderCode}</td>
                            <td className="border-t border-[#edf0f4]">{item.customerName}</td>
                            <td className="border-t border-[#edf0f4]">{item.branch.name}</td>
                            <td className="border-t border-[#edf0f4]">{customerReturnReasonLabels[item.reason]}</td>
                            <td className="border-t border-[#edf0f4]"><CustomerReturnStatusBadge status={item.status} /></td>
                            <td className="border-t border-[#edf0f4]">{customerReturnRefundStatusLabels[item.refundStatus]}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(item.totalQuantity)}</td>
                            <td className="border-t border-[#edf0f4] font-bold">{formatVnd(item.totalValue)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamDateTime(item.requestedAt)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function CustomerReturnEmpty({ text }: { text: string }) {
    return <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center"><div className="grid max-w-sm gap-2"><RotateCcw className="mx-auto text-[#159734]" /><strong className="text-base font-black text-[#172317]">{text}</strong></div></div>;
}

function TextField({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} type={type} value={value} /></label>;
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} value={value}>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
