import { X } from "lucide-react";
import { useCallback, useState } from "react";

import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../components/InventoryResourceManagement";
import { InventoryStatusBadge } from "../components/InventoryBadges";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { storeShelfApi } from "../services/storeShelf.api";
import type {
    InventoryLocationResponse,
    InventoryStatus,
    StoreShelfRequest
} from "../types/inventory.types";
import { inventoryStatusLabels } from "../utils/inventory-labels";

export function ShelfManagementPage() {
    const [status, setStatus] = useState<"ALL" | InventoryStatus>("ALL");
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [editing, setEditing] = useState<InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);

    const load = useCallback(
        () =>
            storeShelfApi.list({
                page: page - 1,
                size: pageSize,
                status: status === "ALL" ? undefined : status,
                query: query.trim() || undefined
            }),
        [page, pageSize, status, query]
    );

    const resource = useInventoryPage(load);
    const shelves = resource.data?.content ?? [];

    const handleFormSubmit = async (input: StoreShelfRequest) => {
        if (editing) {
            await storeShelfApi.update(editing.id, input);
        } else {
            await storeShelfApi.create(input);
        }
        resource.refresh();
    };

    const handleDelete = async (item: any) => {
        await storeShelfApi.delete(item.id);
    };

    const handleToggleStatus = async (item: any) => {
        const nextStatus = item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
        await storeShelfApi.updateStatus(item.id, nextStatus);
    };

    const columns: Column[] = [
        { key: "code", label: "Mã kệ" },
        { key: "name", label: "Tên kệ" },
        { key: "status", label: "Trạng thái" }
    ];

    return (
        <InventoryResourceManagement
            title="Quản lý kệ"
            description="Quản lý các kệ hàng trong cửa hàng. Mỗi kệ thuộc kho chính của cửa hàng."
            createLabel="Thêm kệ"
            searchPlaceholder="Tìm theo tên kệ, mã kệ..."
            totalItemsLabel="Tổng số kệ"
            items={shelves}
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
            onDelete={handleDelete}
            controlledEditingItem={editing}
            controlledShowForm={showForm}
            onControlledShowFormChange={setShowForm}
            onControlledEditingItemChange={(item) => setEditing(item as InventoryLocationResponse | null)}
            columns={columns}
            renderCells={(item: any) => (
                <>
                    <TableCell strong>{item.code}</TableCell>
                    <TableCell>{item.name}</TableCell>
                    <TableCell>
                        <InventoryStatusBadge status={item.status} />
                    </TableCell>
                </>
            )}
            renderDetail={(item: any) => (
                <dl className="grid gap-4 text-sm">
                    <DetailItem label="Mã kệ" value={item.code} />
                    <DetailItem label="Tên kệ" value={item.name} />
                    <DetailItem
                        label="Trạng thái"
                        value={inventoryStatusLabels[item.status as InventoryStatus]}
                    />
                </dl>
            )}
            customForm={(editingItem, onClose) => (
                <ShelfForm
                    item={editingItem as InventoryLocationResponse | null}
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

interface ShelfFormProps {
    item: InventoryLocationResponse | null;
    onClose(): void;
    onSubmit(input: StoreShelfRequest): Promise<void>;
}

function ShelfForm({ item, onClose, onSubmit }: ShelfFormProps) {
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError("");
        setSubmitting(true);
        try {
            const data = new FormData(event.currentTarget);
            await onSubmit({
                name: String(data.get("name") ?? "").trim(),
                status: data.get("status") as InventoryStatus
            });
        } catch (err: any) {
            setError(err?.message || "Có lỗi xảy ra khi lưu kệ.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/45 p-6 overflow-y-auto">
            <form
                onSubmit={handleSubmit}
                className="w-full max-w-md rounded-3xl bg-white p-6 shadow-pos-panel border border-pos-border animate-in fade-in zoom-in-95 duration-150"
            >
                <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                    <h2 className="text-xl font-bold text-gray-700">
                        {item ? "Chỉnh sửa kệ" : "Thêm kệ mới"}
                    </h2>
                    <button
                        type="button"
                        onClick={onClose}
                        className="grid size-9 place-items-center rounded-lg hover:bg-neutral-100 transition-all focus:outline-none cursor-pointer"
                        aria-label="Đóng"
                    >
                        <X size={20} />
                    </button>
                </header>

                <div className="grid gap-4">
                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Tên kệ *
                        <input
                            name="name"
                            type="text"
                            required
                            defaultValue={item?.name ?? ""}
                            placeholder="Nhập tên kệ..."
                            className="h-10 w-full rounded-xl border border-pos-border px-3 font-medium text-gray-700 focus:outline-none"
                        />
                    </label>

                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Trạng thái
                        <select
                            name="status"
                            defaultValue={item?.status ?? "ACTIVE"}
                            className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                        >
                            {Object.entries(inventoryStatusLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    </label>
                </div>

                {error && (
                    <div className="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-600 border border-red-200">
                        {error}
                    </div>
                )}

                <footer className="mt-6 flex justify-end gap-3 border-t border-pos-border pt-4">
                    <button
                        type="button"
                        onClick={onClose}
                        className="h-11 rounded-xl border border-pos-border bg-white px-6 font-bold text-gray-500 hover:bg-neutral-50 transition-all cursor-pointer focus:outline-none"
                    >
                        Hủy
                    </button>
                    <button
                        type="submit"
                        disabled={submitting}
                        className="h-11 rounded-xl bg-[#07883a] px-6 font-bold text-white hover:bg-[#067331] transition-all disabled:opacity-50 cursor-pointer shadow-sm focus:outline-none"
                    >
                        {submitting ? "Đang lưu..." : item ? "Lưu thay đổi" : "Lưu"}
                    </button>
                </footer>
            </form>
        </div>
    );
}
