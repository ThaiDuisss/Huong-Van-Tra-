import { CalendarClock, PackageCheck, RefreshCw, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ReceiptDialog } from "../../sales/components/dialogs/ReceiptDialog";
import { formatCurrency, parseCurrencyInput } from "../../sales/services/money";
import {
    formatAvailabilityDate,
    formatQuantity,
    getScenarioLabel,
    posBackorderApi,
    type BackorderAvailability,
    type BackorderOrderDto
} from "../../sales/services/posBackorder.api";

const PAGE_SIZE = 20;

type AvailabilityState =
    | { status: "checking" }
    | { status: "done"; result: BackorderAvailability }
    | { status: "error"; message: string };

function getErrorMessage(error: unknown, fallback: string) {
    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string" &&
        error.message
    ) {
        return error.message;
    }

    return fallback;
}

function getItemName(item: NonNullable<BackorderOrderDto["items"]>[number]) {
    const name = item.productName ?? item.productNameSnapshot ?? item.sku ?? "Mặt hàng";

    return item.variantNameSnapshot ? `${name} (${item.variantNameSnapshot})` : name;
}

function getRemaining(order: BackorderOrderDto) {
    return Math.max(0, Number(order.debtAmount ?? 0));
}

export function BackorderOrdersPage() {
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [reloadKey, setReloadKey] = useState(0);
    const [orders, setOrders] = useState<BackorderOrderDto[]>([]);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [availability, setAvailability] = useState<Record<string, AvailabilityState>>({});
    const [fulfillOrder, setFulfillOrder] = useState<BackorderOrderDto | null>(null);
    const [cashReceived, setCashReceived] = useState(0);
    const [fulfilling, setFulfilling] = useState(false);
    const [fulfillError, setFulfillError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const [receiptOrderId, setReceiptOrderId] = useState<string | null>(null);
    const requestIdRef = useRef(0);

    async function checkOrder(orderId: string) {
        setAvailability((current) => ({ ...current, [orderId]: { status: "checking" } }));

        try {
            const result = await posBackorderApi.checkAvailability(orderId);
            setAvailability((current) => ({
                ...current,
                [orderId]: { status: "done", result }
            }));
        } catch (checkError) {
            setAvailability((current) => ({
                ...current,
                [orderId]: {
                    status: "error",
                    message: getErrorMessage(checkError, "Không kiểm tra được tồn kho.")
                }
            }));
        }
    }

    useEffect(() => {
        const requestId = ++requestIdRef.current;

        posBackorderApi
            .getWaitingOrders({ keyword, page: 0, size: PAGE_SIZE })
            .then(async (page) => {
                if (requestId !== requestIdRef.current) {
                    return;
                }

                setOrders(page.content ?? []);
                setTotalElements(page.totalElements ?? 0);
                setError("");
                setLoading(false);

                // Kiểm tra lần lượt để không dồn nhiều yêu cầu vào kho cùng lúc.
                for (const order of page.content ?? []) {
                    if (requestId !== requestIdRef.current) {
                        return;
                    }

                    await checkOrder(order.id);
                }
            })
            .catch((loadError: unknown) => {
                if (requestId !== requestIdRef.current) {
                    return;
                }

                setOrders([]);
                setTotalElements(0);
                setError(getErrorMessage(loadError, "Không tải được danh sách đơn chờ hàng."));
                setLoading(false);
            });
    }, [keyword, reloadKey]);

    function reload() {
        setLoading(true);
        setAvailability({});
        setReloadKey((current) => current + 1);
    }

    function submitSearch() {
        setLoading(true);
        setAvailability({});
        setKeyword(keywordInput.trim());
        setReloadKey((current) => current + 1);
    }

    function openFulfill(order: BackorderOrderDto) {
        setFulfillOrder(order);
        setCashReceived(getRemaining(order));
        setFulfillError("");
        setSuccessMessage("");
    }

    async function confirmFulfill() {
        if (!fulfillOrder || fulfilling) {
            return;
        }

        const remaining = getRemaining(fulfillOrder);

        if (remaining > 0 && cashReceived < remaining) {
            setFulfillError("Tiền khách đưa chưa đủ phần còn lại.");
            return;
        }

        setFulfilling(true);
        setFulfillError("");

        try {
            const completed = await posBackorderApi.fulfill(
                fulfillOrder.id,
                remaining > 0 ? cashReceived : undefined
            );
            setFulfillOrder(null);
            setSuccessMessage(
                `Đã giao hàng và hoàn tất đơn ${completed.orderCode ?? fulfillOrder.orderCode}.`
            );
            setReceiptOrderId(fulfillOrder.id);
            reload();
        } catch (fulfillFailure) {
            setFulfillError(getErrorMessage(fulfillFailure, "Không giao được hàng. Vui lòng thử lại."));
        } finally {
            setFulfilling(false);
        }
    }

    const fulfillRemaining = fulfillOrder ? getRemaining(fulfillOrder) : 0;

    return (
        <div className="grid h-full min-h-0 grid-rows-[auto_1fr] gap-4 p-5">
            <header className="flex flex-wrap items-center gap-3">
                <div className="mr-auto grid gap-0.5">
                    <h1 className="text-2xl font-black text-[#172317]">Đơn chờ hàng</h1>
                    <p className="text-sm font-semibold text-[#697684]">
                        Đơn đã thu tiền (đủ hoặc cọc), giao và trừ tồn khi có hàng. Đang chờ: {totalElements} đơn.
                    </p>
                </div>
                <form
                    className="flex items-center gap-2"
                    onSubmit={(event) => {
                        event.preventDefault();
                        submitSearch();
                    }}
                >
                    <label className="relative block">
                        <Search
                            aria-hidden="true"
                            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#697684]"
                            size={16}
                        />
                        <input
                            aria-label="Tìm đơn chờ hàng"
                            className="h-10 w-64 rounded-lg border border-[#dce5e1] bg-white pl-9 pr-3 text-sm font-semibold text-[#172317]"
                            onChange={(event) => setKeywordInput(event.target.value)}
                            placeholder="Mã đơn, tên hoặc SĐT khách"
                            value={keywordInput}
                        />
                    </label>
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] bg-white px-4 text-sm font-black text-[#536078]"
                        type="submit"
                    >
                        Tìm
                    </button>
                </form>
                <button
                    className="flex h-10 items-center gap-2 rounded-lg border border-[#dce5e1] bg-white px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                    disabled={loading}
                    onClick={reload}
                    type="button"
                >
                    <RefreshCw aria-hidden="true" size={16} />
                    Tải lại
                </button>
            </header>

            <div className="grid min-h-0 content-start gap-3 overflow-y-auto pb-4">
                {successMessage ? (
                    <p className="rounded-lg border border-hvt-green-200 bg-hvt-green-50 px-3 py-2 text-sm font-semibold text-hvt-green-900">
                        {successMessage}
                    </p>
                ) : null}

                {error ? (
                    <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700" role="alert">
                        {error}
                    </p>
                ) : null}

                {loading ? (
                    <p className="rounded-xl bg-white px-4 py-8 text-center text-sm font-semibold text-[#697684]">
                        Đang tải đơn chờ hàng...
                    </p>
                ) : null}

                {!loading && !error && orders.length === 0 ? (
                    <p className="rounded-xl bg-white px-4 py-10 text-center text-sm font-semibold text-[#697684]">
                        Không có đơn nào đang chờ hàng.
                    </p>
                ) : null}

                {!loading
                    ? orders.map((order) => {
                        const state = availability[order.id];
                        const ready = state?.status === "done" && state.result.ready;
                        const remaining = getRemaining(order);
                        const expectedAt =
                            state?.status === "done" && !state.result.ready
                                ? state.result.estimatedAvailableAt ?? order.expectedAvailableAt
                                : order.expectedAvailableAt;
                        const waitingLines =
                            state?.status === "done"
                                ? state.result.lines.filter((line) => line.scenario !== "AVAILABLE")
                                : [];

                        return (
                            <article
                                className="grid gap-3 rounded-2xl bg-white p-4 shadow-pos-panel"
                                key={order.id}
                            >
                                <div className="flex flex-wrap items-start gap-3">
                                    <div className="mr-auto grid gap-0.5">
                                        <h2 className="text-lg font-black text-[#172317]">{order.orderCode}</h2>
                                        <p className="text-sm font-semibold text-[#697684]">
                                            {order.customerName || "Khách lẻ"}
                                            {order.customerPhone ? ` · ${order.customerPhone}` : ""}
                                            {order.createdAt
                                                ? ` · Đặt ngày ${formatAvailabilityDate(order.createdAt)}`
                                                : ""}
                                        </p>
                                    </div>
                                    {state?.status === "checking" || !state ? (
                                        <span className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-bold text-[#536078]">
                                            Đang kiểm tra hàng...
                                        </span>
                                    ) : ready ? (
                                        <span className="flex items-center gap-1 rounded-md bg-hvt-green-50 px-2 py-1 text-xs font-bold text-hvt-green-900">
                                            <PackageCheck aria-hidden="true" size={14} />
                                            Đủ hàng, có thể giao
                                        </span>
                                    ) : state.status === "error" ? (
                                        <span className="rounded-md bg-red-50 px-2 py-1 text-xs font-bold text-red-700">
                                            {state.message}
                                        </span>
                                    ) : (
                                        <span className="flex items-center gap-1 rounded-md bg-amber-50 px-2 py-1 text-xs font-bold text-amber-800">
                                            <CalendarClock aria-hidden="true" size={14} />
                                            Chưa đủ hàng · dự kiến {formatAvailabilityDate(expectedAt)}
                                        </span>
                                    )}
                                </div>

                                <ul className="grid gap-1 text-sm font-semibold text-[#536078]">
                                    {(order.items ?? []).map((item) => (
                                        <li className="flex justify-between gap-3" key={item.id}>
                                            <span className="min-w-0 truncate">{getItemName(item)}</span>
                                            <span className="shrink-0 tabular-nums">× {formatQuantity(item.quantity)}</span>
                                        </li>
                                    ))}
                                </ul>

                                {waitingLines.length > 0 ? (
                                    <ul className="grid gap-1 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
                                        {waitingLines.map((line) => (
                                            <li key={line.productVariantId}>
                                                {line.itemName ?? "Mặt hàng"}: thiếu {formatQuantity(line.missingQuantity)} ·{" "}
                                                {getScenarioLabel(line.scenario)}
                                                {line.productionOrderCode
                                                    ? ` · Lệnh sản xuất ${line.productionOrderCode}`
                                                    : ""}
                                                {line.estimatedAvailableAt
                                                    ? ` · dự kiến ${formatAvailabilityDate(line.estimatedAvailableAt)}`
                                                    : ""}
                                            </li>
                                        ))}
                                    </ul>
                                ) : null}

                                <div className="flex flex-wrap items-end gap-x-6 gap-y-2 border-t border-[#e8eef0] pt-3">
                                    <dl className="mr-auto flex flex-wrap gap-x-6 gap-y-1 text-sm">
                                        <div>
                                            <dt className="text-xs font-bold text-[#697684]">Tổng tiền</dt>
                                            <dd className="font-black tabular-nums text-[#172317]">
                                                {formatCurrency(Number(order.finalAmount ?? 0))}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt className="text-xs font-bold text-[#697684]">
                                                {remaining > 0 ? "Đã cọc" : "Đã thu"}
                                            </dt>
                                            <dd className="font-black tabular-nums text-[#172317]">
                                                {formatCurrency(Number(order.paidAmount ?? 0))}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt className="text-xs font-bold text-[#697684]">Còn phải thu</dt>
                                            <dd
                                                className={`font-black tabular-nums ${
                                                    remaining > 0 ? "text-red-700" : "text-[#172317]"
                                                }`}
                                            >
                                                {formatCurrency(remaining)}
                                            </dd>
                                        </div>
                                    </dl>
                                    <button
                                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                                        disabled={state?.status === "checking"}
                                        onClick={() => {
                                            void checkOrder(order.id);
                                        }}
                                        type="button"
                                    >
                                        Kiểm tra hàng
                                    </button>
                                    <button
                                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:bg-neutral-300"
                                        disabled={!ready}
                                        onClick={() => openFulfill(order)}
                                        type="button"
                                    >
                                        Giao hàng
                                    </button>
                                </div>
                            </article>
                        );
                    })
                    : null}
            </div>

            {fulfillOrder ? (
                <div className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4" role="presentation">
                    <section
                        aria-labelledby="fulfill-backorder-title"
                        aria-modal="true"
                        className="grid w-full max-w-[460px] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                        role="dialog"
                    >
                        <header className="border-b border-[#e8eef0] px-5 py-4">
                            <h2 className="text-xl font-black text-[#172317]" id="fulfill-backorder-title">
                                Giao hàng đơn {fulfillOrder.orderCode}
                            </h2>
                            <p className="text-sm font-semibold text-[#697684]">
                                Hệ thống trừ tồn kho, hoàn tất đơn và in hóa đơn cho khách.
                            </p>
                        </header>

                        <div className="grid gap-3 px-5 py-4">
                            {fulfillRemaining > 0 ? (
                                <>
                                    <p className="text-sm font-semibold text-[#536078]">
                                        Khách đã cọc {formatCurrency(Number(fulfillOrder.paidAmount ?? 0))}. Còn phải thu{" "}
                                        <strong className="text-red-700">{formatCurrency(fulfillRemaining)}</strong>{" "}
                                        bằng tiền mặt.
                                    </p>
                                    <label className="grid gap-1 text-xs font-bold text-[#697684]">
                                        Tiền khách đưa
                                        <input
                                            className="h-11 rounded-lg border border-[#dce5e1] px-3 text-right text-lg font-black tabular-nums text-[#172317]"
                                            inputMode="numeric"
                                            onChange={(event) =>
                                                setCashReceived(parseCurrencyInput(event.target.value))
                                            }
                                            value={cashReceived > 0 ? cashReceived.toLocaleString("vi-VN") : ""}
                                        />
                                    </label>
                                    <p className="text-sm font-semibold text-[#536078]">
                                        Tiền thừa trả khách:{" "}
                                        <strong>{formatCurrency(Math.max(0, cashReceived - fulfillRemaining))}</strong>
                                    </p>
                                </>
                            ) : (
                                <p className="text-sm font-semibold text-[#536078]">
                                    Đơn đã thu đủ {formatCurrency(Number(fulfillOrder.finalAmount ?? 0))}. Xác nhận giao hàng cho khách.
                                </p>
                            )}

                            {fulfillError ? (
                                <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700" role="alert">
                                    {fulfillError}
                                </p>
                            ) : null}
                        </div>

                        <footer className="flex justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                            <button
                                className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                                disabled={fulfilling}
                                onClick={() => setFulfillOrder(null)}
                                type="button"
                            >
                                Đóng
                            </button>
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                                disabled={fulfilling || (fulfillRemaining > 0 && cashReceived < fulfillRemaining)}
                                onClick={() => {
                                    void confirmFulfill();
                                }}
                                type="button"
                            >
                                {fulfilling ? "Đang xử lý..." : "Xác nhận giao hàng"}
                            </button>
                        </footer>
                    </section>
                </div>
            ) : null}

            <ReceiptDialog orderId={receiptOrderId} onClose={() => setReceiptOrderId(null)} />
        </div>
    );
}
