import { useState, useMemo, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Search, Layers, AlertCircle, Clock, PackageCheck, RefreshCw } from "lucide-react";
import { Button, IconButton } from "../../../components/ui/Button";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { isDashboardMockEnabled } from "../../dashboard/services/dashboard.config";
import { mockInventoryData } from "../mocks/stock-analysis.mock";

/* ═══════════════════════════════════════════════════════════════════════════
   1. TYPES & COLOR SYSTEM
   ═══════════════════════════════════════════════════════════════════════════ */

export interface RawInventoryRecord {
  sku: string;
  name: string;
  category: string;
  onHand: number;
  reserved: number;
  blocked: number;
  warehouse: string;
  unit: string;
}

export interface InventoryItemWithAvailable extends RawInventoryRecord {
  available: number;
}

export interface CategoryColorConfig {
  name: string;
  accentHex: string;
  bg: string;
  text: string;
  border: string;
  dot: string;
}

/**
 * Hệ thống màu sắc Category đồng bộ giữa Filter Pills, Biểu đồ và Bảng dữ liệu:
 * - Category 1 (Nguyên liệu chính/Trà): Accent xanh lá (#10b981)
 * - Category 2 (Thảo mộc/Hoa): Accent tím (#a855f7)
 * - Category 3 (Bao bì/Đóng gói): Accent xanh dương (#3b82f6)
 * - Category 4 (Tem nhãn): Accent cam đậm (#f97316)
 * - Category 5 (Phụ liệu): Accent đỏ hồng (#ec4899)
 */
const PRESET_CATEGORY_COLORS: Record<string, CategoryColorConfig> = {
  "Nguyên liệu chính": {
    name: "Nguyên liệu chính",
    accentHex: "#10b981",
    bg: "bg-emerald-50",
    text: "text-emerald-700",
    border: "border-emerald-200",
    dot: "bg-emerald-500",
  },
  "Thảo mộc & Hoa": {
    name: "Thảo mộc & Hoa",
    accentHex: "#a855f7",
    bg: "bg-purple-50",
    text: "text-purple-700",
    border: "border-purple-200",
    dot: "bg-purple-500",
  },
  "Bao bì & Đóng gói": {
    name: "Bao bì & Đóng gói",
    accentHex: "#3b82f6",
    bg: "bg-blue-50",
    text: "text-blue-700",
    border: "border-blue-200",
    dot: "bg-blue-500",
  },
  "Tem nhãn": {
    name: "Tem nhãn",
    accentHex: "#f97316",
    bg: "bg-orange-50",
    text: "text-orange-700",
    border: "border-orange-200",
    dot: "bg-orange-500",
  },
  "Phụ liệu": {
    name: "Phụ liệu",
    accentHex: "#ec4899",
    bg: "bg-pink-50",
    text: "text-pink-700",
    border: "border-pink-200",
    dot: "bg-pink-500",
  },
};

const DYNAMIC_PALETTES: Omit<CategoryColorConfig, "name">[] = [
  { accentHex: "#10b981", bg: "bg-emerald-50", text: "text-emerald-700", border: "border-emerald-200", dot: "bg-emerald-500" },
  { accentHex: "#a855f7", bg: "bg-purple-50", text: "text-purple-700", border: "border-purple-200", dot: "bg-purple-500" },
  { accentHex: "#3b82f6", bg: "bg-blue-50", text: "text-blue-700", border: "border-blue-200", dot: "bg-blue-500" },
  { accentHex: "#f97316", bg: "bg-orange-50", text: "text-orange-700", border: "border-orange-200", dot: "bg-orange-500" },
  { accentHex: "#ec4899", bg: "bg-pink-50", text: "text-pink-700", border: "border-pink-200", dot: "bg-pink-500" },
  { accentHex: "#06b6d4", bg: "bg-cyan-50", text: "text-cyan-700", border: "border-cyan-200", dot: "bg-cyan-500" },
  { accentHex: "#eab308", bg: "bg-amber-50", text: "text-amber-700", border: "border-amber-200", dot: "bg-amber-500" },
  { accentHex: "#6366f1", bg: "bg-indigo-50", text: "text-indigo-700", border: "border-indigo-200", dot: "bg-indigo-500" },
  { accentHex: "#14b8a6", bg: "bg-teal-50", text: "text-teal-700", border: "border-teal-200", dot: "bg-teal-500" },
];

function getCategoryStyle(category: string): CategoryColorConfig {
  if (PRESET_CATEGORY_COLORS[category]) {
    return PRESET_CATEGORY_COLORS[category];
  }

  // Keyword match
  const lower = category.toLowerCase();
  if (lower.includes("nguyên liệu") || lower.includes("trà")) {
    return { name: category, ...DYNAMIC_PALETTES[0] };
  }
  if (lower.includes("thảo mộc") || lower.includes("hoa")) {
    return { name: category, ...DYNAMIC_PALETTES[1] };
  }
  if (lower.includes("bao bì") || lower.includes("đóng gói") || lower.includes("hộp") || lower.includes("túi")) {
    return { name: category, ...DYNAMIC_PALETTES[2] };
  }
  if (lower.includes("tem") || lower.includes("nhãn") || lower.includes("decal")) {
    return { name: category, ...DYNAMIC_PALETTES[3] };
  }
  if (lower.includes("phụ liệu") || lower.includes("dụng cụ") || lower.includes("gia vị")) {
    return { name: category, ...DYNAMIC_PALETTES[4] };
  }

  // Deterministic palette pick
  let hash = 0;
  for (let i = 0; i < category.length; i++) {
    hash = (hash * 31 + category.charCodeAt(i)) >>> 0;
  }
  const palette = DYNAMIC_PALETTES[hash % DYNAMIC_PALETTES.length];
  return { name: category, ...palette };
}

/* ═══════════════════════════════════════════════════════════════════════════
   2. HELPER FUNCTIONS
   ═══════════════════════════════════════════════════════════════════════════ */

function fmtNum(n: number): string {
  return n.toLocaleString("vi-VN");
}

function fmtCompactTick(n: number): string {
  if (n === 0) return "0";
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1)}k`;
  return String(n);
}

function computeScaleAndTicks(maxVal: number): { maxScale: number; ticks: number[] } {
  if (maxVal <= 0) {
    return { maxScale: 100, ticks: [0, 20, 40, 60, 80, 100] };
  }

  const targetTicks = 6;
  const roughStep = maxVal / targetTicks;
  const magnitude = Math.pow(10, Math.max(0, Math.floor(Math.log10(roughStep))));
  const normalized = roughStep / magnitude;

  let step = magnitude;
  if (normalized > 5) step = 10 * magnitude;
  else if (normalized > 2) step = 5 * magnitude;
  else if (normalized > 1) step = 2 * magnitude;

  const ticks: number[] = [0];
  let curr = step;
  while (curr < maxVal || ticks.length < 5) {
    ticks.push(curr);
    curr += step;
  }
  ticks.push(curr);
  return { maxScale: curr, ticks };
}

/** Tạo SVG path với 2 đầu góc ngoài bên phải được bo tròn (rounded-r) */
function getRoundedRightBarPath(x: number, y: number, w: number, h: number, r: number = 4): string {
  if (w <= 0) return "";
  const radius = Math.min(r, w, h / 2);
  return [
    `M ${x} ${y}`,
    `h ${Math.max(0, w - radius)}`,
    `a ${radius} ${radius} 0 0 1 ${radius} ${radius}`,
    `v ${Math.max(0, h - 2 * radius)}`,
    `a ${radius} ${radius} 0 0 1 -${radius} ${radius}`,
    `h -${Math.max(0, w - radius)}`,
    `Z`,
  ].join(" ");
}

/* ═══════════════════════════════════════════════════════════════════════════
   3. MAIN COMPONENT
   ═══════════════════════════════════════════════════════════════════════════ */

export function StockCategoryAnalysisPage() {
  const navigate = useNavigate();

  // Data fetching states
  const [items, setItems] = useState<RawInventoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // State lọc & tương tác
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [hoveredBarIndex, setHoveredBarIndex] = useState<number | null>(null);

  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let ignore = false;

    async function fetchBalances() {
      try {
        const res = await inventoryBalanceApi.view({ page: 0, size: 250 });
        if (ignore) return;

        const content = res?.content ?? [];
        if (content.length > 0) {
          const mapped: RawInventoryRecord[] = content.map((b) => {
            const onHand = Number(b.quantityOnHand ?? 0);
            const reserved = Number(b.quantityReserved ?? 0);
            const available = Number(b.availableQuantity ?? Math.max(0, onHand - reserved));
            const blocked = Math.max(0, onHand - reserved - available);

            return {
              sku: b.productCode || b.code || "SKU-N/A",
              name: b.productName || b.name || "Chưa có tên",
              category: b.categoryName || "Khác",
              onHand,
              reserved,
              blocked,
              warehouse: b.locationName || "Kho trung tâm",
              unit: b.unitSymbol || b.unitName || "đơn vị",
            };
          });
          setItems(mapped);
          setError(null);
        } else if (isDashboardMockEnabled) {
          setItems(mockInventoryData);
          setError(null);
        } else {
          setItems([]);
          setError(null);
        }
      } catch {
        if (ignore) return;
        if (isDashboardMockEnabled) {
          setItems(mockInventoryData);
          setError(null);
        } else {
          setError("Không thể nạp dữ liệu tồn kho từ máy chủ. Vui lòng kiểm tra lại kết nối.");
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    void fetchBalances();

    return () => {
      ignore = true;
    };
  }, [refreshKey]);

  const handleRefresh = useCallback(() => {
    setLoading(true);
    setRefreshKey((k) => k + 1);
  }, []);

  // 1. Tự động tính toán available = onHand - reserved - blocked cho toàn bộ dữ liệu
  const computedAllData = useMemo<InventoryItemWithAvailable[]>(() => {
    return items.map((item) => ({
      ...item,
      available: Math.max(0, item.onHand - item.reserved - item.blocked),
    }));
  }, [items]);

  // Danh mục duy nhất có trong dữ liệu
  const availableCategories = useMemo(() => {
    const map = new Map<string, number>();
    computedAllData.forEach((i) => {
      map.set(i.category, (map.get(i.category) ?? 0) + 1);
    });
    return Array.from(map.entries()).map(([name, count]) => ({ name, count }));
  }, [computedAllData]);

  // 2. Lọc dữ liệu theo Category Pill và Search Input (SKU hoặc Tên)
  const filteredData = useMemo<InventoryItemWithAvailable[]>(() => {
    return computedAllData
      .filter((item) => {
        if (selectedCategory && item.category !== selectedCategory) {
          return false;
        }
        if (searchQuery.trim()) {
          const q = searchQuery.trim().toLowerCase();
          return item.sku.toLowerCase().includes(q) || item.name.toLowerCase().includes(q);
        }
        return true;
      })
      .sort((a, b) => b.available - a.available);
  }, [computedAllData, selectedCategory, searchQuery]);

  // 3. Dữ liệu Top 10 mặt hàng có tồn khả dụng cao nhất cho biểu đồ
  const top10Data = useMemo(() => filteredData.slice(0, 10), [filteredData]);

  // Thống kê nhanh KPI
  const stats = useMemo(() => {
    const totalItems = filteredData.length;
    const totalOnHand = filteredData.reduce((acc, cur) => acc + cur.onHand, 0);
    const totalReserved = filteredData.reduce((acc, cur) => acc + cur.reserved, 0);
    const totalBlocked = filteredData.reduce((acc, cur) => acc + cur.blocked, 0);
    const totalAvailable = filteredData.reduce((acc, cur) => acc + cur.available, 0);
    return { totalItems, totalOnHand, totalReserved, totalBlocked, totalAvailable };
  }, [filteredData]);

  // Tính scale và ticks thích ứng động
  const maxAvailableInTop = useMemo(() => {
    if (top10Data.length === 0) return 100;
    return Math.max(...top10Data.map((d) => d.available), 10);
  }, [top10Data]);

  const { maxScale, ticks: TICKS } = useMemo(() => {
    return computeScaleAndTicks(maxAvailableInTop);
  }, [maxAvailableInTop]);

  // Kích thước SVG
  const BAR_HEIGHT = 22;
  const BAR_GAP = 14;
  const ROW_HEIGHT = BAR_HEIGHT + BAR_GAP; // 36px
  const LABEL_AREA = 110;
  const CHART_WIDTH = 640;
  const SVG_W = 880;
  const chartBodyHeight = Math.max(80, top10Data.length * ROW_HEIGHT);
  const SVG_H = chartBodyHeight + 45;

  return (
    <div className="min-h-screen bg-slate-50/60 p-6 space-y-5">
      {/* ── Header trang & Breadcrumbs ── */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div className="flex items-center gap-3">
          <IconButton
            aria-label="Quay lại Dashboard"
            onClick={() => navigate(-1)}
            title="Quay lại Dashboard"
            variant="secondary"
          >
            <ArrowLeft size={16} />
          </IconButton>
          <div>
            <div className="flex items-center gap-2 text-xs font-medium text-slate-400 mb-0.5">
              <span>Dashboard</span>
              <span>/</span>
              <span>Báo cáo kho</span>
              <span>/</span>
              <span className="text-slate-700 font-semibold">Cơ cấu danh mục</span>
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
              <Layers className="size-5 text-emerald-600" />
              Quản lý chi tiết tồn kho
              <span className="text-sm font-normal text-slate-500">
                (Inventory Stock &amp; Category Analysis View)
              </span>
            </h1>
          </div>
        </div>

        {/* Quick summary metrics & Reload */}
        <div className="flex items-center gap-3 flex-wrap text-xs">
          <IconButton
            aria-label="Làm mới dữ liệu"
            disabled={loading}
            onClick={handleRefresh}
            title="Làm mới dữ liệu"
            variant="secondary"
          >
            <RefreshCw size={14} className={loading ? "animate-spin text-emerald-600" : ""} />
          </IconButton>

          <div className="bg-white border border-slate-200 rounded-lg px-3 py-1.5 shadow-xs flex items-center gap-2">
            <PackageCheck className="size-3.5 text-emerald-600" />
            <span className="text-slate-500">Khả dụng:</span>
            <span className="font-bold text-emerald-600">{fmtNum(stats.totalAvailable)}</span>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg px-3 py-1.5 shadow-xs flex items-center gap-2">
            <Clock className="size-3.5 text-amber-500" />
            <span className="text-slate-500">Giữ chỗ:</span>
            <span className="font-bold text-amber-600">{fmtNum(stats.totalReserved)}</span>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg px-3 py-1.5 shadow-xs flex items-center gap-2">
            <AlertCircle className="size-3.5 text-rose-500" />
            <span className="text-slate-500">Lỗi/Chặn:</span>
            <span className="font-bold text-rose-600">{fmtNum(stats.totalBlocked)}</span>
          </div>
        </div>
      </div>

      {/* Thông báo lỗi nếu có */}
      {error && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-700 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0 text-rose-500" />
            <span>{error}</span>
          </div>
          <Button onClick={handleRefresh} size="sm">
            Thử lại
          </Button>
        </div>
      )}

      {/* ────────────────────────────────────────────────────────────────────────
          [PHẦN 1] THANH LỌC DANH MỤC TRÊN CÙNG (Category Filter Pills Bar)
          ──────────────────────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center gap-2.5">
        {/* Nút Tất cả */}
        <button
          onClick={() => setSelectedCategory(null)}
          type="button"
          className={`rounded-full px-3.5 py-1.5 text-xs font-medium border flex items-center gap-2 transition-colors cursor-pointer ${
            selectedCategory === null
              ? "bg-slate-900 text-white border-slate-900 shadow-xs"
              : "bg-white text-slate-600 border-slate-200 hover:border-slate-300 hover:bg-slate-50"
          }`}
        >
          <span>Tất cả</span>
          <span
            className={`text-[10px] px-1.5 py-0.2 rounded-full ${
              selectedCategory === null ? "bg-slate-700 text-slate-200" : "bg-slate-100 text-slate-500"
            }`}
          >
            {computedAllData.length}
          </span>
        </button>

        {/* Các nút Category động từ dữ liệu thực tế */}
        {availableCategories.map(({ name: catName, count }) => {
          const isActive = selectedCategory === catName;
          const colors = getCategoryStyle(catName);

          return (
            <button
              key={catName}
              onClick={() => setSelectedCategory(isActive ? null : catName)}
              type="button"
              className={`rounded-full px-3.5 py-1.5 text-xs font-medium border flex items-center gap-2 transition-colors cursor-pointer ${
                isActive
                  ? `${colors.bg} ${colors.text} ${colors.border} shadow-xs font-semibold`
                  : "bg-white text-slate-600 border-slate-200 hover:border-slate-300 hover:bg-slate-50"
              }`}
            >
              <span className={`w-2 h-2 rounded-full shrink-0 ${colors.dot}`} />
              <span>{catName}</span>
              <span
                className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                  isActive ? "bg-white/80 text-current font-bold" : "bg-slate-100 text-slate-500"
                }`}
              >
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {/* ────────────────────────────────────────────────────────────────────────
          [PHẦN 2] CARD BIỂU ĐỒ TOP TỒN KHẢ DỤNG (Horizontal Bar Chart Card)
          ──────────────────────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-slate-200 p-5 mb-5 shadow-xs">
        <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
          <h2 className="font-semibold text-slate-800 text-base">
            Top 10 Nguyên liệu — Tồn khả dụng{" "}
            <span className="text-slate-400 font-normal text-sm">
              (phân màu theo category)
            </span>
          </h2>

          {/* Legend danh mục */}
          <div className="flex items-center gap-3 text-[11px] text-slate-500 flex-wrap">
            {availableCategories.map(({ name: c }) => {
              const style = getCategoryStyle(c);
              return (
                <span key={c} className="inline-flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${style.dot}`} />
                  {c}
                </span>
              );
            })}
          </div>
        </div>

        {loading ? (
          <div className="py-12 flex flex-col items-center justify-center gap-2 text-slate-400">
            <RefreshCw className="size-6 animate-spin text-emerald-600" />
            <span className="text-xs font-medium">Đang tải dữ liệu tồn kho...</span>
          </div>
        ) : top10Data.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-400 font-medium">
            Không tìm thấy dữ liệu mặt hàng phù hợp với bộ lọc hiện tại.
          </div>
        ) : (
          <div className="w-full overflow-x-auto">
            <svg
              viewBox={`0 0 ${SVG_W} ${SVG_H}`}
              className="w-full min-w-[700px] h-auto select-none"
              role="img"
              aria-label="Biểu đồ Top 10 nguyên liệu có tồn khả dụng cao nhất"
            >
              {/* Vạch lưới gióng mờ & X-axis tick labels */}
              {TICKS.map((tick) => {
                const x = LABEL_AREA + (tick / maxScale) * CHART_WIDTH;
                return (
                  <g key={tick}>
                    {/* Vạch gióng mờ (border-slate-100) */}
                    <line
                      x1={x}
                      y1={0}
                      x2={x}
                      y2={chartBodyHeight}
                      stroke="#f1f5f9"
                      strokeDasharray="4 3"
                      strokeWidth="1"
                    />
                    {/* Tick label trục hoành */}
                    <text
                      x={x}
                      y={chartBodyHeight + 20}
                      textAnchor="middle"
                      fill="#94a3b8"
                      fontSize="11"
                      fontFamily="ui-monospace, monospace"
                    >
                      {fmtCompactTick(tick)}
                    </text>
                  </g>
                );
              })}

              {/* Trục tung Y-axis dọc */}
              <line
                x1={LABEL_AREA}
                y1={0}
                x2={LABEL_AREA}
                y2={chartBodyHeight}
                stroke="#cbd5e1"
                strokeWidth="1.5"
              />

              {/* Các thanh Bar & Nhãn SKU */}
              {top10Data.map((item, index) => {
                const y = index * ROW_HEIGHT;
                const barY = y + (ROW_HEIGHT - BAR_HEIGHT) / 2;
                const barWidth = Math.max(4, (item.available / maxScale) * CHART_WIDTH);
                const colors = getCategoryStyle(item.category);
                const isHovered = hoveredBarIndex === index;
                const barPath = getRoundedRightBarPath(LABEL_AREA, barY, barWidth, BAR_HEIGHT, 4);

                return (
                  <g
                    key={`${item.sku}-${item.warehouse}`}
                    onMouseEnter={() => setHoveredBarIndex(index)}
                    onMouseLeave={() => setHoveredBarIndex(null)}
                    className="cursor-pointer"
                  >
                    {/* Trục tung (Y-axis): Danh sách mã SKU/Code Top 10 */}
                    <text
                      x={LABEL_AREA - 10}
                      y={barY + BAR_HEIGHT / 2 + 4}
                      textAnchor="end"
                      fill={isHovered ? "#1e293b" : "#64748b"}
                      fontSize="12"
                      fontWeight={isHovered ? "700" : "500"}
                      fontFamily="ui-monospace, monospace"
                      className="transition-colors duration-150"
                    >
                      {item.sku}
                    </text>

                    {/* Thanh Bar với rounded-r bo góc 2 đầu ngoài và màu sắc category */}
                    <path
                      d={barPath}
                      fill={colors.accentHex}
                      opacity={isHovered ? 0.9 : 1}
                      style={{
                        filter: isHovered ? "drop-shadow(0 2px 4px rgba(0,0,0,0.15))" : "none",
                        transition: "all 0.15s ease-out",
                      }}
                    />

                    {/* Giá trị hiển thị ngay phía sau thanh bar */}
                    <text
                      x={LABEL_AREA + barWidth + 8}
                      y={barY + BAR_HEIGHT / 2 + 4}
                      fill={isHovered ? colors.accentHex : "#64748b"}
                      fontSize="11"
                      fontWeight={isHovered ? "700" : "500"}
                      fontFamily="ui-monospace, monospace"
                      className="transition-colors duration-150"
                    >
                      {fmtNum(item.available)} {item.unit}
                    </text>

                    {/* Tooltip gốc SVG title fallback */}
                    <title>
                      {`${item.sku} — ${item.name} (${item.category})\nTồn khả dụng: ${fmtNum(
                        item.available
                      )} ${item.unit}\n(On Hand: ${fmtNum(item.onHand)} - Reserved: ${fmtNum(
                        item.reserved
                      )} - Blocked: ${fmtNum(item.blocked)})`}
                    </title>
                  </g>
                );
              })}
            </svg>
          </div>
        )}
      </div>

      {/* ────────────────────────────────────────────────────────────────────────
          [PHẦN 3] BẢNG DỮ LIỆU CHI TIẾT TỒN KHO (Detailed Inventory Table Card)
          ──────────────────────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        {/* Toolbar trên cùng của bảng */}
        <div className="p-4 flex items-center justify-between border-b border-slate-100 flex-wrap gap-3">
          {/* Bên trái: Ô Search Input kèm icon kính lúp */}
          <div className="relative w-72 sm:w-80">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400 pointer-events-none" />
            <input
              type="text"
              placeholder="Tìm kiếm theo SKU hoặc tên..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-8 py-2 bg-white border border-slate-200 rounded-lg text-xs text-slate-800 focus:outline-none focus:border-slate-400 focus:ring-1 focus:ring-slate-400 transition placeholder-slate-400"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs p-1 cursor-pointer"
                title="Xóa tìm kiếm"
              >
                ✕
              </button>
            )}
          </div>

          {/* Bên phải: Text nhỏ hiển thị tổng số dòng */}
          <div className="flex items-center gap-2">
            {selectedCategory && (
              <Button onClick={() => setSelectedCategory(null)} size="sm" variant="ghost">
                Bỏ lọc danh mục
              </Button>
            )}
            <span className="text-xs text-slate-400 font-medium shrink-0">
              {filteredData.length} dòng
            </span>
          </div>
        </div>

        {/* Cấu trúc Table */}
        <div className="w-full overflow-x-auto">
          <Table className="whitespace-nowrap">
            {/* Thead: Viền kẻ dưới mảnh, chữ in hoa xám nhạt */}
            <TableHead>
              <TableRow>
                <TableHeaderCell className="w-12 text-center">
                  #
                </TableHeaderCell>
                <TableHeaderCell className="min-w-[200px]">
                  SKU / TÊN
                </TableHeaderCell>
                <TableHeaderCell className="min-w-[150px]">
                  CATEGORY
                </TableHeaderCell>
                <TableHeaderCell className="text-right min-w-[110px]">
                  ON HAND
                </TableHeaderCell>
                <TableHeaderCell className="text-right min-w-[100px]">
                  RESERVED
                </TableHeaderCell>
                <TableHeaderCell className="text-right min-w-[100px]">
                  BLOCKED
                </TableHeaderCell>
                <TableHeaderCell className="text-right min-w-[190px]">
                  AVAILABLE
                </TableHeaderCell>
                <TableHeaderCell className="text-center min-w-[90px]">
                  KHO
                </TableHeaderCell>
                <TableHeaderCell className="text-center min-w-[70px]">
                  ĐVT
                </TableHeaderCell>
              </TableRow>
            </TableHead>

            <tbody>
              {loading ? (
                <TableRow>
                  <TableCell className="py-12 text-center text-slate-400 text-xs font-medium" colSpan={9}>
                    <div className="flex items-center justify-center gap-2">
                      <RefreshCw className="size-4 animate-spin text-emerald-600" />
                      <span>Đang nạp dữ liệu chi tiết...</span>
                    </div>
                  </TableCell>
                </TableRow>
              ) : filteredData.length === 0 ? (
                <TableRow>
                  <TableCell className="py-12 text-center text-slate-400 text-sm font-medium" colSpan={9}>
                    Không tìm thấy nguyên liệu phù hợp với tìm kiếm
                  </TableCell>
                </TableRow>
              ) : (
                filteredData.map((item, index) => {
                  const colors = getCategoryStyle(item.category);
                  const rank = index + 1;

                  return (
                    <TableRow
                      key={`${item.sku}-${item.warehouse}-${index}`}
                      className="hover:bg-slate-50/80 transition-colors"
                    >
                      {/* Cột #: Thứ tự (badge số tròn cho Top 1, 2, 3) */}
                      <TableCell className="text-center">
                        {rank === 1 && (
                          <span
                            className="inline-flex items-center justify-center size-5 rounded-full bg-amber-100 text-amber-800 font-bold border border-amber-300 text-[11px] shadow-xs mx-auto"
                            title="Top 1 Tồn khả dụng"
                          >
                            1
                          </span>
                        )}
                        {rank === 2 && (
                          <span
                            className="inline-flex items-center justify-center size-5 rounded-full bg-slate-200 text-slate-700 font-bold border border-slate-300 text-[11px] shadow-xs mx-auto"
                            title="Top 2 Tồn khả dụng"
                          >
                            2
                          </span>
                        )}
                        {rank === 3 && (
                          <span
                            className="inline-flex items-center justify-center size-5 rounded-full bg-orange-100 text-orange-800 font-bold border border-orange-300 text-[11px] shadow-xs mx-auto"
                            title="Top 3 Tồn khả dụng"
                          >
                            3
                          </span>
                        )}
                        {rank > 3 && (
                          <span className="text-slate-400 font-mono text-xs">
                            {rank}
                          </span>
                        )}
                      </TableCell>

                      {/* Cột SKU / TÊN: SKU in đậm, Tên hiển thị nhỏ ngay bên dưới */}
                      <TableCell>
                        <div className="font-mono font-bold text-slate-800 text-xs">
                          {item.sku}
                        </div>
                        <div
                          className="text-slate-400 text-[11px] mt-0.5 max-w-[220px] truncate"
                          title={item.name}
                        >
                          {item.name}
                        </div>
                      </TableCell>

                      {/* Cột CATEGORY: Badge pill nhỏ bo tròn kèm chấm tròn màu đại diện */}
                      <TableCell>
                        <span
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-medium ${colors.bg} ${colors.text} ${colors.border}`}
                        >
                          <span className={`size-1.5 rounded-full ${colors.dot}`} />
                          {item.category}
                        </span>
                      </TableCell>

                      {/* Cột ON HAND: Số tồn thực tế trong kho (căn phải, in đậm) */}
                      <TableCell className="text-right font-bold tabular-nums text-slate-800 text-xs">
                        {fmtNum(item.onHand)}
                      </TableCell>

                      {/* Cột RESERVED: Số lượng đang giữ chỗ (màu cam đất hoặc xám) */}
                      <TableCell
                        className={`text-right tabular-nums font-medium ${
                          item.reserved > 0 ? "text-amber-600" : "text-slate-300"
                        }`}
                      >
                        {fmtNum(item.reserved)}
                      </TableCell>

                      {/* Cột BLOCKED: Số lượng bị chặn/lỗi/hỏng (màu đỏ nhẹ hoặc xám) */}
                      <TableCell
                        className={`text-right tabular-nums font-medium ${
                          item.blocked > 0 ? "text-rose-500" : "text-slate-300"
                        }`}
                      >
                        {fmtNum(item.blocked)}
                      </TableCell>

                      {/* Cột AVAILABLE (Cột quan trọng nhất):
                          Số lượng khả dụng in đậm màu xanh lá (text-emerald-600 font-bold text-sm)
                          Dưới số lượng là công thức diễn giải: = On Hand - Reserved - Blocked */}
                      <TableCell className="text-right">
                        <div className="text-emerald-600 font-bold text-sm tabular-nums">
                          {fmtNum(item.available)}
                        </div>
                        <div className="text-slate-400 text-[10px] font-mono mt-0.5">
                          = {fmtNum(item.onHand)} - {fmtNum(item.reserved)} - {fmtNum(item.blocked)}
                        </div>
                      </TableCell>

                      {/* Cột KHO: Mã kho lưu trữ (HN-01, HN-02, HCM-01) */}
                      <TableCell className="text-center font-mono text-slate-600">
                        <span className="inline-block bg-slate-100 px-2 py-0.5 rounded text-[11px] border border-slate-200/60 font-medium">
                          {item.warehouse}
                        </span>
                      </TableCell>

                      {/* Cột ĐVT: Đơn vị tính (kg, cái, hộp, tờ, m...) */}
                      <TableCell className="text-center text-slate-500 font-medium">
                        {item.unit}
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </tbody>
          </Table>
        </div>
      </div>
    </div>
  );
}
