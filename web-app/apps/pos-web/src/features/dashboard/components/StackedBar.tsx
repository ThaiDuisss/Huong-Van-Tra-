import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import type { DashboardLocationSummary } from "../types/dashboard.types";

const segments = [
  { color: "bg-[#159734]", key: "available", label: "Khả dụng" },
  { color: "bg-amber-400", key: "reserved", label: "Giữ chỗ" },
  { color: "bg-red-500", key: "blocked", label: "Bị chặn" },
] as const;

/** Stacked bar on-hand = khả dụng + giữ chỗ + bị chặn theo địa điểm (theo bảng chọn chart mục 9). */
export function StackedBar({ rows }: { rows: DashboardLocationSummary[] }) {
  const maxOnHand = Math.max(1, ...rows.map((row) => row.onHand));

  return (
    <div className="grid gap-3">
      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs font-bold text-[#536078]" aria-label="Chú giải">
        {segments.map((segment) => (
          <li className="inline-flex items-center gap-1.5" key={segment.key}>
            <span className={`size-2.5 rounded-sm ${segment.color}`} aria-hidden="true" />
            {segment.label}
          </li>
        ))}
      </ul>
      <ul className="grid gap-3">
        {rows.map((row) => {
          const widthPercent = (row.onHand / maxOnHand) * 100;

          return (
            <li className="grid gap-1.5" key={row.locationId}>
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="truncate font-black text-[#172317]">{row.locationName}</span>
                <span className="shrink-0 font-black tabular-nums text-[#172317]">
                  {formatVietnamNumber(row.onHand)}
                </span>
              </div>
              <div
                aria-label={`${row.locationName}: khả dụng ${formatVietnamNumber(row.available)}, giữ chỗ ${formatVietnamNumber(row.reserved)}, bị chặn ${formatVietnamNumber(row.blocked)}`}
                className="flex h-2.5 overflow-hidden rounded-full bg-[#edf0f4]"
                role="img"
                style={{ width: `${Math.max(4, widthPercent)}%` }}
              >
                {segments.map((segment) => {
                  const share = row.onHand > 0 ? (row[segment.key] / row.onHand) * 100 : 0;

                  return share > 0 ? (
                    <span
                      className={`h-full ${segment.color}`}
                      key={segment.key}
                      style={{ width: `${share}%` }}
                      title={`${segment.label}: ${formatVietnamNumber(row[segment.key])}`}
                    />
                  ) : null;
                })}
              </div>
              <div className="flex flex-wrap gap-x-4 text-xs font-semibold text-[#697684]">
                <span>Khả dụng {formatVietnamNumber(row.available)}</span>
                <span>Giữ chỗ {formatVietnamNumber(row.reserved)}</span>
                <span>Bị chặn {formatVietnamNumber(row.blocked)}</span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
