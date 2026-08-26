import { Link } from "react-router-dom";

import { stocktakeScopeLabels, stocktakeStatusLabels } from "../constants/stocktake.constants";
import type { StocktakeListQuery, StocktakeRole, StocktakeSummary } from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

export function StocktakeRoleSwitch({ onChange, value }: { onChange: (role: StocktakeRole) => void; value: StocktakeRole }) {
    return (
        <label className="grid gap-1 text-xs font-black text-[#536078]">
            Vai trò
            <select className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-black" onChange={(event) => onChange(event.currentTarget.value as StocktakeRole)} value={value}>
                <option value="WAREHOUSE">Nhân viên kho</option>
                <option value="BRANCH_MANAGER">Quản lý chi nhánh</option>
                <option value="ADMIN">Quản trị viên</option>
                <option value="DIRECTOR">Giám đốc</option>
                <option value="ACCOUNTANT">Kế toán</option>
                <option value="SALES_STAFF">Bán hàng</option>
            </select>
        </label>
    );
}

export function StocktakeSummaryCard({ label, value }: { label: string; value: number }) {
    return (
        <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <span className="text-sm font-black text-[#315f3c]">{label}</span>
            <strong className="text-2xl font-black text-[#172317]">{formatVietnamNumber(value)}</strong>
        </article>
    );
}

export function StocktakeFilterBar({
    onChange,
    onReset,
    query
}: {
    onChange: (patch: Partial<StocktakeListQuery>) => void;
    onReset: () => void;
    query: StocktakeListQuery;
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-3">
                <SelectField label="Chi nhánh" onChange={(branchId) => onChange({ branchId, page: 0 })} options={[{ label: "Tất cả", value: "ALL" }]} value={query.branchId} />
                <SelectField label="Trạng thái" onChange={(status) => onChange({ page: 0, status: status as StocktakeListQuery["status"] })} options={[{ label: "Tất cả", value: "ALL" }, ...Object.entries(stocktakeStatusLabels).map(([value, label]) => ({ label, value }))]} value={query.status} />
                <SelectField label="Phạm vi" onChange={(scopeType) => onChange({ page: 0, scopeType: scopeType as StocktakeListQuery["scopeType"] })} options={[{ label: "Tất cả", value: "ALL" }, ...Object.entries(stocktakeScopeLabels).map(([value, label]) => ({ label, value }))]} value={query.scopeType} />
            </div>
            <div className="flex justify-end">
                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button>
            </div>
        </section>
    );
}

export function StocktakeTable({ stocktakes }: { stocktakes: StocktakeSummary[] }) {
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[980px] text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                    <tr>
                        <th className="px-4 py-3">Mã</th>
                        <th>Chi nhánh</th>
                        <th>Phạm vi</th>
                        <th>Trạng thái</th>
                        <th>Tổng dòng</th>
                        <th>Đã đếm</th>
                        <th>Chênh lệch</th>
                        <th>Bắt đầu</th>
                        <th>Người tạo</th>
                    </tr>
                </thead>
                <tbody>
                    {stocktakes.map((stocktake) => (
                        <tr className="hover:bg-[#f8fbf8]" key={stocktake.id}>
                            <td className="border-t border-[#edf0f4] px-4 py-3 font-black">
                                <Link className="text-[#159734] hover:underline" to={`/management/inventory/stocktakes/${stocktake.id}`}>{stocktake.countCode}</Link>
                            </td>
                            <td className="border-t border-[#edf0f4]">{stocktake.branch.name}</td>
                            <td className="border-t border-[#edf0f4]">{stocktake.scopeLabel}</td>
                            <td className="border-t border-[#edf0f4]">{stocktakeStatusLabels[stocktake.status]}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(stocktake.totalLines)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(stocktake.countedLines)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamNumber(stocktake.varianceLines)}</td>
                            <td className="border-t border-[#edf0f4]">{formatVietnamDateTime(stocktake.countingStartedAt ?? stocktake.snapshotAt ?? "")}</td>
                            <td className="border-t border-[#edf0f4]">{stocktake.createdBy.name}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold text-[#172317]" onChange={(event) => onChange(event.currentTarget.value)} value={value}>
                {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
        </label>
    );
}
