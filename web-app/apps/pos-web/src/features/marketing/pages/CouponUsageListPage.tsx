import {
    ChevronDown,
    ChevronRight,
    ReceiptText,
    RefreshCw,
    Search
} from "lucide-react";
import {
    Fragment,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type ReactNode
} from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import {
    MarketingTableSkeleton,
    MarketingToast,
    type MarketingNotice
} from "../components/MarketingFeedback";
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
    const [expandedUsageId, setExpandedUsageId] = useState<string | null>(null);
    const [notice, setNotice] = useState<MarketingNotice | null>(null);
    const notifyAfterLoadRef = useRef(false);

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
            if (notifyAfterLoadRef.current) {
                setNotice({
                    type: "success",
                    message: "Tải lại lịch sử giảm giá thành công."
                });
            }
        } catch (err) {
            console.error("Không thể tải lịch sử sử dụng coupon", err);
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
        } finally {
            setLoading(false);
            notifyAfterLoadRef.current = false;
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
        notifyAfterLoadRef.current = true;
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
                        <colgroup>
                            <col className={styles.usageExpandColumn} />
                            <col className={styles.usageCouponColumn} />
                            <col className={styles.usageOrderColumn} />
                            <col className={styles.usageAmountColumn} />
                            <col className={styles.usageStatusColumn} />
                            <col className={styles.usageTimeColumn} />
                        </colgroup>
                        <thead>
                            <tr>
                                <th aria-label="Xem chi tiết" />
                                <th>Mã coupon</th>
                                <th>Đơn hàng</th>
                                <th>Số tiền giảm</th>
                                <th>Trạng thái</th>
                                <th>Thời gian</th>
                            </tr>
                        </thead>
                        <tbody>
                            {loading ? (
                                <MarketingTableSkeleton columns={6} />
                            ) : usages.map((usage, index) => {
                                const expanded = expandedUsageId === usage.id;
                                const zebraClass =
                                    index % 2 === 0
                                        ? styles.rowOdd
                                        : styles.rowEven;
                                return (
                                    <Fragment key={usage.id}>
                                        <tr
                                            className={`${zebraClass} ${styles.summaryRow}`}
                                            aria-expanded={expanded}
                                            onClick={() =>
                                                setExpandedUsageId(
                                                    expanded ? null : usage.id
                                                )
                                            }
                                        >
                                            <td>
                                                <button
                                                    type="button"
                                                    className={styles.tableIconButton}
                                                    aria-label={
                                                        expanded
                                                            ? "Thu gọn chi tiết"
                                                            : "Xem chi tiết"
                                                    }
                                                >
                                                    {expanded ? (
                                                        <ChevronDown size={18} />
                                                    ) : (
                                                        <ChevronRight size={18} />
                                                    )}
                                                </button>
                                            </td>
                                            <td>
                                                <strong>{formatText(usage.couponCode)}</strong>
                                            </td>
                                            <td>{formatText(usage.orderId)}</td>
                                            <td>{formatMoney(usage.discountAmount)}</td>
                                            <td>
                                                <StatusBadge value={usage.status} />
                                            </td>
                                            <td>{formatDate(getUsageEventTime(usage))}</td>
                                        </tr>
                                        {expanded ? (
                                            <tr className={styles.expandedDetailRow}>
                                                <td colSpan={6}>
                                                    <UsageDetail usage={usage} />
                                                </td>
                                            </tr>
                                        ) : null}
                                    </Fragment>
                                );
                            })}
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

            <MarketingToast
                notice={notice}
                onClose={() => setNotice(null)}
            />
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

function getUsageEventTime(usage: CouponUsageResponse) {
    return usage.usedAt ?? usage.releasedAt ?? usage.reservedAt;
}

function UsageDetail({ usage }: { usage: CouponUsageResponse }) {
    return (
        <div className={styles.usageDetailGrid}>
            <DetailItem label="Khách hàng" value={usage.customerId} />
            <DetailItem label="Kênh bán" value={usage.channel} />
            <DetailItem label="Chi nhánh" value={usage.branchId} />
            <DetailItem label="Thu ngân" value={usage.cashierId} />
            <DetailItem label="Giữ lúc" value={formatDate(usage.reservedAt)} />
            <DetailItem
                label="Hết thời gian giữ"
                value={formatDate(usage.reservationExpiresAt)}
            />
            <DetailItem label="Dùng/nhả lúc" value={<UsageTimeline usage={usage} />} />
            <DetailItem label="Mã reservation" value={usage.reservationToken} />
        </div>
    );
}

function DetailItem({
    label,
    value
}: {
    label: string;
    value?: string | null | ReactNode;
}) {
    return (
        <div className={styles.usageDetailItem}>
            <span>{label}</span>
            <strong>{value || "-"}</strong>
        </div>
    );
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
