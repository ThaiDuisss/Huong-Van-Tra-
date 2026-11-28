import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { createPortal } from "react-dom";
import { toast, useToasts } from "./Toast";

const ICONS = {
    success: CheckCircle2,
    error: AlertCircle,
    warning: AlertTriangle,
    info: Info
};

const STYLES = {
    success: "bg-[#159734] border-emerald-600",
    error: "bg-red-600 border-red-700",
    warning: "bg-amber-600 border-amber-700",
    info: "bg-slate-900 border-slate-800"
};

export function ToastContainer() {
    const currentToasts = useToasts();
    if (currentToasts.length === 0) return null;

    return createPortal(
        <div className="pointer-events-none fixed right-6 top-6 z-[9999] flex w-full max-w-[min(28rem,calc(100vw-3rem))] flex-col gap-2.5">
            {currentToasts.map((item) => {
                const Icon = ICONS[item.type];
                return (
                    <div
                        key={item.id}
                        className={`pointer-events-auto flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-sm font-semibold text-white shadow-2xl transition-all ${STYLES[item.type]}`}
                    >
                        <div className="flex items-center gap-2.5">
                            <Icon size={18} className="shrink-0" aria-hidden="true" />
                            <div role={item.type === "error" ? "alert" : "status"} aria-atomic="true">
                                {item.title && <p className="font-bold">{item.title}</p>}
                                <p className={item.title ? "font-normal" : undefined}>{item.message}</p>
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={() => toast.dismiss(item.id)}
                            className="shrink-0 rounded-md p-1 opacity-80 hover:bg-white/10 hover:opacity-100 transition"
                            aria-label="Đóng thông báo"
                        >
                            <X size={15} />
                        </button>
                    </div>
                );
            })}
        </div>,
        document.body
    );
}
