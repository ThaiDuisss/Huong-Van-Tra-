import { TablePagination } from "../../../components/shared/list/TablePagination";

interface MarketingPaginationProps {
    /** Trang hiện tại, tính từ 1. */
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
}

const pageSizeOptions = [10, 20, 50] as const;

/** Phân trang của các màn marketing; giao diện lấy từ `TablePagination` dùng chung. */
export function MarketingPagination({
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange
}: MarketingPaginationProps) {
    return (
        <TablePagination
            itemLabel="kết quả"
            onPageChange={(zeroBased) => onPageChange(zeroBased + 1)}
            onPageSizeChange={onPageSizeChange}
            page={page - 1}
            pageSize={pageSize}
            pageSizeOptions={pageSizeOptions}
            totalItems={totalItems}
            totalPages={pageCount}
        />
    );
}
