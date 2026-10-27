export type ActionLogStatus = "SUCCESS" | "FAIL";

/** Khớp `ActionLogResponse` của Identity-Service. */
export interface ActionLogResponse {
  id: number;
  requestId: string | null;
  userId: string | null;
  username: string | null;
  service: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  httpMethod: string | null;
  path: string | null;
  ip: string | null;
  status: ActionLogStatus | string;
  errorMessage: string | null;
  durationMs: number | null;
  detail: string | null;
  /** ISO-8601 (UTC). */
  occurredAt: string;
}

export interface ActionLogPageResponse {
  content: ActionLogResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type ActionLogTimeRange = "TODAY" | "SEVEN_DAYS" | "THIRTY_DAYS" | "ALL" | "CUSTOM";

/** Trạng thái bộ lọc trên màn hình. Chuỗi rỗng nghĩa là không lọc. */
export interface ActionLogFilters {
  timeRange: ActionLogTimeRange;
  /** Giá trị của input datetime-local, chỉ dùng khi timeRange = CUSTOM. */
  customFrom: string;
  customTo: string;
  service: string;
  status: string;
  action: string;
  username: string;
  entityType: string;
  entityId: string;
  requestId: string;
  userId: string;
}

/** Query string của GET /identity/action-logs. */
export interface ActionLogQuery {
  userId?: string;
  username?: string;
  service?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  requestId?: string;
  status?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}
