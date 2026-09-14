import { Download, Upload } from "lucide-react";
import { useCallback, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { ExcelImportModal } from "../components/ExcelImportModal";
import { useApiResource, usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { unitApi } from "../services/unit.api";
import type { CatalogStatus, Unit, UnitType } from "../types/product.types";
import { getNumber, getString } from "../utils/form-values";

export function UnitListPage() {
    const state = useCatalogPageState();
    const [isImportModalOpen, setIsImportModalOpen] = useState(false);

    const loadUnits = useCallback(
        () =>
            unitApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                status: state.status === "ALL" ? undefined : state.status,
                type: state.secondaryValue === "ALL" ? undefined : state.secondaryValue as UnitType
            }),
        [state.page, state.pageSize, state.query, state.status, state.secondaryValue]
    );
    const loadAllUnits = useCallback(() => unitApi.list({ page: 0, size: 500 }), []);
    const loadBaseUnits = useCallback(() => unitApi.listBases(), []);
    const resource = usePagedApiResource(loadUnits);
    const allUnitsResource = useApiResource(loadAllUnits);
    const baseUnitsResource = useApiResource(loadBaseUnits);

    const units = resource.data?.content ?? [];
    const allUnits = allUnitsResource.data?.content ?? [];
    const baseUnitOptions = baseUnitsResource.data ?? [];

    const handleRefresh = useCallback(() => {
        resource.refresh();
        allUnitsResource.refresh();
        baseUnitsResource.refresh();
    }, [resource, allUnitsResource, baseUnitsResource]);

    async function submit(values: FormValues, editingItem: Unit | null) {
        const isBase = getString(values, "isBaseUnit") === "true";
        const input = {
            name: getString(values, "name"),
            symbol: getString(values, "symbol"),
            precisionScale: getNumber(values, "precisionScale"),
            type: getString(values, "type") as UnitType,
            baseUnitId: isBase ? null : (getString(values, "baseUnitId") || null),
            conversionRate: (!isBase && getString(values, "baseUnitId")) ? getNumber(values, "conversionRate") : 1,
            status: getString(values, "status") as CatalogStatus
        };

        if (editingItem) {
            await unitApi.update(String(editingItem.id), input);
        } else {
            await unitApi.create(input);
        }
    }

    const handleDelete = async (item: Unit) => {
        await unitApi.delete(String(item.id));
    };

    const handleExport = async () => {
        try {
            const blob = await unitApi.export({
                keyword: state.query || undefined,
                status: state.status === "ALL" ? undefined : state.status,
                type: state.secondaryValue === "ALL" ? undefined : state.secondaryValue
            });
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
            const timestamp = `${year}${month}${day}_${hours}${minutes}${seconds}`;

            link.setAttribute("download", `Danh_sach_don_vi_tinh_${timestamp}.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            alert("Có lỗi xảy ra khi xuất file!");
        }
    };

    const validateUnitRow = (row: any, index?: number, allRows?: any[]) => {
        const name = String(row["Tên đơn vị (*)"] || row["Tên đơn vị"] || row["name"] || "").trim();
        const symbol = String(row["Ký hiệu (*)"] || row["Ký hiệu"] || row["symbol"] || "").trim();

        if (!name) {
            return { isValid: false, error: "Tên đơn vị không được để trống" };
        }
        if (!symbol) {
            return { isValid: false, error: "Ký hiệu không được để trống" };
        }
        if (allRows && typeof index === "number") {
            const firstIdx = allRows.findIndex((r) => {
                const s = String(r["Ký hiệu (*)"] || r["Ký hiệu"] || r["symbol"] || "").trim();
                return s.toLowerCase() === symbol.toLowerCase();
            });
            if (firstIdx !== -1 && firstIdx !== index) {
                return { isValid: false, error: `Ký hiệu '${symbol}' bị trùng lặp trong file (dòng ${firstIdx + 2})` };
            }
        }
        return { isValid: true };
    };

    const handleImportExcel = async (file: File) => {
        await unitApi.import(file);
        handleRefresh();
    };

    const handleDownloadTemplate = async () => {
        try {
            const blob = await unitApi.downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", "mau_nhap_don_vi.xlsx");
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi tải file mẫu:", error);
            alert("Có lỗi xảy ra khi tải file mẫu!");
        }
    };

    return (
        <>
            <CatalogResourceManagement
                title="Đơn vị tính"
                description="Quản lý các đơn vị tính dùng cho sản phẩm và Mặt hàng."
                createLabel="Tạo đơn vị"
                searchPlaceholder="Tìm tên hoặc ký hiệu đơn vị..."
                totalItemsLabel="Tổng số đơn vị"
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
                items={units}
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
                onRefresh={handleRefresh}
                onSubmit={submit}
                onDelete={handleDelete}
                onChangeStatus={(item) =>
                    unitApi
                        .updateStatus(
                            String(item.id),
                            item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                        )
                        .then(() => undefined)
                }
                getInitialValues={(item) => ({
                    name: item.name,
                    symbol: item.symbol,
                    precisionScale: String(item.precisionScale ?? 0),
                    type: item.type ?? "COUNT",
                    isBaseUnit: item.id ? (item.baseUnitId ? "false" : "true") : "true",
                    baseUnitId: String(item.baseUnitId ?? ""),
                    conversionRate: String(item.conversionRate ?? 1),
                    status: item.status
                })}
                secondaryFilter={{
                    label: "Phân loại",
                    value: state.secondaryValue,
                    options: [
                        { label: "Khối lượng", value: "WEIGHT" },
                        { label: "Thể tích", value: "VOLUME" },
                        { label: "Đếm", value: "COUNT" }
                    ],
                    onChange: state.setSecondaryValue
                }}
                columns={[
                    { key: "name", label: "Tên đơn vị" },
                    { key: "symbol", label: "Ký hiệu" },
                    { key: "type", label: "Phân loại" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item) => {
                    return (
                        <>
                            <TableCell className="font-bold text-gray-700">
                                {item.name}
                            </TableCell>
                            <TableCell>
                                <span className="inline-flex items-center px-2.5 py-0.5 rounded-lg bg-gray-100 text-gray-700 font-mono text-sm font-semibold">
                                    {item.symbol}
                                </span>
                            </TableCell>
                            <TableCell>
                                <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                                    item.type === "WEIGHT"
                                        ? "bg-blue-100 text-blue-700"
                                        : item.type === "VOLUME"
                                            ? "bg-purple-100 text-purple-700"
                                            : "bg-emerald-100 text-emerald-700"
                                }`}>
                                    {item.type === "WEIGHT" ? "Khối lượng" : item.type === "VOLUME" ? "Thể tích" : "Đếm"}
                                </span>
                            </TableCell>
                            <TableCell>
                                <CatalogStatusBadge status={item.status} />
                            </TableCell>
                        </>
                    );
                }}
                renderDetail={(item) => {
                    const baseUnit = allUnits.find((u) => u.id === item.baseUnitId);
                    return (
                        <DetailList
                            items={[
                                { label: "Tên đơn vị", value: item.name },
                                { label: "Ký hiệu", value: item.symbol },
                                {
                                    label: "Phân loại",
                                    value: item.type === "WEIGHT" ? "Khối lượng" : item.type === "VOLUME" ? "Thể tích" : "Đếm"
                                },
                                {
                                    label: "Đơn vị gốc",
                                    value: item.baseUnitId ? (
                                        <span className="font-mono text-xs">
                                            {baseUnit ? `${baseUnit.name} (${item.baseUnitId})` : String(item.baseUnitId)}
                                        </span>
                                    ) : (
                                        "Là đơn vị gốc"
                                    )
                                },
                                {
                                    label: "Tỷ lệ quy đổi",
                                    value: item.baseUnitId
                                        ? `1 ${item.symbol} = ${item.conversionRate} (đơn vị gốc)`
                                        : "—"
                                },
                                {
                                    label: "Độ chính xác thập phân",
                                    value: `${item.precisionScale} chữ số`
                                }
                            ]}
                        />
                    );
                }}
                formFields={[
                    {
                        name: "name",
                        label: "Tên đơn vị",
                        required: true,
                        placeholder: "Ví dụ: Kilogram, Hộp 100g, Thùng..."
                    },
                    {
                        name: "symbol",
                        label: "Ký hiệu",
                        required: true,
                        placeholder: "Ví dụ: kg, hộp100g, thùng..."
                    },
                    {
                        name: "precisionScale",
                        label: "Độ chính xác thập phân",
                        type: "number",
                        required: true,
                        placeholder: "0 = số nguyên, 1 = 0.1, 2 = 0.01..."
                    },
                    {
                        name: "type",
                        label: "Phân loại đơn vị",
                        type: "select",
                        required: true,
                        options: [
                            { label: "Khối lượng", value: "WEIGHT" },
                            { label: "Thể tích", value: "VOLUME" },
                            { label: "Đếm", value: "COUNT" }
                        ]
                    },
                    {
                        name: "isBaseUnit",
                        label: "Đây là đơn vị gốc",
                        type: "checkbox",
                        required: false
                    },
                    {
                        name: "baseUnitId",
                        label: "Đơn vị gốc",
                        type: "select",
                        required: (values) => values.isBaseUnit !== "true",
                        hidden: (values) => values.isBaseUnit === "true",
                        options: [
                            { label: "-- Chọn đơn vị gốc --", value: "" },
                            ...baseUnitOptions.map((u) => ({
                                label: `${u.name} (${u.symbol})`,
                                value: String(u.id)
                            }))
                        ]
                    },
                    {
                        name: "conversionRate",
                        label: "Tỷ lệ quy đổi (so với đơn vị gốc)",
                        type: "number",
                        required: (values) => values.isBaseUnit !== "true" && !!values.baseUnitId,
                        hidden: (values) => values.isBaseUnit === "true" || !values.baseUnitId,
                        placeholder: "Ví dụ: 100 (1 Hộp 100g = 100 gram)"
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
                title="Nhập danh sách Đơn vị tính từ Excel"
                description="Tải lên danh sách đơn vị tính theo mẫu định dạng của hệ thống."
                onDownloadTemplate={handleDownloadTemplate}
                onImport={handleImportExcel}
                validateRow={validateUnitRow}
            />
        </>
    );
}

