import { useState } from "react";
import { Link } from "react-router-dom";
import { AlertCircle, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { TextArea } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";

import { getReturnErrorMessage } from "../../order/utils/order-return.utils";
import { qualityApi } from "../services/quality.api";
import type {
    CustomerReturnInspection,
    CustomerReturnInspectionLine
} from "../types/customer-return-inspection.types";
import { formatVietnamNumber } from "../utils/inventory-format";

interface Props {
    inspection: CustomerReturnInspection;
    canRequest: boolean;
    onCreated: () => void;
}

function isDamagedLine(line: CustomerReturnInspectionLine) {
    return line.posted && line.result === "DAMAGED";
}

/**
 * Hàng lỗi (DAMAGED) đã nhập vị trí hàng lỗi sau QC. Tạo phiếu tiêu hủy để quản lý
 * phê duyệt; khi duyệt, kho trừ tồn tại vị trí hàng lỗi.
 */
export function DamagedReturnDisposalPanel({ inspection, canRequest, onCreated }: Props) {
    const [note, setNote] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [createdCode, setCreatedCode] = useState("");

    const damagedLines = inspection.lines.filter(isDamagedLine);
    if (inspection.status !== "POSTED" || damagedLines.length === 0) return null;
    const awaitingLines = damagedLines.filter((line) => !line.disposalRequestId);

    async function createRequest() {
        if (submitting || awaitingLines.length === 0) return;
        setSubmitting(true);
        setError("");
        try {
            const created = await qualityApi.createDamagedReturnDisposal({
                inspectionId: inspection.id,
                lineIds: awaitingLines.map((line) => line.id),
                note: note.trim() || undefined
            });
            setCreatedCode(created.code);
            setNote("");
            onCreated();
        } catch (cause: unknown) {
            setError(getReturnErrorMessage(cause, "Không thể tạo yêu cầu tiêu hủy hàng lỗi."));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section aria-label="Tiêu hủy hàng lỗi" className="grid gap-3 rounded-xl border border-[#f1d9b5] bg-[#fffaf2] p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="flex items-center gap-2 text-sm font-black uppercase text-[#8a5a00]">
                    <Trash2 size={16} aria-hidden="true" /> Tiêu hủy hàng lỗi
                </h3>
                <span className="text-xs font-semibold text-[#697684]">
                    {awaitingLines.length > 0
                        ? `${formatVietnamNumber(awaitingLines.length)} dòng chưa có phiếu tiêu hủy`
                        : "Mọi dòng hàng lỗi đã có phiếu tiêu hủy"}
                </span>
            </div>

            <ul className="grid gap-2">
                {damagedLines.map((line) => (
                    <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-white px-3 py-2 text-sm" key={line.id}>
                        <span className="min-w-0">
                            <strong className="font-black text-[#172317]">{line.itemName || "Sản phẩm"}</strong>
                            <span className="ml-2 text-[#536078]">
                                {formatVietnamNumber(Number(line.quantity))} · {line.destinationLocationName || "Vị trí hàng lỗi"}
                            </span>
                        </span>
                        {line.disposalRequestId ? (
                            <Link
                                className="inline-flex items-center gap-2 font-black text-[#159734] hover:underline"
                                to={`/management/inventory/disposals/${line.disposalRequestId}`}
                            >
                                {line.disposalRequestCode ?? "Phiếu tiêu hủy"}
                                <StatusBadge shape="tag" tone={line.disposalStatus === "APPROVED" ? "success" : "warning"}>
                                    {line.disposalStatus === "APPROVED" ? "Đã tiêu hủy" : "Chờ duyệt"}
                                </StatusBadge>
                            </Link>
                        ) : (
                            <StatusBadge shape="tag">Chưa tiêu hủy</StatusBadge>
                        )}
                    </li>
                ))}
            </ul>

            {createdCode ? (
                <p className="rounded-lg bg-green-50 p-3 text-sm font-bold text-green-800" role="status">
                    Đã tạo phiếu {createdCode}, chờ quản lý phê duyệt.
                </p>
            ) : null}
            {error ? (
                <p className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm font-bold text-red-700" role="alert">
                    <AlertCircle size={16} aria-hidden="true" />{error}
                </p>
            ) : null}

            {canRequest && awaitingLines.length > 0 ? (
                <div className="grid gap-2">
                    <TextArea
                        aria-label="Lý do tiêu hủy"
                        disabled={submitting}
                        maxLength={500}
                        onChange={(event) => setNote(event.target.value)}
                        placeholder="Lý do tiêu hủy (tùy chọn), ví dụ: vỡ không sửa được, hết hạn..."
                        rows={2}
                        value={note}
                    />
                    <div className="flex justify-end">
                        <Button disabled={submitting} onClick={() => void createRequest()} variant="primary">
                            <Trash2 size={16} aria-hidden="true" />
                            {submitting ? "Đang tạo…" : "Tạo yêu cầu tiêu hủy"}
                        </Button>
                    </div>
                </div>
            ) : null}
        </section>
    );
}
