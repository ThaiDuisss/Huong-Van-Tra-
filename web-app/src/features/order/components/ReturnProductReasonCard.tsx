import { CheckCircle2, ChevronDown, ChevronUp, AlertCircle } from "lucide-react";

import { Field, NativeSelect, TextArea } from "../../../components/ui/Field";
import { StatusBadge } from "../../../components/ui/StatusBadge";
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
                        <StatusBadge
                            className="max-w-[220px]"
                            shape="tag"
                            title={RETURN_REASON_LABELS[draft.reason as ReturnReason]}
                            tone="success"
                        >
                            <CheckCircle2 className="mr-1 inline align-[-2px]" size={14} aria-hidden="true" />
                            {RETURN_REASON_LABELS[draft.reason as ReturnReason]}
                        </StatusBadge>
                    ) : (
                        <StatusBadge shape="tag" tone="warning">
                            <AlertCircle className="mr-1 inline align-[-2px]" size={14} aria-hidden="true" />
                            Chưa chọn lý do
                        </StatusBadge>
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
                        <Field label="Lý do hoàn" required>
                            <NativeSelect
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
                            </NativeSelect>
                        </Field>

                        <Field label="Tình trạng khai báo">
                            <NativeSelect
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
                            </NativeSelect>
                        </Field>

                        <Field
                            label={
                                <>
                                    Ghi chú sản phẩm{" "}
                                    {draft.reason === "OTHER" ? (
                                        <strong className={styles.requiredStar}>(Bắt buộc khi chọn lý do khác)</strong>
                                    ) : (
                                        <small>(Tùy chọn)</small>
                                    )}
                                </>
                            }
                        >
                            <TextArea
                                value={draft.note}
                                onChange={(e) => onUpdateDraft({ note: e.target.value })}
                                placeholder={
                                    draft.reason === "OTHER"
                                        ? "Nhập lý do chi tiết cho sản phẩm này..."
                                        : "Ghi chú thêm về lỗi hoặc tình trạng sản phẩm (nếu có)..."
                                }
                                aria-label={`Ghi chú sản phẩm ${getItemName(item)}`}
                            />
                        </Field>
                    </div>
                </div>
            )}
        </div>
    );
}
