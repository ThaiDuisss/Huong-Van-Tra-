import { useId } from "react";

import { Field, TextInput } from "../../../components/ui/Field";
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
        <Field
          className="content-start"
          error={errors[field]}
          errorId={`${id}-${field}-error`}
          key={field}
          label={label}
          required
        >
          <TextInput
            aria-describedby={errors[field] ? `${id}-${field}-error` : undefined}
            className="min-w-0"
            disabled={disabled}
            maxLength={maxLength}
            onBlur={() => onBlur(field)}
            onChange={(event) => onChange(field, event.target.value)}
            placeholder={placeholder}
            required
            type="text"
            invalid={Boolean(errors[field])}
            value={values[field]}
          />
        </Field>
      ))}
    </div>
  );
}
