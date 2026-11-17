import { ChevronDown, ChevronUp, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { NativeSelect, TextInput } from "../../../components/ui/Field";
import { StatusBadge } from "../../../components/ui/StatusBadge";
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
                        <StatusBadge tone="warning">
                            Trùng {item.occurrenceCount} dòng — đã gộp
                        </StatusBadge>
                    ) : null}
                    <StatusBadge tone={isValid ? "success" : "danger"}>
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </StatusBadge>
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
                        <TextInput
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
                            <NativeSelect
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
                            </NativeSelect>
                            {errorByField.get("unitId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("unitId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Điểm đặt lại</label>
                            <TextInput
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
                        <NativeSelect
                            disabled={!editable}
                            onChange={(e) => setStatus(e.target.value)}
                            value={status}
                        >
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </NativeSelect>
                    </div>

                    {editable ? (
                        <div className="flex items-center justify-between pt-1">
                            <Button onClick={() => void onDelete()} size="sm" variant="dangerOutline">
                                <Trash2 size={14} aria-hidden="true" />
                                Xóa dòng
                            </Button>
                            <Button disabled={saving} onClick={() => void handleSave()} size="sm" variant="primary">
                                {saving ? "Đang lưu..." : "Lưu dòng này"}
                            </Button>
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
