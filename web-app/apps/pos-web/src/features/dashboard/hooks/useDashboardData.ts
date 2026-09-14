import { useCallback, useEffect, useRef, useState } from "react";

import { dashboardApi, describeDashboardError } from "../services/dashboard.api";
import type {
  DashboardAlertSnapshot,
  DashboardFilters,
  DashboardInventorySnapshot,
  DashboardProductionSnapshot,
  DashboardResource,
  DashboardSalesSnapshot,
  DashboardTab,
} from "../types/dashboard.types";
import type { DashboardCapabilities } from "../utils/dashboard-permissions";

export type DashboardDomain = "sales" | "inventory" | "alerts" | "production";

export interface DashboardLocationOption {
  id: string;
  name: string;
}

interface UseDashboardDataInput {
  activeTab: DashboardTab;
  capabilities: DashboardCapabilities;
  filters: DashboardFilters;
}

const domainTabs: Record<DashboardDomain, DashboardTab[]> = {
  alerts: ["OVERVIEW", "ALERTS"],
  inventory: ["OVERVIEW", "INVENTORY", "ALERTS"],
  production: ["PRODUCTION"],
  sales: ["OVERVIEW", "POS"],
};

/**
 * Một domain dữ liệu: chỉ tải khi `enabled`, nhớ key đã tải để đổi tab không gọi lại,
 * giữ dữ liệu cũ khi đang tải lại (tránh layout shift), cô lập lỗi theo domain.
 * `loader` phải ổn định (useCallback) theo key để effect không chạy lại mỗi render.
 */
function useDomainResource<T>(input: {
  enabled: boolean;
  key: string;
  loader: () => Promise<T>;
  refreshToken: number;
}) {
  const { enabled, key, loader, refreshToken } = input;
  const [resource, setResource] = useState<DashboardResource<T>>({ status: "idle" });
  const loadedKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }

    const requestKey = `${key}#${refreshToken}`;

    if (loadedKeyRef.current === requestKey) {
      return undefined;
    }

    loadedKeyRef.current = requestKey;
    let active = true;
    let settled = false;

    // Gọi loader sau microtask để lần chạy effect bị StrictMode hủy ngay không tạo request thừa.
    queueMicrotask(() => {
      if (!active) return;
      setResource((current) =>
        current.status === "ready" || current.status === "error"
          ? { data: current.data, status: "loading" }
          : { status: "loading" },
      );

      void loader()
        .then((data) => {
          settled = true;
          if (active) setResource({ data, loadedAt: Date.now(), status: "ready" });
        })
        .catch((error: unknown) => {
          settled = true;
          if (!active) return;
          loadedKeyRef.current = null;
          setResource((current) => ({
            data: current.status === "loading" ? current.data : undefined,
            message: describeDashboardError(error),
            status: "error",
          }));
        });
    });

    return () => {
      active = false;
      // StrictMode chạy effect hai lần ở dev: lần đầu bị hủy thì cho phép lần sau tải lại.
      if (!settled) loadedKeyRef.current = null;
    };
  }, [enabled, key, loader, refreshToken]);

  return resource;
}

/**
 * Tải dữ liệu theo domain, chỉ khi role có quyền và tab đang mở cần domain đó.
 */
export function useDashboardData({ activeTab, capabilities, filters }: UseDashboardDataInput) {
  const [refreshToken, setRefreshToken] = useState(0);
  const [retryTokens, setRetryTokens] = useState<Record<DashboardDomain, number>>({
    alerts: 0,
    inventory: 0,
    production: 0,
    sales: 0,
  });
  const [locations, setLocations] = useState<DashboardLocationOption[]>([]);
  const { locationId, timeRange } = filters;

  const wants = (domain: DashboardDomain) => domainTabs[domain].includes(activeTab);

  const loadSales = useCallback(() => dashboardApi.getSalesSnapshot(timeRange), [timeRange]);
  const loadInventory = useCallback(
    () => dashboardApi.getInventorySnapshot(locationId),
    [locationId],
  );
  const loadAlerts = useCallback(() => dashboardApi.getAlertSnapshot(), []);
  const loadProduction = useCallback(() => dashboardApi.getProductionSnapshot(), []);

  const sales = useDomainResource<DashboardSalesSnapshot>({
    enabled: capabilities.sales && wants("sales"),
    key: timeRange,
    loader: loadSales,
    refreshToken: refreshToken + retryTokens.sales,
  });
  const inventory = useDomainResource<DashboardInventorySnapshot>({
    enabled: capabilities.inventory && wants("inventory"),
    key: locationId,
    loader: loadInventory,
    refreshToken: refreshToken + retryTokens.inventory,
  });
  const alerts = useDomainResource<DashboardAlertSnapshot>({
    enabled: capabilities.alerts && wants("alerts"),
    key: "alerts",
    loader: loadAlerts,
    refreshToken: refreshToken + retryTokens.alerts,
  });
  const production = useDomainResource<DashboardProductionSnapshot>({
    enabled: capabilities.production && wants("production"),
    key: "production",
    loader: loadProduction,
    refreshToken: refreshToken + retryTokens.production,
  });

  useEffect(() => {
    if (!capabilities.inventory) {
      return undefined;
    }

    let active = true;

    queueMicrotask(() => {
      if (!active) return;
      void dashboardApi
        .getLocationOptions()
        .then((options) => {
          if (active) setLocations(options);
        })
        .catch(() => {
          // Không có lookup → ẩn bộ lọc kho, các widget vẫn dùng toàn bộ scope.
          if (active) setLocations([]);
        });
    });

    return () => {
      active = false;
    };
  }, [capabilities.inventory]);

  const loadedAt = [sales, inventory, alerts, production].reduce(
    (latest, item) => (item.status === "ready" ? Math.max(latest, item.loadedAt) : latest),
    0,
  );
  const lastUpdated = loadedAt > 0 ? new Date(loadedAt) : null;

  const refresh = useCallback(() => setRefreshToken((token) => token + 1), []);
  const retry = useCallback((domain: DashboardDomain) => {
    setRetryTokens((tokens) => ({ ...tokens, [domain]: tokens[domain] + 1 }));
  }, []);

  const isLoading = [sales, inventory, alerts, production].some((item) => item.status === "loading");

  return { alerts, inventory, isLoading, lastUpdated, locations, production, refresh, retry, sales };
}
