import { FileText } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import {
    CustomerReturnRoleSwitch,
    CustomerReturnStatusBadge
} from "../components/CustomerReturnComponents";
import {
    customerReturnConditionLabels,
    customerReturnRefundStatusLabels,
    customerReturnResolutionLabels
} from "../constants/customer-return.constants";
import { customerReturnApi } from "../services/customerReturn.api";
import type {
    CustomerReturnCondition,
    CustomerReturnDetail,
    CustomerReturnRole
} from "../types/customer-return.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

type DetailTab = "summary" | "lines" | "manager" | "receiving" | "qc" | "refund" | "documents" | "audit";
const tabs: Array<{ id: DetailTab; label: string }> = [
    { id: "summary", label: "Tổng quan" },
    { id: "lines", label: "Return lines" },
    { id: "manager", label: "Manager review" },
    { id: "receiving", label: "Receiving" },
    { id: "qc", label: "QC" },
    { id: "refund", label: "Refund/exchange" },
    { id: "documents", label: "Chứng từ" },
    { id: "audit", label: "Audit" }
];

function readRole(): CustomerReturnRole {
    const stored = window.localStorage.getItem("hvt.customerReturn.role");
    return stored && ["SALES_STAFF", "BRANCH_MANAGER", "QC_STAFF", "ADMIN", "ACCOUNTANT", "WAREHOUSE"].includes(stored)
        ? stored as CustomerReturnRole
        : "BRANCH_MANAGER";
}

export function CustomerReturnDetailPage() {
    const { returnId } = useParams();
    const [role, setRole] = useState<CustomerReturnRole>(readRole);
    const [detail, setDetail] = useState<CustomerReturnDetail | null>(null);
    const [activeTab, setActiveTab] = useState<DetailTab>("summary");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [rejectReason, setRejectReason] = useState("");
    const [quantityReason, setQuantityReason] = useState("");

    const load = useCallback(() => {
        if (!returnId) return;
        let active = true;
        setLoading(true);
        void customerReturnApi.getCustomerReturn(returnId)
            .then((response) => { if (active) setDetail(response); })
            .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải yêu cầu trả hàng."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [returnId]);

    useEffect(() => { window.localStorage.setItem("hvt.customerReturn.role", role); }, [role]);
    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => { if (!cancelled) cleanup = load(); });
        return () => { cancelled = true; cleanup?.(); };
    }, [load]);

    if (!returnId) return <Navigate to="/management/inventory/customer-returns" replace />;

    async function runAction(action: () => Promise<CustomerReturnDetail>, fallback: string) {
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

    const canManager = ["ADMIN", "BRANCH_MANAGER"].includes(role);
    const canQc = ["ADMIN", "QC_STAFF"].includes(role);

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><Link className="text-sm font-black text-[#159734]" to="/management/inventory/customer-returns">Quay lại danh sách</Link><h1 className="text-2xl font-black text-[#172317]">{detail?.code ?? "Khách trả hàng"}</h1>{detail ? <p className="text-sm font-semibold text-[#697684]">{detail.customerName} · {detail.orderCode}</p> : null}</div><CustomerReturnRoleSwitch onChange={setRole} value={role} /></header>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{detail ? <><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div className="grid gap-2"><CustomerReturnStatusBadge status={detail.status} /><p className="text-sm font-semibold text-[#536078]">Resolution: {customerReturnResolutionLabels[detail.desiredResolution]} · Value {formatVnd(detail.totalValue)}</p></div><div className="flex flex-wrap gap-2">{detail.status === "PENDING_MANAGER_REVIEW" && canManager ? <><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.reviewCustomerReturn({ accepted: true, actorRole: role, lines: detail.lines.map((line) => ({ acceptedQuantity: line.requestedQuantity, lineId: line.id })), note: "Đủ điều kiện đổi trả.", quantityChangeReason: quantityReason, returnId: detail.id }), "Không thể accept.")} type="button">Accept</button><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setRejectReason(event.currentTarget.value)} placeholder="Lý do reject" value={rejectReason} /><button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.reviewCustomerReturn({ accepted: false, actorRole: role, lines: [], rejectReason, returnId: detail.id }), "Không thể reject.")} type="button">Reject</button></> : null}{detail.status === "ACCEPTED" && canManager ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.receiveCustomerReturn({ actorRole: role, lines: detail.lines.map((line) => ({ lineId: line.id, receivedCondition: line.condition, receivedQuantity: line.acceptedQuantity })), note: "Nhận vào CUSTOMER_RETURN_QUARANTINE.", returnId: detail.id }), "Không thể nhận hàng.")} type="button">Nhận vào quarantine</button> : null}{["RECEIVED_IN_QUARANTINE", "PENDING_QC"].includes(detail.status) && canQc ? <><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.inspectCustomerReturn({ actorRole: role, evidence: "qc-evidence.jpg", lines: detail.lines.map((line) => ({ failedQuantity: 0, lineId: line.id, passedQuantity: line.receivedQuantity, qcCondition: "SEALED" as CustomerReturnCondition })), remainingShelfLifeDays: 90, returnId: detail.id, sealStatus: "SEALED" }), "Không thể QC pass.")} type="button">QC đạt</button><button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.inspectCustomerReturn({ actorRole: role, evidence: "qc-fail.jpg", lines: detail.lines.map((line) => ({ failedQuantity: line.receivedQuantity, lineId: line.id, passedQuantity: 0, qcCondition: "DAMAGED" as CustomerReturnCondition })), remainingShelfLifeDays: 0, returnId: detail.id, sealStatus: "BROKEN" }), "Không thể QC fail.")} type="button">QC không đạt</button></> : null}{detail.status === "RESTOCK_APPROVED" && canQc ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.approveCustomerReturnRestock({ actorRole: role, returnId: detail.id }), "Không thể tạo PNK.")} type="button">Tạo PNK restock</button> : null}{detail.status === "DISPOSAL_REQUIRED" && canQc ? <button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void runAction(() => customerReturnApi.requestCustomerReturnDisposal({ actorRole: role, returnId: detail.id }), "Không thể tạo disposal.")} type="button">Tạo Disposal Request</button> : null}</div></div></section><nav className="flex gap-2 overflow-x-auto">{tabs.map((tab) => <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${activeTab === tab.id ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} type="button">{tab.label}</button>)}</nav>{activeTab === "summary" ? <Summary detail={detail} /> : null}{activeTab === "lines" ? <Lines detail={detail} /> : null}{activeTab === "manager" ? <Manager detail={detail} quantityReason={quantityReason} setQuantityReason={setQuantityReason} /> : null}{activeTab === "receiving" ? <Receiving detail={detail} /> : null}{activeTab === "qc" ? <Qc detail={detail} /> : null}{activeTab === "refund" ? <Refund detail={detail} /> : null}{activeTab === "documents" ? <Documents detail={detail} /> : null}{activeTab === "audit" ? <Audit detail={detail} /> : null}</> : null}</section>;
}

function Summary({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><Info label="Customer" value={`${detail.customerName} · ${detail.customerPhone}`} /><Info label="Order" value={detail.orderCode} /><Info label="Invoice" value={detail.invoiceCode} /><Info label="Branch" value={detail.branch.name} /><Info label="Requested" value={formatVietnamDateTime(detail.requestedAt)} /><Info label="Refund" value={customerReturnRefundStatusLabels[detail.refundStatus]} /><Info label="Payment ref" value={detail.paymentReference ?? "-"} /><Info label="Value" value={formatVnd(detail.totalValue)} /></section>;
}

function Lines({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{detail.lines.map((line) => <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-5" key={line.id}><Info label="SKU" value={`${line.skuCode} · ${line.productName}`} /><Info label="Requested" value={formatVietnamNumber(line.requestedQuantity)} /><Info label="Accepted" value={formatVietnamNumber(line.acceptedQuantity)} /><Info label="Received" value={formatVietnamNumber(line.receivedQuantity)} /><Info label="Batch" value={line.unknownBatch ? "Không xác định lô" : line.batchCode ?? "-"} /></div>)}</section>;
}

function Manager({ detail, quantityReason, setQuantityReason }: { detail: CustomerReturnDetail; quantityReason: string; setQuantityReason: (value: string) => void }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Info label="Thời hạn đổi trả" value="Mock: còn trong chính sách" /><Info label="Loại hàng" value="Mock: được phép review" /><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuantityReason(event.currentTarget.value)} placeholder="Lý do nếu chỉnh accepted quantity" value={quantityReason} />{detail.managerReview ? <Info label="Review" value={detail.managerReview.accepted ? "Accepted" : detail.managerReview.rejectReason ?? "Rejected"} /> : null}</section>;
}

function Receiving({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Info label="Quarantine" value={detail.receiving ? "CUSTOMER_RETURN_QUARANTINE" : "Chưa nhận"} /><p className="text-sm font-semibold text-[#697684]">Không cộng ngay vào available khi nhận hàng.</p>{detail.receiving ? <Info label="Received by" value={`${detail.receiving.receivedBy.name} · ${formatVietnamDateTime(detail.receiving.receivedAt)}`} /> : null}</section>;
}

function Qc({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{detail.lines.map((line) => <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-4" key={line.id}><Info label="SKU" value={line.skuCode} /><Info label="Passed" value={formatVietnamNumber(line.passedQuantity)} /><Info label="Failed" value={formatVietnamNumber(line.failedQuantity)} /><Info label="QC condition" value={line.qcCondition ? customerReturnConditionLabels[line.qcCondition] : "-"} /></div>)}{detail.qc ? <Info label="QC note" value={detail.qc.note ?? "-"} /> : null}</section>;
}

function Refund({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><Info label="Refund requested" value={detail.desiredResolution === "REFUND" ? "Có" : "Không"} /><Info label="Refund status" value={customerReturnRefundStatusLabels[detail.refundStatus]} /><Info label="Exchange order" value={detail.exchangeOrderCode ?? "-"} /><Info label="Payment reference" value={detail.paymentReference ?? "-"} /><p className="text-sm font-semibold text-[#697684] md:col-span-4">Frontend chỉ hiển thị mock integration sang Order/Payment, không tự xử lý hoàn tiền.</p></section>;
}

function Documents({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><Doc label="PNK CUSTOMER_RETURN_RECEIPT" code={detail.stockDocumentCode} id={detail.stockDocumentId} /><Doc label="Disposal Request" code={detail.disposalRequestCode} id={detail.disposalRequestId} /></section>;
}

function Audit({ detail }: { detail: CustomerReturnDetail }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{detail.auditEvents.map((event) => <p className="text-sm font-semibold text-[#536078]" key={event.id}>{formatVietnamDateTime(event.happenedAt)} · {event.actor.name} · {event.note}</p>)}</section>;
}

function Doc({ code, id, label }: { code?: string; id?: string; label: string }) {
    return <div className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] p-3"><strong className="text-sm font-black text-[#172317]">{label}</strong>{id && code ? <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734] hover:underline" to={label.startsWith("PNK") ? `/management/inventory/documents/${id}` : "#"}><FileText size={16} />{code}</Link> : <span className="text-sm font-bold text-[#697684]">Chưa tạo</span>}</div>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-white px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value || "-"}</strong></div>;
}
