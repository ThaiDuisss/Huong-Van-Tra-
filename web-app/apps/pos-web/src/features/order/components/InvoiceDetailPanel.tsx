import type { ReactNode } from "react";
import { X } from "lucide-react";

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
    getRecipientName,
    getRecipientPhone,
    getStaffName
} from "../utils/order-formatters";
import { PaymentStatusBadge } from "./PaymentStatusBadge";

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
            className="flex min-h-0 flex-col overflow-auto rounded-xl border border-[#edf0f4] bg-white p-4 shadow-[0_16px_38px_rgb(31_53_43_/_0.05)]"
            aria-label="Thông tin hóa đơn"
        >
            <div className="flex items-center justify-between gap-3 border-b border-[#edf0f4] pb-3">
                <h2 className="m-0 text-[17px] font-black text-[#172317]">
                    Thông tin hóa đơn
                </h2>
                {onClose ? (
                    <button
                        type="button"
                        className="grid size-8 place-items-center rounded-lg text-[#536078] hover:bg-[#f4f6f8]"
                        aria-label="Đóng thông tin hóa đơn"
                        onClick={onClose}
                    >
                        <X size={16} aria-hidden="true" />
                    </button>
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
                    <section className="border-b border-[#edf0f4] py-3.5">
                        <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                            Thông tin chung
                        </h3>
                        <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
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

                    <section className="border-b border-[#edf0f4] py-3.5">
                        <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                            Thanh toán
                        </h3>
                        <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
                            <InfoRow
                                label="Trạng thái"
                                value={
                                    <PaymentStatusBadge
                                        status={displayOrder.paymentStatus}
                                        label={getPaymentStatusText(displayOrder)}
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
                        <section className="py-3.5">
                            <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                                Giao hàng
                            </h3>
                            <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
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

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-4 text-xs text-[#172317]">
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
        <div className="grid gap-2 py-6 text-sm">
            <strong className="text-[#172317]">{title}</strong>
            <span className="font-semibold text-[#536078]">{description}</span>
        </div>
    );
}
