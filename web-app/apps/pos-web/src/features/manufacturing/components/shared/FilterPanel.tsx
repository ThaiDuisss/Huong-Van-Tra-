import type { ReactNode } from "react";
import { SlidersHorizontal } from "lucide-react";

interface FilterPanelProps {
    children: ReactNode;
}

export function FilterPanel({ children }: FilterPanelProps) {
    return (
        <aside className="grid content-start gap-5 rounded-2xl bg-white p-4 shadow-pos-card">
            <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-pos-ink">
                    Bộ lọc
                </h2>
                <span className="inline-flex items-center gap-2 text-xs font-bold text-hvt-green-800">
                    <SlidersHorizontal
                        size={15}
                        aria-hidden="true"
                    />
                    Thu gọn
                </span>
            </div>
            {children}
        </aside>
    );
}
