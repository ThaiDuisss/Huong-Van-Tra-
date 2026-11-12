import {
  AlertTriangle,
  Flame,
  Gift,
  Search,
  Tag,
  X
} from "lucide-react";
import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState
} from "react";
import type { ReactNode } from "react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { StatusBadge } from "../../../../components/ui/StatusBadge";
import { formatCurrency } from "../../services/money";
import type { SaleDiscountOption } from "../../types/sale.types";

interface DiscountSearchDialogProps {
  open: boolean;
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  subtotal: number;
  onSelect(discountId: string): void;
  onRemove(): void;
  onClose(): void;
}

function getDiscountValueLabel(discount: SaleDiscountOption) {
  if (discount.type === "PERCENT") {
    return `-${discount.value}%`;
  }

  if (discount.type === "AMOUNT") {
    return `-${formatCurrency(discount.value)}`;
  }

  if (discount.type === "GIFT") {
    return `Tặng ${discount.giftQuantity ?? 1}`;
  }

  return "Không giảm";
}

function isDiscountEligible(
  discount: SaleDiscountOption,
  subtotal: number
) {
  if (discount.type === "NONE") {
    return true;
  }

  if (discount.status === "EXPIRED") {
    return false;
  }

  if (discount.status === "INELIGIBLE") {
    return false;
  }

  return subtotal >= (discount.minSubtotal ?? 0);
}

function getStatusLabel(
  discount: SaleDiscountOption,
  eligible: boolean
) {
  if (discount.status === "EXPIRED") {
    return "Đã hết hạn";
  }

  if (!eligible) {
    return "Không đủ điều kiện";
  }

  if (discount.status === "RECOMMENDED") {
    return "Đề xuất";
  }

  return "Áp dụng";
}

export function DiscountSearchDialog({
  open,
  discounts,
  selectedDiscountId,
  subtotal,
  onSelect,
  onRemove,
  onClose
}: DiscountSearchDialogProps) {
  const [query, setQuery] = useState("");
  const searchInputRef = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    if (open) {
      window.requestAnimationFrame(() => {
        searchInputRef.current?.focus();
      });
    }
  }, [open]);

  const filteredDiscounts = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const visibleDiscounts = discounts.filter(
      (discount) => discount.type !== "NONE"
    );

    if (!normalizedQuery) {
      return visibleDiscounts;
    }

    return visibleDiscounts.filter(
      (discount) =>
        discount.code.toLowerCase().includes(normalizedQuery) ||
        discount.label.toLowerCase().includes(normalizedQuery) ||
        discount.description.toLowerCase().includes(normalizedQuery)
    );
  }, [discounts, query]);

  const recommended = filteredDiscounts.filter(
    (discount) => discount.status === "RECOMMENDED"
  );
  const available = filteredDiscounts.filter(
    (discount) =>
      discount.status === "AVAILABLE" &&
      isDiscountEligible(discount, subtotal)
  );
  const ineligible = filteredDiscounts.filter(
    (discount) =>
      discount.status === "INELIGIBLE" ||
      (discount.status === "AVAILABLE" &&
        !isDiscountEligible(discount, subtotal))
  );
  const expired = filteredDiscounts.filter(
    (discount) => discount.status === "EXPIRED"
  );

  function closeDialog() {
    setQuery("");
    onClose();
  }

  function selectDiscount(discountId: string) {
    setQuery("");
    onSelect(discountId);
    onClose();
  }

  function removeDiscount() {
    setQuery("");
    onRemove();
    onClose();
  }

  if (!open) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className="absolute left-0 right-0 top-[calc(100%+8px)] z-20 rounded-2xl border border-pos-border bg-white p-4 shadow-pos-panel"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.stopPropagation();
          closeDialog();
        }
      }}
    >
      <header className="mb-3 flex items-start justify-between gap-3">
        <div className="flex min-w-0 gap-3">
          <Gift
            className="mt-1 size-5 shrink-0 text-pos-gold"
            aria-hidden="true"
          />
          <div className="min-w-0">
            <h3
              id={titleId}
              className="text-sm font-bold text-gray-500"
            >
              Khuyến mãi
            </h3>
            <p
              id={descriptionId}
              className="mt-0.5 text-xs text-neutral-500"
            >
              Tìm theo mã, tên chương trình hoặc voucher
            </p>
          </div>
        </div>

        <IconButton aria-label="Đóng tìm khuyến mãi" onClick={closeDialog}>
          <X size={20} aria-hidden="true" />
        </IconButton>
      </header>

      <label className="mb-3 flex h-10 items-center gap-2 rounded-lg border border-pos-border px-3 focus-within:border-hvt-green-700">
        <Search
          size={18}
          className="shrink-0 text-neutral-500"
          aria-hidden="true"
        />
        <input
          ref={searchInputRef}
          className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400"
          value={query}
          onChange={(event) => setQuery(event.currentTarget.value)}
          placeholder="Tìm theo mã, tên chương trình hoặc voucher..."
          aria-label="Tìm khuyến mãi"
        />
      </label>

      {selectedDiscountId !== "none" ? (
        <Button className="mb-3 w-full" onClick={removeDiscount} size="sm" variant="dangerOutline">
          Bỏ khuyến mãi đang áp dụng
        </Button>
      ) : null}

      <DiscountGroup
        title="Đề xuất cho hóa đơn này"
        icon={<Flame size={15} aria-hidden="true" />}
        discounts={recommended}
        selectedDiscountId={selectedDiscountId}
        subtotal={subtotal}
        onSelect={selectDiscount}
      />
      <DiscountGroup
        title="Khuyến mãi đang chạy"
        icon={<Gift size={15} aria-hidden="true" />}
        discounts={available}
        selectedDiscountId={selectedDiscountId}
        subtotal={subtotal}
        onSelect={selectDiscount}
      />
      <DiscountGroup
        title="Không áp dụng được"
        icon={<AlertTriangle size={15} aria-hidden="true" />}
        discounts={ineligible}
        selectedDiscountId={selectedDiscountId}
        subtotal={subtotal}
        onSelect={selectDiscount}
      />
      <DiscountGroup
        title="Đã hết hạn"
        icon={<Tag size={15} aria-hidden="true" />}
        discounts={expired}
        selectedDiscountId={selectedDiscountId}
        subtotal={subtotal}
        onSelect={selectDiscount}
      />

      {filteredDiscounts.length === 0 ? (
        <div className="rounded-xl border border-dashed border-pos-border py-6 text-center text-sm text-neutral-500">
          Không tìm thấy khuyến mãi phù hợp.
        </div>
      ) : null}
    </div>
  );
}

interface DiscountGroupProps {
  title: string;
  icon: ReactNode;
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  subtotal: number;
  onSelect(discountId: string): void;
}

function DiscountGroup({
  title,
  icon,
  discounts,
  selectedDiscountId,
  subtotal,
  onSelect
}: DiscountGroupProps) {
  if (discounts.length === 0) {
    return null;
  }

  return (
    <section className="mb-3">
      <div className="mb-2 flex items-center justify-between">
        <h4 className="flex items-center gap-1 text-xs font-bold text-gray-500">
          {icon}
          {title}
        </h4>
        <Button className="-mr-2" size="xs" variant="link">
          Xem tất cả
        </Button>
      </div>

      <div className="grid gap-2">
        {discounts.map((discount) => {
          const eligible = isDiscountEligible(discount, subtotal);
          const selected = discount.id === selectedDiscountId;

          return (
            <button
              type="button"
              key={discount.id}
              disabled={!eligible}
              onClick={() => onSelect(discount.id)}
              aria-current={selected ? "true" : undefined}
              className={`grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-xl border px-3 py-2 text-left text-sm ${
                selected
                  ? "border-hvt-green-700 bg-hvt-green-50"
                  : "border-pos-border bg-white"
              } ${
                eligible
                  ? "hover:border-hvt-green-500"
                  : "cursor-not-allowed bg-orange-50 text-neutral-500"
              }`}
            >
              <span className="min-w-0">
                <strong className="block truncate text-gray-500">
                  {discount.label}
                </strong>
                <span className="block text-xs text-neutral-500">
                  {discount.description}
                </span>
                <span className="block text-[11px] text-neutral-400">
                  {discount.condition}
                </span>
              </span>

              <span className="grid justify-items-end gap-1">
                <StatusBadge tone={eligible ? "success" : "pending"}>
                  {getDiscountValueLabel(discount)}
                </StatusBadge>
                <span className="text-[11px] font-semibold text-hvt-green-800">
                  {getStatusLabel(discount, eligible)}
                </span>
                {discount.expiresIn ? (
                  <span className="text-[11px] text-neutral-400">
                    {discount.expiresIn}
                  </span>
                ) : null}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
