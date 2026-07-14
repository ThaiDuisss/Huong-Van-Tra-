import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@hvt/api-client";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { useNotifications } from "../../notification/context/useNotifications";
import type { NotificationResponse } from "../../notification/types/notification.types";
import { CheckoutPanel } from "../components/checkout/CheckoutPanel";
import { DeliveryCheckoutPanel } from "../components/delivery/DeliveryCheckoutPanel";
import type { PaymentStatusValue } from "../components/dialogs/PaymentStatus";
import { SaleWorkspace } from "../components/workspace/SaleWorkspace";
import { mockDiscounts } from "../mock/discounts.mock";
import { deliveryApi } from "../services/delivery.api";
import {
    calculateDeliveryTotals,
    addressToSnapshot,
    isDeliveryOptionSelectable,
    isPhoneValid,
    isQuoteExpired,
    manualAddressToSnapshot,
    selectPreferredDeliveryOption,
    selectSuggestedAddress
} from "../services/delivery.utils";
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
import type {
    BranchLocation,
    DeliveryCheckoutState
} from "../types/delivery.types";

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
const currentBranchId = import.meta.env.VITE_POS_BRANCH_ID ?? null;
const currentBranchLocation: BranchLocation | null = null;

const initialDeliveryState: DeliveryCheckoutState = {
    addresses: [],
    addressLoading: false,
    addressError: "",
    selectedAddressId: null,
    addressSuggestionReason: null,
    manualAddress: {
        recipientName: "",
        recipientPhone: "",
        provinceCode: "",
        provinceName: "",
        districtCode: "",
        districtName: "",
        wardCode: "",
        wardName: "",
        addressLine: "",
        fullAddress: "",
        latitude: "",
        longitude: "",
        deliveryNote: "",
        addressType: "HOME"
    },
    useManualAddress: false,
    saveAddress: false,
    setAsDefaultAddress: false,
    deliveryNote: "",
    selectedDeliveryMode: "INTERNAL",
    deliveryModeSelectionSource: "RECOMMENDED",
    recommendedDeliveryMode: null,
    deliveryModeRecommendationReason: "",
    internalDeliveryOption: null,
    thirdPartyQuotes: [],
    selectedThirdPartyProvider: "ALL",
    selectedThirdPartyQuoteId: null,
    shippingFee: 0,
    deliveryOptions: [],
    selectedDeliveryOptionId: null,
    selectedDeliveryQuoteId: null,
    recommendedDeliveryOptionId: null,
    deliverySelectionSource: "RECOMMENDED",
    customerAcceptedDeliveryWait: false,
    quoteLoading: false,
    quoteError: "",
    quoteRequestedAt: 0,
    deliveryUrgency: "ASAP",
    deliveryPriority: "FASTEST",
    requestedDeliveryAt: "",
    paymentMethod: "COD",
    depositAmount: 0,
    submitError: "",
    submitPending: false
};

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

const deliveryRequoteErrorCodes = new Set([
    "DELIVERY_QUOTE_EXPIRED",
    "DELIVERY_OPTION_NO_LONGER_AVAILABLE",
    "INTERNAL_CAPACITY_CHANGED",
    "PROVIDER_PRICE_CHANGED",
    "PROVIDER_UNAVAILABLE"
]);

function isDeliveryRequoteRequired(error: unknown) {
    if (!(error instanceof ApiError)) {
        return false;
    }

    return (
        deliveryRequoteErrorCodes.has(error.code) ||
        getRequiresRequoteFlag(error.details)
    );
}

function getDeliveryRequoteMessage(error: unknown) {
    if (error instanceof ApiError) {
        switch (error.code) {
            case "DELIVERY_QUOTE_EXPIRED":
                return "Báo giá giao hàng đã hết hạn. Hệ thống đang lấy báo giá mới.";
            case "DELIVERY_OPTION_NO_LONGER_AVAILABLE":
                return "Phương thức giao hàng đã thay đổi. Hệ thống đang lấy báo giá mới.";
            case "INTERNAL_CAPACITY_CHANGED":
                return "Sức chứa giao nội bộ đã thay đổi. Hệ thống đang lấy báo giá mới.";
            case "PROVIDER_PRICE_CHANGED":
                return "Phí giao hàng từ đối tác đã thay đổi. Vui lòng xác nhận lại báo giá mới.";
            case "PROVIDER_UNAVAILABLE":
                return "Đối tác giao hàng hiện không khả dụng. Hệ thống đang lấy báo giá mới.";
            default:
                return error.message || "Báo giá giao hàng đã thay đổi. Vui lòng xác nhận lại.";
        }
    }

    return "Báo giá giao hàng đã thay đổi. Vui lòng xác nhận lại.";
}

function getRequiresRequoteFlag(details: unknown) {
    return (
        typeof details === "object" &&
        details !== null &&
        "requiresRequote" in details &&
        Boolean((details as { requiresRequote?: unknown }).requiresRequote)
    );
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

function isBankTransferNotificationForPayment(
    notification: NotificationResponse,
    payment: PosCheckoutPaymentResult
) {
    const sameOrderId =
        payment.orderId &&
        notification.orderId &&
        payment.orderId === notification.orderId;
    const sameOrderCode =
        payment.orderCode &&
        notification.orderCode &&
        payment.orderCode === notification.orderCode;

    return Boolean(
        (sameOrderId || sameOrderCode) &&
        [
            "ORDER_COMPLETED",
            "ORDER_PAYMENT_FAILED",
            "ORDER_CANCELLED",
            "ORDER_COMPLETED",
            "ORDER_STATUS_CHANGED"
        ].includes(notification.type)
    );
}

function notificationToPaymentStatus(
    notification: NotificationResponse
): PaymentStatusValue {
    if (
        notification.type === "ORDER_COMPLETED"
    ) {
        return "RECEIVED";
    }

    if (notification.type === "ORDER_CANCELLED") {
        return "CANCELLED";
    }

    const message = `${notification.title} ${notification.message}`.toLowerCase();
    if (message.includes("hết hạn") || message.includes("expired")) {
        return "EXPIRED";
    }

    return "FAILED";
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
    const [deliveryStateByInvoice, setDeliveryStateByInvoice] =
        useState<Record<string, DeliveryCheckoutState>>({
            [currentInvoiceId]: initialDeliveryState
        });
    const [checkoutLoading, setCheckoutLoading] = useState(false);
    const [checkoutError, setCheckoutError] = useState("");
    const [checkoutSuccess, setCheckoutSuccess] = useState("");
    const [bankTransferEventMessage, setBankTransferEventMessage] =
        useState("");
    const { notifications } = useNotifications();
    const handledBankNotificationIdRef = useRef<string | null>(null);
    const deliveryState =
        deliveryStateByInvoice[currentInvoiceId] ?? initialDeliveryState;
    const deliveryStateRef = useRef(deliveryState);

    useEffect(() => {
        deliveryStateRef.current = deliveryState;
    }, [deliveryState]);

    const setCurrentDeliveryState = useCallback(
        (nextState: DeliveryCheckoutState) => {
            setDeliveryStateByInvoice((currentState) => ({
                ...currentState,
                [currentInvoiceId]: nextState
            }));
        },
        []
    );

    const patchCurrentDeliveryState = useCallback(
        (partial: Partial<DeliveryCheckoutState>) => {
            setDeliveryStateByInvoice((currentState) => ({
                ...currentState,
                [currentInvoiceId]: {
                    ...(currentState[currentInvoiceId] ?? initialDeliveryState),
                    ...partial
                }
            }));
        },
        []
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

    const baseTotals = useMemo(
        () => calculateSaleTotals(cartItems, selectedDiscount),
        [cartItems, selectedDiscount]
    );
    const totals: SaleTotals = useMemo(() => {
        const subtotalOverrideDiscount =
            subtotalOverride === null
                ? 0
                : calculateDiscountAmount(subtotalOverride, selectedDiscount);

        return subtotalOverride === null
            ? baseTotals
            : {
                  subtotal: subtotalOverride,
                  discountAmount: subtotalOverrideDiscount,
                  total: Math.max(
                      0,
                      subtotalOverride - subtotalOverrideDiscount
                  )
              };
    }, [baseTotals, selectedDiscount, subtotalOverride]);
    const changeAmount = Math.max(0, cashReceived - totals.total);

    const clearCustomer = useCallback(() => {
        setSelectedCustomersByInvoice((currentCustomers) => ({
            ...currentCustomers,
            [currentInvoiceId]: null
        }));
        patchCurrentDeliveryState({
            addresses: [],
            addressError: "",
            addressLoading: false,
            selectedAddressId: null,
            addressSuggestionReason: null,
            useManualAddress: false,
            selectedDeliveryMode: "INTERNAL",
            deliveryModeSelectionSource: "RECOMMENDED",
            recommendedDeliveryMode: null,
            deliveryModeRecommendationReason: "",
            internalDeliveryOption: null,
            thirdPartyQuotes: [],
            selectedThirdPartyProvider: "ALL",
            selectedThirdPartyQuoteId: null,
            shippingFee: 0,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            quoteError: "",
            submitError: ""
        });
    }, [patchCurrentDeliveryState]);

    const resetCurrentSale = useCallback(() => {
        setCartItemsById({});
        setDraftOrder(null);
        setNote("");
        setSelectedDiscountId("none");
        setSubtotalOverride(null);
        setSubtotalOverrideNote("");
        setCashReceived(0);
        setPaymentMethod("CASH");
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
        setBankTransferEventMessage("");
        handledBankNotificationIdRef.current = null;
        clearCustomer();
    }, [clearCustomer]);

    async function prepareBankTransferPayment() {
        if (checkoutLoading) {
            return;
        }

        setCheckoutError("");
        setCheckoutSuccess("");
        setBankTransferEventMessage("");
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
        handledBankNotificationIdRef.current = null;
        setCheckoutLoading(true);

        try {
            const checkout = await completePosCheckout({
                orderId: draftOrder?.id ?? null,
                cartItems,
                orderType,
                paymentMethod: "BANK_TRANSFER",
                cashReceived,
                totals,
                originalSubtotal: baseTotals.subtotal,
                subtotalOverride,
                subtotalOverrideNote,
                selectedCustomerId
            });

            if (!checkout.payment?.qrUrl) {
                setCheckoutError("Payment-Service chưa trả về URL QR.");
                return;
            }

            setBankTransferPayment(checkout.payment);
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
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
        patchCurrentDeliveryState({
            addresses: [],
            addressError: "",
            addressLoading: Boolean(nextCustomer),
            selectedAddressId: null,
            addressSuggestionReason: null,
            useManualAddress: false,
            selectedDeliveryMode: "INTERNAL",
            deliveryModeSelectionSource: "RECOMMENDED",
            recommendedDeliveryMode: null,
            deliveryModeRecommendationReason: "",
            internalDeliveryOption: null,
            thirdPartyQuotes: [],
            selectedThirdPartyProvider: "ALL",
            selectedThirdPartyQuoteId: null,
            shippingFee: 0,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            quoteError: "",
            submitError: ""
        });
    }

    function applyDraftOrder(order: PosOrderDto) {
        setDraftOrder(order);
        setCartItemsById(orderToCartItemsById(order));
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
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
            setBankTransferEventMessage("");
            patchCurrentDeliveryState(initialDeliveryState);
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        }
    }

    useEffect(() => {
        if (!selectedCustomerId) {
            return undefined;
        }

        const abortController = new AbortController();
        const timeoutId = window.setTimeout(() => {
            patchCurrentDeliveryState({
                addressLoading: true,
                addressError: "",
                addresses: [],
                selectedAddressId: null,
                addressSuggestionReason: null,
                selectedDeliveryMode: "INTERNAL",
                deliveryModeSelectionSource: "RECOMMENDED",
                recommendedDeliveryMode: null,
                deliveryModeRecommendationReason: "",
                internalDeliveryOption: null,
                thirdPartyQuotes: [],
                selectedThirdPartyProvider: "ALL",
                selectedThirdPartyQuoteId: null,
                shippingFee: 0,
                deliveryOptions: [],
                selectedDeliveryOptionId: null,
                selectedDeliveryQuoteId: null,
                recommendedDeliveryOptionId: null,
                deliverySelectionSource: "RECOMMENDED",
                customerAcceptedDeliveryWait: false,
                quoteError: ""
            });

            void deliveryApi
                .getCustomerAddresses(selectedCustomerId, abortController.signal)
                .then((addresses) => {
                    const suggestedAddress = selectSuggestedAddress(
                        addresses,
                        currentBranchLocation
                    );

                    patchCurrentDeliveryState({
                        addresses,
                        addressLoading: false,
                        selectedAddressId: suggestedAddress?.address.id ?? null,
                        addressSuggestionReason: suggestedAddress?.reason ?? null,
                        useManualAddress: !suggestedAddress,
                        addressError: ""
                    });
                })
                .catch((error: unknown) => {
                    if (abortController.signal.aborted) {
                        return;
                    }

                    logApiError(error);
                    patchCurrentDeliveryState({
                        addressLoading: false,
                        addressError: "Không tải được địa chỉ giao hàng.",
                        addresses: [],
                        selectedAddressId: null,
                        addressSuggestionReason: null,
                        useManualAddress: true
                    });
                });
        }, 0);

        return () => {
            window.clearTimeout(timeoutId);
            abortController.abort();
        };
    }, [patchCurrentDeliveryState, selectedCustomerId]);

    const deliverySnapshot = useMemo(
        () => {
            if (deliveryState.useManualAddress) {
                return manualAddressToSnapshot(
                    deliveryState.manualAddress,
                    deliveryState.deliveryNote
                );
            }

            const selectedAddress = deliveryState.addresses.find(
                (address) => address.id === deliveryState.selectedAddressId
            );

            return selectedAddress
                ? addressToSnapshot(selectedAddress, deliveryState.deliveryNote)
                : null;
        },
        [
            deliveryState.addresses,
            deliveryState.deliveryNote,
            deliveryState.manualAddress,
            deliveryState.selectedAddressId,
            deliveryState.useManualAddress
        ]
    );
    const selectedDeliveryOption = useMemo(
        () => {
            if (deliveryState.selectedDeliveryMode === "INTERNAL") {
                return deliveryState.internalDeliveryOption &&
                    deliveryState.selectedDeliveryOptionId ===
                        deliveryState.internalDeliveryOption.optionId
                    ? deliveryState.internalDeliveryOption
                    : null;
            }

            if (deliveryState.selectedDeliveryMode === "THIRD_PARTY") {
                return (
                    deliveryState.thirdPartyQuotes.find(
                        (quote) =>
                            quote.quoteId ===
                            deliveryState.selectedThirdPartyQuoteId
                    ) ?? null
                );
            }

            return null;
        },
        [
            deliveryState.internalDeliveryOption,
            deliveryState.selectedDeliveryMode,
            deliveryState.selectedDeliveryOptionId,
            deliveryState.selectedThirdPartyQuoteId,
            deliveryState.thirdPartyQuotes
        ]
    );
    const deliveryTotals = useMemo(
        () =>
            calculateDeliveryTotals(
                totals,
                deliveryState.shippingFee,
                deliveryState.depositAmount
            ),
        [deliveryState.depositAmount, deliveryState.shippingFee, totals]
    );

    useEffect(() => {
        if (
            orderType !== "DELIVERY" ||
            !selectedCustomerId ||
            !deliveryState.selectedDeliveryMode ||
            !deliverySnapshot ||
            !deliverySnapshot.deliveryAddress ||
            !deliverySnapshot.recipientName ||
            !isPhoneValid(deliverySnapshot.recipientPhone) ||
            (deliveryState.deliveryUrgency === "SCHEDULED" &&
                (!deliveryState.requestedDeliveryAt ||
                    Date.parse(deliveryState.requestedDeliveryAt) <= Date.now()))
        ) {
            return undefined;
        }

        const abortController = new AbortController();
        const requestedDeliveryMode = deliveryState.selectedDeliveryMode;
        const timeoutId = window.setTimeout(() => {
            patchCurrentDeliveryState({
                quoteLoading: true,
                quoteError: ""
            });

            void deliveryApi
                .getDeliveryQuotes(
                    {
                        customerId: selectedCustomerId,
                        deliveryMode: requestedDeliveryMode,
                        branchId: currentBranchId,
                        address: deliverySnapshot,
                        recipientAddress: {
                            provinceCode: deliverySnapshot.deliveryProvinceCode,
                            districtCode: deliverySnapshot.deliveryDistrictCode,
                            wardCode: deliverySnapshot.deliveryWardCode,
                            addressLine: deliverySnapshot.deliveryAddress,
                            latitude: deliverySnapshot.deliveryLatitude,
                            longitude: deliverySnapshot.deliveryLongitude
                        },
                        subtotal: totals.subtotal,
                        orderValue: totals.total,
                        deliveryUrgency: deliveryState.deliveryUrgency,
                        urgency: deliveryState.deliveryUrgency,
                        deliveryPriority: deliveryState.deliveryPriority,
                        priority: deliveryState.deliveryPriority,
                        shippingProvider:
                            deliveryState.selectedDeliveryMode === "THIRD_PARTY" &&
                            deliveryState.selectedThirdPartyProvider !== "ALL"
                                ? deliveryState.selectedThirdPartyProvider
                                : null,
                        requestedDeliveryAt:
                            deliveryState.deliveryUrgency === "SCHEDULED"
                                ? deliveryState.requestedDeliveryAt || null
                                : null,
                        items: cartItems.map((item) => ({
                            productVariantId: item.productVariantId ?? item.productId,
                            quantity: item.quantity
                        }))
                    },
                    abortController.signal
                )
                .then((quoteResponse) => {
                    const { options, recommendedOptionId } = quoteResponse;
                    const latestDeliveryState = deliveryStateRef.current;
                    if (
                        latestDeliveryState.selectedDeliveryMode !==
                        requestedDeliveryMode
                    ) {
                        return;
                    }

                    const preferredOption = selectPreferredDeliveryOption({
                        options,
                        recommendedOptionId,
                        urgency: latestDeliveryState.deliveryUrgency,
                        currentOptionId:
                            requestedDeliveryMode === "THIRD_PARTY"
                                ? latestDeliveryState.thirdPartyQuotes.find(
                                      (quote) =>
                                          quote.quoteId ===
                                          latestDeliveryState.selectedThirdPartyQuoteId
                                  )?.optionId ?? null
                                : latestDeliveryState.selectedDeliveryOptionId,
                        currentSelectionSource:
                            latestDeliveryState.deliverySelectionSource,
                        customerAcceptedWait:
                            latestDeliveryState.customerAcceptedDeliveryWait
                    });

                    const nextSelectedOption =
                        requestedDeliveryMode === "INTERNAL"
                            ? options[0] ?? null
                            : preferredOption.option;

                    patchCurrentDeliveryState({
                        quoteLoading: false,
                        internalDeliveryOption:
                            requestedDeliveryMode === "INTERNAL"
                                ? options[0] ?? null
                                : latestDeliveryState.internalDeliveryOption,
                        thirdPartyQuotes:
                            requestedDeliveryMode === "THIRD_PARTY" ? options : [],
                        deliveryOptions: options,
                        selectedDeliveryOptionId: nextSelectedOption?.optionId ?? null,
                        selectedDeliveryQuoteId: nextSelectedOption?.quoteId ?? null,
                        selectedThirdPartyQuoteId:
                            requestedDeliveryMode === "THIRD_PARTY"
                                ? nextSelectedOption?.quoteId ?? null
                                : null,
                        recommendedDeliveryOptionId: recommendedOptionId ?? null,
                        recommendedDeliveryMode:
                            quoteResponse.recommendedMode ?? null,
                        deliveryModeRecommendationReason:
                            quoteResponse.recommendationReason ?? "",
                        deliverySelectionSource:
                            requestedDeliveryMode === "INTERNAL"
                                ? "MANUAL"
                                : preferredOption.source,
                        shippingFee: nextSelectedOption?.shippingFee ?? 0,
                        customerAcceptedDeliveryWait:
                            nextSelectedOption &&
                            isDeliveryOptionSelectable(
                                nextSelectedOption,
                                latestDeliveryState.deliveryUrgency,
                                latestDeliveryState.customerAcceptedDeliveryWait
                            )
                                ? latestDeliveryState.customerAcceptedDeliveryWait
                                : false,
                        quoteError: options.length
                            ? ""
                            : "Không có phương án giao hàng phù hợp."
                    });
                })
                .catch((error: unknown) => {
                    if (abortController.signal.aborted) {
                        return;
                    }

                    if (
                        deliveryStateRef.current.selectedDeliveryMode !==
                        requestedDeliveryMode
                    ) {
                        return;
                    }

                    logApiError(error);
                    patchCurrentDeliveryState({
                        quoteLoading: false,
                        internalDeliveryOption:
                            requestedDeliveryMode === "INTERNAL"
                                ? null
                                : deliveryStateRef.current.internalDeliveryOption,
                        thirdPartyQuotes:
                            requestedDeliveryMode === "THIRD_PARTY"
                                ? []
                                : deliveryStateRef.current.thirdPartyQuotes,
                        deliveryOptions: [],
                        selectedDeliveryOptionId: null,
                        selectedDeliveryQuoteId: null,
                        selectedThirdPartyQuoteId: null,
                        recommendedDeliveryOptionId: null,
                        deliverySelectionSource: "RECOMMENDED",
                        customerAcceptedDeliveryWait: false,
                        shippingFee: 0,
                        quoteError: "Không lấy được báo giá giao hàng."
                    });
                });
        }, 400);

        return () => {
            window.clearTimeout(timeoutId);
            abortController.abort();
        };
    }, [
        cartItems,
        deliverySnapshot,
        deliveryState.deliveryPriority,
        deliveryState.deliveryUrgency,
        deliveryState.quoteRequestedAt,
        deliveryState.requestedDeliveryAt,
        deliveryState.selectedDeliveryMode,
        deliveryState.selectedThirdPartyProvider,
        orderType,
        patchCurrentDeliveryState,
        selectedCustomerId,
        totals.subtotal,
        totals.total
    ]);

    function refreshDeliveryQuote() {
        patchCurrentDeliveryState({
            internalDeliveryOption:
                deliveryState.selectedDeliveryMode === "INTERNAL"
                    ? null
                    : deliveryState.internalDeliveryOption,
            thirdPartyQuotes:
                deliveryState.selectedDeliveryMode === "THIRD_PARTY"
                    ? []
                    : deliveryState.thirdPartyQuotes,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            selectedThirdPartyQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            shippingFee: 0,
            quoteError: "",
            quoteRequestedAt: Date.now()
        });
    }

    async function createDeliveryOrder() {
        if (deliveryState.submitPending) {
            return;
        }

        if (!deliverySnapshot || !deliveryState.selectedDeliveryMode) {
            patchCurrentDeliveryState({
                submitError: "Vui lòng chọn địa chỉ và hình thức vận chuyển."
            });
            return;
        }

        if (!selectedDeliveryOption) {
            patchCurrentDeliveryState({
                submitError:
                    deliveryState.selectedDeliveryMode === "INTERNAL"
                        ? "Chưa có phương án giao nội bộ hợp lệ."
                        : "Vui lòng chọn provider giao hàng."
            });
            return;
        }

        if (isQuoteExpired(selectedDeliveryOption)) {
            patchCurrentDeliveryState({
                submitError: "Báo giá giao hàng đã hết hạn. Vui lòng báo giá lại.",
                deliveryOptions: [],
                selectedDeliveryOptionId: null,
                selectedDeliveryQuoteId: null,
                recommendedDeliveryOptionId: null,
                deliverySelectionSource: "RECOMMENDED",
                customerAcceptedDeliveryWait: false,
                shippingFee: 0,
                quoteError: "Báo giá đã hết hạn."
            });
            return;
        }

        if (
            !isDeliveryOptionSelectable(
                selectedDeliveryOption,
                deliveryState.deliveryUrgency,
                deliveryState.customerAcceptedDeliveryWait
            )
        ) {
            patchCurrentDeliveryState({
                submitError:
                    "Phương thức này chưa thể giao ngay. Cần xác nhận khách đồng ý chờ hoặc chọn phương thức khác."
            });
            return;
        }

        if (!selectedCustomer) {
            patchCurrentDeliveryState({
                submitError: "Vui lòng chọn khách hàng."
            });
            return;
        }

        if (!isPhoneValid(deliverySnapshot.recipientPhone)) {
            patchCurrentDeliveryState({
                submitError: "Số điện thoại người nhận không hợp lệ."
            });
            return;
        }

        patchCurrentDeliveryState({
            submitPending: true,
            submitError: ""
        });

        try {
            const response = await deliveryApi.createDeliveryOrder({
                orderType: "DELIVERY",
                customerId: selectedCustomer.id,
                customerAddressId: deliverySnapshot.customerAddressId,
                recipientName: deliverySnapshot.recipientName,
                recipientPhone: deliverySnapshot.recipientPhone,
                deliveryAddress: deliverySnapshot.deliveryAddress,
                deliveryProvinceCode: deliverySnapshot.deliveryProvinceCode,
                deliveryDistrictCode: deliverySnapshot.deliveryDistrictCode,
                deliveryWardCode: deliverySnapshot.deliveryWardCode,
                deliveryLatitude: deliverySnapshot.deliveryLatitude,
                deliveryLongitude: deliverySnapshot.deliveryLongitude,
                deliveryNote: deliverySnapshot.deliveryNote,
                deliveryUrgency: deliveryState.deliveryUrgency,
                deliveryPriority: deliveryState.deliveryPriority,
                selectedDeliveryOptionId: selectedDeliveryOption.optionId,
                recommendedDeliveryOptionId:
                    deliveryState.recommendedDeliveryOptionId,
                deliverySelectionSource: deliveryState.deliverySelectionSource,
                deliveryMode: selectedDeliveryOption.mode,
                shippingProvider:
                    deliveryState.selectedDeliveryMode === "INTERNAL"
                        ? "INTERNAL"
                        : selectedDeliveryOption.provider,
                shippingServiceCode:
                    deliveryState.selectedDeliveryMode === "THIRD_PARTY"
                        ? selectedDeliveryOption.serviceCode
                        : undefined,
                deliveryQuoteId: selectedDeliveryOption.quoteId,
                shippingFee: deliveryState.shippingFee,
                requestedDeliveryAt:
                    deliveryState.deliveryUrgency === "SCHEDULED"
                        ? deliveryState.requestedDeliveryAt || null
                        : null,
                paymentMethod: deliveryState.paymentMethod,
                depositAmount: deliveryState.depositAmount,
                codAmount: deliveryTotals.codAmount,
                itemTotal: deliveryTotals.itemTotal,
                discountAmount: deliveryTotals.discountAmount,
                grandTotal: deliveryTotals.grandTotal,
                items: cartItems,
                customerSnapshot: selectedCustomer,
                saveAddress: deliveryState.saveAddress,
                setAsDefaultAddress: deliveryState.setAsDefaultAddress,
                addressType: deliveryState.manualAddress.addressType
            });

            resetCurrentSale();
            patchCurrentDeliveryState(initialDeliveryState);
            setCheckoutSuccess(
                response.orderCode
                    ? `Đã tạo đơn giao hàng ${response.orderCode}.`
                    : "Đã tạo đơn giao hàng."
            );
        } catch (error) {
            logApiError(error);

            if (isDeliveryRequoteRequired(error)) {
                const message = getDeliveryRequoteMessage(error);

                patchCurrentDeliveryState({
                    submitError: message,
                    deliveryOptions: [],
                    selectedDeliveryOptionId: null,
                    selectedDeliveryQuoteId: null,
                    recommendedDeliveryOptionId: null,
                    deliverySelectionSource: "RECOMMENDED",
                    customerAcceptedDeliveryWait: false,
                    shippingFee: 0,
                    quoteError: message,
                    quoteRequestedAt: Date.now()
                });
                return;
            }

            patchCurrentDeliveryState({
                submitError: `Tạo đơn giao hàng thất bại: ${getErrorMessage(error)}`
            });
        } finally {
            patchCurrentDeliveryState({
                submitPending: false
            });
        }
    }

    useEffect(() => {
        if (paymentMethod !== "BANK_TRANSFER" || !bankTransferPayment) {
            return undefined;
        }

        const matchedNotification = notifications.find((notification) =>
            isBankTransferNotificationForPayment(notification, bankTransferPayment)
        );

        if (
            !matchedNotification ||
            handledBankNotificationIdRef.current === matchedNotification.id
        ) {
            return undefined;
        }

        handledBankNotificationIdRef.current = matchedNotification.id;
        const nextStatus = notificationToPaymentStatus(matchedNotification);
        let resetTimeoutId: number | undefined;

        const applyNotificationTimeoutId = window.setTimeout(() => {
            setBankTransferStatus(nextStatus);
            setBankTransferEventMessage(matchedNotification.message);

            if (nextStatus !== "RECEIVED") {
                setCheckoutError(
                    matchedNotification.message ||
                    "Thanh toán chuyển khoản chưa hoàn tất."
                );
                setCheckoutSuccess("");
                return;
            }

            setCheckoutSuccess(
                matchedNotification.message ||
                "Thanh toán chuyển khoản thành công."
            );
            setCheckoutError("");

            resetTimeoutId = window.setTimeout(() => {
                resetCurrentSale();
                handledBankNotificationIdRef.current = null;
            }, 1500);
        }, 0);

        return () => {
            window.clearTimeout(applyNotificationTimeoutId);

            if (resetTimeoutId !== undefined) {
                window.clearTimeout(resetTimeoutId);
            }
        };
    }, [bankTransferPayment, notifications, paymentMethod, resetCurrentSale]);

    return (
        <PosShell
            checkoutVariant={orderType === "DELIVERY" ? "delivery" : "default"}
            checkout={
                orderType === "DELIVERY" ? (
                    <DeliveryCheckoutPanel
                        branchLocation={currentBranchLocation}
                        cartItems={cartItems}
                        customers={customers}
                        customersLoading={customersLoading}
                        customersError={customersError}
                        discounts={mockDiscounts}
                        selectedDiscountId={selectedDiscount.id}
                        selectedCustomer={selectedCustomer}
                        state={deliveryState}
                        totals={totals}
                        onCustomerOpen={loadCustomers}
                        onCustomerChange={selectCustomer}
                        onCustomerClear={clearCustomer}
                        onDiscountChange={setSelectedDiscountId}
                        onDeliveryStateChange={setCurrentDeliveryState}
                        onQuoteRefresh={refreshDeliveryQuote}
                        onSubmit={() => {
                            void createDeliveryOrder();
                        }}
                    />
                ) : (
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
                        bankTransferEventMessage={bankTransferEventMessage}
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
                            void prepareBankTransferPayment();
                        }}
                        onCompletePayment={completePayment}
                    />
                )
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
