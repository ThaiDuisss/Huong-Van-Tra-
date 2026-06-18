import type { ReactNode } from "react";
import {
  Bell,
  CheckCircle2,
  Copy,
  Landmark,
  RefreshCw,
  ShieldCheck,
  X
} from "lucide-react";

import { formatCurrency } from "../../services/money";
import { type PaymentStatusValue } from "./PaymentStatus";

interface BankTransferDialogProps {
  amount: number;
  status: PaymentStatusValue;
  referenceCode: string;
  onClose(): void;
  onCancel(): void;
  onConfirmReceived(): void;
  onRefreshStatus(): void;
}

const bankInformation = {
  bankName: "MB Bank",
  accountName: "HƯƠNG VÂN TRÀ",
  accountNumber: "0345102087",
  branch: "CN Ba Đình"
};

const qrCells = Array.from({ length: 169 }, (_, index) => {
  const darkCellIndexes = new Set([
    0, 1, 2, 3, 4, 8, 9, 11, 12, 13, 14, 16, 20, 22, 23, 25, 26, 28, 31,
    32, 34, 36, 38, 39, 40, 42, 46, 48, 49, 51, 52, 55, 57, 58, 59, 61,
    63, 65, 67, 68, 70, 73, 75, 76, 79, 81, 82, 83, 85, 87, 90, 92, 94,
    96, 98, 99, 101, 102, 104, 107, 108, 110, 112, 114, 116, 118, 119,
    120, 122, 124, 127, 128, 130, 132, 134, 137, 138, 140, 142, 144, 146,
    147, 149, 151, 154, 156, 157, 159, 160, 162, 164, 165, 166, 167, 168
  ]);

  return {
    id: `qr-cell-${index}`,
    dark: darkCellIndexes.has(index)
  };
});

export function BankTransferDialog({
  amount,
  status,
  referenceCode,
  onClose,
  onCancel,
  onConfirmReceived,
  onRefreshStatus
}: BankTransferDialogProps) {
  const received = status === "RECEIVED";

  return (
    <section
      aria-labelledby="bank-transfer-dialog-title"
      className="w-[560px] max-w-[calc(100vw-32px)] rounded-xl bg-white p-0 shadow-[0_26px_80px_rgba(10,25,18,0.28)]"
      role="dialog"
      aria-modal="true"
    >
      <header className="flex h-12 items-center justify-between border-b border-[#edf0f4] px-5">
        <h2
          id="bank-transfer-dialog-title"
          className="text-[14px] font-extrabold text-[#172317]"
        >
          Thanh toán QR
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="grid size-8 place-items-center rounded-lg text-[#5f6b7a] hover:bg-[#f4f6f8]"
          aria-label="Đóng thanh toán chuyển khoản"
        >
          <X size={17} aria-hidden="true" />
        </button>
      </header>

      <div className="grid grid-cols-[230px_minmax(0,1fr)] gap-5 px-5 py-5">
        <div className="grid justify-items-center gap-3">
          <div className="flex items-center gap-2 text-[#159734]">
            <span className="grid size-8 place-items-center rounded-full bg-[#eaf8ed]">
              <ShieldCheck size={20} aria-hidden="true" />
            </span>
            <strong className="text-[22px] font-black">SePay</strong>
          </div>

          <div
            aria-label="QR thanh toán mẫu"
            className="grid aspect-square w-[174px] grid-cols-[repeat(13,minmax(0,1fr))] gap-[2px] rounded-lg border border-[#d8eadc] bg-white p-2"
            role="img"
          >
            {qrCells.map((cell, index) => {
              const row = Math.floor(index / 13);
              const col = index % 13;
              const finder =
                (row < 4 && col < 4) ||
                (row < 4 && col > 8) ||
                (row > 8 && col < 4);

              return (
                <span
                  key={cell.id}
                  className={`rounded-[1px] ${
                    finder || cell.dark
                      ? "bg-[#111827]"
                      : "bg-transparent"
                  }`}
                />
              );
            })}
          </div>

          <p className="max-w-[190px] text-center text-[11px] font-medium leading-snug text-[#5f6b7a]">
            Quét mã QR bằng ứng dụng ngân hàng hoặc ví điện tử hỗ trợ VietQR.
          </p>
        </div>

        <div className="grid content-start gap-3">
          <div
            className={`flex items-center justify-center gap-2 rounded-full px-3 py-2 text-[12px] font-extrabold ${
              received
                ? "bg-[#e8f8ec] text-[#159734]"
                : "bg-[#fff7e8] text-[#c77906]"
            }`}
          >
            <CheckCircle2 size={15} aria-hidden="true" />
            {received ? "Đã nhận đủ thanh toán" : "Đang chờ thanh toán"}
          </div>

          <InfoRow
            icon={<Landmark size={15} aria-hidden="true" />}
            label="Ngân hàng"
            value={bankInformation.bankName}
          />
          <InfoRow
            label="Chủ tài khoản"
            value={bankInformation.accountName}
          />
          <InfoRow
            label="Số tài khoản"
            value={bankInformation.accountNumber}
            copyable
          />
          <InfoRow
            label="Chi nhánh"
            value={bankInformation.branch}
          />
          <InfoRow label="Nội dung" value={referenceCode} copyable />

          <div className="rounded-lg bg-[#f7fff9] px-4 py-3">
            <p className="text-[12px] font-bold text-[#5f6b7a]">
              Số tiền thanh toán
            </p>
            <strong className="mt-1 block text-[26px] font-black text-[#159734]">
              {formatCurrency(amount)}
            </strong>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1">
          <button
            type="button"
            onClick={onRefreshStatus}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#d8eadc] text-[12px] font-bold text-[#159734] hover:bg-[#f4faf5]"
          >
            <RefreshCw size={15} aria-hidden="true" />
            Làm mới trạng thái
          </button>
          <button
            type="button"
            onClick={onConfirmReceived}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#d8eadc] text-[12px] font-bold text-[#159734] hover:bg-[#f4faf5]"
          >
            <Bell size={15} aria-hidden="true" />
            Xác nhận tiền về
          </button>
          <button
            type="button"
            onClick={() => {
              onCancel();
              onClose();
            }}
            className="h-10 rounded-lg border border-[#edf0f4] text-[12px] font-bold text-[#5f6b7a] hover:bg-[#f4f6f8]"
          >
            Hủy thanh toán
          </button>
          <button
            type="button"
            onClick={onConfirmReceived}
            className="h-10 rounded-lg bg-[#159734] text-[12px] font-extrabold text-white shadow-[0_8px_18px_rgba(21,151,52,0.22)]"
          >
            Đã nhận đủ tiền
          </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function InfoRow({
  icon,
  label,
  value,
  copyable = false
}: {
  icon?: ReactNode;
  label: string;
  value: string;
  copyable?: boolean;
}) {
  return (
    <div className="grid grid-cols-[22px_84px_minmax(0,1fr)_22px] items-center gap-2 rounded-lg border border-[#edf0f4] px-3 py-2 text-[12px]">
      <span className="grid size-5 place-items-center text-[#159734]">
        {icon}
      </span>
      <span className="font-bold text-[#5f6b7a]">{label}</span>
      <strong className="min-w-0 truncate text-right text-[#172317]">
        {value}
      </strong>
      <span className="grid size-5 place-items-center text-[#5f6b7a]">
        {copyable ? <Copy size={13} aria-hidden="true" /> : null}
      </span>
    </div>
  );
}
