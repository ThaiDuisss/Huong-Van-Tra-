import type { AiReport, AiReportMetadata } from "../types/ai-report.types";
import { AiReportFormDialog } from "./AiReportFormDialog";

export function AiReportEditDialog({
  report,
  onClose,
  onSubmit,
}: {
  report: AiReport;
  onClose(): void;
  onSubmit?: (values: AiReportMetadata) => Promise<void>;
}) {
  return (
    <AiReportFormDialog
      description="Cập nhật tên và phiên bản báo cáo. Nội dung file được giữ nguyên."
      initialValues={{ name: report.name, version: report.version }}
      onClose={onClose}
      onSubmit={onSubmit}
      submitLabel="Lưu thay đổi"
      title="Chỉnh sửa báo cáo"
    >
      <p className="break-words rounded-lg bg-[#f4faf5] p-3 text-sm font-semibold text-[#536078]">
        File báo cáo: {report.fileName} ({report.fileType})
      </p>
    </AiReportFormDialog>
  );
}
