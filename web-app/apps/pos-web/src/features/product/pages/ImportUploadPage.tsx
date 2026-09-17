import { Download, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { Link } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { ImportBatchPanel } from "../components/ImportBatchPanel";
import { IMPORT_ITEMS_LIST_REGISTRY } from "../import-batch-registry";
import { importBatchApi } from "../services/importBatch.api";
import type { ImportType } from "../types/import-batch.types";

interface ImportUploadPageProps {
    importType: ImportType;
    title: string;
    description: string;
    templateFileName: string;
    downloadTemplate(): Promise<Blob>;
}

export function ImportUploadPage({ importType, title, description, templateFileName, downloadTemplate }: ImportUploadPageProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const [createdBatchId, setCreatedBatchId] = useState<string | null>(null);

    async function handleDownloadTemplate() {
        try {
            const blob = await downloadTemplate();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.setAttribute("href", url);
            link.setAttribute("download", templateFileName);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (err) {
            console.error("Lỗi tải file mẫu:", err);
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
            const batch = await importBatchApi.create(importType, selectedFile);
            setCreatedBatchId(batch.id);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Tải file lên thất bại.");
        } finally {
            setUploading(false);
        }
    }

    function handleImportAnother() {
        setCreatedBatchId(null);
        setSelectedFile(null);
        setError("");
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    }

    return (
        <section className="grid gap-4">
            <div className="flex items-center justify-between">
                <Link className="text-sm font-black text-[#159734] hover:underline" to={routePaths.importExcelRequests}>
                    ← Quay lại danh sách yêu cầu nhập
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

            {createdBatchId ? (
                <>
                    <div className="flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-700">
                        <span>Đã phân tích xong file Excel — kiểm tra/sửa danh sách bên dưới trước khi gửi duyệt.</span>
                        <button
                            className="shrink-0 rounded-lg border border-emerald-300 bg-white px-3 py-1.5 text-xs font-black text-emerald-700 hover:bg-emerald-100"
                            onClick={handleImportAnother}
                            type="button"
                        >
                            Nhập file khác
                        </button>
                    </div>
                    <ImportBatchPanel batchId={createdBatchId} itemsListRegistry={IMPORT_ITEMS_LIST_REGISTRY} />
                </>
            ) : (
                <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                    <button
                        className="inline-flex h-10 w-fit items-center gap-2 rounded-lg border border-pos-border bg-white px-4 text-sm font-black text-[#536078] hover:bg-neutral-50"
                        onClick={() => void handleDownloadTemplate()}
                        type="button"
                    >
                        <Download size={16} aria-hidden="true" />
                        Tải file mẫu
                    </button>

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
                        <button
                            className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                            disabled={!selectedFile || uploading}
                            onClick={() => void handleUpload()}
                            type="button"
                        >
                            <Upload size={16} aria-hidden="true" />
                            {uploading ? "Đang tải lên & phân tích..." : "Tải lên & Phân tích"}
                        </button>
                    </div>
                </div>
            )}
        </section>
    );
}
