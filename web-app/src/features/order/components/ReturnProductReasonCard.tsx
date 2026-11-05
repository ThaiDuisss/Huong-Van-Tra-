import { CheckCircle2, ChevronDown, ChevronUp, AlertCircle } from "lucide-react";
import type { OrderItemResponse } from "../types/order.types";
import type { ReturnCondition, ReturnReason } from "../types/order-return.types";
import {
    RETURN_CONDITIONS,
    RETURN_CONDITION_LABELS,
    RETURN_REASONS,
    RETURN_REASON_LABELS,
    formatReturnQuantity
} from "../utils/order-return.utils";
import type { ReturnDraftItem } from "../utils/return-quantity.validation";
import styles from "../pages/CreateReturnPage.module.css";

interface ReturnProductReasonCardProps {
    item: OrderItemResponse;
    itemKey: string;
    draft: ReturnDraftItem;
    isOpen: boolean;
    onToggle: () => void;
    onUpdateDraft: (update: Partial<ReturnDraftItem>) => void;
    hasError?: boolean;
}

function getItemName(item: OrderItemResponse) {
    return item.productNameSnapshot?.trim() || "Sản phẩm";
}

function getItemVariant(item: OrderItemResponse) {
    return item.variantNameSnapshot?.trim() || "Không có phân loại";
}

export function ReturnProductReasonCard({
    item,
    itemKey,
    draft,
    isOpen,
    onToggle,
    onUpdateDraft,
    hasError = false
}: ReturnProductReasonCardProps) {
    const hasReason = Boolean(draft.reason);
    const isOtherMissingNote = draft.reason === "OTHER" && !draft.note.trim();

    return (
        <div
            className={`${styles.reasonCard} ${
                hasError || isOtherMissingNote ? styles.reasonCardError : ""
            }`}
            id={`reason-card-${itemKey}`}
        >
            {/* Header Accordion */}
            <div
                className={styles.reasonCardHeader}
                onClick={onToggle}
                role="button"
                tabIndex={0}
                aria-expanded={isOpen}
                onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onToggle();
                    }
                }}
            >
                <div className={styles.reasonCardHeaderLeft}>
                    <strong className={styles.reasonCardTitle}>{getItemName(item)}</strong>
                    <div className={styles.reasonCardMeta}>
                        {item.skuSnapshot ? (
                            <span className={styles.skuTag}>{item.skuSnapshot}</span>
                        ) : null}
                        <span className={styles.variantText}>{getItemVariant(item)}</span>
                        <span className={styles.quantityBadge}>
                            SL hoàn: <strong>{formatReturnQuantity(draft.quantity)}</strong>
                        </span>
                    </div>
                </div>

                <div className={styles.reasonCardHeaderRight}>
                    {hasReason ? (
                        <span
                            className={styles.statusBadgeComplete}
                            title={RETURN_REASON_LABELS[draft.reason as ReturnReason]}
                        >
                            <CheckCircle2 size={14} aria-hidden="true" />
                            <span className={styles.badgeLabelText}>
                                {RETURN_REASON_LABELS[draft.reason as ReturnReason]}
                            </span>
                        </span>
                    ) : (
                        <span className={styles.statusBadgeWarning}>
                            <AlertCircle size={14} aria-hidden="true" />
                            <span>Chưa chọn lý do</span>
                        </span>
                    )}

                    <span className={styles.accordionToggleIcon} aria-hidden="true">
                        {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </span>
                </div>
            </div>

            {/* Body */}
            {isOpen && (
                <div className={styles.reasonCardBody}>
                    <div className={styles.reasonGrid}>
                        <label className={styles.reasonField}>
                            <span>
                                Lý do hoàn <strong className={styles.requiredStar}>*</strong>
                            </span>
                            <select
                                value={draft.reason}
                                onChange={(e) =>
                                    onUpdateDraft({
                                        reason: e.target.value as ReturnDraftItem["reason"]
                                    })
                                }
                                aria-label={`Lý do hoàn hàng cho ${getItemName(item)}`}
                            >
                                <option value="">-- Chọn lý do trả hàng --</option>
                                {RETURN_REASONS.map((reason) => (
                                    <option key={reason} value={reason}>
                                        {RETURN_REASON_LABELS[reason]}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className={styles.reasonField}>
                            <span>Tình trạng khai báo</span>
                            <select
                                value={draft.condition}
                                onChange={(e) =>
                                    onUpdateDraft({
                                        condition: e.target.value as ReturnCondition
                                    })
                                }
                                aria-label={`Tình trạng khai báo cho ${getItemName(item)}`}
                            >
                                {RETURN_CONDITIONS.map((cond) => (
                                    <option key={cond} value={cond}>
                                        {RETURN_CONDITION_LABELS[cond]}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className={`${styles.reasonField} ${styles.fullWidthField}`}>
                            <span>
                                Ghi chú sản phẩm{" "}
                                {draft.reason === "OTHER" ? (
                                    <strong className={styles.requiredStar}>(Bắt buộc khi chọn lý do khác)</strong>
                                ) : (
                                    <small>(Tùy chọn)</small>
                                )}
                            </span>
                            <textarea
                                value={draft.note}
                                onChange={(e) => onUpdateDraft({ note: e.target.value })}
                                placeholder={
                                    draft.reason === "OTHER"
                                        ? "Nhập lý do chi tiết cho sản phẩm này..."
                                        : "Ghi chú thêm về lỗi hoặc tình trạng sản phẩm (nếu có)..."
                                }
                                aria-label={`Ghi chú sản phẩm ${getItemName(item)}`}
                            />
                        </label>
                    </div>
                </div>
            )}
        </div>
    );
}
