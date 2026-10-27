import { Plus, RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { ActionLogPagination } from "../../action-log/components/ActionLogPagination";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
    InventoryEmptyState,
    InventoryErrorState,
    InventoryPageHeader
} from "../components/InventoryManagementComponents";
import { ShelfReplenishmentStatusBadge } from "../components/ShelfReplenishmentStatusBadge";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { shelfReplenishmentApi } from "../services/shelfReplenishment.api";
import type {
    ShelfReplenishmentStatus,
    ShelfReplenishmentSummary
} from "../types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    formatReplenishmentQuantity,
    shelfReplenishmentDetailPath
} from "../utils/shelfReplenishment.utils";

const SEARCH_DEBOUNCE_MS = 350;
const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-middle";

type StatusFilter = "ALL" | "PENDING_REVIEW" | ShelfReplenishmentStatus;

const statusTabs: Array<{ value: StatusFilter; label: string; count?: keyof ShelfReplenishmentSummary }> = [
    { value: "ALL", label: "Tất cả" },
    { value: "DRAFT", label: "Nháp", count: "draft" },
    { value: "PENDING_APPROVAL", label: "Chờ duyệt", count: "pending" },
    { value: "PENDING_REVIEW", label: "Đã xuất · chờ duyệt", count: "pendingReview" },
    { value: "POSTED", label: "Đã lên kệ", count: "posted" },
    { value: "REJECTED", label: "Bị từ chối", count: "rejected" },
    { value: "CANCELLED", label: "Đã hủy", count: "cancelled" }
];

export function ShelfReplenishmentListPage() {
    const navigate = useNavigate();
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState<StatusFilter>("ALL");
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [summary, setSummary] = useState<ShelfReplenishmentSummary | null>(null);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setKeyword(keywordInput.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timer);
    }, [keywordInput]);

    const load = useCallback(
        () =>
            shelfReplenishmentApi.list({
                page,
                size: pageSize,
                query: keyword || undefined,
                status: status === "ALL" || status === "PENDING_REVIEW" ? undefined : status,
                pendingReview: status === "PENDING_REVIEW" ? true : undefined
            }),
        [keyword, page, pageSize, status]
    );
    const resource = useInventoryPage(load);
    const rows = resource.data?.content ?? [];
    const hasFilters = keyword !== "" || status !== "ALL";

    // Số đếm theo trạng thái tải lại cùng lúc với danh sách.
    useEffect(() => {
        let active = true;
        shelfReplenishmentApi
            .summary()
            .then((result) => {
                if (active) setSummary(result);
            })
            .catch((error) => console.error("Error loading shelf replenishment summary", error));
        return () => {
            active = false;
        };
    }, [resource.data]);

    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <>
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
                        <button
                            className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#07883a] px-4 text-sm font-black text-white transition hover:opacity-90"
                            onClick={() => navigate(routePaths.shelfReplenishmentCreate)}
                            type="button"
                        >
                            <Plus aria-hidden="true" size={16} />
                            Tạo phiếu nhập kệ
                        </button>
                    </>
                }
                description="Chuyển hàng từ kho lên kệ bán. Phiếu do POS tạo khi kệ thiếu hàng cũng nằm ở đây."
                eyebrow="Kho / Nhập - Xuất"
                title="Phiếu nhập kệ"
            />

            <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
                <div className="flex flex-wrap items-center gap-2">
                    {statusTabs.map((tab) => {
                        const active = status === tab.value;
                        const count = tab.count && summary ? summary[tab.count] : null;
                        return (
                            <button
                                aria-pressed={active}
                                className={`h-[32px] rounded-full border px-3 text-[13px] font-[850] transition-all ${
                                    active
                                        ? "border-[#159734] bg-[#159734] text-white"
                                        : "border-pos-border bg-white text-[#536078] hover:border-[#159734] hover:text-[#159734]"
                                }`}
                                key={tab.value}
                                onClick={() => {
                                    setStatus(tab.value);
                                    setPage(0);
                                }}
                                type="button"
                            >
                                {tab.label}
                                {count !== null ? ` (${count})` : ""}
                            </button>
                        );
                    })}
                </div>
                <label className="relative">
                    <span className="sr-only">Tìm phiếu nhập kệ</span>
                    <Search
                        aria-hidden="true"
                        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
                        size={16}
                    />
                    <input
                        className="h-[38px] w-full rounded-lg border border-pos-border bg-white pl-9 pr-3 text-sm font-semibold text-[#172317] outline-none placeholder:font-medium placeholder:text-[#9aa5b1] focus:border-[#159734]"
                        onChange={(event) => setKeywordInput(event.target.value)}
                        placeholder="Tìm theo mã phiếu, mã đơn tham chiếu, ghi chú..."
                        type="search"
                        value={keywordInput}
                    />
                </label>
            </div>

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
                                            onClick={() => {
                                                setKeywordInput("");
                                                setStatus("ALL");
                                                setPage(0);
                                            }}
                                            type="button"
                                        >
                                            Xóa bộ lọc
                                        </button>
                                    ) : undefined
                                }
                                message={
                                    hasFilters
                                        ? "Không có phiếu nhập kệ nào khớp với bộ lọc hiện tại."
                                        : "Chưa có phiếu nhập kệ nào."
                                }
                            />
                        </div>
                    ) : (
                        <div
                            aria-busy={resource.loading}
                            className={`min-w-0 overflow-x-auto transition-opacity ${resource.loading ? "opacity-60" : ""}`}
                        >
                            <table className="w-full min-w-[900px] text-left text-sm">
                                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                                    <tr>
                                        <th className={headClass} scope="col">Mã phiếu</th>
                                        <th className={headClass} scope="col">Kho → Kệ</th>
                                        <th className={`${headClass} text-right`} scope="col">Số mặt hàng</th>
                                        <th className={`${headClass} text-right`} scope="col">Tổng số lượng</th>
                                        <th className={headClass} scope="col">Trạng thái</th>
                                        <th className={headClass} scope="col">Tạo lúc</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((row) => (
                                        <tr className="transition-colors hover:bg-[#f8fbf8]" key={row.id}>
                                            <td className={cellClass}>
                                                <Link
                                                    className="font-mono text-[13px] font-bold text-[#07883a] hover:underline"
                                                    to={shelfReplenishmentDetailPath(row.id)}
                                                >
                                                    {row.documentCode}
                                                </Link>
                                            </td>
                                            <td className={`${cellClass} font-semibold text-[#172317]`}>
                                                {row.warehouseName ?? "—"} → {row.shelfName ?? "—"}
                                            </td>
                                            <td className={`${cellClass} text-right tabular-nums text-[#172317]`}>
                                                {row.lineCount}
                                            </td>
                                            <td className={`${cellClass} text-right font-bold tabular-nums text-[#172317]`}>
                                                {formatReplenishmentQuantity(row.totalQuantity)}
                                            </td>
                                            <td className={cellClass}>
                                                <ShelfReplenishmentStatusBadge pendingReview={row.pendingReview} status={row.status} />
                                            </td>
                                            <td className={`${cellClass} whitespace-nowrap tabular-nums text-[#536078]`}>
                                                {formatReplenishmentDateTime(row.createdAt)}
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
        </section>
    );
}
