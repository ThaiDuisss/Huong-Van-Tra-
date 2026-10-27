import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import type { DashboardSourceStatus } from "../types/dashboard.types";
import { SkeletonBlock, SourceBadge } from "./DashboardStates";

export type KpiTone = "neutral" | "good" | "warn" | "bad";

export interface DashboardKpiCardProps {
  icon: ReactNode;
  label: string;
  /** Giá trị đã format. Bỏ trống khi `source === "GAP"`. */
  value?: string;
  /** Dòng phụ: so sánh, ghi chú phạm vi. */
  note?: string;
  noteTone?: KpiTone;
  source?: DashboardSourceStatus;
  loading?: boolean;
  error?: string;
  to?: string;
  onClick?: () => void;
}

const noteToneClass: Record<KpiTone, string> = {
  bad: "text-red-700",
  good: "text-[#159734]",
  neutral: "text-[#697684]",
  warn: "text-amber-700",
};

/**
 * KPI card theo layout `InventorySummaryCard`/`ProductionSummaryCard` của dự án
 * (icon chip + label cùng hàng, giá trị text-2xl), thêm dòng phụ nhỏ.
 */
export function DashboardKpiCard({
  error,
  icon,
  label,
  loading = false,
  note,
  noteTone = "neutral",
  onClick,
  source = "REAL",
  to,
  value,
}: DashboardKpiCardProps) {
  const isGap = source === "GAP";
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2 text-sm font-black leading-tight text-[#315f3c]">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
            {icon}
          </span>
          <span>{label}</span>
        </div>
        <SourceBadge source={source} />
      </div>
      {loading && !value ? (
        <SkeletonBlock className="h-7 w-2/3" />
      ) : error ? (
        <span className="text-sm font-black text-red-700">Không tải được</span>
      ) : isGap ? (
        <span className="text-2xl font-black text-[#9aa7b5]" aria-label="Chưa có dữ liệu">
          —
        </span>
      ) : (
        <strong className="truncate text-2xl font-black text-[#172317]" title={value}>
          {value}
        </strong>
      )}
      {note ? (
        <p className={`text-xs font-semibold ${error ? "text-red-700" : noteToneClass[noteTone]}`}>
          {error ?? note}
        </p>
      ) : error ? (
        <p className="text-xs font-semibold text-red-700">{error}</p>
      ) : null}
    </>
  );
  const className =
    "grid min-h-[94px] min-w-0 content-start gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 text-left shadow-pos-card";

  if (to) {
    return (
      <Link className={`${className} transition hover:border-[#d8eadc] hover:bg-[#fbfdfb]`} to={to}>
        {body}
      </Link>
    );
  }

  if (onClick) {
    return (
      <button
        className={`${className} transition hover:border-[#d8eadc] hover:bg-[#fbfdfb]`}
        onClick={onClick}
        type="button"
      >
        {body}
      </button>
    );
  }

  return <article className={className}>{body}</article>;
}

/** Grid KPI: tối đa 4 card/hàng; 5–6 card chia 3 cột để không lẻ hàng. */
export function DashboardKpiGrid({ children, count }: { children: ReactNode; count: number }) {
  const columns = count <= 4 ? "xl:grid-cols-4" : "xl:grid-cols-3";

  return <div className={`grid gap-3 md:grid-cols-2 ${columns}`}>{children}</div>;
}
