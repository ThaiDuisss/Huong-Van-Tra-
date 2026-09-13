import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { getOrderReturnDetail } from "../services/order-return.api";
import type { OrderReturnResponse } from "../types/order-return.types";
import {
    formatReturnQuantity,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    getReturnStatusLabel
} from "../utils/order-return.utils";
import { formatDateTime } from "../utils/order-formatters";
import styles from "./OrderReturnDetailPage.module.css";

export function OrderReturnDetailPage() {
    const { returnId } = useParams();
    const navigate = useNavigate();
    const [detail, setDetail] = useState<OrderReturnResponse | null>(null);
    const [loading, setLoading] = useState(Boolean(returnId));
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!returnId) {
            return;
        }

        let ignore = false;

        const timerId = window.setTimeout(() => {
            setLoading(true);
            setError(null);
            getOrderReturnDetail(returnId)
                .then((response) => {
                    if (!ignore) {
                        setDetail(response);
                    }
                })
                .catch((loadError: unknown) => {
                    if (!ignore) {
                        setDetail(null);
                        setError(getReturnErrorMessage(loadError));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [returnId]);

    const routeError = !returnId ? "Không tìm thấy mã phiếu hoàn." : error;
    const totalQuantity = (detail?.items ?? []).reduce(
        (total, item) => total + Number(item.quantity ?? 0),
        0
    );

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <button
                    className={styles.backButton}
                    onClick={() => navigate("/system/orders/returns")}
                    type="button"
                >
                    <ArrowLeft size={18} aria-hidden="true" />
                    Quay lại
                </button>
                <div>
                    <h1>{detail ? getReturnCode(detail) : "Chi tiết phiếu hoàn"}</h1>
                    <p>Đơn hàng: {detail?.orderCode ?? detail?.orderId ?? "--"}</p>
                </div>
            </header>

            {returnId && loading ? (
                <PanelMessage
                    title="Đang tải phiếu hoàn"
                    description="Vui lòng chờ trong giây lát."
                />
            ) : null}

            {(!returnId || !loading) && routeError ? (
                <PanelMessage
                    title="Không tải được phiếu hoàn"
                    description={routeError}
                />
            ) : null}

            {!loading && detail ? (
                <>
                    <section className={styles.card}>
                        <Info
                            label="Mã phiếu"
                            value={getReturnCode(detail)}
                        />
                        <Info
                            label="Trạng thái"
                            value={getReturnStatusLabel(detail.status)}
                        />
                        <Info
                            label="Ngày tạo"
                            value={formatDateTime(detail.createdAt)}
                        />
                        <Info
                            label="Tổng số lượng"
                            value={formatReturnQuantity(totalQuantity)}
                        />
                        {detail.note?.trim() ? (
                            <Info label="Ghi chú" value={detail.note} wide />
                        ) : null}
                    </section>

                    <section className={styles.card}>
                        <h2>Sản phẩm hoàn</h2>
                        <div className={styles.lines}>
                            {(detail.items ?? []).map((item, index) => (
                                <article
                                    className={styles.line}
                                    key={item.id ?? item.orderItemId ?? index}
                                >
                                    <div>
                                        <strong>
                                            {item.productNameSnapshot || "Sản phẩm"}
                                        </strong>
                                        <span>
                                            SKU: {item.skuSnapshot || "--"} ·{" "}
                                            {item.variantNameSnapshot ||
                                                "Không có phân loại"}
                                        </span>
                                    </div>
                                    <Info
                                        label="Số lượng"
                                        value={formatReturnQuantity(item.quantity)}
                                    />
                                    <Info
                                        label="Lý do"
                                        value={getReturnReasonLabel(item.reason)}
                                    />
                                    <Info
                                        label="Tình trạng"
                                        value={getReturnConditionLabel(
                                            item.condition
                                        )}
                                    />
                                    {item.note?.trim() ? (
                                        <p>{item.note}</p>
                                    ) : null}
                                </article>
                            ))}
                        </div>
                    </section>
                </>
            ) : null}
        </section>
    );
}

function Info({
    label,
    value,
    wide = false
}: {
    label: string;
    value: string;
    wide?: boolean;
}) {
    return (
        <div className={`${styles.info} ${wide ? styles.wideInfo : ""}`}>
            <span>{label}</span>
            <strong>{value || "--"}</strong>
        </div>
    );
}

function PanelMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <div className={styles.panelMessage}>
            <strong>{title}</strong>
            <span>{description}</span>
        </div>
    );
}
