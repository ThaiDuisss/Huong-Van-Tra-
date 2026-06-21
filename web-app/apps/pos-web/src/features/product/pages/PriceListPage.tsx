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
import { routePaths } from "../../../routes/path";
import { formatDate } from "../utils/product-formatters";

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
            detailPath={(item) =>
                routePaths.priceListDetail.replace(
                    ":id",
                    String(item.id)
                )
            }
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
                    <TableCell className="font-bold text-pos-ink">
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
