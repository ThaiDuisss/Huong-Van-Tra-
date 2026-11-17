import { Download, Eye, Pencil } from "lucide-react";

import { Button } from "../../../components/ui/Button";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
  InventoryEmptyState,
  InventoryErrorState,
} from "../../inventory/components/InventoryManagementComponents";
import type { AiReport, AiReportPage } from "../types/ai-report.types";
import { AI_REPORT_PENDING_MESSAGE } from "../utils/ai-report.validation";

export type AiReportListState =
  | { status: "pending-backend" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; reports: AiReport[]; page: AiReportPage };

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
        <Table className="min-w-[820px]">
          <caption className="sr-only">
            Danh sách báo cáo AI. Chọn một báo cáo để xuất file gốc.
          </caption>
          <TableHead>
            <TableRow>
              <TableHeaderCell className="w-12">
                <span className="sr-only">Chọn báo cáo</span>
              </TableHeaderCell>
              <TableHeaderCell>
                Tên báo cáo
              </TableHeaderCell>
              <TableHeaderCell className="w-32">
                Phiên bản
              </TableHeaderCell>
              <TableHeaderCell className="w-44">
                Ngày cập nhật
              </TableHeaderCell>
              <TableHeaderCell className="w-80">
                Thao tác
              </TableHeaderCell>
            </TableRow>
          </TableHead>
          <tbody>
            {rows.map((report) => (
              <TableRow
                className={selectedId === report.id ? "bg-[#edf8ef]" : "hover:bg-[#f8fbf8]"}
                key={report.id}
              >
                <TableCell>
                  <input
                    aria-label={`Chọn báo cáo ${report.name}`}
                    checked={selectedId === report.id}
                    className="size-4 accent-[#159734]"
                    disabled={busy}
                    name="ai-report-selection"
                    onChange={() => onSelect(report.id)}
                    type="radio"
                  />
                </TableCell>
                <TableCell className="max-w-[400px] break-words font-bold text-[#172317]">
                  {report.name}
                  <p className="mt-1 text-xs font-normal text-[#697684]">{report.fileName} · {report.fileType}</p>
                </TableCell>
                <TableCell className="break-words font-semibold text-[#536078]">
                  {report.version}
                </TableCell>
                <TableCell className="text-[#697684]">
                  <time dateTime={report.updatedAt}>{formatDate(report.updatedAt)}</time>
                  <p className="mt-1 text-xs text-[#697684]">Tạo: <time dateTime={report.createdAt}>{formatDate(report.createdAt)}</time></p>
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap items-center gap-1">
                    <span title={!fileActionsAvailable ? AI_REPORT_PENDING_MESSAGE : undefined}>
                      <Button
                        aria-label={`Xem báo cáo ${report.name}`}
                        size="sm"
                        variant="ghost"
                        disabled={!fileActionsAvailable || busy}
                        onClick={() => onView(report)}
                      >
                        <Eye aria-hidden="true" size={14} />
                        Xem
                      </Button>
                    </span>
                    <Button
                      aria-label={`Chỉnh sửa báo cáo ${report.name}`}
                      size="sm"
                      variant="ghost"
                      disabled={busy}
                      onClick={() => onEdit(report)}
                    >
                      <Pencil aria-hidden="true" size={14} />
                      Chỉnh sửa
                    </Button>
                    <span title={!fileActionsAvailable ? AI_REPORT_PENDING_MESSAGE : undefined}>
                      <Button
                        aria-label={`Tải bản gốc báo cáo ${report.name}`}
                        size="sm"
                        variant="ghost"
                        disabled={!fileActionsAvailable || busy}
                        onClick={() => onExport(report)}
                      >
                        <Download aria-hidden="true" size={14} />
                        Tải bản gốc
                      </Button>
                    </span>
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5}>
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
                </TableCell>
              </TableRow>
            ) : null}
          </tbody>
        </Table>
      </div>
    </div>
  );
}
