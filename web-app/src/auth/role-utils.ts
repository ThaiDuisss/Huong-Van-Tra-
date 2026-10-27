import { hasAllRoles, hasAnyRole, hasRole, normalizeRole } from "./auth.utils";
import { ROLES, type Role } from "./roles";
import { useAuthorization } from "./useAuthorization";

export { ROLES, type Role, hasRole, hasAnyRole, hasAllRoles, normalizeRole, useAuthorization };

export const APP_ROLES = Object.values(ROLES);

export type AppRole = Role;

export const POS_WORKSPACE_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.SALE_STAFF
];

export const BACKOFFICE_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.INVENTORY_STAFF,
    ROLES.QC
];

/** Access to the AI report page only; does not grant backoffice access. */
export const AI_REPORT_ROLES: AppRole[] = [...BACKOFFICE_ROLES, ROLES.SALE_STAFF];

export const ORDER_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.SALE_STAFF
];

export const PRODUCT_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.INVENTORY_STAFF
];

export const MANUFACTURING_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.INVENTORY_STAFF,
    ROLES.QC
];

export const INVENTORY_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.INVENTORY_STAFF,
    ROLES.QC
];

export const INVENTORY_OPERATION_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.INVENTORY_STAFF,
    ROLES.QC
];

export const CUSTOMER_RETURN_QC_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.QC
];

export const MARKETING_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER
];

/** Mã giảm giá (voucher): cùng nhóm marketing. */
export const VOUCHER_ROLES: AppRole[] = [...MARKETING_ROLES];

export const CUSTOMER_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER
];

export const PRICE_LIST_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER
];

/** Báo cáo thống kê bán hàng: nhân viên bán tốt nhất, sản phẩm bán chạy. */
export const STATISTIC_REPORT_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

/** Quản lý tài khoản nhân viên (tạo, sửa, khóa, đặt lại mật khẩu). */
export const ACCOUNT_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

export const REPORT_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER
];

export const SETTINGS_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

/** Nhật ký hoạt động người dùng: chỉ quản trị viên. */
export const ACTION_LOG_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

/** Cấu hình số lượng có thể bán và ngưỡng sản xuất: chỉ SUPER_ADMIN. */
export const SELLABLE_CONFIG_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

export const IMPORT_MANAGEMENT_ROLES: AppRole[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER
];

const DEFAULT_ROUTE_BY_ROLE: Record<string, string> = {
    [ROLES.SUPER_ADMIN]: "/system/dashboard",
    [ROLES.MANAGER]: "/system/orders",
    [ROLES.INVENTORY_STAFF]: "/system/inventory/balances",
    [ROLES.QC]: "/management/inventory/disposals",
    [ROLES.SALE_STAFF]: "/pos/sales"
};

const ROLE_PRIORITY: Role[] = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.QC,
    ROLES.INVENTORY_STAFF,
    ROLES.SALE_STAFF
];

export function getDefaultRouteForRoles(
    userRoles: readonly string[] | undefined
) {
    if (!userRoles || userRoles.length === 0) {
        return DEFAULT_ROUTE_BY_ROLE[ROLES.SALE_STAFF];
    }

    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    const matchedRole = ROLE_PRIORITY.find((role) =>
        normalizedUserRoles.has(role)
    );

    return matchedRole
        ? DEFAULT_ROUTE_BY_ROLE[matchedRole]
        : DEFAULT_ROUTE_BY_ROLE[ROLES.SALE_STAFF];
}
