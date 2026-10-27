import { Pencil, Printer } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuthorization } from "../../../auth/role-utils";
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
    GoodsReceiptLifecycleStepper,
    GoodsReceiptLineTable,
    GoodsReceiptStatusBadge,
    QcBadge
} from "../components/GoodsReceiptComponents";
import { GoodsReceiptExceptionResolutionModal } from "../components/GoodsReceiptExceptionResolutionModal";
import { GoodsReceiptInspectionModal } from "../components/GoodsReceiptInspectionModal";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptDetail,
    GoodsReceiptLineResolutionInput,
    GoodsReceiptQcInput
} from "../types/goods-receipt.types";
import { hasLineException } from "../utils/goodsReceiptException";
import { printExceptionReport } from "../utils/goodsReceiptExceptionReport";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

type ReceiptTab = "lines" | "overview" | "cost" | "documents" | "evidence" | "audit";

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

export function GoodsReceiptDetailPage() {
    const { receiptId } = useParams();
    const [receipt, setReceipt] = useState<GoodsReceiptDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [tab, setTab] = useState<ReceiptTab>("lines");
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);

    // Dialog state
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectionReason, setRejectionReason] = useState("");

    const [inspectionOpen, setInspectionOpen] = useState(false);
    const [inspectionTab, setInspectionTab] = useState<"receive" | "qc">("receive");
    const [receiveDraft, setReceiveDraft] = useState<ReceiveDraftLine[]>([]);
    const [qcDraft, setQcDraft] = useState<QcDraftLine[]>([]);
    const [qcPage, setQcPage] = useState(1);
    const [qcPageSize, setQcPageSize] = useState(3);

    const [postOpen, setPostOpen] = useState(false);
    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelNote, setCancelNote] = useState("");

    const [mutationLoading, setMutationLoading] = useState(false);

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

    const { isAdmin, isManager, isInventoryStaff, isQc, keycloakSubject } = useAuthorization();

    useEffect(() => loadReceipt(), [loadReceipt]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 3000);
    }

    // const canShowCost = hasAnyRole(["ADMIN", "ACCOUNTANT", "MANAGER", "MANAGER"]);

    // 1. Submit for Approval (DRAFT -> PENDING_APPROVAL)
    async function submitReceipt() {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.submitGoodsReceipt({
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
    function openInspectionModal() {
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
        setQcDraft(
            receipt.lines.map((l) => ({
                lineId: l.id,
                materialName: l.materialName,
                unit: l.unit,
                receivedQuantity: l.receivedQuantity || l.orderedQuantity || 0,
                acceptedQuantity: l.qc?.passedQuantity ?? 0,
                rejectedQuantity: l.qc?.failedQuantity ?? 0,
                supplierLot: l.supplierLot ?? "",
                manufactureDate: l.manufacturedAt ? l.manufacturedAt.split("T")[0] : "",
                expiryDate: l.expiryDate ? l.expiryDate.split("T")[0] : "",
                isIngredient: l.itemType === "INGREDIENT",
                note: l.qc?.inspectionNote ?? ""
            }))
        );
        setQcPage(1);
        setInspectionTab("receive");
        setInspectionOpen(true);
    }

    // 6. Confirm Receiving - submit tab 1
    async function submitReceive() {
        // Sync receivedQuantity từ receiveDraft sang qcDraft
        setQcDraft((cur) =>
            cur.map((qcLine) => {
                const receiveLine = receiveDraft.find((r) => r.lineId === qcLine.lineId);
                return receiveLine ? { ...qcLine, receivedQuantity: receiveLine.receivedQuantity } : qcLine;
            })
        );
        setInspectionTab("qc");
    }

    // 7. Submit QC - gộp receive & QC thành 1 API call
    async function submitQc() {
        if (!receipt) return;

        for (let i = 0; i < qcDraft.length; i++) {
            const line = qcDraft[i];
            if (line.acceptedQuantity + line.rejectedQuantity !== line.receivedQuantity) {
                const targetPage = Math.floor(i / qcPageSize) + 1;
                setQcPage(targetPage);
                showToast(`Dòng #${i + 1} (${line.materialName}): Tổng SL Đạt + Lỗi phải bằng Thực nhận (${line.receivedQuantity}).`, false);
                return;
            }
            if (line.acceptedQuantity > 0 && (!line.manufactureDate || (!line.isIngredient && !line.expiryDate))) {
                const targetPage = Math.floor(i / qcPageSize) + 1;
                setQcPage(targetPage);
                const message = line.isIngredient
                    ? "Vui lòng nhập Ngày sản xuất."
                    : "Vui lòng nhập đầy đủ Ngày sản xuất & Hạn sử dụng.";
                showToast(`Dòng #${i + 1} (${line.materialName}): ${message}`, false);
                return;
            }
        }

        try {
            setMutationLoading(true);
            const input: GoodsReceiptQcInput = {
                lines: qcDraft.map((l) => ({
                    acceptedQuantity: l.acceptedQuantity,
                    batches: l.acceptedQuantity > 0
                        ? [
                            {
                                expiryDate: l.isIngredient ? undefined : l.expiryDate,
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

            const updated = await goodsReceiptApi.completeGoodsReceiptInspection(
                receipt.id,
                receiveDraft.map((l) => ({
                    lineId: l.lineId,
                    note: l.note,
                    receivedQuantity: l.receivedQuantity
                })),
                input
            );
            setReceipt(updated);
            showToast("Kiểm định số lượng & chất lượng thành công.", true);
            setInspectionOpen(false);
        } catch (err) {
            throw err;
        } finally {
            setMutationLoading(false);
        }
    }

    // 9. Post (QC_COMPLETED -> POSTED)
    async function postReceipt(lines: GoodsReceiptLineResolutionInput[], approvalNote?: string) {
        if (!receipt) return;
        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.postGoodsReceipt({
                approvalNote,
                lines,
                receiptId: receipt.id
            });
            setReceipt(updated);
            setPostOpen(false);
            showToast("Đã duyệt ngoại lệ & ghi sổ nhập kho thành công.", true);
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
                    className={`fixed right-6 top-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-2xl transition-all ${toast.success ? "bg-[#159734]" : "bg-red-600"
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
                        <div className="text-sm font-semibold text-[#536078] space-y-1">
                            <div>
                                <span className="font-bold text-[#172317]">Nhà cung cấp:</span>{" "}
                                {receipt.supplier.name}
                            </div>

                            <div>
                                <span className="font-bold text-[#172317]">Kho nhận:</span>{" "}
                                {receipt.receivingLocation.name}
                            </div>

                            {/* <div>
        <span className="font-bold text-[#172317]">Đơn đặt PO:</span>{" "}
        {receipt.purchaseOrderCode || "Ngoài PO"}
    </div> */}

                            <div>
                                <span className="font-bold text-[#172317]">Ngày dự kiến:</span>{" "}
                                {formatVietnamDateTime(receipt.receivedAt)}
                            </div>
                        </div>
                        {receipt.note ? (
                            <p className="text-xs font-semibold italic text-[#697684]">
                                Ghi chú: {receipt.note}
                            </p>
                        ) : null}
                        {receipt.exceptionApprovedAt ? (
                            <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
                                <div>
                                    <span className="font-black">Duyệt ngoại lệ:</span>{" "}
                                    {receipt.exceptionApprovedByName || "Không rõ người duyệt"} ·{" "}
                                    {formatVietnamDateTime(receipt.exceptionApprovedAt)}
                                </div>
                                {receipt.exceptionApprovalNote ? (
                                    <div className="italic">Ghi chú: {receipt.exceptionApprovalNote}</div>
                                ) : null}
                            </div>
                        ) : null}
                    </div>

                    {/* VÙNG NÚT HÀNH ĐỘNG THEO TỪNG BƯỚC VÒNG ĐỜI */}
                    <div className="flex flex-wrap items-center gap-2">
                        {receipt.status === "POSTED" && receipt.lines.some(hasLineException) ? (
                            <button
                                className="inline-flex h-10 items-center gap-1.5 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] hover:bg-[#f4faf5]"
                                onClick={() => {
                                    try {
                                        printExceptionReport(receipt);
                                    } catch (printError) {
                                        showToast(printError instanceof Error ? printError.message : "Không thể in biên bản.", false);
                                    }
                                }}
                                type="button"
                            >
                                <Printer size={15} aria-hidden="true" />
                                In biên bản ngoại lệ
                            </button>
                        ) : null}
                        {/* Chỉnh sửa: phiếu DRAFT/REJECTED, chỉ ADMIN hoặc người tạo (createdBy = Keycloak subject) */}
                        {(receipt.status === "DRAFT" || receipt.status === "REJECTED")
                            && (isAdmin() || (Boolean(keycloakSubject) && receipt.createdBy.id === keycloakSubject)) ? (
                            <Link
                                className="inline-flex h-10 items-center gap-1.5 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] hover:bg-[#f4faf5]"
                                state={{ receipt }}
                                to={`/management/inventory/receipts/${receipt.id}/edit`}
                            >
                                <Pencil size={15} aria-hidden="true" />
                                Chỉnh sửa
                            </Link>
                        ) : null}
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

                        {receipt.status === "PENDING_APPROVAL" && (isManager() || isAdmin()) ? (
                            <>
                                <button
                                    className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                    disabled={mutationLoading}
                                    onClick={approveReceipt}
                                    type="button"
                                >
                                    Phê duyệt
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

                        {receipt.status === "APPROVED" && (isInventoryStaff() || isManager() || isAdmin()) ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={startReceiving}
                                type="button"
                            >
                                Tiếp nhận kiểm đếm thực tế
                            </button>
                        ) : null}

                        {receipt.status === "RECEIVING" && isQc() ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={openInspectionModal}
                                type="button"
                            >
                                Kiểm định số lượng & Chất lượng
                            </button>
                        ) : null}

                        {receipt.status === "PENDING_EXCEPTION_APPROVAL" && (isManager() || isAdmin()) ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#d97706] px-4 text-sm font-black text-white hover:bg-[#b45309] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={() => setPostOpen(true)}
                                type="button"
                            >
                                Duyệt ngoại lệ & Ghi sổ
                            </button>
                        ) : null}

                        {receipt.status !== "POSTED" &&
                            receipt.status !== "CANCELLED" &&
                            receipt.status !== "REJECTED" &&
                            (isAdmin() || isManager()) ? (
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
                    ["overview", "Tổng quan phiếu"],
                    ["lines", "Dòng hàng & Kiểm định"],
                    // ["cost", "Giá trị nhập kho"],
                    // ["documents", "Chứng từ liên quan"],
                    // ["evidence", "Ảnh & Biên bản"],
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

            {/* TAB: LỊCH SỬ XỬ LÝ */}
            {tab === "audit" ? (
                <GoodsReceiptAuditTimeline receipt={receipt} />
            ) : null}

            {/* TAB: GIÁ TRỊ NHẬP KHO */}
            {/* {tab === "cost" ? (
                canShowCost ? (
                    <GoodsReceiptCostTable receipt={receipt} />
                ) : (
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-bold text-[#697684]">
                        Tài khoản hiện tại không có quyền xem chi phí giá nhập.
                    </div>
                )
            ) : null} */}

            {/* TAB: CHỨNG TỪ LIÊN QUAN */}
            {/* {tab === "documents" ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">
                    <div className="text-sm font-bold text-[#172317]">
                        Mã chứng từ kho: <span className="font-mono text-[#159734]">{receipt.stockDocumentCode || "Chưa ghi sổ"}</span>
                    </div>
                </div>
            ) : null} */}

            {/* INSPECTION MODAL - Kiểm định số lượng & Chất lượng */}
            <GoodsReceiptInspectionModal
                open={inspectionOpen}
                onOpenChange={setInspectionOpen}
                tab={inspectionTab}
                onTabChange={setInspectionTab}
                receiveDraft={receiveDraft}
                onReceiveChange={setReceiveDraft}
                qcDraft={qcDraft}
                onQcChange={setQcDraft}
                qcPage={qcPage}
                onQcPageChange={setQcPage}
                qcPageSize={qcPageSize}
                onQcPageSizeChange={setQcPageSize}
                onReceiveSubmit={submitReceive}
                onQcSubmit={submitQc}
                loading={mutationLoading}
            />

            {/* ============================================================ */}
            {/* 3. MODAL DUYỆT NGOẠI LỆ & GHI SỔ (PENDING_EXCEPTION_APPROVAL) */}
            {/* ============================================================ */}
            {postOpen ? (
                <GoodsReceiptExceptionResolutionModal
                    loading={mutationLoading}
                    onClose={() => setPostOpen(false)}
                    onSubmit={postReceipt}
                    receipt={receipt}
                />
            ) : null}

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
