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
  reportType?: "PRODUCT" | "CUSTOM_BOM";
  sourceKind?: "SYNTHETIC";
  researchOnly?: boolean;
  productionApproved?: boolean;
  createdBy?: string;
  status?: "COMPLETE";
  provenance?: {
    kind: string; dataset_id: string; dataset_sha256: string; model_sha256: string; model_version: string;
    model_fingerprint: string; source_kind: string; research_only: boolean; production_approved: boolean;
    protocol: {name: string; mode: string; policy: string; top_k: number; as_of: string; label_end_exclusive: string};
  };
}

export interface AiBusinessExportInput {
  fromDate: string;
  toDate: string;
  requestId: string;
  reportType?: "PRODUCT" | "CUSTOM_BOM";
}
