import { Bell, Copy, RefreshCw } from "lucide-react";

import { formatCurrency } from "../../services/money";
import { PaymentAmountSummary } from "./PaymentAmountSummary";
import { PaymentDialogShell } from "./PaymentDialogShell";
import {
  PaymentStatus,
  type PaymentStatusValue
} from "./PaymentStatus";

interface BankTransferDialogProps {
  amount: number;
  status: PaymentStatusValue;
  referenceCode: string;
  onCancel(): void;
  onConfirmReceived(): void;
  onRefreshStatus(): void;
}

const bankInformation = {
  bankName: "MB Bank",
  accountName: "HƯƠNG VÂN TRÀ",
  accountNumber: "0345102087"
};

const qrCells = Array.from({ length: 81 }, (_, index) => {
  const darkCellIndexes = new Set([
    0, 1, 2, 6, 7, 8, 10, 12, 15, 17, 18, 20, 23, 25, 27, 28, 29, 31,
    35, 36, 38, 40, 43, 45, 47, 48, 50, 54, 55, 57, 58, 60, 63, 65, 67,
    70, 72, 73, 75, 78, 79, 80
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
  onCancel,
  onConfirmReceived,
  onRefreshStatus
}: BankTransferDialogProps) {
  return (
    <PaymentDialogShell
      title="Thanh toán chuyển khoản"
      description="Thông tin chuyển khoản ngân hàng và trạng thái thanh toán"
      actions={
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={onRefreshStatus}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-xs font-semibold text-hvt-green-900"
          >
            <RefreshCw size={15} aria-hidden="true" />
            Làm mới trạng thái
          </button>
          <button
            type="button"
            onClick={onConfirmReceived}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-xs font-semibold text-hvt-green-900"
          >
            <Bell size={15} aria-hidden="true" />
            Xác nhận tiền về
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="h-10 rounded-xl border border-pos-border text-xs font-semibold text-pos-muted"
          >
            Hủy chuyển khoản
          </button>
          <button
            type="button"
            onClick={onConfirmReceived}
            className="h-10 rounded-xl bg-hvt-green-900 text-xs font-bold text-white shadow-sm"
          >
            Đã nhận đủ tiền
          </button>
        </div>
      }
    >
      <div className="grid grid-cols-[132px_minmax(0,1fr)] gap-3">
        <div className="grid gap-2">
          <div
            aria-label="QR thanh toán mẫu"
            className="grid aspect-square grid-cols-9 gap-0.5 rounded-xl border border-pos-border bg-white p-2"
            role="img"
          >
            {qrCells.map((cell) => (
              <span
                key={cell.id}
                className={`rounded-[2px] ${
                  cell.dark ? "bg-pos-ink" : "bg-pos-surface"
                }`}
              />
            ))}
          </div>
          <p className="text-center text-[11px] leading-snug text-pos-muted">
            Quét mã bằng Banking App hoặc ví điện tử hỗ trợ VietQR
          </p>
        </div>

        <div className="grid gap-3">
          <PaymentStatus value={status} />

          <div className="grid gap-2 border-t border-pos-border pt-3 text-xs">
            <p className="font-bold text-pos-ink">
              {bankInformation.bankName}
            </p>
            <div>
              <p className="text-pos-muted">Chủ tài khoản</p>
              <p className="font-bold">{bankInformation.accountName}</p>
            </div>
            <div>
              <p className="text-pos-muted">Số tài khoản</p>
              <p className="font-bold">{bankInformation.accountNumber}</p>
            </div>
          </div>

          <PaymentAmountSummary
            amount={amount}
            formatCurrency={formatCurrency}
            referenceCode={referenceCode}
          />

          <button
            type="button"
            className="inline-flex w-fit items-center gap-2 rounded-lg border border-pos-border px-3 py-2 text-xs font-semibold text-hvt-green-900"
          >
            <Copy size={14} aria-hidden="true" />
            Sao chép
          </button>
        </div>
      </div>

      <div className="grid gap-1">
        <div className="h-2 overflow-hidden rounded-full bg-pos-border">
          <div
            className={`h-full rounded-full ${
              status === "RECEIVED"
                ? "w-full bg-hvt-green-800"
                : "w-0 bg-hvt-green-800"
            }`}
          />
        </div>
        <div className="flex items-center justify-between text-xs font-semibold text-hvt-green-900">
          <span>
            Đã nhận:{" "}
            {status === "RECEIVED"
              ? formatCurrency(amount)
              : formatCurrency(0)}
            {" / "}
            {formatCurrency(amount)}
          </span>
          <span className="rounded-lg border border-hvt-green-700 px-2 py-1">
            {status === "RECEIVED" ? "100%" : "0%"}
          </span>
        </div>
      </div>
    </PaymentDialogShell>
  );
}
