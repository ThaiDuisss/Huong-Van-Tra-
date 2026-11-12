import {
    ArrowDownUp,
    LoaderCircle,
    Pencil,
    Plus,
    Power,
    PowerOff,
    SearchX,
    UsersRound
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import {
    DataTable,
    type DataTableColumn
} from "../../../components/shared/list/DataTable";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button, IconButton } from "../../../components/ui/Button";
import { Dialog } from "../../../components/ui/dialog";
import { StatusBadge } from "../../../components/ui/StatusBadge";
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
import { customerApi } from "../services/customer.api";
import type {
    CustomerTierCountResponse,
    CustomerTierResponse,
    CustomerTierStatus
} from "../types/customer.types";
import { customerErrorMessage } from "../utils/customer-list.utils";

type CustomerTierView = CustomerTierResponse & {
    customerCount: number;
};
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

const TIER_SORT_OPTIONS: Array<{ label: string; value: TierSort }> = [
    { value: "spendingDesc", label: "Chi tiêu cao nhất" },
    { value: "spendingAsc", label: "Chi tiêu thấp nhất" },
    { value: "customersDesc", label: "Nhiều khách nhất" }
];

export function CustomerTierManagementPage() {
    const [tiers, setTiers] = useState<CustomerTierResponse[]>([]);
    const [tierCountResponses, setTierCountResponses] = useState<
        CustomerTierCountResponse[]
    >([]);
    const [activeStatus, setActiveStatus] = useState<TierTabStatus>("ALL");
    const [query, setQuery] = useState("");
    const [sort, setSort] = useState<TierSort>("spendingDesc");
    const [dialogOpen, setDialogOpen] = useState(false);
    const [editingTier, setEditingTier] = useState<CustomerTierView | null>(null);
    const [statusChangingTier, setStatusChangingTier] =
        useState<CustomerTierView | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

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

    useEffect(() => {
        void loadTiers();
    }, []);

    async function loadTiers() {
        setLoading(true);
        setError("");
        try {
            const [tierResponse, countResponse] = await Promise.all([
                customerApi.tiers(),
                customerApi.tierCounts()
            ]);
            setTiers(tierResponse);
            setTierCountResponses(countResponse);
        } catch (loadError) {
            setError(customerErrorMessage(loadError));
        } finally {
            setLoading(false);
        }
    }

    function openCreateDialog() {
        setEditingTier(null);
        setDialogOpen(true);
    }

    function openEditDialog(tier: CustomerTierView) {
        setEditingTier(tier);
        setDialogOpen(true);
    }

    function closeDialog() {
        if (saving) return;
        setDialogOpen(false);
        setEditingTier(null);
    }

    async function saveTier(values: CustomerTierFormValues) {
        setSaving(true);
        setError("");
        try {
            if (editingTier) {
                await customerApi.updateTier(editingTier.id, values);
            } else {
                await customerApi.createTier(values);
            }

            setDialogOpen(false);
            setEditingTier(null);
            await loadTiers();
        } catch (saveError) {
            setError(customerErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    async function changeTierStatus(tier: CustomerTierView) {
        const nextStatus: CustomerTierStatus =
            tier.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
        setStatusChangingTier(tier);
        setError("");
        try {
            await customerApi.changeTierStatus(tier.id, { status: nextStatus });
            await loadTiers();
        } catch (changeError) {
            setError(customerErrorMessage(changeError));
        } finally {
            setStatusChangingTier(null);
        }
    }

    return (
        <section className="grid gap-4 text-[#172317]">
            <ListPageHeader
                actions={
                    <>
                        <div className="w-full min-w-[240px] md:w-[360px]">
                            <SearchBox
                                value={query}
                                placeholder="Tìm tên hoặc mã hạng..."
                                onChange={setQuery}
                            />
                        </div>
                        <Button
                            onClick={openCreateDialog}
                            size="search"
                            variant="primary"
                        >
                            <Plus size={17} aria-hidden="true" />
                            <span className="max-md:hidden">Tạo hạng mới</span>
                            <span className="hidden max-md:inline">Tạo hạng</span>
                        </Button>
                    </>
                }
                description="Thiết lập điều kiện xét hạng, quyền lợi và trạng thái áp dụng."
                title="Quản lý hạng khách hàng"
            />

            {error ? (
                <div className="rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]">
                    {error}
                </div>
            ) : null}

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
                            className="h-11 min-w-[190px]"
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

            {loading ? (
                <LoadingState />
            ) : visibleTiers.length > 0 ? (
                <CustomerTierTable
                    statusChangingTierId={statusChangingTier?.id ?? null}
                    tiers={visibleTiers}
                    onEdit={openEditDialog}
                    onStatusChange={changeTierStatus}
                />
            ) : (
                <CustomerTierEmptyState onCreate={openCreateDialog} />
            )}

            <aside className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
                <h2 className="text-sm font-black text-emerald-800">
                    Nguyên tắc xét hạng
                </h2>
                <p className="mt-1.5 text-sm font-semibold leading-6 text-emerald-900/80">
                    Khách hàng chỉ được nâng lên một hạng khi đạt đồng thời mức chi tiêu tích lũy và số đơn hàng tối thiểu của hạng đó trong kỳ xét hạng. Tier đã có lịch sử assignment có thể bị backend chặn đổi ngưỡng để bảo toàn dữ liệu.
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
                        onSubmit={(values) => void saveTier(values)}
                    />
                ) : null}
            </Dialog>
        </section>
    );
}

function CustomerTierTable({
    statusChangingTierId,
    tiers,
    onEdit,
    onStatusChange
}: {
    statusChangingTierId: string | null;
    tiers: CustomerTierView[];
    onEdit: (tier: CustomerTierView) => void;
    onStatusChange: (tier: CustomerTierView) => void;
}) {
    const columns: DataTableColumn<CustomerTierView>[] = [
        {
            key: "code",
            header: "Mã hạng",
            className: "font-black text-[#172317]",
            render: (tier) => tier.code
        },
        {
            key: "name",
            header: "Hạng khách hàng",
            render: (tier) => (
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
            )
        },
        {
            key: "minSpending",
            header: "Chi tiêu tối thiểu",
            render: (tier) => formatCurrency(tier.minSpending)
        },
        {
            key: "minOrderCount",
            header: "Số đơn tối thiểu",
            render: (tier) => <>{tier.minOrderCount.toLocaleString("vi-VN")} đơn</>
        },
        {
            key: "customerCount",
            header: "Số khách hàng",
            render: (tier) => (
                <span className="inline-flex items-center gap-1.5 font-black text-[#172317]">
                    <UsersRound size={15} aria-hidden="true" />
                    {tier.customerCount.toLocaleString("vi-VN")}
                </span>
            )
        },
        {
            key: "status",
            header: "Trạng thái",
            render: (tier) => (
                <StatusBadge tone={tier.status === "ACTIVE" ? "success" : "neutral"}>
                    {tier.status === "ACTIVE" ? "Đang hoạt động" : "Ngừng áp dụng"}
                </StatusBadge>
            )
        },
        {
            key: "actions",
            header: "Thao tác",
            align: "right",
            width: "160px",
            hideOnMobile: true,
            render: (tier) => (
                <div className="flex justify-end gap-2">
                    <IconButton
                        aria-label={`Chỉnh sửa ${tier.name}`}
                        onClick={() => onEdit(tier)}
                        variant="secondary"
                    >
                        <Pencil size={15} aria-hidden="true" />
                    </IconButton>
                    <IconButton
                        aria-label={
                            tier.status === "ACTIVE"
                                ? `Ngừng áp dụng ${tier.name}`
                                : `Áp dụng lại ${tier.name}`
                        }
                        disabled={statusChangingTierId === tier.id}
                        onClick={() => onStatusChange(tier)}
                        variant={tier.status === "ACTIVE" ? "dangerOutline" : "secondary"}
                    >
                        {statusChangingTierId === tier.id ? (
                            <LoaderCircle
                                className="animate-spin"
                                size={15}
                                aria-hidden="true"
                            />
                        ) : tier.status === "ACTIVE" ? (
                            <PowerOff size={15} aria-hidden="true" />
                        ) : (
                            <Power size={15} aria-hidden="true" />
                        )}
                    </IconButton>
                </div>
            )
        }
    ];

    return (
        <DataTable
            caption="Danh sách hạng khách hàng"
            columns={columns}
            emptyTitle="Không tìm thấy hạng khách hàng"
            getRowKey={(tier) => tier.id}
            loading={false}
            minWidth={1080}
            rows={tiers}
        />
    );
}

function LoadingState() {
    return (
        <section className="grid min-h-[320px] place-items-center rounded-2xl border border-[#dce5e1] bg-white p-8 text-center shadow-sm">
            <span className="inline-flex items-center gap-2 text-sm font-black text-[#657282]">
                <LoaderCircle
                    className="animate-spin text-[#159734]"
                    size={20}
                    aria-hidden="true"
                />
                Đang tải hạng khách hàng...
            </span>
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
                <Button className="mt-5" onClick={onCreate} variant="primary">
                    <Plus size={16} aria-hidden="true" />
                    Tạo hạng mới
                </Button>
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
            return normalizeNumber(first.minSpending) - normalizeNumber(second.minSpending);
        }
        if (sort === "customersDesc") {
            return normalizeNumber(second.customerCount) - normalizeNumber(first.customerCount);
        }
        return normalizeNumber(second.minSpending) - normalizeNumber(first.minSpending);
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
        colorCode: tier.colorCode || "#159734",
        minOrderCount: normalizeNumber(tier.minOrderCount),
        minSpending: normalizeNumber(tier.minSpending),
        status: tier.status ?? "ACTIVE",
        customerCount: normalizeNumber(countByTierId.get(tier.id))
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

function normalizeNumber(value: unknown) {
    const numberValue = Number(value ?? 0);
    return Number.isFinite(numberValue) ? numberValue : 0;
}

function formatCurrency(value: number) {
    return `${normalizeNumber(value).toLocaleString("vi-VN")}đ`;
}
