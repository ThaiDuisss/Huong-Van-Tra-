import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import { StockAlertSeverityBadge } from "../../inventory/components/StockAlertComponents";
import type { DashboardAlertItem } from "../types/dashboard.types";
import { formatAgeLabel } from "../utils/dashboard-mappers";
import { SourceBadge } from "./DashboardStates";
import { DashboardTable } from "./DashboardTable";

/**
 * Danh sách cảnh báo ưu tiên duy nhất (compact table) với severity badge dùng chung
 * và CTA drill-down. Không tách card theo từng loại cảnh báo.
 */
export function AlertPriorityList({ items }: { items: DashboardAlertItem[] }) {
  return (
    <DashboardTable
      columns={[
        {
          header: "Mức độ",
          key: "severity",
          className: "w-[130px]",
          render: (item) => <StockAlertSeverityBadge severity={item.severity} />,
        },
        {
          header: "Cảnh báo",
          key: "title",
          render: (item) => (
            <div className="grid min-w-0 gap-0.5">
              <span className="flex flex-wrap items-center gap-2">
                <span className="font-black text-[#172317]">{item.title}</span>
                <SourceBadge source={item.source} />
              </span>
              <span className="text-xs font-semibold text-[#697684]">{item.subject}</span>
            </div>
          ),
        },
        {
          header: "Loại",
          key: "kind",
          className: "whitespace-nowrap",
          render: (item) => <span className="font-semibold text-[#536078]">{item.kindLabel}</span>,
        },
        {
          header: "Thời gian",
          key: "age",
          align: "right",
          className: "whitespace-nowrap",
          render: (item) => (
            <span className="font-semibold text-[#697684]" title={item.occurredAt}>
              {formatAgeLabel(item.occurredAt)}
            </span>
          ),
        },
        {
          header: "",
          key: "action",
          align: "right",
          className: "w-[110px]",
          render: (item) =>
            item.to ? (
              <Link
                className="inline-flex h-8 items-center gap-1 rounded-lg border border-[#d8eadc] px-3 text-xs font-black text-[#159734] hover:bg-[#f4faf5]"
                to={item.to}
              >
                Xử lý <ArrowRight size={14} aria-hidden="true" />
              </Link>
            ) : (
              <span className="text-xs font-semibold text-[#9aa7b5]" title="Chưa có màn chi tiết">
                Chưa có màn
              </span>
            ),
        },
      ]}
      getRowKey={(item) => item.id}
      rows={items}
    />
  );
}
