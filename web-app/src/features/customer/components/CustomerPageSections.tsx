import {
    AlertCircle,
    ArrowUpDown,
    FileInput,
    FileOutput,
    Filter,
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

import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../components/ui/select";
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
    CustomerStatus,
    CustomerTierCountResponse
} from "../types/customer.types";

export type CustomerRecentPurchaseFilter =
    | "all"
    | "today"
    | "7-days"
    | "30-days"
    | "over-30-days"
    | "never";

export type CustomerHeaderFilterState = {
    status: CustomerStatus | "";
    minTotalSpent: string;
    maxTotalSpent: string;
    createdFrom: string;
    createdTo: string;
    recentPurchase: CustomerRecentPurchaseFilter;
};

interface HeaderProps {
    title?: string;
    onCreate: () => void;
    /** Bỏ trống để ẩn nút nhập dữ liệu (màn POS). */
    onImport?: () => void;
}

export function CustomerPageHeader({
    title = "Quản lý khách hàng",
    onCreate,
    onImport
}: HeaderProps) {
    return (
        <section
            className="relative flex flex-col gap-4 md:flex-row md:items-center md:justify-between"
        >
            <div className="grid min-w-[240px] gap-1">
                <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                    {title}
                </h1>
                <p className="text-sm text-gray-500">
                    Theo dõi thông tin, hạng thành viên và lịch sử mua gần nhất.
                </p>
            </div>

            <div className="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-2.5">
                {onImport ? (
                    <>
                        <button
                            className={CUSTOMER_SECONDARY_ACTION_CLASS}
                            onClick={onImport}
                            title="Nhập danh sách khách hàng từ file Excel"
                            type="button"
                        >
                            <FileInput size={17} aria-hidden="true" />
                            Nhập dữ liệu
                        </button>
                        <button
                            className={CUSTOMER_SECONDARY_ACTION_CLASS}
                            disabled
                            title="Chưa có API xuất dữ liệu"
                            type="button"
                        >
                            <FileOutput size={17} aria-hidden="true" />
                            Xuất dữ liệu
                        </button>
                    </>
                ) : null}
                <button
                    className={CUSTOMER_PRIMARY_ACTION_CLASS}
                    onClick={onCreate}
                    type="button"
                >
                    <Plus size={17} aria-hidden="true" />
                    <span className="max-md:hidden">Thêm khách hàng</span>
                    <span className="hidden max-md:inline">Thêm</span>
                </button>
            </div>
        </section>
    );
}

const EMPTY_FILTER_DIALOG_STATE: CustomerHeaderFilterState = {
    status: "",
    minTotalSpent: "",
    maxTotalSpent: "",
    createdFrom: "",
    createdTo: "",
    recentPurchase: "all"
};

const CUSTOMER_STATUS_FILTERS: Array<{ value: CustomerStatus; label: string }> = [
    { value: "ACTIVE", label: "Hoạt động" },
    { value: "SUSPENDED", label: "Đã khóa" },
    { value: "INACTIVE", label: "Ngừng giao dịch" }
];

const RECENT_PURCHASE_OPTIONS: Array<{
    value: CustomerRecentPurchaseFilter;
    label: string;
}> = [
    { value: "all", label: "Tất cả" },
    { value: "today", label: "Hôm nay" },
    { value: "7-days", label: "7 ngày gần đây" },
    { value: "30-days", label: "30 ngày gần đây" },
    { value: "over-30-days", label: "Hơn 30 ngày chưa mua" },
    { value: "never", label: "Chưa từng mua" }
];

function CustomerFilterPopover({
    draft,
    onApply,
    onCancel,
    onClear,
    onStatusToggle,
    onUpdate
}: {
    draft: CustomerHeaderFilterState;
    onApply: () => void;
    onCancel: () => void;
    onClear: () => void;
    onStatusToggle: (status: CustomerStatus) => void;
    onUpdate: (updates: Partial<CustomerHeaderFilterState>) => void;
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
                    <div className="grid gap-5 md:grid-rows-2">
                        <FilterField label="Trạng thái">
                            <div className="flex flex-wrap gap-2.5">
                                {CUSTOMER_STATUS_FILTERS.map((status) => {
                                    const active = draft.status === status.value;
                                    return (
                                        <button
                                            aria-pressed={active}
                                            className={`h-9 rounded-full border px-3.5 text-xs font-bold transition-colors ${
                                                active
                                                    ? "border-[#159734] bg-[#eaf8ee] text-[#116f28]"
                                                    : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1]"
                                            }`}
                                            key={status.value}
                                            onClick={() => onStatusToggle(status.value)}
                                            type="button"
                                        >
                                            {status.label}
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
                                    value={draft.minTotalSpent}
                                    onChange={(value) => onUpdate({ minTotalSpent: value })}
                                />
                                <FilterInput
                                    inputMode="numeric"
                                    placeholder="Đến"
                                    suffix="VND"
                                    value={draft.maxTotalSpent}
                                    onChange={(value) => onUpdate({ maxTotalSpent: value })}
                                />
                            </div>
                        </FilterField>
                    </div>

                    <div className="grid gap-5 md:grid-rows-2">
                        <FilterField label="Lần mua gần nhất">
                            <FilterSelect
                                value={draft.recentPurchase}
                                options={RECENT_PURCHASE_OPTIONS}
                                onChange={(value) =>
                                    onUpdate({
                                        recentPurchase: value as CustomerRecentPurchaseFilter
                                    })
                                }
                            />
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
        <Select className="w-full" onValueChange={onChange} value={value}>
            <SelectTrigger className="h-11 w-full rounded-lg px-3 text-[#172317]">
                <SelectValue />
            </SelectTrigger>
            <SelectContent>
                {options.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                        {option.label}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}

const CUSTOMER_SECONDARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-white";
const CUSTOMER_PRIMARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d] disabled:cursor-not-allowed disabled:opacity-70";

const TIER_CHIPS = [
    { id: null, code: "ALL", name: "Tất cả", colorCode: null, customerCount: 0 }
];

export function CustomerTierChips({
    appliedFilterCount,
    activeTierId,
    counts,
    filters,
    onSortChange,
    onFiltersChange,
    onSearchDraftChange,
    onSubmit,
    onTierChange,
    searchDraft,
    sort
}: {
    appliedFilterCount: number;
    activeTierId: string | null;
    counts: CustomerTierCountResponse[];
    filters: CustomerHeaderFilterState;
    onSortChange: (sort: CustomerSort) => void;
    onFiltersChange: (filters: CustomerHeaderFilterState) => void;
    onSearchDraftChange: (value: string) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    onTierChange: (tierId: string | null) => void;
    searchDraft: string;
    sort: CustomerSort;
}) {
    const [filterOpen, setFilterOpen] = useState(false);
    const [filterDraft, setFilterDraft] =
        useState<CustomerHeaderFilterState>(filters);
    const chips = (counts.length ? counts : TIER_CHIPS).filter(
        (chip) => chip.code !== "UNASSIGNED"
    );

    function updateFilterDraft(updates: Partial<CustomerHeaderFilterState>) {
        setFilterDraft((current) => ({ ...current, ...updates }));
    }

    function toggleStatus(status: CustomerStatus) {
        setFilterDraft((current) => ({
            ...current,
            status: current.status === status ? "" : status
        }));
    }

    function clearFilters() {
        setFilterDraft(EMPTY_FILTER_DIALOG_STATE);
        onFiltersChange(EMPTY_FILTER_DIALOG_STATE);
    }

    function applyFilters() {
        onFiltersChange(filterDraft);
        setFilterOpen(false);
    }

    return (
        <form className="rounded-2xl bg-white p-3 shadow-pos-card" onSubmit={onSubmit}>
            <div className="flex flex-wrap items-center justify-between gap-3">
                <nav
                    aria-label="Lọc theo hạng khách hàng"
                    className="flex min-w-0 flex-1 gap-2 overflow-x-auto"
                >
                    {chips.map((chip) => {
                        const active =
                            chip.code === "ALL"
                                ? !activeTierId
                                : activeTierId === chip.id;
                        return (
                            <button
                                aria-pressed={active}
                                className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-xs font-extrabold transition-colors ${
                                    active
                                        ? "border-[#159734] bg-[#eaf8ee] text-[#116f28] shadow-[0_1px_2px_rgba(21,151,52,0.08)]"
                                        : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1] hover:text-[#172317]"
                                }`}
                                key={`${chip.code}-${chip.id ?? "none"}`}
                                onClick={() => onTierChange(chip.code === "ALL" ? null : chip.id)}
                                type="button"
                            >
                                <span>{chip.name}</span>
                                <span className={active ? "text-[#159734]" : "text-[#8a96a3]"}>
                                    {chip.customerCount.toLocaleString("vi-VN")}
                                </span>
                            </button>
                        );
                    })}
                </nav>
                <div className="flex min-w-0 flex-wrap items-center justify-end gap-2.5">
                    <div className="w-full min-w-[240px] md:w-[320px]">
                        <SearchBox
                            value={searchDraft}
                            placeholder="Tìm tên, SĐT..."
                            onChange={onSearchDraftChange}
                        />
                    </div>
                    <button
                        aria-expanded={filterOpen}
                        className={`${CUSTOMER_SECONDARY_ACTION_CLASS} relative`}
                        onClick={() => {
                            setFilterDraft(filters);
                            setFilterOpen((open) => !open);
                        }}
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
                    <Select
                        onValueChange={(value) => onSortChange(value as CustomerSort)}
                        value={sort}
                    >
                        <SelectTrigger
                            aria-label="Sắp xếp danh sách khách hàng"
                            className={`${CUSTOMER_SECONDARY_ACTION_CLASS} h-11 min-w-[210px]`}
                        >
                            <ArrowUpDown size={17} aria-hidden="true" />
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent className="w-56">
                            {CUSTOMER_SORT_OPTIONS.map((option) => (
                                <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
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
                <Select
                    onValueChange={(value) => onSortChange(value as CustomerSort)}
                    value={sort || CUSTOMER_DEFAULT_SORT_BY_TAB[activeTab]}
                >
                    <SelectTrigger
                        aria-label="Sắp xếp danh sách khách hàng"
                        className="h-8 min-w-[210px] rounded-lg px-2.5 text-xs font-extrabold text-[#4c5a67]"
                    >
                        <span className="text-[#697684]">Sắp xếp:</span>
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="w-56">
                        {CUSTOMER_SORT_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                                {option.label}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
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
