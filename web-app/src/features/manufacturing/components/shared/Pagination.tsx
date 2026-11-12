import { TablePagination } from "../../../../components/shared/list/TablePagination";

interface PaginationProps {
    /** Trang hiện tại, tính từ 1. */
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
    totalItemsLabel?: string;
}

const pageSizeOptions = [5, 10, 20] as const;

/** Phân trang của các màn danh sách; giao diện lấy từ `TablePagination` dùng chung. */
export function Pagination({
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange,
    totalItemsLabel = "Tổng số BOM"
}: PaginationProps) {
    return (
        <TablePagination
            itemLabel=""
            onPageChange={(zeroBased) => onPageChange(zeroBased + 1)}
            onPageSizeChange={onPageSizeChange}
            page={page - 1}
            pageSize={pageSize}
            pageSizeOptions={pageSizeOptions}
            summary={
                <>
                    {totalItemsLabel} <strong>{totalItems.toLocaleString("vi-VN")}</strong>
                </>
            }
            totalItems={totalItems}
            totalPages={pageCount}
        />
    );
}
