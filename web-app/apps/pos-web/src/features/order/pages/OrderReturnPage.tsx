import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Eye,
    Plus,
    RefreshCw,
    Search,
    X,
    XCircle
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import {
    cancelOrderReturn,
    createCustomerReturnInspection,
    getCustomerReturnQcSummary,
    getOrderReturnList,
    getOrderReturnDetail,
    postCustomerReturnInspection,
    receiveOrderReturn
} from "../services/order-return.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import type { InventoryLocationResponse } from "../../inventory/types/inventory.types";
import type { OrderPageResponse } from "../types/order.types";
import type {
    CustomerReturnInspectionResponse,
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason,
    ReturnStatus
} from "../types/order-return.types";
import {
    CUSTOMER_RETURN_QC_RESULT_EFFECTS,
    CUSTOMER_RETURN_QC_RESULTS,
    formatReturnQuantity,
    getAllowedCustomerReturnQcResults,
    getCustomerReturnQcResultLabel,
    getCustomerReturnQcStatusLabel,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    getReturnStatusLabel,
    toReturnNumber
} from "../utils/order-return.utils";
import { formatDateTime } from "../utils/order-formatters";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = "ALL" | ReturnStatus;

interface ConfirmAction {
    type: "receive" | "cancel";
    returnItem: OrderReturnListRow;
}

interface QcDraftLine {
    selected: boolean;
    quantity: string;
    result: CustomerReturnQcResult;
    destinationLocationId: string;
    note: string;
}

const RETURN_STATUS_FILTERS: {
    key: ReturnStatusFilter;
    label: string;
}[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Đã tạo" },
    { key: "RECEIVED", label: "Đã nhận hàng" },
    { key: "CANCELLED", label: "Đã hủy" }
];

const RETURN_LIST_PAGE_SIZE = 20;

const emptyReturnPage: OrderPageResponse<OrderReturnListRow> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: RETURN_LIST_PAGE_SIZE
};

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setDebouncedValue(value);
        }, delayMs);

        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

function getReturnItemCount(returnItem: OrderReturnListRow) {
    if (returnItem.itemCount !== null && returnItem.itemCount !== undefined) {
        return toReturnNumber(returnItem.itemCount);
    }

    return (returnItem.items ?? []).reduce(
        (total, item) => total + toReturnNumber(item.quantity),
        0
    );
}

function getQcQuantity(draft?: QcDraftLine) {
    if (!draft?.quantity.trim()) {
        return 0;
    }

    const quantity = Number(draft.quantity);

    return Number.isFinite(quantity) ? quantity : 0;
}

function getDefaultQcResult(condition?: ReturnCondition | null) {
    return condition === "SEALED" ? "RESTOCK" : "QUARANTINE";
}

function createDefaultQcDraftLine(
    line?: CustomerReturnQcSummaryLine | null
): QcDraftLine {
    return {
        selected: false,
        quantity: "1",
        result: getDefaultQcResult(line?.condition),
        destinationLocationId: "",
        note: ""
    };
}

function findReturnItem(
    returnItem: OrderReturnListRow,
    returnItemId: string
) {
    return (returnItem.items ?? []).find((item) => item.id === returnItemId);
}

function getReturnLineName(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);

    return item?.productNameSnapshot?.trim() || "Sản phẩm";
}

function getReturnLineMeta(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);
    const sku = item?.skuSnapshot?.trim() || "--";
    const variant = item?.variantNameSnapshot?.trim() || "Không có phân loại";

    return `${sku} · ${variant}`;
}

function getReturnLineReasonLabel(reason?: string | null) {
    return getReturnReasonLabel(reason as ReturnReason | null);
}

function isSaleableReturnLocation(location: InventoryLocationResponse) {
    return (
        location.status === "ACTIVE" &&
        location.type !== "DAMAGED" &&
        location.type !== "QUARANTINE"
    );
}

function getLocationOptionLabel(location: InventoryLocationResponse) {
    const code = String(location.code ?? "").trim();
    const name = String(location.name ?? "").trim();

    if (code && name) {
        return `${name} (${code})`;
    }

    return name || code || "Vị trí kho";
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

export function OrderReturnPage() {
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const [statusFilter, setStatusFilter] =
        useState<ReturnStatusFilter>("ALL");
    const [returnPage, setReturnPage] = useState(emptyReturnPage);
    const [currentPage, setCurrentPage] = useState(0);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [isRowsLoading, setIsRowsLoading] = useState(false);
    const [rowsError, setRowsError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const [activeReturn, setActiveReturn] =
        useState<OrderReturnListRow | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(
        null
    );
    const [isTransitioning, setIsTransitioning] = useState(false);
    const [toast, setToast] = useState<string | null>(null);
    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setToast(null);
        }, 3200);

        return () => window.clearTimeout(timerId);
    }, [toast]);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsRowsLoading(true);
            setRowsError(null);

            getOrderReturnList({
                fromDate: timeRange?.fromDate,
                keyword: debouncedSearchTerm,
                status:
                    statusFilter === "ALL" ? undefined : statusFilter,
                toDate: timeRange?.toDate,
                page: currentPage,
                size: RETURN_LIST_PAGE_SIZE,
                sort: "createdAt,desc"
            })
                .then((page) => {
                    if (!ignore) {
                        setReturnPage({
                            ...emptyReturnPage,
                            ...page,
                            content: page.content ?? []
                        });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setReturnPage(emptyReturnPage);
                        setRowsError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsRowsLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        currentPage,
        debouncedSearchTerm,
        reloadKey,
        statusFilter,
        timeRange?.fromDate,
        timeRange?.toDate
    ]);

    const visibleRows = returnPage.content ?? [];
    const totalPages = Math.max(returnPage.totalPages ?? 0, 1);
    const totalElements = returnPage.totalElements ?? 0;
    const fromRecord =
        totalElements > 0 ? returnPage.number * returnPage.size + 1 : 0;
    const toRecord = Math.min(
        (returnPage.number + 1) * returnPage.size,
        totalElements
    );
    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setCurrentPage(0);
    };

    const handleStatusFilterChange = (status: ReturnStatusFilter) => {
        setStatusFilter(status);
        setCurrentPage(0);
    };

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        setCurrentPage(0);
    };

    const clearTimeRange = () => {
        setSelectedTimeNode(null);
        setCurrentPage(0);
    };
    const openReturnDetail = (row: OrderReturnListRow) => {
        setActiveReturn(row);
        setDetailError(null);
        setIsDetailLoading(true);

        getOrderReturnDetail(row.id)
            .then((detail) => {
                setActiveReturn({
                    ...row,
                    ...detail,
                    orderCode: row.orderCode,
                    customerName: row.customerName
                });
            })
            .catch((error: unknown) => {
                setDetailError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsDetailLoading(false);
            });
    };

    const closeReturnDetail = () => {
        setActiveReturn(null);
        setDetailError(null);
        setIsDetailLoading(false);
        setConfirmAction(null);
    };

    const applyTransitionResult = (
        updatedReturn: OrderReturnResponse,
        fallback: OrderReturnListRow
    ) => {
        const row: OrderReturnListRow = {
            ...fallback,
            ...updatedReturn,
            orderCode: fallback.orderCode,
            customerName: fallback.customerName
        };

        setActiveReturn(row);
        setReturnPage((current) => ({
            ...current,
            content: current.content.map((item) =>
                item.id === row.id ? row : item
            )
        }));
    };

    const executeTransition = () => {
        if (!confirmAction) {
            return;
        }

        const { type, returnItem } = confirmAction;
        const action =
            type === "receive" ? receiveOrderReturn : cancelOrderReturn;

        setIsTransitioning(true);
        action(returnItem.id)
            .then((updatedReturn) => {
                applyTransitionResult(updatedReturn, returnItem);
                setToast(
                    type === "receive"
                        ? "Đã xác nhận nhận hàng."
                        : "Đã hủy phiếu hoàn."
                );
                setConfirmAction(null);
                setReloadKey((key) => key + 1);
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsTransitioning(false);
            });
    };

    return (
        <section
            className={`${styles.page} ${
                isTimeCollapsed ? styles.pageTimeCollapsed : ""
            }`}
        >
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={clearTimeRange}
                onSelect={selectTimeNode}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
                selectedNode={selectedTimeNode}
            />

            <div className={styles.content}>
                <div className={styles.header}>
                    <div className={styles.title}>
                        <h1>Hoàn hàng</h1>
                        <p>Quản lý và theo dõi các phiếu hoàn hàng</p>
                    </div>

                    <div className={styles.headerActions}>
                        <button
                            className={styles.secondaryButton}
                            onClick={() => setReloadKey((key) => key + 1)}
                            type="button"
                        >
                            <RefreshCw size={18} aria-hidden="true" />
                            Làm mới
                        </button>
                        <button
                            className={styles.primaryButton}
                            onClick={() => navigate("/system/orders/returns/create")}
                            type="button"
                        >
                            <Plus size={18} aria-hidden="true" />
                            Tạo phiếu hoàn hàng
                        </button>
                    </div>
                </div>

                <div className={styles.workspace}>
                <div className={styles.filterRows}>
                    <div className={styles.filterToolbar}>
                        <label className={styles.searchBox}>
                            <Search size={20} aria-hidden="true" />
                            <input
                                aria-label="Tìm phiếu hoàn"
                                onChange={(event) =>
                                    handleSearchChange(event.target.value)
                                }
                                placeholder="Tìm mã đơn, khách hàng..."
                                value={searchTerm}
                            />
                        </label>

                        <div className={styles.filterGroup}>
                            <span className={styles.filterLabel}>
                                Trạng thái:
                            </span>
                            <div className={styles.quickFilters}>
                                {RETURN_STATUS_FILTERS.map((filter) => (
                                    <button
                                        className={`${styles.filterChip} ${
                                            statusFilter === filter.key
                                                ? styles.activeFilterChip
                                                : ""
                                        }`}
                                        key={filter.key}
                                        onClick={() =>
                                            handleStatusFilterChange(
                                                filter.key
                                            )
                                        }
                                        type="button"
                                    >
                                        {filter.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>

                <div className={styles.tableCard}>
                    <div className={styles.tableWrap}>
                        <table className={styles.returnTable}>
                            <colgroup>
                                <col style={{ width: "20%" }} />
                                <col style={{ width: "20%" }} />
                                <col style={{ width: "18%" }} />
                                <col style={{ width: "16%" }} />
                                <col style={{ width: "18%" }} />
                                <col style={{ width: "8%" }} />
                            </colgroup>
                            <thead>
                                <tr>
                                    <th>Mã phiếu hoàn</th>
                                    <th>Mã đơn hàng</th>
                                    <th>Sản phẩm</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày tạo</th>
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {isRowsLoading ? (
                                    <ReturnTableMessage
                                        description="Dữ liệu đang được tải từ Order-Service"
                                        title="Đang tải phiếu hoàn"
                                    />
                                ) : null}

                                {!isRowsLoading && rowsError ? (
                                    <ReturnTableMessage
                                        description={rowsError}
                                        title="Không tải được danh sách"
                                    />
                                ) : null}

                                {!isRowsLoading && !rowsError
                                    ? visibleRows.map((row, index) => (
                                          <tr
                                              className={
                                                  index % 2 === 0
                                                      ? styles.zebraFirstRow
                                                      : styles.zebraSecondRow
                                              }
                                              key={row.id}
                                              onClick={() =>
                                                  openReturnDetail(row)
                                              }
                                          >
                                              <td className={styles.code}>
                                                  {getReturnCode(row)}
                                              </td>
                                              <td className={styles.code}>
                                                  {row.orderCode || "--"}
                                              </td>
                                              <td>
                                                  {formatReturnQuantity(
                                                      getReturnItemCount(row)
                                                  )}{" "}
                                                  sản phẩm
                                              </td>
                                              <td>
                                                  <ReturnStatusBadge
                                                      status={row.status}
                                                  />
                                              </td>
                                              <td>
                                                  {formatDateTime(
                                                      row.createdAt
                                                  )}
                                              </td>
                                              <td
                                                  className={
                                                      styles.actionCell
                                                  }
                                              >
                                                  <button
                                                      aria-label={`Xem ${getReturnCode(
                                                          row
                                                      )}`}
                                                      className={
                                                          styles.iconButton
                                                      }
                                                      onClick={(event) => {
                                                          event.stopPropagation();
                                                          openReturnDetail(row);
                                                      }}
                                                      type="button"
                                                  >
                                                      <Eye
                                                          size={18}
                                                          aria-hidden="true"
                                                      />
                                                  </button>
                                              </td>
                                          </tr>
                                      ))
                                    : null}

                                {!isRowsLoading &&
                                !rowsError &&
                                visibleRows.length === 0 ? (
                                    <ReturnTableMessage
                                        description="Thử đổi từ khóa hoặc tạo phiếu hoàn mới"
                                        title="Chưa có phiếu hoàn phù hợp"
                                    />
                                ) : null}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.pagination}>
                        <span>
                            Hiển thị {fromRecord} - {toRecord} trong{" "}
                            {totalElements} phiếu hoàn
                        </span>
                        <div className={styles.pages}>
                            <button
                                className={styles.pageButton}
                                disabled={currentPage === 0}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.max(0, page - 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronLeft size={18} aria-hidden="true" />
                            </button>
                            {getPageNumbers(totalPages, currentPage).map(
                                (page) => (
                                    <button
                                        className={`${styles.pageButton} ${
                                            page === currentPage
                                                ? styles.activePage
                                                : ""
                                        }`}
                                        key={page}
                                        onClick={() => setCurrentPage(page)}
                                        type="button"
                                    >
                                        {page + 1}
                                    </button>
                                )
                            )}
                            {totalPages > 5 ? <span>...</span> : null}
                            <button
                                className={styles.pageButton}
                                disabled={currentPage >= totalPages - 1}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.min(totalPages - 1, page + 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronRight size={18} aria-hidden="true" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {activeReturn ? (
                <ReturnDetailModal
                    detailError={detailError}
                    isDetailLoading={isDetailLoading}
                    isTransitioning={isTransitioning}
                    onClose={closeReturnDetail}
                    onRequestCancel={(returnItem) =>
                        setConfirmAction({
                            type: "cancel",
                            returnItem
                        })
                    }
                    onRequestComplete={(returnItem) =>
                        setConfirmAction({
                            type: "receive",
                            returnItem
                        })
                    }
                    onToast={setToast}
                    returnItem={activeReturn}
                />
            ) : null}

            {confirmAction ? (
                <div className={styles.confirmBackdrop} role="presentation">
                    <div
                        aria-modal="true"
                        className={styles.confirmDialog}
                        role="dialog"
                    >
                        <h2>
                            {confirmAction.type === "receive"
                                ? "Xác nhận cửa hàng đã nhận hàng trả lại từ khách?"
                                : "Hủy phiếu hoàn?"}
                        </h2>
                        <p>
                            {confirmAction.type === "receive"
                                ? "Sau khi xác nhận nhận hàng, phiếu hoàn sẽ không thể hủy."
                                : "Bạn có chắc muốn hủy phiếu hoàn hàng này?"}
                        </p>
                        <div className={styles.confirmActions}>
                            <button
                                className={styles.secondaryButton}
                                disabled={isTransitioning}
                                onClick={() => setConfirmAction(null)}
                                type="button"
                            >
                                Giữ lại
                            </button>
                            <button
                                className={
                                    confirmAction.type === "receive"
                                        ? styles.primaryButton
                                        : styles.dangerButton
                                }
                                disabled={isTransitioning}
                                onClick={executeTransition}
                                type="button"
                            >
                                {isTransitioning
                                    ? "Đang xử lý..."
                                    : "Xác nhận"}
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}

            {toast ? <div className={styles.toast}>{toast}</div> : null}
            </div>
        </section>
    );
}

function ReturnDetailModal({
    detailError,
    isDetailLoading,
    isTransitioning,
    onClose,
    onRequestCancel,
    onRequestComplete,
    onToast,
    returnItem
}: {
    detailError: string | null;
    isDetailLoading: boolean;
    isTransitioning: boolean;
    onClose: () => void;
    onRequestCancel: (returnItem: OrderReturnListRow) => void;
    onRequestComplete: (returnItem: OrderReturnListRow) => void;
    onToast: (message: string) => void;
    returnItem: OrderReturnListRow;
}) {
    const canTransition = returnItem.status === "CREATED";
    const [qcSummary, setQcSummary] =
        useState<CustomerReturnQcSummaryResponse | null>(null);
    const [isQcSummaryLoading, setIsQcSummaryLoading] = useState(false);
    const [qcSummaryError, setQcSummaryError] = useState<string | null>(null);
    const [qcReloadKey, setQcReloadKey] = useState(0);
    const [isQcOpen, setIsQcOpen] = useState(false);
    const [qcDraftLines, setQcDraftLines] = useState<Record<string, QcDraftLine>>(
        {}
    );
    const [qcNote, setQcNote] = useState("");
    const [qcFormError, setQcFormError] = useState<string | null>(null);
    const [activeLocations, setActiveLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [quarantineLocations, setQuarantineLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [isLocationLoading, setIsLocationLoading] = useState(false);
    const [locationError, setLocationError] = useState<string | null>(null);
    const [isCreatingInspection, setIsCreatingInspection] = useState(false);
    const [createdInspection, setCreatedInspection] =
        useState<CustomerReturnInspectionResponse | null>(null);
    const [isPostingInspection, setIsPostingInspection] = useState(false);

    const branchId = qcSummary?.branchId ?? returnItem.branchId ?? null;
    const summaryLines = useMemo(
        () => qcSummary?.lines ?? [],
        [qcSummary?.lines]
    );
    const remainingLines = useMemo(
        () =>
            summaryLines.filter(
                (line) => toReturnNumber(line.remainingQcQuantity) > 0
            ),
        [summaryLines]
    );
    const restockLocations = activeLocations.filter(isSaleableReturnLocation);
    const normalizedQuarantineLocations = quarantineLocations.filter(
        (location) =>
            location.status === "ACTIVE" && location.type === "QUARANTINE"
    );
    const isQcCompleted = qcSummary?.qcStatus === "COMPLETED";

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setQcSummary(null);
            setQcSummaryError(null);
            setIsQcOpen(false);
            setCreatedInspection(null);
            setQcDraftLines({});
            setQcNote("");
            setQcFormError(null);
            setActiveLocations([]);
            setQuarantineLocations([]);
            setLocationError(null);
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [returnItem.id]);

    useEffect(() => {
        if (returnItem.status !== "RECEIVED") {
            const timerId = window.setTimeout(() => {
                setQcSummary(null);
                setQcSummaryError(null);
                setIsQcSummaryLoading(false);
            }, 0);

            return () => window.clearTimeout(timerId);
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsQcSummaryLoading(true);
            setQcSummaryError(null);

            getCustomerReturnQcSummary(returnItem.id)
                .then((summary) => {
                    if (!ignore) {
                        setQcSummary(summary);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setQcSummary(null);
                        setQcSummaryError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsQcSummaryLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [qcReloadKey, returnItem.id, returnItem.status]);

    useEffect(() => {
        if (!isQcOpen || !branchId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsLocationLoading(true);
            setLocationError(null);

            Promise.all([
                inventoryLocationApi.list({
                    branchId,
                    page: 0,
                    size: 100,
                    status: "ACTIVE"
                }),
                inventoryLocationApi.list({
                    branchId,
                    page: 0,
                    size: 100,
                    status: "ACTIVE",
                    type: "QUARANTINE"
                })
            ])
                .then(([activePage, quarantinePage]) => {
                    if (!ignore) {
                        setActiveLocations(activePage.content ?? []);
                        setQuarantineLocations(quarantinePage.content ?? []);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setActiveLocations([]);
                        setQuarantineLocations([]);
                        setLocationError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsLocationLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [branchId, isQcOpen]);

    useEffect(() => {
        if (!isQcOpen || createdInspection) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setQcDraftLines((current) => {
                const nextEntries = remainingLines.map((line) => [
                    line.returnItemId,
                    current[line.returnItemId] ?? createDefaultQcDraftLine(line)
                ]);

                return Object.fromEntries(nextEntries);
            });
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [createdInspection, isQcOpen, remainingLines]);

    const openQcForm = () => {
        if (returnItem.status !== "RECEIVED") {
            onToast("Phiếu hoàn chưa ở trạng thái đã nhận hàng.");
            return;
        }

        if (isQcCompleted) {
            onToast("Đã kiểm tra xong hàng trả.");
            return;
        }

        setIsQcOpen(true);
        setCreatedInspection(null);
        setQcFormError(null);
    };

    const updateQcDraftLine = (
        returnItemId: string,
        update: Partial<QcDraftLine>
    ) => {
        setQcDraftLines((current) => {
            const line = summaryLines.find(
                (summaryLine) => summaryLine.returnItemId === returnItemId
            );
            const currentLine =
                current[returnItemId] ?? createDefaultQcDraftLine(line);
            const nextLine = {
                ...currentLine,
                ...update
            };

            if (update.result) {
                nextLine.destinationLocationId = "";
            }

            return {
                ...current,
                [returnItemId]: nextLine
            };
        });
    };

    const getLocationOptions = (result: CustomerReturnQcResult) => {
        if (result === "QUARANTINE") {
            return normalizedQuarantineLocations;
        }

        if (result === "RESTOCK") {
            return restockLocations;
        }

        return [];
    };

    const validateQcDraft = () => {
        if (!branchId) {
            return "Phiếu hoàn chưa có thông tin chi nhánh để chọn vị trí kho.";
        }

        const selectedLines = remainingLines.filter(
            (line) => qcDraftLines[line.returnItemId]?.selected
        );

        if (selectedLines.length === 0) {
            return "Vui lòng chọn ít nhất một sản phẩm cần kiểm tra.";
        }

        for (const line of selectedLines) {
            const draft =
                qcDraftLines[line.returnItemId] ?? createDefaultQcDraftLine(line);
            const quantity = getQcQuantity(draft);
            const remainingQuantity = toReturnNumber(line.remainingQcQuantity);
            const allowedResults = getAllowedCustomerReturnQcResults(
                line.condition
            );

            if (quantity <= 0) {
                return "Số lượng kiểm tra phải lớn hơn 0.";
            }

            if (quantity > remainingQuantity) {
                return "Số lượng kiểm tra vượt quá số lượng còn lại.";
            }

            if (!allowedResults.includes(draft.result)) {
                return "Tình trạng hàng này không được nhập lại tồn bán được.";
            }

            if (draft.result === "RESTOCK") {
                const isValidLocation = restockLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return "Vị trí nhập lại không phải vị trí có thể bán.";
                }
            }

            if (draft.result === "QUARANTINE") {
                if (normalizedQuarantineLocations.length === 0) {
                    return "Chi nhánh chưa có vị trí cách ly hàng.";
                }

                const isValidLocation = normalizedQuarantineLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return "Vui lòng chọn vị trí cách ly.";
                }
            }

            if (
                line.condition === "SEALED" &&
                draft.result === "WRITE_OFF" &&
                draft.note.trim().length < 5 &&
                qcNote.trim().length < 5
            ) {
                return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
            }
        }

        return null;
    };

    const createInspection = () => {
        const validationError = validateQcDraft();

        if (validationError) {
            setQcFormError(validationError);
            return;
        }

        setQcFormError(null);
        setIsCreatingInspection(true);

        createCustomerReturnInspection(returnItem.id, {
            note: qcNote.trim() || null,
            lines: remainingLines
                .filter((line) => qcDraftLines[line.returnItemId]?.selected)
                .map((line) => {
                    const draft =
                        qcDraftLines[line.returnItemId] ??
                        createDefaultQcDraftLine(line);

                    return {
                        returnItemId: line.returnItemId,
                        quantity: getQcQuantity(draft),
                        result: draft.result,
                        destinationLocationId:
                            draft.result === "WRITE_OFF"
                                ? null
                                : draft.destinationLocationId,
                        note: draft.note.trim() || null
                    };
                })
        })
            .then((inspection) => {
                setCreatedInspection(inspection);
                onToast("Đã tạo phiếu kiểm tra hàng trả.");
            })
            .catch((error: unknown) => {
                setQcFormError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsCreatingInspection(false);
            });
    };

    const postInspection = () => {
        if (!createdInspection) {
            return;
        }

        const confirmed = window.confirm(
            "Xác nhận xử lý tồn kho theo kết quả kiểm tra?\n\nThao tác này sẽ cập nhật tồn kho và không nên thực hiện lặp lại."
        );

        if (!confirmed) {
            return;
        }

        setIsPostingInspection(true);
        postCustomerReturnInspection(createdInspection.id)
            .then(() => {
                onToast("Đã xử lý tồn kho theo phiếu kiểm tra.");
                setCreatedInspection(null);
                setIsQcOpen(false);
                setQcNote("");
                setQcDraftLines({});
                setQcReloadKey((key) => key + 1);
            })
            .catch((error: unknown) => {
                setQcFormError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsPostingInspection(false);
            });
    };

    const getDestinationName = (locationId?: string | null) => {
        if (!locationId) {
            return "--";
        }

        const location = [...activeLocations, ...quarantineLocations].find(
            (item) => String(item.id) === locationId
        );

        return location ? getLocationOptionLabel(location) : "--";
    };

    return (
        <div
            className={styles.modalBackdrop}
            onClick={onClose}
            role="presentation"
        >
            <aside
                aria-label="Chi tiết phiếu hoàn"
                aria-modal="true"
                className={styles.detailModal}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.modalHeader}>
                    <div>
                        <h2>{getReturnCode(returnItem)}</h2>
                        <p>{returnItem.orderCode || "Đơn hàng"}</p>
                    </div>
                    <button
                        aria-label="Đóng chi tiết phiếu hoàn"
                        className={styles.closeButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {isDetailLoading ? (
                    <PanelMessage
                        description="Đang tải thông tin phiếu hoàn"
                        title="Đang tải chi tiết"
                    />
                ) : null}
                {!isDetailLoading && detailError ? (
                    <PanelMessage
                        description={detailError}
                        title="Không tải được chi tiết"
                    />
                ) : null}
                {!isDetailLoading && !detailError ? (
                    <>
                        <section className={styles.detailSection}>
                            <div className={styles.detailInfoGrid}>
                                <InfoRow
                                    label="Trạng thái"
                                    value={
                                        <ReturnStatusBadge
                                            status={returnItem.status}
                                        />
                                    }
                                />
                                <InfoRow
                                    label="Khách hàng"
                                    value={returnItem.customerName || "--"}
                                />
                                <InfoRow
                                    label="Ngày tạo"
                                    value={formatDateTime(returnItem.createdAt)}
                                />
                                <InfoRow
                                    label="Cập nhật"
                                    value={formatDateTime(returnItem.updatedAt)}
                                />
                            </div>
                            {returnItem.note ? (
                                <p className={styles.detailNote}>
                                    {returnItem.note}
                                </p>
                            ) : null}
                        </section>

                        <section className={styles.detailSection}>
                            <h3>Sản phẩm hoàn</h3>
                            <div className={styles.detailItems}>
                                {(returnItem.items ?? []).map((item, index) => (
                                    <div
                                        className={styles.detailItem}
                                        key={item.id ?? index}
                                    >
                                        <div>
                                            <strong>
                                                {item.productNameSnapshot ||
                                                    "Sản phẩm"}
                                            </strong>
                                            <span>
                                                {item.skuSnapshot || "--"} ·{" "}
                                                {item.variantNameSnapshot ||
                                                    "Không có phân loại"}
                                            </span>
                                        </div>
                                        <div>
                                            <strong>
                                                SL{" "}
                                                {formatReturnQuantity(
                                                    item.quantity
                                                )}
                                            </strong>
                                            <small>
                                                {getReturnReasonLabel(
                                                    item.reason
                                                )}{" "}
                                                ·{" "}
                                                {getReturnConditionLabel(
                                                    item.condition
                                                )}
                                            </small>
                                        </div>
                                        {item.note ? <p>{item.note}</p> : null}
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className={styles.detailSection}>
                            <div className={styles.qcHeader}>
                                <div>
                                    <h3>Kiểm tra hàng trả</h3>
                                    {returnItem.status === "RECEIVED" ? (
                                        <p>
                                            Trạng thái xử lý kho:{" "}
                                            <strong>
                                                {isQcSummaryLoading
                                                    ? "Đang tải..."
                                                    : getCustomerReturnQcStatusLabel(
                                                          qcSummary?.qcStatus
                                                      )}
                                            </strong>
                                        </p>
                                    ) : null}
                                </div>
                                {returnItem.status === "RECEIVED" &&
                                qcSummary &&
                                !isQcCompleted ? (
                                    <button
                                        className={styles.primaryButton}
                                        disabled={
                                            isQcSummaryLoading ||
                                            isCreatingInspection ||
                                            isPostingInspection
                                        }
                                        onClick={openQcForm}
                                        type="button"
                                    >
                                        <Plus size={18} aria-hidden="true" />
                                        Kiểm tra hàng trả
                                    </button>
                                ) : null}
                            </div>

                            {returnItem.status === "CREATED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn chưa được nhận hàng nên chưa thể QC."
                                    title="Chưa được QC"
                                />
                            ) : null}
                            {returnItem.status === "CANCELLED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn đã hủy nên không được QC."
                                    title="Không được QC"
                                />
                            ) : null}
                            {returnItem.status === "COMPLETED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn đã hoàn tất nên không tạo lượt QC mới."
                                    title="Không tạo QC mới"
                                />
                            ) : null}

                            {returnItem.status === "RECEIVED" ? (
                                <>
                                    {isQcSummaryLoading ? (
                                        <PanelMessage
                                            description="Đang tải số lượng đã kiểm tra và còn lại."
                                            title="Đang tải QC summary"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading && qcSummaryError ? (
                                        <PanelMessage
                                            description={qcSummaryError}
                                            title="Không tải được QC summary"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading &&
                                    !qcSummaryError &&
                                    qcSummary ? (
                                        <div className={styles.qcSummaryList}>
                                            {summaryLines.map((line) => (
                                                <div
                                                    className={styles.qcSummaryItem}
                                                    key={line.returnItemId}
                                                >
                                                    <div>
                                                        <strong>
                                                            {getReturnLineName(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </strong>
                                                        <span>
                                                            {getReturnLineMeta(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </span>
                                                    </div>
                                                    <div>
                                                        <span>
                                                            Đã trả:{" "}
                                                            {formatReturnQuantity(
                                                                line.returnedQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Đã kiểm tra:{" "}
                                                            {formatReturnQuantity(
                                                                line.postedQcQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Còn lại:{" "}
                                                            {formatReturnQuantity(
                                                                line.remainingQcQuantity
                                                            )}
                                                        </span>
                                                    </div>
                                                </div>
                                            ))}
                                            {isQcCompleted ? (
                                                <p className={styles.qcDoneNote}>
                                                    Đã kiểm tra xong hàng trả
                                                </p>
                                            ) : null}
                                        </div>
                                    ) : null}

                                    {isQcOpen ? (
                                        <div className={styles.qcPanel}>
                                            <div className={styles.qcPanelHeader}>
                                                <div>
                                                    <strong>
                                                        Tạo phiếu kiểm tra
                                                    </strong>
                                                    <span>
                                                        Chỉ hiển thị sản phẩm còn
                                                        số lượng chưa QC.
                                                    </span>
                                                </div>
                                                <button
                                                    className={styles.secondaryButton}
                                                    disabled={
                                                        isCreatingInspection ||
                                                        isPostingInspection
                                                    }
                                                    onClick={() => {
                                                        setIsQcOpen(false);
                                                        setCreatedInspection(null);
                                                        setQcFormError(null);
                                                    }}
                                                    type="button"
                                                >
                                                    Đóng
                                                </button>
                                            </div>

                                            {locationError ? (
                                                <p className={styles.qcError}>
                                                    {locationError}
                                                </p>
                                            ) : null}
                                            {isLocationLoading ? (
                                                <PanelMessage
                                                    description="Đang tải vị trí nhập lại và cách ly của chi nhánh."
                                                    title="Đang tải vị trí kho"
                                                />
                                            ) : null}

                                            {!createdInspection ? (
                                                <>
                                                    <div
                                                        className={
                                                            styles.qcFormList
                                                        }
                                                    >
                                                        {remainingLines.map(
                                                            (line) => {
                                                                const draft =
                                                                    qcDraftLines[
                                                                        line
                                                                            .returnItemId
                                                                    ] ??
                                                                    createDefaultQcDraftLine(
                                                                        line
                                                                    );
                                                                const allowedResults =
                                                                    getAllowedCustomerReturnQcResults(
                                                                        line.condition
                                                                    );
                                                                const locationOptions =
                                                                    getLocationOptions(
                                                                        draft.result
                                                                    );

                                                                return (
                                                                    <div
                                                                        className={
                                                                            styles.qcFormItem
                                                                        }
                                                                        key={
                                                                            line.returnItemId
                                                                        }
                                                                    >
                                                                        <label
                                                                            className={
                                                                                styles.qcItemSelector
                                                                            }
                                                                        >
                                                                            <input
                                                                                checked={
                                                                                    draft.selected
                                                                                }
                                                                                onChange={(
                                                                                    event
                                                                                ) =>
                                                                                    updateQcDraftLine(
                                                                                        line.returnItemId,
                                                                                        {
                                                                                            selected:
                                                                                                event
                                                                                                    .target
                                                                                                    .checked
                                                                                        }
                                                                                    )
                                                                                }
                                                                                type="checkbox"
                                                                            />
                                                                            <span>
                                                                                <strong>
                                                                                    {getReturnLineName(
                                                                                        returnItem,
                                                                                        line
                                                                                    )}
                                                                                </strong>
                                                                                <small>
                                                                                    {getReturnLineMeta(
                                                                                        returnItem,
                                                                                        line
                                                                                    )}
                                                                                </small>
                                                                            </span>
                                                                        </label>
                                                                        <div
                                                                            className={
                                                                                styles.qcMetaGrid
                                                                            }
                                                                        >
                                                                            <InfoRow
                                                                                label="Tình trạng"
                                                                                value={getReturnConditionLabel(
                                                                                    line.condition
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Lý do"
                                                                                value={getReturnLineReasonLabel(
                                                                                    line.reason
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Đã trả"
                                                                                value={formatReturnQuantity(
                                                                                    line.returnedQuantity
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Đã kiểm tra"
                                                                                value={formatReturnQuantity(
                                                                                    line.postedQcQuantity
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Còn lại"
                                                                                value={formatReturnQuantity(
                                                                                    line.remainingQcQuantity
                                                                                )}
                                                                            />
                                                                        </div>
                                                                        <div
                                                                            className={
                                                                                styles.qcInputGrid
                                                                            }
                                                                        >
                                                                            <label>
                                                                                <span>
                                                                                    Số lượng QC
                                                                                    lần
                                                                                    này
                                                                                </span>
                                                                                <input
                                                                                    className={
                                                                                        styles.quantityInput
                                                                                    }
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    max={toReturnNumber(
                                                                                        line.remainingQcQuantity
                                                                                    )}
                                                                                    min="0"
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                quantity:
                                                                                                    event
                                                                                                        .target
                                                                                                        .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    type="number"
                                                                                    value={
                                                                                        draft.quantity
                                                                                    }
                                                                                />
                                                                            </label>
                                                                            <label>
                                                                                <span>
                                                                                    Kết quả QC
                                                                                </span>
                                                                                <select
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                result: event
                                                                                                    .target
                                                                                                    .value as CustomerReturnQcResult
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    value={
                                                                                        draft.result
                                                                                    }
                                                                                >
                                                                                    {CUSTOMER_RETURN_QC_RESULTS.map(
                                                                                        (
                                                                                            result
                                                                                        ) => (
                                                                                            <option
                                                                                                disabled={
                                                                                                    !allowedResults.includes(
                                                                                                        result
                                                                                                    )
                                                                                                }
                                                                                                key={
                                                                                                    result
                                                                                                }
                                                                                                value={
                                                                                                    result
                                                                                                }
                                                                                            >
                                                                                                {getCustomerReturnQcResultLabel(
                                                                                                    result
                                                                                                )}
                                                                                            </option>
                                                                                        )
                                                                                    )}
                                                                                </select>
                                                                            </label>
                                                                            <label>
                                                                                <span>
                                                                                    Vị trí đích
                                                                                </span>
                                                                                <select
                                                                                    disabled={
                                                                                        !draft.selected ||
                                                                                        draft.result ===
                                                                                            "WRITE_OFF" ||
                                                                                        isLocationLoading
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                destinationLocationId:
                                                                                                    event
                                                                                                        .target
                                                                                                        .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    value={
                                                                                        draft.destinationLocationId
                                                                                    }
                                                                                >
                                                                                    <option value="">
                                                                                        Chọn vị trí
                                                                                    </option>
                                                                                    {locationOptions.map(
                                                                                        (
                                                                                            location
                                                                                        ) => (
                                                                                            <option
                                                                                                key={String(
                                                                                                    location.id
                                                                                                )}
                                                                                                value={String(
                                                                                                    location.id
                                                                                                )}
                                                                                            >
                                                                                                {getLocationOptionLabel(
                                                                                                    location
                                                                                                )}
                                                                                            </option>
                                                                                        )
                                                                                    )}
                                                                                </select>
                                                                            </label>
                                                                            <label
                                                                                className={
                                                                                    styles.fullField
                                                                                }
                                                                            >
                                                                                <span>
                                                                                    Ghi chú dòng
                                                                                </span>
                                                                                <textarea
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                note: event
                                                                                                    .target
                                                                                                    .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    placeholder="Ghi chú tình trạng kiểm tra"
                                                                                    value={
                                                                                        draft.note
                                                                                    }
                                                                                />
                                                                            </label>
                                                                        </div>
                                                                        <p
                                                                            className={
                                                                                styles.qcEffect
                                                                            }
                                                                        >
                                                                            {
                                                                                CUSTOMER_RETURN_QC_RESULT_EFFECTS[
                                                                                    draft
                                                                                        .result
                                                                                ]
                                                                            }
                                                                        </p>
                                                                    </div>
                                                                );
                                                            }
                                                        )}
                                                    </div>

                                                    {remainingLines.length ===
                                                    0 ? (
                                                        <PanelMessage
                                                            description="Không còn sản phẩm cần QC trong phiếu hoàn này."
                                                            title="Đã kiểm tra xong"
                                                        />
                                                    ) : null}

                                                    <label
                                                        className={
                                                            styles.fullField
                                                        }
                                                    >
                                                        <span>
                                                            Ghi chú phiếu kiểm tra
                                                        </span>
                                                        <textarea
                                                            onChange={(event) =>
                                                                setQcNote(
                                                                    event.target
                                                                        .value
                                                                )
                                                            }
                                                            placeholder="Ghi chú chung nếu có"
                                                            value={qcNote}
                                                        />
                                                    </label>
                                                </>
                                            ) : (
                                                <div
                                                    className={
                                                        styles.qcReviewPanel
                                                    }
                                                >
                                                    <div>
                                                        <strong>
                                                            {createdInspection.inspectionCode ||
                                                                "Phiếu kiểm tra"}
                                                        </strong>
                                                        <span>
                                                            Trạng thái:{" "}
                                                            {createdInspection.status ||
                                                                "CREATED"}
                                                        </span>
                                                    </div>
                                                    <div
                                                        className={
                                                            styles.qcReviewList
                                                        }
                                                    >
                                                        {(
                                                            createdInspection.lines ??
                                                            []
                                                        ).map((line) => {
                                                            const summaryLine =
                                                                summaryLines.find(
                                                                    (item) =>
                                                                        item.returnItemId ===
                                                                        line.returnItemId
                                                                );

                                                            return (
                                                                <div
                                                                    className={
                                                                        styles.qcReviewItem
                                                                    }
                                                                    key={line.id}
                                                                >
                                                                    <strong>
                                                                        {summaryLine
                                                                            ? getReturnLineName(
                                                                                  returnItem,
                                                                                  summaryLine
                                                                              )
                                                                            : "Sản phẩm"}
                                                                    </strong>
                                                                    <span>
                                                                        SL{" "}
                                                                        {formatReturnQuantity(
                                                                            line.quantity
                                                                        )}{" "}
                                                                        ·{" "}
                                                                        {getCustomerReturnQcResultLabel(
                                                                            line.result
                                                                        )}
                                                                    </span>
                                                                    <small>
                                                                        Vị trí:{" "}
                                                                        {getDestinationName(
                                                                            line.destinationLocationId
                                                                        )}
                                                                    </small>
                                                                    {line.note ? (
                                                                        <p>
                                                                            {
                                                                                line.note
                                                                            }
                                                                        </p>
                                                                    ) : null}
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            )}

                                            {qcFormError ? (
                                                <p className={styles.qcError}>
                                                    {qcFormError}
                                                </p>
                                            ) : null}

                                            <div className={styles.qcActions}>
                                                {!createdInspection ? (
                                                    <button
                                                        className={
                                                            styles.primaryButton
                                                        }
                                                        disabled={
                                                            isCreatingInspection ||
                                                            isLocationLoading ||
                                                            remainingLines.length ===
                                                                0
                                                        }
                                                        onClick={
                                                            createInspection
                                                        }
                                                        type="button"
                                                    >
                                                        {isCreatingInspection
                                                            ? "Đang tạo..."
                                                            : "Tạo phiếu kiểm tra"}
                                                    </button>
                                                ) : (
                                                    <button
                                                        className={
                                                            styles.primaryButton
                                                        }
                                                        disabled={
                                                            isPostingInspection
                                                        }
                                                        onClick={
                                                            postInspection
                                                        }
                                                        type="button"
                                                    >
                                                        <CheckCircle2
                                                            size={18}
                                                            aria-hidden="true"
                                                        />
                                                        {isPostingInspection
                                                            ? "Đang xử lý..."
                                                            : "Xác nhận xử lý kho"}
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    ) : null}
                                </>
                            ) : null}
                        </section>

                        {canTransition ? (
                            <div className={styles.detailActions}>
                                <button
                                    className={styles.secondaryButton}
                                    disabled={isTransitioning}
                                    onClick={() => onRequestCancel(returnItem)}
                                    type="button"
                                >
                                    <XCircle size={18} aria-hidden="true" />
                                    Hủy phiếu
                                </button>
                                <button
                                    className={styles.primaryButton}
                                    disabled={isTransitioning}
                                    onClick={() =>
                                        onRequestComplete(returnItem)
                                    }
                                    type="button"
                                >
                                    <CheckCircle2 size={18} aria-hidden="true" />
                                    Xác nhận nhận hàng
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </aside>
        </div>
    );
}

function ReturnStatusBadge({ status }: { status?: ReturnStatus | null }) {
    const statusClass =
        status === "RECEIVED"
            ? styles.completedBadge
            : status === "COMPLETED"
                ? styles.completedBadge
                : status === "CANCELLED"
                    ? styles.cancelledBadge
                    : styles.createdBadge;

    return (
        <span className={`${styles.statusBadge} ${statusClass}`}>
            {getReturnStatusLabel(status)}
        </span>
    );
}

function ReturnTableMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <tr>
            <td className={styles.emptyCell} colSpan={6}>
                <strong>{title}</strong>
                <span>{description}</span>
            </td>
        </tr>
    );
}

function PanelMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <div className={styles.panelMessage}>
            <strong>{title}</strong>
            <span>{description}</span>
        </div>
    );
}

function InfoRow({
    label,
    value
}: {
    label: string;
    value: ReactNode;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            <strong>{value || "--"}</strong>
        </div>
    );
}
