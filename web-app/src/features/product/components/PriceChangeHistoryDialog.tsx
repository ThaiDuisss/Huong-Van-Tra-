import { X } from "lucide-react";
import { useCallback, useState } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { type BadgeTone, StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
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

const changeTypeTones: Record<PriceChangeType, BadgeTone> = {
    CREATED: "success",
    UPDATED: "warning",
    REMOVED: "danger",
    STATUS_CHANGED: "sky"
};

const statusLabels: Record<CatalogStatus, string> = {
    ACTIVE: "Đang hoạt động",
    INACTIVE: "Ngừng hoạt động"
};

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short"
});

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

    return (
        <ModalShell
            className="w-[min(1024px,calc(100vw-32px))]"
            closeOnBackdrop={false}
            labelledBy="price-change-history-title"
            onClose={onClose}
        >
            <div className="flex max-h-[85vh] flex-col overflow-hidden">
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
                    <IconButton aria-label="Đóng" onClick={onClose} size="md">
                        <X size={20} />
                    </IconButton>
                </header>

                <div className="flex flex-wrap items-end gap-3 border-b border-pos-border px-6 py-3 text-sm">
                    <Field label="Từ ngày">
                        <TextInput
                            max={toDate || undefined}
                            onChange={(event) => {
                                setFromDate(event.target.value);
                                setPage(1);
                            }}
                            type="date"
                            value={fromDate}
                        />
                    </Field>
                    <Field label="Đến ngày">
                        <TextInput
                            min={fromDate || undefined}
                            onChange={(event) => {
                                setToDate(event.target.value);
                                setPage(1);
                            }}
                            type="date"
                            value={toDate}
                        />
                    </Field>
                    {fromDate || toDate ? (
                        <Button
                            onClick={() => {
                                setFromDate("");
                                setToDate("");
                                setPage(1);
                            }}
                        >
                            Xóa lọc
                        </Button>
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
                        <Table className={resource.loading ? "opacity-60" : ""}>
                            <TableHead className="sticky top-0">
                                <TableRow>
                                    <TableHeaderCell>Mặt hàng</TableHeaderCell>
                                    <TableHeaderCell>Loại thay đổi</TableHeaderCell>
                                    <TableHeaderCell>
                                        Giá cũ → Giá mới
                                    </TableHeaderCell>
                                    <TableHeaderCell>Chênh lệch</TableHeaderCell>
                                    <TableHeaderCell>Người thay đổi</TableHeaderCell>
                                    <TableHeaderCell>Thời gian</TableHeaderCell>
                                </TableRow>
                            </TableHead>
                            <tbody>
                                {rows.map((item) => (
                                    <TableRow key={item.id}>
                                        <TableCell>
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
                                        </TableCell>
                                        <TableCell>
                                            <StatusBadge tone={changeTypeTones[item.changeType] ?? "neutral"}>
                                                {changeTypeLabels[
                                                    item.changeType
                                                ] ?? item.changeType}
                                            </StatusBadge>
                                        </TableCell>
                                        <TableCell>
                                            <PriceChange item={item} />
                                        </TableCell>
                                        <TableCell>
                                            <PriceDifference
                                                value={item.priceDifference}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            {item.changedByName ||
                                                item.changedBy ||
                                                "Hệ thống"}
                                        </TableCell>
                                        <TableCell className="whitespace-nowrap">
                                            {dateTimeFormatter.format(
                                                new Date(item.changedAt)
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
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
            </div>
        </ModalShell>
    );
}
