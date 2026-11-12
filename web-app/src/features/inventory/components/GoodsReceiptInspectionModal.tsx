import { useState } from "react";

import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button } from "../../../components/ui/Button";
import { TextInput } from "../../../components/ui/Field";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { formatVietnamNumber } from "../utils/inventory-format";

interface ReceiveDraftLine {
    lineId: string;
    materialName: string;
    unit: string;
    orderedQuantity: number;
    receivedQuantity: number;
    note: string;
}

interface QcDraftLine {
    lineId: string;
    materialName: string;
    unit: string;
    receivedQuantity: number;
    acceptedQuantity: number;
    rejectedQuantity: number;
    supplierLot: string;
    manufactureDate: string;
    expiryDate: string;
    isIngredient: boolean;
    note?: string;
}

const QC_PAGE_SIZE_OPTIONS = [3, 5, 10] as const;

interface Props {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    tab: "receive" | "qc";
    onTabChange: (tab: "receive" | "qc") => void;
    receiveDraft: ReceiveDraftLine[];
    onReceiveChange: (line: ReceiveDraftLine[]) => void;
    qcDraft: QcDraftLine[];
    onQcChange: (line: QcDraftLine[]) => void;
    qcPage: number;
    onQcPageChange: (page: number) => void;
    qcPageSize: number;
    onQcPageSizeChange: (size: number) => void;
    onReceiveSubmit: () => Promise<void>;
    onQcSubmit: () => Promise<void>;
    loading: boolean;
}

export function GoodsReceiptInspectionModal({
    open,
    onOpenChange,
    tab,
    onTabChange,
    receiveDraft,
    onReceiveChange,
    qcDraft,
    onQcChange,
    qcPage,
    onQcPageChange,
    qcPageSize,
    onQcPageSizeChange,
    onReceiveSubmit,
    onQcSubmit,
    loading
}: Props) {
    const [localLoading, setLocalLoading] = useState(false);
    const [submitError, setSubmitError] = useState<string>("");

    const handleReceiveSubmit = async () => {
        setLocalLoading(true);
        try {
            await onReceiveSubmit();
            onTabChange("qc");
        } finally {
            setLocalLoading(false);
        }
    };

    const handleQcSubmit = async () => {
        setIsValidating(true);
        if (hasQcErrors) return;
        setSubmitError("");
        setLocalLoading(true);
        try {
            await onQcSubmit();
            onOpenChange(false);
        } catch (err: any) {
            setSubmitError(err?.message || "Có lỗi xảy ra khi hoàn tất kiểm định QC");
        } finally {
            setLocalLoading(false);
        }
    };

    const isLoading = loading || localLoading;

    // QC Pagination
    const totalQcPages = Math.max(1, Math.ceil(qcDraft.length / qcPageSize));
    const safeQcPage = Math.min(qcPage, totalQcPages);
    const startQcIdx = (safeQcPage - 1) * qcPageSize;
    const endQcIdx = Math.min(startQcIdx + qcPageSize, qcDraft.length);
    const paginatedQcDraft = qcDraft.slice(startQcIdx, endQcIdx);

    // QC Validation
    const getQcLineError = (line: QcDraftLine, isValidating: boolean = false): string | null => {
        if (!isValidating) return null;
        if (line.acceptedQuantity + line.rejectedQuantity !== line.receivedQuantity) {
            return `Tổng SL Đạt + Lỗi (${line.acceptedQuantity + line.rejectedQuantity}) phải bằng Thực nhận (${line.receivedQuantity})`;
        }
        if (line.acceptedQuantity > 0 && !line.manufactureDate) {
            return line.isIngredient
                ? "Vui lòng nhập Ngày sản xuất"
                : "Vui lòng nhập đầy đủ Ngày sản xuất & Hạn sử dụng";
        }
        if (line.acceptedQuantity > 0 && !line.isIngredient && !line.expiryDate) {
            return "Vui lòng nhập đầy đủ Ngày sản xuất & Hạn sử dụng";
        }
        return null;
    };

    const [isValidating, setIsValidating] = useState(false);
    const qcErrors = new Map(qcDraft.map((line, idx) => [idx, getQcLineError(line, isValidating)]));
    const hasQcErrors = Array.from(qcErrors.values()).some((error) => error !== null);
    const isQcSubmitDisabled = tab === "qc" && (isLoading || (isValidating && hasQcErrors));

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-[1200px]">
                <DialogHeader>
                    <DialogTitle>
                        {tab === "receive" ? "Giai đoạn 3: Kiểm đếm số lượng" : "Giai đoạn 4: Kiểm định chất lượng & Khai báo Lô"}
                    </DialogTitle>
                    <DialogDescription>
                        {tab === "receive"
                            ? "Nhập số lượng thực nhận khi xe hàng giao đến kho. Hệ thống sẽ tự động so sánh với số lượng đặt theo PO."
                            : "Kiểm tra từng dòng nguyên liệu, nhập số lượng đạt chuẩn (Accepted) và lỗi (Rejected). Khai báo thông tin Lô hàng."}
                    </DialogDescription>
                </DialogHeader>

                {/* TAB NAVIGATION */}
                <div className="flex gap-2 border-b border-[#e8eef0] px-6">
                    <button
                        onClick={() => onTabChange("receive")}
                        className={`px-4 py-3 text-sm font-bold transition-colors ${
                            tab === "receive"
                                ? "border-b-2 border-[#159734] text-[#159734]"
                                : "text-[#697684] hover:text-[#172317]"
                        }`}
                    >
                        📦 Kiểm đếm số lượng
                    </button>
                    <button
                        onClick={() => onTabChange("qc")}
                        className={`px-4 py-3 text-sm font-bold transition-colors ${
                            tab === "qc"
                                ? "border-b-2 border-[#159734] text-[#159734]"
                                : "text-[#697684] hover:text-[#172317]"
                        }`}
                    >
                        ✓ Kiểm định chất lượng
                    </button>
                </div>

                {/* RECEIVE TAB */}
                {tab === "receive" && (
                    <div className="grid max-h-[60vh] gap-3 overflow-y-auto px-6 py-4">
                        {receiveDraft.map((line, index) => {
                            const shortage = Math.max(0, line.orderedQuantity - line.receivedQuantity);
                            const excess = Math.max(0, line.receivedQuantity - line.orderedQuantity);
                            return (
                                <div key={line.lineId} className="grid gap-2 rounded-lg border border-[#e8eef0] p-3.5 bg-[#fbfdfb]">
                                    <div className="flex justify-between items-center font-bold text-sm text-[#172317]">
                                        <span>#{index + 1}. {line.materialName}</span>
                                        <StatusBadge shape="tag">
                                            Đặt hàng: <strong className="text-[#172317]">{formatVietnamNumber(line.orderedQuantity)}</strong> {line.unit}
                                        </StatusBadge>
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 items-start mt-1">
                                        <div>
                                            <label className="text-xs font-bold text-[#536078]">Số lượng thực nhận ({line.unit}):</label>
                                            <TextInput
                                                className="mt-1"
                                                min="0"
                                                step="any"
                                                type="number"
                                                value={line.receivedQuantity || ""}
                                                onChange={(e) => {
                                                    const val = Number(e.target.value) || 0;
                                                    onReceiveChange(
                                                        receiveDraft.map((item, idx) =>
                                                            idx === index ? { ...item, receivedQuantity: val } : item
                                                        )
                                                    );
                                                }}
                                            />
                                        </div>
                                        <div className="text-xs pt-7">
                                            {shortage > 0 ? (
                                                <div className="font-bold text-amber-700 bg-amber-50 border border-amber-200 p-2 rounded">
                                                    Thiếu: {formatVietnamNumber(shortage)} {line.unit}
                                                </div>
                                            ) : excess > 0 ? (
                                                <div className="font-bold text-cyan-700 bg-cyan-50 border border-cyan-200 p-2 rounded">
                                                    Giao thừa: {formatVietnamNumber(excess)} {line.unit}
                                                </div>
                                            ) : (
                                                <div className="font-bold text-green-700 bg-green-50 border border-green-200 p-2 rounded">
                                                    ✓ Giao đủ {formatVietnamNumber(line.receivedQuantity)} {line.unit}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}

                {/* QC TAB */}
                {tab === "qc" && (
                    <>
                        {qcDraft.length === 0 ? (
                            <div className="flex items-center justify-center py-12 px-6 text-center text-[#697684]">
                                <div>
                                    <div className="text-4xl mb-2">📋</div>
                                    <p className="font-bold text-sm">Chưa có dữ liệu kiểm định chất lượng</p>
                                    <p className="text-xs mt-1">Vui lòng hoàn tất bước kiểm đếm số lượng trước</p>
                                </div>
                            </div>
                        ) : (
                            <>
                                <div className="grid max-h-[60vh] gap-3.5 overflow-y-auto px-6 py-3">
                                    {paginatedQcDraft.map((line, relativeIdx) => {
                                    const globalIdx = startQcIdx + relativeIdx;
                                    const lineError = qcErrors.get(globalIdx);
                                    return (
                                        <div key={line.lineId} className="grid gap-3 rounded-xl border border-[#e8eef0] p-4 bg-[#fbfdfb] shadow-xs">
                                            <div className="flex justify-between items-center font-bold text-sm text-[#172317] border-b border-[#eef2f0] pb-2">
                                                <span className="flex items-center gap-2">
                                                    <span className="grid size-6 place-items-center rounded bg-[#edf8ef] text-xs font-black text-[#159734]">
                                                        #{globalIdx + 1}
                                                    </span>
                                                    <span>{line.materialName}</span>
                                                </span>
                                                <StatusBadge shape="tag">
                                                    Thực nhận: <strong className="text-[#172317]">{formatVietnamNumber(line.receivedQuantity)}</strong> {line.unit}
                                                </StatusBadge>
                                            </div>
                                            {lineError && (
                                                <div className="text-xs font-bold text-red-700 bg-red-100 border border-red-300 p-2.5 rounded-md flex items-start gap-2">
                                                    <span className="text-base mt-0.5">⚠️</span>
                                                    <span>{lineError}</span>
                                                </div>
                                            )}
                                            <div className="flex flex-col lg:flex-row gap-3 items-start flex-wrap">
                                                <div className="flex-1 min-w-[130px]">
                                                    <label className="text-xs font-bold text-[#159734]">Số lượng đạt:</label>
                                                    <TextInput
                                                        className="mt-1"
                                                        min="0"
                                                        max={line.receivedQuantity}
                                                        step="any"
                                                        type="number"
                                                        value={line.acceptedQuantity || ""}
                                                        onChange={(e) => {
                                                            const pass = Number(e.target.value) || 0;
                                                            const fail = Math.max(0, line.receivedQuantity - pass);
                                                            const idx = qcDraft.findIndex((l) => l.lineId === line.lineId);
                                                            onQcChange(
                                                                qcDraft.map((l, i) =>
                                                                    i === idx ? { ...l, acceptedQuantity: pass, rejectedQuantity: fail } : l
                                                                )
                                                            );
                                                        }}
                                                    />
                                                </div>
                                                <div className="flex-1 min-w-[130px]">
                                                    <label className="text-xs font-bold text-red-600">Số lượng lỗi:</label>
                                                    <TextInput
                                                        className="mt-1"
                                                        min="0"
                                                        max={line.receivedQuantity}
                                                        step="any"
                                                        type="number"
                                                        value={line.rejectedQuantity || ""}
                                                        onChange={(e) => {
                                                            const fail = Number(e.target.value) || 0;
                                                            const pass = Math.max(0, line.receivedQuantity - fail);
                                                            const idx = qcDraft.findIndex((l) => l.lineId === line.lineId);
                                                            onQcChange(
                                                                qcDraft.map((l, i) =>
                                                                    i === idx ? { ...l, acceptedQuantity: pass, rejectedQuantity: fail } : l
                                                                )
                                                            );
                                                        }}
                                                    />
                                                </div>
                                                <div className="flex-1 min-w-[150px]">
                                                    <label className="text-xs font-bold text-[#315f3c]">Mã lô NCC </label>
                                                    <TextInput
                                                        className="mt-1"
                                                        placeholder="LOT-2026-001"
                                                        type="text"
                                                        value={line.supplierLot}
                                                        onChange={(e) => {
                                                            const idx = qcDraft.findIndex((l) => l.lineId === line.lineId);
                                                            onQcChange(
                                                                qcDraft.map((l, i) =>
                                                                    i === idx ? { ...l, supplierLot: e.target.value } : l
                                                                )
                                                            );
                                                        }}
                                                    />
                                                </div>
                                                <div className="flex-1 min-w-[130px]">
                                                    <label className="text-xs font-bold text-[#315f3c]">Ngày sản xuất (MFG) *:</label>
                                                    <TextInput
                                                        className="mt-1"
                                                        type="date"
                                                        value={line.manufactureDate}
                                                        onChange={(e) => {
                                                            const idx = qcDraft.findIndex((l) => l.lineId === line.lineId);
                                                            onQcChange(
                                                                qcDraft.map((l, i) =>
                                                                    i === idx ? { ...l, manufactureDate: e.target.value } : l
                                                                )
                                                            );
                                                        }}
                                                    />
                                                </div>
                                                {!line.isIngredient && (
                                                <div className="flex-1 min-w-[130px]">
                                                    <label className="text-xs font-bold text-[#315f3c]">Hạn sử dụng (EXP) *:</label>
                                                    <TextInput
                                                        className="mt-1"
                                                        type="date"
                                                        value={line.expiryDate}
                                                        onChange={(e) => {
                                                            const idx = qcDraft.findIndex((l) => l.lineId === line.lineId);
                                                            onQcChange(
                                                                qcDraft.map((l, i) =>
                                                                    i === idx ? { ...l, expiryDate: e.target.value } : l
                                                                )
                                                            );
                                                        }}
                                                    />
                                                </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                    })}
                                </div>

                                <TablePagination
                                    itemLabel="nguyên liệu"
                                    onPageChange={(nextPage) => onQcPageChange(nextPage + 1)}
                                    onPageSizeChange={(size) => {
                                        onQcPageSizeChange(size);
                                        onQcPageChange(1);
                                    }}
                                    page={safeQcPage - 1}
                                    pageSize={qcPageSize}
                                    pageSizeOptions={QC_PAGE_SIZE_OPTIONS}
                                    totalItems={qcDraft.length}
                                    totalPages={totalQcPages}
                                />
                            </>
                        )}
                    </>
                )}

                {submitError && (
                    <div className="mx-6 mb-3 rounded-lg bg-red-50 border border-red-200 p-3">
                        <div className="flex gap-2">
                            <span className="text-red-500 font-bold">⚠️</span>
                            <div className="text-sm text-red-700 font-semibold">{submitError}</div>
                        </div>
                    </div>
                )}

                <DialogFooter className="sticky bottom-0 px-6 py-3 border-t border-[#eef2f0] bg-white">
                    <Button disabled={isLoading} onClick={() => onOpenChange(false)}>
                        Hủy
                    </Button>
                    <Button
                        disabled={tab === "receive" ? isLoading : isQcSubmitDisabled}
                        onClick={tab === "receive" ? handleReceiveSubmit : handleQcSubmit}
                        variant="primary"
                        title={tab === "qc" && hasQcErrors ? "Vui lòng sửa các lỗi trước khi tiếp tục" : ""}
                    >
                        {isLoading
                            ? "Đang xử lý..."
                            : tab === "receive"
                                ? "Tiếp tục sang Kiểm định Chất lượng →"
                                : "Hoàn tất Kiểm định QC & Khai báo Lô"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
