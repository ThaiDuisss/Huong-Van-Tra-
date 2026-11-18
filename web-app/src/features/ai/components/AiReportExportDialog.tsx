import { useEffect, useRef, useState, type FormEvent } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import { ApiError } from "@/lib/api-client";
import type { AiReportApi } from "../services/aiReport.api";
import { aiReportErrorMessage } from "../utils/ai-report.errors";
import { readBomExportRecovery, readBusinessExportRecovery, saveBomExportRecovery, saveBusinessExportRecovery,
  saveBusinessDownload, validateBomPeriod, validateBusinessPeriod, vietnamToday,
  type BomExportPeriod, type BusinessExportRecovery } from "../utils/ai-report.export";

type ReportKind = "PRODUCT" | "CUSTOM_BOM";
const reportKinds = [["PRODUCT", "Theo sản phẩm"], ["CUSTOM_BOM", "Theo BOM"]] as const;
const formatDate = (date: string) => date.split("-").reverse().join("/");

export function AiReportExportDialog({api, actor, onClose, onExported}: {
  api: AiReportApi; actor?: string;
  onClose(): void; onExported(kind: ReportKind): void;
}) {
  const today = vietnamToday();
  const [kind, setKind] = useState<ReportKind>("PRODUCT");
  const isBom = kind === "CUSTOM_BOM";
  const [attempts, setAttempts] = useState<Record<ReportKind, BusinessExportRecovery | null>>(() => ({
    PRODUCT: readBusinessExportRecovery(actor), CUSTOM_BOM: readBomExportRecovery(actor)
  }));
  const [fromDate, setFromDate] = useState(attempts.PRODUCT?.fromDate ?? today.slice(0, 8) + "01");
  const [toDate, setToDate] = useState(attempts.PRODUCT?.toDate ?? today);
  const savedAttempt = attempts[kind];
  const retrying = savedAttempt?.fromDate === fromDate && savedAttempt?.toDate === toDate;
  const saveRecovery = isBom ? saveBomExportRecovery : saveBusinessExportRecovery;
  const [limits, setLimits] = useState<BomExportPeriod | null>(null);
  const [periodError, setPeriodError] = useState<string | null>(null);
  const [periodRetry, setPeriodRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const running = useRef(false);
  const exportController = useRef<AbortController | null>(null);
  useEffect(() => () => exportController.current?.abort(), []);
  useEffect(() => {
    if (!isBom || limits) return;
    const controller = new AbortController();
    api.getBomExportPeriod(controller.signal).then((period) => {
      if (!controller.signal.aborted) setLimits(period);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setPeriodError(aiReportErrorMessage(failure, "Không thể tải phạm vi ngày của báo cáo BOM."));
    });
    return () => controller.abort();
  }, [api, isBom, limits, periodRetry]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (running.current || (isBom && !limits)) return;
    const invalid = isBom ? validateBomPeriod(fromDate, toDate, limits ?? undefined) : validateBusinessPeriod(fromDate, toDate, today);
    if (invalid) {setError(invalid); return;}
    const intent: BusinessExportRecovery = retrying && savedAttempt
      ? {...savedAttempt} : {requestId: crypto.randomUUID(), fromDate, toDate};
    saveRecovery(actor, intent);
    const controller = new AbortController();
    exportController.current = controller;
    running.current = true; setBusy(true); setError(null); setStage("Đang tạo báo cáo...");
    try {
      if (!intent.report) intent.report = await api.exportBusinessReport({requestId: intent.requestId,
        fromDate, toDate, ...(isBom ? {reportType: "CUSTOM_BOM" as const} : {})}, controller.signal);
      if (controller.signal.aborted) return;
      setStage("Đang tải Excel...");
      const blob = await api.downloadBusinessReport(intent.report, controller.signal);
      if (controller.signal.aborted) return;
      saveBusinessDownload(blob, intent.report.fileName);
      saveRecovery(actor, null);
      setAttempts(previous => ({...previous, [kind]: null}));
      onExported(kind);
    } catch (failure) {
      if (controller.signal.aborted) return;
      setAttempts(previous => ({...previous, [kind]: intent}));
      setError(isBom && failure instanceof ApiError && failure.status === 503
        ? "Nguồn dữ liệu báo cáo BOM chưa sẵn sàng. Vui lòng liên hệ quản trị."
        : aiReportErrorMessage(failure, "Không thể xuất báo cáo AI. Vui lòng thử lại cùng lượt xuất."));
    } finally {
      running.current = false;
      if (!controller.signal.aborted) {setBusy(false); setStage("");}
    }
  }

  return <Dialog open onOpenChange={(open) => {if (!open && !busy) onClose();}}>
    <DialogContent className="w-[min(640px,calc(100vw-32px))]! overflow-y-auto!">
      <form noValidate aria-busy={busy} onSubmit={(event) => void submit(event)}>
        <DialogHeader><DialogTitle>Xuất báo cáo AI</DialogTitle>
          <DialogDescription>Chọn loại báo cáo và khoảng thời gian cần xuất.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 px-6 py-5">
          <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-2">
            <legend className="mb-2 text-sm font-semibold">Loại báo cáo</legend>
            {reportKinds.map(([value, label]) => <label key={value}
              className={`flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm font-semibold ${kind === value ? "border-[#159734] bg-[#edf8ef]" : "border-pos-border"}`}>
              <input type="radio" name="ai-report-kind" value={value} checked={kind === value} onChange={() => {
                setKind(value); setError(null); setPeriodError(null);
              }} />{label}
            </label>)}
          </fieldset>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <label className="grid min-w-0 gap-2 text-sm font-semibold">Từ ngày
                <input aria-label="Từ ngày" type="date" value={fromDate} min={isBom ? limits?.fromDate : undefined}
                  max={isBom ? limits?.toDate : today} disabled={busy || retrying}
                  className="h-10 min-w-0 w-full rounded-lg border border-pos-border px-3 disabled:cursor-not-allowed disabled:bg-[#f1f5f3] disabled:text-[#697684]"
                  onChange={(event) => {setFromDate(event.target.value); setError(null);}} /></label>
              <label className="grid min-w-0 gap-2 text-sm font-semibold">Đến ngày
                <input aria-label="Đến ngày" type="date" value={toDate}
                  min={isBom && limits && (fromDate < limits.fromDate || fromDate > limits.toDate) ? limits.fromDate : fromDate}
                  max={isBom ? limits?.toDate : today} disabled={busy || retrying}
                  className="h-10 min-w-0 w-full rounded-lg border border-pos-border px-3 disabled:cursor-not-allowed disabled:bg-[#f1f5f3] disabled:text-[#697684]"
                  onChange={(event) => {setToDate(event.target.value); setError(null);}} /></label>
          </div>
          {isBom && limits ? <p className="text-sm text-[#536078]">
          </p> : null}
          {isBom && !limits && !periodError ? <p role="status" className="text-sm">Đang tải phạm vi ngày...</p> : null}
          {isBom && periodError ? <div className="grid justify-items-start gap-2 text-sm">
            <p role="alert" className="text-red-700">{periodError}</p>
            <button type="button" className="font-semibold text-[#159734] underline" onClick={() => {
              setPeriodError(null); setPeriodRetry((value) => value + 1);
            }}>Tải lại phạm vi ngày</button>
          </div> : null}
          {busy ? <p role="status" className="text-sm font-semibold">{stage}</p> : null}
          {error ? <p role="alert" className="text-sm font-semibold text-red-700">{error}</p> : null}
          {savedAttempt ? <div className="grid justify-items-start gap-2 text-sm text-[#536078]">
            {retrying ? <p>Thử lại sẽ dùng cùng lượt xuất để tránh tạo file trùng.</p> : null}
            <button type="button" disabled={busy} className="font-semibold text-[#159734] underline disabled:opacity-50"
              onClick={() => {
                if (retrying) {
                  saveRecovery(actor, null); setAttempts(previous => ({...previous, [kind]: null}));
                } else {
                  setFromDate(savedAttempt.fromDate); setToDate(savedAttempt.toDate);
                }
                setError(null);
              }}>{retrying ? "Chọn kỳ khác" : "Dùng kỳ đã lưu"}</button>
          </div> : null}
        </div>
        <DialogFooter>
          <button type="button" disabled={busy} onClick={onClose} className="h-10 rounded-lg border border-pos-border px-4 text-sm font-bold disabled:opacity-50">Đóng</button>
          <button type="submit" disabled={(isBom && !limits) || busy} className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-bold text-white disabled:opacity-50">
            Xuất báo cáo</button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
