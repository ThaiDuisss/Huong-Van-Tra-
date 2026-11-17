import { AlertTriangle, CheckCircle2, Clock3, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";

import { DataTable, type DataTableColumn } from "@/components/shared/list/DataTable";
import { Button } from "@/components/ui/Button";
import { Field, NativeSelect, TextInput } from "@/components/ui/Field";
import { type BadgeTone, StatusBadge } from "@/components/ui/StatusBadge";

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

const statusTone: Record<CustomerReturnStatus, BadgeTone> = {
    ACCEPTED: "success",
    CANCELLED: "neutral",
    COMPLETED: "success",
    DISPOSAL_REQUIRED: "danger",
    DRAFT: "neutral",
    PENDING_MANAGER_REVIEW: "warning",
    PENDING_QC: "violet",
    RECEIVED_IN_QUARANTINE: "sky",
    REJECTED: "danger",
    REQUESTED: "warning",
    RESTOCK_APPROVED: "info"
};

function statusIcon(status: CustomerReturnStatus) {
    if (status === "COMPLETED" || status === "RESTOCK_APPROVED") return <CheckCircle2 size={14} aria-hidden="true" />;
    if (status === "DISPOSAL_REQUIRED" || status === "REJECTED") return <AlertTriangle size={14} aria-hidden="true" />;
    return <Clock3 size={14} aria-hidden="true" />;
}

export function CustomerReturnStatusBadge({ status }: { status: CustomerReturnStatus }) {
    return (
        <StatusBadge tone={statusTone[status]}>
            <span className="inline-flex items-center gap-1.5">{statusIcon(status)}{customerReturnStatusLabels[status]}</span>
        </StatusBadge>
    );
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
            <div className="flex justify-end"><Button onClick={onReset}>Xóa bộ lọc</Button></div>
        </section>
    );
}

const returnColumns: DataTableColumn<CustomerReturnSummary>[] = [
    { className: "font-black", header: "Return code", key: "code", render: (item) => <Link className="text-[#159734] hover:underline" to={`/management/inventory/customer-returns/${item.id}`}>{item.code}</Link> },
    { header: "Order", key: "order", render: (item) => item.orderCode },
    { header: "Customer", key: "customer", render: (item) => item.customerName },
    { header: "Branch", key: "branch", render: (item) => item.branch.name },
    { header: "Reason", key: "reason", render: (item) => customerReturnReasonLabels[item.reason] },
    { header: "Status", key: "status", render: (item) => <CustomerReturnStatusBadge status={item.status} /> },
    { header: "Refund", key: "refund", render: (item) => customerReturnRefundStatusLabels[item.refundStatus] },
    { header: "Qty", key: "quantity", render: (item) => formatVietnamNumber(item.totalQuantity) },
    { className: "font-bold", header: "Value", key: "value", render: (item) => formatVnd(item.totalValue) },
    { header: "Request date", key: "requestedAt", render: (item) => formatVietnamDateTime(item.requestedAt) }
];

export function CustomerReturnTable({ returns }: { returns: CustomerReturnSummary[] }) {
    return (
        <DataTable
            caption="Danh sách phiếu trả hàng"
            columns={returnColumns}
            emptyTitle="Không có dữ liệu"
            getRowKey={(item) => item.id}
            loading={false}
            minWidth={1080}
            rows={returns}
        />
    );
}

export function CustomerReturnEmpty({ text }: { text: string }) {
    return <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center"><div className="grid max-w-sm gap-2"><RotateCcw className="mx-auto text-[#159734]" /><strong className="text-base font-black text-[#172317]">{text}</strong></div></div>;
}

function TextField({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return <Field label={label}><TextInput onChange={(event) => onChange(event.currentTarget.value)} type={type} value={value} /></Field>;
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return <Field label={label}><NativeSelect onChange={(event) => onChange(event.currentTarget.value)} value={value}>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</NativeSelect></Field>;
}
