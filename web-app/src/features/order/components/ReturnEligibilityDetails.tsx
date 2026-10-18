import { CheckCircle2, LoaderCircle, XCircle } from "lucide-react";
import type { OrderItemResponse } from "../types/order.types";
import type { OrderReturnEligibilityCheck, OrderReturnEligibilityResponse } from "../types/order-return.types";
import styles from "../pages/CreateReturnPage.module.css";

export function ReturnPolicy({ policy }: { policy?: string[] }) {
    return (
        <section className={styles.policyPanel}>
            <h3>Chính sách áp dụng</h3>
            {policy?.length ? (
                <ul>{policy.map((line) => <li key={line}>{line}</li>)}</ul>
            ) : <p>Chính sách sẽ hiển thị khi hệ thống kiểm tra lý do đã chọn.</p>}
        </section>
    );
}

function CheckList({ checks }: { checks: OrderReturnEligibilityCheck[] }) {
    const visible = checks.filter(check => !check.passed || ![
        "ITEMS_REQUIRED", "ITEM_IDENTIFIED", "ITEMS_UNIQUE", "REASON_REQUIRED", "CONDITION_REQUIRED"
    ].includes(check.code));
    return (
        <ul className={styles.checkList}>
            {visible.map(check => (
                <li className={check.passed ? styles.checkPassed : styles.checkFailed} key={check.code}>
                    {check.passed ? <CheckCircle2 size={16} aria-hidden="true" /> : <XCircle size={16} aria-hidden="true" />}
                    <span>{check.message}</span>
                </li>
            ))}
        </ul>
    );
}

export function ReturnEligibilityDetails({ response, checking, error, items, ready, onRetry, showPolicies = false }: {
    response: OrderReturnEligibilityResponse | null;
    checking: boolean;
    error: string | null;
    items: OrderItemResponse[];
    ready: boolean;
    onRetry: () => void;
    showPolicies?: boolean;
}) {
    return (
        <section className={styles.eligibilityPanel} aria-live="polite" aria-busy={checking}>
            <h3>Kiểm tra điều kiện</h3>
            {!ready ? <p>Chọn lý do trả hàng để xem chính sách và kiểm tra điều kiện hoàn.</p>
                : checking ? <p className={styles.checkingMessage}><LoaderCircle size={16} aria-hidden="true" /> Đang kiểm tra điều kiện hoàn...</p>
                : error ? <div role="alert"><p className={styles.checkFailed}>{error}</p><button className={styles.secondaryButton} onClick={onRetry} type="button">Kiểm tra lại</button></div>
                : response ? (
                    <>
                        <strong className={response.eligible ? styles.checkPassed : styles.checkFailed}>
                            {response.eligible ? "Đủ điều kiện hoàn" : "Chưa đủ điều kiện hoàn — vui lòng kiểm tra các điều kiện không đạt bên dưới."}
                        </strong>
                        <CheckList checks={response.checks} />
                        {response.items.map((item, index) => (
                            <div className={styles.itemChecks} key={`${item.orderItemId}-${index}`}>
                                <h4>{items.find(selected => selected.id === item.orderItemId)?.productNameSnapshot?.trim() || "Sản phẩm"}</h4>
                                {showPolicies && item.reason ? <ReturnPolicy policy={item.policy} /> : null}
                                <CheckList checks={item.checks} />
                            </div>
                        ))}
                    </>
                ) : <p>Chưa có kết quả kiểm tra điều kiện hoàn.</p>}
        </section>
    );
}
