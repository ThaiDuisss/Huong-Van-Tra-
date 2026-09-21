import { Check, Copy, Filter, X } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";

import type { ActionLogFilters, ActionLogResponse } from "../types/action-log.types";
import { formatDuration, formatOccurredAt, getServiceLabel, humanizeAction } from "../utils/action-log.utils";
import { ActionLogStatusBadge, HttpMethodTag } from "./ActionLogStatusBadge";

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => setCopied(false), 1500);
    return () => window.clearTimeout(timer);
  }, [copied]);

  return (
    <button
      aria-label={copied ? "Đã sao chép" : "Sao chép"}
      className="inline-flex size-6 shrink-0 items-center justify-center rounded text-[#8793a1] transition hover:bg-[#eef3ef] hover:text-[#159734]"
      onClick={() => {
        void navigator.clipboard?.writeText(value).then(() => setCopied(true));
      }}
      type="button"
    >
      {copied ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
    </button>
  );
}

function Field({
  children,
  copyValue,
  label,
  onFilter,
}: {
  children: ReactNode;
  copyValue?: string | null;
  label: string;
  onFilter?: () => void;
}) {
  return (
    <div className="grid grid-cols-[120px_minmax(0,1fr)] items-start gap-3 py-2">
      <dt className="pt-0.5 text-xs font-[900] text-[#697684]">{label}</dt>
      <dd className="flex min-w-0 items-start gap-1 text-sm font-semibold text-[#172317]">
        <span className="min-w-0 break-words">{children}</span>
        {copyValue ? <CopyButton value={copyValue} /> : null}
        {onFilter ? (
          <button
            aria-label={`Lọc theo ${label.toLowerCase()} này`}
            className="inline-flex size-6 shrink-0 items-center justify-center rounded text-[#8793a1] transition hover:bg-[#eef3ef] hover:text-[#159734]"
            onClick={onFilter}
            title={`Lọc theo ${label.toLowerCase()} này`}
            type="button"
          >
            <Filter size={14} aria-hidden="true" />
          </button>
        ) : null}
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

const mono = "font-mono text-[13px]";

export function ActionLogDetailDrawer({
  log,
  onApplyFilter,
  onClose,
}: {
  log: ActionLogResponse | null;
  onApplyFilter: (patch: Partial<ActionLogFilters>) => void;
  onClose: () => void;
}) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!log) return;

    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [log, onClose]);

  if (!log) return null;

  const applyFilter = (patch: Partial<ActionLogFilters>) => {
    onApplyFilter(patch);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <button
        aria-label="Đóng chi tiết"
        className="absolute inset-0 cursor-default bg-[#0d1b12]/30"
        onClick={onClose}
        tabIndex={-1}
        type="button"
      />
      <aside
        aria-labelledby="action-log-detail-title"
        aria-modal="true"
        className="relative flex h-full w-full max-w-[520px] flex-col bg-[#f7f8fb] shadow-pos-panel"
        role="dialog"
      >
        <header className="flex items-start justify-between gap-3 border-b border-[#e8eef0] bg-white px-5 py-4">
          <div className="grid min-w-0 gap-1.5">
            <p className="text-xs font-black uppercase text-[#159734]">Chi tiết hành động #{log.id}</p>
            <h2 className="break-words text-lg font-black text-[#172317]" id="action-log-detail-title">
              {humanizeAction(log.action)}
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <ActionLogStatusBadge status={log.status} />
              <span className="text-xs font-semibold text-[#697684]">{formatOccurredAt(log.occurredAt)}</span>
            </div>
          </div>
          <button
            aria-label="Đóng"
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-pos-border bg-white text-[#536078] transition hover:border-[#159734] hover:text-[#159734]"
            onClick={onClose}
            ref={closeButtonRef}
            type="button"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </header>

        <div className="grid flex-1 content-start gap-4 overflow-y-auto px-5 py-4">
          {log.status !== "SUCCESS" && log.errorMessage ? (
            <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3" role="alert">
              <p className="text-xs font-black uppercase text-red-700">Lỗi</p>
              <p className="mt-1 break-words text-sm font-semibold text-red-800">{log.errorMessage}</p>
            </div>
          ) : null}

          <Section title="Hành động">
            <Field copyValue={log.action} label="Mã hành động" onFilter={() => applyFilter({ action: log.action })}>
              <span className={mono}>{log.action}</span>
            </Field>
            <Field label="Phân hệ" onFilter={() => applyFilter({ service: log.service })}>
              {getServiceLabel(log.service)} <span className="font-medium text-[#8793a1]">({log.service})</span>
            </Field>
            <Field label="Thời lượng">{formatDuration(log.durationMs)}</Field>
          </Section>

          <Section title="Người thực hiện">
            <Field
              label="Tên đăng nhập"
              onFilter={log.username ? () => applyFilter({ username: log.username ?? "" }) : undefined}
            >
              {log.username ?? "Không rõ"}
            </Field>
            <Field
              copyValue={log.userId}
              label="Mã người dùng"
              onFilter={log.userId ? () => applyFilter({ userId: log.userId ?? "" }) : undefined}
            >
              <span className={mono}>{log.userId ?? "—"}</span>
            </Field>
            <Field copyValue={log.ip} label="Địa chỉ IP">
              <span className={mono}>{log.ip ?? "—"}</span>
            </Field>
          </Section>

          <Section title="Đối tượng bị tác động">
            <Field
              label="Loại"
              onFilter={log.entityType ? () => applyFilter({ entityType: log.entityType ?? "" }) : undefined}
            >
              {log.entityType ?? "—"}
            </Field>
            <Field
              copyValue={log.entityId}
              label="Mã đối tượng"
              onFilter={log.entityId ? () => applyFilter({ entityId: log.entityId ?? "" }) : undefined}
            >
              <span className={mono}>{log.entityId ?? "—"}</span>
            </Field>
            {log.detail ? (
              <Field copyValue={log.detail} label="Chi tiết">
                <span className="whitespace-pre-wrap">{log.detail}</span>
              </Field>
            ) : null}
          </Section>

          <Section title="Request">
            <Field copyValue={log.path} label="Đường dẫn">
              <span className="flex flex-wrap items-center gap-1.5">
                {log.httpMethod ? <HttpMethodTag method={log.httpMethod} /> : null}
                <span className={`${mono} break-all`}>{log.path ?? "—"}</span>
              </span>
            </Field>
            <Field
              copyValue={log.requestId}
              label="Mã request"
              onFilter={log.requestId ? () => applyFilter({ requestId: log.requestId ?? "" }) : undefined}
            >
              <span className={`${mono} break-all`}>{log.requestId ?? "—"}</span>
            </Field>
          </Section>
        </div>
      </aside>
    </div>
  );
}
