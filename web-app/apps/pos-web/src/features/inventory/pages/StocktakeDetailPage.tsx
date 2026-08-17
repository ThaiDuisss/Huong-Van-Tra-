import { FileText, Lock, Play, Save, Send } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    StocktakeRoleSwitch,
    StocktakeStatusBadge
} from "../components/StocktakeComponents";
import {
    stocktakeApprovalThresholds,
    stocktakeConditionLabels,
    stocktakeReasonLabels,
    stocktakeReasonOptions,
    stocktakeStatusLabels,
    stocktakeVarianceLevelLabels
} from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type {
    StocktakeCondition,
    StocktakeDetail,
    StocktakeReasonCode,
    StocktakeRole
} from "../types/stocktake.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

type DetailTab = "overview" | "counting" | "reconciliation" | "adjustment" | "documents" | "audit";

const tabs: Array<{ id: DetailTab; label: string }> = [
    { id: "overview", label: "Tổng quan" },
    { id: "counting", label: "Đếm thực tế" },
    { id: "reconciliation", label: "Đối chiếu" },
    { id: "adjustment", label: "Adjustment" },
    { id: "documents", label: "Chứng từ" },
    { id: "audit", label: "Audit" }
];

function readRole(): StocktakeRole {
    const stored = window.localStorage.getItem("hvt.stocktake.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored)
        ? stored as StocktakeRole
        : "WAREHOUSE";
}

export function StocktakeDetailPage() {
    const { stocktakeId } = useParams();
    const [role, setRole] = useState<StocktakeRole>(readRole);
    const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);
    const [activeTab, setActiveTab] = useState<DetailTab>("overview");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [startOpen, setStartOpen] = useState(false);
    const [search, setSearch] = useState("");
    const [countFilter, setCountFilter] = useState<"ALL" | "UNCOUNTED" | "COUNTED" | "ISSUE">("ALL");
    const [adjustmentReason, setAdjustmentReason] = useState("Tạo điều chỉnh từ kiểm kê.");
    const [rejectReason, setRejectReason] = useState("");

    const load = useCallback(() => {
        if (!stocktakeId) return;
        let active = true;
        setLoading(true);
        setError("");
        void stocktakeApi.getStocktake(stocktakeId)
            .then((response) => { if (active) setStocktake(response); })
            .catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải kỳ kiểm kê.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [stocktakeId]);

    useEffect(() => {
        window.localStorage.setItem("hvt.stocktake.role", role);
    }, [role]);

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

    if (!stocktakeId) return <Navigate to="/management/inventory/stocktakes" replace />;

    async function runAction(action: () => Promise<StocktakeDetail>, fallback: string) {
        setError("");
        try {
            setSubmitting(true);
            setStocktake(await action());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : fallback);
        } finally {
            setSubmitting(false);
        }
    }

    function saveLine(lineId: string, round: 1 | 2, quantity: number, condition: StocktakeCondition) {
        if (!stocktake) return;
        void runAction(
            () => stocktakeApi.saveStocktakeCount({ actorRole: role, condition, countedQuantity: quantity, lineId, round, stocktakeId: stocktake.id }),
            "Không thể lưu dòng kiểm kê."
        );
    }

    function finalize() {
        if (!stocktake) return;
        void runAction(
            () => stocktakeApi.finalizeStocktakeCount({
                actorRole: role,
                lines: stocktake.lines.map((line) => ({
                    finalPhysicalQuantity: line.rounds.find((round) => round.round === 2 && typeof round.countedQuantity === "number")?.countedQuantity ?? line.rounds.find((round) => round.round === 1)?.countedQuantity ?? line.expectedCurrentQuantity,
                    lineId: line.id,
                    reasonCode: (line.reasonCode ?? "ADJ-99") as StocktakeReasonCode,
                    reasonNote: line.reasonNote ?? "Chọn final count theo vòng đếm mới nhất."
                })),
                stocktakeId: stocktake.id
            }),
            "Không thể finalize count."
        );
    }

    function createAdjustment() {
        if (!stocktake) return;
        void runAction(
            () => stocktakeApi.createAdjustmentFromStocktake({
                actorRole: role,
                evidence: "bien-ban-kiem-ke.jpg",
                lineIds: stocktake.lines.filter((line) => Math.abs(line.varianceQuantity) > 0).map((line) => line.id),
                note: "Chọn tất cả variance.",
                reason: adjustmentReason,
                stocktakeId: stocktake.id
            }),
            "Không thể tạo adjustment request."
        );
    }

    const readonly = stocktake?.status === "POSTED";
    const canOperate = ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"].includes(role) && !readonly;
    const canApprove = stocktake?.varianceLevel === "LARGE" ? ["ADMIN", "DIRECTOR"].includes(role) : ["ADMIN", "DIRECTOR", "WAREHOUSE", "BRANCH_MANAGER"].includes(role);

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="grid gap-1">
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/stocktakes">Quay lại danh sách</Link>
                    <h1 className="text-2xl font-black text-[#172317]">{stocktake?.code ?? "Kiểm kê kho"}</h1>
                    {stocktake ? <p className="text-sm font-semibold text-[#697684]">{stocktake.location.name} · {stocktake.scopeLabel}</p> : null}
                </div>
                <StocktakeRoleSwitch onChange={setRole} value={role} />
            </header>

            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold shadow-pos-card">Đang tải...</div> : null}
            {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}

            {stocktake ? (
                <>
                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                            <div className="grid gap-2">
                                <StocktakeStatusBadge status={stocktake.status} />
                                <p className="text-sm font-semibold text-[#536078]">
                                    Blind count: {stocktake.blindCount ? "Bật" : "Tắt"} · Snapshot: {stocktake.snapshotFrozenAt ? formatVietnamDateTime(stocktake.snapshotFrozenAt) : "Chưa tạo"}
                                </p>
                                {readonly ? <p className="inline-flex items-center gap-2 text-sm font-black text-[#536078]"><Lock size={16} /> POSTED read-only</p> : null}
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {stocktake.status === "DRAFT" && canOperate ? <ActionButton disabled={submitting} icon={<Play size={16} />} label="Start và tạo snapshot" onClick={() => setStartOpen(true)} /> : null}
                                {stocktake.status === "IN_PROGRESS" && canOperate ? <ActionButton disabled={submitting} icon={<Send size={16} />} label="Submit round 1" onClick={() => void runAction(() => stocktakeApi.submitStocktakeRound({ actorRole: role, round: 1, stocktakeId: stocktake.id }), "Không thể submit round 1.")} /> : null}
                                {stocktake.status === "IN_PROGRESS" && canOperate && stocktake.lines.some((line) => line.rounds.some((round) => round.round === 2)) ? <ActionButton disabled={submitting} icon={<Send size={16} />} label="Submit round 2" onClick={() => void runAction(() => stocktakeApi.submitStocktakeRound({ actorRole: role, round: 2, stocktakeId: stocktake.id }), "Không thể submit round 2.")} /> : null}
                                {stocktake.status === "COUNTED" && canOperate ? <ActionButton disabled={submitting} icon={<Save size={16} />} label="Finalize count" onClick={finalize} /> : null}
                                {stocktake.status === "COUNTED" && canOperate ? <ActionButton disabled={submitting} icon={<Save size={16} />} label="Bắt đầu đối chiếu" onClick={() => void runAction(() => stocktakeApi.startStocktakeReconciliation({ actorRole: role, stocktakeId: stocktake.id }), "Không thể bắt đầu đối chiếu.")} /> : null}
                                {stocktake.status === "RECONCILING" && canOperate ? <ActionButton disabled={submitting} icon={<FileText size={16} />} label="Tạo adjustment" onClick={createAdjustment} /> : null}
                                {stocktake.status === "PENDING_APPROVAL" && canApprove ? <ActionButton disabled={submitting} icon={<Save size={16} />} label="Approve" onClick={() => void runAction(() => stocktakeApi.approveStocktakeAdjustment({ actorRole: role, stocktakeId: stocktake.id }), "Không thể approve.")} /> : null}
                                {stocktake.status === "PENDING_APPROVAL" && canApprove ? <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setRejectReason(event.currentTarget.value)} placeholder="Lý do reject" value={rejectReason} /> : null}
                                {stocktake.status === "PENDING_APPROVAL" && canApprove ? <button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void runAction(() => stocktakeApi.rejectStocktakeAdjustment({ actorRole: role, reason: rejectReason, stocktakeId: stocktake.id }), "Không thể reject.")} type="button">Reject</button> : null}
                                {stocktake.status === "APPROVED" && canOperate ? <ActionButton disabled={submitting} icon={<FileText size={16} />} label="Post adjustment" onClick={() => void runAction(() => stocktakeApi.postStocktakeAdjustment({ actorRole: role, stocktakeId: stocktake.id }), "Không thể post adjustment.")} /> : null}
                            </div>
                        </div>
                    </section>

                    <nav className="flex gap-2 overflow-x-auto">
                        {tabs.map((tab) => (
                            <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${activeTab === tab.id ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} type="button">
                                {tab.label}
                            </button>
                        ))}
                    </nav>

                    {activeTab === "overview" ? <Overview stocktake={stocktake} /> : null}
                    {activeTab === "counting" ? <Counting canEdit={canOperate && stocktake.status === "IN_PROGRESS"} countFilter={countFilter} onFilter={setCountFilter} onSave={saveLine} search={search} setSearch={setSearch} stocktake={stocktake} /> : null}
                    {activeTab === "reconciliation" ? <Reconciliation stocktake={stocktake} /> : null}
                    {activeTab === "adjustment" ? <Adjustment adjustmentReason={adjustmentReason} setAdjustmentReason={setAdjustmentReason} stocktake={stocktake} /> : null}
                    {activeTab === "documents" ? <Documents stocktake={stocktake} /> : null}
                    {activeTab === "audit" ? <Audit stocktake={stocktake} /> : null}

                    <Dialog open={startOpen} onOpenChange={setStartOpen}>
                        <DialogContent>
                            <DialogHeader>
                                <DialogTitle>Bắt đầu kiểm kê và tạo snapshot</DialogTitle>
                                <DialogDescription>Hệ thống sẽ đóng băng snapshot. Các movement phát sinh sau snapshot sẽ được ghi nhận riêng và dùng trong reconciliation, không dùng snapshot đơn thuần.</DialogDescription>
                            </DialogHeader>
                            <DialogFooter>
                                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black" onClick={() => setStartOpen(false)} type="button">Hủy</button>
                                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={() => { setStartOpen(false); void runAction(() => stocktakeApi.startStocktake({ actorRole: role, stocktakeId: stocktake.id }), "Không thể start kiểm kê."); }} type="button">Xác nhận start</button>
                            </DialogFooter>
                        </DialogContent>
                    </Dialog>
                </>
            ) : null}
        </section>
    );
}

function Overview({ stocktake }: { stocktake: StocktakeDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
            <Info label="Status" value={stocktakeStatusLabels[stocktake.status]} />
            <Info label="Tổng SKU" value={formatVietnamNumber(stocktake.totalSku)} />
            <Info label="Đã đếm" value={formatVietnamNumber(stocktake.countedSku)} />
            <Info label="Variance value" value={formatVnd(stocktake.varianceValue)} />
            <Info label="Scheduled start" value={formatVietnamDateTime(stocktake.scheduledStartAt)} />
            <Info label="Người đếm vòng 1" value={stocktake.counterRound1.name} />
            <Info label="Người đếm vòng 2" value={stocktake.counterRound2?.name ?? "-"} />
            <Info label="Threshold" value={`${formatVnd(stocktakeApprovalThresholds.medium)} / ${formatVnd(stocktakeApprovalThresholds.large)}`} />
            <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-semibold text-[#536078] md:col-span-4">Note: {stocktake.note ?? "-"}</div>
        </section>
    );
}

function Counting({ canEdit, countFilter, onFilter, onSave, search, setSearch, stocktake }: { canEdit: boolean; countFilter: "ALL" | "UNCOUNTED" | "COUNTED" | "ISSUE"; onFilter: (value: "ALL" | "UNCOUNTED" | "COUNTED" | "ISSUE") => void; onSave: (lineId: string, round: 1 | 2, quantity: number, condition: StocktakeCondition) => void; search: string; setSearch: (value: string) => void; stocktake: StocktakeDetail }) {
    const lines = stocktake.lines.filter((line) => {
        const text = `${line.skuCode} ${line.batchCode} ${line.skuName}`.toLowerCase();
        const matchesSearch = text.includes(search.toLowerCase());
        const counted = line.rounds.some((round) => typeof round.countedQuantity === "number");
        const matchesFilter = countFilter === "ALL" || (countFilter === "COUNTED" && counted) || (countFilter === "UNCOUNTED" && !counted) || (countFilter === "ISSUE" && line.issueFlag);
        return matchesSearch && matchesFilter;
    });
    const progress = stocktake.totalSku === 0 ? 0 : Math.round((stocktake.countedSku / stocktake.totalSku) * 100);
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-2">
                <div className="flex items-center justify-between text-sm font-black text-[#536078]"><span>Progress</span><span>{progress}%</span></div>
                <div className="h-3 overflow-hidden rounded-full bg-[#edf0f4]"><div className="h-full bg-[#159734]" style={{ width: `${progress}%` }} /></div>
            </div>
            <div className="grid gap-3 md:grid-cols-[1fr_220px]">
                <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setSearch(event.currentTarget.value)} placeholder="Tìm SKU/batch..." value={search} />
                <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onFilter(event.currentTarget.value as "ALL" | "UNCOUNTED" | "COUNTED" | "ISSUE")} value={countFilter}>
                    <option value="ALL">Tất cả</option>
                    <option value="UNCOUNTED">Chưa đếm</option>
                    <option value="COUNTED">Đã đếm</option>
                    <option value="ISSUE">Có vấn đề</option>
                </select>
            </div>
            <div className="grid gap-3">
                {lines.map((line) => <CountLine canEdit={canEdit} key={line.id} line={line} onSave={onSave} stocktake={stocktake} />)}
            </div>
        </section>
    );
}

function CountLine({ canEdit, line, onSave, stocktake }: { canEdit: boolean; line: StocktakeDetail["lines"][number]; onSave: (lineId: string, round: 1 | 2, quantity: number, condition: StocktakeCondition) => void; stocktake: StocktakeDetail }) {
    const activeRound = line.rounds.some((round) => round.round === 2) ? 2 : 1;
    const round = line.rounds.find((item) => item.round === activeRound);
    const [quantity, setQuantity] = useState(round?.countedQuantity ?? 0);
    const [condition, setCondition] = useState<StocktakeCondition>(round?.condition ?? "GOOD");
    return (
        <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 xl:grid-cols-[1.2fr_repeat(7,minmax(90px,1fr))_110px]">
            <Info label="SKU" value={`${line.skuCode} · ${line.skuName}`} />
            <Info label="Batch" value={line.batchCode} />
            <Info label="Expiry" value={formatVietnamDateTime(line.expiryDate)} />
            <Info label="Location/bin" value={line.bin} />
            <Info label="UOM" value={line.unit} />
            <Info label="System qty" value={stocktake.blindCount && stocktake.status === "IN_PROGRESS" ? "Blind" : formatVietnamNumber(line.expectedCurrentQuantity)} />
            <label className="grid gap-1 rounded-lg bg-white px-3 py-2 text-xs font-black text-[#697684]">
                Counted quantity
                <input className="h-9 rounded-lg border border-pos-border px-2 text-sm font-bold text-[#172317]" min={0} onChange={(event) => setQuantity(Number(event.currentTarget.value))} type="number" value={quantity} />
            </label>
            <label className="grid gap-1 rounded-lg bg-white px-3 py-2 text-xs font-black text-[#697684]">
                Condition
                <select className="h-9 rounded-lg border border-pos-border px-2 text-sm font-bold text-[#172317]" onChange={(event) => setCondition(event.currentTarget.value as StocktakeCondition)} value={condition}>
                    {Object.entries(stocktakeConditionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
            </label>
            <button className="mt-5 h-10 rounded-lg bg-[#159734] px-3 text-sm font-black text-white disabled:opacity-60" disabled={!canEdit} onClick={() => onSave(line.id, activeRound, quantity, condition)} type="button">Đã đếm</button>
            {quantity < 0 ? <p className="text-sm font-bold text-red-700 xl:col-span-9">Cảnh báo: không được nhập số âm.</p> : null}
        </div>
    );
}

function Reconciliation({ stocktake }: { stocktake: StocktakeDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {stocktake.lines.map((line) => (
                <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-5" key={line.id}>
                    <Info label="SKU" value={line.skuCode} />
                    <Info label="Snapshot" value={formatVietnamNumber(line.snapshotQuantity)} />
                    <Info label="Movement sau snapshot" value={formatVietnamNumber(line.movementAfterSnapshot)} />
                    <Info label="Expected current" value={formatVietnamNumber(line.expectedCurrentQuantity)} />
                    <Info label="Final physical" value={typeof line.finalPhysicalQuantity === "number" ? formatVietnamNumber(line.finalPhysicalQuantity) : "-"} />
                    <Info label="Variance" value={formatVietnamNumber(line.varianceQuantity)} />
                    <Info label="Variance %" value={`${formatVietnamNumber(line.variancePercent)}%`} />
                    <Info label="Impact" value={formatVnd(line.financialImpact)} />
                    <Info label="Reason code" value={line.reasonCode ? `${line.reasonCode} ${stocktakeReasonLabels[line.reasonCode]}` : "-"} />
                    <Info label="Reason note" value={line.reasonNote ?? "-"} />
                </div>
            ))}
        </section>
    );
}

function Adjustment({ adjustmentReason, setAdjustmentReason, stocktake }: { adjustmentReason: string; setAdjustmentReason: (value: string) => void; stocktake: StocktakeDetail }) {
    const increase = stocktake.lines.filter((line) => line.varianceQuantity > 0).reduce((sum, line) => sum + line.financialImpact, 0);
    const decrease = stocktake.lines.filter((line) => line.varianceQuantity < 0).reduce((sum, line) => sum + Math.abs(line.financialImpact), 0);
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-4">
                <Info label="Threshold level" value={stocktakeVarianceLevelLabels[stocktake.varianceLevel]} />
                <Info label="Increase value" value={formatVnd(increase)} />
                <Info label="Decrease value" value={formatVnd(decrease)} />
                <Info label="Approval" value={stocktake.varianceLevel === "LARGE" ? "ADMIN hoặc DIRECTOR" : "Quản lý có quyền"} />
            </div>
            <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                Reason
                <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setAdjustmentReason(event.currentTarget.value)} value={adjustmentReason} />
            </label>
            <div className="grid gap-2">
                {stocktakeReasonOptions.map((code) => <span className="text-sm font-semibold text-[#536078]" key={code}>{code}: {stocktakeReasonLabels[code]}</span>)}
            </div>
            {stocktake.adjustmentRequest ? <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-bold text-[#536078]">Adjustment: {stocktake.adjustmentRequest.status} · Increase {formatVnd(stocktake.adjustmentRequest.increaseValue)} · Decrease {formatVnd(stocktake.adjustmentRequest.decreaseValue)}</div> : null}
        </section>
    );
}

function Documents({ stocktake }: { stocktake: StocktakeDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <DocumentLink code={stocktake.adjustmentInDocumentCode} id={stocktake.adjustmentInDocumentId} label="DCK ADJUSTMENT_IN" />
            <DocumentLink code={stocktake.adjustmentOutDocumentCode} id={stocktake.adjustmentOutDocumentId} label="DCK ADJUSTMENT_OUT" />
        </section>
    );
}

function Audit({ stocktake }: { stocktake: StocktakeDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {stocktake.auditEvents.map((event) => <p className="text-sm font-semibold text-[#536078]" key={event.id}>{formatVietnamDateTime(event.happenedAt)} · {event.actor.name} · {event.note}</p>)}
        </section>
    );
}

function DocumentLink({ code, id, label }: { code?: string; id?: string; label: string }) {
    return (
        <div className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] p-3">
            <strong className="text-sm font-black text-[#172317]">{label}</strong>
            {id && code ? <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734] hover:underline" to={`/management/inventory/documents/${id}`}><FileText size={16} />{code}</Link> : <span className="text-sm font-bold text-[#697684]">Chưa tạo</span>}
        </div>
    );
}

function ActionButton({ disabled, icon, label, onClick }: { disabled: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
    return <button className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={disabled} onClick={onClick} type="button">{icon}{label}</button>;
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-white px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">{value || "-"}</strong>
        </div>
    );
}
