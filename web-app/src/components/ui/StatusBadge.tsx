import type { CSSProperties, ReactNode } from "react";

/** Bảng màu dùng chung cho mọi huy hiệu trạng thái. Đổi màu ở đây là đổi toàn hệ thống. */
export type BadgeTone =
    | "success"
    | "warning"
    | "pending"
    | "danger"
    | "info"
    | "sky"
    | "violet"
    | "teal"
    | "cyan"
    | "neutral";

const TONE_CLASS: Record<BadgeTone, string> = {
    success: "border-emerald-200 bg-emerald-50 text-emerald-700",
    warning: "border-amber-200 bg-amber-50 text-amber-700",
    pending: "border-orange-200 bg-orange-50 text-orange-700",
    danger: "border-red-200 bg-red-50 text-red-700",
    info: "border-blue-200 bg-blue-50 text-blue-700",
    sky: "border-sky-200 bg-sky-50 text-sky-700",
    violet: "border-violet-200 bg-violet-50 text-violet-700",
    teal: "border-teal-200 bg-teal-50 text-teal-700",
    cyan: "border-cyan-200 bg-cyan-50 text-cyan-700",
    neutral: "border-slate-200 bg-slate-100 text-slate-600"
};

const DOT_CLASS: Record<BadgeTone, string> = {
    success: "bg-emerald-600",
    warning: "bg-amber-500",
    pending: "bg-orange-500",
    danger: "bg-red-600",
    info: "bg-blue-600",
    sky: "bg-sky-600",
    violet: "bg-violet-600",
    teal: "bg-teal-600",
    cyan: "bg-cyan-600",
    neutral: "bg-slate-400"
};

interface StatusBadgeProps {
    children: ReactNode;
    tone?: BadgeTone;
    /** Chấm tròn màu trước nhãn. */
    dot?: boolean;
    /** Icon nhỏ trước nhãn (thay cho chấm). */
    icon?: ReactNode;
    /** `tag`: bo góc vuông hơn, dùng cho nhãn phân loại (vai trò, loại vị trí…). */
    shape?: "pill" | "tag";
    /** `sm`: cao 20px, chữ 11px, cho chỗ chật (thẻ POS, ô bảng hẹp). */
    size?: "sm" | "md";
    /** Mặc định cắt bằng dấu … khi thiếu chỗ; `false` để luôn hiện đủ chữ. */
    truncate?: boolean;
    /** Màu tùy biến theo dữ liệu (ví dụ màu hạng khách hàng); ghi đè màu của `tone`. */
    style?: CSSProperties;
    title?: string;
    className?: string;
}

/** Huy hiệu trạng thái dùng chung: một chiều cao, một cỡ chữ, một bảng màu. */
export function StatusBadge({
    children,
    tone = "neutral",
    dot = false,
    icon,
    shape = "pill",
    size = "md",
    truncate = true,
    style,
    title,
    className = ""
}: StatusBadgeProps) {
    return (
        <span
            className={`inline-flex max-w-full items-center whitespace-nowrap border font-bold ${
                size === "sm" ? "h-5 gap-1 px-2 text-[11px]" : "h-6 gap-1.5 px-2.5 text-xs"
            } ${shape === "pill" ? "rounded-full" : "rounded-md"} ${TONE_CLASS[tone]} ${className}`}
            style={style}
            title={title}
        >
            {icon ? <span aria-hidden="true" className="inline-flex shrink-0">{icon}</span> : null}
            {dot && !icon ? <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${DOT_CLASS[tone]}`} /> : null}
            <span className={truncate ? "truncate" : ""}>{children}</span>
        </span>
    );
}
