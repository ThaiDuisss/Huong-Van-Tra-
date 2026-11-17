import {
    type InputHTMLAttributes,
    type ReactNode,
    type Ref,
    type SelectHTMLAttributes,
    type TextareaHTMLAttributes,
    useId
} from "react";

const CONTROL_CLASS =
    "rounded-lg border bg-white px-3 text-[#172317] outline-none transition-colors placeholder:text-[#9aa5b1] focus:ring-2 disabled:cursor-not-allowed disabled:bg-neutral-100 disabled:text-neutral-500";
const VALID_CLASS = "border-[#dfe6ee] focus:border-[#159734] focus:ring-[#159734]/15";
const INVALID_CLASS = "border-red-400 focus:border-red-500 focus:ring-red-500/15";

type ControlSize = "sm" | "md";

interface ControlStyleProps {
    /** `sm`: cao 32px cho ô trong bảng / thanh công cụ. */
    controlSize?: ControlSize;
    /** Viền đỏ khi dữ liệu không hợp lệ. */
    invalid?: boolean;
    /** `false`: rộng theo nội dung (thanh công cụ) thay vì chiếm hết chiều ngang. */
    fullWidth?: boolean;
}

function controlClass({ controlSize = "md", invalid = false, fullWidth = true }: ControlStyleProps, extra: string) {
    return `${CONTROL_CLASS} ${invalid ? INVALID_CLASS : VALID_CLASS} ${fullWidth ? "w-full" : ""} ${
        controlSize === "sm" ? "text-xs" : "text-sm"
    } ${extra}`;
}

/**
 * Nhãn + ô nhập + lỗi/ghi chú. Bọc mọi ô nhập của form để khoảng cách đồng nhất.
 * `as="div"` cho điều khiển tự dựng (dropdown có tìm kiếm, tải file) không nằm được trong `<label>`.
 */
export function Field({
    label,
    required = false,
    error,
    hint,
    children,
    as = "label",
    errorId,
    className = ""
}: {
    label: ReactNode;
    required?: boolean;
    error?: string | null;
    hint?: ReactNode;
    children: ReactNode;
    as?: "label" | "div";
    /** id của dòng lỗi, để ô nhập trỏ `aria-describedby`. */
    errorId?: string;
    className?: string;
}) {
    const fallbackId = useId();
    const Wrapper = as;

    return (
        <Wrapper className={`grid gap-1.5 text-sm ${className}`}>
            <span className="font-bold text-[#3d4b5c]">
                {label}
                {required ? <span className="text-red-600"> *</span> : null}
            </span>
            {children}
            {error ? (
                <span className="text-xs font-semibold text-red-600" id={errorId ?? fallbackId} role="alert">
                    {error}
                </span>
            ) : null}
            {!error && hint ? <span className="text-xs font-normal text-[#697684]">{hint}</span> : null}
        </Wrapper>
    );
}

export function TextInput({
    className = "",
    controlSize,
    invalid,
    fullWidth,
    ...rest
}: InputHTMLAttributes<HTMLInputElement> & ControlStyleProps & { ref?: Ref<HTMLInputElement> }) {
    return (
        <input
            aria-invalid={invalid || undefined}
            className={`${controlSize === "sm" ? "h-8" : "h-10"} font-normal ${controlClass({ controlSize, invalid, fullWidth }, className)}`}
            {...rest}
        />
    );
}

export function TextArea({
    className = "",
    controlSize,
    invalid,
    fullWidth,
    ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & ControlStyleProps & { ref?: Ref<HTMLTextAreaElement> }) {
    return (
        <textarea
            aria-invalid={invalid || undefined}
            className={`${controlSize === "sm" ? "min-h-[56px]" : "min-h-[84px]"} py-2 font-normal ${controlClass({ controlSize, invalid, fullWidth }, className)}`}
            {...rest}
        />
    );
}

/** `<select>` gốc của trình duyệt với kiểu dáng chung. Danh sách có tìm kiếm thì dùng `ui/select`. */
export function NativeSelect({
    className = "",
    controlSize,
    invalid,
    fullWidth,
    children,
    ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & ControlStyleProps & { ref?: Ref<HTMLSelectElement> }) {
    return (
        <select
            aria-invalid={invalid || undefined}
            className={`${controlSize === "sm" ? "h-8" : "h-10"} font-normal ${controlClass({ controlSize, invalid, fullWidth }, className)}`}
            {...rest}
        >
            {children}
        </select>
    );
}
