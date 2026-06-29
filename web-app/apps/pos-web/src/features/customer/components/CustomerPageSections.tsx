import {
    AlertCircle,
    ChevronDown,
    Download,
    LoaderCircle,
    Plus,
    RefreshCw,
    Search,
    UsersRound,
    X
} from "lucide-react";
import type { FormEvent, ReactNode } from "react";

import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_COMPACT_BUTTON,
    CUSTOMER_DEFAULT_SORT_BY_TAB,
    CUSTOMER_OUTLINE_BUTTON,
    CUSTOMER_PRIMARY_TABS,
    CUSTOMER_SECONDARY_TABS,
    CUSTOMER_SORT_OPTIONS
} from "../constants/customer-list.constants";
import type { CustomerPrimaryTab } from "../types/customer-list.types";
import type { CustomerSort } from "../types/customer.types";

interface HeaderProps {
    exporting: boolean;
    loading: boolean;
    searchDraft: string;
    onExport: () => void;
    onSearchDraftChange: (value: string) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    onCreate: () => void;
}

export function CustomerPageHeader({
    exporting,
    loading,
    searchDraft,
    onExport,
    onSearchDraftChange,
    onSubmit,
    onCreate
}: HeaderProps) {
    return (
        <form
            className="grid grid-cols-[minmax(340px,1fr)_auto_auto] items-center gap-2.5 max-lg:grid-cols-[minmax(0,1fr)_auto] max-md:grid-cols-1"
            onSubmit={onSubmit}
        >
            <label className="flex h-10 min-w-0 items-center gap-2.5 rounded-lg border border-[#dce5e1] bg-white px-3 text-[#7a8794] focus-within:border-[#159734] focus-within:shadow-[0_0_0_3px_rgba(21,151,52,0.1)]">
                <Search size={17} aria-hidden="true" />
                <input
                    className="min-w-0 flex-1 bg-transparent text-sm font-semibold text-[#172317] outline-none placeholder:text-[#8a96a3]"
                    onChange={(event) => onSearchDraftChange(event.target.value)}
                    placeholder="Tìm tên, SĐT, email hoặc mã khách hàng..."
                    type="search"
                    value={searchDraft}
                />
            </label>
            <button
                className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white hover:bg-[#12862d]`}
                onClick={onCreate}
                type="button"
            >
                <Plus size={17} aria-hidden="true" />
                Thêm khách hàng
            </button>
            <button
                className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_OUTLINE_BUTTON} max-lg:col-span-full max-lg:justify-self-start max-md:col-span-1`}
                disabled={exporting || loading}
                onClick={onExport}
                type="button"
            >
                {exporting ? (
                    <LoaderCircle className="animate-spin" size={16} aria-hidden="true" />
                ) : (
                    <Download size={16} aria-hidden="true" />
                )}
                {exporting ? "Đang xuất..." : "Xuất Excel"}
            </button>
        </form>
    );
}

export function CustomerOuterTabs({
    activeTab,
    children,
    counts,
    onChange
}: {
    activeTab: CustomerPrimaryTab;
    children: ReactNode;
    counts: Record<CustomerPrimaryTab, number>;
    onChange: (tab: CustomerPrimaryTab) => void;
}) {
    return (
        <section className="flex min-h-0 flex-1 flex-col">
            <div className="flex gap-1 overflow-x-auto px-1">
                {CUSTOMER_PRIMARY_TABS.map((tab) => {
                    const active = activeTab === tab.id;
                    return (
                        <button
                            aria-selected={active}
                            className={`relative z-[2] flex h-11 shrink-0 items-center gap-1.5 rounded-t-xl border px-3.5 text-sm font-black transition-colors ${
                                active
                                    ? "-mb-px border-[#cfdad5] border-b-white bg-white text-[#172317]"
                                    : "border-transparent bg-[#edf3f0] text-[#697684] hover:text-[#172317]"
                            }`}
                            key={tab.id}
                            onClick={() => onChange(tab.id)}
                            role="tab"
                            type="button"
                        >
                            {tab.label}
                            <span className="text-[11px] font-bold text-[#8a96a3]">
                                {counts[tab.id].toLocaleString("vi-VN")}
                            </span>
                        </button>
                    );
                })}
            </div>
            <div className="min-h-0 flex-1 rounded-xl rounded-tl-none border border-[#cfdad5] bg-white p-3 shadow-[0_8px_20px_rgba(31,53,43,0.04)]">
                {children}
            </div>
        </section>
    );
}

export function CustomerInnerTabs({
    activeTab,
    activeSegment,
    children,
    onChange
}: {
    activeTab: CustomerPrimaryTab;
    activeSegment: string;
    children: ReactNode;
    onChange: (segment: string) => void;
}) {
    return (
        <section className="mt-2 flex min-h-0 flex-1 flex-col overflow-hidden rounded-[10px] border border-[#d7e4df] bg-[#f7fbfb]">
            <div className="flex gap-1 overflow-x-auto px-2 pt-2">
                {CUSTOMER_SECONDARY_TABS[activeTab].map((segment) => {
                    const active = activeSegment === segment.id;
                    return (
                        <button
                            aria-selected={active}
                            className={`relative z-[2] h-8 shrink-0 rounded-t-lg border px-2.5 text-xs font-extrabold transition-colors ${
                                active
                                    ? "-mb-px border-[#88bca0] border-b-white bg-white text-[#172317]"
                                    : "border-transparent bg-transparent text-[#697684] hover:text-[#172317]"
                            }`}
                            key={segment.id}
                            onClick={() => onChange(segment.id)}
                            role="tab"
                            type="button"
                        >
                            {segment.label}
                        </button>
                    );
                })}
            </div>
            <div className="flex min-h-0 flex-1 flex-col border-t border-[#d7e4df] bg-white">
                {children}
            </div>
        </section>
    );
}

export function CustomerListToolbar({
    bulkSelection,
    selectedCount,
    sort,
    activeTab,
    onBulkSelectionChange,
    onSortChange
}: {
    bulkSelection: boolean;
    selectedCount: number;
    sort: CustomerSort;
    activeTab: CustomerPrimaryTab;
    onBulkSelectionChange: (enabled: boolean) => void;
    onSortChange: (sort: CustomerSort) => void;
}) {
    return (
        <div className="flex min-h-[44px] flex-wrap items-center justify-between gap-2 border-b border-[#e4ece8] px-3 py-2">
            <div className="text-xs font-extrabold text-[#697684]">
                {bulkSelection ? `Đã chọn ${selectedCount} khách hàng` : "Danh sách khách hàng"}
            </div>
            <div className="flex flex-wrap items-center gap-2">
                {bulkSelection && selectedCount > 0 && (
                    <>
                        <button className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON} h-8 px-2.5 text-xs`} type="button">
                            Gửi ưu đãi
                        </button>
                        <button className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON} h-8 px-2.5 text-xs`} type="button">
                            Gắn nhãn
                        </button>
                        <button className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON} h-8 px-2.5 text-xs`} type="button">
                            Xuất danh sách
                        </button>
                    </>
                )}
                <button
                    className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON} h-8 px-2.5 text-xs`}
                    onClick={() => onBulkSelectionChange(!bulkSelection)}
                    type="button"
                >
                    {bulkSelection ? "Bỏ chọn" : "Chọn nhiều"}
                </button>
                <label className="relative flex h-8 items-center rounded-lg border border-[#dce5e1] bg-white text-xs font-extrabold text-[#4c5a67] hover:bg-[#f7fbf8]">
                    <span className="pl-3">Sắp xếp:</span>
                    <select
                        aria-label="Sắp xếp danh sách khách hàng"
                        className="h-full appearance-none bg-transparent py-0 pl-1.5 pr-8 font-extrabold outline-none"
                        onChange={(event) => onSortChange(event.target.value as CustomerSort)}
                        value={sort || CUSTOMER_DEFAULT_SORT_BY_TAB[activeTab]}
                    >
                        {CUSTOMER_SORT_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                    <ChevronDown
                        aria-hidden="true"
                        className="pointer-events-none absolute right-2.5"
                        size={14}
                    />
                </label>
            </div>
        </div>
    );
}

export function CustomerEmptyState({
    type,
    onClearSearch,
    onCreate,
    onGoAll,
    onRetry
}: {
    type: "empty" | "group" | "error";
    onClearSearch: () => void;
    onCreate: () => void;
    onGoAll: () => void;
    onRetry: () => void;
}) {
    const content = {
        empty: {
            icon: UsersRound,
            title: "Chưa có khách hàng nào",
            description: "Bắt đầu bằng cách thêm khách hàng đầu tiên."
        },
        group: {
            icon: Search,
            title: "Chưa có khách hàng phù hợp",
            description: "Hãy thử chọn nhóm khác hoặc tìm kiếm bằng từ khóa khác."
        },
        error: {
            icon: AlertCircle,
            title: "Không tải được danh sách khách hàng",
            description: "Vui lòng kiểm tra kết nối hoặc thử lại sau."
        }
    }[type];
    const Icon = content.icon;

    return (
        <div className="grid min-h-[300px] place-items-center bg-white p-8 text-center">
            <div className="max-w-[420px]">
                <span className="mx-auto grid size-12 place-items-center rounded-full bg-[#f1f5f3] text-[#697684]">
                    <Icon size={22} aria-hidden="true" />
                </span>
                <h2 className="mt-4 text-base font-black text-[#172317]">
                    {content.title}
                </h2>
                <p className="mt-1.5 text-sm font-semibold text-[#697684]">
                    {content.description}
                </p>
                <div className="mt-5 flex flex-wrap justify-center gap-2">
                    {type === "empty" && (
                        <button className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white`} onClick={onCreate} type="button">
                            <Plus size={16} aria-hidden="true" />
                            Thêm khách hàng
                        </button>
                    )}
                    {type === "group" && (
                        <>
                            <button className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white`} onClick={onGoAll} type="button">
                                Chuyển về Tất cả
                            </button>
                            <button className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_OUTLINE_BUTTON}`} onClick={onClearSearch} type="button">
                                <X size={16} aria-hidden="true" />
                                Xóa tìm kiếm
                            </button>
                        </>
                    )}
                    {type === "error" && (
                        <button className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white`} onClick={onRetry} type="button">
                            <RefreshCw size={16} aria-hidden="true" />
                            Thử lại
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

export function CustomerLoadingState() {
    return (
        <div className="space-y-2 bg-white p-3">
            {Array.from({ length: 8 }, (_, index) => (
                <div
                    className="h-[52px] animate-pulse rounded-md bg-[#eef4f1]"
                    key={index}
                />
            ))}
        </div>
    );
}
