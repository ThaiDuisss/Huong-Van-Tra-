import type { ReactNode } from "react";

import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";

export interface DashboardTableColumn<T> {
  key: string;
  header: string;
  align?: "left" | "right";
  className?: string;
  render: (row: T) => ReactNode;
}

/**
 * Bảng compact trong card dashboard, dựng từ bộ thẻ `ui/Table` dùng chung (số căn phải).
 * Không `min-width` để không tạo scroll ngang trong card.
 */
export function DashboardTable<T>({
  columns,
  getRowKey,
  rows,
}: {
  columns: Array<DashboardTableColumn<T>>;
  getRowKey: (row: T) => string;
  rows: T[];
}) {
  return (
    <div className="min-w-0 overflow-x-auto">
      <Table>
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableHeaderCell
                className={`${column.align === "right" ? "text-right" : ""} ${column.className ?? ""}`}
                key={column.key}
              >
                {column.header}
              </TableHeaderCell>
            ))}
          </TableRow>
        </TableHead>
        <tbody>
          {rows.map((row) => (
            <TableRow className="hover:bg-[#f8fbf8]" key={getRowKey(row)}>
              {columns.map((column) => (
                <TableCell
                  className={`${column.align === "right" ? "text-right tabular-nums" : ""} ${column.className ?? ""}`}
                  key={column.key}
                >
                  {column.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </tbody>
      </Table>
    </div>
  );
}
