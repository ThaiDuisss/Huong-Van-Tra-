import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { Dialog } from "../../../components/ui/dialog";
import { CustomerCreateDialog } from "../components/CustomerCreateDialog";
import { CustomerDeleteDialog } from "../components/CustomerDeleteDialog";
import { CustomerDetailDrawer } from "../components/CustomerDetailDrawer";
import {
    type CustomerHeaderFilterState,
    CustomerPageHeader,
    CustomerTierChips
} from "../components/CustomerPageSections";
import { CustomerTable } from "../components/CustomerTable";
import { useCustomerListData } from "../hooks/useCustomerListData";
import { customerApi } from "../services/customer.api";
import type { CustomerResponse, CustomerSort } from "../types/customer.types";
import {
    customerErrorMessage,
    getCustomerSort
} from "../utils/customer-list.utils";

const EMPTY_HEADER_FILTERS: CustomerHeaderFilterState = {
    status: "",
    minTotalSpent: "",
    maxTotalSpent: "",
    createdFrom: "",
    createdTo: "",
    recentPurchase: "all"
};

export function CustomerListPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [searchDraft, setSearchDraft] = useState(searchParams.get("q") ?? "");
    const [selectedCustomerIds, setSelectedCustomerIds] = useState<Set<string>>(
        () => new Set()
    );
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [editingCustomer, setEditingCustomer] =
        useState<CustomerResponse | null>(null);
    const [deletingCustomer, setDeletingCustomer] =
        useState<CustomerResponse | null>(null);
    const [detailOpen, setDetailOpen] = useState(false);
    const [detailError, setDetailError] = useState("");
    const [detailLoading, setDetailLoading] = useState(false);
    const [selectedCustomer, setSelectedCustomer] =
        useState<CustomerResponse | null>(null);

    const queryString = searchParams.toString();
    const {
        tiers,
        tierCounts,
        result,
        customers,
        loading,
        reload
    } = useCustomerListData(queryString);
    const headerFilters = useMemo(
        () => customerHeaderFiltersFromParams(searchParams),
        [searchParams]
    );
    const appliedFilterCount = useMemo(
        () => countAppliedHeaderFilters(headerFilters),
        [headerFilters]
    );
    const sort = useMemo(() => getCustomerSort(searchParams), [searchParams]);
    const activeTierId = searchParams.getAll("tierIds")[0] ?? null;

    useEffect(() => {
        const currentQuery = searchParams.get("q") ?? "";
        if (searchDraft === currentQuery) return;
        const handle = window.setTimeout(() => {
            const next = new URLSearchParams(searchParams);
            const value = searchDraft.trim();
            if (value) next.set("q", value);
            else next.delete("q");
            resetPage(next);
            setSearchParams(next);
        }, 400);
        return () => window.clearTimeout(handle);
    }, [searchDraft, searchParams, setSearchParams]);

    const closeDetail = useCallback(() => {
        setDetailOpen(false);
    }, []);

    async function openCustomerDetail(customer: CustomerResponse) {
        setSelectedCustomer(customer);
        setDetailOpen(true);
        setDetailError("");
        setDetailLoading(true);
        try {
            setSelectedCustomer(await customerApi.detail(customer.id));
        } catch (error) {
            setDetailError(customerErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    }

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const next = new URLSearchParams(searchParams);
        const value = searchDraft.trim();
        if (value) next.set("q", value);
        else next.delete("q");
        resetPage(next);
        setSearchParams(next);
    }

    function applyHeaderFilters(filters: CustomerHeaderFilterState) {
        const next = new URLSearchParams(searchParams);
        clearHeaderFilterParams(next);

        if (filters.status) next.set("status", filters.status);
        setNumericParam(next, "minTotalSpent", filters.minTotalSpent);
        setNumericParam(next, "maxTotalSpent", filters.maxTotalSpent);
        if (filters.createdFrom) {
            next.set("createdFrom", toDayBoundaryIso(filters.createdFrom, false));
        }
        if (filters.createdTo) {
            next.set("createdTo", toDayBoundaryIso(filters.createdTo, true));
        }
        applyRecentPurchaseFilter(next, filters.recentPurchase);
        resetPage(next);
        setSearchParams(next);
    }

    function changeSort(nextSort: CustomerSort) {
        const next = new URLSearchParams(searchParams);
        next.set("sort", nextSort);
        resetPage(next);
        setSearchParams(next);
    }

    function changeTierFilter(tierId: string | null) {
        const next = new URLSearchParams(searchParams);
        next.delete("tierIds");
        if (tierId) next.append("tierIds", tierId);
        resetPage(next);
        setSearchParams(next);
    }

    function changePage(page: number) {
        const next = new URLSearchParams(searchParams);
        if (page <= 0) next.delete("page");
        else next.set("page", String(page));
        setSelectedCustomerIds(new Set());
        setSearchParams(next);
    }

    return (
        <section className="h-full min-h-0 bg-[#f6f8fb]">
            <main className="flex h-full min-h-0 flex-col gap-4 p-5 text-[#172317] max-xl:p-4 max-md:p-3">
                <CustomerPageHeader
                    appliedFilterCount={appliedFilterCount}
                    filters={headerFilters}
                    loading={loading}
                    searchDraft={searchDraft}
                    onFiltersChange={applyHeaderFilters}
                    onCreate={() => setShowCreateDialog(true)}
                    onSearchDraftChange={setSearchDraft}
                    onSubmit={submitSearch}
                />

                <CustomerTierChips
                    activeTierId={activeTierId}
                    counts={tierCounts}
                    sort={sort}
                    onSortChange={changeSort}
                    onTierChange={changeTierFilter}
                />

                <CustomerTable
                    customers={customers}
                    key={queryString}
                    loading={loading}
                    result={result}
                    selectedIds={selectedCustomerIds}
                    onSelectedIdsChange={setSelectedCustomerIds}
                    onCustomerClick={(customer) => void openCustomerDetail(customer)}
                    onCustomerDelete={setDeletingCustomer}
                    onCustomerEdit={setEditingCustomer}
                    onPageChange={changePage}
                />

                <Dialog onOpenChange={setShowCreateDialog} open={showCreateDialog}>
                    {showCreateDialog && (
                        <CustomerCreateDialog
                            tiers={tiers}
                            onCancel={() => setShowCreateDialog(false)}
                            onSaved={async () => {
                                setShowCreateDialog(false);
                                await reload();
                            }}
                        />
                    )}
                </Dialog>

                <Dialog
                    onOpenChange={(open) => {
                        if (!open) setEditingCustomer(null);
                    }}
                    open={editingCustomer !== null}
                >
                    {editingCustomer && (
                        <CustomerCreateDialog
                            customer={editingCustomer}
                            tiers={tiers}
                            onCancel={() => setEditingCustomer(null)}
                            onSaved={async () => {
                                setEditingCustomer(null);
                                await reload();
                            }}
                        />
                    )}
                </Dialog>

                <Dialog
                    onOpenChange={(open) => {
                        if (!open) setDeletingCustomer(null);
                    }}
                    open={deletingCustomer !== null}
                >
                    {deletingCustomer && (
                        <CustomerDeleteDialog
                            customer={deletingCustomer}
                            onCancel={() => setDeletingCustomer(null)}
                            onDeleted={async () => {
                                setDeletingCustomer(null);
                                await reload();
                            }}
                        />
                    )}
                </Dialog>
            </main>
            <CustomerDetailDrawer
                customer={selectedCustomer}
                error={detailError}
                loading={detailLoading}
                onClose={closeDetail}
                onDelete={setDeletingCustomer}
                onEdit={setEditingCustomer}
                open={detailOpen}
            />
        </section>
    );
}

function customerHeaderFiltersFromParams(
    params: URLSearchParams
): CustomerHeaderFilterState {
    return {
        status: (params.get("status") as CustomerHeaderFilterState["status"]) ?? "",
        minTotalSpent: params.get("minTotalSpent") ?? "",
        maxTotalSpent: params.get("maxTotalSpent") ?? "",
        createdFrom: toLocalDateValue(params.get("createdFrom")),
        createdTo: toLocalDateValue(params.get("createdTo")),
        recentPurchase:
            (params.get("recentPurchase") as CustomerHeaderFilterState["recentPurchase"] | null) ??
            EMPTY_HEADER_FILTERS.recentPurchase
    };
}

function countAppliedHeaderFilters(filters: CustomerHeaderFilterState) {
    return [
        Boolean(filters.status),
        Boolean(filters.minTotalSpent || filters.maxTotalSpent),
        Boolean(filters.createdFrom || filters.createdTo),
        filters.recentPurchase !== "all"
    ].filter(Boolean).length;
}

function clearHeaderFilterParams(params: URLSearchParams) {
    [
        "status",
        "minTotalSpent",
        "maxTotalSpent",
        "createdFrom",
        "createdTo",
        "lastPurchaseFrom",
        "lastPurchaseTo",
        "includeNeverPurchased",
        "hasPurchased",
        "recentPurchase"
    ].forEach((key) => params.delete(key));
}

function resetPage(params: URLSearchParams) {
    params.delete("page");
}

function setNumericParam(params: URLSearchParams, key: string, value: string) {
    const normalized = value.replace(/[^\d]/g, "");
    if (normalized) params.set(key, normalized);
}

function applyRecentPurchaseFilter(
    params: URLSearchParams,
    value: CustomerHeaderFilterState["recentPurchase"]
) {
    if (value === "all") return;
    params.set("recentPurchase", value);
    if (value === "today") {
        const today = todayDateValue();
        params.set("lastPurchaseFrom", today);
        params.set("lastPurchaseTo", today);
        return;
    }
    if (value === "7-days" || value === "30-days") {
        params.set("lastPurchaseFrom", daysAgoDate(value === "7-days" ? 7 : 30));
        params.set("lastPurchaseTo", todayDateValue());
        return;
    }
    if (value === "over-30-days") {
        params.set("lastPurchaseTo", daysAgoDate(30));
        params.set("includeNeverPurchased", "false");
        return;
    }
    if (value === "never") {
        params.set("hasPurchased", "false");
    }
}

function daysAgoDate(days: number) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return toDateValue(date);
}

function todayDateValue() {
    return toDateValue(new Date());
}

function toDateValue(date: Date) {
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}

function toLocalDateValue(value: string | null) {
    if (!value) return "";
    return toDateValue(new Date(value));
}

function toDayBoundaryIso(value: string, endOfDay: boolean) {
    const date = new Date(`${value}T${endOfDay ? "23:59:59.999" : "00:00:00"}`);
    return date.toISOString();
}
