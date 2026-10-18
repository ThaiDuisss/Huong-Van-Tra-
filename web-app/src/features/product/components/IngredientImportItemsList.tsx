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
    const [unitId, setUnitId] = useState(item.payload.unitId ? String(item.payload.unitId) : "");
    const [reorderPoint, setReorderPoint] = useState(Number(item.payload.reorderPoint ?? 0));
    const [status, setStatus] = useState(String(item.payload.status ?? "ACTIVE"));
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setName(String(item.payload.name ?? ""));
        setUnitId(item.payload.unitId ? String(item.payload.unitId) : "");
        setReorderPoint(Number(item.payload.reorderPoint ?? 0));
        setStatus(String(item.payload.status ?? "ACTIVE"));
    }, [item]);

    const errorByField = new Map(item.validationErrors.map((e) => [e.field, e.message]));
    const isValid = item.validationErrors.length === 0;
    const unitSymbol = units.find((u) => u.id === unitId)?.symbol;

    async function handleSave() {
        setSaving(true);
        try {
            await onSave({
                name,
                unitId: unitId || null,
                reorderPoint,
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
                        <div className="truncate text-xs text-[#697684]">{unitSymbol ?? "Chưa chọn đơn vị"}</div>
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
                        <label className="text-xs font-bold text-[#536078]">Tên nguyên liệu *</label>
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

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Đơn vị</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setUnitId(e.target.value)}
                                value={unitId}
                            >
                                <option value="">-- Chọn đơn vị --</option>
                                {units.map((u) => (
                                    <option key={u.id} value={u.id}>
                                        {u.symbol}
                                    </option>
                                ))}
                            </select>
                            {errorByField.get("unitId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("unitId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Điểm đặt lại</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setReorderPoint(Number(e.target.value))}
                                type="number"
                                value={reorderPoint}
                            />
                            {errorByField.get("reorderPoint") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("reorderPoint")}</p>
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

export function IngredientImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
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
