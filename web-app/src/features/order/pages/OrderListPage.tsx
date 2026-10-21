import {
    ChevronLeft,
    ChevronRight,
    MoreHorizontal,
    Plus,
    Search,
    Upload
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { ExportDropdown } from "../components/ExportDropdown";
import { OrderDetailPanel } from "../components/OrderDetailPanel";
import { OrderStatusBadge } from "../components/OrderStatusBadge";
import {
    exportOrders,
    getOrderById,
    getOrders,
    getOrderStats
} from "../services/order.api";
import type {
    OrderDetailResponse,
    OrderPageResponse,
    OrderStatsResponse,
    OrderSummaryResponse
} from "../types/order.types";
import {
    formatCurrency,
    formatDateOnly,
    getOrderCode,
    getOrderStatusText
} from "../utils/order-formatters";
import { buildActiveOrderExportParams } from "../utils/order-export-filters";
import styles from "./OrderListPage.module.css";

type OrderStatusTabKey =
    | "ALL"
    | "PENDING_PAYMENT"
    | "PROCESSING"
    | "SHIPPING"
    | "COMPLETED"
    | "RETURN_REFUND";

const PAGE_SIZE = 10;

const emptyPage: OrderPageResponse<OrderSummaryResponse> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: PAGE_SIZE
};

const ORDER_STATUS_TABS: {
    key: OrderStatusTabKey;
    label: string;
    status: string | null;
    paymentStatus?: string | null;
    emptyResult?: boolean;
    countKey: keyof OrderStatsResponse;
}[] = [
    { key: "ALL", label: "Tất cả", status: null, countKey: "total" },
    {
        key: "PENDING_PAYMENT",
        label: "Chờ thanh toán",
        status: "PENDING_PAYMENT",
        countKey: "pending"
    },
    {
        key: "PROCESSING",
        label: "Xử lý",
        status: "PAYMENT_CONFIRMED",
        countKey: "processing"
    },
    {
        key: "SHIPPING",
        label: "Giao",
        status: null,
        emptyResult: true,
        countKey: "shipping"
    },
    {
        key: "COMPLETED",
        label: "Hoàn thành",
        status: "COMPLETED",
        countKey: "completed"
    },
    {
        key: "RETURN_REFUND",
        label: "Hoàn tiền",
        status: null,
        paymentStatus: "REFUNDED",
        countKey: "returnedOrRefunded"
    }
];

function getErrorMessage(error: unknown) {
    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string"
    ) {
        return error.message;
    }

    return "Không thể tải dữ liệu. Vui lòng thử lại.";
}

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

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

export function OrderListPage() {
    const navigate = useNavigate();
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
    const [filterStatus, setFilterStatus] =
        useState<OrderStatusTabKey>("ALL");
    const [currentPage, setCurrentPage] = useState(0);
    const [activeOrderId, setActiveOrderId] = useState<string | null>(null);
    const [activeSummary, setActiveSummary] =
        useState<OrderSummaryResponse | null>(null);
    const [activeOrder, setActiveOrder] =
        useState<OrderDetailResponse | null>(null);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [openActionCode, setOpenActionCode] = useState<string | null>(null);
    const [ordersPage, setOrdersPage] = useState(emptyPage);
    const [stats, setStats] = useState<OrderStatsResponse>({});
    const [isOrdersLoading, setIsOrdersLoading] = useState(false);
    const [ordersError, setOrdersError] = useState<string | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);

    const activeStatusTab = ORDER_STATUS_TABS.find(
        (tab) => tab.key === filterStatus
    );
    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsOrdersLoading(true);
            setOrdersError(null);

            if (activeStatusTab?.emptyResult) {
                setOrdersPage(emptyPage);
                setIsOrdersLoading(false);
                return;
            }

            getOrders({
                fromDate: timeRange?.fromDate,
                keyword: debouncedKeyword,
                status: activeStatusTab?.status ?? undefined,
                paymentStatus: activeStatusTab?.paymentStatus ?? undefined,
                toDate: timeRange?.toDate,
                page: currentPage,
                size: PAGE_SIZE
            })
                .then((page) => {
                    if (!ignore) {
                        setOrdersPage({
                            ...emptyPage,
                            ...page,
                            content: page.content ?? []
                        });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setOrdersPage(emptyPage);
                        setOrdersError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsOrdersLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        activeStatusTab?.paymentStatus,
        activeStatusTab?.emptyResult,
        activeStatusTab?.status,
        currentPage,
        debouncedKeyword,
        timeRange?.fromDate,
        timeRange?.toDate
    ]);

    useEffect(() => {
        let ignore = false;

        getOrderStats({
            fromDate: timeRange?.fromDate,
            toDate: timeRange?.toDate
        })
            .then((response) => {
                if (!ignore) {
                    setStats(response ?? {});
                }
            })
            .catch(() => {
                if (!ignore) {
                    setStats({});
                }
            });

        return () => {
            ignore = true;
        };
    }, [timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        if (!activeOrderId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsDetailLoading(true);
            setDetailError(null);

            getOrderById(activeOrderId)
                .then((order) => {
                    if (!ignore) {
                        setActiveOrder(order);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setActiveOrder(null);
                        setDetailError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsDetailLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [activeOrderId]);

    const visibleOrders = ordersPage.content ?? [];
    const totalPages = Math.max(ordersPage.totalPages ?? 0, 1);
    const totalElements = ordersPage.totalElements ?? 0;

    const selectFilterStatus = (status: OrderStatusTabKey) => {
        setFilterStatus(status);
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

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setCurrentPage(0);
    };

    const selectOrder = (order: OrderSummaryResponse) => {
        setActiveSummary(order);
        setActiveOrderId(order.id);
    };

    const closeOrderDetail = () => {
        setActiveOrderId(null);
        setActiveOrder(null);
        setActiveSummary(null);
        setDetailError(null);
        setIsDetailLoading(false);
    };

    const handleAction = (action: string, order: OrderSummaryResponse) => {
        if (action === "Hoàn hàng") {
            navigate(`/system/orders/${order.id}/returns/create`);
            setOpenActionCode(null);
            return;
        }

        if (action === "Xem chi tiết" || action === "Sửa đơn") {
            selectOrder(order);
        }

        setOpenActionCode(null);
    };

    const handleClickImport = () => {
        importInputRef.current?.click();
    };

    const handleImportFile = (file?: File) => {
        if (!file) {
            return;
        }

        console.log("TODO: Nhập file đơn hàng", file);
        window.alert("Chức năng nhập file đang được phát triển");
    };

    const buildCurrentFilterExportParams = () =>
        buildActiveOrderExportParams({
            fromDate: timeRange?.fromDate,
            keyword: debouncedKeyword,
            status: activeStatusTab?.status,
            paymentStatus: activeStatusTab?.paymentStatus,
            toDate: timeRange?.toDate
        });

    const fromRecord =
        totalElements > 0 ? ordersPage.number * ordersPage.size + 1 : 0;
    const toRecord = Math.min(
        (ordersPage.number + 1) * ordersPage.size,
        totalElements
    );
    const isOrderDetailOpen = Boolean(
        activeOrderId ||
            activeSummary ||
            activeOrder ||
            isDetailLoading ||
            detailError
    );

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
                        <h1>Tất cả đơn hàng</h1>
                        <p>
                            Quản lý và theo dõi tất cả đơn hàng trong hệ thống
                        </p>
                    </div>

                    <div className={styles.headerActions}>
                        <input
                            ref={importInputRef}
                            accept=".xlsx,.xls,.csv"
                            className={styles.hiddenFileInput}
                            onChange={(event) => {
                                handleImportFile(event.target.files?.[0]);
                                event.target.value = "";
                            }}
                            type="file"
                        />
                        <button className={styles.primaryButton} type="button">
                            <Plus size={18} aria-hidden="true" />
                            Đặt hàng
                        </button>
                        <button
                            className={styles.secondaryButton}
                            onClick={handleClickImport}
                            type="button"
                        >
                            <Upload size={18} aria-hidden="true" />
                            Nhập file
                        </button>
                        <ExportDropdown
                            allDescription="Bỏ qua các bộ lọc hiện tại"
                            allFilenamePrefix="tat-ca-don-hang"
                            allLabel="Xuất toàn bộ đơn hàng"
                            buttonClassName={styles.secondaryButton}
                            emptyFilteredMessage="Không có đơn hàng phù hợp với bộ lọc để xuất file."
                            fallbackErrorMessage="Không thể xuất file đơn hàng. Vui lòng thử lại."
                            filteredDescription="Áp dụng thời gian, trạng thái và từ khóa đang chọn"
                            filteredFilenamePrefix="don-hang-theo-bo-loc"
                            hasNoFilteredData={
                                Boolean(activeStatusTab?.emptyResult) ||
                                (!isOrdersLoading &&
                                    !ordersError &&
                                    totalElements === 0)
                            }
                            onExport={(mode) =>
                                exportOrders(
                                    mode === "filtered"
                                        ? buildCurrentFilterExportParams()
                                        : {}
                                )
                            }
                            successMessage="Đã xuất file đơn hàng thành công."
                        />
                    </div>
                </div>

                <div
                    className={`${styles.workspace} ${styles.workspaceExpanded}`}
                >
                    <div className={styles.tablePanel}>
                        <div className={styles.filterRows}>
                            <div className={styles.filterToolbar}>
                                <label className={styles.searchBox}>
                                    <Search size={20} aria-hidden="true" />
                                    <input
                                        aria-label="Tìm kiếm đơn hàng"
                                        onChange={(event) =>
                                            handleSearchChange(
                                                event.target.value
                                            )
                                        }
                                        onKeyDown={(event) => {
                                            if (event.key === "Escape") {
                                                handleSearchChange("");
                                            }
                                        }}
                                        placeholder="Tìm mã đơn, khách hàng, SĐT..."
                                        value={searchTerm}
                                    />
                                </label>

                                <div className={styles.filterGroup}>
                                    <span className={styles.filterLabel}>
                                        Trạng thái:
                                    </span>
                                    <div className={styles.quickFilters}>
                                        {ORDER_STATUS_TABS.map((filter) => (
                                            <button
                                                className={`${styles.filterChip} ${
                                                    filterStatus === filter.key
                                                        ? styles.activeFilterChip
                                                        : ""
                                                }`}
                                                key={filter.key}
                                                onClick={() =>
                                                    selectFilterStatus(
                                                        filter.key
                                                    )
                                                }
                                                type="button"
                                            >
                                                {filter.label}{" "}
                                                <span>
                                                    {stats[filter.countKey] ??
                                                        0}
                                                </span>
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className={styles.tableCard}>
                            <div className={styles.tableWrap}>
                                <table className={styles.orderTable}>
                                <colgroup>
                                    <col style={{ width: "22%" }} />
                                    <col style={{ width: "40%" }} />
                                    <col style={{ width: "20%" }} />
                                    <col style={{ width: "21%" }} />
                                    <col style={{ width: "10%" }} />
                                </colgroup>
                                <thead>
                                    <tr>
                                        <th>Mã đơn hàng</th>
                                        <th>Trạng thái</th>
                                        <th>Giá trị đơn hàng</th>
                                        <th>Ngày đặt</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {isOrdersLoading ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={5}
                                            >
                                                <strong>Đang tải đơn hàng</strong>
                                                <span>
                                                    Dữ liệu đang được đồng bộ từ
                                                    Order-Service
                                                </span>
                                            </td>
                                        </tr>
                                    ) : null}

                                    {!isOrdersLoading && ordersError ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={5}
                                            >
                                                <strong>
                                                    Không tải được danh sách đơn hàng
                                                </strong>
                                                <span>{ordersError}</span>
                                            </td>
                                        </tr>
                                    ) : null}

                                    {!isOrdersLoading && !ordersError
                                        ? visibleOrders.map((order, index) => (
                                              <tr
                                                  className={`${
                                                      index % 2 === 0
                                                          ? styles.zebraFirstRow
                                                          : styles.zebraSecondRow
                                                  } ${
                                                      activeOrderId === order.id
                                                          ? styles.activeRow
                                                          : ""
                                                  }`}
                                                  key={order.id}
                                                  onClick={() =>
                                                      selectOrder(order)
                                                  }
                                              >
                                                  <td className={styles.code}>
                                                      {getOrderCode(order)}
                                                  </td>
                                                  <td>
                                                      <div
                                                          className={
                                                              styles.statusCell
                                                          }
                                                      >
                                                          <OrderStatusBadge
                                                              status={
                                                                  order.status
                                                              }
                                                              label={getOrderStatusText(
                                                                  order
                                                              )}
                                                          />
                                                      </div>
                                                  </td>
                                                  <td>
                                                      <strong>
                                                          {formatCurrency(
                                                              order.finalAmount
                                                          )}
                                                      </strong>
                                                  </td>
                                                  <td>
                                                      {formatDateOnly(
                                                          order.createdAt
                                                      )}
                                                  </td>
                                                  <td
                                                      className={
                                                          styles.actionCell
                                                      }
                                                  >
                                                      <div
                                                          className={
                                                              styles.actionButtonWrap
                                                          }
                                                      >
                                                          <button
                                                              aria-expanded={
                                                                  openActionCode ===
                                                                  order.id
                                                              }
                                                              aria-label={`Mở thao tác cho đơn ${getOrderCode(order)}`}
                                                              className={
                                                                  styles.rowActionButton
                                                              }
                                                              onClick={(
                                                                  event
                                                              ) => {
                                                                  event.stopPropagation();
                                                                  setOpenActionCode(
                                                                      (
                                                                          current
                                                                      ) =>
                                                                          current ===
                                                                          order.id
                                                                              ? null
                                                                              : order.id
                                                                  );
                                                              }}
                                                              type="button"
                                                          >
                                                              <MoreHorizontal
                                                                  size={18}
                                                                  aria-hidden="true"
                                                              />
                                                          </button>
                                                      </div>
                                                      {openActionCode ===
                                                      order.id ? (
                                                          <div
                                                              className={
                                                                  styles.actionMenu
                                                              }
                                                          >
                                                              {[
                                                                  "Xem chi tiết",
                                                                  "Sửa đơn",
                                                                  "In hóa đơn",
                                                                  "Hủy đơn",
                                                                  "Hoàn hàng"
                                                              ].map((action) => (
                                                                  <button
                                                                      key={
                                                                          action
                                                                      }
                                                                      onClick={(
                                                                          event
                                                                      ) => {
                                                                          event.stopPropagation();
                                                                          handleAction(
                                                                              action,
                                                                              order
                                                                          );
                                                                      }}
                                                                      type="button"
                                                                  >
                                                                      {action}
                                                                  </button>
                                                              ))}
                                                          </div>
                                                      ) : null}
                                                  </td>
                                              </tr>
                                          ))
                                        : null}

                                    {!isOrdersLoading &&
                                    !ordersError &&
                                    visibleOrders.length === 0 ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={5}
                                            >
                                                <strong>
                                                    Không tìm thấy đơn hàng phù hợp
                                                </strong>
                                                <span>
                                                    Thử đổi từ khóa hoặc bộ lọc
                                                </span>
                                            </td>
                                        </tr>
                                    ) : null}
                                </tbody>
                                </table>
                            </div>

                            <div className={styles.pagination}>
                                <span>
                                    Hiển thị {fromRecord} - {toRecord} trong{" "}
                                    {totalElements} đơn hàng
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

                    {isOrderDetailOpen ? (
                        <div
                            className={styles.detailModalBackdrop}
                            onClick={closeOrderDetail}
                            role="presentation"
                        >
                            <div
                                aria-modal="true"
                                className={styles.detailModal}
                                onClick={(event) => event.stopPropagation()}
                                role="dialog"
                            >
                                <OrderDetailPanel
                                    order={activeOrder}
                                    summary={activeSummary}
                                    loading={isDetailLoading}
                                    error={detailError}
                                    onCreateReturn={(targetOrderId) =>
                                        navigate(
                                            `/system/orders/${targetOrderId}/returns/create`
                                        )
                                    }
                                    onClose={closeOrderDetail}
                                />
                            </div>
                        </div>
                    ) : null}
                </div>
            </div>

        </section>
    );
}
