import { useId } from "react";
import {
    AlertCircle,
    Check,
    CheckCircle2,
    ChevronDown,
    ChevronUp,
    Info,
    Package,
    RotateCcw,
    ShieldAlert,
    Trash2
} from "lucide-react";

import {
    RETURN_CONDITIONS,
    RETURN_CONDITION_LABELS,
    getAllowedCustomerReturnQcResults,
    getReturnConditionLabel,
    getReturnReasonLabel
} from "../../order/utils/order-return.utils";
import type { ReturnCondition } from "../../order/types/order-return.types";
import type {
    CustomerReturnActualCondition,
    CustomerReturnInspectionLine,
    CustomerReturnQcDraft,
    CustomerReturnQcLocation,
    CustomerReturnQcResult
} from "../types/customer-return-inspection.types";
import { getCustomerReturnQcLocations } from "../utils/customer-return-qc";
import { formatVietnamNumber } from "../utils/inventory-format";
import styles from "./CustomerReturnWarehouseInspectionModal.module.css";

export interface CustomerReturnInspectionCardProps {
    line: CustomerReturnInspectionLine;
    draft: CustomerReturnQcDraft;
    editable: boolean;
    locations: CustomerReturnQcLocation[];
    declaredCondition?: string | null;
    reason?: string | null;
    sku?: string | null;
    productName?: string | null;
    isCollapsible?: boolean;
    isExpanded?: boolean;
    onToggleExpand?: () => void;
    attemptedSubmit?: boolean;
    touched?: {
        actualCondition?: boolean;
        result?: boolean;
        destinationLocationId?: boolean;
    };
    onFieldTouch?: (field: "actualCondition" | "result" | "destinationLocationId") => void;
    onChange: (update: Partial<CustomerReturnQcDraft>) => void;
}

const RESULT_OPTIONS: {
    key: CustomerReturnQcResult;
    title: string;
    desc: string;
    icon: typeof RotateCcw;
}[] = [
    {
        key: "RESTOCK",
        title: "Nhập lại kho",
        desc: "Hàng nguyên vẹn, nhập lại kho để tiếp tục bán",
        icon: RotateCcw
    },
    {
        key: "DAMAGED",
        title: "Hàng hỏng",
        desc: "Chuyển sang khu vực hàng lỗi / cách ly kiểm định",
        icon: ShieldAlert
    },
    {
        key: "WRITE_OFF",
        title: "Tiêu hủy",
        desc: "Loại bỏ, thanh lý khỏi tồn kho theo quy trình",
        icon: Trash2
    }
];

const CONDITION_DESCRIPTIONS: Record<ReturnCondition, string> = {
    SEALED: "Tem, hộp và niêm phong còn nguyên vẹn",
    OPENED: "Đã mở hộp/bao bì nhưng sản phẩm còn tốt",
    DAMAGED: "Móp méo, trầy xước, vỡ hoặc biến dạng",
    SPOILED: "Mốc, ôi thiu, biến chất hoặc đổi mùi",
    USED: "Đã qua sử dụng thực tế"
};

export function CustomerReturnInspectionCard({
    line,
    draft,
    editable,
    locations,
    declaredCondition,
    reason,
    sku,
    productName,
    isCollapsible = false,
    isExpanded = true,
    onToggleExpand,
    attemptedSubmit = false,
    touched = {},
    onFieldTouch,
    onChange
}: CustomerReturnInspectionCardProps) {
    const cardId = useId();
    const declared = line.declaredCondition ?? line.condition ?? declaredCondition;
    const actual = draft.actualCondition;
    const isPosted = line.posted;

    // Allowed disposition results based on actual condition
    const allowedResults = getAllowedCustomerReturnQcResults(actual || null);
    const eligibleLocations = getCustomerReturnQcLocations(draft.result, locations);

    // Validation checks
    const actualError = !actual
        ? "Vui lòng chọn tình trạng thực tế sau khi kiểm tra hàng."
        : null;

    const resultError = draft.result === "RESTOCK" && actual !== "SEALED"
        ? "Chỉ được nhập lại kho khi tình trạng thực tế còn nguyên seal."
        : draft.result === "QUARANTINE" && line.result !== "QUARANTINE"
            ? "Không tạo mới phân loại cách ly."
            : null;

    const locationError = draft.result !== "WRITE_OFF" &&
        !eligibleLocations.some((loc) => loc.id === draft.destinationLocationId)
        ? "Vui lòng chọn vị trí kho phù hợp với phân loại xử lý."
        : null;

    const hasAnyError = Boolean(actualError || resultError || locationError);
    const isCompleted = !hasAnyError;

    // Field-level error display triggers
    const showActualError = (attemptedSubmit || touched.actualCondition) && Boolean(actualError);
    const showResultError = (attemptedSubmit || touched.result || touched.actualCondition) && Boolean(resultError);
    const showLocationError = (attemptedSubmit || touched.destinationLocationId) && Boolean(locationError);

    const displayName = line.itemName || productName || "Sản phẩm";
    const displaySku = line.itemCode || sku || "--";
    const quantity = formatVietnamNumber(Number(line.quantity));

    // Handle change of actual condition
    const handleActualChange = (newCondition: CustomerReturnActualCondition) => {
        onFieldTouch?.("actualCondition");
        // If switching away from SEALED while currently selecting RESTOCK, suggest DAMAGED
        let nextResult = draft.result;
        let nextLocation = draft.destinationLocationId;
        if (newCondition !== "SEALED" && draft.result === "RESTOCK") {
            nextResult = "DAMAGED";
            nextLocation = "";
        }
        onChange({
            actualCondition: newCondition,
            result: nextResult,
            destinationLocationId: nextLocation
        });
    };

    // Handle change of disposition result
    const handleResultChange = (newResult: CustomerReturnQcResult) => {
        onFieldTouch?.("result");
        // If WRITE_OFF, clear destinationLocationId
        const nextLocation = newResult === "WRITE_OFF" ? "" : "";
        onChange({
            result: newResult,
            destinationLocationId: nextLocation
        });
    };

    return (
        <article
            className={`${styles.inspectionCard} ${showActualError || showResultError || showLocationError ? styles.cardHasError : isCompleted && editable ? styles.cardCompleted : ""}`}
            data-testid={`inspection-card-${line.id}`}
            id={`card-${line.id}`}
        >
            {/* Card Header */}
            <div
                className={`${styles.cardHeader} ${isCollapsible ? styles.cardHeaderClickable : ""}`}
                onClick={isCollapsible ? onToggleExpand : undefined}
                role={isCollapsible ? "button" : undefined}
                tabIndex={isCollapsible ? 0 : undefined}
                onKeyDown={isCollapsible ? (e) => {
                    if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onToggleExpand?.();
                    }
                } : undefined}
            >
                <div className={styles.cardHeaderInfo}>
                    <div className="flex items-center gap-2 flex-wrap">
                        <strong className={styles.cardProductName} title={displayName}>
                            {displayName}
                        </strong>
                        <span className={styles.skuBadge}>{displaySku}</span>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-[#64748b] mt-1">
                        <span>Số lượng cần kiểm: <strong>{quantity}</strong></span>
                        {declared ? (
                            <>
                                <span>·</span>
                                <span>Khai báo: <strong>{getReturnConditionLabel(declared as CustomerReturnActualCondition)}</strong></span>
                            </>
                        ) : null}
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    {/* Status badge */}
                    {isPosted ? (
                        <span className={`${styles.statusPill} ${styles.statusPillPosted}`}>
                            <Check size={13} aria-hidden="true" />
                            Đã hạch toán
                        </span>
                    ) : editable ? (
                        attemptedSubmit && hasAnyError ? (
                            <span className={`${styles.statusPill} ${styles.statusPillError}`}>
                                <AlertCircle size={13} aria-hidden="true" />
                                Có lỗi
                            </span>
                        ) : isCompleted ? (
                            <span className={`${styles.statusPill} ${styles.statusPillSuccess}`}>
                                <CheckCircle2 size={13} aria-hidden="true" />
                                Đã nhập đủ
                            </span>
                        ) : (
                            <span className={`${styles.statusPill} ${styles.statusPillPending}`}>
                                Chưa nhập đủ
                            </span>
                        )
                    ) : null}

                    {isCollapsible ? (
                        <button
                            aria-expanded={isExpanded}
                            aria-label={isExpanded ? "Thu gọn sản phẩm" : "Mở rộng sản phẩm"}
                            className={styles.chevronButton}
                            onClick={(e) => {
                                e.stopPropagation();
                                onToggleExpand?.();
                            }}
                            type="button"
                        >
                            {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                        </button>
                    ) : null}
                </div>
            </div>

            {/* Card Body */}
            {isExpanded ? (
                <div className={styles.cardBody}>
                    {/* Section 1: Thông tin khách khai báo */}
                    <div className={styles.declaredInfoRow}>
                        <div className="flex items-center gap-2 text-xs text-[#475569]">
                            <Package size={14} className="text-[#07883a]" />
                            <span>Tình trạng khách khai báo:</span>
                            <span className="font-bold text-[#0f172a]">
                                {declared ? getReturnConditionLabel(declared as CustomerReturnActualCondition) : "Chưa có khai báo"}
                            </span>
                        </div>
                        {reason ? (
                            <div className="text-xs text-[#64748b]">
                                Lý do: <span className="font-semibold">{getReturnReasonLabel(reason as never) || reason}</span>
                            </div>
                        ) : null}
                    </div>

                    {editable && !isPosted ? (
                        <div className={styles.formFlow}>
                            {/* BƯỚC 1: TÌNH TRẠNG THỰC TẾ */}
                            <div className={styles.fieldSection}>
                                <label className={styles.fieldLabel} id={`${cardId}-condition-label`}>
                                    1. Tình trạng thực tế sau kiểm tra <span className={styles.requiredStar}>*</span>
                                </label>
                                <div
                                    aria-labelledby={`${cardId}-condition-label`}
                                    className={styles.radioGrid}
                                    role="radiogroup"
                                >
                                    {RETURN_CONDITIONS.map((condition) => {
                                        const isSelected = actual === condition;
                                        return (
                                            <button
                                                aria-checked={isSelected}
                                                className={`${styles.radioOptionCard} ${isSelected ? styles.radioOptionCardActive : ""}`}
                                                key={condition}
                                                onClick={() => handleActualChange(condition)}
                                                role="radio"
                                                type="button"
                                            >
                                                <div className="flex items-center justify-between w-full">
                                                    <span className={styles.radioOptionTitle}>
                                                        {RETURN_CONDITION_LABELS[condition]}
                                                    </span>
                                                    <span className={`${styles.radioCircle} ${isSelected ? styles.radioCircleActive : ""}`}>
                                                        {isSelected ? <span className={styles.radioDot} /> : null}
                                                    </span>
                                                </div>
                                                <span className={styles.radioOptionDesc}>
                                                    {CONDITION_DESCRIPTIONS[condition]}
                                                </span>
                                            </button>
                                        );
                                    })}
                                </div>
                                {actual && declared && actual !== declared ? (
                                    <p className={styles.fieldHintWarning}>
                                        <Info size={13} aria-hidden="true" />
                                        Tình trạng thực tế khác với thông tin khai báo ban đầu ({getReturnConditionLabel(declared as CustomerReturnActualCondition)}).
                                    </p>
                                ) : null}
                                {showActualError ? (
                                    <p className={styles.fieldErrorText} role="alert">
                                        <AlertCircle size={13} aria-hidden="true" />
                                        {actualError}
                                    </p>
                                ) : null}
                            </div>

                            {/* BƯỚC 2: PHƯƠNG ÁN XỬ LÝ */}
                            <div className={styles.fieldSection}>
                                <label className={styles.fieldLabel} id={`${cardId}-result-label`}>
                                    2. Phương án xử lý <span className={styles.requiredStar}>*</span>
                                </label>
                                <div
                                    aria-labelledby={`${cardId}-result-label`}
                                    className={styles.radioGrid}
                                    role="radiogroup"
                                >
                                    {RESULT_OPTIONS.map((opt) => {
                                        const isSelected = draft.result === opt.key;
                                        const isDisabled = opt.key === "RESTOCK" && !allowedResults.includes("RESTOCK");
                                        const IconComponent = opt.icon;
                                        return (
                                            <button
                                                aria-checked={isSelected}
                                                aria-disabled={isDisabled}
                                                className={`${styles.radioOptionCard} ${isSelected ? styles.radioOptionCardActive : ""} ${isDisabled ? styles.radioOptionCardDisabled : ""}`}
                                                disabled={isDisabled}
                                                key={opt.key}
                                                onClick={() => handleResultChange(opt.key)}
                                                role="radio"
                                                title={isDisabled ? "Chỉ được nhập lại kho khi hàng còn nguyên seal" : undefined}
                                                type="button"
                                            >
                                                <div className="flex items-center justify-between w-full">
                                                    <div className="flex items-center gap-1.5">
                                                        <IconComponent size={15} className={isSelected ? "text-[#07883a]" : "text-[#64748b]"} />
                                                        <span className={styles.radioOptionTitle}>{opt.title}</span>
                                                    </div>
                                                    <span className={`${styles.radioCircle} ${isSelected ? styles.radioCircleActive : ""}`}>
                                                        {isSelected ? <span className={styles.radioDot} /> : null}
                                                    </span>
                                                </div>
                                                <span className={styles.radioOptionDesc}>
                                                    {isDisabled ? "Chỉ khả dụng khi hàng còn nguyên seal" : opt.desc}
                                                </span>
                                            </button>
                                        );
                                    })}
                                    {line.result === "QUARANTINE" ? (
                                        <button
                                            aria-checked={draft.result === "QUARANTINE"}
                                            className={`${styles.radioOptionCard} ${draft.result === "QUARANTINE" ? styles.radioOptionCardActive : ""}`}
                                            onClick={() => handleResultChange("QUARANTINE")}
                                            role="radio"
                                            type="button"
                                        >
                                            <div className="flex items-center justify-between w-full">
                                                <span className={styles.radioOptionTitle}>Cách ly (Lịch sử)</span>
                                                <span className={`${styles.radioCircle} ${draft.result === "QUARANTINE" ? styles.radioCircleActive : ""}`}>
                                                    {draft.result === "QUARANTINE" ? <span className={styles.radioDot} /> : null}
                                                </span>
                                            </div>
                                            <span className={styles.radioOptionDesc}>Phân loại cách ly từ đợt kiểm tra trước</span>
                                        </button>
                                    ) : null}
                                </div>
                                {showResultError ? (
                                    <p className={styles.fieldErrorText} role="alert">
                                        <AlertCircle size={13} aria-hidden="true" />
                                        {resultError}
                                    </p>
                                ) : null}
                            </div>

                            {/* BƯỚC 3: VỊ TRÍ LƯU KHO */}
                            <div className={styles.fieldSection}>
                                <label className={styles.fieldLabel} htmlFor={`${cardId}-location`}>
                                    3. Vị trí lưu kho {draft.result !== "WRITE_OFF" ? <span className={styles.requiredStar}>*</span> : null}
                                </label>
                                {draft.result === "WRITE_OFF" ? (
                                    <div className={styles.infoNotice}>
                                        <Info size={14} className="text-[#64748b]" />
                                        <span>Phương án tiêu hủy xuất kho trực tiếp, không yêu cầu vị trí lưu kho.</span>
                                    </div>
                                ) : (
                                    <div className="flex flex-col gap-1">
                                        <select
                                            className={`${styles.selectControl} ${showLocationError ? styles.selectControlError : ""}`}
                                            id={`${cardId}-location`}
                                            onChange={(e) => {
                                                onFieldTouch?.("destinationLocationId");
                                                onChange({ destinationLocationId: e.target.value });
                                            }}
                                            value={draft.destinationLocationId}
                                        >
                                            <option value="">-- Chọn vị trí lưu kho phù hợp --</option>
                                            {eligibleLocations.map((loc) => (
                                                <option key={loc.id} value={loc.id}>
                                                    {loc.name}
                                                </option>
                                            ))}
                                        </select>
                                        {eligibleLocations.length === 0 ? (
                                            <span className="text-xs text-amber-700 font-semibold mt-1">
                                                Chưa có vị trí kho phù hợp với phân loại này. Vui lòng kiểm tra danh mục vị trí kho.
                                            </span>
                                        ) : null}
                                        {showLocationError ? (
                                            <p className={styles.fieldErrorText} role="alert">
                                                <AlertCircle size={13} aria-hidden="true" />
                                                {locationError}
                                            </p>
                                        ) : null}
                                    </div>
                                )}
                            </div>

                            {/* BƯỚC 4: GHI CHÚ */}
                            <div className={styles.fieldSection}>
                                <label className={styles.fieldLabel} htmlFor={`${cardId}-note`}>
                                    4. Ghi chú kiểm định <span className="text-xs font-normal text-[#64748b]">(tùy chọn)</span>
                                </label>
                                <textarea
                                    className={styles.textareaControl}
                                    id={`${cardId}-note`}
                                    maxLength={500}
                                    onChange={(e) => onChange({ note: e.target.value })}
                                    placeholder="Ghi chú thêm về ngoại quan, tình trạng bao bì, vết trầy xước..."
                                    rows={2}
                                    value={draft.note}
                                />
                            </div>
                        </div>
                    ) : (
                        /* Read-only view for posted or non-editable lines */
                        <div className={styles.readOnlyGrid}>
                            <div className={styles.readOnlyItem}>
                                <span className={styles.readOnlyLabel}>Tình trạng thực tế</span>
                                <span className={styles.readOnlyValue}>
                                    {actual ? getReturnConditionLabel(actual) : "Chưa ghi nhận"}
                                </span>
                            </div>
                            <div className={styles.readOnlyItem}>
                                <span className={styles.readOnlyLabel}>Phương án xử lý</span>
                                <span className={styles.readOnlyValue}>
                                    {draft.result === "RESTOCK"
                                        ? "Nhập lại kho"
                                        : draft.result === "DAMAGED"
                                            ? "Hàng hỏng"
                                            : draft.result === "WRITE_OFF"
                                                ? "Tiêu hủy"
                                                : draft.result}
                                </span>
                            </div>
                            <div className={styles.readOnlyItem}>
                                <span className={styles.readOnlyLabel}>Vị trí đích</span>
                                <span className={styles.readOnlyValue}>
                                    {draft.result === "WRITE_OFF"
                                        ? "Không áp dụng"
                                        : line.destinationLocationName || "Chưa xác định"}
                                </span>
                            </div>
                            {line.note ? (
                                <div className={styles.readOnlyItemFull}>
                                    <span className={styles.readOnlyLabel}>Ghi chú kiểm định</span>
                                    <span className={styles.readOnlyValue}>{line.note}</span>
                                </div>
                            ) : null}
                        </div>
                    )}
                </div>
            ) : null}
        </article>
    );
}

