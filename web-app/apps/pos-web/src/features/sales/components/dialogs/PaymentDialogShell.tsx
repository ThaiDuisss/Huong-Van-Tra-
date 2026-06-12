import type { ReactNode } from "react";

interface PaymentDialogShellProps {
  title: string;
  description: string;
  children: ReactNode;
  actions?: ReactNode;
}

export function PaymentDialogShell({
  title,
  description,
  children,
  actions
}: PaymentDialogShellProps) {
  return (
    <section
      aria-labelledby="payment-dialog-title"
      aria-describedby="payment-dialog-description"
      className="grid min-h-0 gap-3 rounded-2xl border border-hvt-green-700 bg-white p-3 shadow-sm"
      role="dialog"
    >
      <div className="sr-only">
        <h2 id="payment-dialog-title">{title}</h2>
        <p id="payment-dialog-description">{description}</p>
      </div>

      {children}

      {actions ? <div className="grid gap-2">{actions}</div> : null}
    </section>
  );
}
