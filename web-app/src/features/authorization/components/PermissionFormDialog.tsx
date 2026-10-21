import { AlertCircle, LoaderCircle } from "lucide-react";
import { type FormEvent, type ReactNode, useState } from "react";

import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import { authorizationApi, describeAuthorizationError } from "../services/authorization.api";
import type { PermissionResponse } from "../types/authorization.types";
import { normalizePermissionName, validatePermissionForm } from "../utils/authorization.utils";
import { fieldClass, primaryButton, secondaryButton } from "./authorization-ui";

/** Tạo mới (không truyền `permission`) hoặc sửa metadata (truyền `permission`; tên không đổi được). */
export function PermissionFormDialog({
  onCancel,
  onSaved,
  permission,
}: {
  onCancel: () => void;
  onSaved: (saved: PermissionResponse, created: boolean) => void | Promise<void>;
  permission?: PermissionResponse;
}) {
  const isEdit = Boolean(permission);
  const [name, setName] = useState(permission?.name ?? "");
  const [displayName, setDisplayName] = useState(permission?.displayName ?? "");
  const [description, setDescription] = useState(permission?.description ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validatePermissionForm({ description, displayName, isEdit, name });
    if (validationError) {
      setError(validationError);
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const payload = { description: description.trim(), displayName: displayName.trim() };
      const saved = permission
        ? await authorizationApi.updatePermission(permission.name, payload)
        : await authorizationApi.createPermission({ ...payload, name: normalizePermissionName(name) });
      await onSaved(saved, !permission);
    } catch (submitError) {
      setError(describeAuthorizationError(submitError, "Không thể lưu quyền. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <DialogContent className="w-[min(640px,calc(100vw-32px))]">
      <DialogHeader>
        <DialogTitle>{isEdit ? "Chỉnh sửa quyền" : "Tạo quyền mới"}</DialogTitle>
        <DialogDescription>
          {isEdit
            ? `Chỉ sửa được tên hiển thị và mô tả. Định danh ${permission?.name} được giữ nguyên để không làm lệch kiểm tra quyền ở backend.`
            : "Quyền được tạo dưới dạng client role trên Keycloak. Định danh dùng dạng module:action và không đổi được sau khi tạo."}
        </DialogDescription>
      </DialogHeader>

      <form noValidate onSubmit={(event) => void submit(event)}>
        <div className="grid max-h-[68vh] gap-5 overflow-y-auto p-6">
          <FormField
            hint={isEdit ? "Định danh không thể thay đổi." : "Chữ thường, số, dấu gạch ngang. Ví dụ: order:create, inventory:receive"}
            label="Định danh"
            required={!isEdit}
          >
            <input
              autoCapitalize="none"
              autoFocus={!isEdit}
              className={`${fieldClass} font-mono`}
              disabled={isEdit}
              maxLength={64}
              onChange={(event) => setName(event.target.value)}
              placeholder="module:action"
              spellCheck={false}
              value={name}
            />
          </FormField>
          <FormField label="Tên hiển thị" required>
            <input
              autoFocus={isEdit}
              className={fieldClass}
              maxLength={100}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="vd: Tạo đơn bán"
              value={displayName}
            />
          </FormField>
          <FormField hint="Tối đa 255 ký tự." label="Mô tả">
            <textarea
              className={`${fieldClass} h-24 resize-none py-2`}
              maxLength={255}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Quyền này cho phép làm gì?"
              value={description}
            />
          </FormField>
          {error ? (
            <div
              className="flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]"
              role="alert"
            >
              <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
              {error}
            </div>
          ) : null}
        </div>
        <DialogFooter>
          <button className={secondaryButton} disabled={submitting} onClick={onCancel} type="button">
            Hủy
          </button>
          <button className={primaryButton} disabled={submitting} type="submit">
            {submitting ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
            {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo quyền"}
          </button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}

function FormField({
  children,
  hint,
  label,
  required = false,
}: {
  children: ReactNode;
  hint?: string;
  label: string;
  required?: boolean;
}) {
  return (
    <label className="grid content-start gap-2">
      <span className="text-xs font-bold text-[#536171]">
        {label}
        {required ? <span className="ml-1 text-[#c4422e]">*</span> : null}
      </span>
      {children}
      {hint ? <span className="text-xs font-semibold text-[#8793a1]">{hint}</span> : null}
    </label>
  );
}
