import {
    ArrowDownUp,
    Pencil,
    Plus,
    SearchX,
    Trash2,
    UsersRound
} from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";

import { Dialog } from "../../../components/ui/dialog";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../components/ui/select";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import {
    CustomerTierDialog,
    type CustomerTierFormValues
} from "../components/CustomerTierDialog";
import type {
    CustomerTierCountResponse,
    CustomerTierResponse
} from "../types/customer.types";

type CustomerTierStatus = CustomerTierResponse["status"];
type CustomerTierView = CustomerTierResponse & {
    customerCount: number;
};

const MOCK_CUSTOMER_TIERS: CustomerTierResponse[] = [
    {
        id: "8f8df478-a15a-4985-b3c8-02c6f1a973d2",
        code: "SILVER",
        name: "Silver",
        minSpending: 2_000_000,
        minOrderCount: 5,
        discountRate: 2,
        status: "ACTIVE",
        colorCode: "#A7A7A7"
    },
    {
        id: "a6d3eb4e-9f19-45f7-94b0-74f0388bf6c9",
        code: "GOLD",
        name: "Gold",
        minSpending: 5_000_000,
        minOrderCount: 12,
        discountRate: 5,
        status: "ACTIVE",
        colorCode: "#D4A72C"
    },
    {
        id: "36c5065a-99bc-4a21-b22b-c1bfe65ebce5",
        code: "PLATINUM",
        name: "Platinum",
        minSpending: 12_000_000,
        minOrderCount: 24,
        discountRate: 8,
        status: "ACTIVE",
        colorCode: "#7B8FA1"
    },
    {
        id: "b7f7b358-b56f-43a9-9c93-43124c9f005a",
        code: "DIAMOND",
        name: "Diamond",
        minSpending: 25_000_000,
        minOrderCount: 40,
        discountRate: 12,
        status: "INACTIVE",
        colorCode: "#6B7280"
    }
];

const MOCK_CUSTOMER_TIER_COUNTS: CustomerTierCountResponse[] = [
    {
        id: null,
        code: "ALL",
        name: "Tất cả",
        colorCode: null,
        customerCount: 215
    },
    {
        id: "8f8df478-a15a-4985-b3c8-02c6f1a973d2",
        code: "SILVER",
        name: "Silver",
        colorCode: "#A7A7A7",
        customerCount: 124
    },
    {
        id: "a6d3eb4e-9f19-45f7-94b0-74f0388bf6c9",
        code: "GOLD",
        name: "Gold",
        colorCode: "#D4A72C",
        customerCount: 68
    },
    {
        id: "36c5065a-99bc-4a21-b22b-c1bfe65ebce5",
        code: "PLATINUM",
        name: "Platinum",
        colorCode: "#7B8FA1",
        customerCount: 19
    },
    {
        id: "b7f7b358-b56f-43a9-9c93-43124c9f005a",
        code: "DIAMOND",
        name: "Diamond",
        colorCode: "#6B7280",
        customerCount: 4
    },
    {
        id: null,
        code: "UNASSIGNED",
        name: "Chưa phân hạng",
        colorCode: null,
        customerCount: 37
    }
];

type TierTabStatus = CustomerTierStatus | "ALL";
type TierSort = "spendingDesc" | "spendingAsc" | "customersDesc";

const TABS: Array<{
    label: string;
    status: TierTabStatus;
}> = [
    { label: "Tất cả", status: "ALL" },
    { label: "Đang hoạt động", status: "ACTIVE" },
    { label: "Ngừng áp dụng", status: "INACTIVE" }
];

const TIER_SECONDARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8]";
const TIER_PRIMARY_ACTION_CLASS =
    "inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d]";

const TIER_SORT_OPTIONS: Array<{ label: string; value: TierSort }> = [
    { value: "spendingDesc", label: "Chi tiêu cao nhất" },
    { value: "spendingAsc", label: "Chi tiêu thấp nhất" },
    { value: "customersDesc", label: "Nhiều khách nhất" }
];

export function CustomerTierManagementPage() {
    const [tiers, setTiers] = useState(MOCK_CUSTOMER_TIERS);
    const [tierCountResponses, setTierCountResponses] = useState(
        MOCK_CUSTOMER_TIER_COUNTS
    );
    const [activeStatus, setActiveStatus] = useState<TierTabStatus>("ALL");
    const [query, setQuery] = useState("");
    const [sort, setSort] = useState<TierSort>("spendingDesc");
    const [dialogOpen, setDialogOpen] = useState(false);
    const [editingTier, setEditingTier] = useState<CustomerTierView | null>(null);

    const tierViews = useMemo(
        () => mergeCustomerTierCounts(tiers, tierCountResponses),
        [tierCountResponses, tiers]
    );
    const statusCounts = useMemo(
        () => getTierStatusCounts(tierViews),
        [tierViews]
    );
    const visibleTiers = useMemo(
        () => sortCustomerTiers(filterCustomerTiers(tierViews, activeStatus, query), sort),
        [activeStatus, query, sort, tierViews]
    );

    function openCreateDialog() {
        setEditingTier(null);
        setDialogOpen(true);
    }

    function openEditDialog(tier: CustomerTierView) {
        setEditingTier(tier);
        setDialogOpen(true);
    }

    function closeDialog() {
        setDialogOpen(false);
        setEditingTier(null);
    }

    function saveTier(values: CustomerTierFormValues) {
        if (editingTier) {
            setTiers((current) =>
                current.map((tier) =>
                    tier.id === editingTier.id
                        ? {
                            ...tier,
                            code: values.code,
                            name: values.name,
                            colorCode: values.colorCode,
                            minSpending: values.minSpending,
                            minOrderCount: values.minOrderCount,
                            discountRate: values.discountRate,
                            status: values.status
                        }
                        : tier
                )
            );
        } else {
            const tierId = createTierId(values.code);
            setTiers((current) => [
                ...current,
                {
                    id: tierId,
                    code: values.code,
                    name: values.name,
                    minSpending: values.minSpending,
                    minOrderCount: values.minOrderCount,
                    discountRate: values.discountRate,
                    status: values.status,
                    colorCode: values.colorCode
                }
            ]);
            setTierCountResponses((current) => [
                ...current,
                {
                    id: tierId,
                    code: values.code,
                    name: values.name,
                    colorCode: values.colorCode,
                    customerCount: 0
                }
            ]);
        }

        closeDialog();
    }

    function deleteTier(tierId: string) {
        setTiers((current) => current.filter((tier) => tier.id !== tierId));
        setTierCountResponses((current) =>
            current.filter((item) => item.id !== tierId)
        );
    }

    return (
        <section className="grid gap-4 text-[#172317]">
            <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div className="grid gap-1">
                    <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                        Quản lý hạng khách hàng
                    </h1>
                    <p className="max-w-2xl text-sm text-gray-500">
                        Thiết lập điều kiện xét hạng, quyền lợi và trạng thái áp dụng.
                    </p>
                </div>

                <div className="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-2.5">
                    <div className="w-full min-w-[240px] md:w-[360px]">
                        <SearchBox
                            value={query}
                            placeholder="Tìm tên hoặc mã hạng..."
                            onChange={setQuery}
                        />
                    </div>
                    <button
                        className={TIER_PRIMARY_ACTION_CLASS}
                        onClick={openCreateDialog}
                        type="button"
                    >
                        <Plus size={17} aria-hidden="true" />
                        <span className="max-md:hidden">Tạo hạng mới</span>
                        <span className="hidden max-md:inline">Tạo hạng</span>
                    </button>
                </div>
            </header>

            <section className="rounded-2xl bg-white p-3 shadow-pos-card">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <div
                        className="flex min-w-0 flex-1 gap-2 overflow-x-auto"
                        role="tablist"
                        aria-label="Lọc hạng khách hàng"
                    >
                        {TABS.map((tab) => {
                            const active = activeStatus === tab.status;

                            return (
                                <button
                                    aria-selected={active}
                                    className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-xs font-extrabold transition-colors ${
                                        active
                                            ? "border-[#159734] bg-[#eaf8ee] text-[#116f28] shadow-[0_1px_2px_rgba(21,151,52,0.08)]"
                                            : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1] hover:text-[#172317]"
                                    }`}
                                    key={tab.status}
                                    onClick={() => setActiveStatus(tab.status)}
                                    role="tab"
                                    type="button"
                                >
                                    <span>{tab.label}</span>
                                    <span className={active ? "text-[#159734]" : "text-[#8a96a3]"}>
                                        {statusCounts[tab.status].toLocaleString("vi-VN")}
                                    </span>
                                </button>
                            );
                        })}
                    </div>

                    <Select
                        onValueChange={(value) => setSort(value as TierSort)}
                        value={sort}
                    >
                        <SelectTrigger
                            aria-label="Sắp xếp hạng khách hàng"
                            className={`${TIER_SECONDARY_ACTION_CLASS} h-11 min-w-[190px]`}
                        >
                            <ArrowDownUp size={16} aria-hidden="true" />
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent className="w-52">
                            {TIER_SORT_OPTIONS.map((option) => (
                                <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
            </section>

            {visibleTiers.length > 0 ? (
                <CustomerTierTable
                    tiers={visibleTiers}
                    onDelete={deleteTier}
                    onEdit={openEditDialog}
                />
            ) : (
                <CustomerTierEmptyState onCreate={openCreateDialog} />
            )}

            <aside className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
                <h2 className="text-sm font-black text-emerald-800">
                    Nguyên tắc xét hạng
                </h2>
                <p className="mt-1.5 text-sm font-semibold leading-6 text-emerald-900/80">
                    Khách hàng chỉ được nâng lên một hạng khi đạt đồng thời mức chi tiêu tích lũy và số đơn hàng tối thiểu của hạng đó trong kỳ xét hạng.
                </p>
            </aside>

            <Dialog
                onOpenChange={(open) => {
                    if (open) setDialogOpen(true);
                    else closeDialog();
                }}
                open={dialogOpen}
            >
                {dialogOpen ? (
                    <CustomerTierDialog
                        tier={editingTier}
                        onCancel={closeDialog}
                        onSubmit={saveTier}
                    />
                ) : null}
            </Dialog>
        </section>
    );
}

function CustomerTierTable({
    tiers,
    onDelete,
    onEdit
}: {
    tiers: CustomerTierView[];
    onDelete: (tierId: string) => void;
    onEdit: (tier: CustomerTierView) => void;
}) {
    return (
        <section className="overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <div className="overflow-auto">
                <table className="w-full min-w-[980px] border-separate border-spacing-0 text-[13px] text-[#233025]">
                    <thead>
                        <tr>
                            <TableHead>Mã hạng</TableHead>
                            <TableHead>Hạng khách hàng</TableHead>
                            <TableHead>Chi tiêu tối thiểu</TableHead>
                            <TableHead>Số đơn tối thiểu</TableHead>
                            <TableHead>Số khách hàng</TableHead>
                            <TableHead>Trạng thái</TableHead>
                            <TableHead className="w-[132px] text-right">Thao tác</TableHead>
                        </tr>
                    </thead>
                    <tbody>
                        {tiers.map((tier, index) => (
                            <tr
                                className={index % 2 === 1 ? "[&>td]:bg-[#f6f9f7]" : ""}
                                key={tier.id}
                            >
                                <TableCell>
                                    <span className="font-black text-[#172317]">
                                        {tier.code}
                                    </span>
                                </TableCell>
                                <TableCell>
                                    <div className="flex min-w-[190px] items-center gap-3">
                                        <span
                                            className="size-3 shrink-0 rounded-full ring-4 ring-slate-100"
                                            style={{ backgroundColor: tier.colorCode }}
                                        />
                                        <span className="min-w-0">
                                            <strong className="block truncate font-black text-[#172317]">
                                                {tier.name}
                                            </strong>
                                            <span className="mt-0.5 block text-xs font-semibold text-[#697684]">
                                                {tier.colorCode.toUpperCase()}
                                            </span>
                                        </span>
                                    </div>
                                </TableCell>
                                <TableCell>{formatCurrency(tier.minSpending)}</TableCell>
                                <TableCell>
                                    {tier.minOrderCount.toLocaleString("vi-VN")} đơn
                                </TableCell>
                                <TableCell>
                                    <span className="inline-flex items-center gap-1.5 font-black text-[#172317]">
                                        <UsersRound size={15} aria-hidden="true" />
                                        {tier.customerCount.toLocaleString("vi-VN")}
                                    </span>
                                </TableCell>
                                <TableCell>
                                    <StatusBadge status={tier.status} />
                                </TableCell>
                                <TableCell>
                                    <div className="flex justify-end gap-2">
                                        <IconButton
                                            label={`Chỉnh sửa ${tier.name}`}
                                            onClick={() => onEdit(tier)}
                                        >
                                            <Pencil size={15} aria-hidden="true" />
                                        </IconButton>
                                        <IconButton
                                            danger
                                            label={`Xóa ${tier.name}`}
                                            onClick={() => onDelete(tier.id)}
                                        >
                                            <Trash2 size={15} aria-hidden="true" />
                                        </IconButton>
                                    </div>
                                </TableCell>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}

function CustomerTierEmptyState({ onCreate }: { onCreate: () => void }) {
    return (
        <section className="grid min-h-[320px] place-items-center rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm">
            <div className="max-w-[420px]">
                <span className="mx-auto grid size-12 place-items-center rounded-full bg-slate-100 text-slate-500">
                    <SearchX size={23} aria-hidden="true" />
                </span>
                <h2 className="mt-4 text-base font-black text-slate-900">
                    Không tìm thấy hạng khách hàng
                </h2>
                <p className="mt-1.5 text-sm font-semibold text-slate-500">
                    Thử thay đổi từ khóa tìm kiếm hoặc tạo hạng mới.
                </p>
                <button
                    className="mt-5 inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-200"
                    onClick={onCreate}
                    type="button"
                >
                    <Plus size={16} aria-hidden="true" />
                    Tạo hạng mới
                </button>
            </div>
        </section>
    );
}

function filterCustomerTiers(
    tiers: CustomerTierView[],
    status: TierTabStatus,
    query: string
) {
    const normalizedQuery = query.trim().toLowerCase();

    return tiers.filter((tier) => {
        const matchesStatus = status === "ALL" || tier.status === status;
        const matchesQuery =
            !normalizedQuery ||
            tier.name.toLowerCase().includes(normalizedQuery) ||
            tier.code.toLowerCase().includes(normalizedQuery);

        return matchesStatus && matchesQuery;
    });
}

function sortCustomerTiers(tiers: CustomerTierView[], sort: TierSort) {
    return [...tiers].sort((first, second) => {
        if (sort === "spendingAsc") {
            return first.minSpending - second.minSpending;
        }
        if (sort === "customersDesc") {
            return second.customerCount - first.customerCount;
        }
        return second.minSpending - first.minSpending;
    });
}

function mergeCustomerTierCounts(
    tiers: CustomerTierResponse[],
    counts: CustomerTierCountResponse[]
): CustomerTierView[] {
    const countByTierId = new Map(
        counts
            .filter((item): item is CustomerTierCountResponse & { id: string } =>
                Boolean(item.id)
            )
            .map((item) => [item.id, item.customerCount])
    );

    return tiers.map((tier) => ({
        ...tier,
        customerCount: countByTierId.get(tier.id) ?? 0
    }));
}

function getTierStatusCounts(tiers: CustomerTierView[]) {
    return tiers.reduce<Record<TierTabStatus, number>>(
        (counts, tier) => ({
            ...counts,
            ALL: counts.ALL + 1,
            [tier.status]: counts[tier.status] + 1
        }),
        {
            ACTIVE: 0,
            ALL: 0,
            INACTIVE: 0
        }
    );
}

function createTierId(code: string) {
    const normalizedCode = code.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-");
    return `${normalizedCode || "tier"}-${Date.now()}`;
}

function StatusBadge({ status }: { status: CustomerTierStatus }) {
    const active = status === "ACTIVE";

    return (
        <span
            className={`inline-flex h-7 items-center rounded-full px-2.5 text-[11px] font-black ${
                active
                    ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200"
                    : "bg-slate-100 text-slate-600 ring-1 ring-slate-200"
            }`}
        >
            {active ? "Đang hoạt động" : "Ngừng áp dụng"}
        </span>
    );
}

function IconButton({
    children,
    danger = false,
    label,
    onClick
}: {
    children: ReactNode;
    danger?: boolean;
    label: string;
    onClick: () => void;
}) {
    return (
        <button
            aria-label={label}
            className={`grid size-8 place-items-center rounded-lg border transition-colors ${
                danger
                    ? "border-[#efc9c2] bg-white text-[#c4422e] hover:bg-[#fff5f3]"
                    : "border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f7fbf8] hover:text-[#172317]"
            }`}
            onClick={onClick}
            type="button"
        >
            {children}
        </button>
    );
}

function TableHead({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <th className={`sticky top-0 z-[1] h-10 whitespace-nowrap border-b border-[#e4ece8] bg-[#f8faf9] px-3 text-left text-[11px] font-black uppercase text-[#556477] ${className}`}>
            {children}
        </th>
    );
}

function TableCell({
    children
}: {
    children: ReactNode;
}) {
    return (
        <td className="h-[58px] whitespace-nowrap border-b border-[#eef2f0] bg-white px-3 align-middle">
            {children}
        </td>
    );
}

function formatCurrency(value: number) {
    return `${value.toLocaleString("vi-VN")}đ`;
}

