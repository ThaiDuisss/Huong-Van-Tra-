import { useEffect, useRef, useState, type FormEvent } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import { Button } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
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
        ? "Nguồn model/dataset BOM chưa được cấu hình hoặc không khả dụng. Vui lòng liên hệ quản trị."
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
          {kind === "PRODUCT" ? <div className="grid grid-cols-2 gap-4">
            <Field label="Từ ngày">
              <TextInput aria-label="Từ ngày" type="date" value={fromDate} max={today} disabled={busy || retrying}
                onChange={(event) => setFromDate(event.target.value)} /></Field>
            <Field label="Đến ngày">
              <TextInput aria-label="Đến ngày" type="date" value={toDate} min={fromDate} max={today} disabled={busy || retrying}
                onChange={(event) => setToDate(event.target.value)} /></Field>
          </div> : <div className="grid gap-3">
            <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-950">
              DỮ LIỆU MÔ PHỎNG (SYNTHETIC) — KHÔNG PHẢI BÁO CÁO KINH DOANH THỰC TẾ.
            </p>
            <p className="text-sm text-[#536078]">BOM 4 sheet · CUSTOM_BOM/REPEAT · Top-5 tại 01/04/2026 UTC, đối chiếu TEST đến trước 01/05/2026 UTC. Kỳ BOM cố định, không dùng kỳ sản phẩm.</p>
            <p className="text-xs leading-5 text-[#536078]">Dùng quyền đăng nhập hiện tại. Bản xuất được lưu theo mã xuất của hệ thống; danh sách báo cáo gốc không tự thêm bản xuất này.</p>
            {bomRetrying ? <p className="text-sm text-[#536078]">Thử lại giữ nguyên mã lượt xuất và tải file đã tạo nếu đã có receipt.</p> : null}
          </div>}
          {busy ? <p role="status" className="text-sm font-semibold">{stage}</p> : null}
          {error ? <p role="alert" className="text-sm font-semibold text-red-700">{error}</p> : null}
          {kind === "PRODUCT" && retrying ? <p className="text-sm text-[#536078]">Thử lại sẽ dùng cùng lượt xuất để tránh tạo file trùng.</p> : null}
        </div>
        <DialogFooter>
          <Button disabled={busy} onClick={onClose}>Đóng</Button>
          {kind === "PRODUCT" && retrying ? <Button disabled={busy}
            onClick={() => {attempt.current = null; saveBusinessExportRecovery(actor, null); setRetrying(false); setError(null);}}>Chọn kỳ khác</Button> : null}
          {kind === "CUSTOM_BOM" && bomRetrying ? <Button disabled={busy}
            onClick={() => {bomAttempt.current = null; saveBomExportRecovery(actor, null); setBomRetrying(false); setError(null);}}>Tạo lượt xuất BOM mới</Button> : null}
          <Button type="submit" disabled={busy} variant="primary">
            {busy ? stage : "Xuất báo cáo"}</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
