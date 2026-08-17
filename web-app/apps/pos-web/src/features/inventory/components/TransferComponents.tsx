import { AlertTriangle, CheckCircle2, Clock3, PackageOpen, Truck } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import {
    transferDiscrepancyResolutionLabels,
    transferDiscrepancyTypeLabels,
    transferRoleLabels,
    transferStatusLabels,
    transferStatusOptions
} from "../constants/transfer.constants";
import type {
    TransferDiscrepancy,
    TransferListQuery,
    TransferRole,
    TransferStatus,
    TransferSummary
} from "../types/transfer.types";
import type { InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const statusTone: Record<TransferStatus, string> = {
    APPROVED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    PARTIALLY_RECEIVED: "border-amber-200 bg-amber-50 text-amber-700",
    PICKING: "border-blue-200 bg-blue-50 text-blue-700",
    RECEIVED: "border-green-200 bg-green-50 text-green-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    REQUESTED: "border-amber-200 bg-amber-50 text-amber-700",
    RESERVED: "border-purple-200 bg-purple-50 text-purple-700",
    SHIPPED: "border-sky-200 bg-sky-50 text-sky-700"
};

function statusIcon(status: TransferStatus) {
    if (status === "RECEIVED") return <CheckCircle2 size={14} aria-hidden="true" />;
    if (status === "SHIPPED") return <Truck size={14} aria-hidden="true" />;
    if (status === "PARTIALLY_RECEIVED") return <AlertTriangle size={14} aria-hidden="true" />;
    return <Clock3 size={14} aria-hidden="true" />;
}

export function TransferStatusBadge({ status }: { status: TransferStatus }) {
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}>
            {statusIcon(status)}
            {transferStatusLabels[status]}
        </span>
    );
}

export function TransferRoleSwitch({
    onChange,
    value
}: {
    onChange: (role: TransferRole) => void;
    value: TransferRole;
}) {
    return (
        <label className="grid gap-1 text-xs font-black text-[#536078]">
            Vai trò mô phỏng
            <select
                className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-black text-[#172317]"
                onChange={(event) => onChange(event.currentTarget.value as TransferRole)}
                value={value}
            >
                {Object.entries(transferRoleLabels).map(([role, label]) => (
                    <option key={role} value={role}>
                        {label}
                    </option>
                ))}
            </select>
        </label>
    );
}

export function TransferSummaryCard({
    icon,
    label,
    value
}: {
    icon: ReactNode;
    label: string;
    value: number;
}) {
    return (
        <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
                    {icon}
                </span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">
                {formatVietnamNumber(value)}
            </strong>
        </article>
    );
}

export function TransferFilterBar({
    locations,
    onChange,
    onReset,
    query,
    users
}: {
    locations: InventoryLocationSummary[];
    onChange: (patch: Partial<TransferListQuery>) => void;
    onReset: () => void;
    query: TransferListQuery;
    users: UserSnapshot[];
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextField label="Mã chuyển kho" onChange={(code) => onChange({ code, page: 0 })} value={query.code} />
                <SelectField
                    label="Kho nguồn"
                    onChange={(sourceLocationId) => onChange({ page: 0, sourceLocationId })}
                    options={[{ label: "Tất cả", value: "ALL" }, ...locations.map((item) => ({ label: item.name, value: item.id }))]}
                    value={query.sourceLocationId}
                />
                <SelectField
                    label="Kho đích"
                    onChange={(destinationLocationId) => onChange({ destinationLocationId, page: 0 })}
                    options={[{ label: "Tất cả", value: "ALL" }, ...locations.map((item) => ({ label: item.name, value: item.id }))]}
                    value={query.destinationLocationId}
                />
                <SelectField
                    label="Trạng thái"
                    onChange={(status) => onChange({ page: 0, status: status as TransferListQuery["status"] })}
                    options={[{ label: "Tất cả", value: "ALL" }, ...transferStatusOptions.map((status) => ({ label: transferStatusLabels[status], value: status }))]}
                    value={query.status}
                />
                <SelectField
                    label="Người tạo"
                    onChange={(requestedById) => onChange({ page: 0, requestedById })}
                    options={[{ label: "Tất cả", value: "ALL" }, ...users.map((item) => ({ label: item.name, value: item.id }))]}
                    value={query.requestedById}
                />
                <TextField label="Ngày yêu cầu từ" onChange={(requestedFrom) => onChange({ page: 0, requestedFrom })} type="date" value={query.requestedFrom} />
                <TextField label="Dự kiến nhận từ" onChange={(expectedFrom) => onChange({ expectedFrom, page: 0 })} type="date" value={query.expectedFrom} />
                <SelectField
                    label="Có discrepancy"
                    onChange={(hasDiscrepancy) => onChange({ hasDiscrepancy: hasDiscrepancy as TransferListQuery["hasDiscrepancy"], page: 0 })}
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        { label: "Có", value: "YES" },
                        { label: "Không", value: "NO" }
                    ]}
                    value={query.hasDiscrepancy}
                />
            </div>
            <div className="flex justify-end">
                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">
                    Xóa bộ lọc
                </button>
            </div>
        </section>
    );
}

export function TransferTable({ transfers }: { transfers: TransferSummary[] }) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1120px] text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                    <tr>
                        <th className="px-4 py-3">Mã chuyển kho</th>
                        <th>Kho nguồn</th>
                        <th>Kho đích</th>
                        <th>Tổng SKU</th>
                        <th>Tổng số lượng</th>
                        <th>Trạng thái</th>
                        <th>Ngày yêu cầu</th>
                        <th>Dự kiến giao</th>
                        <th>Người tạo</th>
                        <th>Giá trị</th>
                    </tr>
                </thead>
                <tbody>
                    {transfers.map((transfer) => (
                        <tr className="hover:bg-[#f8fbf8]" key={transfer.id}>
                            <td className="border-t border-[#edf0f4] px-4 py-3 font-black">
                                <Link className="text-[#159734] hover:underline" to={`/management/inventory/transfers/${transfer.id}`}>
                                    {transfer.code}
                                </Link>
                            </td>
                            <td className="border-t border-[#edf0f4]">{transfer.sourceLocation.name}</td>
                            <td className="border-t border-[#edf0f4]">{transfer.destinationLocation.name}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(transfer.totalSku)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(transfer.totalQuantity)}</td>
                            <td className="border-t border-[#edf0f4]"><TransferStatusBadge status={transfer.status} /></td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamDateTime(transfer.requestedAt)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamDateTime(transfer.expectedReceiveAt)}</td>
                            <td className="border-t border-[#edf0f4]">{transfer.requestedBy.name}</td>
                            <td className="border-t border-[#edf0f4] font-bold">{formatVnd(transfer.totalValue)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function TransferDiscrepancyPanel({ discrepancies }: { discrepancies: TransferDiscrepancy[] }) {
    if (discrepancies.length === 0) {
        return (
            <div className="rounded-lg border border-dashed border-[#cfdad2] bg-white p-5 text-sm font-bold text-[#697684]">
                Chưa có chênh lệch.
            </div>
        );
    }
    return (
        <div className="grid gap-3">
            {discrepancies.map((item) => (
                <article className="grid gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3" key={item.id}>
                    <div className="flex items-center gap-2 text-sm font-black text-amber-800">
                        <PackageOpen size={16} aria-hidden="true" />
                        {transferDiscrepancyTypeLabels[item.type]} · {formatVietnamNumber(item.quantity)}
                    </div>
                    <p className="text-sm font-semibold text-amber-800">Bên chịu trách nhiệm: {item.responsibleParty}</p>
                    <p className="text-sm font-semibold text-amber-800">Trạng thái xử lý: {transferDiscrepancyResolutionLabels[item.resolutionStatus]}</p>
                    {item.evidence ? <p className="text-sm font-semibold text-amber-800">Evidence: {item.evidence}</p> : null}
                    {item.adminNote ? <p className="text-sm font-semibold text-amber-800">Ghi chú admin: {item.adminNote}</p> : null}
                </article>
            ))}
        </div>
    );
}

function TextField({
    label,
    onChange,
    type = "text",
    value
}: {
    label: string;
    onChange: (value: string) => void;
    type?: string;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} type={type} value={value} />
        </label>
    );
}

function SelectField({
    label,
    onChange,
    options,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    options: Array<{ label: string; value: string }>;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} value={value}>
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}
