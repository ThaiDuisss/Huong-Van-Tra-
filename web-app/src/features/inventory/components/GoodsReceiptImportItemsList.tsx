import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { Button, IconButton } from "@/components/ui/Button";
import { NativeSelect, TextInput } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";

import type { ImportItemsListProps } from "../../product/components/ImportBatchPanel";
import type { ImportBatchItem } from "../../product/types/import-batch.types";
import { unitApi } from "../../product/services/unit.api";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import { supplierApi } from "../../manufacturing/services/supplier.api";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown } from "../../manufacturing/components/shared/useLookupDropdown";
import type { GoodsReceiptMaterialOption } from "../types/goods-receipt.types";

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
    /** Tên NCC / kho theo id của các dòng trong batch (lấy 1 lần ở component cha). */
    supplierNames: Record<string, string>;
    locationNames: Record<string, string>;
    materialsBySupplierId: Record<string, GoodsReceiptMaterialOption[]>;
    materialsLoadingSupplierId: string | null;
    onEnsureMaterials(supplierId: string): void;
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
    supplierNames,
    locationNames,
    materialsBySupplierId,
    materialsLoadingSupplierId,
    onEnsureMaterials
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

    // Dropdown NCC & kho: lookup phân trang, chỉ tải khi dòng đang mở để sửa
    const lookupEnabled = expanded && editable;
    const supplierDropdown = useLookupDropdown(supplierApi.lookup, {
        enabled: lookupEnabled,
        pinned: [supplierId && supplierNames[supplierId] ? { id: supplierId, name: supplierNames[supplierId] } : null]
    });
    const locationDropdown = useLookupDropdown(inventoryLocationApi.lookupLocations, {
        enabled: lookupEnabled,
        pinned: [locationId && locationNames[locationId] ? { id: locationId, name: locationNames[locationId] } : null]
    });

    // Đơn vị cho nguyên liệu mới: 1 lookup dùng chung cho các dòng của phiếu này
    const hasNewIngredientLine = lines.some((l) => l.isNewIngredient);
    const unitDropdown = useLookupDropdown(unitApi.lookup, {
        enabled: lookupEnabled && hasNewIngredientLine,
        selectedIds: lines.filter((l) => l.isNewIngredient).map((l) => l.newIngredientUnitId),
        fetchByIds: unitApi.lookupByIds
    });

    const supplierDisplayName =
        supplierDropdown.findOption(supplierId)?.name ?? (supplierId ? "Nhà cung cấp đã chọn" : null);

    const locationName = locationDropdown.findOption(locationId)?.name;

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
                    <StatusBadge tone={isValid ? "success" : "danger"}>
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </StatusBadge>
                </div>
            </button>

            {expanded ? (
                <div className="grid gap-3 border-t border-pos-border p-4">
                    {/* Phần Thông tin phiếu nhập */}
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Nhà cung cấp *</label>
                            <SearchableDropdown
                                disabled={!editable}
                                emptyText="Không tìm thấy nhà cung cấp"
                                hasMore={supplierDropdown.hasMore}
                                loading={supplierDropdown.loading}
                                onChange={setSupplierId}
                                onLoadMore={supplierDropdown.onLoadMore}
                                onSearch={supplierDropdown.onSearch}
                                options={supplierDropdown.options}
                                placeholder="-- Chọn nhà cung cấp --"
                                value={supplierId}
                            />
                            {errorByField.get("supplierId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("supplierId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Kho nhận *</label>
                            <SearchableDropdown
                                disabled={!editable}
                                emptyText="Không tìm thấy kho"
                                hasMore={locationDropdown.hasMore}
                                loading={locationDropdown.loading}
                                onChange={setLocationId}
                                onLoadMore={locationDropdown.onLoadMore}
                                onSearch={locationDropdown.onSearch}
                                options={locationDropdown.options}
                                placeholder="-- Chọn kho nhận --"
                                value={locationId}
                            />
                            {errorByField.get("locationId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("locationId")}</p>
                            ) : null}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Ngày dự kiến giao</label>
                            <TextInput
                                disabled={!editable}
                                onChange={(e) => setExpectedDate(e.target.value)}
                                type="date"
                                value={expectedDate}
                            />
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Ghi chú</label>
                            <TextInput
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
                                <Button onClick={addLine} size="sm">
                                    <Plus size={13} aria-hidden="true" />
                                    Thêm dòng
                                </Button>
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
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableHeaderCell className="w-5/12">Nguyên liệu *</TableHeaderCell>
                                            <TableHeaderCell className="w-2/12">Số lượng đặt *</TableHeaderCell>
                                            <TableHeaderCell className="w-3/12">Ghi chú</TableHeaderCell>
                                            <TableHeaderCell className="w-1/12 text-center">Xóa</TableHeaderCell>
                                        </TableRow>
                                    </TableHead>
                                    <tbody>
                                        {lines.map((line, index) => (
                                            <TableRow key={index}>
                                                <TableCell className="align-top">

                                                    {line.isNewIngredient ? (
                                                        <div className="grid gap-1.5">
                                                            <TextInput
                                                                disabled={!editable}
                                                                onChange={(e) => updateLine(index, "newIngredientName", e.target.value)}
                                                                placeholder="Tên nguyên liệu mới *..."
                                                                type="text"
                                                                value={line.newIngredientName}
                                                            />
                                                            <div className="grid grid-cols-2 gap-1.5">
                                                                <TextInput
                                                                    disabled={!editable}
                                                                    onChange={(e) => updateLine(index, "newIngredientCode", e.target.value)}
                                                                    placeholder="Mã NL (để trống tự sinh)..."
                                                                    type="text"
                                                                    value={line.newIngredientCode}
                                                                />
                                                                <SearchableDropdown
                                                                    disabled={!editable}
                                                                    emptyText="Không tìm thấy đơn vị"
                                                                    hasMore={unitDropdown.hasMore}
                                                                    loading={unitDropdown.loading}
                                                                    onChange={(value) => updateLine(index, "newIngredientUnitId", value)}
                                                                    onLoadMore={unitDropdown.onLoadMore}
                                                                    onSearch={unitDropdown.onSearch}
                                                                    options={unitDropdown.options}
                                                                    placeholder="-- Chọn đơn vị * --"
                                                                    value={line.newIngredientUnitId}
                                                                />
                                                            </div>
                                                            {errorByField.get(`items[${index}].newIngredientCode`) ? (
                                                                <p className="text-[11px] font-bold text-red-600">
                                                                    {errorByField.get(`items[${index}].newIngredientCode`)}
                                                                </p>
                                                            ) : null}
                                                            {errorByField.get(`items[${index}].newIngredientUnitId`) ? (
                                                                <p className="text-[11px] font-bold text-red-600">
                                                                    {errorByField.get(`items[${index}].newIngredientUnitId`)}
                                                                </p>
                                                            ) : null}
                                                        </div>
                                                    ) : (
                                                        <>
                                                            <NativeSelect
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
                                                            </NativeSelect>
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
                                                </TableCell>
                                                <TableCell className="align-top">
                                                    <TextInput
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
                                                </TableCell>
                                                <TableCell className="align-top">
                                                    <TextInput
                                                        disabled={!editable}
                                                        onChange={(e) => updateLine(index, "note", e.target.value)}
                                                        type="text"
                                                        value={line.note}
                                                    />
                                                </TableCell>
                                                <TableCell className="text-center align-top">
                                                    {editable ? (
                                                        <IconButton aria-label="Xóa dòng" onClick={() => removeLine(index)} tone="danger">
                                                            <Trash2 size={14} aria-hidden="true" />
                                                        </IconButton>
                                                    ) : null}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </tbody>
                                </Table>
                            </div>
                        )}
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

export function GoodsReceiptImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
    const [supplierNames, setSupplierNames] = useState<Record<string, string>>({});
    const [locationNames, setLocationNames] = useState<Record<string, string>>({});
    // Nguyên liệu phụ thuộc nhà cung cấp — cache theo supplierId để nhiều dòng cùng NCC không tải lại nhiều lần.
    const [materialsBySupplierId, setMaterialsBySupplierId] = useState<Record<string, GoodsReceiptMaterialOption[]>>({});
    const [materialsLoadingSupplierId, setMaterialsLoadingSupplierId] = useState<string | null>(null);
    const requestedSupplierIds = useRef(new Set<string>());

    // Tên NCC / kho cho các dòng — chỉ lấy theo đúng các id có trong batch (1 request mỗi loại)
    const supplierIdsKey = useMemo(
        () => [...new Set(items.map((i) => String(i.payload.supplierId ?? "")).filter(Boolean))].sort().join(","),
        [items]
    );
    const locationIdsKey = useMemo(
        () => [...new Set(items.map((i) => String(i.payload.locationId ?? "")).filter(Boolean))].sort().join(","),
        [items]
    );

    useEffect(() => {
        const ids = supplierIdsKey ? supplierIdsKey.split(",").filter((id) => !(id in supplierNames)) : [];
        if (ids.length === 0) return;
        supplierApi
            .lookupByIds(ids)
            .then((res) => setSupplierNames((prev) => ({
                ...prev,
                ...Object.fromEntries((res.content ?? []).map((s) => [s.id, s.name]))
            })))
            .catch((err: unknown) => console.error("Lỗi tải tên nhà cung cấp", err));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [supplierIdsKey]);

    useEffect(() => {
        const ids = locationIdsKey ? locationIdsKey.split(",").filter((id) => !(id in locationNames)) : [];
        if (ids.length === 0) return;
        inventoryLocationApi
            .lookupLocationsByIds(ids)
            .then((res) => setLocationNames((prev) => ({
                ...prev,
                ...Object.fromEntries((res.content ?? []).map((l) => [l.id, l.name]))
            })))
            .catch((err: unknown) => console.error("Lỗi tải tên kho", err));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [locationIdsKey]);

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
                    supplierNames={supplierNames}
                    locationNames={locationNames}
                />
            ))}
        </>
    );
}
