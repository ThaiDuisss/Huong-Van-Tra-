/** Vai trò nhân viên có thể gán qua màn quản lý tài khoản (không có tiền tố ROLE_). */
export type StaffRole =
  | "ADMIN"
  | "MANAGER"
  | "INVENTORY_STAFF"
  | "SALE_STAFF"
  | "QC"
  | "ACCOUNTANT"
  | "DELIVERY";

/** Vai trò backend có thể trả về: thêm SUPER_ADMIN (chỉ để hiển thị, không gán/sửa được). */
export type AccountRole = StaffRole | "SUPER_ADMIN";

/** Khớp `AccountResponse` của Identity-Service. */
export interface AccountResponse {
  /** Keycloak user id (JWT sub). */
  id: string;
  username: string;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string | null;
  enabled: boolean;
  emailVerified: boolean;
  roles: Array<AccountRole | string>;
  /** ISO-8601 (UTC). */
  createdAt: string | null;
  /** false với tài khoản SUPER_ADMIN: không sửa/khóa được. */
  editable: boolean;
}

export interface AccountPageResponse {
  content: AccountResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type AccountStatusFilter = "" | "ENABLED" | "DISABLED";

/** Trạng thái bộ lọc trên màn hình. Chuỗi rỗng nghĩa là không lọc. */
export interface AccountFilters {
  keyword: string;
  role: StaffRole | "";
  status: AccountStatusFilter;
}

/** Query string của GET /identity/accounts. */
export interface AccountQuery {
  keyword?: string;
  role?: string;
  enabled?: boolean;
  page: number;
  size: number;
}

export interface CreateAccountRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  /** Mật khẩu khởi tạo (tạm thời): người dùng phải đổi ở lần đăng nhập đầu. */
  password: string;
  roles: StaffRole[];
}

export interface UpdateAccountRequest {
  email: string;
  firstName: string;
  lastName: string;
  roles: StaffRole[];
}
