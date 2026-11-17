/** Kiểu dáng nút dùng chung; tách khỏi `Button.tsx` để file component chỉ export component. */

export type ButtonVariant =
    | "primary"
    | "secondary"
    | "danger"
    | "dangerOutline"
    | "warning"
    | "info"
    | "ghost"
    | "link";
export type ButtonSize = "xs" | "sm" | "md" | "search" | "lg";

export const VARIANT_CLASS: Record<ButtonVariant, string> = {
    primary: "border border-transparent bg-[#07883a] text-white shadow-[0_6px_16px_rgba(7,136,58,0.2)] hover:bg-[#067a34]",
    secondary: "border border-[#dfe6ee] bg-white text-[#172317] hover:bg-neutral-50",
    danger: "border border-transparent bg-red-600 text-white hover:bg-red-700",
    dangerOutline: "border border-red-200 bg-white text-red-700 hover:bg-red-50",
    warning: "border border-transparent bg-amber-500 text-white hover:bg-amber-600",
    info: "border border-transparent bg-blue-600 text-white hover:bg-blue-700",
    ghost: "border border-transparent bg-transparent text-[#536171] hover:bg-neutral-100",
    link: "border border-transparent bg-transparent text-[#07883a] hover:underline"
};

export const SIZE_CLASS: Record<ButtonSize, string> = {
    xs: "h-6 gap-1 px-2 text-xs",
    sm: "h-8 gap-1.5 px-3 text-xs",
    md: "h-10 gap-2 px-4 text-sm",
    /** Cùng chiều cao 44px với `SearchInput`, dùng cho nút đứng cạnh ô tìm kiếm. */
    search: "h-11 gap-2 px-4 text-sm",
    lg: "h-12 gap-[9px] px-5 text-sm"
};

export const ICON_SIZE_CLASS: Record<ButtonSize, string> = {
    xs: "size-6",
    sm: "size-8",
    md: "size-10",
    search: "size-11",
    lg: "size-12"
};

export const BASE_CLASS =
    "shrink-0 cursor-pointer items-center justify-center rounded-lg font-black transition-colors disabled:cursor-not-allowed disabled:opacity-60 aria-pressed:border-[#07883a] aria-pressed:bg-[#eaf7ec] aria-pressed:text-[#07883a]";

/**
 * Chuỗi class của `Button`, để thẻ khác (ví dụ `<Link>` của router) có đúng kiểu dáng nút:
 * `<Link className={buttonClassName({ variant: "secondary" })} to="…">`.
 */
export function buttonClassName({
    variant = "secondary",
    size = "md",
    className = ""
}: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {}) {
    return `inline-flex ${BASE_CLASS} ${VARIANT_CLASS[variant]} ${SIZE_CLASS[size]} ${className}`;
}
