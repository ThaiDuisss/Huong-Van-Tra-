import { Plus, Trash2, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { useNavigate, useLocation } from "react-router-dom";
import { routePaths } from "../../../routes/path";
import { SearchableDropdown } from "../components/shared/SearchableDropdown";
import { CatalogResourceManagement, TableCell } from "../../product/components/CatalogResourceManagement";
import { StatusBadge } from "../components/shared/StatusBadge";

import { bomApi } from "../services/bom.api";
import { ingredientApi } from "../../product/services/ingredient.api";
import { productVariantApi } from "../../product/services/productVariant.api";
import { unitApi } from "../../product/services/unit.api";
import { categoryApi } from "../../product/services/category.api";

import type { BomResponse, BomItemInput, BomStatus, LookupItem } from "../types/bom.types";
import type { Ingredient, ProductVariant, Unit } from "../../product/types/product.types";
import { usePagedApiResource } from "../../product/hooks/useApiResource";
import { useCatalogPageState } from "../../product/hooks/useCatalogPageState";

interface BomFormWrapperProps {
    editingItem: BomResponse | null;
    onClose(): void;
    variants: ProductVariant[];
    ingredients: Ingredient[];
    units: Unit[];
    onSave(payload: any): Promise<void>;
}

function BomFormWrapper({
    editingItem,
    onClose,
    variants: _variants,
    ingredients,
    units,
    onSave
}: BomFormWrapperProps) {
    const isEditMode = !!(editingItem && editingItem.id);

    const [formVariantId, setFormVariantId] = useState(editingItem?.productVariantId || "");
    const [variantOptions, setVariantOptions] = useState<LookupItem[]>([]);
    const [variantSearchTerm, setVariantSearchTerm] = useState("");
    const [variantPage, setVariantPage] = useState(0);
    const [variantHasMore, setVariantHasMore] = useState(true);
    const [variantLoading, setVariantLoading] = useState(false);

    const loadVariantOptions = useCallback(async (search: string, page: number) => {
        try {
            setVariantLoading(true);
            const res = await productVariantApi.lookup(search, page, 20);
            setVariantOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setVariantHasMore(!res.last);
        } catch (err) {
            console.error("Error loading product variants", err);
            setVariantHasMore(false);
        } finally {
            setVariantLoading(false);
        }
    }, []);

    useEffect(() => {
        setVariantPage(0);
        loadVariantOptions(variantSearchTerm, 0);
    }, [variantSearchTerm, loadVariantOptions]);

    const handleLoadMoreVariants = useCallback(() => {
        if (!variantLoading && variantHasMore) {
            const nextPage = variantPage + 1;
            setVariantPage(nextPage);
            loadVariantOptions(variantSearchTerm, nextPage);
        }
    }, [variantLoading, variantHasMore, variantPage, variantSearchTerm, loadVariantOptions]);

    useEffect(() => {
        if (editingItem?.productVariantId) {
            if (editingItem.productName) {
                const displayName = editingItem.variantName || editingItem.productName;
                const initItem: LookupItem = {
                    id: editingItem.productVariantId,
                    name: displayName
                };
                setVariantOptions(prev => {
                    if (prev.some(opt => opt.id === initItem.id)) return prev;
                    return [initItem, ...prev];
                });
            } else {
                productVariantApi.detail(editingItem.productVariantId).then(res => {
                    const displayName = res.variantName || res.productName;
                    const initItem: LookupItem = {
                        id: String(res.id),
                        name: displayName
                    };
                    setVariantOptions(prev => {
                        if (prev.some(opt => opt.id === initItem.id)) return prev;
                        return [initItem, ...prev];
                    });
                }).catch(err => console.error("Error fetching variant detail", err));
            }
        }
    }, [editingItem]);
    const [formName, setFormName] = useState(editingItem?.name || "");
    const [formStatus, setFormStatus] = useState<BomStatus>(editingItem?.status || "ACTIVE");
    const [formDescription, setFormDescription] = useState(editingItem?.description || "");
    const [formItems, setFormItems] = useState<BomItemInput[]>(
        editingItem?.items?.map((item) => ({
            ingredientId: item.ingredient.id,
            quantity: item.quantity,
            unitId: item.unit.id,
            wasteRate: item.wasteRate
        })) || [{ ingredientId: "", quantity: 1, unitId: "", wasteRate: 0 }]
    );

    const [formError, setFormError] = useState("");
    const [formSubmitting, setFormSubmitting] = useState(false);

    // Dynamic row actions
    function addIngredientRow() {
        setFormItems((prev) => [
            ...prev,
            { ingredientId: "", quantity: 1, unitId: "", wasteRate: 0 }
        ]);
    }

    function removeIngredientRow(index: number) {
        setFormItems((prev) => prev.filter((_, i) => i !== index));
    }

    function updateRowItem(index: number, field: keyof BomItemInput, value: any) {
        setFormItems((prev) =>
            prev.map((item, i) => (i === index ? { ...item, [field]: value } : item))
        );
    }

    // Validate form inputs
    function validateForm(): boolean {
        if (!formVariantId) {
            setFormError("Vui lòng chọn Mặt hàng.");
            return false;
        }
        if (formItems.length === 0) {
            setFormError("BOM phải có ít nhất 1 nguyên liệu.");
            return false;
        }

        const ingredientIds = new Set<string>();
        for (let i = 0; i < formItems.length; i++) {
            const item = formItems[i];
            if (!item.ingredientId) {
                setFormError(`Dòng ${i + 1}: Vui lòng chọn nguyên liệu.`);
                return false;
            }
            if (!item.unitId) {
                setFormError(`Dòng ${i + 1}: Vui lòng chọn đơn vị.`);
                return false;
            }
            if (item.quantity <= 0) {
                setFormError(`Dòng ${i + 1}: Định lượng phải lớn hơn 0.`);
                return false;
            }
            if (item.wasteRate < 0 || item.wasteRate > 100) {
                setFormError(`Dòng ${i + 1}: Tỷ lệ hao hụt phải từ 0% đến 100%.`);
                return false;
            }
            if (ingredientIds.has(item.ingredientId)) {
                setFormError("Không được chọn trùng nguyên liệu trong cùng một BOM.");
                return false;
            }
            ingredientIds.add(item.ingredientId);
        }

        setFormError("");
        return true;
    }

    async function handleSubmit(event: React.FormEvent) {
        event.preventDefault();
        if (!validateForm()) return;

        setFormSubmitting(true);
        try {
            const payload = {
                productVariantId: formVariantId,
                name: formName || null,
                bomCode: editingItem?.bomCode || null,
                isDefault: editingItem?.isDefault || false,
                version: isEditMode ? Number(editingItem?.version || 1) : null,
                status: formStatus,
                description: formDescription || null,
                items: formItems.map((item) => ({
                    ...item,
                    quantity: Number(item.quantity),
                    wasteRate: Number(item.wasteRate)
                }))
            };

            await onSave(payload);
            onClose();
        } catch (err: any) {
            console.error(err);
            setFormError(err?.message || "Có lỗi xảy ra khi lưu BOM.");
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
                        {isEditMode ? "Chỉnh sửa BOM" : "Tạo BOM mới"}
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
                    {/* General details */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="grid gap-1.5 text-sm font-bold text-gray-500">
                            <span>Mặt hàng (Product Variant) *</span>
                            <SearchableDropdown
                                value={formVariantId}
                                onChange={setFormVariantId}
                                options={variantOptions}
                                onSearch={setVariantSearchTerm}
                                onLoadMore={handleLoadMoreVariants}
                                hasMore={variantHasMore}
                                loading={variantLoading}
                                placeholder="Chọn Mặt hàng..."
                                disabled={isEditMode}
                            />
                        </div>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Tên BOM
                            <input
                                type="text"
                                className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                                placeholder="Tên công thức chuẩn, thay thế..."
                                value={formName}
                                onChange={(e) => setFormName(e.target.value)}
                            />
                        </label>
                    </div>

                    {/* Description */}
                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Mô tả / Ghi chú
                        <textarea
                            className="w-full rounded-xl border border-pos-border bg-white p-3 font-medium text-gray-700 focus:outline-none"
                            placeholder="Nhập mô tả chi tiết của BOM..."
                            rows={3}
                            value={formDescription}
                            onChange={(e) => setFormDescription(e.target.value)}
                        />
                    </label>

                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Trạng thái
                        <select
                            className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                            value={formStatus}
                            onChange={(e) => setFormStatus(e.target.value as any)}
                        >
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </select>
                    </label>

                    {/* Dynamic items details table */}
                    <div className="grid gap-3 border-t border-pos-border pt-4">
                        <div className="flex items-center justify-between">
                            <h3 className="font-bold text-gray-700 text-base">
                                Danh sách định lượng nguyên liệu
                            </h3>
                            <button
                                type="button"
                                onClick={addIngredientRow}
                                className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-[#07883a] bg-white px-3.5 text-xs font-bold text-[#07883a] hover:bg-green-50 transition-all cursor-pointer"
                            >
                                <Plus size={14} />
                                Thêm dòng
                            </button>
                        </div>

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
                                                    onChange={(e) =>
                                                        updateRowItem(
                                                            index,
                                                            "ingredientId",
                                                            e.target.value
                                                        )
                                                    }
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
                                                    onChange={(e) =>
                                                        updateRowItem(
                                                            index,
                                                            "quantity",
                                                            Number(e.target.value)
                                                        )
                                                    }
                                                />
                                            </td>
                                            <td className="p-2">
                                                <select
                                                    className="h-9 w-full rounded-lg border border-pos-border bg-white px-2 focus:outline-none"
                                                    value={row.unitId}
                                                    onChange={(e) =>
                                                        updateRowItem(
                                                            index,
                                                            "unitId",
                                                            e.target.value
                                                        )
                                                    }
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
                                                    onChange={(e) =>
                                                        updateRowItem(
                                                            index,
                                                            "wasteRate",
                                                            Number(e.target.value)
                                                        )
                                                    }
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

export function BomListPage() {
    const pageState = useCatalogPageState();
    const navigate = useNavigate();
    const location = useLocation();

    // Dropdown list resources
    const [variants, setVariants] = useState<ProductVariant[]>([]);
    const [ingredients, setIngredients] = useState<Ingredient[]>([]);
    const [units, setUnits] = useState<Unit[]>([]);

    // Selection states
    const [editingBom, setEditingBom] = useState<BomResponse | null>(null);
    const [showForm, setShowForm] = useState(false);

    // Filters extra state (BOM specific filters)
    const [filterCategoryId, setFilterCategoryId] = useState("");
    const [filterVersion, setFilterVersion] = useState("");

    // Searchable dropdown states for Category
    const [categoryOptions, setCategoryOptions] = useState<LookupItem[]>([]);
    const [categoryPage, setCategoryPage] = useState(0);
    const [categoryHasMore, setCategoryHasMore] = useState(true);
    const [categoryLoading, setCategoryLoading] = useState(false);
    const [categorySearchTerm, setCategorySearchTerm] = useState("");

    // Load main BOMs table paged resource
    const loadBoms = useCallback(
        () =>
            bomApi.list({
                page: pageState.page - 1,
                size: pageState.pageSize,
                search: pageState.query || undefined,
                categoryId: filterCategoryId || undefined,
                version: filterVersion ? Number(filterVersion) : undefined,
                status: pageState.status === "ALL" ? undefined : pageState.status
            }),
        [
            pageState.page,
            pageState.pageSize,
            pageState.query,
            pageState.status,
            filterCategoryId,
            filterVersion
        ]
    );

    const resource = usePagedApiResource(loadBoms);
    const boms = resource.data?.content ?? [];
    
    // Fetch category options with infinite scroll
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

    // Initial load and search change for categories
    useEffect(() => {
        setCategoryPage(0);
        loadCategoryOptions(categorySearchTerm, 0);
    }, [categorySearchTerm, loadCategoryOptions]);

    const handleLoadMoreCategories = useCallback(() => {
        if (!categoryLoading && categoryHasMore) {
            const nextPage = categoryPage + 1;
            setCategoryPage(nextPage);
            loadCategoryOptions(categorySearchTerm, nextPage);
        }
    }, [categoryLoading, categoryHasMore, categoryPage, categorySearchTerm, loadCategoryOptions]);

    // Load active dropdown options once
    useEffect(() => {
        // Load variants
        productVariantApi.list({ size: 1000, status: "ACTIVE" }).then((res) => {
            setVariants(res.content ?? []);
        }).catch(err => console.error("Error loading variants", err));

        // Load ingredients
        ingredientApi.list({ size: 1000, status: "ACTIVE" }).then((res) => {
            setIngredients(res.content ?? []);
        }).catch(err => console.error("Error loading ingredients", err));

        unitApi.list({ page: 0, size: 500 }).then((res) => {
            setUnits(res.content ?? []);
        }).catch(err => console.error("Error loading units", err));
    }, []);

    // Listen to redirect state or query params to trigger edit mode or create mode
    useEffect(() => {
        const searchParams = new URLSearchParams(location.search);
        const createBomParam = searchParams.get("create");
        const variantIdParam = searchParams.get("variantId");
        const state = location.state as { editBomId?: string; createBom?: boolean; variantId?: string } | null;

        if (state?.editBomId) {
            bomApi.detail(state.editBomId)
                .then((res) => {
                    setEditingBom(res);
                    setShowForm(true);
                })
                .catch((err) => console.error("Error fetching BOM for editing from redirect state", err));

            // Clean state to avoid re-triggering on subsequent navigation actions
            window.history.replaceState({}, document.title);
        } else if (state?.createBom || createBomParam === "true") {
            const targetVariantId = state?.variantId || variantIdParam;
            setEditingBom(targetVariantId ? ({ productVariantId: targetVariantId } as any) : null);
            setShowForm(true);
            // Clean state and search query to avoid re-triggering
            window.history.replaceState({}, document.title);
            navigate(location.pathname, { replace: true });
        }
    }, [location.state, location.search, navigate]);

    const handleSave = async (payload: any) => {
        if (editingBom && editingBom.id) {
            await bomApi.update(editingBom.id, payload);
        } else {
            await bomApi.create(payload);
        }
        resource.refresh();
    };

    const handleDelete = async (item: any) => {
        await bomApi.delete(item.id);
    };

    const handleToggleStatus = async (item: any) => {
        const nextStatus = item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
        await bomApi.updateStatus(item.id, nextStatus);
    };

    return (
        <CatalogResourceManagement
            title="Định mức nguyên liệu (BOM)"
            description="Quản lý công thức sản xuất và định mức nguyên liệu."
            createLabel="Tạo BOM"
            searchPlaceholder="Tìm theo tên BOM, mã, tên sản phẩm, mặt hàng, SKU..."
            totalItemsLabel="Tổng số công thức"
            items={boms}
            loading={resource.loading}
            error={resource.error}
            query={pageState.query}
            status={pageState.status}
            page={pageState.page}
            pageCount={resource.data?.totalPages ?? 1}
            totalItems={resource.data?.totalElements ?? 0}
            pageSize={pageState.pageSize}
            onQueryChange={pageState.setQuery}
            onStatusChange={(val) => pageState.setStatus(val as any)}
            onPageChange={pageState.setPage}
            onPageSizeChange={pageState.setPageSize}
            onRefresh={resource.refresh}
            onDelete={handleDelete}
            onChangeStatus={handleToggleStatus}
            detailPath={(item) => routePaths.bomDetail.replace(":id", String(item.id))}
            controlledEditingItem={editingBom}
            controlledShowForm={showForm}
            onControlledShowFormChange={setShowForm}
            onControlledEditingItemChange={(item) => setEditingBom(item as BomResponse | null)}
            onSubmit={async () => {}} // Not used because customForm handles submission
            renderDetail={() => null} // Clicks navigate directly via detailPath
            columns={[
                { key: "name", label: "Tên BOM" },
                { key: "variantName", label: "Mặt Hàng" },
                { key: "isDefault", label: "Mặc định" },
                { key: "status", label: "Trạng thái" }
            ]}
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
                                pageState.setPage(1);
                            }}
                            options={categoryOptions}
                            onSearch={setCategorySearchTerm}
                            onLoadMore={handleLoadMoreCategories}
                            hasMore={categoryHasMore}
                            loading={categoryLoading}
                            placeholder="Danh mục"
                        />
                    )
                },
                {
                    label: "Phiên bản",
                    value: filterVersion,
                    options: [],
                    onChange: setFilterVersion,
                    render: (
                        <input
                            type="number"
                            className="h-10 w-[120px] rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] placeholder:text-gray-400"
                            placeholder="Phiên bản..."
                            value={filterVersion}
                            onChange={(e) => {
                                setFilterVersion(e.target.value);
                                pageState.setPage(1);
                            }}
                            min="1"
                        />
                    )
                }
            ]}
            renderCells={(item: any) => (
                <>
                    <TableCell className="font-bold text-gray-500">
                        <span>{item.name || "-"}</span>
                    </TableCell>
                    <TableCell>
                        <div>
                            <p className="font-semibold text-gray-800">
                                {item.variantName}
                            </p>
                            <p className="text-xs text-gray-400">
                                {item.productName}
                            </p>
                        </div>
                    </TableCell>
                    <TableCell>
                        {item.isDefault ? (
                            <span className="inline-block rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-bold text-green-800">
                                ✓ Mặc định
                            </span>
                        ) : (
                            <span className="inline-block rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-400">
                                Không
                            </span>
                        )}
                    </TableCell>
                    <TableCell>
                        <StatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            customForm={(editingItem, onClose) => (
                <BomFormWrapper
                    editingItem={editingItem as BomResponse | null}
                    onClose={onClose}
                    variants={variants}
                    ingredients={ingredients}
                    units={units}
                    onSave={handleSave}
                />
            )}
        />
    );
}
