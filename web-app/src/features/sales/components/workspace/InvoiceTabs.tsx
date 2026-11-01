import { useState } from "react";
import { ChevronDown, Plus } from "lucide-react";

import styles from "../../pages/PosSalePage.module.css";

export interface InvoiceTabView {
    id: string;
    label: number;
    /** Số dòng hàng trong hoá đơn, hiện ở danh sách hoá đơn. */
    itemCount: number;
}

interface InvoiceTabsProps {
    tabs: InvoiceTabView[];
    activeId: string;
    canAdd: boolean;
    onSelect(id: string): void;
    onNew(): void;
    onClose(id: string): void;
}

/** Hoá đơn vừa tạo / vừa chọn có thể nằm ngoài vùng nhìn của thanh tab cuộn ngang. */
function scrollIntoViewIfNeeded(element: HTMLDivElement | null) {
    element?.scrollIntoView({ block: "nearest", inline: "nearest" });
}

export function InvoiceTabs({ tabs, activeId, canAdd, onSelect, onNew, onClose }: InvoiceTabsProps) {
    const [listOpen, setListOpen] = useState(false);

    return (
        <div className={styles.invoiceTabs} aria-label="Hóa đơn">
            <div className={styles.invoiceTabList} role="tablist">
                {tabs.map((tab) => {
                    const active = tab.id === activeId;

                    return (
                        <div
                            className={`${styles.invoiceTab} ${active ? styles.invoiceTabActive : ""}`}
                            key={tab.id}
                            ref={active ? scrollIntoViewIfNeeded : undefined}
                        >
                            <button
                                aria-selected={active}
                                className={styles.invoiceTabLabel}
                                onClick={() => {
                                    if (!active) {
                                        onSelect(tab.id);
                                    }
                                }}
                                role="tab"
                                type="button"
                            >
                                Hóa đơn {tab.label}
                            </button>
                            <button
                                aria-label={`Đóng hóa đơn ${tab.label}`}
                                className={styles.invoiceTabClose}
                                onClick={() => onClose(tab.id)}
                                type="button"
                            >
                                ×
                            </button>
                        </div>
                    );
                })}
            </div>
            <button
                className={styles.squareAction}
                disabled={!canAdd}
                onClick={onNew}
                type="button"
                aria-label="Tạo hóa đơn mới"
                title={canAdd ? "Tạo hóa đơn mới" : "Đã đạt số hóa đơn tối đa"}
            >
                <Plus size={22} aria-hidden="true" />
            </button>
            <div className="relative">
                <button
                    aria-expanded={listOpen}
                    aria-label="Danh sách hóa đơn"
                    className={styles.squareAction}
                    onClick={() => setListOpen((open) => !open)}
                    title="Danh sách hóa đơn"
                    type="button"
                >
                    <ChevronDown size={22} aria-hidden="true" />
                </button>
                {listOpen ? (
                    <>
                        <button
                            aria-label="Đóng danh sách hóa đơn"
                            className="fixed inset-0 z-30 cursor-default"
                            onClick={() => setListOpen(false)}
                            tabIndex={-1}
                            type="button"
                        />
                        <ul className="absolute right-0 z-40 mt-2 grid w-56 gap-1 rounded-xl border border-[#e8eef0] bg-white p-2 shadow-pos-panel">
                            {tabs.map((tab) => (
                                <li key={tab.id}>
                                    <button
                                        className={`flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left text-sm font-bold ${
                                            tab.id === activeId
                                                ? "bg-[#eaf7ec] text-[#159734]"
                                                : "text-[#172317] hover:bg-[#f4faf6]"
                                        }`}
                                        onClick={() => {
                                            setListOpen(false);

                                            if (tab.id !== activeId) {
                                                onSelect(tab.id);
                                            }
                                        }}
                                        type="button"
                                    >
                                        <span>Hóa đơn {tab.label}</span>
                                        <span className="text-xs font-semibold text-[#697684]">
                                            {tab.itemCount > 0 ? `${tab.itemCount} mặt hàng` : "Trống"}
                                        </span>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </>
                ) : null}
            </div>
        </div>
    );
}
