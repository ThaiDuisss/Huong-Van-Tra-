interface PaymentAmountSummaryProps {
  amount: number;
  formatCurrency(value: number): string;
  label?: string;
  referenceCode?: string;
}

export function PaymentAmountSummary({
  amount,
  formatCurrency,
  label = "Số tiền cần thanh toán",
  referenceCode
}: PaymentAmountSummaryProps) {
  return (
    <div className="grid gap-1">
      <p className="text-xs font-medium text-pos-muted">{label}</p>
      <p className="text-3xl font-bold text-hvt-green-900">
        {formatCurrency(amount)}
      </p>
      {referenceCode ? (
        <p className="text-xs text-pos-muted">
          Nội dung chuyển khoản:{" "}
          <span className="font-semibold text-pos-ink">
            {referenceCode}
          </span>
        </p>
      ) : null}
    </div>
  );
}
