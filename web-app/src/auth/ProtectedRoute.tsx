import { type ReactNode } from "react";
import { LogIn, ShieldAlert } from "lucide-react";

import { HvtLogo } from "../components/shared/HvtLogo";
import { useAuth } from "./auth-context";
import { getDefaultRouteForRoles, hasAnyRole, ROLES } from "./role-utils";

type ProtectedRouteProps = {
    children: ReactNode;
    roles?: string[];
    /**
     * Permission yêu cầu (bất kỳ cái nào). Khi backend đã trả danh sách permission thì permission quyết định;
     * chưa tải được thì dùng `roles` như trước để không khóa màn hình trên môi trường cũ.
     */
    permissions?: string[];
};

type AuthStatePageProps = {
    description: string;
    icon: ReactNode;
    primaryAction: {
        label: string;
        onClick(): void;
    };
    secondaryAction?: {
        label: string;
        onClick(): void;
    };
    title: string;
};

function AuthStatePage({
    description,
    icon,
    primaryAction,
    secondaryAction,
    title
}: AuthStatePageProps) {
    return (
        <main className="grid min-h-dvh place-items-center bg-[#f7f8fb] px-4 py-10 text-[#172317]">
            <section className="w-full max-w-[460px] rounded-xl border border-[#e8eef0] bg-white p-6 text-center shadow-[0_24px_70px_rgba(25,70,45,0.10)]">
                <div className="mb-5 flex justify-center">
                    <HvtLogo compact />
                </div>
                <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-[#fff6ed] text-[#b45309]">
                    {icon}
                </div>
                <h1 className="text-xl font-black text-[#172317]">
                    {title}
                </h1>
                <p className="mx-auto mt-2 max-w-[340px] text-sm font-semibold leading-6 text-[#5f6b7a]">
                    {description}
                </p>
                <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
                    <button
                        type="button"
                        onClick={primaryAction.onClick}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white transition hover:bg-[#0f7f2b] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159734]"
                    >
                        {primaryAction.label}
                    </button>
                    {secondaryAction ? (
                        <button
                            type="button"
                            onClick={secondaryAction.onClick}
                            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] transition hover:bg-[#f4faf5] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159734]"
                        >
                            {secondaryAction.label}
                        </button>
                    ) : null}
                </div>
            </section>
        </main>
    );
}

export function ProtectedRoute({ children, roles = [], permissions = [] }: ProtectedRouteProps) {
    const auth = useAuth();

    if (!auth.initialized) {
        return (
            <main className="grid min-h-dvh place-items-center bg-[#f7f8fb] text-sm font-bold text-[#315f3c]">
                Đang khởi tạo đăng nhập...
            </main>
        );
    }

    if (!auth.authenticated) {
        return (
            <AuthStatePage
                description="Vui lòng đăng nhập bằng tài khoản Hương Vân Trà để tiếp tục sử dụng hệ thống."
                icon={<LogIn size={26} strokeWidth={2} aria-hidden="true" />}
                primaryAction={{
                    label: "Đăng nhập",
                    onClick: auth.login
                }}
                title="Bạn cần đăng nhập"
            />
        );
    }

    if (roles.length > 0 || permissions.length > 0) {
        const superAdmin = hasAnyRole(auth.roles, [ROLES.SUPER_ADMIN]);
        // Danh sách permission rỗng = chưa bootstrap: dùng role như cũ.
        const permissionDecision =
            permissions.length > 0 && Array.isArray(auth.permissions) && auth.permissions.length > 0
                ? permissions.some((permission) => auth.permissions?.includes(permission))
                : undefined;
        const hasRequiredRole =
            superAdmin ||
            (permissionDecision ??
                (hasAnyRole(auth.roles, roles) ||
                    roles.some((role) => auth.hasRealmRole(role))));

        if (!hasRequiredRole) {
            return (
                <AuthStatePage
                    description="Tài khoản hiện tại chưa được phân quyền cho màn hình này. Bạn có thể quay lại trang trước hoặc đăng xuất để dùng tài khoản khác."
                    icon={<ShieldAlert size={28} strokeWidth={2} aria-hidden="true" />}
                    primaryAction={{
                        label: "Quay lại",
                        onClick: () => {
                            if (window.history.length > 1) {
                                window.history.back();
                                return;
                            }

                            window.location.assign(
                                getDefaultRouteForRoles(auth.roles)
                            );
                        }
                    }}
                    secondaryAction={{
                        label: "Đăng xuất",
                        onClick: () => void auth.logout()
                    }}
                    title="Bạn không có quyền truy cập"
                />
            );
        }
    }

    return <>{children}</>;
}
