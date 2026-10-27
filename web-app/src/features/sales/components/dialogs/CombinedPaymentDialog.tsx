import {
  Building2,
  CreditCard,
  Landmark,
  PlusCircle,
  Trash2,
  WalletCards,
  X
} from "lucide-react";

import { formatCurrency } from "../../services/money";

interface CombinedPaymentDialogProps {
  subtotal: number;
  discountAmount: number;
  total: number;
  onClose(): void;
  onConfirm(): void;
}

const paymentRows = [
  {
    label: "Tiền mặt",
    value: 0,
    status: "Chưa thanh toán",
    confirmed: true,
    icon: WalletCards
  },
  {
    label: "Chuyển khoản",
    value: 0,
    status: "Chưa xác nhận",
    confirmed: false,
    icon: Landmark
  },
  {
    label: "Ví điện tử",
    value: 0,
    status: "Chưa xác nhận",
    confirmed: false,
    icon: Building2
  },
  {
    label: "Thẻ",
    value: 0,
    status: "Chưa xác nhận",
    confirmed: false,
    icon: CreditCard
  }
] as const;

export function CombinedPaymentDialog({
  subtotal,
  discountAmount,
  total,
  onClose,
  onConfirm
}: CombinedPaymentDialogProps) {
  const allocated = paymentRows.reduce((sum, row) => sum + row.value, 0);
  const remaining = Math.max(0, total - allocated);

  return (
    <section
      aria-labelledby="combined-payment-dialog-title"
      className="w-[590px] max-w-[calc(100vw-32px)] rounded-xl bg-white p-0 shadow-[0_26px_80px_rgba(10,25,18,0.28)]"
      role="dialog"
      aria-modal="true"
    >
      <header className="flex items-start justify-between border-b border-[#edf0f4] px-6 py-5">
        <div>
          <h2
            id="combined-payment-dialog-title"
            className="text-[22px] font-black text-[#172317]"
          >
            Thanh toán tổng hợp
          </h2>
          <p className="mt-2 text-[13px] font-bold text-[#172317]">
            Hóa đơn:{" "}
            <span className="text-[#159734]">HD000015</span>
          </p>
          <p className="mt-1 text-[13px] text-[#53678f]">
            Chia số tiền hóa đơn cho nhiều phương thức thanh toán.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="grid size-9 place-items-center rounded-lg text-[#1f2a44] hover:bg-[#f4f6f8]"
          aria-label="Đóng thanh toán tổng hợp"
        >
          <X size={24} aria-hidden="true" />
        </button>
      </header>

      <div className="grid gap-4 px-6 py-4">
        <div className="rounded-lg border border-[#dfe6ee] bg-white p-4 shadow-[0_10px_24px_rgba(31,53,43,0.06)]">
          <SummaryRow label="Tổng tiền hàng:" value={formatCurrency(subtotal)} />
          <SummaryRow label="Giảm giá:" value={formatCurrency(discountAmount)} />
          <SummaryRow
            strong
            label="Thành tiền cần thanh toán:"
            value={formatCurrency(total)}
          />
          <div className="my-3 border-t border-dashed border-[#dfe6ee]" />
          <SummaryRow label="Đã phân bổ:" value={formatCurrency(allocated)} />
          <SummaryRow
            danger
            label="Còn phải thanh toán:"
            value={formatCurrency(remaining)}
          />
        </div>

        <div>
          <h3 className="mb-3 text-[14px] font-black text-[#172317]">
            Phân bổ phương thức thanh toán
          </h3>
          <div className="overflow-hidden rounded-lg border border-[#dfe6ee]">
            {paymentRows.map((row) => {
              const Icon = row.icon;

              return (
                <div
                  className="grid grid-cols-[150px_minmax(0,1fr)_108px_32px] items-center gap-12 border-b border-[#edf0f4] px-5 py-3 last:border-b-0"
                  key={row.label}
                >
                  <div className="flex items-center gap-3 text-[14px] font-bold text-[#1f2a44]">
                    <Icon
                      className="text-[#159734]"
                      size={21}
                      aria-hidden="true"
                    />
                    {row.label}
                  </div>
                  <label className="flex h-10 items-center rounded-lg border border-[#dfe6ee] bg-white px-3 focus-within:border-[#8fd6a5]">
                    <input
                      className="min-w-0 flex-1 bg-transparent text-right text-[14px] font-bold outline-none"
                      aria-label={`Số tiền ${row.label}`}
                      inputMode="numeric"
                      value={formatCurrency(row.value)}
                      readOnly
                    />
                  </label>
                  <span
                    className={`inline-flex min-h-[30px] items-center justify-center rounded-md px-3 py-2 text-[12px] font-extrabold ${
                      row.confirmed
                        ? "bg-[#eaf8ed] text-[#159734]"
                        : "bg-[#fff3e8] text-[#ff6b00]"
                    }`}
                  >
                    {row.status}
                  </span>
                  <button
                    type="button"
                    className="grid size-8 place-items-center rounded-lg text-red-500 hover:bg-red-50"
                    aria-label={`Xóa phương thức ${row.label}`}
                  >
                    <Trash2 size={17} aria-hidden="true" />
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        <button
          type="button"
          className="flex h-10 items-center justify-center gap-2 rounded-lg border border-dashed border-[#cbd8e4] text-[13px] font-extrabold text-[#159734] hover:bg-[#f4faf5]"
        >
          <PlusCircle size={18} aria-hidden="true" />
          Thêm phương thức thanh toán
        </button>

        <footer className="grid grid-cols-2 gap-6 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="h-12 rounded-lg border border-[#dfe6ee] text-[14px] font-extrabold text-[#1f2a44] hover:bg-[#f4f6f8]"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="h-12 rounded-lg bg-[#07883a] text-[14px] font-extrabold text-white shadow-[0_10px_22px_rgba(7,136,58,0.24)]"
          >
            Xác nhận thanh toán
          </button>
        </footer>
      </div>
    </section>
  );
}

function SummaryRow({
  label,
  value,
  strong = false,
  danger = false
}: {
  label: string;
  value: string;
  strong?: boolean;
  danger?: boolean;
}) {
  return (
    <div
      className={`flex items-center justify-between py-1 text-[14px] ${
        strong ? "font-black text-[#172317]" : "font-bold text-[#1f2a44]"
      } ${danger ? "text-[#ff6b00]" : ""}`}
    >
      <span>{label}</span>
      <strong className={danger ? "text-[18px]" : ""}>{value}</strong>
    </div>
  );
}
