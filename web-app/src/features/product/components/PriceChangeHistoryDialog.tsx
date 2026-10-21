import { X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { usePagedApiResource } from "../hooks/useApiResource";
import { priceListApi } from "../services/priceList.api";
import type {
    CatalogStatus,
    PriceChangeHistory,
    PriceChangeType,
    PriceList
} from "../types/product.types";
import { formatCurrency } from "../utils/product-formatters";
import { ApiErrorState, ApiLoadingState } from "./ApiState";

interface PriceChangeHistoryDialogProps {
    priceList: PriceList;
    onClose(): void;
}

const changeTypeLabels: Record<PriceChangeType, string> = {
    CREATED: "Thêm giá",
    UPDATED: "Đổi giá",
    REMOVED: "Gỡ giá",
    STATUS_CHANGED: "Đổi trạng thái"
};

const changeTypeClasses: Record<PriceChangeType, string> = {
    CREATED: "bg-emerald-50 text-emerald-700",
    UPDATED: "bg-amber-50 text-amber-700",
    REMOVED: "bg-red-50 text-red-600",
    STATUS_CHANGED: "bg-sky-50 text-sky-700"
};

const statusLabels: Record<CatalogStatus, string> = {
    ACTIVE: "Đang hoạt động",
    INACTIVE: "Ngừng hoạt động"
};

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short"
});

const headClass =
    "sticky top-0 border-b border-pos-border bg-neutral-50 px-3 py-2.5 text-left text-xs font-bold uppercase text-gray-500";
const cellClass = "border-b border-[#edf0f4] px-3 py-2.5 align-middle";
const inputClass =
    "h-9 rounded-lg border border-pos-border bg-white px-2 text-sm text-gray-700 focus:border-[#07883a] focus:outline-none";

function formatPrice(value: number | null) {
    return value === null ? "—" : formatCurrency(value);
}

/** Đổi ngày chọn trên input (giờ địa phương) sang mốc ISO UTC đầu/cuối ngày. */
function toDayBoundary(value: string, endOfDay: boolean) {
    if (!value) {
        return undefined;
    }
    const date = new Date(
        `${value}T${endOfDay ? "23:59:59.999" : "00:00:00"}`
    );
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function PriceChange({ item }: { item: PriceChangeHistory }) {
    if (item.changeType === "STATUS_CHANGED") {
        return (
            <span>
                {item.oldStatus ? statusLabels[item.oldStatus] : "—"}
                {" → "}
                <strong>
                    {item.newStatus ? statusLabels[item.newStatus] : "—"}
                </strong>
            </span>
        );
    }

    return (
        <span className="whitespace-nowrap">
            <span className="text-gray-500">{formatPrice(item.oldPrice)}</span>
            {" → "}
            <strong className="text-hvt-green-900">
                {formatPrice(item.newPrice)}
            </strong>
        </span>
    );
}

function PriceDifference({ value }: { value: number | null }) {
    if (value === null) {
        return <span className="text-gray-400">—</span>;
    }
    if (value === 0) {
        return <span className="text-gray-500">{formatCurrency(0)}</span>;
    }

    return (
        <span
            className={`whitespace-nowrap font-bold ${value > 0 ? "text-red-600" : "text-emerald-700"}`}
        >
            {value > 0 ? "+" : "−"}
            {formatCurrency(Math.abs(value))}
        </span>
    );
}

export function PriceChangeHistoryDialog({
    priceList,
    onClose
}: PriceChangeHistoryDialogProps) {
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const priceListId = String(priceList.id);

    const loadHistory = useCallback(
        () =>
            priceListApi.history(priceListId, {
                page: page - 1,
                size: pageSize,
                from: toDayBoundary(fromDate, false),
                to: toDayBoundary(toDate, true)
            }),
        [priceListId, page, pageSize, fromDate, toDate]
    );
    const resource = usePagedApiResource(loadHistory);
    const rows = resource.data?.content ?? [];

    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") onClose();
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [onClose]);

    return (
        <div className="fixed inset-0 z-40 grid place-items-center overflow-y-auto bg-black/40 p-6">
            <section
                aria-labelledby="price-change-history-title"
                aria-modal="true"
                className="flex max-h-[calc(100vh-3rem)] w-full max-w-5xl flex-col rounded-3xl border border-pos-border bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start justify-between gap-3 border-b border-pos-border px-6 py-4">
                    <div className="grid gap-0.5">
                        <h2
                            className="text-xl font-bold text-gray-700"
                            id="price-change-history-title"
                        >
                            Lịch sử thay đổi giá
                        </h2>
                        <p className="text-sm font-semibold text-gray-500">
                            {priceList.name}
                        </p>
                    </div>
                    <button
                        aria-label="Đóng"
                        className="grid size-9 shrink-0 cursor-pointer place-items-center rounded-lg transition-all hover:bg-neutral-100 focus:outline-none"
                        onClick={onClose}
                        type="button"
                    >
                        <X size={20} />
                    </button>
                </header>

                <div className="flex flex-wrap items-end gap-3 border-b border-pos-border px-6 py-3 text-sm">
                    <label className="grid gap-1 font-bold text-gray-500">
                        Từ ngày
                        <input
                            className={inputClass}
                            max={toDate || undefined}
                            onChange={(event) => {
                                setFromDate(event.target.value);
                                setPage(1);
                            }}
                            type="date"
                            value={fromDate}
                        />
                    </label>
                    <label className="grid gap-1 font-bold text-gray-500">
                        Đến ngày
                        <input
                            className={inputClass}
                            min={fromDate || undefined}
                            onChange={(event) => {
                                setToDate(event.target.value);
                                setPage(1);
                            }}
                            type="date"
                            value={toDate}
                        />
                    </label>
                    {fromDate || toDate ? (
                        <button
                            className="h-9 cursor-pointer rounded-lg border border-pos-border px-3 font-bold text-gray-500 hover:bg-neutral-50"
                            onClick={() => {
                                setFromDate("");
                                setToDate("");
                                setPage(1);
                            }}
                            type="button"
                        >
                            Xóa lọc
                        </button>
                    ) : null}
                </div>

                <div className="min-h-0 flex-1 overflow-auto">
                    {resource.error ? (
                        <ApiErrorState
                            message={resource.error}
                            onRetry={resource.refresh}
                        />
                    ) : resource.loading && !resource.data ? (
                        <ApiLoadingState />
                    ) : rows.length === 0 ? (
                        <p className="p-10 text-center text-sm text-gray-500">
                            Chưa có thay đổi giá nào
                            {fromDate || toDate
                                ? " trong khoảng thời gian đã chọn."
                                : " được ghi nhận cho bảng giá này."}
                        </p>
                    ) : (
                        <table
                            className={`w-full border-collapse text-sm text-gray-700 ${resource.loading ? "opacity-60" : ""}`}
                        >
                            <thead>
                                <tr>
                                    <th className={headClass}>Mặt hàng</th>
                                    <th className={headClass}>Loại thay đổi</th>
                                    <th className={headClass}>
                                        Giá cũ → Giá mới
                                    </th>
                                    <th className={headClass}>Chênh lệch</th>
                                    <th className={headClass}>Người thay đổi</th>
                                    <th className={headClass}>Thời gian</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((item) => (
                                    <tr key={item.id}>
                                        <td className={cellClass}>
                                            {item.changeType ===
                                            "STATUS_CHANGED" ? (
                                                <span className="text-gray-500">
                                                    Toàn bộ bảng giá
                                                </span>
                                            ) : (
                                                <>
                                                    <span className="font-semibold">
                                                        {item.variantName ||
                                                            "Mặt hàng"}
                                                    </span>
                                                    <span className="block font-mono text-xs text-gray-500">
                                                        {item.sku}
                                                    </span>
                                                </>
                                            )}
                                        </td>
                                        <td className={cellClass}>
                                            <span
                                                className={`inline-block whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${changeTypeClasses[item.changeType] ?? "bg-neutral-100 text-gray-500"}`}
                                            >
                                                {changeTypeLabels[
                                                    item.changeType
                                                ] ?? item.changeType}
                                            </span>
                                        </td>
                                        <td className={cellClass}>
                                            <PriceChange item={item} />
                                        </td>
                                        <td className={cellClass}>
                                            <PriceDifference
                                                value={item.priceDifference}
                                            />
                                        </td>
                                        <td className={cellClass}>
                                            {item.changedByName ||
                                                item.changedBy ||
                                                "Hệ thống"}
                                        </td>
                                        <td
                                            className={`${cellClass} whitespace-nowrap`}
                                        >
                                            {dateTimeFormatter.format(
                                                new Date(item.changedAt)
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>

                <Pagination
                    onPageChange={setPage}
                    onPageSizeChange={(size) => {
                        setPageSize(size);
                        setPage(1);
                    }}
                    page={page}
                    pageCount={resource.data?.totalPages ?? 1}
                    pageSize={pageSize}
                    totalItems={resource.data?.totalElements ?? 0}
                    totalItemsLabel="Tổng số thay đổi"
                />
            </section>
        </div>
    );
}
