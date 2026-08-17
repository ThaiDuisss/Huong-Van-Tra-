import { FileText, ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import {
    DisposalStatusBadge,
    QualityRoleSwitch
} from "../components/QualityComponents";
import { disposalMethodLabels } from "../constants/quality.constants";
import { qualityApi } from "../services/quality.api";
import type {
    DisposalRequest,
    QualityRole
} from "../types/quality.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

function readRole(): QualityRole {
    const stored = window.localStorage.getItem("hvt.quality.role");
    return stored && ["QC_STAFF", "WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT"].includes(stored) ? stored as QualityRole : "WAREHOUSE";
}

export function DisposalDetailPage() {
    const { disposalId } = useParams();
    const [role, setRole] = useState<QualityRole>(readRole);
    const [disposal, setDisposal] = useState<DisposalRequest | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [rejectReason, setRejectReason] = useState("");

    const load = useCallback(() => {
        if (!disposalId) return;
        let active = true;
        setLoading(true);
        setError("");
        void qualityApi.getDisposalRequest(disposalId).then((response) => {
            if (active) setDisposal(response);
        }).catch((loadError: unknown) => {
            if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải yêu cầu tiêu hủy.");
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [disposalId]);

    useEffect(() => { window.localStorage.setItem("hvt.quality.role", role); }, [role]);
    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) cleanup = load();
        });
        return () => {
            cancelled = true;
            cleanup?.();
        };
    }, [load]);

    const totals = useMemo(() => {
        const lines = disposal?.lines ?? [];
        return {
            quantity: lines.reduce((sum, line) => sum + line.quantity, 0),
            reserved: lines.reduce((sum, line) => sum + line.reservedQuantity, 0),
            value: lines.reduce((sum, line) => sum + line.estimatedValue, 0)
        };
    }, [disposal]);

    if (!disposalId) return <Navigate to="/management/inventory/disposals" replace />;

    async function run(action: () => Promise<DisposalRequest>, fallback: string) {
        setError("");
        try {
            setSubmitting(true);
            setDisposal(await action());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : fallback);
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><Link className="text-sm font-black text-[#159734]" to="/management/inventory/disposals">Quay lại danh sách</Link><h1 className="text-2xl font-black text-[#172317]">{disposal?.code ?? "Chi tiết tiêu hủy"}</h1>{disposal ? <p className="text-sm font-semibold text-[#697684]">{disposal.location.name} · {disposal.reason}</p> : null}</div><QualityRoleSwitch onChange={setRole} value={role} /></header>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{disposal ? <><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div className="grid gap-2"><DisposalStatusBadge status={disposal.status} /><p className="text-sm font-semibold text-[#536078]">Tổng {formatVietnamNumber(totals.quantity)} · Giá trị {formatVnd(totals.value)} · Reserved {formatVietnamNumber(totals.reserved)}</p>{totals.reserved > 0 ? <p className="inline-flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm font-black text-amber-800"><ShieldAlert size={16} />Không thể tiêu hủy hàng vẫn đang reserved. Cần release/cancel reservation trước.</p> : null}</div><div className="flex flex-wrap gap-2"><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting || disposal.status !== "SUBMITTED"} onClick={() => void run(() => qualityApi.approveDisposalRequest({ actorRole: role, disposalId: disposal.id }), "Không thể duyệt yêu cầu.")} type="button">Duyệt</button><button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting || disposal.status !== "SUBMITTED"} onClick={() => void run(() => qualityApi.rejectDisposalRequest({ actorRole: role, disposalId: disposal.id, reason: rejectReason }), "Không thể từ chối yêu cầu.")} type="button">Từ chối</button><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting || totals.reserved > 0 || !["APPROVED", "SCHEDULED"].includes(disposal.status)} onClick={() => { if (window.confirm("Complete disposal sẽ tạo PXK WRITE_OFF, giảm tồn vật lý và không thể chỉnh trực tiếp sau post. Tiếp tục?")) void run(() => qualityApi.completeDisposalRequest({ actorRole: role, certificate: `CERT-${disposal.code}`, completedAt: new Date().toISOString(), completedByName: "Người thực hiện demo", disposalId: disposal.id, evidenceAfterDisposal: "evidence-after-disposal.jpg", lines: disposal.lines.map((line) => ({ actualDisposedQuantity: line.quantity, lineId: line.id })), witnesses: disposal.witnesses }), "Không thể complete disposal."); }} type="button">Complete disposal</button></div></div><label className="grid max-w-xl gap-1 text-xs font-black text-[#536078]">Lý do từ chối<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setRejectReason(event.currentTarget.value)} placeholder="Bắt buộc khi từ chối" value={rejectReason} /></label></section><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><Info label="Location" value={disposal.location.name} /><Info label="Phương thức" value={disposalMethodLabels[disposal.method]} /><Info label="Ngày lên lịch" value={formatVietnamDateTime(disposal.scheduledAt)} /><Info label="Người tạo" value={disposal.requestedBy.name} /><Info label="Người chứng kiến" value={disposal.witnesses.join(", ")} /><Info label="Ngưỡng duyệt" value={totals.value > 5000000 ? "Giá trị lớn: DIRECTOR/ADMIN" : "Giá trị thường: ADMIN"} /><Info label="PXK WRITE_OFF" value={disposal.stockDocumentCode ?? "Chưa tạo"} /><Info label="Ghi chú" value={disposal.note ?? "-"} /></section><Panel title="Dòng tiêu hủy"><div className="overflow-x-auto"><table className="w-full min-w-[980px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">SKU</th><th>Batch</th><th>Quantity</th><th>Reserved</th><th>Unit cost</th><th>Value</th><th>Reason</th><th>Evidence</th></tr></thead><tbody>{disposal.lines.map((line) => <tr key={line.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black">{line.skuCode}</td><td className="border-t border-[#edf0f4]"><Link className="font-black text-[#159734]" to={`/management/inventory/quality/batches/${line.batchId}`}>{line.batchCode}</Link></td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(line.quantity)} {line.unit}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(line.reservedQuantity)}</td><td className="border-t border-[#edf0f4]">{formatVnd(line.unitCost)}</td><td className="border-t border-[#edf0f4]">{formatVnd(line.estimatedValue)}</td><td className="border-t border-[#edf0f4]">{line.reason}</td><td className="border-t border-[#edf0f4]">{line.evidence.join(", ")}</td></tr>)}</tbody></table></div></Panel><Panel title="Chứng từ">{disposal.stockDocumentCode ? <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734]" to={`/management/inventory/documents/${disposal.stockDocumentId}`}><FileText size={16} />{disposal.stockDocumentCode}</Link> : <p className="text-sm font-semibold text-[#697684]">PXK WRITE_OFF sẽ được tạo sau khi complete disposal.</p>}</Panel><Panel title="Audit">{disposal.auditEvents.map((item) => <p className="text-sm font-semibold text-[#536078]" key={item.id}>{formatVietnamDateTime(item.happenedAt)} · {item.type} · {item.actor.name} · {item.note}</p>)}</Panel></> : null}</section>;
}

function Panel({ children, title }: { children: ReactNode; title: string }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><h2 className="font-black text-[#172317]">{title}</h2>{children}</section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value || "-"}</strong></div>;
}
