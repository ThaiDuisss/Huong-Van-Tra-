import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    FileSpreadsheet,
    MoreHorizontal,
    Plus,
    Search
} from "lucide-react";
import { useEffect, useState } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import { OrderDetailPanel } from "../components/OrderDetailPanel";
import { OrderStatusBadge } from "../components/OrderStatusBadge";
import {
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
        status: "PROCESSING",
        countKey: "processing"
    },
    {
        key: "SHIPPING",
        label: "Giao",
        status: "SHIPPING",
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
        // TODO: khi backend hỗ trợ nhiều status/lần, truyền RETURNED và REFUNDED cùng lúc.
        status: "RETURNED",
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
    const [searchTerm, setSearchTerm] = useState("");
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

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsOrdersLoading(true);
            setOrdersError(null);

            getOrders({
                keyword: debouncedKeyword,
                status: activeStatusTab?.status ?? undefined,
                sort: "businessStatus",
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
    }, [activeStatusTab?.status, currentPage, debouncedKeyword]);

    useEffect(() => {
        let ignore = false;

        getOrderStats()
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
    }, []);

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
        if (action === "Xem chi tiết" || action === "Sửa đơn") {
            selectOrder(order);
        }

        setOpenActionCode(null);
    };

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
                collapsed={isTimeCollapsed}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
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
                        <button className={styles.primaryButton} type="button">
                            <Plus size={18} aria-hidden="true" />
                            Đặt hàng
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <FileSpreadsheet size={18} aria-hidden="true" />
                            Xuất file
                            <ChevronDown size={16} aria-hidden="true" />
                        </button>
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
                                        ? visibleOrders.map((order) => (
                                              <tr
                                                  className={
                                                      activeOrderId === order.id
                                                          ? styles.activeRow
                                                          : ""
                                                  }
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
