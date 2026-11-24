import {
    OrderPageLayout,
    OrderListWorkspace,
    OrderToolbar,
    OrderStatusFilters,
    OrderDataTable,
    OrderPagination,
} from "@/features/order/components/shared/OrderListUI";
import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

import { TimeSidebar } from "@/components/shared/TimeSidebar";
import { Button } from "@/components/ui/Button";
import { useTimeFilter } from "@/components/shared/useTimeFilter";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { ReturnDetailModal } from "../../order/components/return-detail/ReturnDetailModal";
import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import {
    getOrderReturnList,
    getOrderReturnStatusCounts,
    receiveOrderReturn
} from "../../order/services/order-return.api";
import type {
    OrderReturnListRow
} from "../../order/types/order-return.types";
import {
    getCustomerReturnUiStatus,
    getReturnErrorMessage
} from "../../order/utils/order-return.utils";
import { formatDateTime } from "../../order/utils/order-formatters";
import styles from "../pages/CustomerReturnListPage.module.css";
import { CustomerReturnActionMenu } from "./CustomerReturnActionMenu";
import { CustomerReturnWarehouseInspectionModal } from "./CustomerReturnWarehouseInspectionModal";
import { useOrderReturnAccess } from "@/auth/order-return-access";

const PAGE_SIZE = 10;
const CUSTOMER_RETURN_STATUS_TABS: { key: string; label: string; isError?: boolean }[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Chờ nhận hàng" },
    { key: "RECEIVED", label: "Chờ kiểm hàng" },
    { key: "PROCESSING", label: "Đang xử lý" },
    { key: "PENDING_REFUND", label: "Chờ hoàn tiền" },
    { key: "COMPLETED", label: "Hoàn thành" },
    { key: "CANCELLED", label: "Đã hủy" },
    { key: "PROCESSING_FAILED", label: "Có lỗi", isError: true }
];

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

export function CustomerReturnQcQueue({
    onInspectionCreated,
    reloadSignal = 0
}: {
    listState?: unknown;
    onInspectionCreated?: () => void;
    /** Tăng giá trị để tải lại danh sách (nút Làm mới ở header). */
    reloadSignal?: number;
} = {}) {
    const { canReceive } = useOrderReturnAccess();
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);

    const [status, setStatus] = useState("ALL");
    const [page, setPage] = useState(0);

    const {
        selectedTimeNode,
        selectTimeNode,
        clearTimeRange,
        isTimeCollapsed,
        setIsTimeCollapsed,
        timeRange,
    } = useTimeFilter(() => setPage(0));

    const [statusCounts, setStatusCounts] = useState<Record<string, number> | null>(null);
    const [localReloadKey, setReloadKey] = useState(0);
    const reloadKey = localReloadKey + reloadSignal;
    const [rows, setRows] = useState<OrderReturnListRow[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Detail modal state
    const [detailModalItem, setDetailModalItem] = useState<OrderReturnListRow | null>(null);
    const [warehouseInspectionItem, setWarehouseInspectionItem] = useState<OrderReturnListRow | null>(null);
    const [openActionId, setOpenActionId] = useState<string | null>(null);
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
            warehouseActiveOnly: false
        })
            .then((counts) => {
                if (!active || !counts) return;
                setStatusCounts({
                    ALL: counts.ALL ?? 0,
                    CREATED: counts.CREATED ?? 0,
                    RECEIVED: counts.RECEIVED ?? 0,
                    PROCESSING: counts.PROCESSING ?? 0,
                    PENDING_REFUND: counts.PENDING_REFUND ?? 0,
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
                warehouseActiveOnly: false
            })
                .then((response) => {
                    if (!active) return;
                    setOpenActionId(null);
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
    }, [page, reloadKey, status, debouncedSearchTerm, timeRange?.fromDate, timeRange?.toDate]);

    const reload = () => {
        setReloadKey((key) => key + 1);
        onInspectionCreated?.();
    };

    const handleActionMenuChange = (rowId: string, open: boolean) => {
        setOpenActionId((current) => {
            if (open) return rowId;
            return current === rowId ? null : current;
        });
    };

    const handleConfirmReceive = (row: OrderReturnListRow) => {
        if (!canReceive || receivingId) return;
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

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setPage(0);
    };

    const selectStatusFilter = (newStatus: string) => {
        setOpenActionId(null);
        setStatus(newStatus);
        setPage(0);
    };

    return (
        <OrderPageLayout
            title="Khách trả hàng"
            description="Tiếp nhận, kiểm hàng và xử lý kho cho hàng khách trả."
            sidebarCollapsed={isTimeCollapsed}
            sidebar={
                <TimeSidebar
                    activeDate={timeRange?.selectedDate ?? undefined}
                    collapsed={isTimeCollapsed}
                    enableCalendar
                    onClear={clearTimeRange}
                    onSelect={selectTimeNode}
                    onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                    selectedNode={selectedTimeNode}
                />
            }
            actions={
                <Button disabled={loading} onClick={reload}>
                    <RefreshCw size={17} aria-hidden="true" />
                    Làm mới
                </Button>
            }
        >
            <OrderListWorkspace>
                <OrderToolbar
                    search={{
                        "aria-label": "Tìm kiếm phiếu khách trả hàng",
                        placeholder: "Tìm mã phiếu hoàn, mã đơn hàng, khách hàng...",
                        value: searchTerm,
                        onChange: handleSearchChange,
                        onKeyDown: (event) => {
                            if (event.key === "Escape") {
                                handleSearchChange("");
                            }
                        },
                    }}
                >
                    <OrderStatusFilters
                        label="Trạng thái:"
                        options={CUSTOMER_RETURN_STATUS_TABS.map((tab) => ({
                            ...tab,
                            count: statusCounts ? (statusCounts[tab.key] ?? 0) : undefined,
                        }))}
                        value={status}
                        onChange={selectStatusFilter}
                    />
                </OrderToolbar>

                {notice ? <div className={styles.notice} role="status">{notice}</div> : null}

                <OrderDataTable
                    caption="Danh sách phiếu khách trả hàng"
                    columns={[
                        {
                            key: "code",
                            header: "Phiếu hoàn",
                            width: "20%",
                            cellClassName: styles.returnCodeText,
                            render: (row) => (
                                <span title={row.returnCode ?? undefined}>
                                    {row.returnCode ?? "Chưa có mã phiếu"}
                                </span>
                            ),
                        },
                        {
                            key: "status",
                            header: "Trạng thái",
                            width: "18%",
                            align: "center",
                            render: (row) => {
                                const statusInfo = getCustomerReturnUiStatus(row);
                                return <OrderStatusBadge label={statusInfo.label} status={statusInfo.status} />;
                            },
                        },
                        {
                            key: "customer",
                            header: "Khách hàng",
                            width: "22%",
                            render: (row) => (
                                <div className="grid">
                                    <span className="font-bold">{row.customerName?.trim() || "Khách lẻ"}</span>
                                    {row.customerPhone ? <span className={styles.mutedText}>{row.customerPhone}</span> : null}
                                </div>
                            ),
                        },
                        {
                            key: "order",
                            header: "Đơn hàng",
                            width: "16%",
                            cellClassName: styles.orderCodeText,
                            render: (row) => (
                                <span title={row.orderCode ?? undefined}>{row.orderCode ?? "--"}</span>
                            ),
                        },
                        {
                            key: "createdAt",
                            header: "Thời gian",
                            width: "16%",
                            render: (row) => formatDateTime(row.createdAt),
                        },
                        {
                            key: "actions",
                            header: "Thao tác",
                            width: "8%",
                            align: "center",
                            render: (row) => (
                                <CustomerReturnActionMenu
                                    isOpen={openActionId === row.id}
                                    isReceiving={receivingId === row.id}
                                    onOpenChange={(open) => handleActionMenuChange(row.id, open)}
                                    onReceive={() => {
                                        if (!canReceive) return;
                                        setReceiveConfirmError("");
                                        setConfirmReceiveRow(row);
                                    }}
                                    onViewDetail={() => setDetailModalItem(row)}
                                    onInspect={() => {
                                        setOpenActionId(null);
                                        setWarehouseInspectionItem(row);
                                    }}
                                    row={row}
                                />
                            ),
                        },
                    ]}
                    rows={rows}
                    emptyDescription="Thử đổi từ khóa, trạng thái hoặc khoảng thời gian"
                    emptyTitle="Không có phiếu hoàn hàng phù hợp"
                    error={error || null}
                    getRowKey={(row) => row.id}
                    loading={loading}
                    loadingDescription="Dữ liệu đang được tải từ Order-Service"
                    loadingTitle="Đang tải phiếu khách trả hàng"
                    errorTitle="Không tải được danh sách phiếu khách trả hàng"
                    onRetry={reload}
                    onRowClick={(row) => setDetailModalItem(row)}
                    pagination={
                        <OrderPagination
                            disabled={loading}
                            itemLabel="phiếu hoàn"
                            onPageChange={(nextPage) => {
                                setOpenActionId(null);
                                setPage(nextPage);
                            }}
                            page={page}
                            pageSize={PAGE_SIZE}
                            totalItems={totalElements}
                            totalPages={totalPages}
                        />
                    }
                    minWidth={800}
                />
            </OrderListWorkspace>

            {detailModalItem ? (
                <ReturnDetailModal
                    key={detailModalItem.id}
                    onChanged={() => setReloadKey((key) => key + 1)}
                    onClose={() => setDetailModalItem(null)}
                    onToast={setNotice}
                    returnItem={detailModalItem}
                />
            ) : null}

            {warehouseInspectionItem ? (
                <CustomerReturnWarehouseInspectionModal
                    isOpen
                    onClose={() => {
                        setWarehouseInspectionItem(null);
                        setReloadKey((key) => key + 1);
                    }}
                    returnCode={warehouseInspectionItem.returnCode}
                    returnId={warehouseInspectionItem.id}
                    returnItem={warehouseInspectionItem}
                    key={warehouseInspectionItem.id}
                    onSuccess={() => {
                        setReloadKey((k) => k + 1);
                        onInspectionCreated?.();
                    }}
                />
            ) : null}

            {confirmReceiveRow && canReceive ? (
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
        </OrderPageLayout>
    );
}
