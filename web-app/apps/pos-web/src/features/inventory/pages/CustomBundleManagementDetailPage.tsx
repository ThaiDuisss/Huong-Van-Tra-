import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import { bundlePaymentStatusLabels, bundleReservationLabels, customBundleStatusLabels } from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type { BundlePackingInput, CustomBundleDetail } from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function CustomBundleManagementDetailPage() {
    const { bundleId } = useParams();
    const { roles } = useAuth();
    const [bundle, setBundle] = useState<CustomBundleDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [packingOpen, setPackingOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [draft, setDraft] = useState({ actualTotalWeight: 0, numberOfPackages: 1, packagingType: "", packingNote: "", lines: [] as Array<{ lineId: string; actualPackedQuantity: number; picked: boolean; reason: string }> });

    const load = useCallback(() => {
        if (!bundleId) return;
        let active = true;
        setLoading(true);
        void customBundleApi.getCustomBundle(bundleId).then((response)=>{ if(active) setBundle(response); }).catch((loadError:unknown)=>{ if(active) setError(loadError instanceof Error ? loadError.message : "Không thể tải bundle."); }).finally(()=>{ if(active) setLoading(false); });
        return () => { active = false; };
    }, [bundleId]);
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

    if (!bundleId) return <Navigate to="/management/inventory/custom-bundles" replace />;

    function canPack(current: CustomBundleDetail) {
        if (!hasAnyRole(roles, ["ADMIN", "BRANCH_MANAGER", "WAREHOUSE"])) return false;
        return ["PAID", "PACKING"].includes(current.status);
    }

    async function startPacking() {
        if (!bundle) return;
        setError("");
        try {
            setSubmitting(true);
            const updated = await customBundleApi.startCustomBundlePacking(bundle.id);
            setBundle(updated);
            setDraft({ actualTotalWeight: updated.totalWeight, numberOfPackages: 1, packagingType: updated.packagingType, packingNote: updated.packingNote ?? "", lines: updated.lines.map((line)=>({ actualPackedQuantity: line.quantity, lineId: line.id, picked: false, reason: "" })) });
            setPackingOpen(true);
        } catch (startError) {
            setError(startError instanceof Error ? startError.message : "Không thể bắt đầu đóng gói.");
        } finally {
            setSubmitting(false);
        }
    }

    async function confirmPacking() {
        if (!bundle) return;
        const input: BundlePackingInput = { bundleId: bundle.id, operationId: `CUSTOM_BUNDLE_PACK:${bundle.id}`, ...draft };
        try {
            setSubmitting(true);
            const updated = await customBundleApi.confirmCustomBundlePacking(input);
            setBundle(updated);
            setPackingOpen(false);
        } catch (packError) {
            setError(packError instanceof Error ? packError.message : "Confirm packing thất bại.");
        } finally {
            setSubmitting(false);
        }
    }

    async function cancelBundle() {
        if (!bundle) return;
        setBundle(await customBundleApi.cancelCustomBundle(bundle.id));
    }

    return <section className="grid gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3"><Link className="text-sm font-black text-[#159734]" to="/management/inventory/custom-bundles">Quay lại danh sách</Link></div>
        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
        {bundle ? <>
            <header className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><h1 className="text-2xl font-black text-[#172317]">{bundle.code}</h1><p className="text-sm font-semibold text-[#536078]">{bundle.customerName} · {bundle.orderCode} · {bundle.branch.name}</p></div><div className="flex flex-wrap gap-2">{canPack(bundle) ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting || bundle.status === "PACKED"} onClick={()=>void startPacking()} type="button">Bắt đầu đóng gói</button> : null}<button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={bundle.status === "PACKED"} onClick={()=>void cancelBundle()} type="button">Cancel</button></div></div>
                <div className="grid gap-3 md:grid-cols-4"><Info label="Status" value={customBundleStatusLabels[bundle.status]}/><Info label="Payment" value={bundlePaymentStatusLabels[bundle.paymentStatus]}/><Info label="Reservation" value={bundleReservationLabels[bundle.reservationStatus]}/><Info label="Giá" value={formatVnd(bundle.estimatedPrice)}/></div>
            </header>
            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4"><h2 className="font-black">Danh sách nguyên liệu / FEFO allocation</h2>{bundle.lines.map((line)=><div className="grid gap-2 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-4" key={line.id}><Info label="SKU" value={line.skuCode}/><Info label="Required" value={`${formatVietnamNumber(line.quantity)} ${line.unit}`}/><Info label="Reserved" value={formatVietnamNumber(line.batchAllocations.reduce((sum,item)=>sum+item.reservedQuantity,0))}/><Info label="Batch" value={line.batchAllocations.map((item)=>item.batchCode).join(", ") || "-"}/></div>)}</section>
            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4"><h2 className="font-black">Packing checklist / Audit / PXK</h2>{bundle.stockDocumentId ? <Link className="font-black text-[#159734] hover:underline" to={`/management/inventory/documents/${bundle.stockDocumentId}`}>{bundle.stockDocumentCode}</Link> : <p className="text-sm font-semibold text-[#697684]">Chưa tạo PXK CUSTOM_BUNDLE_ISSUE.</p>}{bundle.auditEvents.map((event)=><p className="text-sm font-semibold text-[#536078]" key={event.id}>{formatVietnamDateTime(event.happenedAt)} · {event.actor.name} · {event.note}</p>)}</section>
        </> : null}
        {bundle ? <Dialog open={packingOpen} onOpenChange={setPackingOpen}><DialogContent className="max-w-[760px]"><DialogHeader><DialogTitle>Confirm Packing</DialogTitle><DialogDescription>Operation ID: CUSTOM_BUNDLE_PACK:{bundle.id}. PACKED idempotent, không trừ kho hai lần.</DialogDescription></DialogHeader><div className="grid max-h-[60vh] gap-3 overflow-y-auto px-6 py-5">{draft.lines.map((line,index)=>{const source=bundle.lines.find((item)=>item.id===line.lineId); return <div className="grid gap-2 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-4" key={line.lineId}><Info label="SKU" value={source?.skuCode ?? "-"}/><NumberField label="Actual packed" value={line.actualPackedQuantity} onChange={(actualPackedQuantity)=>setDraft({...draft,lines:draft.lines.map((item,itemIndex)=>itemIndex===index?{...item,actualPackedQuantity}:item)})}/><label className="flex items-center gap-2 text-sm font-black text-[#536078]"><input checked={line.picked} onChange={(e)=>setDraft({...draft,lines:draft.lines.map((item,itemIndex)=>itemIndex===index?{...item,picked:e.currentTarget.checked}:item)})} type="checkbox"/>Đã lấy hàng</label><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setDraft({...draft,lines:draft.lines.map((item,itemIndex)=>itemIndex===index?{...item,reason:e.currentTarget.value}:item)})} placeholder="Reason nếu thiếu" value={line.reason}/></div>;})}<NumberField label="Number of packages" value={draft.numberOfPackages} onChange={(numberOfPackages)=>setDraft({...draft,numberOfPackages})}/><NumberField label="Actual total weight" value={draft.actualTotalWeight} onChange={(actualTotalWeight)=>setDraft({...draft,actualTotalWeight})}/></div><DialogFooter><button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black" onClick={()=>setPackingOpen(false)} type="button">Hủy</button><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={()=>void confirmPacking()} type="button">{submitting ? "Đang confirm..." : "Confirm Packing"}</button></DialogFooter></DialogContent></Dialog> : null}
    </section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}

function NumberField({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" min={0} onChange={(e)=>onChange(Number(e.currentTarget.value))} type="number" value={value}/></label>;
}
