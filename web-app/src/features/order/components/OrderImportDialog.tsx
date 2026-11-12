import {
    AlertCircle,
    CheckCircle2,
    Download,
    FileSpreadsheet,
    LoaderCircle,
    Upload
} from "lucide-react";
import { type DragEvent, useRef, useState } from "react";

import { Button } from "../../../components/ui/Button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { StatusBadge, type BadgeTone } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import { importOrders } from "../services/order.api";
import type {
    OrderImportOrderResult,
    OrderImportResponse,
    OrderImportRow,
    OrderImportStatus
} from "../types/order-import.types";
import { formatCurrency, formatDateTime } from "../utils/order-formatters";
import {
    ORDER_IMPORT_MAX_ORDERS,
    ORDER_IMPORT_MAX_ROWS,
    downloadOrderImportTemplate,
    parseOrderImportFile
} from "../utils/order-import.utils";

type Phase = "idle" | "checking" | "checked" | "importing" | "done";

const STATUS_LABELS: Record<OrderImportStatus, string> = {
    VALID: "Hợp lệ",
    IMPORTED: "Đã nhập",
    DUPLICATE: "Đã có, bỏ qua",
    INVALID: "Lỗi dữ liệu",
    FAILED: "Nhập thất bại"
};

const STATUS_TONES: Record<OrderImportStatus, BadgeTone> = {
    VALID: "success",
    IMPORTED: "success",
    DUPLICATE: "warning",
    INVALID: "danger",
    FAILED: "danger"
};

function isErrorStatus(status: OrderImportStatus) {
    return status === "INVALID" || status === "FAILED";
}

function errorMessage(error: unknown) {
    return error instanceof Error && error.message
        ? error.message
        : "Không thể xử lý file đơn hàng. Vui lòng thử lại.";
}

export function OrderImportDialog({
    onClose,
    onImported,
    title = "Nhập đơn hàng từ file"
}: {
    onClose: () => void;
    onImported: () => void;
    /** Màn Hóa đơn dùng chung luồng nhập (mỗi hóa đơn là một đơn bán), chỉ đổi tiêu đề. */
    title?: string;
}) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [phase, setPhase] = useState<Phase>("idle");
    const [fileName, setFileName] = useState("");
    const [rows, setRows] = useState<OrderImportRow[]>([]);
    const [response, setResponse] = useState<OrderImportResponse | null>(null);
    const [error, setError] = useState("");
    const [dragOver, setDragOver] = useState(false);
    const [onlyErrors, setOnlyErrors] = useState(false);

    const busy = phase === "checking" || phase === "importing";
    const successCount = response?.successCount ?? 0;
    const duplicateCount = response?.duplicateCount ?? 0;
    const failedCount = response?.failedCount ?? 0;
    const visibleResults = (response?.results ?? []).filter(
        (result) => !onlyErrors || isErrorStatus(result.status)
    );

    function close() {
        // Không cho đóng khi đang gửi để người dùng không tưởng đơn chưa được tạo.
        if (!busy) onClose();
    }

    async function selectFile(file: File) {
        setError("");
        setResponse(null);
        setRows([]);
        setOnlyErrors(false);
        setFileName(file.name);
        setPhase("checking");
        try {
            const parsedRows = await parseOrderImportFile(file);
            setRows(parsedRows);
            setResponse(await importOrders({ rows: parsedRows, dryRun: true }));
            setPhase("checked");
        } catch (selectError) {
            setError(errorMessage(selectError));
            setPhase("idle");
        }
    }

    async function importValidOrders() {
        if (rows.length === 0) return;
        setError("");
        setPhase("importing");
        try {
            const result = await importOrders({ rows, dryRun: false });
            setResponse(result);
            setPhase("done");
            if (result.successCount > 0) onImported();
        } catch (importError) {
            setError(
                `${errorMessage(importError)} Chưa xác định được kết quả nhập. Nhập lại cùng file là an toàn: các đơn đã tạo sẽ được bỏ qua.`
            );
            setPhase("checked");
        }
    }

    function handleDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault();
        setDragOver(false);
        const file = event.dataTransfer.files?.[0];
        if (file && !busy) void selectFile(file);
    }

    return (
        <Dialog onOpenChange={(open) => !open && close()} open>
            <DialogContent className="w-[min(1080px,calc(100vw-32px))]">
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    <DialogDescription>
                        Tải file Excel theo mẫu, tối đa {ORDER_IMPORT_MAX_ROWS.toLocaleString("vi-VN")} dòng /{" "}
                        {ORDER_IMPORT_MAX_ORDERS} đơn mỗi lần. Hệ thống kiểm tra toàn bộ trước khi tạo đơn.
                    </DialogDescription>
                </DialogHeader>

                <div className="grid max-h-[68vh] gap-4 overflow-y-auto p-6">
                    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e4ece8] bg-[#f8faf9] px-4 py-3">
                        <div className="min-w-0 flex-1 space-y-1 text-xs font-semibold text-[#536171]">
                            <p>
                                Mỗi dòng là một sản phẩm; các dòng cùng{" "}
                                <strong className="font-black">Mã đơn tham chiếu</strong> gộp thành một đơn.
                                Cột bắt buộc: <strong className="font-black">Mã đơn tham chiếu</strong>,{" "}
                                <strong className="font-black">Mã SKU</strong>,{" "}
                                <strong className="font-black">Số lượng</strong>.
                            </p>
                            <p>
                                Đơn được ghi nhận đã thanh toán tiền mặt đủ và trừ tồn kho như bán tại quầy.
                                Mã đơn tham chiếu đã nhập trước đó sẽ được bỏ qua.
                            </p>
                        </div>
                        <Button onClick={downloadOrderImportTemplate} size="sm">
                            <Download size={15} aria-hidden="true" />
                            Tải file mẫu
                        </Button>
                    </div>

                    {phase !== "done" && (
                        <div
                            className={`grid place-items-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors ${
                                dragOver ? "border-[#159734] bg-[#f3fbf5]" : "border-[#cfdcd5] bg-white"
                            }`}
                            onDragLeave={() => setDragOver(false)}
                            onDragOver={(event) => {
                                event.preventDefault();
                                setDragOver(true);
                            }}
                            onDrop={handleDrop}
                        >
                            <FileSpreadsheet className="text-[#159734]" size={28} aria-hidden="true" />
                            <p className="text-sm font-bold text-[#273a2b]">
                                {fileName || "Kéo thả file Excel vào đây"}
                            </p>
                            <p className="text-xs font-semibold text-[#697684]">
                                Hỗ trợ .xlsx, .xls, .csv - tối đa 5MB
                            </p>
                            <Button
                                disabled={busy}
                                onClick={() => inputRef.current?.click()}
                                size="sm"
                            >
                                <Upload size={15} aria-hidden="true" />
                                {fileName ? "Chọn file khác" : "Chọn file"}
                            </Button>
                            <input
                                accept=".xlsx,.xls,.csv"
                                className="hidden"
                                onChange={(event) => {
                                    const file = event.target.files?.[0];
                                    event.target.value = "";
                                    if (file) void selectFile(file);
                                }}
                                ref={inputRef}
                                type="file"
                            />
                        </div>
                    )}

                    {busy && (
                        <div className="flex items-center gap-2 text-sm font-bold text-[#536171]">
                            <LoaderCircle className="animate-spin" size={16} aria-hidden="true" />
                            {phase === "checking"
                                ? "Đang đọc và kiểm tra dữ liệu..."
                                : "Đang tạo đơn hàng, vui lòng không đóng cửa sổ..."}
                        </div>
                    )}

                    {error && (
                        <div
                            className="flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]"
                            role="alert"
                        >
                            <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
                            {error}
                        </div>
                    )}

                    {response && (
                        <>
                            <div className="flex flex-wrap items-center gap-2 text-xs font-black">
                                {phase === "done" && (
                                    <span className="inline-flex items-center gap-1.5 text-sm text-[#159734]">
                                        <CheckCircle2 size={16} aria-hidden="true" />
                                        Hoàn tất nhập đơn hàng
                                    </span>
                                )}
                                <SummaryChip label="Số dòng" value={response.totalRows} />
                                <SummaryChip label="Số đơn" value={response.totalOrders} />
                                <SummaryChip
                                    label={response.dryRun ? "Hợp lệ" : "Đã nhập"}
                                    tone="success"
                                    value={successCount}
                                />
                                {duplicateCount > 0 && (
                                    <SummaryChip label="Đã có, bỏ qua" tone="warning" value={duplicateCount} />
                                )}
                                <SummaryChip
                                    label={response.dryRun ? "Lỗi" : "Không nhập được"}
                                    tone={failedCount > 0 ? "danger" : "neutral"}
                                    value={failedCount}
                                />
                                {failedCount > 0 && (
                                    <label className="ml-auto inline-flex cursor-pointer items-center gap-2 text-xs font-bold text-[#536171]">
                                        <input
                                            checked={onlyErrors}
                                            onChange={(event) => setOnlyErrors(event.target.checked)}
                                            type="checkbox"
                                        />
                                        Chỉ hiện đơn lỗi
                                    </label>
                                )}
                            </div>

                            {response.dryRun && failedCount > 0 && (
                                <p className="text-xs font-semibold text-[#8a5a12]">
                                    {successCount > 0
                                        ? `Các đơn lỗi sẽ bị bỏ qua, chỉ ${successCount.toLocaleString("vi-VN")} đơn hợp lệ được tạo. Bạn có thể sửa file rồi chọn lại để nhập đầy đủ.`
                                        : "Không có đơn nào hợp lệ. Vui lòng sửa file rồi chọn lại."}
                                </p>
                            )}

                            <div className="overflow-auto rounded-xl border border-[#e4ece8]">
                                <Table className="min-w-[900px] text-xs">
                                    <TableHead>
                                        <TableRow>
                                            {[
                                                "Dòng",
                                                "Mã tham chiếu",
                                                "Ngày bán",
                                                "Khách hàng",
                                                "Số SP",
                                                "Thành tiền",
                                                "Trạng thái",
                                                "Chi tiết"
                                            ].map((header) => (
                                                <TableHeaderCell key={header}>{header}</TableHeaderCell>
                                            ))}
                                        </TableRow>
                                    </TableHead>
                                    <tbody>
                                        {visibleResults.map((result, index) => (
                                            <ResultRow
                                                key={`${result.orderReference ?? "row"}-${result.rowNumbers[0] ?? index}`}
                                                result={result}
                                            />
                                        ))}
                                    </tbody>
                                </Table>
                            </div>
                        </>
                    )}
                </div>

                <DialogFooter>
                    <Button disabled={busy} onClick={close}>
                        {phase === "done" ? "Đóng" : "Hủy"}
                    </Button>
                    {phase !== "done" && (
                        <Button
                            disabled={phase !== "checked" || successCount === 0}
                            onClick={() => void importValidOrders()}
                            variant="primary"
                        >
                            {phase === "importing" && (
                                <LoaderCircle className="animate-spin" size={16} aria-hidden="true" />
                            )}
                            {phase === "importing"
                                ? "Đang nhập..."
                                : successCount > 0
                                  ? `Nhập ${successCount.toLocaleString("vi-VN")} đơn hợp lệ`
                                  : "Nhập đơn hàng"}
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function formatRowNumbers(rowNumbers: number[]) {
    if (rowNumbers.length === 0) return "-";
    if (rowNumbers.length <= 3) return rowNumbers.join(", ");
    return `${rowNumbers.slice(0, 2).join(", ")}, … (${rowNumbers.length} dòng)`;
}

function ResultRow({ result }: { result: OrderImportOrderResult }) {
    const error = isErrorStatus(result.status);
    const customer = [result.customerName, result.customerPhone].filter(Boolean).join(" - ");
    return (
        <TableRow className={error ? "bg-[#fffaf9]" : "bg-white"}>
            <TableCell className="whitespace-nowrap font-black">
                {formatRowNumbers(result.rowNumbers)}
            </TableCell>
            <TableCell className="font-bold">{result.orderReference || "-"}</TableCell>
            <TableCell className="whitespace-nowrap">
                {result.soldAt ? formatDateTime(result.soldAt) : "Lúc nhập"}
            </TableCell>
            <TableCell>{customer || "Khách lẻ"}</TableCell>
            <TableCell className="whitespace-nowrap">{result.itemCount}</TableCell>
            <TableCell className="whitespace-nowrap font-bold">
                {error ? "-" : formatCurrency(result.totalAmount)}
            </TableCell>
            <TableCell className="whitespace-nowrap">
                <StatusBadge tone={STATUS_TONES[result.status]}>{STATUS_LABELS[result.status]}</StatusBadge>
            </TableCell>
            <TableCell className={error ? "font-bold text-[#a43a2a]" : "text-[#697684]"}>
                {result.orderCode && result.status === "IMPORTED" && (
                    <p className="font-bold text-[#159734]">Mã đơn: {result.orderCode}</p>
                )}
                {result.messages.map((message) => (
                    <p key={message}>{message}</p>
                ))}
            </TableCell>
        </TableRow>
    );
}

function SummaryChip({
    label,
    tone = "neutral",
    value
}: {
    label: string;
    tone?: "neutral" | "success" | "warning" | "danger";
    value: number;
}) {
    return (
        <StatusBadge tone={tone}>
            {label}: {value.toLocaleString("vi-VN")}
        </StatusBadge>
    );
}
