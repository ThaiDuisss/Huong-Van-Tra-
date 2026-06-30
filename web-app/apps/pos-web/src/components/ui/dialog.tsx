import { X } from "lucide-react";
import {
    createContext,
    type HTMLAttributes,
    type ReactNode,
    useContext,
    useEffect,
    useRef
} from "react";
import { createPortal } from "react-dom";

interface DialogContextValue {
    onOpenChange: (open: boolean) => void;
    open: boolean;
}

const DialogContext = createContext<DialogContextValue | null>(null);

function cn(...classNames: Array<string | undefined>) {
    return classNames.filter(Boolean).join(" ");
}

export function Dialog({
    children,
    onOpenChange,
    open
}: {
    children: ReactNode;
    onOpenChange: (open: boolean) => void;
    open: boolean;
}) {
    return (
        <DialogContext.Provider value={{ onOpenChange, open }}>
            {children}
        </DialogContext.Provider>
    );
}

export function DialogContent({
    children,
    className
}: {
    children: ReactNode;
    className?: string;
}) {
    const context = useDialogContext();
    const dialogRef = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const dialog = dialogRef.current;
        if (!dialog) return;

        if (context.open && !dialog.open) dialog.showModal();
        if (!context.open && dialog.open) dialog.close();
    }, [context.open]);

    if (!context.open) return null;

    return createPortal(
        <dialog
            aria-modal="true"
            className={cn(
                "m-auto max-h-[90vh] w-[min(960px,calc(100vw-32px))] overflow-hidden rounded-2xl border border-[#dce5e1] bg-white p-0 text-[#172317] shadow-[0_24px_60px_rgba(24,39,28,0.18)] backdrop:bg-[#172317]/10",
                className
            )}
            onCancel={(event) => {
                event.preventDefault();
                context.onOpenChange(false);
            }}
            onClick={(event) => {
                if (event.target === event.currentTarget) {
                    context.onOpenChange(false);
                }
            }}
            ref={dialogRef}
        >
            <div className="relative">{children}</div>
            <button
                aria-label="Đóng"
                className="absolute right-5 top-4 grid size-8 place-items-center rounded-lg text-[#697684] transition-colors hover:bg-[#f1f5f3] hover:text-[#172317] focus:outline-none focus:ring-2 focus:ring-[#159734]/30"
                onClick={() => context.onOpenChange(false)}
                type="button"
            >
                <X aria-hidden="true" size={18} />
            </button>
        </dialog>,
        document.body
    );
}

export function DialogHeader({
    className,
    ...props
}: HTMLAttributes<HTMLDivElement>) {
    return (
        <div
            className={cn(
                "space-y-1.5 border-b border-[#eef2f0] px-6 py-5 pr-16",
                className
            )}
            {...props}
        />
    );
}

export function DialogTitle({
    className,
    ...props
}: HTMLAttributes<HTMLHeadingElement>) {
    return (
        <h2
            className={cn("text-base font-black text-[#172317]", className)}
            {...props}
        />
    );
}

export function DialogDescription({
    className,
    ...props
}: HTMLAttributes<HTMLParagraphElement>) {
    return (
        <p
            className={cn(
                "text-sm font-semibold text-[#697684]",
                className
            )}
            {...props}
        />
    );
}

export function DialogFooter({
    className,
    ...props
}: HTMLAttributes<HTMLDivElement>) {
    return (
        <div
            className={cn(
                "flex justify-end gap-3 border-t border-[#eef2f0] px-6 py-4",
                className
            )}
            {...props}
        />
    );
}

function useDialogContext() {
    const context = useContext(DialogContext);
    if (!context) {
        throw new Error("Dialog components must be used inside Dialog");
    }
    return context;
}
