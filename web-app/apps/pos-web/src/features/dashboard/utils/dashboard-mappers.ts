import type { InventoryBalanceViewResponse } from "../../inventory/types/inventory.types";
import type { DashboardLocationSummary } from "../types/dashboard.types";

export function numeric(value: number | string | null | undefined) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function getBlockedQuantity(balance: InventoryBalanceViewResponse) {
  // TODO(BE): Replace client-side calculation with authoritative backend value (DASH-GAP-007).
  return Math.max(
    0,
    numeric(balance.quantityOnHand) -
      numeric(balance.quantityReserved) -
      numeric(balance.availableQuantity),
  );
}

export function summarizeByLocation(
  balances: InventoryBalanceViewResponse[],
): DashboardLocationSummary[] {
  const summaries = new Map<string, DashboardLocationSummary>();

  balances.forEach((balance) => {
    const locationId = String(balance.locationId);
    const current =
      summaries.get(locationId) ??
      {
        available: 0,
        blocked: 0,
        locationId,
        locationName: balance.locationName || "Chưa xác định",
        onHand: 0,
        reserved: 0,
      };

    current.onHand += numeric(balance.quantityOnHand);
    current.reserved += numeric(balance.quantityReserved);
    current.available += numeric(balance.availableQuantity);
    current.blocked += getBlockedQuantity(balance);
    summaries.set(locationId, current);
  });

  return [...summaries.values()].sort((first, second) => second.onHand - first.onHand);
}

export function isBelowReorderPoint(balance: InventoryBalanceViewResponse) {
  const reorderPoint = numeric(balance.reorderPoint);
  return reorderPoint > 0 && numeric(balance.availableQuantity) <= reorderPoint;
}

export interface InventoryKpiSummary {
  skuCount: number;
  totalOnHand: number;
  totalAvailable: number;
  totalReserved: number;
  totalBlocked: number;
  belowReorder: InventoryBalanceViewResponse[];
  outOfStock: InventoryBalanceViewResponse[];
  /** ADAPTED: sum(quantityOnHand × cost_price) trên các dòng đã tải. */
  inventoryValue: number;
}

export function buildInventoryKpis(balances: InventoryBalanceViewResponse[]): InventoryKpiSummary {
  const summary: InventoryKpiSummary = {
    belowReorder: [],
    inventoryValue: 0,
    outOfStock: [],
    skuCount: new Set(balances.map((balance) => balance.inventoryItemId)).size,
    totalAvailable: 0,
    totalBlocked: 0,
    totalOnHand: 0,
    totalReserved: 0,
  };

  balances.forEach((balance) => {
    const available = numeric(balance.availableQuantity);
    const onHand = numeric(balance.quantityOnHand);
    summary.totalOnHand += onHand;
    summary.totalAvailable += available;
    summary.totalReserved += numeric(balance.quantityReserved);
    summary.totalBlocked += getBlockedQuantity(balance);
    summary.inventoryValue += onHand * numeric(balance.cost_price);

    if (available <= 0) {
      summary.outOfStock.push(balance);
    }
    if (isBelowReorderPoint(balance)) {
      summary.belowReorder.push(balance);
    }
  });

  summary.belowReorder.sort(
    (first, second) =>
      numeric(first.availableQuantity) / Math.max(1, numeric(first.reorderPoint)) -
      numeric(second.availableQuantity) / Math.max(1, numeric(second.reorderPoint)),
  );

  return summary;
}

export function buildCategoryBreakdown(balances: InventoryBalanceViewResponse[]) {
  const map = new Map<string, { qty: number; skuSet: Set<string>; value: number }>();
  let grandTotalValue = 0;

  balances.forEach((balance) => {
    const category = balance.categoryName || "Khác";
    const current = map.get(category) ?? { qty: 0, skuSet: new Set<string>(), value: 0 };
    const val = numeric(balance.quantityOnHand) * numeric(balance.cost_price);
    current.skuSet.add(String(balance.inventoryItemId));
    current.value += val;
    current.qty += numeric(balance.quantityOnHand);
    grandTotalValue += val;
    map.set(category, current);
  });

  const result: Array<{
    category: string;
    inventoryValue: number;
    percentage: number;
    skuCount: number;
    source: "ADAPTED";
    totalQuantity: number;
  }> = [];

  map.forEach((item, category) => {
    result.push({
      category,
      inventoryValue: item.value,
      percentage: grandTotalValue > 0 ? (item.value / grandTotalValue) * 100 : 0,
      skuCount: item.skuSet.size,
      source: "ADAPTED",
      totalQuantity: item.qty,
    });
  });

  return result.sort((first, second) => second.inventoryValue - first.inventoryValue);
}


export function formatPercent(value: number, fractionDigits = 1) {
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: fractionDigits }).format(value)}%`;
}

/** Rút gọn VND cho trục chart: 13.700.000 → "13,7 tr". */
export function formatCompactVnd(value: number) {
  const abs = Math.abs(value);
  const format = (amount: number, unit: string) =>
    `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(amount)} ${unit}`;

  if (abs >= 1_000_000_000) return format(value / 1_000_000_000, "tỷ");
  if (abs >= 1_000_000) return format(value / 1_000_000, "tr");
  if (abs >= 1_000) return format(value / 1_000, "k");

  return new Intl.NumberFormat("vi-VN").format(value);
}

export function formatAgeLabel(value: string, now = Date.now()) {
  const timestamp = new Date(value).getTime();

  if (Number.isNaN(timestamp)) return "";

  const diffMinutes = Math.round((now - timestamp) / 60000);

  if (diffMinutes < 0) {
    const ahead = Math.abs(diffMinutes);
    if (ahead < 60) return `còn ${ahead} phút`;
    if (ahead < 60 * 48) return `còn ${Math.round(ahead / 60)} giờ`;
    return `còn ${Math.round(ahead / 1440)} ngày`;
  }

  if (diffMinutes < 60) return `${diffMinutes} phút`;
  if (diffMinutes < 60 * 48) return `${Math.round(diffMinutes / 60)} giờ`;

  return `${Math.round(diffMinutes / 1440)} ngày`;
}
