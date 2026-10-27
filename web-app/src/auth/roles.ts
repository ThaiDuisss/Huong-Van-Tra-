/** Vai trò nghiệp vụ duy nhất của hệ thống — khớp enum Role ở backend (common). Mọi chỗ dùng role phải dùng ROLES. */
export const ROLES = {
    SUPER_ADMIN: "ROLE_SUPER_ADMIN",
    MANAGER: "ROLE_MANAGER",
    INVENTORY_STAFF: "ROLE_INVENTORY_STAFF",
    SALE_STAFF: "ROLE_SALE_STAFF",
    QC: "ROLE_QC"
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES] | keyof typeof ROLES;

/** Tên role không tiền tố ROLE_ (SUPER_ADMIN, MANAGER, ...) — dùng cho type nghiệp vụ và dữ liệu backend trả về. */
export type RoleKey = keyof typeof ROLES;

export const ROLE_LABELS: Record<RoleKey, string> = {
    SUPER_ADMIN: "Quản trị hệ thống",
    MANAGER: "Quản lý",
    INVENTORY_STAFF: "Nhân viên kho",
    SALE_STAFF: "Nhân viên bán hàng",
    QC: "Kiểm soát chất lượng"
};

/** Tên role cũ / biến thể → role chuẩn (khớp Role.fromName ở backend). */
export const ROLE_ALIASES: Record<string, keyof typeof ROLES> = {
    ADMIN: "SUPER_ADMIN",
    SUPERADMIN: "SUPER_ADMIN",
    BRANCH_MANAGER: "MANAGER",
    INVENTORY_MANAGER: "MANAGER",
    DIRECTOR: "MANAGER",
    WAREHOUSE: "INVENTORY_STAFF",
    WAREHOUSE_STAFF: "INVENTORY_STAFF",
    QC_STAFF: "QC",
    SALES_STAFF: "SALE_STAFF",
    POS_STAFF: "SALE_STAFF",
    CASHIER: "SALE_STAFF",
    STAFF: "SALE_STAFF"
};
