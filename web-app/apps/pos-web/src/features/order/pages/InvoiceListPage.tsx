import {
    ChevronLeft,
    ChevronRight,
    Download,
    MoreHorizontal,
    Plus,
    Printer,
    Search
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { DateQuickFilter } from "../../../components/shared/DateQuickFilter";
import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import type { DateFilterKey } from "../../../components/shared/dateFilters";
import { InvoiceDetailPanel } from "../components/InvoiceDetailPanel";
import { PaymentStatusBadge } from "../components/PaymentStatusBadge";
import { getOrderById, getOrders } from "../services/order.api";
import type {
    OrderDetailResponse,
    OrderPageResponse,
    OrderSummaryResponse
} from "../types/order.types";
import {
    buildDateRangeFilter,
    buildInvoiceCode,
    formatCurrency,
    formatDateTime,
    getCustomerName,
    getOrderCode,
    getPaymentStatusText,
    getStaffName
} from "../utils/order-formatters";
import styles from "./InvoiceListPage.module.css";

type InvoicePaymentTabKey = "ALL" | "UNPAID" | "PAID";

const PAGE_SIZE = 10;

const emptyPage: OrderPageResponse<OrderSummaryResponse> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: PAGE_SIZE
};

const INVOICE_PAYMENT_TABS: {
    key: InvoicePaymentTabKey;
    label: string;
    paymentStatus: string | null;
}[] = [
    { key: "ALL", label: "Tất cả", paymentStatus: null },
    { key: "UNPAID", label: "Chưa thanh toán", paymentStatus: "UNPAID" },
    { key: "PAID", label: "Đã thanh toán", paymentStatus: "PAID" }
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

export function InvoiceListPage() {
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
    const [filterPaymentStatus, setFilterPaymentStatus] =
        useState<InvoicePaymentTabKey>("ALL");
    const [dateFilter, setDateFilter] = useState<DateFilterKey>("ALL");
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
    const [selectedSummary, setSelectedSummary] =
        useState<OrderSummaryResponse | null>(null);
    const [selectedRowIndex, setSelectedRowIndex] = useState(0);
    const [selectedDetail, setSelectedDetail] =
        useState<OrderDetailResponse | null>(null);
    const [selectedInvoiceIds, setSelectedInvoiceIds] = useState<string[]>([]);
    const [openActionCode, setOpenActionCode] = useState<string | null>(null);
    const [invoicePage, setInvoicePage] = useState(emptyPage);
    const [isListLoading, setIsListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);

    const dateRange = useMemo(
        () => buildDateRangeFilter(dateFilter),
        [dateFilter]
    );
    const activePaymentTab = INVOICE_PAYMENT_TABS.find(
        (tab) => tab.key === filterPaymentStatus
    );

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsListLoading(true);
            setListError(null);

            getOrders({
                keyword: debouncedKeyword,
                paymentStatus: activePaymentTab?.paymentStatus ?? undefined,
                ...dateRange,
                page: currentPage,
                size: PAGE_SIZE
            })
                .then((page) => {
                    if (!ignore) {
                        setInvoicePage({
                            ...emptyPage,
                            ...page,
                            content: page.content ?? []
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
        dateRange,
        debouncedKeyword
    ]);

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
    const selectedVisibleInvoices = visibleInvoices.filter((order) =>
        selectedInvoiceIds.includes(order.id)
    );
    const isAllVisibleSelected =
        visibleInvoices.length > 0 &&
        selectedVisibleInvoices.length === visibleInvoices.length;
    const canPrintInvoice = selectedInvoiceIds.length === 1;

    const toggleInvoiceSelection = (orderId: string) => {
        setSelectedInvoiceIds((current) =>
            current.includes(orderId)
                ? current.filter((id) => id !== orderId)
                : [...current, orderId]
        );
    };

    const toggleVisibleInvoices = () => {
        setSelectedInvoiceIds((current) => {
            const visibleIds = visibleInvoices.map((order) => order.id);

            if (isAllVisibleSelected) {
                return current.filter((id) => !visibleIds.includes(id));
            }

            return Array.from(new Set([...current, ...visibleIds]));
        });
    };

    const handleAction = (
        action: string,
        order: OrderSummaryResponse,
        rowIndex: number
    ) => {
        if (action === "Xem chi tiết") {
            selectInvoice(order, rowIndex);
        }

        setOpenActionCode(null);
    };

    const selectPaymentFilter = (status: InvoicePaymentTabKey) => {
        setFilterPaymentStatus(status);
        setCurrentPage(0);
        setSelectedInvoiceIds([]);
    };

    const selectDateFilter = (status: DateFilterKey) => {
        setDateFilter(status);
        setSelectedInvoiceIds([]);
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

    const fromRecord =
        totalElements > 0 ? invoicePage.number * invoicePage.size + 1 : 0;
    const toRecord = Math.min(
        (invoicePage.number + 1) * invoicePage.size,
        totalElements
    );
    const isInvoiceDetailOpen = Boolean(
        selectedOrderId ||
            selectedSummary ||
            selectedDetail ||
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
                        <div className={styles.breadcrumbs}>
                            <span>Đơn hàng</span>
                            <ChevronRight size={16} aria-hidden="true" />
                            <span className={styles.greenText}>Hóa đơn</span>
                        </div>
                        <h1>Hóa đơn</h1>
                        <p>
                            Quản lý và theo dõi tất cả hóa đơn bán trà, phụ
                            kiện và đơn giao hàng trong hệ thống.
                        </p>
                    </div>

                    <div className={styles.actions}>
                        <label className={styles.searchBox}>
                            <Search size={18} aria-hidden="true" />
                            <input
                                aria-label="Tìm kiếm hóa đơn"
                                onChange={(event) => {
                                    setSearchTerm(event.target.value);
                                    setCurrentPage(0);
                                    setSelectedInvoiceIds([]);
                                }}
                                placeholder="Tìm mã HĐ, mã ĐH, khách hàng, SĐT..."
                                value={searchTerm}
                            />
                        </label>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={17} aria-hidden="true" />
                            Tạo thủ công
                        </button>
                        <button
                            className={styles.secondaryButton}
                            disabled={!canPrintInvoice}
                            type="button"
                        >
                            <Printer size={17} aria-hidden="true" />
                            In hóa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </button>
                        <TeaSetArt />
                    </div>
                </div>

                <div className={`${styles.body} ${styles.bodyExpanded}`}>
                    <div className={styles.tablePanel}>
                        <div className={styles.summary}>
                            <div className={styles.filterStack}>
                                <div className={styles.filterGroup}>
                                    <span className={styles.filterLabel}>
                                        Thanh toán:
                                    </span>
                                    <div className={styles.paymentFilters}>
                                        {INVOICE_PAYMENT_TABS.map((filter) => (
                                            <button
                                                className={`${styles.paymentChip} ${
                                                    filterPaymentStatus ===
                                                    filter.key
                                                        ? styles.activePaymentChip
                                                        : ""
                                                }`}
                                                key={filter.key}
                                                onClick={() =>
                                                    selectPaymentFilter(
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

                                <DateQuickFilter
                                    value={dateFilter}
                                    onChange={selectDateFilter}
                                />
                            </div>
                            <span className={styles.summaryText}>
                                Tổng: {totalElements} hóa đơn
                            </span>
                        </div>

                        <div className={styles.tableWrap}>
                            <table className={styles.invoiceTable}>
                                <thead>
                                    <tr>
                                        <th>
                                            <button
                                                aria-label="Chọn tất cả hóa đơn đang hiển thị"
                                                className={`${styles.checkbox} ${
                                                    isAllVisibleSelected
                                                        ? styles.checked
                                                        : ""
                                                }`}
                                                onClick={toggleVisibleInvoices}
                                                type="button"
                                            />
                                        </th>
                                        <th>Mã HĐ</th>
                                        <th>Thanh toán</th>
                                        <th>Tổng tiền</th>
                                        <th>Khách hàng</th>
                                        <th>Ngày</th>
                                        <th>Mã ĐH</th>
                                        <th>Nhân viên</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {isListLoading ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={9}
                                            >
                                                <strong>Đang tải hóa đơn</strong>
                                                <span>
                                                    Dữ liệu đang được đồng bộ từ
                                                    Order-Service
                                                </span>
                                            </td>
                                        </tr>
                                    ) : null}

                                    {!isListLoading && listError ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={9}
                                            >
                                                <strong>
                                                    Không tải được danh sách hóa đơn
                                                </strong>
                                                <span>{listError}</span>
                                            </td>
                                        </tr>
                                    ) : null}

                                    {!isListLoading && !listError
                                        ? visibleInvoices.map((order, index) => {
                                              const absoluteIndex =
                                                  invoicePage.number *
                                                      invoicePage.size +
                                                  index;
                                              const invoiceCode =
                                                  buildInvoiceCode(
                                                      order,
                                                      absoluteIndex
                                                  );

                                              return (
                                                  <tr
                                                      className={
                                                          selectedOrderId ===
                                                          order.id
                                                              ? styles.activeRow
                                                              : ""
                                                      }
                                                      key={order.id}
                                                      onClick={() =>
                                                          selectInvoice(
                                                              order,
                                                              absoluteIndex
                                                          )
                                                      }
                                                  >
                                                      <td>
                                                          <button
                                                              aria-label={`Chọn hóa đơn ${invoiceCode}`}
                                                              className={`${styles.checkbox} ${
                                                                  selectedInvoiceIds.includes(
                                                                      order.id
                                                                  )
                                                                      ? styles.checked
                                                                      : ""
                                                              }`}
                                                              onClick={(
                                                                  event
                                                              ) => {
                                                                  event.stopPropagation();
                                                                  toggleInvoiceSelection(
                                                                      order.id
                                                                  );
                                                              }}
                                                              type="button"
                                                          />
                                                      </td>
                                                      <td>
                                                          <button
                                                              className={
                                                                  styles.invoiceLink
                                                              }
                                                              type="button"
                                                          >
                                                              {invoiceCode}
                                                          </button>
                                                      </td>
                                                      <td>
                                                          <PaymentStatusBadge
                                                              status={
                                                                  order.paymentStatus
                                                              }
                                                              label={getPaymentStatusText(
                                                                  order
                                                              )}
                                                          />
                                                      </td>
                                                      <td>
                                                          <strong>
                                                              {formatCurrency(
                                                                  order.finalAmount
                                                              )}
                                                          </strong>
                                                      </td>
                                                      <td>
                                                          <strong>
                                                              {getCustomerName(
                                                                  order
                                                              )}
                                                          </strong>
                                                      </td>
                                                      <td>
                                                          {formatDateTime(
                                                              order.createdAt
                                                          )}
                                                      </td>
                                                      <td>
                                                          {getOrderCode(order)}
                                                      </td>
                                                      <td>
                                                          <strong>
                                                              {getStaffName(
                                                                  order
                                                              )}
                                                          </strong>
                                                      </td>
                                                      <td
                                                          className={
                                                              styles.actionCell
                                                          }
                                                      >
                                                          <button
                                                              aria-expanded={
                                                                  openActionCode ===
                                                                  order.id
                                                              }
                                                              aria-label={`Mở thao tác cho hóa đơn ${invoiceCode}`}
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
                                                                  size={17}
                                                                  aria-hidden="true"
                                                              />
                                                          </button>
                                                          {openActionCode ===
                                                          order.id ? (
                                                              <div
                                                                  className={
                                                                      styles.actionMenu
                                                                  }
                                                              >
                                                                  {[
                                                                      "Xem chi tiết",
                                                                      "In hóa đơn",
                                                                      "Tải PDF",
                                                                      "Gửi hóa đơn",
                                                                      "Hoàn tiền"
                                                                  ].map(
                                                                      (
                                                                          action
                                                                      ) => (
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
                                                                                      order,
                                                                                      absoluteIndex
                                                                                  );
                                                                              }}
                                                                              type="button"
                                                                          >
                                                                              {
                                                                                  action
                                                                              }
                                                                          </button>
                                                                      )
                                                                  )}
                                                              </div>
                                                          ) : null}
                                                      </td>
                                                  </tr>
                                              );
                                          })
                                        : null}

                                    {!isListLoading &&
                                    !listError &&
                                    visibleInvoices.length === 0 ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={9}
                                            >
                                                <strong>
                                                    Không tìm thấy hóa đơn phù hợp
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
                                Hiển thị{" "}
                                <strong>
                                    {fromRecord} - {toRecord}
                                </strong>{" "}
                                trong {totalElements} dòng
                            </span>
                            <div className={styles.pageTools}>
                                <span>Trang</span>
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
                                    <ChevronLeft
                                        size={15}
                                        aria-hidden="true"
                                    />
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
                                    <ChevronRight
                                        size={15}
                                        aria-hidden="true"
                                    />
                                </button>
                            </div>
                        </div>
                    </div>

                    {isInvoiceDetailOpen ? (
                        <div
                            className={styles.detailModalBackdrop}
                            onClick={closeInvoiceDetail}
                            role="presentation"
                        >
                            <div
                                aria-modal="true"
                                className={styles.detailModal}
                                onClick={(event) => event.stopPropagation()}
                                role="dialog"
                            >
                                <InvoiceDetailPanel
                                    order={selectedDetail}
                                    summary={selectedSummary}
                                    rowIndex={selectedRowIndex}
                                    loading={isDetailLoading}
                                    error={detailError}
                                    onClose={closeInvoiceDetail}
                                />
                            </div>
                        </div>
                    ) : null}
                </div>
            </div>
        </section>
    );
}

function TeaSetArt() {
    return (
        <svg
            className={styles.heroArt}
            viewBox="0 0 164 72"
            aria-hidden="true"
        >
            <path
                d="M8 56c18-8 35-10 52-5 24-20 58-26 93-16-14 17-39 26-69 25-26 8-52 8-76-4Z"
                fill="#4c8d2b"
                opacity="0.22"
            />
            <path
                d="M100 31c12-12 32-12 43 0 8 9 6 21-7 26-16 6-38 2-44-10-3-6 0-12 8-16Z"
                fill="#244322"
            />
            <ellipse cx="118" cy="32" rx="25" ry="8" fill="#dce8db" />
            <ellipse cx="118" cy="32" rx="18" ry="5" fill="#315b2c" />
            <path
                d="M143 38c13 0 15 13 2 17"
                fill="none"
                stroke="#244322"
                strokeWidth="6"
                strokeLinecap="round"
            />
            <path
                d="M92 36c-12-6-20-3-25 6"
                fill="none"
                stroke="#244322"
                strokeWidth="6"
                strokeLinecap="round"
            />
            <ellipse cx="62" cy="52" rx="22" ry="9" fill="#244322" />
            <ellipse cx="62" cy="49" rx="17" ry="6" fill="#dce8db" />
            <ellipse cx="62" cy="49" rx="10" ry="3" fill="#315b2c" />
            <path
                d="M25 50c8-12 20-15 34-9-10 8-22 11-34 9Z"
                fill="#5c9b36"
            />
        </svg>
    );
}
