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

import { ModalShell } from "../../../components/ui/Overlay";
import { Button } from "../../../components/ui/Button";
import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
  buildTodayTimeNode,
  getDateRangeFromTimeNode,
  type TimeNode,
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { ExportDropdown } from "../components/ExportDropdown";
import { InvoiceDetailPanel } from "../components/InvoiceDetailPanel";
import { OrderImportDialog } from "../components/OrderImportDialog";
import { PaymentStatusBadge } from "../components/PaymentStatusBadge";
import { exportInvoices, getOrderById, getOrders, getOrderStats } from "../services/order.api";
import type {
  OrderDetailResponse,
  OrderPageResponse,
  OrderStatsResponse,
  OrderSummaryResponse,
} from "../types/order.types";
import {
  buildInvoiceCode,
  formatCurrency,
  formatDateOnly,
  getCustomerName,
  getPaymentStatusText,
} from "../utils/order-formatters";
import { buildActiveOrderExportParams } from "../utils/order-export-filters";
import styles from "./InvoiceListPage.module.css";

type InvoicePaymentTabKey = "ALL" | "UNPAID" | "PAID";

const PAGE_SIZE = 10;

const emptyPage: OrderPageResponse<OrderSummaryResponse> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: PAGE_SIZE,
};

const INVOICE_PAYMENT_TABS: {
  key: InvoicePaymentTabKey;
  label: string;
  paymentStatus: string | null;
  countKey: keyof OrderStatsResponse;
}[] = [
  { key: "ALL", label: "Tất cả", paymentStatus: null, countKey: "total" },
  {
    key: "UNPAID",
    label: "Chưa thanh toán",
    paymentStatus: "UNPAID",
    countKey: "unpaidInvoices",
  },
  {
    key: "PAID",
    label: "Đã thanh toán",
    paymentStatus: "PAID",
    countKey: "paidInvoices",
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

export function InvoiceListPage() {
  const [isImportOpen, setIsImportOpen] = useState(false);
  /** Tăng sau khi nhập file để tải lại danh sách và thống kê. */
  const [reloadKey, setReloadKey] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(buildTodayTimeNode);
  const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
  const [filterPaymentStatus, setFilterPaymentStatus] = useState<InvoicePaymentTabKey>("ALL");
  const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [selectedSummary, setSelectedSummary] = useState<OrderSummaryResponse | null>(null);
  const [selectedRowIndex, setSelectedRowIndex] = useState(0);
  const [selectedDetail, setSelectedDetail] = useState<OrderDetailResponse | null>(null);
  const [invoicePage, setInvoicePage] = useState(emptyPage);
  const [stats, setStats] = useState<OrderStatsResponse>({});
  const [isListLoading, setIsListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const activePaymentTab = INVOICE_PAYMENT_TABS.find((tab) => tab.key === filterPaymentStatus);
  const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

  useEffect(() => {
    let ignore = false;
    const timerId = window.setTimeout(() => {
      setIsListLoading(true);
      setListError(null);

      getOrders({
        fromDate: timeRange?.fromDate,
        keyword: debouncedKeyword,
        paymentStatus: activePaymentTab?.paymentStatus ?? undefined,
        toDate: timeRange?.toDate,
        sort: "invoicePayment",
        page: currentPage,
        size: PAGE_SIZE,
      })
        .then((page) => {
          if (!ignore) {
            setInvoicePage({
              ...emptyPage,
              ...page,
              content: page.content ?? [],
            });
          }
        })
        .catch((error: unknown) => {
          if (!ignore) {
            setInvoicePage(emptyPage);
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
    activePaymentTab?.paymentStatus,
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
    if (!selectedOrderId) {
      return;
    }

    let ignore = false;
    const timerId = window.setTimeout(() => {
      setIsDetailLoading(true);
      setDetailError(null);

      getOrderById(selectedOrderId)
        .then((order) => {
          if (!ignore) {
            setSelectedDetail(order);
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
  }, [selectedOrderId]);

  const visibleInvoices = invoicePage.content ?? [];
  const totalPages = Math.max(invoicePage.totalPages ?? 0, 1);
  const totalElements = invoicePage.totalElements ?? 0;


  const buildCurrentFilterExportParams = () =>
    buildActiveOrderExportParams({
      fromDate: timeRange?.fromDate,
      keyword: debouncedKeyword,
      paymentStatus: activePaymentTab?.paymentStatus,
      toDate: timeRange?.toDate,
    });

  const selectPaymentFilter = (status: InvoicePaymentTabKey) => {
    setFilterPaymentStatus(status);
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

  const selectInvoice = (order: OrderSummaryResponse, rowIndex: number) => {
    setSelectedSummary(order);
    setSelectedOrderId(order.id);
    setSelectedRowIndex(rowIndex);
  };

  const closeInvoiceDetail = () => {
    setSelectedOrderId(null);
    setSelectedSummary(null);
    setSelectedDetail(null);
    setDetailError(null);
    setIsDetailLoading(false);
  };

  const isInvoiceDetailOpen = Boolean(
    selectedOrderId || selectedSummary || selectedDetail || isDetailLoading || detailError,
  );

  return (
    <OrderPageLayout
      title="Hóa đơn"
      description="Quản lý và theo dõi tất cả hóa đơn bán trà, phụ kiện và đơn giao hàng trong hệ thống."
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
          <Button onClick={() => setIsImportOpen(true)} size="lg">
            <Upload size={18} aria-hidden="true" />
            Nhập file
          </Button>
          <ExportDropdown
            allDescription="Bỏ qua các bộ lọc hiện tại"
            allFilenamePrefix="tat-ca-hoa-don"
            allLabel="Xuất toàn bộ hóa đơn"
            emptyFilteredMessage="Không có hóa đơn phù hợp với bộ lọc để xuất file."
            fallbackErrorMessage="Không thể xuất file hóa đơn. Vui lòng thử lại."
            filteredDescription="Áp dụng thời gian, thanh toán và từ khóa đang chọn"
            filteredFilenamePrefix="hoa-don-theo-bo-loc"
            hasNoFilteredData={!isListLoading && !listError && totalElements === 0}
            onExport={(mode) =>
              exportInvoices(mode === "filtered" ? buildCurrentFilterExportParams() : {})
            }
            successMessage="Đã xuất file hóa đơn thành công."
          />
        </>
      }
    >
      <OrderListWorkspace>
        <OrderToolbar
          search={{
            "aria-label": "Tìm kiếm hóa đơn",
            onChange: (value) => {
              setSearchTerm(value);
              setCurrentPage(0);
            },
            onKeyDown: (event) => {
              if (event.key === "Escape") {
                setSearchTerm("");
                setCurrentPage(0);
              }
            },
            placeholder: "Tìm mã HĐ, mã ĐH, khách hàng, SĐT...",
            value: searchTerm,
          }}
        >
          <OrderStatusFilters
            label="Thanh toán:"
            options={INVOICE_PAYMENT_TABS.map((filter) => ({
              ...filter,
              count: stats[filter.countKey] ?? 0,
            }))}
            value={filterPaymentStatus}
            onChange={selectPaymentFilter}
          />
        </OrderToolbar>
        <OrderDataTable
          rows={visibleInvoices}
          getRowKey={(order) => order.id}
          onRowClick={(order, index) =>
            selectInvoice(order, invoicePage.number * invoicePage.size + index)
          }
          isRowActive={(order) => selectedOrderId === order.id}
          loading={isListLoading}
          error={listError}
          loadingTitle="Đang tải hóa đơn"
          loadingDescription="Dữ liệu đang được đồng bộ từ Order-Service"
          errorTitle="Không tải được danh sách hóa đơn"
          emptyTitle={"Không tìm thấy hóa đơn phù hợp"}
          emptyDescription="Thử đổi từ khóa hoặc bộ lọc"
          caption="Hóa đơn"
          minWidth={840}
          columns={[
            {
              key: "column-0",
              header: "Mã hóa đơn",
              width: "24%",
              align: "left",
              render: (order, index) => {
                const absoluteIndex = invoicePage.number * invoicePage.size + index;
                const invoiceCode = buildInvoiceCode(order, absoluteIndex);
                return (
                  <span className={styles.invoiceLink} title={invoiceCode}>
                    {invoiceCode}
                  </span>
                );
              },
            },
            {
              key: "column-1",
              header: "Thanh toán",
              width: "24%",
              align: "center",
              render: (order) => (
                <>
                  <PaymentStatusBadge
                    status={order.paymentStatus}
                    label={getPaymentStatusText(order)}
                  />
                </>
              ),
            },
            {
              key: "column-2",
              header: "Tổng tiền",
              width: "20%",
              align: "left",
              render: (order) => (
                <>
                  <strong>{formatCurrency(order.finalAmount)}</strong>
                </>
              ),
            },
            {
              key: "column-3",
              header: "Khách hàng",
              width: "18%",
              align: "left",
              render: (order) => (
                <>
                  <strong>{getCustomerName(order)}</strong>
                </>
              ),
            },
            {
              key: "column-4",
              header: "Ngày",
              width: "14%",
              align: "left",
              render: (order) => <>{formatDateOnly(order.createdAt)}</>,
            },
          ]}
          pagination={
            <OrderPagination
              page={currentPage}
              recordPage={invoicePage.number}
              pageSize={invoicePage.size}
              totalPages={totalPages}
              totalItems={totalElements}
              itemLabel="dòng"
              onPageChange={setCurrentPage}
            />
          }
        />
      </OrderListWorkspace>
      {isInvoiceDetailOpen ? (
        <ModalShell className={styles.detailModal} onClose={closeInvoiceDetail}>
            <InvoiceDetailPanel
              order={selectedDetail}
              summary={selectedSummary}
              rowIndex={selectedRowIndex}
              loading={isDetailLoading}
              error={detailError}
              onClose={closeInvoiceDetail}
            />
        </ModalShell>
      ) : null}
      {isImportOpen ? (
        <OrderImportDialog
          onClose={() => setIsImportOpen(false)}
          onImported={() => setReloadKey((key) => key + 1)}
          title="Nhập hóa đơn từ file"
        />
      ) : null}
    </OrderPageLayout>
  );
}
