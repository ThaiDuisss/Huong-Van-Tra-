import { Eye, Pencil, Plus, ToggleLeft, X } from "lucide-react";
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
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selected, setSelected] =
        useState<InventoryLocationResponse | null>(null);
    const [editing, setEditing] =
        useState<InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [actionError, setActionError] = useState("");
    const [toast, setToast] = useState("");
    const load = useCallback(
        () =>
            inventoryLocationApi.list({
                page: page - 1,
                size: pageSize,
                branchId: branchId ? branchId : undefined,
                type: type === "ALL" ? undefined : type,
                status: status === "ALL" ? undefined : status
            }),
        [branchId, page, pageSize, status, type]
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
                <div className="flex flex-col">
                    {/* Loại vị trí */}
                    <div className="flex flex-wrap items-center gap-2 py-[10px] border-b border-[#f3f5f7]">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0 w-28">
                            Loại vị trí:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => { setType("ALL"); setPage(1); }}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    type === "ALL"
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {Object.entries(locationTypeLabels).map(([value, label]) => {
                                const isActive = type === value;
                                return (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => { setType(value as any); setPage(1); }}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Trạng thái */}
                    <div className="flex flex-wrap items-center gap-2 py-[10px] last:border-b-0 last:pb-0">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0 w-28">
                            Trạng thái:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => { setStatus("ALL"); setPage(1); }}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    status === "ALL"
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {Object.entries(inventoryStatusLabels).map(([value, label]) => {
                                const isActive = status === value;
                                return (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => { setStatus(value as any); setPage(1); }}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
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
                        <div className="overflow-x-auto">
                            <table className="min-w-[920px] w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-white text-[13px] font-[950] text-[#172317]">
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
                                    {locations.map((item) => (
                                        <tr
                                            key={item.id}
                                            onClick={() => setSelected(item)}
                                            className={`cursor-pointer hover:bg-neutral-50 transition-all ${selected?.id === item.id ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]" : ""}`}
                                        >
                                            <Cell strong>{item.code}</Cell>
                                            <Cell>{item.name}</Cell>
                                            <Cell><LocationTypeBadge type={item.type} /></Cell>
                                            <Cell>{item.parentLocationId ? `#${item.parentLocationId}` : "-"}</Cell>
                                            <Cell><InventoryStatusBadge status={item.status} /></Cell>
                                            <td className="border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 pr-[18px]" onClick={(e) => e.stopPropagation()}>
                                                <div className="flex justify-end gap-1">
                                                    <IconButton label="Xem chi tiết" onClick={() => setSelected(item)}><Eye size={16} /></IconButton>
                                                    <IconButton label="Chỉnh sửa vị trí" onClick={() => { setEditing(item); setShowForm(true); }}><Pencil size={16} /></IconButton>
                                                    <IconButton label="Thay đổi trạng thái" onClick={() => void changeStatus(item)}><ToggleLeft size={17} /></IconButton>
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

function IconButton({ label, children, onClick }: { label: string; children: React.ReactNode; onClick(): void }) {
    return <button type="button" aria-label={label} title={label} onClick={(event) => { event.stopPropagation(); onClick(); }} className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100">{children}</button>;
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
