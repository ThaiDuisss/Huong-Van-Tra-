import { Search } from "lucide-react";

import { Button } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
import type { AccountFilters, AccountStatusFilter, StaffRole } from "../types/account.types";
import { STAFF_ROLE_OPTIONS, isDefaultAccountFilters } from "../utils/account.utils";

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
      <Field label="Tìm kiếm">
        <span className="relative">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
            size={15}
          />
          <TextInput
            className="pl-8"
            onChange={(event) => onChange({ keyword: event.currentTarget.value })}
            placeholder="Tên đăng nhập, email hoặc họ tên"
            type="search"
            value={filters.keyword}
          />
        </span>
      </Field>
      <Field label="Vai trò">
        <NativeSelect
          onChange={(event) => onChange({ role: event.currentTarget.value as StaffRole | "" })}
          value={filters.role}
        >
          <option value="">Tất cả vai trò</option>
          {STAFF_ROLE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </NativeSelect>
      </Field>
      <Field label="Trạng thái">
        <NativeSelect
          onChange={(event) => onChange({ status: event.currentTarget.value as AccountStatusFilter })}
          value={filters.status}
        >
          <option value="">Tất cả</option>
          <option value="ENABLED">Đang hoạt động</option>
          <option value="DISABLED">Đã khóa</option>
        </NativeSelect>
      </Field>
      <Button
        className="justify-self-start"
        disabled={isDefaultAccountFilters(filters)}
        onClick={onReset}
        variant="link"
      >
        Xóa bộ lọc
      </Button>
    </div>
  );
}
