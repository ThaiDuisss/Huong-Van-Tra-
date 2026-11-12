import { RefreshCw } from "lucide-react";
import { useCallback, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
  InventoryEmptyState,
  InventoryErrorState,
  InventoryPageHeader,
} from "../../inventory/components/InventoryManagementComponents";
import { ActionLogDetailDrawer } from "../components/ActionLogDetailDrawer";
import { ActionLogFilterBar } from "../components/ActionLogFilterBar";
import { ActionLogPagination } from "../components/ActionLogPagination";
import { ActionLogTable } from "../components/ActionLogTable";
import { useActionLogs } from "../hooks/useActionLogs";
import type { ActionLogFilters, ActionLogResponse } from "../types/action-log.types";
import { DEFAULT_ACTION_LOG_FILTERS, isDefaultFilters } from "../utils/action-log.utils";

const timeFormatter = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", hour12: false, minute: "2-digit" });

export function ActionLogPage() {
  const [filters, setFilters] = useState<ActionLogFilters>(DEFAULT_ACTION_LOG_FILTERS);
  const [selected, setSelected] = useState<ActionLogResponse | null>(null);
  const logs = useActionLogs(filters);

  const changeFilters = useCallback((patch: Partial<ActionLogFilters>) => {
    setFilters((current) => ({ ...current, ...patch }));
  }, []);
  const resetFilters = useCallback(() => setFilters(DEFAULT_ACTION_LOG_FILTERS), []);
  const closeDetail = useCallback(() => setSelected(null), []);

  const rows = logs.data?.content ?? [];

  return (
    <section className="grid gap-5">
      <InventoryPageHeader
        actions={
          <>
            {logs.lastUpdated ? (
              <span className="text-xs font-semibold text-[#697684]">
                Cập nhật {timeFormatter.format(logs.lastUpdated)}
              </span>
            ) : null}
            <Button aria-label="Làm mới dữ liệu" disabled={logs.isLoading} onClick={logs.refresh}>
              <RefreshCw className={logs.isLoading ? "animate-spin" : ""} size={16} aria-hidden="true" />
              Làm mới
            </Button>
          </>
        }
        description="Ai đã làm gì, trên đối tượng nào, lúc nào và kết quả ra sao."
        eyebrow="Hệ thống / Nhật ký hoạt động"
        title="Nhật ký hoạt động"
      />

      <ActionLogFilterBar filters={filters} onChange={changeFilters} onReset={resetFilters} />

      {logs.error && !logs.data ? (
        <InventoryErrorState message={logs.error} onRetry={logs.refresh} />
      ) : (
        <div className="min-w-0 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
          {logs.error ? (
            <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-xs font-bold text-red-700" role="alert">
              {logs.error} Đang hiển thị dữ liệu của lần tải trước.
            </p>
          ) : null}

          {!logs.data ? (
            <div className="p-4">
              <DashboardListSkeleton rows={8} />
            </div>
          ) : rows.length === 0 ? (
            <div className="p-4">
              <InventoryEmptyState
                action={
                  isDefaultFilters(filters) ? undefined : (
                    <Button onClick={resetFilters} size="sm" variant="link">
                      Xóa bộ lọc
                    </Button>
                  )
                }
                message={
                  isDefaultFilters(filters)
                    ? "Chưa có hành động nào được ghi nhận trong 7 ngày qua."
                    : "Không có hành động nào khớp với bộ lọc hiện tại."
                }
              />
            </div>
          ) : (
            <div aria-busy={logs.isLoading} className={logs.isLoading ? "opacity-60 transition-opacity" : "transition-opacity"}>
              <ActionLogTable onSelect={setSelected} rows={rows} selectedId={selected?.id ?? null} />
            </div>
          )}

          {logs.data ? (
            <ActionLogPagination
              onPageChange={logs.setPage}
              onPageSizeChange={logs.setPageSize}
              page={logs.page}
              pageSize={logs.pageSize}
              totalElements={logs.data.totalElements}
              totalPages={logs.data.totalPages}
            />
          ) : null}
        </div>
      )}

      <ActionLogDetailDrawer log={selected} onApplyFilter={changeFilters} onClose={closeDetail} />
    </section>
  );
}
