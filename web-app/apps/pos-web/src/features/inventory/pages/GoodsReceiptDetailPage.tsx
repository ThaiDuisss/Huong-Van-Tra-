import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    CostBadge,
    GoodsReceiptAuditTimeline,
    GoodsReceiptCostTable,
    GoodsReceiptEvidenceList,
    GoodsReceiptLineTable,
    GoodsReceiptMovementPanel,
    GoodsReceiptPostPreview,
    GoodsReceiptRoleSwitch,
    GoodsReceiptStatusBadge,
    QcBadge,
    RoleUnavailable
} from "../components/GoodsReceiptComponents";
import {
    goodsReceiptRoleLabels,
    goodsReceiptStatusLabels,
    qcReasonLabels
} from "../constants/goods-receipt.constants";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptCostInput,
    GoodsReceiptDetail,
    GoodsReceiptQcInput,
    GoodsReceiptQcReasonCode,
    GoodsReceiptRole
} from "../types/goods-receipt.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

type ReceiptTab =
    | "overview"
    | "lines"
    | "qc"
    | "cost"
    | "documents"
    | "evidence"
    | "audit";

interface QcDraftLine {
    failedQuantity: number;
    inspectionNote: string;
    lineId: string;
    passedQuantity: number;
    quarantinedQuantity: number;
    reasonCode: "" | GoodsReceiptQcReasonCode;
    receivedQuantity: number;
}

interface CostDraftLine {
    acceptedQuantity: number;
    allocatedShippingCost: number;
    discount: number;
    lineId: string;
    otherCost: number;
    purchaseUnitPrice: number;
    tax: number;
}

function readRole(): GoodsReceiptRole {
    const stored = window.localStorage.getItem("hvt.goodsReceipt.role");

    if (
        stored &&
        ["ADMIN", "WAREHOUSE", "QC_STAFF", "ACCOUNTANT", "BRANCH_MANAGER", "DIRECTOR"].includes(
            stored
        )
    ) {
        return stored as GoodsReceiptRole;
    }

    return "WAREHOUSE";
}

function canQc(role: GoodsReceiptRole) {
    return role === "ADMIN" || role === "QC_STAFF";
}

function canCost(role: GoodsReceiptRole) {
    return role === "ADMIN" || role === "ACCOUNTANT";
}

function canPost(role: GoodsReceiptRole) {
    return role === "ADMIN" || role === "WAREHOUSE";
}

export function GoodsReceiptDetailPage() {
    const { receiptId } = useParams();
    const [role, setRole] = useState<GoodsReceiptRole>(readRole);
    const [receipt, setReceipt] = useState<GoodsReceiptDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [tab, setTab] = useState<ReceiptTab>("overview");
    const [toast, setToast] = useState<{
        message: string;
        success: boolean;
    } | null>(null);
    const [qcOpen, setQcOpen] = useState(false);
    const [costOpen, setCostOpen] = useState(false);
    const [postOpen, setPostOpen] = useState(false);
    const [mutationLoading, setMutationLoading] = useState(false);
    const [qcDraft, setQcDraft] = useState<QcDraftLine[]>([]);
    const [costDraft, setCostDraft] = useState<CostDraftLine[]>([]);

    useEffect(() => {
        window.localStorage.setItem("hvt.goodsReceipt.role", role);
    }, [role]);

    const loadReceipt = useCallback(() => {
        if (!receiptId) {
            return;
        }

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
                if (active) {
                    setReceipt(response);
                }
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
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [receiptId]);

    useEffect(() => loadReceipt(), [loadReceipt]);

    const readOnly = receipt?.status === "POSTED" || receipt?.status === "CANCELLED";
    const canShowCost =
        role === "ADMIN" || role === "ACCOUNTANT" || role === "DIRECTOR";
    const canOpenQc =
        receipt &&
        !readOnly &&
        canQc(role) &&
        ["AWAITING_QC", "PARTIALLY_PASSED"].includes(receipt.status);
    const canOpenCost =
        receipt &&
        !readOnly &&
        canCost(role) &&
        ["AWAITING_COST", "PARTIALLY_PASSED", "READY_TO_POST"].includes(
            receipt.status
        );
    const canOpenPost =
        receipt && !readOnly && canPost(role) && receipt.status === "READY_TO_POST";

    const postPreviewValue = useMemo(
        () =>
            receipt?.lines.reduce(
                (sum, line) => sum + line.cost.lineNetAmount,
                0
            ) ?? 0,
        [receipt]
    );

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 2600);
    }

    function openQc() {
        if (!receipt) return;
        setQcDraft(
            receipt.lines.map((line) => ({
                failedQuantity: line.qc.failedQuantity,
                inspectionNote: line.qc.inspectionNote ?? "",
                lineId: line.id,
                passedQuantity:
                    line.qc.passedQuantity || line.receivedQuantity,
                quarantinedQuantity: line.qc.quarantinedQuantity,
                reasonCode: line.qc.reasonCode ?? "",
                receivedQuantity: line.receivedQuantity
            }))
        );
        setQcOpen(true);
    }

    function openCost() {
        if (!receipt) return;
        setCostDraft(
            receipt.lines.map((line) => ({
                acceptedQuantity:
                    line.cost.acceptedQuantity || line.qc.passedQuantity,
                allocatedShippingCost: line.cost.allocatedShippingCost,
                discount: line.cost.discount,
                lineId: line.id,
                otherCost: line.cost.otherCost,
                purchaseUnitPrice: line.cost.purchaseUnitPrice || 100000,
                tax: line.cost.tax
            }))
        );
        setCostOpen(true);
    }

    async function submitReceipt() {
        if (!receipt) return;

        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.submitGoodsReceipt({
                actorRole: role,
                receiptId: receipt.id
            });
            setReceipt(updated);
            showToast("Submit phiếu nhập thành công.", true);
        } catch (submitError) {
            showToast(
                submitError instanceof Error
                    ? submitError.message
                    : "Submit thất bại.",
                false
            );
        } finally {
            setMutationLoading(false);
        }
    }

    async function submitQc() {
        if (!receipt) return;

        const input: GoodsReceiptQcInput = {
            actorRole: role,
            lines: qcDraft.map((line) => ({
                failedQuantity: line.failedQuantity,
                inspectionNote: line.inspectionNote || undefined,
                lineId: line.lineId,
                passedQuantity: line.passedQuantity,
                quarantinedQuantity: line.quarantinedQuantity,
                reasonCode: line.reasonCode || undefined,
                receivedQuantity: line.receivedQuantity
            })),
            receiptId: receipt.id
        };

        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.performGoodsReceiptQc(input);
            setReceipt(updated);
            setQcOpen(false);
            showToast("QC phiếu nhập thành công.", true);
        } catch (qcError) {
            showToast(
                qcError instanceof Error ? qcError.message : "QC thất bại.",
                false
            );
        } finally {
            setMutationLoading(false);
        }
    }

    async function submitCost(finalize: boolean) {
        if (!receipt) return;

        const input: GoodsReceiptCostInput = {
            actorRole: role,
            finalize,
            lines: costDraft,
            receiptId: receipt.id
        };

        try {
            setMutationLoading(true);
            const updated = finalize
                ? await goodsReceiptApi.finalizeGoodsReceiptCost(input)
                : await goodsReceiptApi.saveGoodsReceiptCost(input);
            setReceipt(updated);
            setCostOpen(false);
            showToast(
                finalize ? "Xác nhận giá FINAL thành công." : "Lưu giá tạm thành công.",
                true
            );
        } catch (costError) {
            showToast(
                costError instanceof Error
                    ? costError.message
                    : "Lưu giá thất bại.",
                false
            );
        } finally {
            setMutationLoading(false);
        }
    }

    async function postReceipt() {
        if (!receipt) return;

        try {
            setMutationLoading(true);
            const updated = await goodsReceiptApi.postGoodsReceipt({
                actorRole: role,
                receiptId: receipt.id
            });
            setReceipt(updated);
            setPostOpen(false);
            showToast("Post phiếu nhập thành công.", true);
        } catch (postError) {
            showToast(
                postError instanceof Error
                    ? postError.message
                    : "Post thất bại.",
                false
            );
        } finally {
            setMutationLoading(false);
        }
    }

    if (!receiptId) {
        return <Navigate to="/management/inventory/receipts" replace />;
    }

    if (loading) {
        return (
            <section className="grid gap-4">
                <div className="h-8 w-52 animate-pulse rounded-lg bg-[#eef3ef]" />
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 8 }, (_, index) => (
                        <div
                            className="h-12 animate-pulse rounded-lg bg-[#eef3ef]"
                            key={index}
                        />
                    ))}
                </div>
            </section>
        );
    }

    if (error || !receipt) {
        return (
            <section className="grid gap-4">
                <Link
                    className="text-sm font-black text-[#159734] hover:underline"
                    to="/management/inventory/receipts"
                >
                    Quay lại danh sách
                </Link>
                <div className="rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center text-sm font-bold text-red-700">
                    {error || "Không tìm thấy phiếu nhập."}
                </div>
            </section>
        );
    }

    return (
        <section className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <Link
                    className="text-sm font-black text-[#159734] hover:underline"
                    to="/management/inventory/receipts"
                >
                    Quay lại danh sách
                </Link>
                <GoodsReceiptRoleSwitch onChange={setRole} value={role} />
            </div>

            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="text-xs font-black text-[#697684]">
                            Kho › Nhập nguyên liệu › Chi tiết
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                            <h1 className="text-2xl font-black text-[#172317]">
                                {receipt.code}
                            </h1>
                            <GoodsReceiptStatusBadge
                                status={receipt.status}
                            />
                            <QcBadge result={receipt.qcResult} />
                            <CostBadge status={receipt.costStatus} />
                        </div>
                        <p className="text-sm font-semibold text-[#536078]">
                            {receipt.supplier.name} ·{" "}
                            {receipt.purchaseOrderCode ?? "Nhập ngoài PO"} ·{" "}
                            {receipt.receivingLocation.name}
                        </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        {receipt.status === "DRAFT" &&
                        (role === "ADMIN" || role === "WAREHOUSE") ? (
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                                disabled={mutationLoading}
                                onClick={() => void submitReceipt()}
                                type="button"
                            >
                                Submit
                            </button>
                        ) : null}
                        {canOpenQc ? (
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white"
                                onClick={openQc}
                                type="button"
                            >
                                Thực hiện QC
                            </button>
                        ) : receipt.status === "AWAITING_QC" ? (
                            <RoleUnavailable currentRole={role} />
                        ) : null}
                        {canOpenCost ? (
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white"
                                onClick={openCost}
                                type="button"
                            >
                                Xác nhận giá nhập
                            </button>
                        ) : receipt.status === "AWAITING_COST" ? (
                            <RoleUnavailable currentRole={role} />
                        ) : null}
                        {canOpenPost ? (
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white"
                                onClick={() => setPostOpen(true)}
                                type="button"
                            >
                                Post receipt
                            </button>
                        ) : receipt.status === "READY_TO_POST" ? (
                            <RoleUnavailable currentRole={role} />
                        ) : null}
                        {readOnly ? (
                            <span className="rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 py-2 text-sm font-bold text-[#697684]">
                                Read-only: {goodsReceiptStatusLabels[receipt.status]}
                            </span>
                        ) : null}
                    </div>
                </div>
            </header>

            <GoodsReceiptMovementPanel receipt={receipt} />

            <nav className="flex gap-2 overflow-x-auto border-b border-[#e8eef0] pb-1">
                {[
                    ["overview", "Tổng quan"],
                    ["lines", "Dòng hàng"],
                    ["qc", "QC"],
                    ["cost", "Giá nhập"],
                    ["documents", "Chứng từ kho"],
                    ["evidence", "Tài liệu đính kèm"],
                    ["audit", "Audit"]
                ].map(([value, label]) => (
                    <button
                        className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${
                            tab === value
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

            {tab === "overview" ? (
                <Overview receipt={receipt} postPreviewValue={postPreviewValue} />
            ) : null}
            {tab === "lines" ? <GoodsReceiptLineTable receipt={receipt} /> : null}
            {tab === "qc" ? <GoodsReceiptLineTable receipt={receipt} /> : null}
            {tab === "cost" ? (
                canShowCost ? (
                    <GoodsReceiptCostTable receipt={receipt} />
                ) : (
                    <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-bold text-[#697684]">
                        Vai trò {goodsReceiptRoleLabels[role]} không được xem
                        giá và chi phí nhập.
                    </div>
                )
            ) : null}
            {tab === "documents" ? (
                <div className="rounded-xl border border-[#e8eef0] bg-white p-4">
                    {receipt.stockDocumentId ? (
                        <Link
                            className="font-black text-[#159734] hover:underline"
                            to={`/management/inventory/documents/${receipt.stockDocumentId}`}
                        >
                            {receipt.stockDocumentCode}
                        </Link>
                    ) : (
                        <p className="text-sm font-semibold text-[#697684]">
                            Chưa tạo chứng từ kho. Chứng từ PNK sẽ được tạo khi
                            post receipt.
                        </p>
                    )}
                </div>
            ) : null}
            {tab === "evidence" ? (
                <GoodsReceiptEvidenceList receipt={receipt} />
            ) : null}
            {tab === "audit" ? (
                <GoodsReceiptAuditTimeline receipt={receipt} />
            ) : null}

            <Dialog open={qcOpen} onOpenChange={setQcOpen}>
                <DialogContent className="max-w-[920px]">
                    <DialogHeader>
                        <DialogTitle>Thực hiện QC</DialogTitle>
                        <DialogDescription>
                            Tổng đạt, cách ly và không đạt phải bằng số lượng
                            nhận. Failed bắt buộc lý do, quarantine bắt buộc ghi
                            chú.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="grid max-h-[62vh] gap-3 overflow-y-auto px-6 py-5">
                        {qcDraft.map((line, index) => (
                            <QcDraftEditor
                                key={line.lineId}
                                line={line}
                                onChange={(patch) =>
                                    setQcDraft((current) =>
                                        current.map((item, itemIndex) =>
                                            itemIndex === index
                                                ? { ...item, ...patch }
                                                : item
                                        )
                                    )
                                }
                            />
                        ))}
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setQcOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void submitQc()}
                            type="button"
                        >
                            {mutationLoading ? "Đang lưu..." : "Lưu QC"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog open={costOpen} onOpenChange={setCostOpen}>
                <DialogContent className="max-w-[920px]">
                    <DialogHeader>
                        <DialogTitle>Xác nhận giá nhập</DialogTitle>
                        <DialogDescription>
                            lineNetAmount = unit price × accepted quantity -
                            discount + tax + shipping + other cost.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="grid max-h-[62vh] gap-3 overflow-y-auto px-6 py-5">
                        {costDraft.map((line, index) => (
                            <CostDraftEditor
                                key={line.lineId}
                                line={line}
                                onChange={(patch) =>
                                    setCostDraft((current) =>
                                        current.map((item, itemIndex) =>
                                            itemIndex === index
                                                ? { ...item, ...patch }
                                                : item
                                        )
                                    )
                                }
                            />
                        ))}
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setCostOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734] disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void submitCost(false)}
                            type="button"
                        >
                            Lưu PROVISIONAL
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void submitCost(true)}
                            type="button"
                        >
                            Xác nhận FINAL
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog open={postOpen} onOpenChange={setPostOpen}>
                <DialogContent className="max-w-[620px]">
                    <DialogHeader>
                        <DialogTitle>Xác nhận post receipt</DialogTitle>
                        <DialogDescription>
                            Sau post, phiếu chuyển sang read-only và chứng từ
                            PNK sẽ được tạo.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-5">
                        <GoodsReceiptPostPreview receipt={receipt} />
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setPostOpen(false)}
                            type="button"
                        >
                            Hủy
                        </button>
                        <button
                            className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void postReceipt()}
                            type="button"
                        >
                            {mutationLoading ? "Đang post..." : "Xác nhận post"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {toast ? (
                <div
                    className={`fixed bottom-6 right-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-pos-panel ${
                        toast.success ? "bg-[#103b2b]" : "bg-red-700"
                    }`}
                    role="status"
                >
                    {toast.message}
                </div>
            ) : null}
        </section>
    );
}

function Overview({
    postPreviewValue,
    receipt
}: {
    postPreviewValue: number;
    receipt: GoodsReceiptDetail;
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-2 xl:grid-cols-3">
            <Info label="Mã phiếu" value={receipt.code} />
            <Info label="Supplier" value={receipt.supplier.name} />
            <Info label="PO" value={receipt.purchaseOrderCode ?? "Ngoài PO"} />
            <Info label="Kho nhận" value={receipt.receivingLocation.name} />
            <Info
                label="Ngày nhận"
                value={formatVietnamDateTime(receipt.receivedAt)}
            />
            <Info label="Người giao" value={receipt.deliveryPerson} />
            <Info label="Biển số xe" value={receipt.vehiclePlate ?? "-"} />
            <Info label="Invoice" value={receipt.invoiceNumber ?? "-"} />
            <Info
                label="Tổng số lượng nhận vật lý"
                value={formatVietnamNumber(receipt.totalReceivedQuantity)}
            />
            <Info
                label="Available hiện tại"
                value={
                    receipt.status === "POSTED"
                        ? formatVietnamNumber(
                              receipt.movementSummary?.availableQuantity ?? 0
                          )
                        : "Chưa tăng"
                }
            />
            <Info
                label="Giá trị dự kiến"
                value={formatVnd(postPreviewValue)}
            />
            <Info label="Ghi chú" value={receipt.note ?? "-"} />
        </section>
    );
}

function QcDraftEditor({
    line,
    onChange
}: {
    line: QcDraftLine;
    onChange: (patch: Partial<QcDraftLine>) => void;
}) {
    return (
        <div className="grid gap-3 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-3">
            <Info label="Số nhận" value={formatVietnamNumber(line.receivedQuantity)} />
            <NumberField
                label="Passed"
                onChange={(passedQuantity) => onChange({ passedQuantity })}
                value={line.passedQuantity}
            />
            <NumberField
                label="Quarantined"
                onChange={(quarantinedQuantity) =>
                    onChange({ quarantinedQuantity })
                }
                value={line.quarantinedQuantity}
            />
            <NumberField
                label="Failed"
                onChange={(failedQuantity) => onChange({ failedQuantity })}
                value={line.failedQuantity}
            />
            <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                Reason
                <select
                    className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]"
                    onChange={(event) =>
                        onChange({
                            reasonCode:
                                event.currentTarget
                                    .value as GoodsReceiptQcReasonCode
                        })
                    }
                    value={line.reasonCode}
                >
                    <option value="">Không chọn</option>
                    {Object.entries(qcReasonLabels).map(([value, label]) => (
                        <option key={value} value={value}>
                            {value} - {label}
                        </option>
                    ))}
                </select>
            </label>
            <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                Inspection note
                <input
                    className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]"
                    onChange={(event) =>
                        onChange({ inspectionNote: event.currentTarget.value })
                    }
                    value={line.inspectionNote}
                />
            </label>
        </div>
    );
}

function CostDraftEditor({
    line,
    onChange
}: {
    line: CostDraftLine;
    onChange: (patch: Partial<CostDraftLine>) => void;
}) {
    const net =
        line.purchaseUnitPrice * line.acceptedQuantity -
        line.discount +
        line.tax +
        line.allocatedShippingCost +
        line.otherCost;

    return (
        <div className="grid gap-3 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-3">
            <NumberField
                label="Purchase unit price"
                onChange={(purchaseUnitPrice) =>
                    onChange({ purchaseUnitPrice })
                }
                value={line.purchaseUnitPrice}
            />
            <NumberField
                label="Quantity passed"
                onChange={(acceptedQuantity) =>
                    onChange({ acceptedQuantity })
                }
                value={line.acceptedQuantity}
            />
            <NumberField
                label="Discount"
                onChange={(discount) => onChange({ discount })}
                value={line.discount}
            />
            <NumberField
                label="Tax"
                onChange={(tax) => onChange({ tax })}
                value={line.tax}
            />
            <NumberField
                label="Shipping allocation"
                onChange={(allocatedShippingCost) =>
                    onChange({ allocatedShippingCost })
                }
                value={line.allocatedShippingCost}
            />
            <NumberField
                label="Other cost"
                onChange={(otherCost) => onChange({ otherCost })}
                value={line.otherCost}
            />
            <Info label="Line net amount" value={formatVnd(net)} />
            <Info
                label="Final unit cost"
                value={
                    line.acceptedQuantity > 0
                        ? formatVnd(net / line.acceptedQuantity)
                        : "Không thể chia cho 0"
                }
            />
        </div>
    );
}

function NumberField({
    label,
    onChange,
    value
}: {
    label: string;
    onChange: (value: number) => void;
    value: number;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]"
                min={0}
                onChange={(event) => onChange(Number(event.currentTarget.value))}
                type="number"
                value={value}
            />
        </label>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">
                {value}
            </strong>
        </div>
    );
}
