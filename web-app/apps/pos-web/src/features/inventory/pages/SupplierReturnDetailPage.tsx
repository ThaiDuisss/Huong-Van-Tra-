import { FileText } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import {
    SupplierReturnStatusBadge
} from "../components/SupplierReturnComponents";
import {
    supplierReturnReasonLabels,
    supplierReturnResolutionLabels,
    supplierReturnStatusLabels
} from "../constants/supplier-return.constants";
import { supplierReturnApi } from "../services/supplierReturn.api";
import type { SupplierReturnDetail } from "../types/supplier-return.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

type DetailTab = "overview" | "lines" | "approval" | "preparing" | "shipping" | "settlement" | "documents" | "audit";
const tabs: Array<{ id: DetailTab; label: string }> = [
    { id: "overview", label: "Tổng quan" },
    { id: "lines", label: "Hàng hóa" },
    { id: "approval", label: "Approval" },
    { id: "preparing", label: "Preparing" },
    { id: "shipping", label: "Ship" },
    { id: "settlement", label: "Accounting" },
    { id: "documents", label: "Liên kết" },
    { id: "audit", label: "Audit" }
];

export function SupplierReturnDetailPage() {
    const { supplierReturnId } = useParams();
    const { roles } = useAuth();
    const [detail, setDetail] = useState<SupplierReturnDetail | null>(null);
    const [activeTab, setActiveTab] = useState<DetailTab>("overview");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [rejectReason, setRejectReason] = useState("");
    const [settlementCode, setSettlementCode] = useState("");

    const load = useCallback(() => {
        if (!supplierReturnId) return;
        let active = true;
        setLoading(true);
        void supplierReturnApi.getSupplierReturn(supplierReturnId)
            .then((response) => { if (active) setDetail(response); })
            .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải phiếu trả NCC."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [supplierReturnId]);

    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => { if (!cancelled) cleanup = load(); });
        return () => { cancelled = true; cleanup?.(); };
    }, [load]);

    if (!supplierReturnId) return <Navigate to="/management/inventory/supplier-returns" replace />;

    async function run(action: () => Promise<SupplierReturnDetail>, fallback: string) {
        setError("");
        try {
            setSubmitting(true);
            setDetail(await action());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : fallback);
        } finally {
            setSubmitting(false);
        }
    }

    const canApprove = hasAnyRole(roles, ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]);
    const canWarehouse = hasAnyRole(roles, ["ADMIN", "INVENTORY_STAFF", "INVENTORY_MANAGER", "BRANCH_MANAGER"]);
    const canAccountant = hasAnyRole(roles, ["ADMIN", "ACCOUNTANT"]);
    const readonly = detail?.status === "COMPLETED";

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><Link className="text-sm font-black text-[#159734]" to="/management/inventory/supplier-returns">Quay lại danh sách</Link><h1 className="text-2xl font-black text-[#172317]">{detail?.code ?? "Trả nhà cung cấp"}</h1>{detail ? <p className="text-sm font-semibold text-[#697684]">{detail.supplier.name} · {detail.receiptCode}</p> : null}</div></header>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{detail ? <><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div className="grid gap-2"><SupplierReturnStatusBadge status={detail.status} /><p className="text-sm font-semibold text-[#536078]">Resolution: {supplierReturnResolutionLabels[detail.resolutionRequested]} · Value {formatVnd(detail.totalValue)}</p>{readonly ? <p className="text-sm font-black text-[#536078]">COMPLETED read-only</p> : null}</div><div className="flex flex-wrap gap-2">{detail.status === "SUBMITTED" && canApprove ? <><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void run(() => supplierReturnApi.approveSupplierReturn({ supplierReturnId: detail.id }), "Không thể approve.")} type="button">Approve</button><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setRejectReason(event.currentTarget.value)} placeholder="Reject reason" value={rejectReason} /><button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void run(() => supplierReturnApi.rejectSupplierReturn({ reason: rejectReason, supplierReturnId: detail.id }), "Không thể reject.")} type="button">Reject</button></> : null}{detail.status === "APPROVED" && canWarehouse ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void run(() => supplierReturnApi.prepareSupplierReturn({ lines: detail.lines.map((line) => ({ lineId: line.id, preparedQuantity: line.returnQuantity })), packageCount: 1, supplierReturnId: detail.id }), "Không thể prepare.")} type="button">Prepare staging</button> : null}{detail.status === "PREPARING" && canWarehouse ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void run(() => supplierReturnApi.shipSupplierReturn({ carrier: "Giao nhận nội bộ", shippedAt: new Date().toISOString(), shippingAttachment: "bien-ban-ban-giao.pdf", supplierReceiver: "Đại diện NCC", supplierReturnId: detail.id, trackingCode: `TRK-${detail.code}` }), "Không thể ship.")} type="button">Ship và tạo PXK</button> : null}{["CREDIT_PENDING", "REPLACEMENT_PENDING"].includes(detail.status) && canAccountant ? <><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setSettlementCode(event.currentTarget.value)} placeholder="Credit note / replacement receipt" value={settlementCode} /><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void run(() => supplierReturnApi.updateSupplierReturnSettlement({ creditAmount: detail.resolutionRequested === "CREDIT_NOTE" ? detail.totalValue : undefined, creditNoteCode: detail.resolutionRequested === "CREDIT_NOTE" ? settlementCode : undefined, creditNoteDate: detail.resolutionRequested === "CREDIT_NOTE" ? new Date().toISOString() : undefined, expectedReplacementDate: detail.resolutionRequested === "REPLACEMENT" ? "2026-08-08T09:00:00.000Z" : undefined, replacementReceiptCode: detail.resolutionRequested === "REPLACEMENT" ? settlementCode : undefined, settlementAttachment: "settlement.pdf", settlementNote: "Cập nhật settlement mock.", supplierReturnId: detail.id }), "Không thể cập nhật settlement.")} type="button">Cập nhật settlement</button><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void run(() => supplierReturnApi.completeSupplierReturn({ supplierReturnId: detail.id }), "Không thể complete.")} type="button">Complete</button></> : null}</div></div></section><nav className="flex gap-2 overflow-x-auto">{tabs.map((tab) => <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${activeTab === tab.id ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} type="button">{tab.label}</button>)}</nav>{activeTab === "overview" ? <Overview detail={detail} /> : null}{activeTab === "lines" ? <Lines detail={detail} /> : null}{activeTab === "approval" ? <Approval detail={detail} /> : null}{activeTab === "preparing" ? <Preparing detail={detail} /> : null}{activeTab === "shipping" ? <Shipping detail={detail} /> : null}{activeTab === "settlement" ? <Settlement detail={detail} /> : null}{activeTab === "documents" ? <Documents detail={detail} /> : null}{activeTab === "audit" ? <Audit detail={detail} /> : null}</> : null}</section>;
}

function Overview({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><Info label="Status" value={supplierReturnStatusLabels[detail.status]} /><Info label="Supplier" value={detail.supplier.name} /><Info label="Receipt gốc" value={detail.receiptCode} /><Info label="Invoice gốc" value={detail.supplierInvoiceCode} /><Info label="Reason" value={`${detail.reason} ${supplierReturnReasonLabels[detail.reason]}`} /><Info label="Pickup" value={detail.pickupMethod} /><Info label="Total qty" value={formatVietnamNumber(detail.totalQuantity)} /><Info label="Value" value={formatVnd(detail.totalValue)} /></section>;
}

function Lines({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{detail.lines.map((line) => <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-5" key={line.id}><Info label="SKU" value={`${line.skuCode} · ${line.skuName}`} /><Info label="Batch" value={line.batchCode} /><Info label="Returnable" value={formatVietnamNumber(line.returnableQuantity)} /><Info label="Return qty" value={formatVietnamNumber(line.returnQuantity)} /><Info label="Value" value={formatVnd(line.estimatedValue)} /><Info label="QC result" value={line.qcResult} /><Info label="Reason" value={supplierReturnReasonLabels[line.reason]} /><Info label="Evidence" value={line.evidence.join(", ")} /><Info label="Available" value={formatVietnamNumber(line.currentAvailableQuantity)} /><Info label="Quarantine" value={formatVietnamNumber(line.currentQuarantineQuantity)} /></div>)}</section>;
}

function Approval({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Info label="Tổng giá trị" value={formatVnd(detail.totalValue)} /><Info label="Batch" value={detail.lines.map((line) => line.batchCode).join(", ")} /><Info label="QC result" value={detail.lines.map((line) => line.qcResult).join(", ")} /><Info label="Ảnh" value={detail.lines.flatMap((line) => line.evidence).join(", ")} />{detail.rejectedReason ? <Info label="Reject reason" value={detail.rejectedReason} /> : null}{detail.approvedBy ? <Info label="Approved by" value={`${detail.approvedBy.name} · ${detail.approvedAt ? formatVietnamDateTime(detail.approvedAt) : ""}`} /> : null}</section>;
}

function Preparing({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Info label="Package count" value={detail.packageCount ? formatVietnamNumber(detail.packageCount) : "-"} /><p className="text-sm font-semibold text-[#697684]">Khi prepare, hàng chuyển sang RETURN_TO_SUPPLIER staging và batch không tiếp tục được reserve.</p>{detail.lines.map((line) => <Info key={line.id} label={line.batchCode} value={`Prepared ${formatVietnamNumber(line.preparedQuantity)} / ${formatVietnamNumber(line.returnQuantity)}`} />)}</section>;
}

function Shipping({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3"><Info label="Carrier" value={detail.carrier ?? "-"} /><Info label="Tracking" value={detail.trackingCode ?? "-"} /><Info label="Ship date" value={detail.shippedAt ? formatVietnamDateTime(detail.shippedAt) : "-"} /><Info label="Supplier receiver" value={detail.supplierReceiver ?? "-"} /><Info label="Attachment" value={detail.shippingAttachment ?? "-"} /><Info label="PXK" value={detail.stockDocumentCode ?? "-"} /></section>;
}

function Settlement({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3"><Info label="Credit note" value={detail.creditNoteCode ?? "-"} /><Info label="Credit amount" value={detail.creditAmount ? formatVnd(detail.creditAmount) : "-"} /><Info label="Credit date" value={detail.creditNoteDate ? formatVietnamDateTime(detail.creditNoteDate) : "-"} /><Info label="Replacement receipt" value={detail.replacementReceiptCode ?? "-"} /><Info label="Expected replacement" value={detail.expectedReplacementDate ? formatVietnamDateTime(detail.expectedReplacementDate) : "-"} /><Info label="Attachment" value={detail.settlementAttachment ?? "-"} /><p className="text-sm font-semibold text-[#697684] md:col-span-3">ACCOUNTANT cập nhật credit note hoặc hàng thay thế, frontend chỉ lưu mock settlement.</p></section>;
}

function Documents({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Doc label="Receipt gốc" code={detail.receiptCode} to={`/management/inventory/receipts/${detail.receiptId}`} /><Doc label="PXK SUPPLIER_RETURN" code={detail.stockDocumentCode} to={detail.stockDocumentId ? `/management/inventory/documents/${detail.stockDocumentId}` : undefined} /><Doc label="Credit note" code={detail.creditNoteCode} /><Doc label="Replacement receipt" code={detail.replacementReceiptCode} to={detail.replacementReceiptCode ? "/management/inventory/receipts" : undefined} /></section>;
}

function Audit({ detail }: { detail: SupplierReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{detail.auditEvents.map((event) => <p className="text-sm font-semibold text-[#536078]" key={event.id}>{formatVietnamDateTime(event.happenedAt)} · {event.actor.name} · {event.note}</p>)}</section>;
}

function Doc({ code, label, to }: { code?: string; label: string; to?: string }) {
    return <div className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] p-3"><strong className="text-sm font-black text-[#172317]">{label}</strong>{code ? to ? <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734] hover:underline" to={to}><FileText size={16} />{code}</Link> : <span className="text-sm font-black text-[#536078]">{code}</span> : <span className="text-sm font-bold text-[#697684]">Chưa tạo</span>}</div>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-white px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value || "-"}</strong></div>;
}
