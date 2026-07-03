import { useState } from "react";

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
    SaleOrderType,
    SalePaymentMethod,
    SaleTotals
} from "../../types/sale.types";

interface CheckoutPanelProps {
    discounts: SaleDiscountOption[];
    selectedDiscountId: string;
    totals: SaleTotals;
    subtotalOverrideNote: string;
    cashReceived: number;
    changeAmount: number;
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    bankTransferReferenceCode?: string;
    bankTransferStatus: PaymentStatusValue;
    bankTransferPayment: PosCheckoutPaymentResult | null;
    checkoutLoading: boolean;
    checkoutError: string;
    checkoutSuccess: string;
    customers: SaleCustomer[];
    customersLoading: boolean;
    customersError: string;
    selectedCustomer: SaleCustomer | null;
    onDiscountChange(discountId: string): void;
    onSubtotalOverride(value: number, note: string): void;
    onSubtotalReset(): void;
    onCashReceivedChange(value: number): void;
    onPaymentMethodChange(value: SalePaymentMethod): void;
    onCustomerOpen(): void;
    onCustomerChange(customerId: string): void;
    onCustomerClear(): void;
    onBankTransferCancel(): void;
    onBankTransferConfirm(): void;
    onBankTransferRefresh(): void;
    onBankTransferStart(): void;
    onCompletePayment(): void;
}

export function CheckoutPanel({
    discounts,
    selectedDiscountId,
    totals,
    subtotalOverrideNote,
    cashReceived,
    changeAmount,
    orderType,
    paymentMethod,
    bankTransferReferenceCode,
    bankTransferStatus,
    bankTransferPayment,
    checkoutLoading,
    checkoutError,
    checkoutSuccess,
    customers,
    customersLoading,
    customersError,
    selectedCustomer,
    onDiscountChange,
    onSubtotalOverride,
    onSubtotalReset,
    onCashReceivedChange,
    onPaymentMethodChange,
    onCustomerOpen,
    onCustomerChange,
    onCustomerClear,
    onBankTransferCancel,
    onBankTransferConfirm,
    onBankTransferRefresh,
    onBankTransferStart,
    onCompletePayment
}: CheckoutPanelProps) {
    const [bankTransferOpen, setBankTransferOpen] = useState(false);
    const [combinedPaymentOpen, setCombinedPaymentOpen] = useState(false);
    const isBankTransfer = paymentMethod === "BANK_TRANSFER";
    const isCombinedPayment = paymentMethod === "COMBINED";
    const canCompletePayment =
        totals.total > 0 &&
        orderType === "QUICK_SALE" &&
        (paymentMethod !== "CASH" || cashReceived >= totals.total) &&
        (!isBankTransfer || bankTransferStatus === "RECEIVED") &&
        !checkoutLoading;

    return (
        <section className="flex h-full min-h-0 flex-col gap-3 rounded-[20px] bg-pos-surface p-4 shadow-pos-panel">
            <CustomerSelector
                customers={customers}
                selectedCustomer={selectedCustomer}
                loading={customersLoading}
                error={customersError}
                onOpen={onCustomerOpen}
                onSelect={onCustomerChange}
                onClear={onCustomerClear}
            />

            <div className="grid gap-3">
                <OrderTotals
                    totals={totals}
                    subtotalOverrideNote={subtotalOverrideNote}
                    onSubtotalOverride={onSubtotalOverride}
                    onSubtotalReset={onSubtotalReset}
                />
                <DiscountSelector
                    discounts={discounts}
                    selectedDiscountId={selectedDiscountId}
                    subtotal={totals.subtotal}
                    onChange={onDiscountChange}
                    onRemove={() => onDiscountChange("none")}
                />
            </div>

            {paymentMethod === "CASH" ? (
                <>
                    <CashReceivedInput
                        value={cashReceived}
                        changeAmount={changeAmount}
                        onChange={onCashReceivedChange}
                    />

                    <QuickCashButtons
                        total={totals.total}
                        onSelect={onCashReceivedChange}
                    />
                </>
            ) : null}

            <PaymentMethodGrid
                value={paymentMethod}
                onChange={(value) => {
                    onPaymentMethodChange(value);

                    if (value === "BANK_TRANSFER") {
                        setBankTransferOpen(true);
                        onBankTransferStart();
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
                        loading={checkoutLoading && !bankTransferPayment}
                        error={checkoutError}
                        onClose={() => setBankTransferOpen(false)}
                        onCancel={onBankTransferCancel}
                        onConfirmReceived={onBankTransferConfirm}
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
                    loading={checkoutLoading}
                    onClick={onCompletePayment}
                />
            </div>
        </section>
    );
}
