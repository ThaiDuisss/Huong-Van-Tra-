import { Calculator, Layers3 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import {
    ReorderPolicyTable
} from "../components/StockAlertComponents";
import { alertLocations, alertSkus } from "../mocks/stock-alerts.mock";
import { inventoryMasterLocations } from "../mocks/inventory-master-data.mock";
import { stockAlertApi } from "../services/stockAlert.api";
import type {
    ReorderPolicy,
    ReorderPolicyQuery,
    ReorderPolicyUpdateInput,
    StockAlertRole
} from "../types/stock-alert.types";
import { formatVietnamNumber } from "../utils/inventory-format";

const initialQuery: ReorderPolicyQuery = {
    enabled: "ALL",
    locationId: "ALL",
    skuOrCategory: ""
};

export function ReorderPolicyPage() {
    const { roles } = useAuth();
    const effectiveRole: StockAlertRole = roles?.includes("ADMIN")
        ? "ADMIN"
        : roles?.includes("BRANCH_MANAGER")
        ? "BRANCH_MANAGER"
        : roles?.includes("ACCOUNTANT")
        ? "ACCOUNTANT"
        : "WAREHOUSE";

    const [query, setQuery] = useState(initialQuery);
    const [policies, setPolicies] = useState<ReorderPolicy[]>([]);
    const [editing, setEditing] = useState<ReorderPolicy | null>(null);
    const [form, setForm] = useState<ReorderPolicyUpdateInput | null>(null);
    const [bulkCategory, setBulkCategory] = useState("Trà xanh");
    const [bulkPreview, setBulkPreview] = useState<ReorderPolicy[]>([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void stockAlertApi.listReorderPolicies(query).then((response) => {
                if (active) setPolicies(response);
            }).catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải reorder policies.");
            }).finally(() => {
                if (active) setLoading(false);
            });
        });
        return () => { active = false; };
    }, [query]);

    const autoReorderPoint = useMemo(() => {
        if (!form) return 0;
        return ((form.averageDailyConsumption ?? 0) * (form.supplierLeadTimeDays ?? 0)) + (form.safetyStock ?? 0);
    }, [form]);

    const overrideRequired = Boolean(form && form.reorderPoint !== autoReorderPoint && !form.overrideReason?.trim());

    function openEdit(policy: ReorderPolicy) {
        setEditing(policy);
        setForm({ actorRole: effectiveRole, enabled: policy.enabled, averageDailyConsumption: policy.averageDailyConsumption, maximumStock: policy.maximumStock, minimumStock: policy.minimumStock, overrideReason: policy.overrideReason ?? "", policyId: policy.id, reorderPoint: policy.reorderPoint, safetyStock: policy.safetyStock, standardReorderQuantity: policy.standardReorderQuantity, supplierLeadTimeDays: policy.supplierLeadTimeDays });
        setError("");
    }

    async function savePolicy() {
        if (!form) return;
        setSubmitting(true);
        setError("");
        try {
            await stockAlertApi.updateReorderPolicy({ ...form, actorRole: effectiveRole });
            setEditing(null);
            setForm(null);
            setPolicies(await stockAlertApi.listReorderPolicies(query));
        } catch (saveError) {
            setError(saveError instanceof Error ? saveError.message : "Không thể lưu policy.");
        } finally {
            setSubmitting(false);
        }
    }

    async function previewBulk(apply: boolean) {
        setSubmitting(true);
        setError("");
        try {
            const response = await stockAlertApi.bulkUpdateReorderPolicies({
                actorRole: effectiveRole,
                category: bulkCategory,
                locationId: inventoryMasterLocations.branchCauGiay.id,
                patch: { safetyStock: 5, standardReorderQuantity: 25 },
                previewOnly: !apply
            });
            setBulkPreview(response);
            if (apply) setPolicies(await stockAlertApi.listReorderPolicies(query));
        } catch (bulkError) {
            setError(bulkError instanceof Error ? bulkError.message : "Không thể bulk apply.");
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <Link className="text-sm font-black text-[#159734]" to="/management/inventory/alerts">Quay lại cảnh báo</Link>
                <h1 className="text-2xl font-black text-[#172317]">Reorder Policies</h1>
                <p className="text-sm font-semibold text-[#697684]">Quản lý minimum stock, safety stock, reorder point và standard reorder quantity. Không gọi là EOQ khi chưa dùng công thức EOQ thật.</p>
            </div>
        </header>

        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3">
            <label className="grid gap-1 text-xs font-black text-[#536078]">Location<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, locationId: event.currentTarget.value })} value={query.locationId}><option value="ALL">Tất cả</option>{alertLocations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}</select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">SKU/category<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, skuOrCategory: event.currentTarget.value })} value={query.skuOrCategory} /></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Enabled<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, enabled: event.currentTarget.value as ReorderPolicyQuery["enabled"] })} value={query.enabled}><option value="ALL">Tất cả</option><option value="YES">Đang bật</option><option value="NO">Tắt</option></select></label>
        </section>

        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 font-black text-[#172317]"><Calculator size={18} />Công thức preview</div>
            <p className="text-sm font-semibold text-[#536078]">Reorder Point = Average Daily Consumption × Supplier Lead Time - Safety Stock</p>
            {form ? <div className="grid gap-3 md:grid-cols-4">
                <NumberField label="Minimum stock" onChange={(minimumStock) => setForm({ ...form, minimumStock })} value={form.minimumStock} />
                <NumberField label="Safety stock" onChange={(safetyStock) => setForm({ ...form, safetyStock })} value={form.safetyStock} />
                <NumberField label="Lead time" onChange={(supplierLeadTimeDays) => setForm({ ...form, supplierLeadTimeDays })} value={form.supplierLeadTimeDays ?? 0} />
                <NumberField label="Avg daily consumption" onChange={(averageDailyConsumption) => setForm({ ...form, averageDailyConsumption })} value={form.averageDailyConsumption ?? 0} />
                <NumberField label="Reorder point" onChange={(reorderPoint) => setForm({ ...form, reorderPoint })} value={form.reorderPoint} />
                <NumberField label="Standard reorder quantity" onChange={(standardReorderQuantity) => setForm({ ...form, standardReorderQuantity })} value={form.standardReorderQuantity} />
                <NumberField label="Maximum stock" onChange={(maximumStock) => setForm({ ...form, maximumStock })} value={form.maximumStock ?? 0} />
                <label className="grid gap-1 text-xs font-black text-[#536078]">Enabled<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, enabled: event.currentTarget.value === "YES" })} value={form.enabled ? "YES" : "NO"}><option value="YES">Đang bật</option><option value="NO">Tắt</option></select></label>
                <label className="grid gap-1 text-xs font-black text-[#536078] md:col-span-2">Override reason<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setForm({ ...form, overrideReason: event.currentTarget.value })} value={form.overrideReason ?? ""} /></label>
                <div className="rounded-lg bg-[#f4faf5] px-3 py-2 text-sm font-black text-[#315f3c]">Preview reorder point: {formatVietnamNumber(autoReorderPoint)}</div>
                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting || overrideRequired} onClick={() => void savePolicy()} type="button">{submitting ? "Đang lưu..." : `Lưu ${editing?.id ?? ""}`}</button>
                {overrideRequired ? <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm font-black text-amber-800 md:col-span-4">Override reorder point bắt buộc nhập lý do.</p> : null}
            </div> : <p className="text-sm font-semibold text-[#697684]">Chọn một policy để sửa inline.</p>}
        </section>

        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex flex-wrap items-end gap-3">
                <label className="grid gap-1 text-xs font-black text-[#536078]">Bulk category<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setBulkCategory(event.currentTarget.value)} value={bulkCategory}>{[...new Set(alertSkus.map((sku) => sku.category))].map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
                <button className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void previewBulk(false)} type="button"><Layers3 size={16} />Preview bulk</button>
                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting || bulkPreview.length === 0} onClick={() => void previewBulk(true)} type="button">Apply bulk</button>
            </div>
            {bulkPreview.length > 0 ? <p className="text-sm font-bold text-[#536078]">Preview ảnh hưởng {formatVietnamNumber(bulkPreview.length)} policy tại {inventoryMasterLocations.branchCauGiay.name}.</p> : null}
        </section>

        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
        {!loading && !error ? <ReorderPolicyTable onEdit={openEdit} policies={policies} /> : null}
    </section>;
}

function NumberField({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
    return <label className="grid gap-1 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" min={0} onChange={(event) => onChange(Number(event.currentTarget.value))} type="number" value={value} /></label>;
}
