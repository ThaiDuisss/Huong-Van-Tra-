import type { ActionLogResponse } from "../types/action-log.types";
import {
  formatDuration,
  formatOccurredAt,
  getServiceLabel,
  humanizeAction,
  shortId,
} from "../utils/action-log.utils";
import { ActionLogStatusBadge, HttpMethodTag } from "./ActionLogStatusBadge";

const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-middle";

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
      <table className="w-full min-w-[980px] text-left text-sm">
        <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
          <tr>
            <th className={headClass} scope="col">Thời gian</th>
            <th className={headClass} scope="col">Người thực hiện</th>
            <th className={headClass} scope="col">Hành động</th>
            <th className={headClass} scope="col">Đối tượng</th>
            <th className={headClass} scope="col">Phân hệ</th>
            <th className={headClass} scope="col">Kết quả</th>
            <th className={`${headClass} text-right`} scope="col">Thời lượng</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const selected = row.id === selectedId;
            return (
              <tr
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
                <td className={`${cellClass} whitespace-nowrap tabular-nums text-[#172317]`}>
                  {formatOccurredAt(row.occurredAt)}
                </td>
                <td className={cellClass}>
                  <div className="grid">
                    <span className="font-bold text-[#172317]">{row.username ?? "Không rõ"}</span>
                    {row.userId ? (
                      <span className="font-mono text-[11px] text-[#8793a1]" title={row.userId}>
                        {shortId(row.userId)}
                      </span>
                    ) : null}
                  </div>
                </td>
                <td className={cellClass}>
                  <div className="grid gap-0.5">
                    <span className="font-bold text-[#172317]">{humanizeAction(row.action)}</span>
                    <span className="flex min-w-0 items-center gap-1.5">
                      {row.httpMethod ? <HttpMethodTag method={row.httpMethod} /> : null}
                      <span className="max-w-[260px] truncate font-mono text-[11px] text-[#8793a1]" title={row.path ?? row.action}>
                        {row.path ?? row.action}
                      </span>
                    </span>
                  </div>
                </td>
                <td className={cellClass}>
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
                </td>
                <td className={`${cellClass} whitespace-nowrap font-semibold text-[#536078]`}>
                  {getServiceLabel(row.service)}
                </td>
                <td className={cellClass}>
                  <ActionLogStatusBadge status={row.status} />
                </td>
                <td className={`${cellClass} whitespace-nowrap text-right tabular-nums text-[#536078]`}>
                  {formatDuration(row.durationMs)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
