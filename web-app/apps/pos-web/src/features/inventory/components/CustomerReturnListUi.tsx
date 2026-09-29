import { ChevronLeft, ChevronRight, Search } from "lucide-react";

import orderStyles from "../../order/pages/OrderListPage.module.css";
import styles from "../pages/CustomerReturnListPage.module.css";

interface StatusOption {
    label: string;
    value: string;
}

interface ListFilterBarProps {
    returnCode: string;
    orderCode: string;
    status: string;
    statusOptions: StatusOption[];
    onReturnCodeChange(value: string): void;
    onOrderCodeChange(value: string): void;
    onStatusChange(value: string): void;
    onReset(): void;
}

export function ListFilterBar({
    returnCode,
    orderCode,
    status,
    statusOptions,
    onReturnCodeChange,
    onOrderCodeChange,
    onStatusChange,
    onReset
}: ListFilterBarProps) {
    return (
        <div className={`${orderStyles.filterRows} ${styles.filterBar}`}>
            <label className={styles.searchField}>
                <span>Mã phiếu hoàn</span>
                <span className={orderStyles.searchBox}>
                    <Search aria-hidden="true" size={18} />
                    <input
                        onChange={(event) => onReturnCodeChange(event.currentTarget.value)}
                        placeholder="Tìm mã phiếu hoàn"
                        value={returnCode}
                    />
                </span>
            </label>
            <label className={styles.searchField}>
                <span>Mã đơn hàng</span>
                <span className={orderStyles.searchBox}>
                    <Search aria-hidden="true" size={18} />
                    <input
                        onChange={(event) => onOrderCodeChange(event.currentTarget.value)}
                        placeholder="Tìm mã đơn hàng"
                        value={orderCode}
                    />
                </span>
            </label>
            <label className={styles.searchField}>
                <span>Trạng thái</span>
                <select
                    className={styles.statusSelect}
                    onChange={(event) => onStatusChange(event.currentTarget.value)}
                    value={status}
                >
                    {statusOptions.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                </select>
            </label>
            <button className={`${orderStyles.secondaryButton} ${styles.resetButton}`} onClick={onReset} type="button">
                Xóa bộ lọc
            </button>
        </div>
    );
}

interface ListPaginationProps {
    page: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    itemLabel: string;
    onPageChange(page: number): void;
}

export function ListPagination({ page, totalPages, totalItems, pageSize, itemLabel, onPageChange }: ListPaginationProps) {
    const pageCount = Math.max(1, totalPages);
    const start = Math.max(0, Math.min(page - 2, pageCount - 5));
    const pageNumbers = Array.from({ length: Math.min(5, pageCount) }, (_, index) => start + index);
    const from = totalItems > 0 ? page * pageSize + 1 : 0;
    const to = Math.min((page + 1) * pageSize, totalItems);

    return (
        <div className={`${orderStyles.pagination} ${styles.pagination}`}>
            <span>Hiển thị {from} - {to} trong {totalItems} {itemLabel}</span>
            <div className={orderStyles.pages}>
                <button
                    aria-label="Trang trước"
                    className={orderStyles.pageButton}
                    disabled={page === 0}
                    onClick={() => onPageChange(page - 1)}
                    type="button"
                ><ChevronLeft aria-hidden="true" size={18} /></button>
                {pageNumbers.map((pageNumber) => (
                    <button
                        aria-current={pageNumber === page ? "page" : undefined}
                        className={`${orderStyles.pageButton} ${pageNumber === page ? orderStyles.activePage : ""}`}
                        key={pageNumber}
                        onClick={() => onPageChange(pageNumber)}
                        type="button"
                    >{pageNumber + 1}</button>
                ))}
                {totalPages > 5 ? <span aria-hidden="true">…</span> : null}
                <button
                    aria-label="Trang sau"
                    className={orderStyles.pageButton}
                    disabled={page >= pageCount - 1}
                    onClick={() => onPageChange(page + 1)}
                    type="button"
                ><ChevronRight aria-hidden="true" size={18} /></button>
            </div>
        </div>
    );
}

export function ListMessage({ message, colSpan }: { message: string; colSpan: number }) {
    return <tr><td className={orderStyles.emptyCell} colSpan={colSpan}><strong>{message}</strong></td></tr>;
}
