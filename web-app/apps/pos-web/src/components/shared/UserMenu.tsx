import {
    LayoutDashboard,
    LogOut,
    MonitorCog
} from "lucide-react";

export type WorkspaceKey = "pos" | "system";

interface UserMenuProps {
    activeWorkspace: WorkspaceKey;
    onSelectWorkspace(workspace: WorkspaceKey): void;
    onLogout(): void;
}

const workspaceItems = [
    {
        key: "pos",
        label: "POS bán hàng",
        routeLabel: "/pos/sales",
        icon: LayoutDashboard
    },
    {
        key: "system",
        label: "Quản lý hệ thống",
        routeLabel: "/system/manufacturing/boms",
        icon: MonitorCog
    }
] as const;

export function UserMenu({
    activeWorkspace,
    onSelectWorkspace,
    onLogout
}: UserMenuProps) {
    return (
        <div
            className="absolute bottom-[calc(100%+12px)] left-0 right-0 z-30 rounded-2xl border border-white/15 bg-white p-2 text-pos-ink shadow-[0_18px_48px_rgba(0,0,0,0.24)]"
            role="menu"
            aria-label="Chuyển workspace"
        >
            <div className="grid gap-1">
                {workspaceItems.map((item) => {
                    const Icon = item.icon;
                    const active = item.key === activeWorkspace;

                    return (
                        <button
                            key={item.key}
                            type="button"
                            role="menuitem"
                            onClick={() =>
                                onSelectWorkspace(item.key)
                            }
                            className={`grid w-full grid-cols-[32px_minmax(0,1fr)] items-center gap-3 rounded-xl px-3 py-2.5 text-left transition ${
                                active
                                    ? "bg-hvt-green-50 text-hvt-green-900"
                                    : "hover:bg-neutral-100"
                            }`}
                        >
                            <span
                                className={`grid size-8 place-items-center rounded-lg ${
                                    active
                                        ? "bg-hvt-green-100"
                                        : "bg-neutral-100"
                                }`}
                                aria-hidden="true"
                            >
                                <Icon size={18} />
                            </span>
                            <span className="min-w-0">
                                <span className="block truncate text-sm font-bold">
                                    {item.label}
                                </span>
                                <span className="block truncate text-xs text-pos-muted">
                                    {item.routeLabel}
                                </span>
                            </span>
                        </button>
                    );
                })}
            </div>

            <div className="mt-2 border-t border-pos-border pt-2">
                <button
                    type="button"
                    role="menuitem"
                    onClick={onLogout}
                    className="grid w-full grid-cols-[32px_minmax(0,1fr)] items-center gap-3 rounded-xl px-3 py-2.5 text-left text-red-700 transition hover:bg-red-50"
                >
                    <span
                        className="grid size-8 place-items-center rounded-lg bg-red-50"
                        aria-hidden="true"
                    >
                        <LogOut size={18} />
                    </span>
                    <span className="text-sm font-bold">
                        Đăng xuất
                    </span>
                </button>
            </div>
        </div>
    );
}
