import { ApiError } from "@/lib/api-client";

import { apiClient } from "../../../api/api-client";
import type {
  AccountPageResponse,
  AccountQuery,
  AccountResponse,
  CreateAccountRequest,
  StaffRole,
  UpdateAccountRequest,
} from "../types/account.types";

const BASE_PATH = "/identity/accounts";

export const accountApi = {
  search(query: AccountQuery) {
    return apiClient.get<AccountPageResponse>(BASE_PATH, { query: { ...query } });
  },
  /** Các vai trò có thể gán (đã tồn tại trong Keycloak). */
  assignableRoles() {
    return apiClient.get<StaffRole[]>(`${BASE_PATH}/roles`);
  },
  detail(id: string) {
    return apiClient.get<AccountResponse>(`${BASE_PATH}/${encodeURIComponent(id)}`);
  },
  create(input: CreateAccountRequest) {
    return apiClient.post<AccountResponse, CreateAccountRequest>(BASE_PATH, input);
  },
  update(id: string, input: UpdateAccountRequest) {
    return apiClient.put<AccountResponse, UpdateAccountRequest>(`${BASE_PATH}/${encodeURIComponent(id)}`, input);
  },
  changeStatus(id: string, enabled: boolean) {
    return apiClient.patch<AccountResponse, { enabled: boolean }>(
      `${BASE_PATH}/${encodeURIComponent(id)}/status`,
      { enabled },
    );
  },
  /** Đặt lại mật khẩu: mật khẩu mới là mật khẩu tạm, nhân viên phải đổi ở lần đăng nhập kế tiếp. */
  resetPassword(id: string, password: string) {
    return apiClient.post<AccountResponse, { password: string }>(
      `${BASE_PATH}/${encodeURIComponent(id)}/reset-password`,
      { password },
    );
  },
};

export function describeAccountError(error: unknown, fallback = "Không thể xử lý yêu cầu. Vui lòng thử lại.") {
  if (error instanceof ApiError) {
    if (error.status === 0) return "Không kết nối được máy chủ.";
    if (error.status === 408) return "Máy chủ phản hồi quá lâu.";
    // Backend trả AppException dưới dạng HTTP 400, mã thật nằm trong body (code = 403, message = "Forbidden").
    const forbidden = error.status === 403 || String(error.code) === "403" || /^forbidden$/i.test(error.message.trim());
    if (error.code === "ACCOUNT_PROTECTED") return "Không thể chỉnh sửa, khóa hoặc đặt lại mật khẩu tài khoản quản trị cấp cao.";
    if (error.status === 401 || forbidden) return "Tài khoản của bạn không có quyền quản lý tài khoản nhân viên.";
    if (error.status === 404 && error.code !== "RESOURCE_NOT_FOUND") {
      return "API quản lý tài khoản chưa khả dụng trên môi trường này.";
    }
    if (error.status === 500) return "Máy chủ gặp lỗi, vui lòng thử lại.";
    // Các lỗi nghiệp vụ còn lại (trùng tên đăng nhập/email, dữ liệu không hợp lệ, Keycloak lỗi...)
    // đã có thông báo tiếng Việt từ backend.
    if (error.message.trim()) return error.message;
  }

  return fallback;
}
