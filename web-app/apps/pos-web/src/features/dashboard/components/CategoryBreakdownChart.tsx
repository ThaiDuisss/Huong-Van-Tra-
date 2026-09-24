import { useMemo, useState } from "react";
import type { DashboardCategoryBreakdown } from "../types/dashboard.types";
import { distributePercentages, formatCompactVnd, formatPercent } from "../utils/dashboard-mappers";

const categoryColors = [
  "bg-[#159734]",
  "bg-[#3b82f6]",
  "bg-[#8b5cf6]",
  "bg-[#f59e0b]",
  "bg-[#ec4899]",
  "bg-[#06b6d4]",
  "bg-[#64748b]",
];

const categoryHexColors = [
  "#159734",
  "#3b82f6",
  "#8b5cf6",
  "#f59e0b",
  "#ec4899",
  "#06b6d4",
  "#64748b",
];

function getArcPath(
  cx: number,
  cy: number,
  rOuter: number,
  rInner: number,
  startAngleDeg: number,
  endAngleDeg: number
): string {
  const sweep = Math.min(359.999, endAngleDeg - startAngleDeg);
  const effectiveEndAngle = startAngleDeg + sweep;

  const startRad = ((startAngleDeg - 90) * Math.PI) / 180;
  const endRad = ((effectiveEndAngle - 90) * Math.PI) / 180;

  const x1 = cx + rOuter * Math.cos(startRad);
  const y1 = cy + rOuter * Math.sin(startRad);
  const x2 = cx + rOuter * Math.cos(endRad);
  const y2 = cy + rOuter * Math.sin(endRad);

  const x3 = cx + rInner * Math.cos(endRad);
  const y3 = cy + rInner * Math.sin(endRad);
  const x4 = cx + rInner * Math.cos(startRad);
  const y4 = cy + rInner * Math.sin(startRad);

  const largeArcFlag = sweep <= 180 ? "0" : "1";

  return [
    `M ${x1.toFixed(3)} ${y1.toFixed(3)}`,
    `A ${rOuter} ${rOuter} 0 ${largeArcFlag} 1 ${x2.toFixed(3)} ${y2.toFixed(3)}`,
    `L ${x3.toFixed(3)} ${y3.toFixed(3)}`,
    `A ${rInner} ${rInner} 0 ${largeArcFlag} 0 ${x4.toFixed(3)} ${y4.toFixed(3)}`,
    `Z`,
  ].join(" ");
}

export function CategoryBreakdownChart({
  categories,
  loading = false,
}: {
  categories: DashboardCategoryBreakdown[];
  loading?: boolean;
}) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const safeCategories = useMemo(() => {
    if (categories.length > 0 && categories.some((c) => c.inventoryValue > 0)) {
      return distributePercentages(categories, (c) => c.inventoryValue, 1);
    }
    return categories;
  }, [categories]);

  const totalVal = safeCategories.reduce((sum, c) => sum + c.inventoryValue, 0);

  if (loading) {
    return <div className="h-48 w-full animate-pulse rounded-xl bg-[#f0f4f1]" />;
  }

  if (safeCategories.length === 0) {
    return (
      <div className="p-6 text-center text-sm font-medium text-[#697684]">
        Chưa có dữ liệu phân bổ theo nhóm sản phẩm.
      </div>
    );
  }

  let accumulatedAngle = 0;
  const sliceAngles = safeCategories.map((cat) => {
    const start = accumulatedAngle;
    const sweep = (cat.percentage / 100) * 360;
    accumulatedAngle += sweep;
    return { end: start + sweep, start };
  });

  const activeCategory = activeIndex !== null ? safeCategories[activeIndex] : null;

  return (
    <div className="grid gap-6 xl:grid-cols-[210px_minmax(0,1fr)] items-center">
      {/* Nửa bên trái: Donut Chart SVG tương tác hover từng phần */}
      <div className="flex flex-col items-center justify-center p-2">
        <div className="relative flex items-center justify-center">
          <svg
            className="h-48 w-48 overflow-visible"
            viewBox="0 0 200 200"
            onMouseLeave={() => setActiveIndex(null)}
          >
            {categories.map((cat, index) => {
              const { start, end } = sliceAngles[index];
              const colorHex = categoryHexColors[index % categoryHexColors.length];
              const isActive = activeIndex === index;
              const isOtherActive = activeIndex !== null && !isActive;

              // Expand outer radius slightly on hover
              const rOuter = isActive ? 88 : 82;
              const rInner = isActive ? 52 : 55;
              const pathD = getArcPath(100, 100, rOuter, rInner, start, end);

              return (
                <path
                  d={pathD}
                  fill={colorHex}
                  key={cat.category}
                  className="cursor-pointer transition-all duration-300 ease-out"
                  style={{
                    filter: isActive ? "drop-shadow(0px 4px 8px rgba(0,0,0,0.2))" : "none",
                    opacity: isOtherActive ? 0.45 : 1,
                    stroke: "#ffffff",
                    strokeWidth: 2,
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                />
              );
            })}
          </svg>

          {/* Tâm Donut Chart linh hoạt hiển thị thông tin khi hover */}
          <div className="absolute pointer-events-none flex h-24 w-24 flex-col items-center justify-center rounded-full bg-white shadow-inner text-center p-1.5 transition-all duration-300">
            {activeCategory ? (
              <>
                <span className="text-[10px] font-bold text-[#697684] truncate max-w-[85px]">
                  {activeCategory.category}
                </span>
                <span className="text-xs font-black text-[#159734] mt-0.5">
                  {formatCompactVnd(activeCategory.inventoryValue)} đ
                </span>
                <span className="text-[10px] font-black text-[#536078]">
                  {formatPercent(activeCategory.percentage)}
                </span>
              </>
            ) : (
              <>
                <span className="text-[10px] font-bold text-[#697684] uppercase tracking-wider">Tổng vốn tồn</span>
                <span className="text-sm font-black text-[#159734] mt-0.5">{formatCompactVnd(totalVal)} đ</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Nửa bên phải: Danh sách nhóm hàng dạng Mini Table 4 cột (Tỷ trọng đứng trước Số lượng & Căn trái) */}
      <div className="divide-y divide-[#edf2ee] min-w-0">
        <div className="grid grid-cols-[1fr_135px_70px_105px] gap-2 pb-2 text-[11px] font-black text-[#697684] uppercase tracking-wider border-b border-[#edf2ee]">
          <span>Nhóm hàng</span>
          <span className="text-left">Tỷ trọng</span>
          <span className="text-right">Số lượng</span>
          <span className="text-right">Giá trị (VNĐ)</span>
        </div>
        {safeCategories.map((cat, index) => {
          const color = categoryColors[index % categoryColors.length];
          const colorHex = categoryHexColors[index % categoryHexColors.length];
          const formattedValue = `${cat.inventoryValue.toLocaleString("vi-VN")} đ`;
          const isActive = activeIndex === index;

          return (
            <div
              className={`grid grid-cols-[1fr_135px_70px_105px] items-center gap-2 py-2.5 text-xs transition cursor-pointer rounded-lg px-1 ${
                isActive ? "bg-[#eef7f0] font-bold" : "hover:bg-[#f7fbf8]"
              }`}
              key={cat.category}
              onMouseEnter={() => setActiveIndex(index)}
              onMouseLeave={() => setActiveIndex(null)}
            >
              {/* Cột 1: Nhóm hàng */}
              <div className="flex items-center gap-2 min-w-0">
                <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${color}`} />
                <div className="truncate grid gap-0.5">
                  <span className="font-black text-[#172317] truncate">{cat.category}</span>
                  <span className="text-[10px] text-[#697684] font-medium">{cat.skuCount} mặt hàng</span>
                </div>
              </div>

              {/* Cột 2: Tỷ trọng (%) - CĂN TRÁI VỚI THANH TIẾN TRÌNH KÉO DÀI */}
              <div className="flex items-center justify-start gap-2 min-w-0">
                <div className="h-2 w-20 overflow-hidden rounded-full bg-neutral-100 hidden sm:block shrink-0 border border-neutral-200/50">
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{ backgroundColor: colorHex, width: `${Math.min(100, cat.percentage)}%` }}
                  />
                </div>
                <span className="font-black text-[#536078] text-[11px] shrink-0">
                  {formatPercent(cat.percentage)}
                </span>
              </div>

              {/* Cột 3: Số lượng (sp) - Căn phải */}
              <div className="text-right font-semibold text-[#172317]">
                {cat.totalQuantity.toLocaleString("vi-VN")} sp
              </div>

              {/* Cột 4: Giá trị (VNĐ) - Căn phải */}
              <div className="text-right font-black text-[#159734]">
                {formattedValue}
              </div>
            </div>
          );
        })}

        {/* Dòng tổng cộng */}
        {safeCategories.length > 0 && (
          <div className="grid grid-cols-[1fr_135px_70px_105px] items-center gap-2 pt-2.5 text-xs font-black text-[#172317] border-t border-[#edf2ee]">
            <span className="text-[#697684]">Tổng cộng</span>
            <span className="text-[11px] font-black text-[#159734]">100,0%</span>
            <span className="text-right font-bold text-[#172317]">
              {safeCategories.reduce((s, c) => s + c.totalQuantity, 0).toLocaleString("vi-VN")} sp
            </span>
            <span className="text-right font-black text-[#159734]">
              {totalVal.toLocaleString("vi-VN")} đ
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
