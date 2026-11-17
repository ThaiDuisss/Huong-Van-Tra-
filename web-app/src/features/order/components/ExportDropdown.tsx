import {
    ChevronDown,
    FileSpreadsheet,
    LoaderCircle
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "../../../components/ui/Button";
import styles from "./ExportDropdown.module.css";

export type ExportMode = "all" | "filtered";

interface ExportDropdownProps {
    allDescription: string;
    allFilenamePrefix: string;
    allLabel: string;
    emptyFilteredMessage: string;
    fallbackErrorMessage: string;
    filteredDescription: string;
    filteredFilenamePrefix: string;
    hasNoFilteredData: boolean;
    onExport: (mode: ExportMode) => Promise<Blob>;
    successMessage: string;
}

function formatExportTimestamp(date: Date) {
    const pad = (value: number) => value.toString().padStart(2, "0");

    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(
        date.getDate()
    )}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(
        date.getSeconds()
    )}`;
}

function getDownloadFilename(
    blob: Blob,
    mode: ExportMode,
    filteredFilenamePrefix: string,
    allFilenamePrefix: string
) {
    const filename = (blob as Blob & { name?: unknown }).name;
    if (typeof filename === "string" && filename.trim()) {
        return filename;
    }

    const prefix =
        mode === "filtered" ? filteredFilenamePrefix : allFilenamePrefix;
    return `${prefix}-${formatExportTimestamp(new Date())}.xlsx`;
}

function getExportErrorMessage(error: unknown, fallback: string) {
    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string" &&
        error.message.trim()
    ) {
        return error.message;
    }

    return fallback;
}

export function ExportDropdown({
    allDescription,
    allFilenamePrefix,
    allLabel,
    emptyFilteredMessage,
    fallbackErrorMessage,
    filteredDescription,
    filteredFilenamePrefix,
    hasNoFilteredData,
    onExport,
    successMessage
}: ExportDropdownProps) {
    const menuRef = useRef<HTMLDivElement | null>(null);
    const exportInProgressRef = useRef(false);
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [isExporting, setIsExporting] = useState(false);
    const [toast, setToast] = useState<{
        message: string;
        type: "error" | "success";
    } | null>(null);

    useEffect(() => {
        if (!isMenuOpen) {
            return;
        }

        const handleDocumentMouseDown = (event: MouseEvent) => {
            if (
                menuRef.current &&
                !menuRef.current.contains(event.target as Node)
            ) {
                setIsMenuOpen(false);
            }
        };

        document.addEventListener("mousedown", handleDocumentMouseDown);
        return () =>
            document.removeEventListener("mousedown", handleDocumentMouseDown);
    }, [isMenuOpen]);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => setToast(null), 3200);
        return () => window.clearTimeout(timerId);
    }, [toast]);

    const handleExport = async (mode: ExportMode) => {
        if (exportInProgressRef.current) {
            return;
        }

        setIsMenuOpen(false);
        if (mode === "filtered" && hasNoFilteredData) {
            setToast({ message: emptyFilteredMessage, type: "error" });
            return;
        }

        exportInProgressRef.current = true;
        setIsExporting(true);
        try {
            const blob = await onExport(mode);
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            try {
                link.href = url;
                link.download = getDownloadFilename(
                    blob,
                    mode,
                    filteredFilenamePrefix,
                    allFilenamePrefix
                );
                document.body.appendChild(link);
                link.click();
            } finally {
                link.remove();
                URL.revokeObjectURL(url);
            }
            setToast({ message: successMessage, type: "success" });
        } catch (error) {
            console.error("Lỗi xuất file Excel", error);
            setToast({
                message: getExportErrorMessage(error, fallbackErrorMessage),
                type: "error"
            });
        } finally {
            exportInProgressRef.current = false;
            setIsExporting(false);
        }
    };

    return (
        <>
            <div className={styles.exportMenuWrap} ref={menuRef}>
                <Button
                    aria-busy={isExporting}
                    aria-expanded={isMenuOpen}
                    disabled={isExporting}
                    onClick={() => setIsMenuOpen((open) => !open)}
                    size="lg"
                >
                    {isExporting ? (
                        <LoaderCircle
                            className={styles.spinIcon}
                            size={18}
                            aria-hidden="true"
                        />
                    ) : (
                        <FileSpreadsheet size={18} aria-hidden="true" />
                    )}
                    {isExporting ? "Đang xuất..." : "Xuất file"}
                    <ChevronDown size={16} aria-hidden="true" />
                </Button>
                {isMenuOpen ? (
                    <div className={styles.exportMenu} role="menu">
                        <button
                            disabled={isExporting}
                            onClick={() => void handleExport("filtered")}
                            role="menuitem"
                            type="button"
                        >
                            <strong>Xuất theo bộ lọc hiện tại</strong>
                            <span>{filteredDescription}</span>
                        </button>
                        <button
                            disabled={isExporting}
                            onClick={() => void handleExport("all")}
                            role="menuitem"
                            type="button"
                        >
                            <strong>{allLabel}</strong>
                            <span>{allDescription}</span>
                        </button>
                    </div>
                ) : null}
            </div>

            {toast ? (
                <div
                    aria-live="polite"
                    className={`${styles.toast} ${
                        toast.type === "success"
                            ? styles.toastSuccess
                            : styles.toastError
                    }`}
                    role="status"
                >
                    {toast.message}
                </div>
            ) : null}
        </>
    );
}
