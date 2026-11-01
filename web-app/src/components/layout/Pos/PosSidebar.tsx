import {
  ChevronDown,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";

import { HvtLogo } from "../../shared/HvtLogo";
import { PosNotificationButton } from "../../../features/notification/components/PosNotificationButton";
import { OfflineSyncButton } from "../../../features/sales/components/offline/OfflineSyncButton";
import { useAuth } from "../../../auth/auth-context";
import { useSidebarCollapsed } from "../useSidebarCollapsed";
import { BACKOFFICE_ROLES, getDefaultRouteForRoles, hasAnyRole } from "../../../auth/role-utils";
import {
  filterPosNavigationByRoles,
  posNavigationItems,
  type PosNavigationItem
} from "./pos-navigation";

const utilityItems = [
  {
    label: "Cài đặt",
    icon: Settings
  },
  {
    label: "Đăng xuất",
    icon: LogOut
  }
] as const;

export function PosSidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { avatarUrl, displayName, logout, roleLabel, roles, username } = useAuth();
  const visibleNavItems = useMemo(
    () => filterPosNavigationByRoles(posNavigationItems, roles),
    [roles]
  );
  const [openGroup, setOpenGroup] = useState<string | null>("orders");
  const [collapsed, setCollapsed] = useSidebarCollapsed("hvt.pos.sidebarCollapsed");
  // Nhân viên bán hàng không có quyền vào khu quản lý: không dẫn sang để khỏi gặp trang "không có quyền".
  const canOpenBackoffice = hasAnyRole(roles, BACKOFFICE_ROLES);
  const userLabel = displayName ?? username ?? "Người dùng";
  const userInitial = userLabel.trim().charAt(0).toUpperCase() || "U";
  const userMeta = roleLabel ?? "Nhân viên POS";

  const currentPath = `${location.pathname}${location.search}`;

  function isItemActive(item: PosNavigationItem) {
    if (item.to && currentPath === item.to) {
      return true;
    }

    return item.children?.some((child) => child.to === currentPath) ?? false;
  }

  return (
    <nav
      className={`flex h-dvh min-h-0 flex-col overflow-hidden border-r border-[#edf0f4] bg-white py-5 text-[#159734] shadow-[10px_0_30px_rgba(35,60,45,0.04)] transition-[width] ${
        collapsed ? "w-[72px] px-2" : "w-[176px] px-3 xl:w-[184px] 2xl:w-[192px]"
      }`}
      aria-label="Điều hướng POS"
    >
      <div className={`grid place-items-center text-center ${collapsed ? "mb-3" : "mb-4 px-1"}`}>
        <HvtLogo mark={collapsed} />
      </div>

      <button
        type="button"
        className={`mb-3 flex h-8 items-center gap-2 rounded-lg text-[12px] font-semibold text-[#5f7a66] transition hover:bg-[#f4f5f3] hover:text-[#159734] ${
          collapsed ? "justify-center" : "px-2"
        }`}
        onClick={() => setCollapsed(!collapsed)}
        aria-label={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
        title={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
      >
        {collapsed ? (
          <PanelLeftOpen size={18} aria-hidden="true" />
        ) : (
          <>
            <PanelLeftClose size={18} aria-hidden="true" />
            <span>Thu gọn</span>
          </>
        )}
      </button>

      <ul className="grid min-h-0 gap-1.5 overflow-y-auto overflow-x-hidden">
        {visibleNavItems.map((item) => {
          const Icon = item.icon;
          const isActive = isItemActive(item);
          const hasChildren = Boolean(item.children?.length);
          const isOpen = openGroup === item.key;

          return (
            <li key={item.key}>
              <button
                className={`flex min-h-[46px] w-full items-center gap-2.5 rounded-lg text-left text-[14px] font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600 ${
                  collapsed ? "justify-center px-0" : "px-2"
                } ${
                  isActive
                    ? "bg-[#f4f5f3] text-[#159734]"
                    : "text-[#159734] hover:bg-[#f4f5f3]"
                }`}
                type="button"
                aria-current={isActive ? "page" : undefined}
                aria-label={item.label}
                title={collapsed ? item.label : undefined}
                aria-expanded={hasChildren ? isOpen : undefined}
                onClick={() => {
                  if (hasChildren) {
                    // Thu gọn thì không đủ chỗ cho nhóm con: mở rộng menu rồi mở nhóm.
                    if (collapsed) {
                      setCollapsed(false);
                      setOpenGroup(item.key);
                      return;
                    }

                    setOpenGroup((current) =>
                      current === item.key ? null : item.key
                    );
                  } else if (item.to) {
                    navigate(item.to);
                  }
                }}
              >
                <span
                  className="grid size-8 shrink-0 place-items-center text-[#159734]"
                  aria-hidden="true"
                >
                  <Icon size={22} strokeWidth={1.8} />
                </span>
                {collapsed ? null : (
                  <span className="line-clamp-2 min-w-0 flex-1 leading-snug">{item.label}</span>
                )}
                {hasChildren && !collapsed ? (
                  <ChevronDown
                    className={`ml-auto shrink-0 transition-transform ${
                      isOpen ? "rotate-180" : ""
                    }`}
                    size={16}
                    aria-hidden="true"
                  />
                ) : null}
              </button>
              {hasChildren && isOpen && !collapsed ? (
                <div className="mt-1 grid gap-1 rounded-lg border border-[#a8e3b7] bg-white p-2">
                  {item.children?.map((child) => {
                    const ChildIcon = child.icon;
                    const childActive = child.to === currentPath;

                    return (
                      <button
                        key={child.key}
                        type="button"
                        className={`flex min-h-9 w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] font-semibold leading-snug transition hover:bg-[#f4f5f3] ${
                          childActive
                            ? "bg-[#f4f5f3] text-[#159734]"
                            : "text-[#159734]"
                        }`}
                        aria-current={childActive ? "page" : undefined}
                        onClick={() => {
                          if (child.to) {
                            navigate(child.to);
                          }
                        }}
                      >
                        <ChildIcon className="shrink-0" size={18} aria-hidden="true" />
                        <span className="line-clamp-2 min-w-0 flex-1">{child.label}</span>
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>

      <div className="mt-auto pt-3">
        <button
          type="button"
          disabled={!canOpenBackoffice}
          onClick={() => navigate(getDefaultRouteForRoles(roles))}
          className={`mb-4 flex w-full items-center gap-3 text-left disabled:cursor-default ${collapsed ? "justify-center" : ""}`}
          aria-label={canOpenBackoffice ? "Chuyển sang quản lý hệ thống" : userLabel}
          title={collapsed ? userLabel : undefined}
        >
          <span className="grid size-9 shrink-0 place-items-center overflow-hidden rounded-full bg-[linear-gradient(135deg,#f4c4b1,#1f2937)] text-sm font-bold text-white">
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
                <strong className="block truncate text-[13px] text-[#159734]">
                  {userLabel}
                </strong>
                <span className="block truncate text-[12px] text-[#159734]">
                  {userMeta}
                </span>
              </span>
              {canOpenBackoffice ? (
                <ChevronDown className="shrink-0" size={14} aria-hidden="true" />
              ) : null}
            </>
          )}
        </button>

        <ul
          className={`grid gap-2 border-t border-[#edf0f4] pt-4 ${
            collapsed ? "grid-cols-1 justify-items-center" : "grid-cols-4"
          }`}
          aria-label="Tiện ích"
        >
          <li>
            <PosNotificationButton />
          </li>
          <li>
            <OfflineSyncButton />
          </li>
          {utilityItems.map((item) => {
            const Icon = item.icon;

            return (
              <li key={item.label}>
                <button
                  type="button"
                  className="grid size-9 place-items-center rounded-lg text-[#159734] transition hover:bg-[#f4f5f3] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600"
                  aria-label={item.label}
                  title={item.label}
                  onClick={() => {
                    if (item.label === "Đăng xuất") {
                      void logout();
                    }
                  }}
                >
                  <Icon size={21} strokeWidth={1.9} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </nav>
  );
}
