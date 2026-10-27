import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@/lib/api-client";

import { useNetworkStatus } from "../../../app/network/useNetworkStatus";
import { useAuth } from "../../../auth/auth-context";
import { PosShell } from "../../../components/layout/Pos/PosShell";
import { useNotifications } from "../../notification/context/useNotifications";
import type { NotificationResponse } from "../../notification/types/notification.types";
import { BackorderCheckoutNotice } from "../components/checkout/BackorderCheckoutNotice";
import { CheckoutPanel } from "../components/checkout/CheckoutPanel";
import { DeliveryCheckoutPanel } from "../components/delivery/DeliveryCheckoutPanel";
import {
    BackorderDialog,
    type BackorderOffer
} from "../components/dialogs/BackorderDialog";
import { CustomBundleDialog } from "../components/dialogs/CustomBundleDialog";
import { CustomTeaRequestDialog } from "../components/dialogs/CustomTeaRequestDialog";
import { ReceiptDialog } from "../components/dialogs/ReceiptDialog";
import {
    ShelfShortageDialog,
    type ShelfShortage
} from "../components/dialogs/ShelfShortageDialog";
import { shelfReplenishmentApi } from "../../inventory/services/shelfReplenishment.api";
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
import {
    formatAvailabilityDate,
    isOutOfStockError,
    posBackorderApi,
    type AvailabilityEstimate
} from "../services/posBackorder.api";
import { posCustomBundleApi } from "../services/posCustomBundle.api";
import { formatCurrency } from "../services/money";
import { posCustomerApi } from "../services/posCustomer.api";
import {
    buildLocalPosSale,
    getLocalPosSaleValidationMessage,
    getPosAmountDue,
    completePosCheckout,
    type PosCheckoutPaymentResult
} from "../services/posCheckout.service";
import { posOrderApi, type PosOrderDto } from "../services/posOrder.api";
import { getOfflineOrderDb } from "../offline/offline-order-db";
import { enqueueOfflineOrder } from "../offline/offline-order-queue";
import { getOfflineOrderSync } from "../offline/offline-order-sync.instance";
import type { SalesOrderDetail } from "../../order/types/sales-history.types";
import { useProductSearch } from "../hooks/useProductSearch";
import { posProductApi } from "../services/posProduct.api";
import {
    loadPersistedSale,
    savePersistedSale
} from "../store/sale-persistence";
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
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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

/** Thao tác đang chờ khách quyết định có đặt trước mặt hàng thiếu hay không. */
type BackorderAction =
    | { kind: "add"; product: SaleProductPreview }
    | { kind: "update"; productId: string; quantity: number };

interface PendingBackorderOffer extends BackorderOffer {
    variantId: string;
    action: BackorderAction;
}

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
    const giftQuantity = toNumber(promotion.giftQuantity);
    const hasBenefit = estimatedDiscount > 0 || giftQuantity > 0;
    const isGift = giftQuantity > 0;
    const couponCode = promotion.couponCode?.trim() || undefined;

    return {
        id: promotion.couponId
            ? `coupon:${promotion.couponId}`
            : `promotion:${promotion.promotionId}`,
        label: promotion.promotionName,
        code: couponCode ?? promotion.promotionName,
        type: isGift ? "GIFT" : "AMOUNT",
        value: estimatedDiscount,
        estimatedDiscount,
        description: isGift
            ? promotion.buyQuantity
                ? `Mua ${promotion.buyQuantity} tặng ${giftQuantity} sản phẩm`
                : `Tặng ${giftQuantity} sản phẩm`
            : promotion.requiresCoupon
              ? "Áp dụng bằng mã khuyến mãi"
              : "Khuyến mãi đủ điều kiện cho hóa đơn",
        status: hasBenefit
            ? index === 0
                ? "RECOMMENDED"
                : "AVAILABLE"
            : "INELIGIBLE",
        condition: hasBenefit
            ? "Có thể áp dụng cho hóa đơn hiện tại"
            : "Chưa đủ điều kiện áp dụng",
        minSubtotal: null,
        expiresIn: getPromotionExpiryLabel(promotion.couponExpiresAt),
        promotionId: promotion.promotionId,
        applyMethod: promotion.applyMethod,
        benefitType: promotion.benefitType,
        requiresCoupon: promotion.requiresCoupon,
        couponId: promotion.couponId,
        couponCode,
        giftProductId: promotion.giftProductId,
        giftQuantity,
        buyQuantity: promotion.buyQuantity
    };
}

function orderToCartItemsById(
    order: PosOrderDto,
    currentItemsById: Record<string, SaleCartItem> = {}
): Record<string, SaleCartItem> {
    const items = order.items ?? [];
    const currentItems = Object.values(currentItemsById);

    return Object.fromEntries(
        items.map((item) => {
            const productVariantId = item.productVariantId;
            const quantity = toNumber(item.quantity);
            const unitPrice = toNumber(item.actualUnitPrice ?? item.unitPrice);
            const note = item.note?.trim() || undefined;
            const cartKey = item.id ?? `${productVariantId}:${note ?? ""}`;
            const currentItem =
                currentItemsById[cartKey] ??
                currentItems.find((candidate) =>
                    candidate.itemId === item.id ||
                    (
                        candidate.productVariantId === productVariantId &&
                        (candidate.note?.trim() || undefined) === note
                    )
                );

            return [
                cartKey,
                {
                    itemId: item.id,
                    productId: cartKey,
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
                        currentItem?.manualDiscount ?? null,
                    note,
                    bundle:
                        item.itemType === "CUSTOM_BUNDLE"
                            ? {
                                  components: (item.components ?? []).map((component) => ({
                                      ingredientId: component.ingredientId,
                                      ingredientName: component.ingredientName,
                                      unit: component.unit,
                                      quantity: toNumber(component.quantity),
                                      lineTotal: toNumber(component.lineTotal)
                                  })),
                                  packingStatus: item.packingStatus,
                                  packingCode: item.packingCode
                              }
                            : null
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
            "ORDER_PAYMENT_SUCCEEDED",
            "ORDER_PAYMENT_NEED_REVIEW",
            "ORDER_COMPLETED",
            "ORDER_PAYMENT_FAILED",
            "ORDER_CANCELLED",
            "ORDER_STATUS_CHANGED"
        ].includes(notification.type)
    );
}

/** Máy chủ giữ đơn chờ kiểm tra tay (tiền lệch / về muộn): chưa thành công, cũng chưa thất bại. */
function isPaymentNeedReviewNotification(notification: NotificationResponse) {
    return notification.type === "ORDER_PAYMENT_NEED_REVIEW";
}

function notificationToPaymentStatus(
    notification: NotificationResponse
): PaymentStatusValue {
    if (
        notification.type === "ORDER_PAYMENT_SUCCEEDED" ||
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

function getBackorderSuccessMessage(
    orderCode: string,
    expectedAvailableAt: string | null,
    debtAmount: number
) {
    const payment =
        debtAmount > 0
            ? `Đã thu cọc, còn lại ${formatCurrency(debtAmount)} thu khi giao hàng.`
            : "Đã thu đủ tiền.";

    return `Đã ghi nhận đơn chờ hàng${orderCode ? ` ${orderCode}` : ""}. Dự kiến có hàng ${formatAvailabilityDate(expectedAvailableAt)}. ${payment} Giao hàng tại mục Đơn chờ hàng.`;
}

export function PosSalePage() {
    const { userId, keycloakSubject, displayName } = useAuth();
    const { online } = useNetworkStatus();
    // Khoá chủ sở hữu giỏ: dùng subject Keycloak vì có ngay từ lúc mount và
    // không đổi trong phiên; userId từ identity-service có thể tải xong sau
    // (nếu dùng nó, lần render đầu sẽ coi snapshot là của người khác và xoá).
    const saleOwnerId = keycloakSubject ?? userId ?? null;
    // Đọc một lần khi mount: đơn đang bán của chính thu ngân này trên máy này
    // (giữ lại sau khi mất mạng, reload hay tắt nhầm tab).
    const [persistedSale] = useState(() => loadPersistedSale(saleOwnerId));
    const [searchQuery, setSearchQuery] = useState("");
    const [customTeaOpen, setCustomTeaOpen] = useState(false);
    const [customBundleOpen, setCustomBundleOpen] = useState(false);
    const [customBundleSubmitting, setCustomBundleSubmitting] = useState(false);
    const [shelfShortage, setShelfShortage] = useState<ShelfShortage | null>(null);
    const [shelfShortageSubmitting, setShelfShortageSubmitting] = useState(false);
    const [shelfShortageError, setShelfShortageError] = useState("");
    const [backorderOffer, setBackorderOffer] = useState<PendingBackorderOffer | null>(null);
    const [backorderSubmitting, setBackorderSubmitting] = useState(false);
    const [backorderError, setBackorderError] = useState("");
    /** Ước tính ngày có hàng của cả đơn; khác null nghĩa là đơn đang có mặt hàng khách đồng ý chờ. */
    const [backorderEstimate, setBackorderEstimate] = useState<AvailabilityEstimate | null>(null);
    const [depositEnabled, setDepositEnabled] = useState(false);
    const [depositAmount, setDepositAmount] = useState(0);
    /** Mặt hàng khách đã đồng ý chờ trong đơn hiện tại: các lần đổi số lượng sau không hỏi lại. */
    const backorderVariantIdsRef = useRef<Set<string>>(new Set());
    /** Đơn chuyển khoản đang chờ tiền là đơn chờ hàng: lưu ngày dự kiến để báo sau khi nhận tiền. */
    const bankTransferBackorderRef = useRef<{ expectedAvailableAt: string | null } | null>(null);
    // Tồn kệ / kho của các mặt hàng đã thêm vào giỏ, để so khi tăng số lượng.
    const stockByVariantIdRef = useRef<Record<string, SaleProductPreview>>({});
    // Số thiếu đã có phiếu nhập kệ cho từng mặt hàng trong đơn hiện tại, tránh hỏi lại cùng một số.
    const replenishedMissingRef = useRef<Record<string, number>>({});
    // Phương án nhân viên đã chọn cho mặt hàng (true = gửi kho ngay, false = lưu nháp), để lần tăng số lượng sau
    // tự cập nhật phiếu đã có thay vì hỏi lại.
    const replenishmentModeRef = useRef<Record<string, boolean>>({});
    const [customTeaSearchQuery, setCustomTeaSearchQuery] = useState("");
    // Tìm mặt hàng qua TanStack Query: debounce, cache theo từ khoá, giữ kết
    // quả cũ khi mất mạng.
    const productSearch = useProductSearch(searchQuery);
    const customTeaSearch = useProductSearch(customTeaSearchQuery, customTeaOpen);
    const searchResults = productSearch.products;
    const searchLoading = productSearch.loading;
    const searchError = productSearch.error
        ? getErrorMessage(productSearch.error)
        : "";
    const customTeaSearchResults = customTeaSearch.products;
    const customTeaSearchLoading = customTeaSearch.loading;
    const customTeaSearchError = customTeaSearch.error
        ? getErrorMessage(customTeaSearch.error)
        : "";
    const [customTeaSubmitLoading, setCustomTeaSubmitLoading] = useState(false);
    const [addingProductId, setAddingProductId] = useState<string | null>(
        null
    );
    const [cartItemsById, setCartItemsById] = useState<
        Record<string, SaleCartItem>
    >(persistedSale?.cartItemsById ?? initialCartItems);
    const [draftOrder, setDraftOrder] = useState<PosOrderDto | null>(
        persistedSale?.draftOrder ?? null
    );

    // Đơn mới thì bắt đầu theo dõi phiếu nhập kệ lại từ đầu.
    useEffect(() => {
        replenishedMissingRef.current = {};
        replenishmentModeRef.current = {};
    }, [draftOrder?.id]);
    const [note, setNote] = useState(persistedSale?.note ?? "");
    const [orderType, setOrderType] =
        useState<SaleOrderType>(persistedSale?.orderType ?? "QUICK_SALE");
    const [selectedDiscountId, setSelectedDiscountId] =
        useState(persistedSale?.selectedDiscountId ?? "none");
    const [promotionDiscounts, setPromotionDiscounts] = useState<
        SaleDiscountOption[]
    >([]);
    const [reservedCouponUsage, setReservedCouponUsage] =
        useState<CouponUsageResponse | null>(null);
    const [manualOrderDiscount, setManualOrderDiscount] =
        useState<SaleManualDiscount | null>(
            persistedSale?.manualOrderDiscount ?? null
        );
    const [cashReceived, setCashReceived] = useState(
        persistedSale?.cashReceived ?? 0
    );
    // Luôn về tiền mặt khi khôi phục: phiên chuyển khoản (QR, mã tham chiếu)
    // không dựng lại được, dù snapshot có lưu paymentMethod để đối soát.
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
            [currentInvoiceId]: persistedSale?.selectedCustomer ?? null
        });
    const [deliveryStateByInvoice, setDeliveryStateByInvoice] =
        useState<Record<string, DeliveryCheckoutState>>({
            [currentInvoiceId]: initialDeliveryState
        });
    const [checkoutLoading, setCheckoutLoading] = useState(false);
    const [checkoutError, setCheckoutError] = useState("");
    const [checkoutSuccess, setCheckoutSuccess] = useState(
        persistedSale
            ? `Đã khôi phục đơn đang bán${
                  persistedSale.draftOrder?.orderCode
                      ? ` ${persistedSale.draftOrder.orderCode}`
                      : ""
              } (${Object.keys(persistedSale.cartItemsById).length} mặt hàng).`
            : ""
    );
    const [receiptOrderId, setReceiptOrderId] = useState<string | null>(null);
    /** Hoá đơn mã tạm của đơn vừa lưu khi mất mạng. */
    const [offlineReceipt, setOfflineReceipt] = useState<SalesOrderDetail | null>(null);
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

    const cartItems = useMemo(
        () => Object.values(cartItemsById),
        [cartItemsById]
    );
    /**
     * Giỏ có dòng chưa nằm trong đơn nháp trên máy chủ (thêm/sửa khi mất mạng).
     * Khi đó thanh toán đi đường "một phát" kèm clientOrderId thay vì checkout đơn nháp.
     */
    const hasLocalLines = cartItems.some((item) => !item.itemId);
    const selectedCustomer =
        selectedCustomersByInvoice[currentInvoiceId] ?? null;
    const selectedCustomerId = selectedCustomer?.id ?? null;

    // Lưu đơn đang bán để mất mạng / reload không mất giỏ. Giỏ rỗng = xoá.
    useEffect(() => {
        savePersistedSale({
            userId: saleOwnerId,
            draftOrder,
            cartItemsById,
            note,
            orderType,
            paymentMethod,
            cashReceived,
            selectedDiscountId,
            manualOrderDiscount,
            selectedCustomer
        });
    }, [
        cartItemsById,
        cashReceived,
        draftOrder,
        manualOrderDiscount,
        note,
        orderType,
        paymentMethod,
        saleOwnerId,
        selectedCustomer,
        selectedDiscountId
    ]);
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
    const depositActive =
        backorderEstimate !== null && depositEnabled && paymentMethod === "CASH";
    const amountDue = getPosAmountDue({
        paymentMethod,
        totals,
        allowBackorder: backorderEstimate !== null,
        depositAmount: depositActive ? depositAmount : 0
    });
    const depositInvalid =
        depositActive && (depositAmount <= 0 || depositAmount >= totals.total);
    const changeAmount = Math.max(0, cashReceived - amountDue);

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
                    channel: "POS",
                    customerOrderCount: selectedCustomer?.orderCount,
                    lines: cartItems.flatMap((item) => {
                        const productId = getUuid(
                            item.productVariantId ?? item.productId
                        );
                        return productId
                            ? [{
                                  productId,
                                  quantity: item.quantity,
                                  lineAmount: item.unitPrice * item.quantity
                              }]
                            : [];
                    })
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
        cartItems,
        cartItems.length,
        discountableSubtotal,
        selectedCustomerId,
        selectedCustomer?.orderCount,
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
        setBackorderOffer(null);
        setBackorderEstimate(null);
        setDepositEnabled(false);
        setDepositAmount(0);
        backorderVariantIdsRef.current = new Set();
        bankTransferBackorderRef.current = null;
        setNote("");
        setSelectedDiscountId("none");
        setReservedCouponUsage(null);
        setManualOrderDiscount(null);
        setCashReceived(0);
        setPaymentMethod("CASH");
        setBankTransferStatus("WAITING");
        setBankTransferPayment(null);
        setBankTransferEventMessage("");
        setCustomTeaOpen(false);
        setCustomTeaSearchQuery("");
        setCustomBundleOpen(false);
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
                selectedCustomerId,
                allowBackorder: backorderEstimate !== null
            });
            bankTransferBackorderRef.current = checkout.backorder
                ? { expectedAvailableAt: checkout.expectedAvailableAt }
                : null;

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

    /**
     * Bán với giỏ cục bộ: mất mạng thì lưu vào hàng chờ IndexedDB và in hoá đơn mã tạm;
     * có mạng (giỏ có dòng thêm khi offline) thì gửi một phát lên máy chủ. Cả hai đều
     * mang clientOrderId nên gửi lại không tạo đơn trùng.
     */
    async function completeLocalSale() {
        const validationMessage = getLocalPosSaleValidationMessage({
            cartItems,
            orderType,
            paymentMethod,
            cashReceived,
            totals,
            selectedDiscount,
            offline: !online
        });

        if (validationMessage) {
            setCheckoutError(validationMessage);
            return;
        }

        setCheckoutLoading(true);
        setCheckoutError("");
        setCheckoutSuccess("");

        const record = buildLocalPosSale({
            cartItems,
            orderType,
            paymentMethod,
            cashReceived,
            totals,
            originalSubtotal: baseTotals.subtotal,
            selectedDiscount,
            manualOrderDiscount,
            selectedCustomer,
            cashierId: getUuid(userId) ?? null,
            cashierName: displayName ?? null,
            ownerId: saleOwnerId,
            branchId: currentBranchId,
            staleDraftOrderId:
                draftOrder?.id && draftOrder.status === "DRAFT" ? draftOrder.id : null,
            offline: !online
        });

        try {
            if (online) {
                try {
                    const order = await posOrderApi.checkoutOffline(record.request);

                    if (record.staleDraftOrderId) {
                        void posOrderApi
                            .cancelDraftOrder(record.staleDraftOrderId)
                            .catch(logApiError);
                    }

                    resetCurrentSale();
                    setCheckoutSuccess(`Thanh toán thành công đơn ${order.orderCode}.`);
                    setReceiptOrderId(order.id);
                    return;
                } catch (error) {
                    if (!(error instanceof ApiError && error.isNetworkError)) {
                        throw error;
                    }
                    // Mạng rớt đúng lúc gửi: chuyển sang lưu offline. Máy chủ có thể đã
                    // nhận đơn, nhưng replay cùng clientOrderId chỉ trả về đơn đó.
                }
            }

            await enqueueOfflineOrder(getOfflineOrderDb(), {
                ...record,
                request: { ...record.request, offlineSale: true }
            });
            void getOfflineOrderSync().notifyEnqueued();
            resetCurrentSale();
            setCheckoutSuccess(
                `Đã lưu đơn ${record.tempCode} trên máy (${formatCurrency(record.totalAmount)}). Sẽ tự đồng bộ lên máy chủ khi có mạng.`
            );
            setOfflineReceipt(record.receipt);
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    async function completePayment() {
        if (!online || hasLocalLines) {
            await completeLocalSale();
            return;
        }

        if (paymentMethod === "BANK_TRANSFER") {
            // Chuyển khoản hoàn tất khi máy chủ xác nhận tiền về (thông báo SePay hoặc kiểm tra lại).
            await refreshBankTransferStatus();
            return;
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
                selectedCustomerId,
                allowBackorder: backorderEstimate !== null,
                depositAmount: depositActive ? depositAmount : undefined
            });
            // Mã giảm giá được máy chủ đánh dấu đã dùng sau khi thanh toán; POS không gọi thêm.
            setReservedCouponUsage(null);

            const paidOrderId =
                draftOrder?.id ?? checkout.payment?.orderId ?? null;
            const bundlePending = cartItems.some((item) => Boolean(item.bundle));

            resetCurrentSale();

            if (checkout.backorder) {
                // Đơn chờ hàng chưa giao nên chưa in hóa đơn; in khi giao ở màn hình Đơn chờ hàng.
                setCheckoutSuccess(
                    getBackorderSuccessMessage(
                        checkout.orderCode,
                        checkout.expectedAvailableAt,
                        checkout.debtAmount
                    )
                );
                return;
            }

            setCheckoutSuccess(
                (checkout.orderCode
                    ? `Thanh toán thành công đơn ${checkout.orderCode}.`
                    : "Thanh toán thành công.") +
                    (bundlePending
                        ? " Trà đóng theo yêu cầu đang chờ kho đóng gói, giao ở màn Bundle chờ giao."
                        : "")
            );
            setReceiptOrderId(paidOrderId);
        } catch (error) {
            await releaseCouponUsage(couponUsage);
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    /**
     * Máy chủ đã xác nhận tiền về (qua thông báo SePay hoặc khi POS hỏi lại). POS không tự đánh dấu
     * "đã nhận tiền": chỉ máy chủ mới chuyển đơn sang đã thanh toán.
     */
    const applyBankTransferPaid = useCallback((orderCode: string, serverMessage?: string) => {
        setBankTransferStatus("RECEIVED");
        setReservedCouponUsage(null);
        const waitingStock = bankTransferBackorderRef.current;
        const successMessage = waitingStock
            ? getBackorderSuccessMessage(orderCode, waitingStock.expectedAvailableAt, 0)
            : serverMessage || `Thanh toán chuyển khoản thành công đơn ${orderCode}.`;
        setCheckoutSuccess(successMessage);
        setCheckoutError("");

        if (!waitingStock) {
            setReceiptOrderId(bankTransferPayment?.orderId ?? draftOrder?.id ?? null);
        }

        window.setTimeout(() => {
            resetCurrentSale();
            handledBankNotificationIdRef.current = null;
            setCheckoutSuccess(successMessage);
        }, 1500);
    }, [bankTransferPayment?.orderId, draftOrder?.id, resetCurrentSale]);

    /**
     * Phiên chuyển khoản kết thúc không có tiền (QR hết hạn / thu ngân huỷ): máy chủ đã trả đơn về nháp,
     * POS đồng bộ lại để chọn phương thức khác với cùng giỏ hàng.
     */
    const applyBankTransferSessionEnded = useCallback((
        nextStatus: PaymentStatusValue,
        message: string,
        order: PosOrderDto | null
    ) => {
        setBankTransferStatus(nextStatus);
        setBankTransferPayment(null);
        setBankTransferEventMessage(message);
        setCheckoutError(message);
        setCheckoutSuccess("");

        if (order) {
            setDraftOrder(order);
        } else {
            setDraftOrder((current) => (current ? { ...current, status: "DRAFT" } : current));
        }
    }, []);

    /** Hỏi máy chủ trạng thái thật của đơn thay vì tin vào thao tác tay của thu ngân. */
    async function refreshBankTransferStatus() {
        const orderId = bankTransferPayment?.orderId ?? draftOrder?.id;

        if (!orderId || checkoutLoading) {
            return;
        }

        setCheckoutLoading(true);
        setCheckoutError("");

        try {
            const order = await posOrderApi.getOrder(orderId);

            if (order.paymentStatus === "PAID") {
                applyBankTransferPaid(order.orderCode);
                return;
            }

            if (order.paymentStatus === "NEED_REVIEW") {
                const message =
                    "Tiền đã về nhưng lệch số tiền hoặc về sau khi QR hết hạn. Đơn được giữ lại, chờ quản lý đối soát.";
                setBankTransferEventMessage(message);
                setCheckoutError(message);
                return;
            }

            if (order.status === "DRAFT") {
                applyBankTransferSessionEnded(
                    "EXPIRED",
                    "Phiên chuyển khoản đã kết thúc. Vui lòng chọn lại phương thức thanh toán.",
                    order
                );
                return;
            }

            setBankTransferEventMessage("Chưa nhận được xác nhận từ ngân hàng. Vui lòng chờ hoặc kiểm tra lại sau.");
        } catch (error) {
            logApiError(error);
            setCheckoutError(getErrorMessage(error));
        } finally {
            setCheckoutLoading(false);
        }
    }

    /** Thu ngân huỷ phiên chuyển khoản: máy chủ đóng QR, đơn về nháp, chọn lại phương thức. */
    async function cancelBankTransferPayment() {
        const orderId = bankTransferPayment?.orderId ?? draftOrder?.id;

        void releaseCouponUsage(reservedCouponUsage, "cancel");

        if (!orderId || !bankTransferPayment) {
            setBankTransferStatus("CANCELLED");
            setBankTransferPayment(null);
            return;
        }

        setCheckoutLoading(true);

        try {
            const order = await posOrderApi.cancelPosPayment(orderId);
            applyBankTransferSessionEnded(
                "CANCELLED",
                "Đã huỷ thanh toán chuyển khoản. Giỏ hàng được giữ nguyên, chọn lại phương thức thanh toán.",
                order
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
        setCartItemsById((currentItemsById) =>
            orderToCartItemsById(order, currentItemsById)
        );
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
    }

    /**
     * Sau mỗi lần giỏ thay đổi: nếu tổng số đặt của mặt hàng vượt số trên kệ thì hỏi tạo phiếu nhập kệ.
     * Chỉ hỏi một lần cho mỗi mặt hàng trong đơn; các lần tăng số lượng sau tự cập nhật số thiếu trên phiếu đã có.
     */
    async function checkShelfShortage(order: PosOrderDto, variantId: string) {
        const lines = (order.items ?? []).filter(
            (item) => item.productVariantId === variantId
        );
        const requestedQuantity = lines.reduce(
            (total, item) => total + toNumber(item.quantity),
            0
        );

        if (requestedQuantity <= 0) {
            delete replenishedMissingRef.current[variantId];
            delete replenishmentModeRef.current[variantId];
            return;
        }

        let stock = stockByVariantIdRef.current[variantId];

        if (!stock) {
            try {
                stock = await posProductApi.getPosView(variantId);
                stockByVariantIdRef.current[variantId] = stock;
            } catch (error) {
                logApiError(error);
                return;
            }
        }

        const missingQuantity = requestedQuantity - stock.shelfQuantity;

        if (
            missingQuantity <= 0 ||
            missingQuantity <= (replenishedMissingRef.current[variantId] ?? 0)
        ) {
            return;
        }

        const shortage: ShelfShortage = {
            productVariantId: variantId,
            name: lines[0]?.productNameSnapshot ?? lines[0]?.productName ?? stock.name,
            sku: lines[0]?.skuSnapshot ?? lines[0]?.sku ?? stock.sku,
            requestedQuantity,
            shelfQuantity: stock.shelfQuantity,
            warehouseQuantity: stock.warehouseQuantity,
            missingQuantity,
            shelfLocationId: stock.shelfLocationId,
            warehouseLocationId: stock.warehouseLocationId
        };
        const chosenMode = replenishmentModeRef.current[variantId];

        if (chosenMode !== undefined) {
            try {
                await saveShelfReplenishment(shortage, chosenMode, order.id);
            } catch (error) {
                logApiError(error);
                setCheckoutError(
                    `Không thể cập nhật phiếu nhập kệ: ${getErrorMessage(error)}`
                );
            }
            return;
        }

        setShelfShortageError("");
        setShelfShortage(shortage);
    }

    /** Backend gộp theo referenceId (mã đơn) nên gọi lại chỉ cập nhật số lượng trên phiếu đang mở của đơn. */
    async function saveShelfReplenishment(
        shortage: ShelfShortage,
        submit: boolean,
        orderId: string | null
    ) {
        const slip = await shelfReplenishmentApi.create({
            shelfId: shortage.shelfLocationId ?? null,
            warehouseId: shortage.warehouseLocationId ?? null,
            branchId: currentBranchId,
            referenceId: orderId,
            note: `POS: thiếu trên kệ khi bán ${shortage.name}`,
            submit,
            lines: [
                {
                    productVariantId: shortage.productVariantId,
                    quantity: shortage.missingQuantity
                }
            ]
        });
        replenishedMissingRef.current[shortage.productVariantId] =
            shortage.missingQuantity;
        replenishmentModeRef.current[shortage.productVariantId] = submit;
        setCheckoutError("");
        setCheckoutSuccess(
            submit
                ? `Phiếu nhập kệ ${slip.documentCode}: cần đưa ${shortage.missingQuantity} ${shortage.name} lên kệ, đã gửi kho.`
                : `Đã lưu nháp phiếu nhập kệ ${slip.documentCode} (thiếu ${shortage.missingQuantity} ${shortage.name}).`
        );
    }

    async function createShelfReplenishment(submit: boolean) {
        if (!shelfShortage || shelfShortageSubmitting) {
            return;
        }

        setShelfShortageSubmitting(true);
        setShelfShortageError("");

        try {
            await saveShelfReplenishment(
                shelfShortage,
                submit,
                draftOrder?.id ?? null
            );
            setShelfShortage(null);
        } catch (error) {
            logApiError(error);
            setShelfShortageError(
                `Không thể tạo phiếu nhập kệ: ${getErrorMessage(error)}`
            );
        } finally {
            setShelfShortageSubmitting(false);
        }
    }

    /** Tổng số đang đặt của một mặt hàng trong giỏ (một mặt hàng có thể nằm ở nhiều dòng khác ghi chú). */
    function getCartQuantity(variantId: string, excludeProductId?: string) {
        return Object.values(cartItemsById)
            .filter(
                (item) =>
                    (item.productVariantId ?? item.productId) === variantId &&
                    item.productId !== excludeProductId
            )
            .reduce((sum, item) => sum + item.quantity, 0);
    }

    /**
     * Mặt hàng không đủ để bán ngay: hỏi kho ngày dự kiến có hàng (đang sản xuất, đủ nguyên liệu, chờ phiếu
     * nhập hay phải nhập mới) rồi để khách quyết định có đặt trước hay không.
     */
    async function offerBackorder(
        action: BackorderAction,
        variantId: string,
        name: string,
        sku: string,
        requestedQuantity: number
    ) {
        try {
            const estimate = await posBackorderApi.estimate({
                branchId: currentBranchId,
                orderId: draftOrder?.id ?? null,
                lines: [{ productVariantId: variantId, quantity: requestedQuantity }]
            });
            const line = estimate.lines.find(
                (item) => item.productVariantId === variantId
            );

            if (!line || line.scenario === "AVAILABLE") {
                setCheckoutError("Tồn kho vừa thay đổi. Vui lòng thử lại.");
                return;
            }

            setCheckoutError("");
            setBackorderError("");
            setBackorderOffer({ variantId, name, sku, estimate: line, action });
        } catch (error) {
            logApiError(error);
            setCheckoutError(
                `Không ước tính được ngày có hàng: ${getErrorMessage(error)}`
            );
        }
    }

    /** Cập nhật ngày dự kiến có hàng của cả đơn sau mỗi lần giỏ thay đổi. */
    async function refreshBackorderEstimate(order: PosOrderDto) {
        // Trà đóng theo yêu cầu: tồn tính theo nguyên liệu, không theo variant hệ thống của dòng bundle.
        const bundleItems = (order.items ?? []).filter((item) => item.itemType === "CUSTOM_BUNDLE");

        if (backorderVariantIdsRef.current.size === 0 && bundleItems.length === 0) {
            if (backorderEstimate !== null) {
                setBackorderEstimate(null);
                setDepositEnabled(false);
                setDepositAmount(0);
            }
            return;
        }

        const quantityByVariant = new Map<string, number>();

        for (const item of order.items ?? []) {
            if (item.itemType === "CUSTOM_BUNDLE") {
                continue;
            }
            quantityByVariant.set(
                item.productVariantId,
                (quantityByVariant.get(item.productVariantId) ?? 0) + Number(item.quantity)
            );
        }

        const clearBackorder = () => {
            backorderVariantIdsRef.current = new Set();
            setBackorderEstimate(null);
            setDepositEnabled(false);
            setDepositAmount(0);
        };

        if (quantityByVariant.size === 0 && bundleItems.length === 0) {
            clearBackorder();
            return;
        }

        try {
            const productEstimate =
                backorderVariantIdsRef.current.size > 0 && quantityByVariant.size > 0
                    ? await posBackorderApi.estimate({
                        branchId: currentBranchId,
                        orderId: order.id,
                        lines: Array.from(quantityByVariant, ([productVariantId, quantity]) => ({
                            productVariantId,
                            quantity
                        }))
                    })
                    : null;
            const bundleAvailabilities = await Promise.all(
                bundleItems.map(async (item) => ({
                    item,
                    availability: await posCustomBundleApi.checkAvailability({
                        branchId: currentBranchId,
                        lines: (item.components ?? []).map((component) => ({
                            ingredientId: component.ingredientId,
                            ingredientName: component.ingredientName,
                            quantity: toNumber(component.quantity)
                        }))
                    })
                }))
            );
            const waitingBundles = bundleAvailabilities.filter(({ availability }) => !availability.enough);
            const productBackorder = productEstimate?.backorder === true;

            if (!productBackorder && waitingBundles.length === 0) {
                clearBackorder();
                return;
            }

            const dates = [
                productBackorder ? productEstimate?.estimatedAvailableAt : null,
                ...waitingBundles.map(({ availability }) => availability.estimatedAvailableAt)
            ].filter((value): value is string => Boolean(value)).sort();

            setBackorderEstimate({
                backorder: true,
                estimatedAvailableAt: dates.length > 0 ? dates[dates.length - 1] : null,
                lines: [
                    ...(productBackorder && productEstimate ? productEstimate.lines : []),
                    ...waitingBundles.map(({ item, availability }) => ({
                        productVariantId: item.productVariantId,
                        itemName: `${item.variantNameSnapshot ?? "Trà đóng theo yêu cầu"} (nguyên liệu)`,
                        requestedQuantity: 1,
                        missingQuantity: availability.lines.reduce(
                            (total, line) => total + toNumber(line.missingQuantity),
                            0
                        ),
                        scenario: "WAIT_MATERIAL_RECEIPT" as const,
                        estimatedAvailableAt: availability.estimatedAvailableAt,
                        depositRecommended: true
                    }))
                ]
            });
        } catch (error) {
            logApiError(error);
        }
    }

    async function addCustomBundle(input: {
        bundleName: string;
        note: string;
        lines: Array<{ ingredientId: string; ingredientName: string; quantity: number }>;
    }) {
        if (customBundleSubmitting) {
            return;
        }

        if (!online) {
            setCheckoutError("Đóng trà theo yêu cầu cần kết nối mạng để kiểm tra nguyên liệu.");
            return;
        }

        setCustomBundleSubmitting(true);

        try {
            const order = await posCustomBundleApi.addDraftBundleItem({
                orderId: draftOrder?.id ?? null,
                branchId: currentBranchId,
                customerId: selectedCustomerId,
                bundleName: input.bundleName,
                note: input.note,
                lines: input.lines
            });
            applyDraftOrder(order);
            setCustomBundleOpen(false);
            setCheckoutError("");
            setCheckoutSuccess("");
            void refreshBackorderEstimate(order);
        } catch (error) {
            logApiError(error);
            setCheckoutError(
                `Không thể thêm trà đóng theo yêu cầu: ${getErrorMessage(error)}`
            );
        } finally {
            setCustomBundleSubmitting(false);
        }
    }

    function clearBankTransferAndMessages() {
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
        setCheckoutError("");
        setCheckoutSuccess("");
    }

    /**
     * Thêm vào giỏ cục bộ (mất mạng): không gọi API đơn nháp. Dòng cùng mặt hàng và
     * cùng ghi chú thì cộng số lượng; dòng vốn là của đơn nháp thì bỏ liên kết (itemId).
     */
    function upsertLocalCartItem(
        product: SaleProductPreview,
        quantity: number,
        note?: string
    ) {
        const variantId = product.productVariantId ?? product.id;
        const normalizedNote = note?.trim() || undefined;

        setCartItemsById((currentItemsById) => {
            const existingKey = Object.keys(currentItemsById).find((key) => {
                const item = currentItemsById[key];

                return (
                    (item.productVariantId ?? item.productId) === variantId &&
                    (item.note?.trim() || undefined) === normalizedNote
                );
            });

            if (existingKey) {
                const existing = currentItemsById[existingKey];

                return {
                    ...currentItemsById,
                    [existingKey]: {
                        ...existing,
                        itemId: undefined,
                        quantity: existing.quantity + quantity
                    }
                };
            }

            const key = `local:${variantId}:${normalizedNote ?? ""}`;

            return {
                ...currentItemsById,
                [key]: {
                    productId: key,
                    productVariantId: variantId,
                    inventoryLocationId: product.inventoryLocationId ?? null,
                    name: product.name,
                    sku: product.sku,
                    variant: product.variant,
                    quantity,
                    unitPrice: product.price,
                    availableQuantity: product.availableQuantity,
                    sellableQuantity: product.sellableQuantity,
                    manualDiscount: null,
                    note: normalizedNote
                }
            };
        });
        stockByVariantIdRef.current[variantId] = product;
        clearBankTransferAndMessages();
    }

    function updateLocalCartItemQuantity(productId: string, quantity: number) {
        setCartItemsById((currentItemsById) => {
            const item = currentItemsById[productId];

            if (!item) {
                return currentItemsById;
            }

            if (quantity <= 0) {
                return Object.fromEntries(
                    Object.entries(currentItemsById).filter(([key]) => key !== productId)
                );
            }

            return {
                ...currentItemsById,
                [productId]: { ...item, itemId: undefined, quantity }
            };
        });
        clearBankTransferAndMessages();

        return true;
    }

    function changeLocalCartItemNote(productId: string, nextNote: string) {
        setCartItemsById((currentItemsById) => {
            const item = currentItemsById[productId];

            if (!item) {
                return currentItemsById;
            }

            return {
                ...currentItemsById,
                [productId]: {
                    ...item,
                    itemId: undefined,
                    note: nextNote.trim() || undefined
                }
            };
        });
        clearBankTransferAndMessages();
    }

    async function addProduct(product: SaleProductPreview, allowBackorder = false) {
        const variantId = product.productVariantId ?? product.id;

        if (addingProductId) {
            return false;
        }

        if (!variantId) {
            setCheckoutError("Không xác định được mã Mặt hàng.");
            return false;
        }

        const waitAccepted =
            allowBackorder || backorderVariantIdsRef.current.has(variantId);

        if (!online) {
            // Mất mạng không ước tính được ngày có hàng nên không nhận đơn chờ hàng, kể cả mặt hàng đã từng đồng ý chờ.
            if (product.sellableQuantity <= 0) {
                setCheckoutError(
                    `${product.name} đã hết hàng theo dữ liệu đã tải; không đặt chờ hàng được khi mất mạng.`
                );
                return false;
            }

            upsertLocalCartItem(product, 1);
            return true;
        }

        if (product.sellableQuantity <= 0 && !waitAccepted) {
            setAddingProductId(product.id);
            await offerBackorder(
                { kind: "add", product },
                variantId,
                product.name,
                product.sku,
                getCartQuantity(variantId) + 1
            );
            setAddingProductId(null);
            return false;
        }

        setAddingProductId(product.id);

        try {
            const order = await posOrderApi.addDraftItem({
                orderId: draftOrder?.id ?? null,
                branchId: currentBranchId,
                customerId: selectedCustomerId,
                orderType,
                productVariantId: variantId,
                inventoryLocationId: product.inventoryLocationId,
                allowBackorder: waitAccepted
            });
            applyDraftOrder(order);
            setCheckoutError("");
            setCheckoutSuccess("");
            stockByVariantIdRef.current[variantId] = product;

            if (waitAccepted) {
                backorderVariantIdsRef.current.add(variantId);
            } else {
                void checkShelfShortage(order, variantId);
            }

            void refreshBackorderEstimate(order);
            return true;
        } catch (error) {
            if (!waitAccepted && isOutOfStockError(error)) {
                await offerBackorder(
                    { kind: "add", product },
                    variantId,
                    product.name,
                    product.sku,
                    getCartQuantity(variantId) + 1
                );
                return false;
            }

            logApiError(error);
            setCheckoutError(
                `Không thể thêm sản phẩm: ${getErrorMessage(error)}`
            );
            return false;
        } finally {
            setAddingProductId(null);
        }
    }

    async function acceptBackorder() {
        if (!backorderOffer || backorderSubmitting) {
            return;
        }

        setBackorderSubmitting(true);
        setBackorderError("");

        const { action } = backorderOffer;
        const added =
            action.kind === "add"
                ? await addProduct(action.product, true)
                : await updateCartItemQuantity(action.productId, action.quantity, true);

        setBackorderSubmitting(false);

        if (added) {
            setBackorderOffer(null);

            if (action.kind === "add") {
                clearSearch();
            }
        } else {
            setBackorderError("Chưa thêm được mặt hàng đặt trước. Vui lòng thử lại.");
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
    }

    function clearSearch() {
        setSearchQuery("");
    }

    function changeCustomTeaSearchQuery(value: string) {
        setCustomTeaSearchQuery(value);
    }

    function closeCustomTeaDialog() {
        setCustomTeaOpen(false);
        setCustomTeaSearchQuery("");
    }

    async function updateCartItemQuantity(
        productId: string,
        quantity: number,
        allowBackorder = false
    ) {
        const currentItem = cartItemsById[productId];

        if (!currentItem) {
            return false;
        }

        // Dòng trà đóng theo yêu cầu luôn có số lượng 1; chỉ xoá được, không tăng/giảm.
        if (currentItem.bundle && quantity > 0) {
            return false;
        }

        // Mất mạng, hoặc dòng chưa có trên đơn nháp: sửa cục bộ, gửi cả giỏ khi thanh toán.
        if (!online || !draftOrder?.id || !currentItem.itemId) {
            return updateLocalCartItemQuantity(productId, quantity);
        }

        const variantId = currentItem.productVariantId ?? currentItem.productId;
        const waitAccepted =
            allowBackorder || backorderVariantIdsRef.current.has(variantId);

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
                        quantity,
                        waitAccepted
                    );
            applyDraftOrder(order);
            setCheckoutError("");
            setCheckoutSuccess("");

            if (waitAccepted && quantity > 0) {
                backorderVariantIdsRef.current.add(variantId);
            } else if (currentItem.productVariantId) {
                void checkShelfShortage(order, currentItem.productVariantId);
            }

            void refreshBackorderEstimate(order);
            return true;
        } catch (error) {
            if (!waitAccepted && quantity > 0 && isOutOfStockError(error)) {
                await offerBackorder(
                    { kind: "update", productId, quantity },
                    variantId,
                    currentItem.name,
                    currentItem.sku,
                    getCartQuantity(variantId, productId) + quantity
                );
                return false;
            }

            logApiError(error);
            setCheckoutError(getErrorMessage(error));
            return false;
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

    async function changeProductNote(productId: string, nextNote: string) {
        const currentItem = cartItemsById[productId];
        const productVariantId = currentItem?.productVariantId;

        if (!currentItem) {
            return;
        }

        if (!online || !draftOrder?.id || !currentItem.itemId || !productVariantId) {
            changeLocalCartItemNote(productId, nextNote);
            return;
        }

        try {
            const orderAfterRemove = await posOrderApi.removeDraftItem(
                draftOrder.id,
                currentItem.itemId
            );
            const orderAfterAdd = await posOrderApi.addDraftItem({
                orderId: orderAfterRemove.id,
                customerId: selectedCustomerId,
                orderType,
                productVariantId,
                inventoryLocationId: currentItem.inventoryLocationId,
                quantity: currentItem.quantity,
                note: nextNote
            });
            applyDraftOrder(orderAfterAdd);
            setCheckoutError("");
            setCheckoutSuccess("");
        } catch (error) {
            logApiError(error);
            setCheckoutError(
                `Không thể cập nhật yêu cầu: ${getErrorMessage(error)}`
            );
        }
    }

    async function addCustomTeaRequest(input: {
        product: SaleProductPreview;
        quantity: number;
        note: string;
    }) {
        const variantId = input.product.productVariantId ?? input.product.id;

        if (customTeaSubmitLoading) {
            return;
        }

        if (!variantId) {
            setCheckoutError("Không xác định được mã Mặt hàng.");
            return;
        }

        if (
            input.product.sellableQuantity < input.quantity
        ) {
            setCheckoutError("Số lượng yêu cầu vượt số lượng có thể bán.");
            return;
        }

        if (!online) {
            upsertLocalCartItem(input.product, input.quantity, input.note);
            closeCustomTeaDialog();
            return;
        }

        setCustomTeaSubmitLoading(true);

        try {
            const order = await posOrderApi.addDraftItem({
                orderId: draftOrder?.id ?? null,
                customerId: selectedCustomerId,
                orderType,
                productVariantId: variantId,
                inventoryLocationId: input.product.inventoryLocationId,
                quantity: input.quantity,
                note: input.note
            });
            applyDraftOrder(order);
            closeCustomTeaDialog();
            setCheckoutError("");
            setCheckoutSuccess("");
        } catch (error) {
            logApiError(error);
            setCheckoutError(
                `Không thể thêm trà theo yêu cầu: ${getErrorMessage(error)}`
            );
        } finally {
            setCustomTeaSubmitLoading(false);
        }
    }

    function changeSelectedDiscount(discountId: string) {
        setSelectedDiscountId(discountId);
        setBankTransferPayment(null);
        setBankTransferStatus("WAITING");
        setBankTransferEventMessage("");
    }

    function changePaymentMethod(method: SalePaymentMethod) {
        if (!online && (method === "BANK_TRANSFER" || method === "COMBINED")) {
            setCheckoutError(
                "Mất kết nối máy chủ: chưa thể thanh toán chuyển khoản. Vui lòng thu tiền mặt hoặc chờ có mạng."
            );
            return;
        }

        if (hasLocalLines && (method === "BANK_TRANSFER" || method === "COMBINED")) {
            setCheckoutError(
                "Giỏ có mặt hàng thêm khi mất mạng nên chỉ thu tiền mặt. Muốn chuyển khoản, hãy đóng hoá đơn và tạo lại."
            );
            return;
        }

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

            // Mất mạng thì chỉ dọn cục bộ; đơn nháp trên máy chủ tự hết hạn.
            if (online && draftOrder?.id && draftOrder.status === "DRAFT") {
                try {
                    await posOrderApi.cancelDraftOrder(draftOrder.id);
                } catch (error) {
                    // Đơn nháp khôi phục từ máy có thể đã bị huỷ/thanh toán trên
                    // máy chủ: vẫn dọn giỏ cục bộ. Chỉ giữ lại khi mất mạng.
                    if (error instanceof ApiError && error.isNetworkError) {
                        throw error;
                    }

                    logApiError(error);
                }
            }
            setDraftOrder(null);
            setCartItemsById({});
            setBackorderOffer(null);
            setBackorderEstimate(null);
            setDepositEnabled(false);
            setDepositAmount(0);
            backorderVariantIdsRef.current = new Set();
            bankTransferBackorderRef.current = null;
            setNote("");
            setCashReceived(0);
            setSelectedDiscountId("none");
            setReservedCouponUsage(null);
            setManualOrderDiscount(null);
            setBankTransferPayment(null);
            setBankTransferStatus("WAITING");
            setBankTransferEventMessage("");
            setCustomTeaOpen(false);
            setCustomTeaSearchQuery("");
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

        const applyNotificationTimeoutId = window.setTimeout(() => {
            if (isPaymentNeedReviewNotification(matchedNotification)) {
                // Giữ QR và mã giảm giá: quản lý đối soát xong sẽ xác nhận trên máy chủ.
                setBankTransferEventMessage(matchedNotification.message);
                setCheckoutError(matchedNotification.message);
                setCheckoutSuccess("");
                return;
            }

            const nextStatus = notificationToPaymentStatus(matchedNotification);

            if (nextStatus !== "RECEIVED") {
                void releaseCouponUsage(
                    reservedCouponUsage,
                    nextStatus === "CANCELLED"
                        ? "cancel"
                        : nextStatus === "EXPIRED"
                            ? "expire"
                            : "release"
                );
                // Máy chủ đã trả đơn về nháp; lấy lại trạng thái để chọn phương thức khác.
                const orderId = bankTransferPayment.orderId ?? draftOrder?.id;
                const message =
                    matchedNotification.message || "Thanh toán chuyển khoản chưa hoàn tất.";
                if (orderId) {
                    posOrderApi
                        .getOrder(orderId)
                        .then((order) => applyBankTransferSessionEnded(nextStatus, message, order))
                        .catch(() => applyBankTransferSessionEnded(nextStatus, message, null));
                } else {
                    applyBankTransferSessionEnded(nextStatus, message, null);
                }
                return;
            }

            applyBankTransferPaid(
                bankTransferPayment.orderCode ?? draftOrder?.orderCode ?? "",
                matchedNotification.message
            );
        }, 0);

        return () => {
            window.clearTimeout(applyNotificationTimeoutId);
        };
    }, [
        applyBankTransferPaid,
        applyBankTransferSessionEnded,
        bankTransferPayment,
        draftOrder?.id,
        draftOrder?.orderCode,
        notifications,
        paymentMethod,
        releaseCouponUsage,
        reservedCouponUsage
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
                        amountDue={amountDue}
                        paymentBlocked={depositInvalid}
                        offline={!online}
                        backorderNotice={
                            backorderEstimate ? (
                                <BackorderCheckoutNotice
                                    estimate={backorderEstimate}
                                    total={totals.total}
                                    depositAllowed={paymentMethod === "CASH"}
                                    depositEnabled={depositActive}
                                    depositAmount={depositAmount}
                                    onDepositEnabledChange={setDepositEnabled}
                                    onDepositAmountChange={setDepositAmount}
                                />
                            ) : null
                        }
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
                            void cancelBankTransferPayment();
                        }}
                        onBankTransferRefresh={() => {
                            void refreshBankTransferStatus();
                        }}
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
                onProductNoteChange={(productId, nextNote) => {
                    void changeProductNote(productId, nextNote);
                }}
                onCustomTeaOpen={() => setCustomTeaOpen(true)}
                onCustomBundleOpen={() => setCustomBundleOpen(true)}
                onInvoiceClose={() => {
                    void closeCurrentInvoice();
                }}
                onNoteChange={setNote}
                onOrderTypeChange={setOrderType}
            />
            {shelfShortage ? (
                <ShelfShortageDialog
                    shortage={shelfShortage}
                    submitting={shelfShortageSubmitting}
                    error={shelfShortageError}
                    onCreateNow={() => {
                        void createShelfReplenishment(true);
                    }}
                    onSaveDraft={() => {
                        void createShelfReplenishment(false);
                    }}
                    onDismiss={() => setShelfShortage(null)}
                />
            ) : null}
            {backorderOffer ? (
                <BackorderDialog
                    offer={backorderOffer}
                    submitting={backorderSubmitting}
                    error={backorderError}
                    onAccept={() => {
                        void acceptBackorder();
                    }}
                    onDecline={() => setBackorderOffer(null)}
                />
            ) : null}
            <ReceiptDialog
                orderId={receiptOrderId}
                localOrder={offlineReceipt}
                title={offlineReceipt ? "Đã lưu đơn bán offline" : undefined}
                hint={
                    offlineReceipt
                        ? "Mã tạm. Đơn sẽ tự đồng bộ lên máy chủ và nhận mã chính thức khi có mạng."
                        : undefined
                }
                onClose={() => {
                    setReceiptOrderId(null);
                    setOfflineReceipt(null);
                }}
            />
            {customTeaOpen ? (
                <CustomTeaRequestDialog
                    open={customTeaOpen}
                    products={customTeaSearchResults}
                    searchQuery={customTeaSearchQuery}
                    searchLoading={customTeaSearchLoading}
                    searchError={customTeaSearchError}
                    submitLoading={customTeaSubmitLoading}
                    onClose={closeCustomTeaDialog}
                    onSearchChange={changeCustomTeaSearchQuery}
                    onSubmit={(input) => {
                        void addCustomTeaRequest(input);
                    }}
                />
            ) : null}
            {customBundleOpen ? (
                <CustomBundleDialog
                    open={customBundleOpen}
                    branchId={currentBranchId}
                    submitLoading={customBundleSubmitting}
                    onClose={() => setCustomBundleOpen(false)}
                    onSubmit={(input) => {
                        void addCustomBundle(input);
                    }}
                />
            ) : null}
        </PosShell>
    );
}
