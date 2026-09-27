import { useCallback, useState } from "react";

import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown, withPinned } from "../../manufacturing/components/shared/useLookupDropdown";

import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { usePagedApiResource } from "../hooks/useApiResource";
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
    const resource = usePagedApiResource(loadCategories);
    const categories = resource.data?.content ?? [];
    // Dropdown "Danh mục cha": lookup phân trang, chỉ gọi API khi mở dropdown
    const [pickedParent, setPickedParent] = useState<LookupItem | null>(null);
    const parentDropdown = useLookupDropdown(categoryApi.lookup, { lazy: true, pinned: [pickedParent] });

    function getParentName(item: Category) {
        if (item.parentId === null) {
            return "Danh mục gốc";
        }
        return item.parentName ?? `#${item.parentId}`;
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
                        // {
                        //     label: "Trạng thái",
                        //     value: (
                        //         <CatalogStatusBadge status={item.status} />
                        //     )
                        // },
                        {
                            label: "Tồn kho cảnh báo mặc định",
                            value: item.defaultReorderPoint
                        }
                    ]}
                />
            )}
            formFields={(editingItem) => [
                {
                    name: "name",
                    label: "Tên danh mục",
                    required: true
                },
                {
                    name: "parentId",
                    label: "Danh mục cha",
                    render: (value, onChange) => (
                            <SearchableDropdown
                                allOptionText="Không có"
                                emptyText="Không tìm thấy danh mục"
                                hasMore={parentDropdown.hasMore}
                                loading={parentDropdown.loading}
                                onChange={(val) => {
                                    const next = val === "ALL" ? "" : val;
                                    setPickedParent(parentDropdown.findOption(next));
                                    onChange(next);
                                }}
                                onLoadMore={parentDropdown.onLoadMore}
                                onOpen={parentDropdown.onOpen}
                                onSearch={parentDropdown.onSearch}
                                options={withPinned(parentDropdown.options, editingItem?.parentId ? { id: String(editingItem.parentId), name: editingItem.parentName ?? "Danh mục cha" } : null)}
                                placeholder="Tìm danh mục cha..."
                                value={value}
                            />
                        )
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
