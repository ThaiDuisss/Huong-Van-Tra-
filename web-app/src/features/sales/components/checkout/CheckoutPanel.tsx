import { useEffect, useState, type ReactNode } from "react";

import { AddDebtAction } from "./AddDebtAction";
import { CashReceivedInput } from "./CashReceivedInput";
import { CompletePaymentButton } from "./CompletePaymentButton";
import { CustomerSelector } from "./CustomerCard";
import { DiscountSelector } from "./DiscountSelector";
import { OrderTotals } from "./OrderTotals";
import { PaymentMethodGrid } from "./PaymentMethodGrid";
import { QuickCashButtons } from "./QuickCashButtons";
import { ReturnMoneyAction } from "./ReturnMoneyAction";
import { BankTransferDialog } from "../dialogs/BankTransferDialog";
import { CombinedPaymentDialog } from "../dialogs/CombinedPaymentDialog";
import type { PaymentStatusValue } from "../dialogs/PaymentStatus";
import type { PosCheckoutPaymentResult } from "../../services/posCheckout.service";
import type {
    SaleCustomer,
    SaleDiscountOption,
    SaleManualDiscount,
    SaleOrderType,
    SalePaymentMethod,
    SaleTotals
} from "../../types/sale.types";

/** Các phương thức cần máy chủ xác nhận giao dịch, không dùng được khi offline. */
const OFFLINE_DISABLED_METHODS: readonly SalePaymentMethod[] = [
    "BANK_TRANSFER",
    "COMBINED"
];

interface CheckoutPanelProps {
    discounts: SaleDiscountOption[];
    selectedDiscountId: string;
    totals: SaleTotals;
    manualOrderDiscount: SaleManualDiscount | null;
    cashReceived: number;
    changeAmount: number;
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    bankTransferReferenceCode?: string;
    bankTransferStatus: PaymentStatusValue;
    bankTransferPayment: PosCheckoutPaymentResult | null;
    bankTransferEventMessage?: string;
    checkoutLoading: boolean;
    checkoutError: string;
    checkoutSuccess: string;
    customers: SaleCustomer[];
    customersLoading: boolean;
    customersError: string;
    selectedCustomer: SaleCustomer | null;
    /** Số tiền phải thu ở lần này (tiền cọc của đơn chờ hàng). Mặc định là tổng tiền. */
    amountDue?: number;
    /** Thông báo đơn chờ hàng kèm lựa chọn thu đủ / đặt cọc. */
    backorderNotice?: ReactNode;
    /** Đơn chờ hàng đang chọn đặt cọc nhưng số tiền cọc chưa hợp lệ. */
    paymentBlocked?: boolean;
    /**
     * Mất kết nối máy chủ: khoá chuyển khoản/QR (cần webhook ngân hàng). Tiền mặt
     * vẫn hoàn tất được: đơn lưu trên máy, in hoá đơn mã tạm, đồng bộ sau.
     */
    offline?: boolean;
    onDiscountChange(discountId: string): void;
    onManualOrderDiscountChange(value: SaleManualDiscount | null): void;
    onCashReceivedChange(value: number): void;
    onPaymentMethodChange(value: SalePaymentMethod): void;
    onCustomerOpen(): void;
    onCustomerChange(customerId: string): void;
    onCustomerCreated(customer: SaleCustomer): void;
    onCustomerClear(): void;
    onBankTransferCancel(): void;
    onBankTransferRefresh(): void;
    onBankTransferStart(): void;
    onCompletePayment(): void;
}

export function CheckoutPanel({
    discounts,
    selectedDiscountId,
    totals,
    manualOrderDiscount,
    cashReceived,
    changeAmount,
    orderType,
    paymentMethod,
    bankTransferReferenceCode,
    bankTransferStatus,
    bankTransferPayment,
    bankTransferEventMessage = "",
    checkoutLoading,
    checkoutError,
    checkoutSuccess,
    customers,
    customersLoading,
    customersError,
    selectedCustomer,
    amountDue,
    backorderNotice,
    paymentBlocked = false,
    offline = false,
    onDiscountChange,
    onManualOrderDiscountChange,
    onCashReceivedChange,
    onPaymentMethodChange,
    onCustomerOpen,
    onCustomerChange,
    onCustomerCreated,
    onCustomerClear,
    onBankTransferCancel,
    onBankTransferRefresh,
    onBankTransferStart,
    onCompletePayment
}: CheckoutPanelProps) {
    const [bankTransferOpen, setBankTransferOpen] = useState(false);
    const [combinedPaymentOpen, setCombinedPaymentOpen] = useState(false);
    const isBankTransfer = paymentMethod === "BANK_TRANSFER";
    const isCombinedPayment = paymentMethod === "COMBINED";
    const cashDue = amountDue ?? totals.total;
    const canCompletePayment =
        totals.total > 0 &&
        orderType === "QUICK_SALE" &&
        (paymentMethod !== "CASH" || cashReceived >= cashDue) &&
        !isBankTransfer &&
        !paymentBlocked &&
        (!offline || paymentMethod === "CASH") &&
        !checkoutLoading;

    useEffect(() => {
        if (bankTransferStatus === "RECEIVED") {
            const timeoutId = window.setTimeout(() => {
                setBankTransferOpen(false);
            }, 0);

            return () => window.clearTimeout(timeoutId);
        }

        return undefined;
    }, [bankTransferStatus]);

    return (
        <section className="flex h-full min-h-0 flex-col gap-3 rounded-[20px] bg-pos-surface p-4 shadow-pos-panel">
            <CustomerSelector
                customers={customers}
                selectedCustomer={selectedCustomer}
                loading={customersLoading}
                error={customersError}
                onOpen={onCustomerOpen}
                onSelect={onCustomerChange}
                onCreated={onCustomerCreated}
                onClear={onCustomerClear}
            />

            <div className="grid gap-3">
                <OrderTotals
                    totals={totals}
                    manualOrderDiscount={manualOrderDiscount}
                    onManualOrderDiscountChange={onManualOrderDiscountChange}
                />
                <DiscountSelector
                    discounts={discounts}
                    selectedDiscountId={selectedDiscountId}
                    subtotal={totals.subtotal}
                    onChange={onDiscountChange}
                    onRemove={() => onDiscountChange("none")}
                />
            </div>

            {backorderNotice}

            {paymentMethod === "CASH" ? (
                <>
                    <CashReceivedInput
                        value={cashReceived}
                        changeAmount={changeAmount}
                        onChange={onCashReceivedChange}
                    />

                    <QuickCashButtons
                        total={cashDue}
                        onSelect={onCashReceivedChange}
                    />
                </>
            ) : null}

            {offline ? (
                <p
                    role="status"
                    className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800"
                >
                    Mất kết nối máy chủ. Thu tiền mặt vẫn được: đơn lưu trên máy này,
                    in hoá đơn mã tạm và tự đồng bộ khi có mạng. Chuyển khoản tạm khoá.
                </p>
            ) : null}

            <PaymentMethodGrid
                value={paymentMethod}
                disabledMethods={offline ? OFFLINE_DISABLED_METHODS : undefined}
                disabledReason="Cần kết nối mạng để nhận xác nhận từ ngân hàng"
                onChange={(value) => {
                    if (offline && OFFLINE_DISABLED_METHODS.includes(value)) {
                        return;
                    }

                    const alreadySelected = value === paymentMethod;
                    onPaymentMethodChange(value);

                    if (value === "BANK_TRANSFER") {
                        setBankTransferOpen(true);

                        if (!alreadySelected || !bankTransferPayment) {
                            onBankTransferStart();
                        }
                    }

                    if (value === "COMBINED") {
                        setCombinedPaymentOpen(true);
                    }
                }}
            />

            <div className="grid grid-cols-2 gap-3">
                <ReturnMoneyAction onClick={() => undefined} />
                <AddDebtAction onClick={() => undefined} />
            </div>

            {checkoutError ? (
                <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
                    {checkoutError}
                </p>
            ) : null}

            {checkoutSuccess ? (
                <p className="rounded-lg border border-hvt-green-200 bg-hvt-green-50 px-3 py-2 text-sm font-semibold text-hvt-green-900">
                    {checkoutSuccess}
                </p>
            ) : null}

            {isBankTransfer && bankTransferOpen ? (
                <div
                    className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
                    role="presentation"
                >
                    <BankTransferDialog
                        amount={totals.total}
                        referenceCode={bankTransferReferenceCode}
                        status={bankTransferStatus}
                        payment={bankTransferPayment}
                        eventMessage={bankTransferEventMessage}
                        loading={checkoutLoading && !bankTransferPayment}
                        error={checkoutError}
                        onClose={() => setBankTransferOpen(false)}
                        onCancel={onBankTransferCancel}
                        onRefreshStatus={onBankTransferRefresh}
                    />
                </div>
            ) : null}

            {isCombinedPayment && combinedPaymentOpen ? (
                <div
                    className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
                    role="presentation"
                >
                    <CombinedPaymentDialog
                        subtotal={totals.subtotal}
                        discountAmount={totals.discountAmount}
                        total={totals.total}
                        onClose={() => setCombinedPaymentOpen(false)}
                        onConfirm={() => setCombinedPaymentOpen(false)}
                    />
                </div>
            ) : null}

            <div className="mt-auto">
                <CompletePaymentButton
                    total={totals.total}
                    disabled={!canCompletePayment}
                    label={offline ? "Lưu đơn & in hoá đơn tạm" : undefined}
                    loading={checkoutLoading}
                    onClick={onCompletePayment}
                />
            </div>
        </section>
    );
}
