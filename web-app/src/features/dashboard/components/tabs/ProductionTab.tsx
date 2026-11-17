import { AlertTriangle, Factory, PackageSearch, Scale } from "lucide-react";

import { Button } from "../../../../components/ui/Button";
import { StatusBadge, type BadgeTone } from "../../../../components/ui/StatusBadge";
import { routePaths } from "../../../../routes/path";
import { formatVietnamNumber } from "../../../inventory/utils/inventory-format";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import { InventoryEmptyState } from "../../../inventory/components/InventoryManagementComponents";
import type {
  DashboardFilters,
  DashboardProductionSnapshot,
  DashboardProductionWastage,
  DashboardResource,
} from "../../types/dashboard.types";
import { formatPercent } from "../../utils/dashboard-mappers";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardGapState, DashboardListSkeleton } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { isResourceLoading, resourceData, timeRangeLabels } from "./dashboard-tab-shared";

const wastageStatusMeta: Record<
  DashboardProductionWastage["status"],
  { label: string; tone: BadgeTone }
> = {
  NORMAL: { label: "Bình thường", tone: "success" },
  OVER_THRESHOLD: { label: "Vượt ngưỡng", tone: "danger" },
  WATCH: { label: "Theo dõi", tone: "warning" },
};

export function ProductionTab({
  filters,
  production,
  retry,
}: {
  filters: DashboardFilters;
  production: DashboardResource<DashboardProductionSnapshot>;
  retry: (domain: DashboardDomain) => void;
}) {
  const data = resourceData(production);
  const loading = isResourceLoading(production);
  const wastage = data?.wastage ?? null;
  const summary = data?.summary ?? null;
  const rangeLabel = timeRangeLabels[filters.timeRange];
  const overThreshold = wastage?.filter((item) => item.status === "OVER_THRESHOLD").length ?? 0;
  const totalWastage = wastage?.reduce((sum, item) => sum + item.wastageQuantity, 0) ?? 0;
  const source = summary ? "REAL" : wastage && wastage.length > 0 ? (wastage[0].source ?? "DEMO") : "GAP";
  // Đang tải lại (đổi kỳ) vẫn giữ số cũ; chỉ hiện "—" khi chưa từng có dữ liệu.
  const summarySource = summary ? "REAL" : data ? "GAP" : "REAL";

  const kpis: DashboardKpiCardProps[] = [
    {
      icon: <AlertTriangle size={18} />,
      label: "Vượt ngưỡng hao hụt",
      loading,
      note: wastage ? "Nguyên liệu xuất quá hao hụt cho phép của BOM" : "Đang cập nhật số liệu",
      noteTone: overThreshold > 0 ? "bad" : "neutral",
      source: data ? source : "REAL",
      value: wastage ? formatVietnamNumber(overThreshold) : undefined,
    },
    {
      icon: <Scale size={18} />,
      label: "Tổng hao hụt nguyên liệu",
      loading,
      note: wastage ? `Thực xuất so với định mức BOM, ${rangeLabel}` : "Đang cập nhật số liệu",
      source: data ? source : "REAL",
      value: wastage ? formatVietnamNumber(totalWastage) : undefined,
    },
    {
      icon: <Factory size={18} />,
      label: "Sản lượng thực tế",
      loading,
      note: summary
        ? `${formatVietnamNumber(summary.completedOrderCount)} lệnh đã duyệt, ${rangeLabel}`
        : "Tổng hợp từ lệnh sản xuất đã duyệt",
      source: summarySource,
      value: summary ? formatVietnamNumber(summary.totalOutputQuantity) : undefined,
    },
    {
      icon: <PackageSearch size={18} />,
      label: "Lệnh thiếu nguyên liệu",
      loading,
      note: summary
        ? summary.shortageMaterialCount > 0
          ? `${formatVietnamNumber(summary.shortageMaterialCount)} nguyên liệu không đủ cho ${formatVietnamNumber(summary.openOrderCount)} lệnh đang mở`
          : `Đủ nguyên liệu cho ${formatVietnamNumber(summary.openOrderCount)} lệnh đang mở`
        : "Lệnh nháp/chờ duyệt so với tồn kho khả dụng",
      noteTone: summary && summary.shortageMaterialCount > 0 ? "bad" : "neutral",
      source: summarySource,
      to: routePaths.inventoryProductionOrderList,
      value: summary ? formatVietnamNumber(summary.shortageOrderCount) : undefined,
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
        description={`Định mức BOM so với nguyên liệu thực xuất của lệnh đã duyệt, ${rangeLabel}`}
        flush={Boolean(wastage && wastage.length > 0)}
        icon={<Factory size={15} />}
        title="Hao hụt sản xuất"
      >
        {loading && !data ? (
          <DashboardListSkeleton rows={4} />
        ) : production.status === "error" ? (
          <DashboardGapState
            action={
              <Button onClick={() => retry("production")} size="sm">
                Thử lại
              </Button>
            }
            message={production.message}
          />
        ) : wastage && wastage.length === 0 ? (
          <InventoryEmptyState message={`Chưa có lệnh sản xuất nào được duyệt ${rangeLabel}.`} />
        ) : wastage ? (
          <div className="px-4 pb-4">
            <DashboardTable
              columns={[
                {
                  header: "Số lệnh",
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
                  header: "Định mức",
                  key: "planned",
                  render: (item) => formatVietnamNumber(item.plannedQuantity),
                },
                {
                  align: "right",
                  header: "Thực xuất",
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
                    <StatusBadge tone={wastageStatusMeta[item.status].tone}>
                      {wastageStatusMeta[item.status].label}
                    </StatusBadge>
                  ),
                },
              ]}
              getRowKey={(item) => item.id}
              rows={wastage}
            />
          </div>
        ) : (
          <DashboardGapState message="Chưa có dữ liệu so sánh định mức công thức với thực tế tiêu hao." />
        )}
      </DashboardSection>
    </div>
  );
}
