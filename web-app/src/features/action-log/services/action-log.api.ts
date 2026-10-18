import { ApiError } from "@/lib/api-client";

import { apiClient } from "../../../api/api-client";
import type { ActionLogPageResponse, ActionLogQuery } from "../types/action-log.types";

export const actionLogApi = {
  search(query: ActionLogQuery) {
    return apiClient.get<ActionLogPageResponse>("/identity/action-logs", {
      query: { ...query },
    });
  },
};

export function describeActionLogError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 0) return "Không kết nối được máy chủ.";
    if (error.status === 408) return "Máy chủ phản hồi quá lâu.";
    // Backend trả AppException dưới dạng HTTP 400, mã thật nằm trong body (code = 403).
    const forbidden = error.status === 403 || String(error.code) === "403" || /forbidden/i.test(error.message);
    if (error.status === 401 || forbidden) return "Tài khoản của bạn không có quyền xem nhật ký hoạt động.";
    if (error.status === 404) return "API nhật ký hoạt động chưa khả dụng trên môi trường này.";
    if (error.status >= 500) return "Máy chủ gặp lỗi, vui lòng thử lại.";
  }

  return "Không thể tải nhật ký hoạt động.";
}
