import { RefreshCw, Search, SlidersHorizontal } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { ActionLogPagination } from "../../action-log/components/ActionLogPagination";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
    InventoryEmptyState,
    InventoryErrorState,
    InventoryPageHeader
} from "../../inventory/components/InventoryManagementComponents";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { SellableConfigDialog } from "../components/SellableConfigDialog";
import { usePagedApiResource } from "../hooks/useApiResource";
import { productVariantApi } from "../services/productVariant.api";
import type { CatalogStatus, ProductVariant } from "../types/product.types";

const SEARCH_DEBOUNCE_MS = 350;

const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-middle";
const fieldClass =
    "h-[38px] rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none placeholder:font-medium placeholder:text-[#9aa5b1] focus:border-[#159734]";

const quantityFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

function formatQuantity(value: number | null | undefined) {
    return quantityFormatter.format(value ?? 0);
}

function SellableLevelBadge({ variant }: { variant: ProductVariant }) {
    const sellable = variant.sellableQuantity ?? 0;
    const threshold = variant.productionThreshold ?? 0;

    if (sellable <= 0) {
        return (
            <span className="inline-flex rounded-full bg-red-50 px-2.5 py-1 text-xs font-black text-red-700">
                Hết lượng bán
            </span>
        );
    }
    if (sellable <= threshold) {
        return (
            <span className="inline-flex rounded-full bg-amber-50 px-2.5 py-1 text-xs font-black text-amber-700">
                Dưới ngưỡng
            </span>
        );
    }
    return (
        <span className="inline-flex rounded-full bg-[#eaf7ec] px-2.5 py-1 text-xs font-black text-[#07883a]">
            Đủ bán
        </span>
    );
}

export function SellableConfigPage() {
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState<"ALL" | CatalogStatus>("ALL");
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [editing, setEditing] = useState<ProductVariant | null>(null);
    const [notice, setNotice] = useState("");

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setKeyword(keywordInput.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timer);
    }, [keywordInput]);

    const loadVariants = useCallback(
        () =>
            productVariantApi.list({
                page,
                size: pageSize,
                keyword: keyword || undefined,
                status: status === "ALL" ? undefined : status
            }),
        [keyword, page, pageSize, status]
    );
    const resource = usePagedApiResource(loadVariants);
    const rows = resource.data?.content ?? [];
    const hasFilters = keyword !== "" || status !== "ALL";

    const closeDialog = useCallback(() => setEditing(null), []);

    function handleSaved(variant: ProductVariant) {
        setEditing(null);
        setNotice(`Đã lưu cấu hình bán hàng cho ${variant.variantName}.`);
        resource.refresh();
    }

    function resetFilters() {
        setKeywordInput("");
        setStatus("ALL");
        setPage(0);
    }

    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <button
                        aria-label="Làm mới dữ liệu"
                        className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] transition hover:bg-[#f4faf5] disabled:opacity-60"
                        disabled={resource.loading}
                        onClick={resource.refresh}
                        type="button"
                    >
                        <RefreshCw
                            aria-hidden="true"
                            className={resource.loading ? "animate-spin" : ""}
                            size={16}
                        />
                        Làm mới
                    </button>
                }
                description="Đặt số lượng có thể bán, ngưỡng yêu cầu sản xuất và số lượng mỗi mẻ cho từng Mặt hàng."
                eyebrow="Hệ thống / Cấu hình bán hàng"
                title="Cấu hình bán hàng"
            />

            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
                <label className="relative min-w-[260px] flex-1">
                    <span className="sr-only">Tìm Mặt hàng</span>
                    <Search
                        aria-hidden="true"
                        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
                        size={16}
                    />
                    <input
                        className={`${fieldClass} w-full pl-9`}
                        onChange={(event) => setKeywordInput(event.target.value)}
                        placeholder="Tìm theo tên sản phẩm, tên mặt hàng, SKU..."
                        type="search"
                        value={keywordInput}
                    />
                </label>
                <label className="flex items-center gap-2 text-[13px] font-[900] text-[#536078]">
                    Trạng thái
                    <select
                        className={fieldClass}
                        onChange={(event) => {
                            setStatus(event.target.value as "ALL" | CatalogStatus);
                            setPage(0);
                        }}
                        value={status}
                    >
                        <option value="ALL">Tất cả</option>
                        <option value="ACTIVE">Đang hoạt động</option>
                        <option value="INACTIVE">Ngừng hoạt động</option>
                    </select>
                </label>
            </div>

            {notice ? (
                <p
                    className="rounded-xl border border-[#d8eadc] bg-[#f4faf5] px-4 py-3 text-sm font-bold text-[#07883a]"
                    role="status"
                >
                    {notice}
                </p>
            ) : null}

            {resource.error && !resource.data ? (
                <InventoryErrorState message={resource.error} onRetry={resource.refresh} />
            ) : (
                <div className="min-w-0 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                    {resource.error ? (
                        <p
                            className="border-b border-red-100 bg-red-50 px-4 py-2 text-xs font-bold text-red-700"
                            role="alert"
                        >
                            {resource.error} Đang hiển thị dữ liệu của lần tải trước.
                        </p>
                    ) : null}

                    {!resource.data ? (
                        <div className="p-4">
                            <DashboardListSkeleton rows={8} />
                        </div>
                    ) : rows.length === 0 ? (
                        <div className="p-4">
                            <InventoryEmptyState
                                action={
                                    hasFilters ? (
                                        <button
                                            className="text-sm font-black text-[#159734] hover:underline"
                                            onClick={resetFilters}
                                            type="button"
                                        >
                                            Xóa bộ lọc
                                        </button>
                                    ) : undefined
                                }
                                message={
                                    hasFilters
                                        ? "Không có Mặt hàng nào khớp với bộ lọc hiện tại."
                                        : "Chưa có Mặt hàng nào để cấu hình."
                                }
                            />
                        </div>
                    ) : (
                        <div
                            aria-busy={resource.loading}
                            className={`min-w-0 overflow-x-auto transition-opacity ${resource.loading ? "opacity-60" : ""}`}
                        >
                            <table className="w-full min-w-[980px] text-left text-sm">
                                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                                    <tr>
                                        <th className={headClass} scope="col">Mặt hàng</th>
                                        <th className={headClass} scope="col">Trạng thái</th>
                                        <th className={`${headClass} text-right`} scope="col">Có thể bán</th>
                                        <th className={`${headClass} text-right`} scope="col">Ngưỡng sản xuất</th>
                                        <th className={`${headClass} text-right`} scope="col">Mỗi mẻ sản xuất</th>
                                        <th className={headClass} scope="col">Mức bán</th>
                                        <th className={`${headClass} text-right`} scope="col">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((row) => (
                                        <tr className="transition-colors hover:bg-[#f8fbf8]" key={row.id}>
                                            <td className={cellClass}>
                                                <div className="grid">
                                                    <span className="font-bold text-[#172317]">{row.variantName}</span>
                                                    <span className="font-mono text-[11px] text-[#8793a1]">{row.sku}</span>
                                                </div>
                                            </td>
                                            <td className={cellClass}>
                                                <CatalogStatusBadge status={row.status} />
                                            </td>
                                            <td className={`${cellClass} text-right font-bold tabular-nums text-[#172317]`}>
                                                {formatQuantity(row.sellableQuantity)}
                                            </td>
                                            <td className={`${cellClass} text-right tabular-nums text-[#172317]`}>
                                                {formatQuantity(row.productionThreshold)}
                                            </td>
                                            <td className={`${cellClass} text-right tabular-nums text-[#172317]`}>
                                                {formatQuantity(row.productionBatchQuantity)}
                                            </td>
                                            <td className={cellClass}>
                                                <SellableLevelBadge variant={row} />
                                            </td>
                                            <td className={`${cellClass} text-right`}>
                                                <button
                                                    className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-lg border border-[#07883a] bg-white px-3.5 text-xs font-bold text-[#07883a] transition-all hover:bg-green-50"
                                                    onClick={() => {
                                                        setNotice("");
                                                        setEditing(row);
                                                    }}
                                                    type="button"
                                                >
                                                    <SlidersHorizontal aria-hidden="true" size={14} />
                                                    Cấu hình
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {resource.data ? (
                        <ActionLogPagination
                            onPageChange={setPage}
                            onPageSizeChange={(size) => {
                                setPageSize(size);
                                setPage(0);
                            }}
                            page={page}
                            pageSize={pageSize}
                            totalElements={resource.data.totalElements}
                            totalPages={resource.data.totalPages}
                        />
                    ) : null}
                </div>
            )}

            {editing ? (
                <SellableConfigDialog
                    key={String(editing.id)}
                    onClose={closeDialog}
                    onSaved={handleSaved}
                    variant={editing}
                />
            ) : null}
        </section>
    );
}
