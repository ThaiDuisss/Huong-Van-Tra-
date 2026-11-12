import { Download, Upload } from "lucide-react";
import type { ComponentType } from "react";
import { useRef, useState } from "react";
import { Link } from "react-router-dom";

import { Button } from "../../../components/ui/Button";
import { routePaths } from "../../../routes/path";
import { ImportBatchPanel, type ImportItemsListProps } from "../components/ImportBatchPanel";
import { IMPORT_ITEMS_LIST_REGISTRY } from "../import-batch-registry";
import { importBatchApi as defaultImportBatchApi, type ImportBatchApi } from "../services/importBatch.api";
import type { ImportPreviewResponse, ImportType } from "../types/import-batch.types";

interface ImportUploadPageProps {
    importType: ImportType;
    title: string;
    description: string;
    templateFileName: string;
    downloadTemplate(): Promise<Blob>;
    /** API import-batch dùng cho trang này — mặc định trỏ Catalog, service khác truyền bộ riêng qua createImportBatchApi(). */
    api?: ImportBatchApi;
    /** Registry hiển thị danh sách dòng theo loại import — mặc định là registry của Catalog (Sản phẩm/Mặt hàng). */
    itemsListRegistry?: Partial<Record<ImportType, ComponentType<ImportItemsListProps>>>;
    /** Route "Quay lại danh sách yêu cầu nhập" — mặc định là trang danh sách của Catalog. */
    backTo?: string;
    /** Nhãn nút quay lại — mặc định là "← Quay lại danh sách yêu cầu nhập" */
    backLabel?: string;
}

export function ImportUploadPage({
    importType,
    title,
    description,
    templateFileName,
    downloadTemplate,
    api = defaultImportBatchApi,
    itemsListRegistry = IMPORT_ITEMS_LIST_REGISTRY,
    backTo = routePaths.importExcelRequests,
    backLabel = "← Quay lại danh sách yêu cầu nhập"
}: ImportUploadPageProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const [previewData, setPreviewData] = useState<ImportPreviewResponse | null>(null);

    async function handleDownloadTemplate() {
        try {
            const blob = await downloadTemplate();
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = templateFileName;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
        } catch {
            setError("Không thể tải file mẫu.");
        }
    }

    async function handleUpload() {
        if (!selectedFile) {
            setError("Vui lòng chọn file Excel trước.");
            return;
        }
        setError("");
        setUploading(true);
        try {
            const preview = await api.preview(importType, selectedFile);
            setPreviewData(preview);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Tải file lên thất bại.");
        } finally {
            setUploading(false);
        }
    }

    function handleImportAnother() {
        setPreviewData(null);
        setSelectedFile(null);
        setError("");
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    }

    return (
        <section className="grid gap-4">
            <div className="flex items-center justify-between">
                <Link className="text-sm font-black text-[#159734] hover:underline" to={backTo}>
                    {backLabel}
                </Link>
            </div>

            <header className="grid gap-1 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <h1 className="text-2xl font-black text-[#172317]">{title}</h1>
                <p className="text-sm font-semibold text-[#536078]">{description}</p>
            </header>

            {error ? (
                <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-700">
                    {error}
                </div>
            ) : null}

            {previewData ? (
                <>
                    <div className="flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-800">
                        <span>Đã phân tích xong file Excel ({previewData.totalRows} dòng, {previewData.validRows} hợp lệ, {previewData.errorRows} lỗi) — kiểm tra/sửa danh sách bên dưới trước khi lưu nháp hoặc gửi duyệt.</span>
                        <Button onClick={handleImportAnother} size="sm">
                            Nhập file khác
                        </Button>
                    </div>
                    <ImportBatchPanel
                        api={api}
                        itemsListRegistry={itemsListRegistry}
                        previewData={previewData}
                        sourceFile={selectedFile}
                    />
                </>
            ) : (
                <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                    <Button className="w-fit" onClick={() => void handleDownloadTemplate()}>
                        <Download size={16} aria-hidden="true" />
                        Tải file mẫu
                    </Button>

                    <label className="grid gap-1.5">
                        <span className="text-xs font-bold text-[#536078]">Chọn file Excel (.xlsx)</span>
                        <input
                            ref={fileInputRef}
                            accept=".xlsx,.xls"
                            className="h-10 w-full rounded-xl border border-pos-border bg-white px-3 text-sm outline-none file:mr-3 file:h-full file:rounded-lg file:border-0 file:bg-[#eaf7ec] file:px-3 file:text-sm file:font-bold file:text-[#159734]"
                            onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
                            type="file"
                        />
                    </label>

                    <div className="flex justify-end">
                        <Button disabled={!selectedFile || uploading} onClick={() => void handleUpload()} variant="primary">
                            <Upload size={16} aria-hidden="true" />
                            {uploading ? "Đang tải lên & phân tích..." : "Tải lên & Phân tích"}
                        </Button>
                    </div>
                </div>
            )}
        </section>
    );
}
