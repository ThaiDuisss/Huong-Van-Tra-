import { Download, RefreshCw, Upload } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { AiReportEditDialog } from "../components/AiReportEditDialog";
import { AiReportImportDialog } from "../components/AiReportImportDialog";
import { AiReportExportDialog } from "../components/AiReportExportDialog";
import { AiReportTable, type AiReportListState } from "../components/AiReportTable";
import { getAiReportApi, type AiReportApi } from "../services/aiReport.api";
import type { AiReport } from "../types/ai-report.types";
import { AI_REPORT_PENDING_MESSAGE } from "../utils/ai-report.validation";
import { aiReportErrorMessage } from "../utils/ai-report.errors";
import { useAuth } from "../../../auth/auth-context";
import { Pagination } from "../../manufacturing/components/shared/Pagination";

export function AiReportsPage({ api = getAiReportApi() }: { api?: AiReportApi | null }) {
  const [reloadKey, setReloadKey] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const { keycloakSubject } = useAuth();
  const request = useMemo(() => ({ api, reloadKey, page, size, actor: keycloakSubject }), [api, reloadKey, page, size, keycloakSubject]);
  const [result, setResult] = useState<{
    request: typeof request;
    state: AiReportListState;
  } | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [editing, setEditing] = useState<AiReport | null>(null);
  const [fileBusy, setFileBusy] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    if (!request.api) return;
    const controller = new AbortController();
    void request.api
      .listAiReports(request.page, request.size, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        if (data.totalPages > 0 && request.page >= data.totalPages) {
          setPage(data.totalPages - 1);
          return;
        }
        setResult({ request, state: { status: "ready", reports: data.content, page: data } });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setResult({
            request,
            state: {
              status: "error",
              message: aiReportErrorMessage(error, "Không thể tải danh sách báo cáo. Vui lòng thử lại."),
            },
          });
      });
    return () => controller.abort();
  }, [request]);

  const state: AiReportListState = !api
    ? { status: "pending-backend" }
    : result?.request === request
      ? result.state
      : { status: "loading" };
  const selected =
    state.status === "ready" ? state.reports.find((report) => report.id === selectedId) : undefined;

  async function openOriginalFile(report: AiReport, preview = false) {
    if (!api || fileBusy) return;
    // Open synchronously so PDF previews are not blocked after the async request.
    const previewWindow =
      preview && report.fileType === "PDF" ? window.open("about:blank", "_blank") : null;
    if (preview && report.fileType === "PDF" && !previewWindow) {
      setFileError(
        "Trình duyệt đã chặn cửa sổ xem báo cáo. Vui lòng cho phép cửa sổ mới hoặc dùng Tải bản gốc.",
      );
      return;
    }
    if (previewWindow) previewWindow.opener = null;
    setFileBusy(true);
    setFileError(null);
    try {
      const blob = await api.downloadAiReport(report);
      const url = URL.createObjectURL(blob);
      if (previewWindow) {
        previewWindow.location.replace(url);
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      } else {
        const link = document.createElement("a");
        link.href = url;
        link.download = (blob as Blob & { name?: string }).name || report.fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
      }
    } catch (error) {
      previewWindow?.close();
      setFileError(aiReportErrorMessage(error, "Không thể tải file báo cáo. Vui lòng thử lại."));
    } finally {
      setFileBusy(false);
    }
  }

  return (
    <section className="grid min-w-0 gap-5">
      <PageHeader
        actions={
          <>
            <ActionButton disabled={fileBusy} onClick={() => setImportOpen(true)} variant="primary">
              <Upload aria-hidden="true" size={17} />
              Nhập báo cáo
            </ActionButton>
            <ActionButton disabled={!api || fileBusy} onClick={() => setExportOpen(true)}>
              <Download aria-hidden="true" size={17} /> Xuất báo cáo AI
            </ActionButton>
            <ActionButton disabled={state.status === "loading"} onClick={() => setReloadKey((value) => value + 1)}>
              <RefreshCw aria-hidden="true" size={17} /> Làm mới
            </ActionButton>
            <span
              title={
                !api
                  ? AI_REPORT_PENDING_MESSAGE
                  : !selected
                    ? "Chọn một báo cáo để xuất file gốc."
                    : undefined
              }
            >
              <ActionButton
                disabled={!selected || !api || fileBusy}
                onClick={() => {
                  if (selected) void openOriginalFile(selected);
                }}
              >
                <Download aria-hidden="true" size={17} />
                {fileBusy ? "Đang tải..." : "Tải bản gốc"}
              </ActionButton>
            </span>
          </>
        }
        description="Quản lý báo cáo gốc và xuất báo cáo gợi ý, mua trà theo kỳ."
        title="Báo cáo AI"
      />
      {feedback ? (
        <p
          className="rounded-lg border border-[#d8eadc] bg-[#edf8ef] p-3 text-sm font-semibold text-[#315f3c]"
          role="status"
        >
          {feedback}
        </p>
      ) : null}
      {fileError ? (
        <p
          className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700"
          role="alert"
        >
          {fileError}
        </p>
      ) : null}
      <AiReportTable
        busy={fileBusy}
        fileActionsAvailable={Boolean(api)}
        onEdit={setEditing}
        onExport={(report) => void openOriginalFile(report)}
        onRetry={() => setReloadKey((value) => value + 1)}
        onSelect={setSelectedId}
        onView={(report) => void openOriginalFile(report, true)}
        selectedId={selectedId}
        state={state}
      />
      {state.status === "ready" ? <Pagination page={page + 1} pageCount={state.page.totalPages}
        totalItems={state.page.totalElements} pageSize={size} totalItemsLabel="Tổng số báo cáo"
        onPageChange={(value) => {setPage(value - 1); setSelectedId(null);}}
        onPageSizeChange={(value) => {setSize(value); setPage(0); setSelectedId(null);}} /> : null}
      {api ? (
        <AiReportImportDialog
          key={`import:${keycloakSubject ?? "anonymous"}`}
          actor={keycloakSubject}
          api={api}
          open={importOpen}
          onClose={() => setImportOpen(false)}
          onImported={() => {
                  setImportOpen(false);
                  setPage(0);
                  setSelectedId(null);
                  setReloadKey((value) => value + 1);
                  setFeedback("Đã nhập báo cáo.");
                }}
        />
      ) : null}
      {editing ? (
        <AiReportEditDialog
          key={`${editing.id}-${editing.updatedAt}`}
          onClose={() => setEditing(null)}
          onSubmit={
            api
              ? async (values) => {
                  await api.updateAiReport(editing.id, values);
                  setEditing(null);
                  setPage(0);
                  setReloadKey((value) => value + 1);
                  setFeedback("Đã cập nhật thông tin báo cáo.");
                }
              : undefined
          }
          report={editing}
        />
      ) : null}
      {api && exportOpen ? <AiReportExportDialog key={`export:${keycloakSubject ?? "anonymous"}`} api={api} actor={keycloakSubject}
        onClose={() => setExportOpen(false)} onExported={() => {setExportOpen(false); setFeedback("Đã xuất và tải báo cáo AI.");}} /> : null}
    </section>
  );
}
