import { Package } from "lucide-react";
import { useCallback } from "react";

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
    getNumber,
    getString
} from "../utils/form-values";
import { routePaths } from "../../../routes/path";

export function ProductListPage() {
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
            state.status
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

    async function submit(
        values: FormValues,
        editingItem: ProductSummary | null
    ) {
        const description = getString(values, "description");
        const input = {
            categoryId: getNumber(values, "categoryId"),
            name: getString(values, "name"),
            description,
            imageUrl: getString(values, "imageUrl") || null,
            status: getString(values, "status") as CatalogStatus
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

    return (
        <CatalogResourceManagement
            title="Sản phẩm"
            description="Quản lý thông tin nhóm sản phẩm và các biến thể trực thuộc."
            createLabel="Tạo sản phẩm"
            searchPlaceholder="Tìm tên sản phẩm..."
            items={products}
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
            onSubmit={submit}
            onChangeStatus={(item) =>
                productApi.updateStatus(
                    String(item.id),
                    item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                ).then(() => undefined)
            }
            getInitialValues={(item) => ({
                categoryId: String(item.category.id),
                name: item.name,
                description: "",
                imageUrl: item.imageUrl ?? "",
                status: item.status
            })}
            detailPath={(item) =>
                routePaths.productDetail.replace(":id", String(item.id))
            }
            secondaryFilter={{
                label: "Danh mục",
                value: state.secondaryValue,
                options: categories.map((category) => ({
                    label: category.name,
                    value: String(category.id)
                })),
                onChange: state.setSecondaryValue
            }}
            columns={[
                { key: "image", label: "Ảnh" },
                { key: "name", label: "Tên sản phẩm" },
                { key: "category", label: "Danh mục" },
                { key: "variants", label: "Số biến thể" },
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
                            <span className="grid size-12 place-items-center rounded-xl bg-neutral-100 text-pos-muted">
                                <Package size={20} />
                            </span>
                        )}
                    </TableCell>
                    <TableCell className="font-bold text-pos-ink">
                        {item.name}
                    </TableCell>
                    <TableCell>{item.category.name}</TableCell>
                    <TableCell>{item.totalVariants}</TableCell>
                    <TableCell>
                        <CatalogStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item) => (
                <DetailList
                    items={[
                        { label: "Tên sản phẩm", value: item.name },
                        { label: "Danh mục", value: item.category.name },
                        {
                            label: "Tổng biến thể",
                            value: item.totalVariants
                        },
                        {
                            label: "Trạng thái",
                            value: (
                                <CatalogStatusBadge
                                    status={item.status}
                                />
                            )
                        }
                    ]}
                />
            )}
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
                    name: "imageUrl",
                    label: "URL hình ảnh"
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
    );
}
