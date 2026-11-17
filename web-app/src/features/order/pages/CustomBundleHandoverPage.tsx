import { PackageCheck, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";

import { ReceiptDialog } from "../../sales/components/dialogs/ReceiptDialog";
import { formatCurrency, parseCurrencyInput } from "../../sales/services/money";
import { formatAvailabilityDate, type BackorderOrderDto } from "../../sales/services/posBackorder.api";
import {
    bundlePackingStatusLabels,
    posCustomBundleApi,
    type BundlePackingStatus
} from "../../sales/services/posCustomBundle.api";

const PAGE_SIZE = 20;

const statusFilters: Array<{ value: BundlePackingStatus | "ALL"; label: string }> = [
    { value: "ALL", label: "Tất cả" },
    { value: "PACKED", label: "Đã đóng gói" },
    { value: "AWAITING_PACKING", label: "Chờ đóng gói" },
    { value: "WAITING_MATERIAL", label: "Chờ nguyên liệu" }
];

function getErrorMessage(error: unknown, fallback: string) {
    if (typeof error === "object" && error !== null && "message" in error
        && typeof error.message === "string" && error.message) {
        return error.message;
    }
    return fallback;
}

function bundleItems(order: BackorderOrderDto) {
    return (order.items ?? []).filter((item) => item.itemType === "CUSTOM_BUNDLE");
}

function allPacked(order: BackorderOrderDto) {
    const bundles = bundleItems(order);
    return bundles.length > 0 && bundles.every((item) => item.packingStatus === "PACKED");
}

function getRemaining(order: BackorderOrderDto) {
    return Math.max(0, Number(order.debtAmount ?? 0));
}

/** Quầy: đơn đã thu tiền có trà đóng theo yêu cầu, giao cho khách khi kho đóng gói xong. */
export function CustomBundleHandoverPage() {
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState<BundlePackingStatus | "ALL">("ALL");
    const [reloadKey, setReloadKey] = useState(0);
    const [orders, setOrders] = useState<BackorderOrderDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
    const [handoverOrder, setHandoverOrder] = useState<BackorderOrderDto | null>(null);
    const [cashReceived, setCashReceived] = useState(0);
    const [handoverError, setHandoverError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const [receiptOrderId, setReceiptOrderId] = useState<string | null>(null);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => setKeyword(keywordInput.trim()), 300);
        return () => window.clearTimeout(timeoutId);
    }, [keywordInput]);

    useEffect(() => {
        let active = true;
        const timeoutId = window.setTimeout(() => {
            setLoading(true);
            setError("");
            posCustomBundleApi
                .getAwaitingHandover({
                    keyword,
                    packingStatus: status === "ALL" ? undefined : status,
                    page: 0,
                    size: PAGE_SIZE
                })
                .then((page) => {
                    if (active) setOrders(page.content ?? []);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getErrorMessage(loadError, "Không tải được danh sách."));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        }, 0);
        return () => {
            active = false;
            window.clearTimeout(timeoutId);
        };
    }, [keyword, status, reloadKey]);

    async function refresh(order: BackorderOrderDto) {
        setBusyOrderId(order.id);
        setError("");
        try {
            const updated = await posCustomBundleApi.refreshPacking(order.id);
            setOrders((current) =>
                current.map((candidate) => (candidate.id === order.id ? { ...candidate, ...updated } : candidate))
            );
        } catch (refreshError) {
            setError(getErrorMessage(refreshError, "Không lấy được trạng thái đóng gói."));
        } finally {
            setBusyOrderId(null);
        }
    }

    async function handover() {
        if (!handoverOrder) return;
        const remaining = getRemaining(handoverOrder);
        if (remaining > 0 && cashReceived < remaining) {
            setHandoverError(`Khách còn phải trả ${formatCurrency(remaining)}.`);
            return;
        }
        setBusyOrderId(handoverOrder.id);
        setHandoverError("");
        try {
            const completed = await posCustomBundleApi.handover(
                handoverOrder.id,
                remaining > 0 ? cashReceived : undefined
            );
            setSuccessMessage(`Đã giao trà và hoàn tất đơn ${completed.orderCode ?? handoverOrder.orderCode}.`);
            setReceiptOrderId(handoverOrder.id);
            setHandoverOrder(null);
            setReloadKey((value) => value + 1);
        } catch (handoverFailure) {
            setHandoverError(getErrorMessage(handoverFailure, "Không giao được. Vui lòng thử lại."));
        } finally {
            setBusyOrderId(null);
        }
    }

    const handoverRemaining = handoverOrder ? getRemaining(handoverOrder) : 0;

    return (
        <section className="grid h-full min-h-0 grid-rows-[auto_auto_minmax(0,1fr)] gap-4 overflow-hidden p-4">
            <ListPageHeader
                actions={
                    <SearchInput
                        className="max-w-sm"
                        onChange={setKeywordInput}
                        placeholder="Mã đơn, khách hàng, mã đóng gói"
                        value={keywordInput}
                    />
                }
                description="Đơn có trà đóng theo yêu cầu đã thu tiền. Giao khi kho báo đã đóng gói."
                title="Bundle chờ giao"
            />

            <div className="grid gap-2">
                <nav className="flex gap-2 overflow-x-auto">
                    {statusFilters.map((filter) => (
                        <button
                            className={`h-9 shrink-0 rounded-lg px-4 text-sm font-black ${
                                status === filter.value ? "bg-[#159734] text-white" : "bg-white text-[#536078]"
                            }`}
                            key={filter.value}
                            onClick={() => setStatus(filter.value)}
                            type="button"
                        >
                            {filter.label}
                        </button>
                    ))}
                </nav>
                {successMessage ? (
                    <div className="rounded-lg bg-[#eaf7ec] px-3 py-2 text-sm font-bold text-[#159734]">{successMessage}</div>
                ) : null}
                {error ? <div className="rounded-lg bg-red-50 px-3 py-2 text-sm font-bold text-red-700">{error}</div> : null}
            </div>

            <div className="grid min-h-0 auto-rows-min gap-3 overflow-y-auto">
                {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
                {!loading && orders.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white p-10 text-center text-sm font-bold text-[#697684]">
                        Không có đơn chờ giao.
                    </div>
                ) : null}
                {orders.map((order) => {
                    const ready = allPacked(order);
                    const remaining = getRemaining(order);
                    return (
                        <article className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card" key={order.id}>
                            <div className="flex flex-wrap items-start justify-between gap-3">
                                <div className="grid gap-1">
                                    <strong className="text-base font-black text-[#172317]">{order.orderCode}</strong>
                                    <span className="text-sm font-semibold text-[#697684]">
                                        {order.customerName ?? "Khách lẻ"}
                                        {order.customerPhone ? ` · ${order.customerPhone}` : ""}
                                    </span>
                                </div>
                                <div className="grid justify-items-end gap-1 text-sm font-bold">
                                    <span>Tổng {formatCurrency(Number(order.finalAmount ?? 0))}</span>
                                    {remaining > 0 ? (
                                        <span className="text-amber-700">Còn thu {formatCurrency(remaining)}</span>
                                    ) : (
                                        <span className="text-[#159734]">Đã thu đủ</span>
                                    )}
                                </div>
                            </div>
                            <ul className="grid gap-1 text-sm">
                                {bundleItems(order).map((item) => (
                                    <li className="flex flex-wrap items-center justify-between gap-2" key={item.id}>
                                        <span className="font-bold text-[#172317]">
                                            {item.variantNameSnapshot ?? "Trà đóng theo yêu cầu"}
                                            {item.packingCode ? (
                                                <span className="ml-2 text-xs font-semibold text-[#697684]">{item.packingCode}</span>
                                            ) : null}
                                        </span>
                                        <StatusBadge>
                                            {item.packingStatus ? bundlePackingStatusLabels[item.packingStatus] : "Chưa gửi kho"}
                                        </StatusBadge>
                                    </li>
                                ))}
                            </ul>
                            {!ready && order.expectedAvailableAt ? (
                                <span className="text-xs font-bold text-amber-700">
                                    Dự kiến đủ nguyên liệu {formatAvailabilityDate(order.expectedAvailableAt)}
                                </span>
                            ) : null}
                            <div className="flex justify-end gap-2">
                                <Button disabled={busyOrderId === order.id} onClick={() => void refresh(order)}>
                                    <RefreshCw aria-hidden="true" size={16} />
                                    Làm mới
                                </Button>
                                <Button
                                    disabled={!ready || busyOrderId === order.id}
                                    onClick={() => {
                                        setHandoverOrder(order);
                                        setCashReceived(0);
                                        setHandoverError("");
                                    }}
                                    variant="primary"
                                >
                                    <PackageCheck aria-hidden="true" size={16} />
                                    Giao cho khách
                                </Button>
                            </div>
                        </article>
                    );
                })}
            </div>

            {handoverOrder ? (
                <ModalShell
                    className="grid w-full max-w-md gap-4 p-5"
                    closeOnBackdrop={false}
                    closeOnEscape={busyOrderId !== handoverOrder.id}
                    labelledBy="handover-bundle-title"
                    onClose={() => setHandoverOrder(null)}
                >
                    <h2 className="text-xl font-black text-[#172317]" id="handover-bundle-title">
                        Giao đơn {handoverOrder.orderCode}
                    </h2>
                    {handoverRemaining > 0 ? (
                        <Field label={`Khách đưa (còn phải thu ${formatCurrency(handoverRemaining)})`}>
                            <TextInput
                                inputMode="numeric"
                                onChange={(event) => setCashReceived(parseCurrencyInput(event.currentTarget.value))}
                                value={cashReceived > 0 ? cashReceived.toLocaleString("vi-VN") : ""}
                            />
                        </Field>
                    ) : (
                        <p className="text-sm font-semibold text-[#536078]">Đơn đã thu đủ tiền. Xác nhận đã giao trà cho khách.</p>
                    )}
                    {handoverError ? (
                        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">{handoverError}</p>
                    ) : null}
                    <div className="flex justify-end gap-2">
                        <Button disabled={busyOrderId === handoverOrder.id} onClick={() => setHandoverOrder(null)}>
                            Huỷ
                        </Button>
                        <Button
                            disabled={busyOrderId === handoverOrder.id}
                            onClick={() => void handover()}
                            variant="primary"
                        >
                            Xác nhận giao
                        </Button>
                    </div>
                </ModalShell>
            ) : null}

            <ReceiptDialog orderId={receiptOrderId} onClose={() => setReceiptOrderId(null)} />
        </section>
    );
}
