import { ClipboardEdit, Clock } from "lucide-react";
import { Link } from "react-router-dom";
import type { DashboardSlowMovingSku } from "../types/dashboard.types";
import { formatCompactVnd } from "../utils/dashboard-mappers";
import { DashboardTable } from "./DashboardTable";

export function SlowMovingSkusWidget({
  items,
  loading = false,
}: {
  items: DashboardSlowMovingSku[];
  loading?: boolean;
}) {
  if (loading) {
    return <div className="h-40 w-full animate-pulse rounded-lg bg-[#f0f4f1]" />;
  }

  if (items.length === 0) {
    return (
      <div className="p-6 text-center text-sm font-medium text-[#697684]">
        Không có mặt hàng nào bị tồn đọng lâu ngày.
      </div>
    );
  }

  return (
    <DashboardTable
      columns={[
        {
          header: "Mã / Tên sản phẩm",
          key: "sku",
          render: (item) => (
            <div className="grid gap-0.5 min-w-0">
              <div className="flex items-center gap-1.5">
                <span className="whitespace-nowrap font-black text-[#172317]">{item.skuCode}</span>
                <span className="rounded bg-[#fef2f2] px-1.5 py-0.5 text-[10px] font-bold text-red-700">
                  {item.category}
                </span>
              </div>
              <span className="truncate text-xs font-semibold text-[#697684]">{item.skuName}</span>
            </div>
          ),
        },
        {
          align: "right",
          header: "Số ngày tồn",
          key: "days",
          render: (item) => (
            <div className="inline-flex items-center gap-1 text-xs font-black text-[#d97706]">
              <Clock size={13} />
              <span>{item.daysUnmoved} ngày</span>
            </div>
          ),
        },
        {
          align: "right",
          header: "Tồn kho / Giá trị đọng",
          key: "value",
          render: (item) => (
            <div className="grid gap-0.5 text-right">
              <span className="font-black text-[#172317]">
                {item.quantityOnHand.toLocaleString("vi-VN")} {item.unitSymbol ?? ""}
              </span>
              <span className="text-xs font-bold text-[#b91c1c]">
                {formatCompactVnd(item.inventoryValue)} đ
              </span>
            </div>
          ),
        },
        {
          align: "right",
          header: "Thao tác",
          key: "action",
          render: (item) => (
            <Link
              aria-label="Tạo phiếu kiểm kê spot"
              className="inline-flex size-7 items-center justify-center rounded-lg bg-[#159734]/10 text-[#159734] transition hover:bg-[#159734] hover:text-white"
              title="Tạo phiếu kiểm kê spot"
              to={`/management/inventory/stocktakes/new?scopeType=ITEM&itemId=${item.id}`}
            >
              <ClipboardEdit size={14} />
            </Link>
          ),
        },
      ]}
      getRowKey={(item) => item.id}
      rows={items}
    />
  );
}
