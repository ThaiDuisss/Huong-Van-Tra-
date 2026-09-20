import { Bell, Boxes, Factory, Home, RefreshCw, Store } from "lucide-react";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";

import { useAuth } from "../../../auth/auth-context";
import { InventoryPageHeader } from "../../inventory/components/InventoryManagementComponents";
import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import { DashboardFilterBar } from "../components/DashboardFilterBar";
import { AlertsTab } from "../components/tabs/AlertsTab";
import { InventoryTab } from "../components/tabs/InventoryTab";
import { OverviewTab } from "../components/tabs/OverviewTab";
import { PosTab } from "../components/tabs/PosTab";
import { ProductionTab } from "../components/tabs/ProductionTab";
import { isResourceLoading, resourceData } from "../components/tabs/dashboard-tab-shared";
import { useDashboardData } from "../hooks/useDashboardData";
import type { DashboardFilters, DashboardTab } from "../types/dashboard.types";
import { buildAlertItems } from "../utils/dashboard-alerts";
import { defaultDashboardFilters } from "../utils/dashboard-filters";
import { buildInventoryKpis } from "../utils/dashboard-mappers";
import { getDashboardCapabilities, getVisibleDashboardTabs } from "../utils/dashboard-permissions";

const tabMeta: Record<DashboardTab, { icon: ReactNode; label: string }> = {
  ALERTS: { icon: <Bell size={16} aria-hidden="true" />, label: "Cảnh báo" },
  INVENTORY: { icon: <Boxes size={16} aria-hidden="true" />, label: "Kho hàng" },
  OVERVIEW: { icon: <Home size={16} aria-hidden="true" />, label: "Tổng quan" },
  POS: { icon: <Store size={16} aria-hidden="true" />, label: "Bán hàng tại cửa hàng" },
  PRODUCTION: { icon: <Factory size={16} aria-hidden="true" />, label: "Sản xuất" },
};

const timeFormatter = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" });

export function DashboardOverviewPage() {
  const { roles } = useAuth();
  const capabilities = useMemo(() => getDashboardCapabilities(roles), [roles]);
  const visibleTabs = useMemo(() => getVisibleDashboardTabs(capabilities), [capabilities]);
  const [activeTab, setActiveTab] = useState<DashboardTab>("OVERVIEW");
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
  const currentTab = visibleTabs.includes(activeTab) ? activeTab : "OVERVIEW";
  const showAlerts = () => setActiveTab(capabilities.alerts ? "ALERTS" : "OVERVIEW");

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
        showTimeRange={capabilities.sales}
      />

      {visibleTabs.length > 1 ? (
        <nav aria-label="Khu vực dashboard" className="flex gap-2 overflow-x-auto" role="tablist">
          {visibleTabs.map((tab) => {
            const active = tab === currentTab;
            const count = tab === "ALERTS" ? alertItems.length : 0;

            return (
              <button
                aria-selected={active}
                className={`inline-flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-black transition ${
                  active ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card hover:text-[#172317]"
                }`}
                key={tab}
                onClick={() => setActiveTab(tab)}
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
        {currentTab === "OVERVIEW" ? (
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
        {currentTab === "POS" ? (
          <PosTab capabilities={capabilities} filters={filters} retry={data.retry} sales={data.sales} />
        ) : null}
        {currentTab === "INVENTORY" ? (
          <InventoryTab
            alerts={data.alerts}
            inventory={data.inventory}
            inventoryKpis={inventoryKpis}
            onShowAlerts={showAlerts}
            retry={data.retry}
          />
        ) : null}
        {currentTab === "PRODUCTION" ? (
          <ProductionTab production={data.production} retry={data.retry} />
        ) : null}
        {currentTab === "ALERTS" ? (
          <AlertsTab alertItems={alertItems} alerts={data.alerts} loading={alertsLoading} retry={data.retry} />
        ) : null}
      </div>
    </section>
  );
}
