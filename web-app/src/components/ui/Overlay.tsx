import { type ReactNode, useEffect } from "react";
import { createPortal } from "react-dom";

function useEscape(onClose: () => void, enabled = true) {
    useEffect(() => {
        if (!enabled) return;

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") onClose();
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [enabled, onClose]);
}

interface ModalShellProps {
    children: ReactNode;
    onClose: () => void;
    /** Class của khung nội dung (độ rộng, padding…). Mặc định rộng tối đa 640px. */
    className?: string;
    labelledBy?: string;
    /** Tên hộp thoại khi không có tiêu đề để trỏ `labelledBy`. */
    ariaLabel?: string;
    /**
     * `false`: khung không tự cuộn (`overflow-hidden`, xếp dọc) để màn hình tự ghim đầu / chân
     * và cho phần thân cuộn bằng `min-h-0 flex-1 overflow-auto`.
     */
    scroll?: boolean;
    /** Bấm ra nền để đóng. Tắt với form dài để tránh mất dữ liệu đang nhập. */
    closeOnBackdrop?: boolean;
    closeOnEscape?: boolean;
}

/**
 * Lớp phủ + khung hộp thoại giữa màn hình. Dùng cho mọi popup tự dựng thay vì viết lại
 * `fixed inset-0 …`; nội dung bên trong (tiêu đề, form, nút) do màn hình tự quyết định.
 */
export function ModalShell({
    children,
    onClose,
    className = "w-[min(640px,calc(100vw-32px))]",
    labelledBy,
    ariaLabel,
    scroll = true,
    closeOnBackdrop = true,
    closeOnEscape = true
}: ModalShellProps) {
    useEscape(onClose, closeOnEscape);

    return createPortal(
        <div
            className="fixed inset-0 z-50 grid place-items-center bg-[#0d1b12]/35 p-4"
            onMouseDown={(event) => {
                if (closeOnBackdrop && event.target === event.currentTarget) onClose();
            }}
        >
            <div
                aria-label={ariaLabel}
                aria-labelledby={labelledBy}
                aria-modal="true"
                className={`relative max-h-[90vh] ${scroll ? "overflow-auto" : "flex flex-col overflow-hidden"} rounded-2xl border border-[#dce5e1] bg-white text-[#172317] shadow-[0_24px_60px_rgba(24,39,28,0.18)] ${className}`}
                role="dialog"
            >
                {children}
            </div>
        </div>,
        document.body
    );
}

interface DrawerProps {
    children: ReactNode;
    onClose: () => void;
    /** `false` thì không render gì. */
    open?: boolean;
    /** Class của bảng trượt (độ rộng, nền…). Mặc định rộng tối đa 520px. */
    className?: string;
    labelledBy?: string;
    /** Tắt khi đang có hộp thoại mở chồng lên trên, để Escape chỉ đóng hộp thoại đó. */
    closeOnEscape?: boolean;
}

/** Bảng chi tiết trượt từ bên phải, dùng chung cho mọi màn "xem chi tiết". */
export function Drawer({
    children,
    onClose,
    open = true,
    className = "max-w-[520px]",
    labelledBy,
    closeOnEscape = true
}: DrawerProps) {
    useEscape(onClose, open && closeOnEscape);

    if (!open) return null;

    return createPortal(
        <div className="fixed inset-0 z-50 flex justify-end">
            <button
                aria-label="Đóng"
                className="absolute inset-0 cursor-default bg-[#0d1b12]/35"
                onClick={onClose}
                tabIndex={-1}
                type="button"
            />
            <aside
                aria-labelledby={labelledBy}
                aria-modal="true"
                className={`relative flex h-full w-full flex-col overflow-hidden bg-[#f7f8fb] shadow-pos-panel ${className}`}
                role="dialog"
            >
                {children}
            </aside>
        </div>,
        document.body
    );
}
