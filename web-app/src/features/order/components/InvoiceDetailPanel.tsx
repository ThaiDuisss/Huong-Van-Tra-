import { useEffect, useState, type ReactNode } from "react";
import { X } from "lucide-react";

import { IconButton } from "../../../components/ui/Button";
import type {
    OrderDetailResponse,
    OrderSummaryResponse
} from "../types/order.types";
import {
    buildInvoiceCode,
    formatCurrency,
    formatDateTime,
    getCustomerName,
    getOrderCode,
    getOrderTypeText,
    getPaymentStatusText,
    getInvoicePaymentStatus,
    getInvoicePaymentStatusText,
    getRecipientName,
    getRecipientPhone,
    getStaffName
} from "../utils/order-formatters";
import { PaymentStatusBadge } from "./PaymentStatusBadge";
import { getInvoiceRefundedAmount } from "../services/invoice-refund-summary";

interface InvoiceDetailPanelProps {
    order: OrderDetailResponse | null;
    summary?: OrderSummaryResponse | null;
    rowIndex?: number;
    loading?: boolean;
    error?: string | null;
    onClose?: () => void;
}

const PAYMENT_METHOD_LABELS: Record<string, string> = {
    BANK_TRANSFER: "Chuyển khoản",
    CARD: "Thẻ",
    CASH: "Tiền mặt",
    DEBT: "Công nợ",
    MOMO: "MoMo",
    VNPAY: "VNPay"
};

function hasValue(value: unknown) {
    if (value === null || value === undefined) {
        return false;
    }

    const textValue = String(value).trim();

    return (
        textValue !== "" &&
        textValue !== "--" &&
        textValue !== "Chưa có dữ liệu"
    );
}

function getPaymentMethodLabel(method?: string | null) {
    if (!hasValue(method)) {
        return "--";
    }

    const normalizedMethod = String(method).trim();

    return PAYMENT_METHOD_LABELS[normalizedMethod] ?? normalizedMethod;
}

export function InvoiceDetailPanel({
    order,
    summary,
    rowIndex = 0,
    loading = false,
    error,
    onClose
}: InvoiceDetailPanelProps) {
    const displayOrder = order ?? summary;
    const customerPhone = displayOrder?.customerPhone;
    const staffName = displayOrder ? getStaffName(displayOrder) : "--";
    const branchName = order?.branchName;
    const paymentMethodName = order?.paymentMethodName;
    const paymentMethod = getPaymentMethodLabel(order?.paymentMethod);
    const shippingProvider = order?.shippingProvider;
    const recipientName = getRecipientName(order);
    const shippingAddress = order?.shippingAddress;
    const recipientPhone = getRecipientPhone(order);
    const hasShippingInfo = [
        shippingProvider,
        recipientName,
        shippingAddress,
        recipientPhone
    ].some(hasValue);

    return (
        <aside
            className="flex min-h-0 flex-col overflow-auto p-7"
            aria-label="Thông tin hóa đơn"
        >
            <div className="flex items-center justify-between gap-3 border-b border-[#edf0f4] pb-5">
                <h2 className="m-0 text-xl font-black text-[#172317]">
                    Thông tin hóa đơn
                </h2>
                {onClose ? (
                    <IconButton aria-label="Đóng thông tin hóa đơn" onClick={onClose}>
                        <X size={18} aria-hidden="true" />
                    </IconButton>
                ) : null}
            </div>

            {!displayOrder && !loading && !error ? (
                <PanelMessage
                    title="Chưa chọn hóa đơn"
                    description="Thông tin hóa đơn sẽ hiển thị tại đây."
                />
            ) : null}

            {loading ? (
                <PanelMessage
                    title="Đang tải hóa đơn"
                    description="Vui lòng chờ trong giây lát."
                />
            ) : null}

            {error ? (
                <PanelMessage title="Không tải được hóa đơn" description={error} />
            ) : null}

            {displayOrder && !loading && !error ? (
                <>
                    <section className="border-b border-[#edf0f4] py-5">
                        <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                            Thông tin chung
                        </h3>
                        <div className="grid grid-cols-1 gap-x-10 gap-y-4 sm:grid-cols-2">
                            <InfoRow
                                label="Mã hóa đơn"
                                value={buildInvoiceCode(displayOrder, rowIndex)}
                            />
                            <InfoRow label="Mã đơn hàng" value={getOrderCode(displayOrder)} />
                            <InfoRow
                                label="Khách hàng"
                                value={getCustomerName(displayOrder)}
                            />
                            <InfoRow
                                label="Ngày lập"
                                value={formatDateTime(displayOrder.createdAt)}
                            />
                            <InfoRow label="Kênh bán" value={getOrderTypeText(displayOrder)} />
                            {hasValue(customerPhone) ? (
                                <InfoRow label="SĐT" value={customerPhone} />
                            ) : null}
                            {hasValue(staffName) ? (
                                <InfoRow label="Nhân viên bán" value={staffName} />
                            ) : null}
                            {hasValue(branchName) ? (
                                <InfoRow label="Chi nhánh" value={branchName} />
                            ) : null}
                        </div>
                    </section>

                    <section className="border-b border-[#edf0f4] py-5">
                        <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                            Thanh toán
                        </h3>
                        <div className="grid grid-cols-1 gap-x-10 gap-y-4 sm:grid-cols-2">
                            <InfoRow
                                label="Trạng thái"
                                value={
                                    <PaymentStatusBadge
                                        status={getInvoicePaymentStatus(displayOrder)}
                                        label={getInvoicePaymentStatusText(displayOrder)}
                                    />
                                }
                            />
                            <InfoRow
                                label="Phương thức TT"
                                value={paymentMethod}
                            />
                            <InfoRow
                                label="Tổng tiền"
                                value={formatCurrency(
                                    order?.originalAmount ?? displayOrder.finalAmount
                                )}
                            />
                            <InfoRow
                                label="Giảm giá"
                                value={formatCurrency(order?.discountAmount)}
                            />
                            <InfoRow
                                label="Khách trả"
                                value={formatCurrency(order?.paidAmount)}
                            />
                            {displayOrder.paymentStatus === "PARTIALLY_REFUNDED" || displayOrder.paymentStatus === "REFUNDED" ? (
                                <>
                                    <InfoRow label="Hoàn tiền" value={getPaymentStatusText(displayOrder)} />
                                    <InvoiceRefundDetails key={`${displayOrder.id}:${displayOrder.paymentStatus}`} order={order} />
                                </>
                            ) : null}
                            {hasValue(order?.debtAmount) ? (
                                <InfoRow
                                    label="Công nợ"
                                    value={formatCurrency(order?.debtAmount)}
                                />
                            ) : null}
                            {hasValue(paymentMethodName) ? (
                                <InfoRow
                                    label="Hình thức TT"
                                    value={paymentMethodName}
                                />
                            ) : null}
                        </div>
                    </section>

                    {hasShippingInfo ? (
                        <section className="py-5">
                            <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                                Giao hàng
                            </h3>
                            <div className="grid grid-cols-1 gap-x-10 gap-y-4 sm:grid-cols-2">
                                {hasValue(shippingProvider) ? (
                                    <InfoRow
                                        label="Đơn vị VC"
                                        value={shippingProvider}
                                    />
                                ) : null}
                                {hasValue(recipientName) ? (
                                    <InfoRow
                                        label="Người nhận"
                                        value={recipientName}
                                    />
                                ) : null}
                                {hasValue(shippingAddress) ? (
                                    <InfoRow
                                        label="Địa chỉ nhận"
                                        value={shippingAddress}
                                    />
                                ) : null}
                                {hasValue(recipientPhone) ? (
                                    <InfoRow
                                        label="SĐT nhận"
                                        value={recipientPhone}
                                    />
                                ) : null}
                            </div>
                        </section>
                    ) : null}
                </>
            ) : null}
        </aside>
    );
}

function InvoiceRefundDetails({ order }: { order: OrderDetailResponse | null }) {
    const [refunded, setRefunded] = useState<number | null>(null);
    const [failed, setFailed] = useState(false);
    const orderId = order?.id;
    useEffect(() => {
        if (!orderId) return;
        let active = true;
        void getInvoiceRefundedAmount(orderId)
            .then(amount => { if (active) setRefunded(amount); })
            .catch(() => { if (active) setFailed(true); });
        return () => { active = false; };
    }, [orderId]);
    const rawPaid = order?.paidAmount;
    const paid = rawPaid == null || String(rawPaid).trim() === "" ? NaN : Number(rawPaid);
    const net = refunded !== null && Number.isFinite(paid) && paid >= refunded ? paid - refunded : null;
    return <>
        <InfoRow label="Đã hoàn" value={formatCurrency(refunded)} />
        <InfoRow label="Thực nhận sau hoàn" value={formatCurrency(net)} />
        {failed ? <p role="alert" className="text-sm text-red-700">Chưa xác minh được số tiền hoàn.</p> : null}
    </>;
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-5 text-sm text-[#172317]">
            <span className="font-bold text-[#536078]">{label}</span>
            <strong className="min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-right font-black">
                {value || "--"}
            </strong>
        </div>
    );
}

function PanelMessage({
    title,
    description
}: {
    title: string;
    description: string;
}) {
    return (
        <div className="grid gap-2.5 py-8 text-base">
            <strong className="text-[#172317]">{title}</strong>
            <span className="font-semibold text-[#536078]">{description}</span>
        </div>
    );
}
