import { Banknote, CreditCard, Landmark } from "lucide-react";
import { useMemo, useState } from "react";

import {
    DataTable,
    type DataTableColumn
} from "../../../../components/shared/list/DataTable";
import { TablePagination } from "../../../../components/shared/list/TablePagination";
import { RowActionMenu } from "../../../../components/shared/RowActionMenu";
import { StatusBadge, type BadgeTone } from "../../../../components/ui/StatusBadge";
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

function getStatusTone(order: SalesHistoryOrder): BadgeTone {
    if (order.paymentStatus === "PAID" || order.status === "COMPLETED") {
        return "success";
    }

    if (order.status === "CANCELLED") {
        return "danger";
    }

    return "pending";
}

const COLUMNS: readonly DataTableColumn<SalesHistoryOrder>[] = [
    {
        key: "orderCode",
        header: "Mã hóa đơn",
        className: "font-black",
        render: (order) => order.orderCode
    },
    {
        key: "saleTime",
        header: "Thời gian",
        render: (order) => formatSaleTime(getOrderSaleTime(order))
    },
    {
        key: "customer",
        header: "Khách hàng",
        render: (order) => (
            <>
                <strong>{getSalesOrderCustomer(order)}</strong>
                {order.customerPhone ? (
                    <span className={styles.mutedBlock}>
                        {order.customerPhone}
                    </span>
                ) : null}
            </>
        )
    },
    {
        key: "paymentMethod",
        header: "Thanh toán",
        render: (order) => {
            const PaymentIcon = getPaymentIcon(order.paymentMethod);

            return (
                <StatusBadge shape="tag">
                    <span className="inline-flex items-center gap-1.5 align-middle">
                        <PaymentIcon size={14} aria-hidden="true" />
                        {getSalesPaymentMethodLabel(
                            order.paymentMethod,
                            order.paymentMethodLabel
                        )}
                    </span>
                </StatusBadge>
            );
        }
    },
    {
        key: "finalAmount",
        header: "Tổng tiền",
        render: (order) => <strong>{formatCurrency(order.finalAmount)}</strong>
    },
    {
        key: "status",
        header: "Trạng thái",
        render: (order) => (
            <StatusBadge tone={getStatusTone(order)}>
                {getSalesStatusLabel(order.status, order.statusLabel)}
            </StatusBadge>
        )
    }
];

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
    const fromRecord = totalItems > 0 ? currentPage * pageSize + 1 : 0;
    const toRecord = Math.min((currentPage + 1) * pageSize, totalItems);
    // Giữ hành vi cũ: đang tải thì thay toàn bộ dòng bằng khung chờ.
    const rows = useMemo(() => (loading ? [] : orders), [loading, orders]);

    return (
        <DataTable
            caption="Hóa đơn bán hàng hôm nay"
            columns={COLUMNS}
            emptyDescription="Các đơn đã thanh toán sẽ hiển thị tại đây."
            emptyTitle="Chưa có hóa đơn nào trong ngày"
            getRowKey={(order) => order.orderId}
            loading={loading}
            minWidth={920}
            onRowClick={(order) => onOpenDetail(order.orderId)}
            pagination={
                <TablePagination
                    itemLabel="dòng"
                    onPageChange={onPageChange}
                    page={currentPage}
                    pageSize={pageSize}
                    summary={
                        <>
                            Hiển thị {fromRecord} - {toRecord} trong {totalItems} dòng
                        </>
                    }
                    totalItems={totalItems}
                    totalPages={totalPages}
                />
            }
            rowActions={(order) => (
                <RowActionMenu
                    actions={[
                        {
                            label: "Xem chi tiết",
                            onSelect: () => onOpenDetail(order.orderId)
                        },
                        { label: "In hóa đơn", onSelect: () => onPrint(order) }
                    ]}
                    isOpen={openActionOrderId === order.orderId}
                    menuLabel={`Thao tác cho hóa đơn ${order.orderCode}`}
                    onOpenChange={(open) =>
                        setOpenActionOrderId(open ? order.orderId : null)
                    }
                    triggerLabel={`Mở thao tác cho hóa đơn ${order.orderCode}`}
                />
            )}
            rows={rows}
        />
    );
}
