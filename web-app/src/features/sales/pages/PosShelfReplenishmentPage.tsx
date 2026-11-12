import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ExternalLink, PackageOpen, Search, X } from "lucide-react";

import { Button, IconButton } from "../../../components/ui/Button";
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
import { useProductSearch } from "../hooks/useProductSearch";
import type { SaleProductPreview } from "../types/sale.types";

const currentBranchId = import.meta.env.VITE_POS_BRANCH_ID ?? null;

const SEARCH_DEBOUNCE_MS = 350;

function errorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Đã có lỗi xảy ra";
}

/** Màn POS: tự đưa hàng từ kho lên kệ và xem các phiếu nhập kệ gần đây. */
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
    /** Tăng lên để tải lại danh sách (vd. sau khi vừa đưa hàng lên kệ). */
    const [reloadKey, setReloadKey] = useState(0);

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
    }, [filter, keyword, page, pageSize, reloadKey]);

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
                        Đưa hàng từ kho lên kệ bán ngay; quản lý duyệt lại phiếu sau.
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

            <div className="grid min-h-0 grid-cols-[380px_minmax(0,1fr)] gap-4 overflow-hidden">
                <ManualIssueForm
                    branchId={currentBranchId}
                    onIssued={(slip) => {
                        setDetailById((current) => ({ ...current, [slip.id]: slip }));
                        setReloadKey((key) => key + 1);
                    }}
                />

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

/** Thu ngân tự chọn mặt hàng và số lượng để đưa từ kho lên kệ ngay; quản lý duyệt lại sau. */
function ManualIssueForm({
    branchId,
    onIssued
}: {
    branchId: string | null;
    onIssued(slip: ShelfReplenishment): void;
}) {
    const [keyword, setKeyword] = useState("");
    const [selected, setSelected] = useState<SaleProductPreview | null>(null);
    const [draft, setDraft] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
    // Dùng chung cách tìm với ô tìm hàng của màn bán hàng (debounce + cache theo từ khoá).
    const search = useProductSearch(keyword, selected === null);
    const searching = keyword.trim().length > 0 && selected === null;

    const quantity = Number(draft);
    const overWarehouse = selected !== null && quantity > selected.warehouseQuantity;
    const canIssue =
        selected !== null && Number.isInteger(quantity) && quantity > 0 && !overWarehouse && !submitting;

    async function issue() {
        if (!selected || !canIssue) {
            return;
        }

        const variantId = selected.productVariantId ?? selected.id;
        setSubmitting(true);
        setMessage(null);

        try {
            const slip = await shelfReplenishmentApi.create({
                shelfId: selected.shelfLocationId ?? null,
                warehouseId: selected.warehouseLocationId ?? null,
                branchId,
                note: `POS: đưa ${selected.name} lên kệ`,
                submit: false,
                issueNow: true,
                lines: [{ productVariantId: variantId, quantity }]
            });
            setMessage({
                tone: "ok",
                text: `${slip.documentCode}: đã đưa ${quantity} ${selected.name} lên kệ, chờ Quản lý duyệt lại.`
            });
            setSelected(null);
            setKeyword("");
            setDraft("");
            onIssued(slip);
        } catch (error) {
            setMessage({ tone: "error", text: `Không đưa được hàng lên kệ: ${errorMessage(error)}` });
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid content-start gap-3 self-start rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-0.5">
                <h2 className="text-base font-black text-[#172317]">Đưa hàng từ kho lên kệ</h2>
                <p className="text-xs font-semibold text-[#697684]">
                    Tìm mặt hàng, nhập số lượng muốn đưa lên (có thể cả thùng). Hàng lên kệ ngay.
                </p>
            </div>
            {selected ? (
                <div className="grid gap-2">
                    <div className="flex items-center justify-between gap-3 rounded-lg border border-[#b9dfc8] bg-[#eaf7ec] px-3 py-2">
                        <span className="grid">
                            <strong className="text-sm font-black text-[#172317]">
                                {selected.name}
                                {selected.variant ? ` · ${selected.variant}` : ""}
                            </strong>
                            <small className="text-xs font-semibold text-[#536078]">
                                Trên kệ {selected.shelfQuantity} · Trong kho {selected.warehouseQuantity}
                            </small>
                        </span>
                        <IconButton aria-label="Chọn mặt hàng khác" onClick={() => setSelected(null)}>
                            <X aria-hidden="true" size={16} />
                        </IconButton>
                    </div>
                    <div className="flex gap-2">
                        <input
                            autoFocus
                            aria-label="Số lượng đưa lên kệ"
                            className="h-10 min-w-0 flex-1 rounded-lg border border-[#dce5e1] px-3 text-sm font-black tabular-nums outline-none focus:border-[#159734]"
                            inputMode="numeric"
                            onChange={(event) => setDraft(event.currentTarget.value.replace(/\D/g, ""))}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    void issue();
                                }
                            }}
                            placeholder="Số lượng"
                            value={draft}
                        />
                        <Button
                            disabled={!canIssue}
                            onClick={() => {
                                void issue();
                            }}
                            variant="primary"
                        >
                            {submitting ? "Đang chuyển..." : "Đưa lên kệ ngay"}
                        </Button>
                    </div>
                    {overWarehouse ? (
                        <p className="text-xs font-semibold text-amber-700">
                            Kho chỉ còn {selected.warehouseQuantity}.
                        </p>
                    ) : null}
                </div>
            ) : (
                <div className="relative">
                    <Search
                        aria-hidden="true"
                        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#697684]"
                        size={16}
                    />
                    <input
                        aria-label="Tìm mặt hàng cần đưa lên kệ"
                        className="h-10 w-full rounded-lg border border-[#dce5e1] pl-9 pr-3 text-sm font-semibold outline-none focus:border-[#159734]"
                        onChange={(event) => setKeyword(event.currentTarget.value)}
                        placeholder="Tìm theo tên, SKU hoặc quét mã vạch"
                        value={keyword}
                    />
                    {searching && (search.loading || search.error || search.products.length === 0) ? (
                        <p
                            className={`absolute z-10 mt-1 w-full rounded-lg border bg-white px-3 py-2 text-sm font-semibold shadow-pos-panel ${
                                search.error ? "border-red-200 text-red-700" : "border-[#e8eef0] text-[#697684]"
                            }`}
                            role={search.error ? "alert" : "status"}
                        >
                            {search.loading
                                ? "Đang tìm..."
                                : search.error
                                  ? `Không tìm được mặt hàng: ${errorMessage(search.error)}`
                                  : "Không có mặt hàng nào khớp."}
                        </p>
                    ) : null}
                    {searching && !search.loading && search.products.length > 0 ? (
                        <ul className="absolute z-10 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-[#e8eef0] bg-white shadow-pos-panel">
                            {search.products.slice(0, 10).map((product) => (
                                <li key={product.id}>
                                    <button
                                        className="grid w-full gap-0.5 px-3 py-2 text-left hover:bg-[#f4faf6]"
                                        onClick={() => {
                                            setSelected(product);
                                            setDraft("");
                                        }}
                                        type="button"
                                    >
                                        <strong className="text-sm font-black text-[#172317]">
                                            {product.name}
                                            {product.variant ? ` · ${product.variant}` : ""}
                                        </strong>
                                        <small className="text-xs font-semibold text-[#697684]">
                                            {product.sku} · Trên kệ {product.shelfQuantity} · Trong kho{" "}
                                            {product.warehouseQuantity}
                                        </small>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    ) : null}
                </div>
            )}
            {message ? (
                <p
                    className={`rounded-lg px-3 py-2 text-sm font-semibold ${
                        message.tone === "ok"
                            ? "border border-[#b9dfc8] bg-[#eaf7ec] text-[#07883a]"
                            : "border border-red-200 bg-red-50 text-red-700"
                    }`}
                    role={message.tone === "error" ? "alert" : "status"}
                >
                    {message.text}
                </p>
            ) : null}
        </section>
    );
}
