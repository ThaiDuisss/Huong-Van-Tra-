import {
  Bell,
  BarChart3,
  BriefcaseBusiness,
  ChevronDown,
  Clock3,
  FileClock,
  FileText,
  LogOut,
  Settings,
  ShoppingBag,
  Warehouse
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";

import { HvtLogo } from "../../shared/HvtLogo";
import { routePaths } from "../../../routes/path";

const navItems = [
  {
    label: "POS",
    icon: BriefcaseBusiness,
    to: routePaths.posSales
  },
  {
    label: "Tồn kho hiện tại",
    icon: Warehouse,
    to: routePaths.inventoryBalances
  },
  {
    label: "Đơn hàng",
    icon: ShoppingBag,
    to: null
  },
  {
    label: "Ca làm việc",
    icon: Clock3,
    to: null
  }
];

const utilityItems = [
  {
    label: "Thông báo",
    icon: Bell
  },
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

  return (
    <nav
      className="flex h-dvh min-h-0 flex-col border-r border-[#edf0f4] bg-white px-4 py-5 text-[#159734] shadow-[10px_0_30px_rgba(35,60,45,0.04)]"
      aria-label="Điều hướng POS"
    >
      <div className="mb-7 grid place-items-center px-1 text-center">
        <HvtLogo />
      </div>

      <ul className="grid gap-3">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = item.to ? location.pathname === item.to : false;

          return (
            <li key={item.label}>
              <button
                className={`flex min-h-[52px] w-full items-center gap-3 rounded-lg px-2 text-left text-[14px] font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600 ${
                  isActive
                    ? "bg-[#f4f5f3] text-[#159734]"
                    : "text-[#159734] hover:bg-[#f4f5f3]"
                }`}
                type="button"
                aria-current={isActive ? "page" : undefined}
                aria-label={item.label}
                onClick={() => {
                  if (item.to) {
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
                {item.label === "Đơn hàng" ? (
                  <ChevronDown
                    className="ml-auto"
                    size={16}
                    aria-hidden="true"
                  />
                ) : null}
              </button>
              {item.label === "Đơn hàng" ? (
                <div className="mt-1 grid gap-1 rounded-lg border border-[#a8e3b7] bg-white p-2">
                  <button
                    type="button"
                    className="flex h-9 items-center gap-2 rounded-md px-2 text-left text-[13px] font-semibold text-[#159734] hover:bg-[#f4f5f3]"
                  >
                    <FileClock size={18} aria-hidden="true" />
                    Đơn đang giữ
                  </button>
                  <button
                    type="button"
                    className="flex h-9 items-center gap-2 rounded-md px-2 text-left text-[13px] font-semibold text-[#159734] hover:bg-[#f4f5f3]"
                  >
                    <FileText size={18} aria-hidden="true" />
                    Đơn trong ca
                  </button>
                  <button
                    type="button"
                    className={`flex h-9 items-center gap-2 rounded-md px-2 text-left text-[13px] font-semibold transition hover:bg-[#f4f5f3] ${
                      location.pathname === routePaths.salesHistory
                        ? "bg-[#f4f5f3] text-[#159734]"
                        : "text-[#159734]"
                    }`}
                    aria-current={
                      location.pathname === routePaths.salesHistory
                        ? "page"
                        : undefined
                    }
                    onClick={() => navigate(routePaths.salesHistory)}
                  >
                    <BarChart3 size={18} aria-hidden="true" />
                    Bán hàng hôm nay
                  </button>
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
          <span className="block size-9 overflow-hidden rounded-full bg-[linear-gradient(135deg,#f4c4b1,#1f2937)]" />
          <span className="min-w-0 flex-1">
            <strong className="block truncate text-[13px] text-[#159734]">
              Đỗ Xuân Thái
            </strong>
            <span className="text-[12px] text-[#159734]">Ca sáng</span>
          </span>
          <ChevronDown size={14} aria-hidden="true" />
        </button>

        <ul
          className="grid grid-cols-3 gap-2 border-t border-[#edf0f4] pt-5"
          aria-label="Tiện ích"
        >
          {utilityItems.map((item) => {
            const Icon = item.icon;

            return (
              <li key={item.label}>
                <button
                  type="button"
                  className="grid size-9 place-items-center rounded-lg text-[#159734] transition hover:bg-[#f4f5f3] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600"
                  aria-label={item.label}
                  title={item.label}
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
