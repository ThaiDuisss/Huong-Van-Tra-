import { AlertTriangle, CheckCircle2, PackageX } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";

import { DisposalTable } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type { DisposalListQuery, DisposalListResult } from "../types/quality.types";
import { formatVietnamNumber } from "../utils/inventory-format";

const initialQuery: DisposalListQuery = {
    code: "",
    locationId: "ALL",
    page: 0,
    reason: "",
    returnCode: "",
    size: 10,
    status: "ALL"
};

export function DisposalListPage() {
    const [query, setQuery] = useState<DisposalListQuery>(initialQuery);
    const [result, setResult] = useState<DisposalListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void qualityApi.listDisposalRequests(query)
                .then((response) => {
                    if (active) setResult(response);
                })
                .catch((loadError: unknown) => {
                    if (active) {
                        setError(loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải danh sách yêu cầu tiêu hủy.");
                    }
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });
        return () => { active = false; };
    }, [query]);

    const disposals = useMemo(() => result?.content ?? [], [result?.content]);
    const pending = disposals.filter((item) => item.status === "PENDING_APPROVAL").length;
    const approved = disposals.filter((item) => item.status === "APPROVED").length;
    const quantity = disposals.reduce(
        (sum, item) => sum + item.lines.reduce((lineSum, line) => lineSum + line.quantity, 0),
        0
    );

    return (
        <section className="grid gap-4">
            <header>
                <p className="text-xs font-black uppercase text-[#159734]">Quản lý / Kho</p>
                <h1 className="text-2xl font-black text-[#172317]">Phê duyệt tiêu hủy hàng trả</h1>
                <p className="text-sm font-semibold text-[#697684]">
                    Yêu cầu được tạo tự động khi phiếu kiểm định có dòng tiêu hủy. Duyệt sẽ ghi nhận WRITE_OFF đúng một lần.
                </p>
            </header>

            <section className="grid gap-3 md:grid-cols-3">
                <Summary icon={<AlertTriangle size={18} />} label="Chờ duyệt trong trang" value={formatVietnamNumber(pending)} />
                <Summary icon={<CheckCircle2 size={18} />} label="Đã duyệt trong trang" value={formatVietnamNumber(approved)} />
                <Summary icon={<PackageX size={18} />} label="Tổng số lượng trong trang" value={formatVietnamNumber(quantity)} />
            </section>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3">
                <label className="grid gap-1 text-xs font-black text-[#536078]">
                    Mã yêu cầu
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, code: event.currentTarget.value, page: 0 })} value={query.code} />
                </label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">
                    Mã trả hàng
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, returnCode: event.currentTarget.value })} value={query.returnCode ?? ""} />
                </label>
                <label className="grid gap-1 text-xs font-black text-[#536078]">
                    Trạng thái
                    <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, status: event.currentTarget.value as DisposalListQuery["status"] })} value={query.status}>
                        <option value="ALL">Tất cả</option>
                        <option value="PENDING_APPROVAL">Chờ duyệt</option>
                        <option value="APPROVED">Đã duyệt và ghi nhận</option>
                    </select>
                </label>
            </section>

            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
            {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
            {!loading && !error && disposals.length === 0 ? <div className="rounded-xl bg-white p-8 text-center text-sm font-bold text-[#697684]">Chưa có yêu cầu tiêu hủy phù hợp bộ lọc.</div> : null}
            {!loading && !error && disposals.length > 0 ? <DisposalTable disposals={disposals} /> : null}
            {result ? (
                <footer className="flex items-center justify-between rounded-xl bg-white p-3 text-sm font-bold text-[#536078]">
                    <span>Trang {result.page + 1}/{Math.max(result.totalPages, 1)} · {formatVietnamNumber(result.totalElements)} yêu cầu</span>
                    <div className="flex gap-2">
                        <button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page === 0} onClick={() => setQuery({ ...query, page: Math.max(0, query.page - 1) })} type="button">Trước</button>
                        <button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page + 1 >= result.totalPages} onClick={() => setQuery({ ...query, page: query.page + 1 })} type="button">Sau</button>
                    </div>
                </footer>
            ) : null}
        </section>
    );
}

function Summary({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
    return (
        <article className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{icon}</span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">{value}</strong>
        </article>
    );
}
