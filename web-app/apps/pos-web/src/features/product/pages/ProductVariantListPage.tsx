import { Download, Upload } from "lucide-react";
import { useCallback } from "react";
import { useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";

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
    const selectedProductId = productId ?? state.secondaryValue;
    const loadVariants = useCallback(
        () =>
            productVariantApi.list({
                page: state.page - 1,
                size: state.pageSize,
                sku: state.query || undefined,
                productId:
                    selectedProductId === "ALL"
                        ? undefined
                        : selectedProductId,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [
            selectedProductId,
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
    const loadUnits = useCallback(() => unitApi.list(), []);
    const unitResource = useApiResource(loadUnits);
    const variants = resource.data?.content ?? [];
    const products = productResource.data?.content ?? [];
    const units = unitResource.data ?? [];

    async function submit(
        values: FormValues,
        editingItem: ProductVariant | null
    ) {
        const input = {
            productId: getNumber(values, "productId"),
            sku: getString(values, "sku"),
            variantName: getString(values, "variantName"),
            sellingPrice: getNumber(values, "sellingPrice"),
            costPrice: getNumber(values, "costPrice"),
            baseUnitId: getNumber(values, "baseUnitId"),
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
            const blob = await productVariantApi.export(selectedProductId);
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
            
            link.setAttribute("download", `Danh sách biến thể sản phẩm_${timestamp}.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi xuất file:", error);
            alert("Có lỗi xảy ra khi xuất file!");
        }
    };

    const handleImportClick = () => {
        let currentProductId = selectedProductId;
        if (!currentProductId || currentProductId === "ALL") {
            currentProductId = String(products[0]?.id);
        }
        
        if (!currentProductId) {
            alert("Không tìm thấy sản phẩm hợp lệ để import biến thể!");
            return;
        }

        const fileInput = document.createElement("input");
        fileInput.type = "file";
        fileInput.accept = ".csv";
        fileInput.onchange = async (e) => {
            const file = (e.target as HTMLInputElement).files?.[0];
            if (!file) return;

            try {
                await productVariantApi.import(file, currentProductId);
                alert("Import biến thể thành công!");
                resource.refresh();
            } catch (error) {
                console.error(error);
                alert("Có lỗi xảy ra khi import file. Vui lòng kiểm tra lại định dạng file CSV!");
            }
        };
        fileInput.click();
    };

    const handleDownloadTemplate = async () => {
        try {
            const blob = await productVariantApi.downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", "mau_nhap_bien_the.csv");
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error("Lỗi tải file mẫu:", error);
            alert("Có lỗi xảy ra khi tải file mẫu!");
        }
    };

    return (
        <CatalogResourceManagement
            title="Biến thể sản phẩm"
            description="Quản lý các mặt hàng bán được, SKU và chính sách tồn kho."
            createLabel="Tạo biến thể"
            searchPlaceholder="Tìm theo SKU..."
            totalItemsLabel="Tổng số biến thể"
            extraHeaderActions={
                <>
                    <ActionButton variant="secondary" onClick={handleDownloadTemplate}>
                        <Download size={17} aria-hidden="true" />
                        Tải file mẫu
                    </ActionButton>
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
                sku: item.sku,
                variantName: item.variantName,
                sellingPrice: String(item.sellingPrice),
                costPrice: String(item.costPrice),
                baseUnitId: String(item.baseUnitId),
                inventoryPolicy: item.inventoryPolicy,
                status: item.status
            })}
            secondaryFilter={
                productId
                    ? undefined
                    : {
                          label: "Sản phẩm",
                          value: state.secondaryValue,
                          options: products.map((product) => ({
                              label: product.name,
                              value: String(product.id)
                          })),
                          onChange: state.setSecondaryValue
                      }
            }
            columns={[
                { key: "sku", label: "SKU" },
                { key: "product", label: "Sản phẩm" },
                { key: "variant", label: "Tên biến thể" },
                { key: "selling", label: "Giá bán" },
                // { key: "cost", label: "Giá vốn" },
                { key: "inventory", label: "Tồn kho" },
                { key: "status", label: "Trạng thái" }
            ]}
            renderCells={(item) => (
                <>
                    <TableCell className="font-bold text-pos-ink">
                        {item.sku}
                    </TableCell>
                    <TableCell>{item.productName}</TableCell>
                    <TableCell>{item.variantName}</TableCell>
                    <TableCell>{formatCurrency(item.sellingPrice)}</TableCell>
                    {/* <TableCell>{formatCurrency(item.costPrice)}</TableCell> */}
                    <TableCell>
                        {item.inventoryPolicy === "TRACK_STOCK"
                            ? "Theo dõi tồn"
                            : "Không theo dõi"}
                    </TableCell>
                    <TableCell>
                        <CatalogStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item) => (
                <DetailList
                    items={[
                        { label: "Sản phẩm", value: item.productName },
                        { label: "SKU", value: item.sku },
                        { label: "Tên biến thể", value: item.variantName },
                        {
                            label: "Giá bán",
                            value: formatCurrency(item.sellingPrice)
                        },
                        {
                            label: "Giá vốn",
                            value: formatCurrency(item.costPrice)
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
            )}
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
                { name: "sku", label: "SKU", required: true },
                {
                    name: "variantName",
                    label: "Tên biến thể",
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
                    name: "baseUnitId",
                    label: "Đơn vị cơ sở",
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
    );
}
