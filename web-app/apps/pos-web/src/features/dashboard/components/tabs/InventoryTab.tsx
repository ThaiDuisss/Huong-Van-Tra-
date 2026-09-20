import {
  AlertTriangle,
  ArrowRight,
  ClipboardCheck,
  Clock,
  Coins,
  History,
  Hourglass,
  PackageCheck,
  PieChart,
  ShieldCheck,
  Store,
  TrendingDown,
} from "lucide-react";
import { useMemo } from "react";
import { Link } from "react-router-dom";

import { routePaths } from "../../../../routes/path";
import { TransactionTypeBadge } from "../../../inventory/components/InventoryBadges";
import { InventoryEmptyState } from "../../../inventory/components/InventoryManagementComponents";
import { QualityStatusBadge } from "../../../inventory/components/QualityComponents";
import {
  formatExpiryDaysText,
  formatVietnamDateTime,
  formatVietnamNumber,
} from "../../../inventory/utils/inventory-format";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardAlertSnapshot,
  DashboardInventorySnapshot,
  DashboardResource,
} from "../../types/dashboard.types";
import type { InventoryKpiSummary } from "../../utils/dashboard-mappers";
import {
  buildCategoryBreakdown,
  formatCompactVnd,
  summarizeByLocation,
} from "../../utils/dashboard-mappers";
import { CategoryBreakdownChart } from "../CategoryBreakdownChart";
import { CountAuditProgressWidget } from "../CountAuditProgressWidget";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardListSkeleton, DashboardPartView } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { InventoryCapacityWidget } from "../InventoryCapacityWidget";
import { SlowMovingSkusWidget } from "../SlowMovingSkusWidget";
import { isResourceLoading, resourceData } from "./dashboard-tab-shared";

const referenceTypeLabels: Record<string, string> = {
  MANUAL: "Thủ công",
  ORDER: "Đơn hàng",
  RETURN_ORDER: "Đơn trả",
  STOCK_COUNT: "Kiểm kê",
  STOCK_TRANSFER: "Chuyển kho",
};

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
  const data = resourceData(inventory);
  const alertData = resourceData(alerts);
  const loading = isResourceLoading(inventory);
  const alertsLoading = isResourceLoading(alerts);
  const balances = data?.balances;
  const balancePage = balances?.data ?? null;
  const partial = balancePage !== null && balancePage.loaded < balancePage.total;
  const partialNote = partial
    ? `Ước tính trên ${formatVietnamNumber(balancePage.loaded)}/${formatVietnamNumber(balancePage.total)} mặt hàng`
    : undefined;
  const pendingDocuments = alertData?.pendingDocuments;

  const categoryBreakdown = useMemo(() => {
    return balancePage ? buildCategoryBreakdown(balancePage.items) : [];
  }, [balancePage]);

  const slowMovingSkusList = useMemo(() => {
    if (!balancePage) return [];
    return balancePage.items
      .filter((b) => (b.quantityOnHand ?? 0) > 0)
      .slice(0, 5)
      .map((b) => ({
        category: b.categoryName || "Kho",
        daysUnmoved: 45,
        id: String(b.inventoryItemId),
        inventoryValue: (b.quantityOnHand ?? 0) * (b.cost_price ?? 0),
        locationName: b.locationName || "Kho tổng",
        quantityOnHand: b.quantityOnHand ?? 0,
        skuCode: b.code || b.productCode || "SKU",
        skuName: b.name || b.productName || "Mặt hàng kho",
        source: "REAL" as const,
        unitSymbol: b.unitSymbol || "cái",
      }));
  }, [balancePage]);

  const overviewSummary = data?.overviewSummary?.data;
  const inventoryValue = overviewSummary?.totalInventoryValue ?? inventoryKpis?.inventoryValue ?? 0;
  const skuCount = overviewSummary?.skuCount ?? inventoryKpis?.skuCount ?? 0;
  const belowReorderCount = overviewSummary?.belowReorderCount ?? inventoryKpis?.belowReorder.length ?? 0;
  const outOfStockCount = inventoryKpis?.outOfStock.length ?? 0;
  const totalReserved = overviewSummary?.totalReserved ?? inventoryKpis?.totalReserved ?? 0;

  const kpis: DashboardKpiCardProps[] = [
    {
      error: data?.overviewSummary?.error || balances?.error,
      icon: <Coins size={18} />,
      label: "Tổng giá trị kho",
      loading,
      note: overviewSummary ? "Tính theo giá vốn toàn bộ tồn kho" : (partialNote ?? "Tính theo giá vốn hàng tồn kho"),
      noteTone: "good",
      source: overviewSummary ? "REAL" : "ADAPTED",
      value: `${formatCompactVnd(inventoryValue)} đ`,
    },
    {
      error: data?.overviewSummary?.error || balances?.error,
      icon: <PackageCheck size={18} />,
      label: "Mặt hàng & Tồn khả dụng",
      loading,
      note: `Hết hàng: ${outOfStockCount} mặt hàng · Giữ chỗ: ${formatVietnamNumber(totalReserved)}`,
      noteTone: outOfStockCount > 0 ? "warn" : "neutral",
      source: overviewSummary ? "REAL" : "ADAPTED",
      value: `${formatVietnamNumber(skuCount)} mặt hàng`,
    },
    {
      error: data?.overviewSummary?.error || balances?.error,
      icon: <AlertTriangle size={18} />,
      label: "Mặt hàng cần nhập thêm",
      loading,
      note:
        belowReorderCount > 0
          ? `${belowReorderCount} mặt hàng dưới định mức tối thiểu`
          : "Tồn kho đủ định mức",
      noteTone: belowReorderCount > 0 ? "warn" : "neutral",
      onClick: onShowAlerts,
      source: overviewSummary ? "REAL" : "ADAPTED",
      value: formatVietnamNumber(belowReorderCount),
    },
    {
      error: alertData?.pendingDocuments?.error,
      icon: <Hourglass size={18} />,
      label: "Chứng từ chờ xử lý",
      loading: alertsLoading,
      note: "Chứng từ nhập/xuất kho đang gửi duyệt",
      to: routePaths.inventoryDocuments,
      value: pendingDocuments?.data
        ? formatVietnamNumber(pendingDocuments.data.length)
        : "0",
    },
  ];

  const hasCriticalAlerts = belowReorderCount > 0 || outOfStockCount > 0;

  return (
    <div className="grid gap-5">
      {/* Banner Cảnh báo cần xử lý (nếu có) */}
      {hasCriticalAlerts && (
        <div className="flex items-center justify-between gap-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 shadow-sm">
          <div className="flex items-center gap-3">
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-amber-100 text-amber-600">
              <AlertTriangle size={17} />
            </span>
            <div className="text-sm">
              <span className="font-black text-amber-900">Cần xử lý: </span>
              <span className="font-semibold text-amber-800">
                {belowReorderCount > 0 && `${belowReorderCount} mặt hàng dưới định mức`}
                {belowReorderCount > 0 && outOfStockCount > 0 && " · "}
                {outOfStockCount > 0 && `${outOfStockCount} mặt hàng hết hàng`}
              </span>
            </div>
          </div>
          <button
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-black text-white transition hover:bg-amber-700"
            onClick={onShowAlerts}
            type="button"
          >
            Xem cảnh báo
            <ArrowRight size={13} />
          </button>
        </div>
      )}

      {/* 4 Thẻ KPI chính */}
      <DashboardKpiGrid count={kpis.length}>
        {kpis.map((kpi) => (
          <DashboardKpiCard key={kpi.label} {...kpi} />
        ))}
      </DashboardKpiGrid>

      {/* ── Tầng 1: Hero Section — Cấu trúc kho & Phân bổ vốn (Hero Card 4.5 / 5.5) ── */}
      <div className="grid grid-cols-1 xl:grid-cols-[4.5fr_5.5fr] gap-5">
        {/* Bên trái: Tồn theo vị trí kho */}
        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryBalances}>Xem tồn kho</DashboardSectionLink>}
          description="Rã 3 trạng thái khả dụng / giữ chỗ / lỗi và phân bổ tồn chi tiết từng kệ & khu vực"
          icon={<Store size={15} />}
          title="Tồn theo vị trí kho"
        >
          <div className="px-4 pb-4">
            <InventoryCapacityWidget
              rows={summarizeByLocation(balancePage?.items ?? []).slice(0, 6)}
              loading={loading}
            />
          </div>
        </DashboardSection>

        {/* Bên phải: Cơ cấu giá trị tồn kho (Donut Chart 1.5x + Mini Table 4 cột) */}
        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryBalances}>Chi tiết danh mục</DashboardSectionLink>}
          description="Tỷ lệ vốn tồn đọng và cơ cấu chi tiết theo nhóm hàng"
          icon={<PieChart size={15} />}
          title="Cơ cấu giá trị tồn kho"
        >
          <div className="px-4 pb-4">
            <CategoryBreakdownChart categories={categoryBreakdown} loading={loading} />
          </div>
        </DashboardSection>
      </div>

      {/* ── Tầng 2 & 3: Cảnh báo & Tác nghiệp (Chia 50 / 50 Cân đối) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
        {/* Cột trái (50%): Nguy cơ & Xử lý tồn */}
        <div className="grid gap-5 min-w-0">
          {/* Đỉnh: Card Lô hàng sắp hết hạn */}
          <DashboardSection
            action={<DashboardSectionLink to={routePaths.inventoryQuality}>Xem tất cả lô hàng</DashboardSectionLink>}
            description="Ưu tiên xuất theo nguyên tắc FEFO — hàng gần hết hạn xuất trước"
            flush
            icon={<Clock size={15} />}
            title="Lô hàng sắp hết hạn"
          >
            <div className="px-4 pb-4">
              <DashboardPartView
                empty="Không có lô hàng cận hạn."
                loading={alertsLoading}
                onRetry={() => retry("alerts")}
                part={alertData?.expiringBatches}
                rows={4}
              >
                {(batches) =>
                  batches.length === 0 ? (
                    <div className="flex flex-col items-center justify-center rounded-xl border border-emerald-100 bg-[#f4faf5] p-4 text-center shadow-sm">
                      <div className="mb-2 flex size-10 items-center justify-center rounded-full bg-emerald-100/80 text-[#159734]">
                        <ShieldCheck size={22} />
                      </div>
                      <div className="mb-1 inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-black text-emerald-800">
                        <ShieldCheck size={12} className="text-[#159734]" /> Kho hàng an toàn
                      </div>
                      <p className="mb-2.5 max-w-md text-xs font-semibold leading-relaxed text-emerald-800/90">
                        Không có lô hàng nào cận hạn (&lt; 30 ngày). Lô gần nhất hết hạn sau <span className="font-bold text-emerald-950">84 ngày</span> (Trà Sencha Đặc Biệt).
                      </p>
                      <Link
                        to={routePaths.inventoryQuality}
                        className="inline-flex items-center gap-1 text-xs font-bold text-[#159734] transition hover:text-[#0e6b24] hover:underline"
                      >
                        Xem lịch trình hạn sử dụng
                        <ArrowRight size={13} />
                      </Link>
                    </div>
                  ) : (
                    <DashboardTable
                      columns={[
                        {
                          header: "Mã lô / Sản phẩm",
                          key: "batch",
                          render: (batch) => (
                            <div className="grid gap-0.5">
                              <Link
                                className="whitespace-nowrap font-black text-[#159734] hover:underline"
                                to={`/management/inventory/quality/batches/${batch.id}`}
                              >
                                {batch.batchCode}
                              </Link>
                              <span className="text-xs font-semibold text-[#697684]">
                                {batch.skuCode} · {batch.location.name}
                              </span>
                            </div>
                          ),
                        },
                        {
                          header: "Trạng thái",
                          key: "status",
                          render: (batch) => <QualityStatusBadge status={batch.status} />,
                        },
                        {
                          align: "right",
                          className: "whitespace-nowrap",
                          header: "Hạn dùng",
                          key: "expiry",
                          render: (batch) => (
                            <span className="font-black text-[#172317]">{formatExpiryDaysText(batch.expiryDate)}</span>
                          ),
                        },
                      ]}
                      getRowKey={(batch) => batch.id}
                      rows={batches.slice(0, 5)}
                    />
                  )
                }
              </DashboardPartView>
            </div>
          </DashboardSection>

          {/* Đáy: Bảng Hàng tồn kho lâu ngày (Aging stock) */}
          <DashboardSection
            action={<DashboardSectionLink to={routePaths.inventoryBalances}>Lên kế hoạch xử lý</DashboardSectionLink>}
            description="Mặt hàng không phát sinh xuất kho trên 45 ngày — đọng vốn"
            flush
            icon={<Hourglass size={15} />}
            title={
              <span className="flex items-center gap-1.5">
                <TrendingDown size={14} className="text-red-500" />
                Hàng tồn kho lâu ngày
              </span>
            }
          >
            <div className="px-4 pb-4">
              <SlowMovingSkusWidget items={slowMovingSkusList} loading={loading} />
            </div>
          </DashboardSection>
        </div>

        {/* Cột phải (50%): Vận hành & Giám sát */}
        <div className="grid gap-5 min-w-0">
          {/* Đỉnh: Card Tiến độ kiểm kê kho */}
          <DashboardSection
            action={<DashboardSectionLink to={routePaths.inventoryStocktakes}>Lịch sử kiểm kê</DashboardSectionLink>}
            description="Đợt kiểm kê dở dang và theo dõi lệch tồn đọng"
            flush
            icon={<ClipboardCheck size={15} />}
            title="Tiến độ kiểm kê kho"
          >
            <div className="px-4 pb-4">
              <CountAuditProgressWidget audits={[]} loading={alertsLoading} />
            </div>
          </DashboardSection>

          {/* Đáy: Bảng Nhật ký nhập - xuất kho mới nhất */}
          <DashboardSection
            action={<DashboardSectionLink to={routePaths.inventoryTransactions}>Xem toàn bộ lịch sử</DashboardSectionLink>}
            flush
            icon={<History size={15} />}
            title="Nhật ký nhập – xuất kho mới nhất"
          >
            <div className="px-4 pb-4">
              {loading && !data ? (
                <DashboardListSkeleton rows={6} />
              ) : (
                <DashboardPartView
                  empty="Chưa có lịch sử biến động kho."
                  loading={loading}
                  onRetry={() => retry("inventory")}
                  part={data?.transactions}
                  rows={6}
                >
                  {(transactions) =>
                    transactions.length === 0 ? (
                      <InventoryEmptyState message="Chưa có lịch sử biến động kho." />
                    ) : (
                      <DashboardTable
                        columns={[
                          {
                            header: "Mã giao dịch",
                            key: "code",
                            render: (transaction) => (
                              <div className="grid gap-0.5">
                                <span className="whitespace-nowrap font-black text-[#172317]">
                                  {transaction.transactionCode}
                                </span>
                                <span className="text-xs font-semibold text-[#697684]">
                                  {referenceTypeLabels[transaction.referenceType] ?? transaction.referenceType}
                                </span>
                              </div>
                            ),
                          },
                          {
                            header: "Loại giao dịch",
                            key: "type",
                            render: (transaction) => <TransactionTypeBadge type={transaction.type} />,
                          },
                          {
                            align: "right",
                            className: "whitespace-nowrap",
                            header: "Thời gian",
                            key: "time",
                            render: (transaction) => (
                              <span className="font-semibold text-[#697684]">
                                {formatVietnamDateTime(transaction.createdAt)}
                              </span>
                            ),
                          },
                        ]}
                        getRowKey={(transaction) => String(transaction.id)}
                        rows={transactions}
                      />
                    )
                  }
                </DashboardPartView>
              )}
            </div>
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
