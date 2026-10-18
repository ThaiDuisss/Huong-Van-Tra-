import { ChevronLeft, ChevronRight } from "lucide-react";

const pageSizeOptions = [20, 50, 100] as const;
const buttonClass =
  "inline-flex size-9 items-center justify-center rounded-lg border border-pos-border bg-white text-[#536078] transition hover:border-[#159734] hover:text-[#159734] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-pos-border disabled:hover:text-[#536078]";

export function ActionLogPagination({
  onPageChange,
  onPageSizeChange,
  page,
  pageSize,
  totalElements,
  totalPages,
}: {
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
  /** Bắt đầu từ 0, theo backend. */
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}) {
  const safeTotalPages = Math.max(1, totalPages);
  const first = totalElements === 0 ? 0 : page * pageSize + 1;
  const last = Math.min(totalElements, (page + 1) * pageSize);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-pos-border px-4 py-3 text-sm">
      <p className="font-semibold text-[#697684]">
        {first.toLocaleString("vi-VN")}–{last.toLocaleString("vi-VN")} trong{" "}
        <strong className="text-[#172317]">{totalElements.toLocaleString("vi-VN")}</strong> bản ghi
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 font-semibold text-[#697684]">
          Hiển thị
          <select
            className="h-9 rounded-lg border border-pos-border bg-white px-2 font-semibold text-[#172317] outline-none focus:border-[#159734]"
            onChange={(event) => onPageSizeChange(Number(event.currentTarget.value))}
            value={pageSize}
          >
            {pageSizeOptions.map((option) => (
              <option key={option} value={option}>
                {option} dòng
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-center gap-2">
          <button
            aria-label="Trang trước"
            className={buttonClass}
            disabled={page <= 0}
            onClick={() => onPageChange(page - 1)}
            type="button"
          >
            <ChevronLeft size={16} aria-hidden="true" />
          </button>
          <span className="min-w-[84px] text-center font-bold tabular-nums text-[#172317]">
            Trang {page + 1} / {safeTotalPages}
          </span>
          <button
            aria-label="Trang sau"
            className={buttonClass}
            disabled={page + 1 >= safeTotalPages}
            onClick={() => onPageChange(page + 1)}
            type="button"
          >
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  );
}
