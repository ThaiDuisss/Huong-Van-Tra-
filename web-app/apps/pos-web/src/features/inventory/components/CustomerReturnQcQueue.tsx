import { useEffect, useState } from "react";

import type { TimeRange } from "../../../components/shared/TimeHierarchyFilter.utils";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import orderStyles from "../../order/pages/OrderListPage.module.css";
import { ReturnInspectionModal } from "../../order/components/ReturnInspectionModal";
import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import {
    getCustomerReturnQcSummary,
    getOrderReturnDetail,
    getOrderReturnList,
    getOrderReturnStatusCounts,
    receiveOrderReturn
} from "../../order/services/order-return.api";
import type {
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow
} from "../../order/types/order-return.types";
import {
    canInspectCustomerReturn,
    getCustomerReturnUiStatus,
    getReturnErrorMessage,
    toReturnNumber
} from "../../order/utils/order-return.utils";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import styles from "../pages/CustomerReturnListPage.module.css";
import {
    type CustomerReturnStatusTab,
    ListFilterBar,
    ListMessage,
    ListPagination
} from "./CustomerReturnListUi";
import { CustomerReturnDetailModal } from "./CustomerReturnDetailModal";

const PAGE_SIZE = 10;
const CUSTOMER_RETURN_STATUS_TABS: CustomerReturnStatusTab[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Chờ nhận hàng" },
    { key: "RECEIVED", label: "Chờ kiểm định" },
    { key: "PROCESSING", label: "Đang xử lý" },
    { key: "COMPLETED", label: "Hoàn thành" },
    { key: "CANCELLED", label: "Đã hủy" },
    { key: "PROCESSING_FAILED", label: "Có lỗi", isError: true }
];

const INITIAL_STATUS_COUNTS: Record<string, number> = {
    ALL: 0,
    CREATED: 0,
    RECEIVED: 0,
    PROCESSING: 0,
    COMPLETED: 0,
    CANCELLED: 0,
    PROCESSING_FAILED: 0
};

export function CustomerReturnQcQueue({
    onInspectionCreated,
    onPageChange,
    page,
    timeRange
}: {
    onInspectionCreated?: () => void;
    onPageChange: (page: number) => void;
    page: number;
    timeRange?: TimeRange | null;
}) {
    const [searchTerm, setSearchTerm] = useState("");
    const [status, setStatus] = useState("ALL");
    const [statusCounts, setStatusCounts] = useState<Record<string, number>>(INITIAL_STATUS_COUNTS);
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 300);
    const [reloadKey, setReloadKey] = useState(0);
    const [rows, setRows] = useState<OrderReturnListRow[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Inspection modal state
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [selectedReturn, setSelectedReturn] = useState<OrderReturnListRow | null>(null);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [selectionLoading, setSelectionLoading] = useState(false);
    const [selectionError, setSelectionError] = useState("");

    // Detail modal state
    const [detailModalItem, setDetailModalItem] = useState<OrderReturnListRow | null>(null);
    const [receivingId, setReceivingId] = useState<string | null>(null);
    const [confirmReceiveRow, setConfirmReceiveRow] = useState<OrderReturnListRow | null>(null);
    const [receiveConfirmError, setReceiveConfirmError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        let active = true;
        getOrderReturnStatusCounts({
            fromDate: timeRange?.fromDate,
            keyword: debouncedSearchTerm || undefined,
            toDate: timeRange?.toDate,
            warehouseActiveOnly: true
        })
            .then((counts) => {
                if (!active || !counts) return;
                setStatusCounts({
                    ALL: counts.ALL ?? 0,
                    CREATED: counts.CREATED ?? 0,
                    RECEIVED: counts.RECEIVED ?? 0,
                    PROCESSING: counts.PROCESSING ?? 0,
                    COMPLETED: counts.COMPLETED ?? 0,
                    CANCELLED: counts.CANCELLED ?? 0,
                    PROCESSING_FAILED: counts.PROCESSING_FAILED ?? 0
                });
            })
            .catch((countsErr: unknown) => {
                if (active) {
                    console.warn("Không thể tải số lượng trạng thái phiếu hoàn:", countsErr);
                }
            });

        return () => {
            active = false;
        };
    }, [debouncedSearchTerm, reloadKey, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        if (searchTerm.trim() !== debouncedSearchTerm) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            getOrderReturnList({
                displayStatus: status === "ALL" ? undefined : status,
                fromDate: timeRange?.fromDate,
                keyword: debouncedSearchTerm || undefined,
                page,
                paged: true,
                size: PAGE_SIZE,
                sort: "createdAt,desc",
                status: status === "ALL" ? undefined : status,
                toDate: timeRange?.toDate,
                warehouseActiveOnly: true
            })
                .then((response) => {
                    if (!active) return;
                    setRows(response.content ?? []);
                    setTotalPages(response.totalPages ?? 0);
                    setTotalElements(response.totalElements ?? 0);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });
        return () => { active = false; };
    }, [page, reloadKey, status, searchTerm, debouncedSearchTerm, timeRange?.fromDate, timeRange?.toDate]);


    useEffect(() => {
        if (!selectedId) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setSelectionLoading(true);
            setSelectionError("");
            setSelectedReturn(null);
            setQcSummary(null);
            Promise.all([
                getOrderReturnDetail(selectedId),
                getCustomerReturnQcSummary(selectedId)
            ])
                .then(([detail, summary]) => {
                    if (!active) return;
                    if (detail.status !== "RECEIVED" || summary.returnStatus !== "RECEIVED") {
                        setSelectionError("Phiếu hoàn không còn ở trạng thái đã nhận hàng. Vui lòng tải lại danh sách.");
                        return;
                    }
                    if (!(summary.lines ?? []).some((line) => toReturnNumber(line.remainingQcQuantity) > 0)) {
                        setSelectionError("Phiếu hoàn không còn số lượng cần xử lý kho. Vui lòng tải lại danh sách.");
                        return;
                    }
                    setSelectedReturn(detail);
                    setQcSummary(summary);
                })
                .catch((loadError: unknown) => {
                    if (active) setSelectionError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setSelectionLoading(false);
                });
        });
        return () => { active = false; };
    }, [selectedId]);

    const reload = () => {
        setReloadKey((key) => key + 1);
        onInspectionCreated?.();
    };

    const closeSelection = () => {
        setSelectedId(null);
        setSelectedReturn(null);
        setQcSummary(null);
        setSelectionError("");
    };

    const handleConfirmReceive = (row: OrderReturnListRow) => {
        setReceivingId(row.id);
        setReceiveConfirmError("");
        receiveOrderReturn(row.id)
            .then(() => {
                setNotice(`Đã xác nhận nhận hàng cho phiếu ${row.returnCode ?? ""}.`);
                setConfirmReceiveRow(null);
                reload();
            })
            .catch((err: unknown) => {
                setReceiveConfirmError(getReturnErrorMessage(err));
            })
            .finally(() => {
                setReceivingId(null);
            });
    };

    return (
        <section className={styles.section}>
            <ListFilterBar
                activeStatus={status}
                onSearchChange={(value) => {
                    setSearchTerm(value);
                    onPageChange(0);
                }}
                onStatusChange={(value) => {
                    setStatus(value);
                    onPageChange(0);
                }}
                searchTerm={searchTerm}
                statusCounts={statusCounts}
                statusTabs={CUSTOMER_RETURN_STATUS_TABS}
            />

            {notice ? <div className={styles.notice}>{notice}</div> : null}
            {error ? <div className={styles.error}>{error}</div> : null}

            <div className={orderStyles.tableCard}>
                <div className={orderStyles.tableWrap}>
                    <table className={`${orderStyles.orderTable} ${styles.dataTable} ${styles.returnTable}`}>
                        <thead>
                            <tr>
                                <th>Phiếu hoàn</th>
                                <th>Trạng thái</th>
                                <th>Đơn hàng</th>
                                <th>Thao tác</th>
                            </tr>
                        </thead>
                        <tbody>
                            {loading ? <ListMessage colSpan={4} message="Đang tải danh sách phiếu hoàn..." /> : null}
                            {!loading && error ? <ListMessage colSpan={4} message="Không thể hiển thị phiếu hoàn hàng" /> : null}
                            {!loading && !error && rows.map((row, index) => {
                                const canInspect = canInspectCustomerReturn(row);
                                const statusInfo = getCustomerReturnUiStatus(row);

                                return (
                                    <tr className={index % 2 === 0 ? orderStyles.zebraFirstRow : orderStyles.zebraSecondRow} key={row.id}>
                                        <td>
                                            <button
                                                className={styles.codeButton}
                                                onClick={() => setDetailModalItem(row)}
                                                title="Nhấn để xem chi tiết phiếu hoàn"
                                                type="button"
                                            >
                                                {row.returnCode ?? "Chưa có mã phiếu"}
                                            </button>
                                        </td>
                                        <td>
                                            <OrderStatusBadge
                                                label={statusInfo.label}
                                                status={statusInfo.status}
                                            />
                                        </td>
                                        <td>
                                            <span className={styles.orderCodeText}>
                                                {row.orderCode ?? "--"}
                                            </span>
                                        </td>
                                        <td>
                                            {row.status === "CREATED" ? (
                                                <button
                                                    className={styles.actionButtonPrimary}
                                                    disabled={receivingId === row.id}
                                                    onClick={() => {
                                                        setReceiveConfirmError("");
                                                        setConfirmReceiveRow(row);
                                                    }}
                                                    type="button"
                                                >
                                                    {receivingId === row.id ? "Đang nhận..." : "Nhận hàng"}
                                                </button>
                                            ) : canInspect ? (
                                                <button
                                                    className={styles.actionButtonPrimary}
                                                    onClick={() => setSelectedId(row.id)}
                                                    type="button"
                                                >
                                                    {row.qcStatus === "PARTIAL" ? "Tiếp tục kiểm định" : "Kiểm định"}
                                                </button>
                                            ) : row.isPendingDisposition ? (
                                                <span className={styles.mutedText}>Đang xử lý...</span>
                                            ) : (
                                                <button
                                                    className={styles.actionButtonSecondary}
                                                    onClick={() => setDetailModalItem(row)}
                                                    type="button"
                                                >
                                                    Xem chi tiết
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                            {!loading && !error && rows.length === 0 ? <ListMessage colSpan={4} message="Không có phiếu hoàn hàng phù hợp" /> : null}
                        </tbody>
                    </table>
                </div>

                {!loading && !error ? (
                    <ListPagination
                        itemLabel="phiếu hoàn"
                        onPageChange={onPageChange}
                        page={page}
                        pageSize={PAGE_SIZE}
                        totalItems={totalElements}
                        totalPages={totalPages}
                    />
                ) : null}
            </div>

            {selectedId && (selectionLoading || selectionError) ? (
                <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-semibold">
                    <p>{selectionLoading ? "Đang tải dữ liệu kiểm hàng..." : selectionError}</p>
                    <button className="mt-2 text-[#159734]" onClick={closeSelection} type="button">Đóng</button>
                </div>
            ) : null}

            {/* Inspection Form Modal */}
            {selectedReturn && qcSummary ? (
                <ReturnInspectionModal
                    isOpen
                    onClose={closeSelection}
                    onRefresh={reload}
                    onSuccess={reload}
                    onToast={setNotice}
                    qcSummary={qcSummary}
                    returnItem={selectedReturn}
                />
            ) : null}

            {/* Detail Drawer/Modal */}
            {detailModalItem ? (
                <CustomerReturnDetailModal
                    isOpen={Boolean(detailModalItem)}
                    onClose={() => setDetailModalItem(null)}
                    onOpenInspection={(returnDetail, summary) => {
                        setDetailModalItem(null);
                        setSelectedReturn(returnDetail);
                        setQcSummary(summary);
                    }}
                    onRefreshList={reload}
                    returnItem={detailModalItem}
                />
            ) : null}

            {confirmReceiveRow ? (
                <ConfirmDialog
                    confirmLabel="Xác nhận nhận hàng"
                    confirmVariant="primary"
                    description={`Bạn xác nhận đã nhận hàng của phiếu hoàn ${confirmReceiveRow.returnCode ?? "này"}?`}
                    error={receiveConfirmError}
                    onCancel={() => {
                        if (receivingId !== null) return;
                        setConfirmReceiveRow(null);
                        setReceiveConfirmError("");
                    }}
                    onConfirm={() => handleConfirmReceive(confirmReceiveRow)}
                    submitting={receivingId === confirmReceiveRow.id}
                    title="Xác nhận nhận hàng"
                />
            ) : null}
        </section>
    );
}
