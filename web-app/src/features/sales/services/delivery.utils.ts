import type {
  AddressSuggestionReason,
  BranchLocation,
  CustomerAddress,
  DeliveryAddressSnapshot,
  DeliveryCheckoutState,
  DeliveryOption,
  DeliveryPriority,
  DeliverySelectionSource,
  DeliveryTotals,
  DeliveryUrgency,
  ManualDeliveryAddress,
  SuggestedAddress
} from "../types/delivery.types";
import type { SaleTotals } from "../types/sale.types";

const earthRadiusKm = 6371;

interface Coordinate {
  latitude: number;
  longitude: number;
}

function toRadians(value: number) {
  return (value * Math.PI) / 180;
}

export function calculateDistanceKm(
  from: Coordinate,
  to: Coordinate
) {
  const latitudeDelta = toRadians(to.latitude - from.latitude);
  const longitudeDelta = toRadians(to.longitude - from.longitude);
  const fromLatitude = toRadians(from.latitude);
  const toLatitude = toRadians(to.latitude);
  const a =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) *
      Math.cos(toLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;

  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function getAddressDistanceKm(
  address: CustomerAddress,
  branch?: BranchLocation | null
) {
  const branchLatitude = branch?.latitude;
  const branchLongitude = branch?.longitude;
  const addressLatitude = address.latitude;
  const addressLongitude = address.longitude;

  if (
    typeof branchLatitude !== "number" ||
    typeof branchLongitude !== "number" ||
    typeof addressLatitude !== "number" ||
    typeof addressLongitude !== "number"
  ) {
    return undefined;
  }

  const from: Coordinate = {
    latitude: branchLatitude,
    longitude: branchLongitude
  };
  const to: Coordinate = {
    latitude: addressLatitude,
    longitude: addressLongitude
  };

  return calculateDistanceKm(from, to);
}

function isAddressUsable(address: CustomerAddress) {
  return Boolean(
    address.recipientName &&
      address.recipientPhone &&
      (address.fullAddress || address.addressLine)
  );
}

function recentAddress(addresses: CustomerAddress[]) {
  return addresses
    .filter((address) => address.lastUsedAt)
    .sort(
      (left, right) =>
        Date.parse(right.lastUsedAt ?? "") - Date.parse(left.lastUsedAt ?? "")
    )[0];
}

function nearestAddress(
  addresses: CustomerAddress[],
  branch?: BranchLocation | null
) {
  return addresses
    .map((address) => ({
      address,
      distanceKm: getAddressDistanceKm(address, branch)
    }))
    .filter((item): item is { address: CustomerAddress; distanceKm: number } =>
      typeof item.distanceKm === "number"
    )
    .sort((left, right) => left.distanceKm - right.distanceKm)[0];
}

export function selectSuggestedAddress(
  addresses: CustomerAddress[],
  branch?: BranchLocation | null
): SuggestedAddress | null {
  const usableAddresses = addresses.filter(isAddressUsable);
  const defaultAddress = usableAddresses.find((address) => address.isDefault);

  if (defaultAddress) {
    return {
      address: defaultAddress,
      reason: "DEFAULT",
      distanceKm: getAddressDistanceKm(defaultAddress, branch)
    };
  }

  const latestAddress = recentAddress(usableAddresses);

  if (latestAddress) {
    return {
      address: latestAddress,
      reason: "RECENT",
      distanceKm: getAddressDistanceKm(latestAddress, branch)
    };
  }

  const nearest = nearestAddress(usableAddresses, branch);

  if (nearest) {
    return {
      address: nearest.address,
      reason: "NEAREST",
      distanceKm: nearest.distanceKm
    };
  }

  const firstAddress = usableAddresses[0];

  return firstAddress
    ? {
        address: firstAddress,
        reason: "FIRST_VALID",
        distanceKm: getAddressDistanceKm(firstAddress, branch)
      }
    : null;
}

export function getSuggestionLabel(reason: AddressSuggestionReason | null) {
  switch (reason) {
    case "DEFAULT":
      return "Mặc định";
    case "RECENT":
      return "Dùng gần đây nhất";
    case "NEAREST":
      return "Gần cửa hàng nhất";
    case "FIRST_VALID":
      return "Địa chỉ phù hợp";
    default:
      return "";
  }
}

function numberOrNull(value: string) {
  const numberValue = Number(value);

  return Number.isFinite(numberValue) ? numberValue : null;
}

export function addressToSnapshot(
  address: CustomerAddress,
  deliveryNote = ""
): DeliveryAddressSnapshot {
  return {
    customerAddressId: address.id,
    recipientName: address.recipientName ?? "",
    recipientPhone: address.recipientPhone ?? "",
    deliveryAddress: address.fullAddress ?? address.addressLine ?? "",
    deliveryProvinceCode: address.provinceCode ?? "",
    deliveryDistrictCode: address.districtCode ?? "",
    deliveryWardCode: address.wardCode ?? "",
    deliveryLatitude: address.latitude ?? null,
    deliveryLongitude: address.longitude ?? null,
    deliveryNote
  };
}

export function manualAddressToSnapshot(
  address: ManualDeliveryAddress,
  deliveryNote = ""
): DeliveryAddressSnapshot {
  return {
    customerAddressId: null,
    recipientName: address.recipientName.trim(),
    recipientPhone: address.recipientPhone.trim(),
    deliveryAddress:
      address.fullAddress.trim() ||
      [
        address.addressLine,
        address.wardName,
        address.districtName,
        address.provinceName
      ]
        .filter(Boolean)
        .join(", "),
    deliveryProvinceCode: address.provinceCode.trim(),
    deliveryDistrictCode: address.districtCode.trim(),
    deliveryWardCode: address.wardCode.trim(),
    deliveryLatitude: numberOrNull(address.latitude),
    deliveryLongitude: numberOrNull(address.longitude),
    deliveryNote: deliveryNote || address.deliveryNote.trim()
  };
}

export function getSelectedDeliveryOption(
  options: DeliveryOption[],
  optionId: string | null
) {
  return options.find((option) => option.optionId === optionId) ?? null;
}

export function isDeliveryOptionSelectable(
  option: DeliveryOption,
  urgency: DeliveryUrgency,
  customerAcceptedWait = false
) {
  if (option.availableNow) {
    return true;
  }

  if (!option.availableLater) {
    return false;
  }

  return urgency !== "ASAP" || customerAcceptedWait;
}

export function selectPreferredDeliveryOption({
  options,
  recommendedOptionId,
  urgency,
  currentOptionId,
  currentSelectionSource,
  customerAcceptedWait = false
}: {
  options: DeliveryOption[];
  recommendedOptionId?: string | null;
  urgency: DeliveryUrgency;
  currentOptionId?: string | null;
  currentSelectionSource?: DeliverySelectionSource;
  customerAcceptedWait?: boolean;
}) {
  const selectableOptions = options.filter((option) =>
    isDeliveryOptionSelectable(option, urgency, customerAcceptedWait)
  );
  const currentOption = selectableOptions.find(
    (option) => option.optionId === currentOptionId && !isQuoteExpired(option)
  );

  if (currentOption && currentSelectionSource === "MANUAL") {
    return {
      option: currentOption,
      source: "MANUAL" as const
    };
  }

  if (currentOption && currentSelectionSource === "MANAGER_OVERRIDE") {
    return {
      option: currentOption,
      source: "MANAGER_OVERRIDE" as const
    };
  }

  const recommendedOption =
    selectableOptions.find((option) => option.optionId === recommendedOptionId) ??
    selectableOptions.find((option) => option.recommended);

  if (recommendedOption) {
    return {
      option: recommendedOption,
      source:
        currentOptionId && currentOptionId !== recommendedOption.optionId
          ? ("AUTO_FALLBACK" as const)
          : ("RECOMMENDED" as const)
    };
  }

  return (
    selectableOptions[0]
      ? {
          option: selectableOptions[0],
          source: currentOptionId || recommendedOptionId
            ? ("AUTO_FALLBACK" as const)
            : ("RECOMMENDED" as const)
        }
      : {
          option: null,
          source: "RECOMMENDED" as const
        }
  );
}

export function isQuoteExpired(option: DeliveryOption | null, now = Date.now()) {
  const expiresAt = option?.quoteExpiresAt ?? option?.expiresAt;

  if (!expiresAt) {
    return false;
  }

  return Date.parse(expiresAt) <= now;
}

export function getDefaultDeliveryPriority(urgency: DeliveryUrgency) {
  return urgency === "ASAP"
    ? ("FASTEST" satisfies DeliveryPriority)
    : ("BALANCED" satisfies DeliveryPriority);
}

export function calculateDeliveryTotals(
  totals: SaleTotals,
  shippingFee: number,
  depositPaidAmount: number,
  paidAmount = 0
): DeliveryTotals {
  const grandTotal = Math.max(0, totals.total + shippingFee);
  const codAmount = Math.max(0, grandTotal - depositPaidAmount - paidAmount);

  return {
    itemTotal: totals.subtotal,
    discountAmount: totals.discountAmount,
    shippingFee,
    grandTotal,
    paidAmount,
    depositPaidAmount,
    codAmount
  };
}

export function isPhoneValid(value: string) {
  return /^(0|\+84)\d{8,10}$/.test(value.replace(/\s/g, ""));
}

export function getDeliverySnapshotFromState(state: DeliveryCheckoutState) {
  if (state.useManualAddress) {
    return manualAddressToSnapshot(state.manualAddress, state.deliveryNote);
  }

  const selectedAddress = state.addresses.find(
    (address) => address.id === state.selectedAddressId
  );

  return selectedAddress
    ? addressToSnapshot(selectedAddress, state.deliveryNote)
    : null;
}
