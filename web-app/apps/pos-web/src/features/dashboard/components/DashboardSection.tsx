import type { ReactNode } from "react";

/**
 * Card section dùng chung cho dashboard: cùng token với card của khu Management
 * (rounded-xl, border #e8eef0, shadow-pos-card). `flush` cho bảng tràn sát mép card.
 */
export function DashboardSection({
  action,
  badge,
  children,
  className = "",
  description,
  flush = false,
  title,
}: {
  action?: ReactNode;
  badge?: ReactNode;
  children: ReactNode;
  className?: string;
  description?: string;
  flush?: boolean;
  title: string;
}) {
  return (
    <section
      className={`grid min-w-0 content-start gap-4 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card ${className}`}
    >
      <header className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
        <div className="grid min-w-0 gap-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-base font-black text-[#172317]">{title}</h2>
            {badge}
          </div>
          {description ? (
            <p className="text-xs font-semibold text-[#697684]">{description}</p>
          ) : null}
        </div>
        {action ? <div className="flex shrink-0 items-center gap-2">{action}</div> : null}
      </header>
      {flush ? <div className="-mx-4 -mb-4 min-w-0">{children}</div> : children}
    </section>
  );
}

export function DashboardSectionLink({
  children,
  onClick,
  to,
}: {
  children: ReactNode;
  onClick?: () => void;
  to?: string;
}) {
  const className = "text-sm font-black text-[#159734] hover:underline";

  if (to) {
    return (
      <a className={className} href={to}>
        {children}
      </a>
    );
  }

  return (
    <button className={className} onClick={onClick} type="button">
      {children}
    </button>
  );
}
