import {
    Banknote,
    CreditCard,
    Eye,
    Landmark,
    Printer,
    ReceiptText
} from "lucide-react";

import type {
    SalesHistoryOrder,
    SalesPaymentMethod
} from "../../types/sales-history.types";
import {
    formatCurrency,
    formatQuantity,
    formatSaleTime,
    getOrderSaleTime,
    getSalesOrderCustomer,
    getSalesPaymentMethodLabel,
    getSalesStatusLabel
} from "../../utils/sales-history-formatters";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesOrdersTableProps {
    orders: SalesHistoryOrder[];
    loading: boolean;
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

export function SalesOrdersTable({
    orders,
    loading,
    onOpenDetail,
    onPrint
}: SalesOrdersTableProps) {
    if (loading) {
        return (
            <div className={styles.tablePanel}>
                <div className={styles.tableHeader}>
                    <strong>Danh sách hóa đơn</strong>
                    <span>Đang tải dữ liệu trong ngày</span>
                </div>
                <div className={styles.tableSkeleton}>
                    {Array.from({ length: 6 }, (_, index) => (
                        <span key={index} />
                    ))}
                </div>
            </div>
        );
    }

    if (orders.length === 0) {
        return (
            <div className={`${styles.tablePanel} ${styles.emptyState}`}>
                <ReceiptText size={44} aria-hidden="true" />
                <strong>Chưa có hóa đơn nào trong ngày</strong>
                <span>Các đơn đã thanh toán sẽ hiển thị tại đây.</span>
            </div>
        );
    }

    return (
        <div className={styles.tablePanel}>
            <div className={styles.tableHeader}>
                <strong>Danh sách hóa đơn</strong>
                <span>{orders.length.toLocaleString("vi-VN")} hóa đơn</span>
            </div>
            <div className={styles.tableWrap}>
                <table className={styles.salesTable}>
                    <thead>
                        <tr>
                            <th>Mã hóa đơn</th>
                            <th>Thời gian</th>
                            <th>Khách hàng</th>
                            <th>Nhân viên</th>
                            <th>Số món</th>
                            <th>Tổng tiền</th>
                            <th>Thanh toán</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        {orders.map((order) => {
                            const PaymentIcon = getPaymentIcon(
                                order.paymentMethod
                            );

                            return (
                                <tr
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
                                            <span
                                                className={styles.mutedBlock}
                                            >
                                                {order.customerPhone}
                                            </span>
                                        ) : null}
                                    </td>
                                    <td>{order.cashierName || "--"}</td>
                                    <td>
                                        {formatQuantity(order.totalItems)}
                                    </td>
                                    <td>
                                        <strong>
                                            {formatCurrency(order.finalAmount)}
                                        </strong>
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
                                                aria-label={`Xem chi tiết ${order.orderCode}`}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    onOpenDetail(order.orderId);
                                                }}
                                                title="Xem chi tiết"
                                                type="button"
                                            >
                                                <Eye
                                                    size={16}
                                                    aria-hidden="true"
                                                />
                                            </button>
                                            <button
                                                aria-label={`In lại hóa đơn ${order.orderCode}`}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    onPrint(order);
                                                }}
                                                title="In lại hóa đơn"
                                                type="button"
                                            >
                                                <Printer
                                                    size={16}
                                                    aria-hidden="true"
                                                />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
