import { X } from "lucide-react";
import type { ReactNode } from "react";

import type {
    OrderDetailResponse,
    OrderSummaryResponse
} from "../types/order.types";
import {
    formatCurrency,
    formatDateTime,
    formatQuantity,
    getCustomerName,
    getHistoryTargetStatus,
    getOrderCode,
    getOrderStatusText,
    getOrderTypeText,
    getPaymentStatusText,
    getStaffName
} from "../utils/order-formatters";
import { OrderStatusBadge } from "./OrderStatusBadge";
import { PaymentStatusBadge } from "./PaymentStatusBadge";

interface OrderDetailPanelProps {
    order: OrderDetailResponse | null;
    summary?: OrderSummaryResponse | null;
    loading?: boolean;
    error?: string | null;
    onClose?: () => void;
}

export function OrderDetailPanel({
    order,
    summary,
    loading = false,
    error,
    onClose
}: OrderDetailPanelProps) {
    const displayOrder = order ?? summary;

    return (
        <aside
            className="min-h-0 overflow-auto rounded-xl border border-[#edf0f4] bg-white shadow-[0_16px_38px_rgb(31_53_43_/_0.05)]"
            aria-label="Chi tiết đơn hàng"
        >
            <div className="flex items-center justify-between gap-3 border-b border-[#edf0f4] px-4 py-3.5">
                <h2 className="m-0 text-[17px] font-black text-[#172317]">
                    Chi tiết đơn hàng
                </h2>
                {onClose ? (
                    <button
                        aria-label="Đóng chi tiết đơn hàng"
                        className="grid size-8 place-items-center rounded-lg border-0 bg-transparent text-[#536078] hover:bg-[#f4f7f5]"
                        onClick={onClose}
                        type="button"
                    >
                        <X size={17} aria-hidden="true" />
                    </button>
                ) : null}
            </div>

            {!displayOrder && !loading && !error ? (
                <PanelMessage
                    title="Chưa chọn đơn hàng"
                    description="Thông tin chi tiết sẽ hiển thị tại đây."
                />
            ) : null}

            {loading ? (
                <PanelMessage
                    title="Đang tải chi tiết"
                    description="Vui lòng chờ trong giây lát."
                />
            ) : null}

            {error ? (
                <PanelMessage title="Không tải được chi tiết" description={error} />
            ) : null}

            {displayOrder && !loading && !error ? (
                <>
                    <section className="border-b border-[#edf0f4] px-4 py-3.5">
                        <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                            Thông tin đơn
                        </h3>
                        <InfoRow label="Mã đơn hàng" value={getOrderCode(displayOrder)} />
                        <InfoRow
                            label="Khách hàng"
                            value={getCustomerName(displayOrder)}
                        />
                        <InfoRow
                            label="SĐT"
                            value={displayOrder.customerPhone || "--"}
                        />
                        <InfoRow
                            label="Trạng thái"
                            value={
                                <OrderStatusBadge
                                    status={displayOrder.status}
                                    label={getOrderStatusText(displayOrder)}
                                />
                            }
                        />
                        <InfoRow
                            label="Giá trị"
                            value={formatCurrency(displayOrder.finalAmount)}
                        />
                        <InfoRow
                            label="Ngày đặt"
                            value={formatDateTime(displayOrder.createdAt)}
                        />
                        <InfoRow
                            label="Nghiệp vụ"
                            value={getOrderTypeText(displayOrder)}
                        />
                        <InfoRow
                            label="Nhân viên"
                            value={getStaffName(displayOrder)}
                        />
                        <InfoRow
                            label="Thanh toán"
                            value={
                                <PaymentStatusBadge
                                    status={displayOrder.paymentStatus}
                                    label={getPaymentStatusText(displayOrder)}
                                />
                            }
                        />
                    </section>

                    <section className="border-b border-[#edf0f4] px-4 py-3.5">
                        <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                            Sản phẩm
                        </h3>
                        {order?.items?.length ? (
                            <ul className="m-0 grid gap-2.5 p-0">
                                {order.items.map((item, index) => (
                                    <li
                                        className="list-none rounded-lg bg-[#f8faf9] px-3 py-2 text-xs text-[#172317]"
                                        key={item.id ?? `${item.productVariantId}-${index}`}
                                    >
                                        <div className="flex items-start justify-between gap-3 font-black">
                                            <span className="min-w-0 truncate">
                                                {item.productNameSnapshot || "--"}
                                            </span>
                                            <span className="shrink-0">
                                                x{formatQuantity(item.quantity)}
                                            </span>
                                        </div>
                                        {item.variantNameSnapshot ? (
                                            <div className="mt-1 truncate font-bold text-[#536078]">
                                                {item.variantNameSnapshot}
                                            </div>
                                        ) : null}
                                    </li>
                                ))}
                            </ul>
                        ) : (
                            <p className="m-0 text-xs font-bold text-[#536078]">
                                Chưa có dữ liệu sản phẩm
                            </p>
                        )}
                    </section>

                    <section className="px-4 py-3.5">
                        <h3 className="mb-3 mt-0 text-[13px] font-black text-[#172317]">
                            Lịch sử trạng thái
                        </h3>
                        {order?.statusHistories?.length ? (
                            <ol className="m-0 grid gap-3 p-0">
                                {order.statusHistories.map((history, index) => (
                                    <li
                                        className="grid list-none gap-1 border-l-2 border-[#07883a] pl-3 text-xs"
                                        key={history.id ?? index}
                                    >
                                        <strong className="text-[#172317]">
                                            {getHistoryTargetStatus(history)}
                                        </strong>
                                        {history.note ? (
                                            <span className="font-bold text-[#536078]">
                                                {history.note}
                                            </span>
                                        ) : null}
                                        <span className="font-bold text-[#7b8796]">
                                            {formatDateTime(history.createdAt)}
                                        </span>
                                    </li>
                                ))}
                            </ol>
                        ) : (
                            <p className="m-0 text-xs font-bold text-[#536078]">
                                Chưa có lịch sử trạng thái
                            </p>
                        )}
                    </section>
                </>
            ) : null}
        </aside>
    );
}

function InfoRow({
    label,
    value
}: {
    label: string;
    value: ReactNode;
}) {
    return (
        <div className="mt-2.5 grid grid-cols-[92px_minmax(0,1fr)] gap-2.5 text-xs text-[#172317]">
            <span className="font-bold text-[#536078]">{label}</span>
            <strong className="min-w-0 overflow-hidden text-ellipsis whitespace-nowrap font-black">
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
        <div className="grid gap-2 px-4 py-6 text-sm">
            <strong className="text-[#172317]">{title}</strong>
            <span className="font-semibold text-[#536078]">{description}</span>
        </div>
    );
}
