import orderUiStyles from "../components/shared/OrderListUI.module.css";
import {
  OrderPageLayout,
  OrderListWorkspace,
  OrderToolbar,
  OrderStatusFilters,
  OrderDataTable,
  OrderPagination,
} from "../components/shared/OrderListUI";
import {
  Upload,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
  buildTodayTimeNode,
  getDateRangeFromTimeNode,
  type TimeNode,
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { ExportDropdown } from "../components/ExportDropdown";
import { OrderDetailPanel } from "../components/OrderDetailPanel";
import { OrderImportDialog } from "../components/OrderImportDialog";
import { OrderStatusBadge } from "../components/OrderStatusBadge";
import { exportOrders, getOrderById, getOrders, getOrderStats } from "../services/order.api";
import type {
  OrderDetailResponse,
  OrderPageResponse,
  OrderStatsResponse,
  OrderSummaryResponse,
} from "../types/order.types";
import {
  formatCurrency,
  formatDateOnly,
  getOrderCode,
  getOrderStatusText,
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
  size: PAGE_SIZE,
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
    countKey: "pending",
  },
  {
    key: "PROCESSING",
    label: "Xử lý",
    status: "PAYMENT_CONFIRMED",
    countKey: "processing",
  },
  {
    key: "SHIPPING",
    label: "Giao",
    status: null,
    emptyResult: true,
    countKey: "shipping",
  },
  {
    key: "COMPLETED",
    label: "Hoàn thành",
    status: "COMPLETED",
    countKey: "completed",
  },
  {
    key: "RETURN_REFUND",
    label: "Hoàn tiền",
    status: null,
    paymentStatus: "REFUNDED",
    countKey: "returnedOrRefunded",
  },
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

export function OrderListPage() {
  const navigate = useNavigate();
  const [isImportOpen, setIsImportOpen] = useState(false);
  /** Tăng sau khi nhập file để tải lại danh sách và thống kê. */
  const [reloadKey, setReloadKey] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(buildTodayTimeNode);
  const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
  const [filterStatus, setFilterStatus] = useState<OrderStatusTabKey>("ALL");
  const [currentPage, setCurrentPage] = useState(0);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(null);
  const [activeSummary, setActiveSummary] = useState<OrderSummaryResponse | null>(null);
  const [activeOrder, setActiveOrder] = useState<OrderDetailResponse | null>(null);
  const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
  const [ordersPage, setOrdersPage] = useState(emptyPage);
  const [stats, setStats] = useState<OrderStatsResponse>({});
  const [isOrdersLoading, setIsOrdersLoading] = useState(false);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const activeStatusTab = ORDER_STATUS_TABS.find((tab) => tab.key === filterStatus);
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
        size: PAGE_SIZE,
      })
        .then((page) => {
          if (!ignore) {
            setOrdersPage({
              ...emptyPage,
              ...page,
              content: page.content ?? [],
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
    reloadKey,
    timeRange?.fromDate,
    timeRange?.toDate,
  ]);

  useEffect(() => {
    let ignore = false;

    getOrderStats({
      fromDate: timeRange?.fromDate,
      toDate: timeRange?.toDate,
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
  }, [reloadKey, timeRange?.fromDate, timeRange?.toDate]);

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


  const buildCurrentFilterExportParams = () =>
    buildActiveOrderExportParams({
      fromDate: timeRange?.fromDate,
      keyword: debouncedKeyword,
      status: activeStatusTab?.status,
      paymentStatus: activeStatusTab?.paymentStatus,
      toDate: timeRange?.toDate,
    });

  const isOrderDetailOpen = Boolean(
    activeOrderId || activeSummary || activeOrder || isDetailLoading || detailError,
  );

  return (
    <OrderPageLayout
      title="Tất cả đơn hàng"
      description="Quản lý và theo dõi tất cả đơn hàng trong hệ thống"
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
        <>
          <button
            className={orderUiStyles.secondaryButton}
            onClick={() => setIsImportOpen(true)}
            type="button"
          >
            <Upload size={18} aria-hidden="true" />
            Nhập file
          </button>
          <ExportDropdown
            allDescription="Bỏ qua các bộ lọc hiện tại"
            allFilenamePrefix="tat-ca-don-hang"
            allLabel="Xuất toàn bộ đơn hàng"
            buttonClassName={orderUiStyles.secondaryButton}
            emptyFilteredMessage="Không có đơn hàng phù hợp với bộ lọc để xuất file."
            fallbackErrorMessage="Không thể xuất file đơn hàng. Vui lòng thử lại."
            filteredDescription="Áp dụng thời gian, trạng thái và từ khóa đang chọn"
            filteredFilenamePrefix="don-hang-theo-bo-loc"
            hasNoFilteredData={
              Boolean(activeStatusTab?.emptyResult) ||
              (!isOrdersLoading && !ordersError && totalElements === 0)
            }
            onExport={(mode) =>
              exportOrders(mode === "filtered" ? buildCurrentFilterExportParams() : {})
            }
            successMessage="Đã xuất file đơn hàng thành công."
          />
        </>
      }
    >
      <OrderListWorkspace>
        <OrderToolbar
          search={{
            "aria-label": "Tìm kiếm đơn hàng",
            onChange: (event) => handleSearchChange(event.target.value),
            onKeyDown: (event) => {
              if (event.key === "Escape") {
                handleSearchChange("");
              }
            },
            placeholder: "Tìm mã đơn, khách hàng, SĐT...",
            value: searchTerm,
          }}
        >
          <OrderStatusFilters
            label="Trạng thái:"
            options={ORDER_STATUS_TABS.map((filter) => ({
              ...filter,
              count: stats[filter.countKey] ?? 0,
            }))}
            value={filterStatus}
            onChange={selectFilterStatus}
          />
        </OrderToolbar>
        <OrderDataTable
          rows={visibleOrders}
          getRowKey={(order) => order.id}
          onRowClick={selectOrder}
          isRowActive={(order) => activeOrderId === order.id}
          loading={isOrdersLoading}
          error={ordersError}
          loadingTitle="Đang tải đơn hàng"
          loadingDescription="Dữ liệu đang được đồng bộ từ Order-Service"
          errorTitle="Không tải được danh sách đơn hàng"
          emptyTitle={"Không tìm thấy đơn hàng phù hợp"}
          emptyDescription="Thử đổi từ khóa hoặc bộ lọc"
          caption="Tất cả đơn hàng"
          minWidth={720}
          columns={[
            {
              key: "column-0",
              header: "Mã đơn hàng",
              width: "26%",
              align: "left",
              cellClassName: styles.code,
              render: (order) => <>{getOrderCode(order)}</>,
            },
            {
              key: "column-1",
              header: "Trạng thái",
              width: "30%",
              align: "center",
              render: (order) => (
                <>
                  <div className={styles.statusCell}>
                    <OrderStatusBadge status={order.status} label={getOrderStatusText(order)} />
                  </div>
                </>
              ),
            },
            {
              key: "column-2",
              header: "Giá trị đơn hàng",
              width: "24%",
              align: "left",
              render: (order) => (
                <>
                  <strong>{formatCurrency(order.finalAmount)}</strong>
                </>
              ),
            },
            {
              key: "column-3",
              header: "Ngày đặt",
              width: "20%",
              align: "left",
              render: (order) => <>{formatDateOnly(order.createdAt)}</>,
            },
          ]}
          pagination={
            <OrderPagination
              page={currentPage}
              recordPage={ordersPage.number}
              pageSize={ordersPage.size}
              totalPages={totalPages}
              totalItems={totalElements}
              itemLabel="đơn hàng"
              onPageChange={setCurrentPage}
            />
          }
        />
      </OrderListWorkspace>
      {isOrderDetailOpen ? (
        <div className={styles.detailModalBackdrop} onClick={closeOrderDetail} role="presentation">
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
                navigate(`/system/orders/${targetOrderId}/returns/create`)
              }
              onClose={closeOrderDetail}
            />
          </div>
        </div>
      ) : null}
      {isImportOpen ? (
        <OrderImportDialog
          onClose={() => setIsImportOpen(false)}
          onImported={() => setReloadKey((key) => key + 1)}
        />
      ) : null}
    </OrderPageLayout>
  );
}
