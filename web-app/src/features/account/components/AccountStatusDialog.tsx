import { AlertCircle, LoaderCircle, Lock, LockOpen } from "lucide-react";
import { useState } from "react";

import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import { Button } from "../../../components/ui/Button";
import { accountApi, describeAccountError } from "../services/account.api";
import type { AccountResponse } from "../types/account.types";
import { getAccountDisplayName } from "../utils/account.utils";



/** Xác nhận khóa / mở khóa tài khoản. */
export function AccountStatusDialog({
  account,
  onCancel,
  onChanged,
}: {
  account: AccountResponse;
  onCancel: () => void;
  onChanged: (account: AccountResponse) => void | Promise<void>;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const disabling = account.enabled;

  async function confirm() {
    setSubmitting(true);
    setError("");
    try {
      const updated = await accountApi.changeStatus(account.id, !account.enabled);
      await onChanged(updated);
    } catch (changeError) {
      setError(describeAccountError(changeError, "Không thể đổi trạng thái tài khoản. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <DialogContent className="w-[min(520px,calc(100vw-32px))]">
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          {disabling ? (
            <Lock className="text-[#c4422e]" size={18} aria-hidden="true" />
          ) : (
            <LockOpen className="text-[#159734]" size={18} aria-hidden="true" />
          )}
          {disabling ? "Khóa tài khoản" : "Mở khóa tài khoản"}
        </DialogTitle>
        <DialogDescription>
          {disabling
            ? "Nhân viên sẽ bị đăng xuất và không thể đăng nhập cho đến khi tài khoản được mở khóa. Dữ liệu liên quan vẫn được giữ lại."
            : "Nhân viên sẽ đăng nhập lại được với mật khẩu và vai trò hiện có."}
        </DialogDescription>
      </DialogHeader>
      <div className="px-6 py-5 text-sm font-semibold text-[#425047]">
        <div className="rounded-xl border border-[#eef2f0] bg-[#f8faf9] p-4">
          Bạn có chắc muốn {disabling ? "khóa" : "mở khóa"} tài khoản{" "}
          <strong className="font-black text-[#172317]">{getAccountDisplayName(account)}</strong> (@{account.username})?
        </div>
        {error ? (
          <div
            className="mt-4 flex gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]"
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
        <Button disabled={submitting} onClick={() => void confirm()} variant={disabling ? "danger" : "primary"}>
          {submitting ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
          {submitting ? "Đang xử lý..." : disabling ? "Khóa tài khoản" : "Mở khóa tài khoản"}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}
