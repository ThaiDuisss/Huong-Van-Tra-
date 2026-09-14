import type { DashboardFilters, DashboardTimeRange } from "../types/dashboard.types";

export const dashboardTimeRangeOptions: Array<{ key: DashboardTimeRange; label: string }> = [
  { key: "TODAY", label: "Hôm nay" },
  { key: "SEVEN_DAYS", label: "7 ngày" },
  { key: "THIRTY_DAYS", label: "30 ngày" },
];

export const defaultDashboardFilters: DashboardFilters = {
  locationId: "",
  timeRange: "TODAY",
};
