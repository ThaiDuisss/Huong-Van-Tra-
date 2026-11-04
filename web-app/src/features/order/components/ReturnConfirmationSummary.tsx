import { Info } from "lucide-react";
import type { OrderDetailResponse, OrderItemResponse } from "../types/order.types";
import type { OrderReturnEligibilityResponse } from "../types/order-return.types";
import {
    formatReturnQuantity,
    getReturnConditionLabel,
    getReturnReasonLabel
} from "../utils/order-return.utils";
import { getCustomerName, getOrderCode } from "../utils/order-formatters";
import {
    getReturnItemKey as getItemKey,
    type ReturnDraftItem
} from "../utils/return-quantity.validation";
import { ReturnEligibilityStatus } from "./ReturnEligibilityStatus";
import { ReturnSelectedSummary } from "./ReturnSelectedSummary";
import styles from "../pages/CreateReturnPage.module.css";

interface ReturnConfirmationSummaryProps {
    order: OrderDetailResponse;
    items: OrderItemResponse[];
    draftItems: Record<string, ReturnDraftItem>;
    refundRequired: boolean;
    returnNote: string;
    eligibility: OrderReturnEligibilityResponse | null;
    isCheckingEligibility: boolean;
    eligibilityError: string | null;
    onRetryEligibility: () => void;
    onOpenPolicyModal?: () => void;
}

function getItemName(item: OrderItemResponse) {
    return item.productNameSnapshot?.trim() || "Sản phẩm";
}

function getItemVariant(item: OrderItemResponse) {
    return item.variantNameSnapshot?.trim() || "Không có phân loại";
}

export function ReturnConfirmationSummary({
    order,
    items,
    draftItems,
    refundRequired,
    returnNote,
    eligibility,
    isCheckingEligibility,
    eligibilityError,
    onRetryEligibility
}: ReturnConfirmationSummaryProps) {
    // Only items selected at Step 2
    const selectedEntries = items
        .map((item, index) => ({
            index,
            item,
            itemKey: getItemKey(item, index)
        }))
        .filter(({ itemKey }) => draftItems[itemKey]?.selected);

    // Calculate total quantity
    const totalQuantity = selectedEntries.reduce((sum, { itemKey }) => {
        const qty = Number(draftItems[itemKey]?.quantity);
        return sum + (Number.isSafeInteger(qty) && qty > 0 ? qty : 0);
    }, 0);

    return (
        <div className={styles.confirmQuickWrap}>
            {/* 1. Thông tin đơn hàng */}
            <div className={styles.confirmOrderBar}>
                <div className={styles.confirmOrderField}>
                    <span className={styles.confirmLabel}>Mã đơn hàng</span>
                    <strong className={styles.confirmOrderCode}>{getOrderCode(order)}</strong>
                </div>
                <div className={styles.confirmOrderField}>
                    <span className={styles.confirmLabel}>Khách hàng</span>
                    <span className={styles.confirmCustomerText}>
                        {getCustomerName(order)}
                        {order.customerPhone ? ` · ${order.customerPhone}` : ""}
                    </span>
                </div>
                <div className={styles.confirmOrderField}>
                    <span className={styles.confirmLabel}>Yêu cầu hoàn tiền</span>
                    <span
                        className={`${styles.refundBadge} ${
                            refundRequired ? styles.refundBadgeYes : styles.refundBadgeNo
                        }`}
                    >
                        {refundRequired ? "Có hoàn tiền" : "Không hoàn tiền"}
                    </span>
                </div>
                {returnNote.trim() ? (
                    <div className={`${styles.confirmOrderField} ${styles.fullWidthField}`}>
                        <span className={styles.confirmLabel}>Ghi chú phiếu</span>
                        <span className={styles.confirmNoteText}>{returnNote.trim()}</span>
                    </div>
                ) : null}
            </div>

            {/* 2. Danh sách sản phẩm hoàn */}
            <div className={styles.confirmTableContainer}>
                <table className={styles.confirmTable}>
                    <thead>
                        <tr>
                            <th>Sản phẩm</th>
                            <th style={{ width: "90px", textAlign: "center" }}>SL hoàn</th>
                            <th style={{ width: "190px" }}>Lý do hoàn</th>
                            <th style={{ width: "150px" }}>Tình trạng</th>
                            <th>Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>
                        {selectedEntries.map(({ item, itemKey }) => {
                            const draft = draftItems[itemKey];

                            return (
                                <tr key={itemKey}>
                                    <td>
                                        <div className={styles.confirmItemBlock}>
                                            <strong className={styles.confirmItemTitle}>
                                                {getItemName(item)}
                                            </strong>
                                            <div className={styles.confirmItemMeta}>
                                                {item.skuSnapshot ? (
                                                    <span className={styles.skuTag}>
                                                        {item.skuSnapshot}
                                                    </span>
                                                ) : null}
                                                <span className={styles.variantText}>
                                                    {getItemVariant(item)}
                                                </span>
                                            </div>
                                        </div>
                                    </td>
                                    <td style={{ textAlign: "center" }}>
                                        <span className={styles.confirmQuantityBadge}>
                                            {formatReturnQuantity(draft.quantity)}
                                        </span>
                                    </td>
                                    <td className={styles.confirmCellText}>
                                        {getReturnReasonLabel(draft.reason || null)}
                                    </td>
                                    <td className={styles.confirmCellText}>
                                        {getReturnConditionLabel(draft.condition)}
                                    </td>
                                    <td className={styles.confirmCellNote}>
                                        {draft.note.trim() ? draft.note.trim() : "—"}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {/* 3. Một dòng tổng kết */}
            <ReturnSelectedSummary
                selectedCount={selectedEntries.length}
                totalQuantity={totalQuantity}
                inline
            />

            {/* 4. Một dòng trạng thái eligibility tổng hợp (không tạo card riêng nếu PASS) */}
            <ReturnEligibilityStatus
                response={eligibility}
                checking={isCheckingEligibility}
                error={eligibilityError}
                items={items}
                ready={true}
                compact={true}
                onRetry={onRetryEligibility}
            />

            {/* 5. Một dòng lưu ý ngắn về tiếp nhận/QC/hoàn tiền */}
            <div className={styles.confirmSubtleNote}>
                <Info size={14} className={styles.confirmSubtleIcon} aria-hidden="true" />
                <span>
                    Lưu ý: Hàng hoàn sẽ được kiểm định chất lượng (QC) trước khi xử lý nhập kho và hoàn tiền (nếu có).
                </span>
            </div>
        </div>
    );
}
