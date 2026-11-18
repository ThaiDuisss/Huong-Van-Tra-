import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ExternalLink, PackageOpen, Search } from "lucide-react";

import { buttonClassName } from "../../../components/ui/button-styles";
import { routePaths } from "../../../routes/path";
import { ShelfReplenishmentStatusBadge } from "../../inventory/components/ShelfReplenishmentStatusBadge";
import { shelfReplenishmentApi } from "../../inventory/services/shelfReplenishment.api";
import type {
    ShelfReplenishment,
    ShelfReplenishmentSummary
} from "../../inventory/types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    formatReplenishmentQuantity,
    shelfReplenishmentDetailPath,
    shelfReplenishmentStatusTabs,
    statusFilterToQuery,
    type ShelfReplenishmentStatusFilter
} from "../../inventory/utils/shelfReplenishment.utils";
import { ActionLogPagination } from "../../action-log/components/ActionLogPagination";

const SEARCH_DEBOUNCE_MS = 350;

function errorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Đã có lỗi xảy ra";
}

/** Màn POS: xem các phiếu nhập kệ gần đây. */
export function PosShelfReplenishmentPage() {
    const [filter, setFilter] = useState<ShelfReplenishmentStatusFilter>("ALL");
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [totals, setTotals] = useState({ elements: 0, pages: 0 });
    const [summary, setSummary] = useState<ShelfReplenishmentSummary | null>(null);
    const [slips, setSlips] = useState<ShelfReplenishment[] | null>(null);
    const [listError, setListError] = useState("");
    const [expandedId, setExpandedId] = useState<string | null>(null);
    const [detailById, setDetailById] = useState<Record<string, ShelfReplenishment>>({});

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setKeyword(keywordInput.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timer);
    }, [keywordInput]);

    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const [result, counts] = await Promise.all([
                    shelfReplenishmentApi.list({
                        page,
                        size: pageSize,
                        query: keyword || undefined,
                        ...statusFilterToQuery(filter)
                    }),
                    shelfReplenishmentApi.summary().catch(() => null)
                ]);

                if (!cancelled) {
                    setSlips(result.content ?? []);
                    setTotals({ elements: result.totalElements ?? 0, pages: result.totalPages ?? 0 });
                    setSummary(counts);
                    setListError("");
                }
            } catch (error) {
                if (!cancelled) {
                    setSlips([]);
                    setListError(`Không tải được phiếu nhập kệ: ${errorMessage(error)}`);
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [filter, keyword, page, pageSize]);

    async function toggleSlip(id: string) {
        if (expandedId === id) {
            setExpandedId(null);
            return;
        }

        setExpandedId(id);

        if (!detailById[id]) {
            try {
                const detail = await shelfReplenishmentApi.detail(id);
                setDetailById((current) => ({ ...current, [id]: detail }));
            } catch (error) {
                setListError(`Không tải được chi tiết phiếu: ${errorMessage(error)}`);
            }
        }
    }

    return (
        <section className="grid h-full min-h-0 grid-rows-[auto_minmax(0,1fr)] gap-4 overflow-hidden p-4">
            <header className="flex flex-wrap items-end justify-between gap-3">
                <div className="grid gap-1">
                    <h1 className="flex items-center gap-2 text-2xl font-black text-[#172317]">
                        <PackageOpen aria-hidden="true" className="text-[#159734]" size={22} />
                        Phiếu nhập kệ
                    </h1>
                    <p className="text-sm font-semibold text-[#697684]">
                        Theo dõi các phiếu nhập kệ; quản lý duyệt lại phiếu sau.
                    </p>
                </div>
                <a
                    className={buttonClassName({ variant: "secondary" })}
                    href={routePaths.shelfReplenishments}
                    rel="noreferrer"
                    target="_blank"
                >
                    Mở trang quản lý
                    <ExternalLink aria-hidden="true" size={14} />
                </a>
            </header>

            <div className="grid min-h-0 overflow-hidden">
                <div className="grid min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] gap-3 overflow-hidden">
                    <div className="grid gap-2">
                        <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
                            <nav className="flex flex-wrap gap-2">
                                {shelfReplenishmentStatusTabs.map((tab) => {
                                    const active = filter === tab.value;
                                    const count = tab.count && summary ? summary[tab.count] : null;

                                    return (
                                        <button
                                            aria-pressed={active}
                                            className={`h-8 shrink-0 rounded-full border px-3 text-[13px] font-black transition ${
                                                active
                                                    ? "border-[#159734] bg-[#159734] text-white"
                                                    : "border-pos-border bg-white text-[#536078] hover:border-[#159734] hover:text-[#159734]"
                                            }`}
                                            key={tab.value}
                                            onClick={() => {
                                                setFilter(tab.value);
                                                setPage(0);
                                            }}
                                            type="button"
                                        >
                                            {tab.label}
                                            {count !== null && count !== undefined ? ` (${count})` : ""}
                                        </button>
                                    );
                                })}
                            </nav>
                            <label className="relative">
                                <span className="sr-only">Tìm phiếu nhập kệ</span>
                                <Search
                                    aria-hidden="true"
                                    className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#9aa5b1]"
                                    size={16}
                                />
                                <input
                                    className="h-10 w-full rounded-lg border border-pos-border bg-white pl-9 pr-3 text-sm font-semibold text-[#172317] outline-none placeholder:font-medium placeholder:text-[#9aa5b1] focus:border-[#159734]"
                                    onChange={(event) => setKeywordInput(event.target.value)}
                                    placeholder="Tìm theo mã phiếu, mã đơn tham chiếu, ghi chú..."
                                    type="search"
                                    value={keywordInput}
                                />
                            </label>
                        </div>
                        {listError ? (
                            <div className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{listError}</div>
                        ) : null}
                    </div>

                    <div className="grid min-h-0 auto-rows-min gap-3 overflow-y-auto">
                        {slips === null ? (
                            <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div>
                        ) : null}
                        {slips !== null && slips.length === 0 ? (
                            <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white p-10 text-center text-sm font-bold text-[#697684]">
                                {keyword || filter !== "ALL"
                                    ? "Không có phiếu nhập kệ nào khớp với bộ lọc."
                                    : "Chưa có phiếu nhập kệ nào."}
                            </div>
                        ) : null}
                        {(slips ?? []).map((slip) => {
                            const expanded = expandedId === slip.id;
                            const detail = detailById[slip.id];

                            return (
                                <article
                                    className="grid rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"
                                    key={slip.id}
                                >
                                    <button
                                        className="grid w-full grid-cols-[auto_1fr_auto] items-center gap-3 p-4 text-left"
                                        onClick={() => {
                                            void toggleSlip(slip.id);
                                        }}
                                        type="button"
                                    >
                                        {expanded ? (
                                            <ChevronDown aria-hidden="true" className="text-[#536078]" size={18} />
                                        ) : (
                                            <ChevronRight aria-hidden="true" className="text-[#536078]" size={18} />
                                        )}
                                        <span className="grid gap-1">
                                            <strong className="text-base font-black text-[#172317]">
                                                {slip.documentCode}
                                            </strong>
                                            <span className="text-sm font-semibold text-[#697684]">
                                                {formatReplenishmentDateTime(slip.createdAt)}
                                                {slip.shelfName ? ` · ${slip.shelfName}` : ""} ·{" "}
                                                {formatReplenishmentQuantity(slip.totalQuantity)} sp · {slip.lineCount}{" "}
                                                mặt hàng
                                            </span>
                                        </span>
                                        <ShelfReplenishmentStatusBadge
                                            pendingReview={slip.pendingReview}
                                            status={slip.status}
                                        />
                                    </button>
                                    {expanded ? (
                                        <div className="grid gap-2 border-t border-[#e8eef0] px-4 py-3">
                                            {detail ? (
                                                <ul className="grid gap-1 text-sm">
                                                    {detail.lines.map((line) => (
                                                        <li
                                                            className="flex justify-between gap-3 font-bold text-[#172317]"
                                                            key={line.id}
                                                        >
                                                            <span>{line.itemName}</span>
                                                            <span className="tabular-nums">
                                                                {formatReplenishmentQuantity(line.quantity)}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            ) : (
                                                <p className="text-sm font-semibold text-[#697684]">Đang tải...</p>
                                            )}
                                            {slip.note ? (
                                                <p className="text-xs font-semibold text-[#697684]">{slip.note}</p>
                                            ) : null}
                                            <a
                                                className={buttonClassName({ variant: "link", size: "sm", className: "-ml-3 w-fit" })}
                                                href={shelfReplenishmentDetailPath(slip.id)}
                                                rel="noreferrer"
                                                target="_blank"
                                            >
                                                Xem / duyệt phiếu
                                                <ExternalLink aria-hidden="true" size={14} />
                                            </a>
                                        </div>
                                    ) : null}
                                </article>
                            );
                        })}
                    </div>

                    {totals.elements > 0 ? (
                        <div className="rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                            <ActionLogPagination
                                onPageChange={setPage}
                                onPageSizeChange={(size) => {
                                    setPageSize(size);
                                    setPage(0);
                                }}
                                page={page}
                                pageSize={pageSize}
                                totalElements={totals.elements}
                                totalPages={totals.pages}
                            />
                        </div>
                    ) : null}
                </div>
            </div>
        </section>
    );
}
