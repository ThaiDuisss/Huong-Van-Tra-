import { Plus, Trash2, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { ActionButton } from "../components/shared/ActionButton";
import { useNavigate, useLocation } from "react-router-dom";
import { routePaths } from "../../../routes/path";
import { PageHeader } from "../components/shared/PageHeader";
import { Pagination } from "../components/shared/Pagination";
import { SearchBox } from "../components/shared/SearchBox";
import { SearchableDropdown } from "../components/shared/SearchableDropdown";
import { BomTable } from "../components/bom/BomTable";

import { bomApi } from "../services/bom.api";
import { ingredientApi } from "../../product/services/ingredient.api";
import { productVariantApi } from "../../product/services/productVariant.api";
import { unitApi } from "../../product/services/unit.api";
import { categoryApi } from "../../product/services/category.api";

import type { BomResponse, BomItemInput, BomStatus, LookupItem } from "../types/bom.types";
import type { Ingredient, ProductVariant, Unit } from "../../product/types/product.types";
import { usePagedApiResource } from "../../product/hooks/useApiResource";
import { useCatalogPageState } from "../../product/hooks/useCatalogPageState";

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

    // Form dynamic rows state
    const [formVariantId, setFormVariantId] = useState("");
    const [formBomCode, setFormBomCode] = useState("");
    const [formName, setFormName] = useState("");
    const [formIsDefault, setFormIsDefault] = useState(false);
    const [formVersion, setFormVersion] = useState(1);
    const [formStatus, setFormStatus] = useState<BomStatus>("ACTIVE");
    const [formDescription, setFormDescription] = useState("");
    const [formItems, setFormItems] = useState<BomItemInput[]>([]);
    const [formError, setFormError] = useState("");
    const [formSubmitting, setFormSubmitting] = useState(false);

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

    // Listen to redirect state to trigger edit mode
    useEffect(() => {
        const state = location.state as { editBomId?: string } | null;
        if (state?.editBomId) {
            bomApi.detail(state.editBomId)
                .then((res) => {
                    handleEdit(res);
                })
                .catch((err) => console.error("Error fetching BOM for editing from redirect state", err));

            // Clean state to avoid re-triggering on subsequent navigation actions
            window.history.replaceState({}, document.title);
        }
    }, [location.state]);


    // Handle trigger open Form dialog for Create
    function handleCreate() {
        setEditingBom(null);
        setFormVariantId("");
        setFormBomCode("");
        setFormName("");
        setFormIsDefault(false);
        setFormVersion(1);
        setFormStatus("ACTIVE");
        setFormDescription("");
        setFormItems([{ ingredientId: "", quantity: 1, unitId: "", wasteRate: 0 }]);
        setFormError("");
        setShowForm(true);
    }

    // Handle trigger open Form dialog for Update
    function handleEdit(bom: BomResponse) {
        setEditingBom(bom);
        setFormVariantId(bom.productVariantId);
        setFormBomCode(bom.bomCode || "");
        setFormName(bom.name || "");
        setFormIsDefault(bom.isDefault);
        setFormVersion(bom.version);
        setFormStatus(bom.status);
        setFormDescription(bom.description || "");
        setFormItems(
            bom.items.map((item) => ({
                ingredientId: item.ingredient.id,
                quantity: item.quantity,
                unitId: item.unit.id,
                wasteRate: item.wasteRate
            }))
        );
        setFormError("");
        setShowForm(true);
    }

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
        if (editingBom && (!formVersion || formVersion <= 0)) {
            setFormError("Phiên bản BOM phải lớn hơn 0.");
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

    // Submit form handler
    async function handleSubmit(event: React.FormEvent) {
        event.preventDefault();
        if (!validateForm()) return;

        setFormSubmitting(true);
        try {
            const payload = {
                productVariantId: formVariantId,
                name: formName || null,
                bomCode: formBomCode || null,
                isDefault: formIsDefault,
                version: editingBom ? Number(formVersion) : null,
                status: formStatus,
                description: formDescription || null,
                items: formItems.map((item) => ({
                    ...item,
                    quantity: Number(item.quantity),
                    wasteRate: Number(item.wasteRate)
                }))
            };

            if (editingBom) {
                await bomApi.update(editingBom.id, payload);
            } else {
                await bomApi.create(payload);
            }
            setShowForm(false);
            resource.refresh();
        } catch (err: any) {
            console.error(err);
            setFormError(err?.message || "Có lỗi xảy ra khi lưu BOM.");
        } finally {
            setFormSubmitting(false);
        }
    }

    // Toggle BOM status (Enable/Disable)
    async function handleToggleStatus(item: BomResponse) {
        try {
            const nextStatus = item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
            await bomApi.updateStatus(item.id, nextStatus);
            resource.refresh();
        } catch (err: any) {
            alert(err?.message || "Không thể thay đổi trạng thái BOM.");
        }
    }

    return (
        <section className="grid gap-5">
            <PageHeader
                title="Danh sách công thức"
                description="Quản lý công thức sản xuất và định mức nguyên liệu."
                actions={
                    <div className="flex flex-wrap gap-2">
                        <ActionButton variant="primary" onClick={handleCreate}>
                            <Plus size={17} aria-hidden="true" />
                            Tạo BOM
                        </ActionButton>
                    </div>
                }
            />

            {/* Filter Container */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                <div className="flex flex-col md:flex-row gap-3 justify-between items-stretch md:items-center">
                    <div className="relative flex-1">
                        <SearchBox
                            value={pageState.query}
                            placeholder="Tìm theo tên BOM, mã, tên sản phẩm, mặt hàng, SKU..."
                            onChange={(value) => {
                                pageState.setQuery(value);
                            }}
                            className="w-full"
                        />
                    </div>
                    <div className="flex flex-wrap gap-3 items-center">
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
                            placeholder="Tất cả danh mục"
                        />
                        <input
                            type="number"
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] min-w-[120px]"
                            placeholder="Phiên bản..."
                            value={filterVersion}
                            onChange={(e) => {
                                setFilterVersion(e.target.value);
                                pageState.setPage(1);
                            }}
                            min="1"
                        />
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 pt-3 border-t border-pos-border mt-3">
                    <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                        Trạng thái:
                    </span>
                    <div className="flex flex-wrap gap-2 items-center">
                        {[
                            { label: "Tất cả", value: "ALL" },
                            { label: "Đang hoạt động", value: "ACTIVE" },
                            { label: "Ngừng hoạt động", value: "INACTIVE" }
                        ].map((opt) => {
                            const isActive = pageState.status === opt.value;
                            return (
                                <button
                                    key={opt.value}
                                    onClick={() => pageState.setStatus(opt.value as any)}
                                    type="button"
                                    className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${isActive
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                >
                                    {opt.label}
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>

            {/* Table Content & Details dialog */}
            <div className="grid gap-4 grid-cols-1">
                <section className="flex flex-col min-h-[580px] min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card border border-pos-border">
                    <div className="flex-1">
                        {resource.loading ? (
                            <div className="grid place-items-center py-20 text-gray-400 font-medium">
                                Đang tải Danh sách công thức...
                            </div>
                        ) : resource.error ? (
                            <div className="grid place-items-center py-20 text-red-500 font-semibold">
                                {resource.error}
                            </div>
                        ) : (
                             <BomTable
                                 items={boms}
                                 selectedBomId=""
                                 onRowClick={(item) => navigate(routePaths.bomDetail.replace(":id", item.id))}
                                 onEdit={handleEdit}
                                 onToggleStatus={handleToggleStatus}
                             />
                        )}
                    </div>
                    <Pagination
                        page={pageState.page}
                        pageCount={resource.data?.totalPages ?? 1}
                        totalItems={resource.data?.totalElements ?? 0}
                        pageSize={pageState.pageSize}
                        onPageChange={pageState.setPage}
                        onPageSizeChange={pageState.setPageSize}
                        totalItemsLabel="Tổng số BOM"
                    />
                </section>


            </div>

            {/* Dynamic Create/Update Form Dialog */}
            {showForm && (
                <div className="fixed inset-0 z-40 grid place-items-center bg-black/40 p-6 overflow-y-auto">
                    <form
                        onSubmit={handleSubmit}
                        className="my-8 w-full max-w-4xl rounded-3xl bg-white p-6 shadow-pos-panel border border-pos-border max-h-[90vh] overflow-y-auto"
                    >
                        <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                            <h2 className="text-xl font-bold text-gray-700">
                                {editingBom ? "Chỉnh sửa BOM" : "Tạo BOM mới"}
                            </h2>
                            <button
                                type="button"
                                onClick={() => setShowForm(false)}
                                className="grid size-9 place-items-center rounded-lg hover:bg-neutral-100 transition-all focus:outline-none"
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
                                <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                                    Mặt hàng (Product Variant) *
                                    <select
                                        className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none disabled:bg-neutral-100 disabled:text-gray-400"
                                        value={formVariantId}
                                        onChange={(e) => setFormVariantId(e.target.value)}
                                        disabled={!!editingBom}
                                    >
                                        <option value="">-- Chọn Mặt hàng --</option>
                                        {variants.map((v) => (
                                            <option key={v.id} value={v.id}>
                                                {v.variantName} ({v.sku})
                                            </option>
                                        ))}
                                    </select>
                                </label>

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
                                    className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none disabled:bg-neutral-100 disabled:text-gray-400"
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
                                onClick={() => setShowForm(false)}
                                className="h-11 rounded-xl border border-pos-border bg-white px-6 font-bold text-gray-500 hover:bg-neutral-50 transition-all cursor-pointer focus:outline-none"
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="submit"
                                disabled={formSubmitting}
                                className="h-11 rounded-xl bg-[#07883a] px-6 font-bold text-white hover:bg-[#067331] transition-all disabled:opacity-50 cursor-pointer shadow-sm focus:outline-none"
                            >
                                {formSubmitting ? "Đang lưu..." : editingBom ? "Lưu thay đổi" : "Tạo mới"}
                            </button>
                        </footer>
                    </form>
                </div>
            )}
        </section>
    );
}
