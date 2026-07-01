import { MoreHorizontal, Pencil, Users } from "lucide-react";

export type CustomerTierStatus = "ACTIVE" | "INACTIVE";

export interface CustomerTierMock {
    id: string;
    name: string;
    code: string;
    colorCode: string;
    minSpending: number;
    minOrderCount: number;
    discountRate: number;
    customerCount: number;
    status: CustomerTierStatus;
}

interface TierCardProps {
    tier: CustomerTierMock;
    onEdit?: (tier: CustomerTierMock) => void;
}

export function TierCard({ tier, onEdit }: TierCardProps) {
    const active = tier.status === "ACTIVE";

    return (
        <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
            <div
                className="h-1 w-full"
                style={{ backgroundColor: tier.colorCode }}
            />

            <div className="p-4">
                <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                        <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                            {tier.code}
                        </p>
                        <h2 className="mt-1 truncate text-xl font-black text-slate-900">
                            {tier.name}
                        </h2>
                    </div>

                    <span
                        className={`inline-flex h-7 shrink-0 items-center rounded-full px-2.5 text-xs font-bold ${
                            active
                                ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200"
                                : "bg-slate-100 text-slate-600 ring-1 ring-slate-200"
                        }`}
                    >
                        {active ? "Đang hoạt động" : "Ngừng áp dụng"}
                    </span>
                </div>

                <div className="my-4 h-px bg-slate-100" />

                <section className="grid gap-2.5">
                    <h3 className="text-sm font-black text-slate-800">
                        Điều kiện lên hạng
                    </h3>
                    <div className="grid gap-2 text-sm font-semibold text-slate-600">
                        <span className="rounded-xl bg-slate-50 px-3 py-2">
                            Từ {formatCurrency(tier.minSpending)}
                        </span>
                        <span className="rounded-xl bg-slate-50 px-3 py-2">
                            Ít nhất {tier.minOrderCount.toLocaleString("vi-VN")} đơn
                        </span>
                    </div>
                </section>

                <section className="mt-4 grid gap-2.5">
                    <h3 className="text-sm font-black text-slate-800">
                        Quyền lợi
                    </h3>
                    <span className="rounded-xl bg-emerald-50 px-3 py-2 text-sm font-bold text-emerald-700 ring-1 ring-emerald-100">
                        Giảm trực tiếp {tier.discountRate}%
                    </span>
                </section>

                <footer className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
                    <div className="inline-flex items-center gap-2 text-sm font-bold text-slate-600">
                        <Users size={17} aria-hidden="true" />
                        <span>{tier.customerCount.toLocaleString("vi-VN")} khách hàng</span>
                    </div>

                    <div className="flex items-center gap-2">
                        <button
                            className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-200"
                            onClick={() => onEdit?.(tier)}
                            type="button"
                        >
                            <Pencil size={15} aria-hidden="true" />
                            Chỉnh sửa
                        </button>
                        <button
                            aria-label={`Mở thao tác cho hạng ${tier.name}`}
                            className="grid size-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-200"
                            type="button"
                        >
                            <MoreHorizontal size={18} aria-hidden="true" />
                        </button>
                    </div>
                </footer>
            </div>
        </article>
    );
}

function formatCurrency(value: number) {
    return `${value.toLocaleString("vi-VN")}đ`;
}
