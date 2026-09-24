import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import type { DashboardLocationSummary } from "../types/dashboard.types";

const defaultLocationRows: DashboardLocationSummary[] = [
  {
    available: 520,
    blocked: 0,
    locationId: "loc-1",
    locationName: "Kệ A-01 (Trà hộp & Set quà)",
    onHand: 580,
    reserved: 60,
  },
  {
    available: 720,
    blocked: 0,
    locationId: "loc-2",
    locationName: "Kệ B-02 (Trà xanh cao cấp & Trà Ô long)",
    onHand: 760,
    reserved: 40,
  },
  {
    available: 24,
    blocked: 0,
    locationId: "loc-3",
    locationName: "Kệ C-01 (Dụng cụ & Ấm chén)",
    onHand: 39,
    reserved: 15,
  },
  {
    available: 620,
    blocked: 0,
    locationId: "loc-4",
    locationName: "Kho phụ (Vật tư & Bao bì)",
    onHand: 620,
    reserved: 0,
  },
];

export function InventoryLocationWidget({
  rows,
  loading = false,
}: {
  rows: DashboardLocationSummary[];
  loading?: boolean;
}) {
  if (loading) {
    return <div className="h-48 w-full animate-pulse rounded-xl bg-[#f0f4f1]" />;
  }

  const displayRows = rows.length > 0 ? rows : defaultLocationRows;

  const totalAvailable = displayRows.reduce((sum, r) => sum + r.available, 0);
  const totalReserved = displayRows.reduce((sum, r) => sum + r.reserved, 0);
  const totalBlocked = displayRows.reduce((sum, r) => sum + r.blocked, 0);

  return (
    <div className="grid gap-3.5 p-1">
      {/* 1. 3 Chip thông số tổng quan */}
      <div className="grid grid-cols-3 gap-2.5">
        <div className="flex flex-col items-center justify-center rounded-xl border border-emerald-200 bg-emerald-50/70 p-2.5 text-center transition hover:bg-emerald-50">
          <span className="flex items-center gap-1 text-[11px] font-black text-emerald-900">
            <span className="size-2 rounded-full bg-[#159734]" /> Khả dụng
          </span>
          <span className="text-base sm:text-lg font-black text-[#159734] mt-0.5">
            {formatVietnamNumber(totalAvailable)}
          </span>
        </div>

        <div className="flex flex-col items-center justify-center rounded-xl border border-amber-200 bg-amber-50/70 p-2.5 text-center transition hover:bg-amber-50">
          <span className="flex items-center gap-1 text-[11px] font-black text-amber-900">
            <span className="size-2 rounded-full bg-amber-500" /> Giữ chỗ
          </span>
          <span className="text-base sm:text-lg font-black text-amber-700 mt-0.5">
            {formatVietnamNumber(totalReserved)}
          </span>
        </div>

        <div className="flex flex-col items-center justify-center rounded-xl border border-red-200 bg-red-50/70 p-2.5 text-center transition hover:bg-red-50">
          <span className="flex items-center gap-1 text-[11px] font-black text-red-900">
            <span className="size-2 rounded-full bg-red-500" /> Khóa / Lỗi
          </span>
          <span className="text-base sm:text-lg font-black text-red-600 mt-0.5">
            {formatVietnamNumber(totalBlocked)}
          </span>
        </div>
      </div>

      {/* 2. Danh sách Tồn theo vị trí kho / Kệ / Khu vực */}
      <div className="mt-1 divide-y divide-[#edf2ee]">
        {displayRows.map((row) => {
          const onHand = row.onHand > 0 ? row.onHand : (row.available + row.reserved + row.blocked);
          const availableWidth = onHand > 0 ? (row.available / onHand) * 100 : 0;
          const reservedWidth = onHand > 0 ? (row.reserved / onHand) * 100 : 0;
          const blockedWidth = onHand > 0 ? (row.blocked / onHand) * 100 : 0;

          return (
            <div key={row.locationId} className="py-2.5 grid gap-1.5 text-xs">
              {/* Dòng trên: Tên khu vực / Kệ -> Căn phải: Tổng số lượng SP */}
              <div className="flex items-center justify-between gap-2">
                <span className="font-black text-[#172317] truncate">{row.locationName}</span>
                <span className="font-black text-[#159734] shrink-0 font-mono text-xs bg-emerald-50 border border-emerald-200/80 px-2 py-0.5 rounded-md">
                  {formatVietnamNumber(onHand)} SP
                </span>
              </div>

              {/* Visual Stacked Mini Bar (Thanh ngang mini phân bổ 3 màu) */}
              <div className="h-2 w-full overflow-hidden rounded-full bg-[#edf0f4] flex border border-neutral-200/50">
                {availableWidth > 0 && (
                  <div
                    className="h-full bg-[#159734] transition-all"
                    style={{ width: `${availableWidth}%` }}
                    title={`Khả dụng: ${formatVietnamNumber(row.available)}`}
                  />
                )}
                {reservedWidth > 0 && (
                  <div
                    className="h-full bg-amber-400 transition-all"
                    style={{ width: `${reservedWidth}%` }}
                    title={`Giữ chỗ: ${formatVietnamNumber(row.reserved)}`}
                  />
                )}
                {blockedWidth > 0 && (
                  <div
                    className="h-full bg-red-500 transition-all"
                    style={{ width: `${blockedWidth}%` }}
                    title={`Khóa/Lỗi: ${formatVietnamNumber(row.blocked)}`}
                  />
                )}
              </div>

              {/* Dòng dưới: Chi tiết phân bổ (x khả dụng / y giữ chỗ) */}
              <div className="flex items-center justify-between text-[11px] font-semibold text-[#697684]">
                <span>
                  ({formatVietnamNumber(row.available)} khả dụng / {formatVietnamNumber(row.reserved)} giữ chỗ
                  {row.blocked > 0 ? ` / ${formatVietnamNumber(row.blocked)} khóa` : ""})
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export const InventoryCapacityWidget = InventoryLocationWidget;

