import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import type { ActionLogResponse } from "../types/action-log.types";
import {
  formatDuration,
  formatOccurredAt,
  getServiceLabel,
  humanizeAction,
  shortId,
} from "../utils/action-log.utils";
import { ActionLogStatusBadge, HttpMethodTag } from "./ActionLogStatusBadge";

/** Bảng theo class của `DashboardTable`; cả dòng bấm được (và focus bằng bàn phím) để mở chi tiết. */
export function ActionLogTable({
  onSelect,
  rows,
  selectedId,
}: {
  onSelect: (row: ActionLogResponse) => void;
  rows: ActionLogResponse[];
  selectedId: number | null;
}) {
  return (
    <div className="min-w-0 overflow-x-auto">
      <Table className="min-w-[980px]">
        <TableHead>
          <TableRow>
            <TableHeaderCell>Thời gian</TableHeaderCell>
            <TableHeaderCell>Người thực hiện</TableHeaderCell>
            <TableHeaderCell>Hành động</TableHeaderCell>
            <TableHeaderCell>Đối tượng</TableHeaderCell>
            <TableHeaderCell>Phân hệ</TableHeaderCell>
            <TableHeaderCell>Kết quả</TableHeaderCell>
            <TableHeaderCell className="text-right">Thời lượng</TableHeaderCell>
          </TableRow>
        </TableHead>
        <tbody>
          {rows.map((row) => {
            const selected = row.id === selectedId;
            return (
              <TableRow
                className={`cursor-pointer outline-none transition-colors focus-visible:bg-[#eef7f0] ${
                  selected ? "bg-[#eef7f0]" : "hover:bg-[#f8fbf8]"
                }`}
                key={row.id}
                onClick={() => onSelect(row)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onSelect(row);
                  }
                }}
                tabIndex={0}
              >
                <TableCell className="whitespace-nowrap tabular-nums text-[#172317]">
                  {formatOccurredAt(row.occurredAt)}
                </TableCell>
                <TableCell>
                  <div className="grid">
                    <span className="font-bold text-[#172317]">{row.username ?? "Không rõ"}</span>
                    {row.userId ? (
                      <span className="font-mono text-[11px] text-[#8793a1]" title={row.userId}>
                        {shortId(row.userId)}
                      </span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell>
                  <div className="grid gap-0.5">
                    <span className="font-bold text-[#172317]">{humanizeAction(row.action)}</span>
                    <span className="flex min-w-0 items-center gap-1.5">
                      {row.httpMethod ? <HttpMethodTag method={row.httpMethod} /> : null}
                      <span className="max-w-[260px] truncate font-mono text-[11px] text-[#8793a1]" title={row.path ?? row.action}>
                        {row.path ?? row.action}
                      </span>
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  {row.entityType || row.entityId ? (
                    <div className="grid">
                      <span className="font-semibold text-[#172317]">{row.entityType ?? "—"}</span>
                      {row.entityId ? (
                        <span className="font-mono text-[11px] text-[#8793a1]" title={row.entityId}>
                          {shortId(row.entityId, 13)}
                        </span>
                      ) : null}
                    </div>
                  ) : (
                    <span className="text-[#8793a1]">—</span>
                  )}
                </TableCell>
                <TableCell className="whitespace-nowrap font-semibold text-[#536078]">
                  {getServiceLabel(row.service)}
                </TableCell>
                <TableCell>
                  <ActionLogStatusBadge status={row.status} />
                </TableCell>
                <TableCell className="whitespace-nowrap text-right tabular-nums text-[#536078]">
                  {formatDuration(row.durationMs)}
                </TableCell>
              </TableRow>
            );
          })}
        </tbody>
      </Table>
    </div>
  );
}
