import { routePaths } from "../../../routes/path";
import { stockAlertTypeLabels } from "../../inventory/constants/stock-alert.constants";
import { stockDocumentTypeLabels } from "../../inventory/constants/stock-document.constants";
import type { InventoryBalanceViewResponse } from "../../inventory/types/inventory.types";
import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import type {
  DashboardAlertItem,
  DashboardAlertKind,
  DashboardAlertSeverity,
  DashboardAlertSnapshot,
} from "../types/dashboard.types";
import { numeric } from "./dashboard-mappers";

export const dashboardAlertKindLabels: Record<DashboardAlertKind, string> = {
  EXPIRY: "Hạn dùng",
  PENDING_APPROVAL: "Chờ duyệt",
  PENDING_DEDUCTION: "Pending deduction",
  REORDER: "Sắp hết hàng",
  RESERVATION: "Reservation",
  STOCK_ALERT: "Tồn kho",
};

const severityOrder: Record<DashboardAlertSeverity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  LOW: 3,
  MEDIUM: 2,
};

function sortAlerts(items: DashboardAlertItem[]) {
  return [...items].sort((first, second) => {
    const bySeverity = severityOrder[first.severity] - severityOrder[second.severity];
    if (bySeverity !== 0) return bySeverity;
    return new Date(first.occurredAt).getTime() - new Date(second.occurredAt).getTime();
  });
}

/** REAL: SKU có available ≤ reorderPoint từ balance view. */
export function buildReorderAlerts(balances: InventoryBalanceViewResponse[]): DashboardAlertItem[] {
  return balances.map((balance) => {
    const available = numeric(balance.availableQuantity);
    const reorderPoint = numeric(balance.reorderPoint);
    const severity: DashboardAlertSeverity =
      available <= 0 ? "CRITICAL" : available <= reorderPoint / 2 ? "HIGH" : "MEDIUM";

    return {
      id: `reorder-${balance.inventoryBalanceId}`,
      kind: "REORDER",
      kindLabel: dashboardAlertKindLabels.REORDER,
      occurredAt: balance.updatedAt,
      severity,
      source: "REAL",
      subject: `${balance.locationName} · Khả dụng ${formatVietnamNumber(available)} / điểm đặt lại ${formatVietnamNumber(reorderPoint)}`,
      title: `${balance.code ?? balance.productCode ?? ""} ${balance.name ?? balance.productName ?? ""}`.trim(),
      to: routePaths.inventoryBalances,
    };
  });
}

export function buildAlertItems(input: {
  balancesBelowReorder: InventoryBalanceViewResponse[];
  snapshot: DashboardAlertSnapshot | null;
  now?: number;
}): DashboardAlertItem[] {
  const now = input.now ?? Date.now();
  const items: DashboardAlertItem[] = buildReorderAlerts(input.balancesBelowReorder);
  const snapshot = input.snapshot;

  if (!snapshot) {
    return sortAlerts(items);
  }

  (snapshot.stockAlerts.data ?? []).forEach((alert) => {
    items.push({
      id: `stock-alert-${alert.id}`,
      kind: "STOCK_ALERT",
      kindLabel: dashboardAlertKindLabels.STOCK_ALERT,
      occurredAt: alert.createdAt,
      severity: alert.severity,
      source: "DEMO",
      subject: `${alert.sku.skuCode} · ${alert.location.name}`,
      title: stockAlertTypeLabels[alert.type],
      to: `/management/inventory/alerts/${alert.id}`,
    });
  });

  (snapshot.expiringBatches.data ?? []).forEach((batch) => {
    const days = Math.ceil((new Date(batch.expiryDate).getTime() - now) / 86400000);
    const severity: DashboardAlertSeverity =
      days < 0 ? "CRITICAL" : days <= 7 ? "HIGH" : days <= 30 ? "MEDIUM" : "LOW";

    items.push({
      id: `expiry-${batch.id}`,
      kind: "EXPIRY",
      kindLabel: dashboardAlertKindLabels.EXPIRY,
      occurredAt: batch.expiryDate,
      severity,
      source: "DEMO",
      subject: `${batch.skuCode} · ${batch.location.name} · ${days < 0 ? `hết hạn ${Math.abs(days)} ngày` : `còn ${days} ngày`}`,
      title: `Lô ${batch.batchCode}`,
      to: `/management/inventory/quality/batches/${batch.id}`,
    });
  });

  (snapshot.pendingDocuments.data ?? []).forEach((document) => {
    const ageDays = (now - new Date(document.createdAt).getTime()) / 86400000;

    items.push({
      id: `document-${document.id}`,
      kind: "PENDING_APPROVAL",
      kindLabel: dashboardAlertKindLabels.PENDING_APPROVAL,
      occurredAt: document.createdAt,
      severity: ageDays > 3 ? "HIGH" : "MEDIUM",
      source: "DEMO",
      subject: `${stockDocumentTypeLabels[document.type]} · ${document.createdBy.name}`,
      title: document.code,
      to: `/management/inventory/documents/${document.id}`,
    });
  });

  (snapshot.pendingDeductions ?? []).forEach((deduction) => {
    items.push({
      id: `deduction-${deduction.id}`,
      kind: "PENDING_DEDUCTION",
      kindLabel: dashboardAlertKindLabels.PENDING_DEDUCTION,
      occurredAt: deduction.createdAt,
      severity: deduction.status === "FAILED" ? "CRITICAL" : "HIGH",
      source: "DEMO",
      subject: `${deduction.skuCode} · ${deduction.locationName} · SL ${formatVietnamNumber(deduction.quantity)}`,
      title: `${deduction.referenceCode} chưa trừ tồn`,
    });
  });

  (snapshot.expiringReservations ?? []).forEach((reservation) => {
    const hoursLeft = (new Date(reservation.expiresAt).getTime() - now) / 3600000;

    items.push({
      id: `reservation-${reservation.id}`,
      kind: "RESERVATION",
      kindLabel: dashboardAlertKindLabels.RESERVATION,
      occurredAt: reservation.expiresAt,
      severity: hoursLeft <= 6 ? "HIGH" : "MEDIUM",
      source: "DEMO",
      subject: `${reservation.skuCode} · ${reservation.reservedFor} · SL ${formatVietnamNumber(reservation.reservedQuantity)}`,
      title: `${reservation.code} sắp hết hạn giữ chỗ`,
    });
  });

  return sortAlerts(items);
}

export function countBySeverity(items: DashboardAlertItem[]) {
  return items.reduce<Record<DashboardAlertSeverity, number>>(
    (counts, item) => {
      counts[item.severity] += 1;
      return counts;
    },
    { CRITICAL: 0, HIGH: 0, LOW: 0, MEDIUM: 0 },
  );
}
