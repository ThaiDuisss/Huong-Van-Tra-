import type { ReactNode } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Landmark,
  RefreshCw,
  ShieldCheck,
  TimerOff,
  X
} from "lucide-react";

import { formatCurrency } from "../../services/money";
import type { PosCheckoutPaymentResult } from "../../services/posCheckout.service";
import { type PaymentStatusValue } from "./PaymentStatus";

interface BankTransferDialogProps {
  amount: number;
  referenceCode?: string;
  status: PaymentStatusValue;
  payment: PosCheckoutPaymentResult | null;
  eventMessage?: string;
  loading?: boolean;
  error?: string;
  onClose(): void;
  onCancel(): void;
  /** Hỏi máy chủ tiền đã về chưa; POS không tự đánh dấu đã nhận tiền. */
  onRefreshStatus(): void;
}

const bankInformation = {
  bankName: "MB Bank",
  bankCode: "MBBank",
  accountName: "HƯƠNG VÂN TRÀ",
  accountNumber: "0345102087",
  branch: "CN Ba Đình"
};

function formatExpiry(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

function createSepayQrUrl(amount: number, referenceCode: string) {
  const params = new URLSearchParams({
    acc: bankInformation.accountNumber,
    bank: bankInformation.bankCode,
    amount: String(Math.round(amount)),
    des: referenceCode
  });

  return `https://qr.sepay.vn/img?${params.toString()}`;
}

export function BankTransferDialog({
  amount,
  referenceCode: fallbackReferenceCode,
  status,
  payment,
  eventMessage = "",
  loading = false,
  error = "",
  onClose,
  onCancel,
  onRefreshStatus
}: BankTransferDialogProps) {
  const received = status === "RECEIVED";
  const failed = status === "FAILED";
  const expired = status === "EXPIRED";
  const cancelled = status === "CANCELLED";
  const terminal = received || failed || expired || cancelled;
  const referenceCode =
    payment?.transferContent ??
    payment?.paymentCode ??
    fallbackReferenceCode ??
    "HVTRA-POS";
  const qrImageUrl =
    payment?.qrUrl ??
    createSepayQrUrl(payment?.amountRequired ?? amount, referenceCode);
  const bankName = payment?.bankCode ?? bankInformation.bankName;
  const accountName = payment?.bankAccountName ?? bankInformation.accountName;
  const accountNumber =
    payment?.bankAccountNumber ?? bankInformation.accountNumber;
  const payableAmount = payment?.amountRequired ?? amount;
  const statusClassName = received
    ? "bg-[#e8f8ec] text-[#159734]"
    : failed || cancelled
      ? "bg-red-50 text-red-700"
      : expired
        ? "bg-stone-100 text-stone-700"
        : "bg-[#fff7e8] text-[#c77906]";
  const ResultIcon = received
    ? CheckCircle2
    : expired
      ? TimerOff
      : AlertTriangle;
  const resultCopy = received
    ? {
        title: "Thanh toán thành công",
        message:
          eventMessage ||
          "Giao dịch chuyển khoản đã được xác nhận. Hệ thống sẽ quay lại màn POS sau giây lát.",
        className: "border-[#bde9c6] bg-[#f3fff6] text-[#159734]",
        iconClassName: "bg-[#e8f8ec] text-[#159734]"
      }
    : expired
      ? {
          title: "QR đã hết hạn",
          message:
            eventMessage ||
            "Mã QR không còn hiệu lực. Vui lòng tạo lại thanh toán nếu khách vẫn muốn chuyển khoản.",
          className: "border-stone-200 bg-stone-50 text-stone-700",
          iconClassName: "bg-stone-200 text-stone-700"
        }
      : {
          title: cancelled ? "Thanh toán đã hủy" : "Thanh toán thất bại",
          message:
            eventMessage ||
            "Giao dịch chưa hoàn tất. Vui lòng kiểm tra lại trạng thái thanh toán.",
          className: "border-red-200 bg-red-50 text-red-700",
          iconClassName: "bg-red-100 text-red-700"
        };

  return (
    <section
      aria-labelledby="bank-transfer-dialog-title"
      className="w-[680px] max-w-[calc(100vw-32px)] rounded-xl bg-white p-0 shadow-[0_26px_80px_rgba(10,25,18,0.28)]"
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

      <div className="grid grid-cols-[300px_minmax(0,1fr)] gap-5 px-5 py-5 max-[700px]:grid-cols-1">
        <div className="grid justify-items-center gap-3">
          <div className="flex items-center gap-2 text-[#159734]">
            <span className="grid size-8 place-items-center rounded-full bg-[#eaf8ed]">
              <ShieldCheck size={20} aria-hidden="true" />
            </span>
            <strong className="text-[22px] font-black">SePay</strong>
          </div>

          {terminal ? (
            <div
              className={`grid aspect-square w-[260px] max-w-full place-items-center rounded-lg border p-5 text-center ${resultCopy.className}`}
            >
              <div className="grid gap-3 justify-items-center">
                <span
                  className={`grid size-16 place-items-center rounded-full ${resultCopy.iconClassName}`}
                >
                  <ResultIcon size={34} strokeWidth={2.2} aria-hidden="true" />
                </span>
                <strong className="text-[20px] font-black">
                  {resultCopy.title}
                </strong>
                <span className="text-[12px] font-semibold leading-snug">
                  {resultCopy.message}
                </span>
              </div>
            </div>
          ) : qrImageUrl ? (
            <img
              className="aspect-square w-[260px] max-w-full rounded-lg border border-[#d8eadc] bg-white object-contain p-2"
              src={qrImageUrl}
              alt={`QR thanh toán SePay ${referenceCode}`}
            />
          ) : (
            <div className="grid aspect-square w-[260px] max-w-full place-items-center rounded-lg border border-dashed border-[#d8eadc] bg-[#f7fff9] p-4 text-center text-[12px] font-bold text-[#5f6b7a]">
              {loading ? "Đang tạo mã QR..." : "Chưa có mã QR"}
            </div>
          )}

          <p className="max-w-[260px] text-center text-[11px] font-medium leading-snug text-[#5f6b7a]">
            Quét mã QR bằng ứng dụng ngân hàng hoặc ví điện tử hỗ trợ VietQR.
          </p>
        </div>

        <div className="grid content-start gap-3">
          <div
            className={`flex items-center justify-center gap-2 rounded-full px-3 py-2 text-[12px] font-extrabold ${statusClassName}`}
          >
            <CheckCircle2 size={15} aria-hidden="true" />
            {received
              ? "Thanh toán thành công"
              : failed
                ? "Thanh toán thất bại"
                : expired
                  ? "QR đã hết hạn"
                  : cancelled
                    ? "Đã hủy thanh toán"
                    : loading
                      ? "Đang tạo thanh toán"
                      : "Đang chờ thanh toán"}
          </div>

          {error ? (
            <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-[12px] font-bold text-red-700">
              {error}
            </p>
          ) : null}

          <InfoRow
            icon={<Landmark size={15} aria-hidden="true" />}
            label="Ngân hàng"
            value={bankName}
          />
          <InfoRow
            label="Chủ tài khoản"
            value={accountName}
          />
          <InfoRow
            label="Số tài khoản"
            value={accountNumber}
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
              {formatCurrency(payableAmount)}
            </strong>
          </div>

          {payment?.expiresAt && !terminal ? (
            <p className="text-center text-[11px] font-semibold text-[#5f6b7a]">
              Mã QR có hiệu lực đến {formatExpiry(payment.expiresAt)}. Quá hạn, hệ thống tự đóng
              phiên và cho chọn lại phương thức.
            </p>
          ) : null}

          <div className="grid grid-cols-2 gap-2 pt-1">
          <button
            type="button"
            onClick={() => {
              onCancel();
              onClose();
            }}
            disabled={loading || received}
            className="h-10 rounded-lg border border-[#edf0f4] text-[12px] font-bold text-[#5f6b7a] hover:bg-[#f4f6f8] disabled:opacity-50"
          >
            Hủy thanh toán
          </button>
          <button
            type="button"
            onClick={onRefreshStatus}
            disabled={loading || terminal}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-[#159734] text-[12px] font-extrabold text-white shadow-[0_8px_18px_rgba(21,151,52,0.22)] disabled:opacity-60"
          >
            <RefreshCw size={15} aria-hidden="true" />
            Kiểm tra tiền về
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
