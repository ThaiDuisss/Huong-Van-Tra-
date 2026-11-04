import { ChevronLeft, ChevronRight, ClipboardList, RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { adjustmentStatusLabels } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type {
  AdjustmentListItem,
  AdjustmentStatus,
  StocktakeOptions,
} from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const pageSize = 20;

export function StocktakeAdjustmentListPage() {
  const [items, setItems] = useState<AdjustmentListItem[]>([]);
  const [options, setOptions] = useState<StocktakeOptions | null>(null);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [branchId, setBranchId] = useState("ALL");
  const [status, setStatus] = useState<"ALL" | AdjustmentStatus>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [result, stocktakeOptions] = await Promise.all([
        stocktakeApi.listAdjustments({
          branchId: branchId === "ALL" ? undefined : branchId,
          keyword: appliedKeyword || undefined,
          page,
          size: pageSize,
          status: status === "ALL" ? undefined : status,
        }),
        options ? Promise.resolve(options) : stocktakeApi.getStocktakeOptions(),
      ]);
      setItems(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
      setOptions(stocktakeOptions);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách phiếu.");
    } finally {
      setLoading(false);
    }
  }, [appliedKeyword, branchId, options, page, status]);

  useEffect(() => {
    const handle = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  function applySearch() {
    setPage(0);
    setAppliedKeyword(keyword.trim());
  }

  function resetFilters() {
    setKeyword("");
    setAppliedKeyword("");
    setBranchId("ALL");
    setStatus("ALL");
    setPage(0);
  }

  return (
    <section className="grid min-h-[calc(100vh-120px)] gap-4 text-[#172317]">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold sm:text-3xl">Phiếu cập nhật tồn kho</h1>
          <p className="mt-1 text-sm text-gray-500">
            Quản lý tập trung các phiếu được tạo từ kết quả kiểm kê.
          </p>
        </div>
        <button
          className={secondaryButton}
          disabled={loading}
          onClick={() => void load()}
          type="button"
        >
          <RefreshCw size={17} /> Làm mới
        </button>
      </header>

      <section className="grid gap-3 rounded-xl border border-[#dce5e1] bg-white p-4 shadow-[0_8px_24px_rgba(31,53,43,0.05)] lg:grid-cols-[minmax(260px,1fr)_220px_220px_auto] lg:items-end">
        <label className="grid gap-1.5 text-xs font-bold text-[#536171]">
          Tìm phiếu
          <div className="flex">
            <input
              className="h-10 min-w-0 flex-1 rounded-l-xl border border-r-0 border-[#dce5e1] px-3 text-sm font-semibold outline-none focus:border-[#159734]"
              onChange={(event) => setKeyword(event.currentTarget.value)}
              onKeyDown={(event) => event.key === "Enter" && applySearch()}
              placeholder="Nhập mã phiếu"
              value={keyword}
            />
            <button
              aria-label="Tìm kiếm"
              className="grid size-10 place-items-center rounded-r-xl bg-[#159734] text-white"
              onClick={applySearch}
              type="button"
            >
              <Search size={17} />
            </button>
          </div>
        </label>
        <SelectField
          label="Chi nhánh"
          onChange={(value) => {
            setBranchId(value);
            setPage(0);
          }}
          options={[
            { label: "Tất cả", value: "ALL" },
            ...(options?.branches.map((branch) => ({ label: branch.name, value: branch.id })) ??
              []),
          ]}
          value={branchId}
        />
        <SelectField
          label="Trạng thái"
          onChange={(value) => {
            setStatus(value as "ALL" | AdjustmentStatus);
            setPage(0);
          }}
          options={[
            { label: "Tất cả", value: "ALL" },
            ...Object.entries(adjustmentStatusLabels).map(([value, label]) => ({ label, value })),
          ]}
          value={status}
        />
        <button className={secondaryButton} onClick={resetFilters} type="button">
          Xóa bộ lọc
        </button>
      </section>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">
          {error}
        </div>
      ) : null}

      <section className="grid min-h-[420px] grid-rows-[1fr_auto] overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
        <div className="overflow-auto">
          <table className="w-full min-w-[1100px] text-left text-sm">
            <thead className="sticky top-0 bg-[#f3f7f4] text-xs font-black uppercase text-[#536171]">
              <tr>
                {[
                  "Mã phiếu",
                  "Kỳ kiểm kê",
                  "Chi nhánh",
                  "Ngày tạo",
                  "Số mặt hàng",
                  "Tổng điều chỉnh",
                  "Trạng thái",
                  "Ngày cập nhật",
                  "",
                ].map((label) => (
                  <th className="border-b border-[#dce5e1] px-4 py-3" key={label}>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className={loading ? "opacity-50" : ""}>
              {items.map((item) => {
                const quantity = item.lines.reduce(
                  (total, line) => total + line.adjustmentQuantity,
                  0,
                );
                const branch = options?.branches.find(
                  (candidate) => candidate.id === item.branchId,
                );
                // Phiếu từ kiểm kê được xem ngay trong màn chi tiết kỳ kiểm kê
                const target =
                  item.referenceType === "STOCK_COUNT" && item.referenceId
                    ? `/management/inventory/stocktakes/${item.referenceId}`
                    : null;
                const countCode = item.note?.match(/CT-[\w-]+/)?.[0];
                return (
                  <tr key={item.id}>
                    <td className="border-b border-[#edf2ef] px-4 py-3 font-black text-[#159734]">
                      {item.documentCode}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      {target ? (
                        <Link className="font-bold text-[#159734] hover:underline" to={target}>
                          {countCode ?? "Xem kỳ kiểm kê"}
                        </Link>
                      ) : (
                        "Điều chỉnh thủ công"
                      )}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      {branch?.name ?? item.branchId.slice(0, 8)}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      {formatVietnamDateTime(item.createdAt)}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">{item.lines.length}</td>
                    <td
                      className={`border-b border-[#edf2ef] px-4 font-black ${quantity < 0 ? "text-red-700" : "text-emerald-700"}`}
                    >
                      {quantity > 0 ? "+" : ""}
                      {formatVietnamNumber(quantity)}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      <Status status={item.status} />
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      {item.postedAt ? formatVietnamDateTime(item.postedAt) : "Chưa cập nhật"}
                    </td>
                    <td className="border-b border-[#edf2ef] px-4">
                      {target ? (
                        <Link className="font-bold text-[#159734] hover:underline" to={target}>
                          Xem phiếu
                        </Link>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
              {!loading && !items.length ? (
                <tr>
                  <td colSpan={9}>
                    <div className="grid min-h-80 place-items-center text-center">
                      <div>
                        <ClipboardList className="mx-auto text-[#159734]" size={30} />
                        <h2 className="mt-3 font-black">Chưa có phiếu phù hợp</h2>
                        <p className="mt-1 text-sm text-[#697684]">
                          Thử thay đổi bộ lọc hoặc tạo phiếu từ kỳ kiểm kê đã duyệt.
                        </p>
                      </div>
                    </div>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <footer className="flex min-h-14 items-center justify-between border-t border-[#e4ece8] px-4 text-xs font-bold text-[#657282]">
          <span>Tổng {formatVietnamNumber(totalElements)} phiếu</span>
          <div className="flex items-center gap-2">
            <button
              className={pageButton}
              disabled={loading || page === 0}
              onClick={() => setPage((value) => value - 1)}
              type="button"
            >
              <ChevronLeft size={16} />
            </button>
            <span>
              Trang {page + 1}/{Math.max(totalPages, 1)}
            </span>
            <button
              className={pageButton}
              disabled={loading || page + 1 >= totalPages}
              onClick={() => setPage((value) => value + 1)}
              type="button"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </footer>
      </section>
    </section>
  );
}

function SelectField({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: Array<{ label: string; value: string }>;
  value: string;
}) {
  return (
    <label className="grid gap-1.5 text-xs font-bold text-[#536171]">
      {label}
      <select
        className="h-10 rounded-xl border border-[#dce5e1] bg-white px-3 text-sm font-semibold outline-none focus:border-[#159734]"
        onChange={(event) => onChange(event.currentTarget.value)}
        value={value}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
function Status({ status }: { status: AdjustmentStatus }) {
  const tone =
    status === "REJECTED"
      ? "bg-red-50 text-red-700"
      : status === "POSTED"
        ? "bg-emerald-50 text-emerald-700"
        : "bg-amber-50 text-amber-700";
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-black ${tone}`}>
      {adjustmentStatusLabels[status]}
    </span>
  );
}
const secondaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#536171] disabled:opacity-50";
const pageButton =
  "grid size-8 place-items-center rounded-lg border border-[#dce5e1] disabled:opacity-40";
