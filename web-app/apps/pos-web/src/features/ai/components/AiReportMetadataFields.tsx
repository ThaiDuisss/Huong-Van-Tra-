import { useId } from "react";

import type { AiReportMetadata } from "../types/ai-report.types";
import {
  AI_REPORT_NAME_MAX_LENGTH,
  AI_REPORT_VERSION_MAX_LENGTH,
  type AiReportMetadataErrors,
} from "../utils/ai-report.validation";

export function AiReportMetadataFields({
  values,
  errors,
  disabled,
  onChange,
  onBlur,
}: {
  values: AiReportMetadata;
  errors: AiReportMetadataErrors;
  disabled: boolean;
  onChange(field: keyof AiReportMetadata, value: string): void;
  onBlur(field: keyof AiReportMetadata): void;
}) {
  const id = useId();
  return (
    <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_160px]">
      {(
        [
          {
            field: "name",
            label: "Tên báo cáo",
            placeholder: "Nhập tên báo cáo",
            maxLength: AI_REPORT_NAME_MAX_LENGTH,
          },
          {
            field: "version",
            label: "Phiên bản",
            placeholder: "Ví dụ: 1.0",
            maxLength: AI_REPORT_VERSION_MAX_LENGTH,
          },
        ] as const
      ).map(({ field, label, placeholder, maxLength }) => (
        <label className="grid content-start gap-1.5 text-xs font-bold text-[#536078]" key={field}>
          <span>
            {label} <span className="text-red-600">*</span>
          </span>
          <input
            aria-describedby={errors[field] ? `${id}-${field}-error` : undefined}
            aria-invalid={Boolean(errors[field])}
            className="h-10 min-w-0 rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734] focus:ring-2 focus:ring-[#159734]/15 disabled:opacity-60"
            disabled={disabled}
            maxLength={maxLength}
            onBlur={() => onBlur(field)}
            onChange={(event) => onChange(field, event.target.value)}
            placeholder={placeholder}
            required
            type="text"
            value={values[field]}
          />
          {errors[field] ? (
            <span className="text-xs text-red-700" id={`${id}-${field}-error`} role="alert">
              {errors[field]}
            </span>
          ) : null}
        </label>
      ))}
    </div>
  );
}
