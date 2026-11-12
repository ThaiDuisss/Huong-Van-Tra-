import {
    ChevronDown,
    ChevronRight,
    RefreshCw
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

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button, IconButton } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
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
import { getMarketingStatusTone } from "../utils/event-labels";
import { routePaths } from "../../../routes/path";

const usageStatusLabels: Record<string, string> = {
    RESERVED: "Đang giữ",
    APPLIED: "Đã áp dụng",
    RELEASED: "Đã nhả",
    EXPIRED: "Hết hạn",
    CANCELLED: "Đã hủy"
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
            <ListPageHeader
                actions={
                    <>
                        {couponId ? (
                            <Link
                                to={routePaths.discountHistory}
                                className={buttonClassName({ variant: "secondary" })}
                            >
                                Xem tất cả lịch sử
                            </Link>
                        ) : null}
                        <Button disabled={loading} onClick={reload}>
                            <RefreshCw size={17} aria-hidden="true" />
                            Tải lại
                        </Button>
                    </>
                }
                breadcrumb={
                    <nav className="flex items-center gap-2 text-sm" aria-label="Breadcrumb">
                        <span className="font-bold text-[#009b4d]">Marketing</span>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Lịch sử coupon
                        </span>
                    </nav>
                }
                description={
                    couponId
                        ? `Đang xem lịch sử sử dụng của coupon ${
                              couponCode || couponId
                          }.`
                        : "Theo dõi các lượt giữ, áp dụng, nhả và hủy coupon."
                }
                title="Lịch sử sử dụng coupon"
            />

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <section className={styles.filtersCard}>
                <SearchInput
                    value={query}
                    placeholder="Tìm mã coupon, đơn hàng, khách hàng, kênh..."
                    onChange={(value) => {
                        setQuery(value);
                        setPage(1);
                    }}
                />
            </section>

            <section className={styles.tableCard}>
                <div className={styles.tableScroll}>
                    <Table className={styles.table}>
                        <colgroup>
                            <col className={styles.usageExpandColumn} />
                            <col className={styles.usageCouponColumn} />
                            <col className={styles.usageOrderColumn} />
                            <col className={styles.usageAmountColumn} />
                            <col className={styles.usageStatusColumn} />
                            <col className={styles.usageTimeColumn} />
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableHeaderCell aria-label="Xem chi tiết" />
                                <TableHeaderCell>Mã coupon</TableHeaderCell>
                                <TableHeaderCell>Đơn hàng</TableHeaderCell>
                                <TableHeaderCell>Số tiền giảm</TableHeaderCell>
                                <TableHeaderCell>Trạng thái</TableHeaderCell>
                                <TableHeaderCell>Thời gian</TableHeaderCell>
                            </TableRow>
                        </TableHead>
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
                                        <TableRow
                                            className={`${zebraClass} ${styles.summaryRow}`}
                                            aria-expanded={expanded}
                                            onClick={() =>
                                                setExpandedUsageId(
                                                    expanded ? null : usage.id
                                                )
                                            }
                                        >
                                            <TableCell>
                                                <IconButton
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
                                                </IconButton>
                                            </TableCell>
                                            <TableCell>
                                                <strong>{formatText(usage.couponCode)}</strong>
                                            </TableCell>
                                            <TableCell>{formatText(usage.orderId)}</TableCell>
                                            <TableCell>{formatMoney(usage.discountAmount)}</TableCell>
                                            <TableCell>
                                                <StatusBadge
                                                    dot
                                                    tone={getMarketingStatusTone(usage.status)}
                                                >
                                                    {usageStatusLabels[usage.status] ??
                                                        usage.status.replaceAll("_", " ")}
                                                </StatusBadge>
                                            </TableCell>
                                            <TableCell>{formatDate(getUsageEventTime(usage))}</TableCell>
                                        </TableRow>
                                        {expanded ? (
                                            <TableRow className={styles.expandedDetailRow}>
                                                <TableCell colSpan={6}>
                                                    <UsageDetail usage={usage} />
                                                </TableCell>
                                            </TableRow>
                                        ) : null}
                                    </Fragment>
                                );
                            })}
                        </tbody>
                    </Table>
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
