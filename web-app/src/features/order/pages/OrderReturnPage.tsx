import { Plus, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { DataTable } from "../../../components/shared/list/DataTable";
import { ListFilterToolbar } from "../../../components/shared/list/ListFilterToolbar";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import {
  LIST_CODE_TEXT,
  LIST_MUTED_TEXT,
  LIST_PRIMARY_BUTTON,
  LIST_SECONDARY_BUTTON,
} from "../../../components/shared/list/list-ui.constants";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { TimeRangeSelect } from "../../../components/shared/list/TimeRangeSelect";
import { useListUrlState } from "../../../components/shared/list/useListUrlState";
import { RowActionMenu, type RowAction } from "../../../components/shared/RowActionMenu";
import { routePaths } from "../../../routes/path";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { ReturnDetailModal } from "../components/return-detail/ReturnDetailModal";
import { ReturnStatusBadge } from "../components/ReturnStatusBadge";
import {
  cancelOrderReturn,
  getOrderReturnList,
  getOrderReturnStatusCounts,
  receiveOrderReturn,
} from "../services/order-return.api";
import type { OrderPageResponse } from "../types/order.types";
import type { OrderReturnListRow, ReturnFilterKey } from "../types/order-return.types";
import { formatDateTime } from "../utils/order-formatters";
import {
  formatReturnQuantity,
  getReturnCode,
  getReturnErrorMessage,
  toReturnNumber,
} from "../utils/order-return.utils";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = ReturnFilterKey;

interface ConfirmAction {
  type: "receive" | "cancel";
  returnItem: OrderReturnListRow;
}

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

function getReturnItemCount(returnItem: OrderReturnListRow) {
  if (returnItem.itemCount !== null && returnItem.itemCount !== undefined) {
    return toReturnNumber(returnItem.itemCount);
  }

  return (returnItem.items ?? []).reduce((total, item) => total + toReturnNumber(item.quantity), 0);
}

export function OrderReturnPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const listState = useListUrlState();
  const debouncedSearchTerm = listState.keyword;
  const statusFilter = (
    RETURN_STATUS_FILTERS.some((filter) => filter.key === listState.status) ? listState.status : "ALL"
  ) as ReturnStatusFilter;
  const currentPage = listState.page;
  const timeRange = listState.timeRange;
  const [openActionId, setOpenActionId] = useState<string | null>(null);
  const [returnPage, setReturnPage] = useState(emptyReturnPage);
  const [isRowsLoading, setIsRowsLoading] = useState(false);
  const [rowsError, setRowsError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [statusCounts, setStatusCounts] =
    useState<Record<ReturnStatusFilter, number> | null>(null);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [confirmError, setConfirmError] = useState("");
  const [isTransitioning, setIsTransitioning] = useState(false);
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
        keyword: debouncedSearchTerm,
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
      keyword: debouncedSearchTerm,
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

  const goToRefund = (row: OrderReturnListRow) => {
    const params = new URLSearchParams({ returnId: row.id, keyword: getReturnCode(row) });
    navigate(`${routePaths.refunds}?${params.toString()}`);
  };

  const getRowActions = (row: OrderReturnListRow): RowAction[] => {
    const actions: RowAction[] = [{ label: "Xem chi tiết", onSelect: () => setDetailParam(row.id) }];
    if (row.status === "CREATED") {
      actions.push(
        { label: "Xác nhận nhận hàng", onSelect: () => setConfirmAction({ type: "receive", returnItem: row }) },
        { label: "Hủy phiếu", onSelect: () => setConfirmAction({ type: "cancel", returnItem: row }) },
      );
    }
    if (row.displayStatus === "PENDING_REFUND") {
      actions.push({ label: "Đi tới hoàn tiền", onSelect: () => goToRefund(row) });
    }
    return actions;
  };

  const executeTransition = () => {
    if (!confirmAction) {
      return;
    }

    const { type, returnItem } = confirmAction;
    const action = type === "receive" ? receiveOrderReturn : cancelOrderReturn;

    setIsTransitioning(true);
    setConfirmError("");
    action(returnItem.id)
      .then(() => {
        setToast(type === "receive" ? "Đã xác nhận nhận hàng." : "Đã hủy phiếu hoàn.");
        setConfirmAction(null);
        setReloadKey((key) => key + 1);
      })
      .catch((error: unknown) => {
        setConfirmError(getReturnErrorMessage(error));
      })
      .finally(() => {
        setIsTransitioning(false);
      });
  };

  return (
    <section className="flex h-full min-h-0 flex-col gap-4 text-[#172317]">
      <ListPageHeader
        title="Hoàn hàng"
        description="Quản lý toàn bộ quy trình hoàn hàng từ tiếp nhận, kiểm hàng đến hoàn tiền"
        actions={
          <>
            <button
              className={LIST_SECONDARY_BUTTON}
              disabled={isRowsLoading}
              onClick={() => setReloadKey((key) => key + 1)}
              type="button"
            >
              <RefreshCw size={17} aria-hidden="true" />
              Làm mới
            </button>
            <button
              className={LIST_PRIMARY_BUTTON}
              onClick={() => navigate(routePaths.returnCreate)}
              type="button"
            >
              <Plus size={17} aria-hidden="true" />
              Tạo phiếu hoàn hàng
            </button>
          </>
        }
      />

      <ListFilterToolbar
        activeChip={statusFilter}
        chips={RETURN_STATUS_FILTERS.map((filter) => ({
          key: filter.key,
          label: filter.label,
          count: statusCounts ? (statusCounts[filter.key] ?? 0) : null,
          tone: filter.isError ? "danger" : "default",
        }))}
        onChipChange={listState.setStatus}
        search={{
          value: listState.searchDraft,
          placeholder: "Tìm mã phiếu, mã đơn, khách hàng...",
          onChange: listState.setSearchDraft,
        }}
      >
        <TimeRangeSelect onChange={listState.setTime} value={listState.time} />
      </ListFilterToolbar>

      <DataTable
        caption="Danh sách phiếu hoàn hàng"
        rows={visibleRows}
        columns={[
          {
            key: "code",
            header: "Mã phiếu hoàn",
            className: LIST_CODE_TEXT,
            render: (row) => getReturnCode(row),
          },
          {
            key: "customer",
            header: "Khách hàng",
            render: (row) => (
              <div className="grid">
                <span className="font-bold">{row.customerName?.trim() || "Khách lẻ"}</span>
                {row.customerPhone ? <span className={LIST_MUTED_TEXT}>{row.customerPhone}</span> : null}
              </div>
            ),
          },
          {
            key: "order",
            header: "Mã đơn hàng",
            className: LIST_CODE_TEXT,
            render: (row) => row.orderCode || "--",
          },
          {
            key: "items",
            header: "Sản phẩm",
            render: (row) => <ReturnItemsSummary row={row} />,
          },
          {
            key: "status",
            header: "Trạng thái",
            render: (row) => <ReturnStatusBadge displayStatus={row.displayStatus} status={row.status} />,
          },
          {
            key: "createdAt",
            header: "Ngày tạo",
            render: (row) => formatDateTime(row.createdAt),
          },
        ]}
        emptyDescription={
          listState.hasFilter
            ? "Thử đổi từ khóa, trạng thái hoặc khoảng thời gian."
            : "Tạo phiếu hoàn mới khi khách trả hàng."
        }
        emptyTitle="Chưa có phiếu hoàn phù hợp"
        error={rowsError}
        getRowKey={(row) => row.id}
        loading={isRowsLoading}
        onRetry={() => setReloadKey((key) => key + 1)}
        onRowClick={(row) => setDetailParam(row.id)}
        pagination={
          <TablePagination
            disabled={isRowsLoading}
            itemLabel="phiếu hoàn"
            onPageChange={listState.setPage}
            page={currentPage}
            pageSize={returnPage.size || PAGE_SIZE}
            totalItems={totalElements}
            totalPages={totalPages}
          />
        }
        rowActions={(row) => (
          <RowActionMenu
            actions={getRowActions(row)}
            isOpen={openActionId === row.id}
            menuLabel={`Thao tác phiếu ${getReturnCode(row)}`}
            onOpenChange={(open) =>
              setOpenActionId((current) => (open ? row.id : current === row.id ? null : current))
            }
            triggerLabel={`Mở thao tác cho phiếu ${getReturnCode(row)}`}
          />
        )}
      />

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

      {confirmAction ? (
        <ConfirmDialog
          confirmLabel={confirmAction.type === "receive" ? "Xác nhận nhận hàng" : "Hủy phiếu"}
          confirmVariant={confirmAction.type === "receive" ? "primary" : "danger"}
          description={
            confirmAction.type === "receive"
              ? `Xác nhận cửa hàng đã nhận hàng trả của phiếu ${getReturnCode(confirmAction.returnItem)}? Sau khi nhận hàng, phiếu không thể hủy.`
              : `Bạn có chắc muốn hủy phiếu hoàn ${getReturnCode(confirmAction.returnItem)}?`
          }
          error={confirmError}
          onCancel={() => {
            if (isTransitioning) return;
            setConfirmAction(null);
            setConfirmError("");
          }}
          onConfirm={executeTransition}
          submitting={isTransitioning}
          title={confirmAction.type === "receive" ? "Xác nhận nhận hàng" : "Hủy phiếu hoàn"}
        />
      ) : null}
      {toast ? (
        <div className={styles.toast} role="status">
          {toast}
        </div>
      ) : null}
    </section>
  );
}

function ReturnItemsSummary({ row }: { row: OrderReturnListRow }) {
  const items = row.items ?? [];
  const quantity = formatReturnQuantity(getReturnItemCount(row));
  if (items.length === 0) {
    return <>{quantity} sản phẩm</>;
  }
  const firstName = items[0]?.productNameSnapshot?.trim() || "Sản phẩm";
  return (
    <div className="grid max-w-[260px]">
      <span className="truncate font-bold" title={firstName}>
        {firstName}
        {items.length > 1 ? <span className={LIST_MUTED_TEXT}> +{items.length - 1}</span> : null}
      </span>
      <span className={LIST_MUTED_TEXT}>SL hoàn: {quantity}</span>
    </div>
  );
}
