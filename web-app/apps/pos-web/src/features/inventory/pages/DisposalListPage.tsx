import { AlertTriangle, CheckCircle2, PackageX, Plus } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
    DisposalTable,
    QualityRoleSwitch
} from "../components/QualityComponents";
import {
    disposalMethodLabels,
    disposalStatusLabels,
    disposalStatuses
} from "../constants/quality.constants";
import { qualityApi } from "../services/quality.api";
import type {
    DisposalCreateInput,
    DisposalListQuery,
    DisposalListResult,
    QualityBatchSummary,
    QualityRole
} from "../types/quality.types";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

function readRole(): QualityRole {
    const stored = window.localStorage.getItem("hvt.quality.role");
    return stored && ["QC_STAFF", "WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT"].includes(stored) ? stored as QualityRole : "WAREHOUSE";
}

const initialQuery: DisposalListQuery = {
    code: "",
    locationId: "ALL",
    page: 0,
    reason: "",
    size: 10,
    status: "ALL"
};

const initialForm = {
    batchId: "",
    evidence: "quality-evidence.jpg",
    method: "DESTROY" as DisposalCreateInput["method"],
    note: "",
    quantity: 1,
    reason: "Hàng hết hạn/hỏng cần tiêu hủy",
    scheduledAt: "2026-08-05T09:00",
    witnesses: "QC, Kho"
};

export function DisposalListPage() {
    const navigate = useNavigate();
    const [role, setRole] = useState<QualityRole>(readRole);
    const [query, setQuery] = useState<DisposalListQuery>(initialQuery);
    const [result, setResult] = useState<DisposalListResult | null>(null);
    const [candidateBatches, setCandidateBatches] = useState<QualityBatchSummary[]>([]);
    const [form, setForm] = useState(initialForm);
    const [showCreate, setShowCreate] = useState(false);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => { window.localStorage.setItem("hvt.quality.role", role); }, [role]);
    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void qualityApi.listDisposalRequests(query).then((response) => {
                if (active) setResult(response);
            }).catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách tiêu hủy.");
            }).finally(() => {
                if (active) setLoading(false);
            });
        });
        return () => { active = false; };
    }, [query]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            void Promise.all([
                qualityApi.listQualityBatches({ batchCode: "", expiryFrom: "", expiryTo: "", locationId: "ALL", minValue: 0, productType: "ALL", quarantineDays: 0, reason: "", sku: "", status: "ALL", supplier: "", tab: "EXPIRED" }),
                qualityApi.listQualityBatches({ batchCode: "", expiryFrom: "", expiryTo: "", locationId: "ALL", minValue: 0, productType: "ALL", quarantineDays: 0, reason: "", sku: "", status: "ALL", supplier: "", tab: "DAMAGED" })
            ]).then(([expired, damaged]) => {
                if (!active) return;
                const byId = new Map<string, QualityBatchSummary>();
                [...expired.content, ...damaged.content].forEach((batch) => byId.set(batch.id, batch));
                const candidates = [...byId.values()].filter((batch) => batch.blockedQuantity + batch.damagedQuantity > 0);
                setCandidateBatches(candidates);
                setForm((current) => current.batchId ? current : { ...current, batchId: candidates[0]?.id ?? "" });
            });
        });
        return () => { active = false; };
    }, []);

    const disposals = result?.content ?? [];
    const selectedBatch = candidateBatches.find((batch) => batch.id === form.batchId);
    const pending = disposals.filter((item) => item.status === "SUBMITTED").length;
    const completed = disposals.filter((item) => item.status === "COMPLETED").length;
    const value = disposals.reduce((sum, item) => sum + item.lines.reduce((lineSum, line) => lineSum + line.estimatedValue, 0), 0);
    const maxDisposable = selectedBatch ? Math.min(selectedBatch.onHandQuantity - selectedBatch.reservedQuantity, Math.max(selectedBatch.blockedQuantity, selectedBatch.damagedQuantity)) : 0;
    const formError = useMemo(() => {
        if (!selectedBatch) return "Cần chọn batch.";
        if (selectedBatch.reservedQuantity > 0) return "Batch còn reserved, cần release/cancel reservation trước.";
        if (form.quantity <= 0) return "Quantity phải lớn hơn 0.";
        if (form.quantity > maxDisposable) return "Quantity không được vượt blocked/damaged.";
        if (!form.reason.trim()) return "Lý do là bắt buộc.";
        if (!form.evidence.trim()) return "Evidence là bắt buộc.";
        return "";
    }, [form.evidence, form.quantity, form.reason, maxDisposable, selectedBatch]);

    async function createDisposal() {
        if (!selectedBatch || formError) return;
        setSubmitting(true);
        setError("");
        try {
            const created = await qualityApi.createDisposalRequest({
                actorRole: role,
                lines: [{
                    batchId: selectedBatch.id,
                    evidence: form.evidence.split(",").map((item) => item.trim()).filter(Boolean),
                    quantity: form.quantity,
                    reason: form.reason
                }],
                locationId: selectedBatch.location.id,
                method: form.method,
                note: form.note,
                reason: form.reason,
                scheduledAt: new Date(form.scheduledAt).toISOString(),
                witnesses: form.witnesses.split(",").map((item) => item.trim()).filter(Boolean)
            });
            navigate(`/management/inventory/disposals/${created.id}`);
        } catch (createError) {
            setError(createError instanceof Error ? createError.message : "Không thể tạo yêu cầu tiêu hủy.");
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p>
                <h1 className="text-2xl font-black text-[#172317]">Yêu cầu tiêu hủy</h1>
                <p className="text-sm font-semibold text-[#697684]">Tạo, phê duyệt và hoàn tất tiêu hủy. Complete disposal mới tạo PXK WRITE_OFF và giảm tồn vật lý.</p>
            </div>
            <div className="flex flex-wrap items-end gap-2">
                <QualityRoleSwitch onChange={setRole} value={role} />
                <button className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={() => setShowCreate((open) => !open)} type="button"><Plus size={16} />Tạo yêu cầu</button>
            </div>
        </header>

        <section className="grid gap-3 md:grid-cols-3">
            <Summary icon={<AlertTriangle size={18} />} label="Chờ duyệt" value={formatVietnamNumber(pending)} />
            <Summary icon={<PackageX size={18} />} label="Giá trị trong trang" value={formatVnd(value)} />
            <Summary icon={<CheckCircle2 size={18} />} label="Đã tiêu hủy" value={formatVietnamNumber(completed)} />
        </section>

        {showCreate ? <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div>
                <h2 className="font-black text-[#172317]">Tạo Disposal Request</h2>
                <p className="text-sm font-semibold text-[#697684]">Không cho tiêu hủy hàng vẫn đang reserved; quantity không vượt blocked/damaged.</p>
            </div>
            <div className="grid gap-3 md:grid-cols-3">
                <label className="grid gap-1 text-xs font-black text-[#536078]">Batch<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, batchId: event.currentTarget.value })} value={form.batchId}>{candidateBatches.map((batch) => <option key={batch.id} value={batch.id}>{batch.batchCode} · {batch.skuCode}</option>)}</select></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Quantity<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" min={1} onChange={(event) => setForm({ ...form, quantity: Number(event.currentTarget.value) })} type="number" value={form.quantity} /></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Phương thức<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, method: event.currentTarget.value as DisposalCreateInput["method"] })} value={form.method}>{Object.entries(disposalMethodLabels).map(([method, label]) => <option key={method} value={method}>{label}</option>)}</select></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Ngày lên lịch<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, scheduledAt: event.currentTarget.value })} type="datetime-local" value={form.scheduledAt} /></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Người chứng kiến<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, witnesses: event.currentTarget.value })} value={form.witnesses} /></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Evidence<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, evidence: event.currentTarget.value })} value={form.evidence} /></label>
                <label className="grid gap-1 text-xs font-black text-[#536078] md:col-span-2">Lý do<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, reason: event.currentTarget.value })} value={form.reason} /></label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">Ghi chú<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, note: event.currentTarget.value })} value={form.note} /></label>
            </div>
            {selectedBatch ? <p className="text-sm font-bold text-[#536078]">Có thể tiêu hủy: {formatVietnamNumber(maxDisposable)} · Reserved: {formatVietnamNumber(selectedBatch.reservedQuantity)} · Location: {selectedBatch.location.name}</p> : null}
            {formError ? <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm font-black text-amber-800">{formError}</p> : null}
            <button className="h-10 w-fit rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:bg-[#9cb7a3]" disabled={submitting || Boolean(formError)} onClick={() => void createDisposal()} type="button">{submitting ? "Đang tạo..." : "Tạo yêu cầu tiêu hủy"}</button>
        </section> : null}

        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
            <label className="grid gap-1 text-xs font-black text-[#536078]">Mã yêu cầu<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, code: event.currentTarget.value, page: 0 })} value={query.code} /></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Lý do<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, reason: event.currentTarget.value })} value={query.reason} /></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Trạng thái<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, status: event.currentTarget.value as DisposalListQuery["status"] })} value={query.status}><option value="ALL">Tất cả</option>{disposalStatuses.map((status) => <option key={status} value={status}>{disposalStatusLabels[status]}</option>)}</select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Location<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, locationId: event.currentTarget.value, page: 0 })} value={query.locationId}><option value="ALL">Tất cả</option><option value="loc-central">Kho trung tâm</option><option value="loc-q1">Chi nhánh Quận 1</option></select></label>
        </section>

        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
        {!loading && !error && disposals.length === 0 ? <div className="rounded-xl bg-white p-8 text-center text-sm font-bold text-[#697684]">Chưa có yêu cầu tiêu hủy phù hợp bộ lọc.</div> : null}
        {!loading && !error && disposals.length > 0 ? <DisposalTable disposals={disposals} /> : null}
        {result ? <footer className="flex items-center justify-between rounded-xl bg-white p-3 text-sm font-bold text-[#536078]"><span>Trang {result.page + 1}/{result.totalPages} · {formatVietnamNumber(result.totalElements)} yêu cầu</span><div className="flex gap-2"><button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page === 0} onClick={() => setQuery({ ...query, page: Math.max(0, query.page - 1) })} type="button">Trước</button><button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page + 1 >= result.totalPages} onClick={() => setQuery({ ...query, page: query.page + 1 })} type="button">Sau</button></div></footer> : null}
    </section>;
}

function Summary({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
    return <article className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex items-center gap-2 text-sm font-black text-[#315f3c]"><span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{icon}</span>{label}</div><strong className="text-2xl font-black text-[#172317]">{value}</strong></article>;
}
