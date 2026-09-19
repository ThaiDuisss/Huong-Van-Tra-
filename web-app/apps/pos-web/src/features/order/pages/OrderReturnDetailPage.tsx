import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ReturnBusinessProgress } from "../components/ReturnBusinessProgress";
import {
    getCustomerReturnQcSummary,
    getOrderRefundDetail,
    getOrderReturnDetail,
    getOrderReturnTimeline
} from "../services/order-return.api";
import type {
    CustomerReturnQcSummaryResponse,
    OrderRefundDetailResponse,
    OrderReturnResponse,
    OrderReturnTimelineResponse
} from "../types/order-return.types";
import {
    formatReturnQuantity,
    getCustomerReturnQcStatusLabel,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    getReturnStatusLabel
} from "../utils/order-return.utils";
import { formatCurrency, formatDateTime } from "../utils/order-formatters";
import styles from "./OrderReturnDetailPage.module.css";

const REFUND_STATUS_LABELS: Record<string, string> = {
    FAILED: "Hoàn tiền thất bại",
    NOT_READY: "Chờ xử lý",
    PENDING: "Chờ hoàn tiền",
    REFUNDED: "Đã hoàn tiền",
    NOT_REQUIRED: "Không hoàn tiền"
};

export function OrderReturnDetailPage() {
    const { returnId } = useParams();
    const navigate = useNavigate();
    const [detail, setDetail] = useState<OrderReturnResponse | null>(null);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [refundDetail, setRefundDetail] = useState<OrderRefundDetailResponse | null>(null);
    const [timeline, setTimeline] = useState<OrderReturnTimelineResponse[]>([]);
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
                        // Fetch QC Summary in background
                        getCustomerReturnQcSummary(returnId)
                            .then((qc) => {
                                if (!ignore) setQcSummary(qc);
                            })
                            .catch(() => {});
                        // Fetch Refund Summary in background
                        getOrderRefundDetail(returnId)
                            .then((rf) => {
                                if (!ignore) setRefundDetail(rf);
                            })
                            .catch(() => {});
                        getOrderReturnTimeline(returnId)
                            .then((entries) => {
                                if (!ignore) setTimeline(entries ?? []);
                            })
                            .catch(() => {});
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

    const qcLinesByItemId = new Map(
        (qcSummary?.lines ?? []).map((line) => [line.returnItemId, line])
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
                            label="Trạng thái hàng trả"
                            value={getReturnStatusLabel(detail.status)}
                        />
                        <Info
                            label="Kiểm kho (QC)"
                            value={
                                qcSummary?.qcStatus
                                    ? getCustomerReturnQcStatusLabel(qcSummary.qcStatus)
                                    : "Chưa kiểm"
                            }
                        />
                        <Info
                            label="Hoàn tiền"
                            value={
                                refundDetail?.refundStatus
                                    ? REFUND_STATUS_LABELS[refundDetail.refundStatus] ?? refundDetail.refundStatus
                                    : detail.refundRequired === false
                                    ? "Không hoàn tiền"
                                    : "Chờ xử lý"
                            }
                        />
                        <Info
                            label="Ngày tạo"
                            value={formatDateTime(detail.createdAt)}
                        />
                        <Info
                            label="Tổng số lượng"
                            value={formatReturnQuantity(totalQuantity)}
                        />
                        {refundDetail?.refundAmount != null && Number(refundDetail.refundAmount) > 0 ? (
                            <Info
                                label="Số tiền hoàn"
                                value={formatCurrency(refundDetail.refundAmount)}
                            />
                        ) : (
                            <Info
                                label="Chính sách hoàn tiền"
                                value={
                                    detail.refundRequired === false
                                        ? "Không hoàn tiền"
                                        : "Có hoàn tiền"
                                }
                            />
                        )}
                        {detail.note?.trim() ? (
                            <Info label="Ghi chú" value={detail.note} wide />
                        ) : null}
                    </section>

                    <ReturnBusinessProgress
                        qcSummary={qcSummary}
                        returnData={detail}
                        timeline={timeline}
                    />

                    <section className={styles.card}>
                        <h2>Sản phẩm hoàn</h2>
                        <div className={styles.lines}>
                            {(detail.items ?? []).map((item, index) => {
                                const qcLine = item.id ? qcLinesByItemId.get(item.id) : null;
                                return (
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
                                            label="Số lượng yêu cầu"
                                            value={formatReturnQuantity(item.quantity)}
                                        />
                                        <Info
                                            label="Lý do & Tình trạng"
                                            value={`${getReturnReasonLabel(item.reason)} (${getReturnConditionLabel(item.condition)})`}
                                        />
                                        <Info
                                            label="Kiểm hàng (QC)"
                                            value={
                                                qcLine
                                                    ? `${getCustomerReturnQcStatusLabel(qcLine.status)} (${formatReturnQuantity(qcLine.postedQcQuantity)}/${formatReturnQuantity(item.quantity)})`
                                                    : "Chưa kiểm"
                                            }
                                        />
                                        {item.note?.trim() ? (
                                            <p>{item.note}</p>
                                        ) : null}
                                    </article>
                                );
                            })}
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
