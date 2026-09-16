import { CircleCheck, Clock3, RotateCcw, Wallet } from "lucide-react";

import { routePaths } from "../../../../routes/path";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../../../inventory/utils/inventory-format";
import { OrderStatusBadge } from "../../../order/components/OrderStatusBadge";
import { PaymentStatusBadge } from "../../../order/components/PaymentStatusBadge";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardFilters,
  DashboardResource,
  DashboardSalesSnapshot,
} from "../../types/dashboard.types";
import { formatPercent, numeric } from "../../utils/dashboard-mappers";
import type { DashboardCapabilities } from "../../utils/dashboard-permissions";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardGapState, DashboardListSkeleton, DashboardPartView, SourceBadge } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { RankList } from "../RankList";
import { isResourceLoading, resourceData, timeRangeLabels } from "./dashboard-tab-shared";

export function PosTab({
  capabilities,
  filters,
  retry,
  sales,
}: {
  capabilities: DashboardCapabilities;
  filters: DashboardFilters;
  retry: (domain: DashboardDomain) => void;
  sales: DashboardResource<DashboardSalesSnapshot>;
}) {
  const data = resourceData(sales);
  const loading = isResourceLoading(sales);
  const summary = data?.todaySummary;
  const stats = data?.stats;
  const demo = data?.demo ?? null;
  const rangeLabel = timeRangeLabels[filters.timeRange];
  const total = numeric(stats?.data?.total);
  const returned = numeric(stats?.data?.returnedOrRefunded);

  const kpis: DashboardKpiCardProps[] = [
    {
      error: summary?.error,
      icon: <Clock3 size={18} />,
      label: "Đơn chờ thanh toán",
      loading,
      note: "Hôm nay",
      noteTone: numeric(summary?.data?.pendingPaymentOrders) > 0 ? "warn" : "neutral",
      value: summary?.data ? formatVietnamNumber(numeric(summary.data.pendingPaymentOrders)) : undefined,
    },
  ];

  if (capabilities.finance) {
    kpis.push({
      error: summary?.error,
      icon: <Wallet size={18} />,
      label: "Tiền chờ thu",
      loading,
      note: "Đơn chưa thanh toán hôm nay",
      value: summary?.data ? formatVnd(numeric(summary.data.pendingAmount)) : undefined,
    });
  }

  kpis.push(
    {
      error: stats?.error,
      icon: <CircleCheck size={18} />,
      label: "Đơn hoàn tất",
      loading,
      note: stats?.data ? `Trên ${formatVietnamNumber(total)} đơn · ${rangeLabel}` : rangeLabel,
      value: stats?.data ? formatVietnamNumber(numeric(stats.data.completed)) : undefined,
    },
    {
      error: stats?.error,
      icon: <RotateCcw size={18} />,
      label: "Đơn hoàn / refund",
      loading,
      note: stats?.data
        ? returned > 0
          ? `${formatPercent(total > 0 ? (returned / total) * 100 : 0)} số đơn · ${rangeLabel}`
          : `Không có · ${rangeLabel}`
        : rangeLabel,
      noteTone: returned > 0 ? "bad" : "good",
      source: "ADAPTED",
      value: stats?.data ? formatVietnamNumber(returned) : undefined,
    },
  );

  return (
    <div className="grid gap-5">
      <DashboardKpiGrid count={kpis.length}>
        {kpis.map((kpi) => (
          <DashboardKpiCard key={kpi.label} {...kpi} />
        ))}
      </DashboardKpiGrid>

      <div className="grid gap-4 lg:grid-cols-2">
        <DashboardSection
          badge={data ? <SourceBadge source={demo ? "DEMO" : "GAP"} /> : undefined}
          description="Doanh thu theo phương thức thanh toán"
          title="Cơ cấu thanh toán"
        >
          {loading && !data ? (
            <DashboardListSkeleton rows={3} />
          ) : demo && capabilities.finance ? (
            <RankList
              items={demo.paymentBreakdown.map((item) => ({
                id: item.method,
                label: item.label,
                sub: `${formatVietnamNumber(item.transactionCount)} giao dịch`,
                value: item.revenue,
                valueText: formatVnd(item.revenue),
              }))}
            />
          ) : (
            <DashboardGapState message="Payment-Service chưa có API tổng hợp theo phương thức (DASH-GAP-004)." />
          )}
        </DashboardSection>

        <DashboardSection
          badge={data ? <SourceBadge source={demo ? "DEMO" : "GAP"} /> : undefined}
          description="Doanh thu, số đơn và tỷ lệ hủy/refund theo thu ngân"
          title="Hiệu suất nhân viên"
        >
          {loading && !data ? (
            <DashboardListSkeleton rows={3} />
          ) : demo ? (
            <RankList
              items={demo.staffPerformance.map((staff) => ({
                id: staff.id,
                label: staff.staffName,
                sub: `${formatVietnamNumber(staff.orderCount)} đơn · hủy/refund ${formatPercent(staff.cancelRefundRate)}`,
                value: capabilities.finance ? staff.revenue : staff.orderCount,
                valueText: capabilities.finance
                  ? formatVnd(staff.revenue)
                  : `${formatVietnamNumber(staff.orderCount)} đơn`,
              }))}
            />
          ) : (
            <DashboardGapState message="Cần API tổng hợp theo thu ngân (DASH-GAP-005)." />
          )}
        </DashboardSection>
      </div>

      <DashboardSection
        action={<DashboardSectionLink to={routePaths.orders}>Tất cả đơn</DashboardSectionLink>}
        description="Đơn POS mới nhất hôm nay"
        flush
        title="Đơn POS hôm nay"
      >
        <div className="px-4 pb-4">
          <DashboardPartView
            empty="Chưa có đơn POS nào hôm nay."
            isEmpty={(part) => part.items.length === 0}
            loading={loading}
            onRetry={() => retry("sales")}
            part={data?.recentOrders}
            rows={6}
          >
            {(part) => (
              <DashboardTable
                columns={[
                  {
                    header: "Mã đơn",
                    key: "code",
                    render: (order) => <span className="whitespace-nowrap font-black text-[#172317]">{order.orderCode}</span>,
                  },
                  {
                    header: "Thời gian",
                    key: "time",
                    className: "whitespace-nowrap",
                    render: (order) => (
                      <span className="font-semibold text-[#697684]">
                        {formatVietnamDateTime(order.soldAt ?? order.createdAt ?? "")}
                      </span>
                    ),
                  },
                  {
                    header: "Nhân viên",
                    key: "cashier",
                    render: (order) => (
                      <span className="font-semibold text-[#172317]">{order.cashierName ?? "—"}</span>
                    ),
                  },
                  {
                    header: "Thanh toán",
                    key: "payment",
                    render: (order) => (
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-xs font-semibold text-[#697684]">
                          {order.paymentMethodLabel ?? order.paymentMethod ?? "—"}
                        </span>
                        <PaymentStatusBadge label={order.paymentStatusLabel} status={order.paymentStatus} />
                      </div>
                    ),
                  },
                  {
                    header: "Trạng thái",
                    key: "status",
                    render: (order) => <OrderStatusBadge label={order.statusLabel} status={order.status} />,
                  },
                  {
                    align: "right",
                    header: "Tổng tiền",
                    key: "amount",
                    render: (order) => (
                      <span className="font-black text-[#172317]">
                        {capabilities.finance ? formatVnd(numeric(order.finalAmount)) : "—"}
                      </span>
                    ),
                  },
                ]}
                getRowKey={(order) => order.orderId}
                rows={part.items}
              />
            )}
          </DashboardPartView>
        </div>
      </DashboardSection>
    </div>
  );
}
