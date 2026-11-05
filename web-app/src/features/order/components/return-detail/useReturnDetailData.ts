import { useCallback, useEffect, useState } from "react";

import { customerReturnApi } from "../../../inventory/services/customerReturn.api";
import type { CustomerReturnInspection } from "../../../inventory/types/customer-return-inspection.types";
import {
    getCustomerReturnQcSummary,
    getOrderRefundDetail,
    getOrderReturnDetail,
    getOrderReturnTimeline
} from "../../services/order-return.api";
import type {
    CustomerReturnQcSummaryResponse,
    OrderRefundDetailResponse,
    OrderReturnListRow,
    OrderReturnTimelineResponse
} from "../../types/order-return.types";
import { getReturnErrorMessage } from "../../utils/order-return.utils";

export interface ReturnDetailData {
    /** Phiếu hoàn đã gộp dữ liệu dòng danh sách + chi tiết mới nhất. */
    returnItem: OrderReturnListRow;
    qcSummary: CustomerReturnQcSummaryResponse | null;
    qcSummaryError: string | null;
    refundDetail: OrderRefundDetailResponse | null;
    timeline: OrderReturnTimelineResponse[];
    inspections: CustomerReturnInspection[];
    loading: boolean;
    error: string | null;
    reload: () => void;
}

/** Tải toàn bộ dữ liệu cho hộp thoại chi tiết phiếu hoàn (dùng chung cho Đơn hàng và Kho). */
export function useReturnDetailData(baseRow: OrderReturnListRow): ReturnDetailData {
    const [detail, setDetail] = useState<OrderReturnListRow | null>(null);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [qcSummaryError, setQcSummaryError] = useState<string | null>(null);
    const [refundDetail, setRefundDetail] = useState<OrderRefundDetailResponse | null>(null);
    const [timeline, setTimeline] = useState<OrderReturnTimelineResponse[]>([]);
    const [inspections, setInspections] = useState<CustomerReturnInspection[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const returnId = baseRow.id;
    const returnCode = baseRow.returnCode ?? "";

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError(null);

            const detailRequest = getOrderReturnDetail(returnId);
            const qcRequest = getCustomerReturnQcSummary(returnId)
                .then((summary) => ({ summary, error: null as string | null }))
                .catch((qcError: unknown) => ({
                    summary: null,
                    error: getReturnErrorMessage(qcError, "Không thể tải thông tin kiểm hàng")
                }));
            const refundRequest = getOrderRefundDetail(returnId).catch(() => null);
            const timelineRequest = getOrderReturnTimeline(returnId).catch(() => []);
            const inspectionRequest = returnCode
                ? customerReturnApi.listInspections({
                    orderCode: "",
                    page: 0,
                    returnCode,
                    size: 20,
                    status: "ALL"
                })
                    .then((page) => (page?.content ?? []).filter((inspection) => inspection.returnId === returnId))
                    .catch(() => [])
                : Promise.resolve([]);

            Promise.all([detailRequest, qcRequest, refundRequest, timelineRequest, inspectionRequest])
                .then(([freshDetail, qc, refund, entries, inspectionList]) => {
                    if (!active) return;
                    setDetail(freshDetail);
                    setQcSummary(qc.summary);
                    setQcSummaryError(qc.error);
                    setRefundDetail(refund);
                    setTimeline(entries ?? []);
                    setInspections(inspectionList);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });

        return () => {
            active = false;
        };
    }, [reloadKey, returnCode, returnId]);

    const reload = useCallback(() => setReloadKey((key) => key + 1), []);

    return {
        returnItem: {
            ...baseRow,
            ...detail,
            // Chi tiết có thể thiếu các trường tổng hợp chỉ có ở dòng danh sách.
            orderCode: detail?.orderCode ?? baseRow.orderCode,
            customerName: detail?.customerName ?? baseRow.customerName,
            customerPhone: detail?.customerPhone ?? baseRow.customerPhone,
            items: detail?.items ?? baseRow.items
        },
        qcSummary,
        qcSummaryError,
        refundDetail,
        timeline,
        inspections,
        loading,
        error,
        reload
    };
}
