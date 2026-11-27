import { ApiError } from "@/lib/api-client";
import type { AiReportImportProgress } from "../types/ai-report.types";

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

export function ingredientExportDataMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.status === 422) {
    if (error.code === "INGREDIENT_NO_DATA" || error.code === "NO_DATA") {
      return "Chưa có dữ liệu gợi ý nguyên liệu trong kỳ đã chọn.";
    }
    return "API xuất báo cáo nguyên liệu chưa khả dụng hoặc đang chờ hoàn tất tích hợp từ backend.";
  }
  if (error.status === 404 || error.status === 501) {
    return "API xuất báo cáo nguyên liệu chưa được triển khai trên backend.";
  }
  return null;
}
