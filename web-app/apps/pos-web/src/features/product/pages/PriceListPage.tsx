import { useCallback } from "react";

import {
    CatalogStatusBadge,
    DetailList,
    type FormValues,
    CatalogResourceManagement,
    TableCell
} from "../components/CatalogResourceManagement";
import { usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { priceListApi } from "../services/priceList.api";
import type {
    CatalogStatus,
    PriceList
} from "../types/product.types";
import {
    getNumber,
    getString,
    toApiDateTime,
    toDateInput
} from "../utils/form-values";
import { formatDate, formatCurrency } from "../utils/product-formatters";

export function PriceListPage() {
    const state = useCatalogPageState();
    const loadPriceLists = useCallback(
        () =>
            priceListApi.list({
                page: state.page - 1,
                size: state.pageSize,
                status:
                    state.status === "ALL"
                        ? undefined
                        : state.status
            }),
        [state.page, state.pageSize, state.status]
    );
    const resource = usePagedApiResource(loadPriceLists);
    const priceLists = resource.data?.content ?? [];

    async function submit(
        values: FormValues,
        editingItem: PriceList | null
    ) {
        const baseInput = {
            branchId: getNumber(values, "branchId"),
            name: getString(values, "name"),
            effectiveFrom: toApiDateTime(
                getString(values, "effectiveFrom")
            ),
            effectiveTo: toApiDateTime(
                getString(values, "effectiveTo")
            ),
            status: getString(values, "status") as CatalogStatus
        };

        if (editingItem) {
            const detail = await priceListApi.detail(
                String(editingItem.id)
            );
            await priceListApi.update(String(editingItem.id), {
                ...baseInput,
                items: detail.items.map((item) => ({
                    productVariantId: item.productVariant.id,
                    sellingPrice: item.sellingPrice
                }))
            });
        } else {
            await priceListApi.create({
                ...baseInput,
                items: [
                    {
                        productVariantId: getNumber(
                            values,
                            "productVariantId"
                        ),
                        sellingPrice: getNumber(
                            values,
                            "sellingPrice"
                        )
                    }
                ]
            });
        }
    }

    return (
        <CatalogResourceManagement
            title="Bảng giá"
            description="Quản lý chính sách giá bán theo chi nhánh và thời gian hiệu lực."
            createLabel="Tạo bảng giá"
            totalItemsLabel="Tổng số bảng giá"
            items={priceLists}
            loading={resource.loading}
            error={resource.error}
            query=""
            status={state.status}
            page={(resource.data?.page ?? 0) + 1}
            pageCount={resource.data?.totalPages ?? 1}
            totalItems={resource.data?.totalElements ?? 0}
            pageSize={state.pageSize}
            onQueryChange={() => undefined}
            onStatusChange={state.setStatus}
            onPageChange={state.setPage}
            onPageSizeChange={state.setPageSize}
            onRefresh={resource.refresh}
            onSubmit={submit}
            onChangeStatus={(item) =>
                priceListApi.updateStatus(
                    String(item.id),
                    item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                ).then(() => undefined)
            }
            getInitialValues={(item) => ({
                branchId: String(item.branchId),
                name: item.name,
                effectiveFrom: toDateInput(item.effectiveFrom),
                effectiveTo: toDateInput(item.effectiveTo),
                status: item.status
            })}
            columns={[
                { key: "name", label: "Tên bảng giá" },
                { key: "branch", label: "Chi nhánh" },
                { key: "from", label: "Hiệu lực từ" },
                { key: "to", label: "Hiệu lực đến" },
                { key: "items", label: "Số mức giá" },
                { key: "status", label: "Trạng thái" }
            ]}
            renderCells={(item) => (
                <>
                    <TableCell className="font-bold text-gray-500">
                        {item.name}
                    </TableCell>
                    <TableCell>#{item.branchId}</TableCell>
                    <TableCell>{formatDate(item.effectiveFrom)}</TableCell>
                    <TableCell>{formatDate(item.effectiveTo)}</TableCell>
                    <TableCell>{item.items.length}</TableCell>
                    <TableCell>
                        <CatalogStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item) => (
                <div className="grid gap-6">
                    <DetailList
                        items={[
                            { label: "Tên bảng giá", value: item.name },
                            { label: "Chi nhánh", value: `#${item.branchId}` },
                            {
                                label: "Hiệu lực",
                                value: `${formatDate(item.effectiveFrom)} - ${formatDate(item.effectiveTo)}`
                            },
                            { label: "Số mức giá", value: item.items.length },
                            {
                                label: "Trạng thái",
                                value: (
                                    <CatalogStatusBadge status={item.status} />
                                )
                            }
                        ]}
                    />

                    {item.items && item.items.length > 0 ? (
                        <div className="border-t border-pos-border pt-4">
                            <h3 className="text-sm font-bold text-gray-700 mb-2">
                                Danh sách mức giá ({item.items.length})
                            </h3>
                            <div className="grid gap-2 text-xs">
                                {item.items.map((priceItem) => (
                                    <div key={priceItem.id} className="flex justify-between border-b border-pos-border pb-1.5 last:border-0">
                                        <div>
                                            <span className="font-semibold">{priceItem.productVariant?.variantName || "Biến thể"}</span>
                                            <span className="text-gray-500 block text-[10px]">{priceItem.productVariant?.sku}</span>
                                        </div>
                                        <span className="font-bold text-hvt-green-900">{formatCurrency(priceItem.sellingPrice)}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    ) : null}
                </div>
            )}
            formFields={(editingItem) => [
                {
                    name: "name",
                    label: "Tên bảng giá",
                    required: true
                },
                {
                    name: "branchId",
                    label: "ID chi nhánh",
                    type: "number",
                    required: true
                },
                {
                    name: "effectiveFrom",
                    label: "Hiệu lực từ",
                    type: "date"
                },
                {
                    name: "effectiveTo",
                    label: "Hiệu lực đến",
                    type: "date"
                },
                ...(!editingItem
                    ? [
                          {
                              name: "productVariantId",
                              label: "ID biến thể đầu tiên",
                              type: "number" as const,
                              required: true
                          },
                          {
                              name: "sellingPrice",
                              label: "Giá bán đầu tiên",
                              type: "number" as const,
                              required: true
                          }
                      ]
                    : []),
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
