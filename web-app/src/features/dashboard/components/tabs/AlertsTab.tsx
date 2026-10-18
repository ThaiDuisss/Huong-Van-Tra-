import { useState } from "react";

import { InventoryEmptyState } from "../../../inventory/components/InventoryManagementComponents";
import { stockAlertSeverityLabels } from "../../../inventory/constants/stock-alert.constants";
import { formatVietnamNumber } from "../../../inventory/utils/inventory-format";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardAlertItem,
  DashboardAlertKind,
  DashboardAlertSeverity,
  DashboardAlertSnapshot,
  DashboardResource,
} from "../../types/dashboard.types";
import { countBySeverity, dashboardAlertKindLabels } from "../../utils/dashboard-alerts";
import { AlertPriorityList } from "../AlertPriorityList";
import { DashboardSection } from "../DashboardSection";
import { DashboardListSkeleton } from "../DashboardStates";
import { resourceData } from "./dashboard-tab-shared";

const severityOrder: DashboardAlertSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const severityTone: Record<DashboardAlertSeverity, string> = {
  CRITICAL: "text-red-700",
  HIGH: "text-amber-700",
  LOW: "text-[#536078]",
  MEDIUM: "text-blue-700",
};

export function AlertsTab({
  alertItems,
  alerts,
  loading,
  retry,
}: {
  alertItems: DashboardAlertItem[];
  alerts: DashboardResource<DashboardAlertSnapshot>;
  loading: boolean;
  retry: (domain: DashboardDomain) => void;
}) {
  const [kind, setKind] = useState<DashboardAlertKind | "ALL">("ALL");
  const snapshot = resourceData(alerts);
  const counts = countBySeverity(alertItems);
  const kinds = (Object.keys(dashboardAlertKindLabels) as DashboardAlertKind[]).filter((item) =>
    alertItems.some((alert) => alert.kind === item),
  );
  const visible = kind === "ALL" ? alertItems : alertItems.filter((item) => item.kind === kind);
  const partErrors = snapshot
    ? [
        snapshot.stockAlerts.error ? "cảnh báo tồn kho" : null,
        snapshot.expiringBatches.error ? "hạn dùng" : null,
        snapshot.pendingDocuments.error ? "chứng từ chờ duyệt" : null,
      ].filter((item): item is string => Boolean(item))
    : [];
  const gaps = snapshot
    ? [
        snapshot.pendingDeductions === null ? "pending deduction (DASH-GAP-011)" : null,
        snapshot.expiringReservations === null ? "reservation sắp hết hạn (DASH-GAP-010)" : null,
      ].filter((item): item is string => Boolean(item))
    : [];

  return (
    <div className="grid gap-5">
      <DashboardSection
        action={
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs font-black" aria-label="Số lượng theo mức độ">
            {severityOrder.map((severity) => (
              <span className={severityTone[severity]} key={severity}>
                {stockAlertSeverityLabels[severity]} {formatVietnamNumber(counts[severity])}
              </span>
            ))}
          </div>
        }
        description="Sắp hết hàng, cảnh báo tồn, hạn dùng, chứng từ chờ duyệt, pending deduction và giữ chỗ"
        flush
        title={`Cảnh báo cần xử lý (${formatVietnamNumber(alertItems.length)})`}
      >
        <div className="grid gap-3 px-4 pb-4">
          <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Lọc theo loại cảnh báo">
            <span className="text-[13px] font-[900] text-[#536078]">Loại:</span>
            {(["ALL", ...kinds] as Array<DashboardAlertKind | "ALL">).map((item) => {
              const active = kind === item;
              const count =
                item === "ALL" ? alertItems.length : alertItems.filter((alert) => alert.kind === item).length;

              return (
                <button
                  aria-pressed={active}
                  className={`h-[30px] rounded-full border px-3 text-[13px] font-[850] transition-all ${
                    active
                      ? "border-[#159734] bg-[#159734] text-white"
                      : "border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f4f8f5] hover:text-[#172317]"
                  }`}
                  key={item}
                  onClick={() => setKind(item)}
                  type="button"
                >
                  {item === "ALL" ? "Tất cả" : dashboardAlertKindLabels[item]}{" "}
                  <span className={active ? "text-white/80" : "text-[#9aa7b5]"}>{count}</span>
                </button>
              );
            })}
          </div>

          {partErrors.length > 0 || alerts.status === "error" ? (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-xs font-bold text-red-700">
              <span>
                Không tải được {alerts.status === "error" ? "nguồn cảnh báo" : partErrors.join(", ")}.
              </span>
              <button className="font-black underline" onClick={() => retry("alerts")} type="button">
                Thử lại
              </button>
            </div>
          ) : null}

          {loading && alertItems.length === 0 ? (
            <DashboardListSkeleton rows={6} />
          ) : visible.length === 0 ? (
            <InventoryEmptyState message="Không có cảnh báo mở cho loại đã chọn." />
          ) : (
            <AlertPriorityList items={visible} />
          )}

          {gaps.length > 0 ? (
            <p className="text-xs font-semibold text-[#697684]">
              Chưa có API cho: {gaps.join("; ")}. Xem docs/dashboard-backend-gaps.md.
            </p>
          ) : null}
        </div>
      </DashboardSection>
    </div>
  );
}
