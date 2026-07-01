import {
    CalendarDays,
    Clock3,
    Pencil,
    Percent,
    RotateCcw,
    ShieldCheck,
    Tag,
    Timer
} from "lucide-react";
import type { ReactNode } from "react";

import { DiscountStatusBadge } from "./DiscountProgramBadges";
import styles from "./DiscountProgramPage.module.css";
import type {
    DiscountCondition,
    DiscountProgram,
    DiscountScope
} from "../../types/discount-program.types";
import {
    formatDiscountDateTimeCompact
} from "../../utils/discount-programs";

interface DiscountProgramInfoCardProps {
    program: DiscountProgram;
}

export function DiscountProgramInfoCard({
    program
}: DiscountProgramInfoCardProps) {
    return (
        <section className={styles.detailCard}>
            <div className={styles.detailCardHeader}>
                <h2 className={styles.detailCardTitle}>
                    Thông tin chương trình
                </h2>
            </div>

            <div className={styles.infoGrid}>
                <div className={styles.infoColumn}>
                    <InfoRow
                        label="Tên chương trình"
                        value={program.name}
                    />
                    <InfoRow
                        label="Sự kiện"
                        value={program.eventName}
                        blue
                    />
                    <InfoRow
                        label="Mô tả"
                        value={program.description}
                    />
                    <InfoRow label="Mục tiêu" value={program.goal} />
                    <InfoRow
                        label="Cách áp dụng"
                        value={`Mã giảm giá (${program.applicationType})`}
                    />
                </div>

                <div className={styles.infoColumn}>
                    <InfoRow
                        label="Loại khuyến mãi"
                        value={program.promotionType}
                    />
                    <InfoRow
                        label="Trạng thái"
                        value={
                            <span className={styles.metaLine}>
                                <DiscountStatusBadge
                                    status={program.status}
                                />
                                Đã cấu hình, chờ đến thời gian bắt đầu
                            </span>
                        }
                    />
                    <InfoRow
                        label="Thời gian"
                        value={`${formatDiscountDateTimeCompact(
                            program.startAt
                        )} - ${formatDiscountDateTimeCompact(
                            program.endAt
                        )}`}
                    />
                    <InfoRow
                        label="Dùng chung khuyến mãi khác"
                        value={program.canStackPromotions ? "Có" : "Không"}
                    />
                    <InfoRow
                        label="Mức ưu tiên"
                        value={String(program.priority)}
                    />
                </div>
            </div>

            <footer className={styles.detailFooter}>
                <span className={styles.metaItem}>
                    <CalendarDays size={15} aria-hidden="true" />
                    Tạo lúc{" "}
                    {formatDiscountDateTimeCompact(program.createdAt)}
                </span>
                <span aria-hidden="true">·</span>
                <span className={styles.metaItem}>
                    <RotateCcw size={15} aria-hidden="true" />
                    Cập nhật{" "}
                    {formatDiscountDateTimeCompact(program.updatedAt)}
                </span>
                <span aria-hidden="true">·</span>
                <span className={styles.metaItem}>
                    <Clock3 size={15} aria-hidden="true" />
                    Version {program.version}
                </span>
            </footer>
        </section>
    );
}

export function DiscountBenefitCard({
    program
}: DiscountProgramInfoCardProps) {
    const benefit = program.benefit;

    return (
        <section className={styles.detailCard}>
            <EditableHeader title="Quyền lợi khuyến mãi" />
            <div className={styles.benefitGrid}>
                <span className={styles.benefitIcon}>
                    <Percent size={27} aria-hidden="true" />
                </span>
                <div className={styles.benefitColumn}>
                    <InfoRow
                        label="Loại quyền lợi"
                        value={benefit.benefitType}
                    />
                    <InfoRow label="Giá trị giảm" value={benefit.value} />
                    <InfoRow
                        label="Giảm tối đa"
                        value={benefit.maxDiscount}
                    />
                    <InfoRow
                        label="Biên lợi nhuận tối thiểu"
                        value={benefit.minProfitMargin}
                    />
                    <InfoRow
                        label="Biên lợi nhuận kỳ vọng"
                        value={benefit.expectedProfitMargin}
                    />
                </div>
                <div className={styles.benefitColumn}>
                    <InfoRow
                        label="Giá trị đề xuất"
                        value={benefit.suggestedValue}
                    />
                    <InfoRow
                        label="Độ tin cậy"
                        value={benefit.confidence}
                    />
                    <InfoRow
                        label="AI gợi ý"
                        value={benefit.aiSuggestion}
                    />
                    <InfoRow label="Lý do" value={benefit.reason} />
                </div>
            </div>
        </section>
    );
}

interface DiscountConditionsCardProps {
    conditions: DiscountCondition[];
}

export function DiscountConditionsCard({
    conditions
}: DiscountConditionsCardProps) {
    return (
        <section className={styles.detailCard}>
            <EditableHeader title="Điều kiện áp dụng" />
            <div className={styles.tableScroll}>
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Điều kiện</th>
                            <th>Toán tử</th>
                            <th>Giá trị</th>
                        </tr>
                    </thead>
                    <tbody>
                        {conditions.map((condition) => (
                            <tr key={condition.id}>
                                <td>
                                    <span
                                        className={styles.conditionName}
                                    >
                                        <span
                                            className={
                                                styles.conditionIcon
                                            }
                                        >
                                            <ShieldCheck
                                                size={12}
                                                aria-hidden="true"
                                            />
                                        </span>
                                        {condition.name}
                                    </span>
                                </td>
                                <td>{condition.operator}</td>
                                <td>{condition.value}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
            <p className="mt-3 text-sm font-semibold text-[#98a2b3]">
                Có {conditions.length} điều kiện áp dụng
            </p>
        </section>
    );
}

interface DiscountScopesCardProps {
    scopes: DiscountScope[];
}

export function DiscountScopesCard({ scopes }: DiscountScopesCardProps) {
    return (
        <section className={styles.detailCard}>
            <EditableHeader title="Phạm vi áp dụng" />
            <div className={styles.tableScroll}>
                <table className={styles.smallTable}>
                    <thead>
                        <tr>
                            <th>Loại đối tượng</th>
                            <th>Đối tượng áp dụng</th>
                            <th>Loại trừ</th>
                            <th>Override quyền lợi</th>
                            <th>Giá trị override</th>
                            <th>Lý do</th>
                        </tr>
                    </thead>
                    <tbody>
                        {scopes.map((scope) => (
                            <tr key={scope.id}>
                                <td>
                                    <ScopeObject scope={scope} />
                                </td>
                                <td>{scope.target}</td>
                                <td>
                                    <span
                                        className={
                                            scope.excluded
                                                ? styles.yesBadge
                                                : styles.noBadge
                                        }
                                    >
                                        {scope.excluded ? "Có" : "Không"}
                                    </span>
                                </td>
                                <td>
                                    {scope.overrideBenefit === "-" ? (
                                        "-"
                                    ) : (
                                        <span
                                            className={
                                                styles.overrideBadge
                                            }
                                        >
                                            {scope.overrideBenefit}
                                        </span>
                                    )}
                                </td>
                                <td>{scope.overrideValue}</td>
                                <td>{scope.reason}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}

function EditableHeader({ title }: { title: string }) {
    return (
        <div className={styles.detailCardHeader}>
            <h2 className={styles.detailCardTitle}>{title}</h2>
            <button className={styles.editButton} type="button">
                <Pencil size={14} aria-hidden="true" />
                Sửa
            </button>
        </div>
    );
}

function InfoRow({
    label,
    value,
    blue = false
}: {
    label: string;
    value: ReactNode;
    blue?: boolean;
}) {
    return (
        <div className={styles.infoRow}>
            <span className={styles.infoLabel}>{label}</span>
            <span
                className={`${styles.infoValue} ${
                    blue ? styles.blueText : ""
                }`}
            >
                {value}
            </span>
        </div>
    );
}

function ScopeObject({ scope }: { scope: DiscountScope }) {
    const isProduct = scope.objectType === "PRODUCT";

    return (
        <span className={styles.scopeObject}>
            <span
                className={`${styles.scopeIcon} ${
                    isProduct ? styles.scopeIconProduct : ""
                }`}
            >
                {isProduct ? (
                    <Tag size={12} aria-hidden="true" />
                ) : (
                    <Timer size={12} aria-hidden="true" />
                )}
            </span>
            {scope.objectType}
        </span>
    );
}
