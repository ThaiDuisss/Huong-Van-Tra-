import type { ReactNode } from "react";

export interface DashboardTableColumn<T> {
  key: string;
  header: string;
  align?: "left" | "right";
  className?: string;
  render: (row: T) => ReactNode;
}

/**
 * Bảng compact theo class của `InventoryDataTable` (header #eaf7ec, border ô #edf0f4,
 * số căn phải). Không `min-width` để không tạo scroll ngang trong card.
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
      <table className="w-full text-left text-sm">
        <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
          <tr>
            {columns.map((column) => (
              <th
                className={`whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5 ${
                  column.align === "right" ? "text-right" : ""
                } ${column.className ?? ""}`}
                key={column.key}
                scope="col"
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr className="hover:bg-[#f8fbf8]" key={getRowKey(row)}>
              {columns.map((column) => (
                <td
                  className={`border-b border-[#edf0f4] px-4 py-2.5 align-middle ${
                    column.align === "right" ? "text-right tabular-nums" : ""
                  } ${column.className ?? ""}`}
                  key={column.key}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
