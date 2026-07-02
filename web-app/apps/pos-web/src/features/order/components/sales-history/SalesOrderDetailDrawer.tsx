import { Printer, X } from "lucide-react";

import type { SalesOrderDetail } from "../../types/sales-history.types";
import {
    formatCurrency,
    formatQuantity,
    formatSaleDateTime,
    getDetailCustomer,
    getDetailSaleTime,
    getItemProductName,
    getItemSku,
    getItemUnitPrice,
    getSalesPaymentMethodLabel,
    getSalesStatusLabel,
    toNumber
} from "../../utils/sales-history-formatters";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesOrderDetailDrawerProps {
    order: SalesOrderDetail | null;
    loading: boolean;
    error: string | null;
    open: boolean;
    onClose: () => void;
    onPrint: (order: SalesOrderDetail) => void;
}

function getDetailItemCount(order: SalesOrderDetail) {
    if (order.totalItems !== null && order.totalItems !== undefined) {
        return order.totalItems;
    }

    return (order.items ?? []).reduce(
        (total, item) => total + toNumber(item.quantity),
        0
    );
}

function getStatusClass(order: SalesOrderDetail) {
    if (order.paymentStatus === "PAID" || order.status === "COMPLETED") {
        return styles.statusPaid;
    }

    if (order.status === "CANCELLED") {
        return styles.statusCancelled;
    }

    return styles.statusUnpaid;
}

function getItemLineTotal(item: NonNullable<SalesOrderDetail["items"]>[number]) {
    if (item.lineTotal !== null && item.lineTotal !== undefined) {
        return item.lineTotal;
    }

    return toNumber(item.quantity) * toNumber(getItemUnitPrice(item));
}

export function SalesOrderDetailDrawer({
    order,
    loading,
    error,
    open,
    onClose,
    onPrint
}: SalesOrderDetailDrawerProps) {
    if (!open) {
        return null;
    }

    return (
        <div
            className={styles.drawerBackdrop}
            onClick={onClose}
            role="presentation"
        >
            <aside
                aria-label="Chi tiết hóa đơn"
                aria-modal="true"
                className={styles.detailDrawer}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.drawerHeader}>
                    <h2>Chi tiết hóa đơn</h2>
                    <button
                        aria-label="Đóng"
                        className={styles.iconButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {loading ? (
                    <div className={styles.drawerBody}>
                        <div className={styles.tableSkeleton}>
                            {Array.from({ length: 5 }, (_, index) => (
                                <span key={index} />
                            ))}
                        </div>
                    </div>
                ) : null}

                {!loading && error ? (
                    <div className={styles.drawerBody}>
                        <div className={styles.alert} role="alert">
                            {error}
                        </div>
                    </div>
                ) : null}

                {!loading && !error && order ? (
                    <>
                        <div className={styles.drawerBody}>
                            <section className={styles.detailSection}>
                                <h3>Thông tin bán hàng</h3>
                                <dl className={styles.infoGrid}>
                                    <div>
                                        <dt>Mã hóa đơn</dt>
                                        <dd>{order.orderCode || "--"}</dd>
                                    </div>
                                    <div>
                                        <dt>Thời gian bán</dt>
                                        <dd>
                                            {formatSaleDateTime(
                                                getDetailSaleTime(order)
                                            )}
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>Khách hàng</dt>
                                        <dd>{getDetailCustomer(order)}</dd>
                                    </div>
                                    <div>
                                        <dt>Số món</dt>
                                        <dd>
                                            {formatQuantity(
                                                getDetailItemCount(order)
                                            )}
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>Thanh toán</dt>
                                        <dd>
                                            <span className={styles.paymentBadge}>
                                                {getSalesPaymentMethodLabel(
                                                    order.paymentMethod,
                                                    order.paymentMethodLabel
                                                )}
                                            </span>
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>Trạng thái</dt>
                                        <dd>
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
                                        </dd>
                                    </div>
                                </dl>
                            </section>

                            <section className={styles.detailSection}>
                                <h3>Sản phẩm</h3>
                                <div className={styles.itemList}>
                                    {(order.items ?? []).map((item, index) => (
                                        <div
                                            className={styles.itemRow}
                                            key={`${getItemSku(item)}-${index}`}
                                        >
                                            <div>
                                                <strong>
                                                    {getItemProductName(item)}
                                                </strong>
                                                <span>
                                                    {getItemSku(item)} ·{" "}
                                                    Đơn giá:{" "}
                                                    {formatCurrency(
                                                        getItemUnitPrice(item)
                                                    )}
                                                </span>
                                            </div>
                                            <div className={styles.itemAmount}>
                                                <b>
                                                    {formatCurrency(
                                                        getItemLineTotal(item)
                                                    )}
                                                </b>
                                                <span>
                                                    SL: x
                                                    {formatQuantity(
                                                        item.quantity
                                                    )}
                                                </span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </section>

                            <section className={styles.totalBox}>
                                <h3>Thanh toán</h3>
                                <div>
                                    <span>Tổng tiền hàng</span>
                                    <strong>
                                        {formatCurrency(
                                            order.subTotal ??
                                                order.originalAmount
                                        )}
                                    </strong>
                                </div>
                                <div>
                                    <span>Giảm giá</span>
                                    <strong>
                                        {formatCurrency(order.discountAmount)}
                                    </strong>
                                </div>
                                <div className={styles.finalRow}>
                                    <span>Thành tiền</span>
                                    <strong>
                                        {formatCurrency(order.finalAmount)}
                                    </strong>
                                </div>
                                <div>
                                    <span>Khách đưa</span>
                                    <strong>
                                        {formatCurrency(order.paidAmount)}
                                    </strong>
                                </div>
                                <div>
                                    <span>Tiền thừa</span>
                                    <strong>
                                        {formatCurrency(order.changeAmount)}
                                    </strong>
                                </div>
                                <div>
                                    <span>Công nợ</span>
                                    <strong>
                                        {formatCurrency(order.debtAmount)}
                                    </strong>
                                </div>
                            </section>
                        </div>

                        <div className={styles.drawerFooter}>
                            <button
                                className={styles.secondaryButton}
                                onClick={onClose}
                                type="button"
                            >
                                Đóng
                            </button>
                            <button
                                className={styles.primaryButton}
                                onClick={() => onPrint(order)}
                                type="button"
                            >
                                <Printer size={17} aria-hidden="true" />
                                In lại hóa đơn
                            </button>
                        </div>
                    </>
                ) : null}

                {!loading && !error && !order ? (
                    <div className={styles.drawerBody}>
                        <div className={styles.emptyState}>
                            <strong>Không có dữ liệu hóa đơn</strong>
                            <span>Vui lòng chọn lại hóa đơn để xem chi tiết.</span>
                        </div>
                    </div>
                ) : null}
            </aside>
        </div>
    );
}
