import { TablePagination } from "../../../components/shared/list/TablePagination";

const pageSizeOptions = [20, 50, 100] as const;

/** Phân trang nhật ký thao tác; giao diện lấy từ `TablePagination` dùng chung. */
export function ActionLogPagination({
  onPageChange,
  onPageSizeChange,
  page,
  pageSize,
  totalElements,
  totalPages,
}: {
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
  /** Bắt đầu từ 0, theo backend. */
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}) {
  return (
    <TablePagination
      itemLabel="bản ghi"
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      page={page}
      pageSize={pageSize}
      pageSizeOptions={pageSizeOptions}
      totalItems={totalElements}
      totalPages={totalPages}
    />
  );
}
