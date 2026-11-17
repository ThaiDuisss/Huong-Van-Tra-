import { Download, Upload, Plus, Trash2, X, Printer } from "lucide-react";
import { useCallback, useState, useEffect, useMemo } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { Button, IconButton } from "../../../components/ui/Button";
import { NativeSelect, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import {
    Table,
    TableCell as UiTableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import { toast } from "../../../components/ui/Toast";
import { routePaths } from "../../../routes/path";
import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown } from "../../manufacturing/components/shared/useLookupDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import { bomApi } from "../../manufacturing/services/bom.api";
import type { BomItemInput } from "../../manufacturing/types/bom.types";
import { categoryApi } from "../services/category.api";

import {
    CatalogStatusBadge,
    DetailList,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { ingredientApi } from "../services/ingredient.api";
import { productApi } from "../services/product.api";
import { productVariantApi } from "../services/productVariant.api";
import { unitApi } from "../services/unit.api";
import type {
    CatalogStatus,
    InventoryPolicy,
    ProductVariant,
    ProductVariantInput
} from "../types/product.types";
import { formatCurrency } from "../utils/product-formatters";
import { BulkBarcodePrintModal } from "../components/BulkBarcodePrintModal";

interface ProductVariantFormWrapperProps {
    editingItem: ProductVariant | null;
    onClose(): void;
    productOptions: LookupItem[];
    onProductSearch(term: string): void;
    onLoadMoreProducts(): void;
    productHasMore: boolean;
    productLoading: boolean;
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
    const [formBarcode, setFormBarcode] = useState(editingItem?.barcode || "");
    const [formInventoryPolicy, setFormInventoryPolicy] = useState<InventoryPolicy>(
        editingItem?.inventoryPolicy || "TRACK_STOCK"
    );
    const [formStatus, setFormStatus] = useState<CatalogStatus>(editingItem?.status || "ACTIVE");

    const [formItems, setFormItems] = useState<BomItemInput[]>([]);
    const [itemsLoading, setItemsLoading] = useState(false);

    const [formError, setFormError] = useState("");
    const [formSubmitting, setFormSubmitting] = useState(false);

    const selectedProductName = productOptions.find((p) => p.id === formProductId)?.name ?? "";
    // Dropdown đơn vị: lookup phân trang dùng chung cho đơn vị bán và các dòng định mức
    const unitDropdown = useLookupDropdown(unitApi.lookup, {
        selectedIds: [formUnitId, ...formItems.map((item) => item.unitId)],
        fetchByIds: unitApi.lookupByIds
    });
    const selectedUnit = unitDropdown.findOption(formUnitId);
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

    // Dropdown nguyên liệu: lookup phân trang dùng chung cho các dòng định mức
    const ingredientDropdown = useLookupDropdown(ingredientApi.lookup, {
        selectedIds: formItems.map((item) => item.ingredientId),
        fetchByIds: ingredientApi.lookupByIds
    });

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
        console.log("formQuantity", formQuantity);
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
                status: formStatus,
                barcode: formBarcode.trim() ? formBarcode.trim() : null
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
        <ModalShell
            className="w-[min(896px,calc(100vw-32px))] p-6"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="product-variant-form-title"
            onClose={onClose}
        >
            <form onSubmit={handleSubmit}>
                <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                    <h2 className="text-xl font-bold text-gray-700" id="product-variant-form-title">
                        {isEditMode ? "Chỉnh sửa Mặt hàng" : "Tạo Mặt hàng mới"}
                    </h2>
                    <IconButton aria-label="Đóng biểu mẫu" onClick={onClose} size="md">
                        <X size={20} />
                    </IconButton>
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
                            <TextInput
                                type="number"
                                min={0}
                                step="any"
                                className="font-medium"
                                placeholder="Ví dụ: 100, 250, 500"
                                value={formQuantity}
                                onChange={(e) => setFormQuantity(Number(e.target.value))}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Giá bán *
                            <TextInput
                                type="number"
                                className="font-medium"
                                value={formSellingPrice}
                                onChange={(e) => setFormSellingPrice(Number(e.target.value))}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Giá vốn *
                            <TextInput
                                type="number"
                                className="font-medium"
                                value={formCostPrice}
                                onChange={(e) => setFormCostPrice(Number(e.target.value))}
                            />
                        </label>

                        <div className="grid gap-1.5 text-sm font-bold text-gray-500">
                            <span>Đơn vị bán thương mại *</span>
                            <SearchableDropdown
                                emptyText="Không tìm thấy đơn vị"
                                hasMore={unitDropdown.hasMore}
                                loading={unitDropdown.loading}
                                onChange={setFormUnitId}
                                onLoadMore={unitDropdown.onLoadMore}
                                onSearch={unitDropdown.onSearch}
                                options={unitDropdown.options}
                                placeholder="-- Chọn đơn vị --"
                                value={formUnitId}
                            />
                        </div>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Mã vạch (Barcode)
                            <TextInput
                                type="text"
                                placeholder="Nhập mã vạch hoặc để trống"
                                className="font-mono font-medium"
                                value={formBarcode}
                                onChange={(e) => setFormBarcode(e.target.value)}
                            />
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Chính sách tồn kho *
                            <NativeSelect
                                className="font-medium"
                                value={formInventoryPolicy}
                                onChange={(e) => setFormInventoryPolicy(e.target.value as InventoryPolicy)}
                            >
                                <option value="TRACK_STOCK">Theo dõi tồn kho</option>
                                <option value="DO_NOT_TRACK">Không theo dõi</option>
                            </NativeSelect>
                        </label>

                        <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                            Trạng thái *
                            <NativeSelect
                                className="font-medium"
                                value={formStatus}
                                onChange={(e) => setFormStatus(e.target.value as CatalogStatus)}
                            >
                                <option value="ACTIVE">Đang hoạt động</option>
                                <option value="INACTIVE">Ngừng hoạt động</option>
                            </NativeSelect>
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
                            <Button onClick={addIngredientRow} size="sm">
                                <Plus size={14} />
                                Thêm dòng
                            </Button>
                        </div>

                        {itemsLoading ? (
                            <div className="text-sm font-semibold text-gray-400">Đang tải định mức nguyên liệu...</div>
                        ) : formItems.length === 0 ? (
                            <div className="rounded-xl border border-dashed border-pos-border p-4 text-center text-sm font-medium text-gray-400">
                                Chưa có dòng nguyên liệu nào.
                            </div>
                        ) : (
                            <div className="max-h-[300px] overflow-y-auto rounded-xl border border-pos-border">
                                <Table>
                                    <TableHead className="sticky top-0 z-10">
                                        <TableRow>
                                            <TableHeaderCell className="w-5/12">Nguyên liệu *</TableHeaderCell>
                                            <TableHeaderCell className="w-2/12">Định lượng *</TableHeaderCell>
                                            <TableHeaderCell className="w-2/12">Đơn vị *</TableHeaderCell>
                                            <TableHeaderCell className="w-2/12">Hao hụt (%)</TableHeaderCell>
                                            <TableHeaderCell className="w-1/12 text-center">Xóa</TableHeaderCell>
                                        </TableRow>
                                    </TableHead>
                                    <tbody>
                                        {formItems.map((row, index) => (
                                            <TableRow key={index} className="hover:bg-neutral-50/50">
                                                <UiTableCell>
                                                    <SearchableDropdown
                                                        emptyText="Không tìm thấy nguyên liệu"
                                                        hasMore={ingredientDropdown.hasMore}
                                                        loading={ingredientDropdown.loading}
                                                        onChange={(value) => updateRowItem(index, "ingredientId", value)}
                                                        onLoadMore={ingredientDropdown.onLoadMore}
                                                        onSearch={ingredientDropdown.onSearch}
                                                        options={ingredientDropdown.options}
                                                        placeholder="-- Chọn nguyên liệu --"
                                                        value={row.ingredientId}
                                                    />
                                                </UiTableCell>
                                                <UiTableCell>
                                                    <TextInput
                                                        type="number"
                                                        step="any"
                                                        className="font-semibold"
                                                        placeholder="0.00"
                                                        value={row.quantity}
                                                        onChange={(e) => updateRowItem(index, "quantity", Number(e.target.value))}
                                                    />
                                                </UiTableCell>
                                                <UiTableCell>
                                                    <SearchableDropdown
                                                        emptyText="Không tìm thấy đơn vị"
                                                        hasMore={unitDropdown.hasMore}
                                                        loading={unitDropdown.loading}
                                                        onChange={(value) => updateRowItem(index, "unitId", value)}
                                                        onLoadMore={unitDropdown.onLoadMore}
                                                        onSearch={unitDropdown.onSearch}
                                                        options={unitDropdown.options}
                                                        placeholder="-- Đơn vị --"
                                                        value={row.unitId}
                                                    />
                                                </UiTableCell>
                                                <UiTableCell>
                                                    <TextInput
                                                        type="number"
                                                        step="any"
                                                        min={0}
                                                        max={100}
                                                        placeholder="0"
                                                        value={row.wasteRate}
                                                        onChange={(e) => updateRowItem(index, "wasteRate", Number(e.target.value))}
                                                    />
                                                </UiTableCell>
                                                <UiTableCell className="text-center">
                                                    <IconButton aria-label="Xóa dòng" onClick={() => removeIngredientRow(index)}>
                                                        <Trash2 size={16} />
                                                    </IconButton>
                                                </UiTableCell>
                                            </TableRow>
                                        ))}
                                    </tbody>
                                </Table>
                            </div>
                        )}
                    </div>
                </div>

                <footer className="mt-6 flex items-center justify-end gap-3 border-t border-pos-border pt-4">
                    <Button onClick={onClose}>
                        Hủy bỏ
                    </Button>
                    <Button disabled={formSubmitting} type="submit" variant="primary">
                        {formSubmitting ? "Đang lưu..." : isEditMode ? "Lưu thay đổi" : "Tạo mới"}
                    </Button>
                </footer>
            </form>
        </ModalShell>
    );
}

export function ProductVariantListPage() {
    const { productId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const state = useCatalogPageState();

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
    const resource = usePagedApiResource(loadVariants);
    const variants = resource.data?.content ?? [];

    const [selectedVariantIds, setSelectedVariantIds] = useState<Set<string | number>>(new Set());
    const [showBulkPrintModal, setShowBulkPrintModal] = useState(false);

    const handleToggleSelect = (id: string | number) => {
        setSelectedVariantIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

    const isAllSelected = variants.length > 0 && variants.every((v) => selectedVariantIds.has(v.id));

    const handleToggleSelectAll = () => {
        if (isAllSelected) {
            setSelectedVariantIds((prev) => {
                const next = new Set(prev);
                variants.forEach((v) => next.delete(v.id));
                return next;
            });
        } else {
            setSelectedVariantIds((prev) => {
                const next = new Set(prev);
                variants.forEach((v) => next.add(v.id));
                return next;
            });
        }
    };

    const selectedVariants = useMemo(() => {
        return variants.filter((v) => selectedVariantIds.has(v.id));
    }, [variants, selectedVariantIds]);

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
            toast.error("Có lỗi xảy ra khi xuất file!");
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
                        <ActionButton
                            variant={selectedVariantIds.size > 0 ? "primary" : "secondary"}
                            onClick={() => {
                                if (selectedVariantIds.size === 0) {
                                    toast.warning("Vui lòng tích chọn ít nhất một mặt hàng để in mã vạch hàng loạt.");
                                    return;
                                }
                                setShowBulkPrintModal(true);
                            }}
                        >
                            <Printer size={17} aria-hidden="true" />
                            In mã {selectedVariantIds.size > 0 ? `(${selectedVariantIds.size})` : ""}
                        </ActionButton>
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.productVariantImport)}>
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
                    { key: "sku", label: "SKU / Mã vạch" },
                    { key: "quantity", label: "Định lượng" },
                    { key: "selling", label: "Giá bán" },
                    { key: "status", label: "Trạng thái" }
                ]}
                selectable={true}
                selectedIds={selectedVariantIds}
                onToggleSelect={handleToggleSelect}
                onToggleSelectAll={handleToggleSelectAll}
                isAllSelected={isAllSelected}
                renderCells={(item) => (
                    <>
                        <TableCell>
                            <Link
                                className="font-bold text-gray-900 hover:text-[#07883a] hover:underline"
                                onClick={(event) => event.stopPropagation()}
                                to={`/system/product-variants/${item.id}`}
                            >
                                {item.variantName}
                            </Link>
                            <div className="text-xs text-gray-500">{item.productName}</div>
                        </TableCell>
                        <TableCell>
                            <div className="font-mono text-xs font-semibold text-gray-700">{item.sku}</div>
                            {item.barcode ? (
                                <div className="font-mono text-xs text-emerald-700 font-medium">{item.barcode}</div>
                            ) : (
                                <span className="text-[11px] text-gray-400 italic">Chưa có mã vạch</span>
                            )}
                        </TableCell>
                        <TableCell>{item.quantity}</TableCell>
                        <TableCell>{formatCurrency(item.sellingPrice)}</TableCell>
                        <TableCell>
                            <CatalogStatusBadge status={item.status} />
                        </TableCell>
                    </>
                )}
                detailPath={(item) => `/system/product-variants/${item.id}`}
                renderDetail={(item) => {
                    const unitText = item.unitName
                        ? `${item.unitName}${item.unitSymbol ? ` (${item.unitSymbol})` : ""}`
                        : `Mã đơn vị: ${item.unitId}`;
                    return (
                        <div className="grid gap-4">
                            <DetailList
                                items={[
                                    { label: "Sản phẩm", value: item.productName },
                                    { label: "SKU", value: item.sku },
                                    { label: "Mã vạch (Barcode)", value: item.barcode || "Chưa có mã vạch" },
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
                        onSave={handleSave}
                    />
                )}
            />

            <BulkBarcodePrintModal
                isOpen={showBulkPrintModal}
                onClose={() => setShowBulkPrintModal(false)}
                variants={selectedVariants}
            />
        </>
    );
}
