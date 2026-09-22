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
    const unitCost = numeric(balance.cost_price) || numeric((balance as any).costPrice) || 0;
    summary.totalOnHand += onHand;
    summary.totalAvailable += available;
    summary.totalReserved += numeric(balance.quantityReserved);
    summary.totalBlocked += getBlockedQuantity(balance);
    summary.inventoryValue += onHand * unitCost;

    if (available <= 0) {
      summary.outOfStock.push(balance);
    } else if (available <= Math.max(1, numeric(balance.reorderPoint))) {
      summary.belowReorder.push(balance);
    }
  });

  summary.outOfStock.sort(
    (first, second) =>
      numeric(first.availableQuantity) - numeric(second.availableQuantity),
  );
  summary.belowReorder.sort(
    (first, second) =>
      numeric(first.availableQuantity) / Math.max(1, numeric(first.reorderPoint)) -
      numeric(second.availableQuantity) / Math.max(1, numeric(second.reorderPoint)),
  );

  return summary;
}

/**
 * Phân bổ phần trăm theo phương pháp Số dư Lớn nhất (Largest Remainder Method / Hare-Niemeyer).
 * Đảm bảo tổng các phần trăm sau khi làm tròn đến `precision` chữ số thập phân LUÔN CHÍNH XÁC bằng 100.0%.
 * Giải quyết triệt để lỗi làm tròn độc lập từng dòng dẫn tới tổng = 99.9% hoặc 100.1%.
 */
export function distributePercentages<T>(
  items: T[],
  getValue: (item: T) => number,
  precision = 1,
): Array<T & { percentage: number }> {
  if (items.length === 0) return [];

  const multiplier = Math.pow(10, precision);
  const targetSum = 100 * multiplier; // Ví dụ 1000 với precision = 1 (tương đương 100.0%)

  const totalValue = items.reduce((sum, item) => sum + Math.max(0, getValue(item)), 0);
  if (totalValue <= 0) {
    return items.map((item) => ({ ...item, percentage: 0 }));
  }

  // 1. Tính tỷ lệ % thô, lấy phần nguyên (floor) và phần dư (remainder)
  const calculated = items.map((item, index) => {
    const val = Math.max(0, getValue(item));
    const rawPct = (val / totalValue) * 100;
    const scaled = rawPct * multiplier;
    const floor = Math.floor(scaled);
    const remainder = scaled - floor;
    return {
      floor,
      index,
      item,
      remainder,
      scaled,
    };
  });

  // 2. Tính phần đơn vị nhỏ nhất còn thiếu do làm tròn xuống
  const currentSum = calculated.reduce((sum, c) => sum + c.floor, 0);
  const diff = Math.round(targetSum - currentSum);

  if (diff > 0) {
    // Phân bổ phần còn thiếu cho các dòng có phần dư lớn nhất (gần chạm ngưỡng làm tròn lên nhất)
    const candidates = calculated
      .filter((c) => c.scaled > 0)
      .sort((a, b) => b.remainder - a.remainder);

    for (let i = 0; i < diff && i < candidates.length; i++) {
      candidates[i].floor += 1;
    }
  } else if (diff < 0) {
    // Trường hợp hiếm khi diff < 0, trừ bớt ở các dòng có phần dư nhỏ nhất
    const candidates = calculated
      .filter((c) => c.floor > 0)
      .sort((a, b) => a.remainder - b.remainder);

    for (let i = 0; i < Math.abs(diff) && i < candidates.length; i++) {
      candidates[i].floor -= 1;
    }
  }

  // 3. Trả về mảng với tỷ trọng phần trăm chính xác (theo thứ tự ban đầu)
  return calculated.map((c) => ({
    ...c.item,
    percentage: c.floor / multiplier,
  }));
}

export function buildCategoryBreakdown(balances: InventoryBalanceViewResponse[]) {
  const map = new Map<string, { qty: number; skuSet: Set<string>; value: number }>();

  balances.forEach((balance) => {
    const category = balance.categoryName || "Khác";
    const current = map.get(category) ?? { qty: 0, skuSet: new Set<string>(), value: 0 };
    const unitCost = numeric(balance.cost_price) || numeric((balance as any).costPrice) || 0;
    const val = numeric(balance.quantityOnHand) * unitCost;
    current.skuSet.add(String(balance.inventoryItemId));
    current.value += val;
    current.qty += numeric(balance.quantityOnHand);
    map.set(category, current);
  });

  const rawList: Array<{
    category: string;
    inventoryValue: number;
    skuCount: number;
    source: "ADAPTED";
    totalQuantity: number;
  }> = [];

  map.forEach((item, category) => {
    rawList.push({
      category,
      inventoryValue: item.value,
      skuCount: item.skuSet.size,
      source: "ADAPTED",
      totalQuantity: item.qty,
    });
  });

  // Sắp xếp giảm dần theo giá trị tồn
  rawList.sort((first, second) => second.inventoryValue - first.inventoryValue);

  // Phân bổ phần trăm chuẩn xác bằng Largest Remainder Method (tổng luôn = 100.0%)
  return distributePercentages(rawList, (item) => item.inventoryValue, 1);
}

export function formatPercent(value: number, fractionDigits = 1) {
  return `${new Intl.NumberFormat("vi-VN", {
    maximumFractionDigits: fractionDigits,
    minimumFractionDigits: fractionDigits,
  }).format(value)}%`;
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
