import {
    AlertTriangle,
    ArrowUpDown,
    CheckCircle2,
    Clock3,
    Eye,
    FileText,
    Lock,
    PackageOpen,
    ShieldAlert,
    Upload,
    XCircle
} from "lucide-react";
import type { ReactNode } from "react";

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    inventoryActionLabels,
    inventoryDocumentTypeLabels,
    inventoryReasonCodeLabels,
    inventoryRoleLabels,
    inventoryStatusLabels
} from "../constants/inventory-management.constants";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";
import type {
    InventoryActionType,
    InventoryActivity,
    InventoryApprovalStep,
    InventoryBatchAllocation,
    InventoryBatchOption,
    InventoryLocationOption,
    InventoryManagementQuery,
    InventoryManagementRecord,
    InventoryRecordStatus,
    AppRole,
    InventorySkuOption
} from "../types/inventory-management.types";

interface CommonOption {
    id: string;
    label: string;
}

const statusTone: Record<InventoryRecordStatus, string> = {
    APPROVED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    BLOCKED: "border-red-200 bg-red-50 text-red-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    COMPLETED: "border-green-200 bg-green-50 text-green-700",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    IN_PROGRESS: "border-blue-200 bg-blue-50 text-blue-700",
    PENDING_APPROVAL: "border-amber-200 bg-amber-50 text-amber-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    WARNING: "border-orange-200 bg-orange-50 text-orange-700"
};

function statusIcon(status: InventoryRecordStatus) {
    if (status === "COMPLETED" || status === "APPROVED") {
        return <CheckCircle2 size={14} aria-hidden="true" />;
    }

    if (status === "WARNING" || status === "PENDING_APPROVAL") {
        return <AlertTriangle size={14} aria-hidden="true" />;
    }

    if (status === "BLOCKED" || status === "REJECTED") {
        return <XCircle size={14} aria-hidden="true" />;
    }

    return <Clock3 size={14} aria-hidden="true" />;
}

export function InventoryStatusBadge({
    status
}: {
    status: InventoryRecordStatus;
}) {
    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}
        >
            {statusIcon(status)}
            {inventoryStatusLabels[status]}
        </span>
    );
}

export function InventoryPageHeader({
    actions,
    description,
    eyebrow,
    title
}: {
    actions?: ReactNode;
    description: string;
    eyebrow?: string;
    title: string;
}) {
    return (
        <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div className="grid gap-1">
                {eyebrow ? (
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">
                        {eyebrow}
                    </p>
                ) : null}
                <h1 className="text-2xl font-black text-[#172317]">
                    {title}
                </h1>
                <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                    {description}
                </p>
            </div>
            {actions ? (
                <div className="flex flex-wrap items-center gap-2">
                    {actions}
                </div>
            ) : null}
        </header>
    );
}

export function InventorySummaryCard({
    icon,
    label,
    value
}: {
    icon: ReactNode;
    label: string;
    value: string;
}) {
    return (
        <article className="grid min-h-[94px] gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
                    {icon}
                </span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">
                {value}
            </strong>
        </article>
    );
}

export function InventoryFilterBar({
    locations,
    onChange,
    query
}: {
    locations: InventoryLocationOption[];
    onChange: (patch: Partial<InventoryManagementQuery>) => void;
    query: InventoryManagementQuery;
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-[minmax(240px,1fr)_180px_190px_170px]">
                <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                    Tìm kiếm
                    <input
                        aria-label="Tìm kiếm chứng từ kho"
                        className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                        onChange={(event) =>
                            onChange({
                                page: 0,
                                search: event.currentTarget.value
                            })
                        }
                        placeholder="Mã chứng từ, SKU, mặt hàng..."
                        type="search"
                        value={query.search}
                    />
                </label>

                <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                    Trạng thái
                    <select
                        aria-label="Lọc theo trạng thái"
                        className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                        onChange={(event) =>
                            onChange({
                                page: 0,
                                status: event.currentTarget
                                    .value as InventoryManagementQuery["status"]
                            })
                        }
                        value={query.status}
                    >
                        <option value="ALL">Tất cả</option>
                        {Object.entries(inventoryStatusLabels).map(
                            ([value, label]) => (
                                <option key={value} value={value}>
                                    {label}
                                </option>
                            )
                        )}
                    </select>
                </label>

                <LocationSelect
                    label="Kho"
                    locations={locations}
                    onChange={(locationId) =>
                        onChange({ locationId, page: 0 })
                    }
                    value={query.locationId}
                />

                <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                    Sắp xếp
                    <select
                        aria-label="Sắp xếp danh sách"
                        className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                        onChange={(event) =>
                            onChange({
                                page: 0,
                                sortBy: event.currentTarget
                                    .value as InventoryManagementQuery["sortBy"]
                            })
                        }
                        value={query.sortBy}
                    >
                        <option value="updatedAt">Cập nhật mới nhất</option>
                        <option value="code">Mã chứng từ</option>
                        <option value="quantity">Số lượng</option>
                        <option value="value">Giá trị</option>
                    </select>
                </label>
            </div>
        </section>
    );
}

export function InventoryDataTable({
    activeRecordId,
    onSortToggle,
    onView,
    records,
    sortDirection
}: {
    activeRecordId?: string;
    onSortToggle: () => void;
    onView: (record: InventoryManagementRecord) => void;
    records: InventoryManagementRecord[];
    sortDirection: "asc" | "desc";
}) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1120px] border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                    <tr>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            Chứng từ
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            Loại
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            Mặt hàng
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            Kho
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3 text-right">
                            Số lượng
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3 text-right">
                            Giá trị
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            Trạng thái
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3">
                            <button
                                className="inline-flex items-center gap-1 font-black"
                                onClick={onSortToggle}
                                type="button"
                            >
                                Cập nhật
                                <ArrowUpDown
                                    className={
                                        sortDirection === "asc"
                                            ? "rotate-180"
                                            : ""
                                    }
                                    size={14}
                                    aria-hidden="true"
                                />
                            </button>
                        </th>
                        <th className="border-b border-[#dce8df] px-4 py-3 text-right">
                            Thao tác
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {records.map((record) => (
                        <tr
                            className={
                                activeRecordId === String(record.id)
                                    ? "bg-[#f0fbef]"
                                    : "bg-white hover:bg-[#f8fbf8]"
                            }
                            key={String(record.id)}
                        >
                            <td className="border-b border-[#edf0f4] px-4 py-3">
                                <InventoryDocumentLink record={record} />
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 font-semibold text-[#536078]">
                                {
                                    inventoryDocumentTypeLabels[
                                        record.documentType
                                    ]
                                }
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3">
                                <div className="grid gap-1">
                                    <strong className="text-[#172317]">
                                        {record.itemName}
                                    </strong>
                                    <span className="text-xs font-bold text-[#697684]">
                                        {record.sku}
                                    </span>
                                </div>
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 font-semibold text-[#536078]">
                                {record.targetLocationName
                                    ? `${record.locationName} -> ${record.targetLocationName}`
                                    : record.locationName}
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 text-right font-black text-[#172317]">
                                {formatVietnamNumber(record.quantity)}{" "}
                                {record.unit}
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 text-right font-black text-[#172317]">
                                {formatVnd(record.value)}
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3">
                                <InventoryStatusBadge
                                    status={record.status}
                                />
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 font-semibold text-[#536078]">
                                {formatVietnamDateTime(record.updatedAt)}
                            </td>
                            <td className="border-b border-[#edf0f4] px-4 py-3 text-right">
                                <button
                                    className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#d8eadc] px-3 text-xs font-black text-[#159734] hover:bg-[#f4faf5]"
                                    onClick={() => onView(record)}
                                    type="button"
                                >
                                    <Eye size={15} aria-hidden="true" />
                                    Xem nhanh
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function InventoryEmptyState({
    action,
    message = "Không có dữ liệu phù hợp với bộ lọc hiện tại."
}: {
    action?: ReactNode;
    message?: string;
}) {
    return (
        <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
            <div className="grid max-w-sm gap-3 justify-items-center">
                <PackageOpen
                    className="text-[#159734]"
                    size={34}
                    aria-hidden="true"
                />
                <strong className="text-base font-black text-[#172317]">
                    Chưa có dữ liệu
                </strong>
                <p className="text-sm font-semibold leading-6 text-[#697684]">
                    {message}
                </p>
                {action}
            </div>
        </div>
    );
}

export function InventoryErrorState({
    message,
    onRetry
}: {
    message: string;
    onRetry: () => void;
}) {
    return (
        <div className="grid place-items-center rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center">
            <div className="grid max-w-sm gap-3 justify-items-center">
                <ShieldAlert
                    className="text-red-600"
                    size={34}
                    aria-hidden="true"
                />
                <strong className="text-base font-black text-red-800">
                    Không thể tải dữ liệu
                </strong>
                <p className="text-sm font-semibold leading-6 text-red-700">
                    {message}
                </p>
                <button
                    className="rounded-lg bg-red-700 px-4 py-2 text-sm font-black text-white"
                    onClick={onRetry}
                    type="button"
                >
                    Thử lại
                </button>
            </div>
        </div>
    );
}

export function InventoryLoadingSkeleton() {
    return (
        <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {Array.from({ length: 6 }, (_, index) => (
                <div
                    className="h-12 animate-pulse rounded-lg bg-[#eef3ef]"
                    key={index}
                />
            ))}
        </div>
    );
}

export function InventoryTimeline({
    activities
}: {
    activities: InventoryActivity[];
}) {
    return (
        <ol className="grid gap-3">
            {activities.map((activity) => (
                <li
                    className="grid grid-cols-[18px_1fr] gap-3"
                    key={activity.id}
                >
                    <span className="mt-1 size-2.5 rounded-full bg-[#159734]" />
                    <div className="grid gap-1">
                        <div className="flex flex-wrap items-center gap-2">
                            <strong className="text-sm font-black text-[#172317]">
                                {activity.title}
                            </strong>
                            <span className="text-xs font-bold text-[#697684]">
                                {formatVietnamDateTime(activity.at)}
                            </span>
                        </div>
                        <p className="text-xs font-semibold leading-5 text-[#697684]">
                            {activity.actor}: {activity.description}
                        </p>
                    </div>
                </li>
            ))}
        </ol>
    );
}

export function InventoryAuditPanel({
    record
}: {
    record: InventoryManagementRecord;
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
            <h3 className="text-sm font-black text-[#172317]">
                Nhật ký nghiệp vụ
            </h3>
            <InventoryTimeline activities={record.activities} />
        </section>
    );
}

export function InventoryApprovalPanel({
    steps
}: {
    steps: InventoryApprovalStep[];
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
            <h3 className="text-sm font-black text-[#172317]">
                Phê duyệt
            </h3>
            <div className="grid gap-2">
                {steps.map((step) => (
                    <div
                        className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] px-3 py-2"
                        key={step.id}
                    >
                        <div className="grid gap-0.5">
                            <strong className="text-sm font-black text-[#172317]">
                                {step.label}
                            </strong>
                            <span className="text-xs font-bold text-[#697684]">
                                {inventoryRoleLabels[step.role]}
                                {step.actor ? ` - ${step.actor}` : ""}
                            </span>
                        </div>
                        <span className="text-xs font-black text-[#159734]">
                            {step.status === "APPROVED"
                                ? "Đã duyệt"
                                : step.status === "REJECTED"
                                  ? "Từ chối"
                                  : "Đang chờ"}
                        </span>
                    </div>
                ))}
            </div>
        </section>
    );
}

export function InventoryDocumentLink({
    record
}: {
    record: InventoryManagementRecord;
}) {
    return (
        <button
            className="grid gap-1 text-left"
            type="button"
            aria-label={`Mở chứng từ ${record.code}`}
        >
            <span className="inline-flex items-center gap-2 font-black text-[#159734]">
                <FileText size={15} aria-hidden="true" />
                {record.code}
            </span>
            <span className="max-w-[240px] truncate text-xs font-semibold text-[#697684]">
                {record.title}
            </span>
        </button>
    );
}

export function BatchAllocationTable({
    allocations
}: {
    allocations: InventoryBatchAllocation[];
}) {
    if (allocations.length === 0) {
        return (
            <p className="rounded-lg bg-[#f7f8fb] px-3 py-3 text-sm font-semibold text-[#697684]">
                Chưa cấp phát batch.
            </p>
        );
    }

    return (
        <div className="overflow-x-auto rounded-lg border border-[#e8eef0]">
            <table className="w-full min-w-[460px] text-left text-sm">
                <thead className="bg-[#f7f8fb] text-xs font-black text-[#536078]">
                    <tr>
                        <th className="px-3 py-2">Batch</th>
                        <th className="px-3 py-2">Hạn dùng</th>
                        <th className="px-3 py-2 text-right">Số lượng</th>
                    </tr>
                </thead>
                <tbody>
                    {allocations.map((allocation) => (
                        <tr key={allocation.batchId}>
                            <td className="border-t border-[#edf0f4] px-3 py-2 font-black text-[#172317]">
                                {allocation.batchCode}
                            </td>
                            <td className="border-t border-[#edf0f4] px-3 py-2 font-semibold text-[#697684]">
                                {formatVietnamDateTime(
                                    allocation.expiryDate
                                )}
                            </td>
                            <td className="border-t border-[#edf0f4] px-3 py-2 text-right font-black text-[#172317]">
                                {formatVietnamNumber(allocation.quantity)}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function QuantityInput({
    label,
    onChange,
    value
}: {
    label: string;
    onChange: (value: number) => void;
    value: number;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                min={0}
                onChange={(event) =>
                    onChange(Number(event.currentTarget.value))
                }
                type="number"
                value={value}
            />
        </label>
    );
}

export function UnitOfMeasureSelect({
    onChange,
    value
}: {
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <SimpleSelect
            label="Đơn vị tính"
            onChange={onChange}
            options={[
                { id: "gói", label: "gói" },
                { id: "hộp", label: "hộp" },
                { id: "kg", label: "kg" },
                { id: "cái", label: "cái" }
            ]}
            value={value}
        />
    );
}

export function LocationSelect({
    label = "Kho xử lý",
    locations,
    onChange,
    value
}: {
    label?: string;
    locations: InventoryLocationOption[];
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <SimpleSelect
            label={label}
            onChange={onChange}
            options={[
                { id: "ALL", label: "Tất cả kho" },
                ...locations.map((location) => ({
                    id: location.id,
                    label: location.name
                }))
            ]}
            value={value}
        />
    );
}

export function SkuSelect({
    onChange,
    skus,
    value
}: {
    onChange: (value: string) => void;
    skus: InventorySkuOption[];
    value: string;
}) {
    return (
        <SimpleSelect
            label="Mặt hàng"
            onChange={onChange}
            options={skus.map((sku) => ({
                id: sku.id,
                label: `${sku.sku} - ${sku.name}`
            }))}
            value={value}
        />
    );
}

export function BatchSelect({
    batches,
    onChange,
    value
}: {
    batches: InventoryBatchOption[];
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <SimpleSelect
            label="Batch"
            onChange={onChange}
            options={batches.map((batch) => ({
                id: batch.id,
                label: `${batch.code} - còn ${formatVietnamNumber(
                    batch.availableQuantity
                )}`
            }))}
            value={value}
        />
    );
}

export function ReasonCodeSelect({
    onChange,
    value
}: {
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <SimpleSelect
            label="Lý do"
            onChange={onChange}
            options={Object.entries(inventoryReasonCodeLabels).map(
                ([id, label]) => ({ id, label })
            )}
            value={value}
        />
    );
}

export function EvidenceUpload({
    onDirty
}: {
    onDirty: () => void;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            Bằng chứng
            <span className="flex min-h-20 items-center justify-center gap-2 rounded-lg border border-dashed border-[#cfdad2] bg-[#f7f8fb] px-3 text-sm font-bold text-[#697684]">
                <Upload size={16} aria-hidden="true" />
                Tải ảnh hoặc biên bản kiểm tra
            </span>
            <input
                aria-label="Tải bằng chứng"
                className="sr-only"
                onChange={onDirty}
                type="file"
            />
        </label>
    );
}

export function ConfirmInventoryActionDialog({
    action,
    loading,
    onCancel,
    onConfirm,
    open,
    record
}: {
    action: InventoryActionType;
    loading: boolean;
    onCancel: () => void;
    onConfirm: () => void;
    open: boolean;
    record: InventoryManagementRecord | null;
}) {
    return (
        <Dialog onOpenChange={(nextOpen) => !nextOpen && onCancel()} open={open}>
            <DialogContent className="max-w-[520px]">
                <DialogHeader>
                    <DialogTitle>Xác nhận thao tác kho</DialogTitle>
                    <DialogDescription>
                        Hành động này sẽ ghi nhận nhật ký và thay đổi trạng
                        thái chứng từ.
                    </DialogDescription>
                </DialogHeader>
                <div className="grid gap-2 px-6 py-5 text-sm font-semibold text-[#536078]">
                    <p>
                        Thao tác:{" "}
                        <strong className="text-[#172317]">
                            {inventoryActionLabels[action]}
                        </strong>
                    </p>
                    <p>
                        Chứng từ:{" "}
                        <strong className="text-[#172317]">
                            {record?.code ?? "-"}
                        </strong>
                    </p>
                </div>
                <DialogFooter>
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                        disabled={loading}
                        onClick={onCancel}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={loading}
                        onClick={onConfirm}
                        type="button"
                    >
                        {loading ? "Đang xử lý..." : "Xác nhận"}
                    </button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export function RoleRestrictedAction({
    allowedRoles,
    children,
    currentRole
}: {
    allowedRoles: AppRole[];
    children: ReactNode;
    currentRole: AppRole;
}) {
    const allowed = allowedRoles.includes(currentRole);

    if (allowed) {
        return <>{children}</>;
    }

    return (
        <span className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 text-xs font-black text-[#697684]">
            <Lock size={14} aria-hidden="true" />
            Không khả dụng với {inventoryRoleLabels[currentRole]}
        </span>
    );
}

export function InventoryReferenceLink({
    code
}: {
    code?: string;
}) {
    return (
        <span className="inline-flex items-center gap-1.5 rounded-lg bg-[#f7f8fb] px-2 py-1 text-xs font-black text-[#536078]">
            <FileText size={13} aria-hidden="true" />
            {code ?? "Không có tham chiếu"}
        </span>
    );
}

function SimpleSelect({
    label,
    onChange,
    options,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    options: CommonOption[];
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                onChange={(event) => onChange(event.currentTarget.value)}
                value={value}
            >
                {options.map((option) => (
                    <option key={option.id} value={option.id}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}
