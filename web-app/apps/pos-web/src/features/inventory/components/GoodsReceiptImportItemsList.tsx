import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import type { ImportItemsListProps } from "../../product/components/ImportBatchPanel";
import type { ImportBatchItem } from "../../product/types/import-batch.types";
import { unitApi } from "../../product/services/unit.api";
import type { Unit } from "../../product/types/product.types";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type { GoodsReceiptMaterialOption, GoodsReceiptOptions } from "../types/goods-receipt.types";

interface ReceiptLineInput {
    inventoryItemId: string;
    unitId: string;
    orderedQuantity: number;
    note: string;
    /** true = nguyên liệu chưa có trong hệ thống, sẽ được tạo mới lúc duyệt (xem newIngredientName/newIngredientUnitId/newIngredientCode). */
    isNewIngredient: boolean;
    newIngredientCode: string;
    newIngredientName: string;
    newIngredientUnitId: string;
}

function toReceiptLineInputs(value: unknown): ReceiptLineInput[] {
    if (!Array.isArray(value)) return [];
    return value.map((raw) => {
        const line = (raw ?? {}) as Record<string, unknown>;
        const newIngredientName = line.newIngredientName ? String(line.newIngredientName) : "";
        const newIngredientCode = line.newIngredientCode ? String(line.newIngredientCode) : "";
        return {
            inventoryItemId: line.inventoryItemId ? String(line.inventoryItemId) : "",
            unitId: line.unitId ? String(line.unitId) : "",
            orderedQuantity:
                typeof line.orderedQuantity === "number" ? line.orderedQuantity : Number(line.orderedQuantity ?? 0),
            note: line.note ? String(line.note) : "",
            isNewIngredient: Boolean(newIngredientName),
            newIngredientCode,
            newIngredientName,
            newIngredientUnitId: line.newIngredientUnitId ? String(line.newIngredientUnitId) : ""
        };
    });
}

interface ItemRowProps {
    item: ImportBatchItem;
    editable: boolean;
    expanded: boolean;
    onToggle(): void;
    onSave(payload: Record<string, unknown>): Promise<void>;
    onDelete(): Promise<void>;
    options: GoodsReceiptOptions;
    materialsBySupplierId: Record<string, GoodsReceiptMaterialOption[]>;
    materialsLoadingSupplierId: string | null;
    onEnsureMaterials(supplierId: string): void;
    units: Unit[];
}

function toDateInputValue(value: unknown): string {
    if (!value) return "";
    const date = new Date(String(value));
    return Number.isNaN(date.getTime()) ? "" : date.toISOString().slice(0, 10);
}

function ItemRow({
    item,
    editable,
    expanded,
    onToggle,
    onSave,
    onDelete,
    options,
    materialsBySupplierId,
    materialsLoadingSupplierId,
    onEnsureMaterials,
    units
}: ItemRowProps) {
    const [supplierId, setSupplierId] = useState(item.payload.supplierId ? String(item.payload.supplierId) : "");
    const [locationId, setLocationId] = useState(item.payload.locationId ? String(item.payload.locationId) : "");
    const [expectedDate, setExpectedDate] = useState(toDateInputValue(item.payload.expectedDate));
    const [note, setNote] = useState(item.payload.note ? String(item.payload.note) : "");
    const [lines, setLines] = useState<ReceiptLineInput[]>(toReceiptLineInputs(item.payload.items));
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setSupplierId(item.payload.supplierId ? String(item.payload.supplierId) : "");
        setLocationId(item.payload.locationId ? String(item.payload.locationId) : "");
        setExpectedDate(toDateInputValue(item.payload.expectedDate));
        setNote(item.payload.note ? String(item.payload.note) : "");
        setLines(toReceiptLineInputs(item.payload.items));
    }, [item]);

    // Nguyên liệu phụ thuộc nhà cung cấp (mỗi NCC chỉ cung cấp 1 số nguyên liệu nhất định) — mỗi khi
    // đổi nhà cung cấp của dòng này thì tải lại danh sách nguyên liệu tương ứng (có cache ở component cha).
    useEffect(() => {
        if (supplierId) {
            onEnsureMaterials(supplierId);
        }
    }, [supplierId, onEnsureMaterials]);

    const errorByField = new Map(item.validationErrors.map((e) => [e.field, e.message]));
    const isValid = item.validationErrors.length === 0;

    const supplierDisplayName =
        options.suppliers.find((s) => String(s.id) === supplierId)?.name ?? (supplierId ? "Nhà cung cấp đã chọn" : null);

    const locationName = options.locations.find((l) => String(l.id) === locationId)?.name;

    const materials = supplierId ? materialsBySupplierId[supplierId] ?? [] : [];

    const materialsLoading = Boolean(supplierId) && materialsLoadingSupplierId === supplierId;

    function updateLine(index: number, field: keyof ReceiptLineInput, value: string | number) {
        setLines((prev) => prev.map((line, i) => (i === index ? { ...line, [field]: value } : line)));
    }

    function addLine() {
        setLines((prev) => [
            ...prev,
            {
                inventoryItemId: "",
                unitId: "",
                orderedQuantity: 1,
                note: "",
                isNewIngredient: false,
                newIngredientCode: "",
                newIngredientName: "",
                newIngredientUnitId: ""
            }
        ]);
    }

    function removeLine(index: number) {
        setLines((prev) => prev.filter((_, i) => i !== index));
    }

    function toggleNewIngredient(index: number, isNew: boolean) {
        setLines((prev) =>
            prev.map((line, i) =>
                i === index
                    ? {
                        ...line,
                        isNewIngredient: isNew,
                        inventoryItemId: isNew ? "" : line.inventoryItemId,
                        newIngredientCode: isNew ? line.newIngredientCode : "",
                        newIngredientName: isNew ? line.newIngredientName : "",
                        newIngredientUnitId: isNew ? line.newIngredientUnitId : ""
                    }
                    : line
            )
        );
    }

    async function handleSave() {
        setSaving(true);
        try {
            const payloadToSave: Record<string, unknown> = {
                supplierId: supplierId || null,
                locationId: locationId || null,
                branchId: null,
                expectedDate: expectedDate ? new Date(`${expectedDate}T00:00:00`).toISOString() : null,
                referenceType: null,
                referenceId: null,
                note: note || null,
                items: lines.map((line) =>
                    line.isNewIngredient
                        ? {
                            inventoryItemId: null,
                            unitId: null,
                            orderedQuantity: line.orderedQuantity,
                            note: line.note || null,
                            newIngredientCode: line.newIngredientCode.trim() || null,
                            newIngredientName: line.newIngredientName.trim() || null,
                            newIngredientUnitId: line.newIngredientUnitId || null
                        }
                        : {
                            inventoryItemId: line.inventoryItemId || null,
                            unitId: line.unitId || null,
                            orderedQuantity: line.orderedQuantity,
                            note: line.note || null
                        }
                )
            };

            await onSave(payloadToSave);
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
                        <div className="truncate font-bold text-[#172317]">
                            {supplierDisplayName ? `${supplierDisplayName} — ${locationName ?? "?"}` : `Dòng ${item.rowIndex}`}
                        </div>
                        <div className="truncate text-xs text-[#697684]">{lines.length} dòng nguyên liệu</div>
                    </div>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                    <span
                        className={`inline-flex h-6 items-center rounded-full px-2 text-[11px] font-bold ${isValid ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"
                            }`}
                    >
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </span>
                </div>
            </button>

            {expanded ? (
                <div className="grid gap-3 border-t border-pos-border p-4">
                    {/* Phần Thông tin phiếu nhập */}
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Nhà cung cấp *</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setSupplierId(e.target.value)}
                                value={supplierId}
                            >
                                <option value="">-- Chọn nhà cung cấp --</option>
                                {options.suppliers.map((s) => (
                                    <option key={s.id} value={s.id}>
                                        {s.name} ({s.code})
                                    </option>
                                ))}
                            </select>
                            {errorByField.get("supplierId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("supplierId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Kho nhận *</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setLocationId(e.target.value)}
                                value={locationId}
                            >
                                <option value="">-- Chọn kho nhận --</option>
                                {options.locations.map((l) => (
                                    <option key={l.id} value={l.id}>
                                        {l.name} ({l.code})
                                    </option>
                                ))}
                            </select>
                            {errorByField.get("locationId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("locationId")}</p>
                            ) : null}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Ngày dự kiến giao</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setExpectedDate(e.target.value)}
                                type="date"
                                value={expectedDate}
                            />
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Ghi chú</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setNote(e.target.value)}
                                placeholder="Ghi chú phiếu nhập..."
                                type="text"
                                value={note}
                            />
                        </div>
                    </div>

                    <div className="grid gap-2 border-t border-pos-border pt-3">
                        <div className="flex items-center justify-between">
                            <label className="text-xs font-bold text-[#536078]">Dòng nguyên liệu</label>
                            {editable ? (
                                <button
                                    className="inline-flex h-8 items-center gap-1 rounded-lg border border-[#07883a] bg-white px-2.5 text-xs font-bold text-[#07883a] hover:bg-green-50"
                                    onClick={addLine}
                                    type="button"
                                >
                                    <Plus size={13} aria-hidden="true" />
                                    Thêm dòng
                                </button>
                            ) : null}
                        </div>

                        {errorByField.get("items") ? (
                            <p className="text-xs font-bold text-red-600">{errorByField.get("items")}</p>
                        ) : null}

                        {lines.length === 0 ? (
                            <div className="rounded-lg border border-dashed border-pos-border p-3 text-center text-xs font-medium text-gray-400">
                                Chưa có dòng nguyên liệu nào.
                            </div>
                        ) : (
                            <div className="overflow-x-auto rounded-lg border border-pos-border">
                                <table className="w-full text-left text-xs">
                                    <thead className="bg-neutral-50 text-[11px] font-bold uppercase text-gray-500">
                                        <tr>
                                            <th className="p-2 w-5/12">Nguyên liệu *</th>
                                            <th className="p-2 w-2/12">Số lượng đặt *</th>
                                            <th className="p-2 w-3/12">Ghi chú</th>
                                            <th className="p-2 w-1/12 text-center">Xóa</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-pos-border bg-white">
                                        {lines.map((line, index) => (
                                            <tr key={index}>
                                                <td className="p-2 align-top">

                                                    {line.isNewIngredient ? (
                                                        <div className="grid gap-1.5">
                                                            <input
                                                                className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                                disabled={!editable}
                                                                onChange={(e) => updateLine(index, "newIngredientName", e.target.value)}
                                                                placeholder="Tên nguyên liệu mới *..."
                                                                type="text"
                                                                value={line.newIngredientName}
                                                            />
                                                            <div className="grid grid-cols-2 gap-1.5">
                                                                <input
                                                                    className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                                    disabled={!editable}
                                                                    onChange={(e) => updateLine(index, "newIngredientCode", e.target.value)}
                                                                    placeholder="Mã NL (để trống tự sinh)..."
                                                                    type="text"
                                                                    value={line.newIngredientCode}
                                                                />
                                                                <select
                                                                    className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                                    disabled={!editable}
                                                                    onChange={(e) => updateLine(index, "newIngredientUnitId", e.target.value)}
                                                                    value={line.newIngredientUnitId}
                                                                >
                                                                    <option value="">-- Chọn đơn vị * --</option>
                                                                    {units.map((u) => (
                                                                        <option key={u.id} value={u.id}>
                                                                            {u.symbol} ({u.name})
                                                                        </option>
                                                                    ))}
                                                                </select>
                                                            </div>
                                                            {errorByField.get(`items[${index}].newIngredientUnitId`) ? (
                                                                <p className="text-[11px] font-bold text-red-600">
                                                                    {errorByField.get(`items[${index}].newIngredientUnitId`)}
                                                                </p>
                                                            ) : null}
                                                        </div>
                                                    ) : (
                                                        <>
                                                            <select
                                                                className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                                disabled={!editable || !supplierId}
                                                                onChange={(e) => updateLine(index, "inventoryItemId", e.target.value)}
                                                                value={line.inventoryItemId}
                                                            >
                                                                <option value="">
                                                                    {!supplierId
                                                                        ? "-- Chọn nhà cung cấp trước --"
                                                                        : materialsLoading
                                                                            ? "Đang tải nguyên liệu..."
                                                                            : "-- Chọn nguyên liệu --"}
                                                                </option>
                                                                {materials.map((m) => (
                                                                    <option key={m.id} value={m.id}>
                                                                        {m.name} ({m.skuCode}) — {m.unit}
                                                                    </option>
                                                                ))}
                                                            </select>
                                                            {supplierId && !materialsLoading && materials.length === 0 ? (
                                                                <p className="mt-1 text-[11px] font-bold text-amber-600">
                                                                    Nhà cung cấp này chưa có nguyên liệu nào.
                                                                </p>
                                                            ) : null}
                                                        </>
                                                    )}
                                                    <label className="mt-2 flex items-center gap-1.5 text-[11px] font-bold text-[#536078] cursor-pointer">
                                                        <input
                                                            checked={line.isNewIngredient}
                                                            disabled={!editable}
                                                            onChange={(e) => toggleNewIngredient(index, e.target.checked)}
                                                            type="checkbox"
                                                        />
                                                        Nguyên liệu mới (chưa có trong hệ thống)
                                                    </label>
                                                    {errorByField.get(`items[${index}].inventoryItemId`) ? (
                                                        <p className="mt-1 text-[11px] font-bold text-red-600">
                                                            {errorByField.get(`items[${index}].inventoryItemId`)}
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="p-2 align-top">
                                                    <input
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        onChange={(e) => updateLine(index, "orderedQuantity", Number(e.target.value))}
                                                        step="any"
                                                        type="number"
                                                        value={line.orderedQuantity}
                                                    />
                                                    {errorByField.get(`items[${index}].orderedQuantity`) ? (
                                                        <p className="mt-1 text-[11px] font-bold text-red-600">
                                                            {errorByField.get(`items[${index}].orderedQuantity`)}
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="p-2 align-top">
                                                    <input
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        onChange={(e) => updateLine(index, "note", e.target.value)}
                                                        type="text"
                                                        value={line.note}
                                                    />
                                                </td>
                                                <td className="p-2 text-center align-top">
                                                    {editable ? (
                                                        <button
                                                            aria-label="Xóa dòng"
                                                            className="grid size-8 place-items-center rounded-lg text-red-500 hover:bg-red-50"
                                                            onClick={() => removeLine(index)}
                                                            type="button"
                                                        >
                                                            <Trash2 size={14} aria-hidden="true" />
                                                        </button>
                                                    ) : null}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
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

export function GoodsReceiptImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
    const [options, setOptions] = useState<GoodsReceiptOptions>({ locations: [], materials: [], suppliers: [] });
    // Nguyên liệu phụ thuộc nhà cung cấp — cache theo supplierId để nhiều dòng cùng NCC không tải lại nhiều lần.
    const [materialsBySupplierId, setMaterialsBySupplierId] = useState<Record<string, GoodsReceiptMaterialOption[]>>({});
    const [materialsLoadingSupplierId, setMaterialsLoadingSupplierId] = useState<string | null>(null);
    const requestedSupplierIds = useRef(new Set<string>());
    const [units, setUnits] = useState<Unit[]>([]);

    useEffect(() => {
        goodsReceiptApi
            .getGoodsReceiptOptions()
            .then(setOptions)
            .catch((err: unknown) => console.error("Lỗi tải danh mục nhà cung cấp/kho/nguyên liệu", err));
    }, []);

    // Dùng khi tạo nguyên liệu mới ngay trong lúc import (chưa có trong hệ thống) — cần chọn đơn vị.
    useEffect(() => {
        unitApi
            .listAll()
            .then((res) => setUnits(res ?? []))
            .catch((err: unknown) => console.error("Lỗi tải đơn vị", err));
    }, []);

    const ensureMaterials = useCallback((supplierId: string) => {
        if (requestedSupplierIds.current.has(supplierId)) return;
        requestedSupplierIds.current.add(supplierId);
        setMaterialsLoadingSupplierId(supplierId);
        goodsReceiptApi
            .getGoodsReceiptOptions(supplierId)
            .then((res) => {
                setMaterialsBySupplierId((prev) => ({ ...prev, [supplierId]: res.materials }));
            })
            .catch((err: unknown) => {
                console.error("Lỗi tải nguyên liệu theo nhà cung cấp", err);
                requestedSupplierIds.current.delete(supplierId);
            })
            .finally(() => {
                setMaterialsLoadingSupplierId((current) => (current === supplierId ? null : current));
            });
    }, []);

    return (
        <>
            {items.map((item) => (
                <ItemRow
                    editable={editable}
                    expanded={expandedId === item.id}
                    item={item}
                    key={item.id}
                    materialsBySupplierId={materialsBySupplierId}
                    materialsLoadingSupplierId={materialsLoadingSupplierId}
                    onDelete={() => onDelete(item.id)}
                    onEnsureMaterials={ensureMaterials}
                    onSave={(payload) => onSave(item.id, payload)}
                    onToggle={() => onToggle(item.id)}
                    options={options}
                    units={units}
                />
            ))}
        </>
    );
}
