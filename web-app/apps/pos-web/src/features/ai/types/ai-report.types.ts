export type AiReportFileType = "PDF" | "XLSX";

export interface AiReport {
  id: string;
  name: string;
  version: string;
  fileId: string;
  fileName: string;
  fileType: AiReportFileType;
  createdAt: string;
  updatedAt: string;
}

export interface AiReportMetadata {
  name: string;
  version: string;
}

/** Frontend form input, not an agreed backend request DTO. */
export interface AiReportImportInput extends AiReportMetadata {
  file: File;
}
