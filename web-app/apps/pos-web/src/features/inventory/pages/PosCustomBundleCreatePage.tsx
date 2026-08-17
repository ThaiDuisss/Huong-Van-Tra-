import { Search } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { maxBundleWeight } from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type { BundleIngredientOption, CustomBundleOptions } from "../types/custom-bundle.types";
import { formatVietnamNumber, formatVnd } from "../utils/inventory-format";

interface DraftLine {
    ingredientId: string;
    quantity: number;
}

export function PosCustomBundleCreatePage() {
    const navigate = useNavigate();
    const [options, setOptions] = useState<CustomBundleOptions>({ ingredients: [], locations: [] });
    const [search, setSearch] = useState("");
    const [category, setCategory] = useState("ALL");
    const [lines, setLines] = useState<DraftLine[]>([]);
    const [form, setForm] = useState({
        branchId: "",
        bundleName: "",
        customerName: "Khách lẻ",
        displayName: "",
        packagingType: "Hộp giấy kraft",
        packingNote: "",
        requestedPickupAt: "2026-08-02T18:00",
        targetWeight: 200,
        usagePurpose: "GIFT" as const
    });
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        void customBundleApi.getCustomBundleOptions().then((response) => {
            setOptions(response);
            setForm((current) => ({ ...current, branchId: response.locations[0]?.id ?? "" }));
        });
    }, []);

    const categories = ["ALL", ...Array.from(new Set(options.ingredients.map((item) => item.category)))];
    const filteredIngredients = options.ingredients.filter((item) => {
        const matchesSearch = `${item.skuCode} ${item.name}`.toLowerCase().includes(search.toLowerCase());
        const matchesCategory = category === "ALL" || item.category === category;
        return matchesSearch && matchesCategory;
    });
    const selectedLines = lines.map((line) => {
        const ingredient = options.ingredients.find((item) => item.id === line.ingredientId);
        return { ...line, ingredient };
    }).filter((line): line is DraftLine & { ingredient: BundleIngredientOption } => Boolean(line.ingredient));
    const totalWeight = selectedLines.reduce((sum, line) => sum + line.quantity, 0);
    const estimatedPrice = selectedLines.reduce((sum, line) => sum + line.quantity * line.ingredient.pricePerGram, 0);
    const weightWarning = Math.abs(totalWeight - form.targetWeight) > 10;

    function addIngredient(ingredient: BundleIngredientOption) {
        if (ingredient.expired || ingredient.blocked) {
            setError("Không thể chọn hàng hết hạn hoặc blocked.");
            return;
        }
        setLines((current) => {
            const exists = current.find((line) => line.ingredientId === ingredient.id);
            if (exists) return current.map((line) => line.ingredientId === ingredient.id ? { ...line, quantity: line.quantity + 10 } : line);
            return [...current, { ingredientId: ingredient.id, quantity: 10 }];
        });
        setError("");
    }

    async function saveAndReserve() {
        if (lines.length === 0) {
            setError("Bundle phải có ít nhất một nguyên liệu.");
            return;
        }
        if (totalWeight > maxBundleWeight) {
            setError("Tổng khối lượng vượt giới hạn cấu hình.");
            return;
        }
        try {
            setSubmitting(true);
            const created = await customBundleApi.createCustomBundle({
                ...form,
                lines: selectedLines.map((line) => ({ ingredientId: line.ingredientId, quantity: line.quantity, unit: line.ingredient.unit }))
            });
            const reserved = await customBundleApi.reserveCustomBundle(created.id);
            navigate(`/pos/custom-bundles/${reserved.id}`);
        } catch (saveError) {
            setError(saveError instanceof Error ? saveError.message : "Không thể tạo bundle.");
        } finally {
            setSubmitting(false);
        }
    }

    const checkout = (
        <aside className="grid h-full min-h-0 gap-3 rounded-2xl bg-white p-4 shadow-pos-panel">
            <h2 className="text-xl font-black text-[#172317]">Bundle hiện tại</h2>
            <div className="grid gap-2 overflow-y-auto">
                {selectedLines.map((line) => (
                    <div className="rounded-lg border border-[#e8eef0] p-3" key={line.ingredientId}>
                        <div className="flex justify-between gap-2">
                            <strong className="text-sm text-[#172317]">{line.ingredient.name}</strong>
                            <button className="text-xs font-black text-red-700" onClick={() => setLines((current) => current.filter((item) => item.ingredientId !== line.ingredientId))} type="button">Xóa</button>
                        </div>
                        <input className="mt-2 h-9 w-full rounded-lg border border-pos-border px-3 text-sm font-bold" min={1} onChange={(event) => setLines((current) => current.map((item) => item.ingredientId === line.ingredientId ? { ...item, quantity: Number(event.currentTarget.value) } : item))} type="number" value={line.quantity} />
                        <p className="mt-1 text-xs font-bold text-[#697684]">Available: {formatVietnamNumber(line.ingredient.branchAvailableQuantity)} {line.ingredient.unit}</p>
                    </div>
                ))}
            </div>
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setForm({...form,bundleName:e.currentTarget.value})} placeholder="Tên bundle tùy chọn" value={form.bundleName}/>
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setForm({...form,displayName:e.currentTarget.value})} placeholder="Tên hiển thị trên nhãn" value={form.displayName}/>
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setForm({...form,packagingType:e.currentTarget.value})} value={form.packagingType}><option>Hộp giấy kraft</option><option>Túi zip bạc</option><option>Hộp thiếc</option></select>
            <label className="text-xs font-black text-[#536078]">Khối lượng mục tiêu<input className="mt-1 h-10 w-full rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setForm({...form,targetWeight:Number(e.currentTarget.value)})} type="number" value={form.targetWeight}/></label>
            <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-bold text-[#536078]">
                <p>Tổng khối lượng: {formatVietnamNumber(totalWeight)} g</p>
                <p>Giá dự kiến: {formatVnd(estimatedPrice)}</p>
                {weightWarning ? <p className="text-amber-700">Tổng lượng khác khối lượng mục tiêu.</p> : null}
            </div>
            {error ? <div className="rounded-lg bg-red-50 p-3 text-sm font-bold text-red-700">{error}</div> : null}
            <button className="h-11 rounded-lg bg-[#159734] text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={() => void saveAndReserve()} type="button">{submitting ? "Đang giữ nguyên liệu..." : "Tạo và giữ nguyên liệu"}</button>
        </aside>
    );

    return (
        <PosShell checkout={checkout}>
            <section className="grid h-full min-h-0 gap-4">
                <header className="flex items-center justify-between gap-3">
                    <div>
                        <Link className="text-sm font-black text-[#159734]" to="/pos/sales">Quay lại POS</Link>
                        <h1 className="text-2xl font-black text-[#172317]">Custom Tea Bundle</h1>
                    </div>
                </header>
                <div className="flex gap-3">
                    <div className="relative flex-1"><Search className="absolute left-3 top-3 text-[#697684]" size={16}/><input className="h-10 w-full rounded-lg border border-pos-border pl-9 pr-3 text-sm font-bold" onChange={(e)=>setSearch(e.currentTarget.value)} placeholder="Tìm nguyên liệu, SKU..." value={search}/></div>
                    <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setCategory(e.currentTarget.value)} value={category}>{categories.map((item)=><option key={item} value={item}>{item === "ALL" ? "Tất cả" : item}</option>)}</select>
                </div>
                <div className="grid min-h-0 flex-1 grid-cols-2 gap-3 overflow-y-auto xl:grid-cols-3">
                    {filteredIngredients.map((ingredient) => (
                        <button className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 text-left shadow-pos-card disabled:opacity-50" disabled={ingredient.expired || ingredient.blocked} key={ingredient.id} onClick={() => addIngredient(ingredient)} type="button">
                            <strong className="text-sm text-[#172317]">{ingredient.name}</strong>
                            <span className="text-xs font-bold text-[#697684]">{ingredient.skuCode} · {ingredient.category}</span>
                            <span className="text-sm font-black text-[#159734]">Available: {formatVietnamNumber(ingredient.branchAvailableQuantity)} {ingredient.unit}</span>
                            <span className="text-xs font-bold text-[#697684]">Conversion: 1 {ingredient.unit} = 1 {ingredient.unit} bundle</span>
                            {ingredient.nearLowStock ? <span className="text-xs font-black text-amber-700">Sắp hết</span> : null}
                            {ingredient.expired || ingredient.blocked ? <span className="text-xs font-black text-red-700">Hết hạn hoặc blocked</span> : null}
                        </button>
                    ))}
                </div>
            </section>
        </PosShell>
    );
}
