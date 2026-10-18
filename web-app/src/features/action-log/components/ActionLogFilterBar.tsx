import { ChevronDown, Search, SlidersHorizontal } from "lucide-react";
import { useState } from "react";

import type { ActionLogFilters, ActionLogTimeRange } from "../types/action-log.types";
import { ACTION_LOG_SERVICES, countAdvancedFilters, isDefaultFilters } from "../utils/action-log.utils";

const timeRangeOptions: Array<{ value: ActionLogTimeRange; label: string }> = [
  { value: "TODAY", label: "Hôm nay" },
  { value: "SEVEN_DAYS", label: "7 ngày" },
  { value: "THIRTY_DAYS", label: "30 ngày" },
  { value: "ALL", label: "Tất cả" },
  { value: "CUSTOM", label: "Tùy chọn" },
];

const fieldClass =
  "h-[34px] w-full rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none placeholder:font-medium placeholder:text-[#9aa5b1] focus:border-[#159734]";
const labelClass = "grid gap-1 text-[12px] font-[900] text-[#536078]";

export function ActionLogFilterBar({
  filters,
  onChange,
  onReset,
}: {
  filters: ActionLogFilters;
  onChange: (patch: Partial<ActionLogFilters>) => void;
  onReset: () => void;
}) {
  const advancedCount = countAdvancedFilters(filters);
  const [expanded, setExpanded] = useState(false);
  // Luôn mở khi đang có bộ lọc nâng cao (kể cả khi được áp từ màn chi tiết) để không có bộ lọc ẩn.
  const showAdvanced = expanded || advancedCount > 0;

  return (
    <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
        <fieldset className="flex flex-wrap items-center gap-2">
          <legend className="sr-only">Khoảng thời gian</legend>
          <span className="text-[13px] font-[900] text-[#536078]">Thời gian:</span>
          {timeRangeOptions.map((option) => {
            const active = filters.timeRange === option.value;
            return (
              <button
                aria-pressed={active}
                className={`h-[30px] rounded-full border px-3 text-[13px] font-[850] transition-all ${
                  active
                    ? "border-[#159734] bg-[#159734] text-white"
                    : "border-pos-border bg-white text-[#536078] hover:border-[#159734] hover:text-[#159734]"
                }`}
                key={option.value}
                onClick={() => onChange({ timeRange: option.value })}
                type="button"
              >
                {option.label}
              </button>
            );
          })}
        </fieldset>

        {filters.timeRange === "CUSTOM" ? (
          <div className="flex flex-wrap items-center gap-2 text-[13px] font-[900] text-[#536078]">
            <label className="flex items-center gap-2">
              Từ
              <input
                className={`${fieldClass} w-[200px]`}
                max={filters.customTo || undefined}
                onChange={(event) => onChange({ customFrom: event.currentTarget.value })}
                type="datetime-local"
                value={filters.customFrom}
              />
            </label>
            <label className="flex items-center gap-2">
              đến
              <input
                className={`${fieldClass} w-[200px]`}
                min={filters.customFrom || undefined}
                onChange={(event) => onChange({ customTo: event.currentTarget.value })}
                type="datetime-local"
                value={filters.customTo}
              />
            </label>
          </div>
        ) : null}

        {!isDefaultFilters(filters) ? (
          <button className="ml-auto text-[13px] font-black text-[#159734] hover:underline" onClick={onReset} type="button">
            Xóa bộ lọc
          </button>
        ) : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[1.3fr_1.3fr_1fr_1fr_auto] xl:items-end">
        <label className={labelClass}>
          Hành động
          <span className="relative">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
              size={15}
            />
            <input
              className={`${fieldClass} pl-8`}
              onChange={(event) => onChange({ action: event.currentTarget.value })}
              placeholder="VD: ORDER_CANCEL, PRODUCT"
              type="search"
              value={filters.action}
            />
          </span>
        </label>
        <label className={labelClass}>
          Người thực hiện
          <input
            className={fieldClass}
            onChange={(event) => onChange({ username: event.currentTarget.value })}
            placeholder="Tên đăng nhập"
            type="search"
            value={filters.username}
          />
        </label>
        <label className={labelClass}>
          Phân hệ
          <select
            className={fieldClass}
            onChange={(event) => onChange({ service: event.currentTarget.value })}
            value={filters.service}
          >
            <option value="">Tất cả phân hệ</option>
            {ACTION_LOG_SERVICES.map((service) => (
              <option key={service.value} value={service.value}>
                {service.label}
              </option>
            ))}
          </select>
        </label>
        <label className={labelClass}>
          Kết quả
          <select
            className={fieldClass}
            onChange={(event) => onChange({ status: event.currentTarget.value })}
            value={filters.status}
          >
            <option value="">Tất cả</option>
            <option value="SUCCESS">Thành công</option>
            <option value="FAIL">Thất bại</option>
          </select>
        </label>
        <button
          aria-controls="action-log-advanced-filters"
          aria-expanded={showAdvanced}
          className="inline-flex h-[34px] items-center justify-center gap-2 rounded-lg border border-pos-border bg-white px-3 text-[13px] font-black text-[#536078] transition hover:border-[#159734] hover:text-[#159734]"
          onClick={() => setExpanded((value) => !value)}
          type="button"
        >
          <SlidersHorizontal size={15} aria-hidden="true" />
          Nâng cao
          {advancedCount > 0 ? (
            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[#159734] px-1.5 text-[11px] text-white">
              {advancedCount}
            </span>
          ) : null}
          <ChevronDown className={`transition-transform ${showAdvanced ? "rotate-180" : ""}`} size={15} aria-hidden="true" />
        </button>
      </div>

      {showAdvanced ? (
        <div className="grid gap-3 border-t border-[#edf0f4] pt-3 md:grid-cols-2 xl:grid-cols-4" id="action-log-advanced-filters">
          <label className={labelClass}>
            Loại đối tượng
            <input
              className={fieldClass}
              onChange={(event) => onChange({ entityType: event.currentTarget.value })}
              placeholder="VD: Order, Product"
              type="search"
              value={filters.entityType}
            />
          </label>
          <label className={labelClass}>
            Mã đối tượng
            <input
              className={`${fieldClass} font-mono`}
              onChange={(event) => onChange({ entityId: event.currentTarget.value })}
              placeholder="ID đơn hàng, sản phẩm..."
              type="search"
              value={filters.entityId}
            />
          </label>
          <label className={labelClass}>
            Mã request
            <input
              className={`${fieldClass} font-mono`}
              onChange={(event) => onChange({ requestId: event.currentTarget.value })}
              placeholder="requestId"
              type="search"
              value={filters.requestId}
            />
          </label>
          <label className={labelClass}>
            Mã người dùng
            <input
              className={`${fieldClass} font-mono`}
              onChange={(event) => onChange({ userId: event.currentTarget.value })}
              placeholder="userId (khớp chính xác)"
              type="search"
              value={filters.userId}
            />
          </label>
        </div>
      ) : null}
    </div>
  );
}
