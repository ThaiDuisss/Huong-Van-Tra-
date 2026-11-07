import { ChevronDown, ChevronRight, X } from "lucide-react";
import { useCallback, useMemo, useState } from "react";

import {
    InventoryTreeResourceManagement,
    TreeTableCell,
    type Column
} from "../components/InventoryTreeResourceManagement";
import {
    InventoryStatusBadge,
    LocationTypeBadge
} from "../components/InventoryBadges";
import { useInventoryResource } from "../hooks/useInventoryResource";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import type {
    InventoryLocationHierarchyResponse,
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
    
    const [editing, setEditing] = useState<InventoryLocationHierarchyResponse | InventoryLocationResponse | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [collapsedIds, setCollapsedIds] = useState<Set<string>>(new Set());

    const load = useCallback(
        () =>
            inventoryLocationApi.getAll({
                branchId: branchId ? branchId : undefined,
                type: type === "ALL" ? undefined : type,
                status: status === "ALL" ? undefined : status,
                query: query.trim() || undefined
            }),
        [branchId, status, type, query]
    );

    const resource = useInventoryResource(load);
    const locations = resource.data ?? [];

    const locationMap = useMemo(() => {
        const map = new Map<string, InventoryLocationHierarchyResponse>();
        for (const loc of locations) {
            map.set(String(loc.id), loc);
        }
        return map;
    }, [locations]);

    // Lọc các vị trí hiển thị dựa theo trạng thái đóng/mở của các vị trí cha
    const visibleLocations = useMemo(() => {
        return locations.filter((loc) => {
            let parentId = loc.parentLocationId;
            while (parentId) {
                if (collapsedIds.has(String(parentId))) {
                    return false;
                }
                const parent = locationMap.get(String(parentId));
                parentId = parent?.parentLocationId ?? null;
            }
            return true;
        });
    }, [locations, collapsedIds, locationMap]);

    const toggleCollapse = (id: string) => {
        setCollapsedIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

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
        resource.refresh();
    };

    const columns: Column[] = [
        { key: "code", label: "Mã vị trí", width: "170px" },
        { key: "name", label: "Tên vị trí" },
        { key: "type", label: "Loại vị trí", width: "160px" },
        { key: "status", label: "Trạng thái", width: "150px" }
    ];

    return (
        <InventoryTreeResourceManagement
            title="Quản lý vị trí lưu kho"
            description="Quản lý vị trí lưu kho, phân cấp khu vực và các vị trí lưu trữ trong kho."
            createLabel="Tạo vị trí"
            searchPlaceholder="Tìm theo tên vị trí, mã vị trí..."
            totalItemsLabel="Tổng số vị trí"
            items={visibleLocations}
            loading={resource.loading}
            error={resource.error || ""}
            query={query}
            status={status}
            onQueryChange={(val) => {
                setQuery(val);
            }}
            onRefresh={resource.refresh}
            onSubmit={async () => {}} // customForm handles onSubmit
            onChangeStatus={handleToggleStatus}
            controlledEditingItem={editing as any}
            controlledShowForm={showForm}
            onControlledShowFormChange={setShowForm}
            onControlledEditingItemChange={(item) => setEditing(item as InventoryLocationHierarchyResponse | null)}
            columns={columns}
            filters={[
                {
                    label: "Bộ lọc",
                    value: "FILTER",
                    options: [],
                    onChange: () => {},
                    render: (
                        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-y-3 gap-x-6 pt-1 w-full">
                            {/* Loại vị trí (bên trái) */}
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                                    Loại vị trí:
                                </span>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <button
                                        type="button"
                                        onClick={() => setType("ALL")}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            type === "ALL"
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        Tất cả
                                    </button>
                                    {Object.entries(locationTypeLabels).map(([val, label]) => {
                                        const isActive = type === val;
                                        return (
                                            <button
                                                key={val}
                                                type="button"
                                                onClick={() => setType(val as any)}
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

                            {/* Trạng thái (bên phải) */}
                            <div className="flex flex-wrap items-center gap-2 md:ml-auto">
                                <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                                    Trạng thái:
                                </span>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <button
                                        type="button"
                                        onClick={() => setStatus("ALL")}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            status === "ALL"
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        Tất cả
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setStatus("ACTIVE")}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            status === "ACTIVE"
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        Đang hoạt động
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setStatus("INACTIVE")}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            status === "INACTIVE"
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        Ngừng hoạt động
                                    </button>
                                </div>
                            </div>
                        </div>
                    )
                }
            ]}
            renderCells={(item: InventoryLocationHierarchyResponse) => {
                const isParent = (item.level ?? 0) === 0;
                const isCollapsed = collapsedIds.has(String(item.id));
                const indentPx = (item.level ?? 0) * 28;

                return (
                    <>
                        <TreeTableCell strong={isParent}>
                            {item.code}
                        </TreeTableCell>
                        <TreeTableCell>
                            <div
                                className="flex items-center gap-1.5 py-0.5"
                                style={{ paddingLeft: `${indentPx}px` }}
                            >
                                {item.hasChildren ? (
                                    <button
                                        type="button"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            toggleCollapse(String(item.id));
                                        }}
                                        className="grid size-6 place-items-center rounded hover:bg-black/10 text-gray-600 transition-colors cursor-pointer shrink-0"
                                        title={isCollapsed ? "Mở rộng vị trí con" : "Thu gọn vị trí con"}
                                    >
                                        {isCollapsed ? (
                                            <ChevronRight size={16} />
                                        ) : (
                                            <ChevronDown size={16} />
                                        )}
                                    </button>
                                ) : (
                                    <span className="inline-block w-6 shrink-0" />
                                )}

                                <span
                                    className={
                                        isParent
                                            ? "font-bold text-[#14532d]"
                                            : "font-medium text-gray-700"
                                    }
                                >
                                    {item.name}
                                </span>

                                {item.hasChildren && (
                                    <span className="text-xs text-gray-400 font-medium">
                                        ({item.childrenCount})
                                    </span>
                                )}
                            </div>
                        </TreeTableCell>
                        <TreeTableCell>
                            <LocationTypeBadge type={item.type} />
                        </TreeTableCell>
                        <TreeTableCell>
                            <InventoryStatusBadge status={item.status} />
                        </TreeTableCell>
                    </>
                );
            }}
            renderDetail={(item: any) => (
                <dl className="grid gap-4 text-sm">
                    <DetailItem label="Mã vị trí" value={item.code} />
                    <DetailItem label="Tên vị trí" value={item.name} />
                    <DetailItem
                        label="Phân cấp vị trí"
                        value={
                            (item.level ?? 0) === 0
                                ? "Vị trí cha (gốc)"
                                : `Vị trí con (Cấp ${(item.level ?? 0) + 1})`
                        }
                    />
                    {item.parentLocationName && (
                        <DetailItem
                            label="Vị trí cha"
                            value={`${item.parentLocationCode ? `[${item.parentLocationCode}] ` : ""}${item.parentLocationName}`}
                        />
                    )}
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
                    item={editingItem as InventoryLocationHierarchyResponse | null}
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
    item: InventoryLocationHierarchyResponse | InventoryLocationResponse | null;
    locations: InventoryLocationHierarchyResponse[];
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
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/45 p-6 overflow-y-auto">
            <form
                onSubmit={handleSubmit}
                className="w-full max-w-xl rounded-3xl bg-white p-6 shadow-pos-panel border border-pos-border animate-in fade-in zoom-in-95 duration-150"
            >
                <header className="mb-5 flex items-center justify-between border-b border-pos-border pb-3">
                    <h2 className="text-xl font-bold text-gray-700">
                        {item ? "Chỉnh sửa vị trí lưu kho" : "Tạo vị trí lưu kho mới"}
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

                {item ? (
                    <div className="mb-4 flex items-center gap-2 rounded-xl bg-neutral-50 px-4 py-3 text-sm border border-neutral-200">
                        <span className="font-semibold text-gray-500">Mã vị trí:</span>
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
                            ✦ Mã vị trí sẽ được hệ thống tự động tạo khi lưu
                        </span>
                    </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Vị trí cha
                        <select
                            name="parentLocationId"
                            defaultValue={String(item?.parentLocationId ?? "")}
                            className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                        >
                            <option value="">Không có (Vị trí cha)</option>
                            {locations
                                .filter((location) => location.id !== item?.id)
                                .map((location) => (
                                    <option key={location.id} value={location.id}>
                                        {"— ".repeat(location.level || 0)}{location.code} - {location.name}
                                    </option>
                                ))}
                        </select>
                    </label>

                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Tên vị trí *
                        <input
                            name="name"
                            type="text"
                            required
                            placeholder="Nhập tên vị trí chi tiết..."
                            defaultValue={item?.name ?? ""}
                            className="h-10 w-full rounded-xl border border-pos-border px-3 font-medium text-gray-700 focus:outline-none"
                        />
                    </label>

                    <label className="grid gap-1.5 text-sm font-bold text-gray-500">
                        Loại vị trí
                        <select
                            name="type"
                            defaultValue={item?.type ?? "WAREHOUSE"}
                            className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:outline-none"
                        >
                            {Object.entries(locationTypeLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </select>
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
                        {submitting ? "Đang lưu..." : item ? "Lưu thay đổi" : "Tạo mới"}
                    </button>
                </footer>
            </form>
        </div>
    );
}
