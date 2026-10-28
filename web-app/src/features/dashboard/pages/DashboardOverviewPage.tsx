import { Bell, Boxes, Factory, Home, RefreshCw, Store } from "lucide-react";
import type { ReactNode } from "react";
import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { routePaths } from "../../../routes/path";
import { InventoryPageHeader } from "../../inventory/components/InventoryManagementComponents";
import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import { DashboardFilterBar } from "../components/DashboardFilterBar";
import { isResourceLoading, resourceData } from "../components/tabs/dashboard-tab-shared";
import { useDashboardData } from "../hooks/useDashboardData";
import type { DashboardFilters, DashboardTab } from "../types/dashboard.types";
import { buildAlertItems } from "../utils/dashboard-alerts";
import { defaultDashboardFilters } from "../utils/dashboard-filters";
import { buildInventoryKpis } from "../utils/dashboard-mappers";
import { getDashboardCapabilities, getVisibleDashboardTabs } from "../utils/dashboard-permissions";

const OverviewTab = lazy(() =>
  import("../components/tabs/OverviewTab").then((m) => ({ default: m.OverviewTab })),
);
const PosTab = lazy(() =>
  import("../components/tabs/PosTab").then((m) => ({ default: m.PosTab })),
);
const InventoryTab = lazy(() =>
  import("../components/tabs/InventoryTab").then((m) => ({ default: m.InventoryTab })),
);
const ProductionTab = lazy(() =>
  import("../components/tabs/ProductionTab").then((m) => ({ default: m.ProductionTab })),
);
const AlertsTab = lazy(() =>
  import("../components/tabs/AlertsTab").then((m) => ({ default: m.AlertsTab })),
);

const tabMeta: Record<DashboardTab, { icon: ReactNode; label: string }> = {
  ALERTS: { icon: <Bell size={16} aria-hidden="true" />, label: "Cảnh báo" },
  INVENTORY: { icon: <Boxes size={16} aria-hidden="true" />, label: "Kho hàng" },
  OVERVIEW: { icon: <Home size={16} aria-hidden="true" />, label: "Tổng quan" },
  POS: { icon: <Store size={16} aria-hidden="true" />, label: "Bán hàng tại cửa hàng" },
  PRODUCTION: { icon: <Factory size={16} aria-hidden="true" />, label: "Sản xuất" },
};

const pathToTabMap: Record<string, DashboardTab> = {
  overview: "OVERVIEW",
  pos: "POS",
  inventory: "INVENTORY",
  production: "PRODUCTION",
  alerts: "ALERTS",
};

const tabToPathMap: Record<DashboardTab, string> = {
  OVERVIEW: routePaths.systemDashboardOverview,
  POS: routePaths.systemDashboardPos,
  INVENTORY: routePaths.systemDashboardInventory,
  PRODUCTION: routePaths.systemDashboardProduction,
  ALERTS: routePaths.systemDashboardAlerts,
};

const timeFormatter = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" });

export function DashboardOverviewPage() {
  const { roles } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const capabilities = useMemo(() => getDashboardCapabilities(roles), [roles]);
  const visibleTabs = useMemo(() => getVisibleDashboardTabs(capabilities), [capabilities]);

  const currentPathSegment = useMemo(() => {
    const parts = location.pathname.split("/").filter(Boolean);
    return parts[parts.length - 1] ?? "";
  }, [location.pathname]);

  const requestedTab = pathToTabMap[currentPathSegment] ?? "OVERVIEW";

  const activeTab = useMemo(() => {
    if (visibleTabs.includes(requestedTab)) {
      return requestedTab;
    }
    return visibleTabs[0] ?? "OVERVIEW";
  }, [requestedTab, visibleTabs]);

  useEffect(() => {
    const isBaseDashboard =
      location.pathname === routePaths.systemDashboard ||
      location.pathname === `${routePaths.systemDashboard}/`;
    const isInvalidTab = !pathToTabMap[currentPathSegment] || !visibleTabs.includes(requestedTab);

    if (isBaseDashboard || isInvalidTab) {
      const targetPath = tabToPathMap[activeTab];
      if (location.pathname !== targetPath) {
        navigate(targetPath, { replace: true });
      }
    }
  }, [location.pathname, currentPathSegment, activeTab, visibleTabs, requestedTab, navigate]);

  const [filters, setFilters] = useState<DashboardFilters>(defaultDashboardFilters);
  const data = useDashboardData({ activeTab, capabilities, filters });

  const inventoryData = resourceData(data.inventory);
  const inventoryKpis = useMemo(() => {
    const balances = inventoryData?.balances.data;
    return balances ? buildInventoryKpis(balances.items) : null;
  }, [inventoryData]);
  const alertSnapshot = resourceData(data.alerts) ?? null;
  const alertItems = useMemo(
    () =>
      buildAlertItems({
        balancesBelowReorder: inventoryKpis?.belowReorder ?? [],
        snapshot: alertSnapshot,
      }),
    [alertSnapshot, inventoryKpis],
  );
  const alertsLoading = isResourceLoading(data.alerts) || isResourceLoading(data.inventory);

  const showAlerts = () => {
    const target = capabilities.alerts
      ? routePaths.systemDashboardAlerts
      : routePaths.systemDashboardOverview;
    navigate(target);
  };

  return (
    <section className="grid gap-5">
      <InventoryPageHeader
        actions={
          <>
            {data.lastUpdated ? (
              <span className="text-xs font-semibold text-[#697684]">
                Cập nhật {timeFormatter.format(data.lastUpdated)}
              </span>
            ) : null}
            <button
              aria-label="Làm mới dữ liệu"
              className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] transition hover:bg-[#f4faf5] disabled:opacity-60"
              disabled={data.isLoading}
              onClick={data.refresh}
              type="button"
            >
              <RefreshCw className={data.isLoading ? "animate-spin" : ""} size={16} aria-hidden="true" />
              Làm mới
            </button>
          </>
        }
        description="Theo dõi tình hình kinh doanh, kho hàng, sản xuất và các cảnh báo khẩn cấp."
        title="Tổng quan kinh doanh & kho"
      />

      <DashboardFilterBar
        filters={filters}
        locations={data.locations}
        onChange={setFilters}
        showLocation={capabilities.inventory}
        showTimeRange={capabilities.sales || capabilities.production}
      />

      {visibleTabs.length > 1 ? (
        <nav aria-label="Khu vực dashboard" className="flex gap-2 overflow-x-auto" role="tablist">
          {visibleTabs.map((tab) => {
            const active = tab === activeTab;
            const count = tab === "ALERTS" ? alertItems.length : 0;

            return (
              <button
                aria-selected={active}
                className={`inline-flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-black transition ${
                  active ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card hover:text-[#172317]"
                }`}
                key={tab}
                onClick={() => navigate(tabToPathMap[tab])}
                role="tab"
                type="button"
              >
                {tabMeta[tab].icon}
                {tabMeta[tab].label}
                {count > 0 ? (
                  <span
                    className={`inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[11px] ${
                      active ? "bg-white/20 text-white" : "bg-red-50 text-red-700"
                    }`}
                  >
                    {formatVietnamNumber(count)}
                  </span>
                ) : null}
              </button>
            );
          })}
        </nav>
      ) : null}

      <div role="tabpanel">
        <Suspense
          fallback={
            <div className="flex h-64 w-full items-center justify-center rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
              <div className="flex items-center gap-3 text-sm font-bold text-[#159734]">
                <RefreshCw className="animate-spin" size={20} aria-hidden="true" />
                <span>Đang tải giao diện...</span>
              </div>
            </div>
          }
        >
          {activeTab === "OVERVIEW" ? (
            <OverviewTab
              alertItems={alertItems}
              alerts={data.alerts}
              capabilities={capabilities}
              filters={filters}
              inventory={data.inventory}
              inventoryKpis={inventoryKpis}
              onShowAlerts={showAlerts}
              retry={data.retry}
              sales={data.sales}
            />
          ) : null}
          {activeTab === "POS" ? (
            <PosTab capabilities={capabilities} filters={filters} retry={data.retry} sales={data.sales} />
          ) : null}
          {activeTab === "INVENTORY" ? (
            <InventoryTab
              alerts={data.alerts}
              inventory={data.inventory}
              inventoryKpis={inventoryKpis}
              onShowAlerts={showAlerts}
              retry={data.retry}
            />
          ) : null}
          {activeTab === "PRODUCTION" ? (
            <ProductionTab filters={filters} production={data.production} retry={data.retry} />
          ) : null}
          {activeTab === "ALERTS" ? (
            <AlertsTab alertItems={alertItems} alerts={data.alerts} loading={alertsLoading} retry={data.retry} />
          ) : null}
        </Suspense>
      </div>
    </section>
  );
}
