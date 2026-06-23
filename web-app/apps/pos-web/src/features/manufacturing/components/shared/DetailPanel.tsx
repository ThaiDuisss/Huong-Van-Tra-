import type { ReactNode } from "react";
import { X } from "lucide-react";

interface DetailPanelProps {
    title: string;
    children: ReactNode;
    onClose(): void;
}

export function DetailPanel({
    title,
    children,
    onClose
}: DetailPanelProps) {
    return (
        <aside className="flex min-h-0 flex-col rounded-2xl bg-white shadow-pos-card">
            <header className="flex items-center justify-between border-b border-pos-border p-4">
                <h2 className="text-xl font-bold text-gray-500">
                    {title}
                </h2>
                <button
                    type="button"
                    onClick={onClose}
                    className="grid size-8 place-items-center rounded-lg text-gray-500 hover:bg-neutral-100 hover:text-gray-500"
                    aria-label="Đóng chi tiết BOM"
                >
                    <X size={20} aria-hidden="true" />
                </button>
            </header>
            <div className="min-h-0 flex-1 overflow-y-auto p-4">
                {children}
            </div>
        </aside>
    );
}
