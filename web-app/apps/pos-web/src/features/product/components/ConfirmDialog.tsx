interface ConfirmDialogProps {
    error: string;
    submitting: boolean;
    onCancel(): void;
    onConfirm(): void;
    title?: string;
    description?: string;
    confirmLabel?: string;
    confirmVariant?: "primary" | "danger";
}

export function ConfirmDialog({
    error,
    submitting,
    onCancel,
    onConfirm,
    title = "Xác nhận thay đổi trạng thái",
    description = "Thao tác này sẽ thay đổi khả năng sử dụng dữ liệu trong hệ thống.",
    confirmLabel = "Xác nhận",
    confirmVariant = "primary"
}: ConfirmDialogProps) {
    const btnBgClass = confirmVariant === "danger"
        ? "bg-red-600 shadow-[0_6px_16px_rgba(220,38,38,0.18)] hover:bg-red-700 text-white"
        : "bg-[#07883a] shadow-[0_6px_16px_rgba(7,136,58,0.18)] hover:opacity-90 text-white";

    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-6">
            <section className="w-full max-w-md rounded-3xl bg-white p-6 shadow-pos-panel">
                <h2 className={`text-xl font-bold ${confirmVariant === "danger" ? "text-red-600" : "text-gray-800"}`}>
                    {title}
                </h2>
                <p className="mt-2 text-sm text-gray-500">
                    {description}
                </p>
                {error ? (
                    <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                        {error}
                    </p>
                ) : null}
                <div className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={submitting}
                        className="h-11 rounded-lg border border-[#dfe6ee] bg-white text-[#172317] px-5 text-sm font-black transition-all cursor-pointer hover:bg-neutral-50"
                    >
                        Hủy
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={submitting}
                        className={`h-11 rounded-lg border-0 px-5 text-sm font-black disabled:opacity-60 transition-all cursor-pointer ${btnBgClass}`}
                    >
                        {submitting ? "Đang xử lý..." : confirmLabel}
                    </button>
                </div>
            </section>
        </div>
    );
}
