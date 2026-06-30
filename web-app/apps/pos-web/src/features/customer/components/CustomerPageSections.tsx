import {
    AlertCircle,
    ArrowUpDown,
    ChevronDown,
    FileInput,
    FileOutput,
    Filter,
    LoaderCircle,
    Plus,
    RefreshCw,
    Search,
    UsersRound,
    X
} from "lucide-react";
import {
    type FormEvent,
    type ReactNode,
    useState
} from "react";

import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
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
import type {
    CustomerSort,
    CustomerTierCountResponse
} from "../types/customer.types";

interface HeaderProps {
    loading: boolean;
    searchDraft: string;
    onSearchDraftChange: (value: string) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    onCreate: () => void;
}

export function CustomerPageHeader({
    loading,
    searchDraft,
    onSearchDraftChange,
    onSubmit,
    onCreate
}: HeaderProps) {
    const [filterOpen, setFilterOpen] = useState(false);
    const [appliedFilterCount, setAppliedFilterCount] = useState(0);
    const [filterDraft, setFilterDraft] = useState<CustomerFilterDialogState>(
        EMPTY_FILTER_DIALOG_STATE
    );

    function updateFilterDraft(updates: Partial<CustomerFilterDialogState>) {
        setFilterDraft((current) => ({ ...current, ...updates }));
    }

    function toggleStatus(status: string) {
        setFilterDraft((current) => {
            const selected = current.statuses.includes(status);
            return {
                ...current,
                statuses: selected
                    ? current.statuses.filter((item) => item !== status)
                    : [...current.statuses, status]
            };
        });
    }

    function clearFilters() {
        setFilterDraft(EMPTY_FILTER_DIALOG_STATE);
        setAppliedFilterCount(0);
    }

    function applyFilters() {
        setAppliedFilterCount(countActiveFilters(filterDraft));
        setFilterOpen(false);
    }

    return (
        <form
            className="relative flex flex-col gap-4 md:flex-row md:items-center md:justify-between"
            onSubmit={onSubmit}
        >
            <div className="grid min-w-[240px] gap-1">
                <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                    Quản lý khách hàng
                </h1>
                <p className="text-sm text-gray-500">
                    Theo dõi thông tin, hạng thành viên và lịch sử mua gần nhất.
                </p>
            </div>

            <div className="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-2.5">
                <div className="w-full min-w-[240px] md:w-[360px]">
                    <SearchBox
                        value={searchDraft}
                        placeholder="Tìm tên, SĐT, mã khách hàng..."
                        onChange={onSearchDraftChange}
                    />
                </div>
                <button
                    aria-expanded={filterOpen}
                    className={`${CUSTOMER_SECONDARY_ACTION_CLASS} relative`}
                    onClick={() => setFilterOpen((open) => !open)}
                    type="button"
                >
                    <Filter size={17} aria-hidden="true" />
                    Bộ lọc
                    {appliedFilterCount > 0 && (
                        <span className="absolute -right-1.5 -top-1.5 grid min-w-5 place-items-center rounded-full bg-[#159734] px-1.5 text-[10px] font-black leading-5 text-white shadow-sm">
                            {appliedFilterCount}
                        </span>
                    )}
                </button>
                <button className={CUSTOMER_SECONDARY_ACTION_CLASS} type="button">
                    <FileInput size={17} aria-hidden="true" />
                    Nhập dữ liệu
                </button>
                <button className={CUSTOMER_SECONDARY_ACTION_CLASS} type="button">
                    <FileOutput size={17} aria-hidden="true" />
                    Xuất dữ liệu
                </button>
                <button
                    className={CUSTOMER_PRIMARY_ACTION_CLASS}
                    disabled={loading}
                    onClick={onCreate}
                    type="button"
                >
                    {loading ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : <Plus size={17} aria-hidden="true" />}
                    <span className="max-md:hidden">Thêm khách hàng</span>
                    <span className="hidden max-md:inline">Thêm</span>
                </button>
            </div>
            {filterOpen && (
                <CustomerFilterPopover
                    draft={filterDraft}
                    onApply={applyFilters}
                    onCancel={() => setFilterOpen(false)}
                    onClear={clearFilters}
                    onStatusToggle={toggleStatus}
                    onUpdate={updateFilterDraft}
                />
            )}
        </form>
    );
}

type CustomerFilterDialogState = {
    statuses: string[];
    totalSpentFrom: string;
    totalSpentTo: string;
    createdFrom: string;
    createdTo: string;
    recentPurchase: string;
    completedOrdersFrom: string;
    completedOrdersTo: string;
    contactInfo: string;
};

const EMPTY_FILTER_DIALOG_STATE: CustomerFilterDialogState = {
    statuses: [],
    totalSpentFrom: "",
    totalSpentTo: "",
    createdFrom: "",
    createdTo: "",
    recentPurchase: "all",
    completedOrdersFrom: "",
    completedOrdersTo: "",
    contactInfo: "all"
};

const CUSTOMER_STATUS_FILTERS = [
    "Hoạt động",
    "Đã khóa",
    "Ngừng giao dịch"
];

const RECENT_PURCHASE_OPTIONS = [
    { value: "all", label: "Tất cả" },
    { value: "today", label: "Hôm nay" },
    { value: "7-days", label: "7 ngày gần đây" },
    { value: "30-days", label: "30 ngày gần đây" },
    { value: "over-30-days", label: "Hơn 30 ngày chưa mua" },
    { value: "never", label: "Chưa từng mua" }
];

const CONTACT_INFO_OPTIONS = [
    { value: "all", label: "Tất cả" },
    { value: "has-phone", label: "Có số điện thoại" },
    { value: "no-phone", label: "Chưa có số điện thoại" },
    { value: "has-email", label: "Có email" },
    { value: "no-email", label: "Chưa có email" }
];

function CustomerFilterPopover({
    draft,
    onApply,
    onCancel,
    onClear,
    onStatusToggle,
    onUpdate
}: {
    draft: CustomerFilterDialogState;
    onApply: () => void;
    onCancel: () => void;
    onClear: () => void;
    onStatusToggle: (status: string) => void;
    onUpdate: (updates: Partial<CustomerFilterDialogState>) => void;
}) {
    return (
        <div className="fixed inset-0 z-50 grid place-items-center bg-[#172317]/10 p-5">
            <div className="w-[min(760px,calc(100vw-32px))] overflow-hidden rounded-2xl border border-[#dce5e1] bg-white shadow-[0_24px_60px_rgba(24,39,28,0.18)]">
                <div className="flex min-h-16 items-center justify-between border-b border-[#eef2f0] px-6">
                    <h2 className="text-base font-black text-[#172317]">
                        Bộ lọc khách hàng
                    </h2>
                    <button
                        className="text-xs font-bold text-[#159734] hover:text-[#116f28]"
                        onClick={onClear}
                        type="button"
                    >
                        Xóa tất cả
                    </button>
                </div>

                <div className="grid gap-7 p-6 md:grid-cols-2">
                    <div className="grid gap-5 md:grid-rows-3">
                        <FilterField label="Trạng thái">
                            <div className="flex flex-wrap gap-2.5">
                                {CUSTOMER_STATUS_FILTERS.map((status) => {
                                    const active = draft.statuses.includes(status);
                                    return (
                                        <button
                                            aria-pressed={active}
                                            className={`h-9 rounded-full border px-3.5 text-xs font-bold transition-colors ${
                                                active
                                                    ? "border-[#159734] bg-[#eaf8ee] text-[#116f28]"
                                                    : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1]"
                                            }`}
                                            key={status}
                                            onClick={() => onStatusToggle(status)}
                                            type="button"
                                        >
                                            {status}
                                        </button>
                                    );
                                })}
                            </div>
                        </FilterField>

                        <FilterField label="Tổng chi tiêu">
                            <div className="grid grid-cols-2 gap-3">
                                <FilterInput
                                    inputMode="numeric"
                                    placeholder="Từ"
                                    suffix="VND"
                                    value={draft.totalSpentFrom}
                                    onChange={(value) => onUpdate({ totalSpentFrom: value })}
                                />
                                <FilterInput
                                    inputMode="numeric"
                                    placeholder="Đến"
                                    suffix="VND"
                                    value={draft.totalSpentTo}
                                    onChange={(value) => onUpdate({ totalSpentTo: value })}
                                />
                            </div>
                        </FilterField>

                        <FilterField label="Ngày tạo khách hàng">
                            <div className="grid grid-cols-2 gap-3">
                                <FilterInput
                                    placeholder="Từ ngày"
                                    type="date"
                                    value={draft.createdFrom}
                                    onChange={(value) => onUpdate({ createdFrom: value })}
                                />
                                <FilterInput
                                    placeholder="Đến ngày"
                                    type="date"
                                    value={draft.createdTo}
                                    onChange={(value) => onUpdate({ createdTo: value })}
                                />
                            </div>
                        </FilterField>
                    </div>

                    <div className="grid gap-5 md:grid-rows-3">
                        <FilterField label="Lần mua gần nhất">
                            <FilterSelect
                                value={draft.recentPurchase}
                                options={RECENT_PURCHASE_OPTIONS}
                                onChange={(value) => onUpdate({ recentPurchase: value })}
                            />
                        </FilterField>

                        <FilterField label="Số đơn hoàn tất">
                            <div className="grid grid-cols-2 gap-3">
                                <FilterInput
                                    inputMode="numeric"
                                    placeholder="Từ"
                                    value={draft.completedOrdersFrom}
                                    onChange={(value) => onUpdate({ completedOrdersFrom: value })}
                                />
                                <FilterInput
                                    inputMode="numeric"
                                    placeholder="Đến"
                                    value={draft.completedOrdersTo}
                                    onChange={(value) => onUpdate({ completedOrdersTo: value })}
                                />
                            </div>
                        </FilterField>

                        <FilterField label="Thông tin liên hệ">
                            <FilterSelect
                                value={draft.contactInfo}
                                options={CONTACT_INFO_OPTIONS}
                                onChange={(value) => onUpdate({ contactInfo: value })}
                            />
                        </FilterField>
                    </div>
                </div>

                <div className="sticky bottom-0 flex items-center justify-end gap-3 border-t border-[#eef2f0] bg-white px-6 py-4">
                    <button
                        className={CUSTOMER_SECONDARY_ACTION_CLASS}
                        onClick={onCancel}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className={CUSTOMER_PRIMARY_ACTION_CLASS}
                        onClick={onApply}
                        type="button"
                    >
                        Áp dụng bộ lọc
                    </button>
                </div>
            </div>
        </div>
    );
}

function FilterField({
    children,
    label
}: {
    children: ReactNode;
    label: string;
}) {
    return (
        <label className="grid min-h-[86px] content-start gap-2 text-xs font-bold text-[#536171]">
            <span>{label}</span>
            {children}
        </label>
    );
}

function FilterInput({
    inputMode,
    onChange,
    placeholder,
    suffix,
    type = "text",
    value
}: {
    inputMode?: "numeric";
    onChange: (value: string) => void;
    placeholder: string;
    suffix?: string;
    type?: "date" | "text";
    value: string;
}) {
    return (
        <span className="flex h-11 min-w-0 items-center gap-2 rounded-lg border border-[#dce5e1] bg-white px-3 text-sm font-semibold text-[#172317] focus-within:border-[#159734]">
            <input
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-[#8a96a3]"
                inputMode={inputMode}
                onChange={(event) => onChange(event.currentTarget.value)}
                placeholder={placeholder}
                type={type}
                value={value}
            />
            {suffix && (
                <span className="shrink-0 text-xs font-black text-[#8a96a3]">
                    {suffix}
                </span>
            )}
        </span>
    );
}

function FilterSelect({
    onChange,
    options,
    value
}: {
    onChange: (value: string) => void;
    options: Array<{ value: string; label: string }>;
    value: string;
}) {
    return (
        <span className="relative flex h-11 items-center rounded-lg border border-[#dce5e1] bg-white">
            <select
                className="h-full w-full appearance-none bg-transparent px-3 pr-9 text-sm font-semibold text-[#172317] outline-none"
                onChange={(event) => onChange(event.currentTarget.value)}
                value={value}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
            <ChevronDown
                aria-hidden="true"
                className="pointer-events-none absolute right-3 text-[#8a96a3]"
                size={15}
            />
        </span>
    );
}

function countActiveFilters(draft: CustomerFilterDialogState) {
    return [
        draft.statuses.length > 0,
        Boolean(draft.totalSpentFrom || draft.totalSpentTo),
        Boolean(draft.createdFrom || draft.createdTo),
        draft.recentPurchase !== "all",
        Boolean(draft.completedOrdersFrom || draft.completedOrdersTo),
        draft.contactInfo !== "all"
    ].filter(Boolean).length;
}

const CUSTOMER_SECONDARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8]";
const CUSTOMER_PRIMARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d] disabled:cursor-not-allowed disabled:opacity-70";

const TIER_CHIPS = [
    { code: "ALL", name: "Tất cả", customerCount: 0 },
    { code: "UNASSIGNED", name: "Chưa phân hạng", customerCount: 0 }
];

export function CustomerTierChips({
    counts
}: {
    counts: CustomerTierCountResponse[];
}) {
    const chips = counts.length ? counts : TIER_CHIPS;

    return (
        <section className="rounded-2xl bg-white p-3 shadow-pos-card">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <nav
                    aria-label="Lọc theo hạng khách hàng"
                    className="flex min-w-0 flex-1 gap-2 overflow-x-auto"
                >
                    {chips.map((chip) => (
                        <button
                            aria-pressed={chip.code === "ALL"}
                            className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-xs font-extrabold transition-colors ${
                                chip.code === "ALL"
                                    ? "border-[#159734] bg-[#eaf8ee] text-[#116f28] shadow-[0_1px_2px_rgba(21,151,52,0.08)]"
                                    : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1] hover:text-[#172317]"
                            }`}
                            key={chip.code}
                            type="button"
                        >
                            <span>{chip.name}</span>
                            <span className={chip.code === "ALL" ? "text-[#159734]" : "text-[#8a96a3]"}>
                                {chip.customerCount.toLocaleString("vi-VN")}
                            </span>
                        </button>
                    ))}
                </nav>
                <button className={CUSTOMER_SECONDARY_ACTION_CLASS} type="button">
                    <ArrowUpDown size={17} aria-hidden="true" />
                    Sắp xếp
                </button>
            </div>
        </section>
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
