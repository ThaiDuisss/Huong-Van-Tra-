import {
    Banknote,
    ChevronLeft,
    ChevronRight,
    CreditCard,
    Landmark,
    MoreHorizontal,
    ReceiptText
} from "lucide-react";
import { useState } from "react";

import type {
    SalesHistoryOrder,
    SalesPaymentMethod
} from "../../types/sales-history.types";
import {
    formatCurrency,
    formatSaleTime,
    getOrderSaleTime,
    getSalesOrderCustomer,
    getSalesPaymentMethodLabel,
    getSalesStatusLabel
} from "../../utils/sales-history-formatters";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesOrdersTableProps {
    currentPage: number;
    orders: SalesHistoryOrder[];
    pageSize: number;
    loading: boolean;
    totalItems: number;
    totalPages: number;
    onPageChange: (page: number) => void;
    onOpenDetail: (orderId: string) => void;
    onPrint: (order: SalesHistoryOrder) => void;
}

function getPaymentIcon(method?: SalesPaymentMethod | null) {
    if (method === "CASH") {
        return Banknote;
    }

    if (method === "BANK_TRANSFER" || method === "TRANSFER" || method === "QR_CODE") {
        return Landmark;
    }

    return CreditCard;
}

function getStatusClass(order: SalesHistoryOrder) {
    if (order.paymentStatus === "PAID" || order.status === "COMPLETED") {
        return styles.statusPaid;
    }

    if (order.status === "CANCELLED") {
        return styles.statusCancelled;
    }

    return styles.statusUnpaid;
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

export function SalesOrdersTable({
    currentPage,
    orders,
    pageSize,
    loading,
    totalItems,
    totalPages,
    onPageChange,
    onOpenDetail,
    onPrint
}: SalesOrdersTableProps) {
    const [openActionOrderId, setOpenActionOrderId] = useState<string | null>(
        null
    );
    const normalizedTotalPages = Math.max(totalPages, 1);
    const fromRecord = totalItems > 0 ? currentPage * pageSize + 1 : 0;
    const toRecord = Math.min((currentPage + 1) * pageSize, totalItems);
    const isFirstPage = currentPage <= 0;
    const isLastPage = currentPage >= normalizedTotalPages - 1;

    const paginationFooter = (
        <div className={styles.pagination}>
            <span>
                Hiển thị {fromRecord} - {toRecord} trong {totalItems} dòng
            </span>
            <div className={styles.pageTools}>
                <button
                    className={styles.pageButton}
                    disabled={isFirstPage || totalItems === 0}
                    onClick={() => onPageChange(Math.max(0, currentPage - 1))}
                    type="button"
                >
                    <ChevronLeft size={15} aria-hidden="true" />
                </button>
                {getPageNumbers(normalizedTotalPages, currentPage).map(
                    (page) => (
                        <button
                            className={`${styles.pageButton} ${
                                page === currentPage ? styles.activePage : ""
                            }`}
                            key={page}
                            onClick={() => onPageChange(page)}
                            type="button"
                        >
                            {page + 1}
                        </button>
                    )
                )}
                <button
                    className={styles.pageButton}
                    disabled={isLastPage || totalItems === 0}
                    onClick={() =>
                        onPageChange(
                            Math.min(normalizedTotalPages - 1, currentPage + 1)
                        )
                    }
                    type="button"
                >
                    <ChevronRight size={15} aria-hidden="true" />
                </button>
            </div>
        </div>
    );

    if (loading) {
        return (
            <div className={styles.tableCard}>
                <div className={styles.tableSkeleton}>
                    {Array.from({ length: 6 }, (_, index) => (
                        <span key={index} />
                    ))}
                </div>
                {paginationFooter}
            </div>
        );
    }

    if (orders.length === 0) {
        return (
            <div className={styles.tableCard}>
                <div className={styles.emptyState}>
                    <ReceiptText size={44} aria-hidden="true" />
                    <strong>Chưa có hóa đơn nào trong ngày</strong>
                    <span>Các đơn đã thanh toán sẽ hiển thị tại đây.</span>
                </div>
                {paginationFooter}
            </div>
        );
    }

    return (
        <div className={styles.tableCard}>
            <div className={styles.tableWrap}>
                <table className={styles.salesTable}>
                    <thead>
                        <tr>
                            <th>Mã hóa đơn</th>
                            <th>Thời gian</th>
                            <th>Khách hàng</th>
                            <th>Thanh toán</th>
                            <th>Tổng tiền</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        {orders.map((order, index) => {
                            const PaymentIcon = getPaymentIcon(
                                order.paymentMethod
                            );

                            return (
                                <tr
                                    className={
                                        index % 2 === 0
                                            ? styles.zebraFirstRow
                                            : styles.zebraSecondRow
                                    }
                                    key={order.orderId}
                                    onClick={() => onOpenDetail(order.orderId)}
                                >
                                    <td>
                                        <button
                                            className={styles.invoiceLink}
                                            type="button"
                                        >
                                            {order.orderCode}
                                        </button>
                                    </td>
                                    <td>
                                        {formatSaleTime(
                                            getOrderSaleTime(order)
                                        )}
                                    </td>
                                    <td>
                                        <strong>
                                            {getSalesOrderCustomer(order)}
                                        </strong>
                                        {order.customerPhone ? (
                                            <span className={styles.mutedBlock}>
                                                {order.customerPhone}
                                            </span>
                                        ) : null}
                                    </td>
                                    <td>
                                        <span className={styles.paymentBadge}>
                                            <PaymentIcon
                                                size={14}
                                                aria-hidden="true"
                                            />
                                            {getSalesPaymentMethodLabel(
                                                order.paymentMethod,
                                                order.paymentMethodLabel
                                            )}
                                        </span>
                                    </td>
                                    <td>
                                        <strong>
                                            {formatCurrency(order.finalAmount)}
                                        </strong>
                                    </td>
                                    <td>
                                        <span
                                            className={`${styles.statusBadge} ${getStatusClass(
                                                order
                                            )}`}
                                        >
                                            {getSalesStatusLabel(
                                                order.status,
                                                order.statusLabel
                                            )}
                                        </span>
                                    </td>
                                    <td>
                                        <div className={styles.tableActions}>
                                            <button
                                                aria-expanded={
                                                    openActionOrderId ===
                                                    order.orderId
                                                }
                                                aria-label={`Mở thao tác cho hóa đơn ${order.orderCode}`}
                                                className={
                                                    styles.rowActionButton
                                                }
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    setOpenActionOrderId(
                                                        (current) =>
                                                            current ===
                                                            order.orderId
                                                                ? null
                                                                : order.orderId
                                                    );
                                                }}
                                                type="button"
                                            >
                                                <MoreHorizontal
                                                    size={17}
                                                    aria-hidden="true"
                                                />
                                            </button>
                                            {openActionOrderId ===
                                            order.orderId ? (
                                                <div
                                                    className={
                                                        styles.actionMenu
                                                    }
                                                >
                                                    <button
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            setOpenActionOrderId(
                                                                null
                                                            );
                                                            onOpenDetail(
                                                                order.orderId
                                                            );
                                                        }}
                                                        type="button"
                                                    >
                                                        Xem chi tiết
                                                    </button>
                                                    <button
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            setOpenActionOrderId(
                                                                null
                                                            );
                                                            onPrint(order);
                                                        }}
                                                        type="button"
                                                    >
                                                        In hóa đơn
                                                    </button>
                                                </div>
                                            ) : null}
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
            {paginationFooter}
        </div>
    );
}
