import {
    AlertTriangle,
    Check,
    ChevronRight,
    Loader2,
    Plus,
    X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import type { InventoryLocationResponse } from "../../inventory/types/inventory.types";
import { submitReturnInventoryDisposition } from "../services/order-return.api";
import type {
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason
} from "../types/order-return.types";
import {
    formatReturnQuantity,
    getAllowedCustomerReturnQcResults,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    toReturnNumber
} from "../utils/order-return.utils";
import styles from "./ReturnInspectionModal.module.css";

interface ReturnInspectionModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (updatedResponse?: OrderReturnResponse) => void;
    onRefresh?: () => void;
    onToast: (message: string) => void;
    qcSummary: CustomerReturnQcSummaryResponse | null;
    returnItem: OrderReturnListRow;
}

interface QcDraftLine {
    selected: boolean;
    quantity: string;
    result: CustomerReturnQcResult;
    destinationLocationId: string;
    note: string;
}

const QC_RESULT_OPTIONS: {
    result: CustomerReturnQcResult;
    title: string;
    desc: string;
}[] = [
    {
        result: "RESTOCK",
        title: "Đạt chất lượng",
        desc: "Có thể nhập lại kho và tiếp tục bán"
    },
    {
        result: "DAMAGED",
        title: "Hàng hỏng",
        desc: "Chuyển sang khu hàng hỏng, không được bán"
    },
    {
        result: "WRITE_OFF",
        title: "Tiêu hủy",
        desc: "Không nhập lại tồn kho bán được"
    }
];

function isSaleableReturnLocation(location: InventoryLocationResponse) {
    return (
        location.status === "ACTIVE" &&
        location.type !== "DAMAGED" &&
        location.type !== "QUARANTINE"
    );
}

function getLocationOptionLabel(location: InventoryLocationResponse) {
    const code = String(location.code ?? "").trim();
    const name = String(location.name ?? "").trim();

    if (code && name) {
        return `${name} (${code})`;
    }

    return name || code || "Vị trí kho";
}

function getDefaultQcResult(condition?: ReturnCondition | null): CustomerReturnQcResult {
    return condition === "SEALED" ? "RESTOCK" : "DAMAGED";
}

export function ReturnInspectionModal({
    isOpen,
    onClose,
    onSuccess,
    onRefresh,
    onToast,
    qcSummary,
    returnItem
}: ReturnInspectionModalProps) {
    const [step, setStep] = useState<1 | 2>(1);
    const [qcDraftLines, setQcDraftLines] = useState<Record<string, QcDraftLine>>({});
    const [generalNote, setGeneralNote] = useState("");
    const [isGeneralNoteOpen, setIsGeneralNoteOpen] = useState(false);
    const [activeLocations, setActiveLocations] = useState<InventoryLocationResponse[]>([]);
    const [damagedLocations, setDamagedLocations] = useState<InventoryLocationResponse[]>([]);
    const [isLocationLoading, setIsLocationLoading] = useState(false);
    const [locationError, setLocationError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);

    const summaryLines = useMemo(
        () => qcSummary?.lines ?? [],
        [qcSummary?.lines]
    );

    const remainingLines = useMemo(
        () =>
            summaryLines.filter(
                (line) => toReturnNumber(line.remainingQcQuantity) > 0
            ),
        [summaryLines]
    );

    const totalRemainingQuantity = useMemo(
        () =>
            remainingLines.reduce(
                (total, line) => total + toReturnNumber(line.remainingQcQuantity),
                0
            ),
        [remainingLines]
    );

    const restockLocations = useMemo(
        () => activeLocations.filter(isSaleableReturnLocation),
        [activeLocations]
    );

    const normalizedDamagedLocations = useMemo(() => {
        const map = new Map<string, InventoryLocationResponse>();
        activeLocations
            .filter((l) => l.status === "ACTIVE" && l.type === "DAMAGED")
            .forEach((l) => map.set(String(l.id), l));
        damagedLocations
            .filter((l) => l.status === "ACTIVE" && l.type === "DAMAGED")
            .forEach((l) => map.set(String(l.id), l));
        return Array.from(map.values());
    }, [activeLocations, damagedLocations]);

    const allLocationsMap = useMemo(() => {
        const map = new Map<string, InventoryLocationResponse>();
        activeLocations.forEach((loc) => map.set(String(loc.id), loc));
        damagedLocations.forEach((loc) => map.set(String(loc.id), loc));
        return map;
    }, [activeLocations, damagedLocations]);

    const getValidLocations = useCallback(
        (result: CustomerReturnQcResult) => {
            if (result === "RESTOCK") {
                return restockLocations;
            }
            if (result === "DAMAGED") {
                return normalizedDamagedLocations;
            }
            return [];
        },
        [normalizedDamagedLocations, restockLocations]
    );

    // Load locations when modal opens
    useEffect(() => {
        if (!isOpen) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsLocationLoading(true);
            setLocationError(null);

            Promise.all([
                inventoryLocationApi.list({
                    page: 0,
                    size: 100,
                    status: "ACTIVE"
                }),
                inventoryLocationApi.list({
                    page: 0,
                    size: 100,
                    status: "ACTIVE",
                    type: "DAMAGED"
                })
            ])
                .then(([activePage, damagedPage]) => {
                    if (!ignore) {
                        setActiveLocations(activePage.content ?? []);
                        setDamagedLocations(damagedPage.content ?? []);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setActiveLocations([]);
                        setDamagedLocations([]);
                        setLocationError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsLocationLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [isOpen]);

    // Initialize draft lines when remaining lines or modal open status changes
    useEffect(() => {
        if (!isOpen) {
            setStep(1);
            setFormError(null);
            setGeneralNote("");
            setIsGeneralNoteOpen(false);
            return;
        }

        const isSingleItem = remainingLines.length === 1;

        setQcDraftLines((current) => {
            const nextEntries = remainingLines.map((line) => {
                const remainingQty = toReturnNumber(line.remainingQcQuantity);
                const existing = current[line.returnItemId];
                if (existing) {
                    const currentQty = Number(existing.quantity);
                    const safeQty =
                        Number.isFinite(currentQty) && currentQty > 0
                            ? Math.min(currentQty, remainingQty)
                            : remainingQty;
                    return [
                        line.returnItemId,
                        {
                            ...existing,
                            quantity: String(safeQty || 1)
                        }
                    ];
                }

                const defaultResult = getDefaultQcResult(line.condition);
                const validLocs = defaultResult === "RESTOCK"
                    ? restockLocations
                    : defaultResult === "DAMAGED"
                        ? normalizedDamagedLocations
                        : [];

                // Smart auto-select location if exactly 1 valid location
                const autoLocationId = validLocs.length === 1 ? String(validLocs[0].id) : "";

                const initialLine: QcDraftLine = {
                    selected: isSingleItem || true, // Default all to selected
                    quantity: String(remainingQty || 1),
                    result: defaultResult,
                    destinationLocationId: autoLocationId,
                    note: ""
                };

                return [line.returnItemId, initialLine];
            });

            return Object.fromEntries(nextEntries);
        });
    }, [isOpen, normalizedDamagedLocations, remainingLines, restockLocations]);

    // Handle updating location when location lists finish loading
    useEffect(() => {
        if (!isOpen) return;

        setQcDraftLines((current) => {
            let changed = false;
            const updated = { ...current };

            for (const [returnItemId, draft] of Object.entries(updated)) {
                if (!draft.destinationLocationId && draft.result !== "WRITE_OFF") {
                    const validLocs = getValidLocations(draft.result);
                    if (validLocs.length === 1) {
                        updated[returnItemId] = {
                            ...draft,
                            destinationLocationId: String(validLocs[0].id)
                        };
                        changed = true;
                    }
                }
            }

            return changed ? updated : current;
        });
    }, [getValidLocations, isOpen, normalizedDamagedLocations, restockLocations]);

    const updateDraftLine = (
        returnItemId: string,
        update: Partial<QcDraftLine>
    ) => {
        setFormError(null);
        setQcDraftLines((current) => {
            const existing = current[returnItemId];
            if (!existing) return current;

            const next = { ...existing, ...update };

            // If result changed, auto-select location if exactly 1 option exists
            if (update.result) {
                if (update.result === "WRITE_OFF") {
                    next.destinationLocationId = "";
                } else {
                    const validLocs = getValidLocations(update.result);
                    next.destinationLocationId = validLocs.length === 1 ? String(validLocs[0].id) : "";
                }
            }

            return {
                ...current,
                [returnItemId]: next
            };
        });
    };

    const selectedLines = useMemo(() => {
        if (remainingLines.length === 1) {
            return remainingLines;
        }
        return remainingLines.filter(
            (line) => qcDraftLines[line.returnItemId]?.selected
        );
    }, [qcDraftLines, remainingLines]);

    const findReturnItemSnapshot = (returnItemId: string) => {
        return (returnItem.items ?? []).find((item) => item.id === returnItemId);
    };

    const getLineName = (line: CustomerReturnQcSummaryLine) => {
        const item = findReturnItemSnapshot(line.returnItemId);
        return item?.productNameSnapshot?.trim() || "Sản phẩm";
    };

    const getLineMeta = (line: CustomerReturnQcSummaryLine) => {
        const item = findReturnItemSnapshot(line.returnItemId);
        const sku = item?.skuSnapshot?.trim() || "--";
        const variant = item?.variantNameSnapshot?.trim() || "Mặc định";
        return `${sku} · ${variant}`;
    };

    // Validation before moving to Step 2
    const validateBeforeReview = () => {
        if (selectedLines.length === 0) {
            setFormError("Vui lòng chọn ít nhất một sản phẩm để kiểm tra.");
            return false;
        }

        for (const line of selectedLines) {
            const draft = qcDraftLines[line.returnItemId];
            const name = getLineName(line);

            if (!draft) {
                setFormError(`Thiếu thông tin kiểm tra cho sản phẩm "${name}".`);
                return false;
            }

            const qty = Number(draft.quantity);
            const remaining = toReturnNumber(line.remainingQcQuantity);

            if (!Number.isFinite(qty) || qty <= 0) {
                setFormError(`Số lượng kiểm tra của "${name}" phải lớn hơn 0.`);
                return false;
            }

            if (qty > remaining) {
                setFormError(`Số lượng kiểm tra của "${name}" (${qty}) vượt quá số lượng còn lại (${remaining}).`);
                return false;
            }

            const allowedResults = getAllowedCustomerReturnQcResults(line.condition);
            if (!allowedResults.includes(draft.result)) {
                setFormError(`Sản phẩm "${name}" với tình trạng "${getReturnConditionLabel(line.condition)}" không được chọn kết quả "${draft.result}".`);
                return false;
            }

            if (draft.result === "RESTOCK") {
                if (restockLocations.length === 0) {
                    setFormError(`Chưa có vị trí kho bán được trong hệ thống.`);
                    return false;
                }
                const isValid = restockLocations.some((l) => String(l.id) === draft.destinationLocationId);
                if (!draft.destinationLocationId || !isValid) {
                    setFormError(`Vui lòng chọn vị trí kho bán được cho "${name}".`);
                    return false;
                }
            }

            if (draft.result === "DAMAGED") {
                if (normalizedDamagedLocations.length === 0) {
                    setFormError(`Chưa có vị trí kho hàng hỏng trong hệ thống.`);
                    return false;
                }
                const isValid = normalizedDamagedLocations.some((l) => String(l.id) === draft.destinationLocationId);
                if (!draft.destinationLocationId || !isValid) {
                    setFormError(`Vui lòng chọn vị trí kho hàng hỏng cho "${name}".`);
                    return false;
                }
            }

            // Backend rule: SEALED item marked DAMAGED or WRITE_OFF requires reason note >= 5 chars
            if (line.condition === "SEALED" && (draft.result === "DAMAGED" || draft.result === "WRITE_OFF")) {
                const hasLineNote = draft.note.trim().length >= 5;
                const hasGenNote = generalNote.trim().length >= 5;
                if (!hasLineNote && !hasGenNote) {
                    setFormError(`Sản phẩm "${name}" còn nguyên seal nhưng chọn ${draft.result === "DAMAGED" ? "hàng hỏng" : "tiêu hủy"} cần ghi chú lý do tối thiểu 5 ký tự.`);
                    return false;
                }
            }
        }

        setFormError(null);
        return true;
    };

    const handleGoToReview = () => {
        if (validateBeforeReview()) {
            setStep(2);
        }
    };

    // Final Submission
    const handleConfirmSubmit = () => {
        if (!validateBeforeReview()) {
            setStep(1);
            return;
        }

        const linesPayload = selectedLines.map((line) => {
            const draft = qcDraftLines[line.returnItemId];
            const lineNote = draft.note.trim();
            const combinedNote = lineNote || generalNote.trim() || null;

            return {
                returnItemId: line.returnItemId,
                quantity: Number(draft.quantity),
                result: draft.result,
                destinationLocationId: draft.result === "WRITE_OFF" ? null : draft.destinationLocationId || null,
                note: combinedNote
            };
        });

        const commandId = crypto.randomUUID();
        setIsSubmitting(true);
        setFormError(null);

        submitReturnInventoryDisposition(returnItem.id, {
            commandId,
            lines: linesPayload
        })
            .then((response) => {
                if (response.status === "PROCESSED") {
                    onToast("Đã kiểm tra và cập nhật kho thành công.");
                    onSuccess();
                    onClose();
                } else if (response.status === "FAILED") {
                    const failureMsg = response.failureReason
                        ? getReturnErrorMessage(new Error(response.failureReason))
                        : "Không thể cập nhật kho.";
                    setFormError(failureMsg);
                    onRefresh?.();
                } else {
                    onToast("Đã gửi lệnh xử lý kho. Đang cập nhật tồn kho...");
                    onSuccess();
                    onClose();
                }
            })
            .catch((error: unknown) => {
                const mappedError = getReturnErrorMessage(error);
                setFormError(mappedError);
                setStep(1);
                onRefresh?.();
            })
            .finally(() => {
                setIsSubmitting(false);
            });
    };

    if (!isOpen) {
        return null;
    }

    const isSingleRemainingItem = remainingLines.length === 1;

    return (
        <div
            className={styles.modalBackdrop}
            onClick={(e) => {
                e.stopPropagation();
                onClose();
            }}
            role="presentation"
        >
            <aside
                aria-label="Kiểm tra hàng trả"
                aria-modal="true"
                className={styles.modalDialog}
                onClick={(e) => e.stopPropagation()}
                role="dialog"
            >
                {/* Header */}
                <div className={styles.modalHeader}>
                    <div className={styles.headerInfo}>
                        <h2>Kiểm tra hàng trả</h2>
                        <div className={styles.headerMeta}>
                            <span className={styles.returnCodeTag}>
                                {getReturnCode(returnItem)}
                            </span>
                            <span className={styles.metaDot}>·</span>
                            <span>
                                {remainingLines.length} sản phẩm cần kiểm tra
                            </span>
                            <span className={styles.metaDot}>·</span>
                            <span>
                                Còn lại {formatReturnQuantity(totalRemainingQuantity)} sản phẩm
                            </span>
                        </div>
                    </div>
                    <button
                        aria-label="Đóng kiểm tra hàng trả"
                        className={styles.closeButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {/* 2-Step Bar */}
                <div className={styles.stepperWrap}>
                    <div
                        className={`${styles.stepItem} ${
                            step === 1 ? styles.stepActive : ""
                        }`}
                    >
                        <span className={styles.stepNumber}>1</span>
                        <span>Kiểm tra sản phẩm</span>
                    </div>
                    <ChevronRight className={styles.stepDivider} size={14} />
                    <div
                        className={`${styles.stepItem} ${
                            step === 2 ? styles.stepActive : ""
                        }`}
                    >
                        <span className={styles.stepNumber}>2</span>
                        <span>Xác nhận</span>
                    </div>
                </div>

                {/* Modal Body */}
                <div className={styles.modalBody}>
                    {/* Location Error if any */}
                    {locationError ? (
                        <div className={styles.errorBanner}>
                            <AlertTriangle size={16} />
                            <span>{locationError}</span>
                        </div>
                    ) : null}

                    {/* Form Error Banner */}
                    {formError ? (
                        <div className={styles.errorBanner}>
                            <AlertTriangle size={16} />
                            <span>{formError}</span>
                        </div>
                    ) : null}

                    {/* Empty/Exhausted State when no remaining items */}
                    {remainingLines.length === 0 ? (
                        <div
                            style={{
                                padding: "48px 24px",
                                textAlign: "center",
                                display: "flex",
                                flexDirection: "column",
                                alignItems: "center",
                                gap: "12px"
                            }}
                        >
                            <AlertTriangle size={44} color="#d97706" />
                            <h3
                                style={{
                                    fontSize: "16px",
                                    fontWeight: 600,
                                    color: "#1e293b",
                                    margin: 0
                                }}
                            >
                                Không còn sản phẩm cần xử lý kho
                            </h3>
                            <p
                                style={{
                                    fontSize: "14px",
                                    color: "#64748b",
                                    maxWidth: "460px",
                                    margin: 0,
                                    lineHeight: 1.5
                                }}
                            >
                                {formError ||
                                    "Tất cả sản phẩm của phiếu hoàn này đã được xử lý kho hoặc đã có lệnh xử lý đang chờ thực hiện."}
                            </p>
                        </div>
                    ) : null}

                    {/* Step 1: Form QC */}
                    {remainingLines.length > 0 && step === 1 ? (
                        <>
                            {remainingLines.map((line) => {
                                const draft = qcDraftLines[line.returnItemId] ?? {
                                    selected: true,
                                    quantity: "1",
                                    result: "RESTOCK",
                                    destinationLocationId: "",
                                    note: ""
                                };

                                const isSelected = isSingleRemainingItem || draft.selected;
                                const remainingQty = toReturnNumber(line.remainingQcQuantity);
                                const allowedResults = getAllowedCustomerReturnQcResults(line.condition);
                                const validLocations = getValidLocations(draft.result);
                                const snapshot = findReturnItemSnapshot(line.returnItemId);

                                return (
                                    <div
                                        className={`${styles.productCard} ${
                                            !isSelected ? styles.productCardUnselected : ""
                                        }`}
                                        key={line.returnItemId}
                                    >
                                        {/* Card Top Row */}
                                        <div className={styles.cardTopRow}>
                                            <div className={styles.cardIdentity}>
                                                {!isSingleRemainingItem ? (
                                                    <input
                                                        aria-label={`Chọn sản phẩm ${getLineName(line)}`}
                                                        checked={draft.selected}
                                                        className={styles.cardCheckbox}
                                                        onChange={(e) =>
                                                            updateDraftLine(line.returnItemId, {
                                                                selected: e.target.checked
                                                            })
                                                        }
                                                        type="checkbox"
                                                    />
                                                ) : null}
                                                <div className={styles.cardTitles}>
                                                    <strong>{getLineName(line)}</strong>
                                                    <span>{getLineMeta(line)}</span>
                                                </div>
                                            </div>
                                            <span className={styles.remainingBadge}>
                                                Còn kiểm tra: {formatReturnQuantity(remainingQty)}
                                            </span>
                                        </div>

                                        {/* Readonly Info: Lý do hoàn, Tình trạng khi trả */}
                                        <div className={styles.cardMetaGrid}>
                                            <div className={styles.metaRow}>
                                                <dt>Lý do hoàn:</dt>
                                                <dd>
                                                    {getReturnReasonLabel(snapshot?.reason as ReturnReason) || "--"}
                                                </dd>
                                            </div>
                                            <div className={styles.metaRow}>
                                                <dt>Tình trạng:</dt>
                                                <dd>
                                                    {getReturnConditionLabel(line.condition) || "--"}
                                                </dd>
                                            </div>
                                        </div>

                                        {/* QC Inputs Section */}
                                        {isSelected ? (
                                            <div className={styles.formSection}>
                                                {/* 1. Số lượng kiểm tra */}
                                                <div className={styles.fieldGroup}>
                                                    <label
                                                        className={styles.fieldLabel}
                                                        htmlFor={`qty-${line.returnItemId}`}
                                                    >
                                                        <span>Số lượng kiểm tra</span>
                                                        <span className={styles.quantityMaxHint}>
                                                            Tối đa: {formatReturnQuantity(remainingQty)}
                                                        </span>
                                                    </label>
                                                    <div className={styles.quantityInputWrap}>
                                                        <input
                                                            className={styles.quantityInput}
                                                            id={`qty-${line.returnItemId}`}
                                                            max={remainingQty}
                                                            min={1}
                                                            onChange={(e) =>
                                                                updateDraftLine(line.returnItemId, {
                                                                    quantity: e.target.value
                                                                })
                                                            }
                                                            step="1"
                                                            type="number"
                                                            value={draft.quantity}
                                                        />
                                                    </div>
                                                </div>

                                                {/* 2. Kết quả kiểm tra (Radio Cards) */}
                                                <div className={styles.fieldGroup}>
                                                    <label className={styles.fieldLabel}>
                                                        <span>Kết quả kiểm tra</span>
                                                    </label>
                                                    <div className={styles.resultCardsGrid}>
                                                        {QC_RESULT_OPTIONS.map((opt) => {
                                                            const isAllowed = allowedResults.includes(opt.result);
                                                            const isActive = draft.result === opt.result;

                                                            return (
                                                                <button
                                                                    className={`${styles.resultCard} ${
                                                                        isActive ? styles.resultCardActive : ""
                                                                    }`}
                                                                    disabled={!isAllowed}
                                                                    key={opt.result}
                                                                    onClick={() =>
                                                                        updateDraftLine(line.returnItemId, {
                                                                            result: opt.result
                                                                        })
                                                                    }
                                                                    type="button"
                                                                >
                                                                    <div className={styles.resultCardHeader}>
                                                                        <span className={styles.resultCardTitle}>
                                                                            {opt.title}
                                                                        </span>
                                                                        <div
                                                                            className={`${styles.resultCardRadio} ${
                                                                                isActive
                                                                                    ? styles.resultCardRadioChecked
                                                                                    : ""
                                                                            }`}
                                                                        >
                                                                            {isActive ? (
                                                                                <div
                                                                                    className={
                                                                                        styles.resultCardRadioInner
                                                                                    }
                                                                                />
                                                                            ) : null}
                                                                        </div>
                                                                    </div>
                                                                    <span className={styles.resultCardDesc}>
                                                                        {opt.desc}
                                                                    </span>
                                                                </button>
                                                            );
                                                        })}
                                                    </div>
                                                </div>

                                                {/* 3. Vị trí lưu / xử lý (Smart Location Handling) */}
                                                {draft.result !== "WRITE_OFF" ? (
                                                    <div className={styles.fieldGroup}>
                                                        <label
                                                            className={styles.fieldLabel}
                                                            htmlFor={`loc-${line.returnItemId}`}
                                                        >
                                                            <span>
                                                                {draft.result === "RESTOCK"
                                                                    ? "Vị trí kho bán được"
                                                                    : "Vị trí kho hàng hỏng"}
                                                            </span>
                                                            {isLocationLoading ? (
                                                                <span className={styles.fieldHelp}>
                                                                    Đang tải vị trí...
                                                                </span>
                                                            ) : null}
                                                        </label>

                                                        {validLocations.length === 1 ? (
                                                            <div className={styles.locationSingleHint}>
                                                                <Check size={16} color="#07883a" />
                                                                <span>
                                                                    Tự động chọn:{" "}
                                                                    <strong>
                                                                        {getLocationOptionLabel(validLocations[0])}
                                                                    </strong>
                                                                </span>
                                                            </div>
                                                        ) : (
                                                            <select
                                                                className={styles.locationSelect}
                                                                id={`loc-${line.returnItemId}`}
                                                                onChange={(e) =>
                                                                    updateDraftLine(line.returnItemId, {
                                                                        destinationLocationId: e.target.value
                                                                    })
                                                                }
                                                                value={draft.destinationLocationId}
                                                            >
                                                                <option value="">
                                                                    -- Chọn vị trí kho phù hợp ({validLocations.length}) --
                                                                </option>
                                                                {validLocations.map((loc) => (
                                                                    <option
                                                                        key={loc.id}
                                                                        value={String(loc.id)}
                                                                    >
                                                                        {getLocationOptionLabel(loc)}
                                                                    </option>
                                                                ))}
                                                            </select>
                                                        )}
                                                    </div>
                                                ) : (
                                                    <div className={styles.fieldGroup}>
                                                        <label className={styles.fieldLabel}>
                                                            <span>Vị trí xử lý</span>
                                                        </label>
                                                        <div className={styles.locationNoneHint}>
                                                            Không yêu cầu vị trí kho (sản phẩm sẽ được xuất tiêu hủy).
                                                        </div>
                                                    </div>
                                                )}

                                                {/* 4. Ghi chú kiểm tra */}
                                                <div className={styles.fieldGroup}>
                                                    <label
                                                        className={styles.fieldLabel}
                                                        htmlFor={`note-${line.returnItemId}`}
                                                    >
                                                        <span>Ghi chú kiểm tra</span>
                                                        {line.condition === "SEALED" &&
                                                        draft.result !== "RESTOCK" ? (
                                                            <span className={styles.fieldHelp} style={{ color: "#b45309" }}>
                                                                * Bắt buộc tối thiểu 5 ký tự
                                                            </span>
                                                        ) : null}
                                                    </label>
                                                    <textarea
                                                        className={styles.lineNoteTextarea}
                                                        id={`note-${line.returnItemId}`}
                                                        onChange={(e) =>
                                                            updateDraftLine(line.returnItemId, {
                                                                note: e.target.value
                                                            })
                                                        }
                                                        placeholder="Nhập ghi chú chi tiết về tình trạng sản phẩm..."
                                                        value={draft.note}
                                                    />
                                                </div>
                                            </div>
                                        ) : null}
                                    </div>
                                );
                            })}

                            {/* Optional Collapsible General Note */}
                            <div className={styles.generalNoteSection}>
                                <button
                                    className={styles.generalNoteToggle}
                                    onClick={() => setIsGeneralNoteOpen(!isGeneralNoteOpen)}
                                    type="button"
                                >
                                    <Plus size={14} />
                                    <span>
                                        {isGeneralNoteOpen
                                            ? "Ẩn ghi chú chung cho lần xử lý"
                                            : "+ Thêm ghi chú chung cho lần xử lý"}
                                    </span>
                                </button>

                                {isGeneralNoteOpen ? (
                                    <div className={styles.generalNoteCollapse}>
                                        <label htmlFor="modal-general-note">
                                            Ghi chú chung
                                        </label>
                                        <textarea
                                            className={styles.lineNoteTextarea}
                                            id="modal-general-note"
                                            onChange={(e) => setGeneralNote(e.target.value)}
                                            placeholder="Ghi chú thêm cho toàn bộ đợt kiểm tra hàng này..."
                                            rows={2}
                                            value={generalNote}
                                        />
                                    </div>
                                ) : null}
                            </div>
                        </>
                    ) : null}

                    {/* Step 2: Xác nhận (Review Only) */}
                    {remainingLines.length > 0 && step === 2 ? (
                        <div className={styles.reviewWrap}>
                            <div className={styles.reviewIntro}>
                                <span>
                                    Vui lòng kiểm tra lại kết quả trước khi gửi lệnh cập nhật kho.
                                </span>
                            </div>

                            <div className={styles.reviewCardsList}>
                                {selectedLines.map((line) => {
                                    const draft = qcDraftLines[line.returnItemId];
                                    const resultOpt = QC_RESULT_OPTIONS.find(
                                        (o) => o.result === draft.result
                                    );
                                    const loc = draft.destinationLocationId
                                        ? allLocationsMap.get(draft.destinationLocationId)
                                        : undefined;
                                    const locName = draft.result === "WRITE_OFF"
                                        ? "Tiêu hủy (không nhập kho)"
                                        : loc
                                            ? getLocationOptionLabel(loc)
                                            : draft.destinationLocationId || "--";

                                    return (
                                        <div
                                            className={styles.reviewCard}
                                            key={line.returnItemId}
                                        >
                                            <div className={styles.reviewCardHeader}>
                                                <strong>{getLineName(line)}</strong>
                                                <span>{getLineMeta(line)}</span>
                                            </div>

                                            <div className={styles.reviewGrid}>
                                                <div className={styles.reviewField}>
                                                    <span className={styles.reviewLabel}>
                                                        Số lượng
                                                    </span>
                                                    <span className={styles.reviewValue}>
                                                        {formatReturnQuantity(draft.quantity)}
                                                    </span>
                                                </div>

                                                <div className={styles.reviewField}>
                                                    <span className={styles.reviewLabel}>
                                                        Kết quả kiểm tra
                                                    </span>
                                                    <span className={styles.reviewValue}>
                                                        {resultOpt?.title || draft.result}
                                                    </span>
                                                </div>

                                                <div className={styles.reviewField}>
                                                    <span className={styles.reviewLabel}>
                                                        Xử lý kho
                                                    </span>
                                                    <span className={styles.reviewValue}>
                                                        {locName}
                                                    </span>
                                                </div>

                                                {draft.note.trim() ? (
                                                    <div className={styles.reviewNote}>
                                                        <strong>Ghi chú:</strong>
                                                        <span>{draft.note.trim()}</span>
                                                    </div>
                                                ) : null}
                                            </div>
                                        </div>
                                    );
                                })}

                                {generalNote.trim() ? (
                                    <div className={styles.reviewGeneralNote}>
                                        <strong>Ghi chú chung:</strong>
                                        <p>{generalNote.trim()}</p>
                                    </div>
                                ) : null}
                            </div>
                        </div>
                    ) : null}
                </div>

                {/* Sticky Footer */}
                <div className={styles.modalFooter}>
                    {remainingLines.length === 0 ? (
                        <button
                            className={styles.secondaryButton}
                            onClick={onClose}
                            type="button"
                        >
                            Đóng
                        </button>
                    ) : step === 1 ? (
                        <>
                            <button
                                className={styles.secondaryButton}
                                disabled={isSubmitting}
                                onClick={onClose}
                                type="button"
                            >
                                Hủy
                            </button>
                            <button
                                className={styles.primaryButton}
                                disabled={selectedLines.length === 0}
                                onClick={handleGoToReview}
                                type="button"
                            >
                                Tiếp tục xác nhận{" "}
                                {!isSingleRemainingItem ? `(${selectedLines.length})` : ""}
                            </button>
                        </>
                    ) : (
                        <>
                            <button
                                className={styles.secondaryButton}
                                disabled={isSubmitting}
                                onClick={() => setStep(1)}
                                type="button"
                            >
                                Quay lại
                            </button>
                            <button
                                className={styles.primaryButton}
                                disabled={isSubmitting}
                                onClick={handleConfirmSubmit}
                                type="button"
                            >
                                {isSubmitting ? (
                                    <>
                                        <Loader2 className="animate-spin" size={16} />
                                        Đang cập nhật kho...
                                    </>
                                ) : (
                                    "Xác nhận xử lý"
                                )}
                            </button>
                        </>
                    )}
                </div>
            </aside>
        </div>
    );
}
