import { AlertTriangle, Loader2, RefreshCw } from "lucide-react";

import type { DeliveryOption } from "../../types/delivery.types";
import { isQuoteExpired } from "../../services/delivery.utils";
import { DeliveryOptionCard } from "./DeliveryOptionCard";

interface DeliveryOptionsSectionProps {
  addressReady: boolean;
  error: string;
  loading: boolean;
  options: DeliveryOption[];
  selectedOptionId: string | null;
  onRefresh(): void;
  onSelect(optionId: string): void;
}

export function DeliveryOptionsSection({
  addressReady,
  error,
  loading,
  options,
  selectedOptionId,
  onRefresh,
  onSelect
}: DeliveryOptionsSectionProps) {
  const selectableCount = options.filter((option) =>
    canSelectDeliveryCard(option)
  ).length;
  const selectedOption =
    options.find((option) => option.optionId === selectedOptionId) ?? null;
  const quoteExpired = isQuoteExpired(selectedOption);

  return (
    <section className="grid gap-3 border-b border-pos-border pb-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-base font-bold text-gray-500">
          Phương thức giao hàng
        </h2>
        <button
          type="button"
          onClick={onRefresh}
          disabled={!addressReady || loading}
          className="inline-flex h-8 items-center gap-1 rounded-lg px-2 text-xs font-bold text-[#159734] hover:bg-[#f4faf5] disabled:opacity-50"
        >
          <RefreshCw size={14} aria-hidden="true" />
          Báo giá lại
        </button>
      </div>

      {!addressReady ? (
        <p className="rounded-xl border border-dashed border-[#d8eadc] bg-white px-3 py-4 text-sm font-semibold text-[#697684]">
          Chọn địa chỉ hợp lệ để lấy phương thức giao hàng.
        </p>
      ) : null}

      {loading ? (
        <div className="grid gap-2 rounded-xl border border-[#edf0f4] bg-white p-4 text-sm font-bold text-[#697684]">
          <span className="inline-flex items-center gap-2">
            <Loader2 className="animate-spin" size={16} aria-hidden="true" />
            Đang lấy báo giá giao hàng...
          </span>
          <span className="h-3 rounded-full bg-[#edf0f4]" />
          <span className="h-3 w-4/5 rounded-full bg-[#edf0f4]" />
        </div>
      ) : null}

      {error && !loading ? (
        <p className="inline-flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm font-bold text-red-700">
          <AlertTriangle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
          {error}
        </p>
      ) : null}

      {quoteExpired ? (
        <p className="rounded-xl border border-[#f1d9a8] bg-[#fff7e8] px-3 py-3 text-sm font-bold text-[#9b5d05]">
          Báo giá đã hết hạn. Vui lòng báo giá lại trước khi tạo đơn.
        </p>
      ) : null}

      {!loading && addressReady && options.length === 0 && !error ? (
        <p className="rounded-xl border border-dashed border-[#d8eadc] bg-white px-3 py-4 text-sm font-semibold text-[#697684]">
          Chưa có phương án giao hàng phù hợp.
        </p>
      ) : null}

      {!loading && options.length > 0 ? (
        <div className="grid gap-2">
          {selectableCount === 0 ? (
            <p className="rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm font-bold text-red-700">
              Không có phương thức giao hàng khả dụng cho địa chỉ này.
            </p>
          ) : null}
          {options.map((option) => (
            <DeliveryOptionCard
              key={option.quoteId}
              option={option}
              selected={option.optionId === selectedOptionId}
              selectable={canSelectDeliveryCard(option)}
              onSelect={onSelect}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

function canSelectDeliveryCard(option: DeliveryOption) {
  return option.availableNow || option.availableLater;
}
