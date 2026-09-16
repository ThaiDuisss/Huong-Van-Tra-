import type {
  DashboardPaymentBreakdown,
  DashboardExpiringReservation,
  DashboardPendingDeduction,
  DashboardProductionWastage,
  DashboardSkuPerformance,
  DashboardStaffPerformance,
  DashboardStorePerformance,
  DashboardTrendPoint,
} from "../types/dashboard.types";

// TODO(BE): Replace mock with dashboard API when backend endpoint is available.
export const pendingDeductions: DashboardPendingDeduction[] = [
  {
    batchCode: "BATCH-SENCHA-2608",
    createdAt: "2026-09-14T08:12:00+07:00",
    id: "pending-deduction-001",
    locationName: "Kho tổng Hương Vân Trà",
    quantity: 12,
    referenceCode: "ORD-20260914-018",
    skuCode: "TRA-SENCHA-100G",
    source: "ORDER",
    status: "RETRYING",
  },
  {
    batchCode: "BATCH-OOLONG-2607",
    createdAt: "2026-09-13T17:40:00+07:00",
    id: "pending-deduction-002",
    locationName: "Cửa hàng Cầu Giấy",
    quantity: 4,
    referenceCode: "ORD-20260913-044",
    skuCode: "TRA-OOLONG-250G",
    source: "ORDER",
    status: "FAILED",
  },
];

// TODO(BE): Replace mock with GET /api/inventory/reservations list endpoint.
export const expiringReservations: DashboardExpiringReservation[] = [
  {
    batchCode: "BATCH-JASMINE-2609",
    code: "RSV-20260914-007",
    expiresAt: "2026-09-14T21:00:00+07:00",
    id: "reservation-001",
    locationName: "Cửa hàng Tây Mỗ",
    reservedFor: "ORD-20260914-021",
    reservedQuantity: 6,
    skuCode: "TRA-JASMINE-100G",
    status: "RESERVED",
  },
  {
    batchCode: "BATCH-MATCHA-2606",
    code: "RSV-20260914-002",
    expiresAt: "2026-09-15T09:30:00+07:00",
    id: "reservation-002",
    locationName: "Kho tổng Hương Vân Trà",
    reservedFor: "ORD-20260914-006",
    reservedQuantity: 3,
    skuCode: "MATCHA-LATTE-SET",
    status: "RESERVED",
  },
];

// TODO(BE): Replace mock with sales analytics aggregation API.
export const revenueTrend: DashboardTrendPoint[] = [
  { label: "08:00", secondaryValue: 8, source: "MOCK", value: 4800000 },
  { label: "10:00", secondaryValue: 14, source: "MOCK", value: 7600000 },
  { label: "12:00", secondaryValue: 21, source: "MOCK", value: 11800000 },
  { label: "14:00", secondaryValue: 12, source: "MOCK", value: 6300000 },
  { label: "16:00", secondaryValue: 18, source: "MOCK", value: 9200000 },
  { label: "18:00", secondaryValue: 24, source: "MOCK", value: 13700000 },
];

// TODO(BE): Replace mock with order analytics aggregation API.
export const orderTrend: DashboardTrendPoint[] = revenueTrend.map((item) => ({
  label: item.label,
  source: "MOCK",
  value: item.secondaryValue ?? 0,
}));

// TODO(BE): Replace mock with top SKU sales aggregation API.
export const topSkusByRevenue: DashboardSkuPerformance[] = [
  {
    category: "Trà xanh",
    id: "sku-sencha-100g",
    quantity: 42,
    revenue: 12600000,
    skuCode: "TRA-SENCHA-100G",
    skuName: "Trà Sencha 100g",
    source: "MOCK",
  },
  {
    category: "Trà ô long",
    id: "sku-oolong-250g",
    quantity: 31,
    revenue: 11160000,
    skuCode: "TRA-OOLONG-250G",
    skuName: "Trà Ô Long 250g",
    source: "MOCK",
  },
  {
    category: "Set quà",
    id: "sku-gift-premium",
    quantity: 12,
    revenue: 9600000,
    skuCode: "GIFT-PREMIUM-01",
    skuName: "Set quà trà Premium",
    source: "MOCK",
  },
];

// TODO(BE): Replace mock with payment service analytics API.
export const paymentBreakdown: DashboardPaymentBreakdown[] = [
  { label: "Tiền mặt", method: "CASH", revenue: 18400000, status: "MOCK", transactionCount: 38 },
  { label: "Chuyển khoản", method: "BANK_TRANSFER", revenue: 23700000, status: "MOCK", transactionCount: 44 },
  { label: "Ghi nợ", method: "DEBT", revenue: 5200000, status: "MOCK", transactionCount: 7 },
];

// TODO(BE): Replace mock with branch/store scoped sales report.
export const storePerformance: DashboardStorePerformance[] = [
  { averageOrderValue: 426000, id: "store-cau-giay", orderCount: 52, revenue: 22152000, source: "MOCK", storeName: "Cửa hàng Cầu Giấy" },
  { averageOrderValue: 398000, id: "store-tay-mo", orderCount: 41, revenue: 16318000, source: "MOCK", storeName: "Cửa hàng Tây Mỗ" },
  { averageOrderValue: 612000, id: "store-central", orderCount: 17, revenue: 10404000, source: "MOCK", storeName: "Kho tổng / Online" },
];

// TODO(BE): Replace mock with cashier/staff analytics API.
export const staffPerformance: DashboardStaffPerformance[] = [
  { cancelRefundRate: 1.8, id: "staff-lan", orderCount: 43, revenue: 18700000, source: "MOCK", staffName: "Nguyễn Hà Lan" },
  { cancelRefundRate: 2.4, id: "staff-minh", orderCount: 39, revenue: 16400000, source: "MOCK", staffName: "Trần Đức Minh" },
  { cancelRefundRate: 0.9, id: "staff-anh", orderCount: 28, revenue: 12900000, source: "MOCK", staffName: "Phạm Tú Anh" },
];

// TODO(BE): Replace mock with production wastage dashboard API.
export const productionWastage: DashboardProductionWastage[] = [
  {
    actualQuantity: 105.4,
    id: "wastage-mo-001",
    materialCode: "NL-SENCHA-A",
    materialName: "Lá trà Sencha loại A",
    plannedQuantity: 100,
    productionOrderCode: "MO-20260912-006",
    source: "MOCK",
    status: "WATCH",
    thresholdRate: 4,
    wastageQuantity: 5.4,
    wastageRate: 5.4,
  },
  {
    actualQuantity: 80.8,
    id: "wastage-mo-002",
    materialCode: "NL-OOLONG-B",
    materialName: "Lá trà Ô Long B",
    plannedQuantity: 80,
    productionOrderCode: "MO-20260913-002",
    source: "MOCK",
    status: "NORMAL",
    thresholdRate: 3,
    wastageQuantity: 0.8,
    wastageRate: 1,
  },
  {
    actualQuantity: 68.2,
    id: "wastage-mo-003",
    materialCode: "PACK-TIN-100",
    materialName: "Lon thiếc 100g",
    plannedQuantity: 60,
    productionOrderCode: "MO-20260914-001",
    source: "MOCK",
    status: "OVER_THRESHOLD",
    thresholdRate: 5,
    wastageQuantity: 8.2,
    wastageRate: 13.67,
  },
];
