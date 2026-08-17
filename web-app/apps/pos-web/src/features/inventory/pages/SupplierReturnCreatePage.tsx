import { Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    supplierReturnReasonLabels,
    supplierReturnReasons,
    supplierReturnResolutionLabels
} from "../constants/supplier-return.constants";
import { supplierReturnApi } from "../services/supplierReturn.api";
import type {
    SupplierReturnOptions,
    SupplierReturnReason,
    SupplierReturnResolution,
    SupplierReturnRole
} from "../types/supplier-return.types";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

interface DraftLine {
    batchId: string;
    returnQuantity: number;
    reason: SupplierReturnReason;
    evidence: string;
}

function readRole(): SupplierReturnRole {
    const stored = window.localStorage.getItem("hvt.supplierReturn.role");
    return stored && ["WAREHOUSE", "QC_STAFF", "ACCOUNTANT", "ADMIN", "DIRECTOR"].includes(stored) ? stored as SupplierReturnRole : "WAREHOUSE";
}

export function SupplierReturnCreatePage() {
    const navigate = useNavigate();
    const [role] = useState<SupplierReturnRole>(readRole);
    const [options, setOptions] = useState<SupplierReturnOptions>({ batches: [], suppliers: [], users: [] });
    const [form, setForm] = useState({ note: "", pickupMethod: "NCC đến lấy", reason: "SUP-01" as SupplierReturnReason, receiptId: "", resolutionRequested: "CREDIT_NOTE" as SupplierReturnResolution, supplierId: "", supplierInvoiceCode: "" });
    const [lines, setLines] = useState<DraftLine[]>([]);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        void supplierReturnApi.getSupplierReturnOptions().then((response) => {
            const firstBatch = response.batches[0];
            setOptions(response);
            setForm((current) => ({ ...current, receiptId: firstBatch?.receiptId ?? "", supplierId: firstBatch?.supplierId ?? "", supplierInvoiceCode: firstBatch?.supplierInvoiceCode ?? "" }));
        });
    }, []);

    const availableBatches = useMemo(() => options.batches.filter((batch) => batch.supplierId === form.supplierId && batch.receiptId === form.receiptId), [form.receiptId, form.supplierId, options.batches]);
    const selectedLines = lines.map((line) => ({ ...line, batch: options.batches.find((batch) => batch.id === line.batchId) })).filter((line): line is DraftLine & { batch: SupplierReturnOptions["batches"][number] } => Boolean(line.batch));
    const totalValue = selectedLines.reduce((sum, line) => sum + line.returnQuantity * line.batch.unitCost, 0);

    function addLine() {
        const batch = availableBatches.find((item) => !lines.some((line) => line.batchId === item.id));
        if (batch) setLines((current) => [...current, { batchId: batch.id, evidence: "", reason: form.reason, returnQuantity: 1 }]);
    }

    async function create(submit: boolean) {
        try {
            setSubmitting(true);
            setError("");
            const created = await supplierReturnApi.createSupplierReturn({ actorRole: role, lines: selectedLines.map((line) => ({ batchId: line.batchId, evidence: line.evidence ? [line.evidence] : [], reason: line.reason, returnQuantity: line.returnQuantity })), note: form.note, pickupMethod: form.pickupMethod, reason: form.reason, receiptId: form.receiptId, resolutionRequested: form.resolutionRequested, submit, supplierId: form.supplierId, supplierInvoiceCode: form.supplierInvoiceCode });
            navigate(`/management/inventory/supplier-returns/${created.id}`);
        } catch (createError) {
            setError(createError instanceof Error ? createError.message : "Không thể tạo phiếu trả NCC.");
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4"><header><Link className="text-sm font-black text-[#159734]" to="/management/inventory/supplier-returns">Quay lại danh sách</Link><h1 className="text-2xl font-black text-[#172317]">Tạo yêu cầu trả nhà cung cấp</h1><p className="text-sm font-semibold text-[#697684]">Bắt buộc chọn supplier, receipt và batch gốc. Nếu hàng AVAILABLE, hệ thống cảnh báo block/chuyển staging khi prepare.</p></header><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3"><Select label="Supplier" onChange={(supplierId) => { const batch = options.batches.find((item) => item.supplierId === supplierId); setForm({ ...form, receiptId: batch?.receiptId ?? "", supplierId, supplierInvoiceCode: batch?.supplierInvoiceCode ?? "" }); setLines([]); }} options={options.suppliers.map((item) => ({ label: item.name, value: item.id }))} value={form.supplierId} /><Select label="Original receipt" onChange={(receiptId) => { const batch = options.batches.find((item) => item.receiptId === receiptId && item.supplierId === form.supplierId); setForm({ ...form, receiptId, supplierInvoiceCode: batch?.supplierInvoiceCode ?? form.supplierInvoiceCode }); setLines([]); }} options={[...new Map(options.batches.filter((item) => item.supplierId === form.supplierId).map((item) => [item.receiptId, { label: item.receiptCode, value: item.receiptId }])).values()]} value={form.receiptId} /><input className="h-10 self-end rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, supplierInvoiceCode: event.currentTarget.value })} placeholder="Original supplier invoice" value={form.supplierInvoiceCode} /><Select label="Return reason" onChange={(reason) => setForm({ ...form, reason: reason as SupplierReturnReason })} options={supplierReturnReasons.map((reason) => ({ label: `${reason} ${supplierReturnReasonLabels[reason]}`, value: reason }))} value={form.reason} /><Select label="Resolution requested" onChange={(resolutionRequested) => setForm({ ...form, resolutionRequested: resolutionRequested as SupplierReturnResolution })} options={Object.entries(supplierReturnResolutionLabels).map(([value, label]) => ({ label, value }))} value={form.resolutionRequested} /><input className="h-10 self-end rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, pickupMethod: event.currentTarget.value })} placeholder="Pickup method" value={form.pickupMethod} /></section><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex items-center justify-between"><h2 className="font-black">Lines</h2><button className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" onClick={addLine} type="button"><Plus size={16} />Thêm batch</button></div>{selectedLines.map((line, index) => <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-[1.3fr_120px_160px_1fr_44px]" key={line.batchId}><Select label="Batch" onChange={(batchId) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, batchId } : item))} options={availableBatches.map((batch) => ({ label: `${batch.batchCode} · ${batch.skuCode}`, value: batch.id }))} value={line.batchId} /><input className="h-10 self-end rounded-lg border border-pos-border px-3 text-sm font-bold" min={1} onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, returnQuantity: Number(event.currentTarget.value) } : item))} type="number" value={line.returnQuantity} /><Select label="Reason" onChange={(reason) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, reason: reason as SupplierReturnReason } : item))} options={supplierReturnReasons.map((reason) => ({ label: reason, value: reason }))} value={line.reason} /><input className="h-10 self-end rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, evidence: event.currentTarget.value } : item))} placeholder="Evidence" value={line.evidence} /><button className="mt-5 grid size-10 place-items-center rounded-lg border border-red-200 text-red-700" onClick={() => setLines((current) => current.filter((_, itemIndex) => itemIndex !== index))} type="button"><Trash2 size={16} /></button><p className="text-xs font-bold text-[#697684] md:col-span-5">Received {formatVietnamNumber(line.batch.receivedQuantity)} · Available {formatVietnamNumber(line.batch.currentAvailableQuantity)} · Quarantine {formatVietnamNumber(line.batch.currentQuarantineQuantity)} · Returnable {formatVietnamNumber(line.batch.currentAvailableQuantity + line.batch.currentQuarantineQuantity)} · Unit cost {formatVnd(line.batch.unitCost)} · QC {line.batch.qcResult}</p>{line.batch.currentAvailableQuantity > 0 ? <p className="text-xs font-black text-amber-700 md:col-span-5">Cảnh báo: hàng AVAILABLE sẽ bị block/chuyển khu chờ trả khi prepare.</p> : null}</div>)}{selectedLines.length === 0 ? <div className="rounded-lg border border-dashed border-[#cfdad2] p-6 text-center text-sm font-bold text-[#697684]">Chưa chọn batch.</div> : null}<footer className="flex flex-wrap items-center justify-end gap-2 border-t border-[#edf0f4] pt-3"><strong className="text-sm font-black text-[#172317]">Giá trị dự kiến: {formatVnd(totalValue)}</strong>{error ? <span className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{error}</span> : null}<button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void create(false)} type="button">Lưu nháp</button><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void create(true)} type="button">Submit</button></footer></section></section>;
}

function Select({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} value={value}>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
