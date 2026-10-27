import { createContext, useContext } from "react";

import type { CurrentUser } from "./identity-api";

export type AuthContextValue = {
    initialized: boolean;
    authenticated: boolean;
    user?: CurrentUser;
    userId?: string;
    /** Keycloak subject (JWT sub) của user đang đăng nhập — so sánh với createdBy của dữ liệu backend. */
    keycloakSubject?: string;
    username?: string;
    email?: string;
    fullName?: string;
    displayName?: string;
    avatarUrl?: string;
    shiftName?: string;
    roles?: string[];
    roleLabel?: string;
    /**
     * Permission hiệu lực từ `/identity/me/permissions`. `undefined` khi chưa tải được (dùng role như cũ),
     * mảng (kể cả rỗng) khi backend đã trả lời.
     */
    permissions?: string[];
    permissionsLoaded: boolean;

    login: () => void;
    register: () => void;
    logout: () => Promise<void>;

    hasRealmRole: (role: string) => boolean;
    hasResourceRole: (role: string, resource?: string) => boolean;
    /** Chỉ để ẩn/hiện thao tác; backend luôn kiểm tra lại. */
    hasPermission: (permission: string) => boolean;
    /** Xin token mới từ Keycloak rồi tải lại permission (sau khi đổi vai trò/quyền). */
    refreshPermissions: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
    const ctx = useContext(AuthContext);

    if (!ctx) {
        throw new Error("useAuth must be used inside AuthProvider");
    }

    return ctx;
}
