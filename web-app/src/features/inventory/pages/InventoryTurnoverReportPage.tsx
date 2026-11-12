import { Clock3, Download, RotateCcw, TrendingDown } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { toast } from "../../../components/ui/Toast";
import { InventoryPageHeader } from "../components/InventoryManagementComponents";
import { inventoryReportApi, type InventoryTurnoverReportResponse } from "../services/inventoryReport.api";
import { formatVietnamNumber } from "../utils/inventory-format";

export function InventoryTurnoverReportPage() {
  const [data, setData] = useState<InventoryTurnoverReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    inventoryReportApi
      .getTurnover()
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Không thể tải báo cáo vòng quay.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="grid gap-5">
      <InventoryPageHeader
        actions={
          <Button onClick={() => toast.success("Xuất báo cáo thành công.")}>
            <Download size={16} />
            Xuất Excel
          </Button>
        }
        description="Báo cáo tốc độ luân chuyển kho, số ngày tồn kho bình quân (DSI) và phân nhóm hàng hóa để giải phóng vốn tồn đọng."
        eyebrow="Báo cáo / Kho hàng"
        title="Báo cáo hiệu suất luân chuyển tồn kho"
      />

      {/* KPI Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <RotateCcw size={18} />
            </span>
            Hệ số luân chuyển kho trung bình
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? `${data.averageTurnoverRate} lần` : "0 lần"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <Clock3 size={18} />
            </span>
            Số ngày tồn kho trung bình (DSI)
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? `${data.averageDsi} ngày` : "0 ngày"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#fff1f1] text-red-600">
              <TrendingDown size={18} />
            </span>
            Mặt hàng tồn lâu ngày (DSI &gt; 90)
          </div>
          <strong className="text-2xl font-black text-red-600">
            {data ? formatVietnamNumber(data.slowMovingSkuCount) : "0"} SKU
          </strong>
        </article>
      </div>

      {/* Detail Table */}
      <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="font-black text-[#172317]">Phân tích tốc độ luân chuyển & Phân nhóm mặt hàng theo SKU</div>
        {loading ? (
          <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải báo cáo...</div>
        ) : error ? (
          <div className="p-4 text-sm font-bold text-red-700 bg-red-50 rounded-lg">{error}</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeaderCell>Phân loại</TableHeaderCell>
                  <TableHeaderCell>Mã SKU</TableHeaderCell>
                  <TableHeaderCell>Tên mặt hàng</TableHeaderCell>
                  <TableHeaderCell>Nhóm hàng</TableHeaderCell>
                  <TableHeaderCell>Tồn kho trung bình</TableHeaderCell>
                  <TableHeaderCell>Hệ số quay vòng</TableHeaderCell>
                  <TableHeaderCell>Số ngày tồn (DSI)</TableHeaderCell>
                </TableRow>
              </TableHead>
              <tbody>
                {data?.items.map((item) => (
                  <TableRow className="hover:bg-[#f8fbf8]" key={item.inventoryItemId}>
                    <TableCell className="font-black">
                      <StatusBadge shape="tag" tone={item.abcCategory === "A" ? "success" : item.abcCategory === "B" ? "info" : "danger"}>
                        Nhóm {item.abcCategory}
                      </StatusBadge>
                    </TableCell>
                    <TableCell className="font-black text-[#159734]">{item.code}</TableCell>
                    <TableCell className="font-semibold">{item.name}</TableCell>
                    <TableCell>{item.categoryName}</TableCell>
                    <TableCell className="font-black">{formatVietnamNumber(item.averageStock)} {item.unitSymbol}</TableCell>
                    <TableCell className="font-black">{item.turnoverRate} lần</TableCell>
                    <TableCell className="font-black text-[#172317]">{item.dsi} ngày</TableCell>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        )}
      </section>
    </section>
  );
}
