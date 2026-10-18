import type { AccountFilters, AccountQuery, AccountResponse, StaffRole } from "../types/account.types";

export const STAFF_ROLE_OPTIONS: Array<{ value: StaffRole; label: string; description: string }> = [
  { value: "ADMIN", label: "Quản trị viên", description: "Quản trị vận hành, cấu hình hệ thống" },
  { value: "MANAGER", label: "Quản lý", description: "Quản lý cửa hàng, đơn hàng, khuyến mãi" },
  { value: "INVENTORY_STAFF", label: "Nhân viên kho", description: "Nhập, xuất, kiểm kê tồn kho" },
  { value: "SALE_STAFF", label: "Nhân viên bán hàng", description: "Bán hàng tại quầy POS" },
  { value: "QC", label: "Kiểm soát chất lượng", description: "Kiểm tra chất lượng hàng hóa" },
  { value: "ACCOUNTANT", label: "Kế toán", description: "Hóa đơn, đối soát, báo cáo" },
  { value: "DELIVERY", label: "Giao hàng", description: "Giao nhận đơn hàng" },
];

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: "Quản trị cấp cao",
  ...Object.fromEntries(STAFF_ROLE_OPTIONS.map((option) => [option.value, option.label])),
};

const STAFF_ROLE_VALUES = new Set<string>(STAFF_ROLE_OPTIONS.map((option) => option.value));

export function getRoleLabel(role: string) {
  return ROLE_LABELS[role] ?? role;
}

export function isStaffRole(role: string): role is StaffRole {
  return STAFF_ROLE_VALUES.has(role);
}

export const DEFAULT_ACCOUNT_FILTERS: AccountFilters = {
  keyword: "",
  role: "",
  status: "",
};

export function isDefaultAccountFilters(filters: AccountFilters) {
  return !filters.keyword.trim() && !filters.role && !filters.status;
}

export function toAccountQuery(filters: AccountFilters, page: number, size: number): AccountQuery {
  const query: AccountQuery = { page, size };
  const keyword = filters.keyword.trim();
  if (keyword) query.keyword = keyword;
  if (filters.role) query.role = filters.role;
  if (filters.status) query.enabled = filters.status === "ENABLED";
  return query;
}

export function getAccountDisplayName(account: Pick<AccountResponse, "fullName" | "username">) {
  return account.fullName?.trim() || account.username;
}

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  hour: "2-digit",
  hour12: false,
  minute: "2-digit",
  month: "2-digit",
  year: "numeric",
});

export function formatAccountDate(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : dateTimeFormatter.format(date);
}

export function shortAccountId(value: string, length = 8) {
  return value.length > length ? `${value.slice(0, length)}…` : value;
}

const USERNAME_PATTERN = /^[A-Za-z0-9._@-]+$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const ACCOUNT_PASSWORD_MIN_LENGTH = 8;

/** Kiểm tra phía client, khớp với ràng buộc của backend. Trả về thông báo lỗi đầu tiên hoặc null. */
export function validateAccountForm(input: {
  email: string;
  firstName: string;
  lastName: string;
  password?: string;
  roles: readonly string[];
  username?: string;
}) {
  if (input.username !== undefined) {
    const username = input.username.trim();
    if (username.length < 3 || username.length > 50) return "Tên đăng nhập phải từ 3 đến 50 ký tự.";
    if (!USERNAME_PATTERN.test(username)) return "Tên đăng nhập chỉ gồm chữ không dấu, số và các ký tự . _ - @";
  }
  if (!input.lastName.trim()) return "Vui lòng nhập họ.";
  if (!input.firstName.trim()) return "Vui lòng nhập tên.";
  if (!EMAIL_PATTERN.test(input.email.trim())) return "Email không hợp lệ.";
  if (input.password !== undefined && input.password.length < ACCOUNT_PASSWORD_MIN_LENGTH) {
    return `Mật khẩu khởi tạo phải có ít nhất ${ACCOUNT_PASSWORD_MIN_LENGTH} ký tự.`;
  }
  if (input.roles.length === 0) return "Vui lòng chọn ít nhất một vai trò.";
  return null;
}
