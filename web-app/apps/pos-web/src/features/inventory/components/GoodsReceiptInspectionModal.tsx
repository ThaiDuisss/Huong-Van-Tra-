import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
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
    note?: string;
}

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
        if (line.acceptedQuantity > 0 && (!line.manufactureDate || !line.expiryDate)) {
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
                                        <span className="text-xs text-[#536078] bg-slate-100 px-2 py-1 rounded">
                                            Đặt hàng: <strong className="text-[#172317]">{formatVietnamNumber(line.orderedQuantity)}</strong> {line.unit}
                                        </span>
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 items-start mt-1">
                                        <div>
                                            <label className="text-xs font-bold text-[#536078]">Số lượng thực nhận ({line.unit}):</label>
                                            <input
                                                className="mt-1 h-9 w-full rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-[#172317]"
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
                                                <span className="text-xs text-[#536078] bg-slate-100 px-2.5 py-1 rounded-md">
                                                    Thực nhận: <strong className="text-[#172317]">{formatVietnamNumber(line.receivedQuantity)}</strong> {line.unit}
                                                </span>
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
                                                    <input
                                                        className="mt-1 h-9 w-full rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-[#159734] outline-none focus:border-[#159734]"
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
                                                    <input
                                                        className="mt-1 h-9 w-full rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-red-600 outline-none focus:border-red-500"
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
                                                    <input
                                                        className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-semibold text-[#172317] outline-none focus:border-[#159734] bg-white"
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
                                                    <input
                                                        className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-semibold text-[#172317] outline-none focus:border-[#159734] bg-white"
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
                                                <div className="flex-1 min-w-[130px]">
                                                    <label className="text-xs font-bold text-[#315f3c]">Hạn sử dụng (EXP) *:</label>
                                                    <input
                                                        className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-semibold text-[#172317] outline-none focus:border-[#159734] bg-white"
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
                                            </div>
                                        </div>
                                    );
                                    })}
                                </div>

                                {/* PAGINATION */}
                                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#edf0f4] px-6 py-2.5 text-xs bg-[#fafbfc]">
                                    <div className="text-[#536078] font-bold">
                                        Hiển thị <span className="text-[#172317]">{qcDraft.length > 0 ? startQcIdx + 1 : 0} - {endQcIdx}</span> trên tổng <span className="text-[#172317]">{qcDraft.length}</span> nguyên liệu
                                    </div>
                                    <div className="flex flex-wrap items-center gap-3">
                                        <div className="flex items-center gap-1.5 text-[#697684] font-bold">
                                            <span>Hiển thị:</span>
                                            <select
                                                className="h-8 rounded-lg border border-[#dce5e1] bg-white px-2 text-xs font-bold text-[#172317] outline-none focus:border-[#159734]"
                                                value={qcPageSize}
                                                onChange={(e) => {
                                                    onQcPageSizeChange(Number(e.target.value));
                                                    onQcPageChange(1);
                                                }}
                                            >
                                                <option value={3}>3 dòng / trang</option>
                                                <option value={5}>5 dòng / trang</option>
                                                <option value={10}>10 dòng / trang</option>
                                            </select>
                                        </div>
                                        {totalQcPages > 1 && (
                                            <div className="flex items-center gap-1">
                                                <button
                                                    type="button"
                                                    disabled={safeQcPage <= 1}
                                                    onClick={() => onQcPageChange(Math.max(1, safeQcPage - 1))}
                                                    className="grid size-8 place-items-center rounded-lg border border-[#dce5e1] text-[#536078] hover:bg-white disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                                >
                                                    <ChevronLeft size={16} />
                                                </button>
                                                {Array.from({ length: totalQcPages }, (_, i) => i + 1).map((p) => (
                                                    <button
                                                        key={p}
                                                        type="button"
                                                        onClick={() => onQcPageChange(p)}
                                                        className={`grid size-8 place-items-center rounded-lg text-xs font-black transition-colors cursor-pointer ${
                                                            p === safeQcPage
                                                                ? "bg-[#7c3aed] text-white shadow-sm"
                                                                : "border border-[#dce5e1] bg-white text-[#536078] hover:bg-[#f7f8fb]"
                                                        }`}
                                                    >
                                                        {p}
                                                    </button>
                                                ))}
                                                <button
                                                    type="button"
                                                    disabled={safeQcPage >= totalQcPages}
                                                    onClick={() => onQcPageChange(Math.min(totalQcPages, safeQcPage + 1))}
                                                    className="grid size-8 place-items-center rounded-lg border border-[#dce5e1] text-[#536078] hover:bg-white disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                                >
                                                    <ChevronRight size={16} />
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </div>
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
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078] hover:bg-[#f7f8fb] cursor-pointer"
                        disabled={isLoading}
                        onClick={() => onOpenChange(false)}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className={`h-10 rounded-lg px-5 text-sm font-black text-white disabled:opacity-60 cursor-pointer shadow-sm ${
                            tab === "receive"
                                ? "bg-[#0891b2] hover:bg-[#0e7490]"
                                : "bg-[#7c3aed] hover:bg-[#6d28d9]"
                        }`}
                        disabled={tab === "receive" ? isLoading : isQcSubmitDisabled}
                        onClick={tab === "receive" ? handleReceiveSubmit : handleQcSubmit}
                        type="button"
                        title={tab === "qc" && hasQcErrors ? "Vui lòng sửa các lỗi trước khi tiếp tục" : ""}
                    >
                        {isLoading
                            ? "Đang xử lý..."
                            : tab === "receive"
                                ? "Tiếp tục sang Kiểm định Chất lượng →"
                                : "Hoàn tất Kiểm định QC & Khai báo Lô"}
                    </button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
