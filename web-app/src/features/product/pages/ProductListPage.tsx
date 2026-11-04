import { Download, Package, Upload } from "lucide-react";
import { useCallback, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { toast } from "../../../components/ui/Toast";
import { routePaths } from "../../../routes/path";
import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";

import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { useApiResource, usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { categoryApi } from "../services/category.api";
import { productApi } from "../services/product.api";
import type {
    CatalogStatus,
    ProductSummary
} from "../types/product.types";
import {
    getString
} from "../utils/form-values";
import { formatCurrency } from "../utils/product-formatters";

export function ProductListPage() {
    const navigate = useNavigate();

    // Searchable dropdown states for Category
    const [categoryOptions, setCategoryOptions] = useState<LookupItem[]>([]);
    const [categoryPage, setCategoryPage] = useState(0);
    const [categoryHasMore, setCategoryHasMore] = useState(true);
    const [categoryLoading, setCategoryLoading] = useState(false);
    const [categorySearchTerm, setCategorySearchTerm] = useState("");
    const [filterCategoryId, setFilterCategoryId] = useState("");
    const state = useCatalogPageState();
    const loadProducts = useCallback(
        () =>
            productApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                categoryId:
                    state.secondaryValue === "ALL"
                        ? undefined
                        : state.secondaryValue,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [
            state.page,
            state.pageSize,
            state.query,
            state.secondaryValue,
            state.status,
            filterCategoryId
        ]
    );
    const loadCategories = useCallback(
        () => categoryApi.list({ page: 0, size: 200 }),
        []
    );
    const resource = usePagedApiResource(loadProducts);
    const categoryResource = useApiResource(loadCategories);
    const products = resource.data?.content ?? [];
    const categories = categoryResource.data?.content ?? [];

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

    async function submit(
        values: FormValues,
        editingItem: ProductSummary | null
    ) {
        const description = getString(values, "description");

        const imageUrl = getString(values, "imageFile") || null;
        const fieldId = getString(values, "imageFileId") || null;

        const input = {
            categoryId: getString(values, "categoryId"),
            productCode: editingItem ? editingItem.productCode : undefined,
            name: getString(values, "name"),
            description,
            imageUrl: imageUrl,
            status: getString(values, "status") as CatalogStatus,
            fieldId: fieldId
        };

        if (editingItem) {
            if (!description) {
                const detail = await productApi.detail(
                    String(editingItem.id)
                );
                input.description = detail.description ?? "";
            }
            await productApi.update(String(editingItem.id), input);
        } else {
            await productApi.create(input);
        }
    }

    const handleExport = async () => {
        try {
            const blob = await productApi.export();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);

            const now = new Date();
            const year = now.getFullYear();
            const month = String(now.getMonth() + 1).padStart(2, '0');
            const day = String(now.getDate()).padStart(2, '0');
            const hours = String(now.getHours()).padStart(2, '0');
            const minutes = String(now.getMinutes()).padStart(2, '0');
            const seconds = String(now.getSeconds()).padStart(2, '0');
            const timestamp = `${year}${month}${day}_${hours}${minutes}${seconds}`;

            link.setAttribute("download", `Danh sách sản phẩm_${timestamp}.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            toast.error("Có lỗi xảy ra khi xuất file!");
        }
    };

    const handleDelete = async (item: ProductSummary) => {
        await productApi.delete(String(item.id));
    };

    return (
        <>
            <CatalogResourceManagement
                title="Sản phẩm"
                description="Quản lý thông tin nhóm sản phẩm trực thuộc."
                createLabel="Tạo sản phẩm"
                searchPlaceholder="Tìm tên sản phẩm..."
                totalItemsLabel="Tổng số sản phẩm"
                extraHeaderActions={
                    <>
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.productImport)}>
                            <Upload size={17} aria-hidden="true" />
                            Nhập Excel
                        </ActionButton>
                        <ActionButton variant="secondary" onClick={handleExport}>
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                    </>
                }
                items={products}
                loading={resource.loading}
                error={resource.error}
                query={state.query}
                filters={[
                    {
                        label: "Trạng thái",
                        value: state.status,
                        options: [
                            { label: "Tất cả", value: "ALL" },
                            { label: "Đang hoạt động", value: "ACTIVE" },
                            { label: "Ngừng hoạt động", value: "INACTIVE" }
                        ],
                        onChange: (val) => state.setStatus(val as any)
                    },
                    {
                        label: "Danh mục",
                        value: state.secondaryValue,
                        options: [],
                        onChange: state.setSecondaryValue,
                        render: (
                            <SearchableDropdown
                                value={filterCategoryId}
                                onChange={(val) => {
                                    setFilterCategoryId(val);
                                    state.setPage(1);
                                }}
                                options={categoryOptions}
                                onSearch={setCategorySearchTerm}
                                onLoadMore={handleLoadMoreCategories}
                                hasMore={categoryHasMore}
                                loading={categoryLoading}
                                placeholder="Danh mục"
                            />
                        )
                    }
                ]}
                page={(resource.data?.page ?? 0) + 1}
                pageCount={resource.data?.totalPages ?? 1}
                totalItems={resource.data?.totalElements ?? 0}
                pageSize={state.pageSize}
                onQueryChange={state.setQuery}
                onPageChange={state.setPage}
                onPageSizeChange={state.setPageSize}
                onRefresh={resource.refresh}
                onSubmit={submit}
                onDelete={handleDelete}
                onChangeStatus={(item) =>
                    productApi.updateStatus(
                        String(item.id),
                        item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                    ).then(() => undefined)
                }
                getInitialValues={(item) => ({
                    categoryId: item.category?.id ? String(item.category.id) : "",
                    name: item.name,
                    description: "",
                    imageFile: item.imageUrl ?? "",
                    status: item.status
                })}
                columns={[
                    { key: "image", label: "Ảnh" },
                    { key: "name", label: "Tên sản phẩm" },
                    { key: "category", label: "Danh mục" },
                    // { key: "variants", label: "Số Mặt hàng" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item) => (
                    <>
                        <TableCell>
                            {item.imageUrl ? (
                                <img
                                    src={item.imageUrl}
                                    alt=""
                                    className="size-12 rounded-xl object-cover"
                                />
                            ) : (
                                <span className="grid size-12 place-items-center rounded-xl bg-neutral-100 text-gray-500">
                                    <Package size={20} />
                                </span>
                            )}
                        </TableCell>
                        <TableCell className="font-bold text-gray-500">
                            <span>{item.name}</span>
                            <span className="text-gray-500 block text-[10px] font-normal">{item.productCode}</span>
                        </TableCell>
                        <TableCell>{item.category?.name || "—"}</TableCell>
                        {/* <TableCell>{item.totalVariants}</TableCell> */}
                        <TableCell>
                            <CatalogStatusBadge status={item.status} />
                        </TableCell>
                    </>
                )}
                renderDetail={(item) => <ProductDetailContent productId={item.id} />}
                formFields={[
                    {
                        name: "categoryId",
                        label: "Danh mục",
                        type: "select",
                        required: true,
                        options: categories.map((category) => ({
                            label: category.name,
                            value: String(category.id)
                        }))
                    },
                    {
                        name: "name",
                        label: "Tên sản phẩm",
                        required: true
                    },
                    {
                        name: "description",
                        label: "Mô tả",
                        type: "textarea"
                    },
                    {
                        name: "imageFile",
                        label: "Hình ảnh sản phẩm",
                        type: "file",
                        purpose: "PRODUCT_IMAGE"
                    },
                    {
                        name: "status",
                        label: "Trạng thái",
                        type: "select",
                        required: true,
                        options: [
                            { label: "Đang hoạt động", value: "ACTIVE" },
                            { label: "Ngừng hoạt động", value: "INACTIVE" }
                        ]
                    }
                ]}
            />
        </>
    );
}

function ProductDetailContent({ productId }: { productId: string | number }) {
    const loadDetail = useCallback(() => productApi.detail(String(productId)), [productId]);
    const { data: product, loading, error } = useApiResource(loadDetail);

    if (loading) return <div className="text-sm text-gray-500 p-4">Đang tải chi tiết...</div>;
    if (error || !product) return <div className="text-sm text-red-500 p-4">Lỗi tải chi tiết: {error}</div>;

    return (
        <div className="grid gap-6">
            {product.imageUrl ? (
                <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="h-40 w-full rounded-2xl object-cover"
                />
            ) : (
                <div className="grid h-40 place-items-center rounded-2xl bg-neutral-100 text-gray-500">
                    <Package size={34} />
                </div>
            )}

            <DetailList
                items={[
                    { label: "Tên sản phẩm", value: product.name },
                    { label: "Mã sản phẩm", value: product.productCode },
                    { label: "Danh mục", value: product.category.name },
                    { label: "Mô tả", value: product.description || "Chưa có mô tả." },
                    {
                        label: "Trạng thái",
                        value: <CatalogStatusBadge status={product.status} />
                    }
                ]}
            />

            {product.variants && product.variants.length > 0 ? (
                <div className="border-t border-pos-border pt-4">
                    <h3 className="text-sm font-bold text-gray-700 mb-2">
                        Danh sách Mặt hàng ({product.variants.length})
                    </h3>
                    <div className="grid gap-2 text-xs">
                        {product.variants.map((variant) => (
                            <div key={variant.id} className="flex justify-between border-b border-pos-border pb-1.5 last:border-0">
                                <div>
                                    <span className="font-semibold">{variant.variantName}</span>
                                    <span className="text-gray-400 block text-[10px]">{variant.sku}</span>
                                </div>
                                <div className="text-right">
                                    <span className="font-bold text-hvt-green-900 block">{formatCurrency(variant.sellingPrice)}</span>
                                    <span className="text-[10px]">{variant.status === "ACTIVE" ? "Đang bán" : "Ngừng bán"}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            ) : null}
        </div>
    );
}
