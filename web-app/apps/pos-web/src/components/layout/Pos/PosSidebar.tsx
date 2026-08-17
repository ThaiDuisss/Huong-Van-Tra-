import {
  ChevronDown,
  LogOut,
  Settings,
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";

import { HvtLogo } from "../../shared/HvtLogo";
import { routePaths } from "../../../routes/path";
import { PosNotificationButton } from "../../../features/notification/components/PosNotificationButton";
import { useAuth } from "../../../auth/auth-context";
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
  const userLabel = displayName ?? username ?? "Người dùng";
  const userInitial = userLabel.trim().charAt(0).toUpperCase() || "U";
  const userMeta = roleLabel ?? "Nhân viên POS";

  function isItemActive(item: PosNavigationItem) {
    if (item.to && location.pathname === item.to) {
      return true;
    }

    return item.children?.some((child) => child.to === location.pathname) ?? false;
  }

  return (
    <nav
      className="flex h-dvh min-h-0 flex-col border-r border-[#edf0f4] bg-white px-4 py-5 text-[#159734] shadow-[10px_0_30px_rgba(35,60,45,0.04)]"
      aria-label="Điều hướng POS"
    >
      <div className="mb-7 grid place-items-center px-1 text-center">
        <HvtLogo />
      </div>

      <ul className="grid gap-3">
        {visibleNavItems.map((item) => {
          const Icon = item.icon;
          const isActive = isItemActive(item);
          const hasChildren = Boolean(item.children?.length);
          const isOpen = openGroup === item.key;

          return (
            <li key={item.key}>
              <button
                className={`flex min-h-[52px] w-full items-center gap-3 rounded-lg px-2 text-left text-[14px] font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600 ${
                  isActive
                    ? "bg-[#f4f5f3] text-[#159734]"
                    : "text-[#159734] hover:bg-[#f4f5f3]"
                }`}
                type="button"
                aria-current={isActive ? "page" : undefined}
                aria-label={item.label}
                aria-expanded={hasChildren ? isOpen : undefined}
                onClick={() => {
                  if (hasChildren) {
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
                <span className="truncate">{item.label}</span>
                {hasChildren ? (
                  <ChevronDown
                    className={`ml-auto transition-transform ${
                      isOpen ? "rotate-180" : ""
                    }`}
                    size={16}
                    aria-hidden="true"
                  />
                ) : null}
              </button>
              {hasChildren && isOpen ? (
                <div className="mt-1 grid gap-1 rounded-lg border border-[#a8e3b7] bg-white p-2">
                  {item.children?.map((child) => {
                    const ChildIcon = child.icon;
                    const childActive = child.to === location.pathname;

                    return (
                      <button
                        key={child.key}
                        type="button"
                        className={`flex h-9 items-center gap-2 rounded-md px-2 text-left text-[13px] font-semibold transition hover:bg-[#f4f5f3] ${
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
                        <ChildIcon size={18} aria-hidden="true" />
                        {child.label}
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>

      <div className="mt-auto">
        <button
          type="button"
          onClick={() => navigate(routePaths.customers)}
          className="mb-5 flex w-full items-center gap-3 text-left"
          aria-label="Chuyển sang quản lý hệ thống"
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
          <span className="min-w-0 flex-1">
            <strong className="block truncate text-[13px] text-[#159734]">
              {userLabel}
            </strong>
            <span className="text-[12px] text-[#159734]">
              {userMeta}
            </span>
          </span>
          <ChevronDown size={14} aria-hidden="true" />
        </button>

        <ul
          className="grid grid-cols-3 gap-2 border-t border-[#edf0f4] pt-5"
          aria-label="Tiện ích"
        >
          <li>
            <PosNotificationButton />
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
