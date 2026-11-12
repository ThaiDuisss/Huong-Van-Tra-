import { History, Pencil, Plus, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import {
    DataTable,
    type DataTableColumn
} from "../../../components/shared/list/DataTable";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import { NativeSelect } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
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
import { getMarketingStatusTone } from "../utils/event-labels";
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

    const columns: DataTableColumn<CouponResponse>[] = [
        {
            key: "code",
            header: "Mã coupon",
            render: (coupon) => (
                <>
                    <strong className={styles.codeText}>{coupon.code}</strong>
                    <span className={styles.codeMeta}>
                        {formatText(coupon.issueReason)}
                    </span>
                </>
            )
        },
        {
            key: "promotion",
            header: "Khuyến mãi",
            render: (coupon) =>
                formatText(coupon.promotionName ?? coupon.snapshotPromotionName)
        },
        {
            key: "scope",
            header: "Phạm vi",
            render: (coupon) => formatEnum(coupon.scope, couponScopeLabels)
        },
        {
            key: "usage",
            header: "Sử dụng",
            render: (coupon) => (
                <>
                    {coupon.usedCount ?? 0} / {coupon.usageLimit ?? 0}
                </>
            )
        },
        {
            key: "expiresAt",
            header: "Hết hạn",
            render: (coupon) => formatDate(coupon.expiresAt)
        },
        {
            key: "status",
            header: "Trạng thái",
            render: (coupon) => (
                <StatusBadge dot tone={getMarketingStatusTone(coupon.status)}>
                    {formatEnum(coupon.status, couponStatusLabels)}
                </StatusBadge>
            )
        },
        {
            key: "actions",
            header: "Thao tác",
            render: (coupon) => (
                <div className="flex items-center gap-2">
                    <Button
                        disabled={coupon.status === "CANCELLED"}
                        onClick={() => openEditForm(coupon)}
                    >
                        <Pencil size={16} aria-hidden="true" />
                        Sửa
                    </Button>
                    <span className="w-[160px]">
                        <NativeSelect
                            className="font-bold"
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
                        </NativeSelect>
                    </span>
                    <Link
                        className={buttonClassName({ variant: "secondary" })}
                        to={`${routePaths.discountHistory}?couponId=${encodeURIComponent(
                            coupon.id
                        )}&couponCode=${encodeURIComponent(coupon.code)}`}
                    >
                        <History size={16} aria-hidden="true" />
                        Lịch sử
                    </Link>
                </div>
            )
        }
    ];

    return (
        <section className={styles.page}>
            <ListPageHeader
                actions={
                    <>
                        {promotionId ? (
                            <Link
                                to={routePaths.discountCodes}
                                className={buttonClassName({ variant: "secondary" })}
                            >
                                Xem tất cả coupon
                            </Link>
                        ) : null}
                        <Button disabled={loading} onClick={reload}>
                            <RefreshCw size={17} aria-hidden="true" />
                            Tải lại
                        </Button>
                        <Button variant="primary" onClick={openCreateForm}>
                            <Plus size={17} aria-hidden="true" />
                            Thêm mã giảm giá
                        </Button>
                    </>
                }
                breadcrumb={
                    <nav className="flex items-center gap-2 text-sm" aria-label="Breadcrumb">
                        <span className="font-bold text-[#009b4d]">Marketing</span>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">Coupon</span>
                    </nav>
                }
                description={
                    promotionId
                        ? `Đang xem coupon thuộc khuyến mãi ${
                              promotionName || promotionId
                          }.`
                        : "Quản lý các mã coupon đã phát hành trong hệ thống."
                }
                title="Coupon"
            />

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <section className={styles.filtersCard}>
                <SearchInput
                    value={query}
                    placeholder="Tìm mã coupon, khuyến mãi, khách hàng..."
                    onChange={(value) => {
                        setQuery(value);
                        setPage(1);
                    }}
                />
            </section>

            <DataTable
                caption="Danh sách coupon"
                columns={columns}
                rows={coupons}
                getRowKey={(coupon) => coupon.id}
                loading={loading}
                emptyTitle="Chưa có coupon phù hợp."
                minWidth={1080}
                pagination={
                    <MarketingPagination
                        page={page}
                        pageCount={pageCount}
                        totalItems={totalItems}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={updatePageSize}
                    />
                }
            />

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
                <ModalShell
                    className="w-full max-w-md p-6"
                    closeOnBackdrop={false}
                    closeOnEscape={false}
                    labelledBy="coupon-status-confirm-title"
                    onClose={() => setPendingStatus(null)}
                >
                    <h2
                        className="text-lg font-bold text-[#17213a]"
                        id="coupon-status-confirm-title"
                    >
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
                        <Button
                            disabled={changingStatus}
                            onClick={() => setPendingStatus(null)}
                        >
                            Hủy
                        </Button>
                        <Button
                            variant="primary"
                            disabled={changingStatus}
                            onClick={() => void confirmStatusChange()}
                        >
                            {changingStatus ? "Đang lưu..." : "Xác nhận"}
                        </Button>
                    </footer>
                </ModalShell>
            ) : null}

            <MarketingToast
                notice={notice}
                onClose={() => setNotice(null)}
            />
        </section>
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
