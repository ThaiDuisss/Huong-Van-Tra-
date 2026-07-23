import type {
  SaleCartItem,
  SaleCustomer,
  SalePaymentMethod,
  SaleTotals
} from "./sale.types";

export type AddressSuggestionReason =
  | "DEFAULT"
  | "RECENT"
  | "NEAREST"
  | "FIRST_VALID";

export type DeliveryAddressType = "HOME" | "OFFICE" | "OTHER";
export type DeliveryMode = "INTERNAL" | "THIRD_PARTY";
export type ThirdPartyDeliveryProvider = "ALL" | "GHN" | "AHAMOVE";
export type DeliveryUrgency = "ASAP" | "SAME_DAY" | "SCHEDULED";
export type DeliveryPriority = "BALANCED" | "FASTEST" | "CHEAPEST";
export type DeliveryModeSelectionSource = "MANUAL" | "RECOMMENDED";
export type DeliverySelectionSource =
  | "RECOMMENDED"
  | "MANUAL"
  | "AUTO_FALLBACK"
  | "MANAGER_OVERRIDE";

export interface CustomerAddress {
  id: string;
  label?: string | null;
  recipientName?: string | null;
  recipientPhone?: string | null;
  provinceCode?: string | null;
  provinceName?: string | null;
  districtCode?: string | null;
  districtName?: string | null;
  wardCode?: string | null;
  wardName?: string | null;
  addressLine?: string | null;
  fullAddress?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  isDefault?: boolean | null;
  lastUsedAt?: string | null;
}

export interface BranchLocation {
  latitude?: number | null;
  longitude?: number | null;
}

export interface SuggestedAddress {
  address: CustomerAddress;
  reason: AddressSuggestionReason;
  distanceKm?: number;
}

export interface ManualDeliveryAddress {
  recipientName: string;
  recipientPhone: string;
  provinceCode: string;
  provinceName: string;
  districtCode: string;
  districtName: string;
  wardCode: string;
  wardName: string;
  addressLine: string;
  fullAddress: string;
  latitude: string;
  longitude: string;
  deliveryNote: string;
  addressType: DeliveryAddressType;
}

export interface DeliveryAddressSnapshot {
  customerAddressId: string | null;
  recipientName: string;
  recipientPhone: string;
  deliveryAddress: string;
  deliveryProvinceCode: string;
  deliveryDistrictCode: string;
  deliveryWardCode: string;
  deliveryLatitude: number | null;
  deliveryLongitude: number | null;
  deliveryNote: string;
}

export interface DeliveryOption {
  optionId: string;
  quoteId: string;
  mode: "INTERNAL" | "THIRD_PARTY";
  provider: string;
  serviceCode?: string;
  serviceName: string;
  available: boolean;
  availableNow: boolean;
  availableLater: boolean;
  pickupEtaMinutes?: number;
  deliveryEtaMinutes?: number;
  estimatedPickupAt?: string;
  estimatedDeliveryAt?: string;
  nextAvailableAt?: string;
  shippingFee: number;
  currency: "VND";
  estimatedMinutes?: number;
  estimatedDeliveryFrom?: string;
  estimatedDeliveryTo?: string;
  distanceKm?: number;
  expiresAt?: string;
  quoteExpiresAt?: string;
  recommended?: boolean;
  recommendationReason?: string;
  unavailableReason?: string;
}

export interface DeliveryQuoteResponse {
  recommendedOptionId?: string | null;
  recommendedMode?: DeliveryMode | null;
  recommendationReason?: string | null;
  options: DeliveryOption[];
}

export interface DeliveryQuoteRequest {
  customerId: string;
  deliveryMode: DeliveryMode;
  branchId?: string | null;
  address: DeliveryAddressSnapshot;
  recipientAddress: {
    provinceCode: string;
    districtCode: string;
    wardCode: string;
    addressLine: string;
    latitude: number | null;
    longitude: number | null;
  };
  subtotal: number;
  orderValue: number;
  deliveryUrgency: DeliveryUrgency;
  urgency: DeliveryUrgency;
  deliveryPriority: DeliveryPriority;
  priority: DeliveryPriority;
  shippingProvider?: Exclude<ThirdPartyDeliveryProvider, "ALL"> | null;
  requestedDeliveryAt: string | null;
  items: Array<{
    productVariantId: string;
    quantity: number;
  }>;
}

export interface DeliveryTotals {
  itemTotal: number;
  discountAmount: number;
  shippingFee: number;
  grandTotal: number;
  paidAmount: number;
  depositPaidAmount: number;
  codAmount: number;
}

export interface DeliveryCheckoutState {
  addresses: CustomerAddress[];
  addressLoading: boolean;
  addressError: string;
  selectedAddressId: string | null;
  addressSuggestionReason: AddressSuggestionReason | null;
  manualAddress: ManualDeliveryAddress;
  useManualAddress: boolean;
  saveAddress: boolean;
  setAsDefaultAddress: boolean;
  deliveryNote: string;
  selectedDeliveryMode: DeliveryMode | null;
  deliveryModeSelectionSource: DeliveryModeSelectionSource | null;
  recommendedDeliveryMode: DeliveryMode | null;
  deliveryModeRecommendationReason: string;
  internalDeliveryOption: DeliveryOption | null;
  thirdPartyQuotes: DeliveryOption[];
  selectedThirdPartyProvider: ThirdPartyDeliveryProvider;
  selectedThirdPartyQuoteId: string | null;
  shippingFee: number;
  deliveryOptions: DeliveryOption[];
  selectedDeliveryOptionId: string | null;
  selectedDeliveryQuoteId: string | null;
  recommendedDeliveryOptionId: string | null;
  deliverySelectionSource: DeliverySelectionSource;
  customerAcceptedDeliveryWait: boolean;
  quoteLoading: boolean;
  quoteError: string;
  quoteRequestedAt: number;
  deliveryUrgency: DeliveryUrgency;
  deliveryPriority: DeliveryPriority;
  requestedDeliveryAt: string;
  paymentMethod: SalePaymentMethod | "COD";
  depositAmount: number;
  submitError: string;
  submitPending: boolean;
}

export interface CreateDeliveryOrderPayload {
  orderType: "DELIVERY";
  customerId: string | null;
  customerAddressId: string | null;
  recipientName: string;
  recipientPhone: string;
  deliveryAddress: string;
  deliveryProvinceCode: string;
  deliveryDistrictCode: string;
  deliveryWardCode: string;
  deliveryLatitude: number | null;
  deliveryLongitude: number | null;
  deliveryNote: string;
  deliveryUrgency: DeliveryUrgency;
  deliveryPriority: DeliveryPriority;
  selectedDeliveryOptionId: string;
  recommendedDeliveryOptionId: string | null;
  deliverySelectionSource: DeliverySelectionSource;
  deliveryMode: "INTERNAL" | "THIRD_PARTY";
  shippingProvider: string;
  shippingServiceCode?: string;
  deliveryQuoteId: string;
  shippingFee: number;
  requestedDeliveryAt: string | null;
  paymentMethod: string;
  depositAmount: number;
  codAmount: number;
  itemTotal: number;
  discountAmount: number;
  grandTotal: number;
  items: SaleCartItem[];
  customerSnapshot?: SaleCustomer | null;
  saveAddress?: boolean;
  setAsDefaultAddress?: boolean;
  addressType?: DeliveryAddressType;
}

export interface DeliveryValidationErrors {
  customer?: string;
  recipientName?: string;
  recipientPhone?: string;
  address?: string;
  province?: string;
  district?: string;
  ward?: string;
  deliveryOption?: string;
  quote?: string;
}

export interface DeliveryCheckoutPanelSubmitInput {
  customer: SaleCustomer | null;
  cartItems: SaleCartItem[];
  totals: SaleTotals;
  state: DeliveryCheckoutState;
}
