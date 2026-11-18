import { Banknote, CreditCard, Landmark } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { StatusBadge, type BadgeTone } from "../../../components/ui/StatusBadge";
import { toast } from "../../../components/ui/Toast";
import { SalesOrderDetailDrawer } from "../components/sales-history/SalesOrderDetailDrawer";
import {
  OrderDataTable,
  OrderListWorkspace,
  OrderPageLayout,
  OrderPagination,
  OrderStatusFilters,
  OrderToolbar,
  type OrderStatusOption,
  type OrderTableColumn,
} from "../components/shared/OrderListUI";
import {
  SALES_PAYMENT_METHOD_OPTIONS,
  SALES_STATUS_FILTER_OPTIONS,
} from "../constants/sales-history-options";
import {
  getSalesOrderDetail,
  getTodaySales,
  getTodaySalesSummary,
} from "../services/sales-history.api";
import type {
  SalesHistoryFilters as SalesHistoryFilterValues,
  SalesHistoryOrder,
  SalesHistoryPageResponse,
  SalesHistoryPaymentFilter,
  SalesHistoryStatusFilter,
  SalesHistorySummary,
  SalesOrderDetail,
  SalesPaymentMethod,
} from "../types/sales-history.types";
import { printReceipt } from "../utils/receipt-print";
import {
  formatCurrency,
  formatSaleTime,
  getOrderSaleTime,
  getSalesOrderCustomer,
  getSalesPaymentMethodLabel,
  getSalesStatusLabel,
} from "../utils/sales-history-formatters";
import styles from "./SalesHistoryPage.module.css";

const PAGE_SIZE = 10;

const defaultFilters: SalesHistoryFilterValues = {
  status: "ALL",
  paymentMethod: "ALL",
};

const emptyPage: SalesHistoryPageResponse<SalesHistoryOrder> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: PAGE_SIZE,
};

const emptyStatusCounts: Record<SalesHistoryStatusFilter, number> = {
  ALL: 0,
  PENDING_PAYMENT: 0,
  COMPLETED: 0,
  CANCELLED: 0,
};

const emptyPaymentCounts: Record<SalesHistoryPaymentFilter, number> = {
  ALL: 0,
  CASH: 0,
  BANK_TRANSFER: 0,
  DEBT: 0,
};

function getErrorMessage(error: unknown) {
  if (
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof error.message === "string"
  ) {
    return error.message;
  }

  return "Không thể tải lịch sử bán hàng. Vui lòng thử lại.";
}

function normalizeSearchValue(value?: string | null) {
  return value?.trim().toLowerCase() ?? "";
}

function matchesSearchTerm(order: SalesHistoryOrder, keyword: string) {
  if (!keyword) {
    return true;
  }

  return [
    order.orderCode,
    order.customerName,
    order.customerPhone,
  ].some((value) => normalizeSearchValue(value).includes(keyword));
}

function isPendingPayment(order: SalesHistoryOrder) {
  return (
    order.status === "PENDING_PAYMENT" ||
    order.paymentStatus === "UNPAID" ||
    order.paymentStatus === "WAITING_PAYMENT" ||
    order.paymentStatus === "PENDING_PAYMENT" ||
    order.paymentStatus === "PENDING"
  );
}

function isCompleted(order: SalesHistoryOrder) {
  return order.status === "COMPLETED" || order.paymentStatus === "PAID";
}

function isCancelled(order: SalesHistoryOrder) {
  return order.status === "CANCELLED";
}

function getPaymentCountKey(
  order: SalesHistoryOrder,
): SalesHistoryPaymentFilter | null {
  if (order.paymentMethod === "CASH") {
    return "CASH";
  }

  if (
    order.paymentMethod === "BANK_TRANSFER" ||
    order.paymentMethod === "TRANSFER" ||
    order.paymentMethod === "QR_CODE"
  ) {
    return "BANK_TRANSFER";
  }

  if (order.paymentMethod === "DEBT") {
    return "DEBT";
  }

  return null;
}

function getPaymentIcon(method?: SalesPaymentMethod | null) {
  if (method === "CASH") {
    return Banknote;
  }

  if (
    method === "BANK_TRANSFER" ||
    method === "TRANSFER" ||
    method === "QR_CODE"
  ) {
    return Landmark;
  }

  return CreditCard;
}

function getStatusTone(order: SalesHistoryOrder): BadgeTone {
  if (order.paymentStatus === "PAID" || order.status === "COMPLETED") {
    return "success";
  }

  if (order.status === "CANCELLED") {
    return "danger";
  }

  return "pending";
}

export function SalesHistoryPage() {
  const [filters, setFilters] =
    useState<SalesHistoryFilterValues>(defaultFilters);
  const [searchTerm, setSearchTerm] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [ordersPage, setOrdersPage] = useState(emptyPage);
  const [summary, setSummary] = useState<SalesHistorySummary | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] =
    useState<SalesOrderDetail | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  const loadOrders = useCallback(() => {
    setListLoading(true);
    setListError(null);

    return getTodaySales({
      filters,
      page: currentPage,
      size: PAGE_SIZE,
    })
      .then((response) => {
        setOrdersPage({
          ...emptyPage,
          ...response,
          content: response.content ?? [],
        });
      })
      .catch((error: unknown) => {
        setOrdersPage(emptyPage);
        setListError(getErrorMessage(error));
      })
      .finally(() => {
        setListLoading(false);
      });
  }, [currentPage, filters]);

  useEffect(() => {
    let ignore = false;
    const timerId = window.setTimeout(() => {
      loadOrders().then(() => {
        if (ignore) {
          return;
        }
      });
    }, 0);

    return () => {
      ignore = true;
      window.clearTimeout(timerId);
    };
  }, [loadOrders]);

  useEffect(() => {
    let ignore = false;

    getTodaySalesSummary()
      .then((response) => {
        if (!ignore) {
          setSummary(response);
        }
      })
      .catch(() => {
        if (!ignore) {
          setSummary(null);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedOrderId) {
      return;
    }

    let ignore = false;
    const timerId = window.setTimeout(() => {
      setDetailLoading(true);
      setDetailError(null);
      setSelectedOrder(null);

      getSalesOrderDetail(selectedOrderId)
        .then((response) => {
          if (!ignore) {
            setSelectedOrder(response);
          }
        })
        .catch((error: unknown) => {
          if (!ignore) {
            setDetailError(getErrorMessage(error));
          }
        })
        .finally(() => {
          if (!ignore) {
            setDetailLoading(false);
          }
        });
    }, 0);

    return () => {
      ignore = true;
      window.clearTimeout(timerId);
    };
  }, [selectedOrderId]);

  const closeDetail = () => {
    setSelectedOrderId(null);
    setSelectedOrder(null);
    setDetailError(null);
    setDetailLoading(false);
  };

  const handlePrintDetail = (order: SalesOrderDetail) => {
    try {
      printReceipt(order);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Không in được hóa đơn.",
      );
    }
  };

  const handleFilterStatusChange = (status: SalesHistoryStatusFilter) => {
    setFilters((prev) => ({ ...prev, status }));
    setCurrentPage(0);
  };

  const handleFilterPaymentMethodChange = (
    paymentMethod: SalesHistoryPaymentFilter,
  ) => {
    setFilters((prev) => ({ ...prev, paymentMethod }));
    setCurrentPage(0);
  };

  const handleSearchChange = (value: string) => {
    setSearchTerm(value);
    setCurrentPage(0);
  };

  const normalizedSearchTerm = normalizeSearchValue(searchTerm);
  const visibleOrders = useMemo(
    () =>
      ordersPage.content.filter((order) =>
        matchesSearchTerm(order, normalizedSearchTerm),
      ),
    [normalizedSearchTerm, ordersPage.content],
  );

  const statusCounts = useMemo(() => {
    const counts = { ...emptyStatusCounts };

    ordersPage.content.forEach((order) => {
      if (isPendingPayment(order)) {
        counts.PENDING_PAYMENT += 1;
      }

      if (isCompleted(order)) {
        counts.COMPLETED += 1;
      }

      if (isCancelled(order)) {
        counts.CANCELLED += 1;
      }
    });

    counts.ALL = summary?.totalOrders ?? ordersPage.totalElements ?? 0;
    counts.PENDING_PAYMENT =
      summary?.pendingPaymentOrders ?? counts.PENDING_PAYMENT;
    counts.COMPLETED = summary?.completedOrders ?? counts.COMPLETED;

    return counts;
  }, [ordersPage.content, ordersPage.totalElements, summary]);

  const paymentCounts = useMemo(() => {
    const counts = { ...emptyPaymentCounts };

    ordersPage.content.forEach((order) => {
      const paymentKey = getPaymentCountKey(order);

      if (paymentKey) {
        counts[paymentKey] += 1;
      }
    });

    counts.ALL = ordersPage.content.length;

    return counts;
  }, [ordersPage.content]);

  const statusOptions: readonly OrderStatusOption<SalesHistoryStatusFilter>[] =
    useMemo(
      () =>
        SALES_STATUS_FILTER_OPTIONS.map((option) => ({
          ...option,
          count: statusCounts[option.key] ?? 0,
        })),
      [statusCounts],
    );

  const paymentMethodOptions: readonly OrderStatusOption<SalesHistoryPaymentFilter>[] =
    useMemo(
      () =>
        SALES_PAYMENT_METHOD_OPTIONS.map((option) => ({
          ...option,
          count: paymentCounts[option.key] ?? 0,
        })),
      [paymentCounts],
    );

  const totalElements = normalizedSearchTerm
    ? visibleOrders.length
    : (ordersPage.totalElements ?? 0);
  const totalPages = normalizedSearchTerm
    ? Math.max(Math.ceil(visibleOrders.length / PAGE_SIZE), 1)
    : Math.max(ordersPage.totalPages ?? 0, 1);

  const columns: readonly OrderTableColumn<SalesHistoryOrder>[] = useMemo(
    () => [
      {
        key: "orderCode",
        header: "Mã hóa đơn",
        width: "18%",
        align: "left",
        cellClassName: styles.code,
        render: (order) => <>{order.orderCode}</>,
      },
      {
        key: "saleTime",
        header: "Thời gian",
        width: "14%",
        align: "left",
        render: (order) => <>{formatSaleTime(getOrderSaleTime(order))}</>,
      },
      {
        key: "customer",
        header: "Khách hàng",
        width: "22%",
        align: "left",
        render: (order) => (
          <>
            <strong>{getSalesOrderCustomer(order)}</strong>
            {order.customerPhone ? (
              <span className={styles.mutedBlock}>{order.customerPhone}</span>
            ) : null}
          </>
        ),
      },
      {
        key: "paymentMethod",
        header: "Thanh toán",
        width: "16%",
        align: "left",
        render: (order) => {
          const PaymentIcon = getPaymentIcon(order.paymentMethod);

          return (
            <StatusBadge shape="tag">
              <span className="inline-flex items-center gap-1.5 align-middle">
                <PaymentIcon size={14} aria-hidden="true" />
                {getSalesPaymentMethodLabel(
                  order.paymentMethod,
                  order.paymentMethodLabel,
                )}
              </span>
            </StatusBadge>
          );
        },
      },
      {
        key: "finalAmount",
        header: "Tổng tiền",
        width: "15%",
        align: "left",
        render: (order) => <strong>{formatCurrency(order.finalAmount)}</strong>,
      },
      {
        key: "status",
        header: "Trạng thái",
        width: "15%",
        align: "center",
        render: (order) => (
          <div className={styles.statusCell}>
            <StatusBadge tone={getStatusTone(order)}>
              {getSalesStatusLabel(order.status, order.statusLabel)}
            </StatusBadge>
          </div>
        ),
      },
    ],
    [],
  );

  return (
    <OrderPageLayout
      title="Bán hàng hôm nay"
      description="Theo dõi doanh thu, hóa đơn và giao dịch trong ngày."
    >
      <OrderListWorkspace>
        <OrderToolbar
          search={{
            "aria-label": "Tìm kiếm hóa đơn bán hàng hôm nay",
            onChange: handleSearchChange,
            onKeyDown: (event) => {
              if (event.key === "Escape") {
                handleSearchChange("");
              }
            },
            placeholder: "Tìm mã đơn, khách hàng, SĐT...",
            value: searchTerm,
          }}
        >
          <OrderStatusFilters<SalesHistoryStatusFilter>
            label="Trạng thái:"
            options={statusOptions}
            value={filters.status}
            onChange={handleFilterStatusChange}
          />
          <div className={styles.paymentFilterRow}>
            <OrderStatusFilters<SalesHistoryPaymentFilter>
              label="Phương thức thanh toán:"
              options={paymentMethodOptions}
              value={filters.paymentMethod}
              onChange={handleFilterPaymentMethodChange}
            />
          </div>
        </OrderToolbar>

        <OrderDataTable
          rows={visibleOrders}
          getRowKey={(order) => order.orderId}
          onRowClick={(order) => setSelectedOrderId(order.orderId)}
          isRowActive={(order) => selectedOrderId === order.orderId}
          loading={listLoading}
          error={listError}
          loadingTitle="Đang tải hóa đơn"
          loadingDescription="Dữ liệu đang được đồng bộ từ Order-Service"
          errorTitle="Không tải được danh sách hóa đơn"
          emptyTitle={
            normalizedSearchTerm ||
            filters.status !== "ALL" ||
            filters.paymentMethod !== "ALL"
              ? "Không tìm thấy hóa đơn phù hợp"
              : "Chưa có hóa đơn nào trong ngày"
          }
          emptyDescription={
            normalizedSearchTerm ||
            filters.status !== "ALL" ||
            filters.paymentMethod !== "ALL"
              ? "Thử đổi từ khóa hoặc bộ lọc"
              : "Các đơn đã thanh toán sẽ hiển thị tại đây."
          }
          caption="Bán hàng hôm nay"
          minWidth={840}
          columns={columns}
          onRetry={loadOrders}
          pagination={
            <OrderPagination
              page={currentPage}
              recordPage={normalizedSearchTerm ? 0 : ordersPage.number}
              pageSize={ordersPage.size}
              totalPages={totalPages}
              totalItems={totalElements}
              itemLabel="hóa đơn"
              onPageChange={setCurrentPage}
            />
          }
        />
      </OrderListWorkspace>

      <SalesOrderDetailDrawer
        order={selectedOrder}
        loading={detailLoading}
        error={detailError}
        open={Boolean(selectedOrderId)}
        onClose={closeDetail}
        onPrint={handlePrintDetail}
      />
    </OrderPageLayout>
  );
}
