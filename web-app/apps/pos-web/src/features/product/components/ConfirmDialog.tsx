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
                <h2 className="text-xl font-bold text-pos-ink">
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
                        className="h-10 rounded-xl border border-pos-border px-4 text-sm font-bold"
                    >
                        Hủy
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={submitting}
                        className="h-10 rounded-xl bg-hvt-green-900 px-4 text-sm font-bold text-white disabled:opacity-60"
                    >
                        {submitting ? "Đang cập nhật..." : "Xác nhận"}
                    </button>
                </div>
            </section>
        </div>
    );
}
