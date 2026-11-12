import { FileText, Upload, X } from "lucide-react";
import { useId, useRef, useState } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import type { AiReport, AiReportImportOperation, AiReportImportProgress } from "../types/ai-report.types";
import type { AiReportApi } from "../services/aiReport.api";
import { AiReportImportError } from "../utils/ai-report.errors";
import { readAiReportRecovery, saveAiReportRecovery } from "../utils/ai-report.recovery";
import { validateAiReportFile } from "../utils/ai-report.validation";
import { AiReportFormDialog } from "./AiReportFormDialog";

export function AiReportImportDialog({
  onClose,
  onImported,
  api,
  open,
  actor,
}: {
  onClose(): void;
  onImported(report: AiReport): void;
  api: AiReportApi;
  open: boolean;
  actor?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const id = useId();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [recovery, setRecovery] = useState(() => readAiReportRecovery(actor));
  const [operation, setOperation] = useState<AiReportImportOperation | null>(() => recovery ? api.resumeImport(recovery) : null);
  const [progress, setProgress] = useState<AiReportImportProgress | null>(null);
  const [retryable, setRetryable] = useState(true);
  const [formKey, setFormKey] = useState(0);

  function selectFiles(files: File[]) {
    if (operation) return;
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
      key={formKey}
      open={open}
      locked={Boolean(operation)}
      initialValues={recovery ? {name:recovery.name,version:recovery.version} : undefined}
      description="Chọn file PDF hoặc XLSX và nhập thông tin báo cáo."
      onClose={onClose}
      submitDisabled={!retryable}
      onSubmit={async (values) => {
        if (!file && !operation) return;
        const attempt = operation ?? api.prepareImport({...values, file: file!});
        setOperation(attempt);
        try {
          const report = await attempt.submit(setProgress, (saved) => {
            setRecovery(saved);
            saveAiReportRecovery(actor, saved);
          });
          saveAiReportRecovery(actor, null);
          setRecovery(null); setOperation(null); setFile(null); setProgress(null);
          setFormKey((value) => value + 1);
          onImported(report);
        } catch (error) {
          setProgress(null);
          if (error instanceof AiReportImportError) setRetryable(error.retryable);
          throw error;
        }
      }}
      submitLabel={operation ? "Thử lại nhập báo cáo" : "Nhập báo cáo"}
      title="Nhập báo cáo"
      validateExtra={() => {
        if (!file && !operation) {
          setFileError("Vui lòng chọn một file báo cáo hợp lệ.");
          return false;
        }
        return true;
      }}
    >
      <div className="grid gap-2">
        {progress ? <p role="status" className="text-sm font-semibold text-[#315f3c]">
          {progress.stage === "initializing" ? "Đang chuẩn bị tải file..."
            : progress.stage === "uploading" ? `Đang tải file lên: ${progress.percent ?? 0}%`
            : progress.stage === "completing" ? "Đang xác nhận file..." : "Đang lưu thông tin báo cáo..."}
        </p> : null}
        {progress?.stage === "uploading" ? <progress className="w-full accent-[#159734]" aria-label="Tiến trình tải file" max={100} value={progress.percent ?? 0} /> : null}
        {recovery ? <p className="rounded-lg bg-[#edf8ef] p-3 text-sm text-[#315f3c]" role="status">
          File {recovery.fileName} đã được tải lên. Tiếp tục nhập sẽ dùng lại file này.
        </p> : null}
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
            if (!operation) selectFiles(Array.from(event.dataTransfer.files));
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
            disabled={Boolean(operation)}
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
          <Button className="mx-auto" disabled={Boolean(operation)} onClick={() => inputRef.current?.click()}>
            Chọn file
          </Button>
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
            <IconButton
              aria-label="Bỏ file đã chọn"
              disabled={Boolean(operation)}
              onClick={() => {
                setFile(null);
                setFileError(null);
              }}
            >
              <X aria-hidden="true" size={16} />
            </IconButton>
          </div>
        ) : null}
        {fileError ? (
          <p className="text-xs font-semibold text-red-700" id={`${id}-error`} role="alert">
            {fileError}
          </p>
        ) : null}
        {operation ? <Button className="justify-self-start" size="sm" variant="link" onClick={() => {
          saveAiReportRecovery(actor, null);
          setOperation(null); setRecovery(null); setProgress(null); setFile(null);
          setFileError(null); setRetryable(true); setFormKey((value) => value + 1);
        }}>Chọn file khác</Button> : null}
        {operation ? <p className="text-xs text-[#697684]">Chọn file khác sẽ bắt đầu lượt nhập mới. File đã tải lên được giữ lại.</p> : null}
      </div>
    </AiReportFormDialog>
  );
}
