export type AiReportFileType = "PDF" | "XLSX";

export interface AiReport {
  id: string;
  name: string;
  version: string;
  fileId: string;
  fileName: string;
  fileType: AiReportFileType;
  createdBy: string;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiReportMetadata {
  name: string;
  version: string;
}

/** Browser selection; converted to the fileId-based business request after completion. */
export interface AiReportImportInput extends AiReportMetadata {
  file: File;
}

export interface AiReportCreate extends AiReportMetadata {
  fileId: string;
}

export interface AiReportPage {
  content: AiReport[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Only completed upload metadata is stored; never bytes, tokens or presigned URLs. */
export interface AiReportRecovery extends AiReportCreate {
  fileName: string;
  fileType: AiReportFileType;
}

export interface AiReportImportProgress {
  stage: "initializing" | "uploading" | "completing" | "importing";
  percent?: number;
}

export interface AiReportImportOperation {
  submit(onProgress?: (progress: AiReportImportProgress) => void,
    onCompletedUpload?: (recovery: AiReportRecovery) => void): Promise<AiReport>;
}

export interface AiBusinessExport {
  id: string;
  fileId: string;
  fileName: string;
  fromDate: string;
  toDate: string;
  generatedAt: string;
  sha256: string;
}

export interface AiBusinessExportInput {
  fromDate: string;
  toDate: string;
  requestId: string;
}
