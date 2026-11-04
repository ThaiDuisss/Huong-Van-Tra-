import { ExternalLink, PackageCheck, Plus, RefreshCw, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "../../../auth/role-utils";

import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import {
    getCustomerReturnQcSummary,
    getOrderReturnDetail,
    receiveOrderReturn
} from "../../order/services/order-return.api";
import type {
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow,
    OrderReturnResponse
} from "../../order/types/order-return.types";
import {
    canInspectCustomerReturn,
    formatReturnQuantity,
    getCustomerReturnQcProgressStatus,
    getCustomerReturnUiStatus,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    resolveCustomerReturnQuantities,
    toReturnNumber
} from "../../order/utils/order-return.utils";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { customerReturnApi } from "../services/customerReturn.api";
import type { CustomerReturnInspection } from "../types/customer-return-inspection.types";
import { InspectionStatusBadge } from "./CustomerReturnInspectionComponents";
import { CustomerReturnWarehouseInspectionModal } from "./CustomerReturnWarehouseInspectionModal";
import styles from "./CustomerReturnDetailModal.module.css";

interface CustomerReturnDetailModalProps {
    isOpen: boolean;
    onClose: () => void;
    returnItem: OrderReturnListRow;
    onRefreshList: () => void;
    onOpenInspection: (returnDetail: OrderReturnListRow, qcSummary: CustomerReturnQcSummaryResponse) => void;
}

export function CustomerReturnDetailModal({
    isOpen,
    onClose,
    returnItem,
    onRefreshList,
    onOpenInspection
}: CustomerReturnDetailModalProps) {
    const { hasAnyRole } = useAuthorization();
    const canExecuteQc = hasAnyRole(CUSTOMER_RETURN_QC_ROLES);
    const [detail, setDetail] = useState<OrderReturnResponse | null>(null);
    const [inspections, setInspections] = useState<CustomerReturnInspection[]>([]);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState("");
    const [isReceiving, setIsReceiving] = useState(false);
    const [receiveMessage, setReceiveMessage] = useState("");
    const [showReceiveConfirm, setShowReceiveConfirm] = useState(false);
    const [receiveConfirmError, setReceiveConfirmError] = useState("");
    const dialogRef = useRef<HTMLElement>(null);
    const [selectedInspection, setSelectedInspection] = useState<CustomerReturnInspection | null>(null);

    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        if (!isOpen) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setIsLoading(true);
            setError("");
            Promise.all([
                getOrderReturnDetail(returnItem.id),
                customerReturnApi.listInspections({
                    fromDate: undefined,
                    orderCode: "",
                    page: 0,
                    returnCode: returnItem.returnCode ?? "",
                    size: 20,
                    status: "ALL",
                    toDate: undefined
                }),
                getCustomerReturnQcSummary(returnItem.id).catch(() => null)
            ])
                .then(([returnDetail, inspectionRes, qcRes]) => {
                    if (!active) return;
                    setDetail(returnDetail);
                    setInspections((inspectionRes?.content ?? []).filter((inspection) => inspection.returnId === returnItem.id));
                    setQcSummary(qcRes);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setIsLoading(false);
                });
        });

        return () => {
            active = false;
        };
    }, [isOpen, returnItem.id, returnItem.returnCode, reloadKey]);

    useEffect(() => {
        if (!isOpen) return;
        const previous = document.activeElement;
        dialogRef.current?.focus({ preventScroll: true });
        return () => {
            if (previous instanceof HTMLElement && previous.isConnected) previous.focus({ preventScroll: true });
        };
    }, [isOpen]);

    if (!isOpen) return null;

    const currentReturn = detail ?? returnItem;
    const items = detail?.items ?? returnItem.items ?? [];
    const isCreated = currentReturn.status === "CREATED";
    const isReceived = currentReturn.status === "RECEIVED";
    const isCompleted = currentReturn.status === "COMPLETED";

    const postedFromQc = qcSummary?.lines
        ? qcSummary.lines.reduce((s, l) => s + toReturnNumber(l.postedQcQuantity), 0)
        : (qcSummary ? 0 : undefined);
    const remainingFromQc = qcSummary?.lines
        ? qcSummary.lines.reduce((s, l) => s + toReturnNumber(l.remainingQcQuantity), 0)
        : (qcSummary ? 0 : undefined);

    const effQcStatus = qcSummary?.qcStatus ?? returnItem.qcStatus ?? null;
    const effPostedQcQuantity = postedFromQc ?? returnItem.postedQcQuantity ?? undefined;
    const effRemainingQcQuantity = remainingFromQc ?? returnItem.remainingQcQuantity ?? undefined;

    const quantities = resolveCustomerReturnQuantities({
        ...currentReturn,
        postedQcQuantity: effPostedQcQuantity,
        remainingQcQuantity: effRemainingQcQuantity,
        qcStatus: effQcStatus,
        items
    });

    const canInspect = !isLoading && !error && detail != null && canExecuteQc && canInspectCustomerReturn({
        ...currentReturn,
        postedQcQuantity: effPostedQcQuantity,
        remainingQcQuantity: effRemainingQcQuantity,
        qcStatus: effQcStatus,
        items
    });

    const overallUiStatus = getCustomerReturnUiStatus(currentReturn);
    const qcProgressStatus = getCustomerReturnQcProgressStatus({
        ...currentReturn,
        postedQcQuantity: effPostedQcQuantity,
        remainingQcQuantity: effRemainingQcQuantity,
        qcStatus: effQcStatus,
        items
    });

    const handleConfirmReceive = () => {
        setIsReceiving(true);
        setReceiveConfirmError("");
        setReceiveMessage("");
        receiveOrderReturn(returnItem.id)
            .then((updated) => {
                setDetail(updated);
                setReceiveMessage("Đã xác nhận nhận hàng thành công.");
                setShowReceiveConfirm(false);
                onRefreshList();
                setReloadKey((k) => k + 1);
            })
            .catch((err: unknown) => {
                setReceiveConfirmError(getReturnErrorMessage(err));
            })
            .finally(() => {
                setIsReceiving(false);
            });
    };

    const handleStartInspection = () => {
        if (!canInspect) return;
        setIsLoading(true);
        Promise.all([getOrderReturnDetail(returnItem.id), getCustomerReturnQcSummary(returnItem.id)])
            .then(([latest, summary]) => {
                setDetail(latest);
                setQcSummary(summary);
                if (!canInspectCustomerReturn({ ...latest, qcStatus: summary.qcStatus })) {
                    setError("Phiếu chưa sẵn sàng cho yêu cầu kiểm định mới. Vui lòng kiểm tra tiến độ xử lý hoặc liên hệ quản lý để đối soát.");
                    return;
                }
                onOpenInspection(latest, summary);
            })
            .catch((err: unknown) => setError(getReturnErrorMessage(err)))
            .finally(() => setIsLoading(false));
    };

    return (
        <div className={styles.modalBackdrop} onClick={onClose} role="presentation">
            <aside
                aria-label="Chi tiết phiếu khách trả hàng"
                aria-modal="true"
                className={styles.modalCard}
                onClick={(e) => e.stopPropagation()}
                ref={dialogRef}
                role="dialog"
                tabIndex={-1}
            >
                <div className={styles.modalHeader}>
                    <div className={styles.headerTitleGroup}>
                        <h2>{currentReturn.returnCode ?? "Chi tiết phiếu hoàn"}</h2>
                        <span className={styles.orderReference}>
                            Đơn hàng: {currentReturn.orderCode ?? "--"}
                        </span>
                        <OrderStatusBadge
                            label={overallUiStatus.label}
                            status={overallUiStatus.status}
                        />
                    </div>
                    <button
                        aria-label="Đóng chi tiết"
                        className={styles.closeButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X aria-hidden="true" size={18} />
                    </button>
                </div>

                <div className={styles.modalBody}>
                    {receiveMessage ? (
                        <div className={styles.noteBox} style={{ background: "#f0fdf4", borderColor: "#bbf7d0", color: "#166534" }}>
                            <strong>Thành công</strong>
                            <p>{receiveMessage}</p>
                        </div>
                    ) : null}

                    {error ? (
                        <div className={styles.noteBox} style={{ background: "#fef2f2", borderColor: "#fecaca", color: "#991b1b" }}>
                            <strong>Lỗi</strong>
                            <p>{error}</p>
                        </div>
                    ) : null}

                    {/* 1. Tổng quan phiếu hoàn */}
                    <section className={styles.section}>
                        <div className={styles.sectionHeading}>
                            <h3 className={styles.sectionTitle}>Thông tin phiếu hoàn</h3>
                            {isCreated ? (
                                <button
                                    className={styles.primaryButton}
                                    disabled={isReceiving}
                                    onClick={() => {
                                        setReceiveConfirmError("");
                                        setShowReceiveConfirm(true);
                                    }}
                                    type="button"
                                >
                                    <PackageCheck aria-hidden="true" size={16} />
                                    {isReceiving ? "Đang nhận hàng..." : "Xác nhận nhận hàng"}
                                </button>
                            ) : null}
                        </div>

                        <div className={styles.infoGrid}>
                            <div className={styles.infoItem}>
                                <span className={styles.infoLabel}>Khách hàng</span>
                                <span className={styles.infoValue}>{currentReturn.customerName || "Khách lẻ"}</span>
                            </div>
                            <div className={styles.infoItem}>
                                <span className={styles.infoLabel}>Số điện thoại</span>
                                <span className={styles.infoValue}>{currentReturn.customerPhone || "--"}</span>
                            </div>
                            <div className={styles.infoItem}>
                                <span className={styles.infoLabel}>Ngày tạo phiếu</span>
                                <span className={styles.infoValue}>{currentReturn.createdAt ? formatVietnamDateTime(currentReturn.createdAt) : "--"}</span>
                            </div>
                        </div>

                        {currentReturn.note ? (
                            <div className={styles.noteBox}>
                                <strong>Ghi chú hoàn hàng</strong>
                                <p>{currentReturn.note}</p>
                            </div>
                        ) : null}
                    </section>

                    {/* 2. Sản phẩm hoàn */}
                    <section className={styles.section}>
                        <div className={styles.sectionHeading}>
                            <h3 className={styles.sectionTitle}>Sản phẩm khách trả ({items.length})</h3>
                        </div>

                        <div className={styles.itemsTableWrap}>
                            <table className={styles.itemsTable}>
                                <thead>
                                    <tr>
                                        <th>Sản phẩm</th>
                                        <th style={{ width: "12%", textAlign: "center" }}>Số lượng</th>
                                        <th style={{ width: "22%" }}>Lý do trả</th>
                                        <th style={{ width: "22%" }}>Tình trạng</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.length === 0 ? (
                                        <tr>
                                            <td colSpan={4} style={{ textAlign: "center", color: "#64748b", padding: "16px" }}>
                                                {isLoading ? "Đang tải sản phẩm..." : "Không có thông tin dòng sản phẩm"}
                                            </td>
                                        </tr>
                                    ) : (
                                        items.map((item, idx) => (
                                            <tr key={item.id ?? idx}>
                                                <td className={styles.productCell}>
                                                    <strong>{item.productNameSnapshot || "Sản phẩm"}</strong>
                                                    <span>
                                                        SKU: {item.skuSnapshot || "--"}
                                                        {item.variantNameSnapshot ? ` · ${item.variantNameSnapshot}` : ""}
                                                    </span>
                                                </td>
                                                <td style={{ textAlign: "center", fontWeight: 700 }}>
                                                    {formatReturnQuantity(item.quantity)}
                                                </td>
                                                <td>{getReturnReasonLabel(item.reason)}</td>
                                                <td>{getReturnConditionLabel(item.condition)}</td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </section>

                    {/* 3. Section Kiểm định kho (Yêu cầu chính) */}
                    <section className={styles.section}>
                        <div className={styles.sectionHeading}>
                            <h3 className={styles.sectionTitle}>
                                Kiểm định kho ({inspections.length} phiếu)
                            </h3>
                            {(() => {
                                const createdInspection = inspections.find((insp) => insp.status === "CREATED");
                                const processingInspection = inspections.find((insp) => insp.status === "PROCESSING");
                                const postedInspection = inspections.find((insp) => insp.status === "POSTED");

                                if (createdInspection && canExecuteQc) {
                                    return (
                                        <button
                                            className={styles.primaryButton}
                                            onClick={() => setSelectedInspection(createdInspection)}
                                            type="button"
                                        >
                                            <Plus aria-hidden="true" size={16} />
                                            Mở phiếu kiểm định
                                        </button>
                                    );
                                }
                                if (processingInspection && canExecuteQc) {
                                    return (
                                        <button
                                            className={styles.primaryButton}
                                            onClick={() => setSelectedInspection(processingInspection)}
                                            type="button"
                                        >
                                            <Plus aria-hidden="true" size={16} />
                                            Tiếp tục kiểm định
                                        </button>
                                    );
                                }
                                if (canInspect) {
                                    return (
                                        <button
                                            className={styles.primaryButton}
                                            onClick={handleStartInspection}
                                            type="button"
                                        >
                                            <Plus aria-hidden="true" size={16} />
                                            Bắt đầu kiểm định
                                        </button>
                                    );
                                }
                                if (postedInspection) {
                                    return (
                                        <button
                                            className={styles.secondaryButton}
                                            onClick={() => setSelectedInspection(postedInspection)}
                                            type="button"
                                        >
                                            Xem kết quả kiểm định
                                        </button>
                                    );
                                }
                                return null;
                            })()}
                        </div>

                        {quantities.hasDiscrepancy ? (
                            <div className={styles.noteBox} style={{ background: "#fffbeb", borderColor: "#fde68a", color: "#92400e", marginBottom: 12 }}>
                                <strong>Cảnh báo chênh lệch dữ liệu kiểm định giữa Đơn hàng và Kho</strong>
                                <p>
                                    Số lượng xử lý ghi nhận tại Đơn hàng ({quantities.orderProcessedQuantity ?? 0}) khác với số lượng đã kiểm định tại Kho ({quantities.inventoryPostedQuantity ?? 0}). Tiến độ đang hiển thị theo số liệu thực tế từ Kho ({quantities.processed} / {quantities.required}). Cần kiểm tra đối soát.
                                </p>
                            </div>
                        ) : null}

                        {!isLoading && qcSummary == null && !error ? (
                            <div className={styles.noteBox} style={{ background: "#f8fafc", borderColor: "#e2e8f0", color: "#475569", marginBottom: 12 }}>
                                <strong>Chưa tải được QC Summary từ Kho</strong>
                                <p>Tiến độ kiểm định đang hiển thị theo nguồn dự phòng từ Đơn hàng ({quantities.source === "ORDER_FALLBACK" ? "Order Fallback" : "Chưa xác định"}).</p>
                            </div>
                        ) : null}

                        {inspections.length === 0 &&
                        !qcSummary?.inspectionId &&
                        effQcStatus !== "COMPLETED" &&
                        !currentReturn.physicalDone &&
                        (currentReturn.isPendingDisposition ||
                         currentReturn.inventoryProcessingStatus === "PROCESSING" ||
                         currentReturn.displayStatus === "PROCESSING") ? (
                            <div className={styles.noteBox} style={{ background: "#eff6ff", borderColor: "#bfdbfe", color: "#1e40af", marginBottom: 12 }}>
                                <strong>Đang tạo phiếu kiểm định</strong>
                                <p>Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho.</p>
                            </div>
                        ) : null}

                        {effQcStatus !== "COMPLETED" &&
                        !currentReturn.physicalDone &&
                        (currentReturn.hasFailedDisposition ||
                         currentReturn.inventoryProcessingStatus === "FAILED" ||
                         currentReturn.displayStatus === "PROCESSING_FAILED") ? (
                            <div className={styles.noteBox} style={{ background: "#fff7ed", borderColor: "#fed7aa", color: "#9a3412", marginBottom: 12 }}>
                                <strong>Lệnh xử lý kho trước đó không thành công</strong>
                                <p>Cần đối soát yêu cầu với kho trước khi gửi lại để tránh xử lý trùng số lượng. Vui lòng liên hệ quản lý.</p>
                            </div>
                        ) : null}

                        {/* QC Summary Block */}
                        <div className={styles.qcSummaryCard}>
                            <div className={styles.qcSummaryItem}>
                                <span className={styles.qcSummaryLabel}>Tiến độ kiểm định</span>
                                <span className={styles.qcSummaryValue}>
                                    <strong>{formatReturnQuantity(quantities.processed)}</strong>&nbsp;/&nbsp;{formatReturnQuantity(quantities.required)}
                                </span>
                            </div>
                            <div className={styles.qcSummaryItem}>
                                <span className={styles.qcSummaryLabel}>Còn lại</span>
                                <span className={styles.qcSummaryValue}>
                                    {formatReturnQuantity(quantities.remaining)}
                                </span>
                            </div>
                            <div className={styles.qcSummaryItem}>
                                <span className={styles.qcSummaryLabel}>Trạng thái QC</span>
                                <span className={styles.qcSummaryValue}>
                                    <OrderStatusBadge
                                        label={qcProgressStatus.label}
                                        status={qcProgressStatus.status}
                                    />
                                </span>
                            </div>
                        </div>

                        {isLoading ? (
                            <div className={styles.emptyState}>
                                <RefreshCw className="animate-spin text-[#07883a]" size={20} />
                                <span>Đang tải thông tin kiểm định...</span>
                            </div>
                        ) : inspections.length > 0 ? (
                            <div className={styles.inspectionList}>
                                {inspections.map((insp) => {
                                    const totalQty = insp.lines.reduce((s, l) => s + Number(l.quantity), 0);
                                    return (
                                        <div className={styles.inspectionCard} key={insp.id}>
                                            <div className={styles.inspectionCardHeader}>
                                                <button
                                                    type="button"
                                                    className={styles.inspectionCodeLink}
                                                    style={{ background: "none", border: "none", padding: 0, cursor: "pointer", font: "inherit" }}
                                                    title="Mở chi tiết phiếu kiểm định kho"
                                                    onClick={() => setSelectedInspection(insp)}
                                                >
                                                    {insp.inspectionCode}
                                                    <ExternalLink size={14} />
                                                </button>
                                                <InspectionStatusBadge status={insp.status} />
                                            </div>

                                            <div className={styles.inspectionMetaRow}>
                                                <span>
                                                    Số dòng: <strong>{formatVietnamNumber(insp.lines.length)}</strong>
                                                </span>
                                                <span>
                                                    Tổng số lượng: <strong>{formatVietnamNumber(totalQty)}</strong>
                                                </span>
                                                <span>
                                                    Ngày tạo: <strong>{formatVietnamDateTime(insp.createdAt)}</strong>
                                                </span>
                                                {insp.postedAt ? (
                                                    <span>
                                                        Xử lý kho lúc: <strong>{formatVietnamDateTime(insp.postedAt)}</strong>
                                                    </span>
                                                ) : null}
                                                <button
                                                    type="button"
                                                    className={styles.externalLink}
                                                    style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer", font: "inherit" }}
                                                    onClick={() => setSelectedInspection(insp)}
                                                >
                                                    {insp.status === "CREATED"
                                                        ? "Mở phiếu kiểm định"
                                                        : insp.status === "PROCESSING"
                                                            ? "Tiếp tục kiểm định"
                                                            : "Xem kết quả kiểm định"}
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className={styles.emptyState}>
                                <strong>Chưa tạo phiếu kiểm định</strong>
                                <p>
                                    {isCreated
                                        ? "Phiếu hoàn đang chờ nhận hàng tại cửa hàng. Sau khi nhận hàng, nhân viên có thể tiến hành kiểm định chất lượng."
                                        : isReceived
                                            ? "Phiếu hoàn chưa có phiếu kiểm định nào được ghi nhận. Bạn có thể bấm 'Bắt đầu kiểm định' ở đầu phần này."
                                            : isCompleted
                                                ? "Phiếu hoàn đã hoàn tất quy trình."
                                                : "Phiếu hoàn không phát sinh kiểm định."}
                                </p>
                            </div>
                        )}
                    </section>
                </div>

                <div className={styles.modalFooter}>
                    <Link
                        className={styles.externalLink}
                        to={`/management/orders/returns/${currentReturn.id}`}
                    >
                        Xem chi tiết hoàn tiền & timeline đơn hoàn <ExternalLink size={14} />
                    </Link>
                    <button className={styles.secondaryButton} onClick={onClose} type="button">
                        Đóng
                    </button>
                </div>
            </aside>

            {showReceiveConfirm ? (
                <ConfirmDialog
                    confirmLabel="Xác nhận nhận hàng"
                    confirmVariant="primary"
                    description={`Bạn xác nhận đã nhận hàng của phiếu hoàn ${currentReturn.returnCode ?? "này"}?`}
                    error={receiveConfirmError}
                    onCancel={() => {
                        if (!isReceiving) {
                            setShowReceiveConfirm(false);
                            setReceiveConfirmError("");
                        }
                    }}
                    onConfirm={handleConfirmReceive}
                    submitting={isReceiving}
                    title="Xác nhận nhận hàng"
                />
            ) : null}

            {selectedInspection ? (
                <CustomerReturnWarehouseInspectionModal
                    isOpen
                    onClose={() => setSelectedInspection(null)}
                    returnId={currentReturn.id}
                    returnCode={currentReturn.returnCode}
                    returnItem={currentReturn}
                    inspectionId={selectedInspection.id}
                    onSuccess={() => {
                        onRefreshList();
                        setReloadKey((k) => k + 1);
                    }}
                />
            ) : null}
        </div>
    );
}
