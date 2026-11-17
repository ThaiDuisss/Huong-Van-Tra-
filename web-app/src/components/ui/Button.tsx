import type { ButtonHTMLAttributes, ReactNode, Ref } from "react";

import {
    BASE_CLASS,
    buttonClassName,
    type ButtonSize,
    type ButtonVariant,
    ICON_SIZE_CLASS,
    VARIANT_CLASS
} from "./button-styles";

export type { ButtonSize, ButtonVariant } from "./button-styles";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    children: ReactNode;
    variant?: ButtonVariant;
    size?: ButtonSize;
    ref?: Ref<HTMLButtonElement>;
}

/** Nút bấm dùng chung. Đổi kiểu dáng ở đây là đổi toàn hệ thống. */
export function Button({
    children,
    variant = "secondary",
    size = "md",
    className = "",
    type = "button",
    ...rest
}: ButtonProps) {
    return (
        <button className={buttonClassName({ variant, size, className })} type={type} {...rest}>
            {children}
        </button>
    );
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    /** Bắt buộc: nút chỉ có icon phải có nhãn cho trình đọc màn hình. */
    "aria-label": string;
    children: ReactNode;
    variant?: ButtonVariant;
    size?: ButtonSize;
    /** `danger`: icon đỏ, nền đỏ nhạt khi rê chuột (xóa, hủy) trên nền `ghost` / `secondary`. */
    tone?: "default" | "danger";
    ref?: Ref<HTMLButtonElement>;
}

/** Nút vuông chỉ chứa icon (đóng, sửa, xóa, làm mới…). */
export function IconButton({
    children,
    variant = "ghost",
    size = "sm",
    tone = "default",
    className = "",
    type = "button",
    ...rest
}: IconButtonProps) {
    return (
        <button
            className={`inline-grid place-items-center ${BASE_CLASS} ${VARIANT_CLASS[variant]} ${ICON_SIZE_CLASS[size]} ${
                tone === "danger" ? "text-red-600 hover:bg-red-50 hover:text-red-700" : ""
            } ${className}`}
            type={type}
            {...rest}
        >
            {children}
        </button>
    );
}
