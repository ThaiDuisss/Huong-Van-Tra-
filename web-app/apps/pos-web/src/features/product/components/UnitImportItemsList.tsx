import { ChevronDown, ChevronUp, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import type { ImportItemsListProps } from "./ImportBatchPanel";
import { unitApi } from "../services/unit.api";
import type { ImportBatchItem } from "../types/import-batch.types";
import type { Unit } from "../types/product.types";

interface ItemRowProps {
    item: ImportBatchItem;
    editable: boolean;
    expanded: boolean;
    onToggle(): void;
    onSave(payload: Record<string, unknown>): Promise<void>;
    onDelete(): Promise<void>;
    units: Unit[];
}

function ItemRow({ item, editable, expanded, onToggle, onSave, onDelete, units }: ItemRowProps) {
    const [name, setName] = useState(String(item.payload.name ?? ""));
    const [symbol, setSymbol] = useState(String(item.payload.symbol ?? ""));
    const [type, setType] = useState(String(item.payload.type ?? "COUNT"));
    const [baseUnitId, setBaseUnitId] = useState(item.payload.baseUnitId ? String(item.payload.baseUnitId) : "");
    const [conversionRate, setConversionRate] = useState(item.payload.conversionRate ? Number(item.payload.conversionRate) : "");
    const [precisionScale, setPrecisionScale] = useState(Number(item.payload.precisionScale ?? 0));
    const [status, setStatus] = useState(String(item.payload.status ?? "ACTIVE"));
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setName(String(item.payload.name ?? ""));
        setSymbol(String(item.payload.symbol ?? ""));
        setType(String(item.payload.type ?? "COUNT"));
        setBaseUnitId(item.payload.baseUnitId ? String(item.payload.baseUnitId) : "");
        setConversionRate(item.payload.conversionRate ? Number(item.payload.conversionRate) : "");
        setPrecisionScale(Number(item.payload.precisionScale ?? 0));
        setStatus(String(item.payload.status ?? "ACTIVE"));
    }, [item]);

    const errorByField = new Map(item.validationErrors.map((e) => [e.field, e.message]));
    const isValid = item.validationErrors.length === 0;

    async function handleSave() {
        setSaving(true);
        try {
            await onSave({
                name,
                symbol,
                type,
                baseUnitId: baseUnitId || null,
                conversionRate: conversionRate ? Number(conversionRate) : null,
                precisionScale,
                status
            });
        } finally {
            setSaving(false);
        }
    }

    return (
        <div className="overflow-hidden rounded-xl border border-pos-border bg-white">
            <button
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-neutral-50"
                onClick={onToggle}
                type="button"
            >
                <div className="flex min-w-0 items-center gap-3">
                    {expanded ? <ChevronUp size={16} aria-hidden="true" /> : <ChevronDown size={16} aria-hidden="true" />}
                    <div className="min-w-0">
                        <div className="truncate font-bold text-[#172317]">{name || `Dòng ${item.rowIndex}`}</div>
                        <div className="truncate text-xs text-[#697684]">{symbol || "Chưa chọn ký hiệu"}</div>
                    </div>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                    {item.occurrenceCount > 1 ? (
                        <span className="inline-flex h-6 items-center rounded-full bg-amber-50 px-2 text-[11px] font-bold text-amber-700">
                            Trùng {item.occurrenceCount} dòng — đã gộp
                        </span>
                    ) : null}
                    <span
                        className={`inline-flex h-6 items-center rounded-full px-2 text-[11px] font-bold ${
                            isValid ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"
                        }`}
                    >
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </span>
                </div>
            </button>

            {expanded ? (
                <div className="grid gap-3 border-t border-pos-border p-4">
                    {item.occurrenceCount > 1 ? (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-700">
                            Dòng này xuất hiện {item.occurrenceCount} lần giống hệt nhau trong file Excel gốc — hệ thống đã tự
                            gộp thành 1 dòng duy nhất.
                        </div>
                    ) : null}

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Tên đơn vị *</label>
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setName(e.target.value)}
                            type="text"
                            value={name}
                        />
                        {errorByField.get("name") ? (
                            <p className="text-xs font-bold text-red-600">{errorByField.get("name")}</p>
                        ) : null}
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Ký hiệu *</label>
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setSymbol(e.target.value)}
                            type="text"
                            value={symbol}
                        />
                        {errorByField.get("symbol") ? (
                            <p className="text-xs font-bold text-red-600">{errorByField.get("symbol")}</p>
                        ) : null}
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Loại *</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setType(e.target.value)}
                                value={type}
                            >
                                <option value="COUNT">Cái (Count)</option>
                                <option value="WEIGHT">Cân nặng (Weight)</option>
                                <option value="VOLUME">Thể tích (Volume)</option>
                                <option value="PACKAGING">Đóng gói (Packaging)</option>
                            </select>
                            {errorByField.get("type") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("type")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Độ chính xác</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                min="0"
                                max="10"
                                onChange={(e) => setPrecisionScale(Number(e.target.value))}
                                type="number"
                                value={precisionScale}
                            />
                            {errorByField.get("precisionScale") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("precisionScale")}</p>
                            ) : null}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Đơn vị gốc</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setBaseUnitId(e.target.value)}
                                value={baseUnitId}
                            >
                                <option value="">-- Chọn đơn vị gốc --</option>
                                {units.map((u) => (
                                    <option key={u.id} value={u.id}>
                                        {u.symbol} - {u.name}
                                    </option>
                                ))}
                            </select>
                            {errorByField.get("baseUnitId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("baseUnitId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Tỉ lệ quy đổi</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setConversionRate(e.target.value ? Number(e.target.value) : "")}
                                placeholder="Ví dụ: 1000 (nếu có đơn vị gốc)"
                                step="any"
                                type="number"
                                value={conversionRate}
                            />
                            {errorByField.get("conversionRate") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("conversionRate")}</p>
                            ) : null}
                        </div>
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Trạng thái</label>
                        <select
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setStatus(e.target.value)}
                            value={status}
                        >
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </select>
                    </div>

                    {editable ? (
                        <div className="flex items-center justify-between pt-1">
                            <button
                                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3 text-xs font-bold text-red-600 hover:bg-red-50"
                                onClick={() => void onDelete()}
                                type="button"
                            >
                                <Trash2 size={14} aria-hidden="true" />
                                Xóa dòng
                            </button>
                            <button
                                className="inline-flex h-9 items-center rounded-lg bg-[#159734] px-4 text-xs font-black text-white hover:bg-[#12802c] disabled:opacity-50"
                                disabled={saving}
                                onClick={() => void handleSave()}
                                type="button"
                            >
                                {saving ? "Đang lưu..." : "Lưu dòng này"}
                            </button>
                        </div>
                    ) : null}
                </div>
            ) : null}
        </div>
    );
}

export function UnitImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
    const [units, setUnits] = useState<Unit[]>([]);

    useEffect(() => {
        unitApi
            .listAll()
            .then((res) => setUnits(res ?? []))
            .catch((err: unknown) => console.error("Lỗi tải đơn vị", err));
    }, []);

    return (
        <>
            {items.map((item) => (
                <ItemRow
                    editable={editable}
                    expanded={expandedId === item.id}
                    item={item}
                    key={item.id}
                    onDelete={() => onDelete(item.id)}
                    onSave={(payload) => onSave(item.id, payload)}
                    onToggle={() => onToggle(item.id)}
                    units={units}
                />
            ))}
        </>
    );
}
