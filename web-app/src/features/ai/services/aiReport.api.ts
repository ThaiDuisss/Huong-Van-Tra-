import { ApiError, type ApiClient } from "@hvt/api-client";
import { apiClient } from "../../../api/api-client";
import type { InitUploadResponse } from "../../../api/file.api";
import type { AiReport, AiReportCreate, AiReportImportInput, AiReportImportOperation,
  AiReportImportProgress, AiReportMetadata, AiReportPage, AiReportRecovery } from "../types/ai-report.types";
import { AiReportImportError, aiReportErrorMessage } from "../utils/ai-report.errors";
import { AI_REPORT_CONTENT_TYPES, AI_REPORT_MAX_FILE_BYTES, aiReportContentType,
  aiReportFileType, validateAiReportFile, validateAiReportMetadata } from "../utils/ai-report.validation";
import { putAiReportOriginal } from "./aiReport.upload";
import type { AiBusinessExport, AiBusinessExportInput } from "../types/ai-report.types";
import { validateBusinessPeriod, verifyBusinessDownload } from "../utils/ai-report.export";

const BASE = "/recommendation/ai-reports"; // Existing apiClient base URL already includes /api.

function confirmedReport(value: AiReport): AiReport {
  if (!value || typeof value !== "object" || !["id","name","version","fileId","fileName","createdBy","createdAt","updatedAt"]
    .every((key) => typeof value[key as keyof AiReport] === "string" && Boolean(value[key as keyof AiReport]))
    || !["PDF","XLSX"].includes(value.fileType)
    || !(value.updatedBy === null || typeof value.updatedBy === "string")
    || Number.isNaN(Date.parse(value.createdAt)) || Number.isNaN(Date.parse(value.updatedAt))) {
    throw new Error("Invalid report metadata response");
  }
  return value;
}

export interface AiReportApi {
  exportBusinessReport(input: AiBusinessExportInput): Promise<AiBusinessExport>;
  downloadBusinessReport(report: AiBusinessExport): Promise<Blob>;
  listAiReports(page: number, size: number, signal?: AbortSignal): Promise<AiReportPage>;
  getAiReport(id: string): Promise<AiReport>;
  createAiReport(input: AiReportCreate): Promise<AiReport>;
  prepareImport(input: AiReportImportInput): AiReportImportOperation;
  resumeImport(input: AiReportRecovery): AiReportImportOperation;
  updateAiReport(id: string, input: AiReportMetadata): Promise<AiReport>;
  /** Original file bytes; preserve the filename supplied by Content-Disposition. */
  downloadAiReport(report: AiReport): Promise<Blob>;
}

export function createAiReportApi(client: ApiClient = apiClient, putOriginal = putAiReportOriginal): AiReportApi {
  const api: AiReportApi = {
    async exportBusinessReport(input) {
      const invalid = validateBusinessPeriod(input.fromDate, input.toDate);
      if (invalid) throw new Error(invalid);
      const report = await client.post<AiBusinessExport>(`${BASE}/exports`, input);
      const expectedName = `AI_Report_HuongVanTra_${input.fromDate.replaceAll("-", "")}_${input.toDate.replaceAll("-", "")}.xlsx`;
      if (!report || typeof report.id !== "string" || !report.id || report.fileId !== report.id
        || report.fromDate !== input.fromDate || report.toDate !== input.toDate
        || report.fileName !== expectedName || !/^[0-9a-f]{64}$/.test(report.sha256)
        || Number.isNaN(Date.parse(report.generatedAt))) throw new Error("Generated export was not confirmed");
      return report;
    },
    async downloadBusinessReport(report) {
      const blob = await client.download(`${BASE}/exports/${encodeURIComponent(report.id)}/download`);
      if (!(blob instanceof Blob) || !blob.size || blob.size > AI_REPORT_MAX_FILE_BYTES
        || blob.type !== AI_REPORT_CONTENT_TYPES.XLSX) throw new Error("Invalid generated workbook response");
      await verifyBusinessDownload(blob, report);
      return blob;
    },
    async listAiReports(page, size, signal) {
      const data = await client.get<AiReportPage>(BASE, {query: {page, size}, signal});
      if (!data || !Array.isArray(data.content) || ![data.page,data.size,data.totalElements,data.totalPages]
        .every((value) => Number.isInteger(value) && value >= 0) || data.size === 0
        || typeof data.first !== "boolean" || typeof data.last !== "boolean") throw new Error("Invalid report page response");
      data.content.forEach(confirmedReport);
      return data;
    },
    getAiReport: async (id) => confirmedReport(await client.get<AiReport>(`${BASE}/${encodeURIComponent(id)}`)),
    createAiReport: async ({name, version, fileId}) => confirmedReport(await client.post<AiReport>(BASE, {name, version, fileId})),
    async updateAiReport(id, {name, version}) {
      const data = confirmedReport(await client.patch<AiReport>(`${BASE}/${encodeURIComponent(id)}`, {name, version}));
      if (data.id !== id) throw new Error("Metadata update was not confirmed");
      return data;
    },
    async downloadAiReport(report) {
      const blob = await client.download(`${BASE}/${encodeURIComponent(report.id)}/download`);
      if (!(blob instanceof Blob) || blob.size === 0 || blob.size > AI_REPORT_MAX_FILE_BYTES
        || blob.type !== AI_REPORT_CONTENT_TYPES[report.fileType]) {
        throw new Error("Invalid original-file response");
      }
      return blob;
    },
    prepareImport: (input) => operation(input),
    resumeImport: (input) => operation(undefined, input),
  };

  function operation(input?: AiReportImportInput, completed?: AiReportRecovery): AiReportImportOperation {
    const metadata = {name: (completed ?? input)!.name.trim(), version: (completed ?? input)!.version.trim()};
    let upload: InitUploadResponse | undefined;
    let expiresAt = 0;
    let uploaded = false;
    let recovery = completed ? {...completed, ...metadata} : undefined;
    let report: AiReport | undefined;
    let running: Promise<AiReport> | undefined;
    async function run(onProgress?: (progress: AiReportImportProgress) => void,
      onCompletedUpload?: (value: AiReportRecovery) => void): Promise<AiReport> {
      if (report) return report;
      if (Object.keys(validateAiReportMetadata(metadata)).length) {
        throw new AiReportImportError("initializing", "Vui lòng kiểm tra tên và phiên bản báo cáo.", false);
      }
      let stage: AiReportImportProgress["stage"] = "initializing";
      try {
        if (!recovery) {
          const file = input!.file;
          const validation = validateAiReportFile(file);
          if (validation) throw new AiReportImportError(stage, validation, false);
          const mime = aiReportContentType(file);
          if (!upload) {
            onProgress?.({stage});
            upload = await client.post<InitUploadResponse>("/file/init-upload", {
              originalFilename: file.name, contentType: mime, sizeBytes: file.size,
              purpose: "USER_DOCUMENT", ownerType: "AI_REPORT",
            });
            if (!upload || upload.visibility !== "PRIVATE" || upload.publicUrl || !upload.fileId || !upload.uploadUrl) {
              throw new AiReportImportError(stage, "Không thể khởi tạo file báo cáo riêng tư.", false);
            }
            let capability: URL;
            try { capability = new URL(upload.uploadUrl); }
            catch { throw new AiReportImportError(stage, "Đường dẫn tải file không hợp lệ.", false); }
            if (!["http:","https:"].includes(capability.protocol) || capability.username || capability.password
              || !Number.isFinite(upload.expiresInSeconds) || upload.expiresInSeconds <= 0) {
              throw new AiReportImportError(stage, "Không thể xác minh đường dẫn tải file riêng tư.", false);
            }
            expiresAt = Date.now() + upload.expiresInSeconds * 1000;
          }
          stage = "uploading";
          if (!uploaded) {
            if (Date.now() >= expiresAt) throw new AiReportImportError(stage,
              "Đường dẫn tải lên đã hết hạn. Vui lòng chọn file lại để bắt đầu lượt mới.", false);
            onProgress?.({stage, percent: 0});
            await putOriginal(upload.uploadUrl, file, mime, (percent) => onProgress?.({stage: "uploading", percent}));
            uploaded = true;
          }
          stage = "completing";
          onProgress?.({stage});
          const confirmation = await client.post<string>(`/file/${encodeURIComponent(upload.fileId)}/complete`);
          if (confirmation !== "Success") throw new Error("Upload completion was not confirmed");
          recovery = {...metadata, fileId: upload.fileId, fileName: file.name, fileType: aiReportFileType(file)};
        }
        onCompletedUpload?.({...recovery});
        stage = "importing";
        onProgress?.({stage});
        const created = await api.createAiReport({...metadata, fileId: recovery.fileId});
        if (!created?.id || created.fileId !== recovery.fileId) throw new Error("Import was not confirmed");
        report = created;
        return report;
      } catch (error) {
        if (error instanceof AiReportImportError) throw error;
        if (stage === "uploading" && error instanceof ApiError && error.status >= 400 && error.status < 500) {
          throw new AiReportImportError(stage, "Đường dẫn tải lên hết hạn hoặc bị từ chối. Vui lòng chọn file lại để bắt đầu lượt mới.", false);
        }
        const permanent = error instanceof ApiError && [400,404,409,415,422].includes(error.status);
        const suffix = stage === "importing" && !permanent ? " File gốc đã được lưu; thử lại sẽ dùng chính file này." : "";
        throw new AiReportImportError(stage, aiReportErrorMessage(error) + suffix, !permanent);
      }
    }
    return {submit(onProgress, onCompletedUpload) {
      if (running) return running;
      running = run(onProgress, onCompletedUpload).finally(() => {running = undefined;});
      return running;
    }};
  }
  return api;
}

const connectedApi = createAiReportApi();
export function getAiReportApi(): AiReportApi { return connectedApi; }
