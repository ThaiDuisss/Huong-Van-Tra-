import { Download, Package, Upload } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { routePaths } from "../../../routes/path";
import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../../product/components/CatalogResourceManagement";
import { usePagedApiResource } from "../../product/hooks/useApiResource";
import { useCatalogPageState } from "../../product/hooks/useCatalogPageState";
import { ingredientApi } from "../../product/services/ingredient.api";
import { unitApi } from "../../product/services/unit.api";
import type {
    CatalogStatus,
    Ingredient,
    Unit
} from "../../product/types/product.types";
import {
    getString
} from "../../product/utils/form-values";

export function RawMaterialListPage() {
    const navigate = useNavigate();
    const state = useCatalogPageState();
    const [units, setUnits] = useState<Unit[]>([]);

    useEffect(() => {
        unitApi.list({ size: 200, status: "ACTIVE" })
            .then((res) => setUnits(res.content || []))
            .catch((err) => console.error("Lỗi tải danh sách đơn vị tính:", err));
    }, []);
    
    const loadIngredients = useCallback(
        () =>
            ingredientApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [
            state.page,
            state.pageSize,
            state.query,
            state.status
        ]
    );

    const resource = usePagedApiResource(loadIngredients);
    const ingredients = resource.data?.content ?? [];

    async function submit(
        values: FormValues,
        editingItem: Ingredient | null
    ) {
        const unitIdVal = getString(values, "unitId");
        const reorderPointVal = getString(values, "reorderPoint");
        const imageUrl = getString(values, "imageFile") || null;
        const input = {
            name: getString(values, "name"),
            imageUrl: imageUrl,
            unitId: unitIdVal ? unitIdVal : null,
            reorderPoint: reorderPointVal ? Number(reorderPointVal) : 0,
            status: getString(values, "status") as CatalogStatus
        };

        if (editingItem) {
            await ingredientApi.update(String(editingItem.id), input);
        } else {
            await ingredientApi.create(input);
        }
    }

    const handleExport = async () => {
        try {
            const blob = await ingredientApi.export({
                keyword: state.query || undefined,
                status: state.status === "ALL" ? undefined : state.status
            });
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
            
            link.setAttribute("download", `Danh sách nguyên liệu_${timestamp}.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            alert("Có lỗi xảy ra khi xuất file!");
        }
    };

    const handleDelete = async (item: Ingredient) => {
        await ingredientApi.delete(String(item.id));
    };

    return (
        <>
            <CatalogResourceManagement
                title="Nguyên liệu"
                description="Quản lý danh sách nguyên liệu và trạng thái sử dụng trong sản xuất."
                createLabel="Tạo nguyên liệu"
                searchPlaceholder="Tìm kiếm nguyên liệu..."
                totalItemsLabel="Tổng số nguyên liệu"
                extraHeaderActions={
                    <>
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.ingredientImport)}>
                            <Upload size={17} aria-hidden="true" />
                            Nhập Excel
                        </ActionButton>
                        <ActionButton variant="secondary" onClick={handleExport}>
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                    </>
                }
                items={ingredients}
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
                onChangeStatus={(item: Ingredient) =>
                    ingredientApi.updateStatus(
                        String(item.id),
                        item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                    ).then(() => undefined)
                }
                onDelete={handleDelete}
                getInitialValues={(item: Ingredient) => ({
                    name: item.name,
                    imageFile: item.imageUrl ?? "",
                    unitId: item.unitId ? String(item.unitId) : "",
                    reorderPoint: item.reorderPoint != null ? String(item.reorderPoint) : "0",
                    status: item.status
                })}
                columns={[
                    { key: "image", label: "Ảnh" },
                    { key: "ingredientCode", label: "Mã nguyên liệu" },
                    { key: "name", label: "Tên nguyên liệu" },
                    { key: "unit", label: "Đơn vị tính" },
                    { key: "reorderPoint", label: "Cảnh báo tồn" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item: Ingredient) => (
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
                            {item.ingredientCode}
                        </TableCell>
                        <TableCell className="font-semibold text-gray-700">
                            {item.name}
                        </TableCell>
                        <TableCell className="text-gray-600">
                            {item.unitName ? `${item.unitName} (${item.unitSymbol || ""})` : "-"}
                        </TableCell>
                        <TableCell className="font-medium text-gray-600">
                            {item.reorderPoint ?? 0}
                        </TableCell>
                        <TableCell>
                            <CatalogStatusBadge status={item.status} />
                        </TableCell>
                    </>
                )}
                renderDetail={(item: Ingredient) => (
                    <div className="grid gap-6">
                        {item.imageUrl ? (
                            <img
                                src={item.imageUrl}
                                alt={item.name}
                                className="h-40 w-full rounded-2xl object-cover"
                            />
                        ) : (
                            <div className="grid h-40 place-items-center rounded-2xl bg-neutral-100 text-gray-500">
                                <Package size={34} />
                            </div>
                        )}
                        <DetailList
                            items={[
                                { label: "Mã nguyên liệu", value: item.ingredientCode },
                                { label: "Tên nguyên liệu", value: item.name },
                                { label: "Đơn vị tính", value: item.unitName ? `${item.unitName} (${item.unitSymbol || ""})` : "-" },
                                { label: "Ngưỡng cảnh báo tồn", value: item.reorderPoint ?? 0 },
                                {
                                    label: "Trạng thái",
                                    value: <CatalogStatusBadge status={item.status} />
                                }
                            ]}
                        />
                    </div>
                )}
                formFields={[
                    {
                        name: "name",
                        label: "Tên nguyên liệu",
                        required: true,
                        placeholder: "Nhập tên nguyên liệu..."
                    },
                    {
                        name: "imageFile",
                        label: "Hình ảnh nguyên liệu",
                        type: "file",
                        purpose: "PRODUCT_IMAGE"
                    },
                    {
                        name: "unitId",
                        label: "Đơn vị tính",
                        type: "select",
                        placeholder: "Chọn đơn vị tính...",
                        options: [
                            { label: "-- Chọn đơn vị tính --", value: "" },
                            ...units.map((u) => ({
                                label: `${u.name} (${u.symbol})`,
                                value: String(u.id)
                            }))
                        ]
                    },
                    {
                        name: "reorderPoint",
                        label: "Ngưỡng cảnh báo tồn tối thiểu",
                        type: "number",
                        placeholder: "0"
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
