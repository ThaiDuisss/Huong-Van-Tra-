import type { DashboardLocationOption } from "../hooks/useDashboardData";
import type { DashboardFilters } from "../types/dashboard.types";
import { dashboardTimeRangeOptions, defaultDashboardFilters } from "../utils/dashboard-filters";

/**
 * Filter landing chỉ giữ thời gian và kho (tác động thật). Chip áp dụng ngay,
 * Reset chỉ hiện khi khác mặc định. Chip dùng class của StockAlertListPage/InventoryResourceManagement.
 */
export function DashboardFilterBar({
  filters,
  locations,
  onChange,
  showLocation,
  showTimeRange,
}: {
  filters: DashboardFilters;
  locations: DashboardLocationOption[];
  onChange: (next: DashboardFilters) => void;
  showLocation: boolean;
  showTimeRange: boolean;
}) {
  const isDirty =
    filters.timeRange !== defaultDashboardFilters.timeRange ||
    filters.locationId !== defaultDashboardFilters.locationId;

  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
      {showTimeRange ? (
        <fieldset className="flex flex-wrap items-center gap-2">
          <legend className="sr-only">Khoảng thời gian</legend>
          <span className="text-[13px] font-[900] text-[#536078]">Thời gian:</span>
          {dashboardTimeRangeOptions.map((option) => {
            const active = filters.timeRange === option.key;

            return (
              <button
                aria-pressed={active}
                className={`h-[30px] rounded-full border px-3 text-[13px] font-[850] transition-all ${
                  active
                    ? "border-[#159734] bg-[#159734] text-white"
                    : "border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f4f8f5] hover:text-[#172317]"
                }`}
                key={option.key}
                onClick={() => onChange({ ...filters, timeRange: option.key })}
                type="button"
              >
                {option.label}
              </button>
            );
          })}
        </fieldset>
      ) : null}

      {showLocation && locations.length > 0 ? (
        <label className="flex items-center gap-2 text-[13px] font-[900] text-[#536078]">
          Kho:
          <select
            className="h-[34px] min-w-[200px] rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
            onChange={(event) => onChange({ ...filters, locationId: event.currentTarget.value })}
            value={filters.locationId}
          >
            <option value="">Tất cả kho</option>
            {locations.map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      {isDirty ? (
        <button
          className="ml-auto text-[13px] font-black text-[#159734] hover:underline"
          onClick={() => onChange(defaultDashboardFilters)}
          type="button"
        >
          Đặt lại
        </button>
      ) : null}
    </div>
  );
}
