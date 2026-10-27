import {
    BadgePercent,
    CalendarDays,
    ChevronDown,
    ChevronLeft,
    ChevronsDown,
    ChevronsUp,
    Edit3,
    ListChecks,
    Plus,
    Target,
    TicketPercent,
    Trash2,
    X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { eventApi } from "../services/event.api";
import { promotionApi } from "../services/promotion.api";
import type { EventResponse } from "../types/event.types";
import type {
    PromotionBenefitRequest,
    PromotionBenefitResponse,
    PromotionConditionRequest,
    PromotionConditionResponse,
    PromotionResponse,
    PromotionTargetRequest,
    PromotionTargetResponse
} from "../types/promotion.types";
import { formatDiscountDateTimeCompact } from "../utils/discount-programs";
import {
    getPromotionApplyMethodLabel,
    getPromotionStatusLabel,
    getPromotionTypeLabel
} from "../utils/promotion-labels";
import { routePaths } from "../../../routes/path";

type DetailModal =
    | { kind: "benefit"; mode: "create"; value?: undefined }
    | { kind: "benefit"; mode: "edit"; value: PromotionBenefitResponse }
    | { kind: "target"; mode: "create"; value?: undefined }
    | { kind: "target"; mode: "edit"; value: PromotionTargetResponse }
    | { kind: "condition"; mode: "create"; value?: undefined }
    | { kind: "condition"; mode: "edit"; value: PromotionConditionResponse };

type DetailSectionId = "information" | "event" | "benefits" | "targets" | "conditions";

const statusClassNames: Record<string, string> = {
    DRAFT: styles.statusInactive,
    SCHEDULED: styles.statusScheduled,
    ACTIVE: styles.statusActive,
    PAUSED: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    ENDED: styles.statusUsed,
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

const benefitTypeOptions = Object.keys(benefitTypeLabels);
const targetTypeOptions = Object.keys(targetTypeLabels);
const conditionTypeOptions = Object.keys(conditionTypeLabels);
const conditionOperatorOptions = Object.keys(conditionOperatorLabels);

const emptyBenefitForm: PromotionBenefitRequest = {
    benefitType: "PERCENT_DISCOUNT",
    value: "",
    maxDiscount: "",
    giftProductId: "",
    buyQuantity: undefined,
    getQuantity: undefined,
    reason: "",
    minMarginPercent: "",
    expectedMarginPercent: ""
};

const emptyTargetForm: PromotionTargetRequest = {
    targetType: "PRODUCT",
    targetId: "",
    importBatchId: "",
    excluded: false,
    overrideBenefitType: "",
    overrideValue: "",
    overrideMaxDiscount: "",
    reason: ""
};

const emptyConditionForm: PromotionConditionRequest = {
    conditionType: "MIN_ORDER_AMOUNT",
    operator: "GTE",
    conditionValue: "{\"amount\":500000}"
};

export function PromotionDetailPage() {
    const { programId } = useParams();
    const [promotion, setPromotion] =
        useState<PromotionResponse | null>(null);
    const [event, setEvent] = useState<EventResponse | null>(null);
    const [benefits, setBenefits] = useState<PromotionBenefitResponse[]>([]);
    const [targets, setTargets] = useState<PromotionTargetResponse[]>([]);
    const [conditions, setConditions] = useState<PromotionConditionResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [modal, setModal] = useState<DetailModal | null>(null);
    const [actionLoading, setActionLoading] = useState<string | null>(null);
    const [expandedSections, setExpandedSections] = useState<
        Set<DetailSectionId>
    >(() => new Set());
    const allSectionsOpen = expandedSections.size === 5;

    function toggleSection(section: DetailSectionId) {
        setExpandedSections((current) => {
            const next = new Set(current);
            if (next.has(section)) {
                next.delete(section);
            } else {
                next.add(section);
            }
            return next;
        });
    }

    const canModifyDetails = useMemo(
        () =>
            promotion?.status === "DRAFT" ||
            promotion?.status === "SCHEDULED" ||
            promotion?.status === "PAUSED",
        [promotion?.status]
    );

    const loadPromotionDetail = useCallback(async () => {
        if (!programId) {
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const detail = await promotionApi.getPromotion(programId);
            setPromotion(detail);

            const [benefitPage, targetPage, conditionPage, eventDetail] =
                await Promise.all([
                    promotionApi.getPromotionBenefits(programId),
                    promotionApi.getPromotionTargets(programId),
                    promotionApi.getPromotionConditions(programId),
                    detail.eventId
                        ? eventApi.detail(detail.eventId)
                        : Promise.resolve(null)
                ]);

            setBenefits(benefitPage.content ?? []);
            setTargets(targetPage.content ?? []);
            setConditions(conditionPage.content ?? []);
            setEvent(eventDetail);
        } catch (err) {
            console.error("Cannot load promotion detail", err);
            setError(getErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [programId]);

    useEffect(() => {
        void loadPromotionDetail();
    }, [loadPromotionDetail]);

    if (!programId) {
        return <Navigate to={routePaths.discountPrograms} replace />;
    }

    async function submitBenefit(input: PromotionBenefitRequest) {
        if (!programId || !modal || modal.kind !== "benefit") return;

        const payload = normalizeBenefitPayload(input);
        const validationError = validateBenefit(payload);

        if (validationError) {
            setError(validationError);
            return;
        }

        try {
            setActionLoading("benefit");
            setError(null);

            if (modal.mode === "edit") {
                await promotionApi.updatePromotionBenefit(
                    programId,
                    modal.value.id,
                    payload
                );
            } else {
                await promotionApi.createPromotionBenefit(programId, payload);
            }

            setModal(null);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot save promotion benefit", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
    }

    async function submitTarget(input: PromotionTargetRequest) {
        if (!programId || !modal || modal.kind !== "target") return;

        const payload = normalizeTargetPayload(input);
        const validationError = validateTarget(payload);

        if (validationError) {
            setError(validationError);
            return;
        }

        try {
            setActionLoading("target");
            setError(null);

            if (modal.mode === "edit") {
                await promotionApi.updatePromotionTarget(
                    programId,
                    modal.value.id,
                    payload
                );
            } else {
                await promotionApi.createPromotionTarget(programId, payload);
            }

            setModal(null);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot save promotion target", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
    }

    async function submitCondition(input: PromotionConditionRequest) {
        if (!programId || !modal || modal.kind !== "condition") return;

        const payload = normalizeConditionPayload(input);
        const validationError = validateCondition(payload);

        if (validationError) {
            setError(validationError);
            return;
        }

        try {
            setActionLoading("condition");
            setError(null);

            if (modal.mode === "edit") {
                await promotionApi.updatePromotionCondition(
                    programId,
                    modal.value.id,
                    payload
                );
            } else {
                await promotionApi.createPromotionCondition(programId, payload);
            }

            setModal(null);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot save promotion condition", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
    }

    async function deleteBenefit(benefit: PromotionBenefitResponse) {
        if (!programId || !window.confirm("Xóa quyền lợi khuyến mãi này?")) return;

        try {
            setActionLoading(benefit.id);
            await promotionApi.deletePromotionBenefit(programId, benefit.id);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot delete promotion benefit", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
    }

    async function deleteTarget(target: PromotionTargetResponse) {
        if (!programId || !window.confirm("Xóa phạm vi áp dụng này?")) return;

        try {
            setActionLoading(target.id);
            await promotionApi.deletePromotionTarget(programId, target.id);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot delete promotion target", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
    }

    async function deleteCondition(condition: PromotionConditionResponse) {
        if (!programId || !window.confirm("Xóa điều kiện áp dụng này?")) return;

        try {
            setActionLoading(condition.id);
            await promotionApi.deletePromotionCondition(programId, condition.id);
            await loadPromotionDetail();
        } catch (err) {
            console.error("Cannot delete promotion condition", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoading(null);
        }
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
                    <h1 className={styles.detailTitle}>{promotion.name}</h1>
                    <div className={styles.metaLine}>
                        <PromotionDetailStatusBadge status={promotion.status} />
                        <span aria-hidden="true">·</span>
                        <span className={styles.metaItem}>
                            <CalendarDays size={15} aria-hidden="true" />
                            {formatDiscountDateTimeCompact(promotion.startAt)} -{" "}
                            {formatDiscountDateTimeCompact(promotion.endAt)}
                        </span>
                    </div>
                </div>

                <div className={styles.actions}>
                    <button
                        className={`${styles.secondaryButton} ${styles.compactButton}`}
                        type="button"
                        onClick={() => {
                            setExpandedSections(
                                allSectionsOpen
                                    ? new Set()
                                    : new Set<DetailSectionId>([
                                          "information",
                                          "event",
                                          "benefits",
                                          "targets",
                                          "conditions"
                                      ])
                            );
                        }}
                    >
                        {allSectionsOpen ? (
                            <ChevronsUp size={17} aria-hidden="true" />
                        ) : (
                            <ChevronsDown size={17} aria-hidden="true" />
                        )}
                        {allSectionsOpen ? "Thu gọn tất cả" : "Hiện tất cả"}
                    </button>
                    <Link
                        to={`${routePaths.discountCodes}?promotionId=${encodeURIComponent(
                            promotion.id
                        )}&promotionName=${encodeURIComponent(promotion.name)}`}
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

            {!canModifyDetails ? (
                <div className={styles.contextBanner}>
                    <div>
                        <strong>Cấu hình đang bị khóa</strong>
                        <p>
                            Chỉ promotion ở Bản nháp, Đã lên lịch hoặc Tạm dừng
                            mới được chỉnh quyền lợi, phạm vi và điều kiện.
                        </p>
                    </div>
                </div>
            ) : null}

            <DetailAccordionSection
                title="Thông tin khuyến mãi"
                icon={<BadgePercent size={19} aria-hidden="true" />}
                open={expandedSections.has("information")}
                onToggle={() => toggleSection("information")}
            >
                <div className={styles.infoGrid}>
                    <div className={styles.infoColumn}>
                        <InfoRow label="Tên" value={promotion.name} />
                        <InfoRow
                            label="Loại"
                            value={getPromotionTypeLabel(promotion.type)}
                        />
                        <InfoRow
                            label="Cách áp dụng"
                            value={getPromotionApplyMethodLabel(
                                promotion.applyMethod
                            )}
                        />
                        <InfoRow
                            label="Trạng thái"
                            value={getPromotionStatusLabel(promotion.status)}
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
            </DetailAccordionSection>

            <DetailAccordionSection
                title="Sự kiện liên quan"
                icon={<CalendarDays size={19} aria-hidden="true" />}
                open={expandedSections.has("event")}
                onToggle={() => toggleSection("event")}
            >
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
                            value={formatMissingText(promotion.eventId)}
                        />
                    </div>
                    <div className={styles.infoColumn}>
                        <InfoRow
                            label="Trạng thái"
                            value={formatMissingText(event?.status)}
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
            </DetailAccordionSection>

            <RelatedSection
                title="Quyền lợi khuyến mãi"
                icon={<BadgePercent size={19} aria-hidden="true" />}
                emptyText="Chưa cấu hình quyền lợi."
                isEmpty={benefits.length === 0}
                open={expandedSections.has("benefits")}
                onToggle={() => toggleSection("benefits")}
                action={
                    canModifyDetails ? (
                        <button
                            className={styles.editButton}
                            type="button"
                            onClick={() =>
                                setModal({ kind: "benefit", mode: "create" })
                            }
                        >
                            <Plus size={15} aria-hidden="true" />
                            Thêm quyền lợi
                        </button>
                    ) : null
                }
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại quyền lợi</th>
                            <th>Giá trị</th>
                            <th>Giảm tối đa</th>
                            <th>Quà tặng / số lượng</th>
                            <th>Lý do</th>
                            {canModifyDetails ? <th>Thao tác</th> : null}
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
                                <td>
                                    {formatGiftSummary(benefit)}
                                </td>
                                <td>{formatText(benefit.reason)}</td>
                                {canModifyDetails ? (
                                    <ActionCell
                                        disabled={actionLoading === benefit.id}
                                        onEdit={() =>
                                            setModal({
                                                kind: "benefit",
                                                mode: "edit",
                                                value: benefit
                                            })
                                        }
                                        onDelete={() => void deleteBenefit(benefit)}
                                    />
                                ) : null}
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
                open={expandedSections.has("targets")}
                onToggle={() => toggleSection("targets")}
                action={
                    canModifyDetails ? (
                        <button
                            className={styles.editButton}
                            type="button"
                            onClick={() =>
                                setModal({ kind: "target", mode: "create" })
                            }
                        >
                            <Plus size={15} aria-hidden="true" />
                            Thêm phạm vi
                        </button>
                    ) : null
                }
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại phạm vi</th>
                            <th>ID phạm vi</th>
                            <th>Loại trừ</th>
                            <th>Cấu hình riêng</th>
                            <th>Lý do</th>
                            {canModifyDetails ? <th>Thao tác</th> : null}
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
                                <td>{formatTargetOverride(target)}</td>
                                <td>{formatText(target.reason)}</td>
                                {canModifyDetails ? (
                                    <ActionCell
                                        disabled={actionLoading === target.id}
                                        onEdit={() =>
                                            setModal({
                                                kind: "target",
                                                mode: "edit",
                                                value: target
                                            })
                                        }
                                        onDelete={() => void deleteTarget(target)}
                                    />
                                ) : null}
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
                open={expandedSections.has("conditions")}
                onToggle={() => toggleSection("conditions")}
                action={
                    canModifyDetails ? (
                        <button
                            className={styles.editButton}
                            type="button"
                            onClick={() =>
                                setModal({ kind: "condition", mode: "create" })
                            }
                        >
                            <Plus size={15} aria-hidden="true" />
                            Thêm điều kiện
                        </button>
                    ) : null
                }
            >
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại điều kiện</th>
                            <th>Toán tử</th>
                            <th>Giá trị</th>
                            {canModifyDetails ? <th>Thao tác</th> : null}
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
                                        {formatJsonPreview(
                                            condition.conditionValue
                                        )}
                                    </code>
                                </td>
                                {canModifyDetails ? (
                                    <ActionCell
                                        disabled={actionLoading === condition.id}
                                        onEdit={() =>
                                            setModal({
                                                kind: "condition",
                                                mode: "edit",
                                                value: condition
                                            })
                                        }
                                        onDelete={() =>
                                            void deleteCondition(condition)
                                        }
                                    />
                                ) : null}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </RelatedSection>

            {modal?.kind === "benefit" ? (
                <BenefitFormModal
                    mode={modal.mode}
                    value={
                        modal.mode === "edit"
                            ? benefitToForm(modal.value)
                            : emptyBenefitForm
                    }
                    submitting={actionLoading === "benefit"}
                    onClose={() => setModal(null)}
                    onSubmit={submitBenefit}
                />
            ) : null}

            {modal?.kind === "target" ? (
                <TargetFormModal
                    mode={modal.mode}
                    value={
                        modal.mode === "edit"
                            ? targetToForm(modal.value)
                            : emptyTargetForm
                    }
                    submitting={actionLoading === "target"}
                    onClose={() => setModal(null)}
                    onSubmit={submitTarget}
                />
            ) : null}

            {modal?.kind === "condition" ? (
                <ConditionFormModal
                    mode={modal.mode}
                    value={
                        modal.mode === "edit"
                            ? conditionToForm(modal.value)
                            : emptyConditionForm
                    }
                    submitting={actionLoading === "condition"}
                    onClose={() => setModal(null)}
                    onSubmit={submitCondition}
                />
            ) : null}
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

function DetailAccordionSection({
    title,
    icon,
    open,
    onToggle,
    action,
    children
}: {
    title: string;
    icon: ReactNode;
    open: boolean;
    onToggle(): void;
    action?: ReactNode;
    children: ReactNode;
}) {
    return (
        <section
            className={`${styles.detailCard} ${
                open ? styles.detailAccordionOpen : styles.detailAccordionClosed
            }`}
        >
            <button
                className={styles.detailAccordionTrigger}
                type="button"
                aria-expanded={open}
                onClick={onToggle}
            >
                <span className={styles.detailAccordionHeading}>
                    {icon}
                    <span className={styles.detailCardTitle}>{title}</span>
                </span>
                <ChevronDown
                    className={styles.detailAccordionChevron}
                    size={19}
                    aria-hidden="true"
                />
            </button>
            {open ? (
                <div className={styles.detailAccordionContent}>
                    {action ? (
                        <div className={styles.detailAccordionAction}>
                            {action}
                        </div>
                    ) : null}
                    {children}
                </div>
            ) : null}
        </section>
    );
}

function RelatedSection({
    title,
    icon,
    emptyText,
    isEmpty,
    open,
    onToggle,
    action,
    children
}: {
    title: string;
    icon: ReactNode;
    emptyText: string;
    isEmpty: boolean;
    open: boolean;
    onToggle(): void;
    action?: ReactNode;
    children: ReactNode;
}) {
    return (
        <DetailAccordionSection
            title={title}
            icon={icon}
            open={open}
            onToggle={onToggle}
            action={action}
        >
            <div className={styles.tableScroll}>
                {isEmpty ? (
                    <div className={styles.empty}>{emptyText}</div>
                ) : (
                    children
                )}
            </div>
        </DetailAccordionSection>
    );
}

function ActionCell({
    disabled,
    onEdit,
    onDelete
}: {
    disabled: boolean;
    onEdit(): void;
    onDelete(): void;
}) {
    return (
        <td>
            <div className="flex justify-end gap-2">
                <button
                    className={styles.tableIconButton}
                    type="button"
                    disabled={disabled}
                    onClick={onEdit}
                    aria-label="Chỉnh sửa"
                >
                    <Edit3 size={16} aria-hidden="true" />
                </button>
                <button
                    className={styles.tableIconButton}
                    type="button"
                    disabled={disabled}
                    onClick={onDelete}
                    aria-label="Xóa"
                >
                    <Trash2 size={16} aria-hidden="true" />
                </button>
            </div>
        </td>
    );
}

function BenefitFormModal({
    mode,
    value,
    submitting,
    onClose,
    onSubmit
}: {
    mode: "create" | "edit";
    value: PromotionBenefitRequest;
    submitting: boolean;
    onClose(): void;
    onSubmit(input: PromotionBenefitRequest): void;
}) {
    const [form, setForm] = useState(value);

    function update<K extends keyof PromotionBenefitRequest>(
        key: K,
        nextValue: PromotionBenefitRequest[K]
    ) {
        setForm((current) => ({ ...current, [key]: nextValue }));
    }

    return (
        <ConfigModal
            title={mode === "create" ? "Thêm quyền lợi" : "Chỉnh sửa quyền lợi"}
            submitting={submitting}
            onClose={onClose}
            onSubmit={() => onSubmit(form)}
        >
            <div className={styles.detailEditGrid}>
                <SelectField
                    label="Loại quyền lợi"
                    value={form.benefitType}
                    options={benefitTypeOptions}
                    labels={benefitTypeLabels}
                    onChange={(value) => update("benefitType", value)}
                />
                <InputField
                    label="Giá trị"
                    type="number"
                    value={form.value}
                    onChange={(value) => update("value", value)}
                />
                <InputField
                    label="Giảm tối đa"
                    type="number"
                    value={form.maxDiscount ?? ""}
                    onChange={(value) => update("maxDiscount", value)}
                />
                <InputField
                    label="ID sản phẩm tặng"
                    value={form.giftProductId ?? ""}
                    onChange={(value) => update("giftProductId", value)}
                />
                <InputField
                    label="Số lượng mua"
                    type="number"
                    value={form.buyQuantity ?? ""}
                    onChange={(value) =>
                        update("buyQuantity", toOptionalNumber(value))
                    }
                />
                <InputField
                    label="Số lượng tặng"
                    type="number"
                    value={form.getQuantity ?? ""}
                    onChange={(value) =>
                        update("getQuantity", toOptionalNumber(value))
                    }
                />
                <InputField
                    label="Biên lợi nhuận tối thiểu"
                    type="number"
                    value={form.minMarginPercent ?? ""}
                    onChange={(value) => update("minMarginPercent", value)}
                />
                <InputField
                    label="Biên lợi nhuận kỳ vọng"
                    type="number"
                    value={form.expectedMarginPercent ?? ""}
                    onChange={(value) => update("expectedMarginPercent", value)}
                />
                <TextAreaField
                    label="Lý do"
                    value={form.reason ?? ""}
                    onChange={(value) => update("reason", value)}
                />
            </div>
        </ConfigModal>
    );
}

function TargetFormModal({
    mode,
    value,
    submitting,
    onClose,
    onSubmit
}: {
    mode: "create" | "edit";
    value: PromotionTargetRequest;
    submitting: boolean;
    onClose(): void;
    onSubmit(input: PromotionTargetRequest): void;
}) {
    const [form, setForm] = useState(value);

    function update<K extends keyof PromotionTargetRequest>(
        key: K,
        nextValue: PromotionTargetRequest[K]
    ) {
        setForm((current) => ({ ...current, [key]: nextValue }));
    }

    return (
        <ConfigModal
            title={mode === "create" ? "Thêm phạm vi" : "Chỉnh sửa phạm vi"}
            submitting={submitting}
            onClose={onClose}
            onSubmit={() => onSubmit(form)}
        >
            <div className={styles.detailEditGrid}>
                <SelectField
                    label="Loại phạm vi"
                    value={form.targetType}
                    options={targetTypeOptions}
                    labels={targetTypeLabels}
                    onChange={(value) => update("targetType", value)}
                />
                <InputField
                    label="ID phạm vi"
                    value={form.targetId}
                    onChange={(value) => update("targetId", value)}
                />
                <InputField
                    label="ID batch import"
                    value={form.importBatchId ?? ""}
                    onChange={(value) => update("importBatchId", value)}
                />
                <label className={styles.checkboxField}>
                    <input
                        type="checkbox"
                        checked={Boolean(form.excluded)}
                        onChange={(event) =>
                            update("excluded", event.currentTarget.checked)
                        }
                    />
                    <span>Loại trừ khỏi khuyến mãi</span>
                </label>
                <SelectField
                    label="Loại quyền lợi riêng"
                    value={form.overrideBenefitType ?? ""}
                    options={["", ...benefitTypeOptions]}
                    labels={{ "": "Không áp dụng", ...benefitTypeLabels }}
                    onChange={(value) => update("overrideBenefitType", value)}
                />
                <InputField
                    label="Giá trị riêng"
                    type="number"
                    value={form.overrideValue ?? ""}
                    onChange={(value) => update("overrideValue", value)}
                />
                <InputField
                    label="Giảm tối đa riêng"
                    type="number"
                    value={form.overrideMaxDiscount ?? ""}
                    onChange={(value) => update("overrideMaxDiscount", value)}
                />
                <TextAreaField
                    label="Lý do"
                    value={form.reason ?? ""}
                    onChange={(value) => update("reason", value)}
                />
            </div>
        </ConfigModal>
    );
}

function ConditionFormModal({
    mode,
    value,
    submitting,
    onClose,
    onSubmit
}: {
    mode: "create" | "edit";
    value: PromotionConditionRequest;
    submitting: boolean;
    onClose(): void;
    onSubmit(input: PromotionConditionRequest): void;
}) {
    const [form, setForm] = useState(value);

    function update<K extends keyof PromotionConditionRequest>(
        key: K,
        nextValue: PromotionConditionRequest[K]
    ) {
        setForm((current) => ({ ...current, [key]: nextValue }));
    }

    return (
        <ConfigModal
            title={mode === "create" ? "Thêm điều kiện" : "Chỉnh sửa điều kiện"}
            submitting={submitting}
            onClose={onClose}
            onSubmit={() => onSubmit(form)}
        >
            <div className={styles.detailEditGrid}>
                <SelectField
                    label="Loại điều kiện"
                    value={form.conditionType}
                    options={conditionTypeOptions}
                    labels={conditionTypeLabels}
                    onChange={(value) => update("conditionType", value)}
                />
                <SelectField
                    label="Toán tử"
                    value={form.operator}
                    options={conditionOperatorOptions}
                    labels={conditionOperatorLabels}
                    onChange={(value) => update("operator", value)}
                />
                <TextAreaField
                    label="Giá trị JSON"
                    value={form.conditionValue}
                    rows={6}
                    onChange={(value) => update("conditionValue", value)}
                />
            </div>
        </ConfigModal>
    );
}

function ConfigModal({
    title,
    submitting,
    onClose,
    onSubmit,
    children
}: {
    title: string;
    submitting: boolean;
    onClose(): void;
    onSubmit(): void;
    children: ReactNode;
}) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
            <section className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
                <header className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-bold text-[#17213a]">
                            {title}
                        </h2>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg p-2 hover:bg-[#f2f4f7]"
                        aria-label="Đóng form"
                    >
                        <X size={20} aria-hidden="true" />
                    </button>
                </header>

                <form
                    className={styles.editFormSections}
                    onSubmit={(event) => {
                        event.preventDefault();
                        onSubmit();
                    }}
                >
                    <section className={styles.editFormSection}>
                        {children}
                    </section>

                    <footer className={styles.eventPanelFooter}>
                        <button
                            className={styles.secondaryButton}
                            type="button"
                            disabled={submitting}
                            onClick={onClose}
                        >
                            Hủy
                        </button>
                        <button
                            className={styles.primaryButton}
                            type="submit"
                            disabled={submitting}
                        >
                            {submitting ? "Đang lưu..." : "Lưu"}
                        </button>
                    </footer>
                </form>
            </section>
        </div>
    );
}

function InputField({
    label,
    value,
    type = "text",
    onChange
}: {
    label: string;
    value: string | number;
    type?: string;
    onChange(value: string): void;
}) {
    return (
        <label className={styles.detailField}>
            <span>{label}</span>
            <input
                type={type}
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
            />
        </label>
    );
}

function SelectField({
    label,
    value,
    options,
    labels,
    onChange
}: {
    label: string;
    value: string;
    options: string[];
    labels: Record<string, string>;
    onChange(value: string): void;
}) {
    return (
        <label className={styles.detailField}>
            <span>{label}</span>
            <select
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
            >
                {options.map((option) => (
                    <option key={option || "empty"} value={option}>
                        {labels[option] ?? option}
                    </option>
                ))}
            </select>
        </label>
    );
}

function TextAreaField({
    label,
    value,
    rows = 3,
    onChange
}: {
    label: string;
    value: string;
    rows?: number;
    onChange(value: string): void;
}) {
    return (
        <label className={`${styles.detailField} ${styles.detailFieldFull}`}>
            <span>{label}</span>
            <textarea
                rows={rows}
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
            />
        </label>
    );
}

function normalizeBenefitPayload(
    input: PromotionBenefitRequest
): PromotionBenefitRequest {
    return {
        benefitType: input.benefitType,
        value: numberString(input.value),
        maxDiscount: optionalNumberString(input.maxDiscount),
        giftProductId: optionalText(input.giftProductId),
        buyQuantity: input.buyQuantity,
        getQuantity: input.getQuantity,
        reason: optionalText(input.reason),
        minMarginPercent: optionalNumberString(input.minMarginPercent),
        expectedMarginPercent: optionalNumberString(input.expectedMarginPercent)
    };
}

function normalizeTargetPayload(
    input: PromotionTargetRequest
): PromotionTargetRequest {
    return {
        targetType: input.targetType,
        targetId: input.targetId.trim(),
        importBatchId: optionalText(input.importBatchId),
        excluded: Boolean(input.excluded),
        overrideBenefitType: optionalText(input.overrideBenefitType),
        overrideValue: optionalNumberString(input.overrideValue),
        overrideMaxDiscount: optionalNumberString(input.overrideMaxDiscount),
        reason: optionalText(input.reason)
    };
}

function normalizeConditionPayload(
    input: PromotionConditionRequest
): PromotionConditionRequest {
    return {
        conditionType: input.conditionType,
        operator: input.operator,
        conditionValue: input.conditionValue.trim()
    };
}

function validateBenefit(input: PromotionBenefitRequest) {
    if (!input.benefitType) return "Vui lòng chọn loại quyền lợi.";
    if (input.value === "" || Number(input.value) < 0) {
        return "Giá trị quyền lợi phải lớn hơn hoặc bằng 0.";
    }
    if (
        input.benefitType === "PERCENT_DISCOUNT" &&
        Number(input.value) > 100
    ) {
        return "Giảm theo phần trăm không được vượt quá 100.";
    }
    if (
        (input.benefitType === "FREE_PRODUCT" ||
            input.benefitType === "BUY_X_GET_Y") &&
        !input.giftProductId
    ) {
        return "Vui lòng nhập ID sản phẩm tặng.";
    }
    return null;
}

function validateTarget(input: PromotionTargetRequest) {
    if (!input.targetType) return "Vui lòng chọn loại phạm vi.";
    if (!input.targetId.trim()) return "Vui lòng nhập ID phạm vi.";
    if (
        input.overrideBenefitType &&
        (input.overrideValue === undefined || input.overrideValue === "")
    ) {
        return "Vui lòng nhập giá trị quyền lợi riêng.";
    }
    return null;
}

function validateCondition(input: PromotionConditionRequest) {
    if (!input.conditionType) return "Vui lòng chọn loại điều kiện.";
    if (!input.operator) return "Vui lòng chọn toán tử.";
    if (!input.conditionValue.trim()) return "Vui lòng nhập giá trị JSON.";

    try {
        JSON.parse(input.conditionValue);
    } catch {
        return "Giá trị điều kiện phải là JSON hợp lệ.";
    }

    return null;
}

function benefitToForm(
    benefit: PromotionBenefitResponse
): PromotionBenefitRequest {
    return {
        benefitType: benefit.benefitType,
        value: benefit.value ?? "",
        maxDiscount: benefit.maxDiscount ?? "",
        giftProductId: benefit.giftProductId ?? "",
        buyQuantity: benefit.buyQuantity,
        getQuantity: benefit.getQuantity,
        reason: benefit.reason ?? "",
        minMarginPercent: benefit.minMarginPercent ?? "",
        expectedMarginPercent: benefit.expectedMarginPercent ?? ""
    };
}

function targetToForm(target: PromotionTargetResponse): PromotionTargetRequest {
    return {
        targetType: target.targetType,
        targetId: target.targetId,
        importBatchId: target.importBatchId ?? "",
        excluded: Boolean(target.excluded),
        overrideBenefitType: target.overrideBenefitType ?? "",
        overrideValue: target.overrideValue ?? "",
        overrideMaxDiscount: target.overrideMaxDiscount ?? "",
        reason: target.reason ?? ""
    };
}

function conditionToForm(
    condition: PromotionConditionResponse
): PromotionConditionRequest {
    return {
        conditionType: condition.conditionType,
        operator: condition.operator,
        conditionValue: condition.conditionValue
    };
}

function numberString(value: number | string | undefined) {
    return value === undefined || value === "" ? "0" : value;
}

function optionalNumberString(value: number | string | undefined) {
    return value === undefined || value === "" ? undefined : value;
}

function optionalText(value?: string) {
    const text = value?.trim();
    return text ? text : undefined;
}

function toOptionalNumber(value: string) {
    return value === "" ? undefined : Number(value);
}

function formatEnum(value?: string, labels: Record<string, string> = {}) {
    if (!value) return "-";
    return labels[value] ?? value;
}

function formatValue(value?: number | string | null) {
    if (value === null || value === undefined || value === "") return "-";
    return String(value);
}

function formatText(value?: string | number | null) {
    const text = String(value ?? "").trim();
    return text ? text : "-";
}

function formatGiftSummary(benefit: PromotionBenefitResponse) {
    const parts = [
        benefit.giftProductId ? `SP: ${benefit.giftProductId}` : "",
        benefit.buyQuantity ? `Mua ${benefit.buyQuantity}` : "",
        benefit.getQuantity ? `Tặng ${benefit.getQuantity}` : ""
    ].filter(Boolean);

    return parts.length > 0 ? parts.join(" · ") : "-";
}

function formatTargetOverride(target: PromotionTargetResponse) {
    if (!target.overrideBenefitType) return "-";

    return `${formatEnum(
        target.overrideBenefitType,
        benefitTypeLabels
    )}: ${formatValue(target.overrideValue)}`;
}

function formatJsonPreview(value: string) {
    try {
        return JSON.stringify(JSON.parse(value));
    } catch {
        return value;
    }
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

function formatMissingText(value?: string | null) {
    return value?.trim() || "Không có";
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    if (typeof error === "object" && error) {
        const maybeMessage =
            "message" in error ? (error as { message?: unknown }).message : null;

        if (typeof maybeMessage === "string" && maybeMessage.trim()) {
            return maybeMessage;
        }
    }

    return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}
