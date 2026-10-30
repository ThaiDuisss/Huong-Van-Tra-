import { useRef, useState, type FormEvent } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import type { AiReportApi } from "../services/aiReport.api";
import { aiReportErrorMessage } from "../utils/ai-report.errors";
import { readBusinessExportRecovery, saveBusinessExportRecovery, saveBusinessDownload, validateBusinessPeriod, vietnamToday,
  type BusinessExportRecovery } from "../utils/ai-report.export";

export function AiReportExportDialog({api, actor, onClose, onExported}: {
  api: AiReportApi; actor?: string; onClose(): void; onExported(): void;
}) {
  const today = vietnamToday();
  const [recovery] = useState(() => readBusinessExportRecovery(actor));
  const [fromDate, setFromDate] = useState(recovery?.fromDate || today.slice(0, 8) + "01");
  const [toDate, setToDate] = useState(recovery?.toDate || today);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(Boolean(recovery));
  const attempt = useRef<BusinessExportRecovery | null>(recovery);
  const running = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (running.current) return;
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
      onExported();
    } catch (failure) {
      setRetrying(true);
      setError(aiReportErrorMessage(failure, "Không thể xuất báo cáo AI. Vui lòng thử lại."));
    } finally {
      running.current = false; setBusy(false); setStage("");
    }
  }
  return <Dialog open onOpenChange={(open) => {if (!open && !busy) onClose();}}>
    <DialogContent className="w-[min(640px,calc(100vw-32px))]!">
      <form noValidate aria-busy={busy} onSubmit={(event) => void submit(event)}>
        <DialogHeader><DialogTitle>Xuất báo cáo AI</DialogTitle>
          <DialogDescription>Xuất Excel bốn trang từ gợi ý đã ghi nhận và đơn mua thực tế.</DialogDescription></DialogHeader>
        <div className="grid gap-4 px-6 py-5">
          <div className="grid grid-cols-2 gap-4">
            <label className="grid gap-2 text-sm font-semibold">Từ ngày
              <input aria-label="Từ ngày" type="date" value={fromDate} max={today} disabled={busy || retrying}
                className="h-10 rounded-lg border border-pos-border px-3" onChange={(event) => setFromDate(event.target.value)} /></label>
            <label className="grid gap-2 text-sm font-semibold">Đến ngày
              <input aria-label="Đến ngày" type="date" value={toDate} min={fromDate} max={today} disabled={busy || retrying}
                className="h-10 rounded-lg border border-pos-border px-3" onChange={(event) => setToDate(event.target.value)} /></label>
          </div>
          <p className="text-sm leading-6 text-[#536078]">Ngày tính theo giờ Việt Nam. Gợi ý chưa đủ thời gian đánh giá được ghi riêng; báo cáo không xác nhận khách đã xem gợi ý.</p>
          {busy ? <p role="status" className="text-sm font-semibold">{stage}</p> : null}
          {error ? <p role="alert" className="text-sm font-semibold text-red-700">{error}</p> : null}
          {retrying ? <p className="text-sm text-[#536078]">Thử lại sẽ dùng cùng lượt xuất để tránh tạo file trùng.</p> : null}
        </div>
        <DialogFooter>
          <button type="button" disabled={busy} onClick={onClose} className="h-10 rounded-lg border border-pos-border px-4 text-sm font-bold disabled:opacity-50">Đóng</button>
          {retrying ? <button type="button" disabled={busy} className="h-10 rounded-lg border border-pos-border px-4 text-sm font-bold"
            onClick={() => {attempt.current = null; saveBusinessExportRecovery(actor, null); setRetrying(false); setError(null);}}>Chọn kỳ khác</button> : null}
          <button type="submit" disabled={busy} className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-bold text-white disabled:opacity-50">
            {busy ? stage : retrying ? "Thử lại xuất báo cáo" : "Xuất báo cáo AI"}</button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
