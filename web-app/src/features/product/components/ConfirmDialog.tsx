import { useId } from "react";

import { Button } from "../../../components/ui/Button";
import { ModalShell } from "../../../components/ui/Overlay";

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
    const titleId = useId();

    return (
        <ModalShell
            className="w-[min(448px,calc(100vw-32px))] p-6"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy={titleId}
            onClose={onCancel}
        >
            <h2
                className={`text-xl font-bold ${confirmVariant === "danger" ? "text-red-600" : "text-gray-800"}`}
                id={titleId}
            >
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
                <Button disabled={submitting} onClick={onCancel}>
                    Hủy
                </Button>
                <Button disabled={submitting} onClick={onConfirm} variant={confirmVariant}>
                    {submitting ? "Đang xử lý..." : confirmLabel}
                </Button>
            </div>
        </ModalShell>
    );
}
