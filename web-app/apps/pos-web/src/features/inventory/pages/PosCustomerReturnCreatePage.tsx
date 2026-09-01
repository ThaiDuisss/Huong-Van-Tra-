import { Search } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import {
    customerReturnConditionLabels,
    customerReturnConditions,
    customerReturnReasonLabels,
    customerReturnReasons,
    customerReturnResolutionLabels
} from "../constants/customer-return.constants";
import { customerReturnApi } from "../services/customerReturn.api";
import type {
    CustomerReturnCondition,
    CustomerReturnReason,
    CustomerReturnResolution,
    ReturnableOrder
} from "../types/customer-return.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

interface DraftLine {
    orderLineId: string;
    quantity: number;
    reason: CustomerReturnReason;
    condition: CustomerReturnCondition;
    customerNote: string;
}

export function PosCustomerReturnCreatePage() {
    const navigate = useNavigate();
    const [keyword, setKeyword] = useState("");
    const [orders, setOrders] = useState<ReturnableOrder[]>([]);
    const [order, setOrder] = useState<ReturnableOrder | null>(null);
    const [lines, setLines] = useState<DraftLine[]>([]);
    const [resolution, setResolution] = useState<CustomerReturnResolution>("REFUND");
    const [note, setNote] = useState("");
    const [evidence, setEvidence] = useState("");
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    async function search() {
        setOrders(await customerReturnApi.searchReturnableOrders(keyword));
    }

    function toggleLine(orderLineId: string) {
        setLines((current) => current.some((line) => line.orderLineId === orderLineId)
            ? current.filter((line) => line.orderLineId !== orderLineId)
            : [...current, { condition: "SEALED", customerNote: "", orderLineId, quantity: 1, reason: "CHANGE_MIND" }]);
    }

    async function submit() {
        if (!order) return;
        try {
            setSubmitting(true);
            setError("");
            const created = await customerReturnApi.createCustomerReturn({
                customerNote: note,
                desiredResolution: resolution,
                evidence: evidence ? [evidence] : [],
                lines,
                orderId: order.id
            });
            navigate(`/pos/customer-returns/${created.id}`);
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "Không thể tạo yêu cầu trả hàng.");
        } finally {
            setSubmitting(false);
        }
    }

    const checkout = (
        <aside className="grid h-full min-h-0 gap-3 rounded-2xl bg-white p-4 shadow-pos-panel">
            <h2 className="text-xl font-black text-[#172317]">Review trả hàng</h2>
            {order ? <Info label="Order" value={order.code} /> : <p className="text-sm font-bold text-[#697684]">Chưa chọn order.</p>}
            <Info label="Phương án" value={customerReturnResolutionLabels[resolution]} />
            <Info label="Số dòng trả" value={formatVietnamNumber(lines.length)} />
            {error ? <div className="rounded-lg bg-red-50 p-3 text-sm font-bold text-red-700">{error}</div> : null}
            <button className="h-11 rounded-lg bg-[#159734] text-sm font-black text-white disabled:opacity-60" disabled={!order || lines.length === 0 || submitting} onClick={() => void submit()} type="button">{submitting ? "Đang submit..." : "Submit yêu cầu"}</button>
        </aside>
    );

    return (
        <PosShell checkout={checkout}>
            <section className="grid h-full min-h-0 gap-4">
                <header><Link className="text-sm font-black text-[#159734]" to="/pos/sales">Quay lại POS</Link><h1 className="text-2xl font-black text-[#172317]">Khách hàng trả hàng</h1></header>
                <div className="flex gap-2"><div className="relative flex-1"><Search className="absolute left-3 top-3 text-[#697684]" size={16} /><input className="h-10 w-full rounded-lg border border-pos-border pl-9 pr-3 text-sm font-bold" onChange={(event) => setKeyword(event.currentTarget.value)} placeholder="Order code, SĐT, hóa đơn..." value={keyword} /></div><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={() => void search()} type="button">Tìm</button></div>
                <div className="grid gap-3 overflow-y-auto">
                    {orders.map((item) => <button className={`rounded-xl border p-4 text-left shadow-pos-card ${order?.id === item.id ? "border-[#159734] bg-[#f4faf5]" : "border-[#e8eef0] bg-white"}`} key={item.id} onClick={() => { setOrder(item); setLines([]); }} type="button"><strong>{item.code}</strong><p className="text-sm font-bold text-[#697684]">{item.customerName} · {item.customerPhone} · {formatVietnamDateTime(item.purchasedAt)}</p><p className="text-sm font-bold text-[#697684]">{item.branch.name} · Payment: {item.paymentStatus}</p></button>)}
                    {order ? <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><h2 className="font-black">Sản phẩm đã mua</h2>{order.lines.map((orderLine) => {
                        const selected = lines.find((line) => line.orderLineId === orderLine.id);
                        const remaining = orderLine.purchasedQuantity - orderLine.previouslyReturnedQuantity;
                        return <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-[1fr_120px_160px_160px]" key={orderLine.id}><label className="flex items-center gap-2 text-sm font-black"><input checked={Boolean(selected)} onChange={() => toggleLine(orderLine.id)} type="checkbox" />{orderLine.skuCode} · {orderLine.productName}</label><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" disabled={!selected} max={remaining} min={1} onChange={(event) => setLines((current) => current.map((line) => line.orderLineId === orderLine.id ? { ...line, quantity: Number(event.currentTarget.value) } : line))} type="number" value={selected?.quantity ?? 1} /><select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" disabled={!selected} onChange={(event) => setLines((current) => current.map((line) => line.orderLineId === orderLine.id ? { ...line, reason: event.currentTarget.value as CustomerReturnReason } : line))} value={selected?.reason ?? "CHANGE_MIND"}>{customerReturnReasons.map((reason) => <option key={reason} value={reason}>{customerReturnReasonLabels[reason]}</option>)}</select><select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" disabled={!selected} onChange={(event) => setLines((current) => current.map((line) => line.orderLineId === orderLine.id ? { ...line, condition: event.currentTarget.value as CustomerReturnCondition } : line))} value={selected?.condition ?? "SEALED"}>{customerReturnConditions.map((condition) => <option key={condition} value={condition}>{customerReturnConditionLabels[condition]}</option>)}</select><p className="text-xs font-bold text-[#697684] md:col-span-4">Đã mua {formatVietnamNumber(orderLine.purchasedQuantity)}, đã trả trước {formatVietnamNumber(orderLine.previouslyReturnedQuantity)}, còn được trả {formatVietnamNumber(remaining)} · Batch {orderLine.batchCode ?? "Không xác định lô"}</p></div>;
                    })}</section> : null}
                    {order ? <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3"><select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setResolution(event.currentTarget.value as CustomerReturnResolution)} value={resolution}>{Object.entries(customerReturnResolutionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setEvidence(event.currentTarget.value)} placeholder="Ảnh/evidence file name" value={evidence} /><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setNote(event.currentTarget.value)} placeholder="Ghi chú khách hàng" value={note} /></section> : null}
                </div>
            </section>
        </PosShell>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}
