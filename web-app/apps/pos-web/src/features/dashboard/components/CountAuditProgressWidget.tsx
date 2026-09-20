import { ClipboardCheck, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { routePaths } from "../../../routes/path";
import type { DashboardCountAuditSummary } from "../types/dashboard.types";
import { formatCompactVnd } from "../utils/dashboard-mappers";
import { DashboardTable } from "./DashboardTable";

const auditStatusConfig: Record<
  string,
  { bg: string; text: string; label: string }
> = {
  COUNTING: { bg: "bg-blue-50", label: "Đang kiểm đếm", text: "text-blue-700" },
  FROZEN: { bg: "bg-purple-50", label: "Tạm khóa kho", text: "text-purple-700" },
  PENDING_APPROVAL: { bg: "bg-amber-50", label: "Chờ phê duyệt", text: "text-amber-800" },
  RECONCILING: { bg: "bg-orange-50", label: "Đang đối soát", text: "text-orange-700" },
};

export function CountAuditProgressWidget({
  audits,
  loading = false,
}: {
  audits: DashboardCountAuditSummary[];
  loading?: boolean;
}) {
  if (loading) {
    return <div className="h-40 w-full animate-pulse rounded-lg bg-[#f0f4f1]" />;
  }

  if (audits.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-emerald-100 bg-[#f4faf5] p-5 text-center shadow-sm">
        <div className="mb-2.5 flex size-11 items-center justify-center rounded-full bg-emerald-100/80 text-[#159734]">
          <ClipboardCheck size={24} />
        </div>
        <p className="mb-3.5 max-w-md text-xs font-semibold text-emerald-800/90 leading-relaxed">
          Đã hoàn thành toàn bộ đợt kiểm kê gần nhất. Lần kiểm kê cuối: <span className="font-bold text-emerald-950">15/09/2026</span> (Đạt chuẩn 100%).
        </p>
        <Link
          to={routePaths.inventoryStocktakeCreate}
          className="inline-flex items-center gap-1.5 rounded-lg border border-[#159734]/30 bg-[#159734]/10 px-4 py-2 text-xs font-bold text-[#159734] transition hover:bg-[#159734] hover:text-white shadow-sm"
        >
          <Plus size={14} />
          Lập lịch kiểm kê mới
        </Link>
      </div>
    );
  }

  return (
    <DashboardTable
      columns={[
        {
          header: "Đợt kiểm kê / Kho",
          key: "title",
          render: (audit) => (
            <div className="grid gap-0.5 min-w-0">
              <div className="flex items-center gap-1.5">
                <span className="whitespace-nowrap font-black text-[#172317]">{audit.countCode}</span>
                <span className="truncate text-xs font-medium text-[#697684]">
                  · {audit.warehouseName}
                </span>
              </div>
              <span className="truncate text-xs font-semibold text-[#172317]">{audit.title}</span>
            </div>
          ),
        },
        {
          header: "Trạng thái",
          key: "status",
          render: (audit) => {
            const conf = auditStatusConfig[audit.status] ?? {
              bg: "bg-gray-50",
              label: audit.status,
              text: "text-gray-700",
            };
            return (
              <span
                className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${conf.bg} ${conf.text}`}
              >
                {conf.label}
              </span>
            );
          },
        },
        {
          align: "right",
          header: "Lệch thực tế",
          key: "variance",
          render: (audit) => (
            <div className="grid gap-0.5 text-right">
              <span className="font-black text-[#172317]">
                {audit.varianceItemsCount} dòng lệch
              </span>
              <span
                className={`text-xs font-black ${
                  audit.totalVarianceValue < 0 ? "text-red-700" : "text-[#159734]"
                }`}
              >
                {audit.totalVarianceValue > 0 ? "+" : ""}
                {formatCompactVnd(audit.totalVarianceValue)} đ
              </span>
            </div>
          ),
        },
      ]}
      getRowKey={(audit) => audit.id}
      rows={audits}
    />
  );
}

