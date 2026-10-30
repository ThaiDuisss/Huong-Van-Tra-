import { ChevronDown, MapPin, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";

import { useAuth } from "../../../auth/auth-context";
import { routePaths } from "../../../routes/path";
import { HvtLogo } from "../../shared/HvtLogo";
import { useSidebarCollapsed } from "../useSidebarCollapsed";
import {
    filterSystemNavigationByRoles,
    getFirstNavigationPath,
    isNavigationItemActive,
    type SystemNavigationItem
} from "./system-navigation";

function getActiveKeys(items: readonly SystemNavigationItem[], pathname: string) {
    const keys: string[] = [];

    for (const item of items) {
        if (!isNavigationItemActive(item, pathname)) {
            continue;
        }

        keys.push(item.key);

        if (item.children) {
            keys.push(...getActiveKeys(item.children, pathname));
        }

        break;
    }

    return keys;
}

type SidebarItemProps = {
    item: SystemNavigationItem;
    level: number;
    /** Menu thu gọn: chỉ hiện icon của mục cấp 1, rê chuột để xem tên. */
    collapsed: boolean;
    openKeys: Set<string>;
    pathname: string;
    onNavigate(to: string): void;
    onToggle(item: SystemNavigationItem): void;
};

function SidebarItem({
    item,
    level,
    collapsed,
    onNavigate,
    onToggle,
    openKeys,
    pathname
}: SidebarItemProps) {
    const Icon = item.icon;
    const hasChildren = Boolean(item.children?.length);
    const isOpen = openKeys.has(item.key);
    const isActive = isNavigationItemActive(item, pathname);
    const itemPath = getFirstNavigationPath(item);
    const levelClasses =
        level === 0
            ? collapsed
                ? "h-10 justify-center px-0 text-[13px]"
                : "min-h-9 px-2 py-1.5 text-[13px]"
            : level === 1
                ? "h-8 px-2 text-[12px]"
                : "h-8 px-2 text-[12px]";
    const stateClasses = isActive
        ? level === 2
            ? "bg-[#f1fbf3] text-[#159734]"
            : "bg-[#edf8ef] text-[#159734]"
        : level === 0
            ? "text-[#159734] hover:bg-[#f4faf5]"
            : "text-[#315f3c] hover:bg-[#f4faf5] hover:text-[#159734]";

    if (!hasChildren && item.to) {
        return (
            <NavLink
                to={item.to}
                title={collapsed ? item.label : undefined}
                aria-label={collapsed ? item.label : undefined}
                className={({ isActive: navActive }) =>
                    `flex w-full items-center gap-2 rounded-lg text-left font-semibold transition ${levelClasses} ${
                        navActive || isActive
                            ? "bg-[#f1fbf3] text-[#159734]"
                            : stateClasses
                    }`
                }
            >
                {level < 2 ? (
                    <Icon size={level === 0 ? 17 : 14} strokeWidth={1.9} aria-hidden="true" />
                ) : (
                    <span className="size-1.5 shrink-0 rounded-full bg-current" aria-hidden="true" />
                )}
                {collapsed ? null : <span className="min-w-0 flex-1 truncate">{item.label}</span>}
            </NavLink>
        );
    }

    return (
        <>
            <button
                type="button"
                onClick={() => {
                    if (hasChildren) {
                        onToggle(item);
                        return;
                    }

                    if (itemPath) {
                        onNavigate(itemPath);
                    }
                }}
                className={`flex w-full items-center gap-2 rounded-lg text-left font-semibold transition ${levelClasses} ${stateClasses}`}
                aria-expanded={hasChildren ? isOpen : undefined}
                aria-label={collapsed ? item.label : undefined}
                title={collapsed ? item.label : undefined}
            >
                <Icon className="shrink-0" size={level === 0 ? 17 : 14} strokeWidth={1.9} aria-hidden="true" />
                {collapsed ? null : <span className="min-w-0 flex-1 truncate">{item.label}</span>}
                {hasChildren && !collapsed ? (
                    <ChevronDown
                        className={`shrink-0 transition-transform ${isOpen ? "rotate-180" : ""}`}
                        size={14}
                        aria-hidden="true"
                    />
                ) : null}
            </button>

            {hasChildren && isOpen && !collapsed ? (
                <ul
                    className={`mt-1 grid gap-1 border-l border-[#d8eadc] ${
                        level === 0 ? "ml-4 pl-3" : "ml-3 pl-2"
                    }`}
                >
                    {item.children?.map((child) => (
                        <li key={child.key}>
                            <SidebarItem
                                item={child}
                                level={level + 1}
                                collapsed={false}
                                onNavigate={onNavigate}
                                onToggle={onToggle}
                                openKeys={openKeys}
                                pathname={pathname}
                            />
                        </li>
                    ))}
                </ul>
            ) : null}
        </>
    );
}

export function SystemSidebar() {
    const location = useLocation();
    const navigate = useNavigate();
    const { avatarUrl, displayName, permissions, roleLabel, roles, username } = useAuth();
    const visibleNavigationItems = useMemo(
        () => filterSystemNavigationByRoles(roles, permissions),
        [permissions, roles]
    );
    const activeKeys = useMemo(
        () => getActiveKeys(visibleNavigationItems, location.pathname),
        [location.pathname, visibleNavigationItems]
    );
    const [openKeys, setOpenKeys] = useState<Set<string>>(() => new Set(activeKeys));
    const effectiveOpenKeys = useMemo(
        () => new Set([...openKeys, ...activeKeys]),
        [activeKeys, openKeys]
    );
    const userLabel = displayName ?? username ?? "Người dùng";
    const userInitial = userLabel.trim().charAt(0).toUpperCase() || "U";
    const [collapsed, setCollapsed] = useSidebarCollapsed("hvt.system.sidebarCollapsed");

    function handleToggle(item: SystemNavigationItem) {
        // Thu gọn thì không đủ chỗ cho nhóm con: mở rộng menu rồi mở nhóm.
        if (collapsed) {
            setCollapsed(false);
            setOpenKeys((current) => new Set([...current, item.key]));
            return;
        }

        setOpenKeys((current) => {
            const next = new Set(current);

            if (next.has(item.key)) {
                next.delete(item.key);
            } else {
                if (item.children && visibleNavigationItems.some((root) => root.key === item.key)) {
                    visibleNavigationItems.forEach((root) => {
                        if (root.key !== item.key) {
                            next.delete(root.key);
                        }
                    });
                }

                next.add(item.key);
            }

            return next;
        });
    }

    return (
        <aside
            className={`flex h-dvh min-h-0 shrink-0 flex-col overflow-hidden border-r border-[#e8eef0] bg-white py-4 text-[#159734] shadow-[8px_0_30px_rgba(25,70,45,0.05)] transition-[width] ${
                collapsed ? "w-[68px] px-2" : "w-[228px] px-3 xl:w-[244px]"
            }`}
        >
            <div className={`shrink-0 ${collapsed ? "mb-3 grid place-items-center" : "mb-4 px-1"}`}>
                <HvtLogo compact={false} mark={collapsed} />
            </div>

            <button
                type="button"
                className={`mb-3 flex h-8 shrink-0 items-center gap-2 rounded-lg text-[12px] font-semibold text-[#5f7a66] transition hover:bg-[#f4faf5] hover:text-[#159734] ${
                    collapsed ? "justify-center" : "px-2"
                }`}
                onClick={() => setCollapsed(!collapsed)}
                aria-label={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
                title={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
            >
                {collapsed ? (
                    <PanelLeftOpen size={17} aria-hidden="true" />
                ) : (
                    <>
                        <PanelLeftClose size={17} aria-hidden="true" />
                        <span>Thu gọn</span>
                    </>
                )}
            </button>

            <nav
                className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden pr-1"
                aria-label="Điều hướng quản lý hệ thống"
            >
                <ul className="grid gap-1.5">
                    {visibleNavigationItems.map((item) => (
                        <li key={item.key}>
                            <SidebarItem
                                item={item}
                                level={0}
                                collapsed={collapsed}
                                onNavigate={navigate}
                                onToggle={handleToggle}
                                openKeys={effectiveOpenKeys}
                                pathname={location.pathname}
                            />
                        </li>
                    ))}
                </ul>
            </nav>

            <div className="mt-3 shrink-0 border-t border-[#edf0f4] pt-3">
                <button
                    type="button"
                    onClick={() => navigate(routePaths.posSales)}
                    className={`flex w-full items-center gap-2.5 rounded-lg px-1.5 py-2 text-left hover:bg-[#f4faf5] ${
                        collapsed ? "justify-center" : ""
                    }`}
                    aria-label="Chuyển về POS bán hàng"
                    title={collapsed ? userLabel : undefined}
                >
                    <span className="grid size-8 shrink-0 place-items-center overflow-hidden rounded-full bg-[linear-gradient(135deg,#f8c9b5,#182438)] text-xs font-bold text-white">
                        {avatarUrl ? (
                            <img
                                src={avatarUrl}
                                alt=""
                                className="size-full object-cover"
                                aria-hidden="true"
                            />
                        ) : (
                            userInitial
                        )}
                    </span>
                    {collapsed ? null : (
                        <>
                            <span className="min-w-0 flex-1">
                                <strong className="block truncate text-[12px] text-[#315f3c]">
                                    {userLabel}
                                </strong>
                                <span className="block truncate text-[11px] text-[#5f7a66]">
                                    {roleLabel ?? "Người dùng"}
                                </span>
                            </span>
                            <ChevronDown className="shrink-0" size={13} aria-hidden="true" />
                        </>
                    )}
                </button>

                <div
                    className={`mt-2 flex items-center gap-2 rounded-lg px-1.5 py-1.5 text-[11px] font-semibold text-[#315f3c] ${
                        collapsed ? "justify-center" : ""
                    }`}
                    title={collapsed ? "Cửa hàng trung tâm" : undefined}
                >
                    <MapPin className="shrink-0" size={14} aria-hidden="true" />
                    {collapsed ? null : (
                        <span className="min-w-0 flex-1 truncate">
                            Cửa hàng trung tâm
                        </span>
                    )}
                </div>
            </div>
        </aside>
    );
}
