import { History, RefreshCw, Search, TicketPercent } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { couponApi } from "../services/coupon.api";
import type { CouponPageResponse } from "../types/coupon.types";
import { formatDiscountDateTimeCompact } from "../utils/discount-programs";
import { routePaths } from "../../../routes/path";

const couponStatusLabels: Record<string, string> = {
    DRAFT: "Bản nháp",
    SCHEDULED: "Đã lên lịch",
    ACTIVE: "Đang hoạt động",
    PAUSED: "Tạm dừng",
    EXPIRED: "Hết hạn",
    CANCELLED: "Đã hủy",
    ARCHIVED: "Đã lưu trữ"
};

const couponScopeLabels: Record<string, string> = {
    PUBLIC: "Công khai",
    PERSONAL: "Cá nhân"
};

const statusClassNames: Record<string, string> = {
    DRAFT: styles.statusInactive,
    SCHEDULED: styles.statusScheduled,
    ACTIVE: styles.statusActive,
    PAUSED: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    CANCELLED: styles.statusCancelled,
    ARCHIVED: styles.statusInactive
};

export function CouponListPage() {
    const [searchParams] = useSearchParams();
    const promotionId = searchParams.get("promotionId")?.trim() || "";
    const promotionName = searchParams.get("promotionName")?.trim() || "";
    const [resource, setResource] = useState<CouponPageResponse | null>(null);
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    const loadCoupons = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const response = await couponApi.list({
                page: page - 1,
                size: pageSize,
                promotionId: promotionId || undefined
            });

            setResource(response);
        } catch (err) {
            console.error("Không thể tải danh sách coupon", err);
            setError(getErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [page, pageSize, promotionId]);

    useEffect(() => {
        void loadCoupons();
    }, [loadCoupons, refreshKey]);

    useEffect(() => {
        setPage(1);
    }, [promotionId]);

    const coupons = useMemo(() => {
        const content = resource?.content ?? [];
        const keyword = query.trim().toLowerCase();

        if (!keyword) {
            return content;
        }

        return content.filter((coupon) =>
            [
                coupon.code,
                coupon.promotionName,
                coupon.snapshotPromotionName,
                coupon.customerId,
                coupon.status,
                coupon.scope
            ]
                .filter(Boolean)
                .some((value) =>
                    String(value).toLowerCase().includes(keyword)
                )
        );
    }, [query, resource?.content]);

    const totalItems = query.trim()
        ? coupons.length
        : resource?.totalElements ?? coupons.length;
    const pageCount = query.trim()
        ? Math.max(1, Math.ceil(coupons.length / pageSize))
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
                        <span className="font-bold text-[#17213a]">Coupon</span>
                    </nav>
                    <h1 className={styles.title}>Coupon</h1>
                    <p className={styles.description}>
                        {promotionId
                            ? `Đang xem coupon thuộc khuyến mãi ${
                                  promotionName || promotionId
                              }.`
                            : "Quản lý các mã coupon đã phát hành trong hệ thống."}
                    </p>
                </div>

                <div className={styles.actions}>
                    {promotionId ? (
                        <Link
                            to={routePaths.discountCodes}
                            className={styles.secondaryButton}
                        >
                            Xem tất cả coupon
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
                    <span className="sr-only">Tìm coupon</span>
                    <input
                        className={styles.searchInput}
                        value={query}
                        placeholder="Tìm mã coupon, khuyến mãi, khách hàng..."
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
                                <th>Khuyến mãi</th>
                                <th>Phạm vi</th>
                                <th>Sử dụng</th>
                                <th>Hết hạn</th>
                                <th>Trạng thái</th>
                                <th>Thao tác</th>
                            </tr>
                        </thead>
                        <tbody>
                            {coupons.map((coupon, index) => (
                                <tr
                                    key={coupon.id}
                                    className={
                                        index % 2 === 0
                                            ? styles.rowOdd
                                            : styles.rowEven
                                    }
                                >
                                    <td>
                                        <strong className={styles.codeText}>
                                            {coupon.code}
                                        </strong>
                                        <span className={styles.codeMeta}>
                                            {formatText(coupon.issueReason)}
                                        </span>
                                    </td>
                                    <td>{formatText(coupon.promotionName ?? coupon.snapshotPromotionName)}</td>
                                    <td>{formatEnum(coupon.scope, couponScopeLabels)}</td>
                                    <td>
                                        {coupon.usedCount ?? 0} /{" "}
                                        {coupon.usageLimit ?? 0}
                                    </td>
                                    <td>{formatDate(coupon.expiresAt)}</td>
                                    <td>
                                        <StatusBadge
                                            value={coupon.status}
                                            labels={couponStatusLabels}
                                        />
                                    </td>
                                    <td className={styles.actionCell}>
                                        <Link
                                            className={styles.secondaryButton}
                                            to={`${routePaths.discountHistory}?couponId=${encodeURIComponent(
                                                coupon.id
                                            )}&couponCode=${encodeURIComponent(
                                                coupon.code
                                            )}`}
                                        >
                                            <History size={16} aria-hidden="true" />
                                            Lịch sử
                                        </Link>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {!loading && coupons.length === 0 ? (
                    <div className={styles.empty}>Chưa có coupon phù hợp.</div>
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

function StatusBadge({
    value,
    labels
}: {
    value: string;
    labels: Record<string, string>;
}) {
    return (
        <span
            className={`${styles.statusBadge} ${
                statusClassNames[value] ?? styles.statusInactive
            }`}
        >
            <TicketPercent size={13} aria-hidden="true" />
            {formatEnum(value, labels)}
        </span>
    );
}

function formatEnum(value?: string, labels: Record<string, string> = {}) {
    if (!value) return "-";

    return labels[value] ?? value.replaceAll("_", " ");
}

function formatDate(value?: string) {
    return value ? formatDiscountDateTimeCompact(value) : "-";
}

function formatText(value?: string | null) {
    return value?.trim() || "-";
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    return "Không thể tải danh sách coupon.";
}
