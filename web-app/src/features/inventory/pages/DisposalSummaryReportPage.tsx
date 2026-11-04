import { AlertTriangle, Download, FileText, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { toast } from "../../../components/ui/Toast";
import { InventoryPageHeader } from "../components/InventoryManagementComponents";
import { inventoryReportApi, type DisposalSummaryReportResponse } from "../services/inventoryReport.api";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function DisposalSummaryReportPage() {
  const [data, setData] = useState<DisposalSummaryReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    inventoryReportApi
      .getDisposalSummary()
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Không thể tải báo cáo tiêu hủy.");
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
            onClick={() => toast.success("Xuất báo cáo thành công.")}
            type="button"
          >
            <Download size={16} />
            Xuất Excel
          </button>
        }
        description="Báo cáo tổng hợp tổn thất từ các nguyên nhân: hàng hết hạn, hàng hỏng hóc, trả lại nhà cung cấp, khách trả hàng và phiếu tiêu hủy (Write-off)."
        eyebrow="Báo cáo / Kho hàng"
        title="Báo cáo hàng hỏng, trả lại & tiêu hủy"
      />

      {/* KPI Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#fff1f1] text-red-600">
              <Trash2 size={18} />
            </span>
            Tổng giá trị hàng hỏng & tiêu hủy
          </div>
          <strong className="text-2xl font-black text-red-600">
            {data ? formatVnd(data.totalDisposalValue) : "0 đ"}
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#fff7ed] text-amber-600">
              <AlertTriangle size={18} />
            </span>
            Số lô đã hết hạn / hư hỏng
          </div>
          <strong className="text-2xl font-black text-amber-600">
            {data ? formatVietnamNumber(data.expiredBatchesCount + data.damagedBatchesCount) : "0"} lô
          </strong>
        </article>

        <article className="grid min-h-[94px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
          <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
            <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
              <FileText size={18} />
            </span>
            Số phiếu tiêu hủy (Write-off)
          </div>
          <strong className="text-2xl font-black text-[#172317]">
            {data ? formatVietnamNumber(data.writeOffDocumentsCount) : "0"} phiếu
          </strong>
        </article>
      </div>

      {/* Detail Table */}
      <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="font-black text-[#172317]">Danh sách chi tiết chứng từ & lô hàng tiêu hủy</div>
        {loading ? (
          <div className="py-8 text-center text-sm font-bold text-[#697684]">Đang tải dữ liệu...</div>
        ) : error ? (
          <div className="p-4 text-sm font-bold text-red-700 bg-red-50 rounded-lg">{error}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                <tr>
                  <th className="px-4 py-3">Mã phiếu</th>
                  <th>Loại nghiệp vụ</th>
                  <th>Số lượng</th>
                  <th>Giá trị thiệt hại</th>
                  <th>Lý do tiêu hủy</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {data?.items.map((item, idx) => (
                  <tr className="hover:bg-[#f8fbf8]" key={idx}>
                    <td className="border-t border-[#edf0f4] px-4 py-3 font-black text-[#159734]">{item.code}</td>
                    <td className="border-t border-[#edf0f4]">
                      <span className="inline-block px-2 py-0.5 rounded text-xs bg-red-100 text-red-800 font-bold">
                        {item.type}
                      </span>
                    </td>
                    <td className="border-t border-[#edf0f4] font-black">{formatVietnamNumber(item.quantity)} {item.unitSymbol}</td>
                    <td className="border-t border-[#edf0f4] font-black text-red-600">{formatVnd(item.estimatedValue)}</td>
                    <td className="border-t border-[#edf0f4] font-semibold">{item.reason}</td>
                    <td className="border-t border-[#edf0f4] text-xs">{formatVietnamDateTime(item.date)}</td>
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
