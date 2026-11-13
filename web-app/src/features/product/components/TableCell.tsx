import type { ReactNode } from "react";

import { TableCell as UiTableCell } from "../../../components/ui/Table";

interface TableCellProps {
    children: ReactNode;
    className?: string;
}

/** Ô dữ liệu của bảng danh mục; kiểu dáng lấy từ `TableCell` dùng chung. */
export function TableCell({
    children,
    className = ""
}: TableCellProps) {
    return <UiTableCell className={className}>{children}</UiTableCell>;
}
