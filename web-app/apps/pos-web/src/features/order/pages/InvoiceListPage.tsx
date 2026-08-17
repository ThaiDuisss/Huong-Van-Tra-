import {
    ChevronLeft,
    ChevronRight,
    MoreHorizontal,
    Search,
    Upload
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { ExportDropdown } from "../components/ExportDropdown";
import { InvoiceDetailPanel } from "../components/InvoiceDetailPanel";
import { PaymentStatusBadge } from "../components/PaymentStatusBadge";
import {
    exportInvoices,
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
    buildInvoiceCode,
    formatCurrency,
    formatDateOnly,
    getCustomerName,
    getPaymentStatusText
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
    size: PAGE_SIZE
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
            countKey: "unpaidInvoices"
        },
        {
            key: "PAID",
            label: "Đã thanh toán",
            paymentStatus: "PAID",
            countKey: "paidInvoices"
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

export function InvoiceListPage() {
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
    const [filterPaymentStatus, setFilterPaymentStatus] =
        useState<InvoicePaymentTabKey>("ALL");
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
    const [selectedSummary, setSelectedSummary] =
        useState<OrderSummaryResponse | null>(null);
    const [selectedRowIndex, setSelectedRowIndex] = useState(0);
    const [selectedDetail, setSelectedDetail] =
        useState<OrderDetailResponse | null>(null);
    const [openActionCode, setOpenActionCode] = useState<string | null>(null);
    const [invoicePage, setInvoicePage] = useState(emptyPage);
    const [stats, setStats] = useState<OrderStatsResponse>({});
    const [isListLoading, setIsListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);

    const activePaymentTab = INVOICE_PAYMENT_TABS.find(
        (tab) => tab.key === filterPaymentStatus
    );
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

    const handleClickImport = () => {
        importInputRef.current?.click();
    };

    const handleImportFile = (file?: File) => {
        if (!file) {
            return;
        }

        console.log("TODO: Nhập file hóa đơn", file);
        window.alert("Chức năng nhập file đang được phát triển");
    };

    const buildCurrentFilterExportParams = () =>
        buildActiveOrderExportParams({
            fromDate: timeRange?.fromDate,
            keyword: debouncedKeyword,
            paymentStatus: activePaymentTab?.paymentStatus,
            toDate: timeRange?.toDate
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
            className={`${styles.page} ${isTimeCollapsed ? styles.pageTimeCollapsed : ""
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
                        <h1>Hóa đơn</h1>
                        <p>
                            Quản lý và theo dõi tất cả hóa đơn bán trà, phụ
                            kiện và đơn giao hàng trong hệ thống.
                        </p>
                    </div>

                    <div className={styles.actions}>
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
                            allFilenamePrefix="tat-ca-hoa-don"
                            allLabel="Xuất toàn bộ hóa đơn"
                            buttonClassName={styles.secondaryButton}
                            emptyFilteredMessage="Không có hóa đơn phù hợp với bộ lọc để xuất file."
                            fallbackErrorMessage="Không thể xuất file hóa đơn. Vui lòng thử lại."
                            filteredDescription="Áp dụng thời gian, thanh toán và từ khóa đang chọn"
                            filteredFilenamePrefix="hoa-don-theo-bo-loc"
                            hasNoFilteredData={
                                !isListLoading &&
                                !listError &&
                                totalElements === 0
                            }
                            onExport={(mode) =>
                                exportInvoices(
                                    mode === "filtered"
                                        ? buildCurrentFilterExportParams()
                                        : {}
                                )
                            }
                            successMessage="Đã xuất file hóa đơn thành công."
                        />
                    </div>
                </div>

                <div className={`${styles.body} ${styles.bodyExpanded}`}>
                    <div className={styles.tablePanel}>
                        <div className={styles.filterRows}>
                            <div className={styles.filterToolbar}>
                                <label className={styles.searchBox}>
                                    <Search size={20} aria-hidden="true" />
                                    <input
                                        aria-label="Tìm kiếm hóa đơn"
                                        onChange={(event) => {
                                            setSearchTerm(event.target.value);
                                            setCurrentPage(0);
                                        }}
                                        onKeyDown={(event) => {
                                            if (event.key === "Escape") {
                                                setSearchTerm("");
                                                setCurrentPage(0);
                                            }
                                        }}
                                        placeholder="Tìm mã HĐ, mã ĐH, khách hàng, SĐT..."
                                        value={searchTerm}
                                    />
                                </label>

                                <div className={styles.filterGroup}>
                                    <span className={styles.filterLabel}>
                                        Thanh toán:
                                    </span>
                                    <div className={styles.paymentFilters}>
                                        {INVOICE_PAYMENT_TABS.map((filter) => (
                                            <button
                                                className={`${styles.paymentChip} ${filterPaymentStatus ===
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
                                <table className={styles.invoiceTable}>
                                <colgroup>
                                    <col style={{ width: "28%" }} />
                                    <col style={{ width: "30%" }} />
                                    <col style={{ width: "17%" }} />
                                    <col style={{ width: "15%" }} />
                                    <col style={{ width: "12%" }} />
                                    <col style={{ width: "10%" }} />
                                </colgroup>
                                <thead>
                                    <tr>
                                        <th>Mã hóa đơn</th>
                                        <th>Thanh toán</th>
                                        <th>Tổng tiền</th>
                                        <th>Khách hàng</th>
                                        <th>Ngày</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {isListLoading ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={6}
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
                                                colSpan={6}
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
                                                    className={`${
                                                        index % 2 === 0
                                                            ? styles.zebraFirstRow
                                                            : styles.zebraSecondRow
                                                    } ${
                                                        selectedOrderId ===
                                                        order.id
                                                            ? styles.activeRow
                                                            : ""
                                                    }`}
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
                                                colSpan={6}
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
                                                className={`${styles.pageButton} ${page === currentPage
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
