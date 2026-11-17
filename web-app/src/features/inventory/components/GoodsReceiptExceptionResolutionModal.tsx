import { useState } from "react";
import { Button } from "../../../components/ui/Button";
import { TextArea } from "../../../components/ui/Field";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import type {
    GoodsReceiptDetail,
    GoodsReceiptLine,
    GoodsReceiptLineResolutionInput,
    ReceiptExcessResolution,
    ReceiptShortageResolution
} from "../types/goods-receipt.types";
import {
    EXCESS_RESOLUTION_LABELS,
    SHORTAGE_RESOLUTION_LABELS,
    getLineException,
    hasLineException,
    isLineResolved,
    previewPostedQuantity
} from "../utils/goodsReceiptException";
import { formatVietnamNumber } from "../utils/inventory-format";
import { LineExceptionBadges } from "./GoodsReceiptExceptionBadges";

interface Props {
    receipt: GoodsReceiptDetail;
    loading: boolean;
    onClose: () => void;
    onSubmit: (lines: GoodsReceiptLineResolutionInput[], approvalNote?: string) => Promise<void>;
}

const EXCESS_OPTIONS: ReceiptExcessResolution[] = ["ACCEPT_ALL", "ACCEPT_ORDERED_ONLY"];
const SHORTAGE_OPTIONS: ReceiptShortageResolution[] = ["ACCEPT_PARTIAL_CLOSE", "REJECT_LINE"];

/** Mount mỗi lần mở để lựa chọn luôn bắt đầu trống, quản lý phải chủ động chọn cho từng dòng ngoại lệ. */
export function GoodsReceiptExceptionResolutionModal({ receipt, loading, onClose, onSubmit }: Props) {
    const [resolutions, setResolutions] = useState<Record<string, GoodsReceiptLineResolutionInput>>({});
    const [approvalNote, setApprovalNote] = useState("");
    const [submitted, setSubmitted] = useState(false);

    const exceptionLines = receipt.lines.filter(hasLineException);
    const normalLines = receipt.lines.filter((line) => !hasLineException(line));
    const unresolvedCount = exceptionLines.filter((line) => !isLineResolved(line, resolutions[line.id])).length;

    const totals = receipt.lines.reduce(
        (sum, line) => {
            const resolution = resolutions[line.id];
            const posted = previewPostedQuantity(line, resolution);
            sum.posted += posted;
            sum.returned += line.qc.passedQuantity - posted;
            sum.damaged += line.qc.failedQuantity;
            return sum;
        },
        { damaged: 0, posted: 0, returned: 0 }
    );

    const updateResolution = (lineId: string, patch: Partial<GoodsReceiptLineResolutionInput>) => {
        setResolutions((current) => ({
            ...current,
            [lineId]: { ...current[lineId], lineId, ...patch }
        }));
    };

    const handleSubmit = async () => {
        setSubmitted(true);
        if (unresolvedCount > 0) return;
        await onSubmit(
            exceptionLines.map((line) => ({
                ...resolutions[line.id],
                lineId: line.id,
                note: resolutions[line.id]?.note?.trim() || undefined
            })),
            approvalNote.trim() || undefined
        );
    };

    return (
        <Dialog open onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="max-w-[1000px]">
                <DialogHeader>
                    <DialogTitle>Giai đoạn 5: Duyệt ngoại lệ & Ghi sổ nhập kho</DialogTitle>
                    <DialogDescription>
                        Chọn cách xử lý cho từng dòng giao thừa / giao thiếu. Hàng không đạt QC luôn được lập biên bản hủy, không nhập kho.
                    </DialogDescription>
                </DialogHeader>

                <div className="grid max-h-[60vh] gap-3 overflow-y-auto px-6 py-4">
                    {exceptionLines.map((line) => (
                        <ExceptionLineCard
                            key={line.id}
                            line={line}
                            resolution={resolutions[line.id]}
                            showError={submitted && !isLineResolved(line, resolutions[line.id])}
                            onChange={(patch) => updateResolution(line.id, patch)}
                        />
                    ))}

                    {normalLines.length > 0 ? (
                        <div className="rounded-lg border border-[#e8eef0] bg-[#fbfdfb] p-3">
                            <p className="text-xs font-black text-[#315f3c]">Dòng không có ngoại lệ (nhập kho bình thường)</p>
                            <ul className="mt-2 grid gap-1 text-xs font-semibold text-[#536078]">
                                {normalLines.map((line) => (
                                    <li key={line.id} className="flex justify-between">
                                        <span>{line.materialName}</span>
                                        <strong className="text-[#159734]">
                                            +{formatVietnamNumber(line.qc.passedQuantity)} {line.unit}
                                        </strong>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ) : null}
                </div>

                <div className="mx-6 mb-3 grid gap-2 rounded-lg border border-[#d8eadc] bg-[#f4faf5] p-3 text-xs font-bold text-[#536078] sm:grid-cols-3">
                    <SummaryItem label="Nhập kho" value={`+${formatVietnamNumber(totals.posted)}`} tone="text-[#159734]" />
                    <SummaryItem label="Trả lại NCC" value={formatVietnamNumber(totals.returned)} tone="text-cyan-700" />
                    <SummaryItem label="Hủy (không đạt QC)" value={formatVietnamNumber(totals.damaged)} tone="text-red-600" />
                </div>

                <div className="mx-6 mb-3 grid gap-1">
                    <label className="text-xs font-black text-[#315f3c]" htmlFor="exception-approval-note">
                        Ghi chú duyệt ngoại lệ
                    </label>
                    <TextArea
                        id="exception-approval-note"
                        maxLength={2000}
                        placeholder="Ví dụ: Đã trao đổi với NCC, đồng ý nhận phần thiếu và trả lại phần thừa"
                        rows={2}
                        value={approvalNote}
                        onChange={(e) => setApprovalNote(e.target.value)}
                    />
                </div>

                {submitted && unresolvedCount > 0 ? (
                    <p className="mx-6 mb-3 rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs font-bold text-red-700">
                        Còn {unresolvedCount} dòng ngoại lệ chưa chọn cách xử lý.
                    </p>
                ) : null}

                <DialogFooter>
                    <Button disabled={loading} onClick={onClose}>
                        Hủy
                    </Button>
                    <Button disabled={loading} onClick={() => void handleSubmit()} variant="primary">
                        {loading ? "Đang ghi sổ..." : "Duyệt ngoại lệ & Ghi sổ"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function ExceptionLineCard({
    line,
    resolution,
    showError,
    onChange
}: {
    line: GoodsReceiptLine;
    resolution?: GoodsReceiptLineResolutionInput;
    showError: boolean;
    onChange: (patch: Partial<GoodsReceiptLineResolutionInput>) => void;
}) {
    const { excess, shortage, damaged } = getLineException(line);
    const posted = previewPostedQuantity(line, resolution);
    const returned = line.qc.passedQuantity - posted;

    return (
        <div className={`grid gap-3 rounded-xl border p-4 ${showError ? "border-red-300 bg-red-50/40" : "border-[#e8eef0] bg-white"}`}>
            <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-black text-[#172317]">{line.materialName}</span>
                <LineExceptionBadges line={line} />
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs font-semibold text-[#536078] sm:grid-cols-4">
                <span>Số đặt: <strong className="text-[#172317]">{formatVietnamNumber(line.orderedQuantity ?? 0)}</strong></span>
                <span>Thực nhận: <strong className="text-[#172317]">{formatVietnamNumber(line.receivedQuantity)}</strong></span>
                <span>Đạt QC: <strong className="text-[#159734]">{formatVietnamNumber(line.qc.passedQuantity)}</strong></span>
                <span>Không đạt: <strong className="text-red-600">{formatVietnamNumber(line.qc.failedQuantity)}</strong></span>
            </div>

            {excess > 0 ? (
                <ResolutionOptions
                    name={`excess-${line.id}`}
                    title={`Giao thừa ${formatVietnamNumber(excess)} ${line.unit} — chọn cách xử lý:`}
                    options={EXCESS_OPTIONS.map((value) => ({ label: EXCESS_RESOLUTION_LABELS[value], value }))}
                    value={resolution?.excessResolution}
                    onChange={(value) => onChange({ excessResolution: value as ReceiptExcessResolution })}
                />
            ) : null}

            {shortage > 0 ? (
                <ResolutionOptions
                    name={`shortage-${line.id}`}
                    title={`Giao thiếu ${formatVietnamNumber(shortage)} ${line.unit} — chọn cách xử lý:`}
                    options={SHORTAGE_OPTIONS.map((value) => ({ label: SHORTAGE_RESOLUTION_LABELS[value], value }))}
                    value={resolution?.shortageResolution}
                    onChange={(value) => onChange({ shortageResolution: value as ReceiptShortageResolution })}
                />
            ) : null}

            {damaged > 0 ? (
                <p className="rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs font-bold text-red-700">
                    {formatVietnamNumber(damaged)} {line.unit} không đạt QC sẽ được lập biên bản hủy, không nhập kho.
                </p>
            ) : null}

            <TextArea
                aria-label="Ghi chú / lý do xử lý"
                maxLength={500}
                placeholder="Ghi chú / lý do xử lý (in trên biên bản)"
                rows={2}
                value={resolution?.note ?? ""}
                onChange={(e) => onChange({ note: e.target.value })}
            />

            <div className="flex flex-wrap gap-4 text-xs font-black">
                <span className="text-[#159734]">Nhập kho: +{formatVietnamNumber(posted)} {line.unit}</span>
                {returned > 0 ? (
                    <span className="text-cyan-700">Trả NCC: {formatVietnamNumber(returned)} {line.unit}</span>
                ) : null}
            </div>
        </div>
    );
}

function ResolutionOptions({
    name,
    title,
    options,
    value,
    onChange
}: {
    name: string;
    title: string;
    options: Array<{ label: string; value: string }>;
    value?: string;
    onChange: (value: string) => void;
}) {
    return (
        <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-xs font-black text-[#315f3c]">{title}</legend>
            {options.map((option) => (
                <label key={option.value} className="flex cursor-pointer items-center gap-2 text-sm font-semibold text-[#172317]">
                    <input
                        checked={value === option.value}
                        className="accent-[#159734]"
                        name={name}
                        onChange={() => onChange(option.value)}
                        type="radio"
                        value={option.value}
                    />
                    {option.label}
                </label>
            ))}
        </fieldset>
    );
}

function SummaryItem({ label, value, tone }: { label: string; value: string; tone: string }) {
    return (
        <div>
            <div>{label}</div>
            <div className={`text-base font-black ${tone}`}>{value}</div>
        </div>
    );
}
