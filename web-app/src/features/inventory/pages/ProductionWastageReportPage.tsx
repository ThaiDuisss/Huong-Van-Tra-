import { Download, Factory, Flame, PackageX } from "lucide-react";
import { useEffect, useState } from "react";

import { InventoryPageHeader } from "../components/InventoryManagementComponents";
import { inventoryReportApi, type ProductionWastageReportResponse } from "../services/inventoryReport.api";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function ProductionWastageReportPage() {
  const [data, setData] = useState<ProductionWastageReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    inventoryReportApi
      .getProductionWastage()
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Không thể tải báo cáo hao hụt sản xuất.");
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
            onClick={() => alert("Xuất báo cáo hao hụt thành công.")}
            type="button"
          >
            <Download size={16} />
            Xuất Excel
          </button>
        }
        description="Theo dõi chênh lệch định mức nguyên liệu dự kiến (BOM) so với thực tế tiêu hao trong quá trình chế biến & sản xuất."
        eyebrow="Báo cáo / Sản xuất"
        title="Báo cáo hao hụt sản xuất (Production Wastage Report)"
      />

      {/* KPI Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <Factory size={18} />
            </span>
            Tổng NL định mức (BOM)
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? formatVietnamNumber(data.totalPlannedQuantity) : "0"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#fff1f1] text-red-600">
              <Flame size={18} />
            </span>
            Tổng lượng hao hụt vượt định mức
          </div>
          <strong className="text-2xl font-black text-red-600">
            {data ? formatVietnamNumber(data.totalWastageQuantity) : "0"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#fff1f1] text-red-600">
              <PackageX size={18} />
            </span>
            Ước tính giá trị thiệt hại
          </div>
          <strong className="text-2xl font-black text-red-600">
            {data ? formatVnd(data.totalWastageValue) : "0 đ"}
          </strong>
        </article>
      </div>

      {/* Detail Table */}
      <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="font-black text-[#172317]">Chi tiết chênh lệch tiêu hao nguyên liệu</div>
        {loading ? (
          <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải chi tiết...</div>
        ) : error ? (
          <div className="p-4 text-sm font-bold text-red-700 bg-red-50 rounded-lg">{error}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                <tr>
                  <th className="px-4 py-3">Mã nguyên liệu</th>
                  <th>Tên nguyên liệu</th>
                  <th>Định mức (BOM)</th>
                  <th>Thực tế xuất</th>
                  <th>Hao hụt (Chênh lệch)</th>
                  <th>Tỷ lệ hao hụt</th>
                  <th>Giá trị tổn thất</th>
                </tr>
              </thead>
              <tbody>
                {data?.materials.map((mat) => (
                  <tr className="hover:bg-[#f8fbf8]" key={mat.materialCode}>
                    <td className="border-t border-[#edf0f4] px-4 py-3 font-black text-[#159734]">{mat.materialCode}</td>
                    <td className="border-t border-[#edf0f4] font-semibold">{mat.materialName}</td>
                    <td className="border-t border-[#edf0f4]">{formatVietnamNumber(mat.plannedQuantity)} {mat.unitSymbol}</td>
                    <td className="border-t border-[#edf0f4]">{formatVietnamNumber(mat.actualQuantity)} {mat.unitSymbol}</td>
                    <td className="border-t border-[#edf0f4] font-black text-red-600">+{formatVietnamNumber(mat.wastageQuantity)} {mat.unitSymbol}</td>
                    <td className="border-t border-[#edf0f4] font-black text-red-600">{mat.wastageRate}%</td>
                    <td className="border-t border-[#edf0f4] font-black text-[#172317]">{formatVnd(mat.estimatedLossValue)}</td>
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
