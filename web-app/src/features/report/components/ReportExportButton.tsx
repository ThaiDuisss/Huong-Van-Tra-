import { FileSpreadsheet, LoaderCircle } from "lucide-react";
import { useRef, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { toast } from "../../../components/ui/Toast";
import {
    exportTimestamp,
    getErrorMessage,
    saveBlob
} from "../utils/statistic-report.utils";

const TOAST_DURATION_MS = 3200;

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

    const handleExport = async () => {
        if (inProgressRef.current) {
            return;
        }

        inProgressRef.current = true;
        setIsExporting(true);
        try {
            const blob = await onExport();
            saveBlob(blob, `${filenamePrefix}-${exportTimestamp(new Date())}.xlsx`);
            toast.success("Đã xuất file Excel thành công.", TOAST_DURATION_MS);
        } catch (error) {
            console.error("Lỗi xuất file Excel báo cáo", error);
            toast.error(
                getErrorMessage(error, "Không thể xuất file Excel. Vui lòng thử lại."),
                TOAST_DURATION_MS
            );
        } finally {
            inProgressRef.current = false;
            setIsExporting(false);
        }
    };

    return (
        <Button
            aria-busy={isExporting}
            disabled={disabled || isExporting}
            onClick={() => void handleExport()}
            variant="primary"
        >
            {isExporting ? (
                <LoaderCircle className="animate-spin" size={18} aria-hidden="true" />
            ) : (
                <FileSpreadsheet size={18} aria-hidden="true" />
            )}
            {isExporting ? "Đang xuất..." : label}
        </Button>
    );
}
