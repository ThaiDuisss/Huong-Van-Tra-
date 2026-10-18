import { History, Pencil, Plus, RefreshCw, Search, TicketPercent } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import {
    MarketingTableSkeleton,
    MarketingToast,
    type MarketingNotice
} from "../components/MarketingFeedback";
import { CouponFormModal } from "../components/discount-codes/CouponFormModal";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { couponApi } from "../services/coupon.api";
import type {
    CouponPageResponse,
    CouponResponse,
    CouponStatusValue
} from "../types/coupon.types";
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

/** Trạng thái có thể chuyển tới; mã đã hủy hoặc đã lưu trữ thì không đổi được nữa. */
const CHANGEABLE_STATUSES: CouponStatusValue[] = [
    "ACTIVE",
    "PAUSED",
    "EXPIRED",
    "CANCELLED",
    "ARCHIVED"
];
const LOCKED_STATUSES = new Set<CouponStatusValue>(["CANCELLED", "ARCHIVED"]);

interface PendingStatusChange {
    coupon: CouponResponse;
    status: CouponStatusValue;
}

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
    const [notice, setNotice] = useState<MarketingNotice | null>(null);
    const notifyAfterLoadRef = useRef(false);
    const [formOpen, setFormOpen] = useState(false);
    const [editingCoupon, setEditingCoupon] = useState<CouponResponse | null>(null);
    const [pendingStatus, setPendingStatus] = useState<PendingStatusChange | null>(null);
    const [changingStatus, setChangingStatus] = useState(false);

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
            if (notifyAfterLoadRef.current) {
                setNotice({
                    type: "success",
                    message: "Tải lại danh sách mã giảm giá thành công."
                });
            }
        } catch (err) {
            console.error("Không thể tải danh sách coupon", err);
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
        } finally {
            setLoading(false);
            notifyAfterLoadRef.current = false;
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
        notifyAfterLoadRef.current = true;
        setRefreshKey((current) => current + 1);
    }

    function openCreateForm() {
        setEditingCoupon(null);
        setFormOpen(true);
    }

    function openEditForm(coupon: CouponResponse) {
        setEditingCoupon(coupon);
        setFormOpen(true);
    }

    function handleSaved(coupon: CouponResponse, mode: "create" | "update") {
        setNotice({
            type: "success",
            message:
                mode === "create"
                    ? `Đã thêm mã giảm giá ${coupon.code}.`
                    : `Đã cập nhật mã giảm giá ${coupon.code}.`
        });
        setRefreshKey((current) => current + 1);
    }

    async function confirmStatusChange() {
        if (!pendingStatus) {
            return;
        }

        try {
            setChangingStatus(true);
            await couponApi.updateStatus(
                pendingStatus.coupon.id,
                pendingStatus.status
            );
            setNotice({
                type: "success",
                message: `Đã chuyển mã ${pendingStatus.coupon.code} sang trạng thái ${
                    couponStatusLabels[pendingStatus.status]
                }.`
            });
            setPendingStatus(null);
            setRefreshKey((current) => current + 1);
        } catch (err) {
            console.error("Không thể đổi trạng thái coupon", err);
            setNotice({
                type: "error",
                message:
                    err instanceof Error && err.message
                        ? err.message
                        : "Không thể đổi trạng thái mã giảm giá."
            });
        } finally {
            setChangingStatus(false);
        }
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
                    <button
                        className={styles.primaryButton}
                        type="button"
                        onClick={openCreateForm}
                    >
                        <Plus size={17} aria-hidden="true" />
                        Thêm mã giảm giá
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
                            {loading ? (
                                <MarketingTableSkeleton columns={7} />
                            ) : coupons.map((coupon, index) => (
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
                                        <button
                                            className={styles.secondaryButton}
                                            type="button"
                                            disabled={coupon.status === "CANCELLED"}
                                            onClick={() => openEditForm(coupon)}
                                        >
                                            <Pencil size={16} aria-hidden="true" />
                                            Sửa
                                        </button>
                                        <select
                                            className={styles.secondaryButton}
                                            aria-label={`Đổi trạng thái mã ${coupon.code}`}
                                            value=""
                                            disabled={LOCKED_STATUSES.has(coupon.status)}
                                            onChange={(event) => {
                                                const status = event.currentTarget
                                                    .value as CouponStatusValue;
                                                if (status) {
                                                    setPendingStatus({ coupon, status });
                                                }
                                            }}
                                        >
                                            <option value="">Đổi trạng thái</option>
                                            {CHANGEABLE_STATUSES.filter(
                                                (status) => status !== coupon.status
                                            ).map((status) => (
                                                <option key={status} value={status}>
                                                    {couponStatusLabels[status]}
                                                </option>
                                            ))}
                                        </select>
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

            {formOpen ? (
                <CouponFormModal
                    key={editingCoupon?.id ?? "create"}
                    open
                    coupon={editingCoupon}
                    defaultPromotionId={promotionId}
                    onClose={() => setFormOpen(false)}
                    onSaved={handleSaved}
                />
            ) : null}

            {pendingStatus ? (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
                    <section
                        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
                        role="alertdialog"
                        aria-modal="true"
                        aria-label="Xác nhận đổi trạng thái"
                    >
                        <h2 className="text-lg font-bold text-[#17213a]">
                            Đổi trạng thái mã giảm giá
                        </h2>
                        <p className="mt-2 text-sm font-medium text-[#475467]">
                            Chuyển mã <strong>{pendingStatus.coupon.code}</strong> từ{" "}
                            <strong>
                                {formatEnum(pendingStatus.coupon.status, couponStatusLabels)}
                            </strong>{" "}
                            sang <strong>{couponStatusLabels[pendingStatus.status]}</strong>?
                            {LOCKED_STATUSES.has(pendingStatus.status)
                                ? " Sau khi chuyển sẽ không đổi lại được."
                                : ""}
                        </p>
                        <footer className="mt-5 flex justify-end gap-3">
                            <button
                                type="button"
                                className={styles.secondaryButton}
                                disabled={changingStatus}
                                onClick={() => setPendingStatus(null)}
                            >
                                Hủy
                            </button>
                            <button
                                type="button"
                                className={styles.primaryButton}
                                disabled={changingStatus}
                                onClick={() => void confirmStatusChange()}
                            >
                                {changingStatus ? "Đang lưu..." : "Xác nhận"}
                            </button>
                        </footer>
                    </section>
                </div>
            ) : null}

            <MarketingToast
                notice={notice}
                onClose={() => setNotice(null)}
            />
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
