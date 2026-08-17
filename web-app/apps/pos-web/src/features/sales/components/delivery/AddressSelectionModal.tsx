import { useState } from "react";
import { MapPin, Save } from "lucide-react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "../../../../components/ui/dialog";
import type {
  AddressSuggestionReason,
  BranchLocation,
  CustomerAddress,
  DeliveryAddressType,
  ManualDeliveryAddress
} from "../../types/delivery.types";
import {
  getAddressDistanceKm,
  getSuggestionLabel
} from "../../services/delivery.utils";

interface AddressSelectionModalProps {
  addresses: CustomerAddress[];
  branchLocation?: BranchLocation | null;
  manualAddress: ManualDeliveryAddress;
  open: boolean;
  reason: AddressSuggestionReason | null;
  saveAddress: boolean;
  selectedAddressId: string | null;
  setAsDefaultAddress: boolean;
  onManualAddressChange(address: ManualDeliveryAddress): void;
  onOpenChange(open: boolean): void;
  onSaveAddressChange(value: boolean): void;
  onSelectSaved(addressId: string): void;
  onSetAsDefaultAddressChange(value: boolean): void;
  onUseManualAddress(): void;
}

const addressTypeOptions: Array<{ value: DeliveryAddressType; label: string }> = [
  { value: "HOME", label: "Nhà riêng" },
  { value: "OFFICE", label: "Công ty" },
  { value: "OTHER", label: "Khác" }
];

function updateManualAddress(
  address: ManualDeliveryAddress,
  key: keyof ManualDeliveryAddress,
  value: string
) {
  return {
    ...address,
    [key]: value
  };
}

export function AddressSelectionModal({
  addresses,
  branchLocation,
  manualAddress,
  open,
  reason,
  saveAddress,
  selectedAddressId,
  setAsDefaultAddress,
  onManualAddressChange,
  onOpenChange,
  onSaveAddressChange,
  onSelectSaved,
  onSetAsDefaultAddressChange,
  onUseManualAddress
}: AddressSelectionModalProps) {
  const [tab, setTab] = useState<"SAVED" | "MANUAL">("SAVED");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(760px,calc(100vw-32px))]">
        <DialogHeader>
          <DialogTitle>Chọn địa chỉ giao hàng</DialogTitle>
          <DialogDescription>
            Chọn địa chỉ đã lưu hoặc nhập địa chỉ mới cho đơn giao hàng.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 px-6 py-5">
          <div className="grid grid-cols-2 gap-2 rounded-xl bg-[#f4f6f8] p-1">
            <button
              type="button"
              onClick={() => setTab("SAVED")}
              className={`h-10 rounded-lg text-sm font-extrabold ${
                tab === "SAVED"
                  ? "bg-white text-[#159734] shadow-sm"
                  : "text-[#697684]"
              }`}
            >
              Địa chỉ đã lưu
            </button>
            <button
              type="button"
              onClick={() => setTab("MANUAL")}
              className={`h-10 rounded-lg text-sm font-extrabold ${
                tab === "MANUAL"
                  ? "bg-white text-[#159734] shadow-sm"
                  : "text-[#697684]"
              }`}
            >
              Nhập địa chỉ mới
            </button>
          </div>

          {tab === "SAVED" ? (
            <div className="grid max-h-[430px] gap-3 overflow-auto pr-1">
              {addresses.length === 0 ? (
                <div className="rounded-xl border border-dashed border-[#b9dfc8] bg-[#f7fff9] p-4 text-sm font-semibold text-[#697684]">
                  Khách hàng chưa có địa chỉ lưu. Vui lòng nhập địa chỉ mới.
                </div>
              ) : null}

              {addresses.map((address) => {
                const active = selectedAddressId === address.id;
                const distanceKm = getAddressDistanceKm(address, branchLocation);

                return (
                  <label
                    key={address.id}
                    className={`grid cursor-pointer gap-2 rounded-xl border p-4 ${
                      active
                        ? "border-[#159734] bg-[#f4fbf6]"
                        : "border-[#edf0f4] bg-white"
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <input
                        type="radio"
                        name="savedDeliveryAddress"
                        checked={active}
                        onChange={() => onSelectSaved(address.id)}
                      />
                      <strong className="text-sm text-[#172317]">
                        {address.label || "Địa chỉ"}
                      </strong>
                      {address.id === selectedAddressId && reason ? (
                        <span className="rounded-full bg-[#eaf8ed] px-2 py-0.5 text-[11px] font-black text-[#159734]">
                          {getSuggestionLabel(reason)}
                        </span>
                      ) : null}
                    </span>
                    <span className="text-xs font-bold text-[#53678f]">
                      {address.recipientName} · {address.recipientPhone}
                    </span>
                    <span className="text-xs font-semibold leading-relaxed text-[#697684]">
                      {address.fullAddress ?? address.addressLine}
                    </span>
                    <span className="text-[11px] font-bold text-[#159734]">
                      {typeof distanceKm === "number"
                        ? `${distanceKm.toFixed(1)} km`
                        : "Chưa có tọa độ"}
                    </span>
                  </label>
                );
              })}
            </div>
          ) : (
            <div className="grid max-h-[430px] gap-3 overflow-auto pr-1">
              <div className="grid grid-cols-2 gap-3">
                <Field
                  label="Tên người nhận"
                  value={manualAddress.recipientName}
                  onChange={(value) =>
                    onManualAddressChange(updateManualAddress(manualAddress, "recipientName", value))
                  }
                />
                <Field
                  label="Số điện thoại"
                  value={manualAddress.recipientPhone}
                  onChange={(value) =>
                    onManualAddressChange(updateManualAddress(manualAddress, "recipientPhone", value))
                  }
                />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <Field label="Tỉnh/thành phố" value={manualAddress.provinceName} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "provinceName", value))} />
                <Field label="Quận/huyện" value={manualAddress.districtName} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "districtName", value))} />
                <Field label="Phường/xã" value={manualAddress.wardName} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "wardName", value))} />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <Field label="Mã tỉnh" value={manualAddress.provinceCode} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "provinceCode", value))} />
                <Field label="Mã huyện" value={manualAddress.districtCode} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "districtCode", value))} />
                <Field label="Mã xã" value={manualAddress.wardCode} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "wardCode", value))} />
              </div>
              <Field label="Địa chỉ chi tiết" value={manualAddress.addressLine} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "addressLine", value))} />
              <Field label="Địa chỉ đầy đủ" value={manualAddress.fullAddress} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "fullAddress", value))} />
              <Field label="Ghi chú giao hàng" value={manualAddress.deliveryNote} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "deliveryNote", value))} />
              <div className="grid grid-cols-3 gap-3">
                <Field label="Latitude" value={manualAddress.latitude} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "latitude", value))} />
                <Field label="Longitude" value={manualAddress.longitude} onChange={(value) => onManualAddressChange(updateManualAddress(manualAddress, "longitude", value))} />
                <label className="grid gap-1 text-xs font-bold text-[#53678f]">
                  Loại địa chỉ
                  <select
                    value={manualAddress.addressType}
                    onChange={(event) =>
                      onManualAddressChange({
                        ...manualAddress,
                        addressType: event.currentTarget.value as DeliveryAddressType
                      })
                    }
                    className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-semibold text-[#172317]"
                  >
                    {addressTypeOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label className="flex items-center gap-2 text-xs font-bold text-[#53678f]">
                <input
                  type="checkbox"
                  checked={saveAddress}
                  onChange={(event) => onSaveAddressChange(event.currentTarget.checked)}
                />
                Lưu địa chỉ này cho khách hàng
              </label>
              {saveAddress ? (
                <label className="flex items-center gap-2 text-xs font-bold text-[#53678f]">
                  <input
                    type="checkbox"
                    checked={setAsDefaultAddress}
                    onChange={(event) =>
                      onSetAsDefaultAddressChange(event.currentTarget.checked)
                    }
                  />
                  Đặt làm địa chỉ mặc định
                </label>
              ) : null}
            </div>
          )}
        </div>

        <DialogFooter>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            className="h-10 rounded-lg border border-[#edf0f4] px-4 text-sm font-bold text-[#697684] hover:bg-[#f4f6f8]"
          >
            Đóng
          </button>
          {tab === "SAVED" ? (
            <button
              type="button"
              disabled={!selectedAddressId}
              onClick={() => onOpenChange(false)}
              className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-extrabold text-white disabled:opacity-50"
            >
              <MapPin size={16} aria-hidden="true" />
              Sử dụng địa chỉ này
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                onUseManualAddress();
                onOpenChange(false);
              }}
              className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-extrabold text-white"
            >
              <Save size={16} aria-hidden="true" />
              Sử dụng địa chỉ mới
            </button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Field({
  label,
  value,
  onChange
}: {
  label: string;
  value: string;
  onChange(value: string): void;
}) {
  return (
    <label className="grid gap-1 text-xs font-bold text-[#53678f]">
      {label}
      <input
        value={value}
        onChange={(event) => onChange(event.currentTarget.value)}
        className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-semibold text-[#172317] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#bce5c7]"
      />
    </label>
  );
}
