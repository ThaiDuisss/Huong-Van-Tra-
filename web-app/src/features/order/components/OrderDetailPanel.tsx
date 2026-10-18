import { RotateCcw, X } from "lucide-react";
import type { ReactNode } from "react";

import type {
    OrderDetailResponse,
    OrderItemResponse,
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
    onCreateReturn?: (orderId: string) => void;
    onClose?: () => void;
}

function toItemNumber(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return null;
    }

    const numericValue = typeof value === "number" ? value : Number(value);

    return Number.isFinite(numericValue) ? numericValue : null;
}

function getItemUnitPrice(item: OrderItemResponse) {
    return item.actualUnitPrice ?? item.originalUnitPrice ?? null;
}

function getItemLineTotal(item: OrderItemResponse) {
    if (item.lineTotal !== null && item.lineTotal !== undefined) {
        return item.lineTotal;
    }

    const quantity = toItemNumber(item.quantity);
    const unitPrice = toItemNumber(getItemUnitPrice(item));

    if (quantity === null || unitPrice === null) {
        return null;
    }

    return quantity * unitPrice;
}

function getItemQuantityText(item: OrderItemResponse) {
    return `x${formatQuantity(item.quantity)}`;
}

function hasValue(value?: ReactNode) {
    if (value === null || value === undefined) {
        return false;
    }

    const textValue = String(value).trim();

    return textValue !== "" && textValue !== "--";
}

export function OrderDetailPanel({
    order,
    summary,
    loading = false,
    error,
    onCreateReturn,
    onClose
}: OrderDetailPanelProps) {
    const displayOrder = order ?? summary;
    const customerPhone = displayOrder?.customerPhone;
    const staffName = displayOrder ? getStaffName(displayOrder) : "--";

    return (
        <aside
            className="min-h-0 overflow-auto rounded-xl border border-[#edf0f4] bg-white shadow-[0_16px_38px_rgb(31_53_43_/_0.05)]"
            aria-label="Chi tiết đơn hàng"
        >
            <div className="flex items-center justify-between gap-3 border-b border-[#edf0f4] px-7 py-5">
                <h2 className="m-0 text-xl font-black text-[#172317]">
                    Chi tiết đơn hàng
                </h2>
                <div className="flex items-center gap-2">
                    {displayOrder?.id && onCreateReturn ? (
                        <button
                            className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#dfe6ee] bg-white px-3 text-sm font-black text-[#172317] hover:bg-[#f4f7f5]"
                            onClick={() => onCreateReturn(displayOrder.id)}
                            type="button"
                        >
                            <RotateCcw size={16} aria-hidden="true" />
                            Tạo phiếu hoàn
                        </button>
                    ) : null}
                    {onClose ? (
                        <button
                            aria-label="Đóng chi tiết đơn hàng"
                            className="grid size-9 place-items-center rounded-lg border-0 bg-transparent text-[#536078] hover:bg-[#f4f7f5]"
                            onClick={onClose}
                            type="button"
                        >
                            <X size={18} aria-hidden="true" />
                        </button>
                    ) : null}
                </div>
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
                    <section className="border-b border-[#edf0f4] px-7 py-5">
                        <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                            Thông tin đơn
                        </h3>
                        <div className="grid grid-cols-1 gap-x-10 gap-y-4 sm:grid-cols-2">
                            <InfoRow label="Mã đơn hàng" value={getOrderCode(displayOrder)} />
                            <InfoRow
                                label="Trạng thái đơn"
                                value={
                                    <OrderStatusBadge
                                        status={displayOrder.status}
                                        label={getOrderStatusText(displayOrder)}
                                    />
                                }
                            />
                            <InfoRow
                                label="Khách hàng"
                                value={getCustomerName(displayOrder)}
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
                            {hasValue(customerPhone) ? (
                                <InfoRow label="SĐT" value={customerPhone} />
                            ) : null}
                            {hasValue(staffName) ? (
                                <InfoRow label="Nhân viên" value={staffName} />
                            ) : null}
                        </div>
                    </section>

                    <section className="border-b border-[#edf0f4] px-7 py-5">
                        <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                            Sản phẩm
                        </h3>
                        {order?.items?.length ? (
                            <ul className="m-0 grid gap-3 p-0">
                                {order.items.map((item, index) => {
                                    const unitPrice = getItemUnitPrice(item);
                                    const lineTotal = getItemLineTotal(item);
                                    const itemMeta = [
                                        item.skuSnapshot,
                                        item.variantNameSnapshot,
                                        `Đơn giá: ${formatCurrency(unitPrice)}`
                                    ].filter(Boolean);

                                    return (
                                        <li
                                            className="grid list-none grid-cols-[minmax(0,1fr)_auto] gap-4 rounded-lg border border-[#edf0f4] bg-[#f8faf9] px-5 py-4 text-sm text-[#172317]"
                                            key={item.id ?? `${item.productVariantId}-${index}`}
                                        >
                                            <div className="min-w-0">
                                                <div className="truncate font-black">
                                                    {item.productNameSnapshot || "--"}
                                                </div>
                                                <div className="mt-1.5 truncate font-bold text-[#536078]">
                                                    {itemMeta.join(" · ")}
                                                </div>
                                            </div>
                                            <div className="shrink-0 self-start text-right">
                                                <strong className="block whitespace-nowrap font-black text-[#172317]">
                                                    {formatCurrency(lineTotal)}
                                                </strong>
                                                <span className="mt-1.5 inline-flex rounded-md bg-[#e9f8ed] px-2.5 py-1 text-xs font-black text-[#07883a]">
                                                    SL: {getItemQuantityText(item)}
                                                </span>
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        ) : (
                            <p className="m-0 text-sm font-bold text-[#536078]">
                                Chưa có dữ liệu sản phẩm
                            </p>
                        )}
                    </section>

                    <section className="px-7 py-5">
                        <h3 className="mb-4 mt-0 text-base font-black text-[#172317]">
                            Lịch sử trạng thái
                        </h3>
                        {order?.statusHistories?.length ? (
                            <ol className="m-0 grid gap-4 p-0">
                                {order.statusHistories.map((history, index) => (
                                    <li
                                        className="grid list-none gap-1.5 border-l-2 border-[#07883a] pl-4 text-sm"
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
                            <p className="m-0 text-sm font-bold text-[#536078]">
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
        <div className="grid gap-2.5 px-7 py-8 text-base">
            <strong className="text-[#172317]">{title}</strong>
            <span className="font-semibold text-[#536078]">{description}</span>
        </div>
    );
}
