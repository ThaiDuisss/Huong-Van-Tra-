/**
 * Mock dashboard-only (xu hướng, top SKU, thanh toán, cửa hàng, nhân viên, hao hụt,
 * pending deduction, reservation) chỉ bật ở môi trường dev hoặc khi
 * VITE_DASHBOARD_MOCK=true. Production mặc định tắt → widget hiển thị trạng thái GAP.
 */
const explicitFlag = import.meta.env.VITE_DASHBOARD_MOCK as string | undefined;

export const isDashboardMockEnabled = explicitFlag === "true";

/** Số dòng balance tối đa tải để tính KPI tồn kho. */
export const DASHBOARD_BALANCE_PAGE_SIZE = 500;

/** Số dòng cho các bảng compact trên dashboard. */
export const DASHBOARD_LIST_SIZE = 6;
