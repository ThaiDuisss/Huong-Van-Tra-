import { Download, Upload } from "lucide-react";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";

import { StatusBadge } from "../../../components/ui/StatusBadge";
import { toast } from "../../../components/ui/Toast";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown, withPinned } from "../../manufacturing/components/shared/useLookupDropdown";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { unitApi } from "../services/unit.api";
import { routePaths } from "../../../routes/path";
import type { CatalogStatus, Unit, UnitType } from "../types/product.types";
import { getNumber, getString } from "../utils/form-values";

export function UnitListPage() {
    const state = useCatalogPageState();
    const navigate = useNavigate();

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
    const resource = usePagedApiResource(loadUnits);

    const units = resource.data?.content ?? [];
    // Dropdown "Đơn vị gốc": lookup phân trang (chỉ đơn vị gốc), chỉ gọi API khi mở dropdown
    const [pickedBaseUnit, setPickedBaseUnit] = useState<LookupItem | null>(null);
    const baseUnitDropdown = useLookupDropdown(unitApi.lookupBases, { lazy: true, pinned: [pickedBaseUnit] });

    const handleRefresh = useCallback(() => {
        resource.refresh();
    }, [resource]);

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
            toast.error("Có lỗi xảy ra khi xuất file!");
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
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.unitImport)}>
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
                                <StatusBadge className="font-mono" shape="tag">
                                    {item.symbol}
                                </StatusBadge>
                            </TableCell>
                            <TableCell>
                                <StatusBadge
                                    shape="tag"
                                    tone={item.type === "WEIGHT" ? "info" : item.type === "VOLUME" ? "violet" : "success"}
                                >
                                    {item.type === "WEIGHT" ? "Khối lượng" : item.type === "VOLUME" ? "Thể tích" : "Đếm"}
                                </StatusBadge>
                            </TableCell>
                            <TableCell>
                                <CatalogStatusBadge status={item.status} />
                            </TableCell>
                        </>
                    );
                }}
                renderDetail={(item) => {
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
                                            {item.baseUnitName ? `${item.baseUnitName} (${item.baseUnitId})` : String(item.baseUnitId)}
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
                formFields={(editingItem) => [
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
                        required: (values) => values.isBaseUnit !== "true",
                        hidden: (values) => values.isBaseUnit === "true",
                        render: (value, onChange) => (
                            <SearchableDropdown
                                allOptionText="-- Chọn đơn vị gốc --"
                                emptyText="Không tìm thấy đơn vị gốc"
                                hasMore={baseUnitDropdown.hasMore}
                                loading={baseUnitDropdown.loading}
                                onChange={(val) => {
                                    const next = val === "ALL" ? "" : val;
                                    setPickedBaseUnit(baseUnitDropdown.findOption(next));
                                    onChange(next);
                                }}
                                onLoadMore={baseUnitDropdown.onLoadMore}
                                onOpen={baseUnitDropdown.onOpen}
                                onSearch={baseUnitDropdown.onSearch}
                                options={withPinned(
                                    baseUnitDropdown.options,
                                    editingItem?.baseUnitId
                                        ? { id: String(editingItem.baseUnitId), name: editingItem.baseUnitName ?? "Đơn vị gốc" }
                                        : null
                                )}
                                placeholder="Tìm đơn vị gốc..."
                                value={value}
                            />
                        )
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

        </>
    );
}

