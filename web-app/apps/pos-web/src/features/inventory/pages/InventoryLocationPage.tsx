import { Eye, Pencil, Plus, ToggleLeft, X, MoreHorizontal } from "lucide-react";
import { useCallback, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";

import {
    InventoryStatusBadge,
    LocationTypeBadge
} from "../components/InventoryBadges";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import {
    messageOf,
    useInventoryPage
} from "../hooks/useInventoryResource";
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
    const [branchId, _setBranchId] = useState("");
    const [type, setType] = useState<"ALL" | InventoryLocationType>("ALL");
    const [status, setStatus] = useState<"ALL" | InventoryStatus>("ALL");
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selected, setSelected] =
        useState<InventoryLocationResponse | null>(null);
    const [editing, setEditing] =
        useState<InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);
    const [actionError, setActionError] = useState("");
    const [toast, setToast] = useState("");
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

    async function save(input: InventoryLocationRequest) {
        setActionError("");

        try {
            if (editing) {
                await inventoryLocationApi.update(editing.id, input);
            } else {
                await inventoryLocationApi.create(input);
            }
            setShowForm(false);
            setToast(editing ? "Cập nhật thành công" : "Tạo thành công");
            window.setTimeout(() => setToast(""), 2500);
            resource.refresh();
        } catch (error) {
            setActionError(messageOf(error));
        }
    }

    async function changeStatus(item: InventoryLocationResponse) {
        if (!window.confirm("Bạn có chắc chắn muốn thay đổi trạng thái?")) {
            return;
        }

        try {
            await inventoryLocationApi.updateStatus(
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

    return (
        <section className="grid gap-5">
            {/* <Breadcrumbs items={["Quản lý tồn kho", "Quản lý kệ hàng"]} /> */}
            <PageHeader
                title="Quản lý kệ hàng"
                description="Quản lý kệ, quầy thu ngân và các vị trí lưu trữ tại cửa hàng."
                actions={
                    <ActionButton
                        variant="primary"
                        onClick={() => {
                            setEditing(null);
                            setShowForm(true);
                        }}
                    >
                        <Plus size={17} />
                        Tạo vị trí
                    </ActionButton>
                }
            />

            {/* Config & Filters Row */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                <div className="flex flex-col md:flex-row gap-3 justify-between items-stretch md:items-center">
                    {/* Search */}
                    <div className="relative flex-1">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                        </span>
                        <input
                            type="text"
                            placeholder="Tìm theo tên vị trí, mã kệ..."
                            value={query}
                            onChange={(e) => { setQuery(e.target.value); setPage(1); }}
                            className="h-10 w-full rounded-xl border border-pos-border pl-9 pr-3 text-sm outline-none focus:border-hvt-green-700 bg-white"
                        />
                    </div>
                    
                    <div className="flex flex-wrap gap-3 items-center">
                        {/* Filter 1: Loại vị trí */}
                        <select
                            value={type}
                            onChange={(e) => { setType(e.target.value as any); setPage(1); }}
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer min-w-[150px] truncate"
                        >
                            <option value="ALL">Loại vị trí</option>
                            {Object.entries(locationTypeLabels).map(([value, label]) => (
                                <option key={value} value={value}>{label}</option>
                            ))}
                        </select>
                        {/* Filter 2: Trạng thái */}
                        <select
                            value={status}
                            onChange={(e) => { setStatus(e.target.value as any); setPage(1); }}
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer min-w-[150px] truncate"
                        >
                            <option value="ALL">Trạng thái</option>
                            {Object.entries(inventoryStatusLabels).map(([value, label]) => (
                                <option key={value} value={value}>{label}</option>
                            ))}
                        </select>
                    </div>
                </div>
            </div>

            {/* Table and Details Card */}
            <div className="grid gap-4 grid-cols-1">
                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {resource.loading ? <InventoryLoading /> : null}
                    {!resource.loading && resource.error ? (
                        <InventoryError
                            message={resource.error}
                            onRetry={resource.refresh}
                        />
                    ) : null}
                    {!resource.loading &&
                    !resource.error &&
                    locations.length === 0 ? (
                        <InventoryEmpty />
                    ) : null}
                    {!resource.loading &&
                    !resource.error &&
                    locations.length > 0 ? (
                        <div className="overflow-x-auto min-h-[280px]">
                            <table className="min-w-[920px] w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900">
                                    <tr>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] first:pl-[18px]">Mã kệ</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Tên vị trí</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Loại vị trí</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Vị trí cha</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Trạng thái</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {locations.map((item, idx) => {
                                        const isSelected = selected?.id === item.id;
                                        const isEven = idx % 2 === 1;
                                        const rowBgClass = isSelected
                                            ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                                            : isEven
                                                ? "bg-[#f2faf1]/50"
                                                : "bg-white";

                                        return (
                                            <tr
                                                key={item.id}
                                                onClick={() => setSelected(item)}
                                                className={`cursor-pointer hover:bg-neutral-50 transition-all ${rowBgClass}`}
                                            >
                                                <Cell strong>{item.code}</Cell>
                                                <Cell>{item.name}</Cell>
                                                <Cell><LocationTypeBadge type={item.type} /></Cell>
                                                <Cell>{item.parentLocationId ? `#${item.parentLocationId}` : "-"}</Cell>
                                                <Cell><InventoryStatusBadge status={item.status} /></Cell>
                                                <td className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 pr-[18px] relative ${activeActionMenuId === String(item.id) ? "z-30" : "z-20"}`} onClick={(e) => e.stopPropagation()}>
                                                    <div className="flex justify-end relative">
                                                        <button
                                                            type="button"
                                                            onClick={() => setActiveActionMenuId(activeActionMenuId === String(item.id) ? null : String(item.id))}
                                                            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 text-gray-500 hover:text-gray-700 cursor-pointer"
                                                        >
                                                            <MoreHorizontal size={16} />
                                                        </button>

                                                        {activeActionMenuId === String(item.id) && (
                                                            <>
                                                                <div className="fixed inset-0 z-30" onClick={() => setActiveActionMenuId(null)} />
                                                                <div className={`absolute right-0 ${idx >= 2 && idx >= locations.length - 2 ? "bottom-8 mb-1" : "top-8 mt-1"} w-44 rounded-xl border border-pos-border bg-white p-1.5 shadow-lg z-40 text-left`}>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => { setSelected(item); setActiveActionMenuId(null); }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <Eye size={14} />
                                                                        Xem chi tiết
                                                                    </button>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => { setEditing(item); setShowForm(true); setActiveActionMenuId(null); }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <Pencil size={14} />
                                                                        Chỉnh sửa vị trí
                                                                    </button>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => { void changeStatus(item); setActiveActionMenuId(null); }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <ToggleLeft size={14} />
                                                                        Đổi trạng thái
                                                                    </button>
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
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

                {selected ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-5"
                        onClick={() => setSelected(null)}
                        role="presentation"
                    >
                        <div
                            aria-modal="true"
                            className="w-full max-w-xl max-h-[85vh] overflow-y-auto"
                            onClick={(event) => event.stopPropagation()}
                            role="dialog"
                        >
                            <DetailPanel title="Chi tiết vị trí" onClose={() => setSelected(null)}>
                                <dl className="grid gap-4 text-sm">
                                    <Detail label="Mã kệ" value={selected.code} />
                                    <Detail label="Tên vị trí" value={selected.name} />
                                    <Detail label="Loại vị trí" value={locationTypeLabels[selected.type]} />
                                    <Detail label="Trạng thái" value={inventoryStatusLabels[selected.status]} />
                                </dl>
                            </DetailPanel>
                        </div>
                    </div>
                ) : null}
            </div>

            {showForm ? (
                <LocationForm
                    item={editing}
                    locations={locations}
                    error={actionError}
                    onClose={() => setShowForm(false)}
                    onSubmit={save}
                />
            ) : null}
            {actionError && !showForm ? (
                <p className="rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{actionError}</p>
            ) : null}
            {toast ? <div className="fixed bottom-6 right-6 z-50 rounded-xl bg-hvt-green-900 px-5 py-3 text-sm font-bold text-white shadow-pos-panel">{toast}</div> : null}
        </section>
    );
}

function Cell({ children, strong = false }: { children: React.ReactNode; strong?: boolean }) {
    return <td className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 first:pl-[18px] ${strong ? "font-black text-[#172317]" : ""}`}>{children}</td>;
}



function Detail({ label, value }: { label: string; value: string }) {
    return <div className="border-b border-pos-border pb-3"><dt className="text-xs font-bold uppercase text-gray-500">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>;
}

function LocationForm({ item, locations, error, onClose, onSubmit }: { item: InventoryLocationResponse | null; locations: InventoryLocationResponse[]; error: string; onClose(): void; onSubmit(input: InventoryLocationRequest): void }) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-5">
            <form
                onSubmit={(event) => {
                    event.preventDefault();
                    const data = new FormData(event.currentTarget);
                    onSubmit({
                        branchId: String(data.get("branchId") || ""),
                        parentLocationId: data.get("parentLocationId") ? String(data.get("parentLocationId")) : null,
                        name: String(data.get("name") ?? "").trim(),
                        type: data.get("type") as InventoryLocationType,
                        status: data.get("status") as InventoryStatus
                    });
                }}
                className="w-full max-w-xl rounded-3xl bg-white p-6 shadow-pos-panel"
            >
                <header className="mb-5 flex items-center justify-between">
                    <h2 className="text-2xl font-bold">{item ? "Chỉnh sửa vị trí" : "Tạo vị trí"}</h2>
                    <button type="button" onClick={onClose} aria-label="Đóng"><X /></button>
                </header>

                {/* Hiển thị mã kệ hiện tại khi chỉnh sửa */}
                {item ? (
                    <div className="mb-4 flex items-center gap-2 rounded-xl bg-neutral-50 px-4 py-3 text-sm border border-neutral-200">
                        <span className="font-semibold text-gray-500">Mã kệ:</span>
                        <span className="font-black text-[#07883a] tracking-wide">{item.code}</span>
                        <span className="ml-auto text-xs text-gray-400 italic">Mã được hệ thống tự tạo, không thể chỉnh sửa</span>
                    </div>
                ) : (
                    <div className="mb-4 flex items-center gap-2 rounded-xl bg-blue-50 px-4 py-3 text-sm border border-blue-100">
                        <span className="text-blue-600 font-semibold">✦ Mã kệ sẽ được hệ thống tự động tạo khi lưu</span>
                    </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                    {/* <FormInput name="branchId" label="Mã chi nhánh" type="number" value={String(item?.branchId ?? "")} /> */}
                    <label className="grid gap-2 text-sm font-bold">Vị trí cha<select name="parentLocationId" defaultValue={String(item?.parentLocationId ?? "")} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"><option value="">Không có</option>{locations.filter((location) => location.id !== item?.id).map((location) => <option key={location.id} value={location.id}>{location.code} - {location.name}</option>)}</select></label>
                    <FormInput name="name" label="Tên vị trí" value={item?.name ?? ""} />
                    <label className="grid gap-2 text-sm font-bold">Loại vị trí<select name="type" defaultValue={item?.type ?? "WAREHOUSE"} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal">{Object.entries(locationTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                    <label className="grid gap-2 text-sm font-bold">Trạng thái<select name="status" defaultValue={item?.status ?? "ACTIVE"} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal">{Object.entries(inventoryStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                </div>
                {error ? <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{error}</p> : null}
                <footer className="mt-6 flex justify-end gap-3">
                    <button type="button" onClick={onClose} className="h-11 rounded-xl border border-pos-border px-5 font-bold">Hủy</button>
                    <button type="submit" className="h-11 rounded-xl bg-hvt-green-900 px-5 font-bold text-white">Lưu</button>
                </footer>
            </form>
        </div>
    );
}

function FormInput({ name, label, value, type = "text" }: { name: string; label: string; value: string; type?: string }) {
    return <label className="grid gap-2 text-sm font-bold">{label}<input name={name} type={type} required defaultValue={value} className="h-11 rounded-xl border border-pos-border px-3 font-normal" /></label>;
}
