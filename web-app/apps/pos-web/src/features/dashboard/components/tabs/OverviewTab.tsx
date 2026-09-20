import {
  AlertTriangle,
  BadgeDollarSign,
  Bell,
  Boxes,
  PackageCheck,
  ReceiptText,
  RotateCcw,
  ShoppingCart,
} from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";

import { routePaths } from "../../../../routes/path";
import { InventoryEmptyState } from "../../../inventory/components/InventoryManagementComponents";
import { formatVietnamNumber, formatVnd } from "../../../inventory/utils/inventory-format";
import { OrderStatusBadge } from "../../../order/components/OrderStatusBadge";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardAlertItem,
  DashboardAlertSnapshot,
  DashboardFilters,
  DashboardInventorySnapshot,
  DashboardResource,
  DashboardSalesSnapshot,
} from "../../types/dashboard.types";
import type { InventoryKpiSummary } from "../../utils/dashboard-mappers";
import { formatPercent, numeric, summarizeByLocation } from "../../utils/dashboard-mappers";
import { countBySeverity } from "../../utils/dashboard-alerts";
import type { DashboardCapabilities } from "../../utils/dashboard-permissions";
import { AlertPriorityList } from "../AlertPriorityList";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardGapState, DashboardListSkeleton, DashboardPartView, SourceBadge } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { RankList } from "../RankList";
import { StackedBar } from "../StackedBar";
import { TrendChart, type TrendMetric } from "../TrendChart";
import { isResourceLoading, resourceData, timeRangeLabels, widgetRowClass } from "./dashboard-tab-shared";

const LANDING_ALERT_LIMIT = 5;

export function OverviewTab({
  alertItems,
  alerts,
  capabilities,
  filters,
  inventory,
  inventoryKpis,
  onShowAlerts,
  retry,
  sales,
}: {
  alertItems: DashboardAlertItem[];
  alerts: DashboardResource<DashboardAlertSnapshot>;
  capabilities: DashboardCapabilities;
  filters: DashboardFilters;
  inventory: DashboardResource<DashboardInventorySnapshot>;
  inventoryKpis: InventoryKpiSummary | null;
  onShowAlerts: () => void;
  retry: (domain: DashboardDomain) => void;
  sales: DashboardResource<DashboardSalesSnapshot>;
}) {
  const [metric, setMetric] = useState<TrendMetric>("revenue");
  const [skuMode, setSkuMode] = useState<"revenue" | "quantity">("revenue");
  const salesData = resourceData(sales);
  const inventoryData = resourceData(inventory);
  const salesLoading = isResourceLoading(sales);
  const inventoryLoading = isResourceLoading(inventory);
  const alertsLoading = isResourceLoading(alerts) || inventoryLoading;
  const isToday = filters.timeRange === "TODAY";
  const rangeLabel = timeRangeLabels[filters.timeRange];
  const summary = salesData?.todaySummary;
  const stats = salesData?.stats;
  const severityCounts = countBySeverity(alertItems);

  const kpis: DashboardKpiCardProps[] = [];

  if (capabilities.finance) {
    kpis.push({
      error: isToday ? summary?.error : undefined,
      icon: <BadgeDollarSign size={18} />,
      label: "Doanh thu",
      loading: salesLoading,
      note: isToday ? "Đơn POS hôm nay" : `API chỉ hỗ trợ hôm nay, chưa có ${rangeLabel}`,
      noteTone: isToday ? "neutral" : "warn",
      source: isToday ? "REAL" : "GAP",
      value: isToday && summary?.data ? formatVnd(numeric(summary.data.todayRevenue)) : undefined,
    });
  }

  if (capabilities.sales) {
    const total = numeric(stats?.data?.total);
    const completed = numeric(stats?.data?.completed);

    kpis.push({
      error: stats?.error,
      icon: <ShoppingCart size={18} />,
      label: "Số đơn",
      loading: salesLoading,
      note: stats?.data ? `${formatVietnamNumber(completed)} hoàn tất · ${rangeLabel}` : rangeLabel,
      value: stats?.data ? formatVietnamNumber(total) : undefined,
    });
  }

  if (capabilities.finance) {
    kpis.push({
      error: isToday ? summary?.error : undefined,
      icon: <ReceiptText size={18} />,
      label: "Giá trị đơn trung bình",
      loading: salesLoading,
      note: isToday ? "Đơn POS hôm nay" : `API chỉ hỗ trợ hôm nay`,
      noteTone: isToday ? "neutral" : "warn",
      source: isToday ? "REAL" : "GAP",
      value:
        isToday && summary?.data ? formatVnd(numeric(summary.data.averageOrderValue)) : undefined,
    });
  }

  if (capabilities.sales) {
    const total = numeric(stats?.data?.total);
    const returned = numeric(stats?.data?.returnedOrRefunded);

    kpis.push({
      error: stats?.error,
      icon: <RotateCcw size={18} />,
      label: "Đơn hoàn / refund",
      loading: salesLoading,
      note: stats?.data
        ? returned > 0
          ? `${formatPercent(total > 0 ? (returned / total) * 100 : 0)} số đơn · ${rangeLabel}`
          : `Không có · ${rangeLabel}`
        : rangeLabel,
      noteTone: returned > 0 ? "bad" : "good",
      source: "ADAPTED",
      value: stats?.data ? formatVietnamNumber(returned) : undefined,
    });
  }

  if (capabilities.inventory && !capabilities.sales) {
    const balances = inventoryData?.balances;

    kpis.push(
      {
        error: balances?.error,
        icon: <Boxes size={18} />,
        label: "SKU có tồn",
        loading: inventoryLoading,
        note: "Trong phạm vi kho đã chọn",
        value: inventoryKpis ? formatVietnamNumber(inventoryKpis.skuCount) : undefined,
      },
      {
        error: balances?.error,
        icon: <PackageCheck size={18} />,
        label: "Tồn khả dụng",
        loading: inventoryLoading,
        note: inventoryKpis
          ? `Giữ chỗ ${formatVietnamNumber(inventoryKpis.totalReserved)} · bị chặn ${formatVietnamNumber(inventoryKpis.totalBlocked)}`
          : undefined,
        value: inventoryKpis ? formatVietnamNumber(inventoryKpis.totalAvailable) : undefined,
      },
      {
        error: balances?.error,
        icon: <AlertTriangle size={18} />,
        label: "SKU dưới điểm đặt lại",
        loading: inventoryLoading,
        note: inventoryKpis && inventoryKpis.belowReorder.length > 0 ? "Cần bổ sung hàng" : "Không có SKU nào",
        noteTone: inventoryKpis && inventoryKpis.belowReorder.length > 0 ? "bad" : "good",
        onClick: onShowAlerts,
        source: "ADAPTED",
        value: inventoryKpis ? formatVietnamNumber(inventoryKpis.belowReorder.length) : undefined,
      },
    );
  }

  if (capabilities.inventoryValue) {
    const balances = inventoryData?.balances;
    const balancePage = balances?.data ?? null;
    const partial = balancePage !== null && balancePage.loaded < balancePage.total;

    kpis.push({
      error: balances?.error,
      icon: <Boxes size={18} />,
      label: "Giá trị tồn kho",
      loading: inventoryLoading,
      note: partial
        ? `Ước tính trên ${formatVietnamNumber(balancePage.loaded)}/${formatVietnamNumber(balancePage.total)} dòng tồn`
        : "Theo giá vốn × tồn vật lý",
      noteTone: partial ? "warn" : "neutral",
      source: "ADAPTED",
      value: inventoryKpis ? formatVnd(inventoryKpis.inventoryValue) : undefined,
    });
  }

  if (capabilities.alerts) {
    kpis.push({
      icon: <Bell size={18} />,
      label: "Cảnh báo cần xử lý",
      loading: alertsLoading && alertItems.length === 0,
      note:
        severityCounts.CRITICAL > 0
          ? `${severityCounts.CRITICAL} nghiêm trọng · ${severityCounts.HIGH} cao`
          : alertItems.length > 0
            ? `${severityCounts.HIGH} cao · ${severityCounts.MEDIUM} trung bình`
            : "Không có cảnh báo mở",
      noteTone: severityCounts.CRITICAL > 0 ? "bad" : alertItems.length > 0 ? "warn" : "good",
      onClick: onShowAlerts,
      value: formatVietnamNumber(alertItems.length),
    });
  }

  const demo = salesData?.demo ?? null;
  const trendPoints = demo ? (metric === "revenue" ? demo.revenueTrend : demo.orderTrend) : [];
  const quickWidgets: ReactNode[] = [];

  if (capabilities.sales) {
    quickWidgets.push(
      <DashboardSection
        action={
          demo ? (
            <SegmentedControl
              onChange={setSkuMode}
              options={[
                { key: "revenue", label: "Doanh thu" },
                { key: "quantity", label: "Số lượng" },
              ]}
              value={skuMode}
            />
          ) : undefined
        }
        badge={salesData ? <SourceBadge source={demo ? "DEMO" : "GAP"} /> : undefined}
        key="top-sku"
        title="Top SKU"
      >
        {salesLoading && !salesData ? (
          <DashboardListSkeleton rows={4} />
        ) : demo ? (
          <RankList
            items={[...demo.topSkus]
              .sort((first, second) =>
                skuMode === "revenue" ? second.revenue - first.revenue : second.quantity - first.quantity,
              )
              .map((sku) => ({
                id: sku.id,
                label: sku.skuName,
                sub: `${sku.skuCode} · ${sku.category}`,
                value: skuMode === "revenue" ? sku.revenue : sku.quantity,
                valueText:
                  skuMode === "revenue" ? formatVnd(sku.revenue) : formatVietnamNumber(sku.quantity),
              }))}
          />
        ) : (
          <DashboardGapState message="Cần API tổng hợp SKU bán chạy (DASH-GAP-003)." />
        )}
      </DashboardSection>,
    );
    quickWidgets.push(
      <DashboardSection
        badge={salesData ? <SourceBadge source={demo ? "DEMO" : "GAP"} /> : undefined}
        key="store"
        title="Hiệu suất điểm bán"
      >
        {salesLoading && !salesData ? (
          <DashboardListSkeleton rows={3} />
        ) : demo ? (
          <RankList
            items={demo.storePerformance.map((store) => ({
              id: store.id,
              label: store.storeName,
              sub: `${formatVietnamNumber(store.orderCount)} đơn · AOV ${formatVnd(store.averageOrderValue)}`,
              value: store.revenue,
              valueText: formatVnd(store.revenue),
            }))}
          />
        ) : (
          <DashboardGapState message="Cần API doanh thu theo chi nhánh (DASH-GAP-005)." />
        )}
      </DashboardSection>,
    );
  }

  if (capabilities.inventory) {
    quickWidgets.push(
      <DashboardSection
        action={<DashboardSectionLink to={routePaths.inventoryBalances}>Xem tồn kho</DashboardSectionLink>}
        description="Khả dụng, giữ chỗ và bị chặn trên tồn vật lý"
        key="locations"
        title="Tồn theo địa điểm"
      >
        <DashboardPartView
          empty="Chưa có dữ liệu tồn trong phạm vi đã chọn."
          isEmpty={(data) => data.items.length === 0}
          loading={inventoryLoading}
          onRetry={() => retry("inventory")}
          part={inventoryData?.balances}
          rows={4}
        >
          {(data) => <StackedBar rows={summarizeByLocation(data.items).slice(0, 5)} />}
        </DashboardPartView>
      </DashboardSection>,
    );
  }

  return (
    <div className="grid gap-5">
      {kpis.length > 0 ? (
        <DashboardKpiGrid count={kpis.length}>
          {kpis.map((kpi) => (
            <DashboardKpiCard key={kpi.label} {...kpi} />
          ))}
        </DashboardKpiGrid>
      ) : null}

      {capabilities.sales ? (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] xl:items-start">
          <DashboardSection
            action={
              demo ? (
                <SegmentedControl
                  onChange={setMetric}
                  options={[
                    { key: "revenue", label: "Doanh thu" },
                    { key: "orders", label: "Số đơn" },
                  ]}
                  value={metric}
                />
              ) : undefined
            }
            badge={salesData ? <SourceBadge source={demo ? "DEMO" : "GAP"} /> : undefined}
            description="Theo giờ trong ngày"
            title="Xu hướng bán hàng"
          >
            {salesLoading && !salesData ? (
              <DashboardListSkeleton rows={5} />
            ) : demo ? (
              <TrendChart
                metric={metric}
                points={trendPoints}
                title={metric === "revenue" ? "Doanh thu theo giờ" : "Số đơn theo giờ"}
              />
            ) : (
              <DashboardGapState message="Cần API doanh thu/số đơn theo thời gian (DASH-GAP-002)." />
            )}
          </DashboardSection>

          <DashboardSection
            action={<DashboardSectionLink to={routePaths.orders}>Tất cả đơn</DashboardSectionLink>}
            description="Đơn POS mới nhất hôm nay"
            flush
            title="Đơn gần đây"
          >
            <div className="px-4 pb-4">
              <DashboardPartView
                empty="Chưa có đơn POS nào hôm nay."
                isEmpty={(data) => data.items.length === 0}
                loading={salesLoading}
                onRetry={() => retry("sales")}
                part={salesData?.recentOrders}
                rows={5}
              >
                {(data) => (
                  <DashboardTable
                    columns={[
                      {
                        header: "Đơn",
                        key: "code",
                        render: (order) => (
                          <div className="grid gap-1">
                            <span className="whitespace-nowrap font-black text-[#172317]">{order.orderCode}</span>
                            <span className="flex flex-wrap items-center gap-2 text-xs font-semibold text-[#697684]">
                              <OrderStatusBadge label={order.statusLabel} status={order.status} />
                              {order.cashierName ?? "Chưa có nhân viên"}
                            </span>
                          </div>
                        ),
                      },
                      {
                        align: "right",
                        header: "Tổng tiền",
                        key: "amount",
                        render: (order) => (
                          <span className="whitespace-nowrap font-black text-[#172317]">
                            {capabilities.finance ? formatVnd(numeric(order.finalAmount)) : "—"}
                          </span>
                        ),
                      },
                    ]}
                    getRowKey={(order) => order.orderId}
                    rows={data.items}
                  />
                )}
              </DashboardPartView>
            </div>
          </DashboardSection>
        </div>
      ) : null}

      {quickWidgets.length > 0 ? (
        <div className={widgetRowClass(quickWidgets.length)}>{quickWidgets}</div>
      ) : null}

      {capabilities.alerts ? (
        <DashboardSection
          action={
            alertItems.length > LANDING_ALERT_LIMIT ? (
              <DashboardSectionLink onClick={onShowAlerts}>
                Xem tất cả ({formatVietnamNumber(alertItems.length)})
              </DashboardSectionLink>
            ) : undefined
          }
          description="Ưu tiên theo mức độ: sắp hết hàng, hạn dùng, chứng từ chờ duyệt, giữ chỗ"
          flush
          title="Cần xử lý"
        >
          <div className="px-4 pb-4">
            {alertsLoading && alertItems.length === 0 ? (
              <DashboardListSkeleton rows={4} />
            ) : alertItems.length === 0 ? (
              <InventoryEmptyState message="Không có cảnh báo mở trong phạm vi hiện tại." />
            ) : (
              <AlertPriorityList items={alertItems.slice(0, LANDING_ALERT_LIMIT)} />
            )}
          </div>
        </DashboardSection>
      ) : null}
    </div>
  );
}

export function SegmentedControl<T extends string>({
  onChange,
  options,
  value,
}: {
  onChange: (value: T) => void;
  options: Array<{ key: T; label: string }>;
  value: T;
}) {
  return (
    <div className="inline-flex rounded-lg border border-[#dce5e1] bg-white p-0.5" role="group">
      {options.map((option) => {
        const active = option.key === value;

        return (
          <button
            aria-pressed={active}
            className={`h-7 rounded-md px-2.5 text-xs font-black transition ${
              active ? "bg-[#159734] text-white" : "text-[#536078] hover:bg-[#f4f8f5]"
            }`}
            key={option.key}
            onClick={() => onChange(option.key)}
            type="button"
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
