import { Search } from "lucide-react";

import type { AccountFilters, AccountStatusFilter, StaffRole } from "../types/account.types";
import { STAFF_ROLE_OPTIONS, isDefaultAccountFilters } from "../utils/account.utils";

const fieldClass =
  "h-[34px] w-full rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none placeholder:font-medium placeholder:text-[#9aa5b1] focus:border-[#159734]";
const labelClass = "grid gap-1 text-[12px] font-[900] text-[#536078]";

export function AccountFilterBar({
  filters,
  onChange,
  onReset,
}: {
  filters: AccountFilters;
  onChange: (patch: Partial<AccountFilters>) => void;
  onReset: () => void;
}) {
  return (
    <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card md:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_auto] xl:items-end">
      <label className={labelClass}>
        Tìm kiếm
        <span className="relative">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
            size={15}
          />
          <input
            className={`${fieldClass} pl-8`}
            onChange={(event) => onChange({ keyword: event.currentTarget.value })}
            placeholder="Tên đăng nhập, email hoặc họ tên"
            type="search"
            value={filters.keyword}
          />
        </span>
      </label>
      <label className={labelClass}>
        Vai trò
        <select
          className={fieldClass}
          onChange={(event) => onChange({ role: event.currentTarget.value as StaffRole | "" })}
          value={filters.role}
        >
          <option value="">Tất cả vai trò</option>
          {STAFF_ROLE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label className={labelClass}>
        Trạng thái
        <select
          className={fieldClass}
          onChange={(event) => onChange({ status: event.currentTarget.value as AccountStatusFilter })}
          value={filters.status}
        >
          <option value="">Tất cả</option>
          <option value="ENABLED">Đang hoạt động</option>
          <option value="DISABLED">Đã khóa</option>
        </select>
      </label>
      <button
        className="h-[34px] justify-self-start rounded-lg px-2 text-[13px] font-black text-[#159734] hover:underline disabled:cursor-default disabled:text-[#9aa5b1] disabled:no-underline"
        disabled={isDefaultAccountFilters(filters)}
        onClick={onReset}
        type="button"
      >
        Xóa bộ lọc
      </button>
    </div>
  );
}
