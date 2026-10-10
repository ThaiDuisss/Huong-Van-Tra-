import { FileText, Upload, X } from "lucide-react";
import { useId, useRef, useState } from "react";

import type { AiReportImportInput } from "../types/ai-report.types";
import { validateAiReportFile } from "../utils/ai-report.validation";
import { AiReportFormDialog } from "./AiReportFormDialog";

export function AiReportImportDialog({
  onClose,
  onSubmit,
}: {
  onClose(): void;
  onSubmit?: (input: AiReportImportInput) => Promise<void>;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const id = useId();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  function selectFiles(files: File[]) {
    setFile(null);
    if (files.length !== 1) {
      setFileError("Vui lòng chọn một file báo cáo.");
      return;
    }
    const error = validateAiReportFile(files[0]);
    setFileError(error);
    if (!error) setFile(files[0]);
  }

  return (
    <AiReportFormDialog
      description="Chọn file PDF hoặc XLSX và nhập thông tin báo cáo."
      onClose={onClose}
      onSubmit={
        onSubmit
          ? async (values) => {
              if (file) await onSubmit({ ...values, file });
            }
          : undefined
      }
      submitLabel="Nhập báo cáo"
      title="Nhập báo cáo"
      validateExtra={() => {
        if (!file) {
          setFileError("Vui lòng chọn một file báo cáo hợp lệ.");
          return false;
        }
        return true;
      }}
    >
      <div className="grid gap-2">
        <label className="text-xs font-bold text-[#536078]" htmlFor={id}>
          File <span className="text-red-600">*</span>
        </label>
        <div
          className={`grid gap-3 rounded-xl border-2 border-dashed px-4 py-6 text-center transition ${dragging ? "border-[#159734] bg-[#edf8ef]" : "border-[#d8eadc] bg-[#f8fbf8]"}`}
          onDragLeave={() => setDragging(false)}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            selectFiles(Array.from(event.dataTransfer.files));
          }}
        >
          <Upload aria-hidden="true" className="mx-auto text-[#159734]" size={28} />
          <p className="text-sm font-semibold text-[#315f3c]">
            Kéo thả file báo cáo vào đây hoặc chọn file
          </p>
          <input
            accept=".pdf,.xlsx,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            aria-describedby={`${id}-hint${fileError ? ` ${id}-error` : ""}`}
            aria-invalid={Boolean(fileError)}
            className="sr-only"
            id={id}
            onChange={(event) => {
              const files = Array.from(event.target.files ?? []);
              if (files.length) selectFiles(files);
              event.target.value = "";
            }}
            ref={inputRef}
            tabIndex={-1}
            type="file"
          />
          <button
            className="mx-auto h-10 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-bold text-[#159734] hover:bg-[#edf8ef] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159734]"
            onClick={() => inputRef.current?.click()}
            type="button"
          >
            Chọn file
          </button>
          <p className="text-xs font-semibold text-[#697684]" id={`${id}-hint`}>
            PDF hoặc XLSX · Tối đa 10 MiB · Một file mỗi báo cáo
          </p>
        </div>
        {file ? (
          <div className="flex min-w-0 items-center gap-2 rounded-lg border border-[#d8eadc] bg-white p-3 text-sm text-[#315f3c]">
            <FileText aria-hidden="true" className="shrink-0" size={18} />
            <span className="min-w-0 flex-1 truncate" title={file.name}>
              {file.name}
            </span>
            <span className="shrink-0 text-xs text-[#697684]">
              {file.size < 1024
                ? `${file.size} B`
                : file.size < 1024 * 1024
                  ? `${(file.size / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 2 })} KiB`
                  : `${(file.size / 1024 / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 2 })} MiB`}
            </span>
            <button
              aria-label="Bỏ file đã chọn"
              className="rounded-lg p-1 hover:bg-[#f4faf5]"
              onClick={() => {
                setFile(null);
                setFileError(null);
              }}
              type="button"
            >
              <X aria-hidden="true" size={16} />
            </button>
          </div>
        ) : null}
        {fileError ? (
          <p className="text-xs font-semibold text-red-700" id={`${id}-error`} role="alert">
            {fileError}
          </p>
        ) : null}
      </div>
    </AiReportFormDialog>
  );
}
