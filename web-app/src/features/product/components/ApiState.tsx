import { AlertTriangle, LoaderCircle } from "lucide-react";

import { Button } from "../../../components/ui/Button";

export function ApiLoadingState() {
    return (
        <div className="grid min-h-[420px] place-items-center p-8">
            <div className="text-center text-gray-500">
                <LoaderCircle
                    className="mx-auto mb-3 animate-spin"
                    size={34}
                />
                Đang tải dữ liệu...
            </div>
        </div>
    );
}

export function ApiErrorState({
    message,
    onRetry
}: {
    message: string;
    onRetry?(): void;
}) {
    return (
        <div className="grid min-h-[420px] place-items-center p-8 text-center">
            <div>
                <AlertTriangle
                    className="mx-auto mb-3 text-red-500"
                    size={34}
                />
                <h2 className="font-bold text-gray-500">
                    Không thể tải dữ liệu
                </h2>
                <p className="mt-1 text-sm text-gray-500">{message}</p>
                {onRetry ? (
                    <Button className="mt-4" onClick={onRetry} variant="primary">
                        Thử lại
                    </Button>
                ) : null}
            </div>
        </div>
    );
}
