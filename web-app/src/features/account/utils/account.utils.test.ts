import { describe, expect, it } from "vitest";

import {
  ACCOUNT_PASSWORD_MIN_LENGTH,
  DEFAULT_ACCOUNT_FILTERS,
  formatAccountDate,
  getAccountDisplayName,
  getRoleLabel,
  isDefaultAccountFilters,
  isStaffRole,
  shortAccountId,
  toAccountQuery,
  validateAccountForm,
} from "./account.utils";

const validForm = {
  email: "thu.ngan@hvtra.vn",
  firstName: "Lan",
  lastName: "Nguyễn",
  password: "Temp@1234",
  roles: ["SALE_STAFF"],
  username: "sale.lan01",
};

describe("validateAccountForm", () => {
  it("accepts a complete create form", () => {
    expect(validateAccountForm(validForm)).toBeNull();
  });

  it("checks username length at the 3 and 50 character boundaries", () => {
    expect(validateAccountForm({ ...validForm, username: "ab" })).toMatch(/3 đến 50/);
    expect(validateAccountForm({ ...validForm, username: "abc" })).toBeNull();
    expect(validateAccountForm({ ...validForm, username: "a".repeat(50) })).toBeNull();
    expect(validateAccountForm({ ...validForm, username: "a".repeat(51) })).toMatch(/3 đến 50/);
  });

  it("rejects usernames with spaces or accented letters", () => {
    expect(validateAccountForm({ ...validForm, username: "sale lan" })).toMatch(/không dấu/);
    expect(validateAccountForm({ ...validForm, username: "nhânviên" })).toMatch(/không dấu/);
    expect(validateAccountForm({ ...validForm, username: "a.b_c-d@e" })).toBeNull();
  });

  it("requires last name, first name and a valid email", () => {
    expect(validateAccountForm({ ...validForm, lastName: "  " })).toMatch(/họ/);
    expect(validateAccountForm({ ...validForm, firstName: "" })).toMatch(/tên/);
    expect(validateAccountForm({ ...validForm, email: "no-at-sign" })).toMatch(/Email/);
    expect(validateAccountForm({ ...validForm, email: "a@b" })).toMatch(/Email/);
  });

  it("checks the temporary password length at the minimum boundary", () => {
    const shortPassword = "x".repeat(ACCOUNT_PASSWORD_MIN_LENGTH - 1);
    expect(validateAccountForm({ ...validForm, password: shortPassword })).toMatch(/ít nhất 8/);
    expect(validateAccountForm({ ...validForm, password: "x".repeat(ACCOUNT_PASSWORD_MIN_LENGTH) })).toBeNull();
  });

  it("requires at least one role", () => {
    expect(validateAccountForm({ ...validForm, roles: [] })).toMatch(/vai trò/);
  });

  it("skips username and password checks on the edit form", () => {
    expect(
      validateAccountForm({ email: "a@b.vn", firstName: "A", lastName: "B", roles: ["QC"] }),
    ).toBeNull();
  });
});

describe("account filters and query", () => {
  it("detects the default filter state, ignoring blank keywords", () => {
    expect(isDefaultAccountFilters(DEFAULT_ACCOUNT_FILTERS)).toBe(true);
    expect(isDefaultAccountFilters({ ...DEFAULT_ACCOUNT_FILTERS, keyword: "   " })).toBe(true);
    expect(isDefaultAccountFilters({ ...DEFAULT_ACCOUNT_FILTERS, role: "QC" })).toBe(false);
  });

  it("builds the query with trimmed keyword and boolean status", () => {
    expect(toAccountQuery({ keyword: "  lan ", role: "SALE_STAFF", status: "ENABLED" }, 2, 20)).toEqual({
      enabled: true,
      keyword: "lan",
      page: 2,
      role: "SALE_STAFF",
      size: 20,
    });
    expect(toAccountQuery({ keyword: "", role: "", status: "DISABLED" }, 0, 10)).toEqual({
      enabled: false,
      page: 0,
      size: 10,
    });
    expect(toAccountQuery(DEFAULT_ACCOUNT_FILTERS, 0, 10)).toEqual({ page: 0, size: 10 });
  });
});

describe("account display helpers", () => {
  it("labels known roles and keeps unknown ones", () => {
    expect(getRoleLabel("SALE_STAFF")).toBe("Nhân viên bán hàng");
    expect(getRoleLabel("SUPER_ADMIN")).toBe("Quản trị hệ thống");
    expect(getRoleLabel("CUSTOMER")).toBe("CUSTOMER");
  });

  it("treats SUPER_ADMIN and CUSTOMER as non-assignable roles", () => {
    expect(isStaffRole("MANAGER")).toBe(true);
    expect(isStaffRole("SUPER_ADMIN")).toBe(false);
    expect(isStaffRole("CUSTOMER")).toBe(false);
  });

  it("falls back to the username when the full name is empty", () => {
    expect(getAccountDisplayName({ fullName: "Nguyễn Lan", username: "lan" })).toBe("Nguyễn Lan");
    expect(getAccountDisplayName({ fullName: "  ", username: "lan" })).toBe("lan");
    expect(getAccountDisplayName({ fullName: null, username: "lan" })).toBe("lan");
  });

  it("formats dates and shows a dash for missing or invalid values", () => {
    expect(formatAccountDate(null)).toBe("—");
    expect(formatAccountDate("not-a-date")).toBe("—");
    expect(formatAccountDate("2026-10-26T02:30:00Z")).toMatch(/2026/);
  });

  it("shortens long ids only", () => {
    expect(shortAccountId("1234567890abcdef")).toBe("12345678…");
    expect(shortAccountId("12345678")).toBe("12345678");
    expect(shortAccountId("abcdef", 4)).toBe("abcd…");
  });
});
