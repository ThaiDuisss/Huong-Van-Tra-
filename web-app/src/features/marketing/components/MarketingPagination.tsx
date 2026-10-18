import { ChevronLeft, ChevronRight } from "lucide-react";

import styles from "./discount-programs/DiscountProgramPage.module.css";

interface MarketingPaginationProps {
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
}

const pageSizeOptions = [10, 20, 50] as const;

export function MarketingPagination({
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange
}: MarketingPaginationProps) {
    const firstItem = totalItems === 0 ? 0 : (page - 1) * pageSize + 1;
    const lastItem = Math.min(page * pageSize, totalItems);
    const safePageCount = Math.max(1, pageCount);
    const canGoBack = page > 1;
    const canGoForward = page < safePageCount;

    return (
        <footer className={styles.footer}>
            <span>
                Hiển thị {firstItem} - {lastItem} trong số {totalItems}{" "}
                kết quả
            </span>

            <div className={styles.paginationControls}>
                <label className={styles.pageSize}>
                    <select
                        value={pageSize}
                        onChange={(event) =>
                            onPageSizeChange(
                                Number(event.currentTarget.value)
                            )
                        }
                    >
                        {pageSizeOptions.map((option) => (
                            <option key={option} value={option}>
                                {option} / trang
                            </option>
                        ))}
                    </select>
                </label>
                <span className={styles.pageButton}>{page}</span>
                <button
                    className={styles.pagerButton}
                    type="button"
                    disabled={!canGoBack}
                    onClick={() => onPageChange(page - 1)}
                    aria-label="Trang trước"
                >
                    <ChevronLeft size={17} aria-hidden="true" />
                </button>
                <button
                    className={styles.pagerButton}
                    type="button"
                    disabled={!canGoForward}
                    onClick={() => onPageChange(page + 1)}
                    aria-label="Trang sau"
                >
                    <ChevronRight size={17} aria-hidden="true" />
                </button>
            </div>
        </footer>
    );
}
