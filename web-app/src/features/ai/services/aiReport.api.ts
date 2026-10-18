import type { AiReport, AiReportImportInput, AiReportMetadata } from "../types/ai-report.types";

/** Frontend operations to bind only after the business API contract is agreed. */
export interface AiReportApi {
  listAiReports(signal?: AbortSignal): Promise<AiReport[]>;
  createAiReport(input: AiReportImportInput): Promise<AiReport>;
  updateAiReport(id: string, input: AiReportMetadata): Promise<AiReport>;
  /** Original file bytes; preserve the filename supplied by Content-Disposition. */
  downloadAiReport(report: AiReport): Promise<Blob>;
}

export function getAiReportApi(): AiReportApi | null {
  // TODO: Bind listAiReports/createAiReport/updateAiReport/downloadAiReport when
  // the AI Report business contract exists. No endpoint currently implements it.
  // Common-Service provides domain import infrastructure, not report APIs.
  // fileApi.uploadFile supports USER_DOCUMENT (PDF/XLSX, <= 10 MiB), but uploading
  // alone cannot create a report. Do not upload until metadata creation/attachment
  // and failure cleanup are agreed. Private downloads currently require ownership;
  // staff sharing must be authorized by the business API before reusing fileApi.
  // Prefer apiClient.download for an authorized original-file response, preserving
  // Content-Disposition. Do not send gateway auth headers to a presigned URL.
  return null;
}
