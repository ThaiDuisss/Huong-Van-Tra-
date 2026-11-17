import { type ReactNode, useMemo, useState } from "react";
import {
  ChevronDown,
  Info,
  MapPin,
  Pencil,
  Phone,
  Settings,
  Truck,
  User
} from "lucide-react";

import { Button, IconButton } from "../../../../components/ui/Button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "../../../../components/ui/dialog";
import { CustomerSelector } from "../checkout/CustomerCard";
import { CompletePaymentButton } from "../checkout/CompletePaymentButton";
import {
  calculateDeliveryTotals,
  getDeliverySnapshotFromState,
  isDeliveryOptionSelectable,
  isPhoneValid,
  isQuoteExpired
} from "../../services/delivery.utils";
import type {
  BranchLocation,
  CustomerAddress,
  DeliveryCheckoutState,
  DeliveryMode,
  DeliveryOption,
  ThirdPartyDeliveryProvider,
  DeliveryUrgency,
  DeliveryValidationErrors,
  ManualDeliveryAddress
} from "../../types/delivery.types";
import type {
  SaleCartItem,
  SaleCustomer,
  SaleDiscountOption,
  SaleTotals
} from "../../types/sale.types";
import { AddressSelectionModal } from "./AddressSelectionModal";
import { DeliveryOptionCard } from "./DeliveryOptionCard";
import { DeliveryPaymentSummary } from "./DeliveryPaymentSummary";
import { formatCurrency } from "../../services/money";

interface DeliveryCheckoutPanelProps {
  branchLocation?: BranchLocation | null;
  cartItems: SaleCartItem[];
  customers: SaleCustomer[];
  customersLoading: boolean;
  customersError: string;
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  selectedCustomer: SaleCustomer | null;
  customerLocked?: boolean;
  customerHasMore?: boolean;
  onCustomerSearch?(query: string): void;
  onCustomerLoadMore?(): void;
  recommendationEntry?: ReactNode;
  state: DeliveryCheckoutState;
  totals: SaleTotals;
  onCustomerChange(customerId: string): void;
  onCustomerCreated(customer: SaleCustomer): void;
  onCustomerClear(): void;
  onCustomerOpen(): void;
  onDiscountChange(discountId: string): void;
  onDeliveryStateChange(nextState: DeliveryCheckoutState): void;
  onQuoteRefresh(): void;
  onSubmit(): void;
}

export function DeliveryCheckoutPanel({
  branchLocation,
  cartItems,
  customers,
  customersLoading,
  customersError,
  discounts,
  selectedDiscountId,
  selectedCustomer,
  customerLocked,
  customerHasMore,
  onCustomerSearch,
  onCustomerLoadMore,
  recommendationEntry,
  state,
  totals,
  onCustomerChange,
  onCustomerCreated,
  onCustomerClear,
  onCustomerOpen,
  onDiscountChange,
  onDeliveryStateChange,
  onQuoteRefresh,
  onSubmit
}: DeliveryCheckoutPanelProps) {
  const [addressModalOpen, setAddressModalOpen] = useState(false);
  const [packageOpen, setPackageOpen] = useState(false);
  const [shippingSettingsOpen, setShippingSettingsOpen] = useState(false);
  const [waitConfirmationOption, setWaitConfirmationOption] =
    useState<DeliveryOption | null>(null);
  const selectedAddress =
    state.addresses.find((address) => address.id === state.selectedAddressId) ??
    null;
  const selectedOption = getSelectedStateDeliveryOption(state);
  const deliveryTotals = calculateDeliveryTotals(
    totals,
    state.shippingFee,
    state.depositAmount
  );
  const snapshot = getDeliverySnapshotFromState(state);
  const errors = useMemo(
    () =>
      getDeliveryValidationErrors({
        cartItems,
        customer: selectedCustomer,
        snapshot,
        selectedOption,
        selectedMode: state.selectedDeliveryMode,
        urgency: state.deliveryUrgency,
        customerAcceptedWait: state.customerAcceptedDeliveryWait,
        requestedDeliveryAt: state.requestedDeliveryAt
      }),
    [
      cartItems,
      selectedCustomer,
      selectedOption,
      state.selectedDeliveryMode,
      snapshot,
      state.customerAcceptedDeliveryWait,
      state.deliveryUrgency,
      state.requestedDeliveryAt
    ]
  );
  const quoteExpired = isQuoteExpired(selectedOption);
  const disabled =
    customerLocked ||
    Object.keys(errors).length > 0 ||
    quoteExpired ||
    state.submitPending ||
    state.quoteLoading;
  const showValidation = Boolean(state.submitError);

  function patchState(partial: Partial<DeliveryCheckoutState>) {
    onDeliveryStateChange({
      ...state,
      ...partial
    });
  }

  function changeDeliveryMode(selectedDeliveryMode: DeliveryMode) {
    patchState({
      selectedDeliveryMode,
      deliveryModeSelectionSource: "MANUAL",
      internalDeliveryOption:
        selectedDeliveryMode === "INTERNAL" ? state.internalDeliveryOption : null,
      thirdPartyQuotes:
        selectedDeliveryMode === "THIRD_PARTY" ? state.thirdPartyQuotes : [],
      selectedThirdPartyProvider:
        selectedDeliveryMode === "THIRD_PARTY"
          ? state.selectedThirdPartyProvider
          : "ALL",
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

  return (
    <section
      className={`grid h-full min-h-0 gap-3 ${
        state.selectedDeliveryMode === "THIRD_PARTY"
          ? "grid-cols-[minmax(330px,1fr)_minmax(330px,1fr)]"
          : "grid-cols-1"
      }`}
    >
      <div className="grid min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] rounded-xl bg-white shadow-pos-panel">
        <div className="border-b border-[#edf0f4] p-4">
          <CustomerSelector
            disabled={customerLocked}
            hasMore={customerHasMore}
            onSearch={onCustomerSearch}
            onLoadMore={onCustomerLoadMore}
            customers={customers}
            selectedCustomer={selectedCustomer}
            loading={customersLoading}
            error={customersError}
            onOpen={onCustomerOpen}
            onSelect={onCustomerChange}
            onCreated={onCustomerCreated}
            onClear={onCustomerClear}
          />
          <div className="mt-3">{recommendationEntry}</div>
          <label className="mt-3 flex items-center justify-between rounded-lg border border-[#dbe4ee] bg-[#f8fafc] px-3 py-2 text-sm font-bold text-[#172317]">
            <span>
              Đơn ngoại tỉnh hoặc cần ship gấp
              <span className="mt-0.5 block text-xs font-semibold text-[#697684]">
                Bật để dùng đối tác vận chuyển bên thứ 3
              </span>
            </span>
            <input
              type="checkbox"
              checked={state.selectedDeliveryMode === "THIRD_PARTY"}
              onChange={(event) =>
                changeDeliveryMode(
                  event.currentTarget.checked ? "THIRD_PARTY" : "INTERNAL"
                )
              }
              className="size-5 accent-[#1479f6]"
            />
          </label>
        </div>

        <div className="min-h-0 overflow-y-auto px-6 py-4">
          <WaybillAddressForm
            address={selectedAddress}
            customer={selectedCustomer}
            manualAddress={state.manualAddress}
            useManualAddress={state.useManualAddress}
            onAddressChange={() => setAddressModalOpen(true)}
          />
          {state.addressLoading ? (
            <p className="mt-2 text-xs font-bold text-[#697684]">
              Đang lấy địa chỉ gần nhất của khách hàng...
            </p>
          ) : null}
          {(state.addressError || errors.address) && showValidation ? (
            <p className="mt-2 text-xs font-bold text-red-600">
              {state.addressError || errors.address}
            </p>
          ) : null}

          <div className="my-5 border-t border-[#edf0f4]" />
          <PackageInformationAccordion
            cartItemCount={cartItems.length}
            open={packageOpen}
            onOpenChange={setPackageOpen}
          />

          {state.selectedDeliveryMode === "INTERNAL" &&
          state.internalDeliveryOption &&
          !state.internalDeliveryOption.availableNow &&
          state.internalDeliveryOption.availableLater ? (
            <Button
              onClick={() =>
                patchState({
                  customerAcceptedDeliveryWait: true,
                  selectedDeliveryOptionId: state.internalDeliveryOption?.optionId ?? null,
                  selectedDeliveryQuoteId: state.internalDeliveryOption?.quoteId ?? null,
                  shippingFee: state.internalDeliveryOption?.shippingFee ?? 0
                })
              }
              className="mt-4"
              variant="info"
            >
              Khách đồng ý chờ
            </Button>
          ) : null}

          <label className="mt-5 grid gap-1 border-b border-[#c9d2dc] pb-2 text-sm font-semibold text-[#697684]">
            <span className="inline-flex items-center gap-2">
              <Pencil size={17} aria-hidden="true" />
              Ghi chú cho bưu tá
            </span>
            <input
              value={state.deliveryNote}
              maxLength={240}
              onChange={(event) => patchState({ deliveryNote: event.currentTarget.value })}
              className="h-9 border-0 bg-transparent text-sm font-semibold text-[#172317] outline-none"
            />
          </label>
        </div>

        <div className="grid gap-2 border-t border-[#edf0f4] px-6 py-3">
          <DeliveryPaymentSummary
            discounts={discounts}
            selectedDiscountId={selectedDiscountId}
            totals={deliveryTotals}
            depositAmount={state.depositAmount}
            onDiscountChange={onDiscountChange}
            onDepositAmountChange={(depositAmount) => patchState({ depositAmount })}
          />

          <div className="flex items-center justify-between border-t border-[#edf0f4] pt-2">
            <label className="inline-flex items-center gap-2 text-sm font-black text-[#172317]">
              Thu hộ tiền (COD)
              <input
                type="checkbox"
                checked={state.paymentMethod === "COD"}
                onChange={(event) =>
                  patchState({
                    paymentMethod: event.currentTarget.checked ? "COD" : "CASH"
                  })
                }
                className="size-4 accent-[#1479f6]"
              />
            </label>
            <strong className="text-xl text-[#172317]">
              {formatCurrency(deliveryTotals.codAmount)}
            </strong>
          </div>
        </div>
        {state.selectedDeliveryMode !== "THIRD_PARTY" ? (
          <div className="grid gap-3 border-t border-[#edf0f4] px-6 py-3">
            {state.submitError ? (
              <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-bold text-red-700">
                {state.submitError}
              </p>
            ) : null}
            <CompletePaymentButton
              total={deliveryTotals.grandTotal}
              disabled={disabled}
              label="Thanh toán"
              loadingLabel="Đang tạo đơn..."
              loading={state.submitPending}
              onClick={onSubmit}
            />
          </div>
        ) : null}
      </div>

      {state.selectedDeliveryMode === "THIRD_PARTY" ? (
      <div className="grid min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] rounded-xl bg-white shadow-pos-panel">
        <div className="flex h-12 items-center justify-between border-b border-[#dfe5ec] px-5">
          <strong className="inline-flex items-center gap-2 text-sm font-black text-[#1479f6]">
            <Truck size={16} aria-hidden="true" />
            Đối tác vận chuyển
          </strong>
          <Button className="-mr-2" onClick={() => changeDeliveryMode("INTERNAL")} size="xs" variant="link">
            Tắt bên thứ 3
          </Button>
        </div>

        <div className="grid min-h-0 content-start gap-4 overflow-y-auto px-5 py-4">
          <div className="flex items-start gap-3 rounded-lg border border-[#b8d7ff] bg-[#eef6ff] px-4 py-3 text-sm font-semibold text-[#334155]">
            <MapPin className="mt-0.5 shrink-0 text-[#1479f6]" size={18} aria-hidden="true" />
            <span>Nhập đầy đủ địa chỉ lấy và giao để tra cước và tạo đơn.</span>
            <IconButton
              aria-label="Thiết lập vận chuyển"
              className="ml-auto"
              onClick={() => setShippingSettingsOpen(true)}
            >
              <Settings size={18} aria-hidden="true" />
            </IconButton>
          </div>

          {state.selectedDeliveryMode === "THIRD_PARTY" ? (
            <ThirdPartyProviderSelect
              value={state.selectedThirdPartyProvider}
              onChange={(selectedThirdPartyProvider) =>
                patchState({
                  selectedThirdPartyProvider,
                  thirdPartyQuotes: [],
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
                })
              }
            />
          ) : null}

          {state.selectedDeliveryMode ? (
            <DeliveryQuoteArea
              addressReady={!errors.address && Boolean(snapshot)}
              error={state.quoteError}
              loading={state.quoteLoading}
              mode={state.selectedDeliveryMode}
              options={getVisibleDeliveryOptions(state)}
              selectedOptionId={state.selectedDeliveryOptionId}
              onRefresh={onQuoteRefresh}
              onSelect={(optionId) => {
                const option =
                  getVisibleDeliveryOptions(state).find(
                    (item) => item.optionId === optionId
                  ) ?? null;

                if (
                  option &&
                  state.deliveryUrgency === "ASAP" &&
                  !option.availableNow &&
                  option.availableLater &&
                  !state.customerAcceptedDeliveryWait
                ) {
                  setWaitConfirmationOption(option);
                  return;
                }

                patchState({
                  selectedDeliveryOptionId: optionId,
                  selectedDeliveryQuoteId: option?.quoteId ?? null,
                  selectedThirdPartyQuoteId:
                    state.selectedDeliveryMode === "THIRD_PARTY"
                      ? option?.quoteId ?? null
                      : null,
                  deliverySelectionSource:
                    optionId === state.recommendedDeliveryOptionId
                      ? "RECOMMENDED"
                      : "MANUAL",
                  shippingFee: option?.shippingFee ?? 0,
                  customerAcceptedDeliveryWait: false
                });
              }}
            />
          ) : null}

          {showValidation && errors.deliveryOption ? (
            <FieldError message={errors.deliveryOption} />
          ) : null}
          {showValidation && errors.quote ? <FieldError message={errors.quote} /> : null}
          {quoteExpired ? <FieldError message="Báo giá giao hàng đã hết hạn." /> : null}
        </div>

        <div className="grid gap-3 border-t border-[#edf0f4] p-4">
          {state.submitError ? (
            <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-bold text-red-700">
              {state.submitError}
            </p>
          ) : null}
          <CompletePaymentButton
            total={deliveryTotals.grandTotal}
            disabled={disabled}
            label="Thanh toán"
            loadingLabel="Đang tạo đơn..."
            loading={state.submitPending}
            onClick={onSubmit}
          />
        </div>
      </div>
      ) : null}

      <AddressSelectionModal
        addresses={state.addresses}
        branchLocation={branchLocation}
        manualAddress={state.manualAddress}
        open={addressModalOpen}
        reason={state.addressSuggestionReason}
        saveAddress={state.saveAddress}
        selectedAddressId={state.selectedAddressId}
        setAsDefaultAddress={state.setAsDefaultAddress}
        onManualAddressChange={(manualAddress: ManualDeliveryAddress) =>
          patchState({
            manualAddress,
            useManualAddress: true,
            selectedAddressId: null,
            addressSuggestionReason: null,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            selectedThirdPartyQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            internalDeliveryOption: null,
            thirdPartyQuotes: [],
            selectedThirdPartyProvider: "ALL",
            shippingFee: 0,
            quoteError: ""
          })
        }
        onOpenChange={setAddressModalOpen}
        onSaveAddressChange={(saveAddress) => patchState({ saveAddress })}
        onSelectSaved={(addressId) =>
          patchState({
            selectedAddressId: addressId,
            useManualAddress: false,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            selectedThirdPartyQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            internalDeliveryOption: null,
            thirdPartyQuotes: [],
            selectedThirdPartyProvider: "ALL",
            shippingFee: 0,
            quoteError: ""
          })
        }
        onSetAsDefaultAddressChange={(setAsDefaultAddress) =>
          patchState({ setAsDefaultAddress })
        }
        onUseManualAddress={() =>
          patchState({
            useManualAddress: true,
            selectedAddressId: null,
            addressSuggestionReason: null,
            deliveryOptions: [],
            selectedDeliveryOptionId: null,
            selectedDeliveryQuoteId: null,
            selectedThirdPartyQuoteId: null,
            recommendedDeliveryOptionId: null,
            deliverySelectionSource: "RECOMMENDED",
            customerAcceptedDeliveryWait: false,
            internalDeliveryOption: null,
            thirdPartyQuotes: [],
            selectedThirdPartyProvider: "ALL",
            shippingFee: 0,
            quoteError: ""
          })
        }
      />

      <DeliveryWaitConfirmationModal
        option={waitConfirmationOption}
        onClose={() => setWaitConfirmationOption(null)}
        onConfirm={() => {
          if (!waitConfirmationOption) {
            return;
          }

          patchState({
            selectedDeliveryOptionId: waitConfirmationOption.optionId,
            selectedDeliveryQuoteId: waitConfirmationOption.quoteId,
            selectedThirdPartyQuoteId:
              waitConfirmationOption.mode === "THIRD_PARTY"
                ? waitConfirmationOption.quoteId
                : null,
            deliverySelectionSource:
              waitConfirmationOption.optionId === state.recommendedDeliveryOptionId
                ? "RECOMMENDED"
                : "MANUAL",
            shippingFee: waitConfirmationOption.shippingFee,
            customerAcceptedDeliveryWait: true
          });
          setWaitConfirmationOption(null);
        }}
      />

      <ShippingSettingsDialog
        open={shippingSettingsOpen}
        onOpenChange={setShippingSettingsOpen}
      />
    </section>
  );
}

function WaybillAddressForm({
  address,
  customer,
  manualAddress,
  useManualAddress,
  onAddressChange
}: {
  address: CustomerAddress | null;
  customer: SaleCustomer | null;
  manualAddress: ManualDeliveryAddress;
  useManualAddress: boolean;
  onAddressChange(): void;
}) {
  const recipientName = useManualAddress
    ? manualAddress.recipientName
    : address?.recipientName || customer?.name || "";
  const recipientPhone = useManualAddress
    ? manualAddress.recipientPhone
    : address?.recipientPhone || customer?.phone || "";
  const addressLine = useManualAddress
    ? manualAddress.addressLine || manualAddress.fullAddress
    : address?.addressLine || address?.fullAddress || "";
  const area = useManualAddress
    ? [manualAddress.districtName, manualAddress.provinceName].filter(Boolean).join(", ")
    : [address?.districtName, address?.provinceName].filter(Boolean).join(", ");
  const ward = useManualAddress ? manualAddress.wardName : address?.wardName || "";

  return (
    <section className="relative grid gap-4 pl-8">
      <span className="absolute left-0 top-2 grid size-5 place-items-center rounded-full bg-[#1479f6] text-white">
        <Phone size={12} aria-hidden="true" />
      </span>
      <span className="absolute left-[9px] top-8 h-16 border-l border-dashed border-[#c9d2dc]" />
      <span className="absolute left-0 top-[84px] grid size-5 place-items-center rounded-full bg-[#1aae4b] text-white">
        <MapPin size={12} aria-hidden="true" />
      </span>

      <button
        type="button"
        onClick={onAddressChange}
        className="grid border-b border-[#c9d2dc] pb-2 text-left"
      >
        <span className="text-base font-bold text-[#172317]">
          {recipientPhone || customer?.phone || "Chọn khách hàng hoặc số điện thoại"}
        </span>
        {!addressLine ? (
          <span className="mt-1 text-xs font-semibold text-red-600">
            Vui lòng thêm địa chỉ lấy hàng mới
          </span>
        ) : null}
      </button>

      <div className="grid grid-cols-2 gap-3">
        <ReadonlyLine icon={<User size={14} />} value={recipientName} placeholder="Tên người nhận" />
        <ReadonlyLine value={recipientPhone} placeholder="Số điện thoại" />
      </div>
      <ReadonlyLine value={addressLine} placeholder="Địa chỉ chi tiết (Số nhà, ngõ, đường)" />
      <ReadonlyLine value={area} placeholder="Khu vực" />
      <ReadonlyLine value={ward} placeholder="Phường/Xã" />

      <Button className="absolute right-0 top-0" onClick={onAddressChange} size="xs" variant="link">
        Chọn địa chỉ
      </Button>
    </section>
  );
}

function ReadonlyLine({
  icon,
  value,
  placeholder
}: {
  icon?: ReactNode;
  value: string;
  placeholder: string;
}) {
  return (
    <div className="flex h-10 items-center gap-2 border-b border-[#c9d2dc] text-sm font-semibold">
      {icon ? <span className="text-[#697684]">{icon}</span> : null}
      <span className={value ? "text-[#172317]" : "text-[#7c8794]"}>
        {value || placeholder}
      </span>
    </div>
  );
}

function DeliveryQuoteArea({
  addressReady,
  error,
  loading,
  mode,
  options,
  selectedOptionId,
  onRefresh,
  onSelect
}: {
  addressReady: boolean;
  error: string;
  loading: boolean;
  mode: DeliveryMode;
  options: DeliveryOption[];
  selectedOptionId: string | null;
  onRefresh(): void;
  onSelect(optionId: string): void;
}) {
  if (!addressReady) {
    return (
      <div className="grid min-h-[360px] place-items-center text-center">
        <div className="max-w-[360px] text-sm font-semibold text-[#697684]">
          <ProviderCloud />
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="grid gap-3">
        {[0, 1, 2].map((item) => (
          <span key={item} className="h-20 rounded-xl bg-[#edf0f4]" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="grid gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-700">
        {error}
        <Button onClick={onRefresh} size="sm" variant="info">
          Thử lại
        </Button>
      </div>
    );
  }

  if (options.length === 0) {
    return (
      <div className="grid min-h-[360px] place-items-center text-center">
        {mode === "THIRD_PARTY" ? <ProviderCloud /> : (
          <p className="text-sm font-semibold text-[#697684]">
            Chưa có phương án tự giao phù hợp.
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="grid gap-2">
      {options.map((option) => (
        <DeliveryOptionCard
          key={option.quoteId}
          option={option}
          selected={option.optionId === selectedOptionId}
          selectable={option.availableNow || option.availableLater}
          onSelect={onSelect}
        />
      ))}
    </div>
  );
}

function ProviderCloud() {
  const providers = ["GHN", "Ahamove"];

  return (
    <div className="grid gap-5">
      <p className="rounded-lg border border-[#b8d7ff] bg-[#eef6ff] px-4 py-3 text-left text-sm font-semibold text-[#334155]">
        Nhập đầy đủ địa chỉ lấy và giao để tra cước và tạo đơn.
      </p>
      <div className="grid gap-3">
        <strong className="text-xs font-black uppercase text-[#1479f6]">
          Đối tác vận chuyển của Hương Vân Trà
        </strong>
        <div className="grid grid-cols-3 gap-2 text-sm font-black text-[#334155]">
          {providers.map((provider) => (
            <span key={provider} className="rounded-lg border border-[#edf0f4] bg-white px-2 py-2">
              {provider}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function DeliveryWaitConfirmationModal({
  option,
  onClose,
  onConfirm
}: {
  option: DeliveryOption | null;
  onClose(): void;
  onConfirm(): void;
}) {
  const pickupTime = option?.nextAvailableAt ?? option?.estimatedPickupAt;

  return (
    <Dialog open={Boolean(option)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-[min(520px,calc(100vw-32px))]">
        <DialogHeader>
          <DialogTitle>Xác nhận khách đồng ý chờ</DialogTitle>
          <DialogDescription>
            Nhân viên cửa hàng chưa thể giao ngay. Thời gian lấy hàng dự kiến là{" "}
            {pickupTime ? formatDateTime(pickupTime) : "theo báo giá"}. Khách
            hàng đã đồng ý chờ?
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={onClose}>Quay lại</Button>
          <Button onClick={onConfirm} variant="primary">
            Xác nhận khách đồng ý
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ThirdPartyProviderSelect({
  value,
  onChange
}: {
  value: ThirdPartyDeliveryProvider;
  onChange(value: ThirdPartyDeliveryProvider): void;
}) {
  return (
    <label className="grid gap-1 text-xs font-black text-[#697684]">
      Đối tác giao hàng
      <select
        value={value}
        onChange={(event) =>
          onChange(event.currentTarget.value as ThirdPartyDeliveryProvider)
        }
        className="h-9 w-full min-w-0 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-bold text-[#172317] outline-none focus:border-[#159734]"
      >
        <option value="ALL">Tất cả đối tác</option>
        <option value="GHN">GHN</option>
        <option value="AHAMOVE">Ahamove</option>
      </select>
    </label>
  );
}

type ShippingSettingsTab = "GENERAL" | "PARTNERS";

interface ShippingSettingsForm {
  declaredValue: string;
  feePayer: "SENDER" | "RECEIVER";
  deliveryNote: string;
  courierNote: string;
  weight: string;
  length: string;
  width: string;
  height: string;
  defaultService: string;
  bulkyEnabled: boolean;
  bulkyStandard: string;
  driverSupportEnabled: boolean;
  driverSupportFee: string;
  applyAllAccounts: boolean;
  enabledPartners: Record<"GHN" | "AHAMOVE", boolean>;
}

const deliveryNoteOptions = [
  "Không cho xem hàng",
  "Cho xem, không thử",
  "Cho thử hàng"
];

const serviceOptions = [
  "SPX Express - Tiêu chuẩn",
  "AhaMove - Siêu tốc",
  "AhaMove - Siêu tốc - Tiết Kiệm",
  "AhaMove - Tiết kiệm",
  "AhaMove - 4H",
  "GHN - Nhanh",
  "GHN - Chuẩn"
];

function ShippingSettingsDialog({
  open,
  onOpenChange
}: {
  open: boolean;
  onOpenChange(open: boolean): void;
}) {
  const [activeTab, setActiveTab] = useState<ShippingSettingsTab>("GENERAL");
  const [form, setForm] = useState<ShippingSettingsForm>({
    declaredValue: "45,000",
    feePayer: "RECEIVER",
    deliveryNote: "Cho xem, không thử",
    courierNote: "không có ghio chữ",
    weight: "45,000",
    length: "10",
    width: "10",
    height: "10",
    defaultService: "AhaMove - Siêu tốc",
    bulkyEnabled: true,
    bulkyStandard: "Tiêu chuẩn 50x40x50:30kg",
    driverSupportEnabled: true,
    driverSupportFee: "5,000 đ",
    applyAllAccounts: false,
    enabledPartners: {
      GHN: true,
      AHAMOVE: true
    }
  });

  function patchForm(partial: Partial<ShippingSettingsForm>) {
    setForm((current) => ({
      ...current,
      ...partial
    }));
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(1000px,calc(100vw-40px))]">
        <DialogHeader className="px-7 py-6">
          <DialogTitle className="text-xl">Thiết lập vận chuyển</DialogTitle>
        </DialogHeader>

        <div className="grid min-h-[560px] grid-cols-[230px_1fr]">
          <aside className="border-r border-[#e1e5ea] bg-white">
            <ShippingSettingsTabButton
              active={activeTab === "GENERAL"}
              label="Thiết lập chung"
              onClick={() => setActiveTab("GENERAL")}
            />
            <ShippingSettingsTabButton
              active={activeTab === "PARTNERS"}
              label="Đối tác vận chuyển"
              onClick={() => setActiveTab("PARTNERS")}
            />
          </aside>

          <main className="min-w-0 px-8 py-5">
            {activeTab === "GENERAL" ? (
              <ShippingGeneralSettings form={form} onChange={patchForm} />
            ) : (
              <ShippingPartnerSettings form={form} onChange={patchForm} />
            )}
          </main>
        </div>

        <DialogFooter className="items-center justify-between px-7 py-5">
          <label className="inline-flex items-center gap-3 text-sm font-semibold text-[#172317]">
            <input
              type="checkbox"
              checked={form.applyAllAccounts}
              onChange={(event) =>
                patchForm({ applyAllAccounts: event.currentTarget.checked })
              }
              className="size-4 accent-[#1479f6]"
            />
            Áp dụng thiết lập cho tất cả tài khoản của gian hàng
          </label>
          <div className="flex gap-3">
            <Button className="min-w-[128px]" onClick={() => onOpenChange(false)} size="search">
              Bỏ qua
            </Button>
            <Button className="min-w-[128px]" onClick={() => onOpenChange(false)} size="search" variant="info">
              Lưu
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ShippingSettingsTabButton({
  active,
  label,
  onClick
}: {
  active: boolean;
  label: string;
  onClick(): void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`h-14 w-full border-b border-[#e1e5ea] px-8 text-left text-base font-black ${
        active
          ? "border-l-2 border-l-[#1479f6] bg-[#eef6ff] text-[#1479f6]"
          : "bg-white text-[#172317] hover:bg-[#f8fafc]"
      }`}
    >
      {label}
    </button>
  );
}

function ShippingGeneralSettings({
  form,
  onChange
}: {
  form: ShippingSettingsForm;
  onChange(partial: Partial<ShippingSettingsForm>): void;
}) {
  return (
    <div className="grid max-w-[720px] gap-6">
      <SettingsRow label="Khai giá">
        <div className="flex items-center gap-4">
          <LineInput
            value={form.declaredValue}
            onChange={(declaredValue) => onChange({ declaredValue })}
          />
          <Toggle checked onChange={() => undefined} />
        </div>
      </SettingsRow>

      <SettingsRow label="Người trả phí">
        <div className="flex items-center gap-10">
          <RadioChoice
            checked={form.feePayer === "SENDER"}
            label="Người gửi"
            onChange={() => onChange({ feePayer: "SENDER" })}
          />
          <RadioChoice
            checked={form.feePayer === "RECEIVER"}
            label="Người nhận"
            onChange={() => onChange({ feePayer: "RECEIVER" })}
          />
        </div>
      </SettingsRow>

      <SettingsRow label="Lưu ý giao hàng">
        <LineSelect
          value={form.deliveryNote}
          options={deliveryNoteOptions}
          onChange={(deliveryNote) => onChange({ deliveryNote })}
        />
      </SettingsRow>

      <SettingsRow label="Ghi chú cho bưu tá">
        <LineInput
          value={form.courierNote}
          onChange={(courierNote) => onChange({ courierNote })}
        />
      </SettingsRow>

      <SettingsRow label="Trọng lượng & Kích thước">
        <div className="flex flex-wrap items-center gap-3">
          <LineInput
            className="w-16"
            value={form.weight}
            onChange={(weight) => onChange({ weight })}
          />
          <LineSelect
            className="w-[78px]"
            value="gram"
            options={["gram", "kg"]}
            onChange={() => undefined}
          />
          <DimensionInput
            value={form.length}
            onChange={(length) => onChange({ length })}
          />
          <span className="text-[#697684]">x</span>
          <DimensionInput
            value={form.width}
            onChange={(width) => onChange({ width })}
          />
          <span className="text-[#697684]">x</span>
          <DimensionInput
            value={form.height}
            onChange={(height) => onChange({ height })}
          />
          <LineSelect
            className="w-16"
            value="cm"
            options={["cm"]}
            onChange={() => undefined}
          />
        </div>
      </SettingsRow>

      <SettingsRow label="Dịch vụ mặc định">
        <LineSelect
          value={form.defaultService}
          options={serviceOptions}
          onChange={(defaultService) => onChange({ defaultService })}
        />
      </SettingsRow>

      <div className="ml-[212px] grid max-h-[172px] gap-3 overflow-y-auto rounded-md border border-[#e1e5ea] bg-[#f8fafc] p-4">
        <AddOnRow
          checked={form.bulkyEnabled}
          label="Giao hàng cồng kềnh"
          onCheckedChange={(bulkyEnabled) => onChange({ bulkyEnabled })}
        >
          <LineSelect
            value={form.bulkyStandard}
            options={["---Tiêu chuẩn---", "Tiêu chuẩn 50x40x50:30kg", "Miễn phí"]}
            onChange={(bulkyStandard) => onChange({ bulkyStandard })}
          />
        </AddOnRow>
        <AddOnRow
          checked={form.driverSupportEnabled}
          label="Hỗ trợ tài xế"
          onCheckedChange={(driverSupportEnabled) =>
            onChange({ driverSupportEnabled })
          }
        >
          <LineSelect
            value={form.driverSupportFee}
            options={["5,000 đ", "25,000 đ", "30,000 đ"]}
            onChange={(driverSupportFee) => onChange({ driverSupportFee })}
          />
        </AddOnRow>
      </div>
    </div>
  );
}

function ShippingPartnerSettings({
  form,
  onChange
}: {
  form: ShippingSettingsForm;
  onChange(partial: Partial<ShippingSettingsForm>): void;
}) {
  return (
    <div className="grid max-w-[620px] gap-4">
      <p className="text-sm font-semibold text-[#697684]">
        Chọn đối tác được phép xuất hiện trong danh sách báo giá tại POS.
      </p>
      {[
        { key: "GHN" as const, label: "GHN" },
        { key: "AHAMOVE" as const, label: "AhaMove" }
      ].map((partner) => (
        <label
          key={partner.key}
          className="flex h-14 items-center justify-between rounded-lg border border-[#e1e5ea] px-4 text-base font-bold text-[#172317]"
        >
          {partner.label}
          <input
            type="checkbox"
            checked={form.enabledPartners[partner.key]}
            onChange={(event) =>
              onChange({
                enabledPartners: {
                  ...form.enabledPartners,
                  [partner.key]: event.currentTarget.checked
                }
              })
            }
            className="size-5 accent-[#1479f6]"
          />
        </label>
      ))}
    </div>
  );
}

function SettingsRow({
  label,
  children
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="grid grid-cols-[190px_1fr] items-center gap-6">
      <span className="text-base font-semibold text-[#172317]">{label}</span>
      {children}
    </div>
  );
}

function LineInput({
  className = "w-full",
  value,
  onChange
}: {
  className?: string;
  value: string;
  onChange(value: string): void;
}) {
  return (
    <input
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      className={`h-9 border-0 border-b border-[#bfc7d2] bg-transparent px-0 text-base font-semibold text-[#172317] outline-none focus:border-[#1479f6] ${className}`}
    />
  );
}

function DimensionInput({
  value,
  onChange
}: {
  value: string;
  onChange(value: string): void;
}) {
  return (
    <LineInput className="w-12 text-center" value={value} onChange={onChange} />
  );
}

function LineSelect({
  className = "w-full",
  value,
  options,
  onChange
}: {
  className?: string;
  value: string;
  options: string[];
  onChange(value: string): void;
}) {
  return (
    <select
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      className={`h-9 border-0 border-b border-[#bfc7d2] bg-transparent px-0 text-base font-semibold text-[#172317] outline-none focus:border-[#1479f6] ${className}`}
    >
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  );
}

function RadioChoice({
  checked,
  label,
  onChange
}: {
  checked: boolean;
  label: string;
  onChange(): void;
}) {
  return (
    <label className="inline-flex items-center gap-2 text-base font-semibold text-[#172317]">
      <input
        type="radio"
        checked={checked}
        onChange={onChange}
        className="size-5 accent-[#1479f6]"
      />
      {label}
    </label>
  );
}

function Toggle({
  checked,
  onChange
}: {
  checked: boolean;
  onChange(checked: boolean): void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`relative h-5 w-9 rounded-full ${checked ? "bg-[#1479f6]" : "bg-[#cbd5e1]"}`}
    >
      <span
        className={`absolute top-0.5 size-4 rounded-full bg-white shadow transition-transform ${
          checked ? "translate-x-[18px]" : "translate-x-0.5"
        }`}
      />
    </button>
  );
}

function AddOnRow({
  checked,
  children,
  label,
  onCheckedChange
}: {
  checked: boolean;
  children: ReactNode;
  label: string;
  onCheckedChange(checked: boolean): void;
}) {
  return (
    <div className="grid grid-cols-[auto_1fr_240px] items-center gap-3">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onCheckedChange(event.currentTarget.checked)}
        className="size-5 accent-[#1479f6]"
      />
      <span className="inline-flex items-center gap-2 text-base font-semibold text-[#172317]">
        {label}
        <Info size={16} aria-hidden="true" />
      </span>
      {children}
    </div>
  );
}

function PackageInformationAccordion({
  cartItemCount,
  open,
  onOpenChange
}: {
  cartItemCount: number;
  open: boolean;
  onOpenChange(open: boolean): void;
}) {
  return (
    <section className="grid gap-2 border-b border-pos-border pb-3">
      <button
        type="button"
        onClick={() => onOpenChange(!open)}
        className="flex items-center justify-between rounded-lg border border-[#edf0f4] bg-white px-3 py-2 text-left"
      >
        <span>
          <strong className="block text-sm text-[#172317]">
            Thông tin kiện hàng
          </strong>
          <span className="text-xs font-bold text-[#697684]">
            {Math.max(1, cartItemCount)} kiện · 500g · 20 x 15 x 10cm
          </span>
        </span>
        <ChevronDown
          size={16}
          aria-hidden="true"
          className={open ? "rotate-180 transition-transform" : "transition-transform"}
        />
      </button>
      {open ? (
        <div className="grid grid-cols-2 gap-2">
          {[
            ["Số kiện", Math.max(1, cartItemCount)],
            ["Khối lượng", "500"],
            ["Dài", "20"],
            ["Rộng", "15"],
            ["Cao", "10"]
          ].map(([label, value]) => (
            <label
              key={label}
              className="grid gap-1 text-[11px] font-bold text-[#53678f]"
            >
              {label}
              <input
                type="text"
                value={value}
                readOnly
                className="h-8 rounded-lg border border-[#d8eadc] bg-white px-2 text-xs font-bold text-[#172317]"
              />
            </label>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function FieldError({ message }: { message: string }) {
  return (
    <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-bold text-red-700">
      {message}
    </p>
  );
}

function getVisibleDeliveryOptions(state: DeliveryCheckoutState) {
  if (state.selectedDeliveryMode === "INTERNAL") {
    return state.internalDeliveryOption ? [state.internalDeliveryOption] : [];
  }

  if (state.selectedDeliveryMode === "THIRD_PARTY") {
    if (state.selectedThirdPartyProvider === "ALL") {
      return state.thirdPartyQuotes;
    }

    return state.thirdPartyQuotes.filter(
      (quote) =>
        normalizeProvider(quote.provider) === state.selectedThirdPartyProvider
    );
  }

  return [];
}

function normalizeProvider(provider: string): Exclude<ThirdPartyDeliveryProvider, "ALL"> | null {
  const normalized = provider.toUpperCase().replace(/[\s_-]/g, "");

  if (normalized.includes("GHN")) {
    return "GHN";
  }

  if (normalized.includes("AHAMOVE") || normalized.includes("AHMOVE")) {
    return "AHAMOVE";
  }

  return null;
}

function getSelectedStateDeliveryOption(state: DeliveryCheckoutState) {
  if (state.selectedDeliveryMode === "INTERNAL") {
    return state.internalDeliveryOption &&
      state.selectedDeliveryOptionId === state.internalDeliveryOption.optionId
      ? state.internalDeliveryOption
      : null;
  }

  if (state.selectedDeliveryMode === "THIRD_PARTY") {
    return (
      state.thirdPartyQuotes.find(
        (quote) => quote.quoteId === state.selectedThirdPartyQuoteId
      ) ?? null
    );
  }

  return null;
}

function getDeliveryValidationErrors({
  cartItems,
  customer,
  snapshot,
  selectedOption,
  selectedMode,
  urgency,
  customerAcceptedWait,
  requestedDeliveryAt
}: {
  cartItems: SaleCartItem[];
  customer: SaleCustomer | null;
  snapshot: ReturnType<typeof getDeliverySnapshotFromState>;
  selectedOption: DeliveryOption | null;
  selectedMode: DeliveryMode | null;
  urgency: DeliveryUrgency;
  customerAcceptedWait: boolean;
  requestedDeliveryAt: string;
}) {
  const errors: DeliveryValidationErrors = {};

  if (cartItems.length === 0) {
    errors.customer = "Giỏ hàng đang rỗng.";
  }

  if (!customer) {
    errors.customer = "Vui lòng chọn khách hàng.";
  }

  if (!snapshot) {
    errors.address = "Vui lòng chọn hoặc nhập địa chỉ giao hàng.";
    return errors;
  }

  if (!snapshot.recipientName) {
    errors.recipientName = "Thiếu tên người nhận.";
  }

  if (!isPhoneValid(snapshot.recipientPhone)) {
    errors.recipientPhone = "Số điện thoại người nhận không hợp lệ.";
  }

  if (!snapshot.deliveryAddress) {
    errors.address = "Thiếu địa chỉ giao hàng.";
  }

  if (!snapshot.deliveryProvinceCode) {
    errors.province = "Thiếu tỉnh/thành phố.";
  }

  if (!snapshot.deliveryDistrictCode) {
    errors.district = "Thiếu quận/huyện.";
  }

  if (!snapshot.deliveryWardCode) {
    errors.ward = "Thiếu phường/xã.";
  }

  if (!selectedMode) {
    errors.deliveryOption = "Vui lòng chọn hình thức vận chuyển.";
    return errors;
  }

  if (!selectedOption?.available) {
    errors.deliveryOption =
      selectedMode === "INTERNAL"
        ? "Chưa có phương án giao nội bộ hợp lệ."
        : "Vui lòng chọn provider giao hàng.";
  }

  if (
    selectedOption &&
    !isDeliveryOptionSelectable(selectedOption, urgency, customerAcceptedWait)
  ) {
    errors.deliveryOption = "Phương thức giao hàng này chưa thể giao ngay.";
  }

  if (urgency === "SCHEDULED") {
    const requestedAt = Date.parse(requestedDeliveryAt);

    if (!requestedDeliveryAt || Number.isNaN(requestedAt)) {
      errors.quote = "Vui lòng chọn thời gian giao hẹn giờ.";
    } else if (requestedAt <= Date.now()) {
      errors.quote = "Không thể chọn thời gian giao trong quá khứ.";
    }
  }

  return errors;
}

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}
