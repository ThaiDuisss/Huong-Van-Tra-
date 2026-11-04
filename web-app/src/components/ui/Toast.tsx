import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { useEffect, useState } from "react";

export type ToastType = "success" | "error" | "warning" | "info";

export interface ToastItem {
    id: string;
    message: string;
    type: ToastType;
}

type ToastListener = (toasts: ToastItem[]) => void;

let toasts: ToastItem[] = [];
const listeners = new Set<ToastListener>();

function notify() {
    listeners.forEach((listener) => listener([...toasts]));
}

export const toast = {
    show(message: string, type: ToastType = "info", duration = 3500) {
        const id = Math.random().toString(36).substring(2, 9);
        const item: ToastItem = { id, message, type };
        toasts = [...toasts, item];
        notify();

        if (duration > 0) {
            window.setTimeout(() => {
                toast.dismiss(id);
            }, duration);
        }
        return id;
    },
    success(message: string, duration = 3500) {
        return toast.show(message, "success", duration);
    },
    error(message: string, duration = 4000) {
        return toast.show(message, "error", duration);
    },
    warning(message: string, duration = 3500) {
        return toast.show(message, "warning", duration);
    },
    info(message: string, duration = 3500) {
        return toast.show(message, "info", duration);
    },
    dismiss(id: string) {
        toasts = toasts.filter((t) => t.id !== id);
        notify();
    }
};

export function useToasts() {
    const [currentToasts, setCurrentToasts] = useState<ToastItem[]>(toasts);

    useEffect(() => {
        listeners.add(setCurrentToasts);
        return () => {
            listeners.delete(setCurrentToasts);
        };
    }, []);

    return currentToasts;
}

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

    return (
        <div className="fixed right-6 top-6 z-[9999] flex flex-col gap-2.5 pointer-events-none max-w-md w-full">
            {currentToasts.map((t) => {
                const Icon = ICONS[t.type];
                const style = STYLES[t.type];

                return (
                    <div
                        key={t.id}
                        className={`pointer-events-auto flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-sm font-semibold text-white shadow-2xl transition-all ${style}`}
                    >
                        <div className="flex items-center gap-2.5">
                            <Icon size={18} className="shrink-0" />
                            <span>{t.message}</span>
                        </div>
                        <button
                            type="button"
                            onClick={() => toast.dismiss(t.id)}
                            className="shrink-0 rounded-md p-1 opacity-80 hover:opacity-100 hover:bg-white/10 transition"
                            aria-label="Đóng"
                        >
                            <X size={15} />
                        </button>
                    </div>
                );
            })}
        </div>
    );
}

