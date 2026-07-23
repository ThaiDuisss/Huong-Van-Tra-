import { ReceiptText, RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { couponApi } from "../services/coupon.api";
import type {
    CouponUsagePageResponse,
    CouponUsageResponse
} from "../types/coupon.types";
import { formatDiscountDateTimeCompact } from "../utils/discount-programs";
import { routePaths } from "../../../routes/path";

const usageStatusLabels: Record<string, string> = {
    RESERVED: "Đang giữ",
    APPLIED: "Đã áp dụng",
    RELEASED: "Đã nhả",
    EXPIRED: "Hết hạn",
    CANCELLED: "Đã hủy"
};

const statusClassNames: Record<string, string> = {
    RESERVED: styles.statusScheduled,
    APPLIED: styles.statusActive,
    RELEASED: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    CANCELLED: styles.statusCancelled
};

export function CouponUsageListPage() {
    const [searchParams] = useSearchParams();
    const couponId = searchParams.get("couponId")?.trim() || "";
    const couponCode = searchParams.get("couponCode")?.trim() || "";
    const [resource, setResource] =
        useState<CouponUsagePageResponse | null>(null);
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    const loadUsages = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const response = await couponApi.usages({
                page: page - 1,
                size: pageSize,
                couponId: couponId || undefined
            });

            setResource(response);
        } catch (err) {
            console.error("Không thể tải lịch sử sử dụng coupon", err);
            setError(getErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [couponId, page, pageSize]);

    useEffect(() => {
        void loadUsages();
    }, [loadUsages, refreshKey]);

    useEffect(() => {
        setPage(1);
    }, [couponId]);

    const usages = useMemo(() => {
        const content = resource?.content ?? [];
        const keyword = query.trim().toLowerCase();

        if (!keyword) {
            return content;
        }

        return content.filter((usage) =>
            [
                usage.couponCode,
                usage.orderId,
                usage.customerId,
                usage.status,
                usage.channel,
                usage.branchId,
                usage.cashierId
            ]
                .filter(Boolean)
                .some((value) =>
                    String(value).toLowerCase().includes(keyword)
                )
        );
    }, [query, resource?.content]);

    const totalItems = query.trim()
        ? usages.length
        : resource?.totalElements ?? usages.length;
    const pageCount = query.trim()
        ? Math.max(1, Math.ceil(usages.length / pageSize))
        : resource?.totalPages ?? 1;

    function reload() {
        setRefreshKey((current) => current + 1);
    }

    function updatePageSize(nextPageSize: number) {
        setPageSize(nextPageSize);
        setPage(1);
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.titleBlock}>
                    <nav className="flex items-center gap-2 text-sm" aria-label="Breadcrumb">
                        <span className="font-bold text-[#009b4d]">Marketing</span>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Lịch sử coupon
                        </span>
                    </nav>
                    <h1 className={styles.title}>Lịch sử sử dụng coupon</h1>
                    <p className={styles.description}>
                        {couponId
                            ? `Đang xem lịch sử sử dụng của coupon ${
                                  couponCode || couponId
                              }.`
                            : "Theo dõi các lượt giữ, áp dụng, nhả và hủy coupon."}
                    </p>
                </div>

                <div className={styles.actions}>
                    {couponId ? (
                        <Link
                            to={routePaths.discountHistory}
                            className={styles.secondaryButton}
                        >
                            Xem tất cả lịch sử
                        </Link>
                    ) : null}
                    <button
                        className={styles.secondaryButton}
                        type="button"
                        disabled={loading}
                        onClick={reload}
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Tải lại
                    </button>
                </div>
            </header>

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <section className={styles.filtersCard}>
                <label className={styles.search}>
                    <Search size={18} aria-hidden="true" />
                    <span className="sr-only">Tìm lịch sử sử dụng coupon</span>
                    <input
                        className={styles.searchInput}
                        value={query}
                        placeholder="Tìm mã coupon, đơn hàng, khách hàng, kênh..."
                        onChange={(event) => {
                            setQuery(event.currentTarget.value);
                            setPage(1);
                        }}
                    />
                </label>
            </section>

            <section className={styles.tableCard}>
                <div className={styles.tableScroll}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th>Mã coupon</th>
                                <th>Đơn hàng</th>
                                <th>Khách hàng</th>
                                <th>Số tiền giảm</th>
                                <th>Trạng thái</th>
                                <th>Thời gian giữ</th>
                                <th>Thời gian dùng/nhả</th>
                                <th>Kênh</th>
                            </tr>
                        </thead>
                        <tbody>
                            {usages.map((usage, index) => (
                                <tr
                                    key={usage.id}
                                    className={
                                        index % 2 === 0
                                            ? styles.rowOdd
                                            : styles.rowEven
                                    }
                                >
                                    <td>{formatText(usage.couponCode)}</td>
                                    <td>{formatText(usage.orderId)}</td>
                                    <td>{formatText(usage.customerId)}</td>
                                    <td>{formatMoney(usage.discountAmount)}</td>
                                    <td>
                                        <StatusBadge value={usage.status} />
                                    </td>
                                    <td>
                                        <div className={styles.timeRange}>
                                            <span className={styles.timeInline}>
                                                {formatDate(usage.reservedAt)}
                                            </span>
                                            <span className={styles.codeMeta}>
                                                Hết giữ:{" "}
                                                {formatDate(
                                                    usage.reservationExpiresAt
                                                )}
                                            </span>
                                        </div>
                                    </td>
                                    <td>
                                        <UsageTimeline usage={usage} />
                                    </td>
                                    <td>{formatText(usage.channel)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {!loading && usages.length === 0 ? (
                    <div className={styles.empty}>
                        Chưa có lịch sử sử dụng coupon phù hợp.
                    </div>
                ) : null}

                <MarketingPagination
                    page={page}
                    pageCount={pageCount}
                    totalItems={totalItems}
                    pageSize={pageSize}
                    onPageChange={setPage}
                    onPageSizeChange={updatePageSize}
                />
            </section>
        </section>
    );
}

function StatusBadge({ value }: { value: string }) {
    return (
        <span
            className={`${styles.statusBadge} ${
                statusClassNames[value] ?? styles.statusInactive
            }`}
        >
            <ReceiptText size={13} aria-hidden="true" />
            {usageStatusLabels[value] ?? value.replaceAll("_", " ")}
        </span>
    );
}

function UsageTimeline({ usage }: { usage: CouponUsageResponse }) {
    if (usage.usedAt) {
        return <span>{formatDate(usage.usedAt)}</span>;
    }

    if (usage.releasedAt) {
        return <span>Nhả lúc {formatDate(usage.releasedAt)}</span>;
    }

    return <span>-</span>;
}

function formatDate(value?: string) {
    return value ? formatDiscountDateTimeCompact(value) : "-";
}

function formatMoney(value?: number | string) {
    if (value === undefined || value === null || value === "") {
        return "-";
    }

    return Number(value).toLocaleString("vi-VN");
}

function formatText(value?: string | null) {
    return value?.trim() || "-";
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    return "Không thể tải lịch sử sử dụng coupon.";
}
