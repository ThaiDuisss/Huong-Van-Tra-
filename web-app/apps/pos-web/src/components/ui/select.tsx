import { Check, ChevronDown } from "lucide-react";
import {
    createContext,
    type CSSProperties,
    type RefObject,
    type ReactNode,
    useContext,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState
} from "react";
import { createPortal } from "react-dom";

type SelectContextValue = {
    disabled?: boolean;
    labels: Map<string, string>;
    open: boolean;
    registerItem: (value: string, label: string) => void;
    setOpen: (open: boolean) => void;
    triggerRef: RefObject<HTMLButtonElement | null>;
    value: string;
    onValueChange: (value: string) => void;
};

const SelectContext = createContext<SelectContextValue | null>(null);

function useSelectContext(component: string) {
    const context = useContext(SelectContext);
    if (!context) {
        throw new Error(`${component} must be used inside Select`);
    }
    return context;
}

export function Select({
    children,
    className = "",
    disabled,
    onValueChange,
    value
}: {
    children: ReactNode;
    className?: string;
    disabled?: boolean;
    onValueChange: (value: string) => void;
    value: string;
}) {
    const [open, setOpen] = useState(false);
    const [labels, setLabels] = useState<Map<string, string>>(() => new Map());
    const rootRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        function handlePointerDown(event: PointerEvent) {
            if (!rootRef.current?.contains(event.target as Node)) {
                setOpen(false);
            }
        }

        document.addEventListener("pointerdown", handlePointerDown);
        return () => document.removeEventListener("pointerdown", handlePointerDown);
    }, []);

    const contextValue = useMemo<SelectContextValue>(
        () => ({
            disabled,
            labels,
            open,
            registerItem: (itemValue, label) => {
                setLabels((current) => {
                    if (current.get(itemValue) === label) return current;
                    const next = new Map(current);
                    next.set(itemValue, label);
                    return next;
                });
            },
            setOpen,
            triggerRef,
            value,
            onValueChange
        }),
        [disabled, labels, open, onValueChange, value]
    );

    return (
        <SelectContext.Provider value={contextValue}>
            <div className={`relative inline-flex min-w-0 ${className}`} ref={rootRef}>
                {children}
            </div>
        </SelectContext.Provider>
    );
}

export function SelectTrigger({
    "aria-label": ariaLabel,
    children,
    className = ""
}: {
    "aria-label"?: string;
    children: ReactNode;
    className?: string;
}) {
    const { disabled, open, setOpen, triggerRef } =
        useSelectContext("SelectTrigger");

    return (
        <button
            aria-expanded={open}
            aria-label={ariaLabel}
            className={`inline-flex h-10 min-w-0 items-center justify-between gap-2 whitespace-nowrap rounded-xl border border-[#dce5e1] bg-white px-3 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#bce5c7] disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
            disabled={disabled}
            onClick={() => setOpen(!open)}
            ref={triggerRef}
            type="button"
        >
            <span className="flex min-w-0 items-center gap-2 truncate whitespace-nowrap">
                {children}
            </span>
            <ChevronDown
                aria-hidden="true"
                className={`shrink-0 text-[#8a96a3] transition-transform ${open ? "rotate-180" : ""}`}
                size={15}
            />
        </button>
    );
}

export function SelectValue({ placeholder }: { placeholder?: string }) {
    const { labels, value } = useSelectContext("SelectValue");

    return <>{labels.get(value) ?? placeholder ?? value}</>;
}

export function SelectContent({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    const { open, triggerRef } = useSelectContext("SelectContent");
    const [style, setStyle] = useState<CSSProperties>({});

    useLayoutEffect(() => {
        if (!open) return;

        function updatePosition() {
            const rect = triggerRef.current?.getBoundingClientRect();
            if (!rect) return;
            setStyle({
                left: rect.left,
                minWidth: rect.width,
                right: "auto",
                top: rect.bottom + 6
            });
        }

        updatePosition();
        window.addEventListener("resize", updatePosition);
        window.addEventListener("scroll", updatePosition, true);
        return () => {
            window.removeEventListener("resize", updatePosition);
            window.removeEventListener("scroll", updatePosition, true);
        };
    }, [open, triggerRef]);

    if (typeof document === "undefined") return null;

    return createPortal(
        <div
            className={`fixed z-[70] max-h-72 overflow-auto rounded-xl border border-[#dce5e1] bg-white p-1.5 shadow-[0_18px_45px_rgba(24,39,28,0.16)] ${
                open
                    ? "pointer-events-auto opacity-100"
                    : "pointer-events-none opacity-0"
            } ${className}`}
            onPointerDown={(event) => event.stopPropagation()}
            role="listbox"
            style={style}
        >
            {children}
        </div>,
        document.body
    );
}

export function SelectItem({
    children,
    value
}: {
    children: ReactNode;
    value: string;
}) {
    const {
        registerItem,
        setOpen,
        value: selectedValue,
        onValueChange
    } = useSelectContext("SelectItem");
    const active = selectedValue === value;
    const label = typeof children === "string" ? children : value;

    useLayoutEffect(() => {
        registerItem(value, label);
    }, [label, registerItem, value]);

    return (
        <button
            aria-selected={active}
            className={`flex h-9 w-full items-center justify-between gap-3 rounded-lg px-2.5 text-left text-sm font-semibold transition-colors ${
                active
                    ? "bg-[#eaf8ee] text-[#116f28]"
                    : "text-[#273a2b] hover:bg-[#f7fbf8]"
            }`}
            onClick={() => {
                onValueChange(value);
                setOpen(false);
            }}
            role="option"
            type="button"
        >
            <span className="min-w-0 truncate">{children}</span>
            {active && <Check className="shrink-0" size={15} aria-hidden="true" />}
        </button>
    );
}
