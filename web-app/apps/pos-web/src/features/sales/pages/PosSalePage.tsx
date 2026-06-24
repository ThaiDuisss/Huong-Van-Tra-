import { useEffect, useMemo, useState } from "react";
import { ApiError } from "@hvt/api-client";
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
import { posCustomerApi } from "../services/posCustomer.api";
import { formatCurrency } from "../services/money";
import { posOrderApi } from "../services/posOrder.api";
import { posPaymentApi } from "../services/posPayment.api";
import { posProductApi } from "../services/posProduct.api";
import {
    calculateDiscountAmount,
    calculateSaleTotals
} from "../services/totals";
import type {
    SaleCartItem,
    SaleDiscountOption,
    SaleCustomer,
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
const currentInvoiceId = "invoice-1";

const initialCartItems: Record<string, SaleCartItem> = {};

function getErrorMessage(error: unknown) {
    if (error instanceof ApiError) {
        return error.message;
    }

    if (error instanceof Error) {
        return error.message;
    }

    return "Có lỗi xảy ra. Vui lòng thử lại.";
}

function logApiError(error: unknown) {
    console.error("POS API error", error);
}

function isUuid(value: string | null) {
    return Boolean(
        value?.match(
            /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
        )
    );
}

export function PosSalePage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState<
        SaleProductPreview[]
    >([]);
    const [searchLoading, setSearchLoading] = useState(false);
    const [searchError, setSearchError] = useState("");
    const [addingProductId, setAddingProductId] = useState<string | null>(
        null
    );
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
    const [customers, setCustomers] = useState<SaleCustomer[]>([]);
    const [customersLoading, setCustomersLoading] = useState(false);
    const [customersLoaded, setCustomersLoaded] = useState(false);
    const [customersError, setCustomersError] = useState("");
    const [selectedCustomersByInvoice, setSelectedCustomersByInvoice] =
        useState<Record<string, SaleCustomer | null>>({
            [currentInvoiceId]: null
        });
    const [checkoutLoading, setCheckoutLoading] = useState(false);
    const [checkoutError, setCheckoutError] = useState("");
    const [checkoutSuccess, setCheckoutSuccess] = useState("");
    const [createdOrderId, setCreatedOrderId] = useState<string | null>(
        null
    );

    useEffect(() => {
        const normalizedQuery = searchQuery.trim();

        if (!normalizedQuery) {
            return;
        }

        let active = true;
        const timeoutId = window.setTimeout(() => {
            setSearchLoading(true);
            setSearchError("");

            void posProductApi
                .search(normalizedQuery)
                .then((products) => {
                    if (active) {
                        setSearchResults(products);
                    }
                })
                .catch((error: unknown) => {
                    logApiError(error);

                    if (active) {
                        setSearchResults([]);
                        setSearchError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (active) {
                        setSearchLoading(false);
                    }
                });
        }, 300);

        return () => {
            active = false;
            window.clearTimeout(timeoutId);
        };
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
        selectedCustomersByInvoice[currentInvoiceId] ?? null;
    const selectedCustomerId = selectedCustomer?.id ?? null;

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

    async function completePayment() {
        if (cartItems.length === 0) {
            setCheckoutError("Giỏ hàng đang rỗng.");
            return;
        }

        if (orderType !== "QUICK_SALE") {
            setCheckoutError("Vui lòng chọn tab Bán nhanh để thanh toán.");
            return;
        }

        if (paymentMethod === "COMBINED") {
            setCheckoutError(
                "Payment-Service hiện chưa có API thanh toán tổng hợp."
            );
            return;
        }

        if (paymentMethod === "BANK_TRANSFER") {
            setCheckoutError(
                "Payment-Service hiện chưa có API thanh toán chuyển khoản."
            );
            return;
        }

        if (paymentMethod === "CASH" && cashReceived < totals.total) {
            setCheckoutError("Tiền khách đưa chưa đủ để thanh toán.");
            return;
        }

        setCheckoutLoading(true);
        setCheckoutError("");
        setCheckoutSuccess("");

        let orderId = createdOrderId;
        let orderCode = "";
        let amountToPay = totals.total;

        try {
            if (!orderId) {
                try {
                    const order = await posOrderApi.create({
                        customerId: isUuid(selectedCustomerId)
                            ? selectedCustomerId
                            : null,
                        orderType,
                        discountAmount: totals.discountAmount,
                        items: cartItems
                    });

                    orderId = order.id;
                    orderCode = order.orderCode;
                    amountToPay = Number(order.finalAmount ?? totals.total);
                    setCreatedOrderId(order.id);
                } catch (error) {
                    logApiError(error);
                    setCheckoutError(
                        `Tạo đơn thất bại: ${getErrorMessage(error)}`
                    );
                    return;
                }
            }

            const receivedAmount =
                paymentMethod === "CASH"
                    ? Math.max(cashReceived, amountToPay)
                    : amountToPay;

            try {
                await posPaymentApi.payByCash({
                    orderId,
                    receivedAmount
                });
            } catch (error) {
                logApiError(error);
                setCheckoutError(
                    `Đã tạo đơn nhưng thanh toán thất bại: ${getErrorMessage(error)}`
                );
                return;
            }

            setCartItemsById({});
            setNote("");
            setSelectedDiscountId("none");
            setSubtotalOverride(null);
            setSubtotalOverrideNote("");
            setCashReceived(0);
            setBankTransferStatus("WAITING");
            setCreatedOrderId(null);
            clearCustomer();
            setCheckoutSuccess(
                orderCode
                    ? `Thanh toán thành công đơn ${orderCode}.`
                    : "Thanh toán thành công."
            );
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    function loadCustomers() {
        if (customersLoading || customersLoaded) {
            return;
        }

        setCustomersLoading(true);
        setCustomersError("");

        void posCustomerApi
            .listCustomerInfo()
            .then((nextCustomers) => {
                setCustomers(nextCustomers);
                setCustomersLoaded(true);
            })
            .catch((error: unknown) => {
                logApiError(error);
                setCustomers([]);
                setCustomersError("Không thể tải danh sách khách hàng");
            })
            .finally(() => {
                setCustomersLoading(false);
            });
    }

    function selectCustomer(customerId: string) {
        const nextCustomer =
            customers.find((customer) => customer.id === customerId) ?? null;

        setSelectedCustomersByInvoice((currentCustomers) => ({
            ...currentCustomers,
            [currentInvoiceId]: nextCustomer
        }));
    }

    function clearCustomer() {
        setSelectedCustomersByInvoice((currentCustomers) => ({
            ...currentCustomers,
            [currentInvoiceId]: null
        }));
    }

    function upsertCartItem(product: SaleProductPreview) {
        setCheckoutError("");
        setCheckoutSuccess("");
        setCartItemsById((currentItems) => {
            const currentItem = currentItems[product.id];

            return {
                ...currentItems,
                [product.id]: {
                    productId: product.id,
                    productVariantId: product.productVariantId,
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

    async function addProduct(product: SaleProductPreview) {
        const variantId = product.productVariantId ?? product.id;

        if (addingProductId) {
            return false;
        }

        if (!variantId) {
            setCheckoutError("Không xác định được mã biến thể sản phẩm.");
            return false;
        }

        setAddingProductId(product.id);

        try {
            const snapshot = await posProductApi.getPosView(variantId);
            upsertCartItem(snapshot);
            return true;
        } catch (error) {
            logApiError(error);
            setCheckoutError(
                `Không thể thêm sản phẩm: ${getErrorMessage(error)}`
            );
            return false;
        } finally {
            setAddingProductId(null);
        }
    }

    async function addProductFromSearch(product: SaleProductPreview) {
        const added = await addProduct(product);

        if (added) {
            clearSearch();
        }
    }

    function changeSearchQuery(value: string) {
        setSearchQuery(value);

        if (!value.trim()) {
            setSearchResults([]);
            setSearchError("");
            setSearchLoading(false);
        } else {
            setSearchLoading(true);
        }
    }

    function clearSearch() {
        setSearchQuery("");
        setSearchResults([]);
        setSearchError("");
        setSearchLoading(false);
    }

    function incrementProduct(productId: string) {
        setCartItemsById((currentItems) => {
            const currentItem = currentItems[productId];

            if (!currentItem) {
                return currentItems;
            }

            return {
                ...currentItems,
                [productId]: {
                    ...currentItem,
                    quantity: currentItem.quantity + 1
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
                    orderType={orderType}
                    paymentMethod={paymentMethod}
                    bankTransferStatus={bankTransferStatus}
                    checkoutLoading={checkoutLoading}
                    checkoutError={checkoutError}
                    checkoutSuccess={checkoutSuccess}
                    customers={customers}
                    customersLoading={customersLoading}
                    customersError={customersError}
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
                    onCustomerOpen={loadCustomers}
                    onCustomerChange={selectCustomer}
                    onCustomerClear={clearCustomer}
                    onBankTransferCancel={() =>
                        setBankTransferStatus("CANCELLED")
                    }
                    onBankTransferConfirm={() =>
                        setBankTransferStatus("RECEIVED")
                    }
                    onBankTransferRefresh={() =>
                        setBankTransferStatus("WAITING")
                    }
                    onCompletePayment={completePayment}
                />
            }
        >
            <section className={styles.saleWorkspace}>
                <div className={styles.topbar}>
                    <ProductSearchBar
                        value={searchQuery}
                        results={searchResults}
                        loading={searchLoading}
                        error={searchError}
                        addingProductId={addingProductId}
                        formatCurrency={formatCurrency}
                        onChange={changeSearchQuery}
                        onClear={clearSearch}
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
    orderType: SaleOrderType;
    paymentMethod: SalePaymentMethod;
    bankTransferStatus: PaymentStatusValue;
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
    onCompletePayment(): void;
}

function CheckoutPanel({
    discounts,
    selectedDiscountId,
    totals,
    subtotalOverrideNote,
    cashReceived,
    changeAmount,
    orderType,
    paymentMethod,
    bankTransferStatus,
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
                    loading={checkoutLoading}
                    onClick={onCompletePayment}
                />
            </div>
        </section>
    );
}
