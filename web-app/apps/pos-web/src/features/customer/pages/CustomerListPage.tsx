import {
    AlertCircle,
    ChevronDown,
    Download,
    Filter,
    LoaderCircle,
    Plus,
    RefreshCw,
    Search,
    X
} from "lucide-react";
import { type FormEvent, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { Dialog } from "../../../components/ui/dialog";
import { CustomerFilterDialog } from "../components/CustomerFilterDialog";
import { CustomerTable } from "../components/CustomerTable";
import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_COMPACT_BUTTON,
    CUSTOMER_OUTLINE_BUTTON,
    CUSTOMER_QUICK_VIEWS,
    CUSTOMER_SORT_OPTIONS,
    CUSTOMER_STATUS_LABELS,
    EMPTY_CUSTOMER_FILTER
} from "../constants/customer-list.constants";
import { useCustomerListData } from "../hooks/useCustomerListData";
import { customerApi } from "../services/customer.api";
import type {
    CustomerFilterDraft,
    CustomerQuickViewId
} from "../types/customer-list.types";
import {
    appendCustomerFilterDraft,
    customerErrorMessage,
    customerFilterDraftFromParams,
    customerQuickViewParams,
    getActiveCustomerQuickView,
    getAppliedCustomerFilters,
    getCustomerSort,
    toCustomerQuery
} from "../utils/customer-list.utils";

export function CustomerListPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [searchDraft, setSearchDraft] = useState(searchParams.get("q") ?? "");
    const [showFilters, setShowFilters] = useState(false);
    const [filterDraft, setFilterDraft] = useState<CustomerFilterDraft>(() =>
        customerFilterDraftFromParams(searchParams)
    );
    const [exporting, setExporting] = useState(false);
    const queryString = searchParams.toString();
    const {
        tiers,
        result,
        customers,
        loading,
        loadingMore,
        error,
        setError,
        loadMore,
        reload
    } = useCustomerListData(queryString);
    const activeQuickView = getActiveCustomerQuickView(searchParams, tiers);
    const appliedFilters = getAppliedCustomerFilters(searchParams, tiers);

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        updateParams({ q: searchDraft.trim() || null });
    }

    function applyQuickView(view: CustomerQuickViewId) {
        const params = customerQuickViewParams(view, tiers);
        const sort = searchParams.get("sort");
        if (sort) params.set("sort", sort);
        setSearchParams(params);
        setSearchDraft("");
        setFilterDraft(customerFilterDraftFromParams(params));
    }

    function applyFilters(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const next = new URLSearchParams();
        if (searchDraft.trim()) next.set("q", searchDraft.trim());
        const sort = searchParams.get("sort");
        if (sort) next.set("sort", sort);
        appendCustomerFilterDraft(next, filterDraft);
        setSearchParams(next);
        setShowFilters(false);
    }

    function clearAllFilters() {
        const sort = searchParams.get("sort");
        setSearchParams(sort ? { sort } : {});
        setSearchDraft("");
        setFilterDraft(EMPTY_CUSTOMER_FILTER);
    }

    function removeFilter(key: string) {
        const next = new URLSearchParams(searchParams);
        next.delete(key);
        setSearchParams(next);
        setSearchDraft(next.get("q") ?? "");
        setFilterDraft(customerFilterDraftFromParams(next));
    }

    function updateParams(updates: Record<string, string | null>) {
        const next = new URLSearchParams(searchParams);
        Object.entries(updates).forEach(([key, value]) => {
            if (value) next.set(key, value);
            else next.delete(key);
        });
        setSearchParams(next);
    }

    async function exportCustomers() {
        setExporting(true);
        setError("");
        try {
            const response = await customerApi.list(
                toCustomerQuery(new URLSearchParams(queryString))
            );
            const XLSX = await import("xlsx");
            const rows = response.content.map((customer) => ({
                "Mã khách hàng": customer.customerCode,
                "Tên khách hàng": customer.fullName,
                "Số điện thoại": customer.phone,
                Email: customer.email ?? "",
                "Hạng thành viên": customer.tierName ?? "Chưa xếp hạng",
                "Ngày sinh": customer.dateOfBirth ?? "",
                "Lần mua gần nhất": customer.lastPurchaseDate ?? "",
                "Tổng chi tiêu": customer.totalSpent,
                "Trạng thái": CUSTOMER_STATUS_LABELS[customer.status],
                "Ngày tạo": customer.createdAt
            }));
            const workbook = XLSX.utils.book_new();
            XLSX.utils.book_append_sheet(
                workbook,
                XLSX.utils.json_to_sheet(rows),
                "Khach hang"
            );
            XLSX.writeFile(workbook, "danh-sach-khach-hang.xlsx");
        } catch (exportError) {
            setError(customerErrorMessage(exportError));
        } finally {
            setExporting(false);
        }
    }

    return (
        <section className="h-full min-h-0 bg-[#f6f8fb]">
            <main
                className="flex h-full min-h-0 flex-col gap-3 p-[18px] text-[#172317] max-xl:p-3.5"
                aria-labelledby="customer-title"
            >
                <form
                    className="grid grid-cols-[minmax(360px,1fr)_auto_auto_auto] items-center gap-2.5 max-xl:grid-cols-[minmax(0,1fr)_auto_auto]"
                    onSubmit={submitSearch}
                >
                    <label className="flex h-11 min-w-0 items-center gap-2.5 rounded-lg border border-[#dce5e1] bg-white px-[13px] text-[#7a8794] focus-within:border-[#159734] focus-within:shadow-[0_0_0_3px_rgba(21,151,52,0.12)]">
                        <Search size={18} aria-hidden="true" />
                        <input
                            className="min-w-0 flex-1 bg-transparent text-sm font-semibold text-[#172317] outline-none placeholder:text-[#8a96a3]"
                            onChange={(event) =>
                                setSearchDraft(event.target.value)
                            }
                            placeholder="Tìm tên, SĐT, email hoặc mã khách hàng..."
                            type="search"
                            value={searchDraft}
                        />
                    </label>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} cursor-not-allowed border border-[#b8c7bd] bg-[#e8eeea] text-[#728078]`}
                        disabled
                        type="button"
                    >
                        <Plus size={17} aria-hidden="true" />
                        Thêm khách hàng
                    </button>
                    <button
                        aria-expanded={showFilters}
                        aria-haspopup="dialog"
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON} ${showFilters
                                ? "border-[#159734] text-[#159734]"
                                : ""
                            }`}
                        onClick={() => setShowFilters((current) => !current)}
                        type="button"
                    >
                        <Filter size={16} aria-hidden="true" />
                        Bộ lọc
                        {!!appliedFilters.length && (
                            <span className="grid size-5 place-items-center rounded-full bg-[#159734] text-[10px] text-white">
                                {appliedFilters.length}
                            </span>
                        )}
                    </button>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_OUTLINE_BUTTON} max-xl:col-span-full max-xl:justify-self-start`}
                        disabled={exporting || loading}
                        onClick={() => void exportCustomers()}
                        type="button"
                    >
                        {exporting ? (
                            <LoaderCircle
                                className="animate-spin"
                                size={16}
                                aria-hidden="true"
                            />
                        ) : (
                            <Download size={16} aria-hidden="true" />
                        )}
                        {exporting ? "Đang xuất..." : "Xuất Excel"}
                    </button>
                </form>

                <Dialog onOpenChange={setShowFilters} open={showFilters}>
                    <CustomerFilterDialog
                        draft={filterDraft}
                        tiers={tiers}
                        onChange={setFilterDraft}
                        onClear={() => setFilterDraft(EMPTY_CUSTOMER_FILTER)}
                        onSubmit={applyFilters}
                    />
                </Dialog>

                <div className="flex flex-wrap gap-2" aria-label="Lọc nhanh">
                    {CUSTOMER_QUICK_VIEWS.map((view) => (
                        <button
                            className={`inline-flex h-8 items-center rounded-lg border px-[11px] text-xs font-extrabold transition-colors ${activeQuickView === view.id
                                    ? "border-[#159734] bg-[#eaf8ed] text-[#0f7d29]"
                                    : "border-[#dde6e2] bg-white text-[#5f6b7a] hover:bg-[#f7fbf8]"
                                }`}
                            disabled={view.id === "vip" && !tiers.length}
                            key={view.id}
                            onClick={() => applyQuickView(view.id)}
                            type="button"
                        >
                            {view.label}
                        </button>
                    ))}
                </div>

                {!!appliedFilters.length && (
                    <div className="flex min-h-[30px] items-center gap-2 overflow-x-auto">
                        <span className="shrink-0 text-xs font-extrabold text-[#697684]">
                            Bộ lọc đang áp dụng
                        </span>
                        {appliedFilters.map((filter) => (
                            <button
                                className="inline-flex h-7 shrink-0 items-center gap-1 rounded-[7px] border border-[#dce8df] bg-white px-[9px] text-xs font-extrabold text-[#334336]"
                                key={filter.key}
                                onClick={() => removeFilter(filter.key)}
                                type="button"
                            >
                                {filter.label}
                                <X size={13} aria-hidden="true" />
                            </button>
                        ))}
                        <button
                            className="ml-auto shrink-0 bg-transparent text-xs font-black text-[#159734]"
                            onClick={clearAllFilters}
                            type="button"
                        >
                            Xóa tất cả
                        </button>
                    </div>
                )}

                <div className="flex items-center justify-between gap-3.5 text-[13px] text-[#253225]">
                    <strong className="font-black">
                        {(result?.totalElements ?? 0).toLocaleString("vi-VN")}{" "}
                        khách hàng phù hợp
                    </strong>
                    <label className="relative flex h-[34px] items-center rounded-lg border border-[#dce5e1] bg-white text-xs font-extrabold text-[#4c5a67] hover:bg-[#f7fbf8]">
                        <span className="pl-3.5">Sắp xếp:</span>
                        <select
                            aria-label="Sắp xếp danh sách khách hàng"
                            className="h-full appearance-none bg-transparent py-0 pl-1.5 pr-8 font-extrabold outline-none"
                            onChange={(event) =>
                                updateParams({
                                    sort:
                                        event.target.value ===
                                            "lastPurchaseDesc"
                                            ? null
                                            : event.target.value
                                })
                            }
                            value={getCustomerSort(searchParams)}
                        >
                            {CUSTOMER_SORT_OPTIONS.map((option) => (
                                <option
                                    key={option.value}
                                    value={option.value}
                                >
                                    {option.label}
                                </option>
                            ))}
                        </select>
                        <ChevronDown
                            aria-hidden="true"
                            className="pointer-events-none absolute right-2.5"
                            size={15}
                        />
                    </label>
                </div>

                {error && (
                    <div
                        className="flex items-center justify-between gap-3 rounded-lg border border-[#f1c8c1] bg-[#fff5f3] px-3.5 py-2.5 text-xs font-bold text-[#a43a2a]"
                        role="alert"
                    >
                        <span className="flex items-center gap-2">
                            <AlertCircle size={16} aria-hidden="true" />
                            {error}
                        </span>
                        <button
                            className="inline-flex items-center gap-1.5 text-[#159734]"
                            onClick={reload}
                            type="button"
                        >
                            <RefreshCw size={14} aria-hidden="true" />
                            Thử lại
                        </button>
                    </div>
                )}

                <CustomerTable
                    customers={customers}
                    error={error}
                    key={queryString}
                    loading={loading}
                    loadingMore={loadingMore}
                    result={result}
                    onLoadMore={() => void loadMore()}
                />
            </main>
        </section>
    );
}
