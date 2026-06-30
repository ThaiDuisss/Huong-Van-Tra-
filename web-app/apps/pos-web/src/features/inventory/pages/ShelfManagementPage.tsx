import { Eye, Pencil, Plus, ToggleLeft, X } from "lucide-react";
import { useCallback, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";

import { InventoryStatusBadge } from "../components/InventoryBadges";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { messageOf, useInventoryPage } from "../hooks/useInventoryResource";
import { storeShelfApi } from "../services/storeShelf.api";
import type {
    InventoryLocationResponse,
    InventoryStatus,
    StoreShelfRequest
} from "../types/inventory.types";
import { inventoryStatusLabels } from "../utils/inventory-labels";

// ─────────────────────────────────────────────────────────────────────────────
// Page component
// ─────────────────────────────────────────────────────────────────────────────

export function ShelfManagementPage() {
    const [status, setStatus] = useState<"ALL" | InventoryStatus>("ALL");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selected, setSelected] = useState<InventoryLocationResponse | null>(null);
    const [editing, setEditing] = useState<InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [actionError, setActionError] = useState("");
    const [toast, setToast] = useState("");

    const load = useCallback(
        () =>
            storeShelfApi.list({
                page: page - 1,
                size: pageSize,
                status: status === "ALL" ? undefined : status
            }),
        [page, pageSize, status]
    );

    const resource = useInventoryPage(load);
    const shelves = resource.data?.content ?? [];

    // ── Actions ────────────────────────────────────────────────────────────

    async function save(input: StoreShelfRequest) {
        setActionError("");
        try {
            if (editing) {
                await storeShelfApi.update(editing.id, input);
            } else {
                await storeShelfApi.create(input);
            }
            setShowForm(false);
            setToast(editing ? "Cập nhật thành công" : "Tạo kệ thành công");
            window.setTimeout(() => setToast(""), 2500);
            resource.refresh();
        } catch (error) {
            setActionError(messageOf(error));
        }
    }

    async function changeStatus(item: InventoryLocationResponse) {
        if (!window.confirm("Bạn có chắc chắn muốn thay đổi trạng thái kệ?")) return;
        try {
            await storeShelfApi.updateStatus(
                item.id,
                item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
            );
            setToast("Thay đổi trạng thái thành công");
            window.setTimeout(() => setToast(""), 2500);
            resource.refresh();
        } catch (error) {
            setActionError(messageOf(error));
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────

    return (
        <section className="grid gap-5">
            <PageHeader
                title="Quản lý kệ"
                description="Quản lý các kệ hàng trong cửa hàng. Mỗi kệ thuộc kho chính của cửa hàng."
                actions={
                    <ActionButton
                        variant="primary"
                        onClick={() => {
                            setEditing(null);
                            setShowForm(true);
                        }}
                    >
                        <Plus size={17} />
                        Thêm kệ
                    </ActionButton>
                }
            />

            {/* ── Bộ lọc ─────────────────────────────────────────────────────── */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                <div className="flex flex-col">
                    {/* Trạng thái */}
                    <div className="flex flex-wrap items-center gap-2 py-[10px]">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0 w-28">
                            Trạng thái:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <FilterChip
                                label="Tất cả"
                                active={status === "ALL"}
                                onClick={() => { setStatus("ALL"); setPage(1); }}
                            />
                            {Object.entries(inventoryStatusLabels).map(([value, label]) => (
                                <FilterChip
                                    key={value}
                                    label={label}
                                    active={status === value}
                                    onClick={() => { setStatus(value as InventoryStatus); setPage(1); }}
                                />
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* ── Bảng dữ liệu ───────────────────────────────────────────────── */}
            <div className="grid gap-4 grid-cols-1">
                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {resource.loading ? <InventoryLoading /> : null}
                    {!resource.loading && resource.error ? (
                        <InventoryError message={resource.error} onRetry={resource.refresh} />
                    ) : null}
                    {!resource.loading && !resource.error && shelves.length === 0 ? (
                        <InventoryEmpty />
                    ) : null}
                    {!resource.loading && !resource.error && shelves.length > 0 ? (
                        <div className="overflow-x-auto">
                            <table className="min-w-[720px] w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-white text-[13px] font-[950] text-[#172317]">
                                    <tr>
                                        <Th first>Mã kệ</Th>
                                        <Th>Tên kệ</Th>
                                        <Th>Trạng thái</Th>
                                        <Th right>Thao tác</Th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {shelves.map((item) => (
                                        <tr
                                            key={item.id}
                                            onClick={() => setSelected(item)}
                                            className={`cursor-pointer hover:bg-neutral-50 transition-all ${selected?.id === item.id
                                                    ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                                                    : ""
                                                }`}
                                        >
                                            <Cell strong first>{item.code}</Cell>
                                            <Cell>{item.name}</Cell>
                                            <Cell>
                                                <InventoryStatusBadge status={item.status} />
                                            </Cell>
                                            <td
                                                className="border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 pr-[18px]"
                                                onClick={(e) => e.stopPropagation()}
                                            >
                                                <div className="flex justify-end gap-1">
                                                    <IconButton
                                                        label="Xem chi tiết"
                                                        onClick={() => setSelected(item)}
                                                    >
                                                        <Eye size={16} />
                                                    </IconButton>
                                                    <IconButton
                                                        label="Chỉnh sửa kệ"
                                                        onClick={() => { setEditing(item); setShowForm(true); }}
                                                    >
                                                        <Pencil size={16} />
                                                    </IconButton>
                                                    <IconButton
                                                        label="Thay đổi trạng thái"
                                                        onClick={() => void changeStatus(item)}
                                                    >
                                                        <ToggleLeft size={17} />
                                                    </IconButton>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    ) : null}
                    {!resource.loading && !resource.error ? (
                        <Pagination
                            page={(resource.data?.page ?? 0) + 1}
                            pageCount={resource.data?.totalPages ?? 1}
                            totalItems={resource.data?.totalElements ?? 0}
                            pageSize={pageSize}
                            onPageChange={setPage}
                            onPageSizeChange={(value) => { setPageSize(value); setPage(1); }}
                        />
                    ) : null}
                </section>

                {/* ── Modal chi tiết ──────────────────────────────────────────── */}
                {selected ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-5"
                        onClick={() => setSelected(null)}
                        role="presentation"
                    >
                        <div
                            aria-modal="true"
                            className="w-full max-w-xl max-h-[85vh] overflow-y-auto"
                            onClick={(e) => e.stopPropagation()}
                            role="dialog"
                        >
                            <DetailPanel title="Chi tiết kệ" onClose={() => setSelected(null)}>
                                <dl className="grid gap-4 text-sm">
                                    <Detail label="Mã kệ" value={selected.code} />
                                    <Detail label="Tên kệ" value={selected.name} />
                                    <Detail
                                        label="Trạng thái"
                                        value={inventoryStatusLabels[selected.status]}
                                    />
                                </dl>
                            </DetailPanel>
                        </div>
                    </div>
                ) : null}
            </div>

            {/* ── Modal thêm / sửa kệ ────────────────────────────────────────── */}
            {showForm ? (
                <ShelfForm
                    item={editing}
                    error={actionError}
                    onClose={() => { setShowForm(false); setActionError(""); }}
                    onSubmit={save}
                />
            ) : null}

            {/* Lỗi ngoài form */}
            {actionError && !showForm ? (
                <p className="rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">
                    {actionError}
                </p>
            ) : null}

            {/* Toast thông báo */}
            {toast ? (
                <div className="fixed bottom-6 right-6 z-50 rounded-xl bg-hvt-green-900 px-5 py-3 text-sm font-bold text-white shadow-pos-panel">
                    {toast}
                </div>
            ) : null}
        </section>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function FilterChip({
    label,
    active,
    onClick
}: {
    label: string;
    active: boolean;
    onClick(): void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${active
                    ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                    : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                }`}
        >
            {label}
        </button>
    );
}

function Th({
    children,
    first = false,
    right = false
}: {
    children: React.ReactNode;
    first?: boolean;
    right?: boolean;
}) {
    return (
        <th
            className={`h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] ${first ? "first:pl-[18px]" : ""
                } ${right ? "text-right pr-[18px]" : ""}`}
        >
            {children}
        </th>
    );
}

function Cell({
    children,
    strong = false,
    first = false
}: {
    children: React.ReactNode;
    strong?: boolean;
    first?: boolean;
}) {
    return (
        <td
            className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 ${first ? "pl-[18px]" : ""
                } ${strong ? "font-black text-[#172317]" : ""}`}
        >
            {children}
        </td>
    );
}

function IconButton({
    label,
    children,
    onClick
}: {
    label: string;
    children: React.ReactNode;
    onClick(): void;
}) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            onClick={(e) => { e.stopPropagation(); onClick(); }}
            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"
        >
            {children}
        </button>
    );
}

function Detail({ label, value }: { label: string; value: string }) {
    return (
        <div className="border-b border-pos-border pb-3">
            <dt className="text-xs font-bold uppercase text-gray-500">{label}</dt>
            <dd className="mt-1 font-semibold">{value}</dd>
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// ShelfForm – chỉ cho nhập Tên kệ và Trạng thái
// ─────────────────────────────────────────────────────────────────────────────

function ShelfForm({
    item,
    error,
    onClose,
    onSubmit
}: {
    item: InventoryLocationResponse | null;
    error: string;
    onClose(): void;
    onSubmit(input: StoreShelfRequest): void;
}) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-5">
            <form
                onSubmit={(event) => {
                    event.preventDefault();
                    const data = new FormData(event.currentTarget);
                    onSubmit({
                        name: String(data.get("name") ?? "").trim(),
                        status: data.get("status") as InventoryStatus
                    });
                }}
                className="w-full max-w-md rounded-3xl bg-white p-6 shadow-pos-panel"
            >
                <header className="mb-5 flex items-center justify-between">
                    <h2 className="text-2xl font-bold">
                        {item ? "Chỉnh sửa kệ" : "Thêm kệ mới"}
                    </h2>
                    <button type="button" onClick={onClose} aria-label="Đóng">
                        <X />
                    </button>
                </header>

                <div className="grid gap-4">
                    {/* Tên kệ */}
                    <label className="grid gap-2 text-sm font-bold">
                        Tên kệ
                        <input
                            name="name"
                            type="text"
                            required
                            defaultValue={item?.name ?? ""}
                            placeholder="Nhập tên kệ..."
                            className="h-11 rounded-xl border border-pos-border px-3 font-normal"
                        />
                    </label>

                    {/* Trạng thái */}
                    <label className="grid gap-2 text-sm font-bold">
                        Trạng thái
                        <select
                            name="status"
                            defaultValue={item?.status ?? "ACTIVE"}
                            className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"
                        >
                            {Object.entries(inventoryStatusLabels).map(([value, label]) => (
                                <option key={value} value={value}>{label}</option>
                            ))}
                        </select>
                    </label>
                </div>

                {error ? (
                    <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">
                        {error}
                    </p>
                ) : null}

                <footer className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onClose}
                        className="h-11 rounded-xl border border-pos-border px-5 font-bold"
                    >
                        Hủy
                    </button>
                    <button
                        type="submit"
                        className="h-11 rounded-xl bg-hvt-green-900 px-5 font-bold text-white"
                    >
                        Lưu
                    </button>
                </footer>
            </form>
        </div>
    );
}
