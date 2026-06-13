import type { ReactNode } from "react";

import { PosSidebar } from "./PosSidebar";

interface PosShellProps {
  children: ReactNode;
  checkout: ReactNode;
}

export function PosShell({
  children,
  checkout
}: PosShellProps) {
  return (
    <div className="grid h-dvh min-h-0 grid-cols-[176px_minmax(0,1fr)_clamp(380px,31vw,448px)] overflow-hidden bg-pos-background xl:grid-cols-[184px_minmax(0,1fr)_448px] 2xl:grid-cols-[220px_minmax(0,1fr)_480px]">
      <PosSidebar />

      <main className="min-h-0 min-w-0 overflow-hidden px-5 py-6 md:px-6 xl:px-8">
        {children}
      </main>

      <aside
        className="min-h-0 min-w-0 overflow-hidden bg-pos-background py-5 pr-5 md:py-6 md:pr-6 xl:pr-8"
        aria-label="Khu vực thanh toán"
      >
        {checkout}
      </aside>
    </div>
  );
}
