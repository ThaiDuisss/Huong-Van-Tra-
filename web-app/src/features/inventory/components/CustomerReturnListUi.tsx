import { ChevronLeft, ChevronRight, Search } from "lucide-react";

import orderStyles from "../../order/pages/OrderListPage.module.css";
import styles from "../pages/CustomerReturnListPage.module.css";

export interface CustomerReturnStatusTab {
    key: string;
    label: string;
    isError?: boolean;
}

export interface ListFilterBarProps {
    searchTerm: string;
    searchPlaceholder?: string;
    onSearchChange(value: string): void;
    activeStatus: string;
    statusTabs: CustomerReturnStatusTab[];
    statusCounts?: Record<string, number>;
    onStatusChange(value: string): void;
    onReset?(): void;
    onReload?(): void;
    isReloading?: boolean;
}

export function ListFilterBar({
    searchTerm,
    searchPlaceholder = "Tìm mã phiếu hoàn hoặc mã đơn hàng",
    onSearchChange,
    activeStatus,
    statusTabs,
    statusCounts,
    onStatusChange
}: ListFilterBarProps) {
    return (
        <div className={styles.filterToolbar}>
            <div className={styles.searchBox}>
                <Search aria-hidden="true" size={18} />
                <input
                    onChange={(event) => onSearchChange(event.currentTarget.value)}
                    placeholder={searchPlaceholder}
                    value={searchTerm}
                />
            </div>

            <div className={styles.statusFilterGroup}>
                <span className={styles.statusLabel}>Trạng thái:</span>
                <div className={styles.statusChipsWrap}>
                    {statusTabs.map((tab) => {
                        const isActive = activeStatus === tab.key;
                        const count = statusCounts ? (statusCounts[tab.key] ?? 0) : undefined;
                        let chipClass = styles.statusChip;
                        if (tab.isError) {
                            chipClass += isActive
                                ? ` ${styles.statusChipErrorActive}`
                                : ` ${styles.statusChipError}`;
                        } else if (isActive) {
                            chipClass += ` ${styles.statusChipActive}`;
                        }

                        return (
                            <button
                                className={chipClass}
                                key={tab.key}
                                onClick={() => onStatusChange(tab.key)}
                                type="button"
                            >
                                <span>{tab.label}</span>
                                {count !== undefined && (
                                    <span className={styles.statusChipCount}>{count}</span>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>
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
