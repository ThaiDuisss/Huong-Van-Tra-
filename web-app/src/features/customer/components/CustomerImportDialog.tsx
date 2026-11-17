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
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import { customerApi } from "../services/customer.api";
import type {
    CustomerImportResponse,
    CustomerImportRow,
    CustomerImportRowResult,
    CustomerImportRowStatus
} from "../types/customer.types";
import {
    CUSTOMER_IMPORT_MAX_ROWS,
    downloadCustomerImportTemplate,
    parseCustomerImportFile
} from "../utils/customer-import.utils";
import { customerErrorMessage } from "../utils/customer-list.utils";

type Phase = "idle" | "checking" | "checked" | "importing" | "done";

const ROW_STATUS_LABELS: Record<CustomerImportRowStatus, string> = {
    VALID: "Hợp lệ",
    IMPORTED: "Đã nhập",
    INVALID: "Lỗi dữ liệu",
    FAILED: "Nhập thất bại"
};

export function CustomerImportDialog({
    onClose,
    onImported
}: {
    onClose: () => void;
    onImported: () => void;
}) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [phase, setPhase] = useState<Phase>("idle");
    const [fileName, setFileName] = useState("");
    const [rows, setRows] = useState<CustomerImportRow[]>([]);
    const [response, setResponse] = useState<CustomerImportResponse | null>(null);
    const [error, setError] = useState("");
    const [dragOver, setDragOver] = useState(false);
    const [onlyErrors, setOnlyErrors] = useState(false);

    const busy = phase === "checking" || phase === "importing";
    const validCount = response?.successCount ?? 0;
    const failedCount = response?.failedCount ?? 0;
    const sourceRows = new Map(rows.map((row) => [row.rowNumber, row]));
    const visibleResults = (response?.results ?? []).filter(
        (result) => !onlyErrors || isErrorStatus(result.status)
    );

    async function selectFile(file: File) {
        setError("");
        setResponse(null);
        setRows([]);
        setOnlyErrors(false);
        setFileName(file.name);
        setPhase("checking");
        try {
            const parsedRows = await parseCustomerImportFile(file);
            setRows(parsedRows);
            setResponse(
                await customerApi.importCustomers({ rows: parsedRows, dryRun: true })
            );
            setPhase("checked");
        } catch (selectError) {
            setError(customerErrorMessage(selectError));
            setPhase("idle");
        }
    }

    async function importValidRows() {
        if (rows.length === 0) return;
        setError("");
        setPhase("importing");
        try {
            const result = await customerApi.importCustomers({ rows, dryRun: false });
            setResponse(result);
            setPhase("done");
            if (result.successCount > 0) onImported();
        } catch (importError) {
            setError(
                `${customerErrorMessage(importError)} Chưa xác định được kết quả nhập, vui lòng kiểm tra lại file trước khi nhập lại.`
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
        <DialogContent className="w-[min(980px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle>Nhập danh sách khách hàng</DialogTitle>
                <DialogDescription>
                    Tải file Excel theo mẫu, tối đa {CUSTOMER_IMPORT_MAX_ROWS} khách hàng mỗi lần.
                    Hệ thống kiểm tra từng dòng trước khi nhập.
                </DialogDescription>
            </DialogHeader>

            <div className="grid max-h-[68vh] gap-4 overflow-y-auto p-6">
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e4ece8] bg-[#f8faf9] px-4 py-3">
                    <p className="min-w-0 flex-1 text-xs font-semibold text-[#536171]">
                        Cột bắt buộc: <strong className="font-black">Tên khách hàng</strong>,{" "}
                        <strong className="font-black">Số điện thoại</strong>. Cột tùy chọn: Email,
                        Ngày sinh (dd/MM/yyyy), Loại khách hàng, Mã khách hàng.
                    </p>
                    <Button onClick={downloadCustomerImportTemplate} size="sm">
                        <Download size={15} aria-hidden="true" />
                        Tải file mẫu
                    </Button>
                </div>

                {phase !== "done" && (
                    <div
                        className={`grid place-items-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors ${
                            dragOver
                                ? "border-[#159734] bg-[#f3fbf5]"
                                : "border-[#cfdcd5] bg-white"
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
                            Hỗ trợ .xlsx, .xls - tối đa 5MB
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
                            accept=".xlsx,.xls"
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

                {phase === "checking" && (
                    <div className="flex items-center gap-2 text-sm font-bold text-[#536171]">
                        <LoaderCircle className="animate-spin" size={16} aria-hidden="true" />
                        Đang đọc và kiểm tra dữ liệu...
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
                                    Hoàn tất nhập dữ liệu
                                </span>
                            )}
                            <StatusBadge>
                                Tổng số dòng: {response.totalRows.toLocaleString("vi-VN")}
                            </StatusBadge>
                            <StatusBadge tone="success">
                                {response.dryRun ? "Hợp lệ" : "Đã nhập"}: {validCount.toLocaleString("vi-VN")}
                            </StatusBadge>
                            <StatusBadge tone={failedCount > 0 ? "danger" : "neutral"}>
                                {response.dryRun ? "Lỗi" : "Không nhập"}: {failedCount.toLocaleString("vi-VN")}
                            </StatusBadge>
                            {failedCount > 0 && (
                                <label className="ml-auto inline-flex cursor-pointer items-center gap-2 text-xs font-bold text-[#536171]">
                                    <input
                                        checked={onlyErrors}
                                        onChange={(event) => setOnlyErrors(event.target.checked)}
                                        type="checkbox"
                                    />
                                    Chỉ hiện dòng lỗi
                                </label>
                            )}
                        </div>

                        {response.dryRun && failedCount > 0 && (
                            <p className="text-xs font-semibold text-[#8a5a12]">
                                {validCount > 0
                                    ? `Các dòng lỗi sẽ bị bỏ qua, chỉ ${validCount.toLocaleString("vi-VN")} dòng hợp lệ được nhập. Bạn có thể sửa file rồi chọn lại để nhập đầy đủ.`
                                    : "Không có dòng nào hợp lệ. Vui lòng sửa file rồi chọn lại."}
                            </p>
                        )}

                        <div className="overflow-auto rounded-xl border border-[#e4ece8]">
                            <Table className="min-w-[760px]">
                                <TableHead>
                                    <TableRow>
                                        {["Dòng", "Tên khách hàng", "Số điện thoại", "Email", "Trạng thái", "Chi tiết"].map(
                                            (header) => (
                                                <TableHeaderCell
                                                    className="sticky top-0 bg-[#f7faf8]"
                                                    key={header}
                                                >
                                                    {header}
                                                </TableHeaderCell>
                                            )
                                        )}
                                    </TableRow>
                                </TableHead>
                                <tbody>
                                    {visibleResults.map((result, index) => (
                                        <ResultRow
                                            key={`${result.rowNumber}-${index}`}
                                            result={result}
                                            source={sourceRows.get(result.rowNumber)}
                                        />
                                    ))}
                                </tbody>
                            </Table>
                        </div>
                    </>
                )}
            </div>

            <DialogFooter>
                <Button disabled={phase === "importing"} onClick={onClose}>
                    {phase === "done" ? "Đóng" : "Hủy"}
                </Button>
                {phase !== "done" && (
                    <Button
                        variant="primary"
                        disabled={phase !== "checked" || validCount === 0}
                        onClick={() => void importValidRows()}
                    >
                        {phase === "importing" && (
                            <LoaderCircle className="animate-spin" size={16} aria-hidden="true" />
                        )}
                        {phase === "importing"
                            ? "Đang nhập..."
                            : validCount > 0
                              ? `Nhập ${validCount.toLocaleString("vi-VN")} khách hàng hợp lệ`
                              : "Nhập khách hàng"}
                    </Button>
                )}
            </DialogFooter>
        </DialogContent>
    );
}

function ResultRow({
    result,
    source
}: {
    result: CustomerImportRowResult;
    source: CustomerImportRow | undefined;
}) {
    const error = isErrorStatus(result.status);
    const cell = "align-top";
    return (
        <TableRow className={error ? "bg-[#fffaf9]" : "bg-white"}>
            <TableCell className={`${cell} font-black`}>{result.rowNumber}</TableCell>
            <TableCell className={`${cell} font-bold`}>{result.fullName || source?.fullName || "-"}</TableCell>
            <TableCell className={`${cell} whitespace-nowrap`}>{result.phone || source?.phone || "-"}</TableCell>
            <TableCell className={cell}>{source?.email || "-"}</TableCell>
            <TableCell className={`${cell} whitespace-nowrap`}>
                <StatusBadge tone={error ? "danger" : "success"}>
                    {ROW_STATUS_LABELS[result.status]}
                </StatusBadge>
            </TableCell>
            <TableCell className={`${cell} ${error ? "font-bold text-[#a43a2a]" : "text-[#697684]"}`}>
                {error
                    ? result.message
                    : result.status === "IMPORTED"
                      ? `Mã khách hàng: ${result.customerCode ?? "-"}`
                      : ""}
            </TableCell>
        </TableRow>
    );
}

function isErrorStatus(status: CustomerImportRowStatus) {
    return status === "INVALID" || status === "FAILED";
}
