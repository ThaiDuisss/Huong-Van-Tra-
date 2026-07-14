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
import type {
    CatalogStatus,
    Category
} from "../types/product.types";
import {
    getNumber,
    getString
} from "../utils/form-values";

export function CategoryListPage() {
    const state = useCatalogPageState();
    const loadCategories = useCallback(
        () =>
            categoryApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [state.page, state.pageSize, state.query, state.status]
    );
    const loadCategoryOptions = useCallback(
        () => categoryApi.list({ page: 0, size: 200 }),
        []
    );
    const resource = usePagedApiResource(loadCategories);
    const optionResource = useApiResource(loadCategoryOptions);
    const categories = resource.data?.content ?? [];
    const categoryOptions = optionResource.data?.content ?? [];

    function getParentName(item: Category) {
        if (item.parentId === null) {
            return "Danh mục gốc";
        }

        return (
            categoryOptions.find(
                (category) => category.id === item.parentId
            )?.name ?? `#${item.parentId}`
        );
    }

    async function submit(values: FormValues, editingItem: Category | null) {
        const input = {
            parentId: getString(values, "parentId") || null,
            categoryCode: editingItem ? editingItem.categoryCode : undefined,
            name: getString(values, "name"),
            status: getString(values, "status") as CatalogStatus,
            defaultReorderPoint: getNumber(values, "defaultReorderPoint")
        };

        if (editingItem) {
            await categoryApi.update(String(editingItem.id), input);
        } else {
            await categoryApi.create(input);
        }
    }

    const handleDelete = async (item: Category) => {
        await categoryApi.delete(String(item.id));
    };

    return (
        <CatalogResourceManagement
            title="Danh mục sản phẩm"
            description="Quản lý cây danh mục và trạng thái phân loại sản phẩm."
            createLabel="Tạo danh mục"
            searchPlaceholder="Tìm tên danh mục..."
            totalItemsLabel="Tổng số danh mục"
            items={categories}
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
            onDelete={handleDelete}
            onChangeStatus={(item) =>
                categoryApi.updateStatus(
                    String(item.id),
                    item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                ).then(() => undefined)
            }
            getInitialValues={(item) => ({
                name: item.name,
                parentId: item.parentId?.toString() ?? "",
                status: item.status,
                defaultReorderPoint: String(item.defaultReorderPoint ?? 10)
            })}
            columns={[
                { key: "name", label: "Tên danh mục" },
                { key: "parent", label: "Danh mục cha" },
                { key: "status", label: "Trạng thái" }
            ]}
            renderCells={(item) => (
                <>
                    <TableCell className="font-bold text-gray-500">
                        <span>{item.name}</span>
                        <span className="text-gray-500 block text-[10px] font-normal">{item.categoryCode}</span>
                    </TableCell>
                    <TableCell>{getParentName(item)}</TableCell>
                    <TableCell>
                        <CatalogStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item) => (
                <DetailList
                    items={[
                        { label: "Tên danh mục", value: item.name },
                        { label: "Mã danh mục", value: item.categoryCode },
                        {
                            label: "Danh mục cha",
                            value: getParentName(item)
                        },
                        {
                            label: "Trạng thái",
                            value: (
                                <CatalogStatusBadge status={item.status} />
                            )
                        },
                        {
                            label: "Tồn kho cảnh báo mặc định",
                            value: item.defaultReorderPoint
                        }
                    ]}
                />
            )}
            formFields={[
                {
                    name: "name",
                    label: "Tên danh mục",
                    required: true
                },
                {
                    name: "parentId",
                    label: "Danh mục cha",
                    type: "select",
                    options: [
                        { label: "Không có", value: "" },
                        ...categoryOptions.map((category) => ({
                            label: category.name,
                            value: String(category.id)
                        }))
                    ]
                },
                {
                    name: "defaultReorderPoint",
                    label: "Tồn tối thiểu mặc định",
                    type: "number",
                    required: true
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
