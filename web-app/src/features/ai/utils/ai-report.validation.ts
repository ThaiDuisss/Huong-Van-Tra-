import type { AiReportMetadata } from "../types/ai-report.types";

export const AI_REPORT_MAX_FILE_BYTES = 10 * 1024 * 1024;
// Frontend input limits; confirm these when the business DTO is specified.
export const AI_REPORT_NAME_MAX_LENGTH = 200;
export const AI_REPORT_VERSION_MAX_LENGTH = 50;

export const AI_REPORT_PENDING_MESSAGE = "Chức năng quản lý báo cáo đang chờ kết nối backend.";

export type AiReportMetadataErrors = Partial<Record<keyof AiReportMetadata, string>>;

export function validateAiReportMetadata(values: AiReportMetadata): AiReportMetadataErrors {
  const errors: AiReportMetadataErrors = {};
  if (!values.name.trim()) errors.name = "Vui lòng nhập tên báo cáo.";
  else if (values.name.trim().length > AI_REPORT_NAME_MAX_LENGTH) {
    errors.name = `Tên báo cáo không được vượt quá ${AI_REPORT_NAME_MAX_LENGTH} ký tự.`;
  }
  if (!values.version.trim()) errors.version = "Vui lòng nhập phiên bản.";
  else if (values.version.trim().length > AI_REPORT_VERSION_MAX_LENGTH) {
    errors.version = `Phiên bản không được vượt quá ${AI_REPORT_VERSION_MAX_LENGTH} ký tự.`;
  }
  return errors;
}

export function validateAiReportFile(file: File): string | null {
  const extension = file.name.match(/\.(pdf|xlsx)$/i)?.[1].toLowerCase();
  const contentTypes: Record<string, string> = {
    pdf: "application/pdf",
    xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  };
  if (!extension || !contentTypes[extension]) return "Chỉ hỗ trợ file PDF hoặc XLSX.";
  if (file.size === 0) return "File đã chọn không có nội dung.";
  if (file.size > AI_REPORT_MAX_FILE_BYTES) return "Dung lượng file không được vượt quá 10 MiB.";
  // Some browsers do not supply a MIME type; keep extension validation in that case.
  if (file.type && file.type !== contentTypes[extension]) {
    return "Định dạng file không khớp với phần mở rộng PDF hoặc XLSX.";
  }
  return null;
}
