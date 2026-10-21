import { apiClient } from "../api/api-client";

/**
 * Permission = client role của client API trên Keycloak, tên dạng `module:action`.
 * Danh sách này chỉ để code FE tham chiếu tên ổn định; nguồn thật là `/identity/me/permissions`.
 * FE chỉ dùng để ẩn/hiện thao tác; backend luôn kiểm tra lại.
 */
export const PERMISSIONS = {
    ORDER_VIEW: "order:view",
    ORDER_CREATE: "order:create",
    ORDER_CANCEL: "order:cancel",
    ORDER_RETURN: "order:return",
    ORDER_REFUND: "order:refund",
    INVENTORY_VIEW: "inventory:view",
    INVENTORY_RECEIVE: "inventory:receive",
    INVENTORY_APPROVE: "inventory:approve",
    INVENTORY_ADJUST: "inventory:adjust",
    INVENTORY_TRANSFER: "inventory:transfer",
    PRODUCT_VIEW: "product:view",
    PRODUCT_MANAGE: "product:manage",
    PRODUCTION_VIEW: "production:view",
    PRODUCTION_MANAGE: "production:manage",
    CUSTOMER_VIEW: "customer:view",
    CUSTOMER_MANAGE: "customer:manage",
    PROMOTION_MANAGE: "promotion:manage",
    REPORT_VIEW: "report:view",
    USER_MANAGE: "user:manage",
    ROLE_MANAGE: "role:manage",
    PERMISSION_MANAGE: "permission:manage"
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS] | (string & {});

export interface MyPermissionsResponse {
    userId: string;
    roles: string[];
    permissions: string[];
    /** "keycloak" = lấy tươi từ Keycloak, "token" = tạm dùng claim trong access token. */
    source: "keycloak" | "token";
}

export const permissionApi = {
    myPermissions() {
        return apiClient.get<MyPermissionsResponse>("/identity/me/permissions");
    }
};

/**
 * Quy tắc hiển thị: khi đã tải được danh sách permission thì chỉ permission được tính;
 * chưa tải được (API lỗi, backend cũ) thì `undefined` nghĩa là "chưa biết" và caller dùng role như trước.
 */
export function hasPermission(permissions: readonly string[] | undefined, permission: string): boolean {
    return Array.isArray(permissions) && permissions.includes(permission);
}

export function hasAnyPermission(
    permissions: readonly string[] | undefined,
    required: readonly string[] | undefined
): boolean | undefined {
    if (!required || required.length === 0) return true;
    if (!Array.isArray(permissions)) return undefined;
    return required.some((permission) => permissions.includes(permission));
}
