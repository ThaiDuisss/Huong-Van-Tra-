import { FileSpreadsheet, LoaderCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import {
    exportTimestamp,
    getErrorMessage,
    saveBlob
} from "../utils/statistic-report.utils";
import styles from "./ReportExportButton.module.css";

interface ReportExportButtonProps {
    disabled?: boolean;
    /** Tên file dự phòng khi response không có Content-Disposition (không gồm đuôi .xlsx). */
    filenamePrefix: string;
    label?: string;
    onExport: () => Promise<Blob>;
}

export function ReportExportButton({
    disabled = false,
    filenamePrefix,
    label = "Xuất Excel",
    onExport
}: ReportExportButtonProps) {
    const inProgressRef = useRef(false);
    const [isExporting, setIsExporting] = useState(false);
    const [toast, setToast] = useState<{
        message: string;
        type: "error" | "success";
    } | null>(null);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => setToast(null), 3200);
        return () => window.clearTimeout(timerId);
    }, [toast]);

    const handleExport = async () => {
        if (inProgressRef.current) {
            return;
        }

        inProgressRef.current = true;
        setIsExporting(true);
        try {
            const blob = await onExport();
            saveBlob(blob, `${filenamePrefix}-${exportTimestamp(new Date())}.xlsx`);
            setToast({ message: "Đã xuất file Excel thành công.", type: "success" });
        } catch (error) {
            console.error("Lỗi xuất file Excel báo cáo", error);
            setToast({
                message: getErrorMessage(error, "Không thể xuất file Excel. Vui lòng thử lại."),
                type: "error"
            });
        } finally {
            inProgressRef.current = false;
            setIsExporting(false);
        }
    };

    return (
        <>
            <button
                aria-busy={isExporting}
                className={styles.exportButton}
                disabled={disabled || isExporting}
                onClick={() => void handleExport()}
                type="button"
            >
                {isExporting ? (
                    <LoaderCircle className={styles.spinIcon} size={18} aria-hidden="true" />
                ) : (
                    <FileSpreadsheet size={18} aria-hidden="true" />
                )}
                {isExporting ? "Đang xuất..." : label}
            </button>

            {toast ? (
                <div
                    aria-live="polite"
                    className={`${styles.toast} ${
                        toast.type === "success" ? styles.toastSuccess : styles.toastError
                    }`}
                    role="status"
                >
                    {toast.message}
                </div>
            ) : null}
        </>
    );
}
