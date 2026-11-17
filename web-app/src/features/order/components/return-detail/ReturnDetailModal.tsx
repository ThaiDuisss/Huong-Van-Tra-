import {
    AlertTriangle,
    ArrowRight,
    CheckCircle2,
    ClipboardCheck,
    PackageCheck,
    RefreshCw,
    X,
    XCircle
} from "lucide-react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "../../../../auth/role-utils";
import { Button, IconButton } from "../../../../components/ui/Button";
import { ModalShell } from "../../../../components/ui/Overlay";
import { StatusBadge, type BadgeTone } from "../../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../../components/ui/Table";
import { routePaths } from "../../../../routes/path";
import { InspectionStatusBadge } from "../../../inventory/components/CustomerReturnInspectionComponents";
import { CustomerReturnWarehouseInspectionModal } from "../../../inventory/components/CustomerReturnWarehouseInspectionModal";
import type { CustomerReturnInspection } from "../../../inventory/types/customer-return-inspection.types";
import { ConfirmDialog } from "../../../product/components/ConfirmDialog";
import {
    cancelOrderReturn,
    getCustomerReturnQcSummary,
    getOrderReturnDetail,
    receiveOrderReturn
} from "../../services/order-return.api";
import type {
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    OrderReturnListRow,
    OrderReturnResponse
} from "../../types/order-return.types";
import { formatCurrency, formatDateTime } from "../../utils/order-formatters";
import {
    canInspectCustomerReturn,
    formatReturnQuantity,
    getBlockReasonLabel,
    getCustomerReturnQcProgressStatus,
    getOrderRefundStatusLabel,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    resolveCustomerReturnQuantities,
    toReturnNumber
} from "../../utils/order-return.utils";
import { OrderStatusBadge } from "../OrderStatusBadge";
import { ReturnBusinessProgress } from "../ReturnBusinessProgress";
import { ReturnInspectionModal } from "../ReturnInspectionModal";
import { ReturnStatusBadge } from "../ReturnStatusBadge";
import { type ReturnDetailData, useReturnDetailData } from "./useReturnDetailData";

type DetailTab = "overview" | "items" | "inspection" | "refund";

interface ReturnDetailModalProps {
    /** Dòng phiếu hoàn từ danh sách (hiển thị ngay, chi tiết tải bổ sung). */
    returnItem: OrderReturnListRow;
    onClose: () => void;
    /** Gọi sau khi phiếu thay đổi (nhận hàng, hủy, kiểm hàng) để danh sách tải lại. */
    onChanged?: (updated?: OrderReturnResponse) => void;
    onToast: (message: string) => void;
    /** Cho phép hủy phiếu (chỉ màn Hoàn hàng bên Đơn hàng). */
    allowCancel?: boolean;
    initialTab?: DetailTab;
}

const DISPOSITION_LABELS: Record<CustomerReturnQcResult, { label: string; tone: BadgeTone }> = {
    RESTOCK: { label: "Đạt chất lượng", tone: "success" },
    DAMAGED: { label: "Hàng hỏng", tone: "danger" },
    QUARANTINE: { label: "Cách ly (lịch sử)", tone: "warning" },
    WRITE_OFF: { label: "Tiêu hủy", tone: "neutral" }
};

export function ReturnDetailModal({
    returnItem: baseRow,
    onClose,
    onChanged,
    onToast,
    allowCancel = false,
    initialTab = "overview"
}: ReturnDetailModalProps) {
    const data = useReturnDetailData(baseRow);
    const { returnItem, qcSummary, inspections, loading, error, reload } = data;
    const navigate = useNavigate();
    const { hasAnyRole } = useAuthorization();
    const canExecuteQc = hasAnyRole(CUSTOMER_RETURN_QC_ROLES);
    const [tab, setTab] = useState<DetailTab>(initialTab);
    const [confirm, setConfirm] = useState<"receive" | "cancel" | null>(null);
    const [confirmError, setConfirmError] = useState("");
    const [transitioning, setTransitioning] = useState(false);
    const [inspectionSource, setInspectionSource] = useState<{
        returnItem: OrderReturnListRow;
        qcSummary: NonNullable<ReturnDetailData["qcSummary"]>;
    } | null>(null);
    const [openingInspection, setOpeningInspection] = useState(false);
    const [warehouseInspection, setWarehouseInspection] = useState<CustomerReturnInspection | null>(null);
    const [submittedInspection, setSubmittedInspection] = useState(false);
    const panelRef = useRef<HTMLDivElement>(null);
    const titleId = useId();
    const childModalOpen = Boolean(confirm || inspectionSource || warehouseInspection);

    // Trả focus về phần tử trước đó khi đóng. Escape do ModalShell xử lý (tắt khi có hộp thoại con).
    useEffect(() => {
        const previous = document.activeElement;
        panelRef.current?.focus({ preventScroll: true });
        return () => {
            if (previous instanceof HTMLElement && previous.isConnected) previous.focus({ preventScroll: true });
        };
    }, []);

    const items = returnItem.items ?? [];
    const summaryLines = qcSummary?.lines ?? [];
    const postedFromQc = qcSummary ? summaryLines.reduce((sum, line) => sum + toReturnNumber(line.postedQcQuantity), 0) : undefined;
    const remainingFromQc = qcSummary ? summaryLines.reduce((sum, line) => sum + toReturnNumber(line.remainingQcQuantity), 0) : undefined;
    const qcSource = {
        ...returnItem,
        postedQcQuantity: postedFromQc ?? returnItem.postedQcQuantity ?? undefined,
        remainingQcQuantity: remainingFromQc ?? returnItem.remainingQcQuantity ?? undefined,
        qcStatus: qcSummary?.qcStatus ?? returnItem.qcStatus ?? null,
        isPendingDisposition: returnItem.isPendingDisposition || submittedInspection,
        items
    };
    const quantities = resolveCustomerReturnQuantities(qcSource);
    const qcProgress = getCustomerReturnQcProgressStatus(qcSource);
    const openInspection = inspections.find((inspection) => inspection.status === "CREATED" || inspection.status === "PROCESSING");
    const canStartInspection = !loading && !error && canExecuteQc && !openInspection
        && canInspectCustomerReturn(qcSource)
        && summaryLines.some((line) => toReturnNumber(line.remainingQcQuantity) > 0);
    const refundBlockReason = data.refundDetail?.refundEligible === false
        && data.refundDetail.refundBlockReason
        && data.refundDetail.refundBlockReason !== "NONE"
        ? data.refundDetail.refundBlockReason
        : null;
    const isPendingRefund = returnItem.displayStatus === "PENDING_REFUND";

    function notifyChanged(updated?: OrderReturnResponse) {
        reload();
        onChanged?.(updated);
    }

    function goToRefund() {
        const params = new URLSearchParams({ returnId: returnItem.id, keyword: getReturnCode(returnItem) });
        navigate(`${routePaths.refunds}?${params.toString()}`);
    }

    function runTransition() {
        if (!confirm) return;
        const action = confirm === "receive" ? receiveOrderReturn : cancelOrderReturn;
        setTransitioning(true);
        setConfirmError("");
        action(returnItem.id)
            .then((updated) => {
                onToast(confirm === "receive" ? "Đã xác nhận nhận hàng." : "Đã hủy phiếu hoàn.");
                setConfirm(null);
                notifyChanged(updated);
            })
            .catch((transitionError: unknown) => setConfirmError(getReturnErrorMessage(transitionError)))
            .finally(() => setTransitioning(false));
    }

    function startInspection() {
        if (openInspection) {
            setWarehouseInspection(openInspection);
            return;
        }
        if (!canStartInspection) return;
        // Lấy số liệu mới nhất trước khi mở form để tránh kiểm trùng số lượng.
        setOpeningInspection(true);
        Promise.all([getOrderReturnDetail(returnItem.id), getCustomerReturnQcSummary(returnItem.id)])
            .then(([latest, summary]) => {
                const latestRow: OrderReturnListRow = { ...returnItem, ...latest };
                if (!canInspectCustomerReturn({ ...latestRow, qcStatus: summary.qcStatus })) {
                    onToast("Phiếu chưa sẵn sàng để kiểm hàng. Vui lòng tải lại để xem tiến độ.");
                    reload();
                    return;
                }
                setInspectionSource({ returnItem: latestRow, qcSummary: summary });
            })
            .catch((loadError: unknown) => onToast(getReturnErrorMessage(loadError)))
            .finally(() => setOpeningInspection(false));
    }

    const headerActions: ReactNode[] = [];
    if (returnItem.status === "CREATED") {
        if (allowCancel) {
            headerActions.push(
                <Button disabled={transitioning} key="cancel" onClick={() => setConfirm("cancel")}>
                    <XCircle size={17} aria-hidden="true" />
                    Hủy phiếu
                </Button>
            );
        }
        headerActions.push(
            <Button disabled={transitioning} key="receive" onClick={() => setConfirm("receive")} variant="primary">
                <PackageCheck size={17} aria-hidden="true" />
                Xác nhận nhận hàng
            </Button>
        );
    } else if (canExecuteQc && (openInspection || canStartInspection)) {
        headerActions.push(
            <Button disabled={openingInspection} key="inspect" onClick={startInspection} variant="primary">
                <ClipboardCheck size={17} aria-hidden="true" />
                {openingInspection ? "Đang mở..." : openInspection ? "Tiếp tục kiểm hàng" : "Kiểm hàng"}
            </Button>
        );
    } else if (isPendingRefund) {
        headerActions.push(
            <Button key="refund" onClick={goToRefund} variant="primary">
                Đi tới hoàn tiền
                <ArrowRight size={17} aria-hidden="true" />
            </Button>
        );
    }

    const tabs: { key: DetailTab; label: string }[] = [
        { key: "overview", label: "Tổng quan" },
        { key: "items", label: `Sản phẩm (${items.length})` },
        { key: "inspection", label: "Kiểm hàng" },
        { key: "refund", label: "Hoàn tiền" }
    ];

    return (
        <ModalShell
            className="w-full max-w-[880px]"
            closeOnEscape={!childModalOpen}
            labelledBy={titleId}
            onClose={onClose}
        >
            <div
                className="flex max-h-[min(880px,calc(90vh-2px))] flex-col bg-[#f6f8f7] outline-none"
                ref={panelRef}
                tabIndex={-1}
            >
                <header className="border-b border-[#e4ece8] bg-white px-6 pb-0 pt-5">
                    <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                                <h2 className="truncate text-xl font-black text-[#172317]" id={titleId}>
                                    {getReturnCode(returnItem)}
                                </h2>
                                <ReturnStatusBadge displayStatus={returnItem.displayStatus} status={returnItem.status} />
                            </div>
                            <p className="mt-1 text-sm font-semibold text-[#697684]">
                                Đơn {returnItem.orderCode || "--"} · {returnItem.customerName?.trim() || "Khách lẻ"}
                                {returnItem.customerPhone ? ` · ${returnItem.customerPhone}` : ""}
                            </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                            <IconButton
                                aria-label="Tải lại chi tiết"
                                disabled={loading}
                                onClick={reload}
                                size="md"
                                variant="secondary"
                            >
                                <RefreshCw className={loading ? "animate-spin" : ""} size={16} aria-hidden="true" />
                            </IconButton>
                            <IconButton
                                aria-label="Đóng chi tiết phiếu hoàn"
                                onClick={onClose}
                                size="md"
                                variant="secondary"
                            >
                                <X size={18} aria-hidden="true" />
                            </IconButton>
                        </div>
                    </div>
                    {headerActions.length ? <div className="mt-4 flex flex-wrap justify-end gap-2">{headerActions}</div> : null}
                    <nav aria-label="Nội dung chi tiết phiếu hoàn" className="mt-3 flex gap-1 overflow-x-auto overflow-y-hidden" role="tablist">
                        {tabs.map((item) => (
                            <button
                                aria-selected={tab === item.key}
                                className={`shrink-0 border-b-2 px-3 py-2.5 text-sm font-black transition-colors ${tab === item.key
                                    ? "border-[#159734] text-[#116f28]"
                                    : "border-transparent text-[#697684] hover:text-[#172317]"}`}
                                key={item.key}
                                onClick={() => setTab(item.key)}
                                role="tab"
                                type="button"
                            >
                                {item.label}
                            </button>
                        ))}
                    </nav>
                </header>

                <div className="min-h-0 flex-1 overflow-y-auto p-5" role="tabpanel">
                    {error ? (
                        <Notice tone="danger" title="Không tải được chi tiết phiếu hoàn">
                            {error}
                        </Notice>
                    ) : null}
                    {tab === "overview" ? (
                        <OverviewTab data={data} qcProgress={qcProgress} quantities={quantities} />
                    ) : null}
                    {tab === "items" ? <ItemsTab returnItem={returnItem} /> : null}
                    {tab === "inspection" ? (
                        <InspectionTab
                            data={data}
                            onOpenInspection={setWarehouseInspection}
                            qcProgress={qcProgress}
                            quantities={quantities}
                        />
                    ) : null}
                    {tab === "refund" ? (
                        <RefundTab data={data} onGoToRefund={goToRefund} refundBlockReason={refundBlockReason} />
                    ) : null}
                </div>
            </div>

            {confirm ? (
                <ConfirmDialog
                    confirmLabel={confirm === "receive" ? "Xác nhận nhận hàng" : "Hủy phiếu"}
                    confirmVariant={confirm === "receive" ? "primary" : "danger"}
                    description={confirm === "receive"
                        ? `Xác nhận cửa hàng đã nhận hàng trả của phiếu ${getReturnCode(returnItem)}? Sau khi nhận hàng, phiếu không thể hủy.`
                        : `Bạn có chắc muốn hủy phiếu hoàn ${getReturnCode(returnItem)}?`}
                    error={confirmError}
                    onCancel={() => {
                        if (transitioning) return;
                        setConfirm(null);
                        setConfirmError("");
                    }}
                    onConfirm={runTransition}
                    submitting={transitioning}
                    title={confirm === "receive" ? "Xác nhận nhận hàng" : "Hủy phiếu hoàn"}
                />
            ) : null}

            {inspectionSource ? (
                <ReturnInspectionModal
                    isOpen
                    onClose={() => setInspectionSource(null)}
                    onRefresh={() => notifyChanged()}
                    onSuccess={(updated) => {
                        setSubmittedInspection(true);
                        notifyChanged(updated);
                    }}
                    onToast={onToast}
                    qcSummary={inspectionSource.qcSummary}
                    returnItem={inspectionSource.returnItem}
                />
            ) : null}

            {warehouseInspection ? (
                <CustomerReturnWarehouseInspectionModal
                    inspectionId={warehouseInspection.id}
                    isOpen
                    key={warehouseInspection.id}
                    onClose={() => setWarehouseInspection(null)}
                    onSuccess={() => notifyChanged()}
                    returnCode={returnItem.returnCode}
                    returnId={returnItem.id}
                    returnItem={returnItem}
                />
            ) : null}
        </ModalShell>
    );
}

type Quantities = ReturnType<typeof resolveCustomerReturnQuantities>;
type QcProgress = ReturnType<typeof getCustomerReturnQcProgressStatus>;

function OverviewTab({ data, qcProgress, quantities }: { data: ReturnDetailData; qcProgress: QcProgress; quantities: Quantities }) {
    const { returnItem, qcSummary, timeline, refundDetail } = data;

    return (
        <div className="grid gap-4">
            <Card title="Thông tin phiếu hoàn">
                <InfoGrid
                    rows={[
                        ["Khách hàng", returnItem.customerName?.trim() || "Khách lẻ"],
                        ["Số điện thoại", returnItem.customerPhone || "--"],
                        ["Mã đơn hàng", returnItem.orderCode || "--"],
                        ["Chính sách hoàn tiền", returnItem.refundRequired === false ? "Không hoàn tiền" : "Có hoàn tiền"],
                        ["Ngày tạo", formatDateTime(returnItem.createdAt)],
                        ["Cập nhật", formatDateTime(returnItem.updatedAt)]
                    ]}
                />
                {returnItem.note ? (
                    <div className="mt-3 rounded-lg bg-[#f4f7f5] p-3 text-sm">
                        <strong className="block text-xs font-black uppercase text-[#697684]">Ghi chú</strong>
                        <p className="mt-1 text-[#172317]">{returnItem.note}</p>
                    </div>
                ) : null}
            </Card>

            <div className="grid gap-3 sm:grid-cols-3">
                <Stat label="Tiến độ kiểm hàng" value={`${formatReturnQuantity(quantities.processed)} / ${formatReturnQuantity(quantities.required)}`}>
                    <OrderStatusBadge label={qcProgress.label} status={qcProgress.status} />
                </Stat>
                <Stat label="Còn cần kiểm hàng" value={formatReturnQuantity(quantities.remaining)} />
                <Stat
                    label="Số tiền hoàn"
                    value={refundDetail?.refundAmount ? formatCurrency(toReturnNumber(refundDetail.refundAmount)) : "--"}
                />
            </div>

            <ReturnBusinessProgress qcSummary={qcSummary} returnData={returnItem} timeline={timeline} />
        </div>
    );
}

function ItemsTab({ returnItem }: { returnItem: OrderReturnListRow }) {
    const items = returnItem.items ?? [];
    if (items.length === 0) {
        return <EmptyBlock>Không có thông tin dòng sản phẩm.</EmptyBlock>;
    }

    return (
        <Card title={`Sản phẩm khách trả (${items.length})`}>
            <div className="overflow-x-auto">
                <Table className="min-w-[560px]">
                    <TableHead>
                        <TableRow>
                            <TableHeaderCell>Sản phẩm</TableHeaderCell>
                            <TableHeaderCell className="text-right">SL hoàn</TableHeaderCell>
                            <TableHeaderCell>Lý do</TableHeaderCell>
                            <TableHeaderCell>Tình trạng</TableHeaderCell>
                        </TableRow>
                    </TableHead>
                    <tbody>
                        {items.map((item, index) => (
                            <TableRow key={item.id ?? index}>
                                <TableCell>
                                    <strong className="block text-[#172317]">{item.productNameSnapshot || "Sản phẩm"}</strong>
                                    <span className="text-xs font-semibold text-[#697684]">
                                        {item.skuSnapshot || "--"}
                                        {item.variantNameSnapshot ? ` · ${item.variantNameSnapshot}` : ""}
                                    </span>
                                    {item.note ? <p className="mt-1 text-xs text-[#536078]">Ghi chú: {item.note}</p> : null}
                                </TableCell>
                                <TableCell className="text-right font-black">{formatReturnQuantity(item.quantity)}</TableCell>
                                <TableCell>{getReturnReasonLabel(item.reason)}</TableCell>
                                <TableCell>{getReturnConditionLabel(item.condition)}</TableCell>
                            </TableRow>
                        ))}
                    </tbody>
                </Table>
            </div>
        </Card>
    );
}

function InspectionTab({
    data,
    onOpenInspection,
    qcProgress,
    quantities
}: {
    data: ReturnDetailData;
    onOpenInspection: (inspection: CustomerReturnInspection) => void;
    qcProgress: QcProgress;
    quantities: Quantities;
}) {
    const { returnItem, qcSummary, qcSummaryError, inspections, loading, reload } = data;
    const isFailed = returnItem.hasFailedDisposition
        || returnItem.inventoryProcessingStatus === "FAILED"
        || returnItem.displayStatus === "PROCESSING_FAILED";
    const isCreatingInspection = inspections.length === 0
        && !qcSummary?.inspectionId
        && qcSummary?.qcStatus !== "COMPLETED"
        && !returnItem.physicalDone
        && (returnItem.isPendingDisposition
            || returnItem.inventoryProcessingStatus === "PROCESSING"
            || returnItem.displayStatus === "PROCESSING");

    if (returnItem.status === "CREATED") {
        return <EmptyBlock>Phiếu hoàn cần được xác nhận nhận hàng trước khi kiểm hàng.</EmptyBlock>;
    }
    if (returnItem.status === "CANCELLED") {
        return <EmptyBlock>Phiếu hoàn đã hủy nên không phát sinh kiểm hàng.</EmptyBlock>;
    }

    return (
        <div className="grid gap-4">
            {quantities.hasDiscrepancy ? (
                <Notice tone="warning" title="Chênh lệch số liệu giữa Đơn hàng và Kho">
                    Đơn hàng ghi nhận đã xử lý {quantities.orderProcessedQuantity ?? 0}, Kho ghi nhận đã kiểm{" "}
                    {quantities.inventoryPostedQuantity ?? 0}. Tiến độ đang hiển thị theo số liệu Kho. Cần đối soát.
                </Notice>
            ) : null}
            {isFailed && qcSummary?.qcStatus !== "COMPLETED" && !returnItem.physicalDone ? (
                <Notice tone="danger" title="Lệnh xử lý kho trước đó không thành công">
                    Cần đối soát với kho trước khi gửi lại để tránh xử lý trùng số lượng. Vui lòng liên hệ quản lý.
                </Notice>
            ) : null}
            {isCreatingInspection ? (
                <Notice tone="info" title="Đang tạo phiếu kiểm định">
                    Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho.
                </Notice>
            ) : null}
            {qcSummaryError ? (
                <Notice tone="danger" title="Không tải được dữ liệu kiểm hàng từ Kho">
                    {qcSummaryError}{" "}
                    <Button onClick={reload} size="xs" variant="link">Thử lại</Button>
                </Notice>
            ) : null}

            <div className="grid gap-3 sm:grid-cols-3">
                <Stat label="Đã trả" value={formatReturnQuantity(quantities.required)} />
                <Stat label="Đã kiểm hàng" value={formatReturnQuantity(quantities.processed)} />
                <Stat label="Còn lại" value={formatReturnQuantity(quantities.remaining)}>
                    <OrderStatusBadge label={qcProgress.label} status={qcProgress.status} />
                </Stat>
            </div>

            {qcSummary?.lines?.length ? (
                <Card title="Kết quả theo sản phẩm">
                    {qcSummary.inspectedAt ? (
                        <p className="-mt-1 mb-3 text-xs font-semibold text-[#697684]">
                            Kiểm hàng gần nhất: {formatDateTime(qcSummary.inspectedAt)}
                        </p>
                    ) : null}
                    <ul className="grid gap-2">
                        {qcSummary.lines.map((line) => (
                            <QcLine key={line.returnItemId} line={line} returnItem={returnItem} />
                        ))}
                    </ul>
                </Card>
            ) : null}

            <Card title={`Phiếu kiểm định kho (${inspections.length})`}>
                {loading && inspections.length === 0 ? (
                    <p className="text-sm font-semibold text-[#697684]">Đang tải phiếu kiểm định...</p>
                ) : inspections.length === 0 ? (
                    <p className="text-sm font-semibold text-[#697684]">
                        {returnItem.status === "COMPLETED" ? "Phiếu hoàn đã hoàn tất quy trình." : "Chưa có phiếu kiểm định nào."}
                    </p>
                ) : (
                    <ul className="grid gap-2">
                        {inspections.map((inspection) => (
                            <li className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg border border-[#e1e8e5] p-3 text-xs" key={inspection.id}>
                                <strong className="text-sm font-black text-[#172317]">{inspection.inspectionCode}</strong>
                                <InspectionStatusBadge status={inspection.status} />
                                <span className="font-semibold text-[#697684]">
                                    SL: {inspection.lines.reduce((sum, line) => sum + Number(line.quantity), 0).toLocaleString("vi-VN")}
                                    {" · "}Tạo {formatDateTime(inspection.createdAt)}
                                    {inspection.postedAt ? ` · Xử lý kho ${formatDateTime(inspection.postedAt)}` : ""}
                                </span>
                                <Button
                                    className="ml-auto"
                                    onClick={() => onOpenInspection(inspection)}
                                    size="xs"
                                    variant="link"
                                >
                                    {inspection.status === "POSTED" || inspection.status === "CANCELLED" ? "Xem kết quả" : "Tiếp tục kiểm hàng"}
                                </Button>
                            </li>
                        ))}
                    </ul>
                )}
            </Card>
        </div>
    );
}

function QcLine({ line, returnItem }: { line: CustomerReturnQcSummaryLine; returnItem: OrderReturnListRow }) {
    const item = (returnItem.items ?? []).find((candidate) => candidate.id === line.returnItemId);

    return (
        <li className="grid gap-2 rounded-lg bg-[#f4f7f5] p-3 text-xs sm:grid-cols-[minmax(0,1fr)_auto]">
            <div className="min-w-0">
                <strong className="block truncate text-sm font-black text-[#172317]">
                    {item?.productNameSnapshot?.trim() || "Sản phẩm"}
                </strong>
                <span className="font-semibold text-[#697684]">
                    {item?.skuSnapshot?.trim() || "--"}
                    {item?.variantNameSnapshot ? ` · ${item.variantNameSnapshot}` : ""}
                </span>
                {line.dispositions?.length ? (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                        {line.dispositions.map((disposition, index) => {
                            const meta = disposition.result ? DISPOSITION_LABELS[disposition.result] : undefined;
                            return (
                                <StatusBadge
                                    key={`${disposition.result}-${disposition.destinationLocationId ?? index}`}
                                    tone={meta?.tone ?? "neutral"}
                                >
                                    {meta?.label ?? disposition.result ?? "--"} · {formatReturnQuantity(disposition.quantity)}
                                    {disposition.destinationLocationName ? ` (${disposition.destinationLocationName})` : ""}
                                </StatusBadge>
                            );
                        })}
                    </div>
                ) : null}
            </div>
            <div className="flex gap-3 font-semibold text-[#536078] sm:flex-col sm:items-end sm:gap-0.5">
                <span>Đã trả: <b>{formatReturnQuantity(line.returnedQuantity)}</b></span>
                <span>Đã kiểm: <b>{formatReturnQuantity(line.postedQcQuantity)}</b></span>
                <span>Còn lại: <b>{formatReturnQuantity(line.remainingQcQuantity)}</b></span>
            </div>
        </li>
    );
}

function RefundTab({
    data,
    onGoToRefund,
    refundBlockReason
}: {
    data: ReturnDetailData;
    onGoToRefund: () => void;
    refundBlockReason: string | null;
}) {
    const { returnItem, refundDetail } = data;
    const isPendingRefund = returnItem.displayStatus === "PENDING_REFUND";

    if (returnItem.refundRequired === false) {
        return <EmptyBlock>Phiếu hoàn không phát sinh hoàn tiền (tự động hoàn tất khi kiểm hàng xong).</EmptyBlock>;
    }

    return (
        <div className="grid gap-4">
            {isPendingRefund ? (
                <div className={`flex flex-wrap items-center justify-between gap-3 rounded-xl border p-4 ${refundBlockReason ? "border-[#f3d9a4] bg-[#fff8ea]" : "border-[#cfe8d6] bg-[#f1f9f3]"}`}>
                    <div>
                        <strong className="block text-sm font-black text-[#172317]">
                            {refundBlockReason ? "Chưa thể hoàn tiền" : "Sẵn sàng hoàn tiền"}
                        </strong>
                        <span className="text-[13px] font-semibold text-[#536078]">
                            {refundBlockReason
                                ? `${getBlockReasonLabel(refundBlockReason)}. Kiểm tra và thử lại tại màn Hoàn tiền.`
                                : "Hàng trả đã kiểm và xử lý kho xong. Tiếp tục hoàn tiền cho khách hàng."}
                        </span>
                    </div>
                    <Button onClick={onGoToRefund} variant="primary">
                        Đi tới hoàn tiền
                        <ArrowRight size={17} aria-hidden="true" />
                    </Button>
                </div>
            ) : null}
            <Card title="Thông tin hoàn tiền">
                <InfoGrid
                    rows={[
                        ["Trạng thái hoàn tiền", getRefundStatusText(data, refundBlockReason)],
                        ["Số tiền hoàn", refundDetail?.refundAmount ? formatCurrency(toReturnNumber(refundDetail.refundAmount)) : "--"],
                        ["Đã hoàn", refundDetail?.totalAmountRefunded ? formatCurrency(toReturnNumber(refundDetail.totalAmountRefunded)) : "--"],
                        ["Thời gian hoàn", refundDetail?.refundedAt ? formatDateTime(refundDetail.refundedAt) : "--"]
                    ]}
                />
            </Card>
            {returnItem.status === "COMPLETED" && refundDetail?.refundStatus === "REFUNDED" ? (
                <Notice tone="success" title="Đã hoàn tiền cho khách">
                    Phiếu hoàn đã hoàn tất.
                </Notice>
            ) : null}
        </div>
    );
}

function getRefundStatusText({ returnItem, refundDetail }: ReturnDetailData, refundBlockReason: string | null) {
    if (returnItem.refundRequired === false) return "Không phát sinh";
    if (refundDetail?.refundStatus === "REFUNDED") return "Đã hoàn tất";
    // Trạng thái nghiệp vụ của phiếu là nguồn chính khi phiếu đã ở "Chờ hoàn tiền".
    if (returnItem.displayStatus === "PENDING_REFUND") {
        return refundBlockReason ? "Tạm chưa hoàn được" : "Sẵn sàng hoàn tiền";
    }
    if (refundDetail?.refundStatus === "PENDING") return "Chờ xử lý sau kiểm hàng";
    if (refundDetail?.refundStatus) return getOrderRefundStatusLabel(refundDetail.refundStatus);
    return returnItem.status === "COMPLETED" ? "Đã hoàn tất" : "Chờ xử lý sau kiểm hàng";
}

function Card({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className="rounded-xl border border-[#e1e8e5] bg-white p-4">
            <h3 className="mb-3 text-sm font-black text-[#172317]">{title}</h3>
            {children}
        </section>
    );
}

function InfoGrid({ rows }: { rows: [string, ReactNode][] }) {
    return (
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {rows.map(([label, value]) => (
                <div className="min-w-0" key={label}>
                    <dt className="text-xs font-bold text-[#697684]">{label}</dt>
                    <dd className="mt-0.5 truncate text-sm font-black text-[#172317]">{value || "--"}</dd>
                </div>
            ))}
        </dl>
    );
}

function Stat({ label, value, children }: { label: string; value: string; children?: ReactNode }) {
    return (
        <div className="rounded-xl border border-[#e1e8e5] bg-white p-3">
            <span className="block text-xs font-bold text-[#697684]">{label}</span>
            <strong className="mt-1 block text-lg font-black text-[#172317]">{value}</strong>
            {children ? <div className="mt-1.5">{children}</div> : null}
        </div>
    );
}

function EmptyBlock({ children }: { children: ReactNode }) {
    return (
        <div className="rounded-xl border border-dashed border-[#d6e0db] bg-white p-8 text-center text-sm font-semibold text-[#697684]">
            {children}
        </div>
    );
}

const NOTICE_TONE = {
    danger: { box: "border-[#fecaca] bg-[#fef2f2] text-[#991b1b]", icon: <AlertTriangle size={16} aria-hidden="true" /> },
    warning: { box: "border-[#fde68a] bg-[#fffbeb] text-[#92400e]", icon: <AlertTriangle size={16} aria-hidden="true" /> },
    info: { box: "border-[#bfdbfe] bg-[#eff6ff] text-[#1e40af]", icon: <RefreshCw size={16} aria-hidden="true" /> },
    success: { box: "border-[#bbf7d0] bg-[#f0fdf4] text-[#166534]", icon: <CheckCircle2 size={16} aria-hidden="true" /> }
} as const;

function Notice({ tone, title, children }: { tone: keyof typeof NOTICE_TONE; title: string; children: ReactNode }) {
    const meta = NOTICE_TONE[tone];
    return (
        <div className={`flex gap-2.5 rounded-xl border p-3 text-[13px] ${meta.box}`} role={tone === "danger" ? "alert" : "status"}>
            <span className="mt-0.5 shrink-0">{meta.icon}</span>
            <div>
                <strong className="block font-black">{title}</strong>
                <span className="font-semibold">{children}</span>
            </div>
        </div>
    );
}
