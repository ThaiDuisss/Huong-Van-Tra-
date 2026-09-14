import { AlertTriangle, Factory, PackageSearch, Scale } from "lucide-react";
import { Link } from "react-router-dom";

import { routePaths } from "../../../../routes/path";
import { formatVietnamNumber } from "../../../inventory/utils/inventory-format";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardProductionSnapshot,
  DashboardProductionWastage,
  DashboardResource,
} from "../../types/dashboard.types";
import { formatPercent } from "../../utils/dashboard-mappers";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardGapState, DashboardListSkeleton, SourceBadge } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { isResourceLoading, resourceData } from "./dashboard-tab-shared";

const wastageStatusMeta: Record<
  DashboardProductionWastage["status"],
  { label: string; tone: string }
> = {
  NORMAL: { label: "Bình thường", tone: "border-green-200 bg-green-50 text-green-700" },
  OVER_THRESHOLD: { label: "Vượt ngưỡng", tone: "border-red-200 bg-red-50 text-red-700" },
  WATCH: { label: "Theo dõi", tone: "border-amber-200 bg-amber-50 text-amber-700" },
};

export function ProductionTab({
  production,
  retry,
}: {
  production: DashboardResource<DashboardProductionSnapshot>;
  retry: (domain: DashboardDomain) => void;
}) {
  const data = resourceData(production);
  const loading = isResourceLoading(production);
  const wastage = data?.wastage ?? null;
  const overThreshold = wastage?.filter((item) => item.status === "OVER_THRESHOLD").length ?? 0;
  const totalWastage = wastage?.reduce((sum, item) => sum + item.wastageQuantity, 0) ?? 0;
  const source = wastage ? "DEMO" : "GAP";

  const kpis: DashboardKpiCardProps[] = [
    {
      icon: <AlertTriangle size={18} />,
      label: "Vượt ngưỡng hao hụt",
      loading,
      note: wastage ? "So kế hoạch BOM với thực xuất" : "Cần API hao hụt (DASH-GAP-013)",
      noteTone: overThreshold > 0 ? "bad" : "neutral",
      source: data ? source : "REAL",
      value: wastage ? formatVietnamNumber(overThreshold) : undefined,
    },
    {
      icon: <Scale size={18} />,
      label: "Tổng hao hụt",
      loading,
      note: wastage ? "Theo nguyên liệu đã xuất" : "Cần API hao hụt (DASH-GAP-013)",
      source: data ? source : "REAL",
      value: wastage ? formatVietnamNumber(totalWastage) : undefined,
    },
    {
      icon: <Factory size={18} />,
      label: "Sản lượng thực tế",
      note: "Cần API sản lượng thành phẩm (DASH-GAP-013)",
      source: "GAP",
    },
    {
      icon: <PackageSearch size={18} />,
      label: "Thiếu nguyên liệu",
      note: "Xem trong danh sách lệnh sản xuất",
      source: "GAP",
      to: routePaths.inventoryProductionOrderList,
    },
  ];

  return (
    <div className="grid gap-5">
      <DashboardKpiGrid count={kpis.length}>
        {kpis.map((kpi) => (
          <DashboardKpiCard key={kpi.label} {...kpi} />
        ))}
      </DashboardKpiGrid>

      <DashboardSection
        action={
          <DashboardSectionLink to={routePaths.inventoryProductionOrderList}>Lệnh sản xuất</DashboardSectionLink>
        }
        badge={data ? <SourceBadge source={source} /> : undefined}
        description="Kế hoạch theo BOM so với thực tế xuất nguyên liệu"
        flush={Boolean(wastage)}
        title="Hao hụt sản xuất"
      >
        {loading && !data ? (
          <DashboardListSkeleton rows={4} />
        ) : production.status === "error" ? (
          <DashboardGapState
            action={
              <button
                className="rounded-lg border border-[#d8eadc] px-3 py-1.5 text-xs font-black text-[#159734]"
                onClick={() => retry("production")}
                type="button"
              >
                Thử lại
              </button>
            }
            message={production.message}
          />
        ) : wastage ? (
          <div className="px-4 pb-4">
            <DashboardTable
              columns={[
                {
                  header: "Lệnh sản xuất",
                  key: "order",
                  render: (item) => <span className="font-black text-[#172317]">{item.productionOrderCode}</span>,
                },
                {
                  header: "Nguyên liệu",
                  key: "material",
                  render: (item) => (
                    <div className="grid gap-0.5">
                      <span className="font-semibold text-[#172317]">{item.materialName}</span>
                      <span className="text-xs font-semibold text-[#697684]">{item.materialCode}</span>
                    </div>
                  ),
                },
                {
                  align: "right",
                  header: "Kế hoạch",
                  key: "planned",
                  render: (item) => formatVietnamNumber(item.plannedQuantity),
                },
                {
                  align: "right",
                  header: "Thực tế",
                  key: "actual",
                  render: (item) => formatVietnamNumber(item.actualQuantity),
                },
                {
                  align: "right",
                  header: "Hao hụt",
                  key: "wastage",
                  render: (item) => (
                    <span className="font-black text-[#172317]">
                      {formatVietnamNumber(item.wastageQuantity)}{" "}
                      <span className="text-xs font-semibold text-[#697684]">
                        ({formatPercent(item.wastageRate)} / ngưỡng {formatPercent(item.thresholdRate)})
                      </span>
                    </span>
                  ),
                },
                {
                  header: "Trạng thái",
                  key: "status",
                  render: (item) => (
                    <span
                      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-black ${wastageStatusMeta[item.status].tone}`}
                    >
                      {wastageStatusMeta[item.status].label}
                    </span>
                  ),
                },
              ]}
              getRowKey={(item) => item.id}
              rows={wastage}
            />
          </div>
        ) : (
          <DashboardGapState message="Chưa có API so sánh kế hoạch BOM với thực tế tiêu hao (DASH-GAP-013)." />
        )}
      </DashboardSection>

      <p className="text-xs font-semibold text-[#697684]">
        Truy xuất nguồn gốc lô dùng màn{" "}
        <Link className="font-black text-[#159734] hover:underline" to={routePaths.inventoryQuality}>
          Chất lượng và hạn sử dụng
        </Link>
        ; chưa có API truy xuất đầu cuối (DASH-GAP-014).
      </p>
    </div>
  );
}
