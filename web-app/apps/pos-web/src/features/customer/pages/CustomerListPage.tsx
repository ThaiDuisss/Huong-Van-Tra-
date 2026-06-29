import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { keycloak } from "../../../auth/keycloak";
import { Dialog } from "../../../components/ui/dialog";
import { CustomerCreateDialog } from "../components/CustomerCreateDialog";
import { CustomerDeleteDialog } from "../components/CustomerDeleteDialog";
import { CustomerDetailDrawer } from "../components/CustomerDetailDrawer";
import {
    CustomerEmptyState,
    CustomerInnerTabs,
    CustomerListToolbar,
    CustomerLoadingState,
    CustomerOuterTabs,
    CustomerPageHeader
} from "../components/CustomerPageSections";
import { CustomerTable } from "../components/CustomerTable";
import {
    CUSTOMER_DEFAULT_SORT_BY_TAB,
    CUSTOMER_SECONDARY_TABS,
    CUSTOMER_STATUS_LABELS
} from "../constants/customer-list.constants";
import { useCustomerListData } from "../hooks/useCustomerListData";
import { customerApi } from "../services/customer.api";
import type { CustomerPrimaryTab } from "../types/customer-list.types";
import type {
    CustomerResponse,
    CustomerSort
} from "../types/customer.types";
import {
    customerErrorMessage,
    getCustomerSort,
    toCustomerQuery
} from "../utils/customer-list.utils";

const PRIMARY_TAB_VALUES: CustomerPrimaryTab[] = [
    "all",
    "care",
    "loyal",
    "new",
    "inactive"
];

export function CustomerListPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const preferredTab = getPreferredTab();
    const activeTab = getPrimaryTab(searchParams, preferredTab);
    const activeSegment = getSecondaryTab(searchParams, activeTab);
    const [searchDraft, setSearchDraft] = useState(searchParams.get("q") ?? "");
    const [sortTouched, setSortTouched] = useState(searchParams.has("sort"));
    const [bulkSelection, setBulkSelection] = useState(false);
    const [selectedCustomerIds, setSelectedCustomerIds] = useState<Set<string>>(
        () => new Set()
    );
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [editingCustomer, setEditingCustomer] =
        useState<CustomerResponse | null>(null);
    const [deletingCustomer, setDeletingCustomer] =
        useState<CustomerResponse | null>(null);
    const [exporting, setExporting] = useState(false);
    const [detailOpen, setDetailOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState("");
    const [selectedCustomer, setSelectedCustomer] =
        useState<CustomerResponse | null>(null);

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
    const sort = getCustomerSort(searchParams);
    const total = result?.totalElements ?? 0;
    const counts = buildTabCounts(activeTab, customers, total);

    useEffect(() => {
        if (searchParams.get("view")) return;
        setSearchParams(
            buildCustomerParams({
                current: searchParams,
                primaryTab: preferredTab,
                secondaryTab: "all",
                sort: CUSTOMER_DEFAULT_SORT_BY_TAB[preferredTab]
            }),
            { replace: true }
        );
    }, [preferredTab, searchParams, setSearchParams]);

    useEffect(() => {
        const currentQuery = searchParams.get("q") ?? "";
        if (searchDraft === currentQuery) return;
        const handle = window.setTimeout(() => {
            const next = new URLSearchParams(searchParams);
            const value = searchDraft.trim();
            if (value) next.set("q", value);
            else next.delete("q");
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
        setDetailLoading(true);
        setDetailError("");

        try {
            setSelectedCustomer(await customerApi.detail(customer.id));
        } catch (detailRequestError) {
            setDetailError(customerErrorMessage(detailRequestError));
        } finally {
            setDetailLoading(false);
        }
    }

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        updateParams({ q: searchDraft.trim() || null });
    }

    function applyPrimaryTab(nextTab: CustomerPrimaryTab) {
        const params = buildCustomerParams({
            current: searchParams,
            primaryTab: nextTab,
            secondaryTab: "all",
            sort: sortTouched ? sort : CUSTOMER_DEFAULT_SORT_BY_TAB[nextTab]
        });
        setSearchParams(params);
        setSelectedCustomerIds(new Set());
    }

    function applySecondaryTab(nextSegment: string) {
        const params = buildCustomerParams({
            current: searchParams,
            primaryTab: activeTab,
            secondaryTab: nextSegment,
            sort
        });
        setSearchParams(params);
        setSelectedCustomerIds(new Set());
    }

    function clearSearch() {
        const params = buildCustomerParams({
            current: searchParams,
            primaryTab: activeTab,
            secondaryTab: activeSegment,
            sort
        });
        params.delete("q");
        setSearchParams(params);
        setSearchDraft("");
    }

    function updateParams(updates: Record<string, string | null>) {
        const next = new URLSearchParams(searchParams);
        Object.entries(updates).forEach(([key, value]) => {
            if (value) next.set(key, value);
            else next.delete(key);
        });
        setSearchParams(next);
    }

    function updateSort(nextSort: CustomerSort) {
        setSortTouched(true);
        updateParams({ sort: nextSort });
    }

    async function exportCustomers() {
        setExporting(true);
        setError("");
        try {
            const response = await customerApi.list(
                toCustomerQuery(new URLSearchParams(queryString), undefined, undefined, tiers)
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

    const showTable = !error && (loading || customers.length > 0);
    const emptyType = error ? "error" : searchParams.toString() ? "group" : "empty";

    return (
        <section className="h-full min-h-0 bg-[#f6f8fb]">
            <main className="flex h-full min-h-0 flex-col gap-3 p-[18px] text-[#172317] max-xl:p-3.5">
                <CustomerPageHeader
                    exporting={exporting}
                    loading={loading}
                    searchDraft={searchDraft}
                    onCreate={() => setShowCreateDialog(true)}
                    onExport={() => void exportCustomers()}
                    onSearchDraftChange={setSearchDraft}
                    onSubmit={submitSearch}
                />

                <CustomerOuterTabs
                    activeTab={activeTab}
                    counts={counts}
                    onChange={applyPrimaryTab}
                >
                    <CustomerInnerTabs
                        activeSegment={activeSegment}
                        activeTab={activeTab}
                        onChange={applySecondaryTab}
                    >
                        <CustomerListToolbar
                            activeTab={activeTab}
                            bulkSelection={bulkSelection}
                            selectedCount={selectedCustomerIds.size}
                            sort={sort}
                            onBulkSelectionChange={(enabled) => {
                                setBulkSelection(enabled);
                                if (!enabled) setSelectedCustomerIds(new Set());
                            }}
                            onSortChange={updateSort}
                        />

                        {showTable && (
                            loading && !customers.length ? (
                                <CustomerLoadingState />
                            ) : (
                                <CustomerTable
                                    activeTab={activeTab}
                                    bulkSelection={bulkSelection}
                                    customers={customers}
                                    key={queryString}
                                    loading={loading}
                                    loadingMore={loadingMore}
                                    result={result}
                                    selectedIds={selectedCustomerIds}
                                    onSelectedIdsChange={setSelectedCustomerIds}
                                    onCustomerClick={(customer) =>
                                        void openCustomerDetail(customer)
                                    }
                                    onCustomerDelete={setDeletingCustomer}
                                    onCustomerEdit={setEditingCustomer}
                                    onLoadMore={() => void loadMore()}
                                />
                            )
                        )}

                        {!showTable && (
                            <CustomerEmptyState
                                type={emptyType}
                                onClearSearch={clearSearch}
                                onCreate={() => setShowCreateDialog(true)}
                                onGoAll={() => applyPrimaryTab("all")}
                                onRetry={() => void reload()}
                            />
                        )}
                    </CustomerInnerTabs>
                </CustomerOuterTabs>

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

function getPreferredTab(): CustomerPrimaryTab {
    const roleNames = ["MANAGER", "manager", "STORE_MANAGER", "store_manager"];
    const isManager = roleNames.some((role) => {
        try {
            return keycloak.hasRealmRole(role) || keycloak.hasResourceRole(role);
        } catch {
            return false;
        }
    });
    return isManager ? "care" : "all";
}

function getPrimaryTab(
    params: URLSearchParams,
    fallback: CustomerPrimaryTab
): CustomerPrimaryTab {
    const view = params.get("view") as CustomerPrimaryTab | null;
    return view && PRIMARY_TAB_VALUES.includes(view) ? view : fallback;
}

function getSecondaryTab(params: URLSearchParams, tab: CustomerPrimaryTab) {
    const segment = params.get("segment") ?? "all";
    return CUSTOMER_SECONDARY_TABS[tab].some((item) => item.id === segment)
        ? segment
        : "all";
}

function buildCustomerParams({
    current,
    primaryTab,
    secondaryTab,
    sort
}: {
    current: URLSearchParams;
    primaryTab: CustomerPrimaryTab;
    secondaryTab: string;
    sort: CustomerSort;
}) {
    const params = new URLSearchParams();
    const q = current.get("q");
    if (q) params.set("q", q);
    params.set("view", primaryTab);
    params.set("segment", secondaryTab);
    params.set("sort", sort);
    return params;
}

function buildTabCounts(
    activeTab: CustomerPrimaryTab,
    customers: CustomerResponse[],
    total: number
): Record<CustomerPrimaryTab, number> {
    const monthAgo = daysAgo(30);
    const ninetyDaysAgo = daysAgo(90);
    return {
        all: activeTab === "all" ? total : customers.length,
        care: customers.filter(
            (customer) =>
                !customer.lastPurchaseDate ||
                new Date(`${customer.lastPurchaseDate}T00:00:00`) <= monthAgo
        ).length,
        loyal: customers.filter(
            (customer) =>
                ["DIAMOND", "PLATINUM", "GOLD", "SILVER"].includes(
                    customer.tierCode ?? ""
                ) ||
                customer.totalSpent >= 1_000_000
        ).length,
        new: customers.filter(
            (customer) => new Date(customer.createdAt) >= monthAgo
        ).length,
        inactive: customers.filter(
            (customer) =>
                customer.status === "INACTIVE" ||
                !customer.lastPurchaseDate ||
                new Date(`${customer.lastPurchaseDate}T00:00:00`) <= ninetyDaysAgo
        ).length
    };
}

function daysAgo(days: number) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    date.setHours(0, 0, 0, 0);
    return date;
}
