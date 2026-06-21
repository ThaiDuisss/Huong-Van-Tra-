interface PaginationProps {
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
    totalItemsLabel?: string;
}

const pageSizeOptions = [5, 10, 20] as const;

export function Pagination({
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange,
    totalItemsLabel = "Tổng số BOM"
}: PaginationProps) {
    const safePageCount = Math.max(1, pageCount);

    return (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-pos-border px-4 py-3 text-sm">
            <p className="text-pos-muted">
                {totalItemsLabel}{" "}
                <strong className="text-pos-ink">
                    {totalItems}
                </strong>
            </p>
            <div className="flex min-w-0 flex-wrap items-center gap-3">
                <label className="flex items-center gap-2 text-pos-muted">
                    Hiển thị
                    <select
                        className="h-9 rounded-lg border border-pos-border bg-white px-2 text-pos-ink"
                        value={pageSize}
                        onChange={(event) =>
                            onPageSizeChange(
                                Number(event.currentTarget.value)
                            )
                        }
                    >
                        {pageSizeOptions.map((option) => (
                            <option key={option} value={option}>
                                {option}
                            </option>
                        ))}
                    </select>
                </label>
                <div className="flex max-w-full items-center gap-1 overflow-x-auto pb-1">
                    {Array.from(
                        { length: safePageCount },
                        (_, index) => index + 1
                    ).map((pageNumber) => (
                        <button
                            key={pageNumber}
                            type="button"
                            onClick={() =>
                                onPageChange(pageNumber)
                            }
                            className={`grid size-8 place-items-center rounded-lg text-sm font-bold ${
                                pageNumber === page
                                    ? "bg-hvt-green-900 text-white"
                                    : "text-pos-muted hover:bg-neutral-100"
                            }`}
                        >
                            {pageNumber}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}
