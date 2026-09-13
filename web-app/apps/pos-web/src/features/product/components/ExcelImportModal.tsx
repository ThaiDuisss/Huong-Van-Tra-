import {
    AlertTriangle,
    CheckCircle,
    Download,
    FileText,
    Loader2,
    Upload,
    X
} from "lucide-react";
import { useEffect, useState } from "react";
import * as XLSX from "xlsx";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";

interface ExcelImportModalProps {
    isOpen: boolean;
    onClose(): void;
    title: string;
    description: string;
    onDownloadTemplate(): void;
    onImport(file: File): Promise<void>;
    validateRow(row: any, index?: number, allRows?: any[]): { isValid: boolean; error?: string };
}

interface ParsedRow {
    row: any;
    isValid: boolean;
    error?: string;
}

export function ExcelImportModal({
    isOpen,
    onClose,
    title,
    description,
    onDownloadTemplate,
    onImport,
    validateRow
}: ExcelImportModalProps) {
    const [file, setFile] = useState<File | null>(null);
    const [fileError, setFileError] = useState<string | null>(null);
    const [uploadTime, setUploadTime] = useState<string | null>(null);
    const [dragOver, setDragOver] = useState(false);

    // Preview state
    const [headers, setHeaders] = useState<string[]>([]);
    const [parsedRows, setParsedRows] = useState<ParsedRow[]>([]);
    const [totalRecords, setTotalRecords] = useState(0);

    // Import status
    const [importing, setImporting] = useState(false);
    const [importProgress, setImportProgress] = useState(0);
    const [importResult, setImportResult] = useState<{
        total: number;
        success: number;
        failure: number;
    } | null>(null);

    // Reset state on open/close
    useEffect(() => {
        if (!isOpen) {
            setFile(null);
            setFileError(null);
            setUploadTime(null);
            setHeaders([]);
            setParsedRows([]);
            setTotalRecords(0);
            setImporting(false);
            setImportProgress(0);
            setImportResult(null);
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const handleFileSelect = (selectedFile: File) => {
        setFileError(null);
        setImportResult(null);

        const ext = selectedFile.name.split(".").pop()?.toLowerCase();
        if (ext !== "xlsx" && ext !== "xls") {
            setFileError("Định dạng file không hợp lệ! Vui lòng chọn file .xlsx hoặc .xls");
            return;
        }

        setFile(selectedFile);
        setUploadTime(new Date().toLocaleTimeString());

        // Parse file
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = new Uint8Array(e.target?.result as ArrayBuffer);
                const workbook = XLSX.read(data, { type: "array" });
                const sheetName = workbook.SheetNames[0];
                const worksheet = workbook.Sheets[sheetName];

                // Get headers
                const rawRows = XLSX.utils.sheet_to_json<any[]>(worksheet, { header: 1 });
                const parsedHeaders = (rawRows[0] || []).map(h => String(h).trim());
                setHeaders(parsedHeaders);

                // Get object rows
                const dataRows = XLSX.utils.sheet_to_json<any>(worksheet);
                const validated: ParsedRow[] = dataRows.map((row, idx) => {
                    const validation = validateRow(row, idx, dataRows);
                    return {
                        row,
                        isValid: validation.isValid,
                        error: validation.error
                    };
                });

                setParsedRows(validated);
                setTotalRecords(validated.length);
            } catch (err) {
                console.error(err);
                setFileError("Lỗi đọc file Excel. Vui lòng kiểm tra lại định dạng file!");
                setFile(null);
            }
        };
        reader.readAsArrayBuffer(selectedFile);
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(true);
    };

    const handleDragLeave = () => {
        setDragOver(false);
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        const droppedFile = e.dataTransfer.files?.[0];
        if (droppedFile) {
            handleFileSelect(droppedFile);
        }
    };

    const handleStartImport = async () => {
        if (!file) return;

        setImporting(true);
        setImportProgress(0);

        try {
            // Simulate progress to look polished
            const interval = setInterval(() => {
                setImportProgress((prev) => {
                    const next = prev + Math.floor(Math.random() * 15) + 5;
                    return next >= 90 ? 90 : next;
                });
            }, 300);

            await onImport(file);
            clearInterval(interval);
            setImportProgress(100);

            // Compute statistics
            const successCount = parsedRows.filter((r) => r.isValid).length;
            const failureCount = parsedRows.length - successCount;

            setImportResult({
                total: parsedRows.length,
                success: successCount,
                failure: failureCount
            });
        } catch (err) {
            console.error(err);
            const errorMessage =
                err instanceof Error && err.message
                    ? err.message
                    : "Có lỗi xảy ra trong quá trình nhập dữ liệu!";
            setFileError(errorMessage);
        } finally {
            setImporting(false);
        }
    };

    const validCount = parsedRows.filter((r) => r.isValid).length;
    const invalidCount = parsedRows.length - validCount;
    const formatSize = (bytes: number) => {
        if (bytes === 0) return "0 Bytes";
        const k = 1024;
        const sizes = ["Bytes", "KB", "MB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-0 md:p-6">
            <div className="flex h-full w-full flex-col bg-white shadow-2xl md:h-[90vh] md:max-h-[850px] md:max-w-[950px] md:rounded-3xl overflow-hidden">
                {/* Header */}
                <header className="flex items-center justify-between border-b border-neutral-100 px-6 py-5">
                    <div>
                        <h2 className="text-xl font-bold text-gray-800">{title}</h2>
                        <p className="mt-1 text-sm text-neutral-500">{description}</p>
                    </div>
                    {!importing && (
                        <button
                            onClick={onClose}
                            className="rounded-xl p-2 hover:bg-neutral-100 text-neutral-400 hover:text-neutral-600"
                        >
                            <X size={20} />
                        </button>
                    )}
                </header>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Template Card */}
                    {!file && !importResult && (
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between rounded-2xl border border-dashed border-pos-border bg-neutral-50/50 p-5 gap-4">
                            <div>
                                <h3 className="font-bold text-gray-800 flex items-center gap-2">
                                    <FileText size={18} className="text-hvt-green-600" />
                                    Tệp mẫu
                                </h3>
                                <p className="mt-1 text-sm text-neutral-500">
                                    Tải xuống tệp Excel mẫu để nhập dữ liệu đúng định dạng.
                                </p>
                            </div>
                            <div className="self-start sm:self-center">
                                <ActionButton variant="secondary" onClick={onDownloadTemplate}>
                                    <Download size={16} />
                                    Tải file mẫu
                                </ActionButton>
                            </div>
                        </div>
                    )}

                    {/* File Dropzone */}
                    {!file && !importResult && (
                        <div
                            onDragOver={handleDragOver}
                            onDragLeave={handleDragLeave}
                            onDrop={handleDrop}
                            onClick={() => {
                                const input = document.createElement("input");
                                input.type = "file";
                                input.accept = ".xlsx,.xls";
                                input.onchange = (e) => {
                                    const selected = (e.target as HTMLInputElement).files?.[0];
                                    if (selected) handleFileSelect(selected);
                                };
                                input.click();
                            }}
                            className={`flex flex-col items-center justify-center rounded-2xl border-2 border-dashed p-10 cursor-pointer transition-all ${
                                dragOver
                                    ? "border-hvt-green-600 bg-hvt-green-50/20"
                                    : "border-neutral-200 hover:border-hvt-green-600/50"
                            }`}
                        >
                            <div className="rounded-full bg-hvt-green-50 p-4 text-hvt-green-600">
                                <Upload size={28} />
                            </div>
                            <p className="mt-4 font-bold text-gray-700">
                                Kéo thả file Excel vào đây hoặc click để chọn file
                            </p>
                            <p className="mt-1 text-xs text-neutral-400">
                                Hỗ trợ định dạng .xlsx, .xls
                            </p>
                        </div>
                    )}

                    {/* File Error */}
                    {fileError && (
                        <div className="rounded-2xl bg-red-50 p-4 text-sm font-semibold text-red-600 flex items-center gap-2">
                            <AlertTriangle size={18} />
                            {fileError}
                        </div>
                    )}

                    {/* File Selected Card */}
                    {file && !importResult && (
                        <div className="rounded-2xl border border-neutral-200 p-5 space-y-4">
                            <div className="flex items-start justify-between">
                                <div className="flex items-center gap-3">
                                    <div className="rounded-xl bg-hvt-green-50 p-3 text-hvt-green-600">
                                        <FileText size={24} />
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-gray-800">{file.name}</h4>
                                        <p className="text-xs text-neutral-500">
                                            Kích thước: {formatSize(file.size)} • Tải lên lúc: {uploadTime}
                                        </p>
                                    </div>
                                </div>
                                {!importing && (
                                    <button
                                        onClick={() => {
                                            setFile(null);
                                            setHeaders([]);
                                            setParsedRows([]);
                                            setTotalRecords(0);
                                        }}
                                        className="rounded-lg p-1.5 hover:bg-neutral-100 text-neutral-400 hover:text-neutral-600"
                                    >
                                        <X size={16} />
                                    </button>
                                )}
                            </div>

                            {/* Summary Stats */}
                            <div className="flex gap-4 text-sm">
                                <div className="rounded-xl bg-hvt-green-50/50 px-4 py-2 text-hvt-green-700 font-semibold">
                                    Hợp lệ: {validCount}
                                </div>
                                {invalidCount > 0 && (
                                    <div className="rounded-xl bg-red-50 px-4 py-2 text-red-600 font-semibold">
                                        Lỗi: {invalidCount}
                                    </div>
                                )}
                            </div>

                            {/* Preview Table */}
                            {parsedRows.length > 0 && (
                                <div className="space-y-2">
                                    <div className="flex justify-between text-xs text-neutral-500 font-semibold">
                                        <span>Đã đọc: {totalRecords} bản ghi</span>
                                        <span>Đang hiển thị: 20 bản ghi đầu tiên</span>
                                    </div>
                                    <div className="overflow-x-auto rounded-xl border border-neutral-200 max-h-[300px]">
                                        <table className="w-full border-collapse text-left text-sm text-neutral-600">
                                            <thead className="sticky top-0 bg-neutral-50 border-b border-neutral-200">
                                                <tr>
                                                    <th className="px-4 py-3 font-bold text-gray-700 whitespace-nowrap">
                                                        Trạng thái kiểm tra
                                                    </th>
                                                    {headers.map((h, i) => (
                                                        <th key={i} className="px-4 py-3 font-bold text-gray-700 whitespace-nowrap">
                                                            {h}
                                                        </th>
                                                    ))}
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-neutral-100">
                                                {parsedRows.slice(0, 20).map((r, rowIdx) => (
                                                    <tr key={rowIdx} className="hover:bg-neutral-50/30">
                                                        <td className="px-4 py-3 whitespace-nowrap">
                                                            {r.isValid ? (
                                                                <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-hvt-green-700">
                                                                    <CheckCircle size={14} />
                                                                    Hợp lệ
                                                                </span>
                                                            ) : (
                                                                <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-red-600">
                                                                    <AlertTriangle size={14} />
                                                                    {r.error || "Không hợp lệ"}
                                                                </span>
                                                            )}
                                                        </td>
                                                        {headers.map((h, colIdx) => (
                                                            <td key={colIdx} className="px-4 py-3 whitespace-nowrap max-w-[200px] truncate">
                                                                {r.row[h] !== undefined ? String(r.row[h]) : ""}
                                                            </td>
                                                        ))}
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Progress Loader */}
                    {importing && (
                        <div className="flex flex-col items-center justify-center py-10 space-y-4">
                            <Loader2 className="animate-spin text-hvt-green-600" size={36} />
                            <div className="text-center">
                                <h4 className="font-bold text-gray-800">
                                    Đang nhập dữ liệu... {Math.round((importProgress / 100) * totalRecords)} / {totalRecords} bản ghi
                                </h4>
                                <p className="text-sm text-neutral-500 mt-1">
                                    Vui lòng không đóng cửa sổ này trong khi hệ thống đang xử lý.
                                </p>
                            </div>
                            <div className="w-full max-w-md bg-neutral-100 h-2.5 rounded-full overflow-hidden">
                                <div
                                    className="bg-hvt-green-600 h-full transition-all duration-300"
                                    style={{ width: `${importProgress}%` }}
                                ></div>
                            </div>
                        </div>
                    )}

                    {/* Import Result Screen */}
                    {importResult && (
                        <div className="flex flex-col items-center justify-center py-10 space-y-6 text-center">
                            <div className="rounded-full bg-hvt-green-50 p-6 text-hvt-green-600">
                                <CheckCircle size={48} />
                            </div>
                            <div>
                                <h3 className="text-2xl font-bold text-gray-800">Import thành công</h3>
                                <p className="text-sm text-neutral-500 mt-1">
                                    Hệ thống đã hoàn tất xử lý tệp dữ liệu.
                                </p>
                            </div>

                            <div className="grid grid-cols-3 gap-6 max-w-sm w-full border border-neutral-200 rounded-2xl p-5 bg-neutral-50/50">
                                <div>
                                    <span className="text-2xl font-black text-gray-800">{importResult.total}</span>
                                    <p className="text-xs text-neutral-500 mt-1 font-bold">Tổng số</p>
                                </div>
                                <div>
                                    <span className="text-2xl font-black text-hvt-green-600">{importResult.success}</span>
                                    <p className="text-xs text-neutral-500 mt-1 font-bold">Thành công</p>
                                </div>
                                <div>
                                    <span className="text-2xl font-black text-red-600">{importResult.failure}</span>
                                    <p className="text-xs text-neutral-500 mt-1 font-bold">Thất bại</p>
                                </div>
                            </div>

                            {importResult.failure > 0 && (
                                <div className="text-sm text-red-600 font-semibold bg-red-50 px-4 py-3 rounded-xl max-w-md flex gap-2 text-left">
                                    <AlertTriangle size={18} className="shrink-0" />
                                    Có {importResult.failure} bản ghi chứa thông tin không hợp lệ (như thiếu mã hoặc tên bắt buộc) nên hệ thống đã tự động bỏ qua.
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <footer className="flex justify-end gap-3 border-t border-neutral-100 px-6 py-4 bg-neutral-50">
                    {!importing && !importResult ? (
                        <>
                            <button
                                onClick={onClose}
                                className="h-11 rounded-lg px-5 text-sm font-black text-neutral-500 hover:bg-neutral-100 transition-all cursor-pointer"
                            >
                                Hủy
                            </button>
                            {file && (
                                <button
                                    onClick={() => {
                                        setFile(null);
                                        setHeaders([]);
                                        setParsedRows([]);
                                        setTotalRecords(0);
                                    }}
                                    className="h-11 rounded-lg px-5 text-sm font-black border border-[#dfe6ee] text-[#172317] bg-white hover:bg-neutral-50 transition-all cursor-pointer"
                                >
                                    Chọn file khác
                                </button>
                            )}
                            <button
                                onClick={handleStartImport}
                                disabled={!file || validCount === 0}
                                className={`h-11 rounded-lg px-5 text-sm font-black text-white transition-all ${
                                    !file || validCount === 0
                                        ? "bg-neutral-200 cursor-not-allowed"
                                        : "border-0 bg-[#07883a] shadow-[0_6px_16px_rgba(7,136,58,0.18)] hover:opacity-90 cursor-pointer"
                                }`}
                            >
                                Nhập dữ liệu
                            </button>
                        </>
                    ) : (
                        importResult && (
                            <button
                                onClick={onClose}
                                className="h-11 rounded-lg border-0 bg-[#07883a] px-6 text-sm font-black text-white shadow-[0_6px_16px_rgba(7,136,58,0.18)] hover:opacity-90 transition-all cursor-pointer"
                            >
                                Hoàn tất
                            </button>
                        )
                    )}
                </footer>
            </div>
        </div>
    );
}
