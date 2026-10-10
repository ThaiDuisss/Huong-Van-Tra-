import { CalendarClock } from "lucide-react";

import {
    formatAvailabilityDate,
    formatQuantity,
    getScenarioLabel,
    type AvailabilityLineEstimate
} from "../../services/posBackorder.api";

export interface BackorderOffer {
    name: string;
    sku: string;
    estimate: AvailabilityLineEstimate;
}

interface BackorderDialogProps {
    offer: BackorderOffer;
    submitting: boolean;
    error: string;
    /** Khách đồng ý chờ: thêm vào đơn dưới dạng đặt trước. */
    onAccept(): void;
    /** Khách không chờ: không thêm vào đơn. */
    onDecline(): void;
}

const arrivalSourceLabels: Record<string, string> = {
    OPEN_RECEIPT: "Theo phiếu nhập",
    RECEIPT_HISTORY: "Theo lịch sử nhập",
    DEFAULT: "Ước tính mặc định"
};

function getDurationNote(estimate: AvailabilityLineEstimate) {
    if (estimate.productionDays == null) {
        return "";
    }

    const days = formatQuantity(estimate.productionDays);

    if (estimate.durationSource === "DEFAULT") {
        return `Thời gian sản xuất: ${days} ngày (mặc định, chưa có lịch sử sản xuất).`;
    }

    if (estimate.durationSource === "ALL_HISTORY") {
        return `Thời gian sản xuất: khoảng ${days} ngày (trung bình các mặt hàng đã sản xuất).`;
    }

    return `Thời gian sản xuất: khoảng ${days} ngày (trung bình các lần sản xuất mặt hàng này).`;
}

export function BackorderDialog({
    offer,
    submitting,
    error,
    onAccept,
    onDecline
}: BackorderDialogProps) {
    const { estimate } = offer;
    const inStock = Math.max(0, estimate.requestedQuantity - estimate.missingQuantity);
    const shortMaterials = (estimate.materials ?? []).filter(
        (material) => material.missingQuantity > 0
    );
    const canEstimate = estimate.scenario !== "NOT_PRODUCIBLE";

    return (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4" role="presentation">
            <section
                aria-labelledby="backorder-title"
                aria-modal="true"
                className="grid max-h-[92vh] w-full max-w-[560px] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start gap-3 border-b border-[#e8eef0] px-5 py-4">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-amber-50 text-amber-700">
                        <CalendarClock aria-hidden="true" size={22} />
                    </span>
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-black text-[#172317]" id="backorder-title">
                            Chưa đủ hàng, thiếu {formatQuantity(estimate.missingQuantity)}
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            {offer.name}
                            {offer.sku ? ` · ${offer.sku}` : ""}
                        </p>
                    </div>
                </header>

                <div className="grid gap-4 overflow-y-auto px-5 py-4">
                    <dl className="grid grid-cols-3 gap-2 text-center">
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Khách đặt</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {formatQuantity(estimate.requestedQuantity)}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Đang có</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {formatQuantity(inStock)}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Còn thiếu</dt>
                            <dd className="text-lg font-black tabular-nums text-red-700">
                                {formatQuantity(estimate.missingQuantity)}
                            </dd>
                        </div>
                    </dl>

                    <div className="grid gap-1 rounded-xl border border-hvt-green-200 bg-hvt-green-50 px-4 py-3">
                        <span className="text-xs font-bold uppercase text-hvt-green-900">
                            {getScenarioLabel(estimate.scenario)}
                        </span>
                        <strong className="text-2xl font-black text-[#172317]">
                            {canEstimate
                                ? `Dự kiến có hàng: ${formatAvailabilityDate(estimate.estimatedAvailableAt)}`
                                : "Chưa ước tính được ngày có hàng"}
                        </strong>
                        {canEstimate && estimate.estimatedWaitDays != null ? (
                            <span className="text-sm font-semibold text-[#536078]">
                                {estimate.estimatedWaitDays <= 0
                                    ? "Có thể có hàng trong hôm nay."
                                    : `Khách chờ khoảng ${estimate.estimatedWaitDays} ngày.`}
                            </span>
                        ) : null}
                    </div>

                    {estimate.message ? (
                        <p className="text-sm font-semibold leading-6 text-[#536078]">
                            {estimate.message}
                        </p>
                    ) : null}

                    {canEstimate && getDurationNote(estimate) ? (
                        <p className="text-sm font-semibold text-[#697684]">{getDurationNote(estimate)}</p>
                    ) : null}

                    {shortMaterials.length > 0 ? (
                        <div className="grid gap-2">
                            <h3 className="text-sm font-black text-[#172317]">Nguyên liệu còn thiếu</h3>
                            <ul className="grid gap-2">
                                {shortMaterials.map((material) => (
                                    <li
                                        className="grid gap-0.5 rounded-lg border border-[#e8eef0] px-3 py-2 text-sm"
                                        key={material.ingredientId}
                                    >
                                        <span className="font-bold text-[#172317]">
                                            {material.name ?? material.ingredientId}
                                        </span>
                                        <span className="font-semibold text-[#536078]">
                                            Cần {formatQuantity(material.requiredQuantity)}, đang có{" "}
                                            {formatQuantity(material.availableQuantity)}, thiếu{" "}
                                            {formatQuantity(material.missingQuantity)}
                                        </span>
                                        <span className="font-semibold text-[#697684]">
                                            Dự kiến về: {formatAvailabilityDate(material.expectedArrivalAt)}
                                            {material.receiptCode
                                                ? ` · Phiếu nhập ${material.receiptCode}`
                                                : material.arrivalSource
                                                    ? ` · ${arrivalSourceLabels[material.arrivalSource] ?? material.arrivalSource}`
                                                    : ""}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ) : null}

                    <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
                        {estimate.depositRecommended
                            ? "Hàng còn phải chờ nguyên liệu: nên thu tiền cọc, phần còn lại thu khi giao hàng."
                            : "Nếu khách đồng ý chờ, đơn được thu tiền trước và giao khi có hàng. Tồn kho chỉ bị trừ lúc giao."}
                    </p>

                    {error ? (
                        <p
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            role="alert"
                        >
                            {error}
                        </p>
                    ) : null}
                </div>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                        disabled={submitting}
                        onClick={onDecline}
                        type="button"
                    >
                        Khách không chờ
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={submitting}
                        onClick={onAccept}
                        type="button"
                    >
                        {submitting ? "Đang thêm..." : "Khách đồng ý chờ"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
