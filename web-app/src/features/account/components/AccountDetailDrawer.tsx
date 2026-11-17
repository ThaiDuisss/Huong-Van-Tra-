import { Check, Copy, KeyRound, Lock, LockOpen, Pencil, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import { Drawer } from "../../../components/ui/Overlay";
import { accountApi, describeAccountError } from "../services/account.api";
import type { AccountResponse } from "../types/account.types";
import { formatAccountDate, getAccountDisplayName } from "../utils/account.utils";
import { AccountRoleTags, AccountStatusBadge } from "./AccountBadges";

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => setCopied(false), 1500);
    return () => window.clearTimeout(timer);
  }, [copied]);

  return (
    <IconButton
      aria-label={copied ? "Đã sao chép" : "Sao chép"}
      className="text-[#8793a1] hover:text-[#159734]"
      onClick={() => {
        void navigator.clipboard?.writeText(value).then(() => setCopied(true));
      }}
      size="xs"
    >
      {copied ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
    </IconButton>
  );
}

function Field({ children, copyValue, label }: { children: ReactNode; copyValue?: string | null; label: string }) {
  return (
    <div className="grid grid-cols-[120px_minmax(0,1fr)] items-start gap-3 py-2">
      <dt className="pt-0.5 text-xs font-[900] text-[#697684]">{label}</dt>
      <dd className="flex min-w-0 items-start gap-1 text-sm font-semibold text-[#172317]">
        <span className="min-w-0 break-words">{children}</span>
        {copyValue ? <CopyButton value={copyValue} /> : null}
      </dd>
    </div>
  );
}

function Section({ children, title }: { children: ReactNode; title: string }) {
  return (
    <section className="grid gap-1">
      <h3 className="text-[11px] font-black uppercase text-[#159734]">{title}</h3>
      <dl className="divide-y divide-[#edf0f4] rounded-xl border border-[#e8eef0] bg-white px-4">{children}</dl>
    </section>
  );
}



/**
 * Chi tiết tài khoản. Hiển thị ngay dữ liệu của dòng đang chọn, đồng thời tải lại bản mới nhất từ
 * GET /identity/accounts/{id} (vai trò, trạng thái có thể đã đổi trên Keycloak).
 */
export function AccountDetailDrawer({
  account,
  currentUserId,
  escapeDisabled = false,
  onClose,
  onEdit,
  onResetPassword,
  onToggleStatus,
}: {
  account: AccountResponse | null;
  currentUserId?: string;
  /** true khi đang mở dialog phía trên: phím Esc chỉ đóng dialog, không đóng luôn drawer. */
  escapeDisabled?: boolean;
  onClose: () => void;
  onEdit: (account: AccountResponse) => void;
  onResetPassword: (account: AccountResponse) => void;
  onToggleStatus: (account: AccountResponse) => void;
}) {

  // Gắn bản chi tiết với đúng object `account` đã dùng để tải, tránh hiện dữ liệu cũ sau khi sửa/khóa.
  const [fresh, setFresh] = useState<{ source: AccountResponse; detail: AccountResponse } | null>(null);
  const [loadError, setLoadError] = useState<{ id: string; message: string } | null>(null);
  const accountId = account?.id ?? null;



  // `account` đổi tham chiếu sau mỗi lần sửa/khóa nên chi tiết cũng được tải lại.
  useEffect(() => {
    if (!account) return;

    let cancelled = false;
    accountApi
      .detail(account.id)
      .then((detail) => {
        if (cancelled) return;
        setFresh({ detail, source: account });
        setLoadError(null);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setLoadError({
          id: account.id,
          message: describeAccountError(cause, "Không thể tải chi tiết tài khoản."),
        });
      });
    return () => {
      cancelled = true;
    };
  }, [account]);

  if (!account) return null;

  const view = fresh?.source === account ? fresh.detail : account;
  const isSelf = Boolean(currentUserId) && view.id === currentUserId;
  const statusLocked = !view.editable || (isSelf && view.enabled);

  return (
    <Drawer key={accountId} labelledBy="account-detail-title" closeOnEscape={!escapeDisabled} onClose={onClose}>
        <header className="flex items-start justify-between gap-3 border-b border-[#e8eef0] bg-white px-5 py-4">
          <div className="grid min-w-0 gap-1.5">
            <p className="text-xs font-black uppercase text-[#159734]">Chi tiết tài khoản</p>
            <h2 className="break-words text-lg font-black text-[#172317]" id="account-detail-title">
              {getAccountDisplayName(view)}
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <AccountStatusBadge enabled={view.enabled} />
              <span className="text-xs font-semibold text-[#697684]">@{view.username}</span>
              {isSelf ? <span className="text-xs font-black text-[#159734]">Tài khoản của bạn</span> : null}
            </div>
          </div>
          <IconButton aria-label="Đóng" autoFocus onClick={onClose} size="md" variant="secondary">
            <X size={18} aria-hidden="true" />
          </IconButton>
        </header>

        <div className="grid flex-1 content-start gap-4 overflow-y-auto px-5 py-4">
          {loadError?.id === account.id ? (
            <p className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-xs font-bold text-red-700" role="alert">
              {loadError.message} Đang hiển thị dữ liệu từ danh sách.
            </p>
          ) : null}

          {!view.editable ? (
            <p className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-xs font-bold text-amber-800">
              Tài khoản quản trị cấp cao chỉ được xem, không thể chỉnh sửa hoặc khóa tại đây.
            </p>
          ) : null}

          <Section title="Thông tin đăng nhập">
            <Field copyValue={view.username} label="Tên đăng nhập">
              {view.username}
            </Field>
            <Field copyValue={view.email} label="Email">
              {view.email ?? "—"}
              {view.email ? (
                <span className="ml-2 text-xs font-semibold text-[#8793a1]">
                  ({view.emailVerified ? "đã xác minh" : "chưa xác minh"})
                </span>
              ) : null}
            </Field>
            <Field copyValue={view.id} label="Mã người dùng">
              <span className="break-all font-mono text-[13px]">{view.id}</span>
            </Field>
          </Section>

          <Section title="Hồ sơ">
            <Field label="Họ">{view.lastName || "—"}</Field>
            <Field label="Tên">{view.firstName || "—"}</Field>
            <Field label="Ngày tạo">{formatAccountDate(view.createdAt)}</Field>
          </Section>

          <Section title="Phân quyền">
            <Field label="Vai trò">
              <AccountRoleTags roles={view.roles} />
            </Field>
            <Field label="Trạng thái">
              {view.enabled ? "Đang hoạt động, được phép đăng nhập" : "Đã khóa, không thể đăng nhập"}
            </Field>
          </Section>
        </div>

        <footer className="flex flex-wrap gap-2 border-t border-[#e8eef0] bg-white px-5 py-3">
          <Button className="flex-1" disabled={!view.editable} onClick={() => onEdit(view)}>
            <Pencil size={16} aria-hidden="true" />
            Chỉnh sửa
          </Button>
          <Button className="flex-1" disabled={!view.editable} onClick={() => onResetPassword(view)}>
            <KeyRound size={16} aria-hidden="true" />
            Đặt lại mật khẩu
          </Button>
          <Button
            className="flex-1"
            disabled={statusLocked}
            onClick={() => onToggleStatus(view)}
            title={isSelf && view.enabled ? "Bạn không thể tự khóa tài khoản của mình" : undefined}
            variant={view.enabled ? "dangerOutline" : "primary"}
          >
            {view.enabled ? <Lock size={16} aria-hidden="true" /> : <LockOpen size={16} aria-hidden="true" />}
            {view.enabled ? "Khóa tài khoản" : "Mở khóa"}
          </Button>
        </footer>
    </Drawer>
  );
}
