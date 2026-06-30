interface ConfirmDialogProps {
    error: string;
    submitting: boolean;
    onCancel(): void;
    onConfirm(): void;
}

export function ConfirmDialog({
    error,
    submitting,
    onCancel,
    onConfirm
}: ConfirmDialogProps) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-6">
            <section className="w-full max-w-md rounded-3xl bg-white p-6 shadow-pos-panel">
                <h2 className="text-xl font-bold text-gray-500">
                    Xác nhận thay đổi trạng thái
                </h2>
                <p className="mt-2 text-sm text-gray-500">
                    Thao tác này sẽ thay đổi khả năng sử dụng dữ liệu trong hệ
                    thống.
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
                        className="h-11 rounded-lg border-0 bg-[#07883a] text-white px-5 text-sm font-black shadow-[0_6px_16px_rgba(7,136,58,0.18)] disabled:opacity-60 transition-all cursor-pointer hover:opacity-90"
                    >
                        {submitting ? "Đang cập nhật..." : "Xác nhận"}
                    </button>
                </div>
            </section>
        </div>
    );
}
