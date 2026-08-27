import { Download, Upload } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";

import { ExcelImportModal } from "../../product/components/ExcelImportModal";
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
    const state = useCatalogPageState();
    const [isImportModalOpen, setIsImportModalOpen] = useState(false);
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
        const input = {
            name: getString(values, "name"),
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

    const validateIngredientRow = (row: any) => {
        const code = String(row["Mã nguyên liệu"] || row["ingredientCode"] || row["code"] || "").trim();
        const name = String(row["Tên nguyên liệu"] || row["name"] || "").trim();
        
        if (!code) {
            return { isValid: false, error: "Mã nguyên liệu trống" };
        }
        if (!name) {
            return { isValid: false, error: "Tên nguyên liệu trống" };
        }
        return { isValid: true };
    };

    const handleImportExcel = async (file: File) => {
        await ingredientApi.import(file);
        resource.refresh();
    };

    const handleDownloadTemplate = async () => {
        try {
            const blob = await ingredientApi.downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", "mau_nhap_nguyen_lieu.xlsx");
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi tải file mẫu:", error);
            alert("Có lỗi xảy ra khi tải file mẫu!");
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
                        <ActionButton variant="secondary" onClick={() => setIsImportModalOpen(true)}>
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
                    unitId: item.unitId ? String(item.unitId) : "",
                    reorderPoint: item.reorderPoint != null ? String(item.reorderPoint) : "0",
                    status: item.status
                })}
                columns={[
                    { key: "ingredientCode", label: "Mã nguyên liệu" },
                    { key: "name", label: "Tên nguyên liệu" },
                    { key: "unit", label: "Đơn vị tính" },
                    { key: "reorderPoint", label: "Cảnh báo tồn" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item: Ingredient) => (
                    <>
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
                )}
                formFields={[
                    {
                        name: "name",
                        label: "Tên nguyên liệu",
                        required: true,
                        placeholder: "Nhập tên nguyên liệu..."
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
            <ExcelImportModal
                isOpen={isImportModalOpen}
                onClose={() => setIsImportModalOpen(false)}
                title="Nhập nguyên liệu từ Excel"
                description="Tải file mẫu hoặc chọn file Excel để nhập nguyên liệu vào hệ thống."
                onDownloadTemplate={handleDownloadTemplate}
                onImport={handleImportExcel}
                validateRow={validateIngredientRow}
            />
        </>
    );
}
