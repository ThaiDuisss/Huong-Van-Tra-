import type { ActionLogFilters, ActionLogQuery } from "../types/action-log.types";

export const DEFAULT_ACTION_LOG_FILTERS: ActionLogFilters = {
  timeRange: "SEVEN_DAYS",
  customFrom: "",
  customTo: "",
  service: "",
  status: "",
  action: "",
  username: "",
  entityType: "",
  entityId: "",
  requestId: "",
  userId: "",
};

/** `spring.application.name` của các service có gắn @UserAction (backend so khớp chính xác). */
export const ACTION_LOG_SERVICES = [
  { value: "Order-Service", label: "Đơn hàng" },
  { value: "catalog-Service", label: "Sản phẩm" },
  { value: "Customer-Service", label: "Khách hàng" },
  { value: "Inventory-Service", label: "Kho" },
  { value: "promotion-service", label: "Khuyến mãi" },
  { value: "Delivery-Service", label: "Giao hàng" },
  { value: "File-Service", label: "Tệp tin" },
  { value: "Payment-Service", label: "Thanh toán" },
  { value: "identity-Service", label: "Tài khoản" },
] as const;

const serviceLabels = new Map<string, string>(
  ACTION_LOG_SERVICES.map((item) => [item.value.toLowerCase(), item.label]),
);

export function getServiceLabel(service: string) {
  return serviceLabels.get(service.toLowerCase()) ?? service;
}

function startOfDay(date: Date) {
  const result = new Date(date);
  result.setHours(0, 0, 0, 0);
  return result;
}

function parseLocalDateTime(value: string) {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

/** Khoảng thời gian theo giờ máy người dùng, gửi lên backend dạng ISO UTC. */
export function resolveTimeRange(filters: ActionLogFilters, now = new Date()) {
  if (filters.timeRange === "ALL") return {};

  if (filters.timeRange === "CUSTOM") {
    return {
      from: parseLocalDateTime(filters.customFrom)?.toISOString(),
      to: parseLocalDateTime(filters.customTo)?.toISOString(),
    };
  }

  const from = startOfDay(now);
  if (filters.timeRange === "SEVEN_DAYS") from.setDate(from.getDate() - 6);
  if (filters.timeRange === "THIRTY_DAYS") from.setDate(from.getDate() - 29);

  return { from: from.toISOString() };
}

function clean(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

export function toActionLogQuery(filters: ActionLogFilters, page: number, size: number): ActionLogQuery {
  return {
    ...resolveTimeRange(filters),
    action: clean(filters.action),
    entityId: clean(filters.entityId),
    entityType: clean(filters.entityType),
    page,
    requestId: clean(filters.requestId),
    service: clean(filters.service),
    size,
    status: clean(filters.status),
    userId: clean(filters.userId),
    username: clean(filters.username),
  };
}

export function countAdvancedFilters(filters: ActionLogFilters) {
  return [filters.entityType, filters.entityId, filters.requestId, filters.userId].filter((value) => value.trim())
    .length;
}

export function isDefaultFilters(filters: ActionLogFilters) {
  return (Object.keys(DEFAULT_ACTION_LOG_FILTERS) as Array<keyof ActionLogFilters>).every(
    (key) => filters[key] === DEFAULT_ACTION_LOG_FILTERS[key],
  );
}

const dateFormatter = new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
const clockFormatter = new Intl.DateTimeFormat("vi-VN", {
  hour: "2-digit",
  hour12: false,
  minute: "2-digit",
  second: "2-digit",
});

/** Ngày trước giờ sau ("20/09/2026 18:17:49") để cột thời gian dễ quét theo ngày. */
export function formatOccurredAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${dateFormatter.format(date)} ${clockFormatter.format(date)}`;
}

export function formatDuration(durationMs: number | null) {
  if (durationMs === null || durationMs === undefined) return "—";
  if (durationMs < 1000) return `${durationMs} ms`;
  return `${(durationMs / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} s`;
}

/** ORDER_DRAFT_ITEM_ADD -> "Order draft item add": dễ đọc hơn mà vẫn giữ mã gốc để tra cứu. */
export function humanizeAction(action: string) {
  const text = action.replace(/_/g, " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function shortId(value: string, head = 8) {
  return value.length > head + 1 ? `${value.slice(0, head)}…` : value;
}
