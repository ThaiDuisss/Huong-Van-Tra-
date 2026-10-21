/** Khớp DTO của Identity-Service (`PermissionDtos`). */
export interface PermissionResponse {
  id: string;
  /** Định danh ổn định `module:action`, trùng tên client role Keycloak. */
  name: string;
  displayName: string;
  description: string;
  /** Vai trò nghiệp vụ đang chứa quyền này. */
  assignedRoles: string[];
  /** Có trong danh mục mặc định của hệ thống. */
  builtIn: boolean;
}

export interface CreatePermissionRequest {
  name: string;
  displayName: string;
  description?: string;
}

export interface UpdatePermissionRequest {
  displayName: string;
  description?: string;
}

export interface BusinessRoleResponse {
  name: string;
  label: string;
  /** false: người gọi không được sửa quyền của vai trò này (vai trò mình đang giữ). */
  editable: boolean;
  permissionCount: number;
}

export interface RolePermissionsResponse {
  role: string;
  permissions: string[];
}

export interface UserRolesResponse {
  userId: string;
  username: string;
  fullName: string;
  editable: boolean;
  roles: string[];
  effectivePermissions: string[];
}

export interface BootstrapResponse {
  createdPermissions: string[];
  updatedRoles: string[];
  totalPermissions: number;
}

export type AuthorizationTab = "permissions" | "roles" | "users";
