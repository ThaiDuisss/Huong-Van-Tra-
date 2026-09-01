import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    AuditTimeline,
    BatchAllocationTable,
    CostPanel,
    MaterialTable,
    OutputTable,
    PriorityBadge,
    ProductionStatusBadge,
    Progress
} from "../components/ProductionOrderComponents";
import { productionOrderApi } from "../services/productionOrder.api";
import type {
    ProductionCompletionInput,
    ProductionOrder
} from "../types/production-order.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

type Tab = "overview" | "outputs" | "materials" | "reservation" | "completions" | "documents" | "cost" | "audit";

export function ProductionOrderDetailPage() {
    const { productionOrderId } = useParams();
    const { roles } = useAuth();
    const [order, setOrder] = useState<ProductionOrder | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [tab, setTab] = useState<Tab>("overview");
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);
    const [completeOpen, setCompleteOpen] = useState(false);
    const [mutationLoading, setMutationLoading] = useState(false);
    const [completeDraft, setCompleteDraft] = useState({
        actualMaterialConsumption: 10,
        expiryDate: "2027-02-01",
        goodQuantity: 10,
        manufacturingDate: "2026-08-02",
        partial: true,
        qcNote: "",
        rejectedQuantity: 0,
        varianceReason: "",
        wasteQuantity: 1
    });

    const canWarehouse = hasAnyRole(roles, ["ADMIN", "WAREHOUSE_STAFF", "WAREHOUSE"]);
    const canApprove = hasAnyRole(roles, ["ADMIN", "DIRECTOR", "BRANCH_MANAGER"]);
    const canFullCost = hasAnyRole(roles, ["ADMIN", "ACCOUNTANT", "DIRECTOR"]);

    const loadOrder = useCallback(() => {
        if (!productionOrderId) return;
        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });
        void productionOrderApi
            .getProductionOrder(productionOrderId)
            .then((response) => {
                if (active) setOrder(response);
            })
            .catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải LSX.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [productionOrderId]);

    useEffect(() => loadOrder(), [loadOrder]);

    const remaining = useMemo(() => {
        if (!order) return 0;
        const planned = order.outputs.reduce((sum, output) => sum + output.plannedQuantity, 0);
        const completed = order.outputs.reduce((sum, output) => sum + output.completedQuantity + output.rejectedQuantity, 0);
        return Math.max(0, planned - completed);
    }, [order]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 2600);
    }

    async function runAction(action: "submit" | "approve" | "release" | "start" | "cancel" | "close") {
        if (!order) return;
        const input = { productionOrderId: order.id };
        const actionMap = {
            approve: productionOrderApi.approveProductionOrder,
            cancel: productionOrderApi.cancelProductionOrder,
            close: productionOrderApi.closeProductionOrderRemainingQuantity,
            release: productionOrderApi.releaseProductionOrder,
            start: productionOrderApi.startProductionOrder,
            submit: productionOrderApi.submitProductionOrder
        };
        try {
            setMutationLoading(true);
            const updated = await actionMap[action](input);
            setOrder(updated);
            showToast("Thao tác LSX thành công.", true);
        } catch (actionError) {
            showToast(actionError instanceof Error ? actionError.message : "Thao tác thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    async function completeProduction() {
        if (!order) return;
        const completionNumber = order.completions.length + 1;
        const input: ProductionCompletionInput = {
            actualMaterialConsumption: completeDraft.actualMaterialConsumption,
            expiryDate: new Date(completeDraft.expiryDate).toISOString(),
            goodQuantity: completeDraft.goodQuantity,
            idempotencyKey: `PRODUCTION_COMPLETE:${order.id}:${completionNumber}`,
            manufacturingDate: new Date(completeDraft.manufacturingDate).toISOString(),
            partial: completeDraft.partial,
            productionOrderId: order.id,
            qcNote: completeDraft.qcNote,
            rejectedQuantity: completeDraft.rejectedQuantity,
            varianceReason: completeDraft.varianceReason || undefined,
            wasteQuantity: completeDraft.wasteQuantity
        };
        try {
            setMutationLoading(true);
            const updated = await productionOrderApi.completeProductionOrder(input);
            setOrder(updated);
            setCompleteOpen(false);
            showToast("Complete Production thành công.", true);
        } catch (completeError) {
            showToast(completeError instanceof Error ? completeError.message : "Complete thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    if (!productionOrderId) return <Navigate to="/management/inventory/production-orders" replace />;
    if (loading) return <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">{Array.from({length:8},(_,i)=><div className="h-12 animate-pulse rounded-lg bg-[#eef3ef]" key={i}/>)}</div>;
    if (error || !order) return <section className="grid gap-4"><BackLink /><div className="rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center text-sm font-bold text-red-700">{error || "Không tìm thấy LSX."}</div></section>;

    const readOnly = order.status === "COMPLETED" || order.status === "CANCELLED" || order.status === "FAILED";

    return (
        <section className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3"><BackLink /></div>
            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="text-xs font-black text-[#697684]">Kho › Lệnh sản xuất › Chi tiết</div>
                        <div className="flex flex-wrap items-center gap-2"><h1 className="text-2xl font-black text-[#172317]">{order.code}</h1><ProductionStatusBadge status={order.status}/><PriorityBadge priority={order.priority}/></div>
                        <p className="text-sm font-semibold text-[#536078]">{order.title} · {order.location.name}</p>
                        <Progress value={order.progressPercent} />
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {order.status === "DRAFT" && canWarehouse ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={mutationLoading} onClick={()=>void runAction("submit")} type="button">Submit</button> : null}
                        {order.status === "PENDING_APPROVAL" && canApprove ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={mutationLoading} onClick={()=>void runAction("approve")} type="button">Approve</button> : null}
                        {order.status === "RELEASED" && canWarehouse ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={mutationLoading} onClick={()=>void runAction("start")} type="button">Start Production</button> : null}
                        {["IN_PROGRESS","PARTIALLY_COMPLETED"].includes(order.status) && canWarehouse ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={()=>setCompleteOpen(true)} type="button">Complete Production</button> : null}
                        {order.status === "PARTIALLY_COMPLETED" && canWarehouse ? <button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={mutationLoading} onClick={()=>void runAction("close")} type="button">Close remaining</button> : null}
                        {readOnly ? <span className="rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 py-2 text-sm font-bold text-[#697684]">Read-only</span> : null}
                    </div>
                </div>
            </header>

            <nav className="flex gap-2 overflow-x-auto border-b border-[#e8eef0] pb-1">{[["overview","Tổng quan"],["outputs","Thành phẩm"],["materials","Nguyên liệu"],["reservation","Reservation và FEFO"],["completions","Các lần hoàn thành"],["documents","Chứng từ kho"],["cost","Giá thành"],["audit","Audit"]].map(([value,label])=><button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${tab===value ? "bg-[#159734] text-white" : "bg-white text-[#536078]"}`} key={value} onClick={()=>setTab(value as Tab)} type="button">{label}</button>)}</nav>

            {tab === "overview" ? <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 md:grid-cols-3"><Info label="Warehouse" value={order.location.name}/><Info label="Planned start" value={order.plannedStartAt ? formatVietnamDateTime(order.plannedStartAt) : "-"}/><Info label="Due date" value={order.dueAt ? formatVietnamDateTime(order.dueAt) : "-"}/><Info label="Owner" value={order.owner.name}/><Info label="Shortage" value={order.hasShortage ? "Có thiếu nguyên liệu" : "Không"}/><Info label="Remaining output" value={formatVietnamNumber(remaining)}/></section> : null}
            {tab === "outputs" ? <OutputTable outputs={order.outputs}/> : null}
            {tab === "materials" ? <MaterialTable materials={order.materials}/> : null}
            {tab === "reservation" ? <BatchAllocationTable materials={order.materials}/> : null}
            {tab === "completions" ? <CompletionList order={order}/> : null}
            {tab === "documents" ? <DocumentLinks order={order}/> : null}
            {tab === "cost" ? <CostPanel order={order} canViewFull={canFullCost}/> : null}
            {tab === "audit" ? <AuditTimeline order={order}/> : null}

            <Dialog open={completeOpen} onOpenChange={setCompleteOpen}>
                <DialogContent className="max-w-[760px]">
                    <DialogHeader><DialogTitle>Complete Production</DialogTitle><DialogDescription>Operation ID: PRODUCTION_COMPLETE:{order.id}:{order.completions.length + 1}. Mutation idempotent, không double complete khi bấm hai lần.</DialogDescription></DialogHeader>
                    <div className="grid max-h-[62vh] gap-3 overflow-y-auto px-6 py-5 md:grid-cols-2">
                        <Info label="Remaining" value={formatVietnamNumber(remaining)} />
                        <NumberField label="Actual good quantity" value={completeDraft.goodQuantity} onChange={(goodQuantity)=>setCompleteDraft({...completeDraft, goodQuantity})}/>
                        <NumberField label="Rejected quantity" value={completeDraft.rejectedQuantity} onChange={(rejectedQuantity)=>setCompleteDraft({...completeDraft, rejectedQuantity})}/>
                        <NumberField label="Actual material consumption" value={completeDraft.actualMaterialConsumption} onChange={(actualMaterialConsumption)=>setCompleteDraft({...completeDraft, actualMaterialConsumption})}/>
                        <NumberField label="Waste quantity" value={completeDraft.wasteQuantity} onChange={(wasteQuantity)=>setCompleteDraft({...completeDraft, wasteQuantity})}/>
                        <TextInput label="Reason nếu vượt tolerance" value={completeDraft.varianceReason} onChange={(varianceReason)=>setCompleteDraft({...completeDraft, varianceReason})}/>
                        <TextInput label="Manufacturing date" type="date" value={completeDraft.manufacturingDate} onChange={(manufacturingDate)=>setCompleteDraft({...completeDraft, manufacturingDate})}/>
                        <TextInput label="Expiry date" type="date" value={completeDraft.expiryDate} onChange={(expiryDate)=>setCompleteDraft({...completeDraft, expiryDate})}/>
                        <TextInput label="QC note" value={completeDraft.qcNote} onChange={(qcNote)=>setCompleteDraft({...completeDraft, qcNote})}/>
                        <label className="flex items-center gap-2 text-sm font-black text-[#536078]"><input checked={completeDraft.partial} onChange={(event)=>setCompleteDraft({...completeDraft, partial:event.currentTarget.checked})} type="checkbox"/>Partial complete</label>
                        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-bold text-amber-800 md:col-span-2">Confirm sẽ commit reservation, trừ batch nguyên liệu, tạo PXK PRODUCTION_ISSUE, tạo batch thành phẩm và PNK PRODUCTION_RECEIPT.</div>
                    </div>
                    <DialogFooter><button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" disabled={mutationLoading} onClick={()=>setCompleteOpen(false)} type="button">Hủy</button><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={mutationLoading} onClick={()=>void completeProduction()} type="button">{mutationLoading ? "Đang complete..." : "Confirm complete"}</button></DialogFooter>
                </DialogContent>
            </Dialog>

            {toast ? <div className={`fixed bottom-6 right-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-pos-panel ${toast.success ? "bg-[#103b2b]" : "bg-red-700"}`} role="status">{toast.message}</div> : null}
        </section>
    );
}

function BackLink() {
    return <Link className="text-sm font-black text-[#159734] hover:underline" to="/management/inventory/production-orders">Quay lại danh sách</Link>;
}

function CompletionList({ order }: { order: ProductionOrder }) {
    if (order.completions.length === 0) return <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-bold text-[#697684]">Chưa có lần hoàn thành.</div>;
    return <div className="grid gap-3">{order.completions.map((completion)=><div className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 md:grid-cols-4" key={completion.id}><Info label="Lần" value={String(completion.completionNumber)}/><Info label="Good" value={formatVietnamNumber(completion.goodQuantity)}/><Info label="Rejected" value={formatVietnamNumber(completion.rejectedQuantity)}/><Info label="Batch thành phẩm" value={completion.newBatchCode}/><Info label="Operation ID" value={completion.operationId}/></div>)}</div>;
}

function DocumentLinks({ order }: { order: ProductionOrder }) {
    return <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">{order.materialIssueDocumentId ? <Link className="font-black text-[#159734] hover:underline" to={`/management/inventory/documents/${order.materialIssueDocumentId}`}>{order.materialIssueDocumentCode}</Link> : <p className="text-sm font-semibold text-[#697684]">Chưa có PXK nguyên liệu.</p>}{order.finishedReceiptDocumentId ? <Link className="font-black text-[#159734] hover:underline" to={`/management/inventory/documents/${order.finishedReceiptDocumentId}`}>{order.finishedReceiptDocumentCode}</Link> : <p className="text-sm font-semibold text-[#697684]">Chưa có PNK thành phẩm.</p>}{order.status === "COMPLETED" ? <p className="text-sm font-bold text-[#536078]">Batch thành phẩm đã tạo trong tab hoàn thành.</p> : null}</div>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}

function NumberField({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]" min={0} onChange={(event)=>onChange(Number(event.currentTarget.value))} type="number" value={value}/></label>;
}

function TextInput({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]" onChange={(event)=>onChange(event.currentTarget.value)} type={type} value={value}/></label>;
}
