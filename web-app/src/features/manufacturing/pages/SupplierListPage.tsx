import { Building2 } from "lucide-react";
import { useCallback } from "react";

import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    CatalogResourceManagement,
    CatalogStatusBadge,
    DetailList,
    TableCell
} from "../../product/components/CatalogResourceManagement";
import { usePagedApiResource } from "../../product/hooks/useApiResource";
import { useCatalogPageState } from "../../product/hooks/useCatalogPageState";
import { SupplierFormModal } from "../components/SupplierFormModal";
import { supplierApi } from "../services/supplier.api";
import type { SupplierResponse } from "../types/supplier.types";

export function SupplierListPage() {
    const state = useCatalogPageState();

    const loadSuppliers = useCallback(
        () =>
            supplierApi.list({
                page: state.page - 1,
                size: state.pageSize,
                search: state.query.trim() || undefined,
                status: state.status === "ALL" ? undefined : state.status
            }),
        [state.page, state.pageSize, state.query, state.status]
    );

    const resource = usePagedApiResource(loadSuppliers);
    const suppliers = resource.data?.content ?? [];

    const handleDelete = async (item: SupplierResponse) => {
        await supplierApi.delete(item.id);
    };

    return (
        <CatalogResourceManagement<SupplierResponse>
            title="Nhà cung cấp"
            description="Quản lý danh sách nhà cung ứng và các nguyên liệu liên kết."
            createLabel="Tạo nhà cung cấp"
            searchPlaceholder="Tìm kiếm theo tên, mã NCC, người liên hệ, SĐT..."
            totalItemsLabel="Tổng số nhà cung cấp"
            items={suppliers}
            loading={resource.loading}
            error={resource.error}
            query={state.query}
            filters={[
                {
                    label: "Trạng thái",
                    value: state.status,
                    options: [
                        { label: "Tất cả trạng thái", value: "ALL" },
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
            onChangeStatus={(item: SupplierResponse) =>
                supplierApi
                    .updateStatus(item.id, item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE")
                    .then(() => undefined)
            }
            onDelete={handleDelete}
            onSubmit={async () => {}}
            customForm={(editingItem, onClose) => (
                <SupplierFormModal
                    isOpen={true}
                    onClose={onClose}
                    supplier={editingItem}
                    onSubmit={async (data) => {
                        if (editingItem) {
                            await supplierApi.update(editingItem.id, data);
                        } else {
                            await supplierApi.create(data);
                        }
                        resource.refresh();
                    }}
                />
            )}
            columns={[
                { key: "supplierCode", label: "Mã NCC" },
                { key: "name", label: "Tên nhà cung cấp" },
                { key: "contact", label: "Liên hệ" },
                { key: "address", label: "Địa chỉ" },
                { key: "ingredients", label: "Nguyên liệu cung cấp" },
                { key: "status", label: "Trạng thái" }
            ]}
            renderCells={(item: SupplierResponse) => (
                <>
                    <TableCell className="font-mono text-xs font-bold text-emerald-700 whitespace-nowrap">
                        {item.supplierCode}
                    </TableCell>
                    <TableCell className="font-bold text-gray-800">{item.name}</TableCell>
                    <TableCell className="text-xs">
                        {item.contactName && <p className="font-bold text-neutral-700">{item.contactName}</p>}
                        {item.phone ? (
                            <p className="font-medium text-neutral-500">{item.phone}</p>
                        ) : (
                            !item.contactName && <span className="text-neutral-300">-</span>
                        )}
                    </TableCell>
                    <TableCell className="text-xs font-medium text-neutral-600 max-w-[200px] truncate">
                        {item.address || <span className="text-neutral-300">-</span>}
                    </TableCell>
                    <TableCell>
                        {!item.ingredients || item.ingredients.length === 0 ? (
                            <span className="text-xs text-neutral-300 font-medium">Chưa có</span>
                        ) : (
                            <div className="flex flex-wrap items-center gap-1.5 max-w-xs">
                                {item.ingredients.filter(Boolean).slice(0, 2).map((ing) => (
                                    <StatusBadge key={ing.id} shape="tag">
                                        {ing.name}
                                    </StatusBadge>
                                ))}
                                {item.ingredients.filter(Boolean).length > 2 && (
                                    <StatusBadge shape="tag" tone="success">
                                        +{item.ingredients.filter(Boolean).length - 2}
                                    </StatusBadge>
                                )}
                            </div>
                        )}
                    </TableCell>
                    <TableCell>
                        <CatalogStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item: SupplierResponse) => (
                <div className="grid gap-6">
                    <div className="flex items-center gap-3">
                        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600">
                            <Building2 size={24} />
                        </div>
                        <div>
                            <h3 className="text-base font-black text-neutral-800">{item.name}</h3>
                            <p className="text-xs font-bold font-mono text-emerald-600">{item.supplierCode}</p>
                        </div>
                    </div>
                    <DetailList
                        items={[
                            { label: "Mã nhà cung cấp", value: item.supplierCode },
                            { label: "Tên nhà cung cấp", value: item.name },
                            { label: "Người liên hệ", value: item.contactName || "—" },
                            { label: "Số điện thoại", value: item.phone || "—" },
                            { label: "Email", value: item.email || "—" },
                            { label: "Mã số thuế", value: item.taxCode || "—" },
                            { label: "Địa chỉ", value: item.address || "—" },
                            { label: "Ghi chú", value: item.notes || "—" },
                            {
                                label: "Trạng thái",
                                value: <CatalogStatusBadge status={item.status} />
                            },
                            {
                                label: "Ngày tạo",
                                value: item.createdAt ? new Date(item.createdAt).toLocaleString("vi-VN") : "—"
                            }
                        ]}
                    />
                    <div>
                        <h4 className="text-xs font-black text-neutral-500 uppercase tracking-wider mb-2">
                            Nguyên liệu cung cấp ({item.ingredients?.length || 0})
                        </h4>
                        {!item.ingredients || item.ingredients.length === 0 ? (
                            <p className="text-xs text-neutral-400 italic">Chưa liên kết nguyên liệu nào</p>
                        ) : (
                            <div className="space-y-1.5">
                                {item.ingredients.filter(Boolean).map((ing) => (
                                    <div
                                        key={ing.id}
                                        className="flex items-center justify-between rounded-xl border border-neutral-100 bg-neutral-50/70 p-2.5 text-xs"
                                    >
                                        <div>
                                            <span className="font-bold text-neutral-800">{ing.name}</span>
                                            <span className="ml-2 font-mono text-[11px] text-neutral-400">
                                                ({ing.ingredientCode})
                                            </span>
                                        </div>
                                        <CatalogStatusBadge status={ing.status} />
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            )}
        />
    );
}
