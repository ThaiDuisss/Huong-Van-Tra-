import { useEffect, useRef, useState, type FormEvent } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import { ApiError } from "@/lib/api-client";
import type { AiReportApi } from "../services/aiReport.api";
import { aiReportErrorMessage } from "../utils/ai-report.errors";
import { BOM_REPORT_FROM, BOM_REPORT_TO, readBomExportRecovery, saveBomExportRecovery,
  readBusinessExportRecovery, saveBusinessExportRecovery, saveBusinessDownload, validateBusinessPeriod, vietnamToday,
  type BusinessExportRecovery } from "../utils/ai-report.export";

type ReportKind = "PRODUCT" | "CUSTOM_BOM";

export function AiReportExportDialog({api, actor, onClose, onExported}: {
  api: AiReportApi; actor?: string;
  onClose(): void; onExported(kind: ReportKind): void;
}) {
  const today = vietnamToday();
  const [kind, setKind] = useState<ReportKind>("PRODUCT");
  const [bomRecovery] = useState(() => readBomExportRecovery(actor));
  const [bomRetrying, setBomRetrying] = useState(Boolean(bomRecovery));
  const bomAttempt = useRef<BusinessExportRecovery | null>(bomRecovery);
  const [recovery] = useState(() => readBusinessExportRecovery(actor));
  const [fromDate, setFromDate] = useState(recovery?.fromDate || today.slice(0, 8) + "01");
  const [toDate, setToDate] = useState(recovery?.toDate || today);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(Boolean(recovery));
  const attempt = useRef<BusinessExportRecovery | null>(recovery);
  const running = useRef(false);
  const bomController = useRef<AbortController | null>(null);
  useEffect(() => () => bomController.current?.abort(), []);
  const isBom = kind === "CUSTOM_BOM";
  const hasRecovery = isBom ? bomRetrying : retrying;

  async function submitBom() {
    if (!bomAttempt.current) bomAttempt.current = {requestId: crypto.randomUUID(), reportType: "CUSTOM_BOM",
      fromDate: BOM_REPORT_FROM, toDate: BOM_REPORT_TO};
    saveBomExportRecovery(actor, bomAttempt.current);
    const controller = new AbortController();
    bomController.current = controller;
    running.current = true; setBusy(true); setError(null); setStage("Đang tạo báo cáo BOM...");
    try {
      const intent = bomAttempt.current;
      if (!intent.report) intent.report = await api.exportBusinessReport({requestId: intent.requestId,
        fromDate: intent.fromDate, toDate: intent.toDate, reportType: "CUSTOM_BOM"}, controller.signal);
      if (controller.signal.aborted) return;
      setStage("Đang tải Excel BOM...");
      const blob = await api.downloadBusinessReport(intent.report, controller.signal);
      if (controller.signal.aborted) return;
      saveBusinessDownload(blob, intent.report.fileName);
      saveBomExportRecovery(actor, null);
      onExported("CUSTOM_BOM");
    } catch (failure) {
      if (controller.signal.aborted) return;
      setBomRetrying(true);
      setError(failure instanceof ApiError && failure.status === 503
        ? "Nguồn dữ liệu báo cáo BOM chưa sẵn sàng. Vui lòng liên hệ quản trị."
        : aiReportErrorMessage(failure, "Không thể xuất báo cáo BOM. Vui lòng thử lại cùng lượt xuất."));
    } finally {
      running.current = false;
      if (!controller.signal.aborted) {setBusy(false); setStage("");}
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (running.current) return;
    if (kind === "CUSTOM_BOM") {await submitBom(); return;}
    const invalid = validateBusinessPeriod(fromDate, toDate);
    if (invalid) {setError(invalid); return;}
    if (!attempt.current) attempt.current = {requestId: crypto.randomUUID(), fromDate, toDate};
    saveBusinessExportRecovery(actor, attempt.current);
    running.current = true; setBusy(true); setError(null);
    try {
      const intent = attempt.current;
      if (!intent.report) {
        setStage("Đang tạo báo cáo...");
        intent.report = await api.exportBusinessReport({requestId: intent.requestId, fromDate: intent.fromDate, toDate: intent.toDate});
      }
      setStage("Đang tải Excel...");
      const blob = await api.downloadBusinessReport(intent.report);
      saveBusinessDownload(blob, intent.report.fileName);
      saveBusinessExportRecovery(actor, null);
      onExported("PRODUCT");
    } catch (failure) {
      setRetrying(true);
      setError(aiReportErrorMessage(failure, "Không thể xuất báo cáo AI. Vui lòng thử lại."));
    } finally {
      running.current = false; setBusy(false); setStage("");
    }
  }
  return <Dialog open onOpenChange={(open) => {if (!open && !busy) onClose();}}>
    <DialogContent className="w-[min(640px,calc(100vw-32px))]! overflow-y-auto!">
      <form noValidate aria-busy={busy} onSubmit={(event) => void submit(event)}>
        <DialogHeader><DialogTitle>Xuất báo cáo AI</DialogTitle>
          <DialogDescription>Chọn loại báo cáo cần xuất.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 px-6 py-5">
          <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-2">
            <legend className="mb-2 text-sm font-semibold">Loại báo cáo</legend>
            {([["PRODUCT", "Theo sản phẩm"], ["CUSTOM_BOM", "Theo BOM"]] as const).map(([value, label]) =>
              <label key={value} className={`flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm font-semibold ${kind === value ? "border-[#159734] bg-[#edf8ef]" : "border-pos-border"}`}>
                <input type="radio" name="ai-report-kind" value={value} checked={kind === value} onChange={() => {
                  setKind(value); setError(null);
                }} />{label}
              </label>)}
          </fieldset>
          <div className="grid gap-2">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <label className="grid min-w-0 gap-2 text-sm font-semibold">Từ ngày
                <input aria-label="Từ ngày" aria-describedby={isBom ? "ai-export-date-note" : undefined}
                  type="date" value={isBom ? "" : fromDate} max={today} disabled={isBom || busy || retrying}
                  className="h-10 min-w-0 w-full rounded-lg border border-pos-border px-3 disabled:cursor-not-allowed disabled:bg-[#f1f5f3] disabled:text-[#697684]"
                  onChange={(event) => setFromDate(event.target.value)} /></label>
              <label className="grid min-w-0 gap-2 text-sm font-semibold">Đến ngày
                <input aria-label="Đến ngày" aria-describedby={isBom ? "ai-export-date-note" : undefined}
                  type="date" value={isBom ? "" : toDate} min={isBom ? undefined : fromDate} max={today} disabled={isBom || busy || retrying}
                  className="h-10 min-w-0 w-full rounded-lg border border-pos-border px-3 disabled:cursor-not-allowed disabled:bg-[#f1f5f3] disabled:text-[#697684]"
                  onChange={(event) => setToDate(event.target.value)} /></label>
            </div>
            {isBom ? <p id="ai-export-date-note" className="text-sm text-[#536078]">
              Báo cáo BOM dùng dữ liệu cố định, chưa hỗ trợ lọc theo ngày.
            </p> : null}
          </div>
          {isBom ? <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-950">
            Dữ liệu mô phỏng (Synthetic), không phản ánh kinh doanh thực tế.
          </p> : null}
          {busy ? <p role="status" className="text-sm font-semibold">{stage}</p> : null}
          {error ? <p role="alert" className="text-sm font-semibold text-red-700">{error}</p> : null}
          {hasRecovery ? <div className="grid justify-items-start gap-2 text-sm text-[#536078]">
            <p>Thử lại sẽ dùng cùng lượt xuất để tránh tạo file trùng.</p>
            <button type="button" disabled={busy} className="font-semibold text-[#159734] underline disabled:opacity-50"
              onClick={() => {
                if (isBom) {bomAttempt.current = null; saveBomExportRecovery(actor, null); setBomRetrying(false);}
                else {attempt.current = null; saveBusinessExportRecovery(actor, null); setRetrying(false);}
                setError(null);
              }}>{isBom ? "Tạo lượt xuất mới" : "Chọn kỳ khác"}</button>
          </div> : null}
        </div>
        <DialogFooter>
          <button type="button" disabled={busy} onClick={onClose} className="h-10 rounded-lg border border-pos-border px-4 text-sm font-bold disabled:opacity-50">Đóng</button>
          <button type="submit" disabled={busy} className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-bold text-white disabled:opacity-50">
            Xuất báo cáo</button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
