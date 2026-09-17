import { Download, Upload, Plus, Trash2, X } from "lucide-react";
import { useCallback, useState, useEffect } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import { bomApi } from "../../manufacturing/services/bom.api";
import type { BomItemInput } from "../../manufacturing/types/bom.types";
import { categoryApi } from "../services/category.api";

import { ExcelImportModal } from "../components/ExcelImportModal";
import {
    CatalogStatusBadge,
    DetailList,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { useApiResource, usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { ingredientApi } from "../services/ingredient.api";
import { productApi } from "../services/product.api";
import { productVariantApi } from "../services/productVariant.api";
import { unitApi } from "../services/unit.api";
import type {
    CatalogStatus,
    Ingredient,
    InventoryPolicy,
    ProductVariant,
    ProductVariantInput,
    Unit
} from "../types/product.types";
import { formatCurrency } from "../utils/product-formatters";

interface ProductVariantFormWrapperProps {
    editingItem: ProductVariant | null;
    onClose(): void;
    productOptions: LookupItem[];
    onProductSearch(term: string): void;
    onLoadMoreProducts(): void;
    productHasMore: boolean;
    productLoading: boolean;
    units: Unit[];
    ingredients: Ingredient[];
    onSave(input: ProductVariantInput, items: BomItemInput[]): Promise<void>;
}

function ProductVariantFormWrapper({
    editingItem,
    onClose,
    productOptions,
    onProductSearch,
    onLoadMoreProducts,
    productHasMore,
    productLoading,
    units,
    ingredients,
    onSave
}: ProductVariantFormWrapperProps) {
    const isEditMode = Boolean(editingItem?.id);

    const [formProductId, setFormProductId] = useState(
        editingItem?.productId ? String(editingItem.productId) : ""
    );
    const [formQuantity, setFormQuantity] = useState(editingItem?.quantity ?? 0);
    const [formSellingPrice, setFormSellingPrice] = useState(editingItem?.sellingPrice ?? 0);
    const [formCostPrice, setFormCostPrice] = useState(editingItem?.costPrice ?? 0);
    const [formUnitId, setFormUnitId] = useState(editingItem?.unitId ? String(editingItem.unitId) : "");
    const [formInventoryPolicy, setFormInventoryPolicy] = useState<InventoryPolicy>(
        editingItem?.inventoryPolicy || "TRACK_STOCK"
    );
    const [formStatus, setFormStatus] = useState<CatalogStatus>(editingItem?.status || "ACTIVE");

    const [formItems, setFormItems] = useState<BomItemInput[]>([]);
    const [itemsLoading, setItemsLoading] = useState(false);

    const [formError, setFormError] = useState("");
    const [formSubmitting, setFormSubmitting] = useState(false);

    const selectedProductName = productOptions.find((p) => p.id === formProductId)?.name ?? "";
    const selectedUnit = units.find((u) => String(u.id) === formUnitId);
    const previewVariantName = [
        selectedProductName,
        formQuantity > 0 ? String(formQuantity) : "",
        selectedUnit?.name ?? ""
    ]
        .filter(Boolean)
        .join(" ");

    useEffect(() => {
        if (!editingItem?.id) return;
        let active = true;
        setItemsLoading(true);
        bomApi
            .listByVariant(String(editingItem.id))
            .then((items) => {
                if (!active) return;
                setFormItems(
                    items.map((item) => ({
                        ingredientId: item.ingredient.id,
                        quantity: item.quantity,
                        unitId: item.unit.id,
                        wasteRate: item.wasteRate
                    }))
                );
            })
            .catch((err) => console.error("Error loading bom items", err))
            .finally(() => {
                if (active) setItemsLoading(false);
            });
        return () => {
            active = false;
        };
    }, [editingItem?.id]);

    function addIngredientRow() {
        setFormItems((prev) => [...prev, { ingredientId: "", quantity: 1, unitId: "", wasteRate: 0 }]);
    }

    function removeIngredientRow(index: number) {
        setFormItems((prev) => prev.filter((_, i) => i !== index));
    }

    function updateRowItem(index: number, field: keyof BomItemInput, value: string | number) {
        setFormItems((prev) => prev.map((item, i) => (i === index ? { ...item, [field]: value } : item)));
    }

    function validateForm(): boolean {
        if (!formProductId) {
            setFormError("Vui lòng chọn Sản phẩm.");
            return false;
        }
        if (!formQuantity || formQuantity <= 0) {
            setFormError("Vui lòng nhập Định lượng lớn hơn 0.");
            return false;
        }
        if (!formUnitId) {
            setFormError("Vui lòng chọn Đơn vị bán.");
            return false;
        }
        if (formSellingPrice < 0 || formCostPrice < 0) {
            setFormError("Giá bán/Giá vốn không được âm.");
            return false;
        }

        const ingredientIds = new Set<string>();
        for (let i = 0; i < formItems.length; i++) {
            const item = formItems[i];
            if (!item.ingredientId) {
                setFormError(`Nguyên liệu dòng ${i + 1}: vui lòng chọn nguyên liệu.`);
                return false;
            }
            if (!item.unitId) {
                setFormError(`Nguyên liệu dòng ${i + 1}: vui lòng chọn đơn vị.`);
                return false;
            }
            if (item.quantity <= 0) {
                setFormError(`Nguyên liệu dòng ${i + 1}: định lượng phải lớn hơn 0.`);
                return false;
            }
            if (item.wasteRate < 0 || item.wasteRate > 100) {
                setFormError(`Nguyên liệu dòng ${i + 1}: tỷ lệ hao hụt phải từ 0% đến 100%.`);
                return false;
            }
            if (ingredientIds.has(item.ingredientId)) {
                setFormError("Không được chọn trùng nguyên liệu trong danh sách.");
                return false;
            }
            ingredientIds.add(item.ingredientId);
        }

        setFormError("");
        return true;
    }

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        if (!validateForm()) return;

        setFormSubmitting(true);
        try {
            const input: ProductVariantInput = {
                productId: formProductId,
                quantity: Number(formQuantity),
                sellingPrice: Number(formSellingPrice),
                costPrice: Number(formCostPrice),
                unitId: formUnitId,
                inventoryPolicy: formInventoryPolicy,
                status: formStatus
            };
            const items = formItems.map((item) => ({
                ...item,
                quantity: Number(item.quantity),
                wasteRate: Number(item.wasteRate)
            }));

            await onSave(input, items);
            onClose();
        } catch (err) {
            setFormError(err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu Mặt hàng.");
        } finally {
            setFormSubmitting(false);
        }
    }

    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/40 p-6 overflow-y-auto">
            <form
                onSubmit={handleSubmit}
                className="my-8 w-full max-w-4xl rounded-3xl bg-white p-6 shadow-pos-panel border border-pos-border max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in-95 duration-150"
            >
                <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                    <h2 className="text-xl font-bold text-gray-700">
                        {isEditMode ? "Chỉnh sửa Mặt hàng" : "Tạo Mặt hàng mới"}
                    </h2>
                    <button
                        type="button"
                        onClick={onClose}
                        className="grid size-9 place-items-center rounded-lg hover:bg-neutral-100 transition-all focus:outline-none cursor-pointer"
                        aria-label="Đóng biểu mẫu"
                    >
                        <X size={20} />
                    </button>
                </header>

                {formError && (
                    <div className="mb-4 rounded-xl bg-red-50 p-4 text-sm font-semibold text-red-600 border border-red-200">
                        {formError}
                    </div>
                )}

                <div className="grid gap-5">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="grid gap-1.5 text-sm font-bold text-gray-500">
                            <span>Sản phẩm *</span>
                            <SearchableDropdown
                                value={formProductId}
                                onChange={setFormProductId}
                                options={productOptions}
                                onSearch={onProductSearch}
                                onLoadMore={onLoadMoreProducts}
                                hasMore={productHasMore}
                                loading={productLoading}
                                placeholder="Chọn sản phẩm..."
                            />
                        </div>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Định lượng *
                            <input
                                type="number"
                                min={0}
                                step="any"
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                placeholder="Ví dụ: 100, 250, 500"
                                value={formQuantity}
                                onChange={(e) => setFormQuantity(Number(e.target.value))}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Giá bán *
                            <input
                                type="number"
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                value={formSellingPrice}
                                onChange={(e) => setFormSellingPrice(Number(e.target.value))}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Giá vốn *
                            <input
                                type="number"
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                value={formCostPrice}
                                onChange={(e) => setFormCostPrice(Number(e.target.value))}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Đơn vị bán thương mại *
                            <select
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                value={formUnitId}
                                onChange={(e) => setFormUnitId(e.target.value)}
                            >
                                <option value="">-- Chọn đơn vị --</option>
                                {units
                                    .filter((unit) => unit.status === "ACTIVE")
                                    .map((unit) => (
                                        <option key={unit.id} value={unit.id}>
                                            {unit.name} ({unit.symbol})
                                        </option>
                                    ))}
                            </select>
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Chính sách tồn kho *
                            <select
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                value={formInventoryPolicy}
                                onChange={(e) => setFormInventoryPolicy(e.target.value as InventoryPolicy)}
                            >
                                <option value="TRACK_STOCK">Theo dõi tồn kho</option>
                                <option value="DO_NOT_TRACK">Không theo dõi</option>
                            </select>
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Trạng thái *
                            <select
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                value={formStatus}
                                onChange={(e) => setFormStatus(e.target.value as CatalogStatus)}
                            >
                                <option value="ACTIVE">Đang hoạt động</option>
                                <option value="INACTIVE">Ngừng hoạt động</option>
                            </select>
                        </label>
                    </div>

                    <div className="rounded-xl border border-pos-border bg-neutral-50 px-4 py-3 text-sm">
                        <span className="font-bold text-gray-500">Tên Mặt hàng (tự sinh): </span>
                        <span className="font-black text-gray-800">
                            {previewVariantName || "Chọn sản phẩm, định lượng và đơn vị để xem tên hiển thị"}
                        </span>
                    </div>

                    <div className="grid gap-3 border-t border-pos-border pt-4">
                        <div className="flex items-center justify-between">
                            <div>
                                <h3 className="font-bold text-gray-700 text-base">Định mức nguyên liệu (BOM)</h3>
                                <p className="text-xs font-medium text-gray-400">
                                    Không bắt buộc — chỉ khai báo nếu Mặt hàng này cần sản xuất từ nguyên liệu.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={addIngredientRow}
                                className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-[#07883a] bg-white px-3.5 text-xs font-bold text-[#07883a] hover:bg-green-50 transition-all cursor-pointer"
                            >
                                <Plus size={14} />
                                Thêm dòng
                            </button>
                        </div>

                        {itemsLoading ? (
                            <div className="text-sm font-semibold text-gray-400">Đang tải định mức nguyên liệu...</div>
                        ) : formItems.length === 0 ? (
                            <div className="rounded-xl border border-dashed border-pos-border p-4 text-center text-sm font-medium text-gray-400">
                                Chưa có dòng nguyên liệu nào.
                            </div>
                        ) : (
                            <div className="max-h-[300px] overflow-y-auto rounded-xl border border-pos-border">
                                <table className="w-full text-left text-xs">
                                    <thead className="sticky top-0 bg-neutral-50 text-[11px] font-bold text-gray-500 uppercase z-10 border-b border-pos-border">
                                        <tr>
                                            <th className="p-3 w-5/12">Nguyên liệu *</th>
                                            <th className="p-3 w-2/12">Định lượng *</th>
                                            <th className="p-3 w-2/12">Đơn vị *</th>
                                            <th className="p-3 w-2/12">Hao hụt (%)</th>
                                            <th className="p-3 w-1/12 text-center">Xóa</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-pos-border bg-white">
                                        {formItems.map((row, index) => (
                                            <tr key={index} className="hover:bg-neutral-50/50">
                                                <td className="p-2">
                                                    <select
                                                        className="h-9 w-full rounded-lg border border-pos-border bg-white px-2 focus:outline-none"
                                                        value={row.ingredientId}
                                                        onChange={(e) => updateRowItem(index, "ingredientId", e.target.value)}
                                                    >
                                                        <option value="">-- Chọn nguyên liệu --</option>
                                                        {ingredients.map((ing) => (
                                                            <option key={ing.id} value={ing.id}>
                                                                {ing.name} ({ing.ingredientCode})
                                                            </option>
                                                        ))}
                                                    </select>
                                                </td>
                                                <td className="p-2">
                                                    <input
                                                        type="number"
                                                        step="any"
                                                        className="h-9 w-full rounded-lg border border-pos-border bg-white px-2 font-semibold focus:outline-none"
                                                        placeholder="0.00"
                                                        value={row.quantity}
                                                        onChange={(e) => updateRowItem(index, "quantity", Number(e.target.value))}
                                                    />
                                                </td>
                                                <td className="p-2">
                                                    <select
                                                        className="h-9 w-full rounded-lg border border-pos-border bg-white px-2 focus:outline-none"
                                                        value={row.unitId}
                                                        onChange={(e) => updateRowItem(index, "unitId", e.target.value)}
                                                    >
                                                        <option value="">-- Đơn vị --</option>
                                                        {units.map((u) => (
                                                            <option key={u.id} value={u.id}>
                                                                {u.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </td>
                                                <td className="p-2">
                                                    <input
                                                        type="number"
                                                        step="any"
                                                        min={0}
                                                        max={100}
                                                        className="h-9 w-full rounded-lg border border-pos-border bg-white px-2 focus:outline-none"
                                                        placeholder="0"
                                                        value={row.wasteRate}
                                                        onChange={(e) => updateRowItem(index, "wasteRate", Number(e.target.value))}
                                                    />
                                                </td>
                                                <td className="p-2 text-center">
                                                    <button
                                                        type="button"
                                                        onClick={() => removeIngredientRow(index)}
                                                        className="grid size-8 place-items-center rounded-lg text-red-500 hover:bg-red-50 mx-auto transition-all cursor-pointer"
                                                        aria-label="Xóa dòng"
                                                    >
                                                        <Trash2 size={16} />
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>

                <footer className="mt-6 flex items-center justify-end gap-3 border-t border-pos-border pt-4">
                    <button
                        type="button"
                        onClick={onClose}
                        className="h-11 rounded-xl border border-pos-border bg-white px-6 font-bold text-gray-500 hover:bg-neutral-50 transition-all cursor-pointer focus:outline-none"
                    >
                        Hủy bỏ
                    </button>
                    <button
                        type="submit"
                        disabled={formSubmitting}
                        className="h-11 rounded-xl bg-[#07883a] px-6 font-bold text-white hover:bg-[#067331] transition-all disabled:opacity-50 cursor-pointer shadow-sm focus:outline-none"
                    >
                        {formSubmitting ? "Đang lưu..." : isEditMode ? "Lưu thay đổi" : "Tạo mới"}
                    </button>
                </footer>
            </form>
        </div>
    );
}

export function ProductVariantListPage() {
    const { productId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const state = useCatalogPageState();
    const [isImportModalOpen, setIsImportModalOpen] = useState(false);

    const [filterCategoryId, setFilterCategoryId] = useState("");
    const [filterProductId, setFilterProductId] = useState(productId && productId !== "ALL" ? productId : "");

    const [editingVariant, setEditingVariant] = useState<ProductVariant | null>(null);
    const [showForm, setShowForm] = useState(false);

    // Searchable dropdown states for Category (bộ lọc)
    const [categoryOptions, setCategoryOptions] = useState<LookupItem[]>([]);
    const [categoryPage, setCategoryPage] = useState(0);
    const [categoryHasMore, setCategoryHasMore] = useState(true);
    const [categoryLoading, setCategoryLoading] = useState(false);
    const [categorySearchTerm, setCategorySearchTerm] = useState("");

    // Searchable dropdown states for Product (dùng chung cho bộ lọc và biểu mẫu)
    const [productOptions, setProductOptions] = useState<LookupItem[]>([]);
    const [productPage, setProductPage] = useState(0);
    const [productHasMore, setProductHasMore] = useState(true);
    const [productLoading, setProductLoading] = useState(false);
    const [productSearchTerm, setProductSearchTerm] = useState("");

    const [ingredients, setIngredients] = useState<Ingredient[]>([]);

    const loadCategoryOptions = useCallback(async (search: string, page: number) => {
        try {
            setCategoryLoading(true);
            const res = await categoryApi.lookup(search, page, 20);
            setCategoryOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setCategoryHasMore(!res.last);
        } catch (err) {
            console.error("Error loading categories", err);
            setCategoryHasMore(false);
        } finally {
            setCategoryLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadCategoryOptions("", 0);
    }, [loadCategoryOptions]);

    const handleCategorySearch = useCallback(
        (term: string) => {
            setCategorySearchTerm(term);
            setCategoryPage(0);
            void loadCategoryOptions(term, 0);
        },
        [loadCategoryOptions]
    );

    const handleLoadMoreCategories = useCallback(() => {
        if (!categoryLoading && categoryHasMore) {
            const nextPage = categoryPage + 1;
            setCategoryPage(nextPage);
            void loadCategoryOptions(categorySearchTerm, nextPage);
        }
    }, [categoryLoading, categoryHasMore, categoryPage, categorySearchTerm, loadCategoryOptions]);

    const loadProductOptions = useCallback(async (search: string, page: number) => {
        try {
            setProductLoading(true);
            const res = await productApi.lookup(search, page, 20);
            setProductOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setProductHasMore(!res.last);
        } catch (err) {
            console.error("Error loading products", err);
            setProductHasMore(false);
        } finally {
            setProductLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadProductOptions("", 0);
    }, [loadProductOptions]);

    const handleProductSearch = useCallback(
        (term: string) => {
            setProductSearchTerm(term);
            setProductPage(0);
            void loadProductOptions(term, 0);
        },
        [loadProductOptions]
    );

    const handleLoadMoreProducts = useCallback(() => {
        if (!productLoading && productHasMore) {
            const nextPage = productPage + 1;
            setProductPage(nextPage);
            void loadProductOptions(productSearchTerm, nextPage);
        }
    }, [productLoading, productHasMore, productPage, productSearchTerm, loadProductOptions]);

    useEffect(() => {
        ingredientApi.list({ page: 0, size: 1000, status: "ACTIVE" }).then((res) => {
            setIngredients(res.content ?? []);
        }).catch((err) => console.error("Error loading ingredients", err));
    }, []);

    // Mở sẵn biểu mẫu chỉnh sửa khi được điều hướng từ trang chi tiết mặt hàng
    useEffect(() => {
        const targetId = (location.state as { editVariantId?: string } | null)?.editVariantId;
        if (!targetId) return;

        productVariantApi.detail(targetId)
            .then((variant) => {
                setEditingVariant(variant);
                setShowForm(true);
            })
            .catch((err) => console.error("Error loading variant for editing from redirect state", err));

        navigate(location.pathname, { replace: true });
    }, [location.state, location.pathname, navigate]);

    const loadVariants = useCallback(
        () =>
            productVariantApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                productId: filterProductId || undefined,
                categoryId: filterCategoryId || undefined,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [
            filterProductId,
            filterCategoryId,
            state.page,
            state.pageSize,
            state.query,
            state.status
        ]
    );
    const loadProducts = useCallback(
        () => productApi.list({ page: 0, size: 500 }),
        []
    );
    const resource = usePagedApiResource(loadVariants);
    const productResource = useApiResource(loadProducts);
    const loadUnits = useCallback(() => unitApi.list({ page: 0, size: 500 }), []);
    const unitResource = useApiResource(loadUnits);
    const variants = resource.data?.content ?? [];
    const products = productResource.data?.content ?? [];
    const units = unitResource.data?.content ?? [];

    async function handleSave(input: ProductVariantInput, items: BomItemInput[]) {
        const variant = editingVariant
            ? await productVariantApi.update(String(editingVariant.id), input)
            : await productVariantApi.create(input);
        await bomApi.replaceForVariant(String(variant.id), items);
        resource.refresh();
    }

    const handleExport = async () => {
        try {
            const blob = await productVariantApi.export(filterProductId || undefined);
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);

            const now = new Date();
            const year = now.getFullYear();
            const month = String(now.getMonth() + 1).padStart(2, "0");
            const day = String(now.getDate()).padStart(2, "0");
            const hours = String(now.getHours()).padStart(2, "0");
            const minutes = String(now.getMinutes()).padStart(2, "0");
            const seconds = String(now.getSeconds()).padStart(2, "0");
            const filename = `Danh_sach_bien_the_san_pham_${year}${month}${day}_${hours}${minutes}${seconds}.xlsx`;

            link.setAttribute("download", filename);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            alert("Có lỗi xảy ra khi xuất file!");
        }
    };

    const handleImportClick = () => {
        setIsImportModalOpen(true);
    };

    const getRowValue = (row: any, ...keys: string[]): string => {
        if (!row) return "";
        for (const k of keys) {
            if (row[k] !== undefined && row[k] !== null) {
                return String(row[k]).trim();
            }
        }
        const rowKeys = Object.keys(row);
        for (const targetKey of keys) {
            const normalizedTarget = targetKey.toLowerCase().replace(/[^a-z0-9]/g, "");
            const foundKey = rowKeys.find((rk) => {
                const normalizedRk = rk.toLowerCase().replace(/[^a-z0-9]/g, "");
                return normalizedRk === normalizedTarget;
            });
            if (foundKey && row[foundKey] !== undefined && row[foundKey] !== null) {
                return String(row[foundKey]).trim();
            }
        }
        return "";
    };

    const validateVariantRow = (row: any) => {
        const productCode = getRowValue(row, "Mã sản phẩm", "Mã SP", "Mã sp", "Product Code", "Mã hàng", "Mã SP/Product");
        const quantityStr = getRowValue(row, "Định lượng", "Quantity");
        const sellingPriceStr = getRowValue(row, "Giá bán", "Selling Price");
        const costPriceStr = getRowValue(row, "Giá vốn", "Cost Price");
        const unitName = getRowValue(row, "Đơn vị", "Đơn vị bán", "Đơn vị tính", "Unit");
        const policyStr = getRowValue(row, "Chính sách tồn kho", "Chính sách", "Inventory Policy");
        const statusStr = getRowValue(row, "Trạng thái", "Status");

        if (!productCode && !productId) {
            return { isValid: false, error: "Mã sản phẩm trống" };
        }

        if (productCode && products.length > 0) {
            const cleanCode = productCode.trim().toLowerCase();
            const matchedProduct = products.some(
                (p) => (p.productCode || "").trim().toLowerCase() === cleanCode
            );
            if (!matchedProduct) {
                // If there are more than 500 products or products only partially loaded, avoid false negative if backend will verify
                if (products.length < 500) {
                    return { isValid: false, error: "Mã sản phẩm không tồn tại" };
                }
            }
        }

        const quantity = Number(String(quantityStr).replace(/,/g, ""));
        if (!quantityStr || isNaN(quantity) || quantity <= 0) {
            return { isValid: false, error: "Định lượng không hợp lệ" };
        }

        const sellingPrice = Number(String(sellingPriceStr).replace(/,/g, ""));
        if (isNaN(sellingPrice) || sellingPrice < 0) {
            return { isValid: false, error: "Giá bán không hợp lệ" };
        }

        const costPrice = Number(String(costPriceStr).replace(/,/g, ""));
        if (isNaN(costPrice) || costPrice < 0) {
            return { isValid: false, error: "Giá vốn không hợp lệ" };
        }

        if (!unitName) {
            return { isValid: false, error: "Đơn vị trống" };
        }

        if (units.length > 0) {
            const cleanUnit = unitName.trim().toLowerCase();
            const matchedUnit = units.some(
                (u) =>
                    (u.name || "").trim().toLowerCase() === cleanUnit ||
                    (u.symbol || "").trim().toLowerCase() === cleanUnit
            );
            if (!matchedUnit) {
                if (units.length < 500) {
                    return { isValid: false, error: "Đơn vị không tồn tại" };
                }
            }
        }

        if (policyStr) {
            const p = policyStr.toLowerCase();
            if (p !== "theo dõi tồn kho" && p !== "không theo dõi" && p !== "track_stock" && p !== "do_not_track") {
                return { isValid: false, error: "Chính sách tồn kho không hợp lệ" };
            }
        }

        if (statusStr) {
            const s = statusStr.toLowerCase();
            if (s !== "đang hoạt động" && s !== "ngừng hoạt động" && s !== "active" && s !== "inactive") {
                return { isValid: false, error: "Trạng thái không hợp lệ" };
            }
        }

        return { isValid: true };
    };

    const handleImportExcel = async (file: File) => {
        let currentProductId = productId;
        if (!currentProductId || currentProductId === "ALL") {
            currentProductId = products[0]?.id ? String(products[0].id) : undefined;
        }
        await productVariantApi.import(file, currentProductId || "");
        resource.refresh();
    };

    const handleDownloadTemplate = async () => {
        try {
            const blob = await productVariantApi.downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", "mau_nhap_bien_the.xlsx");
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi tải file mẫu:", error);
            alert("Có lỗi xảy ra khi tải file mẫu!");
        }
    };

    const handleDelete = async (item: ProductVariant) => {
        await productVariantApi.delete(String(item.id));
    };

    return (
        <>
            <CatalogResourceManagement
                title="Mặt hàng"
                description="Quản lý các phiên bản sản phẩm bán được, SKU và định mức nguyên liệu."
                createLabel="Tạo Mặt hàng"
                searchPlaceholder="Tìm theo tên sản phẩm, tên mặt hàng, SKU..."
                totalItemsLabel="Tổng số Mặt hàng"
                extraHeaderActions={
                    <>
                        <ActionButton variant="secondary" onClick={handleImportClick}>
                            <Upload size={17} aria-hidden="true" />
                            Nhập Excel
                        </ActionButton>
                        <ActionButton variant="secondary" onClick={handleExport}>
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                    </>
                }
                items={variants}
                loading={resource.loading}
                error={resource.error}
                query={state.query}
                status={state.status}
                page={(resource.data?.page ?? 0) + 1}
                pageCount={resource.data?.totalPages ?? 1}
                totalItems={resource.data?.totalElements ?? 0}
                pageSize={state.pageSize}
                onQueryChange={state.setQuery}
                onStatusChange={state.setStatus}
                onPageChange={state.setPage}
                onPageSizeChange={state.setPageSize}
                onRefresh={resource.refresh}
                onSubmit={async () => {}} // Not used because customForm handles submission
                controlledEditingItem={editingVariant}
                controlledShowForm={showForm}
                onControlledShowFormChange={setShowForm}
                onControlledEditingItemChange={(item) => setEditingVariant(item as ProductVariant | null)}
                onChangeStatus={(item) =>
                    productVariantApi.updateStatus(
                        String(item.id),
                        item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                    ).then(() => undefined)
                }
                onDelete={handleDelete}
                filters={[
                    {
                        label: "Danh mục",
                        value: filterCategoryId,
                        options: [],
                        onChange: setFilterCategoryId,
                        render: (
                            <SearchableDropdown
                                value={filterCategoryId}
                                onChange={(val) => {
                                    setFilterCategoryId(val);
                                    state.setPage(1);
                                }}
                                options={categoryOptions}
                                onSearch={handleCategorySearch}
                                onLoadMore={handleLoadMoreCategories}
                                hasMore={categoryHasMore}
                                loading={categoryLoading}
                                placeholder="Chọn danh mục"
                            />
                        )
                    },
                    {
                        label: "Sản phẩm",
                        value: filterProductId,
                        options: [],
                        onChange: setFilterProductId,
                        render: (
                            <SearchableDropdown
                                value={filterProductId}
                                onChange={(val) => {
                                    setFilterProductId(val);
                                    state.setPage(1);
                                }}
                                options={productOptions}
                                onSearch={handleProductSearch}
                                onLoadMore={handleLoadMoreProducts}
                                hasMore={productHasMore}
                                loading={productLoading}
                                placeholder="Chọn sản phẩm"
                            />
                        )
                    }
                ]}
                secondaryFilter={undefined}
                columns={[
                    { key: "variant", label: "Tên Mặt hàng" },
                    { key: "quantity", label: "Định lượng" },
                    { key: "selling", label: "Giá bán" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item) => (
                    <>
                        <TableCell>{item.variantName}</TableCell>
                        <TableCell className="text-right pr-[18px]">{item.quantity}</TableCell>
                        <TableCell>{formatCurrency(item.sellingPrice)}</TableCell>
                        <TableCell>
                            <CatalogStatusBadge status={item.status} />
                        </TableCell>
                    </>
                )}
                detailPath={(item) => `/system/product-variants/${item.id}`}
                renderDetail={(item) => {
                    const matchedUnit = units.find((u) => u.id === item.unitId);
                    const unitText = matchedUnit ? `${matchedUnit.name} (${matchedUnit.symbol})` : `Mã đơn vị: ${item.unitId}`;
                    return (
                        <div className="grid gap-4">
                            <DetailList
                                items={[
                                    { label: "Sản phẩm", value: item.productName },
                                    { label: "SKU", value: item.sku },
                                    { label: "Tên Mặt hàng", value: item.variantName },
                                    { label: "Định lượng", value: String(item.quantity) },
                                    {
                                        label: "Giá bán",
                                        value: formatCurrency(item.sellingPrice)
                                    },
                                    {
                                        label: "Giá vốn",
                                        value: formatCurrency(item.costPrice)
                                    },
                                    {
                                        label: "Đơn vị bán",
                                        value: unitText
                                    },
                                    {
                                        label: "Chính sách tồn kho",
                                        value:
                                            item.inventoryPolicy === "TRACK_STOCK"
                                                ? "Theo dõi tồn kho"
                                                : "Không theo dõi"
                                    },
                                    {
                                        label: "Trạng thái",
                                        value: (
                                            <CatalogStatusBadge status={item.status} />
                                        )
                                    }
                                ]}
                            />
                            <div className="flex gap-2 justify-end border-t border-pos-border pt-4">
                                <Link
                                    to={`/system/product-variants/${item.id}`}
                                    className="flex items-center gap-1.5 rounded-xl border border-pos-border bg-white px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-neutral-50 active:scale-95 transition-all cursor-pointer"
                                >
                                    Xem chi tiết nâng cao
                                </Link>
                            </div>
                        </div>
                    );
                }}
                customForm={(editingItem, onClose) => (
                    <ProductVariantFormWrapper
                        editingItem={editingItem as ProductVariant | null}
                        onClose={onClose}
                        productOptions={productOptions}
                        onProductSearch={handleProductSearch}
                        onLoadMoreProducts={handleLoadMoreProducts}
                        productHasMore={productHasMore}
                        productLoading={productLoading}
                        units={units}
                        ingredients={ingredients}
                        onSave={handleSave}
                    />
                )}
            />
            <ExcelImportModal
                isOpen={isImportModalOpen}
                onClose={() => setIsImportModalOpen(false)}
                title="Nhập dữ liệu từ Excel"
                description="Tải file mẫu hoặc chọn file Excel để nhập dữ liệu vào hệ thống."
                onDownloadTemplate={handleDownloadTemplate}
                onImport={handleImportExcel}
                validateRow={validateVariantRow}
            />
        </>
    );
}
