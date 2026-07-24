import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@hvt/api-client";

import { useAuth } from "../../../auth/auth-context";
import { PosShell } from "../../../components/layout/Pos/PosShell";
import { useNotifications } from "../../notification/context/useNotifications";
import type { NotificationResponse } from "../../notification/types/notification.types";
import { CheckoutPanel } from "../components/checkout/CheckoutPanel";
import { DeliveryCheckoutPanel } from "../components/delivery/DeliveryCheckoutPanel";
import type { PaymentStatusValue } from "../components/dialogs/PaymentStatus";
import { SaleWorkspace } from "../components/workspace/SaleWorkspace";
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
    calculateNetSubtotal,
    calculateSaleTotals
} from "../services/totals";
import {
    posPromotionApi,
    type AvailablePromotionResponse,
    type CouponUsageResponse
} from "../services/posPromotion.api";
import type {
    SaleCartItem,
    SaleDiscountOption,
    SaleCustomer,
    SaleManualDiscount,
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
const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

function getUuid(value: string | null | undefined) {
    return value && uuidPattern.test(value) ? value : undefined;
}

function getPromotionExpiryLabel(value: string | undefined) {
    if (!value) {
        return null;
    }

    const expiryDate = new Date(value);

    if (Number.isNaN(expiryDate.getTime())) {
        return null;
    }

    const daysLeft = Math.ceil(
        (expiryDate.getTime() - Date.now()) / 86_400_000
    );

    if (daysLeft <= 0) {
        return "Đã hết hạn";
    }

    return `Còn ${daysLeft} ngày`;
}

function promotionToDiscount(
    promotion: AvailablePromotionResponse,
    index: number
): SaleDiscountOption {
    const estimatedDiscount = toNumber(promotion.estimatedDiscount);
    const hasDiscount = estimatedDiscount > 0;
    const couponCode = promotion.couponCode?.trim() || undefined;

    return {
        id: promotion.couponId
            ? `coupon:${promotion.couponId}`
            : `promotion:${promotion.promotionId}`,
        label: promotion.promotionName,
        code: couponCode ?? promotion.promotionName,
        type: "AMOUNT",
        value: estimatedDiscount,
        estimatedDiscount,
        description: promotion.requiresCoupon
            ? "Áp dụng bằng mã khuyến mãi"
            : "Khuyến mãi đủ điều kiện cho hóa đơn",
        status: hasDiscount
            ? index === 0
                ? "RECOMMENDED"
                : "AVAILABLE"
            : "INELIGIBLE",
        condition: hasDiscount
            ? "Có thể áp dụng cho hóa đơn hiện tại"
            : "Chưa đủ điều kiện áp dụng",
        minSubtotal: null,
        expiresIn: getPromotionExpiryLabel(promotion.couponExpiresAt),
        promotionId: promotion.promotionId,
        applyMethod: promotion.applyMethod,
        benefitType: promotion.benefitType,
        requiresCoupon: promotion.requiresCoupon,
        couponId: promotion.couponId,
        couponCode
    };
}

function orderToCartItemsById(
    order: PosOrderDto,
    currentItemsById: Record<string, SaleCartItem> = {}
): Record<string, SaleCartItem> {
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
                    inventoryLocationId: null,
                    manualDiscount:
                        currentItemsById[productVariantId]?.manualDiscount ??
                        null
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
    const { userId } = useAuth();
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
    const [promotionDiscounts, setPromotionDiscounts] = useState<
        SaleDiscountOption[]
    >([]);
    const [reservedCouponUsage, setReservedCouponUsage] =
        useState<CouponUsageResponse | null>(null);
    const [manualOrderDiscount, setManualOrderDiscount] =
        useState<SaleManualDiscount | null>(null);
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
    const selectedCustomer =
        selectedCustomersByInvoice[currentInvoiceId] ?? null;
    const selectedCustomerId = selectedCustomer?.id ?? null;
    const discounts = useMemo(
        () => [noDiscount, ...promotionDiscounts],
        [promotionDiscounts]
    );
    const selectedDiscount =
        discounts.find(
            (discount) => discount.id === selectedDiscountId
        ) ?? noDiscount;
    const discountableSubtotal = useMemo(
        () => calculateNetSubtotal(cartItems),
        [cartItems]
    );

    const baseTotals = useMemo(
        () =>
            calculateSaleTotals(
                cartItems,
                selectedDiscount,
                manualOrderDiscount
            ),
        [cartItems, manualOrderDiscount, selectedDiscount]
    );
    const totals: SaleTotals = baseTotals;
    const changeAmount = Math.max(0, cashReceived - totals.total);

    useEffect(() => {
        if (cartItems.length === 0 || discountableSubtotal <= 0) {
            const resetDiscountsTimeoutId = window.setTimeout(() => {
                setPromotionDiscounts([]);
                setSelectedDiscountId("none");
            }, 0);

            return () => window.clearTimeout(resetDiscountsTimeoutId);
        }

        let active = true;
        const timeoutId = window.setTimeout(() => {
            void posPromotionApi
                .preview({
                    orderAmount: discountableSubtotal,
                    customerId: getUuid(selectedCustomerId),
                    branchId: getUuid(currentBranchId),
                    channel: "POS"
                })
                .then((response) => {
                    if (!active) {
                        return;
                    }

                    const nextDiscounts = (
                        response.availablePromotions ?? []
                    ).map(promotionToDiscount);

                    setPromotionDiscounts((currentDiscounts) => {
                        if (
                            selectedDiscountId === "none" ||
                            nextDiscounts.some(
                                (discount) =>
                                    discount.id === selectedDiscountId
                            )
                        ) {
                            return nextDiscounts;
                        }

                        const currentSelectedDiscount = currentDiscounts.find(
                            (discount) =>
                                discount.id === selectedDiscountId
                        );

                        return currentSelectedDiscount
                            ? [...nextDiscounts, currentSelectedDiscount]
                            : nextDiscounts;
                    });
                })
                .catch((error: unknown) => {
                    logApiError(error);
                });
        }, 250);

        return () => {
            active = false;
            window.clearTimeout(timeoutId);
        };
    }, [
        cartItems.length,
        discountableSubtotal,
        selectedCustomerId,
        selectedDiscountId
    ]);

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

    async function reserveSelectedCouponUsage() {
        const couponCode = selectedDiscount.couponCode;

        if (
            !couponCode ||
            selectedDiscount.id === "none" ||
            totals.discountAmount <= 0
        ) {
            return null;
        }

        const orderId = getUuid(draftOrder?.id);
        const customerId = getUuid(selectedCustomerId);

        if (!orderId) {
            throw new Error("Hóa đơn nháp chưa sẵn sàng để giữ mã khuyến mãi.");
        }

        if (!customerId) {
            throw new Error("Vui lòng chọn khách hàng để áp dụng mã khuyến mãi.");
        }

        if (
            reservedCouponUsage?.couponCode === couponCode &&
            reservedCouponUsage.orderId === orderId
        ) {
            return reservedCouponUsage;
        }

        const usage = await posPromotionApi.reserveCoupon({
            couponCode,
            orderId,
            customerId,
            discountAmount: totals.discountAmount,
            branchId: getUuid(currentBranchId),
            cashierId: getUuid(userId),
            channel: "POS"
        });

        setReservedCouponUsage(usage);

        return usage;
    }

    const applyCouponUsage = useCallback(async (usage: CouponUsageResponse | null) => {
        if (!usage) {
            return;
        }

        await posPromotionApi.applyCouponUsage(usage.id);
        setReservedCouponUsage(null);
    }, []);

    const releaseCouponUsage = useCallback(async (
        usage: CouponUsageResponse | null = reservedCouponUsage,
        action: "release" | "cancel" | "expire" = "release"
    ) => {
        if (!usage) {
            return;
        }

        try {
            if (action === "cancel") {
                await posPromotionApi.cancelCouponUsage(usage.id);
            } else if (action === "expire") {
                await posPromotionApi.expireCouponUsage(usage.id);
            } else {
                await posPromotionApi.releaseCouponUsage(usage.id);
            }
        } catch (error) {
            logApiError(error);
        } finally {
            setReservedCouponUsage((currentUsage) =>
                currentUsage?.id === usage.id ? null : currentUsage
            );
        }
    }, [reservedCouponUsage]);

    const resetCurrentSale = useCallback(() => {
        setCartItemsById({});
        setDraftOrder(null);
        setNote("");
        setSelectedDiscountId("none");
        setReservedCouponUsage(null);
        setManualOrderDiscount(null);
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

        if (bankTransferPayment) {
            return;
        }

        setCheckoutError("");
        setCheckoutSuccess("");
        setBankTransferEventMessage("");
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
        handledBankNotificationIdRef.current = null;
        setCheckoutLoading(true);

        let couponUsage: CouponUsageResponse | null = null;

        try {
            couponUsage = await reserveSelectedCouponUsage();
            const checkout = await completePosCheckout({
                orderId: draftOrder?.id ?? null,
                cartItems,
                orderType,
                paymentMethod: "BANK_TRANSFER",
                cashReceived,
                totals,
                originalSubtotal: baseTotals.subtotal,
                selectedDiscount,
                manualOrderDiscount,
                couponUsageId: couponUsage?.id,
                selectedCustomerId
            });

            if (!checkout.payment?.qrUrl) {
                await releaseCouponUsage(couponUsage);
                setCheckoutError("Payment-Service chưa trả về URL QR.");
                return;
            }

            setBankTransferPayment(checkout.payment);
        } catch (error) {
            await releaseCouponUsage(couponUsage);
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    async function completePayment() {
        if (
            paymentMethod === "BANK_TRANSFER" &&
            bankTransferStatus === "RECEIVED"
        ) {
            await confirmBankTransferReceived();
            return;
        }

        if (paymentMethod === "BANK_TRANSFER") {
            if (bankTransferStatus !== "RECEIVED") {
                setCheckoutError("Vui lòng xác nhận đã nhận chuyển khoản.");
                return;
            }
        }

        setCheckoutLoading(true);
        setCheckoutError("");
        setCheckoutSuccess("");

        let couponUsage: CouponUsageResponse | null = null;

        try {
            couponUsage = await reserveSelectedCouponUsage();
            const checkout = await completePosCheckout({
                orderId: draftOrder?.id ?? null,
                cartItems,
                orderType,
                paymentMethod,
                cashReceived,
                totals,
                originalSubtotal: baseTotals.subtotal,
                selectedDiscount,
                manualOrderDiscount,
                couponUsageId: couponUsage?.id,
                selectedCustomerId
            });
            await applyCouponUsage(couponUsage);

            resetCurrentSale();

            setCheckoutSuccess(
                checkout.orderCode
                    ? `Thanh toán thành công đơn ${checkout.orderCode}.`
                    : "Thanh toán thành công."
            );
        } catch (error) {
            await releaseCouponUsage(couponUsage);
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    async function confirmBankTransferReceived() {
        if (checkoutLoading) {
            return;
        }

        setCheckoutLoading(true);
        setCheckoutError("");

        try {
            await applyCouponUsage(reservedCouponUsage);
            setBankTransferStatus("RECEIVED");
            setCheckoutSuccess("Thanh toán chuyển khoản thành công.");

            window.setTimeout(() => {
                resetCurrentSale();
                setCheckoutSuccess("Thanh toán chuyển khoản thành công.");
            }, 1500);
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
            setCheckoutSuccess("");
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
        setCartItemsById((currentItemsById) =>
            orderToCartItemsById(order, currentItemsById)
        );
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

    function changeProductDiscount(
        productId: string,
        discount: SaleManualDiscount | null
    ) {
        setCartItemsById((currentItemsById) => {
            const currentItem = currentItemsById[productId];

            if (!currentItem) {
                return currentItemsById;
            }

            return {
                ...currentItemsById,
                [productId]: {
                    ...currentItem,
                    manualDiscount: discount
                }
            };
        });
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
        setCheckoutError("");
        setCheckoutSuccess("");
    }

    function changeSelectedDiscount(discountId: string) {
        setSelectedDiscountId(discountId);
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
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
            await releaseCouponUsage(reservedCouponUsage, "cancel");

            if (draftOrder?.id && draftOrder.status === "DRAFT") {
                await posOrderApi.cancelDraftOrder(draftOrder.id);
            }
            setDraftOrder(null);
            setCartItemsById({});
            setNote("");
            setCashReceived(0);
            setSelectedDiscountId("none");
            setReservedCouponUsage(null);
            setManualOrderDiscount(null);
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
                void releaseCouponUsage(
                    reservedCouponUsage,
                    nextStatus === "CANCELLED"
                        ? "cancel"
                        : nextStatus === "EXPIRED"
                            ? "expire"
                            : "release"
                );
                setCheckoutError(
                    matchedNotification.message ||
                    "Thanh toán chuyển khoản chưa hoàn tất."
                );
                setCheckoutSuccess("");
                return;
            }

            void applyCouponUsage(reservedCouponUsage).catch(logApiError);

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
    }, [
        applyCouponUsage,
        bankTransferPayment,
        notifications,
        paymentMethod,
        releaseCouponUsage,
        reservedCouponUsage,
        resetCurrentSale
    ]);

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
                        discounts={discounts}
                        selectedDiscountId={selectedDiscount.id}
                        selectedCustomer={selectedCustomer}
                        state={deliveryState}
                        totals={totals}
                        onCustomerOpen={loadCustomers}
                        onCustomerChange={selectCustomer}
                        onCustomerClear={clearCustomer}
                        onDiscountChange={changeSelectedDiscount}
                        onDeliveryStateChange={setCurrentDeliveryState}
                        onQuoteRefresh={refreshDeliveryQuote}
                        onSubmit={() => {
                            void createDeliveryOrder();
                        }}
                    />
                ) : (
                    <CheckoutPanel
                        discounts={discounts}
                        selectedDiscountId={selectedDiscount.id}
                        totals={totals}
                        manualOrderDiscount={manualOrderDiscount}
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
                        onDiscountChange={changeSelectedDiscount}
                        onManualOrderDiscountChange={(discount) => {
                            setManualOrderDiscount(discount);
                            setBankTransferPayment(null);
                            setBankTransferStatus("WAITING");
                            setBankTransferEventMessage("");
                        }}
                        onPaymentMethodChange={changePaymentMethod}
                        onCustomerOpen={loadCustomers}
                        onCustomerChange={selectCustomer}
                        onCustomerClear={clearCustomer}
                        onBankTransferCancel={() => {
                            setBankTransferStatus("CANCELLED");
                            void releaseCouponUsage(
                                reservedCouponUsage,
                                "cancel"
                            );
                        }}
                        onBankTransferConfirm={() => {
                            void confirmBankTransferReceived();
                        }}
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
                onProductDiscountChange={changeProductDiscount}
                onInvoiceClose={() => {
                    void closeCurrentInvoice();
                }}
                onNoteChange={setNote}
                onOrderTypeChange={setOrderType}
            />
        </PosShell>
    );
}
