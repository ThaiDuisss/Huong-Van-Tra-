import type { ReactNode } from "react";

import { PosSidebar } from "./PosSidebar";

interface PosShellProps {
  children: ReactNode;
  checkout?: ReactNode;
}

export function PosShell({
  children,
  checkout
}: PosShellProps) {
  const gridCols = checkout
    ? "grid-cols-[176px_minmax(0,1fr)_clamp(340px,29vw,380px)] xl:grid-cols-[184px_minmax(0,1fr)_380px] 2xl:grid-cols-[192px_minmax(0,1fr)_396px]"
    : "grid-cols-[176px_minmax(0,1fr)] xl:grid-cols-[184px_minmax(0,1fr)] 2xl:grid-cols-[192px_minmax(0,1fr)]";

  const mainOverflow = checkout ? "overflow-hidden" : "overflow-y-auto";
  const mainSpacing = checkout
    ? "px-4 py-5 md:px-5"
    : "px-4 pt-3 pb-4 md:px-5 md:pt-4 md:pb-5";

  return (
    <div className={`grid h-dvh min-h-0 ${gridCols} overflow-hidden bg-[#f7f8fb]`}>
      <PosSidebar />

      <main className={`min-h-0 min-w-0 ${mainOverflow} ${mainSpacing}`}>
        {children}
      </main>

      {checkout && (
        <aside
          className="min-h-0 min-w-0 overflow-hidden bg-[#f7f8fb] py-5 pr-4 md:pr-5"
          aria-label="Khu vực thanh toán"
        >
          {checkout}
        </aside>
      )}
    </div>
  );
}
