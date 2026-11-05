import { AlertCircle, RefreshCw, Search } from "lucide-react";
import { type ReactNode, useSyncExternalStore } from "react";

export interface DataTableColumn<T> {
    key: string;
    header: string;
    render: (row: T) => ReactNode;
    align?: "left" | "center" | "right";
    /** Class cho ô dữ liệu (vd. `font-black`). */
    className?: string;
    /** Độ rộng cột (CSS width). */
    width?: string;
    /** Ẩn khỏi thẻ trên mobile (cột đầu tiên luôn là tiêu đề thẻ). */
    hideOnMobile?: boolean;
}

interface DataTableProps<T> {
    columns: readonly DataTableColumn<T>[];
    rows: readonly T[];
    getRowKey: (row: T) => string;
    loading: boolean;
    error?: string | null;
    onRetry?: () => void;
    onRowClick?: (row: T) => void;
    /** Nút ⋮ cuối dòng (thường là `RowActionMenu`). */
    rowActions?: (row: T) => ReactNode;
    emptyTitle: string;
    emptyDescription?: string;
    caption: string;
    minWidth?: number;
    pagination?: ReactNode;
}

const ALIGN_CLASS = { left: "text-left", center: "text-center", right: "text-right" } as const;
const DESKTOP_QUERY = "(min-width: 768px)";

function subscribeDesktop(onChange: () => void) {
    const media = window.matchMedia(DESKTOP_QUERY);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
}

/**
 * Chỉ render một giao diện (bảng hoặc thẻ mobile). Nếu render cả hai rồi ẩn bằng CSS,
 * mỗi dòng sẽ có hai menu ⋮ cùng mở và menu bị ẩn đóng menu thật trước khi bấm được.
 */
function useIsDesktop() {
    return useSyncExternalStore(subscribeDesktop, () => window.matchMedia(DESKTOP_QUERY).matches, () => true);
}

export function DataTable<T>({
    columns,
    rows,
    getRowKey,
    loading,
    error,
    onRetry,
    onRowClick,
    rowActions,
    emptyTitle,
    emptyDescription,
    caption,
    minWidth = 880,
    pagination
}: DataTableProps<T>) {
    const showLoading = loading && rows.length === 0;
    const showError = !loading && Boolean(error);
    const showEmpty = !loading && !error && rows.length === 0;
    const showRows = !showLoading && !showError && !showEmpty;
    const columnCount = columns.length + (rowActions ? 1 : 0);
    const isDesktop = useIsDesktop();
    const stateBlock = showError ? (
        <TableState
            action={onRetry ? (
                <button
                    className="mt-4 inline-flex items-center gap-2 rounded-lg border border-[#dce5e1] px-3 py-2 text-xs font-black text-[#273a2b] hover:bg-[#f7fbf8]"
                    onClick={onRetry}
                    type="button"
                >
                    <RefreshCw size={14} aria-hidden="true" />
                    Thử lại
                </button>
            ) : null}
            description={error ?? undefined}
            icon={<AlertCircle size={20} aria-hidden="true" />}
            title="Không tải được dữ liệu"
            tone="danger"
        />
    ) : showEmpty ? (
        <TableState
            description={emptyDescription}
            icon={<Search size={20} aria-hidden="true" />}
            title={emptyTitle}
        />
    ) : null;

    return (
        <section className="grid min-h-[330px] flex-1 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            {isDesktop ? <div className="min-h-0 overflow-auto">
                <table
                    className="w-full border-separate border-spacing-0 text-[13px] text-[#233025]"
                    style={{ minWidth }}
                >
                    <caption className="sr-only">{caption}</caption>
                    <thead>
                        <tr>
                            {columns.map((column, index) => (
                                <th
                                    className={`sticky top-0 z-[1] h-10 whitespace-nowrap border-b border-[#e4ece8] bg-[#f8faf9] px-3 text-[11px] font-black uppercase text-[#556477] ${ALIGN_CLASS[column.align ?? "left"]} ${index === 0 ? "pl-6" : ""}`}
                                    key={column.key}
                                    scope="col"
                                    style={column.width ? { width: column.width } : undefined}
                                >
                                    {column.header}
                                </th>
                            ))}
                            {rowActions ? (
                                <th className="sticky top-0 z-[1] h-10 w-12 border-b border-[#e4ece8] bg-[#f8faf9]" scope="col">
                                    <span className="sr-only">Thao tác</span>
                                </th>
                            ) : null}
                        </tr>
                    </thead>
                    <tbody className={loading && rows.length > 0 ? "opacity-60" : ""}>
                        {showLoading ? <LoadingRows columnCount={columnCount} /> : null}
                        {stateBlock ? (
                            <tr>
                                <td colSpan={columnCount}>{stateBlock}</td>
                            </tr>
                        ) : null}
                        {showRows
                            ? rows.map((row, rowIndex) => (
                                <tr
                                    className={`group ${onRowClick ? "cursor-pointer" : ""} ${rowIndex % 2 === 1 ? "[&>td]:bg-[#f3f7f4]" : ""}`}
                                    key={getRowKey(row)}
                                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                                    onKeyDown={onRowClick ? (event) => {
                                        if (event.target !== event.currentTarget) return;
                                        if (event.key === "Enter" || event.key === " ") {
                                            event.preventDefault();
                                            onRowClick(row);
                                        }
                                    } : undefined}
                                    tabIndex={onRowClick ? 0 : undefined}
                                >
                                    {columns.map((column, index) => (
                                        <td
                                            className={`h-[52px] whitespace-nowrap border-b border-[#eef2f0] bg-white px-3 align-middle group-hover:bg-[#f7fbf8] ${ALIGN_CLASS[column.align ?? "left"]} ${index === 0 ? "pl-6" : ""} ${column.className ?? ""}`}
                                            key={column.key}
                                        >
                                            {column.render(row)}
                                        </td>
                                    ))}
                                    {rowActions ? (
                                        <td
                                            className="h-[52px] border-b border-[#eef2f0] bg-white px-2 align-middle group-hover:bg-[#f7fbf8]"
                                            onClick={(event) => event.stopPropagation()}
                                            onKeyDown={(event) => event.stopPropagation()}
                                        >
                                            {rowActions(row)}
                                        </td>
                                    ) : null}
                                </tr>
                            ))
                            : null}
                    </tbody>
                </table>
            </div> : <div className="grid content-start gap-2 overflow-auto p-2">
                {showLoading
                    ? Array.from({ length: 4 }, (_, index) => (
                        <div className="h-[112px] animate-pulse rounded-lg border border-[#e1e8e5] bg-[#f4f7f5]" key={index} />
                    ))
                    : null}
                {stateBlock}
                {showRows
                    ? rows.map((row) => (
                        <MobileCard
                            actions={rowActions?.(row)}
                            columns={columns}
                            key={getRowKey(row)}
                            onClick={onRowClick ? () => onRowClick(row) : undefined}
                            row={row}
                        />
                    ))
                    : null}
            </div>}

            {pagination}
        </section>
    );
}

function LoadingRows({ columnCount }: { columnCount: number }) {
    return (
        <>
            {Array.from({ length: 8 }, (_, rowIndex) => (
                <tr aria-hidden="true" key={rowIndex}>
                    {Array.from({ length: columnCount }, (_cell, cellIndex) => (
                        <td
                            className={`h-[52px] border-b border-[#eef2f0] px-3 ${cellIndex === 0 ? "pl-6" : ""}`}
                            key={cellIndex}
                        >
                            <span className={`block h-4 animate-pulse rounded bg-[#edf3f0] ${cellIndex === 0 ? "w-36" : "w-24"}`} />
                        </td>
                    ))}
                </tr>
            ))}
        </>
    );
}

function TableState({
    action,
    description,
    icon,
    title,
    tone = "default"
}: {
    action?: ReactNode;
    description?: string;
    icon: ReactNode;
    title: string;
    tone?: "default" | "danger";
}) {
    return (
        <div className="grid min-h-[320px] place-items-center bg-white p-8 text-center" role={tone === "danger" ? "alert" : undefined}>
            <div className="max-w-[380px]">
                <div className={`mx-auto grid size-12 place-items-center rounded-full ${tone === "danger" ? "bg-[#fdeeee] text-[#b42318]" : "bg-[#eef6f1] text-[#159734]"}`}>
                    {icon}
                </div>
                <h2 className="mt-4 text-sm font-black text-[#172317]">{title}</h2>
                {description ? (
                    <p className="mt-1.5 text-sm font-semibold text-[#697684]">{description}</p>
                ) : null}
                {action}
            </div>
        </div>
    );
}

function MobileCard<T>({
    actions,
    columns,
    onClick,
    row
}: {
    actions?: ReactNode;
    columns: readonly DataTableColumn<T>[];
    onClick?: () => void;
    row: T;
}) {
    const [titleColumn, ...restColumns] = columns;

    return (
        <article className="rounded-lg border border-[#e1e8e5] bg-white p-3">
            <div className="flex items-start gap-3">
                <button
                    className="min-w-0 flex-1 truncate bg-transparent p-0 text-left text-sm font-black text-[#172317]"
                    disabled={!onClick}
                    onClick={onClick}
                    type="button"
                >
                    {titleColumn?.render(row)}
                </button>
                {actions}
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-2 text-xs">
                {restColumns
                    .filter((column) => !column.hideOnMobile)
                    .map((column) => (
                        <div className="min-w-0 rounded-md bg-[#f4f7f5] p-2" key={column.key}>
                            <dt className="font-semibold text-[#697684]">{column.header}</dt>
                            <dd className="mt-0.5 truncate font-black text-[#172317]">{column.render(row)}</dd>
                        </div>
                    ))}
            </dl>
        </article>
    );
}
