import {
    Coins,
    Copy,
    LoaderCircle,
    MoreHorizontal,
    Pencil,
    RefreshCw,
    ShieldCheck,
    ShieldOff,
    SlidersHorizontal,
    Trash2,
    TrendingUp,
    X
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";

import { TablePagination } from "../../../components/shared/list/TablePagination";
import { RowActionMenu } from "../../../components/shared/RowActionMenu";
import { Button, IconButton } from "../../../components/ui/Button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    Field,
    NativeSelect,
    TextArea,
    TextInput
} from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { StatusBadge, type BadgeTone } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import {
    CUSTOMER_STATUS_LABELS,
    CUSTOMER_STATUS_TONES
} from "../constants/customer-list.constants";
import { customerApi } from "../services/customer.api";
import type {
    CustomerResponse,
    CustomerTierResponse,
    LoyaltyTransactionResponse,
    LoyaltyTransactionType,
    PageResponse
} from "../types/customer.types";
import {
    customerErrorMessage,
    customerStatusActionLabel
} from "../utils/customer-list.utils";

type DetailTab = "progress" | "points";

interface Props {
    customer: CustomerResponse | null;
    error: string;
    loading: boolean;
    open: boolean;
    onClose: () => void;
    onDelete: (customer: CustomerResponse) => void;
    onEdit: (customer: CustomerResponse) => void;
    onStatusChange: (customer: CustomerResponse) => void;
    /** false (màn POS): ẩn điều chỉnh điểm, khoá / mở và xoá khách hàng. */
    canManage?: boolean;
}

const DETAIL_TABS: Array<{
    id: DetailTab;
    icon: LucideIcon;
    label: string;
}> = [
    { id: "progress", icon: TrendingUp, label: "Hạng & điểm" },
    { id: "points", icon: Coins, label: "Lịch sử điểm" }
];

const POINT_PAGE_SIZE = 10;

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
    const [transactions, setTransactions] =
        useState<PageResponse<LoyaltyTransactionResponse> | null>(null);
    const [transactionPage, setTransactionPage] = useState(0);
    const [pointsLoading, setPointsLoading] = useState(false);
    const [pointsError, setPointsError] = useState("");
    // Số dư sau lần điều chỉnh gần nhất, để không phải tải lại cả hồ sơ khách.
    const [balanceOverride, setBalanceOverride] =
        useState<{ customerId: string; balance: number } | null>(null);
    const [adjustDialogOpen, setAdjustDialogOpen] = useState(false);
    const [actionMenuOpen, setActionMenuOpen] = useState(false);
    const customerId = customer?.id ?? null;
    const hasPurchases = (customer?.totalOrderCount ?? 0) > 0;
    const pointBalance =
        customer && balanceOverride?.customerId === customer.id
            ? balanceOverride.balance
            : customer?.pointBalance ?? 0;

    useEffect(() => {
        if (!open) return;

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, [open]);

    useEffect(() => {
        if (!open || !customerId) return;
        void loadPointTransactions(customerId, transactionPage);
    }, [customerId, open, transactionPage]);

    async function loadPointTransactions(customerId: string, page: number) {
        setPointsLoading(true);
        setPointsError("");
        try {
            setTransactions(
                await customerApi.loyaltyTransactions(customerId, page, POINT_PAGE_SIZE)
            );
        } catch (loadError) {
            setPointsError(customerErrorMessage(loadError));
        } finally {
            setPointsLoading(false);
        }
    }

    async function handleAdjusted(transaction: LoyaltyTransactionResponse) {
        setAdjustDialogOpen(false);
        if (!customer) return;
        setBalanceOverride({ customerId: customer.id, balance: transaction.balanceAfter });
        if (transactionPage === 0) {
            await loadPointTransactions(customer.id, 0);
        } else {
            setTransactionPage(0);
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

    return (
        <>
            <ModalShell
                className="flex h-[min(92vh,920px)] w-[min(92vw,1440px)] flex-col max-md:h-[92vh] max-md:w-[calc(100vw-24px)]"
                labelledBy="customer-detail-title"
                onClose={onClose}
            >
                <header className="sticky top-0 z-20 flex min-h-14 shrink-0 items-center justify-between gap-3 border-b border-[#e2e9e5] bg-white px-4 py-2.5">
                    <h2
                        className="truncate text-base font-black text-[#172317]"
                        id="customer-detail-title"
                    >
                        Chi tiết khách hàng
                    </h2>
                    <div className="ml-auto flex shrink-0 items-center gap-2">
                        {customer ? (
                            <RowActionMenu
                                actions={[
                                    {
                                        label: "Sao chép mã khách hàng",
                                        icon: <Copy size={15} />,
                                        onSelect: () => void copyValue(customer.customerCode)
                                    },
                                    {
                                        label: "Sao chép số điện thoại",
                                        icon: <Copy size={15} />,
                                        onSelect: () => void copyValue(customer.phone)
                                    },
                                    ...(canManage
                                        ? [
                                              {
                                                  label: "Điều chỉnh điểm",
                                                  icon: <SlidersHorizontal size={15} />,
                                                  onSelect: () => setAdjustDialogOpen(true)
                                              },
                                              {
                                                  label: customerStatusActionLabel(customer.status),
                                                  icon:
                                                      customer.status === "ACTIVE" ? (
                                                          <ShieldOff size={15} />
                                                      ) : (
                                                          <ShieldCheck size={15} />
                                                      ),
                                                  onSelect: () => runAction(onStatusChange)
                                              },
                                              {
                                                  label: "Xóa khách hàng",
                                                  icon: <Trash2 size={15} />,
                                                  tone: "danger" as const,
                                                  onSelect: () => runAction(onDelete)
                                              }
                                          ]
                                        : [])
                                ]}
                                isOpen={actionMenuOpen}
                                menuLabel="Thao tác khách hàng"
                                onOpenChange={setActionMenuOpen}
                                triggerLabel="Mở menu thao tác"
                            />
                        ) : (
                            <IconButton aria-label="Mở menu thao tác" disabled variant="secondary">
                                <MoreHorizontal size={18} aria-hidden="true" />
                            </IconButton>
                        )}
                        <IconButton aria-label="Đóng" onClick={onClose}>
                            <X size={20} aria-hidden="true" />
                        </IconButton>
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

                            <div className="min-h-0 flex-1 overflow-y-auto p-4">
                                {activeTab === "progress" && (
                                    <ProgressTab
                                        canManage={canManage}
                                        customer={customer}
                                        pointBalance={pointBalance}
                                        onAdjust={() => setAdjustDialogOpen(true)}
                                    />
                                )}

                                {activeTab === "points" && (
                                    <PointsTab
                                        error={pointsError}
                                        loading={pointsLoading}
                                        page={transactionPage}
                                        pointBalance={pointBalance}
                                        transactions={transactions}
                                        onPageChange={setTransactionPage}
                                        onRefresh={() => void loadPointTransactions(customer.id, transactionPage)}
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
                                <Button onClick={() => runAction(onDelete)} variant="dangerOutline">
                                    <Trash2 size={16} aria-hidden="true" />
                                    Xóa khách hàng
                                </Button>
                                <Button
                                    onClick={() => runAction(onStatusChange)}
                                    title={hasPurchases && customer.status === "ACTIVE"
                                        ? "Khách đã có giao dịch, nên ngừng hoạt động thay vì xóa."
                                        : undefined}
                                >
                                    {customer.status === "ACTIVE"
                                        ? <ShieldOff size={16} aria-hidden="true" />
                                        : <ShieldCheck size={16} aria-hidden="true" />}
                                    <span className="max-sm:hidden">{customerStatusActionLabel(customer.status)}</span>
                                </Button>
                            </>
                        ) : null}
                        <div className="ml-auto flex shrink-0 gap-2">
                            <Button onClick={onClose}>Đóng</Button>
                            <Button onClick={() => runAction(onEdit)} variant="primary">
                                <Pencil size={16} aria-hidden="true" />
                                <span className="max-sm:hidden">Chỉnh sửa khách hàng</span>
                                <span className="sm:hidden">Chỉnh sửa</span>
                            </Button>
                        </div>
                    </footer>
                )}
            </ModalShell>

            {customer && canManage && adjustDialogOpen ? (
                <PointAdjustmentDialog
                    key={customer.id}
                    customer={customer}
                    pointBalance={pointBalance}
                    open={adjustDialogOpen}
                    onOpenChange={setAdjustDialogOpen}
                    onSaved={(transaction) => void handleAdjusted(transaction)}
                />
            ) : null}
        </>
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
                    <div className="flex items-center gap-3 border-b border-[#eef2f0] pb-4">
                        <span className="grid size-12 shrink-0 place-items-center rounded-full bg-[#eaf8ee] text-lg font-black text-[#116f28] ring-1 ring-[#c9ead2]">
                            {firstLetter}
                        </span>
                        <div className="min-w-0 flex-1">
                            <strong className="block truncate text-base font-black text-[#172317]">
                                {customer.fullName}
                            </strong>
                        </div>
                    </div>

                    <ProfileSection title="Thông tin cơ bản">
                        <ProfileField label="Mã khách hàng" value={customer.customerCode} />
                        <ProfileField label="Ngày sinh" value={formatDate(customer.dateOfBirth)} />
                        <ProfileField label="Ngày tham gia" value={formatDate(customer.createdAt)} />
                        <ProfileField
                            label="Trạng thái"
                            value={
                                <StatusBadge tone={CUSTOMER_STATUS_TONES[customer.status]}>
                                    {CUSTOMER_STATUS_LABELS[customer.status]}
                                </StatusBadge>
                            }
                        />
                    </ProfileSection>

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
                    </ProfileSection>

                    <ProfileSection
                        action={
                            <Button size="sm">Thêm ghi chú</Button>
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
    canManage,
    customer,
    pointBalance,
    onAdjust
}: {
    canManage: boolean;
    customer: CustomerResponse;
    pointBalance: number;
    onAdjust: () => void;
}) {
    const currentTier = currentTierOf(customer);
    const nextTier = customer.nextTier ?? null;
    const totalOrderCount = customer.totalOrderCount ?? 0;
    const remainingSpending = nextTier
        ? customer.remainingSpending ?? Math.max(nextTier.minSpending - customer.totalSpent, 0)
        : 0;
    const remainingOrderCount = nextTier
        ? customer.remainingOrderCount ?? Math.max(nextTier.minOrderCount - totalOrderCount, 0)
        : 0;

    return (
        <div className="grid gap-4">
            <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
                <div className="flex min-h-14 flex-wrap items-center justify-between gap-3 border-b border-[#eef2f0] px-4 py-2.5">
                    <SectionTitle title="Hạng thành viên & điểm" />
                    {canManage ? (
                        <Button onClick={onAdjust} size="sm">
                            <SlidersHorizontal size={15} aria-hidden="true" />
                            Điều chỉnh điểm
                        </Button>
                    ) : null}
                </div>
                <div className="grid gap-0 md:grid-cols-2 md:divide-x md:divide-[#eef2f0]">
                    <TierSummary
                        label="Hạng hiện tại"
                        tier={currentTier}
                        fallback="Chưa xếp hạng"
                    />
                    <div className="grid gap-1.5 p-4">
                        <span className="text-[11px] font-black uppercase tracking-wide text-[#718078]">
                            Điểm hiện có
                        </span>
                        <strong className="flex items-center gap-2 text-base font-black text-[#172317]">
                            <Coins className="text-[#b7791f]" size={16} aria-hidden="true" />
                            {formatPoints(pointBalance)} điểm
                        </strong>
                    </div>
                </div>
            </section>

            <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
                <div className="flex min-h-14 flex-wrap items-center justify-between gap-3 border-b border-[#eef2f0] px-4 py-2.5">
                    <SectionTitle title="Điều kiện đến hạng kế tiếp" />
                    <div className="inline-flex items-center gap-2 rounded-lg border border-[#e2e9e5] px-3 py-1.5 text-xs font-black text-[#536171]">
                        {currentTier?.name ?? "Chưa xếp hạng"}
                        <span className="text-[#9eaaa3]">→</span>
                        {nextTier?.name ?? "Hạng cao nhất"}
                    </div>
                </div>

                <div className="grid gap-5 p-4">
                    <ProgressBar
                        label="Chi tiêu"
                        percent={nextTier ? percent(customer.totalSpent, nextTier.minSpending) : 100}
                        value={formatCurrency(customer.totalSpent)}
                        target={nextTier ? formatCurrency(nextTier.minSpending) : "Đã đạt"}
                        remaining={
                            nextTier
                                ? `Còn ${formatCurrency(remainingSpending)}`
                                : "Không còn hạng kế tiếp"
                        }
                    />
                    <ProgressBar
                        label="Số đơn"
                        percent={nextTier ? percent(totalOrderCount, nextTier.minOrderCount) : 100}
                        value={`${totalOrderCount.toLocaleString("vi-VN")} đơn`}
                        target={nextTier ? `${nextTier.minOrderCount.toLocaleString("vi-VN")} đơn` : "Đã đạt"}
                        remaining={
                            nextTier
                                ? `Còn ${remainingOrderCount.toLocaleString("vi-VN")} đơn`
                                : "Không còn hạng kế tiếp"
                        }
                    />
                </div>
            </section>

            <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
                <SectionHeader title="Tổng hợp mua hàng" />
                <div className="grid divide-y divide-[#eef2f0] md:grid-cols-3 md:divide-x md:divide-y-0">
                    <DataGridRow label="Tổng chi tiêu" value={formatCurrency(customer.totalSpent)} />
                    <DataGridRow label="Tổng số đơn" value={`${totalOrderCount.toLocaleString("vi-VN")} đơn`} />
                    <DataGridRow label="Lần mua gần nhất" value={formatDateTime(customer.lastPurchaseDate)} />
                </div>
            </section>
        </div>
    );
}

function PointsTab({
    error,
    loading,
    page,
    pointBalance,
    transactions,
    onPageChange,
    onRefresh
}: {
    error: string;
    loading: boolean;
    page: number;
    pointBalance: number;
    transactions: PageResponse<LoyaltyTransactionResponse> | null;
    onPageChange: (page: number) => void;
    onRefresh: () => void;
}) {
    const rows = transactions?.content ?? [];

    return (
        <section className="overflow-hidden rounded-xl border border-[#e0e8e4] bg-white">
            <div className="flex min-h-14 flex-wrap items-center justify-between gap-3 border-b border-[#eef2f0] px-4 py-2.5">
                <div>
                    <SectionTitle title="Lịch sử điểm" />
                    <p className="mt-0.5 text-xs font-bold text-[#718078]">
                        Số dư hiện tại: {formatPoints(pointBalance)} điểm
                    </p>
                </div>
                <Button disabled={loading} onClick={onRefresh} size="sm">
                    <RefreshCw size={14} aria-hidden="true" />
                    Làm mới
                </Button>
            </div>

            {error ? (
                <div className="m-4 rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]">
                    {error}
                </div>
            ) : loading && !transactions ? (
                <TransactionSkeleton />
            ) : rows.length === 0 ? (
                <div className="p-4">
                    <EmptyPanel message="Khách hàng chưa có giao dịch điểm nào." />
                </div>
            ) : (
                <>
                    <div className="overflow-auto max-md:hidden">
                        <Table className="min-w-[860px]">
                            <TableHead>
                                <TableRow>
                                    <TableHeaderCell>Thời gian</TableHeaderCell>
                                    <TableHeaderCell>Loại</TableHeaderCell>
                                    <TableHeaderCell>Chứng từ</TableHeaderCell>
                                    <TableHeaderCell className="text-right">Điểm</TableHeaderCell>
                                    <TableHeaderCell className="text-right">Số dư sau</TableHeaderCell>
                                    <TableHeaderCell>Lý do</TableHeaderCell>
                                </TableRow>
                            </TableHead>
                            <tbody>
                                {rows.map((transaction) => (
                                    <TableRow key={transaction.id}>
                                        <TableCell>{formatDateTime(transaction.createdAt)}</TableCell>
                                        <TableCell>
                                            <StatusBadge tone={POINT_TYPE_TONES[transaction.type]}>
                                                {POINT_TYPE_LABELS[transaction.type]}
                                            </StatusBadge>
                                        </TableCell>
                                        <TableCell>
                                            <span className="font-black text-[#172317]">
                                                {transaction.referenceCode ?? "—"}
                                            </span>
                                        </TableCell>
                                        <TableCell className="text-right">
                                            <PointDelta points={transaction.points} />
                                        </TableCell>
                                        <TableCell className="text-right">
                                            {formatPoints(transaction.balanceAfter)}
                                        </TableCell>
                                        <TableCell>{transaction.reason || "—"}</TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </div>
                    <div className="grid gap-2 p-3 md:hidden">
                        {rows.map((transaction) => (
                            <CompactRecord
                                key={transaction.id}
                                meta={[formatDateTime(transaction.createdAt), transaction.referenceCode]
                                    .filter(Boolean)
                                    .join(" · ")}
                                title={POINT_TYPE_LABELS[transaction.type]}
                                value={formatSignedNumber(transaction.points)}
                            />
                        ))}
                    </div>
                </>
            )}

            <TablePagination
                itemLabel="giao dịch"
                onPageChange={onPageChange}
                page={transactions?.page ?? page}
                pageSize={transactions?.size ?? POINT_PAGE_SIZE}
                totalItems={transactions?.totalElements ?? 0}
                totalPages={transactions?.totalPages ?? 1}
            />
        </section>
    );
}

function PointDelta({ points }: { points: number }) {
    return (
        <strong className={`font-black ${points < 0 ? "text-[#a43a2a]" : "text-[#116f28]"}`}>
            {formatSignedNumber(points)}
        </strong>
    );
}

type AdjustDirection = "add" | "subtract";

function PointAdjustmentDialog({
    customer,
    pointBalance,
    open,
    onOpenChange,
    onSaved
}: {
    customer: CustomerResponse;
    pointBalance: number;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSaved: (transaction: LoyaltyTransactionResponse) => void;
}) {
    const [direction, setDirection] = useState<AdjustDirection>("add");
    const [points, setPoints] = useState("");
    const [reason, setReason] = useState("Chăm sóc khách hàng");
    const [note, setNote] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const amount = Number(points);
        if (!Number.isInteger(amount) || amount <= 0) {
            setError("Số điểm phải là số nguyên lớn hơn 0.");
            return;
        }
        if (direction === "subtract" && amount > pointBalance) {
            setError(
                `Khách chỉ còn ${formatPoints(pointBalance)} điểm, không thể trừ ${formatPoints(amount)} điểm.`
            );
            return;
        }
        const fullReason = [reason.trim(), note.trim()].filter(Boolean).join(" - ");
        if (!fullReason) {
            setError("Vui lòng nhập lý do điều chỉnh.");
            return;
        }

        setSaving(true);
        setError("");
        try {
            const transaction = await customerApi.adjustPoints(customer.id, {
                points: direction === "add" ? amount : -amount,
                reason: fullReason
            });
            onSaved(transaction);
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
                            <DialogTitle>Điều chỉnh điểm</DialogTitle>
                            <DialogDescription>
                                Cộng hoặc trừ điểm cho {customer.fullName}. Số dư hiện tại:{" "}
                                {formatPoints(pointBalance)} điểm.
                            </DialogDescription>
                        </DialogHeader>
                        <div className="grid gap-4 px-6 py-5">
                            {error ? (
                                <div
                                    className="rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]"
                                    role="alert"
                                >
                                    {error}
                                </div>
                            ) : null}
                            <div className="grid gap-4 sm:grid-cols-2">
                                <Field label="Thao tác">
                                    <NativeSelect
                                        onChange={(event) =>
                                            setDirection(event.currentTarget.value as AdjustDirection)
                                        }
                                        value={direction}
                                    >
                                        <option value="add">Cộng điểm</option>
                                        <option value="subtract">Trừ điểm</option>
                                    </NativeSelect>
                                </Field>
                                <Field label="Số điểm" required>
                                    <TextInput
                                        autoFocus
                                        inputMode="numeric"
                                        min={1}
                                        onChange={(event) => setPoints(event.currentTarget.value)}
                                        placeholder="Ví dụ: 100"
                                        step={1}
                                        type="number"
                                        value={points}
                                    />
                                </Field>
                            </div>
                            <Field label="Lý do">
                                <NativeSelect
                                    onChange={(event) => setReason(event.currentTarget.value)}
                                    value={reason}
                                >
                                    <option>Chăm sóc khách hàng</option>
                                    <option>Ưu đãi đặc biệt</option>
                                    <option>Sửa sai dữ liệu</option>
                                    <option>Khác</option>
                                </NativeSelect>
                            </Field>
                            <Field label="Ghi chú chi tiết">
                                <TextArea
                                    onChange={(event) => setNote(event.currentTarget.value)}
                                    placeholder="Ví dụ: Bù điểm cho đơn bị lỗi hệ thống"
                                    value={note}
                                />
                            </Field>
                        </div>
                        <DialogFooter>
                            <Button disabled={saving} onClick={() => onOpenChange(false)}>
                                Hủy
                            </Button>
                            <Button disabled={saving} type="submit" variant="primary">
                                {saving ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : null}
                                Lưu điều chỉnh
                            </Button>
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
            <div className="mb-2 flex min-h-8 items-center justify-between gap-3">
                <h3 className="text-xs font-black uppercase tracking-wide text-[#718078]">
                    {title}
                </h3>
                {action}
            </div>
            <div className="grid gap-1">{children}</div>
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
        <div className="flex min-h-9 min-w-0 items-center gap-3 text-sm">
            <span className="w-28 shrink-0 text-xs font-bold text-[#718078]">
                {label}
            </span>
            <div className="flex min-w-0 flex-1 items-center justify-end gap-2 text-right">
                <strong className="min-w-0 flex-1 break-words font-bold text-[#26342a]">
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
        <IconButton
            aria-label={label}
            disabled={disabled}
            onClick={onClick}
            title={label}
            variant="secondary"
        >
            <Copy size={13} aria-hidden="true" />
        </IconButton>
    );
}

function TierSummary({
    fallback,
    label,
    tier
}: {
    fallback: string;
    label: string;
    tier: CustomerTierResponse | null;
}) {
    return (
        <div className="grid gap-1.5 p-4">
            <span className="text-[11px] font-black uppercase tracking-wide text-[#718078]">
                {label}
            </span>
            <strong className="flex items-center gap-2 text-base font-black text-[#172317]">
                {tier ? (
                    <span
                        className="size-2.5 rounded-full"
                        style={{ backgroundColor: tier.colorCode }}
                    />
                ) : null}
                {tier?.name ?? fallback}
            </strong>
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
        <div className="grid gap-1.5">
            <div className="flex items-center justify-between gap-3 text-xs font-bold">
                <span className="text-[#536171]">{label}</span>
                <span className="text-[#172317]">
                    {value} / {target}
                </span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-[#e6eee9]">
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
        <div className="grid gap-1 px-4 py-3">
            <span className="text-xs font-bold text-[#718078]">{label}</span>
            <strong className="min-w-0 break-words text-sm font-black text-[#172317]">
                {value}
            </strong>
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

const POINT_TYPE_LABELS: Record<LoyaltyTransactionType, string> = {
    EARN: "Tích điểm",
    REVERSE: "Trừ do hoàn tiền",
    ADJUST: "Điều chỉnh",
    REDEEM: "Đổi điểm"
};

const POINT_TYPE_TONES: Record<LoyaltyTransactionType, BadgeTone> = {
    EARN: "success",
    REVERSE: "pending",
    ADJUST: "info",
    REDEEM: "violet"
};

/** Hạng hiện tại dựng từ các field phẳng trên CustomerResponse, chỉ dùng để hiển thị tên và màu. */
function currentTierOf(customer: CustomerResponse): CustomerTierResponse | null {
    if (!customer.tierId || !customer.tierName) return null;
    return {
        id: customer.tierId,
        code: customer.tierCode ?? "",
        name: customer.tierName,
        colorCode: customer.tierColor ?? "#159734",
        minSpending: 0,
        minOrderCount: 0,
        pointMultiplier: 1,
        status: "ACTIVE"
    };
}

function formatCurrency(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}

function formatPoints(value: number) {
    return value.toLocaleString("vi-VN");
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

function percent(value: number, target: number) {
    if (target <= 0) return 100;
    return Math.max(0, Math.min(100, (value / target) * 100));
}

function formatSignedNumber(value: number) {
    if (value > 0) return `+${value.toLocaleString("vi-VN")}`;
    return value.toLocaleString("vi-VN");
}

function customerField(customer: CustomerResponse, key: string) {
    const value = (customer as unknown as Record<string, unknown>)[key];
    return typeof value === "string" && value.trim() ? value : "";
}
