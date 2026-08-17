import { AlertTriangle, CheckCircle2, Clock3, ClipboardCheck, FileWarning } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import {
    stocktakeRoleLabels,
    stocktakeStatusLabels,
    stocktakeStatusOptions,
    stocktakeTypeLabels,
    stocktakeTypeOptions,
    stocktakeVarianceLevelLabels
} from "../constants/stocktake.constants";
import type {
    StocktakeListQuery,
    StocktakeRole,
    StocktakeStatus,
    StocktakeSummary
} from "../types/stocktake.types";
import type { InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const tone: Record<StocktakeStatus, string> = {
    APPROVED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    COUNTED: "border-blue-200 bg-blue-50 text-blue-700",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    IN_PROGRESS: "border-amber-200 bg-amber-50 text-amber-700",
    PENDING_APPROVAL: "border-purple-200 bg-purple-50 text-purple-700",
    POSTED: "border-green-200 bg-green-50 text-green-700",
    RECONCILING: "border-sky-200 bg-sky-50 text-sky-700"
};

function icon(status: StocktakeStatus) {
    if (status === "POSTED" || status === "APPROVED") return <CheckCircle2 size={14} aria-hidden="true" />;
    if (status === "PENDING_APPROVAL") return <FileWarning size={14} aria-hidden="true" />;
    if (status === "IN_PROGRESS") return <ClipboardCheck size={14} aria-hidden="true" />;
    return <Clock3 size={14} aria-hidden="true" />;
}

export function StocktakeStatusBadge({ status }: { status: StocktakeStatus }) {
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone[status]}`}>
            {icon(status)}
            {stocktakeStatusLabels[status]}
        </span>
    );
}

export function StocktakeRoleSwitch({ onChange, value }: { onChange: (role: StocktakeRole) => void; value: StocktakeRole }) {
    return (
        <label className="grid gap-1 text-xs font-black text-[#536078]">
            Vai trò mô phỏng
            <select className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-black" onChange={(event) => onChange(event.currentTarget.value as StocktakeRole)} value={value}>
                {Object.entries(stocktakeRoleLabels).map(([role, label]) => <option key={role} value={role}>{label}</option>)}
            </select>
        </label>
    );
}

export function StocktakeSummaryCard({ icon: cardIcon, label, value }: { icon: ReactNode; label: string; value: number }) {
    return (
        <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{cardIcon}</span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">{formatVietnamNumber(value)}</strong>
        </article>
    );
}

export function StocktakeFilterBar({
    locations,
    onChange,
    onReset,
    query,
    users
}: {
    locations: InventoryLocationSummary[];
    onChange: (patch: Partial<StocktakeListQuery>) => void;
    onReset: () => void;
    query: StocktakeListQuery;
    users: UserSnapshot[];
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextField label="Mã kiểm kê" onChange={(code) => onChange({ code, page: 0 })} value={query.code} />
                <SelectField label="Location" onChange={(locationId) => onChange({ locationId, page: 0 })} options={[{ label: "Tất cả", value: "ALL" }, ...locations.map((item) => ({ label: item.name, value: item.id }))]} value={query.locationId} />
                <SelectField label="Status" onChange={(status) => onChange({ page: 0, status: status as StocktakeListQuery["status"] })} options={[{ label: "Tất cả", value: "ALL" }, ...stocktakeStatusOptions.map((status) => ({ label: stocktakeStatusLabels[status], value: status }))]} value={query.status} />
                <SelectField label="Loại kiểm kê" onChange={(type) => onChange({ page: 0, type: type as StocktakeListQuery["type"] })} options={[{ label: "Tất cả", value: "ALL" }, ...stocktakeTypeOptions.map((type) => ({ label: stocktakeTypeLabels[type], value: type }))]} value={query.type} />
                <SelectField label="Người phụ trách" onChange={(ownerId) => onChange({ ownerId, page: 0 })} options={[{ label: "Tất cả", value: "ALL" }, ...users.map((item) => ({ label: item.name, value: item.id }))]} value={query.ownerId} />
                <TextField label="Ngày bắt đầu từ" onChange={(startedFrom) => onChange({ page: 0, startedFrom })} type="date" value={query.startedFrom} />
                <TextField label="Ngày bắt đầu đến" onChange={(startedTo) => onChange({ page: 0, startedTo })} type="date" value={query.startedTo} />
                <SelectField label="Có chênh lệch" onChange={(hasVariance) => onChange({ hasVariance: hasVariance as StocktakeListQuery["hasVariance"], page: 0 })} options={[{ label: "Tất cả", value: "ALL" }, { label: "Có", value: "YES" }, { label: "Không", value: "NO" }]} value={query.hasVariance} />
            </div>
            <div className="flex justify-end">
                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button>
            </div>
        </section>
    );
}

export function StocktakeTable({ stocktakes }: { stocktakes: StocktakeSummary[] }) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1120px] text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                    <tr>
                        <th className="px-4 py-3">Mã</th>
                        <th>Location</th>
                        <th>Scope</th>
                        <th>Status</th>
                        <th>Tổng SKU</th>
                        <th>Đã đếm</th>
                        <th>Có chênh lệch</th>
                        <th>Giá trị chênh lệch</th>
                        <th>Ngày bắt đầu</th>
                        <th>Người phụ trách</th>
                    </tr>
                </thead>
                <tbody>
                    {stocktakes.map((stocktake) => (
                        <tr className="hover:bg-[#f8fbf8]" key={stocktake.id}>
                            <td className="border-t border-[#edf0f4] px-4 py-3 font-black">
                                <Link className="text-[#159734] hover:underline" to={`/management/inventory/stocktakes/${stocktake.id}`}>{stocktake.code}</Link>
                            </td>
                            <td className="border-t border-[#edf0f4]">{stocktake.location.name}</td>
                            <td className="border-t border-[#edf0f4]">{stocktake.scopeLabel}</td>
                            <td className="border-t border-[#edf0f4]"><StocktakeStatusBadge status={stocktake.status} /></td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(stocktake.totalSku)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(stocktake.countedSku)}</td>
                            <td className="border-t border-[#edf0f4]">{stocktake.hasVariance ? <span className="inline-flex items-center gap-1 text-amber-700"><AlertTriangle size={14} /> Có</span> : "Không"}</td>
                            <td className="border-t border-[#edf0f4] font-bold">{formatVnd(stocktake.varianceValue)} · {stocktakeVarianceLevelLabels[stocktake.varianceLevel]}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamDateTime(stocktake.startedAt ?? stocktake.scheduledStartAt)}</td>
                            <td className="border-t border-[#edf0f4]">{stocktake.owner.name}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function TextField({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} type={type} value={value} />
        </label>
    );
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} value={value}>
                {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
        </label>
    );
}
