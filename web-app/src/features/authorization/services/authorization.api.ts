import { ApiError } from "@/lib/api-client";

import { apiClient } from "../../../api/api-client";
import type {
  BootstrapResponse,
  BusinessRoleResponse,
  CreatePermissionRequest,
  PermissionResponse,
  RolePermissionsResponse,
  UpdatePermissionRequest,
  UserRolesResponse,
} from "../types/authorization.types";

const BASE = "/identity";

/** Mọi thao tác đi qua Identity-Service; FE không bao giờ gọi Keycloak Admin API trực tiếp. */
export const authorizationApi = {
  listPermissions(keyword?: string) {
    return apiClient.get<PermissionResponse[]>(`${BASE}/permissions`, {
      query: keyword ? { keyword } : undefined,
    });
  },
  createPermission(input: CreatePermissionRequest) {
    return apiClient.post<PermissionResponse, CreatePermissionRequest>(`${BASE}/permissions`, input);
  },
  updatePermission(name: string, input: UpdatePermissionRequest) {
    return apiClient.put<PermissionResponse, UpdatePermissionRequest>(
      `${BASE}/permissions/${encodeURIComponent(name)}`,
      input,
    );
  },
  deletePermission(name: string) {
    return apiClient.delete<void>(`${BASE}/permissions/${encodeURIComponent(name)}`);
  },
  bootstrap() {
    return apiClient.post<BootstrapResponse, undefined>(`${BASE}/permissions/bootstrap`, undefined);
  },
  listRoles() {
    return apiClient.get<BusinessRoleResponse[]>(`${BASE}/roles`);
  },
  getRolePermissions(role: string) {
    return apiClient.get<RolePermissionsResponse>(`${BASE}/roles/${encodeURIComponent(role)}/permissions`);
  },
  updateRolePermissions(role: string, permissions: string[]) {
    return apiClient.put<RolePermissionsResponse, { permissions: string[] }>(
      `${BASE}/roles/${encodeURIComponent(role)}/permissions`,
      { permissions },
    );
  },
  getUserRoles(userId: string) {
    return apiClient.get<UserRolesResponse>(`${BASE}/users/${encodeURIComponent(userId)}/roles`);
  },
  updateUserRoles(userId: string, roles: string[]) {
    return apiClient.put<UserRolesResponse, { roles: string[] }>(
      `${BASE}/users/${encodeURIComponent(userId)}/roles`,
      { roles },
    );
  },
};

export function describeAuthorizationError(error: unknown, fallback = "Không thể xử lý yêu cầu. Vui lòng thử lại.") {
  if (error instanceof ApiError) {
    if (error.status === 0) return "Không kết nối được máy chủ.";
    if (error.status === 408) return "Máy chủ phản hồi quá lâu.";
    if (error.status === 401 || error.status === 403 || String(error.code) === "403") {
      return error.message.trim() && error.code !== "FORBIDDEN"
        ? error.message
        : "Tài khoản của bạn không có quyền thực hiện thao tác này.";
    }
    if (error.status === 404 && error.code !== "RESOURCE_NOT_FOUND") {
      return "API phân quyền chưa khả dụng trên môi trường này.";
    }
    if (error.status >= 500 && !error.message.trim()) return "Máy chủ gặp lỗi, vui lòng thử lại.";
    if (error.message.trim()) return error.message;
  }
  return fallback;
}
