import { FileText, ShoppingCart, Truck } from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import {
    StockAlertSeverityBadge,
    StockAlertStatusBadge
} from "../components/StockAlertComponents";
import {
    stockAlertTypeLabels,
    suggestedActionLabels
} from "../constants/stock-alert.constants";
import { stockAlertApi } from "../services/stockAlert.api";
import type {
    StockAlertDetail,
    StockAlertRole
} from "../types/stock-alert.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

export function StockAlertDetailPage() {
    const { alertId } = useParams();
    const { roles } = useAuth();
    const effectiveRole: StockAlertRole = roles?.includes("ADMIN")
        ? "ADMIN"
        : roles?.includes("BRANCH_MANAGER")
        ? "BRANCH_MANAGER"
        : roles?.includes("ACCOUNTANT")
        ? "ACCOUNTANT"
        : "WAREHOUSE";

    const [alert, setAlert] = useState<StockAlertDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [dismissReason, setDismissReason] = useState("");
    const [dismissUntil, setDismissUntil] = useState("");
    const [resolveReason, setResolveReason] = useState("Available đã vượt threshold.");

    const load = useCallback(() => {
        if (!alertId) return;
        let active = true;
        setLoading(true);
        setError("");
        void stockAlertApi.getStockAlert(alertId, effectiveRole).then((response) => {
            if (active) setAlert(response);
        }).catch((loadError: unknown) => {
            if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải chi tiết cảnh báo.");
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [alertId, effectiveRole]);

    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) cleanup = load();
        });
        return () => {
            cancelled = true;
            cleanup?.();
        };
    }, [load]);

    const canResolve = useMemo(() => Boolean(alert && alert.balance.available > alert.policy.reorderPoint), [alert]);
    if (!alertId) return <Navigate to="/management/inventory/alerts" replace />;

    async function run(action: () => Promise<StockAlertDetail>, fallback: string) {
        setSubmitting(true);
        setError("");
        try {
            setAlert(await action());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : fallback);
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
                <Link className="text-sm font-black text-[#159734]" to="/management/inventory/alerts">Quay lại cảnh báo</Link>
                <h1 className="text-2xl font-black text-[#172317]">{alert?.code ?? "Chi tiết cảnh báo"}</h1>
                {alert ? <p className="text-sm font-semibold text-[#697684]">{alert.sku.skuCode} · {alert.location.name} · {stockAlertTypeLabels[alert.type]}</p> : null}
            </div>
        </header>

        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}

        {alert ? <>
            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div className="grid gap-2">
                        <div className="flex flex-wrap gap-2"><StockAlertStatusBadge status={alert.status} /><StockAlertSeverityBadge severity={alert.severity} /></div>
                        <p className="text-sm font-semibold text-[#536078]">{alert.calculationExplanation}</p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                        <button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting || alert.status !== "OPEN"} onClick={() => void run(() => stockAlertApi.acknowledgeStockAlert({ actorRole: effectiveRole, alertId: alert.id, note: "Đã xem cảnh báo." }), "Không thể acknowledge.")} type="button">Acknowledge</button>
                        <button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting || !dismissReason.trim()} onClick={() => void run(() => stockAlertApi.dismissStockAlert({ actorRole: effectiveRole, alertId: alert.id, dismissUntil: dismissUntil ? new Date(dismissUntil).toISOString() : undefined, reason: dismissReason }), "Không thể dismiss.")} type="button">Dismiss</button>
                        <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting || (!canResolve && effectiveRole !== "ADMIN")} onClick={() => void run(() => stockAlertApi.resolveStockAlert({ actorRole: effectiveRole, adminOverride: effectiveRole === "ADMIN", alertId: alert.id, reason: resolveReason }), "Không thể resolve.")} type="button">Resolve</button>
                    </div>
                </div>
                <div className="grid gap-3 md:grid-cols-3">
                    <label className="grid gap-1 text-xs font-black text-[#536078]">Reason dismiss<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setDismissReason(event.currentTarget.value)} value={dismissReason} /></label>
                    <label className="grid gap-1 text-xs font-black text-[#536078]">Dismiss until<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setDismissUntil(event.currentTarget.value)} type="date" value={dismissUntil} /></label>
                    <label className="grid gap-1 text-xs font-black text-[#536078]">Resolve reason<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setResolveReason(event.currentTarget.value)} value={resolveReason} /></label>
                </div>
            </section>

            <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
                <Info label="On hand" value={formatVietnamNumber(alert.balance.onHand)} />
                <Info label="Reserved" value={formatVietnamNumber(alert.balance.reserved)} />
                <Info label="Blocked" value={formatVietnamNumber(alert.balance.blocked)} />
                <Info label="Available" value={formatVietnamNumber(alert.balance.available)} />
                <Info label="Minimum stock" value={formatVietnamNumber(alert.policy.minimumStock)} />
                <Info label="Reorder point" value={formatVietnamNumber(alert.policy.reorderPoint)} />
                <Info label="In-transit" value={formatVietnamNumber(alert.balance.inTransitQuantity)} />
                <Info label="Suggested quantity" value={formatVietnamNumber(alert.suggestedQuantity)} />
            </section>

            <Panel title="Suggested action">
                <div className="grid gap-3 lg:grid-cols-2">
                    {alert.internalSource ? <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-semibold text-[#536078]"><strong className="block text-[#172317]">{suggestedActionLabels.TRANSFER}</strong>Source: {alert.internalSource.sourceLocation.name}<br />Source available after safety stock: {formatVietnamNumber(alert.internalSource.sourceAvailableAfterSafetyStock)}<br />Suggested transfer quantity: {formatVietnamNumber(alert.internalSource.suggestedTransferQuantity)}<br />Expected arrival: {formatVietnamDateTime(alert.internalSource.expectedArrival)}</div> : <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-semibold text-[#536078]"><strong className="block text-[#172317]">{suggestedActionLabels.PURCHASE}</strong>Preferred supplier: {alert.sku.preferredSupplier}<br />Lead time: {formatVietnamNumber(alert.policy.supplierLeadTimeDays ?? 0)} ngày<br />Suggested purchase quantity: {formatVietnamNumber(alert.suggestedQuantity)}<br />Estimated value: {formatVnd(alert.suggestedQuantity * alert.sku.unitCost)}</div>}
                    <div className="flex flex-wrap content-start gap-2">
                        <button className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting || alert.suggestedAction !== "TRANSFER"} onClick={() => void run(() => stockAlertApi.createTransferFromAlert({ actorRole: effectiveRole, alertId: alert.id }), "Không thể tạo transfer draft.")} type="button"><Truck size={16} />Tạo yêu cầu chuyển kho</button>
                        <button className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting || alert.suggestedAction !== "PURCHASE"} onClick={() => void run(() => stockAlertApi.createPurchaseRequestFromAlert({ actorRole: effectiveRole, alertId: alert.id }), "Không thể tạo purchase draft.")} type="button"><ShoppingCart size={16} />Tạo yêu cầu mua hàng</button>
                        {alert.actionReference?.url ? <Link className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" to={alert.actionReference.url}><Truck size={16} />Mở transfer prefilled</Link> : null}
                        {alert.actionReference ? <span className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#f4faf5] px-3 text-sm font-black text-[#315f3c]"><FileText size={16} />{alert.actionReference.code} · {alert.actionReference.status}</span> : null}
                    </div>
                </div>
            </Panel>

            <Panel title="Batch breakdown">{alert.batchBreakdown.map((batch) => <p className="text-sm font-semibold text-[#536078]" key={batch.batchCode}>{batch.batchCode}: on hand {formatVietnamNumber(batch.onHand)}, reserved {formatVietnamNumber(batch.reserved)}, blocked {formatVietnamNumber(batch.blocked)}, EXP {formatVietnamDateTime(batch.expiryDate)}, {batch.status}</p>)}</Panel>
            <Panel title="Consumption trend"><p className="text-sm font-semibold text-[#536078]">7 ngày: {formatVietnamNumber(alert.consumptionTrend.last7Days)} · 30 ngày: {formatVietnamNumber(alert.consumptionTrend.last30Days)} · Trung bình/ngày: {formatVietnamNumber(alert.consumptionTrend.averageDailyConsumption)}</p><p className="text-sm font-semibold text-[#697684]">{alert.consumptionTrend.note}</p></Panel>
            <Panel title="Open production orders">{alert.openProductionOrders.map((order) => <p className="text-sm font-semibold text-[#536078]" key={order.id}>{order.code}: {formatVietnamNumber(order.quantity)} · due {formatVietnamDateTime(order.dueAt)}</p>)}</Panel>
            <Panel title="Open reservations">{alert.openReservations.map((reservation) => <p className="text-sm font-semibold text-[#536078]" key={reservation.id}>{reservation.code}: {formatVietnamNumber(reservation.quantity)} · hết hạn {formatVietnamDateTime(reservation.expiresAt)}</p>)}</Panel>
        </> : null}
    </section>;
}

function Panel({ children, title }: { children: ReactNode; title: string }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><h2 className="font-black text-[#172317]">{title}</h2>{children}</section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}
