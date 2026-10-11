import { Download, Eye, Pencil } from "lucide-react";

import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
  InventoryEmptyState,
  InventoryErrorState,
} from "../../inventory/components/InventoryManagementComponents";
import type { AiReport } from "../types/ai-report.types";
import { AI_REPORT_PENDING_MESSAGE } from "../utils/ai-report.validation";

export type AiReportListState =
  | { status: "pending-backend" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; reports: AiReport[] };

const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : dateFormatter.format(date);
}

export function AiReportTable({
  state,
  selectedId,
  fileActionsAvailable,
  busy,
  onSelect,
  onView,
  onEdit,
  onExport,
  onRetry,
}: {
  state: AiReportListState;
  selectedId: string | null;
  fileActionsAvailable: boolean;
  busy: boolean;
  onSelect(id: string): void;
  onView(report: AiReport): void;
  onEdit(report: AiReport): void;
  onExport(report: AiReport): void;
  onRetry(): void;
}) {
  const rows = state.status === "ready" ? state.reports : [];
  return (
    <div
      aria-busy={state.status === "loading"}
      className="min-w-0 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"
    >
      <div className="overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-sm">
          <caption className="sr-only">
            Danh sách báo cáo AI. Chọn một báo cáo để xuất file gốc.
          </caption>
          <thead className="border-b border-[#e8eef0] bg-[#f8faf9] text-xs font-bold text-[#536078]">
            <tr>
              <th className="w-12 px-4 py-3" scope="col">
                <span className="sr-only">Chọn báo cáo</span>
              </th>
              <th className="px-4 py-3" scope="col">
                Tên báo cáo
              </th>
              <th className="w-32 px-4 py-3" scope="col">
                Phiên bản
              </th>
              <th className="w-44 px-4 py-3" scope="col">
                Ngày cập nhật
              </th>
              <th className="w-60 px-4 py-3" scope="col">
                Thao tác
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#eef2f0]">
            {rows.map((report) => (
              <tr
                className={selectedId === report.id ? "bg-[#edf8ef]" : "hover:bg-[#f8fbf8]"}
                key={report.id}
              >
                <td className="px-4 py-4">
                  <input
                    aria-label={`Chọn báo cáo ${report.name}`}
                    checked={selectedId === report.id}
                    className="size-4 accent-[#159734]"
                    disabled={busy}
                    name="ai-report-selection"
                    onChange={() => onSelect(report.id)}
                    type="radio"
                  />
                </td>
                <td className="max-w-[400px] break-words px-4 py-4 font-bold text-[#172317]">
                  {report.name}
                </td>
                <td className="break-words px-4 py-4 font-semibold text-[#536078]">
                  {report.version}
                </td>
                <td className="px-4 py-4 text-[#697684]">
                  <time dateTime={report.updatedAt}>{formatDate(report.updatedAt)}</time>
                </td>
                <td className="px-4 py-4">
                  <div className="flex flex-wrap items-center gap-3 text-xs font-bold text-[#159734]">
                    <span title={!fileActionsAvailable ? AI_REPORT_PENDING_MESSAGE : undefined}>
                      <button
                        aria-label={`Xem báo cáo ${report.name}`}
                        className="inline-flex items-center gap-1 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        disabled={!fileActionsAvailable || busy}
                        onClick={() => onView(report)}
                        type="button"
                      >
                        <Eye aria-hidden="true" size={14} />
                        Xem
                      </button>
                    </span>
                    <button
                      aria-label={`Chỉnh sửa báo cáo ${report.name}`}
                      className="inline-flex items-center gap-1 hover:underline disabled:opacity-50"
                      disabled={busy}
                      onClick={() => onEdit(report)}
                      type="button"
                    >
                      <Pencil aria-hidden="true" size={14} />
                      Chỉnh sửa
                    </button>
                    <span title={!fileActionsAvailable ? AI_REPORT_PENDING_MESSAGE : undefined}>
                      <button
                        aria-label={`Xuất báo cáo ${report.name}`}
                        className="inline-flex items-center gap-1 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        disabled={!fileActionsAvailable || busy}
                        onClick={() => onExport(report)}
                        type="button"
                      >
                        <Download aria-hidden="true" size={14} />
                        Xuất
                      </button>
                    </span>
                  </div>
                </td>
              </tr>
            ))}
            {rows.length === 0 ? (
              <tr>
                <td className="p-4" colSpan={5}>
                  {state.status === "loading" ? (
                    <DashboardListSkeleton rows={5} />
                  ) : state.status === "error" ? (
                    <InventoryErrorState message={state.message} onRetry={onRetry} />
                  ) : (
                    <InventoryEmptyState
                      message={
                        state.status === "pending-backend"
                          ? AI_REPORT_PENDING_MESSAGE
                          : "Chưa có dữ liệu báo cáo. Nhập báo cáo để bắt đầu."
                      }
                    />
                  )}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}
