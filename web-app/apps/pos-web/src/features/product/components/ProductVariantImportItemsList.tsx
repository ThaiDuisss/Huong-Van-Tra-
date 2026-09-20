import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import type { ImportItemsListProps } from "./ImportBatchPanel";
import { ingredientApi } from "../services/ingredient.api";
import { productApi } from "../services/product.api";
import { unitApi } from "../services/unit.api";
import type { Ingredient, Unit } from "../types/product.types";
import type { ImportBatchItem } from "../types/import-batch.types";

interface BomLineInput {
    ingredientId: string;
    quantity: number;
    unitId: string;
    wasteRate: number;
}

function toBomLineInputs(value: unknown): BomLineInput[] {
    if (!Array.isArray(value)) return [];
    return value.map((raw) => {
        const line = (raw ?? {}) as Record<string, unknown>;
        return {
            ingredientId: line.ingredientId ? String(line.ingredientId) : "",
            quantity: typeof line.quantity === "number" ? line.quantity : Number(line.quantity ?? 0),
            unitId: line.unitId ? String(line.unitId) : "",
            wasteRate: typeof line.wasteRate === "number" ? line.wasteRate : Number(line.wasteRate ?? 0)
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
    productOptions: LookupItem[];
    onProductSearch(term: string): void;
    onLoadMoreProducts(): void;
    productHasMore: boolean;
    productLoading: boolean;
    productNameById: Record<string, string>;
    units: Unit[];
    ingredients: Ingredient[];
}

function ItemRow({
    item,
    editable,
    expanded,
    onToggle,
    onSave,
    onDelete,
    productOptions,
    onProductSearch,
    onLoadMoreProducts,
    productHasMore,
    productLoading,
    productNameById,
    units,
    ingredients
}: ItemRowProps) {
    const [productId, setProductId] = useState(item.payload.productId ? String(item.payload.productId) : "");
    const [quantity, setQuantity] = useState(Number(item.payload.quantity ?? 0));
    const [sellingPrice, setSellingPrice] = useState(Number(item.payload.sellingPrice ?? 0));
    const [costPrice, setCostPrice] = useState(Number(item.payload.costPrice ?? 0));
    const [unitId, setUnitId] = useState(item.payload.unitId ? String(item.payload.unitId) : "");
    const [inventoryPolicy, setInventoryPolicy] = useState(String(item.payload.inventoryPolicy ?? "TRACK_STOCK"));
    const [status, setStatus] = useState(String(item.payload.status ?? "ACTIVE"));
    const [bomItems, setBomItems] = useState<BomLineInput[]>(toBomLineInputs(item.payload.bomItems));
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setProductId(item.payload.productId ? String(item.payload.productId) : "");
        setQuantity(Number(item.payload.quantity ?? 0));
        setSellingPrice(Number(item.payload.sellingPrice ?? 0));
        setCostPrice(Number(item.payload.costPrice ?? 0));
        setUnitId(item.payload.unitId ? String(item.payload.unitId) : "");
        setInventoryPolicy(String(item.payload.inventoryPolicy ?? "TRACK_STOCK"));
        setStatus(String(item.payload.status ?? "ACTIVE"));
        setBomItems(toBomLineInputs(item.payload.bomItems));
    }, [item]);

    const errorByField = new Map(item.validationErrors.map((e) => [e.field, e.message]));
    const isValid = item.validationErrors.length === 0;
    const productName = productId ? productNameById[productId] : undefined;
    const unitSymbol = units.find((u) => u.id === unitId)?.symbol;

    function updateBomLine(index: number, field: keyof BomLineInput, value: string | number) {
        setBomItems((prev) => prev.map((line, i) => (i === index ? { ...line, [field]: value } : line)));
    }

    function addBomLine() {
        setBomItems((prev) => [...prev, { ingredientId: "", quantity: 1, unitId: "", wasteRate: 0 }]);
    }

    function removeBomLine(index: number) {
        setBomItems((prev) => prev.filter((_, i) => i !== index));
    }

    async function handleSave() {
        setSaving(true);
        try {
            await onSave({
                productId: productId || null,
                quantity,
                sellingPrice,
                costPrice,
                unitId: unitId || null,
                reorderPoint: null,
                inventoryPolicy,
                status,
                bomItems: bomItems.map((line) => ({
                    ingredientId: line.ingredientId || null,
                    quantity: line.quantity,
                    unitId: line.unitId || null,
                    wasteRate: line.wasteRate
                }))
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
                        <div className="truncate font-bold text-[#172317]">
                            {productName ? `${productName} — ${quantity || 0} ${unitSymbol ?? ""}`.trim() : `Dòng ${item.rowIndex}`}
                        </div>
                        <div className="truncate text-xs text-[#697684]">
                            {bomItems.length > 0 ? `${bomItems.length} dòng BOM` : "Không có BOM"}
                        </div>
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

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Sản phẩm *</label>
                            <SearchableDropdown
                                disabled={!editable}
                                hasMore={productHasMore}
                                loading={productLoading}
                                onChange={setProductId}
                                onLoadMore={onLoadMoreProducts}
                                onSearch={onProductSearch}
                                options={productOptions}
                                placeholder="Chọn sản phẩm..."
                                value={productId}
                            />
                            {errorByField.get("productId") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("productId")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Định lượng *</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setQuantity(Number(e.target.value))}
                                step="any"
                                type="number"
                                value={quantity}
                            />
                            {errorByField.get("quantity") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("quantity")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Đơn vị *</label>
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
                            <label className="text-xs font-bold text-[#536078]">Giá bán</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setSellingPrice(Number(e.target.value))}
                                type="number"
                                value={sellingPrice}
                            />
                            {errorByField.get("sellingPrice") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("sellingPrice")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Giá vốn</label>
                            <input
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setCostPrice(Number(e.target.value))}
                                type="number"
                                value={costPrice}
                            />
                            {errorByField.get("costPrice") ? (
                                <p className="text-xs font-bold text-red-600">{errorByField.get("costPrice")}</p>
                            ) : null}
                        </div>

                        <div className="grid gap-1.5">
                            <label className="text-xs font-bold text-[#536078]">Chính sách tồn kho</label>
                            <select
                                className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                                disabled={!editable}
                                onChange={(e) => setInventoryPolicy(e.target.value)}
                                value={inventoryPolicy}
                            >
                                <option value="TRACK_STOCK">Theo dõi tồn kho</option>
                                <option value="DO_NOT_TRACK">Không theo dõi</option>
                            </select>
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
                    </div>

                    <div className="grid gap-2 border-t border-pos-border pt-3">
                        <div className="flex items-center justify-between">
                            <label className="text-xs font-bold text-[#536078]">Định mức nguyên liệu (BOM)</label>
                            {editable ? (
                                <button
                                    className="inline-flex h-8 items-center gap-1 rounded-lg border border-[#07883a] bg-white px-2.5 text-xs font-bold text-[#07883a] hover:bg-green-50"
                                    onClick={addBomLine}
                                    type="button"
                                >
                                    <Plus size={13} aria-hidden="true" />
                                    Thêm dòng
                                </button>
                            ) : null}
                        </div>

                        {bomItems.length === 0 ? (
                            <div className="rounded-lg border border-dashed border-pos-border p-3 text-center text-xs font-medium text-gray-400">
                                Chưa có dòng nguyên liệu nào.
                            </div>
                        ) : (
                            <div className="overflow-x-auto rounded-lg border border-pos-border">
                                <table className="w-full text-left text-xs">
                                    <thead className="bg-neutral-50 text-[11px] font-bold uppercase text-gray-500">
                                        <tr>
                                            <th className="p-2 w-5/12">Nguyên liệu *</th>
                                            <th className="p-2 w-2/12">Định lượng *</th>
                                            <th className="p-2 w-2/12">Đơn vị *</th>
                                            <th className="p-2 w-2/12">Hao hụt (%)</th>
                                            <th className="p-2 w-1/12 text-center">Xóa</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-pos-border bg-white">
                                        {bomItems.map((line, index) => (
                                            <tr key={index}>
                                                <td className="p-2 align-top">
                                                    <select
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        onChange={(e) => updateBomLine(index, "ingredientId", e.target.value)}
                                                        value={line.ingredientId}
                                                    >
                                                        <option value="">-- Chọn nguyên liệu --</option>
                                                        {ingredients.map((ing) => (
                                                            <option key={ing.id} value={ing.id}>
                                                                {ing.name} ({ing.ingredientCode})
                                                            </option>
                                                        ))}
                                                    </select>
                                                    {errorByField.get(`bomItems[${index}].ingredientId`) ? (
                                                        <p className="mt-1 text-[11px] font-bold text-red-600">
                                                            {errorByField.get(`bomItems[${index}].ingredientId`)}
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="p-2 align-top">
                                                    <input
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        onChange={(e) => updateBomLine(index, "quantity", Number(e.target.value))}
                                                        step="any"
                                                        type="number"
                                                        value={line.quantity}
                                                    />
                                                    {errorByField.get(`bomItems[${index}].quantity`) ? (
                                                        <p className="mt-1 text-[11px] font-bold text-red-600">
                                                            {errorByField.get(`bomItems[${index}].quantity`)}
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="p-2 align-top">
                                                    <select
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        onChange={(e) => updateBomLine(index, "unitId", e.target.value)}
                                                        value={line.unitId}
                                                    >
                                                        <option value="">-- Đơn vị --</option>
                                                        {units.map((u) => (
                                                            <option key={u.id} value={u.id}>
                                                                {u.symbol}
                                                            </option>
                                                        ))}
                                                    </select>
                                                    {errorByField.get(`bomItems[${index}].unitId`) ? (
                                                        <p className="mt-1 text-[11px] font-bold text-red-600">
                                                            {errorByField.get(`bomItems[${index}].unitId`)}
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="p-2 align-top">
                                                    <input
                                                        className="h-9 w-full rounded-lg border border-pos-border px-2 text-xs outline-none"
                                                        disabled={!editable}
                                                        min={0}
                                                        max={100}
                                                        onChange={(e) => updateBomLine(index, "wasteRate", Number(e.target.value))}
                                                        step="any"
                                                        type="number"
                                                        value={line.wasteRate}
                                                    />
                                                </td>
                                                <td className="p-2 text-center align-top">
                                                    {editable ? (
                                                        <button
                                                            aria-label="Xóa dòng"
                                                            className="grid size-8 place-items-center rounded-lg text-red-500 hover:bg-red-50"
                                                            onClick={() => removeBomLine(index)}
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

export function ProductVariantImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
    const [productOptions, setProductOptions] = useState<LookupItem[]>([]);
    const [productPage, setProductPage] = useState(0);
    const [productHasMore, setProductHasMore] = useState(true);
    const [productLoading, setProductLoading] = useState(false);
    const [productNameById, setProductNameById] = useState<Record<string, string>>({});
    const [units, setUnits] = useState<Unit[]>([]);
    const [ingredients, setIngredients] = useState<Ingredient[]>([]);

    const loadProductOptions = useCallback(async (search: string, page: number) => {
        try {
            setProductLoading(true);
            const res = await productApi.lookup(search, page, 20);
            const content = res.content ?? [];
            setProductOptions((prev) => (page === 0 ? content : [...prev, ...content]));
            setProductHasMore(!res.last);
            setProductNameById((prev) => {
                const next = { ...prev };
                content.forEach((c) => {
                    next[c.id] = c.name;
                });
                return next;
            });
        } catch (err) {
            console.error("Lỗi tải danh sách sản phẩm", err);
            setProductHasMore(false);
        } finally {
            setProductLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadProductOptions("", 0);
    }, [loadProductOptions]);

    useEffect(() => {
        unitApi
            .listAll()
            .then((res) => setUnits(res ?? []))
            .catch((err: unknown) => console.error("Lỗi tải đơn vị", err));
        ingredientApi
            .list({ page: 0, size: 1000, status: "ACTIVE" })
            .then((res) => setIngredients(res.content ?? []))
            .catch((err: unknown) => console.error("Lỗi tải nguyên liệu", err));
    }, []);

    const handleProductSearch = useCallback(
        (term: string) => {
            setProductPage(0);
            void loadProductOptions(term, 0);
        },
        [loadProductOptions]
    );

    const handleLoadMoreProducts = useCallback(() => {
        if (!productLoading && productHasMore) {
            const nextPage = productPage + 1;
            setProductPage(nextPage);
            void loadProductOptions("", nextPage);
        }
    }, [productLoading, productHasMore, productPage, loadProductOptions]);

    // Sản phẩm đã chọn của 1 dòng có thể không nằm trong trang lookup mặc định — resolve riêng để
    // hiện đúng tên thay vì rớt về nhãn mặc định của SearchableDropdown.
    useEffect(() => {
        const idsNeeded = Array.from(
            new Set(
                items
                    .map((item) => (item.payload.productId ? String(item.payload.productId) : null))
                    .filter((id): id is string => Boolean(id) && !productNameById[id as string])
            )
        );
        if (idsNeeded.length === 0) return;
        let active = true;
        Promise.all(
            idsNeeded.map((id) =>
                productApi
                    .detail(id)
                    .then((product) => ({ id, name: product.name }))
                    .catch(() => null)
            )
        ).then((resolved) => {
            if (!active) return;
            const valid = resolved.filter((r): r is { id: string; name: string } => r !== null);
            if (valid.length === 0) return;
            setProductNameById((prev) => {
                const next = { ...prev };
                valid.forEach((v) => {
                    next[v.id] = v.name;
                });
                return next;
            });
            setProductOptions((prev) => {
                const existingIds = new Set(prev.map((o) => o.id));
                const additions = valid.filter((v) => !existingIds.has(v.id));
                return additions.length > 0 ? [...prev, ...additions] : prev;
            });
        });
        return () => {
            active = false;
        };
    }, [items, productNameById]);

    return (
        <>
            {items.map((item) => {
                const itemProductId = item.payload.productId ? String(item.payload.productId) : "";
                const itemProductName = itemProductId ? productNameById[itemProductId] : undefined;
                const itemProductOptions =
                    itemProductId && itemProductName && !productOptions.some((o) => o.id === itemProductId)
                        ? [{ id: itemProductId, name: itemProductName }, ...productOptions]
                        : productOptions;

                return (
                    <ItemRow
                        editable={editable}
                        expanded={expandedId === item.id}
                        ingredients={ingredients}
                        item={item}
                        key={item.id}
                        onDelete={() => onDelete(item.id)}
                        onLoadMoreProducts={handleLoadMoreProducts}
                        onProductSearch={handleProductSearch}
                        onSave={(payload) => onSave(item.id, payload)}
                        onToggle={() => onToggle(item.id)}
                        productHasMore={productHasMore}
                        productLoading={productLoading}
                        productNameById={productNameById}
                        productOptions={itemProductOptions}
                        units={units}
                    />
                );
            })}
        </>
    );
}
