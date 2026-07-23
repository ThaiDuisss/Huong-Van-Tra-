import { type ReactNode } from "react";
import { useAuth } from "./auth-context";

type ProtectedRouteProps = {
    children: ReactNode;
    roles?: string[];
};

export function ProtectedRoute({ children, roles = [] }: ProtectedRouteProps) {
    const auth = useAuth();

    if (!auth.initialized) {
        return <div>Đang khởi tạo đăng nhập...</div>;
    }

    if (!auth.authenticated) {
        return (
            <div>
                <h2>Bạn cần đăng nhập</h2>
                <button onClick={auth.login}>Đăng nhập</button>
            </div>
        );
    }

    if (roles.length > 0) {
        const hasRequiredRole = roles.some((role) => auth.hasRealmRole(role));

        if (!hasRequiredRole) {
            return <div>Bạn không có quyền truy cập trang này.</div>;
        }
    }

    return <>{children}</>;
}
