import { AlertTriangle, LoaderCircle } from "lucide-react";

export function InventoryLoading() {
    return (
        <div className="grid min-h-80 place-items-center text-sm text-gray-500">
            <div className="text-center">
                <LoaderCircle className="mx-auto mb-3 animate-spin" />
                Đang tải dữ liệu...
            </div>
        </div>
    );
}

export function InventoryError({
    message,
    onRetry
}: {
    message: string;
    onRetry(): void;
}) {
    return (
        <div className="grid min-h-80 place-items-center p-6 text-center">
            <div>
                <AlertTriangle className="mx-auto mb-3 text-red-500" />
                <p className="font-bold text-gray-900">
                    Đã xảy ra lỗi. Vui lòng thử lại sau.
                </p>
                <p className="mt-1 text-sm text-gray-500">{message}</p>
                <button
                    type="button"
                    onClick={onRetry}
                    className="mt-4 rounded-xl bg-hvt-green-900 px-4 py-2 text-sm font-bold text-white"
                >
                    Thử lại
                </button>
            </div>
        </div>
    );
}

export function InventoryEmpty() {
    return (
        <div className="grid min-h-80 place-items-center text-center">
            <div>
                <p className="font-bold text-gray-900">Không có dữ liệu</p>
                <p className="mt-1 text-sm text-gray-500">
                    Không tìm thấy kết quả phù hợp
                </p>
            </div>
        </div>
    );
}
