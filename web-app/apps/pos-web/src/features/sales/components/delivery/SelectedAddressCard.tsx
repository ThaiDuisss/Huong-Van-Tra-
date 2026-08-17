import { MapPin, Pencil, Plus } from "lucide-react";

import type {
  AddressSuggestionReason,
  BranchLocation,
  CustomerAddress,
  ManualDeliveryAddress
} from "../../types/delivery.types";
import {
  getAddressDistanceKm,
  getSuggestionLabel,
  manualAddressToSnapshot
} from "../../services/delivery.utils";

interface SelectedAddressCardProps {
  address: CustomerAddress | null;
  branchLocation?: BranchLocation | null;
  manualAddress: ManualDeliveryAddress;
  reason: AddressSuggestionReason | null;
  useManualAddress: boolean;
  onChange(): void;
}

function formatDistance(distanceKm?: number) {
  return typeof distanceKm === "number"
    ? `${distanceKm.toFixed(distanceKm >= 10 ? 0 : 1)} km tới chi nhánh`
    : "Chưa có tọa độ để tính khoảng cách";
}

export function SelectedAddressCard({
  address,
  branchLocation,
  manualAddress,
  reason,
  useManualAddress,
  onChange
}: SelectedAddressCardProps) {
  const snapshot = useManualAddress
    ? manualAddressToSnapshot(manualAddress)
    : address
      ? {
          recipientName: address.recipientName ?? "",
          recipientPhone: address.recipientPhone ?? "",
          deliveryAddress: address.fullAddress ?? address.addressLine ?? ""
        }
      : null;
  const distanceKm = address
    ? getAddressDistanceKm(address, branchLocation)
    : undefined;

  if (!snapshot?.deliveryAddress) {
    return (
      <div className="grid gap-3 rounded-xl border border-dashed border-[#b9dfc8] bg-white p-4">
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-[#eaf8ed] text-[#159734]">
            <MapPin size={18} aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <strong className="text-sm text-[#172317]">
              Chưa có địa chỉ giao hàng
            </strong>
            <p className="mt-1 text-xs font-semibold text-[#697684]">
              Có thể chọn địa chỉ đã lưu hoặc nhập địa chỉ mới cho đơn này.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onChange}
          className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#d8eadc] text-sm font-extrabold text-[#159734] hover:bg-[#f4faf5]"
        >
          <Plus size={16} aria-hidden="true" />
          Thêm địa chỉ giao hàng
        </button>
      </div>
    );
  }

  return (
    <div className="grid gap-3 rounded-xl border border-[#d8eadc] bg-white p-4">
      <div className="flex items-start justify-between gap-3">
        <span className="inline-flex w-fit rounded-full bg-[#eaf8ed] px-2.5 py-1 text-[11px] font-black text-[#159734]">
          {useManualAddress ? "Địa chỉ nhập mới" : getSuggestionLabel(reason)}
        </span>
        <button
          type="button"
          onClick={onChange}
          className="inline-flex h-8 items-center gap-1 rounded-lg px-2 text-xs font-bold text-[#159734] hover:bg-[#f4faf5]"
        >
          <Pencil size={14} aria-hidden="true" />
          Thay đổi
        </button>
      </div>

      <div className="grid gap-1 text-sm">
        <strong className="text-[#172317]">
          {snapshot.recipientName || "Chưa có tên người nhận"}
        </strong>
        <span className="font-semibold text-[#53678f]">
          {snapshot.recipientPhone || "Chưa có số điện thoại"}
        </span>
        <span className="text-xs font-semibold leading-relaxed text-[#697684]">
          {snapshot.deliveryAddress}
        </span>
        <span className="text-[11px] font-bold text-[#159734]">
          {useManualAddress ? "Khoảng cách sẽ được tính khi có tọa độ." : formatDistance(distanceKm)}
        </span>
      </div>
    </div>
  );
}
