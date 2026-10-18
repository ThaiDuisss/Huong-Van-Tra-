import { useState, type FormEvent, type ReactNode } from "react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import type { AiReportMetadata } from "../types/ai-report.types";
import {
  AI_REPORT_PENDING_MESSAGE,
  validateAiReportMetadata,
  type AiReportMetadataErrors,
} from "../utils/ai-report.validation";
import { AiReportMetadataFields } from "./AiReportMetadataFields";

export function AiReportFormDialog({
  title,
  description,
  submitLabel,
  initialValues = { name: "", version: "" },
  children,
  onClose,
  onSubmit,
  validateExtra,
}: {
  title: string;
  description: string;
  submitLabel: string;
  initialValues?: AiReportMetadata;
  children?: ReactNode;
  onClose(): void;
  onSubmit?: (values: AiReportMetadata) => Promise<void>;
  validateExtra?: () => boolean;
}) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState<AiReportMetadataErrors>({});
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const nextErrors = validateAiReportMetadata(values);
    setErrors(nextErrors);
    const extraValid = validateExtra?.() ?? true;
    if (Object.keys(nextErrors).length || !extraValid || !onSubmit) return;
    setBusy(true);
    setSubmitError(null);
    try {
      await onSubmit({ name: values.name.trim(), version: values.version.trim() });
    } catch {
      setSubmitError("Không thể lưu báo cáo. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onClose();
      }}
    >
      <DialogContent className="w-[min(640px,calc(100vw-32px))]!">
        <form aria-busy={busy} noValidate onSubmit={(event) => void submit(event)}>
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>
          <div className="grid max-h-[calc(90vh-180px)] gap-5 overflow-y-auto px-6 py-5">
            {!onSubmit ? (
              <p
                className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold leading-6 text-amber-800"
                role="status"
              >
                {AI_REPORT_PENDING_MESSAGE} Thông tin và file chưa được lưu.
              </p>
            ) : null}
            <AiReportMetadataFields
              disabled={busy}
              errors={errors}
              onBlur={(field) =>
                setErrors((current) => ({
                  ...current,
                  [field]: validateAiReportMetadata(values)[field],
                }))
              }
              onChange={(field, value) => {
                setValues((current) => ({ ...current, [field]: value }));
                setErrors((current) => ({ ...current, [field]: undefined }));
              }}
              values={values}
            />
            {children}
            {submitError ? (
              <p className="text-sm font-semibold text-red-700" role="alert">
                {submitError}
              </p>
            ) : null}
          </div>
          <DialogFooter>
            <button
              className="h-10 rounded-lg border border-pos-border px-4 text-sm font-bold text-[#536078] hover:bg-neutral-50 disabled:opacity-60"
              disabled={busy}
              onClick={onClose}
              type="button"
            >
              Hủy
            </button>
            <span title={!onSubmit ? AI_REPORT_PENDING_MESSAGE : undefined}>
              <button
                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-bold text-white hover:bg-[#12862d] disabled:cursor-not-allowed disabled:opacity-50"
                disabled={busy || !onSubmit}
                type="submit"
              >
                {busy ? "Đang lưu..." : submitLabel}
              </button>
            </span>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
