import { AlertTriangle, CheckCircle2, Clock3, TimerOff, XCircle } from "lucide-react";

export type PaymentStatusValue =
  | "WAITING"
  | "RECEIVED"
  | "FAILED"
  | "EXPIRED"
  | "CANCELLED";

interface PaymentStatusProps {
  value: PaymentStatusValue;
}

const statusCopy: Record<
  PaymentStatusValue,
  {
    label: string;
    description: string;
    className: string;
    icon: typeof Clock3;
  }
> = {
  WAITING: {
    label: "Đang chờ thanh toán",
    description: "Hệ thống đang chờ xác nhận giao dịch.",
    className: "bg-amber-100 text-amber-700",
    icon: Clock3
  },
  RECEIVED: {
    label: "Đã nhận thanh toán",
    description: "Giao dịch đã được xác nhận.",
    className: "bg-hvt-green-50 text-hvt-green-900",
    icon: CheckCircle2
  },
  FAILED: {
    label: "Thanh toán thất bại",
    description: "Giao dịch không hoàn tất. Vui lòng kiểm tra lại đơn.",
    className: "bg-red-50 text-red-700",
    icon: AlertTriangle
  },
  EXPIRED: {
    label: "QR đã hết hạn",
    description: "Mã chuyển khoản không còn hiệu lực.",
    className: "bg-stone-100 text-stone-700",
    icon: TimerOff
  },
  CANCELLED: {
    label: "Đã hủy thanh toán",
    description: "Giỏ hàng hiện tại vẫn được giữ nguyên.",
    className: "bg-red-50 text-red-700",
    icon: XCircle
  }
};

export function PaymentStatus({ value }: PaymentStatusProps) {
  const status = statusCopy[value];
  const Icon = status.icon;

  return (
    <div className="grid gap-1">
      <span
        className={`inline-flex w-fit items-center gap-2 rounded-full px-3 py-1 text-xs font-bold ${status.className}`}
      >
        <Icon size={15} aria-hidden="true" />
        {status.label}
      </span>
      <p className="text-xs text-pos-muted">{status.description}</p>
    </div>
  );
}
