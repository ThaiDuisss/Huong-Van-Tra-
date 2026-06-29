import { Printer, X } from "lucide-react";

import { getSalesPaymentMethod } from "../../constants/sales-history-options";
import type { SalesHistoryOrder } from "../../types/sales-history.types";
import {
    formatCurrency,
    formatSaleDateTime,
    getSalesOrderCustomer,
    getSalesPaymentMethodLabel,
    getSalesPaymentStatusLabel
} from "../../utils/sales-history-formatters";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesOrderDetailDrawerProps {
    order: SalesHistoryOrder | null;
    onClose: () => void;
    onPrint: (order: SalesHistoryOrder) => void;
}

export function SalesOrderDetailDrawer({
    order,
    onClose,
    onPrint
}: SalesOrderDetailDrawerProps) {
    if (!order) {
        return null;
    }

    const paymentMethod = getSalesPaymentMethod(order);

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
                        <h2>{order.orderCode}</h2>
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

                <div className={styles.drawerBody}>
                    <section className={styles.detailSection}>
                        <h3>Thông tin bán hàng</h3>
                        <dl className={styles.infoGrid}>
                            <div>
                                <dt>Thời gian bán</dt>
                                <dd>{formatSaleDateTime(order.createdAt)}</dd>
                            </div>
                            <div>
                                <dt>Khách hàng</dt>
                                <dd>{getSalesOrderCustomer(order)}</dd>
                            </div>
                            <div>
                                <dt>Thanh toán</dt>
                                <dd>
                                    {getSalesPaymentMethodLabel(
                                        paymentMethod
                                    )}
                                </dd>
                            </div>
                            <div>
                                <dt>Trạng thái</dt>
                                <dd>
                                    {getSalesPaymentStatusLabel(
                                        order.paymentStatus
                                    )}
                                </dd>
                            </div>
                        </dl>
                    </section>

                    <section className={styles.detailSection}>
                        <h3>Danh sách sản phẩm</h3>
                        <div className={styles.itemList}>
                            {order.items.map((item) => (
                                <div
                                    className={styles.itemRow}
                                    key={`${item.sku}-${item.name}`}
                                >
                                    <div>
                                        <strong>{item.name}</strong>
                                        <span>
                                            {item.sku} · {item.quantity} x{" "}
                                            {formatCurrency(item.unitPrice)}
                                        </span>
                                    </div>
                                    <b>{formatCurrency(item.lineTotal)}</b>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className={styles.totalBox}>
                        <div>
                            <span>Tổng tiền hàng</span>
                            <strong>{formatCurrency(order.totalAmount)}</strong>
                        </div>
                        <div>
                            <span>Giảm giá</span>
                            <strong>
                                {formatCurrency(order.discountAmount)}
                            </strong>
                        </div>
                        <div className={styles.finalRow}>
                            <span>Thành tiền</span>
                            <strong>{formatCurrency(order.finalAmount)}</strong>
                        </div>
                        <div>
                            <span>Khách đưa</span>
                            <strong>{formatCurrency(order.paidAmount)}</strong>
                        </div>
                        <div>
                            <span>Tiền thừa</span>
                            <strong>
                                {formatCurrency(order.changeAmount)}
                            </strong>
                        </div>
                        <div>
                            <span>Công nợ</span>
                            <strong>{formatCurrency(order.debtAmount)}</strong>
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
            </aside>
        </div>
    );
}
