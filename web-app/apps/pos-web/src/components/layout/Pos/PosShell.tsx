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
    <div className="grid h-dvh min-h-0 grid-cols-[176px_minmax(0,1fr)_clamp(340px,29vw,380px)] overflow-hidden bg-[#f7f8fb] xl:grid-cols-[184px_minmax(0,1fr)_380px] 2xl:grid-cols-[192px_minmax(0,1fr)_396px]">
      <PosSidebar />

      <main className="min-h-0 min-w-0 overflow-hidden px-4 py-5 md:px-5">
        {children}
      </main>

      <aside
        className="min-h-0 min-w-0 overflow-hidden bg-[#f7f8fb] py-5 pr-4 md:pr-5"
        aria-label="Khu vực thanh toán"
      >
        {checkout}
      </aside>
    </div>
  );
}
