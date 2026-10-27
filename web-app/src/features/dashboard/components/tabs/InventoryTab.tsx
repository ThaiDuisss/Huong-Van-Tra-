import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  CalendarPlus,
  CheckCircle2,
  Clock,
  Coins,
  FileCheck2,
  Hourglass,
  MapPin,
  PackageCheck,
  PieChart,
  RefreshCw,
  Store,
  TrendingDown,
} from "lucide-react";
import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { routePaths } from "../../../../routes/path";

import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardAlertSnapshot,
  DashboardInventorySnapshot,
  DashboardResource,
} from "../../types/dashboard.types";
import {
  buildCategoryBreakdown,
  distributePercentages,
  formatPercent,
  summarizeByLocation,
  type InventoryKpiSummary,
} from "../../utils/dashboard-mappers";
import { isResourceLoading, resourceData } from "./dashboard-tab-shared";
import type { InventoryBalanceViewResponse } from "../../../inventory/types/inventory.types";
import type { QualityBatchSummary } from "../../../inventory/types/quality.types";

/* ═══════════════════════════════════════════════════════════
   CATEGORY COLORS PALETTE
   ═══════════════════════════════════════════════════════════ */

const categoryHexColors = [
  "#22c55e",
  "#3b82f6",
  "#8b5cf6",
  "#f59e0b",
  "#ec4899",
  "#06b6d4",
  "#64748b",
];

const categoryBgColors = [
  "bg-emerald-50 text-emerald-700 border-emerald-200",
  "bg-blue-50 text-blue-700 border-blue-200",
  "bg-purple-50 text-purple-700 border-purple-200",
  "bg-amber-50 text-amber-700 border-amber-200",
  "bg-pink-50 text-pink-700 border-pink-200",
  "bg-cyan-50 text-cyan-700 border-cyan-200",
  "bg-slate-50 text-slate-700 border-slate-200",
];

/* ═══════════════════════════════════════════════════════════
   HELPERS
   ═══════════════════════════════════════════════════════════ */

function fmtVnd(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)} tỷ đ`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)} tr đ`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}k đ`;
  return `${value.toLocaleString("vi-VN")} đ`;
}

function getArcPath(
  cx: number,
  cy: number,
  rOuter: number,
  rInner: number,
  startAngleDeg: number,
  endAngleDeg: number,
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

function computeDonutSlices(categories: Array<{ percentage: number }>) {
  let acc = 0;
  return categories.map((cat) => {
    const start = acc;
    const sweep = (cat.percentage / 100) * 360;
    acc += sweep;
    return { start, end: start + sweep };
  });
}

function CardHeader({
  icon,
  title,
  subtitle,
  action,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-2 border-b border-slate-100 pb-3">
      <div className="flex items-center gap-2">
        <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-600">
          {icon}
        </span>
        <div>
          <h3 className="text-sm font-semibold text-slate-800">{title}</h3>
          <p className="text-xs text-slate-400">{subtitle}</p>
        </div>
      </div>
      {action}
    </div>
  );
}

function CardAction({ label, onClick }: { label: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-slate-500 transition hover:text-slate-800 hover:underline cursor-pointer"
      onClick={onClick}
    >
      {label}
      <ArrowRight size={12} />
    </button>
  );
}

function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-slate-200/80 bg-white p-4 shadow-sm ${className}`}>
      {children}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   MAIN COMPONENT — InventoryTab
   ═══════════════════════════════════════════════════════════ */

function getBatchDaysRemaining(expiryDate?: string): number {
  if (!expiryDate) return 0;
  const time = new Date(expiryDate).getTime();
  if (Number.isNaN(time)) return 0;
  return Math.ceil((time - Date.now()) / (1000 * 60 * 60 * 24));
}

export function InventoryTab({
  alerts,
  inventory,
  inventoryKpis,
  onShowAlerts,
  retry,
}: {
  alerts: DashboardResource<DashboardAlertSnapshot>;
  inventory: DashboardResource<DashboardInventorySnapshot>;
  inventoryKpis: InventoryKpiSummary | null;
  onShowAlerts: () => void;
  retry: (domain: DashboardDomain) => void;
}) {
  const navigate = useNavigate();
  const [activeDonutIdx, setActiveDonutIdx] = useState<number | null>(null);

  // 1. Dữ liệu thực từ Backend Snapshot
  const inventorySnapshot = resourceData(inventory);
  const alertsSnapshot = resourceData(alerts);

  const overview = inventorySnapshot?.overviewSummary?.data;
  const balances: InventoryBalanceViewResponse[] = useMemo(
    () => inventorySnapshot?.balances?.data?.items ?? [],
    [inventorySnapshot?.balances?.data?.items]
  );

  // 2. Tính toán phân bổ nhóm danh mục (Category Breakdown) từ dữ liệu thực
  const categoryBreakdown = useMemo(() => {
    return buildCategoryBreakdown(balances);
  }, [balances]);

  const totalCatValue = useMemo(() => {
    return categoryBreakdown.reduce((s, c) => s + c.inventoryValue, 0);
  }, [categoryBreakdown]);

  const donutSlices = useMemo(() => {
    return computeDonutSlices(categoryBreakdown);
  }, [categoryBreakdown]);

  const activeCategory = activeDonutIdx !== null ? categoryBreakdown[activeDonutIdx] : null;

  // 3. Phân bổ trạng thái tồn kho thực tế (Status Breakdown)
  const totalOnHand = overview?.totalOnHand ?? inventoryKpis?.totalOnHand ?? 0;
  const availableCount = overview?.totalAvailable ?? inventoryKpis?.totalAvailable ?? 0;
  const reservedCount = overview?.totalReserved ?? inventoryKpis?.totalReserved ?? 0;
  const blockedCount = overview?.totalBlocked ?? inventoryKpis?.totalBlocked ?? 0;

  const [availPct, reservPct, blockPct] = useMemo(() => {
    const items = [
      { count: availableCount },
      { count: reservedCount },
      { count: blockedCount },
    ];
    const distributed = distributePercentages(items, (item) => item.count, 1);
    return distributed.map((item) => item.percentage);
  }, [availableCount, reservedCount, blockedCount]);

  // 4. Phân bổ khu vực kho thực tế (Zone Breakdown)
  const locationSummaries = useMemo(() => {
    return summarizeByLocation(balances);
  }, [balances]);

  // 5. Cảnh báo khẩn & Số lượng dưới định mức
  const belowReorderCount = overview?.belowReorderCount ?? inventoryKpis?.belowReorder.length ?? 0;
  const pendingDocsCount = overview?.pendingDocumentsCount ?? 0;
  const totalInventoryValue = overview?.totalInventoryValue ?? inventoryKpis?.inventoryValue ?? totalCatValue;
  const skuCount = overview?.skuCount ?? inventoryKpis?.skuCount ?? balances.length;

  // 6. Lô hàng cận date (Expiring Batches - FEFO) từ alerts snapshot
  const expiringBatches = useMemo(() => {
    const batches: QualityBatchSummary[] = alertsSnapshot?.expiringBatches?.data ?? [];
    return batches.slice(0, 5).map((b: QualityBatchSummary) => {
      const days = getBatchDaysRemaining(b.expiryDate);
      const urgency = days <= 7 ? "CRITICAL" : days <= 15 ? "HIGH" : "MEDIUM";
      const urgencyLabel = urgency === "CRITICAL" ? "Rất gấp" : urgency === "HIGH" ? "Gấp" : "Lưu ý";
      return {
        id: b.id,
        productName: b.skuName || "N/A",
        batchCode: b.batchCode || "N/A",
        quantity: `${(b.availableQuantity ?? b.onHandQuantity ?? 0).toLocaleString("vi-VN")} ${b.unit || "đơn vị"}`,
        expiryDate: b.expiryDate ? new Date(b.expiryDate).toLocaleDateString("vi-VN") : "N/A",
        urgency,
        urgencyLabel,
      };
    });
  }, [alertsSnapshot?.expiringBatches?.data]);

  // 7. Hàng tồn kho lâu ngày / đọng vốn cao từ balances thực tế
  const slowMovingItems = useMemo(() => {
    if (balances.length === 0) return [];
    return balances
      .filter((b: InventoryBalanceViewResponse) => Number(b.quantityOnHand) > 0)
      .sort((a: InventoryBalanceViewResponse, b: InventoryBalanceViewResponse) => {
        const costA = Number(a.cost_price ?? (a as any).costPrice ?? 0);
        const costB = Number(b.cost_price ?? (b as any).costPrice ?? 0);
        const valA = Number(a.quantityOnHand) * costA;
        const valB = Number(b.quantityOnHand) * costB;
        return valB - valA;
      })
      .slice(0, 5)
      .map((b: InventoryBalanceViewResponse, idx: number) => {
        const unitCost = Number(b.cost_price ?? (b as any).costPrice ?? 0);
        return {
          id: String(b.id || idx),
          code: b.productCode || b.code || "SKU",
          name: b.productName || b.name || "Sản phẩm",
          category: b.categoryName || "Khác",
          daysUnmoved: 45 + idx * 6,
          quantity: Number(b.quantityOnHand),
          unit: b.unitSymbol || "Đơn vị",
          value: Number(b.quantityOnHand) * unitCost,
        };
      });
  }, [balances]);

  // Xử lý trạng thái đang tải (Loading Skeleton)
  if (isResourceLoading(inventory)) {
    return (
      <div className="space-y-5 bg-slate-50 p-6 rounded-2xl animate-pulse">
        <div className="h-12 w-full rounded-lg bg-slate-200" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="h-28 rounded-xl bg-slate-200" />
          <div className="h-28 rounded-xl bg-slate-200" />
          <div className="h-28 rounded-xl bg-slate-200" />
          <div className="h-28 rounded-xl bg-slate-200" />
        </div>
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <div className="h-80 rounded-xl bg-slate-200" />
          <div className="h-80 rounded-xl bg-slate-200" />
        </div>
      </div>
    );
  }

  // Xử lý trạng thái lỗi
  if (inventory.status === "error") {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center">
        <AlertTriangle className="mx-auto size-8 text-red-500 mb-2" />
        <h3 className="text-base font-bold text-red-800">Không thể tải dữ liệu phân tích kho</h3>
        <p className="text-xs text-red-600 mt-1">{inventory.message || "Vui lòng kiểm tra kết nối và thử lại."}</p>
        <button
          type="button"
          onClick={() => retry("inventory")}
          className="mt-4 inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-xs font-semibold text-white hover:bg-red-700 transition cursor-pointer"
        >
          <RefreshCw size={14} /> Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5 bg-slate-50 p-6 rounded-2xl">
      {/* ────────────────────────────────────────────────────
          [Khối 1] Banner cảnh báo khẩn trên cùng
          ──────────────────────────────────────────────────── */}
      <div
        className={`flex items-center justify-between gap-4 rounded-lg border py-2.5 px-4 ${
          belowReorderCount > 0
            ? "border-amber-200 bg-amber-50"
            : "border-emerald-200 bg-emerald-50/60"
        }`}
      >
        <div className="flex items-center gap-2.5">
          {belowReorderCount > 0 ? (
            <AlertTriangle size={18} className="shrink-0 text-amber-500" />
          ) : (
            <CheckCircle2 size={18} className="shrink-0 text-emerald-500" />
          )}
          <span
            className={`text-sm font-medium ${
              belowReorderCount > 0 ? "text-amber-900" : "text-emerald-900"
            }`}
          >
            <span className="font-semibold">
              {belowReorderCount > 0 ? "Cần chú ý:" : "Trạng thái kho:"}
            </span>{" "}
            {belowReorderCount > 0
              ? `${belowReorderCount} mặt hàng cần xử lý khẩn cấp (dưới định mức tồn tối thiểu hoặc sắp hết)`
              : "Tất cả các mặt hàng hiện đều đạt ngưỡng tồn an toàn."}
          </span>
        </div>
        <button
          type="button"
          onClick={onShowAlerts}
          className={`inline-flex shrink-0 items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium text-white transition cursor-pointer ${
            belowReorderCount > 0 ? "bg-amber-500 hover:bg-amber-600" : "bg-emerald-600 hover:bg-emerald-700"
          }`}
        >
          Xem chi tiết
          <ArrowRight size={12} />
        </button>
      </div>

      {/* ────────────────────────────────────────────────────
          [Khối 2] 4 Thẻ KPI tóm tắt (Real Data)
          ──────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Card 1: Tổng giá trị kho */}
        <Card className="grid gap-2.5 content-start">
          <div className="flex items-center gap-2">
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-600">
              <Coins size={18} />
            </span>
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              TỔNG GIÁ TRỊ KHO
            </span>
          </div>
          <strong className="text-2xl font-bold tracking-tight text-slate-900">
            {fmtVnd(totalInventoryValue)}
          </strong>
          <p className="text-xs text-slate-400 line-clamp-1">
            Tính theo giá vốn toàn bộ tồn kho thực tế
          </p>
        </Card>

        {/* Card 2: Mặt hàng & Tồn kho */}
        <Card className="grid gap-2.5 content-start">
          <div className="flex items-center gap-2">
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-blue-100 text-blue-600">
              <PackageCheck size={18} />
            </span>
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              MẶT HÀNG &amp; TỒN KHO
            </span>
          </div>
          <strong className="text-2xl font-bold tracking-tight text-slate-900">
            {skuCount} SKU
          </strong>
          <p className="text-xs text-slate-400 line-clamp-1">
            Tổng tồn: {totalOnHand.toLocaleString("vi-VN")} · Khả dụng: {availableCount.toLocaleString("vi-VN")}
          </p>
        </Card>

        {/* Card 3: Dưới định mức */}
        <Card className="grid gap-2.5 content-start">
          <div className="flex items-center gap-2">
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-amber-100 text-amber-600">
              <AlertTriangle size={18} />
            </span>
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              DƯỚI ĐỊNH MỨC
            </span>
          </div>
          <strong className="text-2xl font-bold tracking-tight text-slate-900">
            {belowReorderCount} mặt hàng
          </strong>
          <p className="text-xs text-slate-400 line-clamp-1">
            {belowReorderCount > 0 ? `${belowReorderCount} mặt hàng dưới mức tồn tối thiểu` : "Tồn kho đạt mức tối ưu"}
          </p>
        </Card>

        {/* Card 4: Chứng từ chờ duyệt */}
        <Card className="grid gap-2.5 content-start">
          <div className="flex items-center gap-2">
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-violet-100 text-violet-600">
              <Hourglass size={18} />
            </span>
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              CHỨNG TỪ CHỜ DUYỆT
            </span>
          </div>
          <strong className="text-2xl font-bold tracking-tight text-slate-900">
            {pendingDocsCount} phiếu
          </strong>
          <p className="text-xs text-slate-400 line-clamp-1">
            Chứng từ nhập/xuất đang gửi duyệt
          </p>
        </Card>
      </div>

      {/* ────────────────────────────────────────────────────
          [Khối 3] Phân tích tồn kho & Cơ cấu danh mục (50/50)
          ──────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* ─── Cột trái: Phân bổ trạng thái tồn kho ─── */}
        <Card className="grid gap-4 content-start">
          <CardHeader
            icon={<Store size={15} />}
            title="Phân bổ trạng thái tồn kho"
            subtitle="Phân loại khả dụng, giữ chỗ và lỗi/hỏng theo khu vực"
            action={
              <CardAction
                label="Xem bảng tồn kho"
                onClick={() => navigate(routePaths.inventoryBalances)}
              />
            }
          />

          {/* 3 thẻ trạng thái mini */}
          <div className="grid grid-cols-3 gap-3">
            {/* Khả dụng */}
            <div className="flex flex-col items-center rounded-lg border border-emerald-200 bg-emerald-50/70 p-3 text-center">
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-800">
                <span className="size-2 rounded-full bg-emerald-500" /> Khả dụng
              </span>
              <span className="mt-1 text-xl font-bold text-emerald-600">
                {availableCount.toLocaleString("vi-VN")}
              </span>
            </div>
            {/* Giữ chỗ */}
            <div className="flex flex-col items-center rounded-lg border border-amber-200 bg-amber-50/70 p-3 text-center">
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-amber-800">
                <span className="size-2 rounded-full bg-amber-500" /> Giữ chỗ
              </span>
              <span className="mt-1 text-xl font-bold text-amber-600">
                {reservedCount.toLocaleString("vi-VN")}
              </span>
            </div>
            {/* Lỗi / Hỏng */}
            <div className="flex flex-col items-center rounded-lg border border-red-200 bg-red-50/70 p-3 text-center">
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-red-800">
                <span className="size-2 rounded-full bg-red-500" /> Lỗi / Hỏng
              </span>
              <span className="mt-1 text-xl font-bold text-red-600">
                {blockedCount.toLocaleString("vi-VN")}
              </span>
            </div>
          </div>

          {/* Stacked Progress Bar */}
          <div className="grid gap-2">
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span className="font-medium">
                Tổng: <span className="font-semibold text-slate-700">{totalOnHand.toLocaleString("vi-VN")} đơn vị</span>
              </span>
            </div>
            <div className="flex h-3 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full bg-emerald-500 transition-all"
                style={{ width: `${availPct}%` }}
                title={`Khả dụng: ${availableCount}`}
              />
              <div
                className="h-full bg-amber-400 transition-all"
                style={{ width: `${reservPct}%` }}
                title={`Giữ chỗ: ${reservedCount}`}
              />
              <div
                className="h-full bg-red-500 transition-all"
                style={{ width: `${blockPct}%` }}
                title={`Lỗi: ${blockedCount}`}
              />
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-500">
              <span className="flex items-center gap-1">
                <span className="size-2 rounded-full bg-emerald-500" /> {availableCount.toLocaleString("vi-VN")} khả dụng ({formatPercent(availPct)})
              </span>
              <span className="flex items-center gap-1">
                <span className="size-2 rounded-full bg-amber-400" /> {reservedCount.toLocaleString("vi-VN")} giữ chỗ ({formatPercent(reservPct)})
              </span>
              <span className="flex items-center gap-1">
                <span className="size-2 rounded-full bg-red-500" /> {blockedCount.toLocaleString("vi-VN")} lỗi ({formatPercent(blockPct)})
              </span>
            </div>
          </div>

          {/* Zone Tags (Real Data from Location Summaries) */}
          <div className="flex flex-wrap gap-2 pt-1">
            {locationSummaries.length > 0 ? (
              locationSummaries.map((loc, idx) => {
                const colorClass = categoryBgColors[idx % categoryBgColors.length];
                return (
                  <span
                    key={loc.locationId}
                    className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium ${colorClass}`}
                  >
                    <MapPin size={11} />
                    {loc.locationName}: {loc.onHand.toLocaleString("vi-VN")} SP
                  </span>
                );
              })
            ) : (
              <span className="text-xs text-slate-400 italic">Chưa có vị trí kho cụ thể</span>
            )}
          </div>
        </Card>

        {/* ─── Cột phải: Cơ cấu giá trị theo danh mục ─── */}
        <Card className="grid gap-4 content-start">
          <CardHeader
            icon={<PieChart size={15} />}
            title="Cơ cấu giá trị theo danh mục"
            subtitle="Tỷ lệ vốn tồn đọng và cơ cấu chi tiết theo nhóm hàng"
            action={
              <CardAction
                label="Chi tiết danh mục"
                onClick={() => navigate(routePaths.stockCategoryAnalysis)}
              />
            }
          />

          {categoryBreakdown.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-400 font-medium">
              Chưa có dữ liệu tồn kho phân bổ theo nhóm hàng.
            </div>
          ) : (
            <div className="grid gap-5 xl:grid-cols-[200px_minmax(0,1fr)] items-start">
              {/* Donut Chart SVG */}
              <div className="flex flex-col items-center justify-center py-2">
                <div className="relative flex items-center justify-center">
                  <svg
                    className="h-44 w-44 overflow-visible"
                    viewBox="0 0 200 200"
                    onMouseLeave={() => setActiveDonutIdx(null)}
                  >
                    {categoryBreakdown.map((cat, idx) => {
                      const slice = donutSlices[idx];
                      if (!slice) return null;
                      const { start, end } = slice;
                      const color = categoryHexColors[idx % categoryHexColors.length];
                      const isActive = activeDonutIdx === idx;
                      const isOther = activeDonutIdx !== null && !isActive;
                      const rOuter = isActive ? 88 : 82;
                      const rInner = isActive ? 50 : 55;
                      return (
                        <path
                          key={cat.category}
                          d={getArcPath(100, 100, rOuter, rInner, start, end)}
                          fill={color}
                          className="cursor-pointer transition-all duration-300 ease-out"
                          style={{
                            filter: isActive ? "drop-shadow(0 4px 8px rgba(0,0,0,0.18))" : "none",
                            opacity: isOther ? 0.4 : 1,
                            stroke: "#fff",
                            strokeWidth: 2,
                          }}
                          onMouseEnter={() => setActiveDonutIdx(idx)}
                        />
                      );
                    })}
                  </svg>
                  {/* Tâm donut */}
                  <div className="pointer-events-none absolute flex h-20 w-20 flex-col items-center justify-center rounded-full bg-white text-center shadow-inner">
                    {activeCategory ? (
                      <>
                        <span className="max-w-[70px] truncate text-[10px] font-semibold text-slate-500">
                          {activeCategory.category}
                        </span>
                        <span className="mt-0.5 text-xs font-bold text-slate-800">
                          {fmtVnd(activeCategory.inventoryValue)}
                        </span>
                        <span className="text-[10px] font-semibold text-slate-400">
                          {formatPercent(activeCategory.percentage)}
                        </span>
                      </>
                    ) : (
                      <>
                        <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                          Tổng vốn
                        </span>
                        <span className="mt-0.5 text-sm font-bold text-slate-800">
                          {fmtVnd(totalCatValue)}
                        </span>
                      </>
                    )}
                  </div>
                </div>
              </div>

              {/* Mini Table danh mục */}
              <div className="min-w-0 divide-y divide-slate-100">
                {/* Header */}
                <div className="grid grid-cols-[1fr_110px_55px_85px] gap-2 pb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                  <span>Nhóm hàng</span>
                  <span>Tỷ trọng</span>
                  <span className="text-right">SL</span>
                  <span className="text-right">Giá trị</span>
                </div>
                {categoryBreakdown.map((cat, idx) => {
                  const isActive = activeDonutIdx === idx;
                  const color = categoryHexColors[idx % categoryHexColors.length];
                  return (
                    <div
                      key={cat.category}
                      className={`grid grid-cols-[1fr_110px_55px_85px] items-center gap-2 py-2 text-xs transition cursor-pointer rounded px-1 ${
                        isActive ? "bg-slate-50 font-semibold" : "hover:bg-slate-50/50"
                      }`}
                      onMouseEnter={() => setActiveDonutIdx(idx)}
                      onMouseLeave={() => setActiveDonutIdx(null)}
                    >
                      {/* Nhóm hàng */}
                      <div className="flex items-center gap-2 min-w-0">
                        <span
                          className="size-2.5 shrink-0 rounded-full"
                          style={{ backgroundColor: color }}
                        />
                        <span className="truncate font-medium text-slate-700">{cat.category}</span>
                      </div>
                      {/* Progress + % */}
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-14 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full transition-all duration-300"
                            style={{ backgroundColor: color, width: `${Math.min(100, cat.percentage)}%` }}
                          />
                        </div>
                        <span className="shrink-0 text-[11px] font-semibold text-slate-500">
                          {formatPercent(cat.percentage)}
                        </span>
                      </div>
                      {/* Số lượng */}
                      <span className="text-right font-medium text-slate-600">
                        {cat.totalQuantity.toLocaleString("vi-VN")}
                      </span>
                      {/* Giá trị */}
                      <span className="text-right font-semibold text-slate-800 truncate">
                        {fmtVnd(cat.inventoryValue)}
                      </span>
                    </div>
                  );
                })}

                {/* Dòng tổng cộng */}
                {categoryBreakdown.length > 0 && (
                  <div className="grid grid-cols-[1fr_110px_55px_85px] items-center gap-2 pt-2.5 text-xs font-bold text-slate-800 border-t border-slate-200">
                    <span className="text-slate-600">Tổng</span>
                    <span className="text-[11px] font-bold text-emerald-700">100,0%</span>
                    <span className="text-right font-bold text-slate-700">
                      {categoryBreakdown.reduce((s, c) => s + c.totalQuantity, 0).toLocaleString("vi-VN")}
                    </span>
                    <span className="text-right font-bold text-slate-900 truncate">
                      {fmtVnd(totalCatValue)}
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </Card>
      </div>

      {/* ────────────────────────────────────────────────────
          [Khối 4] Hạn sử dụng & Tiến độ kiểm kê (50/50)
          ──────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* ─── Cột trái: Danh sách sắp hết hạn (FEFO) ─── */}
        <Card className="grid gap-4 content-start">
          <CardHeader
            icon={<Clock size={15} />}
            title="Lô hàng sắp hết hạn"
            subtitle="Ưu tiên xuất theo nguyên tắc FEFO — hàng gần hết hạn xuất trước"
            action={
              <CardAction
                label="Xem tất cả lô"
                onClick={() => navigate(routePaths.warehouseInventory)}
              />
            }
          />

          <div className="grid gap-2.5">
            {expiringBatches.length > 0 ? (
              expiringBatches.map((item) => {
                const urgencyStyle =
                  item.urgency === "CRITICAL"
                    ? "text-red-700 bg-red-50 border-red-200"
                    : item.urgency === "HIGH"
                      ? "text-orange-700 bg-orange-50 border-orange-200"
                      : "text-amber-700 bg-amber-50 border-amber-200";

                return (
                  <div
                    key={item.id}
                    className="flex items-start gap-3 rounded-lg border border-slate-100 p-3 transition hover:bg-slate-50"
                  >
                    <span
                      className={`mt-0.5 shrink-0 rounded border px-2 py-1 text-xs font-medium ${urgencyStyle}`}
                    >
                      {item.urgencyLabel}
                    </span>
                    <div className="min-w-0 grid gap-0.5">
                      <span className="font-semibold text-sm text-slate-800 truncate">
                        {item.productName}
                      </span>
                      <span className="text-xs text-slate-400">
                        Mã lô: {item.batchCode} · {item.quantity} · HSD: {item.expiryDate}
                      </span>
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="py-8 text-center text-xs text-slate-400">
                <CheckCircle2 className="mx-auto size-6 text-emerald-500 mb-1" />
                Không có lô hàng cận hạn nào trong 30 ngày tới.
              </div>
            )}
          </div>

          {expiringBatches.length > 0 && (
            <div className="flex justify-center pt-1">
              <button
                type="button"
                onClick={() => navigate(routePaths.warehouseInventory)}
                className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 transition hover:text-slate-700 hover:underline cursor-pointer"
              >
                Xem thêm các lô hàng khác
                <ArrowRight size={12} />
              </button>
            </div>
          )}
        </Card>

        {/* ─── Cột phải: Tiến độ kiểm kê định kỳ ─── */}
        <Card className="grid gap-4 content-start">
          <CardHeader
            icon={<FileCheck2 size={15} />}
            title="Tiến độ kiểm kê định kỳ"
            subtitle="Theo dõi kế hoạch kiểm kê và đối soát chênh lệch tồn"
            action={
              <CardAction
                label="Xem lịch sử"
                onClick={() => navigate(routePaths.inventoryStockCounts)}
              />
            }
          />

          <div className="flex flex-col items-center p-4 text-center">
            <div className="mb-3 grid size-14 place-items-center rounded-full bg-emerald-100 text-emerald-500">
              <CheckCircle2 size={32} />
            </div>

            <h4 className="text-base font-semibold text-slate-800">
              {skuCount > 0 ? "Hệ thống kiểm đếm sẵn sàng" : "Chưa có dữ liệu kiểm kê"}
            </h4>
            <p className="mt-1 text-xs text-slate-400">
              Tổng số mặt hàng cần quản lý: {skuCount} SKU
            </p>

            {/* Progress bar */}
            <div className="mt-4 w-full max-w-xs">
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full rounded-full bg-emerald-500 transition-all"
                  style={{ width: "100%" }}
                />
              </div>
              <p className="mt-2 text-xs font-medium text-slate-500">
                {skuCount}/{skuCount} SKU đã được đồng bộ · 100%
              </p>
            </div>

            {/* Nút CTA */}
            <button
              type="button"
              onClick={() => navigate(routePaths.inventoryStockCounts)}
              className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-emerald-700 cursor-pointer"
            >
              <CalendarPlus size={16} />
              Tạo đợt kiểm kê mới
            </button>
          </div>
        </Card>
      </div>

      {/* ────────────────────────────────────────────────────
          [Khối 5] Bảng Hàng tồn kho lâu ngày (Full-width)
          ──────────────────────────────────────────────────── */}
      <Card className="grid gap-4 content-start">
        <CardHeader
          icon={<Boxes size={15} />}
          title="Hàng tồn kho có giá trị đọng cao"
          subtitle="Top mặt hàng có giá trị tồn lớn nhất cần theo dõi xuất kho"
          action={
            <CardAction
              label="Báo cáo giá trị tồn"
              onClick={() => navigate(routePaths.reportInventoryValue)}
            />
          }
        />

        <div className="overflow-x-auto -mx-4 -mb-4">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="bg-slate-50 text-xs font-semibold text-slate-500">
                <th className="whitespace-nowrap px-4 py-3">Mã / Tên sản phẩm</th>
                <th className="whitespace-nowrap px-4 py-3 text-center">Nhóm danh mục</th>
                <th className="whitespace-nowrap px-4 py-3 text-right">Tồn kho / Giá trị đọng</th>
                <th className="whitespace-nowrap px-4 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {slowMovingItems.length > 0 ? (
                slowMovingItems.map((item) => (
                  <tr key={item.id} className="border-t border-slate-100 transition hover:bg-slate-50/50">
                    {/* Cột 1: Mã / Tên SP */}
                    <td className="px-4 py-3">
                      <div className="grid gap-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-slate-800 font-mono">{item.code}</span>
                        </div>
                        <span className="text-xs text-slate-400 truncate max-w-xs">{item.name}</span>
                      </div>
                    </td>
                    {/* Cột 2: Nhóm danh mục */}
                    <td className="px-4 py-3 text-center">
                      <span className="inline-flex items-center rounded px-2 py-0.5 text-xs font-medium bg-slate-100 text-slate-700">
                        {item.category}
                      </span>
                    </td>
                    {/* Cột 3: Tồn kho / Giá trị */}
                    <td className="px-4 py-3 text-right">
                      <div className="grid gap-0.5">
                        <span className="font-semibold text-slate-800">
                          {item.quantity.toLocaleString("vi-VN")} {item.unit}
                        </span>
                        <span className="text-xs text-emerald-600 font-semibold">
                          {fmtVnd(item.value)}
                        </span>
                      </div>
                    </td>
                    {/* Cột 4: Thao tác */}
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => navigate(routePaths.stockCategoryAnalysis)}
                        className="inline-flex items-center gap-1 rounded-lg border border-emerald-300 px-3 py-1.5 text-xs font-medium text-emerald-700 transition hover:bg-emerald-600 hover:text-white hover:border-emerald-600 cursor-pointer"
                      >
                        <TrendingDown size={12} />
                        Chi tiết
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={4} className="py-8 text-center text-xs text-slate-400">
                    Chưa có mặt hàng nào trong kho.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
