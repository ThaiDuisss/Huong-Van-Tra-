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
    getOrderId,
    getSalesPaymentMethodLabel,
    getSalesStatusLabel
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
                    <div>
                        <span>Chi tiết hóa đơn</span>
                        <h2>{order?.orderCode || "--"}</h2>
                    </div>
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
                                        <dt>Thời gian bán</dt>
                                        <dd>
                                            {formatSaleDateTime(
                                                getDetailSaleTime(order)
                                            )}
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>Nhân viên</dt>
                                        <dd>{order.cashierName || "--"}</dd>
                                    </div>
                                    <div>
                                        <dt>Khách hàng</dt>
                                        <dd>{getDetailCustomer(order)}</dd>
                                    </div>
                                    <div>
                                        <dt>Thanh toán</dt>
                                        <dd>
                                            {getSalesPaymentMethodLabel(
                                                order.paymentMethod,
                                                order.paymentMethodLabel
                                            )}
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>Trạng thái</dt>
                                        <dd>
                                            {getSalesStatusLabel(
                                                order.status,
                                                order.statusLabel
                                            )}
                                        </dd>
                                    </div>
                                </dl>
                            </section>

                            <section className={styles.detailSection}>
                                <h3>Danh sách sản phẩm</h3>
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
                                                    {formatQuantity(
                                                        item.quantity
                                                    )}{" "}
                                                    x{" "}
                                                    {formatCurrency(
                                                        getItemUnitPrice(item)
                                                    )}
                                                </span>
                                            </div>
                                            <b>
                                                {formatCurrency(item.lineTotal)}
                                            </b>
                                        </div>
                                    ))}
                                </div>
                            </section>

                            <section className={styles.totalBox}>
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
                            <span>Mã đơn: {getOrderId(order ?? {})}</span>
                        </div>
                    </div>
                ) : null}
            </aside>
        </div>
    );
}
