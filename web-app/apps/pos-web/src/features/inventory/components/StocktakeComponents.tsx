import { ChevronRight, ClipboardCheck, Filter, Plus, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";

import { stocktakeScopeLabels, stocktakeStatusLabels } from "../constants/stocktake.constants";
import type {
  StocktakeListQuery,
  StocktakeOptions,
  StocktakeSummary,
} from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

export function StocktakePageHeader({ onRefresh }: { onRefresh: () => void }) {
  return (
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div className="grid gap-1">
        <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">Kiểm kê kho</h1>
        <p className="text-sm text-gray-500">
          Theo dõi số đếm thực tế, chênh lệch và phiếu điều chỉnh tồn kho.
        </p>
      </div>
      <div className="flex gap-2.5">
        <button className={secondaryButton} onClick={onRefresh} type="button">
          <RefreshCw size={17} /> Làm mới
        </button>
        <Link className={primaryButton} to="/management/inventory/stocktakes/new">
          <Plus size={17} /> Tạo phiên kiểm kê
        </Link>
      </div>
    </header>
  );
}

export function StocktakeSummaryCards({
  result,
}: {
  result:
    | {
        draft: number;
        counting: number;
        completed: number;
        adjustmentReady: number;
        posted: number;
      }
    | undefined;
}) {
  const cards = [
    ["Chưa bắt đầu", result?.draft ?? 0],
    ["Đang kiểm kê", result?.counting ?? 0],
    ["Hoàn tất", result?.completed ?? 0],
    ["Chờ điều chỉnh", result?.adjustmentReady ?? 0],
    ["Đã cập nhật tồn kho", result?.posted ?? 0],
  ] as const;
  return (
    <section className="grid gap-2">
      <p className="text-xs font-bold text-[#7a8790]">Thống kê trên trang dữ liệu hiện tại</p>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {cards.map(([label, value]) => (
          <article
            className="rounded-xl border border-[#dce5e1] bg-white p-4 shadow-[0_8px_24px_rgba(31,53,43,0.05)]"
            key={label}
          >
            <span className="text-xs font-bold uppercase tracking-wide text-[#657282]">
              {label}
            </span>
            <strong className="mt-2 block text-2xl font-black text-[#172317]">
              {formatVietnamNumber(value)}
            </strong>
          </article>
        ))}
      </div>
    </section>
  );
}

export function StocktakeFilterBar({
  branches,
  onChange,
  onReset,
  query,
}: {
  branches: StocktakeOptions["branches"];
  onChange: (patch: Partial<StocktakeListQuery>) => void;
  onReset: () => void;
  query: StocktakeListQuery;
}) {
  return (
    <section className="flex flex-col gap-3 rounded-xl border border-[#dce5e1] bg-white p-4 shadow-[0_8px_24px_rgba(31,53,43,0.05)] lg:flex-row lg:items-end">
      <div className="flex items-center gap-2 pb-2 text-sm font-black text-[#315f3c]">
        <Filter size={17} /> Bộ lọc
      </div>
      <div className="grid flex-1 gap-3 sm:grid-cols-3">
        <SelectField
          label="Chi nhánh"
          value={query.branchId}
          onChange={(branchId) => onChange({ branchId, page: 0 })}
          options={[
            { label: "Tất cả", value: "ALL" },
            ...branches.map((branch) => ({ label: branch.name, value: branch.id })),
          ]}
        />
        <SelectField
          label="Trạng thái"
          value={query.status}
          onChange={(status) =>
            onChange({ page: 0, status: status as StocktakeListQuery["status"] })
          }
          options={[
            { label: "Tất cả", value: "ALL" },
            ...Object.entries(stocktakeStatusLabels).map(([value, label]) => ({ label, value })),
          ]}
        />
        <SelectField
          label="Phạm vi"
          value={query.scopeType}
          onChange={(scopeType) =>
            onChange({ page: 0, scopeType: scopeType as StocktakeListQuery["scopeType"] })
          }
          options={[
            { label: "Tất cả", value: "ALL" },
            ...Object.entries(stocktakeScopeLabels).map(([value, label]) => ({ label, value })),
          ]}
        />
      </div>
      <button className={secondaryButton} onClick={onReset} type="button">
        Xóa bộ lọc
      </button>
    </section>
  );
}

export function StocktakeTable({
  loading,
  stocktakes,
  totalElements,
  totalPages,
  page,
  onPageChange,
}: {
  loading: boolean;
  stocktakes: StocktakeSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  onPageChange: (page: number) => void;
}) {
  const empty = !loading && stocktakes.length === 0;
  return (
    <section className="grid min-h-[360px] flex-1 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
      <div className="min-h-0 overflow-auto">
        <table className="w-full min-w-[940px] border-separate border-spacing-0 text-[13px] text-[#233025]">
          <thead className="sticky top-0 z-10 bg-[#f3f7f4] text-left text-xs font-black uppercase text-[#536171]">
            <tr>
              {[
                "Mã kiểm kê",
                "Chi nhánh",
                "Phạm vi",
                "Trạng thái",
                "Tiến độ",
                "Chênh lệch",
                "Bắt đầu",
                "",
              ].map((label) => (
                <th className="border-b border-[#dce5e1] px-4 py-3" key={label}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className={loading && stocktakes.length ? "opacity-50" : ""}>
            {loading && !stocktakes.length
              ? Array.from({ length: 7 }, (_, row) => (
                  <tr key={row}>
                    {Array.from({ length: 8 }, (_, cell) => (
                      <td className="border-b border-[#edf2ef] px-4 py-4" key={cell}>
                        <span className="block h-4 animate-pulse rounded bg-[#edf3f0]" />
                      </td>
                    ))}
                  </tr>
                ))
              : null}
            {empty ? (
              <tr>
                <td colSpan={8}>
                  <div className="grid min-h-[300px] place-items-center p-8 text-center">
                    <div>
                      <ClipboardCheck className="mx-auto text-[#159734]" />
                      <h2 className="mt-3 font-black">Chưa có phiên kiểm kê phù hợp</h2>
                      <p className="mt-1 text-sm text-[#697684]">
                        Thử thay đổi bộ lọc hoặc tạo phiên kiểm kê mới.
                      </p>
                    </div>
                  </div>
                </td>
              </tr>
            ) : null}
            {stocktakes.map((item, index) => (
              <tr className={index % 2 ? "bg-[#f8faf9]" : "bg-white"} key={item.id}>
                <td className="border-b border-[#edf2ef] px-4 py-3 font-black text-[#159734]">
                  {item.countCode}
                </td>
                <td className="border-b border-[#edf2ef] px-4 py-3">{item.branch.name}</td>
                <td className="border-b border-[#edf2ef] px-4 py-3">{item.scopeLabel}</td>
                <td className="border-b border-[#edf2ef] px-4 py-3">
                  <StatusBadge status={item.status} />
                </td>
                <td className="border-b border-[#edf2ef] px-4 py-3 font-bold">
                  {item.countedLines}/{item.totalLines}
                </td>
                <td className="border-b border-[#edf2ef] px-4 py-3">{item.varianceLines}</td>
                <td className="border-b border-[#edf2ef] px-4 py-3">
                  {item.countingStartedAt ? formatVietnamDateTime(item.countingStartedAt) : "-"}
                </td>
                <td className="border-b border-[#edf2ef] px-4 py-3">
                  <Link
                    aria-label={`Mở ${item.countCode}`}
                    className="grid size-8 place-items-center rounded-lg text-[#159734] hover:bg-[#eaf8ee]"
                    to={`/management/inventory/stocktakes/${item.id}`}
                  >
                    <ChevronRight size={18} />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <footer className="flex min-h-[54px] items-center justify-between border-t border-[#e4ece8] px-4 text-xs font-bold text-[#657282]">
        <span>Tổng {totalElements.toLocaleString("vi-VN")} phiên kiểm kê</span>
        <div className="flex items-center gap-2">
          <button
            className={pageButton}
            disabled={loading || page <= 0}
            onClick={() => onPageChange(page - 1)}
            type="button"
          >
            Trước
          </button>
          <span>
            Trang {page + 1}/{Math.max(totalPages, 1)}
          </span>
          <button
            className={pageButton}
            disabled={loading || page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
            type="button"
          >
            Sau
          </button>
        </div>
      </footer>
    </section>
  );
}

export function StatusBadge({ status }: { status: StocktakeSummary["status"] }) {
  const style =
    status === "DRAFT"
      ? "bg-slate-100 text-slate-700"
      : status === "FROZEN" ||
          status === "COUNTING" ||
          status === "RECONCILING" ||
          status === "PENDING_APPROVAL" ||
          status === "RETURNED"
        ? "bg-amber-50 text-amber-700"
        : status === "CANCELLED" || status === "REJECTED"
          ? "bg-red-50 text-red-700"
          : "bg-emerald-50 text-emerald-700";
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-black ${style}`}>
      {stocktakeStatusLabels[status]}
    </span>
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

const primaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white hover:bg-[#12862d]";
const secondaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#536171] hover:bg-[#f7faf8] disabled:opacity-50";
const pageButton = "h-8 rounded-lg border border-[#dce5e1] px-3 disabled:opacity-40";
