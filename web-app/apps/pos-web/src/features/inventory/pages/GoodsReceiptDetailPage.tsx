import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    GoodsReceiptAuditTimeline,
    GoodsReceiptCostTable,
    GoodsReceiptEvidenceList,
    GoodsReceiptLifecycleStepper,
    GoodsReceiptLineTable,
    GoodsReceiptStatusBadge,
    QcBadge
} from "../components/GoodsReceiptComponents";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptDetail,
    GoodsReceiptQcInput,
    GoodsReceiptRole
} from "../types/goods-receipt.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

type ReceiptTab = "lines" | "overview" | "cost" | "documents" | "evidence" | "audit";

interface ReceiveDraftLine {
    lineId: string;
    materialName: string;
    note: string;
    orderedQuantity: number;
    receivedQuantity: number;
    unit: string;
}

interface QcDraftLine {
    acceptedQuantity: number;
    batchCode: string;
    expiryDate: string;
    lineId: string;
    manufactureDate: string;
    materialName: string;
    receivedQuantity: number;
    rejectedQuantity: number;
    supplierLot: string;
    unit: string;
}

export function GoodsReceiptDetailPage() {
    const { receiptId } = useParams();
    const { roles } = useAuth();
    const [receipt, setReceipt] = useState<GoodsReceiptDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [tab, setTab] = useState<ReceiptTab>("lines");
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);

    // Modals
    const [receiveOpen, setReceiveOpen] = useState(false);
    const [receiveDraft, setReceiveDraft] = useState<ReceiveDraftLine[]>([]);

    const [qcOpen, setQcOpen] = useState(false);
    const [qcDraft, setQcDraft] = useState<QcDraftLine[]>([]);

    const [postOpen, setPostOpen] = useState(false);
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectionReason, setRejectionReason] = useState("");
    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelNote, setCancelNote] = useState("");

    const [mutationLoading, setMutationLoading] = useState(false);

    const effectiveRole: GoodsReceiptRole = roles?.includes("ADMIN")
        ? "ADMIN"
        : roles?.includes("ACCOUNTANT")
        ? "ACCOUNTANT"
        : roles?.includes("BRANCH_MANAGER")
        ? "BRANCH_MANAGER"
        : roles?.includes("QC_STAFF")
        ? "QC_STAFF"
        : "WAREHOUSE";

    const loadReceipt = useCallback(() => {
        if (!receiptId) return;

        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void goodsReceiptApi
            .getGoodsReceipt(receiptId)
            .then((response) => {
                if (active) setReceipt(response);
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(
                        loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải chi tiết phiếu nhập."
                    );
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [receiptId]);

    useEffect(() => loadReceipt(), [loadReceipt]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 3000);
    }

    const canShowCost = hasAnyRole(roles, ["ADMIN", "ACCOUNTANT", "DIRECTOR", "BRANCH_MANAGER"]);

    // 1. Submit for Approval (DRAFT -> PENDING_APPROVAL)
    async function submitReceipt() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.submitGoodsReceipt({
                actorRole: effectiveRole,
                receiptId: receipt.id
            });
            setReceipt(updated);
            showToast("Gửi duyệt phiếu nhập thành công. Chờ Quản lý phê duyệt.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Gửi duyệt thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 2. Approve (PENDING_APPROVAL -> APPROVED)
    async function approveReceipt() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.approveGoodsReceipt({
                actorRole: effectiveRole,
                note: "Quản lý kho đã duyệt kế hoạch nhập nguyên liệu",
                receiptId: receipt.id
            });
            setReceipt(updated);
            showToast("Đã duyệt phiếu nhập nguyên liệu thành công.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Duyệt phiếu thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 3. Reject (PENDING_APPROVAL -> REJECTED)
    async function rejectReceipt() {
        if (!receipt) return;
        if (!rejectionReason.trim()) {
            showToast("Vui lòng nhập lý do từ chối.", false);
            return;
        }
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.rejectGoodsReceipt({
                actorRole: effectiveRole,
                reason: rejectionReason.trim(),
                receiptId: receipt.id
            });
            setReceipt(updated);
            setRejectOpen(false);
            showToast("Đã từ chối duyệt phiếu nhập.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Từ chối duyệt thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 4. Start Receiving (APPROVED -> RECEIVING)
    async function startReceiving() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.startReceivingGoodsReceipt({
                actorRole: effectiveRole,
                receiptId: receipt.id
            });
            setReceipt(updated);
            showToast("Đã chuyển sang trạng thái Tiếp nhận & Kiểm đếm hàng thực tế.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Thao tác thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 5. Open Receive Modal
    function openReceiveModal() {
        if (!receipt) return;
        setReceiveDraft(
            receipt.lines.map((l) => ({
                lineId: l.id,
                materialName: l.materialName,
                note: l.qc?.inspectionNote ?? "",
                orderedQuantity: l.orderedQuantity ?? 0,
                receivedQuantity: l.receivedQuantity || l.orderedQuantity || 0,
                unit: l.unit
            }))
        );
        setReceiveOpen(true);
    }

    // 6. Confirm Receiving (RECEIVING -> QC_PENDING)
    async function submitReceive() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.confirmReceivingGoodsReceipt(
                receipt.id,
                receiveDraft.map((l) => ({
                    lineId: l.lineId,
                    note: l.note,
                    receivedQuantity: l.receivedQuantity
                }))
            );
            setReceipt(updated);
            setReceiveOpen(false);
            showToast("Xác nhận kiểm đếm thực tế thành công! Phiếu đã chuyển sang Chờ kiểm định QC.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Xác nhận kiểm đếm thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 7. Open QC Modal
    function openQcModal() {
        if (!receipt) return;
        const now = new Date();
        const mfg = now.toISOString().split("T")[0];
        const nextYear = new Date(now.setFullYear(now.getFullYear() + 1)).toISOString().split("T")[0];

        setQcDraft(
            receipt.lines.map((l, index) => {
                const received = l.receivedQuantity || 0;
                const accepted = l.qc.passedQuantity || received;
                const rejected = l.qc.failedQuantity || 0;

                return {
                    acceptedQuantity: accepted,
                    batchCode: l.internalBatchCode || `LOT-${receipt.code}-${index + 1}`,
                    expiryDate: l.expiryDate || nextYear,
                    lineId: l.id,
                    manufactureDate: l.manufacturedAt || mfg,
                    materialName: l.materialName,
                    receivedQuantity: received,
                    rejectedQuantity: rejected,
                    supplierLot: l.supplierLot || "",
                    unit: l.unit
                };
            })
        );
        setQcOpen(true);
    }

    // 8. Submit QC (QC_PENDING -> QC_COMPLETED)
    async function submitQc() {
        if (!receipt) return;

        for (const line of qcDraft) {
            if (line.acceptedQuantity + line.rejectedQuantity !== line.receivedQuantity) {
                showToast(`Dòng ${line.materialName}: Tổng SL Đạt + Lỗi phải bằng Thực nhận (${line.receivedQuantity}).`, false);
                return;
            }
            if (line.acceptedQuantity > 0 && (!line.batchCode || !line.manufactureDate || !line.expiryDate)) {
                showToast(`Dòng ${line.materialName}: Vui lòng nhập đầy đủ thông tin lô hàng đạt chuẩn.`, false);
                return;
            }
        }

        const input: GoodsReceiptQcInput = {
            actorRole: effectiveRole,
            lines: qcDraft.map((l) => ({
                acceptedQuantity: l.acceptedQuantity,
                batches: l.acceptedQuantity > 0
                    ? [
                          {
                              batchCode: l.batchCode,
                              expiryDate: l.expiryDate,
                              manufactureDate: l.manufactureDate,
                              quantity: l.acceptedQuantity,
                              supplierLot: l.supplierLot
                          }
                      ]
                    : [],
                failedQuantity: l.rejectedQuantity,
                lineId: l.lineId,
                passedQuantity: l.acceptedQuantity,
                quarantinedQuantity: 0,
                receivedQuantity: l.receivedQuantity
            })),
            receiptId: receipt.id
        };

        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.performGoodsReceiptQc(input);
            setReceipt(updated);
            setQcOpen(false);
            showToast("Kiểm định QC thành công.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Kiểm định QC thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 9. Post (QC_COMPLETED -> POSTED)
    async function postReceipt() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.postGoodsReceipt({
                actorRole: effectiveRole,
                receiptId: receipt.id
            });
            setReceipt(updated);
            setPostOpen(false);
            showToast("Đã ghi sổ nhập kho thành công.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Ghi sổ nhập kho thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 10. Cancel (Before POSTED)
    async function cancelReceipt() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.cancelGoodsReceipt({
                actorRole: effectiveRole,
                note: cancelNote,
                receiptId: receipt.id
            });
            setReceipt(updated);
            setCancelOpen(false);
            showToast("Đã hủy phiếu nhập kho.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Hủy phiếu thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    if (!receiptId) {
        return <Navigate replace to="/management/inventory/receipts" />;
    }

    if (loading) {
        return (
            <section className="grid min-h-[300px] place-items-center rounded-xl bg-white p-8">
                <div className="text-sm font-bold text-[#315f3c]">Đang tải dữ liệu phiếu nhập...</div>
            </section>
        );
    }

    if (error || !receipt) {
        return (
            <section className="grid gap-3 rounded-xl border border-red-200 bg-red-50 p-6 text-sm font-bold text-red-700">
                <div>{error || "Không tìm thấy dữ liệu phiếu nhập."}</div>
                <div>
                    <button
                        className="rounded-lg bg-red-600 px-4 py-2 text-white hover:bg-red-700"
                        onClick={loadReceipt}
                        type="button"
                    >
                        Tải lại
                    </button>
                </div>
            </section>
        );
    }

    return (
        <section className="grid gap-4">
            {toast ? (
                <div
                    className={`fixed right-6 top-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-2xl transition-all ${
                        toast.success ? "bg-[#159734]" : "bg-red-600"
                    }`}
                >
                    {toast.message}
                </div>
            ) : null}

            <div className="flex flex-wrap items-center justify-between gap-3">
                <Link className="text-sm font-black text-[#159734] hover:underline" to="/management/inventory/receipts">
                    ← Quay lại danh sách phiếu nhập
                </Link>
            </div>

            {/* HEADER PHIẾU NHẬP */}
            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="text-xs font-bold text-[#697684]">
                            Kho vận › Phiếu nhập nguyên liệu từ Nhà cung cấp › Chi tiết
                        </div>
                        <div className="flex flex-wrap items-center gap-3">
                            <h1 className="text-2xl font-black text-[#172317] font-mono">{receipt.code}</h1>
                            <GoodsReceiptStatusBadge status={receipt.status} />
                            <QcBadge result={receipt.qcResult} />
                        </div>
                        <p className="text-sm font-semibold text-[#536078]">
                            <span className="font-bold text-[#172317]">Nhà cung cấp:</span> {receipt.supplier.name} ·{" "}
                            <span className="font-bold text-[#172317]">Kho nhận:</span> {receipt.receivingLocation.name} ·{" "}
                            <span className="font-bold text-[#172317]">Đơn đặt PO:</span> {receipt.purchaseOrderCode || "Ngoài PO"} ·{" "}
                            <span className="font-bold text-[#172317]">Ngày dự kiến:</span> {formatVietnamDateTime(receipt.receivedAt)}
                        </p>
                        {receipt.note ? (
                            <p className="text-xs font-semibold italic text-[#697684]">
                                Ghi chú: {receipt.note}
                            </p>
                        ) : null}
                    </div>

                    {/* VÙNG NÚT HÀNH ĐỘNG THEO TỪNG BƯỚC VÒNG ĐỜI */}
                    <div className="flex flex-wrap items-center gap-2">
                        {receipt.status === "DRAFT" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={submitReceipt}
                                type="button"
                            >
                                Gửi phê duyệt
                            </button>
                        ) : null}

                        {receipt.status === "PENDING_APPROVAL" ? (
                            <>
                                <button
                                    className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                    disabled={mutationLoading}
                                    onClick={approveReceipt}
                                    type="button"
                                >
                                    Phê duyệt phiếu nhập
                                </button>
                                <button
                                    className="inline-flex h-10 items-center rounded-lg border border-red-200 bg-white px-4 text-sm font-black text-red-600 hover:bg-red-50 disabled:opacity-50"
                                    disabled={mutationLoading}
                                    onClick={() => setRejectOpen(true)}
                                    type="button"
                                >
                                    Từ chối duyệt
                                </button>
                            </>
                        ) : null}

                        {receipt.status === "APPROVED" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={startReceiving}
                                type="button"
                            >
                                Tiếp nhận kiểm đếm thực tế
                            </button>
                        ) : null}

                        {receipt.status === "RECEIVING" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={openReceiveModal}
                                type="button"
                            >
                                Cập nhật số lượng kiểm đếm
                            </button>
                        ) : null}

                        {receipt.status === "QC_PENDING" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={openQcModal}
                                type="button"
                            >
                                Thực hiện Kiểm định QC & Lô
                            </button>
                        ) : null}

                        {receipt.status === "QC_COMPLETED" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={() => setPostOpen(true)}
                                type="button"
                            >
                                Ghi sổ Nhập kho khả dụng (Post)
                            </button>
                        ) : null}

                        {receipt.status !== "POSTED" &&
                        receipt.status !== "CANCELLED" &&
                        receipt.status !== "REJECTED" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg border border-[#e8eef0] bg-white px-3 text-sm font-bold text-[#697684] hover:bg-[#f7f8fb] disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={() => setCancelOpen(true)}
                                type="button"
                            >
                                Hủy phiếu
                            </button>
                        ) : null}
                    </div>
                </div>

                {/* THANH TIẾN TRÌNH VÒNG ĐỜI STEPPER */}
                <GoodsReceiptLifecycleStepper status={receipt.status} />
            </header>

            {/* THANH TAB NỘI DUNG */}
            <nav className="flex gap-2 overflow-x-auto pb-1">
                {[
                    ["lines", "Dòng hàng & Kiểm định"],
                    ["overview", "Tổng quan phiếu"],
                    ["cost", "Giá trị nhập kho"],
                    ["documents", "Chứng từ liên quan"],
                    ["evidence", "Ảnh & Biên bản"],
                    ["audit", "Lịch sử xử lý"]
                ].map(([value, label]) => (
                    <button
                        className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${tab === value
                            ? "bg-[#159734] text-white"
                            : "bg-white text-[#536078] hover:bg-[#f4faf5]"
                            }`}
                        key={value}
                        onClick={() => setTab(value as ReceiptTab)}
                        type="button"
                    >
                        {label}
                    </button>
                ))}
            </nav>

            {/* TAB: DÒNG HÀNG & KIỂM ĐỊNH */}
            {tab === "lines" ? (
                <GoodsReceiptLineTable receipt={receipt} />
            ) : null}

            {/* TAB: TỔNG QUAN PHIẾU */}
            {tab === "overview" ? (
                <div className="grid gap-3 md:grid-cols-3">
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4">
                        <div className="text-xs font-bold text-[#697684]">Tổng số dòng hàng</div>
                        <div className="text-lg font-black text-[#172317]">{receipt.totalLines} dòng</div>
                    </div>
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4">
                        <div className="text-xs font-bold text-[#697684]">Tổng số lượng thực nhận</div>
                        <div className="text-lg font-black text-[#159734]">{formatVietnamNumber(receipt.totalReceivedQuantity)}</div>
                    </div>
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4">
                        <div className="text-xs font-bold text-[#697684]">Kết quả kiểm tra QC</div>
                        <div className="text-lg font-black text-[#172317]"><QcBadge result={receipt.qcResult} /></div>
                    </div>
                </div>
            ) : null}

            {/* TAB: GIÁ TRỊ NHẬP KHO */}
            {tab === "cost" ? (
                canShowCost ? (
                    <GoodsReceiptCostTable receipt={receipt} />
                ) : (
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-bold text-[#697684]">
                        Tài khoản hiện tại không có quyền xem chi phí giá nhập.
                    </div>
                )
            ) : null}

            {/* TAB: CHỨNG TỪ LIÊN QUAN */}
            {tab === "documents" ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
                    <div className="text-sm font-bold text-[#172317]">
                        Mã chứng từ kho: <span className="font-mono text-[#159734]">{receipt.stockDocumentCode || "Chưa ghi sổ"}</span>
                    </div>
                    <div className="text-xs text-[#697684]">
                        Khi có số lượng thiếu, hệ thống tự động liên kết với Yêu cầu giao bù hàng (Supplement Request).
                        Khi có số lượng lỗi QC, hệ thống tự động liên kết với Yêu cầu trả hàng NCC (Supplier Return Request).
                    </div>
                </div>
            ) : null}

            {/* TAB: ĐÍNH KÈM & AUDIT */}
            {tab === "evidence" ? <GoodsReceiptEvidenceList receipt={receipt} /> : null}
            {tab === "audit" ? <GoodsReceiptAuditTimeline receipt={receipt} /> : null}

            {/* ============================================================ */}
            {/* 1. MODAL KIỂM ĐẾM THỰC TẾ (RECEIVING) */}
            {/* ============================================================ */}
            <Dialog open={receiveOpen} onOpenChange={setReceiveOpen}>
                <DialogContent className="max-w-[800px]">
                    <DialogHeader>
                        <DialogTitle>Giai đoạn 3: Tiếp nhận & Kiểm đếm thực tế</DialogTitle>
                        <DialogDescription>
                            Nhập số lượng thực nhận khi xe hàng giao đến kho. Hệ thống sẽ tự động so sánh với số lượng đặt theo PO và phát hiện chênh lệch Thừa/Thiếu.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="grid max-h-[60vh] gap-3 overflow-y-auto px-6 py-4">
                        {receiveDraft.map((line, index) => {
                            const shortage = Math.max(0, line.orderedQuantity - line.receivedQuantity);
                            const excess = Math.max(0, line.receivedQuantity - line.orderedQuantity);
                            return (
                                <div className="grid gap-2 rounded-lg border border-[#e8eef0] p-3.5 bg-[#fbfdfb]" key={line.lineId}>
                                    <div className="flex justify-between items-center font-bold text-sm text-[#172317]">
                                        <span>#{index + 1}. {line.materialName}</span>
                                        <span className="text-xs text-[#536078] bg-slate-100 px-2 py-1 rounded">
                                            Đặt hàng: <strong className="text-[#172317]">{formatVietnamNumber(line.orderedQuantity)}</strong> {line.unit}
                                        </span>
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 items-center mt-1">
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
                                                    setReceiveDraft((cur) =>
                                                        cur.map((item, idx) => (idx === index ? { ...item, receivedQuantity: val } : item))
                                                    );
                                                }}
                                            />
                                        </div>
                                        <div className="text-xs">
                                            {shortage > 0 ? (
                                                <div className="font-bold text-amber-700 bg-amber-50 border border-amber-200 p-2 rounded">
                                                    Thiếu: {formatVietnamNumber(shortage)} {line.unit} (Hệ thống sẽ tạo yêu cầu giao bù hàng)
                                                </div>
                                            ) : excess > 0 ? (
                                                <div className="font-bold text-cyan-700 bg-cyan-50 border border-cyan-200 p-2 rounded">
                                                    Giao thừa: {formatVietnamNumber(excess)} {line.unit} (Toàn bộ {formatVietnamNumber(line.receivedQuantity)} {line.unit} sẽ chuyển sang QC)
                                                </div>
                                            ) : (
                                                <div className="font-bold text-green-700 bg-green-50 border border-green-200 p-2 rounded">
                                                    ✓ Giao đủ {formatVietnamNumber(line.receivedQuantity)} {line.unit} theo đơn đặt
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setReceiveOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#0891b2] px-5 text-sm font-black text-white hover:bg-[#0e7490] disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void submitReceive()}
                            type="button"
                        >
                            {mutationLoading ? "Đang xử lý..." : "Xác nhận kiểm đếm & Chuyển sang QC"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ============================================================ */}
            {/* 2. MODAL KIỂM ĐỊNH QC & KHAI BÁO LÔ (QC_PENDING) */}
            {/* ============================================================ */}
            <Dialog open={qcOpen} onOpenChange={setQcOpen}>
                <DialogContent className="max-w-[900px]">
                    <DialogHeader>
                        <DialogTitle>Giai đoạn 4: Kiểm định chất lượng (QC) & Khai báo Lô</DialogTitle>
                        <DialogDescription>
                            Kiểm tra từng dòng nguyên liệu, nhập số lượng đạt chuẩn (Accepted) và lỗi (Rejected). Khai báo thông tin Lô hàng cho lượng nguyên liệu đạt chuẩn.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="grid max-h-[65vh] gap-4 overflow-y-auto px-6 py-4">
                        {qcDraft.map((line, index) => (
                            <div className="grid gap-3 rounded-lg border border-[#e8eef0] p-4 bg-[#fbfdfb]" key={line.lineId}>
                                <div className="flex justify-between items-center font-bold text-sm text-[#172317] border-b border-[#eef2f0] pb-2">
                                    <span>#{index + 1}. {line.materialName}</span>
                                    <span className="text-xs text-[#536078]">
                                        Thực nhận: <strong className="text-[#172317]">{formatVietnamNumber(line.receivedQuantity)}</strong> {line.unit}
                                    </span>
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                    <div>
                                        <label className="text-xs font-bold text-[#159734]">Số lượng đạt QC (Accepted):</label>
                                        <input
                                            className="mt-1 h-9 w-full rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-[#159734]"
                                            min="0"
                                            max={line.receivedQuantity}
                                            step="any"
                                            type="number"
                                            value={line.acceptedQuantity || ""}
                                            onChange={(e) => {
                                                const pass = Number(e.target.value) || 0;
                                                const fail = Math.max(0, line.receivedQuantity - pass);
                                                setQcDraft((cur) =>
                                                    cur.map((item, idx) => (idx === index ? { ...item, acceptedQuantity: pass, rejectedQuantity: fail } : item))
                                                );
                                            }}
                                        />
                                    </div>
                                    <div>
                                        <label className="text-xs font-bold text-red-600">Số lượng lỗi QC (Rejected - Trả NCC):</label>
                                        <input
                                            className="mt-1 h-9 w-full rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-red-600"
                                            min="0"
                                            max={line.receivedQuantity}
                                            step="any"
                                            type="number"
                                            value={line.rejectedQuantity || ""}
                                            onChange={(e) => {
                                                const fail = Number(e.target.value) || 0;
                                                const pass = Math.max(0, line.receivedQuantity - fail);
                                                setQcDraft((cur) =>
                                                    cur.map((item, idx) => (idx === index ? { ...item, acceptedQuantity: pass, rejectedQuantity: fail } : item))
                                                );
                                            }}
                                        />
                                    </div>
                                </div>

                                {line.acceptedQuantity > 0 ? (
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3 bg-[#f4faf5] p-3 rounded-lg border border-[#d8eadc]">
                                        <div>
                                            <label className="text-xs font-bold text-[#315f3c]">Mã Lô (Batch Code) *:</label>
                                            <input
                                                className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-bold font-mono"
                                                value={line.batchCode}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setQcDraft((cur) => cur.map((item, idx) => (idx === index ? { ...item, batchCode: val } : item)));
                                                }}
                                            />
                                        </div>
                                        <div>
                                            <label className="text-xs font-bold text-[#315f3c]">Ngày sản xuất (MFG) *:</label>
                                            <input
                                                className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-semibold"
                                                type="date"
                                                value={line.manufactureDate}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setQcDraft((cur) => cur.map((item, idx) => (idx === index ? { ...item, manufactureDate: val } : item)));
                                                }}
                                            />
                                        </div>
                                        <div>
                                            <label className="text-xs font-bold text-[#315f3c]">Hạn sử dụng (EXP) *:</label>
                                            <input
                                                className="mt-1 h-8 w-full rounded border border-[#dce5e1] px-2 text-xs font-semibold"
                                                type="date"
                                                value={line.expiryDate}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setQcDraft((cur) => cur.map((item, idx) => (idx === index ? { ...item, expiryDate: val } : item)));
                                                }}
                                            />
                                        </div>
                                    </div>
                                ) : null}
                            </div>
                        ))}
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setQcOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#7c3aed] px-5 text-sm font-black text-white hover:bg-[#6d28d9] disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void submitQc()}
                            type="button"
                        >
                            {mutationLoading ? "Đang lưu..." : "Hoàn tất QC & Tạo Lô"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ============================================================ */}
            {/* 3. MODAL GHI SỔ NHẬP KHO (QC_COMPLETED -> POSTED) */}
            {/* ============================================================ */}
            <Dialog open={postOpen} onOpenChange={setPostOpen}>
                <DialogContent className="max-w-[500px]">
                    <DialogHeader>
                        <DialogTitle>Giai đoạn 5: Ghi sổ nhập kho (Post Receipt)</DialogTitle>
                        <DialogDescription>
                            Xác nhận ghi sổ phiếu nhập kho. Tồn kho khả dụng (Available Balance) sẽ tăng đúng bằng số lượng đạt QC.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4 grid gap-3">
                        <div className="rounded-lg bg-[#f4faf5] border border-[#d8eadc] p-3 text-sm">
                            <p className="font-bold text-[#315f3c]">Tóm tắt cập nhật tồn kho:</p>
                            <ul className="mt-2 grid gap-1 text-xs font-semibold text-[#536078]">
                                {receipt.lines.map((l) => (
                                    <li key={l.id} className="flex justify-between">
                                        <span>{l.materialName}:</span>
                                        <strong className="text-[#159734]">+{formatVietnamNumber(l.qc.passedQuantity)} {l.unit}</strong>
                                    </li>
                                ))}
                            </ul>
                        </div>
                        <p className="text-xs text-[#697684]">
                            Sau khi ghi sổ, chứng từ sẽ được khóa (Read-only) và không thể chỉnh sửa.
                        </p>
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setPostOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#159734] px-5 text-sm font-black text-white hover:bg-[#12802c] disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void postReceipt()}
                            type="button"
                        >
                            {mutationLoading ? "Đang ghi sổ..." : "Xác nhận ghi sổ nhập kho"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ============================================================ */}
            {/* 4. MODAL TỪ CHỐI DUYỆT (REJECT) */}
            {/* ============================================================ */}
            <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Từ chối duyệt phiếu nhập</DialogTitle>
                        <DialogDescription>Nhập lý do từ chối phiếu nhập nguyên liệu này.</DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <textarea
                            className="w-full rounded-lg border border-[#dce5e1] p-3 text-sm outline-none focus:border-red-500"
                            placeholder="Ví dụ: Sai số lượng đặt, nhà cung cấp chưa xác nhận..."
                            rows={3}
                            value={rejectionReason}
                            onChange={(e) => setRejectionReason(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setRejectOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-red-600 px-4 text-sm font-black text-white hover:bg-red-700 disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void rejectReceipt()}
                            type="button"
                        >
                            {mutationLoading ? "Đang xử lý..." : "Xác nhận từ chối"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ============================================================ */}
            {/* 5. MODAL HỦY PHIẾU (CANCEL) */}
            {/* ============================================================ */}
            <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Hủy phiếu nhập kho</DialogTitle>
                        <DialogDescription>Bạn có chắc chắn muốn hủy phiếu nhập này không?</DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <textarea
                            className="w-full rounded-lg border border-[#dce5e1] p-3 text-sm outline-none focus:border-red-500"
                            placeholder="Nhập lý do hủy phiếu (tùy chọn)..."
                            rows={3}
                            value={cancelNote}
                            onChange={(e) => setCancelNote(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setCancelOpen(false)}
                            type="button"
                        >
                            Quay lại
                        </button>
                        <button
                            className="h-10 rounded-lg bg-red-600 px-4 text-sm font-black text-white hover:bg-red-700 disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void cancelReceipt()}
                            type="button"
                        >
                            {mutationLoading ? "Đang xử lý..." : "Xác nhận hủy phiếu"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
