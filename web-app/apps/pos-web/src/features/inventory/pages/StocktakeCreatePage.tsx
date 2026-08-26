import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { stocktakeScopeLabels, stocktakeScopeOptions } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeCreateInput, StocktakeOptions, StocktakeScopeType } from "../types/stocktake.types";

export function StocktakeCreatePage() {
    const navigate = useNavigate();
    const [options, setOptions] = useState<StocktakeOptions>({ batches: [], branches: [], items: [], locations: [], users: [] });
    const [form, setForm] = useState({ branchId: "", note: "", scopeType: "FULL" as StocktakeScopeType });
    const [error, setError] = useState("");

    useEffect(() => {
        void stocktakeApi.getStocktakeOptions().then((response) => {
            setOptions(response);
            setForm((current) => ({ ...current, branchId: response.branches[0]?.id ?? "" }));
        });
    }, []);

    async function createStocktake() {
        const input: StocktakeCreateInput = {
            branchId: form.branchId,
            note: form.note,
            scopeType: form.scopeType
        };
        try {
            const stocktake = await stocktakeApi.createStocktake(input);
            navigate(`/management/inventory/stocktakes/${stocktake.id}`);
        } catch (createError) {
            setError(createError instanceof Error ? createError.message : "Không thể tạo phiên kiểm kê.");
        }
    }

    return (
        <section className="grid gap-4">
            <header className="grid gap-1">
                <Link className="text-sm font-black text-[#159734]" to="/management/inventory/stocktakes">Quay lại danh sách</Link>
                <h1 className="text-2xl font-black text-[#172317]">Tạo phiên kiểm kê</h1>
                <p className="text-sm font-semibold text-[#697684]">Chọn chi nhánh và phạm vi kiểm kê theo spec inventory counting.</p>
            </header>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-2">
                <Field label="Chi nhánh">
                    <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, branchId: event.currentTarget.value })} value={form.branchId}>
                        {options.branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
                    </select>
                </Field>
                <Field label="Phạm vi">
                    <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, scopeType: event.currentTarget.value as StocktakeScopeType })} value={form.scopeType}>
                        {stocktakeScopeOptions.map((scopeType) => <option key={scopeType} value={scopeType}>{stocktakeScopeLabels[scopeType]}</option>)}
                    </select>
                </Field>
                <label className="grid gap-1.5 text-xs font-black text-[#536078] md:col-span-2">
                    Ghi chú
                    <textarea className="min-h-28 rounded-lg border border-pos-border px-3 py-2 text-sm font-bold" onChange={(event) => setForm({ ...form, note: event.currentTarget.value })} value={form.note} />
                </label>
            </section>

            <footer className="flex items-center justify-end gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                {error ? <div className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{error}</div> : null}
                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={() => void createStocktake()} type="button">Tạo phiên kiểm kê</button>
            </footer>
        </section>
    );
}

function Field({ children, label }: { children: React.ReactNode; label: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            {children}
        </label>
    );
}
