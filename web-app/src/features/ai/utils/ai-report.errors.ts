import { ApiError } from "@/lib/api-client";
import type { AiReportImportProgress } from "../types/ai-report.types";
import { validateBomPeriod } from "./ai-report.export";

export class AiReportImportError extends Error {
  readonly retryable: boolean;
  readonly stage: AiReportImportProgress["stage"];
  constructor(stage: AiReportImportProgress["stage"], message: string, retryable: boolean) {
    super(message);
    this.name = "AiReportImportError";
    this.stage = stage;
    this.retryable = retryable;
  }
}

export function aiReportErrorMessage(error: unknown, fallback = "Không thể xử lý báo cáo. Vui lòng thử lại."): string {
  if (error instanceof AiReportImportError) return error.message;
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400: return "Yêu cầu không được chấp nhận. Vui lòng kiểm tra file và thông tin báo cáo.";
      case 401: return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
      case 403: return "Bạn không có quyền thực hiện thao tác này.";
      case 404: return "Không tìm thấy báo cáo hoặc file gốc. Vui lòng làm mới danh sách.";
      case 409: return "File hoặc thông tin báo cáo bị xung đột. Vui lòng kiểm tra lại danh sách.";
      case 415: return "File không phải PDF hoặc XLSX hợp lệ.";
      case 422: return "Thông tin báo cáo không hợp lệ. Vui lòng kiểm tra lại.";
      case 502:
      case 503: return "Dịch vụ đang tạm thời không khả dụng. Vui lòng thử lại sau.";
    }
  }
  return fallback;
}

export function bomExportDataMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 422) return null;
  if (error.code === "BOM_NO_DATA") return "Không có lượt mua BOM trong kỳ đã chọn.";
  if (error.code !== "BOM_PERIOD_UNSUPPORTED") return null;

  const message = "Nguồn dữ liệu BOM mô phỏng chưa hỗ trợ toàn bộ kỳ đã chọn.";
  if (!error.details || typeof error.details !== "object") return message;
  const {fromDate, toDate, timezone} = error.details as Record<string, unknown>;
  if (typeof fromDate !== "string" || typeof toDate !== "string" || timezone !== "UTC"
      || validateBomPeriod(fromDate, toDate)) return message;
  const format = (date: string) => date.split("-").reverse().join("/");
  return `${message} Kỳ có thể đối chiếu: ${format(fromDate)} – ${format(toDate)} (UTC).`;
}
