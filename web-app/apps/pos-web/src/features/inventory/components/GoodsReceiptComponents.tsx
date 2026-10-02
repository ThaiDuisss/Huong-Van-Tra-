import {
    AlertTriangle,
    CheckCircle2,
    Clock3,
    Eye,
    FileText,
    Lock,
    PackageCheck,
    XCircle
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown } from "../../manufacturing/components/shared/useLookupDropdown";
import { supplierApi } from "../../manufacturing/services/supplier.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";

import {
    costStatusLabels,
    costStatusOptions,
    goodsReceiptRoleLabels,
    goodsReceiptStatusLabels,
    goodsReceiptStatusOptions,
    qcResultLabels,
    qcResultOptions
} from "../constants/goods-receipt.constants";
import type {
    CostStatus,
    GoodsReceiptDetail,
    GoodsReceiptListQuery,
    AppRole,
    GoodsReceiptStatus,
    GoodsReceiptSummary,
    QcResult
} from "../types/goods-receipt.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const statusTone: Record<GoodsReceiptStatus, string> = {
    DRAFT: "border-slate-200 bg-slate-50 text-slate-700",
    PENDING_APPROVAL: "border-amber-200 bg-amber-50 text-amber-700",
    APPROVED: "border-blue-200 bg-blue-50 text-blue-700",
    REJECTED: "border-red-200 bg-red-50 text-red-700",
    RECEIVING: "border-cyan-200 bg-cyan-50 text-cyan-700",
    QC_PENDING: "border-purple-200 bg-purple-50 text-purple-700",
    QC_COMPLETED: "border-indigo-200 bg-indigo-50 text-indigo-700",
    POSTED: "border-green-200 bg-green-50 text-green-700",
    CANCELLED: "border-slate-200 bg-slate-50 text-slate-600",
    AWAITING_COST: "border-blue-200 bg-blue-50 text-blue-700",
    AWAITING_QC: "border-amber-200 bg-amber-50 text-amber-700",
    PARTIALLY_PASSED: "border-orange-200 bg-orange-50 text-orange-700",
    READY_TO_POST: "border-emerald-200 bg-emerald-50 text-emerald-700",
    SUBMITTED: "border-amber-200 bg-amber-50 text-amber-700"
};

function statusIcon(status: GoodsReceiptStatus) {
    if (status === "POSTED" || status === "READY_TO_POST") {
        return <CheckCircle2 size={14} aria-hidden="true" />;
    }

    if (status === "REJECTED" || status === "CANCELLED") {
        return <XCircle size={14} aria-hidden="true" />;
    }

    if (status === "PARTIALLY_PASSED") {
        return <AlertTriangle size={14} aria-hidden="true" />;
    }

    return <Clock3 size={14} aria-hidden="true" />;
}

export function GoodsReceiptStatusBadge({
    status
}: {
    status: GoodsReceiptStatus;
}) {
    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${statusTone[status]}`}
        >
            {statusIcon(status)}
            {goodsReceiptStatusLabels[status]}
        </span>
    );
}

export function QcBadge({ result }: { result: QcResult }) {
    return (
        <span className="inline-flex rounded-full bg-[#f7f8fb] px-2.5 py-1 text-xs font-black text-[#536078]">
            {qcResultLabels[result]}
        </span>
    );
}

export function CostBadge({ status }: { status: CostStatus }) {
    return (
        <span className="inline-flex rounded-full bg-[#edf8ef] px-2.5 py-1 text-xs font-black text-[#159734]">
            {costStatusLabels[status]}
        </span>
    );
}

export function GoodsReceiptSummaryCard({
    active = false,
    icon,
    label,
    onClick,
    value
}: {
    active?: boolean;
    icon: ReactNode;
    label: string;
    onClick?: () => void;
    value: number;
}) {
    return (
        <article
            className={`grid min-h-[90px] gap-2 rounded-xl border p-4 shadow-pos-card transition-all ${active
                ? "border-[#159734] bg-[#f4faf5] ring-2 ring-[#159734]/20"
                : "border-[#e8eef0] bg-white hover:border-[#159734]/40"
                } ${onClick ? "cursor-pointer" : ""}`}
            onClick={onClick}
        >
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

/**
 * Thanh tiến trình vòng đời (Lifecycle Stepper) trực quan
 */
export function GoodsReceiptLifecycleStepper({
    status
}: {
    status: GoodsReceiptStatus;
}) {
    const steps: Array<{ key: GoodsReceiptStatus; label: string; sub: string }> = [
        { key: "DRAFT", label: "1. Bản nháp", sub: "Lập danh sách đặt" },
        { key: "PENDING_APPROVAL", label: "2. Chờ duyệt", sub: "Quản lý xem xét" },
        { key: "APPROVED", label: "3. Đã duyệt", sub: "Chờ NCC giao" },
        { key: "RECEIVING", label: "4. Tiếp nhận", sub: "Kiểm đếm thực tế" },
        { key: "QC_PENDING", label: "5. Kiểm định QC", sub: "Kiểm tra & Tạo lô" },
        { key: "QC_COMPLETED", label: "6. Chờ ghi sổ", sub: "Sẵn sàng nhập kho" },
        { key: "POSTED", label: "7. Đã nhập kho", sub: "Tồn kho đã tăng" }
    ];

    const orderMap: Record<string, number> = {
        DRAFT: 0,
        PENDING_APPROVAL: 1,
        APPROVED: 2,
        RECEIVING: 3,
        QC_PENDING: 4,
        AWAITING_QC: 4,
        PARTIALLY_PASSED: 4,
        QC_COMPLETED: 5,
        READY_TO_POST: 5,
        POSTED: 6
    };

    const isRejected = status === "REJECTED";
    const isCancelled = status === "CANCELLED";
    const currentStepIndex = orderMap[status] ?? 0;

    if (isRejected || isCancelled) {
        return (
            <div className={`rounded-xl border p-4 ${isRejected ? "border-red-200 bg-red-50 text-red-800" : "border-slate-200 bg-slate-50 text-slate-700"}`}>
                <div className="flex items-center gap-2 font-black text-base">
                    <XCircle size={18} />
                    Phiếu nhập ở trạng thái: {goodsReceiptStatusLabels[status]}
                </div>
                <p className="mt-1 text-sm font-semibold">
                    {isRejected
                        ? "Phiếu nhập đã bị từ chối phê duyệt và không thể tiếp tục quy trình nhập kho."
                        : "Phiếu nhập đã bị hủy trước khi hoàn tất ghi sổ."}
                </p>
            </div>
        );
    }

    return (
        <div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="mb-3 text-xs font-black uppercase tracking-wider text-[#697684]">
                Tiến trình vòng đời phiếu nhập (Workflow Lifecycle)
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
                {steps.map((step, idx) => {
                    const isPassed = idx < currentStepIndex;
                    const isCurrent = idx === currentStepIndex;

                    return (
                        <div
                            key={step.key}
                            className={`flex flex-col justify-between rounded-lg border p-2.5 transition-all ${isCurrent
                                ? "border-[#159734] bg-[#f4faf5] text-[#159734] ring-2 ring-[#159734]/20"
                                : isPassed
                                    ? "border-[#d8eadc] bg-[#fafdfa] text-[#315f3c]"
                                    : "border-[#eef2f0] bg-slate-50 text-[#8c9ba5]"
                                }`}
                        >
                            <div className="flex items-center justify-between">
                                <span className={`text-xs font-black ${isCurrent ? "text-[#159734]" : isPassed ? "text-[#315f3c]" : "text-[#697684]"}`}>
                                    {step.label}
                                </span>
                                {isPassed ? (
                                    <CheckCircle2 size={14} className="text-[#159734]" />
                                ) : isCurrent ? (
                                    <span className="size-2 rounded-full bg-[#159734] animate-pulse" />
                                ) : null}
                            </div>
                            <span className="mt-1 text-[11px] font-semibold text-[#697684] line-clamp-1">
                                {step.sub}
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export function GoodsReceiptFilterBar({
    onChange,
    onReset,
    query
}: {
    onChange: (patch: Partial<GoodsReceiptListQuery>) => void;
    onReset: () => void;
    query: GoodsReceiptListQuery;
}) {
    // Dropdown NCC & kho dùng lookup phân trang (chỉ id + name)
    const supplierDropdown = useLookupDropdown(supplierApi.lookup, {
        selectedIds: [query.supplierId],
        fetchByIds: supplierApi.lookupByIds
    });
    const locationDropdown = useLookupDropdown(inventoryLocationApi.lookupLocations, {
        selectedIds: [query.receivingLocationId],
        fetchByIds: inventoryLocationApi.lookupLocationsByIds
    });

    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextFilter
                    label="Mã phiếu nhập"
                    onChange={(receiptCode) =>
                        onChange({ page: 0, receiptCode })
                    }
                    value={query.receiptCode}
                />
                <LookupFilter
                    label="Nhà cung cấp"
                    dropdown={supplierDropdown}
                    onChange={(supplierId) =>
                        onChange({ page: 0, supplierId })
                    }
                    placeholder="Tìm nhà cung cấp..."
                    value={query.supplierId}
                />
                <TextFilter
                    label="Purchase Order"
                    onChange={(purchaseOrderCode) =>
                        onChange({ page: 0, purchaseOrderCode })
                    }
                    value={query.purchaseOrderCode}
                />
                <SelectFilter
                    label="Trạng thái receipt"
                    onChange={(status) =>
                        onChange({
                            page: 0,
                            status: status as GoodsReceiptListQuery["status"]
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...goodsReceiptStatusOptions.map((status) => ({
                            label: goodsReceiptStatusLabels[status],
                            value: status
                        }))
                    ]}
                    value={query.status}
                />
                <SelectFilter
                    label="Kết quả QC"
                    onChange={(qcResult) =>
                        onChange({
                            page: 0,
                            qcResult:
                                qcResult as GoodsReceiptListQuery["qcResult"]
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...qcResultOptions.map((result) => ({
                            label: qcResultLabels[result],
                            value: result
                        }))
                    ]}
                    value={query.qcResult}
                />
                <SelectFilter
                    label="Trạng thái giá"
                    onChange={(costStatus) =>
                        onChange({
                            costStatus:
                                costStatus as GoodsReceiptListQuery["costStatus"],
                            page: 0
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        ...costStatusOptions.map((status) => ({
                            label: costStatusLabels[status],
                            value: status
                        }))
                    ]}
                    value={query.costStatus}
                />
                <LookupFilter
                    label="Kho nhận"
                    dropdown={locationDropdown}
                    onChange={(receivingLocationId) =>
                        onChange({ page: 0, receivingLocationId })
                    }
                    placeholder="Tìm kho nhận..."
                    value={query.receivingLocationId}
                />
                <SelectFilter
                    label="Có chênh lệch"
                    onChange={(hasVariance) =>
                        onChange({
                            hasVariance:
                                hasVariance as GoodsReceiptListQuery["hasVariance"],
                            page: 0
                        })
                    }
                    options={[
                        { label: "Tất cả", value: "ALL" },
                        { label: "Có", value: "YES" },
                        { label: "Không", value: "NO" }
                    ]}
                    value={query.hasVariance}
                />
                <TextFilter
                    label="Ngày nhận từ"
                    onChange={(receivedFrom) =>
                        onChange({ page: 0, receivedFrom })
                    }
                    type="date"
                    value={query.receivedFrom}
                />
                <TextFilter
                    label="Ngày nhận đến"
                    onChange={(receivedTo) =>
                        onChange({ page: 0, receivedTo })
                    }
                    type="date"
                    value={query.receivedTo}
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

export function GoodsReceiptTable({
    receipts
}: {
    receipts: GoodsReceiptSummary[];
}) {
    const navigate = useNavigate();

    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1180px] border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                    <tr>
                        <TableHead>Mã phiếu</TableHead>
                        <TableHead>Nhà cung cấp</TableHead>
                        <TableHead>PO</TableHead>
                        <TableHead>Kho nhận</TableHead>
                        <TableHead align="right">Số dòng</TableHead>
                        <TableHead align="right">Tổng nhận</TableHead>
                        <TableHead>QC</TableHead>
                        <TableHead>Giá</TableHead>
                        <TableHead>Trạng thái</TableHead>
                        <TableHead>Ngày nhận</TableHead>
                        <TableHead>Người tạo</TableHead>
                        <TableHead align="right">Thao tác</TableHead>
                    </tr>
                </thead>
                <tbody>
                    {receipts.map((receipt) => (
                        <tr
                            className="cursor-pointer bg-white hover:bg-[#f8fbf8]"
                            key={receipt.id}
                            onClick={() =>
                                navigate(
                                    `/management/inventory/receipts/${receipt.id}`
                                )
                            }
                        >
                            <TableCell strong>
                                <Link
                                    className="text-[#159734] hover:underline"
                                    onClick={(event) => event.stopPropagation()}
                                    to={`/management/inventory/receipts/${receipt.id}`}
                                >
                                    {receipt.code}
                                </Link>
                            </TableCell>
                            <TableCell>{receipt.supplier.name}</TableCell>
                            <TableCell>{receipt.purchaseOrderCode ?? "Ngoài PO"}</TableCell>
                            <TableCell>{receipt.receivingLocation.name}</TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(receipt.totalLines)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(
                                    receipt.totalReceivedQuantity
                                )}
                            </TableCell>
                            <TableCell>
                                <QcBadge result={receipt.qcResult} />
                            </TableCell>
                            <TableCell>
                                <CostBadge status={receipt.costStatus} />
                            </TableCell>
                            <TableCell>
                                <GoodsReceiptStatusBadge
                                    status={receipt.status}
                                />
                            </TableCell>
                            <TableCell>
                                {formatVietnamDateTime(receipt.receivedAt)}
                            </TableCell>
                            <TableCell>{receipt.createdBy.name}</TableCell>
                            <TableCell align="right">
                                <Link
                                    className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#d8eadc] px-3 text-xs font-black text-[#159734] hover:bg-[#f4faf5]"
                                    onClick={(event) => event.stopPropagation()}
                                    to={`/management/inventory/receipts/${receipt.id}`}
                                >
                                    <Eye size={15} aria-hidden="true" />
                                    Xem
                                </Link>
                            </TableCell>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function RoleUnavailable({
    currentRole
}: {
    currentRole: AppRole;
}) {
    return (
        <span className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 text-xs font-black text-[#697684]">
            <Lock size={14} aria-hidden="true" />
            Không khả dụng với {goodsReceiptRoleLabels[currentRole]}
        </span>
    );
}

export function GoodsReceiptLineTable({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const totalLines = receipt.lines.length;
    const totalPages = Math.max(1, Math.ceil(totalLines / pageSize));
    const pagedLines = receipt.lines.slice(
        (page - 1) * pageSize,
        page * pageSize
    );

    return (
        <div className="grid gap-3">
            <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white">
                <table className="w-full min-w-[900px] text-left text-sm">
                    <thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]">
                        <tr>
                            <TableHead>Tên nguyên liệu</TableHead>
                            <TableHead align="right">Số đặt</TableHead>
                            <TableHead align="right">Số nhận</TableHead>
                            <TableHead align="right">Thiếu</TableHead>
                            <TableHead align="right">Thừa</TableHead>
                            <TableHead align="right">Đạt QC</TableHead>
                            <TableHead align="right">Không đạt</TableHead>
                            <TableHead>Ngày sản xuất</TableHead>
                            <TableHead>Hạn sử dụng</TableHead>
                        </tr>
                    </thead>
                    <tbody>
                        {pagedLines.map((line) => (
                            <tr key={line.id}>
                                <TableCell strong>{line.materialName}</TableCell>
                                <TableCell align="right">
                                    {line.orderedQuantity ?? "-"}
                                </TableCell>
                                <TableCell align="right">
                                    {formatVietnamNumber(line.receivedQuantity)}
                                </TableCell>
                                <TableCell align="right">
                                    {line.shortageQuantity > 0
                                        ? formatVietnamNumber(line.shortageQuantity)
                                        : "0"}
                                </TableCell>
                                <TableCell align="right">
                                    {line.surplusQuantity > 0
                                        ? formatVietnamNumber(line.surplusQuantity)
                                        : "0"}
                                </TableCell>
                                <TableCell align="right">
                                    {formatVietnamNumber(line.qc.passedQuantity)}
                                </TableCell>
                                <TableCell align="right">
                                    {formatVietnamNumber(line.qc.failedQuantity)}
                                </TableCell>
                                <TableCell>
                                    {formatVietnamDateTime(line.manufacturedAt)}
                                </TableCell>
                                <TableCell>
                                    {formatVietnamDateTime(line.expiryDate)}
                                </TableCell>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
            {totalLines > pageSize ? (
                <Pagination
                    onPageChange={(p: number) => setPage(p)}
                    onPageSizeChange={(s: number) => {
                        setPageSize(s);
                        setPage(1);
                    }}
                    page={page}
                    pageCount={totalPages}
                    pageSize={pageSize}
                    totalItems={totalLines}
                    totalItemsLabel="Tổng số mặt hàng"
                />
            ) : null}
        </div>
    );
}

export function GoodsReceiptCostTable({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white">
            <table className="w-full min-w-[1080px] text-left text-sm">
                <thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]">
                    <tr>
                        <TableHead>Tên nguyên liệu</TableHead>
                        <TableHead align="right">Đơn giá nhập</TableHead>
                        <TableHead align="right">Accepted</TableHead>
                        <TableHead align="right">Discount</TableHead>
                        <TableHead align="right">Tax</TableHead>
                        <TableHead align="right">Shipping</TableHead>
                        <TableHead align="right">Other</TableHead>
                        <TableHead align="right">Net amount</TableHead>
                        <TableHead align="right">Final unit cost</TableHead>
                        <TableHead>Trạng thái</TableHead>
                    </tr>
                </thead>
                <tbody>
                    {receipt.lines.map((line) => (
                        <tr key={line.id}>
                            <TableCell strong>{line.materialName}</TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.purchaseUnitPrice)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVietnamNumber(
                                    line.cost.acceptedQuantity
                                )}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.discount)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.tax)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.allocatedShippingCost)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.otherCost)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.lineNetAmount)}
                            </TableCell>
                            <TableCell align="right">
                                {formatVnd(line.cost.finalUnitCost)}
                            </TableCell>
                            <TableCell>
                                <CostBadge status={line.cost.status} />
                            </TableCell>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function GoodsReceiptAuditTimeline({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    return (
        <ol className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
            {receipt.auditEvents.map((event) => (
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
                            {event.actor.name} - {event.roleSnapshot}
                        </p>
                        {event.reason ? (
                            <p className="text-sm font-bold text-red-700">
                                Lý do: {event.reason}
                            </p>
                        ) : event.note ? (
                            <p className="text-sm font-semibold text-[#697684]">
                                Ghi chú: {event.note}
                            </p>
                        ) : null}
                    </div>
                </li>
            ))}
        </ol>
    );
}

export function GoodsReceiptEvidenceList({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    return (
        <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
            {receipt.evidence.length === 0 ? (
                <p className="text-sm font-semibold text-[#697684]">
                    Chưa có tài liệu đính kèm.
                </p>
            ) : (
                receipt.evidence.map((item) => (
                    <div
                        className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] px-3 py-2"
                        key={item.id}
                    >
                        <span className="inline-flex items-center gap-2 text-sm font-black text-[#172317]">
                            <FileText size={15} aria-hidden="true" />
                            {item.fileName}
                        </span>
                        <span className="text-xs font-bold text-[#697684]">
                            {item.type}
                        </span>
                    </div>
                ))
            )}
        </div>
    );
}

export function GoodsReceiptMovementPanel({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    if (!receipt.movementSummary) {
        return (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-800">
                Hàng đã nhận vật lý tại AWAITING_QC. Available chưa tăng cho
                đến khi QC và post hoàn tất.
            </div>
        );
    }

    return (
        <div className="grid gap-3 rounded-xl border border-[#d8eadc] bg-[#f4faf5] p-4 md:grid-cols-4">
            <MovementItem
                label="Tồn khả dụng"
                value={receipt.movementSummary.availableQuantity}
            />
            <MovementItem
                label="Tồn cách ly"
                value={receipt.movementSummary.quarantineQuantity}
            />
            <MovementItem
                label="Trả nhà cung cấp"
                value={receipt.movementSummary.returnToSupplierQuantity}
            />
            <MovementItem
                label="Tổng giá trị"
                value={formatVnd(receipt.movementSummary.totalInventoryValue)}
            />
        </div>
    );
}

function MovementItem({ label, value }: { label: string; value: number | string }) {
    return (
        <div className="grid gap-1">
            <span className="text-xs font-black text-[#536078]">{label}</span>
            <strong className="text-lg font-black text-[#172317]">
                {typeof value === "number" ? formatVietnamNumber(value) : value}
            </strong>
        </div>
    );
}

function TextFilter({
    label,
    onChange,
    type = "text",
    value
}: {
    label: string;
    onChange: (value: string) => void;
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
                type={type}
                value={value}
            />
        </label>
    );
}

function LookupFilter({
    dropdown,
    label,
    onChange,
    placeholder,
    value
}: {
    dropdown: ReturnType<typeof useLookupDropdown>;
    label: string;
    onChange: (value: string) => void;
    placeholder: string;
    value: string;
}) {
    return (
        <div className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <SearchableDropdown
                allOptionText="Tất cả"
                emptyText="Không có dữ liệu"
                hasMore={dropdown.hasMore}
                loading={dropdown.loading}
                onChange={onChange}
                onLoadMore={dropdown.onLoadMore}
                onSearch={dropdown.onSearch}
                options={dropdown.options}
                placeholder={placeholder}
                value={value}
            />
        </div>
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

function TableHead({
    align = "left",
    children
}: {
    align?: "left" | "right";
    children: ReactNode;
}) {
    return (
        <th
            className={`border-b border-[#dce8df] px-4 py-3 ${align === "right" ? "text-right" : "text-left"
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
            className={`border-b border-[#edf0f4] px-4 py-3 ${align === "right" ? "text-right" : "text-left"
                } ${strong ? "font-black text-[#172317]" : "font-semibold text-[#536078]"}`}
        >
            {children}
        </td>
    );
}

export function GoodsReceiptPostPreview({
    receipt
}: {
    receipt: GoodsReceiptDetail;
}) {
    const available = receipt.lines.reduce(
        (sum, line) => sum + line.qc.passedQuantity,
        0
    );
    const quarantine = receipt.lines.reduce(
        (sum, line) => sum + line.qc.quarantinedQuantity,
        0
    );
    const totalValue = receipt.lines.reduce(
        (sum, line) => sum + line.cost.lineNetAmount,
        0
    );

    return (
        <div className="grid gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-bold text-amber-800">
            <span className="inline-flex items-center gap-2">
                <PackageCheck size={16} aria-hidden="true" />
                Lô đưa vào tồn khả dụng: {formatVietnamNumber(available)}
            </span>
            <span>Lô đưa vào khu cách ly: {formatVietnamNumber(quarantine)}</span>
            <span>Tổng giá trị nhập: {formatVnd(totalValue)}</span>
            <span>Chứng từ PNK sẽ được tạo sau khi xác nhận post.</span>
        </div>
    );
}
