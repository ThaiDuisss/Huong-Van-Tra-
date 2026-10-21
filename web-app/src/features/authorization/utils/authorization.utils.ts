import type { PermissionResponse } from "../types/authorization.types";

/** Cùng quy tắc với backend (`PermissionName`): module:action, chữ thường, số, gạch ngang, tối đa 64 ký tự. */
const PERMISSION_NAME_PATTERN = /^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$/;
export const PERMISSION_NAME_MAX_LENGTH = 64;

export function normalizePermissionName(raw: string) {
  return raw.trim().toLowerCase();
}

export function isValidPermissionName(raw: string) {
  const name = normalizePermissionName(raw);
  return name.length <= PERMISSION_NAME_MAX_LENGTH && PERMISSION_NAME_PATTERN.test(name);
}

export function validatePermissionForm(input: {
  name?: string;
  displayName: string;
  description?: string;
  isEdit: boolean;
}): string | null {
  if (!input.isEdit) {
    if (!input.name?.trim()) return "Vui lòng nhập tên quyền.";
    if (!isValidPermissionName(input.name)) {
      return "Tên quyền phải có dạng module:action, chỉ gồm chữ thường, số và dấu gạch ngang (vd. order:create).";
    }
  }
  if (!input.displayName.trim()) return "Vui lòng nhập tên hiển thị.";
  if (input.displayName.trim().length > 100) return "Tên hiển thị tối đa 100 ký tự.";
  if ((input.description ?? "").trim().length > 255) return "Mô tả tối đa 255 ký tự.";
  return null;
}

/** Phần thêm/bớt giữa tập hiện có và tập mong muốn (thứ tự ổn định để hiển thị). */
export function diffSelection(current: readonly string[], desired: readonly string[]) {
  const currentSet = new Set(current);
  const desiredSet = new Set(desired);
  return {
    added: [...desiredSet].filter((item) => !currentSet.has(item)).sort(),
    removed: [...currentSet].filter((item) => !desiredSet.has(item)).sort(),
  };
}

export function hasSelectionChanged(current: readonly string[], desired: readonly string[]) {
  const diff = diffSelection(current, desired);
  return diff.added.length > 0 || diff.removed.length > 0;
}

export function permissionModule(name: string) {
  const index = name.indexOf(":");
  return index > 0 ? name.slice(0, index) : name;
}

const MODULE_LABELS: Record<string, string> = {
  customer: "Khách hàng",
  inventory: "Kho",
  order: "Đơn hàng",
  permission: "Phân quyền",
  product: "Sản phẩm",
  production: "Sản xuất",
  promotion: "Khuyến mãi",
  report: "Báo cáo",
  role: "Vai trò",
  user: "Tài khoản",
};

export function moduleLabel(module: string) {
  return MODULE_LABELS[module] ?? module;
}

/** Gom quyền theo module (phần trước dấu ':'), giữ thứ tự theo tên. */
export function groupPermissionsByModule(permissions: readonly PermissionResponse[]) {
  const groups = new Map<string, PermissionResponse[]>();
  [...permissions]
    .sort((first, second) => first.name.localeCompare(second.name))
    .forEach((permission) => {
      const module = permissionModule(permission.name);
      const list = groups.get(module) ?? [];
      list.push(permission);
      groups.set(module, list);
    });
  return [...groups.entries()].map(([module, items]) => ({ module, label: moduleLabel(module), items }));
}

/**
 * Quyết định hiển thị một thao tác: đã có danh sách permission thì permission quyết định;
 * chưa có (backend cũ) thì dùng vai trò như trước.
 */
const SUPER_ADMIN_ROLES = ["SUPER_ADMIN", "SUPERADMIN"];

function normalizeRoles(roles: readonly string[] | undefined) {
  return (roles ?? []).map((role) => role.replace(/^ROLE_/i, "").toUpperCase());
}

export function isSuperAdmin(roles: readonly string[] | undefined) {
  const normalized = normalizeRoles(roles);
  return SUPER_ADMIN_ROLES.some((role) => normalized.includes(role));
}

/**
 * Quyết định hiển thị một thao tác (giống quy tắc backend):
 * - SUPER_ADMIN luôn được phép (tài khoản gốc, dùng để khởi tạo).
 * - Có danh sách permission khác rỗng → permission quyết định.
 * - Chưa tải được (undefined) hoặc rỗng (chưa bootstrap) → dùng vai trò như trước.
 */
export function canUse(
  input: {
    permissions: readonly string[] | undefined;
    roles: readonly string[] | undefined;
  },
  required: readonly string[],
  fallbackRoles: readonly string[],
): boolean {
  if (isSuperAdmin(input.roles)) return true;
  if (Array.isArray(input.permissions) && input.permissions.length > 0) {
    return required.some((permission) => input.permissions?.includes(permission));
  }
  const roles = normalizeRoles(input.roles);
  return fallbackRoles.some((role) => roles.includes(role.toUpperCase()));
}

export function filterPermissions(permissions: readonly PermissionResponse[], keyword: string) {
  const needle = keyword.trim().toLowerCase();
  if (!needle) return [...permissions];
  return permissions.filter(
    (permission) =>
      permission.name.toLowerCase().includes(needle) ||
      permission.displayName.toLowerCase().includes(needle) ||
      permission.description.toLowerCase().includes(needle),
  );
}
