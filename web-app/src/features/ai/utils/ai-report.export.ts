import type { AiBusinessExport, AiBusinessExportInput } from "../types/ai-report.types";

export type BusinessExportRecovery = AiBusinessExportInput & {report?: AiBusinessExport};
const recoveryKey = (actor: string) => `hvt:ai-business-export:${actor}`;

export function readBusinessExportRecovery(actor?: string): BusinessExportRecovery | null {
  if (!actor) return null;
  try {
    const record = JSON.parse(sessionStorage.getItem(recoveryKey(actor)) || "null") as BusinessExportRecovery | null;
    if (!record || !/^[0-9a-f-]{36}$/i.test(record.requestId) || validateBusinessPeriod(record.fromDate, record.toDate)) return null;
    // Reconfirm receipt with the backend when resuming. Do not trust browser-stored file capabilities.
    return {requestId: record.requestId, fromDate: record.fromDate, toDate: record.toDate};
  } catch {return null;}
}

export function saveBusinessExportRecovery(actor: string | undefined, record: BusinessExportRecovery | null): void {
  if (!actor) return;
  try {
    if (record) sessionStorage.setItem(recoveryKey(actor), JSON.stringify({requestId: record.requestId, fromDate: record.fromDate, toDate: record.toDate}));
    else sessionStorage.removeItem(recoveryKey(actor));
  } catch { /* In-memory retry is still available while this dialog is mounted. */ }
}

export function vietnamToday(now = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {timeZone: "Asia/Bangkok", year: "numeric", month: "2-digit", day: "2-digit"}).format(now);
}

export function validateBusinessPeriod(fromDate: string, toDate: string, today = vietnamToday()): string | null {
  const validDate = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value)
    && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
  if (!validDate(fromDate) || !validDate(toDate)) return "Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.";
  if (fromDate > toDate) return "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.";
  if (toDate > today) return "Ngày kết thúc không được sau hôm nay.";
  if ((Date.parse(toDate) - Date.parse(fromDate)) / 86_400_000 >= 366) return "Vui lòng chọn khoảng thời gian không quá 366 ngày.";
  return null;
}

export async function verifyBusinessDownload(blob: Blob, report: AiBusinessExport): Promise<void> {
  const digest = await crypto.subtle.digest("SHA-256", await blob.arrayBuffer());
  const hex = Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, "0")).join("");
  if (hex !== report.sha256) throw new Error("Generated workbook checksum mismatch");
}

export function saveBusinessDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url; link.download = filename;
  document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
