import { Download, Upload } from "lucide-react";
import { useCallback, useState } from "react";
import { useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";

import { ExcelImportModal } from "../components/ExcelImportModal";
import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { useApiResource, usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { productApi } from "../services/product.api";
import { productVariantApi } from "../services/productVariant.api";
import { unitApi } from "../services/unit.api";
import type {
    CatalogStatus,
    InventoryPolicy,
    ProductVariant
} from "../types/product.types";
import {
    getNumber,
    getString
} from "../utils/form-values";
import { formatCurrency } from "../utils/product-formatters";

export function ProductVariantListPage() {
    const { productId } = useParams();
    const state = useCatalogPageState();
    const [isImportModalOpen, setIsImportModalOpen] = useState(false);
    const loadVariants = useCallback(
        () =>
            productVariantApi.list({
                page: state.page - 1,
                size: state.pageSize,
                keyword: state.query || undefined,
                productId:
                    productId && productId !== "ALL"
                        ? productId
                        : undefined,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [
            productId,
            state.page,
            state.pageSize,
            state.query,
            state.status
        ]
    );
    const loadProducts = useCallback(
        () => productApi.list({ page: 0, size: 200 }),
        []
    );
    const resource = usePagedApiResource(loadVariants);
    const productResource = useApiResource(loadProducts);
    const loadUnits = useCallback(() => unitApi.list({ page: 0, size: 500 }), []);
    const unitResource = useApiResource(loadUnits);
    const variants = resource.data?.content ?? [];
    const products = productResource.data?.content ?? [];
    const units = unitResource.data?.content ?? [];

    async function submit(
        values: FormValues,
        editingItem: ProductVariant | null
    ) {
        const input = {
            productId: getString(values, "productId"),
            variantName: getString(values, "variantName"),
            sellingPrice: getNumber(values, "sellingPrice"),
            costPrice: getNumber(values, "costPrice"),
            unitId: getString(values, "unitId"),
            inventoryPolicy: getString(
                values,
                "inventoryPolicy"
            ) as InventoryPolicy,
            status: getString(values, "status") as CatalogStatus
        };

        if (editingItem) {
            await productVariantApi.update(String(editingItem.id), input);
        } else {
            await productVariantApi.create(input);
        }
    }

    const handleExport = async () => {
        try {
            const blob = await productVariantApi.export(productId);
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

            link.setAttribute("download", `Danh sách Mặt hàng_${timestamp}.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            alert("Có lỗi xảy ra khi xuất file!");
        }
    };

    const handleImportClick = () => {
        let currentProductId = productId;
        if (!currentProductId || currentProductId === "ALL") {
            currentProductId = String(products[0]?.id);
        }

        if (!currentProductId) {
            alert("Không tìm thấy sản phẩm hợp lệ để import Mặt hàng!");
            return;
        }
        setIsImportModalOpen(true);
    };

    const validateVariantRow = (row: any) => {
        const productCode = String(row["Mã sản phẩm"] || "").trim();
        const variantName = String(row["Tên Mặt hàng"] || "").trim();
        const sellingPriceStr = String(row["Giá bán"] || "").trim();
        const costPriceStr = String(row["Giá vốn"] || "").trim();
        const unitName = String(row["Đơn vị bán"] || row["Đơn vị"] || "").trim();
        const policyStr = String(row["Chính sách tồn kho"] || "").trim();

        const statusStr = String(row["Trạng thái"] || "").trim();

        if (!productCode) {
            return { isValid: false, error: "Mã sản phẩm trống" };
        }

        const matchedProduct = products.some(
            (p) => p.productCode.toLowerCase() === productCode.toLowerCase()
        );
        if (!matchedProduct) {
            return { isValid: false, error: "Mã sản phẩm không tồn tại" };
        }

        if (!variantName) {
            return { isValid: false, error: "Tên Mặt hàng trống" };
        }

        const sellingPrice = Number(String(sellingPriceStr).replace(/,/g, ""));
        if (isNaN(sellingPrice) || sellingPrice < 0) {
            return { isValid: false, error: "Giá bán không hợp lệ" };
        }

        const costPrice = Number(String(costPriceStr).replace(/,/g, ""));
        if (isNaN(costPrice) || costPrice < 0) {
            return { isValid: false, error: "Giá vốn không hợp lệ" };
        }

        if (!unitName) {
            return { isValid: false, error: "Đơn vị trống" };
        }

        const matchedUnit = units.some(
            (u) => u.name.toLowerCase() === unitName.toLowerCase() || u.symbol.toLowerCase() === unitName.toLowerCase()
        );
        if (!matchedUnit) {
            return { isValid: false, error: "Đơn vị không tồn tại" };
        }

        if (policyStr) {
            const p = policyStr.toLowerCase();
            if (p !== "theo dõi tồn kho" && p !== "không theo dõi" && p !== "track_stock" && p !== "do_not_track") {
                return { isValid: false, error: "Chính sách tồn kho không hợp lệ" };
            }
        }

        if (statusStr) {
            const s = statusStr.toLowerCase();
            if (s !== "đang hoạt động" && s !== "ngừng hoạt động" && s !== "active" && s !== "inactive") {
                return { isValid: false, error: "Trạng thái không hợp lệ" };
            }
        }

        return { isValid: true };
    };

    const handleImportExcel = async (file: File) => {
        let currentProductId = productId;
        if (!currentProductId || currentProductId === "ALL") {
            currentProductId = String(products[0]?.id);
        }
        if (!currentProductId) {
            throw new Error("Không tìm thấy sản phẩm hợp lệ để import Mặt hàng!");
        }
        await productVariantApi.import(file, currentProductId);
        resource.refresh();
    };

    const handleDownloadTemplate = async () => {
        try {
            const blob = await productVariantApi.downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", "mau_nhap_bien_the.xlsx");
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
                title="Mặt hàng"
                description="Quản lý các mặt hàng bán được, SKU và chính sách tồn kho."
                createLabel="Tạo Mặt hàng"
                searchPlaceholder="Tìm theo tên sản phẩm, tên mặt hàng, SKU..."
                totalItemsLabel="Tổng số Mặt hàng"
                extraHeaderActions={
                    <>
                        <ActionButton variant="secondary" onClick={handleImportClick}>
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
                onSubmit={submit}
                onChangeStatus={(item) =>
                    productVariantApi.updateStatus(
                        String(item.id),
                        item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                    ).then(() => undefined)
                }
                getInitialValues={(item) => ({
                    productId: String(item.productId),
                    variantName: item.variantName,
                    sellingPrice: String(item.sellingPrice),
                    costPrice: String(item.costPrice),
                    unitId: String(item.unitId),
                    inventoryPolicy: item.inventoryPolicy,
                    status: item.status
                })}
                secondaryFilter={undefined}
                columns={[
                    // { key: "sku", label: "SKU" },
                    // { key: "product", label: "Sản phẩm" },
                    { key: "variant", label: "Tên Mặt hàng" },
                    { key: "selling", label: "Giá bán" },
                    // { key: "cost", label: "Giá vốn" },
                    // { key: "inventory", label: "Tồn kho" },
                    { key: "status", label: "Trạng thái" }
                ]}
                renderCells={(item) => (
                    <>
                        {/* <TableCell className="font-bold text-gray-500">
                            {item.sku}
                        </TableCell> */}
                        {/* <TableCell>{item.productName}</TableCell> */}
                        <TableCell>{item.variantName}</TableCell>
                        <TableCell>{formatCurrency(item.sellingPrice)}</TableCell>
                        {/* <TableCell>{formatCurrency(item.costPrice)}</TableCell> */}
                        {/* <TableCell>
                            {item.inventoryPolicy === "TRACK_STOCK"
                                ? "Theo dõi tồn"
                                : "Không theo dõi"}
                        </TableCell> */}
                        <TableCell>
                            <CatalogStatusBadge status={item.status} />
                        </TableCell>
                    </>
                )}
                renderDetail={(item) => {
                    const matchedUnit = units.find((u) => u.id === item.unitId);
                    const unitText = matchedUnit ? `${matchedUnit.name} (${matchedUnit.symbol})` : `Mã đơn vị: ${item.unitId}`;
                    return (
                        <DetailList
                            items={[
                                { label: "Sản phẩm", value: item.productName },
                                { label: "SKU", value: item.sku },
                                { label: "Tên Mặt hàng", value: item.variantName },
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
                    );
                }}
                formFields={[
                    {
                        name: "productId",
                        label: "Sản phẩm",
                        type: "select",
                        required: true,
                        options: products.map((product) => ({
                            label: product.name,
                            value: String(product.id)
                        }))
                    },
                    {
                        name: "variantName",
                        label: "Tên Mặt hàng",
                        required: true
                    },
                    {
                        name: "sellingPrice",
                        label: "Giá bán",
                        type: "number",
                        required: true
                    },
                    {
                        name: "costPrice",
                        label: "Giá vốn",
                        type: "number",
                        required: true
                    },
                    {
                        name: "unitId",
                        label: "Đơn vị bán thương mại",
                        type: "select",
                        required: true,
                        options: units
                            .filter((unit) => unit.status === "ACTIVE")
                            .map((unit) => ({
                                label: `${unit.name} (${unit.symbol})`,
                                value: String(unit.id)
                            }))
                    },
                    {
                        name: "inventoryPolicy",
                        label: "Chính sách tồn kho",
                        type: "select",
                        required: true,
                        options: [
                            { label: "Theo dõi tồn kho", value: "TRACK_STOCK" },
                            {
                                label: "Không theo dõi",
                                value: "DO_NOT_TRACK"
                            }
                        ]
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
                title="Nhập dữ liệu từ Excel"
                description="Tải file mẫu hoặc chọn file Excel để nhập dữ liệu vào hệ thống."
                onDownloadTemplate={handleDownloadTemplate}
                onImport={handleImportExcel}
                validateRow={validateVariantRow}
            />
        </>
    );
}
