import {
    Copy,
    History,
    LoaderCircle,
    MoreHorizontal,
    Pencil,
    ReceiptText,
    RefreshCw,
    Search,
    ShieldCheck,
    ShieldOff,
    SlidersHorizontal,
    ShoppingCart,
    Trash2,
    TrendingUp,
    X
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Fragment, type FormEvent, type ReactNode, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    CUSTOMER_STATUS_CLASS_NAMES,
    CUSTOMER_STATUS_LABELS
} from "../constants/customer-list.constants";
import { customerApi } from "../services/customer.api";
import type {
    CustomerPurchaseTransactionResponse,
    CustomerResponse,
    CustomerTierAssignmentReason,
    CustomerTierAssignmentResponse,
    CustomerTierResponse,
    CustomerTierYearlyMetricResponse,
    PageResponse,
    TierProgressResponse
} from "../types/customer.types";
import {
    customerErrorMessage,
    customerStatusActionLabel
} from "../utils/customer-list.utils";

type DetailTab = "progress" | "history" | "transactions";

interface Props {
    customer: CustomerResponse | null;
    error: string;
    loading: boolean;
    open: boolean;
    onClose: () => void;
    onDelete: (customer: CustomerResponse) => void;
    onEdit: (customer: CustomerResponse) => void;
    onStatusChange: (customer: CustomerResponse) => void;
    /** false (màn POS): ẩn điều chỉnh hạng, khoá / mở và xoá khách hàng. */
    canManage?: boolean;
}

const DETAIL_TABS: Array<{
    id: DetailTab;
    icon: LucideIcon;
    label: string;
}> = [
    { id: "progress", icon: TrendingUp, label: "Tiến độ hạng" },
    { id: "history", icon: History, label: "Lịch sử hạng" },
    { id: "transactions", icon: ReceiptText, label: "Giao dịch" }
];

export function CustomerDetailDrawer({
    customer,
    error,
    loading,
    open,
    onClose,
    onDelete,
    onEdit,
    onStatusChange,
    canManage = true
}: Props) {
    const [activeTab, setActiveTab] = useState<DetailTab>("progress");
    const [profileCollapsed, setProfileCollapsed] = useState(false);
    const [progress, setProgress] = useState<TierProgressResponse | null>(null);
    const [metric, setMetric] = useState<CustomerTierYearlyMetricResponse | null>(null);
    const [history, setHistory] = useState<CustomerTierAssignmentResponse[]>([]);
    const [transactions, setTransactions] =
        useState<PageResponse<CustomerPurchaseTransactionResponse> | null>(null);
    const [tiers, setTiers] = useState<CustomerTierResponse[]>([]);
    const [transactionPage, setTransactionPage] = useState(0);
    const [loyaltyLoading, setLoyaltyLoading] = useState(false);
    const [loyaltyError, setLoyaltyError] = useState("");
    const [adjustDialogOpen, setAdjustDialogOpen] = useState(false);
    const [actionMenuOpen, setActionMenuOpen] = useState(false);
    const customerId = customer?.id ?? null;
    const hasTransactions = (transactions?.totalElements ?? 0) > 0;

    useEffect(() => {
        if (!open) return;

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") onClose();
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleKeyDown);
        };
    }, [onClose, open]);

    useEffect(() => {
        if (!open || !customerId) return;
        void loadLoyaltyData(customerId, transactionPage);
    }, [customerId, open, transactionPage]);

    const activeTiers = useMemo(
        () => tiers.filter((tier) => tier.status === "ACTIVE"),
        [tiers]
    );

    async function loadLoyaltyData(customerId: string, page: number) {
        setLoyaltyLoading(true);
        setLoyaltyError("");
        try {
            const [
                progressResponse,
                metricResponse,
                historyResponse,
                transactionsResponse,
                tierResponse
            ] = await Promise.all([
                customerApi.tierProgress(customerId),
                customerApi.tierMetric(customerId),
                customerApi.tierHistory(customerId),
                customerApi.purchaseTransactions(customerId, page, 8),
                customerApi.tiers()
            ]);
            setProgress(progressResponse);
            setMetric(metricResponse);
            setHistory(historyResponse);
            setTransactions(transactionsResponse);
            setTiers(tierResponse);
        } catch (loadError) {
            setLoyaltyError(customerErrorMessage(loadError));
        } finally {
            setLoyaltyLoading(false);
        }
    }

    async function handleAdjusted() {
        setAdjustDialogOpen(false);
        if (customer) {
            await loadLoyaltyData(customer.id, transactionPage);
        }
    }

    if (!open) return null;

    function runAction(action: (customer: CustomerResponse) => void) {
        if (!customer) return;
        onClose();
        action(customer);
    }

    async function copyValue(value: string | null | undefined) {
        if (!value) return;
        await navigator.clipboard?.writeText(value);
        setActionMenuOpen(false);
    }

    return createPortal(
        <div className="fixed inset-0 z-50 grid place-items-center p-3 sm:p-4" role="presentation">
            <button
                aria-label="Đóng chi tiết khách hàng"
                className="absolute inset-0 cursor-default bg-[#101a14]/45 backdrop-blur-[2px]"
                onClick={onClose}
                type="button"
            />
            <section
                aria-label="Chi tiết khách hàng"
                aria-modal="true"
                className="relative flex h-[min(92vh,920px)] w-[min(92vw,1440px)] flex-col overflow-hidden rounded-2xl border border-[#dfe8e3] bg-white shadow-[0_28px_90px_rgba(21,43,30,0.24)] max-md:h-[92vh] max-md:w-[calc(100vw-24px)]"
                role="dialog"
            >
                <header className="sticky top-0 z-20 flex min-h-14 shrink-0 items-center justify-between gap-3 border-b border-[#e2e9e5] bg-white px-4 py-2.5">
                    <h2 className="truncate text-base font-black text-[#172317]">
                        Chi tiết khách hàng
                    </h2>
                    <div className="ml-auto flex shrink-0 items-center gap-2">
                        <button
                            className="inline-flex h-9 items-center justify-center gap-2 rounded-lg bg-[#159734] px-3 text-xs font-black text-white shadow-sm disabled:cursor-not-allowed disabled:opacity-60"
                            disabled={!customer}
                            title="Luồng tạo đơn hàng sẽ được nối với POS sau"
                            type="button"
                        >
                            <ShoppingCart size={15} aria-hidden="true" />
                            <span className="max-sm:hidden">Tạo đơn hàng</span>
                        </button>
                        <div className="relative">
                            <button
                                aria-label="Mở menu thao tác"
                                aria-expanded={actionMenuOpen}
                                className="grid size-9 place-items-center rounded-lg border border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f4f8f5] hover:text-[#172317]"
                                disabled={!customer}
                                onClick={() => setActionMenuOpen((value) => !value)}
                                type="button"
                            >
                                <MoreHorizontal size={18} aria-hidden="true" />
                            </button>
                            {actionMenuOpen && customer ? (
                                <div className="absolute right-0 top-10 z-30 w-60 overflow-hidden rounded-xl border border-[#dfe8e3] bg-white py-1 shadow-[0_18px_48px_rgba(21,43,30,0.16)]">
                                    <MenuAction
                                        icon={Copy}
                                        label="Sao chép mã khách hàng"
                                        onClick={() => void copyValue(customer.customerCode)}
                                    />
                                    <MenuAction
                                        icon={Copy}
                                        label="Sao chép số điện thoại"
                                        onClick={() => void copyValue(customer.phone)}
                                    />
                                    {canManage ? (
                                        <>
                                            <MenuAction
                                                icon={SlidersHorizontal}
                                                label="Điều chỉnh hạng"
                                                onClick={() => {
                                                    setActionMenuOpen(false);
                                                    setAdjustDialogOpen(true);
                                                }}
                                            />
                                            <MenuAction
                                                icon={customer.status === "ACTIVE" ? ShieldOff : ShieldCheck}
                                                label={customerStatusActionLabel(customer.status)}
                                                onClick={() => {
                                                    setActionMenuOpen(false);
                                                    runAction(onStatusChange);
                                                }}
                                            />
                                            <MenuAction
                                                danger
                                                icon={Trash2}
                                                label="Xóa khách hàng"
                                                onClick={() => runAction(onDelete)}
                                            />
                                        </>
                                    ) : null}
                                </div>
                            ) : null}
                        </div>
                        <button
                            aria-label="Đóng"
                            className="grid size-9 place-items-center rounded-lg text-[#657282] hover:bg-[#eef3f0] hover:text-[#172317]"
                            onClick={onClose}
                            type="button"
                        >
                            <X size={20} aria-hidden="true" />
                        </button>
                    </div>
                </header>

                {loading && <DialogLoading message="Đang tải chi tiết khách hàng..." />}

                {!loading && error && (
                    <div className="m-5 rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-4 text-sm font-bold text-[#a43a2a]">
                        {error}
                    </div>
                )}

                {!loading && !error && customer && (
                    <main className="grid min-h-0 flex-1 grid-cols-[420px_minmax(0,1fr)] max-lg:grid-cols-[360px_minmax(0,1fr)] max-md:block max-md:overflow-y-auto">
                        <CustomerProfilePanel
                            collapsed={profileCollapsed}
                            customer={customer}
                            onCopy={copyValue}
                            onToggleCollapse={() => setProfileCollapsed((value) => !value)}
                        />
                        <section className="flex min-h-0 min-w-0 flex-col">
                            <WorkspaceTabs
                                activeTab={activeTab}
                                onChange={setActiveTab}
                            />

                            {loyaltyError ? (
                                <div className="mx-4 mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]">
                                    <span>{loyaltyError}</span>
                                    <button
                                        className="inline-flex h-8 items-center gap-2 rounded-lg border border-[#efc9c2] bg-white px-3 text-xs font-black text-[#a43a2a]"
                                        onClick={() => void loadLoyaltyData(customer.id, transactionPage)}
                                        type="button"
                                    >
                                        <RefreshCw size={14} aria-hidden="true" />
                                        Thử lại
                                    </button>
                                </div>
                            ) : null}

                            <div className="min-h-0 flex-1 overflow-y-auto p-4">
                                {activeTab === "progress" && (
                                    <ProgressTab
                                        loading={loyaltyLoading}
                                        metric={metric}
                                        progress={progress}
                                        onAdjust={() => setAdjustDialogOpen(true)}
                                    />
                                )}

                                {activeTab === "history" && (
                                    <HistoryTab
                                        history={history}
                                        loading={loyaltyLoading}
                                    />
                                )}

                                {activeTab === "transactions" && (
                                    <TransactionsTab
                                        loading={loyaltyLoading}
                                        metric={metric}
                                        page={transactionPage}
                                        transactions={transactions}
                                        onPageChange={setTransactionPage}
                                        onRefresh={() => void loadLoyaltyData(customer.id, transactionPage)}
                                    />
                                )}
                            </div>
                        </section>
                    </main>
                )}

                {!loading && !error && customer && (
                    <footer className="sticky bottom-0 z-20 flex min-h-16 shrink-0 items-center gap-3 border-t border-[#e2e9e5] bg-white px-4 py-3">
                        {canManage ? (
                            <>
                                <button
                                    className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg border border-[#efc9c2] bg-white px-4 text-sm font-black text-[#c4422e] hover:bg-[#fff5f3]"
                                    onClick={() => runAction(onDelete)}
                                    type="button"
                                >
                                    <Trash2 size={16} aria-hidden="true" />
                                    Xóa khách hàng
                                </button>
                                <button
                                    className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg border border-[#dce5e1] bg-white px-4 text-sm font-black text-[#536171] hover:bg-[#f4f8f5]"
                                    onClick={() => runAction(onStatusChange)}
                                    title={hasTransactions && customer.status === "ACTIVE"
                                        ? "Khách đã có giao dịch, nên ngừng hoạt động thay vì xóa."
                                        : undefined}
                                    type="button"
                                >
                                    {customer.status === "ACTIVE"
                                        ? <ShieldOff size={16} aria-hidden="true" />
                                        : <ShieldCheck size={16} aria-hidden="true" />}
                                    <span className="max-sm:hidden">{customerStatusActionLabel(customer.status)}</span>
                                </button>
                            </>
                        ) : null}
                        <div className="ml-auto flex shrink-0 gap-2">
                            <button
                                className="inline-flex h-10 shrink-0 items-center justify-center rounded-lg border border-[#dce5e1] bg-white px-4 text-sm font-black text-[#536171] hover:bg-[#f4f8f5]"
                                onClick={onClose}
                                type="button"
                            >
                                Đóng
                            </button>
                            <button
                                className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-sm hover:bg-[#12862d]"
                                onClick={() => runAction(onEdit)}
                                type="button"
                            >
                                <Pencil size={16} aria-hidden="true" />
                                <span className="max-sm:hidden">Chỉnh sửa khách hàng</span>
                                <span className="sm:hidden">Chỉnh sửa</span>
                            </button>
                        </div>
                    </footer>
                )}
            </section>

            {customer && adjustDialogOpen ? (
                <ManualTierAdjustmentDialog
                    key={customer.id}
                    customer={customer}
                    tiers={activeTiers}
                    open={adjustDialogOpen}
                    onOpenChange={setAdjustDialogOpen}
                    onSaved={() => void handleAdjusted()}
                />
            ) : null}
        </div>,
        document.body
    );
}

function CustomerProfilePanel({
    collapsed,
    customer,
    onCopy,
    onToggleCollapse
}: {
    collapsed: boolean;
    customer: CustomerResponse;
    onCopy: (value: string | null | undefined) => Promise<void>;
    onToggleCollapse: () => void;
}) {
    const firstLetter = customer.fullName.trim().charAt(0).toUpperCase() || "K";
    const companyName = customerField(customer, "companyName");
    const taxCode = customerField(customer, "taxCode");
    const contactName = customerField(customer, "contactName") || customer.fullName;
    const invoiceAddress = customerField(customer, "invoiceAddress");
    const address = customerField(customer, "address") || customerField(customer, "defaultAddress");
    const latestNote = customerField(customer, "latestNote");

    return (
        <aside className="min-h-0 border-r border-[#e2e9e5] bg-white max-md:border-b max-md:border-r-0">
            <div className="sticky top-0 max-h-full overflow-y-auto px-4 py-4 max-md:static max-md:max-h-none">
                <button
                    className="mb-3 hidden h-9 w-full items-center justify-between rounded-lg border border-[#dce5e1] bg-white px-3 text-xs font-black text-[#536171] max-md:flex"
                    onClick={onToggleCollapse}
                    type="button"
                >
                    Hồ sơ khách hàng
                    <span>{collapsed ? "Mở" : "Thu gọn"}</span>
                </button>
                <div className={collapsed ? "max-md:hidden" : ""}>
                    <div className="flex items-start gap-3 border-b border-[#eef2f0] pb-4">
                        <span className="grid size-14 shrink-0 place-items-center rounded-full bg-[#eaf8ee] text-lg font-black text-[#116f28] ring-1 ring-[#c9ead2]">
                            {firstLetter}
                        </span>
                        <div className="min-w-0 flex-1">
                            <strong className="block truncate text-base font-black text-[#172317]">
                                {customer.fullName}
                            </strong>
                            <span className="mt-1 block text-xs font-bold text-[#718078]">
                                Hồ sơ khách hàng
                            </span>
                        </div>
                    </div>

                    <ProfileSection title="Thông tin cơ bản">
                        <ProfileField label="Mã khách hàng" value={customer.customerCode} />
                        <ProfileField label="Loại khách" value={customerTypeLabel(customer.customerType)} />
                        <ProfileField label="Ngày sinh" value={formatDate(customer.dateOfBirth)} />
                        <ProfileField label="Ngày tham gia" value={formatDate(customer.createdAt)} />
                        <ProfileField
                            label="Trạng thái"
                            value={
                                <span
                                    className={`inline-flex h-6 items-center rounded-full px-2 text-[11px] font-black ${CUSTOMER_STATUS_CLASS_NAMES[customer.status]}`}
                                >
                                    {CUSTOMER_STATUS_LABELS[customer.status]}
                                </span>
                            }
                        />
                    </ProfileSection>

                    {customer.customerType === "BUSINESS" ? (
                        <ProfileSection title="Thông tin doanh nghiệp">
                            <ProfileField label="Tên công ty" value={companyName || "Chưa cập nhật"} />
                            <ProfileField label="Mã số thuế" value={taxCode || "Chưa cập nhật"} />
                            <ProfileField label="Người liên hệ" value={contactName} />
                            <ProfileField label="Địa chỉ xuất hóa đơn" value={invoiceAddress || "Chưa cập nhật"} />
                        </ProfileSection>
                    ) : null}

                    <ProfileSection title="Thông tin liên hệ">
                        <ProfileField
                            action={
                                <CopyButton
                                    disabled={!customer.phone}
                                    label="Sao chép số điện thoại"
                                    onClick={() => void onCopy(customer.phone)}
                                />
                            }
                            label="Số điện thoại"
                            value={customer.phone}
                        />
                        <ProfileField
                            action={
                                <CopyButton
                                    disabled={!customer.email}
                                    label="Sao chép email"
                                    onClick={() => void onCopy(customer.email)}
                                />
                            }
                            label="Email"
                            value={customer.email || "Chưa cập nhật"}
                        />
                        <ProfileField label="Địa chỉ" value={address || "Chưa cập nhật"} />
                    </ProfileSection>

                    <ProfileSection
                        action={
                            <button
                                className="h-7 rounded-lg border border-[#dce5e1] bg-white px-2.5 text-[11px] font-black text-[#536171] hover:bg-[#f4f8f5]"
                                type="button"
                            >
                                Thêm ghi chú
                            </button>
                        }
                        title="Ghi chú nội bộ"
                    >
                        {latestNote ? (
                            <p className="rounded-lg border border-[#e2e9e5] px-3 py-2 text-xs font-bold leading-5 text-[#536171]">
                                {latestNote}
                            </p>
                        ) : (
                            <p className="rounded-lg border border-dashed border-[#cfdad4] px-3 py-2 text-xs font-bold text-[#718078]">
                                Chưa có ghi chú nội bộ.
                            </p>
                        )}
                    </ProfileSection>
                </div>
            </div>
        </aside>
    );
}

function WorkspaceTabs({
    activeTab,
    onChange
}: {
    activeTab: DetailTab;
    onChange: (tab: DetailTab) => void;
}) {
    return (
        <nav
            aria-label="Không gian làm việc khách hàng"
            className="sticky top-0 z-10 flex shrink-0 gap-1 overflow-x-auto border-b border-[#e2e9e5] bg-white px-4"
        >
            {DETAIL_TABS.map((tab) => {
                const Icon = tab.icon;
                const active = activeTab === tab.id;

                return (
                    <button
                        aria-selected={active}
                        className={`relative inline-flex h-12 shrink-0 items-center gap-2 border-b-2 px-2.5 text-xs font-black transition-colors ${
                            active
                                ? "border-[#159734] text-[#116f28]"
                                : "border-transparent text-[#657282] hover:text-[#172317]"
                        }`}
                        key={tab.id}
                        onClick={() => onChange(tab.id)}
                        role="tab"
                        type="button"
                    >
                        <Icon size={15} aria-hidden="true" />
                        {tab.label}
                    </button>
                );
            })}
        </nav>
    );
}

function ProgressTab({
    loading,
    metric,
    progress,
    onAdjust
}: {
    loading: boolean;
    metric: CustomerTierYearlyMetricResponse | null;
    progress: TierProgressResponse | null;
    onAdjust: () => void;
}) {
    if (loading && !progress) {
        return <InlineLoading message="Đang tải tiến độ hạng..." />;
    }

    if (!progress) {
        return <EmptyPanel message="Chưa có dữ liệu tiến độ hạng cho khách hàng này." />;
    }

    const nextTier = progress.nextTier;
    const spendingPercent = nextTier
        ? percent(progress.totalSpent, nextTier.minSpending)
        : 100;
    const orderPercent = nextTier
        ? percent(progress.totalOrderCount, nextTier.minOrderCount)
        : 100;
    const qualifiedTier = progress.qualifiedTierForCurrentYear;
    const hasDifferentQualifiedTier =
        qualifiedTier?.id && qualifiedTier.id !== progress.currentTier?.id;

    return (
        <div className="grid gap-4">
            <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#eef2f0] px-4 py-3">
                    <div>
                        <SectionTitle title={`Tiến độ xét hạng năm ${progress.currentYear}`} />
                        <p className="mt-1 text-xs font-bold text-[#718078]">
                            Theo dõi điều kiện hiện tại và hạng đủ điều kiện trong kỳ.
                        </p>
                    </div>
                    <button
                        className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-[#bfd5c6] bg-white px-3 text-xs font-black text-[#159734] hover:bg-[#f3fbf5]"
                        onClick={onAdjust}
                        type="button"
                    >
                        <SlidersHorizontal size={15} aria-hidden="true" />
                        Điều chỉnh hạng
                    </button>
                </div>
                <div className="grid gap-0 md:grid-cols-2 md:divide-x md:divide-[#eef2f0]">
                    <TierSummary
                        label="Hạng hiện tại"
                        tier={progress.currentTier}
                        fallback="Chưa xếp hạng"
                        note={`Hiệu lực đến ${formatDateTime(progress.currentTierEffectiveUntil)}`}
                    />
                    <TierSummary
                        label="Hạng đủ điều kiện năm nay"
                        tier={qualifiedTier}
                        fallback="Chưa đủ điều kiện"
                        note={
                            hasDifferentQualifiedTier
                                ? `Khách hàng đã đủ điều kiện hạng ${qualifiedTier?.name}.`
                                : "Dựa trên dữ liệu giao dịch hiện tại."
                        }
                    />
                </div>
            </section>

            <section className="rounded-xl border border-[#e0e8e4] bg-white p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <SectionTitle title="Điều kiện đến hạng kế tiếp" />
                    <div className="inline-flex items-center gap-2 rounded-lg border border-[#e2e9e5] px-3 py-2 text-xs font-black text-[#536171]">
                        {progress.currentTier?.name ?? "Chưa xếp hạng"}
                        <span className="text-[#9eaaa3]">→</span>
                        {nextTier?.name ?? "Hạng cao nhất"}
                    </div>
                </div>

                <div className="mt-5 grid gap-5">
                    <ProgressBar
                        label="Chi tiêu"
                        percent={spendingPercent}
                        value={formatCurrency(progress.totalSpent)}
                        target={nextTier ? formatCurrency(nextTier.minSpending) : "Đã đạt"}
                        remaining={
                            nextTier
                                ? `Còn ${formatCurrency(progress.remainingSpending ?? 0)}`
                                : "Không còn hạng kế tiếp"
                        }
                    />
                    <ProgressBar
                        label="Số đơn"
                        percent={orderPercent}
                        value={`${progress.totalOrderCount.toLocaleString("vi-VN")} đơn`}
                        target={nextTier ? `${nextTier.minOrderCount.toLocaleString("vi-VN")} đơn` : "Đã đạt"}
                        remaining={
                            nextTier
                                ? `Còn ${(progress.remainingOrderCount ?? 0).toLocaleString("vi-VN")} đơn`
                                : "Không còn hạng kế tiếp"
                        }
                    />
                </div>
            </section>

            <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
                <SectionHeader title="Thông tin xét hạng" />
                <div className="grid divide-y divide-[#eef2f0] md:grid-cols-2 md:divide-x md:divide-y-0 xl:grid-cols-4">
                    <DataGridRow label="Năm xét hạng" value={progress.currentYear.toString()} />
                    <DataGridRow label="Kỳ xét" value="Theo năm" />
                    <DataGridRow label="Ngày hết hiệu lực" value={formatDateTime(progress.currentTierEffectiveUntil)} />
                    <DataGridRow label="Lần tính lại gần nhất" value={formatDateTime(metric?.calculatedAt ?? null)} />
                </div>
            </section>
        </div>
    );
}

function HistoryTab({
    history,
    loading
}: {
    history: CustomerTierAssignmentResponse[];
    loading: boolean;
}) {
    const [expandedId, setExpandedId] = useState<string | null>(null);

    if (loading && history.length === 0) {
        return <InlineLoading message="Đang tải lịch sử hạng..." />;
    }

    if (history.length === 0) {
        return <EmptyPanel message="Chưa có lịch sử thay đổi hạng cho khách hàng này." />;
    }

    return (
        <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
            <SectionHeader title="Lịch sử thay đổi hạng" />
            <div className="overflow-auto max-md:hidden">
                <table className="w-full min-w-[920px] border-separate border-spacing-0 text-xs">
                    <thead>
                        <tr>
                            <TableHead>Thời gian hiệu lực</TableHead>
                            <TableHead>Hạng áp dụng</TableHead>
                            <TableHead>Loại thay đổi</TableHead>
                            <TableHead>Lý do</TableHead>
                            <TableHead>Năm xét hạng</TableHead>
                            <TableHead>Trạng thái</TableHead>
                        </tr>
                    </thead>
                    <tbody>
                        {history.map((item) => {
                            const expanded = expandedId === item.id;

                            return (
                                <Fragment key={item.id}>
                                    <tr
                                        className={`cursor-pointer ${item.status === "ACTIVE" ? "bg-[#f3fbf5]" : "bg-white"} hover:bg-[#f8faf9]`}
                                        onClick={() => setExpandedId(expanded ? null : item.id)}
                                    >
                                        <TableCell>
                                            {formatDateTime(item.effectiveFrom)}
                                            <span className="block text-[11px] text-[#8a96a3]">
                                                đến {formatDateTime(item.effectiveUntil)}
                                            </span>
                                        </TableCell>
                                        <TableCell>
                                            <span className="font-black text-[#172317]">
                                                {item.tier?.name ?? "Không có hạng"}
                                            </span>
                                        </TableCell>
                                        <TableCell>{assignmentReasonLabel(item.assignmentReason)}</TableCell>
                                        <TableCell>{item.note || "Không có ghi chú"}</TableCell>
                                        <TableCell>{item.qualificationYear ?? "Không áp dụng"}</TableCell>
                                        <TableCell>
                                            <StatusPill value={assignmentStatusLabel(item.status)} />
                                        </TableCell>
                                    </tr>
                                    {expanded ? (
                                        <tr key={`${item.id}-detail`}>
                                            <td className="border-t border-[#eef2f0] bg-[#fbfcfb] px-4 py-3" colSpan={6}>
                                                <div className="grid gap-2 text-xs font-bold text-[#657282] md:grid-cols-3">
                                                    <DetailLine label="Ghi chú" value={item.note || "Không có ghi chú"} />
                                                    <DetailLine label="Người thực hiện" value="Hệ thống" />
                                                    <DetailLine label="Thời điểm tạo" value={formatDateTime(item.effectiveFrom)} />
                                                </div>
                                            </td>
                                        </tr>
                                    ) : null}
                                </Fragment>
                            );
                        })}
                    </tbody>
                </table>
            </div>
            <div className="grid gap-2 p-3 md:hidden">
                {history.map((item) => (
                    <CompactRecord
                        key={item.id}
                        title={item.tier?.name ?? "Không có hạng"}
                        meta={`${formatDateTime(item.effectiveFrom)} · ${assignmentStatusLabel(item.status)}`}
                        value={assignmentReasonLabel(item.assignmentReason)}
                    />
                ))}
            </div>
        </section>
    );
}

function TransactionsTab({
    loading,
    metric,
    page,
    transactions,
    onPageChange,
    onRefresh
}: {
    loading: boolean;
    metric: CustomerTierYearlyMetricResponse | null;
    page: number;
    transactions: PageResponse<CustomerPurchaseTransactionResponse> | null;
    onPageChange: (page: number) => void;
    onRefresh: () => void;
}) {
    const rows = transactions?.content ?? [];
    const totalAmount = metric?.totalSpent ?? rows.reduce((sum, transaction) => sum + transaction.amount, 0);
    const totalOrders = metric?.totalOrderCount ?? rows.reduce((sum, transaction) => sum + transaction.orderCountDelta, 0);
    const summaryYear = metric?.tierYear ?? new Date().getFullYear();

    return (
        <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
            <div className="border-b border-[#eef2f0] px-4 py-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                        <SectionTitle title="Giao dịch" />
                        <p className="mt-1 text-xs font-bold text-[#718078]">
                            Theo dõi các giao dịch ảnh hưởng đến xét hạng khách hàng.
                        </p>
                    </div>
                    <button
                        className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#dce5e1] bg-white px-3 text-xs font-black text-[#536171] hover:bg-[#f4f8f5]"
                        onClick={onRefresh}
                        type="button"
                    >
                        <RefreshCw size={14} aria-hidden="true" />
                        Làm mới
                    </button>
                </div>
                <div className="mt-4 grid gap-2 md:grid-cols-[minmax(220px,1fr)_170px_170px_auto]">
                    <label className="relative block">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8a96a3]" size={15} aria-hidden="true" />
                        <input
                            className="h-9 w-full rounded-lg border border-[#dce5e1] bg-white pl-9 pr-3 text-xs font-bold text-[#172317] outline-none focus:border-[#159734]"
                            placeholder="Tìm mã hóa đơn"
                            type="search"
                        />
                    </label>
                    <select className="h-9 rounded-lg border border-[#dce5e1] bg-white px-3 text-xs font-black text-[#536171] outline-none focus:border-[#159734]">
                        <option>Loại giao dịch</option>
                        <option>Mua hàng</option>
                        <option>Hoàn tiền</option>
                        <option>Điều chỉnh</option>
                    </select>
                    <select className="h-9 rounded-lg border border-[#dce5e1] bg-white px-3 text-xs font-black text-[#536171] outline-none focus:border-[#159734]">
                        <option>Khoảng thời gian</option>
                        <option>7 ngày qua</option>
                        <option>30 ngày qua</option>
                        <option>Năm hiện tại</option>
                    </select>
                </div>
                <p className="mt-3 text-xs font-black text-[#536171]">
                    Tổng hợp năm {summaryYear}: {formatCurrency(totalAmount)} · {totalOrders.toLocaleString("vi-VN")} đơn hợp lệ
                </p>
            </div>

            {loading && !transactions ? (
                <TransactionSkeleton />
            ) : rows.length === 0 ? (
                <EmptyPanel message="Chưa có giao dịch hợp lệ để xét hạng trong kỳ hiện tại." />
            ) : (
                <>
                    <div className="overflow-auto max-md:hidden">
                        <table className="w-full min-w-[920px] border-separate border-spacing-0 text-xs">
                            <thead>
                                <tr>
                                    <TableHead>Ngày giao dịch</TableHead>
                                    <TableHead>Mã hóa đơn</TableHead>
                                    <TableHead>Loại giao dịch</TableHead>
                                    <TableHead align="right">Số tiền ảnh hưởng</TableHead>
                                    <TableHead align="right">Số đơn ảnh hưởng</TableHead>
                                    <TableHead>Trạng thái</TableHead>
                                    <TableHead>Ghi chú</TableHead>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((transaction) => (
                                    <tr key={transaction.id}>
                                        <TableCell>{formatDateTime(transaction.occurredAt)}</TableCell>
                                        <TableCell>
                                            <span className="font-black text-[#172317]">
                                                {transaction.referenceId ?? transaction.orderId ?? "Không có mã"}
                                            </span>
                                        </TableCell>
                                        <TableCell>{transactionTypeLabel(transaction.transactionType)}</TableCell>
                                        <TableCell align="right">{formatCurrency(transaction.amount)}</TableCell>
                                        <TableCell align="right">{formatSignedNumber(transaction.orderCountDelta)}</TableCell>
                                        <TableCell><StatusPill value="Hợp lệ" /></TableCell>
                                        <TableCell>{transaction.reason ?? transaction.metadata ?? "Đủ điều kiện xét hạng"}</TableCell>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="grid gap-2 p-3 md:hidden">
                        {rows.map((transaction) => (
                            <CompactRecord
                                key={transaction.id}
                                meta={formatDateTime(transaction.occurredAt)}
                                title={transaction.referenceId ?? transaction.orderId ?? "Không có mã"}
                                value={formatCurrency(transaction.amount)}
                            />
                        ))}
                    </div>
                </>
            )}

            <footer className="flex items-center justify-between border-t border-[#eef2f0] px-4 py-3 text-xs font-bold text-[#657282]">
                <span>
                    Trang {(transactions?.page ?? page) + 1}/{transactions?.totalPages ?? 1}
                </span>
                <div className="flex gap-2">
                    <button
                        className="h-8 rounded-lg border border-[#dce5e1] bg-white px-3 disabled:opacity-50"
                        disabled={transactions?.first ?? true}
                        onClick={() => onPageChange(Math.max(page - 1, 0))}
                        type="button"
                    >
                        Trước
                    </button>
                    <button
                        className="h-8 rounded-lg border border-[#dce5e1] bg-white px-3 disabled:opacity-50"
                        disabled={transactions?.last ?? true}
                        onClick={() => onPageChange(page + 1)}
                        type="button"
                    >
                        Sau
                    </button>
                </div>
            </footer>
        </section>
    );
}

function ManualTierAdjustmentDialog({
    customer,
    tiers,
    open,
    onOpenChange,
    onSaved
}: {
    customer: CustomerResponse;
    tiers: CustomerTierResponse[];
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSaved: () => void;
}) {
    const [tierId, setTierId] = useState(customer.tierId ?? "");
    const [reason, setReason] = useState("Điều chỉnh bởi quản lý");
    const [note, setNote] = useState("");
    const [effectiveFrom, setEffectiveFrom] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!tierId) {
            setError("Vui lòng chọn hạng khách hàng.");
            return;
        }
        if (!reason.trim()) {
            setError("Vui lòng nhập lý do điều chỉnh.");
            return;
        }

        setSaving(true);
        setError("");
        try {
            await customerApi.adjustTier(customer.id, {
                tierId,
                reason: [reason.trim(), note.trim()].filter(Boolean).join(" - "),
                effectiveFrom: effectiveFrom ? `${effectiveFrom}:00` : null
            });
            onSaved();
        } catch (submitError) {
            setError(customerErrorMessage(submitError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            {open ? (
                <DialogContent className="w-[min(560px,calc(100vw-32px))]">
                    <form onSubmit={submit}>
                        <DialogHeader>
                            <DialogTitle>Điều chỉnh hạng thủ công</DialogTitle>
                            <DialogDescription>
                                Áp dụng hạng mới cho {customer.fullName} và ghi nhận vào lịch sử.
                            </DialogDescription>
                        </DialogHeader>
                        <div className="grid gap-4 px-6 py-5">
                            {error ? (
                                <div className="rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]">
                                    {error}
                                </div>
                            ) : null}
                            <label className="grid gap-1.5 text-sm font-bold text-slate-700">
                                <span>Hạng mới</span>
                                <select
                                    className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                                    onChange={(event) => setTierId(event.currentTarget.value)}
                                    value={tierId}
                                >
                                    <option value="">Chọn hạng khách hàng</option>
                                    {tiers.map((tier) => (
                                        <option key={tier.id} value={tier.id}>
                                            {tier.name} ({tier.code})
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="grid gap-1.5 text-sm font-bold text-slate-700">
                                <span>Ngày hiệu lực</span>
                                <input
                                    className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                                    onChange={(event) => setEffectiveFrom(event.currentTarget.value)}
                                    type="datetime-local"
                                    value={effectiveFrom}
                                />
                            </label>
                            <label className="grid gap-1.5 text-sm font-bold text-slate-700">
                                <span>Lý do</span>
                                <select
                                    className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                                    onChange={(event) => setReason(event.currentTarget.value)}
                                    value={reason}
                                >
                                    <option>Ưu đãi đặc biệt</option>
                                    <option>Điều chỉnh bởi quản lý</option>
                                    <option>Chăm sóc khách hàng</option>
                                    <option>Sửa sai dữ liệu</option>
                                    <option>Khác</option>
                                </select>
                            </label>
                            <label className="grid gap-1.5 text-sm font-bold text-slate-700">
                                <span>Ghi chú chi tiết</span>
                                <textarea
                                    className="min-h-24 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                                    onChange={(event) => setNote(event.currentTarget.value)}
                                    placeholder="Ví dụ: Điều chỉnh theo quyết định chăm sóc khách hàng"
                                    value={note}
                                />
                            </label>
                        </div>
                        <DialogFooter>
                            <button
                                className="inline-flex h-10 items-center justify-center rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50"
                                disabled={saving}
                                onClick={() => onOpenChange(false)}
                                type="button"
                            >
                                Hủy
                            </button>
                            <button
                                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d] disabled:opacity-60"
                                disabled={saving}
                                type="submit"
                            >
                                {saving ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
                                Lưu điều chỉnh
                            </button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            ) : null}
        </Dialog>
    );
}

function ProfileSection({
    action,
    children,
    title
}: {
    action?: ReactNode;
    children: ReactNode;
    title: string;
}) {
    return (
        <section className="border-b border-[#eef2f0] py-4 last:border-b-0">
            <div className="mb-3 flex items-center justify-between gap-3">
                <h3 className="text-xs font-black uppercase text-[#718078]">
                    {title}
                </h3>
                {action}
            </div>
            <div className="grid gap-3">{children}</div>
        </section>
    );
}

function ProfileField({
    action,
    label,
    value
}: {
    action?: ReactNode;
    label: string;
    value: ReactNode;
}) {
    return (
        <div className="flex min-w-0 items-start gap-3 text-sm">
            <span className="w-32 shrink-0 pt-0.5 text-xs font-bold text-[#718078]">
                {label}
            </span>
            <div className="flex min-w-0 flex-1 items-start justify-end gap-2 text-right">
                <strong className="min-w-0 flex-1 break-words font-black text-[#26342a]">
                    {value}
                </strong>
                {action}
            </div>
        </div>
    );
}

function CopyButton({
    disabled,
    label,
    onClick
}: {
    disabled?: boolean;
    label: string;
    onClick: () => void;
}) {
    return (
        <button
            aria-label={label}
            className="grid size-7 shrink-0 place-items-center rounded-lg border border-[#dce5e1] text-[#536171] hover:bg-[#f4f8f5] disabled:cursor-not-allowed disabled:opacity-40"
            disabled={disabled}
            onClick={onClick}
            title={label}
            type="button"
        >
            <Copy size={13} aria-hidden="true" />
        </button>
    );
}

function TierSummary({
    fallback,
    label,
    note,
    tier
}: {
    fallback: string;
    label: string;
    note?: string;
    tier: CustomerTierResponse | null;
}) {
    return (
        <div className="p-4">
            <span className="text-[11px] font-black uppercase text-[#718078]">
                {label}
            </span>
            <strong className="mt-2 flex items-center gap-2 text-sm font-black text-[#172317]">
                {tier ? (
                    <span
                        className="size-2.5 rounded-full"
                        style={{ backgroundColor: tier.colorCode }}
                    />
                ) : null}
                {tier?.name ?? fallback}
            </strong>
            {note ? <p className="mt-2 text-xs font-bold text-[#718078]">{note}</p> : null}
        </div>
    );
}

function ProgressBar({
    label,
    percent: progressPercent,
    remaining,
    target,
    value
}: {
    label: string;
    percent: number;
    remaining: string;
    target: string;
    value: string;
}) {
    return (
        <div className="grid gap-2">
            <div className="flex items-center justify-between gap-3 text-xs font-bold">
                <span className="text-[#536171]">{label}</span>
                <span className="text-[#172317]">
                    {value} / {target}
                </span>
            </div>
            <div className="h-3 overflow-hidden rounded-full bg-[#e6eee9]">
                <div
                    className="h-full rounded-full bg-[#159734]"
                    style={{ width: `${Math.min(progressPercent, 100)}%` }}
                />
            </div>
            <span className="text-xs font-bold text-[#718078]">{remaining}</span>
        </div>
    );
}

function SectionHeader({ title }: { title: string }) {
    return (
        <div className="border-b border-[#eef2f0] px-4 py-3">
            <SectionTitle title={title} />
        </div>
    );
}

function SectionTitle({ title }: { title: string }) {
    return <h3 className="text-sm font-black text-[#172317]">{title}</h3>;
}

function DataGridRow({ label, value }: { label: string; value: ReactNode }) {
    return (
        <div className="grid grid-cols-[minmax(120px,0.8fr)_minmax(0,1fr)] gap-3 px-4 py-3 text-xs">
            <span className="font-bold text-[#718078]">{label}</span>
            <strong className="min-w-0 break-words text-right font-black text-[#172317]">
                {value}
            </strong>
        </div>
    );
}

function DetailLine({ label, value }: { label: string; value: ReactNode }) {
    return (
        <div className="grid gap-1">
            <span className="text-[11px] font-black uppercase text-[#8a96a3]">{label}</span>
            <strong className="text-xs font-black text-[#26342a]">{value}</strong>
        </div>
    );
}

function CompactRecord({
    meta,
    title,
    value
}: {
    meta: string;
    title: string;
    value: string;
}) {
    return (
        <article className="rounded-lg border border-[#e2e9e5] p-3">
            <div className="flex items-start justify-between gap-3">
                <strong className="min-w-0 truncate text-sm font-black text-[#172317]">
                    {title}
                </strong>
                <span className="shrink-0 text-xs font-black text-[#159734]">{value}</span>
            </div>
            <p className="mt-1 text-xs font-bold text-[#718078]">{meta}</p>
        </article>
    );
}

function MenuAction({
    danger = false,
    icon: Icon,
    label,
    onClick
}: {
    danger?: boolean;
    icon: LucideIcon;
    label: string;
    onClick: () => void;
}) {
    return (
        <button
            className={`flex h-10 w-full items-center gap-2 px-3 text-left text-xs font-black ${
                danger
                    ? "text-[#c4422e] hover:bg-[#fff5f3]"
                    : "text-[#536171] hover:bg-[#f4f8f5] hover:text-[#172317]"
            }`}
            onClick={onClick}
            type="button"
        >
            <Icon size={15} aria-hidden="true" />
            {label}
        </button>
    );
}

function TableHead({
    align = "left",
    children
}: {
    align?: "left" | "right";
    children: ReactNode;
}) {
    return (
        <th
            className={`sticky top-0 z-[1] bg-[#f8faf9] px-3 py-2 font-black text-[#556477] ${
                align === "right" ? "text-right" : "text-left"
            }`}
        >
            {children}
        </th>
    );
}

function TableCell({
    align = "left",
    children
}: {
    align?: "left" | "right";
    children: ReactNode;
}) {
    return (
        <td
            className={`border-t border-[#eef2f0] px-3 py-3 font-bold text-[#657282] ${
                align === "right" ? "text-right" : "text-left"
            }`}
        >
            {children}
        </td>
    );
}

function StatusPill({ value }: { value: string }) {
    return (
        <span className="inline-flex h-7 items-center rounded-full bg-[#eaf8ee] px-2.5 text-[11px] font-black text-[#116f28] ring-1 ring-emerald-200">
            {value}
        </span>
    );
}

function DialogLoading({ message }: { message: string }) {
    return (
        <div className="grid flex-1 place-items-center p-8 text-sm font-bold text-[#657282]">
            <span className="flex items-center gap-2">
                <LoaderCircle className="animate-spin text-[#159734]" size={20} aria-hidden="true" />
                {message}
            </span>
        </div>
    );
}

function InlineLoading({ message }: { message: string }) {
    return (
        <section className="grid min-h-48 place-items-center rounded-xl border border-[#e0e8e4] bg-white p-5 text-sm font-bold text-[#657282]">
            <span className="flex items-center gap-2">
                <LoaderCircle className="animate-spin text-[#159734]" size={18} aria-hidden="true" />
                {message}
            </span>
        </section>
    );
}

function EmptyPanel({ message }: { message: string }) {
    return (
        <section className="rounded-xl border border-dashed border-[#cfdad4] bg-white p-6 text-center text-sm font-bold text-[#657282]">
            {message}
        </section>
    );
}

function TransactionSkeleton() {
    return (
        <div className="grid gap-2 p-4">
            {Array.from({ length: 6 }, (_, index) => (
                <div
                    className="h-10 animate-pulse rounded-lg bg-[#edf3f0]"
                    key={index}
                />
            ))}
        </div>
    );
}

function formatCurrency(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}

function formatDate(value: string | null) {
    if (!value) return "Chưa cập nhật";
    return new Intl.DateTimeFormat("vi-VN").format(new Date(value));
}

function formatDateTime(value: string | null) {
    if (!value) return "Chưa cập nhật";
    return new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short"
    }).format(new Date(value));
}

function customerTypeLabel(value: CustomerResponse["customerType"]) {
    return value === "BUSINESS" ? "Doanh nghiệp" : "Cá nhân";
}

function percent(value: number, target: number) {
    if (target <= 0) return 100;
    return Math.max(0, Math.min(100, (value / target) * 100));
}

function assignmentReasonLabel(value: CustomerTierAssignmentReason) {
    const labels: Record<CustomerTierAssignmentReason, string> = {
        INITIAL_TIER: "Hạng khởi tạo",
        YEARLY_QUALIFICATION: "Đủ điều kiện theo năm",
        YEARLY_REVIEW: "Rà soát định kỳ",
        MANUAL_ADJUSTMENT: "Điều chỉnh thủ công"
    };
    return labels[value];
}

function assignmentStatusLabel(value: CustomerTierAssignmentResponse["status"]) {
    const labels: Record<CustomerTierAssignmentResponse["status"], string> = {
        ACTIVE: "Đang hiệu lực",
        REPLACED: "Đã thay thế",
        EXPIRED: "Hết hiệu lực",
        CANCELLED: "Đã hủy"
    };
    return labels[value];
}

function transactionTypeLabel(
    value: CustomerPurchaseTransactionResponse["transactionType"]
) {
    const labels: Record<
        CustomerPurchaseTransactionResponse["transactionType"],
        string
    > = {
        PURCHASE: "Mua hàng",
        PARTIAL_REFUND: "Hoàn một phần",
        FULL_REFUND: "Hoàn toàn bộ",
        ADJUSTMENT: "Điều chỉnh"
    };
    return labels[value];
}

function formatSignedNumber(value: number) {
    if (value > 0) return `+${value.toLocaleString("vi-VN")}`;
    return value.toLocaleString("vi-VN");
}

function customerField(customer: CustomerResponse, key: string) {
    const value = (customer as unknown as Record<string, unknown>)[key];
    return typeof value === "string" && value.trim() ? value : "";
}
