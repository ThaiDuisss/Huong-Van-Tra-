import {
    AlertTriangle,
    ArrowLeft,
    CheckCircle2,
    ChevronDown,
    Clock3,
    Eye,
    FileText,
    Lock,
    RotateCcw,
    XCircle
} from "lucide-react";
import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    stockBatchStatusLabels,
    stockDocumentActionLabels,
    stockDocumentDirectionLabels,
    stockDocumentLineStatusLabels,
    stockDocumentRoleLabels,
    stockDocumentStatusLabels,
    stockDocumentStatusOptions,
    stockDocumentTypeLabels,
    stockDocumentTypeOptions
} from "../constants/stock-document.constants";
import type {
    InventoryLocationSummary,
    StockDocumentAction,
    StockDocumentDetail,
    StockDocumentLine,
    StockDocumentListQuery,
    StockDocumentRole,
    StockDocumentStatus,
    StockDocumentSummary,
    UserSnapshot
} from "../types/stock-document.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const statusTone: Record<StockDocumentStatus, string> = {
    APPROVED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    POSTED: "border-green-200 bg-green-50 text-green-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    REVERSED: "border-purple-200 bg-purple-50 text-purple-700",
    SUBMITTED: "border-amber-200 bg-amber-50 text-amber-700"
};

function statusIcon(status: StockDocumentStatus) {
    if (status === "POSTED" || status === "APPROVED") {
        return <CheckCircle2 size={14} aria-hidden="true" />;
    }

    if (status === "REJECTED" || status === "CANCELLED") {
        return <XCircle size={14} aria-hidden="true" />;
    }

    if (status === "REVERSED") {
        return <RotateCcw size={14} aria-hidden="true" />;
    }

    return <Clock3 size={14} aria-hidden="true" />;
}

export function StockDocumentStatusBadge({
    status
}: {
    status: StockDocumentStatus;
}) {
    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}
        >
            {statusIcon(status)}
            {stockDocumentStatusLabels[status]}
        </span>
    );
}

export function StockDocumentTypeBadge({
    type
}: {
    type: StockDocumentSummary["type"];
}) {
    return (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-[#d8eadc] bg-[#f4faf5] px-2.5 py-1 text-xs font-black text-[#159734]">
            <FileText size={14} aria-hidden="true" />
            {stockDocumentTypeLabels[type]}
        </span>
    );
}

export function StockDocumentSummaryCard({
    icon,
    label,
    value
}: {
    icon: ReactNode;
    label: string;
    value: number;
}) {
    return (
        <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
                    {icon}
                </span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">
                {formatVietnamNumber(value)}
            </strong>
        </article>
    );
}

export function StockDocumentCreateDropdown({
    links
}: {
    links: Array<{ label: string; to: string }>;
}) {
    return (
        <div className="group relative">
            <button
                className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card"
                type="button"
            >
                Tạo nghiệp vụ kho
                <ChevronDown size={16} aria-hidden="true" />
            </button>
            <div className="invisible absolute right-0 top-11 z-30 grid min-w-[220px] gap-1 rounded-xl border border-[#e8eef0] bg-white p-2 opacity-0 shadow-pos-panel transition group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100">
                {links.map((link) => (
                    <Link
                        className="rounded-lg px-3 py-2 text-sm font-bold text-[#172317] hover:bg-[#f4faf5] hover:text-[#159734]"
                        key={link.to}
                        to={link.to}
                    >
                        {link.label}
                    </Link>
                ))}
            </div>
        </div>
    );
}

export function StockDocumentFilterBar({
    locations,
    onChange,
    onReset,
    query,
    users
}: {
    locations: InventoryLocationSummary[];
    onChange: (patch: Partial<StockDocumentListQuery>) => void;
    onReset: () => void;
    query: StockDocumentListQuery;
    users: UserSnapshot[];
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextFilter
                    label="Mã chứng từ"
                    onChange={(documentCode) =>
                        onChange({ documentCode, page: 0 })
                    }
                    placeholder="INV-2026..."
                    value={query.documentCode}
                />
                <TextFilter
                    label="Mã tham chiếu"
                    onChange={(referenceCode) =>
                        onChange({ page: 0, referenceCode })
                    }
                    placeholder="REF-..."
                    value={query.referenceCode}
                />
                <SelectFilter
                    label="Loại chứng từ"
                    onChange={(type) =>
                        onChange({
                            page: 0,
                            type: type as StockDocumentListQuery["type"]
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...stockDocumentTypeOptions.map((type) => ({
                            label: stockDocumentTypeLabels[type],
                            value: type
                        }))
                    ]}
                    value={query.type}
                />
                <SelectFilter
                    label="Nhóm nhập/xuất"
                    onChange={(direction) =>
                        onChange({
                            direction:
                                direction as StockDocumentListQuery["direction"],
                            page: 0
                        })
                    }
                    options={Object.entries(stockDocumentDirectionLabels).map(
                        ([value, label]) => ({ label, value })
                    )}
                    value={query.direction}
                />
                <SelectFilter
                    label="Trạng thái"
                    onChange={(status) =>
                        onChange({
                            page: 0,
                            status:
                                status as StockDocumentListQuery["status"]
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...stockDocumentStatusOptions.map((status) => ({
                            label: stockDocumentStatusLabels[status],
                            value: status
                        }))
                    ]}
                    value={query.status}
                />
                <LocationFilter
                    label="Kho/chi nhánh"
                    locations={locations}
                    onChange={(locationId) =>
                        onChange({ locationId, page: 0 })
                    }
                    value={query.locationId}
                />
                <LocationFilter
                    label="Kho nguồn"
                    locations={locations}
                    onChange={(sourceLocationId) =>
                        onChange({ page: 0, sourceLocationId })
                    }
                    value={query.sourceLocationId}
                />
                <LocationFilter
                    label="Kho đích"
                    locations={locations}
                    onChange={(destinationLocationId) =>
                        onChange({ destinationLocationId, page: 0 })
                    }
                    value={query.destinationLocationId}
                />
                <SelectFilter
                    label="Người tạo"
                    onChange={(createdById) =>
                        onChange({ createdById, page: 0 })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...users.map((user) => ({
                            label: user.name,
                            value: user.id
                        }))
                    ]}
                    value={query.createdById}
                />
                <TextFilter
                    label="Ngày tạo từ"
                    onChange={(createdFrom) =>
                        onChange({ createdFrom, page: 0 })
                    }
                    type="date"
                    value={query.createdFrom}
                />
                <TextFilter
                    label="Ngày tạo đến"
                    onChange={(createdTo) =>
                        onChange({ createdTo, page: 0 })
                    }
                    type="date"
                    value={query.createdTo}
                />
                <SelectFilter
                    label="Có lô hàng"
                    onChange={(hasBatch) =>
                        onChange({
                            hasBatch:
                                hasBatch as StockDocumentListQuery["hasBatch"],
                            page: 0
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        { label: "Có lô", value: "YES" },
                        { label: "Không có lô", value: "NO" }
                    ]}
                    value={query.hasBatch}
                />
                <TextFilter
                    label="Ngày post từ"
                    onChange={(postedFrom) =>
                        onChange({ page: 0, postedFrom })
                    }
                    type="date"
                    value={query.postedFrom}
                />
                <TextFilter
                    label="Ngày post đến"
                    onChange={(postedTo) =>
                        onChange({ page: 0, postedTo })
                    }
                    type="date"
                    value={query.postedTo}
                />
                <div className="flex items-end">
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] hover:bg-[#f7f8fb]"
                        onClick={onReset}
                        type="button"
                    >
                        Xóa bộ lọc
                    </button>
                </div>
            </div>
        </section>
    );
}

export function StockDocumentTable({
    documents
}: {
    documents: StockDocumentSummary[];
}) {
    const navigate = useNavigate();

    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1320px] border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                    <tr>
                        <TableHead>Mã chứng từ</TableHead>
                        <TableHead>Loại chứng từ</TableHead>
                        <TableHead>Kho nguồn</TableHead>
                        <TableHead>Kho đích</TableHead>
                        <TableHead>Tham chiếu nghiệp vụ</TableHead>
                        <TableHead align="right">Tổng dòng</TableHead>
                        <TableHead align="right">Tổng số lượng</TableHead>
                        <TableHead>Trạng thái</TableHead>
                        <TableHead>Người tạo</TableHead>
                        <TableHead>Ngày tạo</TableHead>
                        <TableHead>Ngày post</TableHead>
                        <TableHead align="right">Thao tác</TableHead>
                    </tr>
                </thead>
                <tbody>
                    {documents.map((document) => (
                        <tr
                            className="cursor-pointer bg-white hover:bg-[#f8fbf8]"
                            key={document.id}
                            onClick={() =>
                                navigate(
                                    `/management/inventory/documents/${document.id}`
                                )
                            }
                        >
                            <TableCell>
                                <Link
                                    className="font-black text-[#159734] hover:underline"
                                    onClick={(event) => event.stopPropagation()}
                                    to={`/management/inventory/documents/${document.id}`}
                                >
                                    {document.code}
                                </Link>
                            </TableCell>
                            <TableCell>
                                <StockDocumentTypeBadge
                                    type={document.type}
                                />
                            </TableCell>
                            <TableCell>
                                {document.sourceLocation?.name ?? "-"}
                            </TableCell>
                            <TableCell>
                                {document.destinationLocation?.name ?? "-"}
                            </TableCell>
                            <TableCell>
                                <div className="grid gap-0.5">
                                    <strong className="text-[#172317]">
                                        {document.referenceCode ?? "-"}
                                    </strong>
                                    <span className="text-xs font-semibold text-[#697684]">
                                        {document.referenceType ?? "-"}
                                    </span>
                                </div>
                            </TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(document.totalLines)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(document.totalQuantity)}
                            </TableCell>
                            <TableCell>
                                <StockDocumentStatusBadge
                                    status={document.status}
                                />
                            </TableCell>
                            <TableCell>{document.createdBy.name}</TableCell>
                            <TableCell>
                                {formatVietnamDateTime(document.createdAt)}
                            </TableCell>
                            <TableCell>
                                {document.postedAt
                                    ? formatVietnamDateTime(document.postedAt)
                                    : "-"}
                            </TableCell>
                            <TableCell align="right">
                                <Link
                                    className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#d8eadc] px-3 text-xs font-black text-[#159734] hover:bg-[#f4faf5]"
                                    onClick={(event) => event.stopPropagation()}
                                    to={`/management/inventory/documents/${document.id}`}
                                >
                                    <Eye size={15} aria-hidden="true" />
                                    Xem nhanh
                                </Link>
                            </TableCell>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function StockDocumentRoleSwitch({
    onChange,
    value
}: {
    onChange: (role: StockDocumentRole) => void;
    value: StockDocumentRole;
}) {
    return (
        <label className="grid gap-1 text-xs font-black text-[#536078]">
            Vai trò mô phỏng
            <select
                aria-label="Vai trò mô phỏng"
                className="h-10 min-w-[210px] rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-black text-[#172317] outline-none focus:border-[#159734]"
                onChange={(event) =>
                    onChange(event.currentTarget.value as StockDocumentRole)
                }
                value={value}
            >
                {Object.entries(stockDocumentRoleLabels).map(
                    ([role, label]) => (
                        <option key={role} value={role}>
                            {label}
                        </option>
                    )
                )}
            </select>
        </label>
    );
}

export function StockDocumentActionDialog({
    action,
    loading,
    note,
    onCancel,
    onConfirm,
    onNoteChange,
    onReasonChange,
    open,
    reason
}: {
    action: StockDocumentAction | null;
    loading: boolean;
    note: string;
    onCancel: () => void;
    onConfirm: () => void;
    onNoteChange: (value: string) => void;
    onReasonChange: (value: string) => void;
    open: boolean;
    reason: string;
}) {
    const requiresReason = action === "REJECT" || action === "REVERSE";
    const isPost = action === "POST";

    return (
        <Dialog onOpenChange={(nextOpen) => !nextOpen && onCancel()} open={open}>
            <DialogContent className="max-w-[560px]">
                <DialogHeader>
                    <DialogTitle>
                        {action ? stockDocumentActionLabels[action] : "Thao tác"}{" "}
                        chứng từ
                    </DialogTitle>
                    <DialogDescription>
                        {isPost
                            ? "Post sẽ thay đổi tồn kho và chứng từ không thể sửa trực tiếp sau đó."
                            : action === "REVERSE"
                              ? "Đảo chứng từ sẽ tạo chứng từ mới, không thay đổi số liệu chứng từ cũ."
                              : "Xác nhận thao tác và ghi nhận audit."}
                    </DialogDescription>
                </DialogHeader>
                <div className="grid gap-4 px-6 py-5">
                    {action === "REVERSE" ? (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-bold text-amber-800">
                            Ảnh hưởng dự kiến: tạo chứng từ đảo với số lượng
                            ngược chiều, giữ nguyên chứng từ gốc để audit.
                        </div>
                    ) : null}
                    <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                        Ghi chú
                        <textarea
                            className="min-h-20 rounded-lg border border-pos-border px-3 py-2 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                            onChange={(event) =>
                                onNoteChange(event.currentTarget.value)
                            }
                            value={note}
                        />
                    </label>
                    {requiresReason ? (
                        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                            Lý do bắt buộc
                            <textarea
                                aria-required="true"
                                className="min-h-20 rounded-lg border border-pos-border px-3 py-2 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                                onChange={(event) =>
                                    onReasonChange(event.currentTarget.value)
                                }
                                value={reason}
                            />
                        </label>
                    ) : null}
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
                        disabled={
                            loading || (requiresReason && !reason.trim())
                        }
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

export function StockDocumentLinesTable({
    canViewCost,
    lines
}: {
    canViewCost: boolean;
    lines: StockDocumentLine[];
}) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white">
            <table className="w-full min-w-[1120px] text-left text-sm">
                <thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]">
                    <tr>
                        <TableHead>SKU</TableHead>
                        <TableHead>Tên sản phẩm/nguyên liệu</TableHead>
                        <TableHead>Loại hàng</TableHead>
                        <TableHead>UOM</TableHead>
                        <TableHead align="right">Số lượng</TableHead>
                        {canViewCost ? (
                            <>
                                <TableHead align="right">Đơn giá</TableHead>
                                <TableHead align="right">Thành tiền</TableHead>
                            </>
                        ) : null}
                        <TableHead align="right">Trước</TableHead>
                        <TableHead align="right">Sau</TableHead>
                        <TableHead>Location</TableHead>
                        <TableHead>Trạng thái dòng</TableHead>
                    </tr>
                </thead>
                <tbody>
                    {lines.map((lineItem) => (
                        <tr key={lineItem.id}>
                            <TableCell strong>{lineItem.skuCode}</TableCell>
                            <TableCell>{lineItem.skuName}</TableCell>
                            <TableCell>{lineItem.itemType}</TableCell>
                            <TableCell>{lineItem.unit}</TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(lineItem.quantity)}
                            </TableCell>
                            {canViewCost ? (
                                <>
                                    <TableCell align="right">
                                        {lineItem.unitCost
                                            ? formatVnd(lineItem.unitCost)
                                            : "-"}
                                    </TableCell>
                                    <TableCell align="right">
                                        {lineItem.unitCost
                                            ? formatVnd(
                                                  lineItem.unitCost *
                                                      lineItem.quantity
                                              )
                                            : "-"}
                                    </TableCell>
                                </>
                            ) : null}
                            <TableCell align="right">
                                {lineItem.quantityBefore ?? "-"}
                            </TableCell>
                            <TableCell align="right">
                                {lineItem.quantityAfter ?? "-"}
                            </TableCell>
                            <TableCell>
                                {lineItem.location?.name ?? "-"}
                            </TableCell>
                            <TableCell>
                                {
                                    stockDocumentLineStatusLabels[
                                        lineItem.status
                                    ]
                                }
                            </TableCell>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function StockDocumentBatchTable({
    lines
}: {
    lines: StockDocumentLine[];
}) {
    const allocations = lines.flatMap((lineItem) =>
        lineItem.allocations.map((allocation) => ({
            ...allocation,
            skuCode: lineItem.skuCode
        }))
    );

    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white">
            <table className="w-full min-w-[1040px] text-left text-sm">
                <thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]">
                    <tr>
                        <TableHead>SKU</TableHead>
                        <TableHead>Batch code</TableHead>
                        <TableHead>Supplier lot</TableHead>
                        <TableHead>Ngày sản xuất</TableHead>
                        <TableHead>Hạn sử dụng</TableHead>
                        <TableHead align="right">Số lượng phân bổ</TableHead>
                        <TableHead align="right">Lô trước</TableHead>
                        <TableHead align="right">Lô sau</TableHead>
                        <TableHead>Trạng thái batch</TableHead>
                        <TableHead>Chi tiết batch</TableHead>
                    </tr>
                </thead>
                <tbody>
                    {allocations.map((allocation) => (
                        <tr key={allocation.id}>
                            <TableCell strong>{allocation.skuCode}</TableCell>
                            <TableCell>{allocation.batchCode}</TableCell>
                            <TableCell>{allocation.supplierLot ?? "-"}</TableCell>
                            <TableCell>
                                {allocation.manufacturedAt
                                    ? formatVietnamDateTime(
                                          allocation.manufacturedAt
                                      )
                                    : "-"}
                            </TableCell>
                            <TableCell>
                                {allocation.expiryDate
                                    ? formatVietnamDateTime(
                                          allocation.expiryDate
                                      )
                                    : "-"}
                            </TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(
                                    allocation.allocatedQuantity
                                )}
                            </TableCell>
                            <TableCell align="right">
                                {allocation.batchQuantityBefore ?? "-"}
                            </TableCell>
                            <TableCell align="right">
                                {allocation.batchQuantityAfter ?? "-"}
                            </TableCell>
                            <TableCell>
                                {stockBatchStatusLabels[allocation.status]}
                            </TableCell>
                            <TableCell>
                                <span className="text-xs font-bold text-[#697684]">
                                    Chưa có route batch
                                </span>
                            </TableCell>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function StockDocumentAuditTimeline({
    document
}: {
    document: StockDocumentDetail;
}) {
    return (
        <ol className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
            {document.auditEvents.map((event) => (
                <li className="grid grid-cols-[18px_1fr] gap-3" key={event.id}>
                    <span className="mt-1 size-2.5 rounded-full bg-[#159734]" />
                    <div className="grid gap-1">
                        <div className="flex flex-wrap items-center gap-2">
                            <strong className="text-sm font-black text-[#172317]">
                                {event.type}
                            </strong>
                            <span className="text-xs font-bold text-[#697684]">
                                {formatVietnamDateTime(event.happenedAt)}
                            </span>
                        </div>
                        <p className="text-sm font-semibold text-[#536078]">
                            {event.actor.name} -{" "}
                            {stockDocumentRoleLabels[event.roleSnapshot]}
                        </p>
                        {event.note ? (
                            <p className="text-sm font-semibold text-[#697684]">
                                Ghi chú: {event.note}
                            </p>
                        ) : null}
                        {event.reason ? (
                            <p className="text-sm font-bold text-red-700">
                                Lý do: {event.reason}
                            </p>
                        ) : null}
                    </div>
                </li>
            ))}
        </ol>
    );
}

export function RoleUnavailable({
    currentRole
}: {
    currentRole: StockDocumentRole;
}) {
    return (
        <span className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 text-xs font-black text-[#697684]">
            <Lock size={14} aria-hidden="true" />
            Không khả dụng với {stockDocumentRoleLabels[currentRole]}
        </span>
    );
}

export function StockDocumentBackLink() {
    return (
        <Link
            className="inline-flex items-center gap-2 text-sm font-black text-[#159734] hover:underline"
            to="/management/inventory/documents"
        >
            <ArrowLeft size={16} aria-hidden="true" />
            Quay lại danh sách
        </Link>
    );
}

export function StockDocumentReadonlyAlert() {
    return (
        <div className="inline-flex items-center gap-2 rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 py-2 text-sm font-bold text-[#697684]">
            <AlertTriangle size={16} aria-hidden="true" />
            Chứng từ chỉ đọc ở trạng thái hiện tại.
        </div>
    );
}

function TextFilter({
    label,
    onChange,
    placeholder,
    type = "text",
    value
}: {
    label: string;
    onChange: (value: string) => void;
    placeholder?: string;
    type?: string;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                onChange={(event) => onChange(event.currentTarget.value)}
                placeholder={placeholder}
                type={type}
                value={value}
            />
        </label>
    );
}

function SelectFilter({
    label,
    onChange,
    options,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    options: Array<{ label: string; value: string }>;
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
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}

function LocationFilter({
    label,
    locations,
    onChange,
    value
}: {
    label: string;
    locations: InventoryLocationSummary[];
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <SelectFilter
            label={label}
            onChange={onChange}
            options={[
                { label: "Tất cả", value: "ALL" },
                ...locations.map((location) => ({
                    label: location.name,
                    value: location.id
                }))
            ]}
            value={value}
        />
    );
}

function TableHead({
    align = "left",
    children
}: {
    align?: "left" | "right";
    children: ReactNode;
}) {
    return (
        <th
            className={`border-b border-[#dce8df] px-4 py-3 ${
                align === "right" ? "text-right" : "text-left"
            }`}
        >
            {children}
        </th>
    );
}

function TableCell({
    align = "left",
    children,
    strong = false
}: {
    align?: "left" | "right";
    children: ReactNode;
    strong?: boolean;
}) {
    return (
        <td
            className={`border-b border-[#edf0f4] px-4 py-3 ${
                align === "right" ? "text-right" : "text-left"
            } ${strong ? "font-black text-[#172317]" : "font-semibold text-[#536078]"}`}
        >
            {children}
        </td>
    );
}
