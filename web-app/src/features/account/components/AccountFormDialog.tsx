import { AlertCircle, Eye, EyeOff, LoaderCircle } from "lucide-react";
import { type FormEvent, useState } from "react";

import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import { Button, IconButton } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
import { accountApi, describeAccountError } from "../services/account.api";
import type { AccountResponse, StaffRole } from "../types/account.types";
import {
  ACCOUNT_PASSWORD_MIN_LENGTH,
  STAFF_ROLE_OPTIONS,
  isStaffRole,
  validateAccountForm,
} from "../utils/account.utils";

interface AccountFormDraft {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  roles: StaffRole[];
}

/** Dùng chung cho tạo mới (không truyền `account`) và chỉnh sửa (truyền `account`). */
export function AccountFormDialog({
  account,
  availableRoles,
  onCancel,
  onSaved,
}: {
  account?: AccountResponse;
  /** Vai trò đã tồn tại trong Keycloak; null khi chưa tải được thì cho chọn tất cả. */
  availableRoles: readonly StaffRole[] | null;
  onCancel: () => void;
  onSaved: (account: AccountResponse) => void | Promise<void>;
}) {
  const isEdit = Boolean(account);
  const [draft, setDraft] = useState<AccountFormDraft>(() => ({
    username: account?.username ?? "",
    email: account?.email ?? "",
    firstName: account?.firstName ?? "",
    lastName: account?.lastName ?? "",
    password: "",
    roles: account ? account.roles.filter(isStaffRole) : [],
  }));
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  function update<K extends keyof AccountFormDraft>(key: K, value: AccountFormDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function toggleRole(role: StaffRole) {
    setDraft((current) => ({
      ...current,
      roles: current.roles.includes(role)
        ? current.roles.filter((item) => item !== role)
        : [...current.roles, role],
    }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validationError = validateAccountForm({
      email: draft.email,
      firstName: draft.firstName,
      lastName: draft.lastName,
      roles: draft.roles,
      ...(isEdit ? {} : { password: draft.password, username: draft.username }),
    });
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError("");

    const profile = {
      email: draft.email.trim(),
      firstName: draft.firstName.trim(),
      lastName: draft.lastName.trim(),
      roles: draft.roles,
    };

    try {
      const saved = account
        ? await accountApi.update(account.id, profile)
        : await accountApi.create({ ...profile, password: draft.password, username: draft.username.trim() });
      await onSaved(saved);
    } catch (submitError) {
      setError(describeAccountError(submitError, "Không thể lưu tài khoản. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <DialogContent className="w-[min(760px,calc(100vw-32px))]">
      <DialogHeader>
        <DialogTitle>{isEdit ? "Chỉnh sửa tài khoản" : "Tạo tài khoản nhân viên"}</DialogTitle>
        <DialogDescription>
          {account
            ? `Cập nhật thông tin và vai trò của @${account.username}.`
            : "Tài khoản mới được kích hoạt ngay. Nhân viên phải đổi mật khẩu ở lần đăng nhập đầu tiên."}
        </DialogDescription>
      </DialogHeader>

      <form noValidate onSubmit={(event) => void submit(event)}>
        <div className="grid max-h-[68vh] grid-cols-2 gap-x-5 gap-y-5 overflow-y-auto p-6 max-sm:grid-cols-1">
          <Field
            hint={isEdit ? "Tên đăng nhập không thể thay đổi." : "3–50 ký tự: chữ không dấu, số và . _ - @"}
            label="Tên đăng nhập"
            required={!isEdit}
          >
            <TextInput
              autoCapitalize="none"
              autoComplete="off"
              autoFocus={!isEdit}
              disabled={isEdit}
              maxLength={50}
              onChange={(event) => update("username", event.target.value)}
              placeholder="vd: lan.nguyen"
              spellCheck={false}
              value={draft.username}
            />
          </Field>

          <Field label="Email" required>
            <TextInput
              autoComplete="off"
              maxLength={150}
              onChange={(event) => update("email", event.target.value)}
              placeholder="nhanvien@example.com"
              type="email"
              value={draft.email}
            />
          </Field>

          <Field label="Họ" required>
            <TextInput
              autoFocus={isEdit}
              maxLength={100}
              onChange={(event) => update("lastName", event.target.value)}
              placeholder="Nguyễn Thị"
              value={draft.lastName}
            />
          </Field>

          <Field label="Tên" required>
            <TextInput
              maxLength={100}
              onChange={(event) => update("firstName", event.target.value)}
              placeholder="Lan"
              value={draft.firstName}
            />
          </Field>

          {!isEdit ? (
            <div className="col-span-full">
              <Field
                hint={`Tối thiểu ${ACCOUNT_PASSWORD_MIN_LENGTH} ký tự. Đây là mật khẩu tạm thời, hãy gửi cho nhân viên qua kênh an toàn.`}
                label="Mật khẩu khởi tạo"
                required
              >
                <span className="relative block">
                  <TextInput
                    autoComplete="new-password"
                    className="pr-11"
                    maxLength={128}
                    onChange={(event) => update("password", event.target.value)}
                    type={showPassword ? "text" : "password"}
                    value={draft.password}
                  />
                  <IconButton
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    className="absolute right-1 top-1/2 -translate-y-1/2"
                    onClick={() => setShowPassword((value) => !value)}
                  >
                    {showPassword ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
                  </IconButton>
                </span>
              </Field>
            </div>
          ) : null}

          <fieldset className="col-span-full grid gap-2">
            <legend className="mb-2 text-xs font-bold text-[#536171]">
              Vai trò
              <span className="ml-1 text-[#c4422e]">*</span>
            </legend>
            <div className="grid grid-cols-2 gap-2 max-sm:grid-cols-1">
              {STAFF_ROLE_OPTIONS.map((option) => {
                const checked = draft.roles.includes(option.value);
                // Vai trò chưa có trong Keycloak thì không cho chọn thêm, nhưng vẫn cho bỏ chọn nếu đang có.
                const unavailable = availableRoles !== null && !availableRoles.includes(option.value);
                const disabled = unavailable && !checked;

                return (
                  <label
                    className={`flex items-start gap-3 rounded-xl border px-3 py-2.5 transition-colors ${
                      checked ? "border-[#159734] bg-[#f4faf5]" : "border-[#dce5e1] bg-white"
                    } ${disabled ? "cursor-not-allowed opacity-55" : "cursor-pointer hover:border-[#159734]"}`}
                    key={option.value}
                  >
                    <input
                      checked={checked}
                      className="mt-0.5 size-4 accent-[#159734]"
                      disabled={disabled}
                      onChange={() => toggleRole(option.value)}
                      type="checkbox"
                    />
                    <span className="grid gap-0.5">
                      <span className="text-sm font-bold text-[#172317]">{option.label}</span>
                      <span className="text-xs font-semibold text-[#697684]">
                        {unavailable ? "Vai trò này chưa được tạo trong Keycloak." : option.description}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          {error ? (
            <div
              className="col-span-full flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]"
              role="alert"
            >
              <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
              {error}
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button disabled={submitting} onClick={onCancel}>
            Hủy
          </Button>
          <Button disabled={submitting} type="submit" variant="primary">
            {submitting ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
            {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo tài khoản"}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
