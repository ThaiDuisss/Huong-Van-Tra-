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
  RefreshCw,
  WalletCards,
  X,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
  buildTodayTimeNode,
  getDateRangeFromTimeNode,
  type TimeNode,
} from "../../../components/shared/TimeHierarchyFilter.utils";
import {
  getOrderRefundDetail,
  getOrderRefundList,
  getOrderRefundPreview,
  getOrderRefundStats,
  refundOrderReturn,
} from "../services/order-return.api";
import type { OrderPageResponse } from "../types/order.types";
import type {
  OrderRefundDetailResponse,
  OrderRefundStatsResponse,
  RefundProgressStatus,
  OrderRefundSummaryResponse,
  OrderReturnRefundPreviewResponse,
  OrderReturnRefundResponse,
} from "../types/order-return.types";
import {
  formatCurrency,
  formatDateTime,
  formatQuantity,
  getPaymentStatusLabel,
} from "../utils/order-formatters";
import {
  getBlockReasonLabel,
  getReturnConditionLabel,
  getReturnReasonLabel,
  getReturnStatusLabel,
} from "../utils/order-return.utils";
import styles from "./OrderRefundPage.module.css";

type RefundFilterKey = "ALL" | Exclude<RefundProgressStatus, "OTHER">;
type ToastState = { message: string; type: "success" | "error" };

const PAGE_SIZE = 10;

const emptyPage: OrderPageResponse<OrderRefundSummaryResponse> = {
  content: [],
  number: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
};

const FILTERS: {
  key: RefundFilterKey;
  label: string;
  progressStatus?: RefundProgressStatus;
  countKey: keyof OrderRefundStatsResponse;
  isError?: boolean;
}[] = [
  { key: "ALL", label: "Tất cả", countKey: "total" },
  {
    key: "WAITING_RECEIPT",
    label: "Chờ nhận hàng",
    progressStatus: "WAITING_RECEIPT",
    countKey: "waitingReceipt",
  },
  {
    key: "WAITING_INSPECTION",
    label: "Chờ kiểm hàng",
    progressStatus: "WAITING_INSPECTION",
    countKey: "waitingInspection",
  },
  {
    key: "WAITING_REFUND",
    label: "Chờ hoàn tiền",
    progressStatus: "WAITING_REFUND",
    countKey: "pendingRefund",
  },
  { key: "REFUNDED", label: "Đã hoàn tiền", progressStatus: "REFUNDED", countKey: "refunded" },
  { key: "FAILED", label: "Có lỗi", progressStatus: "FAILED", countKey: "failed", isError: true },
];

const QC_STATUS_LABELS: Record<string, string> = {
  COMPLETED: "Hoàn tất",
  PARTIAL: "Đang kiểm",
  PENDING: "Chưa kiểm",
  UNAVAILABLE: "Không xác định",
  UNKNOWN: "Không xác định",
};

const METHOD_LABELS: Record<string, string> = {
  BANK_TRANSFER: "Chuyển khoản",
  CASH: "Tiền mặt",
  UNAVAILABLE: "Không xác định",
  UNKNOWN: "Không xác định",
};

const ERROR_MESSAGES: Record<string, string> = {
  "Payment not found": "Không tìm thấy giao dịch thanh toán.",
  "Refund amount exceeds remaining refundable amount":
    "Số tiền hoàn vượt quá số tiền còn có thể hoàn.",
  "Refund method is not supported yet": "Phương thức thanh toán hiện tại chưa hỗ trợ hoàn tiền.",
  "Return already refunded": "Phiếu này đã được hoàn tiền.",
  "Return inventory inspection is not completed":
    "Cần hoàn tất kiểm tra hàng trả trước khi hoàn tiền.",
  "Return is not eligible for refund": "Phiếu hoàn chưa đủ điều kiện hoàn tiền.",
  "Refund is not required for this return": "Phiếu hoàn này không phát sinh hoàn tiền.",
  "Payment service is temporarily unavailable": "Dịch vụ thanh toán tạm thời không khả dụng.",
  "Payment is not eligible for refund": "Thanh toán không đủ điều kiện hoàn tiền.",
  "Refund amount must be greater than 0": "Số tiền hoàn phải lớn hơn 0.",
};

function getErrorMessage(error: unknown) {
  if (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    typeof error.status === "number"
  ) {
    if (error.status === 401) {
      return "Phiên đăng nhập đã hết hạn hoặc không hợp lệ.";
    }

    if (error.status === 403) {
      return "Bạn không có quyền truy cập.";
    }

    if (error.status === 500) {
      return "Máy chủ gặp lỗi khi tải danh sách hoàn tiền.";
    }

    if (error.status === 502) {
      return "Dịch vụ phụ thuộc gặp lỗi khi tải danh sách hoàn tiền.";
    }

    if (error.status === 503) {
      return "Dịch vụ tạm thời không khả dụng.";
    }

    if (error.status === 0) {
      return "Không thể kết nối đến máy chủ.";
    }
  }

  if (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    error.code === "NETWORK_ERROR"
  ) {
    return "Không thể kết nối đến máy chủ.";
  }

  if (
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof error.message === "string"
  ) {
    return ERROR_MESSAGES[error.message] ?? error.message;
  }

  return "Không thể tải dữ liệu. Vui lòng thử lại.";
}

function useDebouncedValue<T>(value: T, delayMs: number) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
    return () => window.clearTimeout(timerId);
  }, [delayMs, value]);

  return debouncedValue;
}

function displayText(value?: string | null, fallback = "--") {
  return value?.trim() || fallback;
}

function getQcStatusLabel(status?: string | null) {
  return status ? (QC_STATUS_LABELS[status] ?? status) : "--";
}

function getMethodLabel(method?: string | null) {
  return method ? (METHOD_LABELS[method] ?? method) : "--";
}

export type RefundDisplayStatusKey =
  | "REFUNDED"
  | "FAILED"
  | "NOT_REQUIRED"
  | "WAITING_REFUND"
  | "INVENTORY_FAILED"
  | "UNSUPPORTED_METHOD"
  | "NO_PAYMENT"
  | "PAYMENT_UNAVAILABLE"
  | "CANCELLED"
  | "WAITING_RECEIPT"
  | "WAITING_INSPECTION"
  | "PROCESSING";

export interface RefundProgressInfo {
  key: RefundDisplayStatusKey;
  label: string;
  className: string;
}

function getRefundProgress(row: OrderRefundSummaryResponse): RefundProgressInfo {
  // 1. Refunded
  if (row.refundStatus === "REFUNDED" || row.refundBlockReason === "ALREADY_REFUNDED") {
    return { key: "REFUNDED", label: "Đã hoàn tiền", className: styles.refundedBadge };
  }

  // 2. Refund Failed
  if (row.refundStatus === "FAILED" || row.refundBlockReason === "REFUND_FAILED") {
    return { key: "FAILED", label: "Có lỗi", className: styles.failedBadge };
  }

  if (row.inventoryProcessingStatus === "FAILED" || row.displayStatus === "PROCESSING_FAILED") {
    return { key: "INVENTORY_FAILED", label: "Xử lý kho thất bại", className: styles.failedBadge };
  }

  // 3. Not Required
  if (
    row.refundStatus === "NOT_REQUIRED" ||
    row.refundRequired === false ||
    row.refundBlockReason === "REFUND_NOT_REQUIRED"
  ) {
    return { key: "NOT_REQUIRED", label: "Không hoàn tiền", className: styles.notRequiredBadge };
  }
  if (row.returnStatus === "CANCELLED") {
    return { key: "CANCELLED", label: "Đã hủy", className: styles.neutralBadge };
  }

  // 4. Waiting Refund (Eligible)
  if (row.refundStatus === "PENDING" || row.refundEligible === true) {
    return { key: "WAITING_REFUND", label: "Chờ hoàn tiền", className: styles.pendingRefundBadge };
  }

  // 5. Waiting Receipt / Inspection (Pre-refund physical progress)
  if (row.returnStatus === "CREATED" || row.refundBlockReason === "RETURN_NOT_RECEIVED") {
    return {
      key: "WAITING_RECEIPT",
      label: "Chờ nhận hàng",
      className: styles.waitingReceiptBadge,
    };
  }
  if (
    row.returnStatus === "RECEIVED" &&
    (row.refundBlockReason == null ||
      row.refundBlockReason === "PHYSICAL_DISPOSITION_NOT_COMPLETED") &&
    (row.physicalDone === false ||
      row.refundBlockReason === "PHYSICAL_DISPOSITION_NOT_COMPLETED" ||
      row.inventoryProcessingStatus === "PENDING" ||
      row.inventoryProcessingStatus === "PROCESSING")
  ) {
    return { key: "WAITING_INSPECTION", label: "Chờ kiểm hàng", className: styles.waitingQcBadge };
  }

  // 6. Payment block reasons (Pre-refund payment conditions)
  if (row.refundBlockReason === "UNSUPPORTED_PAYMENT_METHOD") {
    return {
      key: "UNSUPPORTED_METHOD",
      label: "Phương thức chưa hỗ trợ",
      className: styles.neutralBadge,
    };
  }
  if (row.refundBlockReason === "PAYMENT_NOT_FOUND") {
    return { key: "NO_PAYMENT", label: "Chưa có thanh toán", className: styles.neutralBadge };
  }
  if (row.refundBlockReason === "PAYMENT_SERVICE_UNAVAILABLE") {
    return {
      key: "PAYMENT_UNAVAILABLE",
      label: "Dịch vụ thanh toán bận",
      className: styles.neutralBadge,
    };
  }
  if (row.refundBlockReason === "PAYMENT_NOT_REFUNDABLE") {
    return { key: "FAILED", label: "Không thể hoàn tiền", className: styles.failedBadge };
  }
  if (row.refundBlockReason === "REFUND_AMOUNT_EXCEEDS_REMAINING") {
    return { key: "FAILED", label: "Vượt tiền có thể hoàn", className: styles.failedBadge };
  }
  if (row.refundBlockReason === "NO_REFUND_AMOUNT") {
    return {
      key: "NOT_REQUIRED",
      label: "Số tiền hoàn bằng 0",
      className: styles.notRequiredBadge,
    };
  }

  // 7. In processing
  if (row.inventoryProcessingStatus === "PROCESSING" || row.displayStatus === "PROCESSING") {
    return { key: "PROCESSING", label: "Chờ xử lý", className: styles.waitingReceiptBadge };
  }

  return { key: "PROCESSING", label: "Chờ xử lý", className: styles.waitingReceiptBadge };
}

function canRefund(row: OrderRefundSummaryResponse | OrderRefundDetailResponse) {
  return row.refundEligible === true;
}

export function OrderRefundPage() {
  // Hỗ trợ mở thẳng phiếu từ màn Hoàn hàng: ?returnId=<id>&keyword=<mã phiếu hoàn>
  const [searchParams] = useSearchParams();
  const linkedReturnId = searchParams.get("returnId");
  const linkedKeyword = searchParams.get("keyword") ?? "";
  const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(() =>
    linkedReturnId ? null : buildTodayTimeNode(),
  );
  const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
  const [searchTerm, setSearchTerm] = useState(linkedKeyword);
  const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
  const [activeFilter, setActiveFilter] = useState<RefundFilterKey>("ALL");
  const [currentPage, setCurrentPage] = useState(0);
  const [refundPage, setRefundPage] = useState(emptyPage);
  const [stats, setStats] = useState<OrderRefundStatsResponse>({});
  const [isListLoading, setIsListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedReturnId, setSelectedReturnId] = useState<string | null>(linkedReturnId);
  const [selectedDetail, setSelectedDetail] = useState<OrderRefundDetailResponse | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [confirmRow, setConfirmRow] = useState<OrderRefundSummaryResponse | null>(null);
  const [preview, setPreview] = useState<OrderReturnRefundPreviewResponse | null>(null);
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [isRefunding, setIsRefunding] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const refundInFlightRef = useRef(false);

  const timeRange = getDateRangeFromTimeNode(selectedTimeNode);
  const activeProgressStatus = activeFilter === "ALL" ? undefined : activeFilter;
  const visibleRefunds = refundPage.content ?? [];
  const totalElements = refundPage.totalElements ?? 0;
  const totalPages = Math.max(refundPage.totalPages ?? 0, 1);

  const refreshKey = useMemo(
    () =>
      `${timeRange?.fromDate ?? ""}:${timeRange?.toDate ?? ""}:${debouncedKeyword}:${activeProgressStatus ?? ""}:${currentPage}`,
    [activeProgressStatus, currentPage, debouncedKeyword, timeRange?.fromDate, timeRange?.toDate],
  );

  useEffect(() => {
    let ignore = false;
    const timerId = window.setTimeout(() => {
      setIsListLoading(true);
      setListError(null);

      getOrderRefundList({
        fromDate: timeRange?.fromDate,
        keyword: debouncedKeyword,
        page: currentPage,
        size: PAGE_SIZE,
        sort: "createdAt,desc",
        progressStatus: activeProgressStatus,
        toDate: timeRange?.toDate,
      })
        .then((page) => {
          if (!ignore) {
            setRefundPage({ ...emptyPage, ...page, content: page.content ?? [] });
            if (page.totalPages > 0 && currentPage >= page.totalPages) {
              setCurrentPage(Math.max(0, page.totalPages - 1));
            }
          }
        })
        .catch((error: unknown) => {
          if (!ignore) {
            setRefundPage(emptyPage);
            setListError(getErrorMessage(error));
          }
        })
        .finally(() => {
          if (!ignore) {
            setIsListLoading(false);
          }
        });
    }, 0);

    return () => {
      ignore = true;
      window.clearTimeout(timerId);
    };
  }, [
    activeProgressStatus,
    currentPage,
    debouncedKeyword,
    refreshKey,
    timeRange?.fromDate,
    timeRange?.toDate,
  ]);

  useEffect(() => {
    let ignore = false;

    getOrderRefundStats({
      fromDate: timeRange?.fromDate,
      keyword: debouncedKeyword,
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
  }, [debouncedKeyword, timeRange?.fromDate, timeRange?.toDate]);

  useEffect(() => {
    if (!selectedReturnId) {
      return;
    }

    let ignore = false;
    const timerId = window.setTimeout(() => {
      setIsDetailLoading(true);
      setDetailError(null);

      getOrderRefundDetail(selectedReturnId)
        .then((detail) => {
          if (!ignore) {
            setSelectedDetail(detail);
          }
        })
        .catch((error: unknown) => {
          if (!ignore) {
            setSelectedDetail(null);
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
  }, [selectedReturnId]);

  useEffect(() => {
    if (!toast) {
      return;
    }

    const timerId = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(timerId);
  }, [toast]);

  const reloadList = () => {
    setCurrentPage((page) => page);
    getOrderRefundList({
      fromDate: timeRange?.fromDate,
      keyword: debouncedKeyword,
      page: currentPage,
      size: PAGE_SIZE,
      sort: "createdAt,desc",
      progressStatus: activeProgressStatus,
      toDate: timeRange?.toDate,
    }).then((page) => {
      setRefundPage({ ...emptyPage, ...page, content: page.content ?? [] });
      if (page.totalPages > 0 && currentPage >= page.totalPages) {
        setCurrentPage(Math.max(0, page.totalPages - 1));
      }
    });
    getOrderRefundStats({
      fromDate: timeRange?.fromDate,
      keyword: debouncedKeyword,
      toDate: timeRange?.toDate,
    }).then((response) => setStats(response ?? {}));
  };

  const openDetail = (row: OrderRefundSummaryResponse) => {
    setSelectedReturnId(row.returnId);
  };

  const closeDetail = () => {
    setSelectedReturnId(null);
    setSelectedDetail(null);
    setDetailError(null);
    setIsDetailLoading(false);
  };

  const openConfirm = (row: OrderRefundSummaryResponse) => {
    setConfirmRow(row);
    setPreview(null);
    setPreviewError(null);
    setIsPreviewLoading(true);

    getOrderRefundPreview(row.returnId)
      .then((response) => {
        setPreview(response);
        if (!response.eligible) {
          setPreviewError(
            response.blockReason && response.blockReason !== "NONE"
              ? getBlockReasonLabel(response.blockReason)
              : response.reason
                ? getErrorMessage({ message: response.reason })
                : "Phiếu hoàn chưa đủ điều kiện hoàn tiền.",
          );
        }
      })
      .catch((error: unknown) => {
        setPreviewError(getErrorMessage(error));
      })
      .finally(() => setIsPreviewLoading(false));
  };

  const closeConfirm = () => {
    if (isRefunding) {
      return;
    }
    setConfirmRow(null);
    setPreview(null);
    setPreviewError(null);
    setIsPreviewLoading(false);
  };

  const submitRefund = () => {
    if (refundInFlightRef.current || !confirmRow || !preview?.eligible) {
      return;
    }

    refundInFlightRef.current = true;
    setIsRefunding(true);
    refundOrderReturn(confirmRow.returnId)
      .then((response: OrderReturnRefundResponse) => {
        setToast({ message: "Hoàn tiền thành công.", type: "success" });
        setConfirmRow(null);
        setPreview(null);
        reloadList();
        if (selectedReturnId === confirmRow.returnId) {
          setSelectedDetail((current) =>
            current
              ? {
                  ...current,
                  method: response.method,
                  paymentStatus: response.paymentStatus,
                  refundAmount: response.refundAmount,
                  refundBlockReason: "ALREADY_REFUNDED",
                  refundEligible: false,
                  refundedAt: response.refundedAt,
                  refundStatus: "REFUNDED",
                  returnStatus: response.returnStatus ?? "COMPLETED",
                  totalAmountRefunded: response.totalAmountRefunded,
                }
              : current,
          );
          getOrderRefundDetail(confirmRow.returnId)
            .then((detail) => {
              setSelectedDetail((current) =>
                current?.returnId === detail.returnId ? detail : current,
              );
            })
            .catch(() => undefined);
        }
      })
      .catch((error: unknown) => {
        setToast({ message: getErrorMessage(error), type: "error" });
      })
      .finally(() => {
        refundInFlightRef.current = false;
        setIsRefunding(false);
      });
  };

  const selectTimeNode = (node: TimeNode) => {
    setSelectedTimeNode(node);
    setCurrentPage(0);
  };

  const clearTimeRange = () => {
    setSelectedTimeNode(null);
    setCurrentPage(0);
  };

  return (
    <OrderPageLayout
      title="Hoàn tiền"
      description="Quản lý và xử lý hoàn tiền cho các phiếu hoàn hàng"
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
    >
      <OrderListWorkspace>
        <OrderToolbar
          search={{
            "aria-label": "Tìm kiếm phiếu hoàn tiền",
            onChange: (event) => {
              setSearchTerm(event.target.value);
              setCurrentPage(0);
            },
            onKeyDown: (event) => {
              if (event.key === "Escape") {
                setSearchTerm("");
                setCurrentPage(0);
              }
            },
            placeholder: "Tìm mã đơn, phiếu hoàn, khách hàng...",
            value: searchTerm,
          }}
        >
          <OrderStatusFilters
            label="Trạng thái:"
            options={FILTERS.map((filter) => ({
              ...filter,
              count: (stats[filter.countKey] ?? 0).toLocaleString("vi-VN"),
            }))}
            value={activeFilter}
            onChange={(value) => {
              setActiveFilter(value);
              setCurrentPage(0);
            }}
          />
        </OrderToolbar>
        <OrderDataTable
          rows={visibleRefunds}
          getRowKey={(row) => row.returnId}
          onRowClick={openDetail}
          loading={isListLoading}
          error={listError}
          loadingTitle="Đang tải phiếu hoàn tiền"
          loadingDescription="Dữ liệu đang được tải từ Order-Service"
          errorTitle="Không tải được danh sách hoàn tiền"
          emptyTitle={
            activeFilter === "WAITING_REFUND"
              ? "Không có phiếu nào đang chờ hoàn tiền."
              : debouncedKeyword
                ? "Không tìm thấy phiếu hoàn tiền phù hợp."
                : "Không có phiếu hoàn tiền."
          }
          emptyDescription="Thử đổi bộ lọc thời gian, trạng thái hoặc từ khóa"
          caption="Hoàn tiền"
          minWidth={760}
          columns={[
            {
              key: "column-0",
              header: "Mã phiếu hoàn",
              width: "18%",
              align: "left",
              cellClassName: styles.code,
              render: (row) => <>{displayText(row.returnCode)}</>,
            },
            {
              key: "column-1",
              header: "Trạng thái",
              width: "17%",
              align: "center",
              render: (row) => (
                <>
                  <RefundStatusBadge row={row} />
                </>
              ),
            },
            {
              key: "column-2",
              header: "Số tiền hoàn",
              width: "16%",
              align: "left",
              render: (row) => (
                <>
                  {row.refundAmount === null || row.refundAmount === undefined ? (
                    <span className={styles.unknownAmount}>Chưa xác định</span>
                  ) : (
                    <span className={styles.refundAmount}>{formatCurrency(row.refundAmount)}</span>
                  )}
                </>
              ),
            },
            {
              key: "column-3",
              header: "Khách hàng",
              width: "20%",
              align: "left",
              cellClassName: styles.customerCell,
              render: (row) => (
                <>
                  <strong>{displayText(row.customerName, "Khách lẻ")}</strong>
                  {row.customerPhone?.trim() ? <span>{row.customerPhone.trim()}</span> : null}
                </>
              ),
            },
            {
              key: "column-4",
              header: "Mã đơn hàng",
              width: "16%",
              align: "left",
              cellClassName: styles.code,
              render: (row) => <>{displayText(row.orderCode)}</>,
            },
            {
              key: "column-5",
              header: "Ngày tạo",
              width: "13%",
              align: "left",
              render: (row) => <>{formatDateTime(row.returnCreatedAt)}</>,
            },
          ]}
          pagination={
            <OrderPagination
              page={currentPage}
              recordPage={refundPage.number}
              pageSize={refundPage.size || PAGE_SIZE}
              totalPages={totalPages}
              totalItems={totalElements}
              itemLabel="phiếu hoàn tiền"
              onPageChange={setCurrentPage}
              disabled={isListLoading}
            />
          }
        />
      </OrderListWorkspace>
      {selectedReturnId ? (
        <RefundDetailModal
          detail={selectedDetail}
          error={detailError}
          loading={isDetailLoading}
          onClose={closeDetail}
          onRefund={(row) => {
            closeDetail();
            openConfirm(row);
          }}
        />
      ) : null}
      {confirmRow ? (
        <RefundConfirmModal
          error={previewError}
          loading={isPreviewLoading}
          onCancel={closeConfirm}
          onConfirm={submitRefund}
          preview={preview}
          refunding={isRefunding}
          row={confirmRow}
        />
      ) : null}
      {toast ? (
        <div
          className={`${styles.toast} ${toast.type === "success" ? styles.toastSuccess : styles.toastError}`}
        >
          {toast.message}
        </div>
      ) : null}
    </OrderPageLayout>
  );
}

function RefundStatusBadge({ row }: { row: OrderRefundSummaryResponse }) {
  const progress = getRefundProgress(row);

  return <span className={`${styles.statusBadge} ${progress.className}`}>{progress.label}</span>;
}

function RefundDetailModal({
  detail,
  error,
  loading,
  onClose,
  onRefund,
}: {
  detail: OrderRefundDetailResponse | null;
  error: string | null;
  loading: boolean;
  onClose: () => void;
  onRefund: (row: OrderRefundDetailResponse) => void;
}) {
  return (
    <div className={styles.modalBackdrop} onClick={onClose} role="presentation">
      <aside
        aria-modal="true"
        className={styles.detailModal}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className={styles.modalHeader}>
          <div>
            <h2>Chi tiết hoàn tiền</h2>
            <p>{displayText(detail?.returnCode, "Đang tải phiếu hoàn")}</p>
          </div>
          <button className={styles.iconButton} onClick={onClose} type="button">
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        {loading ? <div className={styles.modalState}>Đang tải chi tiết...</div> : null}
        {!loading && error ? <div className={styles.modalState}>{error}</div> : null}
        {!loading && !error && detail ? (
          <div className={styles.detailContent}>
            <section>
              <h3>Thông tin phiếu hoàn</h3>
              <InfoGrid
                items={[
                  ["Mã phiếu hoàn", displayText(detail.returnCode)],
                  ["Mã đơn", displayText(detail.orderCode)],
                  ["Khách hàng", displayText(detail.customerName, "Khách lẻ")],
                  ["Số điện thoại", displayText(detail.customerPhone)],
                  ["Trạng thái phiếu hoàn", getReturnStatusLabel(detail.returnStatus)],
                  [
                    "Chính sách hoàn tiền",
                    detail.refundRequired === false ? "Không hoàn tiền" : "Có hoàn tiền",
                  ],
                  ["Kiểm hàng", getQcStatusLabel(detail.qcStatus)],
                  [
                    "Xử lý kho",
                    detail.physicalDone
                      ? "Đã xử lý"
                      : detail.inventoryProcessingStatus === "FAILED"
                        ? "Xử lý thất bại"
                        : detail.inventoryProcessingStatus === "PROCESSING"
                          ? "Đang xử lý"
                          : "Chờ kiểm hàng",
                  ],
                ]}
              />
            </section>

            <section>
              <h3>Thông tin hoàn tiền</h3>
              <InfoGrid
                items={[
                  [
                    "Số tiền hoàn",
                    detail.refundAmount ? formatCurrency(detail.refundAmount) : "Chưa xác định",
                  ],
                  [
                    "Tổng đã hoàn",
                    detail.totalAmountRefunded ? formatCurrency(detail.totalAmountRefunded) : "--",
                  ],
                  ["Phương thức", getMethodLabel(detail.method)],
                  ["Trạng thái thanh toán", getPaymentStatusLabel(detail.paymentStatus)],
                  ["Trạng thái hoàn tiền", getRefundProgress(detail).label],
                  ...(detail.refundBlockReason && detail.refundBlockReason !== "NONE"
                    ? [
                        ["Lý do chặn", getBlockReasonLabel(detail.refundBlockReason)] as [
                          string,
                          string,
                        ],
                      ]
                    : []),
                  ["Thời gian hoàn", formatDateTime(detail.refundedAt)],
                ]}
              />
            </section>

            <section>
              <h3>Sản phẩm hoàn</h3>
              <div className={styles.itemList}>
                {(detail.items ?? []).map((item) => (
                  <div className={styles.itemRow} key={item.id ?? item.orderItemId}>
                    <div>
                      <strong>{displayText(item.productNameSnapshot)}</strong>
                      <span>
                        {displayText(item.skuSnapshot)} · {displayText(item.variantNameSnapshot)}
                      </span>
                    </div>
                    <div>
                      <strong>{formatQuantity(item.quantity)}</strong>
                      <span>
                        {getReturnReasonLabel(item.reason)} · {getReturnConditionLabel(item.condition)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <div className={styles.detailActions}>
              <button className={orderUiStyles.secondaryButton} onClick={onClose} type="button">
                Đóng
              </button>
              {canRefund(detail) ? (
                <button
                  className={styles.refundButton}
                  onClick={() => onRefund(detail)}
                  type="button"
                >
                  <WalletCards size={16} aria-hidden="true" />
                  Thực hiện hoàn tiền
                </button>
              ) : null}
            </div>
          </div>
        ) : null}
      </aside>
    </div>
  );
}

function RefundConfirmModal({
  error,
  loading,
  onCancel,
  onConfirm,
  preview,
  refunding,
  row,
}: {
  error: string | null;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  preview: OrderReturnRefundPreviewResponse | null;
  refunding: boolean;
  row: OrderRefundSummaryResponse;
}) {
  const amount = preview?.refundAmount ?? row.refundAmount;
  const canSubmit = Boolean(preview?.eligible) && !loading && !refunding;

  return (
    <div className={styles.modalBackdrop} onClick={onCancel} role="presentation">
      <section
        aria-modal="true"
        className={styles.confirmModal}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className={styles.modalHeader}>
          <div>
            <h2>Xác nhận hoàn tiền?</h2>
            <p>Số tiền hoàn được hệ thống tính theo giá trị thực tế khách đã thanh toán.</p>
          </div>
        </div>

        <InfoGrid
          items={[
            ["Mã phiếu hoàn", displayText(row.returnCode)],
            ["Mã đơn", displayText(row.orderCode)],
            ["Khách hàng", displayText(row.customerName, "Khách lẻ")],
            [
              "Số tiền hoàn",
              loading ? "Đang tải..." : amount ? formatCurrency(amount) : "Chưa xác định",
            ],
            ["Phương thức hoàn", getMethodLabel(preview?.paymentMethod ?? row.method)],
          ]}
        />

        {error ? <div className={styles.errorBox}>{error}</div> : null}

        <div className={styles.modalFooter}>
          <button
            className={orderUiStyles.secondaryButton}
            disabled={refunding}
            onClick={onCancel}
            type="button"
          >
            Đóng
          </button>
          <button
            className={orderUiStyles.primaryButton}
            disabled={!canSubmit}
            onClick={onConfirm}
            type="button"
          >
            {refunding ? (
              <>
                <RefreshCw size={16} aria-hidden="true" />
                Đang hoàn tiền...
              </>
            ) : (
              "Xác nhận hoàn tiền"
            )}
          </button>
        </div>
      </section>
    </div>
  );
}

function InfoGrid({ items }: { items: [string, string][] }) {
  return (
    <dl className={styles.infoGrid}>
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
