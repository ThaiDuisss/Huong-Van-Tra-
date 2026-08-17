import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    stocktakeTypeLabels,
    stocktakeTypeOptions
} from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type {
    StocktakeCreateInput,
    StocktakeOptions,
    StocktakeRole,
    StocktakeType
} from "../types/stocktake.types";

function readRole(): StocktakeRole {
    const stored = window.localStorage.getItem("hvt.stocktake.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored)
        ? stored as StocktakeRole
        : "WAREHOUSE";
}

export function StocktakeCreatePage() {
    const navigate = useNavigate();
    const [role] = useState<StocktakeRole>(readRole);
    const [options, setOptions] = useState<StocktakeOptions>({ categories: [], locations: [], skus: [], users: [] });
    const [form, setForm] = useState({
        batchCodes: [] as string[],
        blindCount: true,
        categoryIds: [] as string[],
        counterRound1Id: "",
        counterRound2Id: "",
        locationId: "",
        note: "",
        scheduledStartAt: "2026-08-03T08:00",
        skuIds: [] as string[],
        type: "FULL" as StocktakeType
    });
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        void stocktakeApi.getStocktakeOptions().then((response) => {
            setOptions(response);
            setForm((current) => ({
                ...current,
                counterRound1Id: response.users.find((user) => user.role === role)?.id ?? response.users[0]?.id ?? "",
                counterRound2Id: response.users.find((user) => user.role !== role)?.id ?? "",
                locationId: role === "BRANCH_MANAGER"
                    ? response.locations.find((item) => !item.isCentral)?.id ?? ""
                    : response.locations[0]?.id ?? ""
            }));
        });
    }, [role]);

    const locationOptions = useMemo(() => {
        if (role === "BRANCH_MANAGER") return options.locations.filter((item) => !item.isCentral);
        return options.locations;
    }, [options.locations, role]);

    async function createStocktake() {
        setError("");
        const input: StocktakeCreateInput = {
            actorRole: role,
            batchCodes: form.batchCodes,
            blindCount: form.blindCount,
            categoryIds: form.categoryIds,
            counterRound1Id: form.counterRound1Id,
            counterRound2Id: form.counterRound2Id || undefined,
            locationId: form.locationId,
            note: form.note,
            scheduledStartAt: new Date(form.scheduledStartAt).toISOString(),
            skuIds: form.skuIds,
            type: form.type
        };
        try {
            setSubmitting(true);
            const stocktake = await stocktakeApi.createStocktake(input);
            navigate(`/management/inventory/stocktakes/${stocktake.id}`);
        } catch (createError) {
            setError(createError instanceof Error ? createError.message : "Không thể tạo kỳ kiểm kê.");
        } finally {
            setSubmitting(false);
        }
    }

    function toggleValue(key: "categoryIds" | "skuIds" | "batchCodes", value: string) {
        setForm((current) => {
            const values = current[key];
            return {
                ...current,
                [key]: values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
            };
        });
    }

    return (
        <section className="grid gap-4">
            <header className="grid gap-1">
                <Link className="text-sm font-black text-[#159734]" to="/management/inventory/stocktakes">Quay lại danh sách</Link>
                <h1 className="text-2xl font-black text-[#172317]">Tạo kỳ kiểm kê</h1>
                <p className="text-sm font-semibold text-[#697684]">Có thể tạo từ cảnh báo hoặc nghi ngờ chênh lệch bằng cách prefill phạm vi SKU/batch tương ứng.</p>
            </header>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-2 xl:grid-cols-3">
                <SelectField label="Location" onChange={(locationId) => setForm({ ...form, locationId })} options={locationOptions.map((item) => ({ label: item.name, value: item.id }))} value={form.locationId} />
                <SelectField label="Loại kiểm kê" onChange={(type) => setForm({ ...form, type: type as StocktakeType })} options={stocktakeTypeOptions.map((item) => ({ label: stocktakeTypeLabels[item], value: item }))} value={form.type} />
                <label className="flex h-10 items-center gap-2 self-end rounded-lg border border-[#d8eadc] px-3 text-sm font-black text-[#536078]">
                    <input checked={form.blindCount} onChange={(event) => setForm({ ...form, blindCount: event.currentTarget.checked })} type="checkbox" />
                    Blind count
                </label>
                <SelectField label="Người đếm vòng 1" onChange={(counterRound1Id) => setForm({ ...form, counterRound1Id })} options={options.users.map((item) => ({ label: item.name, value: item.id }))} value={form.counterRound1Id} />
                <SelectField label="Người đếm vòng 2" onChange={(counterRound2Id) => setForm({ ...form, counterRound2Id })} options={[{ label: "Không chọn", value: "" }, ...options.users.map((item) => ({ label: item.name, value: item.id }))]} value={form.counterRound2Id} />
                <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                    Scheduled start
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, scheduledStartAt: event.currentTarget.value })} type="datetime-local" value={form.scheduledStartAt} />
                </label>
                <label className="grid gap-1.5 text-xs font-black text-[#536078] md:col-span-2 xl:col-span-3">
                    Note
                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, note: event.currentTarget.value })} placeholder="Kiểm kê theo cảnh báo tồn hoặc nghi ngờ chênh lệch..." value={form.note} />
                </label>
            </section>

            {form.type === "CATEGORY" ? <ChoicePanel items={options.categories.map((item) => ({ label: item.name, value: item.id }))} onToggle={(value) => toggleValue("categoryIds", value)} selected={form.categoryIds} title="Chọn category" /> : null}
            {form.type === "SKU_SELECTED" ? <ChoicePanel items={options.skus.map((item) => ({ label: `${item.skuCode} - ${item.name}`, value: item.id }))} onToggle={(value) => toggleValue("skuIds", value)} selected={form.skuIds} title="Chọn SKU" /> : null}
            {form.type === "BATCH_SELECTED" ? <ChoicePanel items={options.skus.map((item) => ({ label: `${item.batchCode} · ${item.skuCode}`, value: item.batchCode }))} onToggle={(value) => toggleValue("batchCodes", value)} selected={form.batchCodes} title="Chọn batch" /> : null}

            <footer className="flex flex-wrap items-center justify-end gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                {error ? <div className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{error}</div> : null}
                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={() => void createStocktake()} type="button">
                    {submitting ? "Đang tạo..." : "Tạo kỳ kiểm kê"}
                </button>
            </footer>
        </section>
    );
}

function ChoicePanel({ items, onToggle, selected, title }: { items: Array<{ label: string; value: string }>; onToggle: (value: string) => void; selected: string[]; title: string }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <h2 className="text-base font-black text-[#172317]">{title}</h2>
            <div className="grid gap-2 md:grid-cols-3">
                {items.map((item) => (
                    <label className="flex items-center gap-2 rounded-lg bg-[#f7f8fb] p-3 text-sm font-bold text-[#536078]" key={item.value}>
                        <input checked={selected.includes(item.value)} onChange={() => onToggle(item.value)} type="checkbox" />
                        {item.label}
                    </label>
                ))}
            </div>
        </section>
    );
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ label: string; value: string }>; value: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} value={value}>
                {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
        </label>
    );
}
