import {
  OrderPageLayout,
  OrderListWorkspace,
  OrderToolbar,
  OrderStatusFilters,
  OrderDataTable,
  OrderPagination,
} from "../components/shared/OrderListUI";
import { Plus, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { Button } from "../../../components/ui/Button";
import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import { useTimeFilter } from "../../../components/shared/useTimeFilter";
import { routePaths } from "../../../routes/path";
import { ReturnDetailModal } from "../components/return-detail/ReturnDetailModal";
import { ReturnStatusBadge } from "../components/ReturnStatusBadge";
import {
  getOrderReturnList,
  getOrderReturnStatusCounts,
} from "../services/order-return.api";
import type { OrderPageResponse } from "../types/order.types";
import type { OrderReturnListRow, ReturnFilterKey } from "../types/order-return.types";
import { formatDateTime } from "../utils/order-formatters";
import {
  getReturnCode,
  getReturnErrorMessage,
} from "../utils/order-return.utils";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = ReturnFilterKey;

const RETURN_STATUS_FILTERS: {
  key: ReturnStatusFilter;
  label: string;
  isError?: boolean;
}[] = [
  { key: "ALL", label: "Tất cả" },
  { key: "CREATED", label: "Chờ nhận hàng" },
  { key: "RECEIVED", label: "Chờ kiểm hàng" },
  { key: "PROCESSING", label: "Đang xử lý" },
  { key: "PENDING_REFUND", label: "Chờ hoàn tiền" },
  { key: "COMPLETED", label: "Hoàn thành" },
  { key: "CANCELLED", label: "Đã hủy" },
  { key: "PROCESSING_FAILED", label: "Có lỗi", isError: true },
];

const PAGE_SIZE = 10;
/** Tham số URL mở sẵn hộp thoại chi tiết, vd. `/system/orders/returns?detail=<id>`. */
const DETAIL_PARAM = "detail";

const emptyReturnPage: OrderPageResponse<OrderReturnListRow> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: PAGE_SIZE,
};

function useDebouncedValue<T>(value: T, delayMs: number) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
    return () => window.clearTimeout(timerId);
  }, [delayMs, value]);

  return debouncedValue;
}

export function OrderReturnPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);

  const [statusFilter, setStatusFilter] = useState<ReturnStatusFilter>("ALL");
  const [currentPage, setCurrentPage] = useState(0);

  const {
    selectedTimeNode,
    selectTimeNode,
    clearTimeRange,
    isTimeCollapsed,
    setIsTimeCollapsed,
    timeRange,
  } = useTimeFilter(() => setCurrentPage(0));

  const [returnPage, setReturnPage] = useState(emptyReturnPage);
  const [isRowsLoading, setIsRowsLoading] = useState(false);
  const [rowsError, setRowsError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [statusCounts, setStatusCounts] =
    useState<Record<ReturnStatusFilter, number> | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const detailId = searchParams.get(DETAIL_PARAM);
  const visibleRows = returnPage.content ?? [];
  const activeReturn: OrderReturnListRow | null = detailId
    ? (visibleRows.find((row) => row.id === detailId) ?? { id: detailId })
    : null;

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
        keyword: debouncedSearchTerm || undefined,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        displayStatus: statusFilter === "ALL" ? undefined : statusFilter,
        toDate: timeRange?.toDate,
        page: currentPage,
        size: PAGE_SIZE,
        sort: "createdAt,desc",
      })
        .then((page) => {
          if (!ignore) {
            setReturnPage({
              ...emptyReturnPage,
              ...page,
              content: page.content ?? [],
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
    timeRange?.toDate,
  ]);

  useEffect(() => {
    let ignore = false;

    getOrderReturnStatusCounts({
      fromDate: timeRange?.fromDate,
      keyword: debouncedSearchTerm || undefined,
      toDate: timeRange?.toDate,
    })
      .then((counts) => {
        if (!ignore && counts) {
          setStatusCounts({
            ALL: counts.ALL ?? 0,
            CREATED: counts.CREATED ?? 0,
            RECEIVED: counts.RECEIVED ?? 0,
            PROCESSING: counts.PROCESSING ?? 0,
            PENDING_REFUND: counts.PENDING_REFUND ?? 0,
            COMPLETED: counts.COMPLETED ?? 0,
            CANCELLED: counts.CANCELLED ?? 0,
            PROCESSING_FAILED: counts.PROCESSING_FAILED ?? 0,
          });
        }
      })
      .catch((error: unknown) => {
        if (!ignore) {
          console.warn("Không thể tải số lượng trạng thái phiếu hoàn:", error);
        }
      });

    return () => {
      ignore = true;
    };
  }, [debouncedSearchTerm, reloadKey, timeRange?.fromDate, timeRange?.toDate]);

  const totalPages = Math.max(returnPage.totalPages ?? 0, 1);
  const totalElements = returnPage.totalElements ?? 0;

  const setDetailParam = (returnId: string | null) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (returnId) next.set(DETAIL_PARAM, returnId);
      else next.delete(DETAIL_PARAM);
      return next;
    });
  };

  const handleSearchChange = (value: string) => {
    setSearchTerm(value);
    setCurrentPage(0);
  };

  const selectFilterStatus = (status: ReturnStatusFilter) => {
    setStatusFilter(status);
    setCurrentPage(0);
  };

  return (
    <OrderPageLayout
      title="Hoàn hàng"
      description="Quản lý toàn bộ quy trình hoàn hàng từ tiếp nhận, kiểm hàng đến hoàn tiền"
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
          <Button
            disabled={isRowsLoading}
            onClick={() => setReloadKey((key) => key + 1)}
            size="lg"
          >
            <RefreshCw size={17} aria-hidden="true" />
            Làm mới
          </Button>
          <Button onClick={() => navigate(routePaths.returnCreate)} size="lg" variant="primary">
            <Plus size={17} aria-hidden="true" />
            Tạo phiếu hoàn hàng
          </Button>
        </>
      }
    >
      <OrderListWorkspace>
        <OrderToolbar
          search={{
            "aria-label": "Tìm kiếm phiếu hoàn hàng",
            onChange: handleSearchChange,
            onKeyDown: (event) => {
              if (event.key === "Escape") {
                handleSearchChange("");
              }
            },
            placeholder: "Tìm mã phiếu, mã đơn, khách hàng...",
            value: searchTerm,
          }}
        >
          <OrderStatusFilters
            label="Trạng thái:"
            options={RETURN_STATUS_FILTERS.map((filter) => ({
              ...filter,
              count: statusCounts ? (statusCounts[filter.key] ?? 0) : undefined,
            }))}
            value={statusFilter}
            onChange={selectFilterStatus}
          />
        </OrderToolbar>
        <OrderDataTable
          caption="Danh sách phiếu hoàn hàng"
          columns={[
            {
              key: "code",
              header: "Mã phiếu hoàn",
              width: "22%",
              align: "left",
              cellClassName: styles.code,
              render: (row) => (
                <span title={getReturnCode(row)}>{getReturnCode(row)}</span>
              ),
            },
            {
              key: "status",
              header: "Trạng thái",
              width: "20%",
              align: "center",
              render: (row) => (
                <ReturnStatusBadge displayStatus={row.displayStatus} status={row.status} />
              ),
            },
            {
              key: "customer",
              header: "Khách hàng",
              width: "24%",
              align: "left",
              render: (row) => (
                <div className={styles.customerCell}>
                  <span className={styles.customerName}>{row.customerName?.trim() || "Khách lẻ"}</span>
                  {row.customerPhone ? <span className={styles.customerPhone}>{row.customerPhone}</span> : null}
                </div>
              ),
            },
            {
              key: "order",
              header: "Mã đơn hàng",
              width: "18%",
              align: "left",
              cellClassName: styles.code,
              render: (row) => (
                <span title={row.orderCode || undefined}>{row.orderCode || "--"}</span>
              ),
            },
            {
              key: "createdAt",
              header: "Ngày tạo",
              width: "16%",
              align: "left",
              render: (row) => formatDateTime(row.createdAt),
            },
          ]}
          rows={visibleRows}
          emptyDescription="Thử đổi từ khóa, trạng thái hoặc khoảng thời gian"
          emptyTitle="Chưa có phiếu hoàn phù hợp"
          error={rowsError}
          getRowKey={(row) => row.id}
          loading={isRowsLoading}
          loadingDescription="Dữ liệu đang được tải từ Order-Service"
          loadingTitle="Đang tải phiếu hoàn hàng"
          errorTitle="Không tải được danh sách phiếu hoàn hàng"
          onRetry={() => setReloadKey((key) => key + 1)}
          onRowClick={(row) => setDetailParam(row.id)}
          pagination={
            <OrderPagination
              disabled={isRowsLoading}
              itemLabel="phiếu hoàn"
              onPageChange={setCurrentPage}
              page={currentPage}
              pageSize={returnPage.size || PAGE_SIZE}
              totalItems={totalElements}
              totalPages={totalPages}
            />
          }
          minWidth={800}
        />
      </OrderListWorkspace>

      {activeReturn ? (
        <ReturnDetailModal
          allowCancel
          key={activeReturn.id}
          onChanged={() => setReloadKey((key) => key + 1)}
          onClose={() => setDetailParam(null)}
          onToast={setToast}
          returnItem={activeReturn}
        />
      ) : null}

      {toast ? (
        <div className={styles.toast} role="status">
          {toast}
        </div>
      ) : null}
    </OrderPageLayout>
  );
}
