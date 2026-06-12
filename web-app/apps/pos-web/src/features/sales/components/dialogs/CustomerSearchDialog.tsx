import {
  Search,
  User,
  UserPlus,
  X
} from "lucide-react";
import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState
} from "react";

import { formatCurrency } from "../../services/money";
import type { SaleCustomer } from "../../types/sale.types";

interface CustomerSearchDialogProps {
  open: boolean;
  customers: SaleCustomer[];
  selectedCustomerId: string;
  onSelect(customerId: string): void;
  onClose(): void;
}

function getInitials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function getTierClassName(tier: SaleCustomer["tier"]) {
  if (tier === "VIP Kim Cương") {
    return "bg-blue-100 text-blue-700";
  }

  if (tier === "VIP Vàng") {
    return "bg-amber-100 text-amber-700";
  }

  if (tier === "VIP Bạc") {
    return "bg-neutral-200 text-neutral-700";
  }

  return "bg-hvt-green-100 text-hvt-green-800";
}

function CustomerAvatar({ customer }: { customer: SaleCustomer }) {
  if (customer.avatarUrl) {
    return (
      <img
        className="size-9 rounded-full object-cover"
        src={customer.avatarUrl}
        alt=""
      />
    );
  }

  return (
    <div className="grid size-9 place-items-center rounded-full bg-hvt-green-100 text-sm font-bold text-hvt-green-900">
      {getInitials(customer.name)}
    </div>
  );
}

export function CustomerSearchDialog({
  open,
  customers,
  selectedCustomerId,
  onSelect,
  onClose
}: CustomerSearchDialogProps) {
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

  function closeDialog() {
    setQuery("");
    onClose();
  }

  function selectCustomer(customerId: string) {
    setQuery("");
    onSelect(customerId);
    onClose();
  }

  const filteredCustomers = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();

    if (!normalizedQuery) {
      return customers;
    }

    return customers.filter(
      (customer) =>
        customer.name.toLowerCase().includes(normalizedQuery) ||
        customer.phone.replace(/\s/g, "").includes(
          normalizedQuery.replace(/\s/g, "")
        )
    );
  }, [customers, query]);

  const recentCustomers = filteredCustomers.slice(0, 3);
  const frequentCustomers = filteredCustomers.slice(3, 5);

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
          <User
            className="mt-1 size-5 shrink-0 text-pos-ink"
            aria-hidden="true"
          />
          <div className="min-w-0">
            <h3
              id={titleId}
              className="text-sm font-bold text-pos-ink"
            >
              Tìm khách hàng
            </h3>
            <p
              id={descriptionId}
              className="mt-0.5 text-xs text-neutral-500"
            >
              Tìm theo tên, số điện thoại hoặc mã khách hàng
            </p>
          </div>
        </div>

        <button
          type="button"
          className="grid size-8 place-items-center rounded-lg text-pos-ink hover:bg-neutral-100"
          aria-label="Đóng tìm khách hàng"
          onClick={closeDialog}
        >
          <X size={20} aria-hidden="true" />
        </button>
      </header>

      <label className="mb-4 flex h-10 items-center gap-2 rounded-lg border border-pos-border px-3 focus-within:border-hvt-green-700">
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
          placeholder="Nhập tên, SĐT hoặc mã KH..."
          aria-label="Tìm khách hàng"
        />
      </label>

      <CustomerGroup
        title="Khách hàng gần đây"
        customers={recentCustomers}
        selectedCustomerId={selectedCustomerId}
        onSelect={selectCustomer}
      />

      <CustomerGroup
        title="Khách hàng mua nhiều nhất hôm nay"
        customers={frequentCustomers}
        selectedCustomerId={selectedCustomerId}
        onSelect={selectCustomer}
      />

      {filteredCustomers.length === 0 ? (
        <div className="rounded-xl border border-dashed border-pos-border py-6 text-center text-sm text-neutral-500">
          Không tìm thấy khách hàng phù hợp.
        </div>
      ) : null}

      <button
        type="button"
        className="mt-3 flex h-9 w-full items-center justify-center gap-2 rounded-full border border-pos-border text-sm font-semibold text-hvt-green-800 hover:bg-hvt-green-50"
      >
        <UserPlus size={17} aria-hidden="true" />
        Tạo khách hàng mới
      </button>
    </div>
  );
}

interface CustomerGroupProps {
  title: string;
  customers: SaleCustomer[];
  selectedCustomerId: string;
  onSelect(customerId: string): void;
}

function CustomerGroup({
  title,
  customers,
  selectedCustomerId,
  onSelect
}: CustomerGroupProps) {
  if (customers.length === 0) {
    return null;
  }

  return (
    <section className="mb-3">
      <div className="mb-2 flex items-center justify-between">
        <h4 className="text-xs font-bold text-pos-ink">{title}</h4>
        <button
          type="button"
          className="text-xs font-semibold text-hvt-green-800"
        >
          Xem tất cả
        </button>
      </div>

      <div className="overflow-hidden rounded-xl border border-pos-border">
        {customers.map((customer) => {
          const selected = customer.id === selectedCustomerId;

          return (
            <button
              key={customer.id}
              type="button"
              onClick={() => {
                onSelect(customer.id);
              }}
              className={`grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b border-pos-border px-3 py-2 text-left last:border-b-0 ${
                selected
                  ? "bg-hvt-green-50 ring-1 ring-inset ring-hvt-green-700"
                  : "bg-white hover:bg-neutral-50"
              }`}
              aria-current={selected ? "true" : undefined}
            >
              <span className="flex min-w-0 items-center gap-3">
                <CustomerAvatar customer={customer} />
                <span className="min-w-0">
                  <strong className="block truncate text-sm text-pos-ink">
                    {customer.name}
                  </strong>
                  <span className="block text-xs text-neutral-500">
                    {customer.phone}
                  </span>
                </span>
              </span>

              <span className="grid justify-items-end gap-0.5 text-right">
                <span
                  className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${getTierClassName(customer.tier)}`}
                >
                  {customer.tier}
                </span>
                <span className="text-xs text-neutral-500">
                  {customer.orderCount} đơn
                </span>
                <span className="text-xs font-semibold text-pos-ink">
                  {formatCurrency(customer.totalSpent)}
                </span>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
