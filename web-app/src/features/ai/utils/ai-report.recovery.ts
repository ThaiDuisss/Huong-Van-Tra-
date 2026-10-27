import type { AiReportRecovery } from "../types/ai-report.types";
import { validateAiReportMetadata } from "./ai-report.validation";

function key(actor: string) { return `hvtra.ai-report-import:${actor}`; }

export function readAiReportRecovery(actor?: string): AiReportRecovery | null {
  if (!actor) return null;
  try {
    const data = JSON.parse(sessionStorage.getItem(key(actor)) ?? "null");
    if (!data || typeof data.fileId !== "string" || !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(data.fileId)
      || typeof data.name !== "string" || typeof data.version !== "string"
      || typeof data.fileName !== "string" || !data.fileName || data.fileName.length > 255
      || !["PDF","XLSX"].includes(data.fileType) || Object.keys(validateAiReportMetadata(data)).length) return null;
    return {name: data.name, version: data.version, fileId: data.fileId, fileName: data.fileName, fileType: data.fileType};
  } catch { return null; }
}

export function saveAiReportRecovery(actor: string | undefined, value: AiReportRecovery | null) {
  if (!actor) return;
  try {
    if (value) sessionStorage.setItem(key(actor), JSON.stringify({name:value.name,version:value.version,
      fileId:value.fileId,fileName:value.fileName,fileType:value.fileType}));
    else sessionStorage.removeItem(key(actor));
  } catch { /* Browser storage can be disabled; in-memory retry remains available. */ }
}
