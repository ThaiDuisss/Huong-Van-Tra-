import { CheckCircle2, LoaderCircle, XCircle } from "lucide-react";
import type { OrderItemResponse } from "../types/order.types";
import type { OrderReturnEligibilityResponse } from "../types/order-return.types";
import styles from "../pages/CreateReturnPage.module.css";

export interface ReturnEligibilityStatusProps {
    response: OrderReturnEligibilityResponse | null;
    checking: boolean;
    error: string | null;
    items?: OrderItemResponse[];
    ready: boolean;
    compact?: boolean;
    onRetry?: () => void;
}

export function ReturnEligibilityStatus({
    response,
    checking,
    error,
    items = [],
    ready,
    compact = false,
    onRetry
}: ReturnEligibilityStatusProps) {
    if (!ready) {
        if (compact) return null;
        return (
            <div className={styles.eligibilityStatusPanel}>
                <p className={styles.eligibilityNeutral}>
                    Vui lòng chọn lý do hoàn hàng cho các sản phẩm đã chọn để hệ thống xác minh điều kiện.
                </p>
            </div>
        );
    }

    if (checking) {
        if (compact) {
            return (
                <div
                    className={styles.eligibilityInlineChecking}
                    aria-live="polite"
                    aria-busy="true"
                >
                    <LoaderCircle className={styles.spinner} size={15} aria-hidden="true" />
                    <span>Đang kiểm tra điều kiện hoàn hàng...</span>
                </div>
            );
        }
        return (
            <div
                className={`${styles.eligibilityStatusPanel} ${styles.eligibilityChecking}`}
                aria-live="polite"
                aria-busy="true"
            >
                <LoaderCircle className={styles.spinner} size={18} aria-hidden="true" />
                <span>Đang kiểm tra điều kiện hoàn hàng...</span>
            </div>
        );
    }

    if (error) {
        if (compact) {
            return (
                <div className={styles.eligibilityInlineError} role="alert">
                    <div className={styles.inlineErrorLeft}>
                        <XCircle size={15} aria-hidden="true" />
                        <span>Chưa thể kiểm tra điều kiện hoàn hàng.</span>
                    </div>
                    {onRetry && (
                        <button
                            type="button"
                            className={styles.retryInlineButton}
                            onClick={onRetry}
                        >
                            Thử lại
                        </button>
                    )}
                </div>
            );
        }
        return (
            <div
                className={`${styles.eligibilityStatusPanel} ${styles.eligibilityError}`}
                role="alert"
            >
                <div className={styles.eligibilityHeader}>
                    <XCircle size={18} aria-hidden="true" />
                    <strong>Chưa thể kiểm tra điều kiện hoàn hàng. Vui lòng thử lại.</strong>
                </div>
                {onRetry && (
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={onRetry}
                    >
                        Kiểm tra lại
                    </button>
                )}
            </div>
        );
    }

    if (!response) {
        return null;
    }

    if (response.eligible) {
        if (compact) {
            return (
                <div className={styles.eligibilityInlineSuccess} role="status">
                    <CheckCircle2 size={16} aria-hidden="true" />
                    <strong>Đủ điều kiện tạo phiếu hoàn</strong>
                </div>
            );
        }
        return (
            <div
                className={`${styles.eligibilityStatusPanel} ${styles.eligibilitySuccess}`}
                role="status"
            >
                <CheckCircle2 size={18} aria-hidden="true" />
                <strong>Đủ điều kiện tạo phiếu hoàn</strong>
            </div>
        );
    }

    // Response is not eligible: aggregate failing checks only
    const failures: string[] = [];

    // Top-level order checks that failed
    (response.checks ?? [])
        .filter((check) => !check.passed)
        .forEach((check) => {
            failures.push(check.message);
        });

    // Item-level checks that failed
    (response.items ?? []).forEach((itemResult) => {
        const itemInfo = items.find((it) => it.id === itemResult.orderItemId);
        const productName = itemInfo?.productNameSnapshot?.trim() || "Sản phẩm";

        (itemResult.checks ?? [])
            .filter((check) => !check.passed)
            .forEach((check) => {
                const message = check.message.startsWith(productName)
                    ? check.message
                    : `${productName}: ${check.message}`;
                failures.push(message);
            });
    });

    const displayFailures = failures.length > 0 ? failures : ["Có điều kiện hoàn chưa được thỏa mãn."];

    return (
        <div
            className={`${styles.eligibilityStatusPanel} ${styles.eligibilityFailed}`}
            role="alert"
        >
            <div className={styles.eligibilityHeader}>
                <XCircle size={18} aria-hidden="true" />
                <strong>Không đủ điều kiện tạo phiếu hoàn</strong>
            </div>
            <ul className={styles.eligibilityFailureList}>
                {displayFailures.map((msg, idx) => (
                    <li key={idx}>{msg}</li>
                ))}
            </ul>
        </div>
    );
}
