import { AlertTriangle, Boxes, ClipboardCheck, PackageCheck } from "lucide-react";
import { Link } from "react-router-dom";

import { routePaths } from "../../../../routes/path";
import { TransactionTypeBadge } from "../../../inventory/components/InventoryBadges";
import { InventoryEmptyState } from "../../../inventory/components/InventoryManagementComponents";
import { QualityStatusBadge } from "../../../inventory/components/QualityComponents";
import {
  formatExpiryDaysText,
  formatVietnamDateTime,
  formatVietnamNumber,
} from "../../../inventory/utils/inventory-format";
import type { DashboardDomain } from "../../hooks/useDashboardData";
import type {
  DashboardAlertSnapshot,
  DashboardInventorySnapshot,
  DashboardResource,
} from "../../types/dashboard.types";
import type { InventoryKpiSummary } from "../../utils/dashboard-mappers";
import { numeric, summarizeByLocation } from "../../utils/dashboard-mappers";
import { DashboardKpiCard, DashboardKpiGrid, type DashboardKpiCardProps } from "../DashboardKpiCard";
import { DashboardSection, DashboardSectionLink } from "../DashboardSection";
import { DashboardListSkeleton, DashboardPartView, SourceBadge } from "../DashboardStates";
import { DashboardTable } from "../DashboardTable";
import { StackedBar } from "../StackedBar";
import { isResourceLoading, resourceData } from "./dashboard-tab-shared";

const referenceTypeLabels: Record<string, string> = {
  MANUAL: "Thủ công",
  ORDER: "Đơn hàng",
  RETURN_ORDER: "Đơn trả",
  STOCK_COUNT: "Kiểm kê",
  STOCK_TRANSFER: "Chuyển kho",
};

export function InventoryTab({
  alerts,
  inventory,
  inventoryKpis,
  onShowAlerts,
  retry,
}: {
  alerts: DashboardResource<DashboardAlertSnapshot>;
  inventory: DashboardResource<DashboardInventorySnapshot>;
  inventoryKpis: InventoryKpiSummary | null;
  onShowAlerts: () => void;
  retry: (domain: DashboardDomain) => void;
}) {
  const data = resourceData(inventory);
  const alertData = resourceData(alerts);
  const loading = isResourceLoading(inventory);
  const alertsLoading = isResourceLoading(alerts);
  const balances = data?.balances;
  const balancePage = balances?.data ?? null;
  const partial = balancePage !== null && balancePage.loaded < balancePage.total;
  const partialNote = partial
    ? `Trên ${formatVietnamNumber(balancePage.loaded)}/${formatVietnamNumber(balancePage.total)} dòng tồn`
    : undefined;
  const pendingDocuments = alertData?.pendingDocuments;

  const kpis: DashboardKpiCardProps[] = [
    {
      error: balances?.error,
      icon: <Boxes size={18} />,
      label: "SKU có tồn",
      loading,
      note: partialNote ?? "Trong phạm vi kho đã chọn",
      noteTone: partial ? "warn" : "neutral",
      value: inventoryKpis ? formatVietnamNumber(inventoryKpis.skuCount) : undefined,
    },
    {
      error: balances?.error,
      icon: <PackageCheck size={18} />,
      label: "Tồn khả dụng",
      loading,
      note: inventoryKpis
        ? `Giữ chỗ ${formatVietnamNumber(inventoryKpis.totalReserved)} · bị chặn ${formatVietnamNumber(inventoryKpis.totalBlocked)}`
        : undefined,
      value: inventoryKpis ? formatVietnamNumber(inventoryKpis.totalAvailable) : undefined,
    },
    {
      error: balances?.error,
      icon: <AlertTriangle size={18} />,
      label: "SKU dưới điểm đặt lại",
      loading,
      note: inventoryKpis && inventoryKpis.belowReorder.length > 0 ? "Cần bổ sung hàng" : "Không có SKU nào",
      noteTone: inventoryKpis && inventoryKpis.belowReorder.length > 0 ? "bad" : "good",
      onClick: onShowAlerts,
      source: "ADAPTED",
      value: inventoryKpis ? formatVietnamNumber(inventoryKpis.belowReorder.length) : undefined,
    },
    {
      error: pendingDocuments?.error,
      icon: <ClipboardCheck size={18} />,
      label: "Chứng từ chờ duyệt",
      loading: alertsLoading,
      note: "Chứng từ kho đã gửi duyệt",
      source: "DEMO",
      to: routePaths.inventoryDocuments,
      value: pendingDocuments?.data ? formatVietnamNumber(pendingDocuments.data.length) : undefined,
    },
  ];

  return (
    <div className="grid gap-5">
      <DashboardKpiGrid count={kpis.length}>
        {kpis.map((kpi) => (
          <DashboardKpiCard key={kpi.label} {...kpi} />
        ))}
      </DashboardKpiGrid>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryBalances}>Xem tồn kho</DashboardSectionLink>}
          description="Khả dụng, giữ chỗ và bị chặn trên tồn vật lý"
          title="Tồn theo địa điểm"
        >
          <DashboardPartView
            empty="Chưa có dữ liệu tồn trong phạm vi đã chọn."
            isEmpty={(part) => part.items.length === 0}
            loading={loading}
            onRetry={() => retry("inventory")}
            part={balances}
            rows={4}
          >
            {(part) => <StackedBar rows={summarizeByLocation(part.items).slice(0, 6)} />}
          </DashboardPartView>
        </DashboardSection>

        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryQuality}>Xem hạn dùng</DashboardSectionLink>}
          badge={<SourceBadge source="DEMO" />}
          flush
          title="Batch sắp hết hạn"
        >
          <div className="px-4 pb-4">
            <DashboardPartView
              empty="Không có lô nào sắp hết hạn."
              loading={alertsLoading}
              onRetry={() => retry("alerts")}
              part={alertData?.expiringBatches}
              rows={4}
            >
              {(batches) => (
                <DashboardTable
                  columns={[
                    {
                      header: "Lô / SKU",
                      key: "batch",
                      render: (batch) => (
                        <div className="grid gap-0.5">
                          <Link
                            className="whitespace-nowrap font-black text-[#159734] hover:underline"
                            to={`/management/inventory/quality/batches/${batch.id}`}
                          >
                            {batch.batchCode}
                          </Link>
                          <span className="text-xs font-semibold text-[#697684]">
                            {batch.skuCode} · {batch.location.name}
                          </span>
                        </div>
                      ),
                    },
                    {
                      header: "Trạng thái",
                      key: "status",
                      render: (batch) => <QualityStatusBadge status={batch.status} />,
                    },
                    {
                      align: "right",
                      header: "Hạn dùng",
                      key: "expiry",
                      className: "whitespace-nowrap",
                      render: (batch) => (
                        <span className="font-black text-[#172317]">{formatExpiryDaysText(batch.expiryDate)}</span>
                      ),
                    },
                  ]}
                  getRowKey={(batch) => batch.id}
                  rows={batches.slice(0, 5)}
                />
              )}
            </DashboardPartView>
          </div>
        </DashboardSection>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryBalances}>Xem tất cả</DashboardSectionLink>}
          flush
          title="SKU tồn nhiều nhất"
        >
          <div className="px-4 pb-4">
            <DashboardPartView
              empty="Chưa có dữ liệu tồn trong phạm vi đã chọn."
              isEmpty={(part) => part.items.length === 0}
              loading={loading}
              onRetry={() => retry("inventory")}
              part={balances}
              rows={6}
            >
              {(part) => (
                <DashboardTable
                  columns={[
                    {
                      header: "SKU",
                      key: "sku",
                      render: (balance) => (
                        <div className="grid gap-0.5">
                          <span className="whitespace-nowrap font-black text-[#172317]">{balance.code ?? balance.productCode}</span>
                          <span className="text-xs font-semibold text-[#697684]">
                            {balance.name ?? balance.productName}
                          </span>
                        </div>
                      ),
                    },
                    {
                      header: "Địa điểm",
                      key: "location",
                      render: (balance) => (
                        <span className="font-semibold text-[#697684]">{balance.locationName}</span>
                      ),
                    },
                    {
                      align: "right",
                      header: "Tồn vật lý",
                      key: "onHand",
                      render: (balance) => (
                        <span className="font-black text-[#172317]">
                          {formatVietnamNumber(numeric(balance.quantityOnHand))}
                          {balance.unitSymbol ? (
                            <span className="ml-1 text-xs font-semibold text-[#697684]">{balance.unitSymbol}</span>
                          ) : null}
                        </span>
                      ),
                    },
                    {
                      align: "right",
                      header: "Khả dụng",
                      key: "available",
                      render: (balance) => (
                        <span className="font-black text-[#159734]">
                          {formatVietnamNumber(numeric(balance.availableQuantity))}
                        </span>
                      ),
                    },
                  ]}
                  getRowKey={(balance) => balance.inventoryBalanceId}
                  rows={[...part.items]
                    .sort((first, second) => numeric(second.quantityOnHand) - numeric(first.quantityOnHand))
                    .slice(0, 6)}
                />
              )}
            </DashboardPartView>
          </div>
        </DashboardSection>

        <DashboardSection
          action={<DashboardSectionLink to={routePaths.inventoryTransactions}>Xem lịch sử</DashboardSectionLink>}
          flush
          title="Biến động gần đây"
        >
          <div className="px-4 pb-4">
            {loading && !data ? (
              <DashboardListSkeleton rows={6} />
            ) : (
              <DashboardPartView
                empty="Chưa có biến động kho."
                loading={loading}
                onRetry={() => retry("inventory")}
                part={data?.transactions}
                rows={6}
              >
                {(transactions) =>
                  transactions.length === 0 ? (
                    <InventoryEmptyState message="Chưa có biến động kho." />
                  ) : (
                    <DashboardTable
                      columns={[
                        {
                          header: "Giao dịch",
                          key: "code",
                          render: (transaction) => (
                            <div className="grid gap-0.5">
                              <span className="whitespace-nowrap font-black text-[#172317]">{transaction.transactionCode}</span>
                              <span className="text-xs font-semibold text-[#697684]">
                                {referenceTypeLabels[transaction.referenceType] ?? transaction.referenceType}
                              </span>
                            </div>
                          ),
                        },
                        {
                          header: "Loại",
                          key: "type",
                          render: (transaction) => <TransactionTypeBadge type={transaction.type} />,
                        },
                        {
                          align: "right",
                          header: "Thời gian",
                          key: "time",
                          className: "whitespace-nowrap",
                          render: (transaction) => (
                            <span className="font-semibold text-[#697684]">
                              {formatVietnamDateTime(transaction.createdAt)}
                            </span>
                          ),
                        },
                      ]}
                      getRowKey={(transaction) => String(transaction.id)}
                      rows={transactions}
                    />
                  )
                }
              </DashboardPartView>
            )}
          </div>
        </DashboardSection>
      </div>
    </div>
  );
}
