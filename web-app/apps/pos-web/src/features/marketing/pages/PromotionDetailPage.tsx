import {
    BadgePercent,
    CalendarDays,
    ChevronLeft,
    ListChecks,
    Target,
    TicketPercent
} from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { eventApi } from "../services/event.api";
import { promotionApi } from "../services/promotion.api";
import type { EventResponse } from "../types/event.types";
import type {
    PromotionBenefitResponse,
    PromotionConditionResponse,
    PromotionResponse,
    PromotionTargetResponse
} from "../types/promotion.types";
import { formatDiscountDateTimeCompact } from "../utils/discount-programs";
import {
    getPromotionApplyMethodLabel,
    getPromotionStatusLabel,
    getPromotionTypeLabel
} from "../utils/promotion-labels";
import { routePaths } from "../../../routes/path";

const statusClassNames: Record<string, string> = {
    DRAFT: styles.statusInactive,
    SCHEDULED: styles.statusScheduled,
    ACTIVE: styles.statusActive,
    INACTIVE: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    CANCELLED: styles.statusCancelled
};

const benefitTypeLabels: Record<string, string> = {
    PERCENT_DISCOUNT: "Giảm theo phần trăm",
    FIXED_DISCOUNT: "Giảm số tiền cố định",
    FREE_SHIPPING: "Miễn phí vận chuyển",
    FREE_PRODUCT: "Tặng sản phẩm",
    BUY_X_GET_Y: "Mua X tặng Y"
};

const targetTypeLabels: Record<string, string> = {
    PRODUCT: "Sản phẩm",
    CATEGORY: "Danh mục",
    BRAND: "Thương hiệu"
};

const conditionTypeLabels: Record<string, string> = {
    MIN_ORDER_AMOUNT: "Giá trị đơn tối thiểu",
    MAX_ORDER_AMOUNT: "Giá trị đơn tối đa",
    MIN_ITEM_QUANTITY: "Số lượng sản phẩm tối thiểu",
    MIN_ORDER_COUNT: "Số đơn hàng tối thiểu",
    MIN_TOTAL_SPENT: "Tổng chi tiêu tối thiểu",
    MIN_LOYALTY_POINT: "Điểm tích lũy tối thiểu",
    FIRST_PURCHASE: "Mua lần đầu",
    CUSTOMER_TIER: "Hạng khách hàng",
    CUSTOMER_ID: "Khách hàng cụ thể",
    CHANNEL: "Kênh bán hàng",
    BRANCH: "Chi nhánh",
    PAYMENT_METHOD: "Phương thức thanh toán"
};

const conditionOperatorLabels: Record<string, string> = {
    EQ: "Bằng",
    NE: "Khác",
    GT: "Lớn hơn",
    GTE: "Lớn hơn hoặc bằng",
    LT: "Nhỏ hơn",
    LTE: "Nhỏ hơn hoặc bằng",
    IN: "Nằm trong danh sách",
    NOT_IN: "Không nằm trong danh sách"
};

export function PromotionDetailPage() {
    const { programId } = useParams();
    const [promotion, setPromotion] =
        useState<PromotionResponse | null>(null);
    const [event, setEvent] = useState<EventResponse | null>(null);
    const [benefits, setBenefits] = useState<PromotionBenefitResponse[]>(
        []
    );
    const [targets, setTargets] = useState<PromotionTargetResponse[]>(
        []
    );
    const [conditions, setConditions] = useState<
        PromotionConditionResponse[]
    >([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!programId) {
            return;
        }

        const promotionId = programId;

        async function loadPromotionDetail() {
            try {
                setLoading(true);
                setError(null);

                const detail = await promotionApi.getPromotion(promotionId);
                setPromotion(detail);

                const [benefitPage, targetPage, conditionPage, eventDetail] =
                    await Promise.all([
                        promotionApi.getPromotionBenefits(promotionId),
                        promotionApi.getPromotionTargets(promotionId),
                        promotionApi.getPromotionConditions(promotionId),
                        detail.eventId
                            ? eventApi.detail(detail.eventId)
                            : Promise.resolve(null)
                    ]);

                setBenefits(benefitPage.content ?? []);
                setTargets(targetPage.content ?? []);
                setConditions(conditionPage.content ?? []);
                setEvent(eventDetail);
            } catch (err) {
                console.error("Không thể tải chi tiết khuyến mãi", err);
                setError(getErrorMessage(err));
            } finally {
                setLoading(false);
            }
        }

        void loadPromotionDetail();
    }, [programId]);

    if (!programId) {
        return <Navigate to={routePaths.discountPrograms} replace />;
    }

    if (loading) {
        return (
            <section className={styles.page}>
                <div className={styles.empty}>Đang tải khuyến mãi...</div>
            </section>
        );
    }

    if (!promotion) {
        return (
            <section className={styles.page}>
                <div className={styles.empty}>
                    {error ?? "Không tìm thấy khuyến mãi."}
                </div>
                <Link
                    to={routePaths.discountPrograms}
                    className={`${styles.secondaryButton} ${styles.compactButton}`}
                >
                    <ChevronLeft size={17} aria-hidden="true" />
                    Quay lại danh sách
                </Link>
            </section>
        );
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.titleBlock}>
                    <nav
                        className="flex items-center gap-2 text-sm"
                        aria-label="Breadcrumb"
                    >
                        <span className="font-bold text-[#009b4d]">
                            Marketing
                        </span>
                        <span aria-hidden="true">›</span>
                        <Link
                            to={routePaths.discountPrograms}
                            className="font-bold text-[#009b4d]"
                        >
                            Khuyến mãi
                        </Link>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Chi tiết
                        </span>
                    </nav>
                    <h1 className={styles.detailTitle}>
                        {promotion.name}
                    </h1>
                    <div className={styles.metaLine}>
                        <PromotionDetailStatusBadge
                            status={promotion.status}
                        />
                        <span aria-hidden="true">·</span>
                        <span className={styles.metaItem}>
                            <CalendarDays
                                size={15}
                                aria-hidden="true"
                            />
                            {formatDiscountDateTimeCompact(
                                promotion.startAt
                            )}{" "}
                            -{" "}
                            {formatDiscountDateTimeCompact(
                                promotion.endAt
                            )}
                        </span>
                    </div>
                </div>

                <div className={styles.actions}>
                    <Link
                        to={`${routePaths.discountCodes}?promotionId=${encodeURIComponent(
                            promotion.id
                        )}&promotionName=${encodeURIComponent(
                            promotion.name
                        )}`}
                        className={`${styles.secondaryButton} ${styles.compactButton}`}
                    >
                        <TicketPercent size={17} aria-hidden="true" />
                        Xem coupon
                    </Link>
                    <Link
                        to={routePaths.discountPrograms}
                        className={`${styles.secondaryButton} ${styles.compactButton}`}
                    >
                        <ChevronLeft size={17} aria-hidden="true" />
                        Quay lại danh sách
                    </Link>
                </div>
            </header>

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <section className={styles.detailCard}>
                <div className={styles.detailCardHeader}>
                    <h2 className={styles.detailCardTitle}>
                        Thông tin khuyến mãi
                    </h2>
                    <BadgePercent size={19} aria-hidden="true" />
                </div>
                <div className={styles.infoGrid}>
                    <div className={styles.infoColumn}>
                        <InfoRow label="Tên" value={promotion.name} />
                        <InfoRow
                            label="Loại"
                            value={getPromotionTypeLabel(
                                promotion.type
                            )}
                        />
                        <InfoRow
                            label="Cách áp dụng"
                            value={getPromotionApplyMethodLabel(
                                promotion.applyMethod
                            )}
                        />
                        <InfoRow
                            label="Trạng thái"
                            value={getPromotionStatusLabel(
                                promotion.status
                            )}
                        />
                    </div>
                    <div className={styles.infoColumn}>
                        <InfoRow
                            label="Bắt đầu"
                            value={formatDiscountDateTimeCompact(
                                promotion.startAt
                            )}
                        />
                        <InfoRow
                            label="Kết thúc"
                            value={formatDiscountDateTimeCompact(
                                promotion.endAt
                            )}
                        />
                        <InfoRow
                            label="Dùng chung"
                            value={promotion.stackable ? "Có" : "Không"}
                        />
                        <InfoRow
                            label="Độ ưu tiên"
                            value={String(promotion.priority ?? 0)}
                        />
                    </div>
                </div>
                <div className={styles.detailFooter}>
                    <span>{promotion.description || "Chưa có mô tả"}</span>
                    {promotion.objective ? (
                        <span>Mục tiêu: {promotion.objective}</span>
                    ) : null}
                </div>
            </section>

            <section className={styles.detailCard}>
                <div className={styles.detailCardHeader}>
                    <h2 className={styles.detailCardTitle}>
                        Sự kiện liên quan
                    </h2>
                    <CalendarDays size={19} aria-hidden="true" />
                </div>
                <div className={styles.infoGrid}>
                    <div className={styles.infoColumn}>
                        <InfoRow
                            label="Sự kiện"
                            value={formatPromotionEventName(
                                event?.name,
                                promotion.eventName,
                                promotion.eventId
                            )}
                        />
                        <InfoRow
                            label="ID sự kiện"
                            value={formatMissingEventText(promotion.eventId)}
                        />
                    </div>
                    <div className={styles.infoColumn}>
                        <InfoRow
                            label="Trạng thái"
                            value={formatMissingEventText(event?.status)}
                        />
                        <InfoRow
                            label="Thời gian sự kiện"
                            value={
                                event
                                    ? `${formatDiscountDateTimeCompact(
                                          event.startAt
                                      )} - ${formatDiscountDateTimeCompact(
                                          event.endAt
                                      )}`
                                    : "Không có"
                            }
                        />
                    </div>
                </div>
            </section>

            <RelatedSection
                title="Quyền lợi khuyến mãi"
                icon={<BadgePercent size={19} aria-hidden="true" />}
                emptyText="Chưa cấu hình quyền lợi."
                isEmpty={benefits.length === 0}
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại quyền lợi</th>
                            <th>Giá trị</th>
                            <th>Giảm tối đa</th>
                            <th>Sản phẩm tặng</th>
                            <th>Lý do</th>
                        </tr>
                    </thead>
                    <tbody>
                        {benefits.map((benefit) => (
                            <tr key={benefit.id}>
                                <td>
                                    {formatEnum(
                                        benefit.benefitType,
                                        benefitTypeLabels
                                    )}
                                </td>
                                <td>{formatValue(benefit.value)}</td>
                                <td>{formatValue(benefit.maxDiscount)}</td>
                                <td>{formatText(benefit.giftProductId)}</td>
                                <td>{formatText(benefit.reason)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </RelatedSection>

            <RelatedSection
                title="Phạm vi áp dụng"
                icon={<Target size={19} aria-hidden="true" />}
                emptyText="Chưa cấu hình phạm vi áp dụng."
                isEmpty={targets.length === 0}
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại phạm vi</th>
                            <th>ID phạm vi</th>
                            <th>Loại trừ</th>
                            <th>Cấu hình riêng</th>
                            <th>Lý do</th>
                        </tr>
                    </thead>
                    <tbody>
                        {targets.map((target) => (
                            <tr key={target.id}>
                                <td>
                                    {formatEnum(
                                        target.targetType,
                                        targetTypeLabels
                                    )}
                                </td>
                                <td>{formatText(target.targetId)}</td>
                                <td>{target.excluded ? "Có" : "Không"}</td>
                                <td>
                                    {target.overrideBenefitType
                                        ? `${formatEnum(
                                              target.overrideBenefitType,
                                              benefitTypeLabels
                                          )}: ${formatValue(
                                              target.overrideValue
                                          )}`
                                        : "-"}
                                </td>
                                <td>{formatText(target.reason)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </RelatedSection>

            <RelatedSection
                title="Điều kiện áp dụng"
                icon={<ListChecks size={19} aria-hidden="true" />}
                emptyText="Chưa cấu hình điều kiện."
                isEmpty={conditions.length === 0}
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại điều kiện</th>
                            <th>Toán tử</th>
                            <th>Giá trị</th>
                        </tr>
                    </thead>
                    <tbody>
                        {conditions.map((condition) => (
                            <tr key={condition.id}>
                                <td>
                                    {formatEnum(
                                        condition.conditionType,
                                        conditionTypeLabels
                                    )}
                                </td>
                                <td>
                                    {formatEnum(
                                        condition.operator,
                                        conditionOperatorLabels
                                    )}
                                </td>
                                <td>
                                    <code>
                                        {formatText(
                                            condition.conditionValue
                                        )}
                                    </code>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </RelatedSection>
        </section>
    );
}

function PromotionDetailStatusBadge({ status }: { status: string }) {
    return (
        <span
            className={`${styles.statusBadge} ${
                statusClassNames[status] ?? styles.statusInactive
            }`}
        >
            {getPromotionStatusLabel(status)}
        </span>
    );
}

function InfoRow({
    label,
    value
}: {
    label: string;
    value?: string | number | null;
}) {
    return (
        <div className={styles.infoRow}>
            <span className={styles.infoLabel}>{label}</span>
            <span className={styles.infoValue}>{formatText(value)}</span>
        </div>
    );
}

function RelatedSection({
    title,
    icon,
    emptyText,
    isEmpty,
    children
}: {
    title: string;
    icon: ReactNode;
    emptyText: string;
    isEmpty: boolean;
    children: ReactNode;
}) {
    return (
        <section className={styles.detailCard}>
            <div className={styles.detailCardHeader}>
                <h2 className={styles.detailCardTitle}>{title}</h2>
                {icon}
            </div>
            <div className={styles.tableScroll}>
                {isEmpty ? (
                    <div className={styles.empty}>{emptyText}</div>
                ) : (
                    children
                )}
            </div>
        </section>
    );
}

function formatEnum(value?: string, labels: Record<string, string> = {}) {
    if (!value) {
        return "-";
    }

    if (labels[value]) {
        return labels[value];
    }

    return value
        .split("_")
        .map(
            (part) =>
                part.charAt(0).toUpperCase() +
                part.slice(1).toLowerCase()
        )
        .join(" ");
}

function formatValue(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }

    return String(value);
}

function formatText(value?: string | number | null) {
    const text = String(value ?? "").trim();

    return text ? text : "-";
}

function formatPromotionEventName(
    eventName?: string | null,
    promotionEventName?: string | null,
    eventId?: string | null
) {
    return (
        eventName?.trim() ||
        promotionEventName?.trim() ||
        eventId?.trim() ||
        "Không có"
    );
}

function formatMissingEventText(value?: string | null) {
    return value?.trim() || "Không có";
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    return "Không thể tải chi tiết khuyến mãi.";
}
