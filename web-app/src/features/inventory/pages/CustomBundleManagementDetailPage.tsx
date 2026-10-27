import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import {
    customBundlePackagingTypes,
    customBundlePackingStatusLabels,
    customBundlePackingStatusTones
} from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type { CustomBundlePacking } from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Có lỗi xảy ra, vui lòng thử lại.";
}

/** Chi tiết yêu cầu đóng trà theo yêu cầu: lấy đủ nguyên liệu rồi xác nhận đóng gói. */
export function CustomBundleManagementDetailPage() {
    const { bundleId } = useParams();
    const [packing, setPacking] = useState<CustomBundlePacking | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [picked, setPicked] = useState<Set<string>>(new Set());
    const [packagingType, setPackagingType] = useState(customBundlePackagingTypes[0]);
    const [numberOfPackages, setNumberOfPackages] = useState("1");
    const [actualTotalWeight, setActualTotalWeight] = useState("");
    const [note, setNote] = useState("");

    const load = useCallback(async () => {
        if (!bundleId) return;
        setLoading(true);
        setError("");
        try {
            const response = await customBundleApi.getPacking(bundleId);
            setPacking(response);
            setPicked(new Set(response.lines.filter((line) => line.picked).map((line) => line.id)));
        } catch (loadError) {
            setError(getErrorMessage(loadError));
        } finally {
            setLoading(false);
        }
    }, [bundleId]);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => void load(), 0);
        return () => window.clearTimeout(timeoutId);
    }, [load]);

    function togglePicked(lineId: string) {
        setPicked((current) => {
            const next = new Set(current);
            if (next.has(lineId)) next.delete(lineId);
            else next.add(lineId);
            return next;
        });
    }

    async function recheck() {
        if (!packing) return;
        setSubmitting(true);
        setError("");
        setMessage("");
        try {
            const response = await customBundleApi.recheck(packing.id);
            setPacking(response);
            setMessage(
                response.status === "AWAITING_PACKING"
                    ? "Đã đủ nguyên liệu và giữ chỗ. Có thể đóng gói."
                    : "Vẫn còn thiếu nguyên liệu."
            );
        } catch (recheckError) {
            setError(getErrorMessage(recheckError));
        } finally {
            setSubmitting(false);
        }
    }

    async function confirm() {
        if (!packing) return;
        if (picked.size !== packing.lines.length) {
            setError("Cần xác nhận đã lấy đủ mọi nguyên liệu trước khi đóng gói.");
            return;
        }
        const packages = Number(numberOfPackages);
        const weight = actualTotalWeight.trim() ? Number(actualTotalWeight) : undefined;
        if (!Number.isInteger(packages) || packages <= 0) {
            setError("Số gói phải là số nguyên lớn hơn 0.");
            return;
        }
        if (weight !== undefined && (!Number.isFinite(weight) || weight <= 0)) {
            setError("Khối lượng thực phải lớn hơn 0.");
            return;
        }
        setSubmitting(true);
        setError("");
        setMessage("");
        try {
            const response = await customBundleApi.confirm(packing.id, {
                pickedLineIds: Array.from(picked),
                packagingType,
                numberOfPackages: packages,
                actualTotalWeight: weight,
                note: note.trim() || undefined
            });
            setPacking(response);
            setMessage(`Đã đóng gói, mã xuất kho ${response.issueTransactionCode ?? ""}. Quầy có thể giao cho khách.`);
        } catch (confirmError) {
            setError(getErrorMessage(confirmError));
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) {
        return <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div>;
    }

    if (!packing) {
        return (
            <div className="grid gap-3 rounded-xl bg-white p-6 text-sm font-bold">
                <span className="text-red-700">{error || "Không tìm thấy yêu cầu đóng gói."}</span>
                <Link className="text-[#159734]" to="/management/inventory/custom-bundles">
                    Quay lại danh sách
                </Link>
            </div>
        );
    }

    const editable = packing.status === "AWAITING_PACKING";

    return (
        <section className="grid gap-4">
            <header className="flex flex-wrap items-start justify-between gap-3">
                <div className="grid gap-1">
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/custom-bundles">
                        Đóng trà theo yêu cầu
                    </Link>
                    <h1 className="text-2xl font-black text-[#172317]">{packing.packingCode}</h1>
                    <p className="text-sm font-semibold text-[#697684]">
                        Đơn {packing.orderCode ?? "-"} · {packing.bundleName ?? "Trà đóng theo yêu cầu"}
                    </p>
                </div>
                <span className={`rounded-full px-3 py-1 text-sm font-black ${customBundlePackingStatusTones[packing.status]}`}>
                    {customBundlePackingStatusLabels[packing.status]}
                </span>
            </header>

            {packing.status === "WAITING_MATERIAL" ? (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">
                    <span>
                        Chưa đủ nguyên liệu, dự kiến có{" "}
                        {packing.expectedAvailableAt ? formatVietnamDateTime(packing.expectedAvailableAt) : "chưa xác định"}.
                        Khi phiếu nhập về, bấm kiểm tra lại để giữ nguyên liệu.
                    </span>
                    <button
                        className="h-10 rounded-lg bg-amber-600 px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={submitting}
                        onClick={() => void recheck()}
                        type="button"
                    >
                        Kiểm tra lại
                    </button>
                </div>
            ) : null}

            <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                <table className="w-full min-w-[640px] text-left text-sm">
                    <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                        <tr>
                            <th className="px-4 py-3">Đã lấy</th>
                            <th>Mã nguyên liệu</th>
                            <th>Nguyên liệu</th>
                            <th>Khối lượng</th>
                        </tr>
                    </thead>
                    <tbody>
                        {packing.lines.map((line) => (
                            <tr key={line.id}>
                                <td className="border-t border-[#edf0f4] px-4 py-3">
                                    <input
                                        aria-label={`Đã lấy ${line.ingredientName ?? ""}`}
                                        checked={picked.has(line.id)}
                                        className="h-5 w-5 accent-[#159734]"
                                        disabled={!editable}
                                        onChange={() => togglePicked(line.id)}
                                        type="checkbox"
                                    />
                                </td>
                                <td className="border-t border-[#edf0f4] font-bold text-[#536078]">{line.ingredientCode ?? "-"}</td>
                                <td className="border-t border-[#edf0f4] font-black text-[#172317]">{line.ingredientName ?? "-"}</td>
                                <td className="border-t border-[#edf0f4]">{formatVietnamNumber(line.requiredQuantity)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {editable ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
                    <label className="grid gap-1 text-xs font-black text-[#536078]">
                        Bao bì
                        <select
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold"
                            onChange={(event) => setPackagingType(event.currentTarget.value)}
                            value={packagingType}
                        >
                            {customBundlePackagingTypes.map((type) => (
                                <option key={type}>{type}</option>
                            ))}
                        </select>
                    </label>
                    <label className="grid gap-1 text-xs font-black text-[#536078]">
                        Số gói
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold"
                            min={1}
                            onChange={(event) => setNumberOfPackages(event.currentTarget.value)}
                            type="number"
                            value={numberOfPackages}
                        />
                    </label>
                    <label className="grid gap-1 text-xs font-black text-[#536078]">
                        Khối lượng thực (kể cả bao bì)
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold"
                            onChange={(event) => setActualTotalWeight(event.currentTarget.value)}
                            placeholder={formatVietnamNumber(packing.expectedTotalWeight)}
                            type="number"
                            value={actualTotalWeight}
                        />
                    </label>
                    <label className="grid gap-1 text-xs font-black text-[#536078]">
                        Ghi chú
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold"
                            maxLength={1000}
                            onChange={(event) => setNote(event.currentTarget.value)}
                            value={note}
                        />
                    </label>
                    <div className="md:col-span-4 flex justify-end">
                        <button
                            className="h-11 rounded-lg bg-[#159734] px-5 text-sm font-black text-white disabled:opacity-60"
                            disabled={submitting}
                            onClick={() => void confirm()}
                            type="button"
                        >
                            {submitting ? "Đang xác nhận..." : "Xác nhận đóng gói"}
                        </button>
                    </div>
                </div>
            ) : null}

            {packing.status === "PACKED" ? (
                <div className="grid gap-1 rounded-xl border border-[#cfe9d5] bg-[#eaf7ec] p-4 text-sm font-bold text-[#172317]">
                    <span>Mã xuất kho: {packing.issueTransactionCode ?? "-"}</span>
                    <span>
                        Đóng gói lúc: {packing.packedAt ? formatVietnamDateTime(packing.packedAt) : "-"} ·{" "}
                        {packing.packagingType ?? "-"} · {packing.numberOfPackages ?? "-"} gói
                    </span>
                    {packing.packingNote ? <span>Ghi chú: {packing.packingNote}</span> : null}
                </div>
            ) : null}

            {packing.status === "CANCELLED" ? (
                <div className="rounded-xl bg-neutral-100 p-4 text-sm font-bold text-neutral-700">
                    Đã huỷ{packing.cancelReason ? `: ${packing.cancelReason}` : ""}. Nguyên liệu đã giữ được trả lại kho.
                </div>
            ) : null}

            {message ? <div className="rounded-xl bg-[#eaf7ec] p-4 text-sm font-bold text-[#159734]">{message}</div> : null}
            {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
        </section>
    );
}
