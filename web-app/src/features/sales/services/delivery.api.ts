import { apiClient } from "../../../api/api-client";
import type {
  CreateDeliveryOrderPayload,
  CustomerAddress,
  DeliveryOption,
  DeliveryQuoteRequest,
  DeliveryQuoteResponse,
  ManualDeliveryAddress
} from "../types/delivery.types";

export type CustomerAddressPayload = ManualDeliveryAddress & {
  isDefault?: boolean;
};

export const deliveryApi = {
  getCustomerAddresses(customerId: string, signal?: AbortSignal) {
    return apiClient.get<CustomerAddress[]>(
      `/customer/addresses/${encodeURIComponent(customerId)}`,
      { signal }
    );
  },

  createCustomerAddress(
    customerId: string,
    payload: CustomerAddressPayload
  ) {
    return apiClient.post<CustomerAddress, CustomerAddressPayload>(
      `/customer/${encodeURIComponent(customerId)}/addresses`,
      payload
    );
  },

  updateCustomerAddress(
    customerId: string,
    addressId: string,
    payload: CustomerAddressPayload
  ) {
    return apiClient.put<CustomerAddress, CustomerAddressPayload>(
      `/customer/${encodeURIComponent(customerId)}/addresses/${encodeURIComponent(addressId)}`,
      payload
    );
  },

  async getDeliveryQuotes(payload: DeliveryQuoteRequest, signal?: AbortSignal) {
    const response = await apiClient.post<
      DeliveryOption[] | DeliveryQuoteResponse,
      DeliveryQuoteRequest
    >(
      "/delivery/quotes",
      payload,
      { signal }
    );

    return normalizeDeliveryQuoteResponse(response, payload.deliveryMode);
  },

  createDeliveryOrder(payload: CreateDeliveryOrderPayload) {
    return apiClient.post<{ orderId: string; orderCode?: string }, CreateDeliveryOrderPayload>(
      "/orders/delivery",
      payload
    );
  }
};

function normalizeDeliveryQuoteResponse(
  response: DeliveryOption[] | DeliveryQuoteResponse,
  requestedMode: DeliveryOption["mode"]
): DeliveryQuoteResponse {
  const rawOptions = Array.isArray(response) ? response : response.options;
  const options = rawOptions.filter(
    (option) => !option.mode || option.mode === requestedMode
  );
  const recommendedOptionId = Array.isArray(response)
    ? options.find((option) => option.recommended)?.optionId ?? null
    : response.recommendedOptionId ?? null;
  const recommendationReason = Array.isArray(response)
    ? options.find((option) => option.recommended)?.recommendationReason ?? null
    : response.recommendationReason ?? null;

  return {
    recommendedOptionId,
    recommendedMode: Array.isArray(response)
      ? options.find((option) => option.recommended)?.mode ?? null
      : response.recommendedMode ?? null,
    recommendationReason,
    options: options.map((option) =>
      normalizeDeliveryOption(option, recommendedOptionId, recommendationReason)
    )
  };
}

function normalizeDeliveryOption(
  option: DeliveryOption,
  recommendedOptionId: string | null,
  recommendationReason: string | null
): DeliveryOption {
  const optionId = option.optionId || option.quoteId;
  const quoteExpiresAt = option.quoteExpiresAt ?? option.expiresAt;
  const availableNow = option.availableNow ?? option.available;
  const availableLater = option.availableLater ?? false;

  return {
    ...option,
    optionId,
    quoteExpiresAt,
    expiresAt: quoteExpiresAt,
    availableNow,
    availableLater,
    available: availableNow || availableLater,
    recommended:
      option.recommended ||
      (Boolean(recommendedOptionId) && optionId === recommendedOptionId),
    recommendationReason:
      option.recommendationReason ??
      (optionId === recommendedOptionId ? recommendationReason ?? undefined : undefined)
  };
}
