import { useMemo, useState } from "react";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { CartNote } from "../components/cart/CartNote";
import { OrderTypeSelector } from "../components/cart/OrderTypeSelector";
import { AddDebtAction } from "../components/checkout/AddDebtAction";
import { CashReceivedInput } from "../components/checkout/CashReceivedInput";
import { CompletePaymentButton } from "../components/checkout/CompletePaymentButton";
import { CustomerSelector } from "../components/checkout/CustomerCard";
import { DiscountSelector } from "../components/checkout/DiscountSelector";
import { OrderTotals } from "../components/checkout/OrderTotals";
import { PaymentMethodGrid } from "../components/checkout/PaymentMethodGrid";
import { QuickCashButtons } from "../components/checkout/QuickCashButtons";
import { ReturnMoneyAction } from "../components/checkout/ReturnMoneyAction";
import { BankTransferDialog } from "../components/dialogs/BankTransferDialog";
import type { PaymentStatusValue } from "../components/dialogs/PaymentStatus";
import { ProductGrid } from "../components/product/ProductGrid";
import { ProductSearchBar } from "../components/product/ProductSearchBar";
import { mockDiscounts } from "../mock/discounts.mock";
import { mockCustomers } from "../mock/customers.mock";
import { mockProducts } from "../mock/products.mock";
import { formatCurrency } from "../services/money";
import {
    calculateDiscountAmount,
    calculateSaleTotals
} from "../services/totals";
import type {
    SaleCartItem,
    SaleDiscountOption,
    SaleOrderType,
    SalePaymentMethod,
    SaleProductPreview,
    SaleTotals
} from "../types/sale.types";

const noDiscount: SaleDiscountOption = {
    id: "none",
    label: "Không áp dụng",
    code: "",
    type: "NONE",
    value: 0,
    description: "Không áp dụng khuyến mãi",
    status: "AVAILABLE",
    condition: "Có thể bán hàng không dùng mã giảm giá",
    minSubtotal: null,
    expiresIn: null
};

const bankTransferReferenceCode = "HVT-HD000015";

export function PosSalePage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [cartItemsById, setCartItemsById] = useState<
        Record<string, SaleCartItem>
    >({});
    const [note, setNote] = useState("");
    const [orderType, setOrderType] =
        useState<SaleOrderType>("QUICK_SALE");
    const [selectedDiscountId, setSelectedDiscountId] =
        useState("none");
    const [subtotalOverride, setSubtotalOverride] =
        useState<number | null>(null);
    const [subtotalOverrideNote, setSubtotalOverrideNote] =
        useState("");
    const [cashReceived, setCashReceived] = useState(0);
    const [paymentMethod, setPaymentMethod] =
        useState<SalePaymentMethod>("CASH");
    const [bankTransferStatus, setBankTransferStatus] =
        useState<PaymentStatusValue>("WAITING");
    const [selectedCustomerId, setSelectedCustomerId] =
        useState<string | null>(null);

    const searchResults = useMemo(() => {
        const normalizedQuery = searchQuery.trim().toLowerCase();

        if (!normalizedQuery) {
            return [];
        }

        return mockProducts.filter(
            (product) =>
                product.name.toLowerCase().includes(normalizedQuery) ||
                product.sku.toLowerCase().includes(normalizedQuery) ||
                product.barcode.toLowerCase().includes(normalizedQuery)
        );
    }, [searchQuery]);

    const cartItems = useMemo(
        () => Object.values(cartItemsById),
        [cartItemsById]
    );

    const productQuantities = useMemo(() => {
        return cartItems.reduce<Record<string, number>>((result, item) => {
            result[item.productId] = Math.max(0, item.quantity);

            return result;
        }, {});
    }, [cartItems]);

    const productIdsInCart = useMemo(
        () => new Set(cartItems.map((item) => item.productId)),
        [cartItems]
    );

    const selectedProducts = useMemo(() => {
        return cartItems
            .map((item) =>
                mockProducts.find((product) => product.id === item.productId)
            )
            .filter((product): product is SaleProductPreview =>
                Boolean(product)
            );
    }, [cartItems]);

    const selectedDiscount =
        mockDiscounts.find(
            (discount) => discount.id === selectedDiscountId
        ) ?? noDiscount;
    const selectedCustomer =
        selectedCustomerId === null
            ? null
            : mockCustomers.find(
                (customer) => customer.id === selectedCustomerId
            ) ?? null;

    const baseTotals = calculateSaleTotals(cartItems, selectedDiscount);
    const subtotalOverrideDiscount =
        subtotalOverride === null
            ? 0
            : calculateDiscountAmount(subtotalOverride, selectedDiscount);
    const totals: SaleTotals =
        subtotalOverride === null
            ? baseTotals
            : {
                subtotal: subtotalOverride,
                discountAmount: subtotalOverrideDiscount,
                total: Math.max(
                    0,
                    subtotalOverride - subtotalOverrideDiscount
                )
            };
    const changeAmount = Math.max(0, cashReceived - totals.total);

    function addProduct(product: SaleProductPreview) {
        setCartItemsById((currentItems) => {
            const currentItem = currentItems[product.id];

            return {
                ...currentItems,
                [product.id]: {
                    productId: product.id,
                    name: product.name,
                    sku: product.sku,
                    variant: product.variant,
                    quantity: Math.min(
                        product.availableQuantity,
                        (currentItem?.quantity ?? 0) + 1
                    ),
                    unitPrice: product.price
                }
            };
        });
    }

    function addProductFromSearch(product: SaleProductPreview) {
        addProduct(product);
        setSearchQuery("");
    }

    function incrementProduct(productId: string) {
        const product = mockProducts.find((item) => item.id === productId);

        if (!product) {
            return;
        }

        setCartItemsById((currentItems) => {
            const currentItem = currentItems[productId];

            if (!currentItem) {
                return currentItems;
            }

            return {
                ...currentItems,
                [productId]: {
                    ...currentItem,
                    quantity: Math.min(
                        product.availableQuantity,
                        currentItem.quantity + 1
                    )
                }
            };
        });
    }

    function decrementProduct(productId: string) {
        setCartItemsById((currentItems) => {
            const currentItem = currentItems[productId];

            if (!currentItem) {
                return currentItems;
            }

            return {
                ...currentItems,
                [productId]: {
                    ...currentItem,
                    quantity: Math.max(0, currentItem.quantity - 1)
                }
            };
        });
    }

    function removeProduct(productId: string) {
        setCartItemsById((currentItems) => {
            const nextItems = { ...currentItems };
            delete nextItems[productId];

            return nextItems;
        });
    }

    function changePaymentMethod(method: SalePaymentMethod) {
        setPaymentMethod(method);

        if (method === "BANK_TRANSFER") {
            setBankTransferStatus("WAITING");
        }
    }

    return (
        <PosShell
            checkout={
                <CheckoutPanel
                    discounts={mockDiscounts}
                    selectedDiscountId={selectedDiscount.id}
                    totals={totals}
                    subtotalOverrideNote={subtotalOverrideNote}
                    cashReceived={cashReceived}
                    changeAmount={changeAmount}
                    paymentMethod={paymentMethod}
                    bankTransferStatus={bankTransferStatus}
                    customers={mockCustomers}
                    selectedCustomer={selectedCustomer}
                    onCashReceivedChange={setCashReceived}
                    onDiscountChange={setSelectedDiscountId}
                    onSubtotalOverride={(value, subtotalNote) => {
                        setSubtotalOverride(value);
                        setSubtotalOverrideNote(subtotalNote);
                    }}
                    onSubtotalReset={() => {
                        setSubtotalOverride(null);
                        setSubtotalOverrideNote("");
                    }}
                    onPaymentMethodChange={changePaymentMethod}
                    onCustomerChange={setSelectedCustomerId}
                    onCustomerClear={() => setSelectedCustomerId(null)}
                    onBankTransferCancel={() =>
                        setBankTransferStatus("CANCELLED")
                    }
                    onBankTransferConfirm={() =>
                        setBankTransferStatus("RECEIVED")
                    }
                    onBankTransferRefresh={() =>
                        setBankTransferStatus("WAITING")
                    }
                />
            }
        >
            <section className="grid h-full min-h-0 grid-rows-[76px_minmax(0,1fr)_84px_76px] gap-4 xl:grid-rows-[76px_minmax(0,1fr)_84px_78px] xl:gap-5">
                <ProductSearchBar
                    value={searchQuery}
                    results={searchResults}
                    formatCurrency={formatCurrency}
                    onChange={setSearchQuery}
                    onClear={() => setSearchQuery("")}
                    onAdd={addProductFromSearch}
                />

                <ProductGrid
                    products={selectedProducts}
                    quantities={productQuantities}
                    productIdsInCart={productIdsInCart}
                    formatCurrency={formatCurrency}
                    onAdd={addProduct}
                    onIncrement={incrementProduct}
                    onDecrement={decrementProduct}
                    onRemove={removeProduct}
                />

                <CartNote value={note} onChange={setNote} />

                <OrderTypeSelector
                    value={orderType}
                    onChange={setOrderType}
                />
            </section>
        </PosShell>
    );
}

interface CheckoutPanelProps {
    discounts: SaleDiscountOption[];
    selectedDiscountId: string;
    totals: SaleTotals;
    subtotalOverrideNote: string;
    cashReceived: number;
    changeAmount: number;
    paymentMethod: SalePaymentMethod;
    bankTransferStatus: PaymentStatusValue;
    customers: typeof mockCustomers;
    selectedCustomer: (typeof mockCustomers)[number] | null;
    onDiscountChange(discountId: string): void;
    onSubtotalOverride(value: number, note: string): void;
    onSubtotalReset(): void;
    onCashReceivedChange(value: number): void;
    onPaymentMethodChange(value: SalePaymentMethod): void;
    onCustomerChange(customerId: string): void;
    onCustomerClear(): void;
    onBankTransferCancel(): void;
    onBankTransferConfirm(): void;
    onBankTransferRefresh(): void;
}

function CheckoutPanel({
    discounts,
    selectedDiscountId,
    totals,
    subtotalOverrideNote,
    cashReceived,
    changeAmount,
    paymentMethod,
    bankTransferStatus,
    customers,
    selectedCustomer,
    onDiscountChange,
    onSubtotalOverride,
    onSubtotalReset,
    onCashReceivedChange,
    onPaymentMethodChange,
    onCustomerChange,
    onCustomerClear,
    onBankTransferCancel,
    onBankTransferConfirm,
    onBankTransferRefresh
}: CheckoutPanelProps) {
    const isBankTransfer = paymentMethod === "BANK_TRANSFER";
    const canCompletePayment =
        totals.total > 0 &&
        (paymentMethod !== "CASH" || cashReceived >= totals.total) &&
        (!isBankTransfer || bankTransferStatus === "RECEIVED");

    return (
        <section className="flex h-full min-h-0 flex-col gap-4 rounded-3xl bg-pos-surface p-5 shadow-pos-panel xl:p-6">
            <CustomerSelector
                customers={customers}
                selectedCustomer={selectedCustomer}
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
                onChange={onPaymentMethodChange}
            />

            {isBankTransfer ? (
                <div className="min-h-0 overflow-y-auto pr-1">
                    <BankTransferDialog
                        amount={totals.total}
                        status={bankTransferStatus}
                        referenceCode={bankTransferReferenceCode}
                        onCancel={onBankTransferCancel}
                        onConfirmReceived={onBankTransferConfirm}
                        onRefreshStatus={onBankTransferRefresh}
                    />
                </div>
            ) : (
                <div className="grid grid-cols-2 gap-3">
                    <ReturnMoneyAction onClick={() => undefined} />
                    <AddDebtAction onClick={() => undefined} />
                </div>
            )}

            <div className="mt-auto">
                <CompletePaymentButton
                    total={totals.total}
                    disabled={!canCompletePayment}
                    onClick={() => undefined}
                />
            </div>
        </section>
    );
}
