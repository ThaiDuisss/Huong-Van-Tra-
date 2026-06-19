import { useMemo, useState } from "react";
import { ChevronDown, Plus } from "lucide-react";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { CartList } from "../components/cart/CartList";
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
import { CombinedPaymentDialog } from "../components/dialogs/CombinedPaymentDialog";
import type { PaymentStatusValue } from "../components/dialogs/PaymentStatus";
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
import styles from "./PosSalePage.module.css";

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

const initialCartItems: Record<string, SaleCartItem> = {
    "tea-oi-tu-tay": {
        productId: "tea-oi-tu-tay",
        name: "Trà Ôi Tứ Tây",
        sku: "SKU-HH-111",
        variant: "100g",
        quantity: 1,
        unitPrice: 120000
    },
    "tea-olong-tu-quy": {
        productId: "tea-olong-tu-quy",
        name: "Trà Ô Long Tứ Quý",
        sku: "SKU-HH-112",
        variant: "100g",
        quantity: 1,
        unitPrice: 120000
    },
    "tea-tu-quy-jasmine": {
        productId: "tea-tu-quy-jasmine",
        name: "Trà Tứ Quý Jasmine",
        sku: "SKU-HH-113",
        variant: "100g",
        quantity: 1,
        unitPrice: 150000
    },
    "tea-shan-tuyet": {
        productId: "tea-shan-tuyet",
        name: "Trà Shan Tuyết Cổ Thụ",
        sku: "SKU-HH-114",
        variant: "100g",
        quantity: 1,
        unitPrice: 140000
    },
    "tea-tu-quy-premium": {
        productId: "tea-tu-quy-premium",
        name: "Trà Tứ Quý Thượng Hạng",
        sku: "SKU-HH-115",
        variant: "100g",
        quantity: 1,
        unitPrice: 180000
    },
    "tea-nhai": {
        productId: "tea-nhai",
        name: "Trà Nhài",
        sku: "SKU-HH-120",
        variant: "100g",
        quantity: 2,
        unitPrice: 110000
    },
    "tea-pho-nhi": {
        productId: "tea-pho-nhi",
        name: "Trà Phổ Nhĩ",
        sku: "SKU-HH-121",
        variant: "357g",
        quantity: 1,
        unitPrice: 280000
    }
};

export function PosSalePage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [cartItemsById, setCartItemsById] = useState<
        Record<string, SaleCartItem>
    >(initialCartItems);
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
        useState<string | null>("customer-do-xuan-thai");

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
                    sku: product.sku.startsWith("SKU-")
                        ? product.sku
                        : `SKU-${product.sku}`,
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

            if (currentItem.quantity <= 1) {
                const nextItems = { ...currentItems };
                delete nextItems[productId];

                return nextItems;
            }

            return {
                ...currentItems,
                [productId]: {
                    ...currentItem,
                    quantity: currentItem.quantity - 1
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
            <section className={styles.saleWorkspace}>
                <div className={styles.topbar}>
                    <ProductSearchBar
                        value={searchQuery}
                        results={searchResults}
                        formatCurrency={formatCurrency}
                        onChange={setSearchQuery}
                        onClear={() => setSearchQuery("")}
                        onAdd={addProductFromSearch}
                    />

                    <div className={styles.invoiceTabs} aria-label="Hóa đơn">
                        <button
                            className={styles.invoiceTab}
                            type="button"
                            aria-current="page"
                        >
                            <span>Hóa đơn 1</span>
                            <span aria-hidden="true">×</span>
                        </button>
                        <button
                            className={styles.squareAction}
                            type="button"
                            aria-label="Tạo hóa đơn mới"
                            title="Tạo hóa đơn mới"
                        >
                            <Plus size={22} aria-hidden="true" />
                        </button>
                        <button
                            className={styles.squareAction}
                            type="button"
                            aria-label="Danh sách hóa đơn"
                            title="Danh sách hóa đơn"
                        >
                            <ChevronDown size={22} aria-hidden="true" />
                        </button>
                    </div>
                </div>

                <CartList
                    items={cartItems}
                    formatCurrency={formatCurrency}
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
    const [bankTransferOpen, setBankTransferOpen] = useState(false);
    const [combinedPaymentOpen, setCombinedPaymentOpen] = useState(false);
    const isBankTransfer = paymentMethod === "BANK_TRANSFER";
    const isCombinedPayment = paymentMethod === "COMBINED";
    const canCompletePayment =
        totals.total > 0 &&
        (paymentMethod !== "CASH" || cashReceived >= totals.total) &&
        (!isBankTransfer || bankTransferStatus === "RECEIVED");

    return (
        <section className="flex h-full min-h-0 flex-col gap-3 rounded-[20px] bg-pos-surface p-4 shadow-pos-panel">
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
                onChange={(value) => {
                    onPaymentMethodChange(value);

                    if (value === "BANK_TRANSFER") {
                        setBankTransferOpen(true);
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

            {isBankTransfer && bankTransferOpen ? (
                <div
                    className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
                    role="presentation"
                >
                    <BankTransferDialog
                        amount={totals.total}
                        status={bankTransferStatus}
                        referenceCode={bankTransferReferenceCode}
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
                    onClick={() => undefined}
                />
            </div>
        </section>
    );
}
