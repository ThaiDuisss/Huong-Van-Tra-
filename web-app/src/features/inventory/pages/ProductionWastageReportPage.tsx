import { Download, Factory, Flame, PackageX } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { toast } from "../../../components/ui/Toast";
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
          <Button onClick={() => toast.success("Xuất báo cáo hao hụt thành công.")}>
            <Download size={16} />
            Xuất Excel
          </Button>
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
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeaderCell>Mã nguyên liệu</TableHeaderCell>
                  <TableHeaderCell>Tên nguyên liệu</TableHeaderCell>
                  <TableHeaderCell>Định mức (BOM)</TableHeaderCell>
                  <TableHeaderCell>Thực tế xuất</TableHeaderCell>
                  <TableHeaderCell>Hao hụt (Chênh lệch)</TableHeaderCell>
                  <TableHeaderCell>Tỷ lệ hao hụt</TableHeaderCell>
                  <TableHeaderCell>Giá trị tổn thất</TableHeaderCell>
                </TableRow>
              </TableHead>
              <tbody>
                {data?.materials.map((mat) => (
                  <TableRow className="hover:bg-[#f8fbf8]" key={mat.materialCode}>
                    <TableCell className="font-black text-[#159734]">{mat.materialCode}</TableCell>
                    <TableCell className="font-semibold">{mat.materialName}</TableCell>
                    <TableCell>{formatVietnamNumber(mat.plannedQuantity)} {mat.unitSymbol}</TableCell>
                    <TableCell>{formatVietnamNumber(mat.actualQuantity)} {mat.unitSymbol}</TableCell>
                    <TableCell className="font-black text-red-600">+{formatVietnamNumber(mat.wastageQuantity)} {mat.unitSymbol}</TableCell>
                    <TableCell className="font-black text-red-600">{mat.wastageRate}%</TableCell>
                    <TableCell className="font-black text-[#172317]">{formatVnd(mat.estimatedLossValue)}</TableCell>
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
