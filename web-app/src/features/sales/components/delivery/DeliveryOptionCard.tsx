import { Clock3, Store, Timer, Truck } from "lucide-react";

import { formatCurrency } from "../../services/money";
import type { DeliveryOption } from "../../types/delivery.types";

interface DeliveryOptionCardProps {
  option: DeliveryOption;
  selected: boolean;
  selectable: boolean;
  onSelect(optionId: string): void;
}

function getModeLabel(option: DeliveryOption) {
  return option.mode === "INTERNAL" ? "Nhân viên cửa hàng" : "Đối tác vận chuyển";
}

function getEta(option: DeliveryOption) {
  if (option.deliveryEtaMinutes) {
    return `${option.deliveryEtaMinutes} phút`;
  }

  if (option.estimatedMinutes) {
    return `${option.estimatedMinutes} phút`;
  }

  if (option.estimatedDeliveryAt) {
    return formatDateTime(option.estimatedDeliveryAt);
  }

  if (option.estimatedDeliveryFrom || option.estimatedDeliveryTo) {
    return [formatDateTime(option.estimatedDeliveryFrom), formatDateTime(option.estimatedDeliveryTo)]
      .filter(Boolean)
      .join(" - ");
  }

  return "Theo báo giá";
}

function getPickupEta(option: DeliveryOption) {
  if (option.pickupEtaMinutes) {
    return `${option.pickupEtaMinutes} phút`;
  }

  if (option.estimatedPickupAt) {
    return formatDateTime(option.estimatedPickupAt);
  }

  if (option.nextAvailableAt) {
    return `Từ ${formatDateTime(option.nextAvailableAt)}`;
  }

  return option.availableNow ? "Có thể lấy hàng ngay" : "Theo báo giá";
}

function formatDateTime(value?: string) {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "2-digit"
  }).format(date);
}

export function DeliveryOptionCard({
  option,
  selected,
  selectable,
  onSelect
}: DeliveryOptionCardProps) {
  const Icon = option.mode === "INTERNAL" ? Store : Truck;
  const unavailableMessage =
    option.unavailableReason || "Phương thức này hiện không khả dụng.";

  return (
    <label
      className={`grid cursor-pointer gap-2 rounded-xl border p-3 ${
        selected
          ? "border-[#159734] bg-[#f4fbf6]"
          : selectable
            ? "border-[#edf0f4] bg-white"
            : "border-red-100 bg-red-50/50"
      } ${selectable ? "" : "cursor-not-allowed opacity-75"}`}
    >
      <span className="flex items-start gap-3">
        <input
          type="radio"
          name="deliveryOption"
          checked={selected}
          disabled={!selectable}
          onChange={() => onSelect(option.optionId)}
          className="mt-1"
        />
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-[#eaf8ed] text-[#159734]">
          <Icon size={18} aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2">
            <strong className="text-sm text-[#172317]">
              {getModeLabel(option)}
            </strong>
            {option.recommended ? (
              <span className="rounded-full bg-[#fff7e8] px-2 py-0.5 text-[10px] font-black text-[#c77906]">
                Đề xuất
              </span>
            ) : null}
          </span>
          <span className="mt-0.5 block text-xs font-semibold text-[#53678f]">
            {option.provider} · {option.serviceName}
          </span>
        </span>
        <strong className="text-sm text-[#159734]">
          {formatCurrency(option.shippingFee)}
        </strong>
      </span>

      <span className="ml-14 flex flex-wrap items-center gap-3 text-[11px] font-bold text-[#697684]">
        <span className="inline-flex items-center gap-1">
          <Timer size={13} aria-hidden="true" />
          Lấy hàng: {getPickupEta(option)}
        </span>
        <span className="inline-flex items-center gap-1">
          <Clock3 size={13} aria-hidden="true" />
          Giao: {getEta(option)}
        </span>
        {typeof option.distanceKm === "number" ? (
          <span>{option.distanceKm.toFixed(1)} km</span>
        ) : null}
      </span>

      <span className="ml-14 flex flex-wrap gap-2 text-[11px] font-black">
        {option.availableNow ? (
          <span className="rounded-full bg-[#eaf8ed] px-2 py-0.5 text-[#159734]">
            Có thể giao ngay
          </span>
        ) : option.availableLater ? (
          <span className="rounded-full bg-[#fff7e8] px-2 py-0.5 text-[#9b5d05]">
            Chưa thể giao ngay
          </span>
        ) : (
          <span className="rounded-full bg-red-100 px-2 py-0.5 text-red-700">
            Không khả dụng
          </span>
        )}
        {option.recommendationReason ? (
          <span className="rounded-full bg-[#eef4ff] px-2 py-0.5 text-[#315aa6]">
            {option.recommendationReason}
          </span>
        ) : null}
      </span>

      {!selectable ? (
        <span className="ml-14 rounded-lg bg-white px-3 py-2 text-xs font-bold text-red-700">
          {unavailableMessage}
        </span>
      ) : null}
    </label>
  );
}
