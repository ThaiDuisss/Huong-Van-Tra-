import {
  BarChart3,
  Bell,
  ChevronDown,
  FileText,
  Leaf,
  LogOut,
  Settings,
  ShoppingBag,
  ShoppingCart,
  Tag,
  User
} from "lucide-react";

const navItems = [
  {
    label: "Bán hàng",
    icon: ShoppingBag,
    active: true
  },
  {
    label: "Đơn online",
    icon: ShoppingCart,
    active: false
  },
  {
    label: "Khách hàng",
    icon: User,
    active: false
  },
  {
    label: "Khuyến mãi",
    icon: Tag,
    active: false
  },
  {
    label: "Lịch sử hóa đơn",
    icon: FileText,
    active: false
  },
  {
    label: "Báo cáo nhanh",
    icon: BarChart3,
    active: false
  }
] as const;

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
  return (
    <nav
      className="flex h-dvh min-h-0 flex-col bg-[radial-gradient(circle_at_42%_12%,rgba(53,141,98,0.34),transparent_35%),linear-gradient(180deg,#0a402d_0%,#06351f_100%)] px-3 py-6 text-white shadow-[inset_-1px_0_0_rgba(255,255,255,0.08)]"
      aria-label="Điều hướng POS"
    >
      <div className="mb-8 grid place-items-center px-1 text-center">
        <div
          className="mb-3 grid size-16 place-items-center rounded-[28px] border border-pos-gold/90 text-pos-gold shadow-[0_0_28px_rgba(191,138,45,0.22)]"
          aria-hidden="true"
        >
          <Leaf size={36} strokeWidth={1.8} />
        </div>
        <strong className="text-[13px] leading-tight tracking-[0.12em] text-pos-gold drop-shadow-sm">
          HƯƠNG VÂN TRÀ
        </strong>
        <span className="mt-1 text-[8px] uppercase tracking-[0.16em] text-pos-gold-light">
          Premium tea house
        </span>
      </div>

      <ul className="grid gap-3">
        {navItems.map((item) => {
          const Icon = item.icon;

          return (
            <li key={item.label}>
              <button
                className={`flex min-h-14 w-full items-center gap-3 rounded-xl px-3 text-left text-[15px] font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pos-gold ${
                  item.active
                    ? "bg-white/16 text-white shadow-[inset_0_0_0_1px_rgba(255,255,255,0.08),0_10px_26px_rgba(0,0,0,0.12)]"
                    : "text-white/82 hover:bg-white/10 hover:text-white"
                }`}
                type="button"
                aria-current={item.active ? "page" : undefined}
                aria-label={item.label}
              >
                <span
                  className={`grid size-9 shrink-0 place-items-center rounded-xl ${
                    item.active
                      ? "bg-white/10 text-white"
                      : "text-white/88"
                  }`}
                  aria-hidden="true"
                >
                  <Icon size={24} strokeWidth={1.9} />
                </span>
                <span className="truncate">{item.label}</span>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="mt-auto">
        <section
          className="flex items-center gap-3 pb-6 text-sm"
          aria-label="Người dùng hiện tại"
        >
          <div
            className="grid size-10 shrink-0 place-items-center overflow-hidden rounded-full bg-white text-hvt-green-900 ring-2 ring-white/20"
            aria-hidden="true"
          >
            <span className="font-bold">Đ</span>
          </div>
          <div className="min-w-0">
            <strong className="block truncate">Đỗ Xuân Thái</strong>
            <p className="mt-0.5 text-white/70">Ca sáng</p>
          </div>
          <button
            type="button"
            className="ml-auto grid size-7 shrink-0 place-items-center rounded-lg text-white/75 hover:bg-white/10 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pos-gold"
            aria-label="Mở thông tin ca bán"
          >
            <ChevronDown size={18} aria-hidden="true" />
          </button>
        </section>

        <ul
          className="grid grid-cols-3 gap-2 border-t border-white/15 pt-5"
          aria-label="Tiện ích"
        >
          {utilityItems.map((item) => {
            const Icon = item.icon;

            return (
              <li key={item.label}>
                <button
                  type="button"
                  className="grid size-10 place-items-center rounded-xl text-white/82 transition hover:bg-white/10 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pos-gold"
                  aria-label={item.label}
                  title={item.label}
                >
                  <Icon size={22} strokeWidth={1.9} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </nav>
  );
}
