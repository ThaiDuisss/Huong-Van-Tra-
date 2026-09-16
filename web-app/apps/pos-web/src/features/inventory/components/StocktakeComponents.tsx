import { ArrowUpDown, ChevronRight, ClipboardCheck, Filter, Plus, RefreshCw } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../../components/ui/select";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import {
  stocktakeScopeLabels,
  stocktakeStatusFilterOptions,
  stocktakeStatusLabels,
} from "../constants/stocktake.constants";
import type {
  StocktakeListQuery,
  StocktakeOptions,
  StocktakeSort,
  StocktakeSummary,
} from "../types/stocktake.types";
import { formatVietnamDateTime } from "../utils/inventory-format";

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
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterDraft, setFilterDraft] = useState({
    branchId: query.branchId,
    scopeType: query.scopeType,
  });
  const appliedFilterCount = Number(query.branchId !== "ALL") + Number(query.scopeType !== "ALL");

  function openFilter() {
    setFilterDraft({ branchId: query.branchId, scopeType: query.scopeType });
    setFilterOpen(true);
  }

  return (
    <section className="rounded-2xl bg-white p-3 shadow-pos-card">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav
          aria-label="Lọc theo trạng thái kiểm kê"
          className="flex min-w-0 flex-1 gap-2 overflow-x-auto"
        >
          {[{ label: "Tất cả", value: "ALL" as const }, ...stocktakeStatusFilterOptions].map(
            ({ label, value }) => {
              const active = query.status === value;
              return (
                <button
                  aria-pressed={active}
                  className={`inline-flex h-8 shrink-0 items-center rounded-full border px-3 text-xs font-extrabold transition-colors ${
                    active
                      ? "border-[#159734] bg-[#eaf8ee] text-[#116f28] shadow-[0_1px_2px_rgba(21,151,52,0.08)]"
                      : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1] hover:text-[#172317]"
                  }`}
                  key={value}
                  onClick={() => onChange({ page: 0, status: value })}
                  type="button"
                >
                  {label}
                </button>
              );
            },
          )}
        </nav>
        <div className="flex min-w-0 flex-wrap items-center justify-end gap-2.5">
          <div className="w-full min-w-[240px] md:w-[320px]">
            <SearchBox
              onChange={(keyword) => onChange({ keyword, page: 0 })}
              placeholder="Tìm mã kiểm kê..."
              value={query.keyword}
            />
          </div>
          <button
            aria-expanded={filterOpen}
            className={`${toolbarButton} relative`}
            onClick={openFilter}
            type="button"
          >
            <Filter size={17} aria-hidden="true" />
            Bộ lọc
            {appliedFilterCount > 0 ? (
              <span className="absolute -right-1.5 -top-1.5 grid min-w-5 place-items-center rounded-full bg-[#159734] px-1.5 text-[10px] font-black leading-5 text-white shadow-sm">
                {appliedFilterCount}
              </span>
            ) : null}
          </button>
          <SortSelect onChange={(sort) => onChange({ page: 0, sort })} value={query.sort} />
        </div>
      </div>
      {filterOpen ? (
        <StocktakeFilterPopover
          branches={branches}
          draft={filterDraft}
          onApply={() => {
            onChange({ ...filterDraft, page: 0 });
            setFilterOpen(false);
          }}
          onCancel={() => setFilterOpen(false)}
          onClear={() => {
            onReset();
            setFilterDraft({ branchId: "ALL", scopeType: "ALL" });
          }}
          onChange={setFilterDraft}
        />
      ) : null}
    </section>
  );
}

function StocktakeFilterPopover({
  branches,
  draft,
  onApply,
  onCancel,
  onChange,
  onClear,
}: {
  branches: StocktakeOptions["branches"];
  draft: Pick<StocktakeListQuery, "branchId" | "scopeType">;
  onApply: () => void;
  onCancel: () => void;
  onChange: (value: Pick<StocktakeListQuery, "branchId" | "scopeType">) => void;
  onClear: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-[#172317]/10 p-5">
      <div className="w-[min(560px,calc(100vw-32px))] overflow-hidden rounded-2xl border border-[#dce5e1] bg-white shadow-[0_24px_60px_rgba(24,39,28,0.18)]">
        <div className="flex min-h-16 items-center justify-between border-b border-[#eef2f0] px-6">
          <h2 className="text-base font-black text-[#172317]">Bộ lọc kiểm kê</h2>
          <button
            className="text-xs font-bold text-[#159734] hover:text-[#116f28]"
            onClick={onClear}
            type="button"
          >
            Xóa tất cả
          </button>
        </div>
        <div className="grid gap-5 p-6 sm:grid-cols-2">
          <SelectField
            label="Chi nhánh"
            onChange={(branchId) => onChange({ ...draft, branchId })}
            options={[
              { label: "Tất cả chi nhánh", value: "ALL" },
              ...branches.map((branch) => ({ label: branch.name, value: branch.id })),
            ]}
            value={draft.branchId}
          />
          <SelectField
            label="Phạm vi kiểm kê"
            onChange={(scopeType) =>
              onChange({ ...draft, scopeType: scopeType as StocktakeListQuery["scopeType"] })
            }
            options={[
              { label: "Tất cả phạm vi", value: "ALL" },
              ...Object.entries(stocktakeScopeLabels).map(([value, label]) => ({ label, value })),
            ]}
            value={draft.scopeType}
          />
        </div>
        <div className="flex items-center justify-end gap-3 border-t border-[#eef2f0] bg-white px-6 py-4">
          <button className={toolbarButton} onClick={onCancel} type="button">
            Hủy
          </button>
          <button className={primaryToolbarButton} onClick={onApply} type="button">
            Áp dụng bộ lọc
          </button>
        </div>
      </div>
    </div>
  );
}

function SortSelect({
  onChange,
  value,
}: {
  onChange: (value: StocktakeSort) => void;
  value: StocktakeSort;
}) {
  const options: Array<{ label: string; value: StocktakeSort }> = [
    { label: "Mới tạo gần đây", value: "createdAt,desc" },
    { label: "Tạo từ lâu đến mới", value: "createdAt,asc" },
    { label: "Mã kiểm kê A–Z", value: "countCode,asc" },
    { label: "Mã kiểm kê Z–A", value: "countCode,desc" },
  ];
  return (
    <Select onValueChange={(next) => onChange(next as StocktakeSort)} value={value}>
      <SelectTrigger
        aria-label="Sắp xếp danh sách kiểm kê"
        className={`${toolbarButton} min-w-[210px]`}
      >
        <ArrowUpDown size={17} aria-hidden="true" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="w-56">
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
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
  const statusGroup = stocktakeStatusFilterOptions.find((option) =>
    option.statuses.includes(status),
  );
  const style =
    statusGroup?.value === "PREPARING"
      ? "bg-slate-100 text-slate-700"
      : statusGroup?.value === "IN_PROGRESS" || statusGroup?.value === "PENDING_APPROVAL"
        ? "bg-amber-50 text-amber-700"
        : statusGroup?.value === "CANCELLED"
          ? "bg-red-50 text-red-700"
          : "bg-emerald-50 text-emerald-700";
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-black ${style}`}>
      {statusGroup?.label ?? stocktakeStatusLabels[status]}
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
    <label className="grid gap-2 text-xs font-bold text-[#536171]">
      {label}
      <select
        className="h-11 min-w-[172px] rounded-lg border border-[#dce5e1] bg-white px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
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
const toolbarButton =
  "inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8]";
const primaryToolbarButton =
  "inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d]";
const pageButton = "h-8 rounded-lg border border-[#dce5e1] px-3 disabled:opacity-40";
