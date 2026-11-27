import { useEffect, useState } from "react";

export type ToastType = "success" | "error" | "warning" | "info";

interface ToastContent {
    message: string;
    title?: string;
    key?: string;
}

export interface ToastItem extends ToastContent {
    id: string;
    type: ToastType;
}

type ToastListener = (toasts: ToastItem[]) => void;

let toasts: ToastItem[] = [];
const listeners = new Set<ToastListener>();

function notify() {
    listeners.forEach((listener) => listener([...toasts]));
}

export const toast = {
    show(content: string | ToastContent, type: ToastType = "info", duration = 3500) {
        const id = Math.random().toString(36).substring(2, 9);
        const item: ToastItem = { ...(typeof content === "string" ? { message: content } : content), id, type };
        toasts = [...(item.key ? toasts.filter((toast) => toast.key !== item.key) : toasts), item];
        notify();

        if (duration > 0) {
            window.setTimeout(() => {
                toast.dismiss(id);
            }, duration);
        }
        return id;
    },
    success(content: string | ToastContent, duration = 3500) {
        return toast.show(content, "success", duration);
    },
    error(content: string | ToastContent, duration = 4000) {
        return toast.show(content, "error", duration);
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


