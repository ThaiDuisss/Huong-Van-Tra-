import { Eye, Pencil, Plus, ToggleLeft, X } from "lucide-react";
import { useCallback, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    InventoryFilters,
    InventorySelect
} from "../components/InventoryFilters";
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

            <div className="grid min-h-[640px] grid-cols-1 gap-4 xl:grid-cols-[240px_minmax(0,1fr)] min-[1800px]:grid-cols-[250px_minmax(0,1fr)_340px]">
                <InventoryFilters>
                    {/* <InventoryInput
                        label="Chi nhánh"
                        value={branchId}
                        type="number"
                        placeholder="Nhập mã chi nhánh"
                        onChange={(value) => {
                            setBranchId(value);
                            setPage(1);
                        }}
                    /> */}
                    <InventorySelect
                        label="Loại vị trí"
                        value={type}
                        options={[
                            { value: "ALL", label: "Tất cả" },
                            ...Object.entries(locationTypeLabels).map(
                                ([value, label]) => ({ value, label })
                            )
                        ]}
                        onChange={(value) => {
                            setType(value as "ALL" | InventoryLocationType);
                            setPage(1);
                        }}
                    />
                    <InventorySelect
                        label="Trạng thái"
                        value={status}
                        options={[
                            { value: "ALL", label: "Tất cả" },
                            ...Object.entries(inventoryStatusLabels).map(
                                ([value, label]) => ({ value, label })
                            )
                        ]}
                        onChange={(value) => {
                            setStatus(value as "ALL" | InventoryStatus);
                            setPage(1);
                        }}
                    />
                    {/* <button
                        type="button"
                        onClick={resource.refresh}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-sm font-bold"
                    >
                        <RefreshCw size={15} />
                        Tải lại dữ liệu
                    </button> */}
                </InventoryFilters>

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
                            <table className="min-w-[920px] w-full text-left text-sm">
                                <thead className="bg-neutral-50 text-xs text-gray-600">
                                    <tr>
                                        <th className="px-4 py-3">Mã kệ</th>
                                        <th className="px-4 py-3">Tên vị trí</th>
                                        <th className="px-4 py-3">Loại vị trí</th>
                                        {/* <th className="px-4 py-3">Chi nhánh</th> */}
                                        <th className="px-4 py-3">Vị trí cha</th>
                                        <th className="px-4 py-3">Trạng thái</th>
                                        <th className="px-4 py-3 text-right">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {locations.map((item) => (
                                        <tr
                                            key={item.id}
                                            onClick={() => setSelected(item)}
                                            className="cursor-pointer hover:bg-neutral-50"
                                        >
                                            <Cell strong>{item.code}</Cell>
                                            <Cell>{item.name}</Cell>
                                            <Cell><LocationTypeBadge type={item.type} /></Cell>
                                            {/* <Cell>#{item.branchId}</Cell> */}
                                            <Cell>{item.parentLocationId ? `#${item.parentLocationId}` : "-"}</Cell>
                                            <Cell><InventoryStatusBadge status={item.status} /></Cell>
                                            <Cell>
                                                <div className="flex justify-end gap-1">
                                                    <IconButton label="Xem chi tiết" onClick={() => setSelected(item)}><Eye size={16} /></IconButton>
                                                    <IconButton label="Chỉnh sửa vị trí" onClick={() => { setEditing(item); setShowForm(true); }}><Pencil size={16} /></IconButton>
                                                    <IconButton label="Thay đổi trạng thái" onClick={() => void changeStatus(item)}><ToggleLeft size={17} /></IconButton>
                                                </div>
                                            </Cell>
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

                <div className="hidden min-h-0 min-[1800px]:block [&>aside]:h-full">
                    {selected ? (
                        <DetailPanel title="Chi tiết vị trí" onClose={() => setSelected(null)}>
                            <dl className="grid gap-4 text-sm">
                                <Detail label="Mã kệ" value={selected.code} />
                                <Detail label="Tên vị trí" value={selected.name} />
                                <Detail label="Loại vị trí" value={locationTypeLabels[selected.type]} />
                                {/* <Detail label="Chi nhánh" value={`#${selected.branchId}`} /> */}
                                <Detail label="Trạng thái" value={inventoryStatusLabels[selected.status]} />
                            </dl>
                        </DetailPanel>
                    ) : null}
                </div>
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
    return <td className={`border-t border-pos-border px-4 py-4 ${strong ? "font-bold" : ""}`}>{children}</td>;
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
            <form onSubmit={(event) => { event.preventDefault(); const data = new FormData(event.currentTarget); onSubmit({ branchId: String(data.get("branchId") || ""), parentLocationId: data.get("parentLocationId") ? String(data.get("parentLocationId")) : null, code: String(data.get("code") ?? "").trim(), name: String(data.get("name") ?? "").trim(), type: data.get("type") as InventoryLocationType, status: data.get("status") as InventoryStatus }); }} className="w-full max-w-xl rounded-3xl bg-white p-6 shadow-pos-panel">
                <header className="mb-5 flex items-center justify-between"><h2 className="text-2xl font-bold">{item ? "Chỉnh sửa vị trí" : "Tạo vị trí"}</h2><button type="button" onClick={onClose} aria-label="Đóng"><X /></button></header>
                <div className="grid gap-4 sm:grid-cols-2">
                    {/* <FormInput name="branchId" label="Mã chi nhánh" type="number" value={String(item?.branchId ?? "")} /> */}
                    <label className="grid gap-2 text-sm font-bold">Vị trí cha<select name="parentLocationId" defaultValue={String(item?.parentLocationId ?? "")} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"><option value="">Không có</option>{locations.filter((location) => location.id !== item?.id).map((location) => <option key={location.id} value={location.id}>{location.code} - {location.name}</option>)}</select></label>
                    <FormInput name="code" label="Mã kệ" value={item?.code ?? ""} />
                    <FormInput name="name" label="Tên vị trí" value={item?.name ?? ""} />
                    <label className="grid gap-2 text-sm font-bold">Loại vị trí<select name="type" defaultValue={item?.type ?? "SHELF"} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal">{Object.entries(locationTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                    <label className="grid gap-2 text-sm font-bold">Trạng thái<select name="status" defaultValue={item?.status ?? "ACTIVE"} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal">{Object.entries(inventoryStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                </div>
                {error ? <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{error}</p> : null}
                <footer className="mt-6 flex justify-end gap-3"><button type="button" onClick={onClose} className="h-11 rounded-xl border border-pos-border px-5 font-bold">Hủy</button><button type="submit" className="h-11 rounded-xl bg-hvt-green-900 px-5 font-bold text-white">Lưu</button></footer>
            </form>
        </div>
    );
}

function FormInput({ name, label, value, type = "text" }: { name: string; label: string; value: string; type?: string }) {
    return <label className="grid gap-2 text-sm font-bold">{label}<input name={name} type={type} required defaultValue={value} className="h-11 rounded-xl border border-pos-border px-3 font-normal" /></label>;
}
