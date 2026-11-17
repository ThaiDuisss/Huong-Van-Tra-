import { AlertCircle, Check, Copy, Eye, EyeOff, KeyRound, LoaderCircle, Shuffle } from "lucide-react";
import { useState } from "react";

import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import { Button, IconButton } from "../../../components/ui/Button";
import { TextInput } from "../../../components/ui/Field";
import { accountApi, describeAccountError } from "../services/account.api";
import type { AccountResponse } from "../types/account.types";
import { getAccountDisplayName } from "../utils/account.utils";

const MIN_LENGTH = 8;
const MAX_LENGTH = 128;


// Bỏ các ký tự dễ nhầm khi đọc cho nhân viên (0/O, 1/l/I).
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#%";

function generatePassword(length = 12) {
  const values = new Uint32Array(length);
  window.crypto.getRandomValues(values);
  return Array.from(values, (value) => ALPHABET[value % ALPHABET.length]).join("");
}

/** Admin đặt lại mật khẩu cho nhân viên. Mật khẩu mới là mật khẩu tạm: nhân viên phải đổi ở lần đăng nhập kế tiếp. */
export function AccountResetPasswordDialog({
  account,
  onCancel,
  onReset,
}: {
  account: AccountResponse;
  onCancel: () => void;
  onReset: (account: AccountResponse) => void | Promise<void>;
}) {
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [copied, setCopied] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const tooShort = password.length > 0 && password.length < MIN_LENGTH;
  const valid = password.length >= MIN_LENGTH && password.length <= MAX_LENGTH;

  async function copyPassword() {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Không sao chép được. Vui lòng bật hiển thị và ghi lại mật khẩu.");
    }
  }

  async function confirm() {
    if (!valid) {
      setError(`Mật khẩu mới phải từ ${MIN_LENGTH} đến ${MAX_LENGTH} ký tự.`);
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const updated = await accountApi.resetPassword(account.id, password);
      await onReset(updated);
    } catch (resetError) {
      setError(describeAccountError(resetError, "Không thể đặt lại mật khẩu. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <DialogContent className="w-[min(520px,calc(100vw-32px))]">
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          <KeyRound className="text-[#b7791f]" size={18} aria-hidden="true" />
          Đặt lại mật khẩu
        </DialogTitle>
        <DialogDescription>
          Nhân viên sẽ bị đăng xuất khỏi mọi thiết bị và phải đổi mật khẩu ở lần đăng nhập kế tiếp. Hãy đưa mật khẩu
          mới cho nhân viên qua kênh riêng.
        </DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 px-6 py-5 text-sm font-semibold text-[#425047]">
        <div className="rounded-xl border border-[#eef2f0] bg-[#f8faf9] p-4">
          Đặt lại mật khẩu cho tài khoản{" "}
          <strong className="font-black text-[#172317]">{getAccountDisplayName(account)}</strong> (@{account.username}).
        </div>
        <label className="grid gap-1.5">
          <span className="text-xs font-black text-[#425047]">Mật khẩu mới</span>
          <span className="flex items-center gap-2">
            <TextInput
              aria-invalid={tooShort}
              autoComplete="new-password"
              className="min-w-0 flex-1"
              maxLength={MAX_LENGTH}
              onChange={(event) => {
                setPassword(event.target.value);
                setError("");
              }}
              placeholder={`Từ ${MIN_LENGTH} ký tự`}
              type={visible ? "text" : "password"}
              value={password}
            />
            <IconButton
              aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              onClick={() => setVisible((current) => !current)}
              size="md"
              title={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              variant="secondary"
            >
              {visible ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
            </IconButton>
            <IconButton
              aria-label="Tạo mật khẩu ngẫu nhiên"
              onClick={() => {
                setPassword(generatePassword());
                setVisible(true);
                setError("");
              }}
              size="md"
              title="Tạo mật khẩu ngẫu nhiên"
              variant="secondary"
            >
              <Shuffle size={16} aria-hidden="true" />
            </IconButton>
            <IconButton
              aria-label="Sao chép mật khẩu"
              disabled={!password}
              onClick={() => void copyPassword()}
              size="md"
              title="Sao chép mật khẩu"
              variant="secondary"
            >
              {copied ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}
            </IconButton>
          </span>
          {tooShort ? (
            <span className="text-xs font-bold text-[#a43a2a]">Mật khẩu mới phải từ {MIN_LENGTH} ký tự.</span>
          ) : null}
        </label>
        {error ? (
          <div
            className="flex gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]"
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
        <Button disabled={submitting || !valid} onClick={() => void confirm()} variant="primary">
          {submitting ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
          {submitting ? "Đang xử lý..." : "Đặt lại mật khẩu"}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}
