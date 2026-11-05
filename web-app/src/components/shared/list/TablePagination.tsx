import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";

interface TablePaginationProps {
    /** Trang hiện tại, tính từ 0. */
    page: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    itemLabel: string;
    onPageChange: (page: number) => void;
    disabled?: boolean;
}

export function TablePagination({
    page,
    totalPages,
    totalItems,
    pageSize,
    itemLabel,
    onPageChange,
    disabled = false
}: TablePaginationProps) {
    const pageCount = Math.max(totalPages, 1);
    const currentPage = Math.min(page + 1, pageCount);
    const visibleStart = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const visibleEnd = Math.min(currentPage * pageSize, totalItems);

    return (
        <footer className="flex min-h-[54px] items-center justify-between gap-4 border-t border-[#e4ece8] bg-white px-4 py-2 text-xs font-extrabold text-[#657282] max-md:flex-col max-md:items-start">
            <span>
                Hiển thị {visibleStart.toLocaleString("vi-VN")}-{visibleEnd.toLocaleString("vi-VN")} trên tổng{" "}
                {totalItems.toLocaleString("vi-VN")} {itemLabel}
            </span>
            <div className="flex items-center gap-1">
                <PageButton
                    ariaLabel="Trang trước"
                    disabled={disabled || currentPage <= 1}
                    onClick={() => onPageChange(currentPage - 2)}
                >
                    <ChevronLeft size={15} aria-hidden="true" />
                </PageButton>
                {visiblePageNumbers(currentPage, pageCount).map((item, index) =>
                    item === "gap" ? (
                        <span className="px-1 text-[#8a96a3]" key={`gap-${index}`}>...</span>
                    ) : (
                        <PageButton
                            active={item === currentPage}
                            ariaLabel={`Trang ${item}`}
                            disabled={disabled}
                            key={item}
                            onClick={item === currentPage ? undefined : () => onPageChange(item - 1)}
                        >
                            {item}
                        </PageButton>
                    )
                )}
                <PageButton
                    ariaLabel="Trang sau"
                    disabled={disabled || currentPage >= pageCount}
                    onClick={() => onPageChange(currentPage)}
                >
                    <ChevronRight size={15} aria-hidden="true" />
                </PageButton>
            </div>
        </footer>
    );
}

function visiblePageNumbers(currentPage: number, totalPages: number): Array<number | "gap"> {
    if (totalPages <= 5) {
        return Array.from({ length: totalPages }, (_item, index) => index + 1);
    }

    const pages: Array<number | "gap"> = [1];
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);

    if (start > 2) pages.push("gap");
    for (let item = start; item <= end; item += 1) pages.push(item);
    if (end < totalPages - 1) pages.push("gap");
    pages.push(totalPages);

    return pages;
}

function PageButton({
    active = false,
    ariaLabel,
    children,
    disabled = false,
    onClick
}: {
    active?: boolean;
    ariaLabel?: string;
    children: ReactNode;
    disabled?: boolean;
    onClick?: () => void;
}) {
    return (
        <button
            aria-current={active ? "page" : undefined}
            aria-label={ariaLabel}
            className={`grid size-8 place-items-center rounded-lg border text-xs font-black transition-colors ${active
                ? "border-[#159734] bg-[#159734] text-white"
                : "border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f7fbf8]"
                } disabled:cursor-not-allowed disabled:opacity-45`}
            disabled={disabled}
            onClick={onClick}
            type="button"
        >
            {children}
        </button>
    );
}
