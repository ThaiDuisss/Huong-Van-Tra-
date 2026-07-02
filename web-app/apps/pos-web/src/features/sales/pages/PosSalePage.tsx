import { useEffect, useMemo, useState } from "react";
import { ApiError } from "@hvt/api-client";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { CheckoutPanel } from "../components/checkout/CheckoutPanel";
import type { PaymentStatusValue } from "../components/dialogs/PaymentStatus";
import { SaleWorkspace } from "../components/workspace/SaleWorkspace";
import { mockDiscounts } from "../mock/discounts.mock";
import { posCustomerApi } from "../services/posCustomer.api";
import {
    completePosCheckout,
    type PosCheckoutPaymentResult
} from "../services/posCheckout.service";
import { posOrderApi, type PosOrderDto } from "../services/posOrder.api";
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

function toNumber(value: number | string | null | undefined) {
    const numberValue = Number(value ?? 0);

    return Number.isFinite(numberValue) ? numberValue : 0;
}

function orderToCartItemsById(order: PosOrderDto): Record<string, SaleCartItem> {
    const items = order.items ?? [];

    return Object.fromEntries(
        items.map((item) => {
            const productVariantId = item.productVariantId;
            const quantity = toNumber(item.quantity);
            const unitPrice = toNumber(item.actualUnitPrice ?? item.unitPrice);

            return [
                productVariantId,
                {
                    itemId: item.id,
                    productId: productVariantId,
                    productVariantId,
                    name:
                        item.productNameSnapshot ??
                        item.productName ??
                        "Sản phẩm",
                    sku: item.skuSnapshot ?? item.sku ?? "",
                    variant: item.variantNameSnapshot ?? "",
                    quantity,
                    unitPrice,
                    inventoryLocationId: null
                }
            ];
        })
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
    const [draftOrder, setDraftOrder] = useState<PosOrderDto | null>(null);
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
    const [bankTransferPayment, setBankTransferPayment] =
        useState<PosCheckoutPaymentResult | null>(null);
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

    function resetCurrentSale() {
        setCartItemsById({});
        setDraftOrder(null);
        setNote("");
        setSelectedDiscountId("none");
        setSubtotalOverride(null);
        setSubtotalOverrideNote("");
        setCashReceived(0);
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
        clearCustomer();
    }

    function prepareBankTransferPayment() {
        if (checkoutLoading) {
            return;
        }

        setCheckoutError("");
        setCheckoutSuccess("");
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
    }

    async function completePayment() {
        if (paymentMethod === "BANK_TRANSFER") {
            if (bankTransferStatus !== "RECEIVED") {
                setCheckoutError("Vui lòng xác nhận đã nhận chuyển khoản.");
                return;
            }
        }

        setCheckoutLoading(true);
        setCheckoutError("");
        setCheckoutSuccess("");

        try {
            const checkout = await completePosCheckout({
                orderId: draftOrder?.id ?? null,
                cartItems,
                orderType,
                paymentMethod,
                cashReceived,
                totals,
                originalSubtotal: baseTotals.subtotal,
                subtotalOverride,
                subtotalOverrideNote,
                selectedCustomerId
            });

            resetCurrentSale();
            setCheckoutSuccess(
                checkout.orderCode
                    ? `Thanh toán thành công đơn ${checkout.orderCode}.`
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

    function applyDraftOrder(order: PosOrderDto) {
        setDraftOrder(order);
        setCartItemsById(orderToCartItemsById(order));
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
    }

    async function addProduct(product: SaleProductPreview) {
        const variantId = product.productVariantId ?? product.id;

        if (addingProductId) {
            return false;
        }

        if (!variantId) {
            setCheckoutError("Không xác định được mã Mặt hàng.");
            return false;
        }

        if (
            product.availableQuantity !== null &&
            product.availableQuantity <= 0
        ) {
            setCheckoutError("Sản phẩm đã hết hàng");
            return false;
        }

        setAddingProductId(product.id);

        try {
            const order = await posOrderApi.addDraftItem({
                orderId: draftOrder?.id ?? null,
                customerId: selectedCustomerId,
                orderType,
                productVariantId: variantId,
                inventoryLocationId: product.inventoryLocationId
            });
            applyDraftOrder(order);
            setCheckoutError("");
            setCheckoutSuccess("");
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

    async function updateCartItemQuantity(productId: string, quantity: number) {
        const currentItem = cartItemsById[productId];

        if (!draftOrder?.id || !currentItem?.itemId) {
            setCheckoutError("Hóa đơn nháp chưa sẵn sàng.");
            return;
        }

        try {
            const order =
                quantity <= 0
                    ? await posOrderApi.removeDraftItem(
                        draftOrder.id,
                        currentItem.itemId
                    )
                    : await posOrderApi.updateDraftItemQuantity(
                        draftOrder.id,
                        currentItem.itemId,
                        quantity
                    );
            applyDraftOrder(order);
            setCheckoutError("");
            setCheckoutSuccess("");
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        }
    }

    function incrementProduct(productId: string) {
        const currentItem = cartItemsById[productId];

        if (!currentItem) {
            return;
        }

        void updateCartItemQuantity(productId, currentItem.quantity + 1);
    }

    function decrementProduct(productId: string) {
        const currentItem = cartItemsById[productId];

        if (!currentItem) {
            return;
        }

        void updateCartItemQuantity(productId, currentItem.quantity - 1);
    }

    function removeProduct(productId: string) {
        void updateCartItemQuantity(productId, 0);
    }

    function changePaymentMethod(method: SalePaymentMethod) {
        setPaymentMethod(method);

        if (method === "BANK_TRANSFER") {
            if (paymentMethod !== "BANK_TRANSFER") {
                setBankTransferStatus("WAITING");
                setBankTransferPayment(null);
            }
        }
    }

    async function closeCurrentInvoice() {
        setCheckoutError("");
        setCheckoutSuccess("");

        try {
            if (draftOrder?.id && draftOrder.status === "DRAFT") {
                await posOrderApi.cancelDraftOrder(draftOrder.id);
            }
            setDraftOrder(null);
            setCartItemsById({});
            setNote("");
            setCashReceived(0);
            setSelectedDiscountId("none");
            setSubtotalOverride(null);
            setSubtotalOverrideNote("");
            setBankTransferPayment(null);
            setBankTransferStatus("WAITING");
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
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
                    bankTransferReferenceCode={
                        draftOrder?.orderCode ?? draftOrder?.id
                    }
                    bankTransferStatus={bankTransferStatus}
                    bankTransferPayment={bankTransferPayment}
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
                    onBankTransferStart={() => {
                        prepareBankTransferPayment();
                    }}
                    onCompletePayment={completePayment}
                />
            }
        >
            <SaleWorkspace
                searchQuery={searchQuery}
                searchResults={searchResults}
                searchLoading={searchLoading}
                searchError={searchError}
                addingProductId={addingProductId}
                cartItems={cartItems}
                note={note}
                orderType={orderType}
                onSearchChange={changeSearchQuery}
                onSearchClear={clearSearch}
                onProductAdd={addProductFromSearch}
                onProductIncrement={incrementProduct}
                onProductDecrement={decrementProduct}
                onProductRemove={removeProduct}
                onInvoiceClose={() => {
                    void closeCurrentInvoice();
                }}
                onNoteChange={setNote}
                onOrderTypeChange={setOrderType}
            />
        </PosShell>
    );
}
