import { ChevronDown, Plus } from "lucide-react";

import styles from "../../pages/PosSalePage.module.css";

interface InvoiceTabsProps {
    onCloseActive?(): void;
}

export function InvoiceTabs({ onCloseActive }: InvoiceTabsProps) {
    return (
        <div className={styles.invoiceTabs} aria-label="Hóa đơn">
            <button
                className={styles.invoiceTab}
                type="button"
                aria-current="page"
            >
                <span>Hóa đơn 1</span>
                <span
                    role="button"
                    tabIndex={0}
                    aria-label="Đóng hóa đơn"
                    onClick={(event) => {
                        event.stopPropagation();
                        onCloseActive?.();
                    }}
                    onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            event.stopPropagation();
                            onCloseActive?.();
                        }
                    }}
                >
                    ×
                </span>
            </button>
            <button
                className={styles.squareAction}
                type="button"
                aria-label="Tạo hóa đơn mới"
                title="Tạo hóa đơn mới"
            >
                <Plus size={22} aria-hidden="true" />
            </button>
            <button
                className={styles.squareAction}
                type="button"
                aria-label="Danh sách hóa đơn"
                title="Danh sách hóa đơn"
            >
                <ChevronDown size={22} aria-hidden="true" />
            </button>
        </div>
    );
}
