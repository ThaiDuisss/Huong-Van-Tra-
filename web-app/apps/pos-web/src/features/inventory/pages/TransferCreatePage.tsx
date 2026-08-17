import { Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import { transferPriorityLabels, transferPriorityOptions } from "../constants/transfer.constants";
import { inventoryMasterLocations } from "../mocks/inventory-master-data.mock";
import { transferApi } from "../services/transfer.api";
import type { TransferCreateInput, TransferOptions, TransferPriority, TransferRole } from "../types/transfer.types";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

interface DraftLine {
    skuId: string;
    requestedQuantity: number;
    note: string;
}

function readRole(): TransferRole {
    const stored = window.localStorage.getItem("hvt.transfer.role");
    return stored && ["BRANCH_MANAGER", "WAREHOUSE", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored)
        ? stored as TransferRole
        : "BRANCH_MANAGER";
}

export function TransferCreatePage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [role] = useState<TransferRole>(readRole);
    const [options, setOptions] = useState<TransferOptions>({ locations: [], skus: [], users: [] });
    const [form, setForm] = useState({
        destinationLocationId: "",
        expectedReceiveAt: "2026-08-03T10:00",
        priority: "NORMAL" as TransferPriority,
        reason: "",
        sourceLocationId: "loc-central"
    });
    const [lines, setLines] = useState<DraftLine[]>([]);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        void transferApi.getTransferOptions().then((response) => {
            const sourceLocationId = searchParams.get("sourceLocationId");
            const destinationLocationId = searchParams.get("destinationLocationId");
            const skuId = searchParams.get("skuId");
            const quantity = Number(searchParams.get("quantity") ?? 0);
            const reason = searchParams.get("reason") ?? "";
            setOptions(response);
            setForm((current) => ({
                ...current,
                destinationLocationId: destinationLocationId ?? response.locations.find((item) => !item.isCentral)?.id ?? "",
                reason: reason || current.reason,
                sourceLocationId: sourceLocationId ?? response.locations.find((item) => item.isCentral)?.id ?? "loc-central"
            }));
            if (skuId && quantity > 0) {
                setLines([{ note: "Prefill từ cảnh báo tồn kho", requestedQuantity: quantity, skuId }]);
            }
        });
    }, [searchParams]);

    const selectedLines = lines.map((line) => {
        const sku = options.skus.find((item) => item.id === line.skuId);
        return { ...line, sku };
    }).filter((line): line is DraftLine & { sku: TransferOptions["skus"][number] } => Boolean(line.sku));

    const totalValue = selectedLines.reduce((sum, line) => sum + line.requestedQuantity * line.sku.unitCost, 0);

    const destinationOptions = useMemo(() => {
        if (role !== "BRANCH_MANAGER") return options.locations;
        return options.locations.filter((item) => item.id === inventoryMasterLocations.branchCauGiay.id);
    }, [options.locations, role]);

    function addLine() {
        const sku = options.skus.find((item) => !lines.some((line) => line.skuId === item.id));
        if (sku) setLines((current) => [...current, { note: "", requestedQuantity: 1, skuId: sku.id }]);
    }

    async function submitTransfer(submit: boolean) {
        setError("");
        const input: TransferCreateInput = {
            actorRole: role,
            destinationLocationId: form.destinationLocationId,
            expectedReceiveAt: new Date(form.expectedReceiveAt).toISOString(),
            lines: selectedLines.map((line) => ({ note: line.note, requestedQuantity: line.requestedQuantity, skuId: line.skuId })),
            priority: form.priority,
            reason: form.reason,
            sourceLocationId: form.sourceLocationId,
            submit
        };
        try {
            setSubmitting(true);
            const transfer = await transferApi.createTransfer(input);
            navigate(`/management/inventory/transfers/${transfer.id}`);
        } catch (createError) {
            setError(createError instanceof Error ? createError.message : "Không thể tạo yêu cầu chuyển kho.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
                <div>
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/transfers">Quay lại danh sách</Link>
                    <h1 className="text-2xl font-black text-[#172317]">Tạo yêu cầu chuyển kho</h1>
                    <p className="text-sm font-semibold text-[#697684]">Source mặc định là kho trung tâm. Nếu tạo từ cảnh báo tồn, các dòng có thể được prefill bằng cùng payload mock.</p>
                </div>
                <div className="rounded-lg bg-[#f4faf5] px-3 py-2 text-sm font-black text-[#315f3c]">
                    Vai trò: {role}
                </div>
            </header>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-2 xl:grid-cols-3">
                <SelectField label="Kho nguồn" onChange={(sourceLocationId) => setForm({ ...form, sourceLocationId })} options={options.locations.map((item) => ({ label: item.name, value: item.id }))} value={form.sourceLocationId} />
                <SelectField label="Kho đích" onChange={(destinationLocationId) => setForm({ ...form, destinationLocationId })} options={destinationOptions.map((item) => ({ label: item.name, value: item.id }))} value={form.destinationLocationId} />
                <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                    Dự kiến giao
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, expectedReceiveAt: event.currentTarget.value })} type="datetime-local" value={form.expectedReceiveAt} />
                </label>
                <SelectField label="Priority" onChange={(priority) => setForm({ ...form, priority: priority as TransferPriority })} options={transferPriorityOptions.map((item) => ({ label: transferPriorityLabels[item], value: item }))} value={form.priority} />
                <label className="grid gap-1.5 text-xs font-black text-[#536078] md:col-span-2">
                    Lý do
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, reason: event.currentTarget.value })} placeholder="Bổ sung theo cảnh báo tồn thấp..." value={form.reason} />
                </label>
            </section>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex items-center justify-between gap-3">
                    <h2 className="text-lg font-black text-[#172317]">Hàng hóa</h2>
                    <button className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" onClick={addLine} type="button">
                        <Plus size={16} aria-hidden="true" />
                        Thêm SKU
                    </button>
                </div>
                <div className="grid gap-3">
                    {selectedLines.map((line, index) => (
                        <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-[1.4fr_140px_1fr_44px]" key={line.skuId}>
                            <SelectField
                                label="SKU"
                                onChange={(skuId) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, skuId } : item))}
                                options={options.skus.map((sku) => ({ label: `${sku.skuCode} - ${sku.name}`, value: sku.id }))}
                                value={line.skuId}
                            />
                            <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                                Số lượng yêu cầu
                                <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" min={1} onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, requestedQuantity: Number(event.currentTarget.value) } : item))} type="number" value={line.requestedQuantity} />
                            </label>
                            <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                                Ghi chú
                                <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, note: event.currentTarget.value } : item))} value={line.note} />
                            </label>
                            <button className="mt-5 grid size-10 place-items-center rounded-lg border border-red-200 text-red-700" onClick={() => setLines((current) => current.filter((_, itemIndex) => itemIndex !== index))} type="button" aria-label="Xóa dòng">
                                <Trash2 size={16} aria-hidden="true" />
                            </button>
                            <div className="grid gap-1 text-xs font-bold text-[#697684] md:col-span-4">
                                <span>Destination available: {formatVietnamNumber(line.sku.destinationAvailableQuantity)} {line.sku.unit} · Min: {formatVietnamNumber(line.sku.destinationMinimumStock)} {line.sku.unit}</span>
                                <span>Source available: {formatVietnamNumber(line.sku.sourceAvailableQuantity)} {line.sku.unit} · UOM: {line.sku.unit}</span>
                            </div>
                        </div>
                    ))}
                    {selectedLines.length === 0 ? <div className="rounded-lg border border-dashed border-[#cfdad2] p-6 text-center text-sm font-bold text-[#697684]">Chưa có SKU nào trong yêu cầu.</div> : null}
                </div>
                <div className="flex flex-col gap-3 border-t border-[#edf0f4] pt-3 md:flex-row md:items-center md:justify-between">
                    <strong className="text-sm font-black text-[#172317]">Giá trị dự kiến: {formatVnd(totalValue)}</strong>
                    <div className="flex flex-wrap gap-2">
                        {error ? <div className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{error}</div> : null}
                        <button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void submitTransfer(false)} type="button">Lưu nháp</button>
                        <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={() => void submitTransfer(true)} type="button">{submitting ? "Đang tạo..." : "Tạo yêu cầu"}</button>
                    </div>
                </div>
            </section>
        </section>
    );
}

function SelectField({
    label,
    onChange,
    options,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    options: Array<{ label: string; value: string }>;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} value={value}>
                {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
        </label>
    );
}
