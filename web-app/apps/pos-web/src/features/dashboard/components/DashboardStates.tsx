import { Info } from "lucide-react";
import type { ReactNode } from "react";

import {
  InventoryEmptyState,
  InventoryErrorState,
} from "../../inventory/components/InventoryManagementComponents";
import type { DashboardPart, DashboardSourceStatus } from "../types/dashboard.types";

/** Nhãn nguồn dữ liệu chỉ hiện khi không phải dữ liệu thật. */
export function SourceBadge({ source }: { source: DashboardSourceStatus }) {
  if (source === "REAL") return null;

  const label =
    source === "DEMO" ? "Dữ liệu demo" : source === "GAP" ? "Chưa có API" : "Ước tính";
  const tone =
    source === "DEMO"
      ? "border-amber-200 bg-amber-50 text-amber-700"
      : source === "GAP"
        ? "border-slate-200 bg-slate-50 text-slate-600"
        : "border-blue-200 bg-blue-50 text-blue-700";

  return (
    <span
      className={`inline-flex h-6 shrink-0 items-center whitespace-nowrap rounded-full border px-2 text-[11px] font-black ${tone}`}
      title={
        source === "DEMO"
          ? "Số liệu minh họa từ mock Front-end, chỉ bật ở môi trường phát triển."
          : source === "GAP"
            ? "Back-end chưa có API tổng hợp cho widget này."
            : "Tính tạm ở Front-end từ dữ liệu đã tải."
      }
    >
      {label}
    </span>
  );
}

/** Trạng thái thiếu API: không hiển thị số giả. */
export function DashboardGapState({
  action,
  message,
}: {
  action?: ReactNode;
  message: string;
}) {
  return (
    <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-[#f8fbf8] px-6 py-10 text-center">
      <div className="grid max-w-sm gap-2 justify-items-center">
        <Info className="text-[#697684]" size={28} aria-hidden="true" />
        <strong className="text-sm font-black text-[#172317]">Chưa có API tổng hợp</strong>
        <p className="text-xs font-semibold leading-5 text-[#697684]">{message}</p>
        {action}
      </div>
    </div>
  );
}

export function SkeletonBlock({ className = "h-12" }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-[#eef3ef] ${className}`} aria-hidden="true" />;
}

/** Skeleton cho danh sách/bảng bên trong section (cùng pulse với InventoryLoadingSkeleton). */
export function DashboardListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="grid gap-2" aria-busy="true" aria-label="Đang tải">
      {Array.from({ length: rows }, (_, index) => (
        <SkeletonBlock className="h-10" key={index} />
      ))}
    </div>
  );
}

export function DashboardKpiSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4" aria-busy="true" aria-label="Đang tải chỉ số">
      {Array.from({ length: count }, (_, index) => (
        <div
          className="grid min-h-[94px] gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"
          key={index}
        >
          <SkeletonBlock className="h-8 w-2/3" />
          <SkeletonBlock className="h-7 w-1/2" />
        </div>
      ))}
    </div>
  );
}

/**
 * Render một phần dữ liệu theo trạng thái: loading → skeleton, lỗi → error state có retry,
 * rỗng → empty state, còn lại → children(data).
 */
export function DashboardPartView<T>({
  children,
  empty,
  isEmpty,
  loading,
  onRetry,
  part,
  rows = 5,
}: {
  children: (data: T) => ReactNode;
  empty: string;
  isEmpty?: (data: T) => boolean;
  loading: boolean;
  onRetry: () => void;
  part: DashboardPart<T> | undefined;
  rows?: number;
}) {
  if (loading && !part?.data) {
    return <DashboardListSkeleton rows={rows} />;
  }

  if (!part || (part.error && !part.data)) {
    return (
      <InventoryErrorState
        message={part?.error ?? "Không thể tải dữ liệu."}
        onRetry={onRetry}
      />
    );
  }

  if (part.data === null || (isEmpty ? isEmpty(part.data) : Array.isArray(part.data) && part.data.length === 0)) {
    return <InventoryEmptyState message={empty} />;
  }

  return <>{children(part.data)}</>;
}
