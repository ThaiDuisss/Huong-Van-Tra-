import { X } from "lucide-react";
import { useCallback, useState } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../components/InventoryResourceManagement";
import {
    InventoryStatusBadge,
    LocationTypeBadge
} from "../components/InventoryBadges";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import type {
    InventoryLocationRequest,
    InventoryLocationResponse,
    InventoryLocationType,
    InventoryStatus
} from "../types/inventory.types";
import {
    inventoryStatusLabels,
    locationTypeLabels
} from "../utils/inventory-labels";

export function InventoryLocationPage() {
    const [branchId] = useState("");
    const [type, setType] = useState<"ALL" | InventoryLocationType>("ALL");
    const [status, setStatus] = useState<"ALL" | InventoryStatus>("ALL");
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    
    const [editing, setEditing] = useState<InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);

    const load = useCallback(
        () =>
            inventoryLocationApi.list({
                page: page - 1,
                size: pageSize,
                branchId: branchId ? branchId : undefined,
                type: type === "ALL" ? undefined : type,
                status: status === "ALL" ? undefined : status,
                query: query.trim() || undefined
            }),
        [branchId, page, pageSize, status, type, query]
    );

    const resource = useInventoryPage(load);
    const locations = resource.data?.content ?? [];

    const handleFormSubmit = async (input: InventoryLocationRequest) => {
        if (editing) {
            await inventoryLocationApi.update(editing.id, input);
        } else {
            await inventoryLocationApi.create(input);
        }
        resource.refresh();
    };

    const handleToggleStatus = async (item: any) => {
        const nextStatus = item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
        await inventoryLocationApi.updateStatus(item.id, nextStatus);
    };

    const columns: Column[] = [
        { key: "code", label: "Mã kệ" },
        { key: "name", label: "Tên vị trí" },
        { key: "type", label: "Loại vị trí" },
        { key: "parent", label: "Vị trí cha" },
        { key: "status", label: "Trạng thái" }
    ];

    return (
        <InventoryResourceManagement
            title="Quản lý kệ hàng"
            description="Quản lý kệ, quầy thu ngân và các vị trí lưu trữ tại cửa hàng."
            createLabel="Tạo vị trí"
            searchPlaceholder="Tìm theo tên vị trí, mã kệ..."
            totalItemsLabel="Tổng số vị trí"
            items={locations}
            loading={resource.loading}
            error={resource.error || ""}
            query={query}
            status={status}
            page={page}
            pageCount={resource.data?.totalPages ?? 1}
            totalItems={resource.data?.totalElements ?? 0}
            pageSize={pageSize}
            onQueryChange={(val) => {
                setQuery(val);
                setPage(1);
            }}
            onStatusChange={(val) => {
                setStatus(val as any);
                setPage(1);
            }}
            onPageChange={setPage}
            onPageSizeChange={(val) => {
                setPageSize(val);
                setPage(1);
            }}
            onRefresh={resource.refresh}
            onSubmit={async () => {}} // customForm handles onSubmit
            onChangeStatus={handleToggleStatus}
            controlledEditingItem={editing}
            controlledShowForm={showForm}
            onControlledShowFormChange={setShowForm}
            onControlledEditingItemChange={(item) => setEditing(item as InventoryLocationResponse | null)}
            columns={columns}
            filters={[
                {
                    label: "Loại vị trí",
                    value: type,
                    options: [],
                    onChange: () => {},
                    render: (
                        <select
                            value={type}
                            onChange={(e) => {
                                setType(e.target.value as any);
                                setPage(1);
                            }}
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer min-w-[150px] truncate"
                        >
                            <option value="ALL">Loại vị trí</option>
                            {Object.entries(locationTypeLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    )
                }
            ]}
            renderCells={(item: any) => (
                <>
                    <TableCell strong>{item.code}</TableCell>
                    <TableCell>{item.name}</TableCell>
                    <TableCell>
                        <LocationTypeBadge type={item.type} />
                    </TableCell>
                    <TableCell>
                        {item.parentLocationId ? `#${item.parentLocationId}` : "-"}
                    </TableCell>
                    <TableCell>
                        <InventoryStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item: any) => (
                <dl className="grid gap-4 text-sm">
                    <DetailItem label="Mã kệ" value={item.code} />
                    <DetailItem label="Tên vị trí" value={item.name} />
                    <DetailItem
                        label="Loại vị trí"
                        value={locationTypeLabels[item.type as InventoryLocationType]}
                    />
                    <DetailItem
                        label="Trạng thái"
                        value={inventoryStatusLabels[item.status as InventoryStatus]}
                    />
                </dl>
            )}
            customForm={(editingItem, onClose) => (
                <LocationForm
                    item={editingItem as InventoryLocationResponse | null}
                    locations={locations}
                    onClose={onClose}
                    onSubmit={async (input) => {
                        await handleFormSubmit(input);
                        onClose();
                    }}
                />
            )}
        />
    );
}

function DetailItem({ label, value }: { label: string; value: string }) {
    return (
        <div className="border-b border-pos-border pb-3">
            <dt className="text-xs font-bold uppercase text-gray-500">{label}</dt>
            <dd className="mt-1 font-semibold text-gray-700">{value}</dd>
        </div>
    );
}

interface LocationFormProps {
    item: InventoryLocationResponse | null;
    locations: InventoryLocationResponse[];
    onClose(): void;
    onSubmit(input: InventoryLocationRequest): Promise<void>;
}

function LocationForm({ item, locations, onClose, onSubmit }: LocationFormProps) {
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError("");
        setSubmitting(true);
        try {
            const data = new FormData(event.currentTarget);
            await onSubmit({
                branchId: String(data.get("branchId") || ""),
                parentLocationId: data.get("parentLocationId")
                    ? String(data.get("parentLocationId"))
                    : null,
                name: String(data.get("name") ?? "").trim(),
                type: data.get("type") as InventoryLocationType,
                status: data.get("status") as InventoryStatus
            });
        } catch (err: any) {
            setError(err?.message || "Có lỗi xảy ra khi lưu vị trí.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <ModalShell className="w-full max-w-xl p-6" closeOnBackdrop={false} labelledBy="location-form-title" onClose={onClose}>
            <form onSubmit={handleSubmit}>
                <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                    <h2 className="text-xl font-bold text-gray-700" id="location-form-title">
                        {item ? "Chỉnh sửa vị trí" : "Tạo vị trí mới"}
                    </h2>
                    <IconButton aria-label="Đóng" onClick={onClose}>
                        <X size={20} />
                    </IconButton>
                </header>

                {item ? (
                    <div className="mb-4 flex items-center gap-2 rounded-xl bg-neutral-50 px-4 py-3 text-sm border border-neutral-200">
                        <span className="font-semibold text-gray-500">Mã kệ:</span>
                        <span className="font-black text-[#07883a] tracking-wide">
                            {item.code}
                        </span>
                        <span className="ml-auto text-xs text-gray-400 italic">
                            Hệ thống tự tạo, không thể chỉnh sửa
                        </span>
                    </div>
                ) : (
                    <div className="mb-4 flex items-center gap-2 rounded-xl bg-green-50 px-4 py-3 text-sm border border-green-100">
                        <span className="text-green-700 font-semibold text-xs">
                            ✦ Mã kệ sẽ được hệ thống tự động tạo khi lưu
                        </span>
                    </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Vị trí cha">
                        <NativeSelect
                            name="parentLocationId"
                            defaultValue={String(item?.parentLocationId ?? "")}
                        >
                            <option value="">Không có</option>
                            {locations
                                .filter((location) => location.id !== item?.id)
                                .map((location) => (
                                    <option key={location.id} value={location.id}>
                                        {location.code} - {location.name}
                                    </option>
                                ))}
                        </NativeSelect>
                    </Field>

                    <Field label="Tên vị trí" required>
                        <TextInput
                            name="name"
                            type="text"
                            required
                            placeholder="Nhập tên vị trí chi tiết..."
                            defaultValue={item?.name ?? ""}
                        />
                    </Field>

                    <Field label="Loại vị trí">
                        <NativeSelect
                            name="type"
                            defaultValue={item?.type ?? "WAREHOUSE"}
                        >
                            {Object.entries(locationTypeLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </NativeSelect>
                    </Field>

                    <Field label="Trạng thái">
                        <NativeSelect
                            name="status"
                            defaultValue={item?.status ?? "ACTIVE"}
                        >
                            {Object.entries(inventoryStatusLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </NativeSelect>
                    </Field>
                </div>

                {error && (
                    <div className="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-600 border border-red-200">
                        {error}
                    </div>
                )}

                <footer className="mt-6 flex justify-end gap-3 border-t border-pos-border pt-4">
                    <Button onClick={onClose}>Hủy</Button>
                    <Button disabled={submitting} type="submit" variant="primary">
                        {submitting ? "Đang lưu..." : item ? "Lưu thay đổi" : "Tạo mới"}
                    </Button>
                </footer>
            </form>
        </ModalShell>
    );
}
