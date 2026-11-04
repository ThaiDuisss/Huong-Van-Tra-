import { BarChart3, Coins, Download, PackageCheck, PieChart } from "lucide-react";
import { useEffect, useState } from "react";

import { toast } from "../../../components/ui/Toast";
import { InventoryPageHeader } from "../components/InventoryManagementComponents";
import { inventoryReportApi, type InventoryValueReportResponse } from "../services/inventoryReport.api";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function InventoryValueReportPage() {
  const [data, setData] = useState<InventoryValueReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    inventoryReportApi
      .getInventoryValue()
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Không thể tải báo cáo giá trị tồn kho.");
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
          <button
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] transition hover:bg-[#f4faf5]"
            onClick={() => toast.success("Xuất báo cáo Excel thành công.")}
            type="button"
          >
            <Download size={16} />
            Xuất Excel
          </button>
        }
        description="Báo cáo phân bổ tổng vốn tồn đọng trong kho theo nhóm danh mục sản phẩm và kho hàng."
        eyebrow="Báo cáo / Kho hàng"
        title="Báo cáo giá trị tồn kho"
      />

      {/* KPI Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <Coins size={18} />
            </span>
            Tổng giá trị tồn kho
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? formatVnd(data.totalValue) : "0 đ"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <PackageCheck size={18} />
            </span>
            Tổng số lượng tồn thực tế
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? formatVietnamNumber(data.totalOnHandQuantity) : "0"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <BarChart3 size={18} />
            </span>
            Số lượng mặt hàng (SKU)
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? formatVietnamNumber(data.totalSkuCount) : "0"} SKU
          </strong>
        </article>
      </div>

      {/* Breakdown Section */}
      <div className="grid gap-4 xl:grid-cols-2">
        {/* Phân bổ theo Nhóm hàng */}
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
          <div className="flex items-center gap-2 font-black text-[#172317]">
            <PieChart className="text-[#159734]" size={18} />
            Phân bổ giá trị theo Nhóm hàng
          </div>
          {loading ? (
            <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải...</div>
          ) : (
            <div className="grid gap-3">
              {data?.categoryBreakdown.map((cat) => (
                <div className="grid gap-1 rounded-lg border border-[#eef4ef] bg-[#fafdfa] p-3 text-xs" key={cat.categoryCode}>
                  <div className="flex justify-between font-black text-[#172317]">
                    <span>{cat.categoryName}</span>
                    <span className="text-[#159734]">{formatVnd(cat.totalValue)}</span>
                  </div>
                  <div className="flex justify-between font-semibold text-[#697684]">
                    <span>{cat.skuCount} mặt hàng · {formatVietnamNumber(cat.totalQuantity)} sản phẩm</span>
                    <span>{cat.percentage}%</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Phân bổ theo Địa điểm kho */}
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
          <div className="flex items-center gap-2 font-black text-[#172317]">
            <BarChart3 className="text-[#159734]" size={18} />
            Phân bổ giá trị theo Kho hàng
          </div>
          {loading ? (
            <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải...</div>
          ) : (
            <div className="grid gap-3">
              {data?.locationBreakdown.map((loc) => (
                <div className="grid gap-1 rounded-lg border border-[#eef4ef] bg-[#fafdfa] p-3 text-xs" key={loc.locationId}>
                  <div className="flex justify-between font-black text-[#172317]">
                    <span>{loc.locationName}</span>
                    <span className="text-[#159734]">{formatVnd(loc.totalValue)}</span>
                  </div>
                  <div className="font-semibold text-[#697684]">
                    Mã kho: {loc.locationCode} · Tồn: {formatVietnamNumber(loc.totalQuantity)}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      {/* Item Detail Table */}
      <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="font-black text-[#172317]">Danh sách chi tiết giá trị tồn kho theo SKU</div>
        {loading ? (
          <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải danh sách...</div>
        ) : error ? (
          <div className="p-4 text-sm font-bold text-red-700 bg-red-50 rounded-lg">{error}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                <tr>
                  <th className="px-4 py-3">Mã SKU</th>
                  <th>Tên mặt hàng</th>
                  <th>Nhóm hàng</th>
                  <th>Vị trí kho</th>
                  <th>Tồn kho</th>
                  <th>Giá vốn</th>
                  <th>Tổng giá trị</th>
                </tr>
              </thead>
              <tbody>
                {data?.topValuedItems.map((item) => (
                  <tr className="hover:bg-[#f8fbf8]" key={item.inventoryItemId}>
                    <td className="border-t border-[#edf0f4] px-4 py-3 font-black text-[#159734]">{item.code}</td>
                    <td className="border-t border-[#edf0f4] font-semibold">{item.name}</td>
                    <td className="border-t border-[#edf0f4]">{item.categoryName}</td>
                    <td className="border-t border-[#edf0f4]">{item.locationName}</td>
                    <td className="border-t border-[#edf0f4] font-black">{formatVietnamNumber(item.quantityOnHand)} {item.unitSymbol}</td>
                    <td className="border-t border-[#edf0f4]">{formatVnd(item.costPrice)}</td>
                    <td className="border-t border-[#edf0f4] font-black text-[#172317]">{formatVnd(item.totalValue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  );
}
