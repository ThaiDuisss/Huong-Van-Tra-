import { useCallback, useEffect, useState } from "react";
import { AlertCircle, CheckCircle2, PackageCheck, RotateCw, X } from "lucide-react";
import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "@/auth/role-utils";

import { Button, IconButton } from "@/components/ui/Button";
import { ModalShell } from "@/components/ui/Overlay";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";

import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import { getOrderReturnDetail } from "../../order/services/order-return.api";
import type {
    CustomerReturnQcStatus,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderReturnItemResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    ReturnReason,
    ReturnStatus
} from "../../order/types/order-return.types";
import {
    formatReturnQuantity,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    toReturnNumber
} from "../../order/utils/order-return.utils";
import { customerReturnApi, loadCustomerReturnQcLocations } from "../services/customerReturn.api";
import type {
    CustomerReturnInspection,
    CustomerReturnQcDraft,
    CustomerReturnQcLocation
} from "../types/customer-return-inspection.types";
import { createCustomerReturnQcDraft, getCustomerReturnQcError } from "../utils/customer-return-qc";
import { formatVietnamDateTime } from "../utils/inventory-format";
import { InspectionStatusBadge } from "./CustomerReturnInspectionComponents";
import { CustomerReturnInspectionCard } from "./CustomerReturnInspectionCard";
import { ChevronDown, ChevronUp, Info } from "lucide-react";
import styles from "./CustomerReturnWarehouseInspectionModal.module.css";

export interface CustomerReturnWarehouseInspectionModalProps {
    isOpen: boolean;
    onClose: () => void;
    returnId: string;
    returnCode?: string | null;
    returnItem?: OrderReturnListRow | null;
    inspectionId?: string | null;
    onSuccess?: () => void;
}

const RESULT_LABELS: Record<string, string> = {
    RESTOCK: "Nhập lại kho",
    DAMAGED: "Hàng hỏng",
    WRITE_OFF: "Tiêu hủy",
    QUARANTINE: "Cách ly"
};

function resolveQcStatusDisplay(
    qcStatus?: CustomerReturnQcStatus | string | null,
    returnStatus?: ReturnStatus | null,
    hasInspection?: boolean
): { label: string; status: string } {
    if (returnStatus === "CREATED") {
        return { label: "Chờ nhận hàng", status: "PENDING" };
    }
    if (qcStatus === "COMPLETED") {
        return { label: "Đã kiểm hàng", status: "QC_COMPLETED" };
    }
    if (qcStatus === "PARTIAL") {
        return { label: "Đang kiểm hàng", status: "PROCESSING" };
    }
    if (qcStatus === "PENDING" || !hasInspection) {
        return { label: "Chờ kiểm hàng", status: "PENDING" };
    }
    return { label: "Chờ kiểm hàng", status: "PENDING" };
}

export function CustomerReturnWarehouseInspectionModal({
    isOpen,
    onClose,
    returnId,
    returnCode,
    returnItem,
    inspectionId: explicitInspectionId,
    onSuccess
}: CustomerReturnWarehouseInspectionModalProps) {
    const { hasAnyRole } = useAuthorization();
    const canExecuteQc = hasAnyRole(CUSTOMER_RETURN_QC_ROLES);

    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [locationError, setLocationError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [orderDetail, setOrderDetail] = useState<OrderReturnResponse | null>(null);
    const [inspectionDetail, setInspectionDetail] = useState<CustomerReturnInspection | null>(null);
    const [drafts, setDrafts] = useState<Record<string, CustomerReturnQcDraft>>({});
    const [locations, setLocations] = useState<CustomerReturnQcLocation[]>([]);

    // UX improvements state: drawer, validation touched & card accordion
    const [showInfoDrawer, setShowInfoDrawer] = useState(false);
    const [attemptedSubmit, setAttemptedSubmit] = useState(false);
    const [touchedFields, setTouchedFields] = useState<Record<string, { actualCondition?: boolean; result?: boolean; destinationLocationId?: boolean }>>({});
    const [expandedCards, setExpandedCards] = useState<Record<string, boolean>>({});

    const loadData = useCallback(async () => {
        if (!returnId) return;
        setLoading(true);
        setError("");
        setLocationError("");
        setSuccessMessage("");
        setQcSummary(null);
        setInspectionDetail(null);
        setOrderDetail(null);
        setDrafts({});

        try {
            // 1. Concurrently fetch summary and order detail
            const [summaryRes, orderDetailRes] = await Promise.all([
                customerReturnApi.getQcSummary(returnId).catch(() => null),
                getOrderReturnDetail(returnId).catch(() => null)
            ]);

            if (summaryRes) setQcSummary(summaryRes);
            if (orderDetailRes) setOrderDetail(orderDetailRes);

            // 2. Resolve target inspectionId
            const targetInspectionId = explicitInspectionId || summaryRes?.inspectionId || null;
            let loadedInspection: CustomerReturnInspection | null = null;

            if (targetInspectionId) {
                loadedInspection = await customerReturnApi.getInspection(targetInspectionId).catch(() => null);
            }

            // Fallback search by returnCode if not found by direct ID
            const effCode = summaryRes?.returnCode || returnCode || returnItem?.returnCode;
            if (!loadedInspection && effCode) {
                const searchRes = await customerReturnApi.listInspections({
                    returnCode: effCode,
                    size: 5,
                    page: 0
                }).catch(() => null);
                if (searchRes && searchRes.content.length > 0) {
                    loadedInspection = searchRes.content[0];
                }
            }

            if (loadedInspection) {
                setInspectionDetail(loadedInspection);
                const initialDrafts: Record<string, CustomerReturnQcDraft> = {};
                for (const line of loadedInspection.lines) {
                    initialDrafts[line.id] = createCustomerReturnQcDraft(line);
                }
                setDrafts(initialDrafts);

                // If editable, preload locations
                if (canExecuteQc && ["CREATED", "PROCESSING"].includes(loadedInspection.status)) {
                    try {
                        const locs = await loadCustomerReturnQcLocations();
                        setLocations(locs);
                    } catch {
                        setLocationError("Không thể tải vị trí kho. Vui lòng tải lại để chọn vị trí trước khi hạch toán.");
                    }
                }
            }
        } catch (err: unknown) {
            setError(getReturnErrorMessage(err, "Không thể tải thông tin kiểm định kho. Vui lòng thử lại."));
        } finally {
            setLoading(false);
        }
    }, [canExecuteQc, explicitInspectionId, returnCode, returnId, returnItem]);

    useEffect(() => {
        if (!isOpen) return;
        let active = true;
        const timerId = window.setTimeout(() => {
            if (active) {
                void loadData();
            }
        }, 0);
        return () => {
            active = false;
            window.clearTimeout(timerId);
        };
    }, [isOpen, loadData]);

    if (!isOpen) return null;

    const effectiveReturnCode = qcSummary?.returnCode || returnCode || returnItem?.returnCode || "--";
    const effectiveOrderCode = orderDetail?.orderCode || qcSummary?.orderCode || returnItem?.orderCode || "--";
    const effectiveReturnStatus = qcSummary?.returnStatus || orderDetail?.status || returnItem?.status;
    const hasInspection = Boolean(inspectionDetail || qcSummary?.inspectionId);

    // Calculate quantities based on authoritative Inventory QC Summary
    const lines: CustomerReturnQcSummaryLine[] = qcSummary?.lines ?? [];
    const totalReturned = lines.length > 0
        ? lines.reduce((sum, l) => sum + toReturnNumber(l.returnedQuantity), 0)
        : (orderDetail?.items ?? returnItem?.items ?? []).reduce(
            (sum, it) => sum + toReturnNumber(it.quantity),
            0
        );

    let totalPosted: number;
    let totalRemaining: number;
    let qcSource: "INVENTORY" | "ORDER_FALLBACK" | "UNAVAILABLE";
    let hasDiscrepancy = false;
    let orderProcessedQuantity: number | undefined;

    if (lines.length > 0) {
        qcSource = "INVENTORY";
        totalPosted = lines.reduce((sum, l) => sum + toReturnNumber(l.postedQcQuantity), 0);
        totalRemaining = lines.reduce((sum, l) => sum + toReturnNumber(l.remainingQcQuantity), 0);

        if (orderDetail?.processedQuantity != null) {
            orderProcessedQuantity = toReturnNumber(orderDetail.processedQuantity);
            if (orderProcessedQuantity !== totalPosted) {
                hasDiscrepancy = true;
            }
        }
    } else if (orderDetail?.processedQuantity != null) {
        qcSource = "ORDER_FALLBACK";
        totalPosted = toReturnNumber(orderDetail.processedQuantity);
        totalRemaining = orderDetail.remainingQuantity != null
            ? toReturnNumber(orderDetail.remainingQuantity)
            : Math.max(0, totalReturned - totalPosted);
    } else if (returnItem?.postedQcQuantity != null) {
        qcSource = "INVENTORY";
        totalPosted = toReturnNumber(returnItem.postedQcQuantity);
        totalRemaining = returnItem.remainingQcQuantity != null
            ? toReturnNumber(returnItem.remainingQcQuantity)
            : Math.max(0, totalReturned - totalPosted);
    } else {
        qcSource = "UNAVAILABLE";
        totalPosted = 0;
        totalRemaining = totalReturned;
    }

    const effQcStatus = qcSummary?.qcStatus || (returnItem?.qcStatus as CustomerReturnQcStatus);
    const isQcCompleted = effQcStatus === "COMPLETED" || (totalReturned > 0 && totalRemaining === 0 && qcSource === "INVENTORY");

    const isPending = !hasInspection && !isQcCompleted && !orderDetail?.physicalDone && Boolean(
        orderDetail?.isPendingDisposition ||
        returnItem?.isPendingDisposition ||
        orderDetail?.displayStatus === "PROCESSING" ||
        returnItem?.displayStatus === "PROCESSING"
    );

    const hasFailed = !isQcCompleted && !orderDetail?.physicalDone && Boolean(
        orderDetail?.hasFailedDisposition ||
        returnItem?.hasFailedDisposition ||
        orderDetail?.displayStatus === "PROCESSING_FAILED" ||
        returnItem?.displayStatus === "PROCESSING_FAILED"
    );

    const qcBadge = resolveQcStatusDisplay(
        effQcStatus,
        effectiveReturnStatus,
        hasInspection
    );

    // Product item lookup map
    const itemsMap = new Map<string, OrderReturnItemResponse>();
    for (const item of orderDetail?.items ?? returnItem?.items ?? []) {
        if (item.id) itemsMap.set(item.id, item);
    }

    // Editable status & lines
    const isEditable = canExecuteQc && Boolean(inspectionDetail && ["CREATED", "PROCESSING"].includes(inspectionDetail.status));
    const pendingLines = inspectionDetail?.lines.filter((line) => !line.posted) ?? [];
    const validationErrors = pendingLines.flatMap((line) => {
        const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
        const msg = getCustomerReturnQcError(line, draft, locations);
        return msg ? [`${line.itemName || line.itemCode || "Sản phẩm"}: ${msg}`] : [];
    });
    const cannotPost = loading || submitting || pendingLines.length === 0 || validationErrors.length > 0;

    const handlePostInspection = async () => {
        if (!inspectionDetail || !isEditable || cannotPost) return;
        setSubmitting(true);
        setError("");
        setSuccessMessage("");
        try {
            const linesPayload = pendingLines.map((line) => {
                const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
                return {
                    lineId: line.id,
                    returnItemId: line.returnItemId,
                    actualCondition: draft.actualCondition || undefined,
                    result: draft.result,
                    destinationLocationId: draft.result === "WRITE_OFF" ? undefined : draft.destinationLocationId || undefined,
                    note: draft.note.trim()
                };
            });
            const updated = await customerReturnApi.postInspection(inspectionDetail.id, { lines: linesPayload });
            setInspectionDetail(updated);
            setDrafts(Object.fromEntries(updated.lines.map((l) => [l.id, createCustomerReturnQcDraft(l)])));
            setSuccessMessage(updated.status === "POSTED"
                ? "Đã hoàn tất kiểm định và hạch toán kho thành công."
                : "Đã cập nhật kiểm định. Một số dòng vẫn đang chờ xử lý.");
            void loadData();
            onSuccess?.();
        } catch (postErr: unknown) {
            setError(getReturnErrorMessage(postErr, "Không thể xác nhận kiểm định kho. Vui lòng thử lại."));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <ModalShell className="w-[min(1080px,96vw)]" onClose={onClose}>
            {/* Kit gap: ModalShell chưa nhận aria-label nên tên hộp thoại đặt ở vùng bên trong. */}
            <section aria-label="Phiếu kiểm định kho" className={styles.modalCard}>
                {/* Header */}
                <div className={styles.modalHeader}>
                    <div className={styles.headerTitleGroup}>
                        <div className="flex flex-col">
                            <div className="flex items-center gap-2.5">
                                <h2>Kiểm định hàng trả</h2>
                                <OrderStatusBadge label={qcBadge.label} status={qcBadge.status} />
                            </div>
                            <span className="text-xs font-mono font-bold text-[#07883a] mt-0.5">
                                {inspectionDetail?.inspectionCode || qcSummary?.inspectionId || effectiveReturnCode}
                            </span>
                        </div>
                        <Button
                            aria-pressed={showInfoDrawer}
                            onClick={() => setShowInfoDrawer((prev) => !prev)}
                            size="sm"
                        >
                            <Info size={14} aria-hidden="true" />
                            Thông tin phiếu
                            {showInfoDrawer ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                        </Button>
                    </div>
                    <IconButton aria-label="Đóng kiểm định kho" onClick={onClose}>
                        <X size={18} />
                    </IconButton>
                </div>

                {/* Body */}
                <div className={styles.modalBody}>
                    {/* Collapsible Info Drawer */}
                    <div
                        className={styles.collapsibleInfoDrawer}
                        style={{ display: showInfoDrawer ? "block" : "none" }}
                        aria-hidden={!showInfoDrawer}
                    >
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                            <div>
                                <span className="text-[#64748b] block font-semibold">Mã phiếu hoàn</span>
                                <strong className="text-[#0f172a] font-bold text-sm">{effectiveReturnCode}</strong>
                            </div>
                            <div>
                                <span className="text-[#64748b] block font-semibold">Mã đơn hàng</span>
                                <strong className="text-[#0f172a] font-bold text-sm">{effectiveOrderCode}</strong>
                            </div>
                            <div>
                                <span className="text-[#64748b] block font-semibold">Thời gian tạo</span>
                                <strong className="text-[#0f172a] font-bold">
                                    {inspectionDetail?.createdAt
                                        ? formatVietnamDateTime(inspectionDetail.createdAt)
                                        : qcSummary?.inspectedAt
                                            ? formatVietnamDateTime(qcSummary.inspectedAt)
                                            : "--"}
                                </strong>
                            </div>
                            <div>
                                <span className="text-[#64748b] block font-semibold">Trạng thái phiếu</span>
                                <div className="mt-0.5">
                                    {inspectionDetail ? (
                                        <InspectionStatusBadge status={inspectionDetail.status} />
                                    ) : (
                                        <OrderStatusBadge label={qcBadge.label} status={qcBadge.status} />
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* QC Quantity Summary Card (3 metrics on 1 row) */}
                    <div className={styles.qcSummaryCard}>
                        <div className={styles.qcSummaryItem}>
                            <span className={styles.qcSummaryLabel}>Tổng SL hàng trả</span>
                            <span className={styles.qcSummaryValue}>
                                <strong>{formatReturnQuantity(totalReturned)}</strong>
                            </span>
                        </div>
                        <div className={styles.qcSummaryItem}>
                            <span className={styles.qcSummaryLabel}>Đã kiểm hàng</span>
                            <span className={styles.qcSummaryValue}>
                                <strong>{formatReturnQuantity(totalPosted)}</strong>
                            </span>
                        </div>
                        <div className={styles.qcSummaryItem}>
                            <span className={styles.qcSummaryLabel}>Còn cần kiểm hàng</span>
                            <span className={styles.qcSummaryValue}>
                                <strong>{formatReturnQuantity(totalRemaining)}</strong>
                            </span>
                        </div>
                    </div>
                    {isEditable && pendingLines.length > 0 ? (
                        <div className={styles.qcProgressLine}>
                            <span>Tiến độ nhập liệu:</span>
                            <span className={styles.qcProgressHighlight}>
                                Đã nhập kết quả {pendingLines.filter(l => {
                                    const d = drafts[l.id];
                                    return d && d.actualCondition && (d.result === "WRITE_OFF" || d.destinationLocationId);
                                }).length}/{pendingLines.length} dòng sản phẩm
                            </span>
                        </div>
                    ) : null}

                    {/* QC Discrepancy & Source Notifications */}
                    {hasDiscrepancy ? (
                        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900" role="alert">
                            <strong>Cảnh báo chênh lệch dữ liệu:</strong> Số lượng đã xử lý ghi nhận tại Đơn hàng ({orderProcessedQuantity}) khác với số lượng đã kiểm định tại Kho ({totalPosted}). Dữ liệu hiển thị theo số liệu thực tế từ Kho. Cần kiểm tra đối soát.
                        </div>
                    ) : null}

                    {!loading && qcSummary == null && (
                        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                            <strong>Chưa tải được QC Summary từ Kho:</strong> Dữ liệu hiển thị tạm thời dựa trên thông tin dự phòng ({qcSource === "ORDER_FALLBACK" ? "Order Fallback" : "Chưa xác định"}).
                        </div>
                    )}

                    {/* Feedback Messages */}
                    {successMessage ? (
                        <div className="flex items-center gap-2 rounded-xl border border-green-200 bg-green-50 p-4 text-sm font-bold text-green-800" role="status">
                            <CheckCircle2 size={18} /> {successMessage}
                        </div>
                    ) : null}

                    {error ? (
                        <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-700" role="alert">
                            <AlertCircle size={18} /> {error}
                        </div>
                    ) : null}

                    {locationError ? (
                        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800" role="alert">
                            {locationError}
                        </div>
                    ) : null}

                    {/* State: Pending Command Banner (Immediate) */}
                    {isPending && !hasInspection ? (
                        <div className="rounded-xl border border-blue-200 bg-blue-50 p-6 text-center text-blue-900">
                            <strong className="block text-base font-bold">
                                Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho.
                            </strong>
                            <p className="mt-2 text-sm text-blue-700">
                                Lệnh điều phối kho đã được gửi tới hệ thống. Sau khi phiếu kiểm định được khởi tạo, bạn có thể mở lại để thực hiện kiểm tra thực tế và hạch toán kho.
                            </p>
                        </div>
                    ) : loading ? (
                        <div className="flex flex-col items-center justify-center gap-2 py-12 text-[#64748b]">
                            <RotateCw className="animate-spin text-[#07883a]" size={26} />
                            <span className="text-sm font-semibold">Đang tải dữ liệu kiểm định kho...</span>
                        </div>
                    ) : null}

                    {/* State: Success but No Inspection Yet */}
                    {!loading && !hasInspection && !isPending ? (
                        <div className="flex flex-col gap-5">
                            {hasFailed ? (
                                <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-center text-amber-900">
                                    <strong className="block text-base font-bold">
                                        Lệnh xử lý kho trước đó không thành công
                                    </strong>
                                    <p className="mt-2 text-sm text-amber-800">
                                        Hệ thống không nhận được xác nhận từ kho hoặc lệnh đã hết hạn chờ. Vui lòng đối soát dữ liệu kho hoặc tạo lại lệnh kiểm định cho số lượng còn lại.
                                    </p>
                                </div>
                            ) : (
                                <div className={styles.emptyState}>
                                    <PackageCheck className="text-[#07883a]" size={36} />
                                    <strong>Chưa có phiếu kiểm định kho</strong>
                                    <p>
                                        Phiếu hoàn {effectiveReturnCode} đang ở trạng thái{" "}
                                        <strong>{qcBadge.label}</strong>, chưa phát sinh phiếu kiểm định kho thực tế.
                                        Thông tin kiểm định sẽ được cập nhật sau khi nhân viên tiếp nhận và xử lý hàng trả.
                                    </p>
                                </div>
                            )}

                            {/* Declared Products List */}
                            <div className={styles.section}>
                                <div className={styles.sectionHeading}>
                                    <h4 className={styles.sectionTitle}>
                                        Sản phẩm khai báo ({lines.length || orderDetail?.items?.length || 0})
                                    </h4>
                                </div>
                                <div className={styles.itemsTableWrap}>
                                    <Table>
                                        <TableHead>
                                            <TableRow>
                                                <TableHeaderCell>Sản phẩm</TableHeaderCell>
                                                <TableHeaderCell>SL trả</TableHeaderCell>
                                                <TableHeaderCell>Tình trạng khai báo</TableHeaderCell>
                                                <TableHeaderCell>Lý do trả</TableHeaderCell>
                                                <TableHeaderCell>Trạng thái QC</TableHeaderCell>
                                            </TableRow>
                                        </TableHead>
                                        <tbody>
                                            {lines.length > 0 ? (
                                                lines.map((line, idx) => {
                                                    const matchedItem = itemsMap.get(line.returnItemId);
                                                    const prodName = matchedItem?.productNameSnapshot || "Sản phẩm hoàn";
                                                    const sku = matchedItem?.skuSnapshot || "--";
                                                    return (
                                                        <TableRow key={line.returnItemId || idx}>
                                                            <TableCell className={styles.productCell}>
                                                                <strong>{prodName}</strong>
                                                                <span className={styles.skuText}>{sku}</span>
                                                            </TableCell>
                                                            <TableCell>
                                                                <strong>{formatReturnQuantity(line.returnedQuantity)}</strong>
                                                            </TableCell>
                                                            <TableCell>{getReturnConditionLabel(line.condition)}</TableCell>
                                                            <TableCell>{getReturnReasonLabel(line.reason as ReturnReason) || "--"}</TableCell>
                                                            <TableCell>
                                                                <OrderStatusBadge label="Chờ kiểm hàng" status="PENDING" />
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })
                                            ) : (orderDetail?.items ?? returnItem?.items ?? []).map((item, idx) => (
                                                <TableRow key={item.id || idx}>
                                                    <TableCell className={styles.productCell}>
                                                        <strong>{item.productNameSnapshot || "Sản phẩm hoàn"}</strong>
                                                        <span className={styles.skuText}>{item.skuSnapshot || "--"}</span>
                                                    </TableCell>
                                                    <TableCell>
                                                        <strong>{formatReturnQuantity(item.quantity)}</strong>
                                                    </TableCell>
                                                    <TableCell>{getReturnConditionLabel(item.condition)}</TableCell>
                                                    <TableCell>{getReturnReasonLabel(item.reason) || "--"}</TableCell>
                                                    <TableCell>
                                                        <OrderStatusBadge label="Chờ kiểm hàng" status="PENDING" />
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </tbody>
                                    </Table>
                                </div>
                            </div>
                        </div>
                    ) : null}

                    {/* State: Success and Has Inspection */}
                    {!loading && hasInspection ? (
                        <div className={styles.cardsSection}>
                            {/* Cards Toolbar */}
                            <div className={styles.cardsToolbar}>
                                <div className="flex items-center gap-2">
                                    <h4 className={styles.cardsTitle}>
                                        Sản phẩm kiểm định ({inspectionDetail?.lines?.length || lines.length})
                                    </h4>
                                    {isEditable ? (
                                        <StatusBadge shape="tag" tone="success">Chế độ kiểm tra &amp; phân bổ kho</StatusBadge>
                                    ) : (
                                        <StatusBadge shape="tag">Chế độ xem kết quả</StatusBadge>
                                    )}
                                </div>

                                {inspectionDetail && inspectionDetail.lines.length > 1 ? (
                                    <Button
                                        size="xs"
                                        variant="link"
                                        onClick={() => {
                                            const anyCollapsed = inspectionDetail.lines.some(
                                                (l) => expandedCards[l.id] === false
                                            );
                                            if (anyCollapsed) {
                                                setExpandedCards({});
                                            } else {
                                                setExpandedCards(
                                                    Object.fromEntries(inspectionDetail.lines.map((l) => [l.id, false]))
                                                );
                                            }
                                        }}
                                        type="button"
                                    >
                                        {inspectionDetail.lines.some((l) => expandedCards[l.id] === false)
                                            ? "Mở rộng tất cả"
                                            : "Thu gọn tất cả"}
                                    </Button>
                                ) : null}
                            </div>

                            {/* Product Cards List */}
                            <div className={styles.cardList}>
                                {inspectionDetail && inspectionDetail.lines.length > 0 ? (
                                    inspectionDetail.lines.map((inspLine) => {
                                        const matchedItem = itemsMap.get(inspLine.returnItemId);
                                        const lineDraft = drafts[inspLine.id] ?? createCustomerReturnQcDraft(inspLine);
                                        const isLineEditable = isEditable && !inspLine.posted;
                                        return (
                                            <CustomerReturnInspectionCard
                                                attemptedSubmit={attemptedSubmit}
                                                declaredCondition={inspLine.declaredCondition ?? inspLine.condition}
                                                draft={lineDraft}
                                                editable={isLineEditable}
                                                isCollapsible={inspectionDetail.lines.length > 1}
                                                isExpanded={expandedCards[inspLine.id] !== false}
                                                key={inspLine.id}
                                                line={inspLine}
                                                locations={locations}
                                                onChange={(update) => {
                                                    setDrafts((curr) => ({
                                                        ...curr,
                                                        [inspLine.id]: {
                                                            ...(curr[inspLine.id] ?? createCustomerReturnQcDraft(inspLine)),
                                                            ...update
                                                        }
                                                    }));
                                                    setError("");
                                                }}
                                                onFieldTouch={(field) =>
                                                    setTouchedFields((prev) => ({
                                                        ...prev,
                                                        [inspLine.id]: {
                                                            ...(prev[inspLine.id] ?? {}),
                                                            [field]: true
                                                        }
                                                    }))
                                                }
                                                onToggleExpand={() =>
                                                    setExpandedCards((prev) => ({
                                                        ...prev,
                                                        [inspLine.id]: prev[inspLine.id] === false
                                                    }))
                                                }
                                                productName={inspLine.itemName || matchedItem?.productNameSnapshot}
                                                sku={inspLine.itemCode || matchedItem?.skuSnapshot}
                                                touched={touchedFields[inspLine.id] ?? {}}
                                            />
                                        );
                                    })
                                ) : lines.map((line, idx) => {
                                    const matchedItem = itemsMap.get(line.returnItemId);
                                    const prodName = matchedItem?.productNameSnapshot || "Sản phẩm hoàn";
                                    const sku = matchedItem?.skuSnapshot || "--";
                                    const disposition = line.dispositions && line.dispositions.length > 0
                                        ? line.dispositions[0]
                                        : null;

                                    return (
                                        <div className={styles.inspectionCard} key={line.returnItemId || idx}>
                                            <div className={styles.cardHeader}>
                                                <div className={styles.cardHeaderInfo}>
                                                    <strong className={styles.cardProductName}>{prodName}</strong>
                                                    <span className={styles.skuBadge}>{sku}</span>
                                                    <span className="text-xs text-[#64748b] ml-2">
                                                        Số lượng: <strong>{formatReturnQuantity(line.returnedQuantity)}</strong>
                                                    </span>
                                                </div>
                                            </div>
                                            <div className={styles.cardBody}>
                                                <div className={styles.readOnlyGrid}>
                                                    <div className={styles.readOnlyItem}>
                                                        <span className={styles.readOnlyLabel}>Tình trạng khai báo</span>
                                                        <span className={styles.readOnlyValue}>{getReturnConditionLabel(line.condition)}</span>
                                                    </div>
                                                    <div className={styles.readOnlyItem}>
                                                        <span className={styles.readOnlyLabel}>Phương án xử lý</span>
                                                        <span className={styles.readOnlyValue}>
                                                            {disposition ? (RESULT_LABELS[disposition.result] ?? disposition.result) : "Chưa phân loại"}
                                                        </span>
                                                    </div>
                                                    <div className={styles.readOnlyItem}>
                                                        <span className={styles.readOnlyLabel}>Vị trí đích</span>
                                                        <span className={styles.readOnlyValue}>
                                                            {disposition?.destinationLocationName || "--"}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    ) : null}
                </div>

                {/* Sticky Footer */}
                <div className={styles.modalFooter}>
                    <div className="flex items-center gap-2">
                        {isEditable && attemptedSubmit && validationErrors.length > 0 ? (
                            <span className="text-xs font-bold text-red-600 flex items-center gap-1.5" role="alert">
                                <AlertCircle size={14} />
                                Còn {validationErrors.length} dòng sản phẩm cần hoàn thiện thông tin trước khi xác nhận.
                            </span>
                        ) : isEditable && pendingLines.length > 0 ? (
                            <span className="text-xs font-semibold text-slate-500">
                                Kiểm tra thực tế và phân bổ vị trí kho cho từng sản phẩm trước khi xác nhận.
                            </span>
                        ) : null}
                    </div>
                    <div className="flex items-center gap-2">
                        <Button onClick={onClose}>Đóng</Button>
                        {isEditable && pendingLines.length > 0 ? (
                            <Button
                                disabled={submitting}
                                onClick={() => {
                                    if (validationErrors.length > 0) {
                                        setAttemptedSubmit(true);
                                        const firstInvalid = pendingLines.find((line) => {
                                            const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
                                            return Boolean(getCustomerReturnQcError(line, draft, locations));
                                        });
                                        if (firstInvalid) {
                                            setExpandedCards((prev) => ({ ...prev, [firstInvalid.id]: true }));
                                            window.setTimeout(() => {
                                                const el = document.getElementById(`card-${firstInvalid.id}`);
                                                el?.scrollIntoView({ behavior: "smooth", block: "center" });
                                            }, 50);
                                        }
                                        return;
                                    }
                                    void handlePostInspection();
                                }}
                                variant="primary"
                            >
                                {submitting ? (
                                    <>
                                        <RotateCw className="animate-spin" size={15} />
                                        Đang xử lý...
                                    </>
                                ) : (
                                    "Xác nhận xử lý"
                                )}
                            </Button>
                        ) : null}
                    </div>
                </div>
            </section>
        </ModalShell>
    );
}
