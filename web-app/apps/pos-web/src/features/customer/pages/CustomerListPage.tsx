import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { Dialog } from "../../../components/ui/dialog";
import { CustomerCreateDialog } from "../components/CustomerCreateDialog";
import { CustomerDeleteDialog } from "../components/CustomerDeleteDialog";
import { CustomerDetailDrawer } from "../components/CustomerDetailDrawer";
import {
    CustomerPageHeader,
    CustomerTierChips
} from "../components/CustomerPageSections";
import { CustomerTable } from "../components/CustomerTable";
import { useCustomerListData } from "../hooks/useCustomerListData";
import type { CustomerResponse } from "../types/customer.types";

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
    const [selectedCustomer, setSelectedCustomer] =
        useState<CustomerResponse | null>(null);

    const queryString = searchParams.toString();
    const {
        tiers,
        tierCounts,
        result,
        customers,
        loading,
        loadingMore,
        loadMore,
        reload
    } = useCustomerListData(queryString);

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

    function openCustomerDetail(customer: CustomerResponse) {
        setSelectedCustomer(customer);
        setDetailOpen(true);
        setDetailError("");
    }

    function submitSearch(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const next = new URLSearchParams(searchParams);
        const value = searchDraft.trim();
        if (value) next.set("q", value);
        else next.delete("q");
        setSearchParams(next);
    }

    return (
        <section className="h-full min-h-0 bg-[#f6f8fb]">
            <main className="flex h-full min-h-0 flex-col gap-4 p-5 text-[#172317] max-xl:p-4 max-md:p-3">
                <CustomerPageHeader
                    loading={loading}
                    searchDraft={searchDraft}
                    onCreate={() => setShowCreateDialog(true)}
                    onSearchDraftChange={setSearchDraft}
                    onSubmit={submitSearch}
                />

                <CustomerTierChips counts={tierCounts} />

                <CustomerTable
                    customers={customers}
                    key={queryString}
                    loading={loading}
                    loadingMore={loadingMore}
                    result={result}
                    selectedIds={selectedCustomerIds}
                    onSelectedIdsChange={setSelectedCustomerIds}
                    onCustomerClick={openCustomerDetail}
                    onCustomerDelete={setDeletingCustomer}
                    onCustomerEdit={setEditingCustomer}
                    onLoadMore={() => void loadMore()}
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
                loading={false}
                onClose={closeDetail}
                onDelete={setDeletingCustomer}
                onEdit={setEditingCustomer}
                open={detailOpen}
            />
        </section>
    );
}
