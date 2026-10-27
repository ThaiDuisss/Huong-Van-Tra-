import type { DashboardResource, DashboardTimeRange } from "../../types/dashboard.types";

export const timeRangeLabels: Record<DashboardTimeRange, string> = {
  SEVEN_DAYS: "7 ngày qua",
  THIRTY_DAYS: "30 ngày qua",
  TODAY: "hôm nay",
};

export function isResourceLoading<T>(resource: DashboardResource<T>) {
  return resource.status === "idle" || resource.status === "loading";
}

export function resourceData<T>(resource: DashboardResource<T>): T | undefined {
  return resource.status === "idle" ? undefined : resource.data;
}

/** Lớp grid cho hàng widget phụ tùy số widget hiển thị để grid tự dồn khi ẩn theo quyền. */
export function widgetRowClass(count: number) {
  if (count <= 1) return "grid gap-4";
  if (count === 2) return "grid gap-4 lg:grid-cols-2";
  return "grid gap-4 lg:grid-cols-2 xl:grid-cols-3";
}
