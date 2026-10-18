import { FileText, PackageCheck, Truck } from "lucide-react";
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
    TransferDiscrepancyPanel,
    TransferStatusBadge
} from "../components/TransferComponents";
import {
    transferDiscrepancyResolutionLabels,
    transferPriorityLabels,
    transferStatusLabels
} from "../constants/transfer.constants";
import { transferApi } from "../services/transfer.api";
import type {
    TransferDetail,
    TransferDiscrepancyResolutionStatus
} from "../types/transfer.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

type DetailTab = "overview" | "lines" | "reservation" | "shipping" | "receiving" | "documents" | "audit";

const tabs: Array<{ id: DetailTab; label: string }> = [
    { id: "overview", label: "Tổng quan" },
    { id: "lines", label: "Hàng hóa" },
    { id: "reservation", label: "Reservation và Picking" },
    { id: "shipping", label: "Vận chuyển" },
    { id: "receiving", label: "Nhận hàng" },
    { id: "documents", label: "Chứng từ" },
    { id: "audit", label: "Audit" }
];

export function TransferDetailPage() {
    const { transferId } = useParams();
    const { roles } = useAuth();
    const [transfer, setTransfer] = useState<TransferDetail | null>(null);
    const [activeTab, setActiveTab] = useState<DetailTab>("overview");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [approveNote, setApproveNote] = useState("");
    const [shipOpen, setShipOpen] = useState(false);
    const [receiveOpen, setReceiveOpen] = useState(false);
    const [shipForm, setShipForm] = useState({
        carrierName: "Tài xế nội bộ",
        expectedArrivalAt: "2026-08-03T10:00",
        internalTrackingCode: "INT-TRK-NEW",
        packageCount: 1,
        vehiclePlate: ""
    });
    const [receiveEvidence, setReceiveEvidence] = useState("");

    const load = useCallback(() => {
        if (!transferId) return;
        let active = true;
        setLoading(true);
        setError("");
        void transferApi.getTransfer(transferId)
            .then((response) => { if (active) setTransfer(response); })
            .catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải yêu cầu chuyển kho.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [transferId]);

    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) cleanup = load();
        });
        return () => {
            cancelled = true;
            cleanup?.();
        };
    }, [load]);

    if (!transferId) return <Navigate to="/management/inventory/transfers" replace />;

    async function runAction(action: () => Promise<TransferDetail>, fallback: string) {
        setError("");
        try {
            setSubmitting(true);
            setTransfer(await action());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : fallback);
        } finally {
            setSubmitting(false);
        }
    }

    function approveAll() {
        if (!transfer) return;
        void runAction(
            () => transferApi.approveTransfer({
                lines: transfer.lines.map((line) => ({ approvedQuantity: line.requestedQuantity, lineId: line.id })),
                note: approveNote,
                transferId: transfer.id
            }),
            "Không thể duyệt yêu cầu."
        );
    }

    function approveReduced() {
        if (!transfer) return;
        void runAction(
            () => transferApi.approveTransfer({
                lines: transfer.lines.map((line) => ({ approvedQuantity: Math.max(1, line.requestedQuantity - 1), lineId: line.id })),
                note: approveNote,
                transferId: transfer.id
            }),
            "Không thể duyệt giảm số lượng."
        );
    }

    function confirmPickAll() {
        if (!transfer) return;
        void runAction(
            () => transferApi.confirmTransferPicking({
                lines: transfer.lines.map((line) => ({
                    allocations: line.allocations.map((allocation) => ({
                        allocationId: allocation.id,
                        picked: true,
                        pickedQuantity: allocation.reservedQuantity
                    })),
                    lineId: line.id
                })),
                scannedBatchCode: transfer.lines[0]?.allocations[0]?.batchCode,
                transferId: transfer.id
            }),
            "Không thể confirm picking."
        );
    }

    function ship() {
        if (!transfer) return;
        void runAction(
            () => transferApi.shipTransfer({
                carrierName: shipForm.carrierName,
                expectedArrivalAt: new Date(shipForm.expectedArrivalAt).toISOString(),
                internalTrackingCode: shipForm.internalTrackingCode,
                packageCount: shipForm.packageCount,
                transferId: transfer.id,
                vehiclePlate: shipForm.vehiclePlate
            }),
            "Không thể ship transfer."
        );
        setShipOpen(false);
    }

    function receive(full: boolean) {
        if (!transfer) return;
        void runAction(
            () => transferApi.receiveTransfer({
                lines: transfer.lines.map((line) => ({
                    batchCode: line.allocations[0]?.batchCode ?? "",
                    damagedQuantity: full ? 0 : 1,
                    evidence: receiveEvidence || (full ? undefined : "anh-bang-chung.jpg"),
                    extraQuantity: 0,
                    lineId: line.id,
                    missingQuantity: full ? 0 : 1,
                    note: full ? "Nhận đủ." : "Nhận thiếu/hỏng, cần xác minh.",
                    receivedGoodQuantity: full ? line.shippedQuantity : Math.max(0, line.shippedQuantity - 2)
                })),
                note: full ? "Chi nhánh nhận đủ." : "Chi nhánh nhận có chênh lệch.",
                transferId: transfer.id
            }),
            "Không thể nhận hàng."
        );
        setReceiveOpen(false);
    }

    const canWarehouse = hasAnyRole(roles, ["ADMIN", "INVENTORY_STAFF", "MANAGER", "MANAGER"]);
    const canBranchReceive = hasAnyRole(roles, ["ADMIN", "MANAGER", "SALE_STAFF", "INVENTORY_STAFF"]);

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="grid gap-1">
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/transfers">Quay lại danh sách</Link>
                    <h1 className="text-2xl font-black text-[#172317]">{transfer?.code ?? "Chuyển kho"}</h1>
                    {transfer ? <p className="text-sm font-semibold text-[#697684]">{transfer.sourceLocation.name} → {transfer.destinationLocation.name}</p> : null}
                </div>
            </header>

            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold shadow-pos-card">Đang tải...</div> : null}
            {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}

            {transfer ? (
                <>
                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                            <div className="grid gap-2">
                                <TransferStatusBadge status={transfer.status} />
                                <p className="text-sm font-semibold text-[#536078]">Priority: {transferPriorityLabels[transfer.priority]} · Giá trị: {formatVnd(transfer.totalValue)}</p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {transfer.status === "REQUESTED" && canWarehouse ? (
                                    <>
                                        <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setApproveNote(event.currentTarget.value)} placeholder="Ghi chú nếu giảm số lượng" value={approveNote} />
                                        <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={approveAll} type="button">Approve toàn bộ</button>
                                        <button className="h-10 rounded-lg border border-amber-200 px-4 text-sm font-black text-amber-700" disabled={submitting} onClick={approveReduced} type="button">Approve giảm SL</button>
                                    </>
                                ) : null}
                                {transfer.status === "APPROVED" && canWarehouse ? <ActionButton disabled={submitting} label="Reserve FEFO" onClick={() => void runAction(() => transferApi.reserveTransfer({ transferId: transfer.id }), "Không thể reserve.")} /> : null}
                                {transfer.status === "RESERVED" && canWarehouse ? <ActionButton disabled={submitting} label="Bắt đầu picking" onClick={() => void runAction(() => transferApi.startPicking({ transferId: transfer.id }), "Không thể bắt đầu picking.")} /> : null}
                                {transfer.status === "PICKING" && canWarehouse ? <ActionButton disabled={submitting} label="Confirm picking" onClick={confirmPickAll} /> : null}
                                {transfer.status === "PICKING" && canWarehouse ? <ActionButton disabled={submitting} label="Ship" onClick={() => setShipOpen(true)} /> : null}
                                {transfer.status === "SHIPPED" && canBranchReceive ? <ActionButton disabled={submitting} label="Nhận hàng" onClick={() => setReceiveOpen(true)} /> : null}
                            </div>
                        </div>
                    </section>

                    <nav className="flex gap-2 overflow-x-auto">
                        {tabs.map((tab) => (
                            <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${activeTab === tab.id ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} type="button">
                                {tab.label}
                            </button>
                        ))}
                    </nav>

                    {activeTab === "overview" ? <Overview transfer={transfer} /> : null}
                    {activeTab === "lines" ? <Lines transfer={transfer} /> : null}
                    {activeTab === "reservation" ? <Reservation transfer={transfer} /> : null}
                    {activeTab === "shipping" ? <Shipping transfer={transfer} /> : null}
                    {activeTab === "receiving" ? <Receiving transfer={transfer} /> : null}
                    {activeTab === "documents" ? <Documents transfer={transfer} /> : null}
                    {activeTab === "audit" ? <Audit transfer={transfer} /> : null}

                    <Dialog open={shipOpen} onOpenChange={setShipOpen}>
                        <DialogContent className="max-w-[620px]">
                            <DialogHeader>
                                <DialogTitle>Confirm Ship</DialogTitle>
                                <DialogDescription>Ship sẽ giảm On Hand kho nguồn, tăng IN_TRANSIT và tạo PXK TRANSFER_ISSUE. Chưa tăng tồn chi nhánh.</DialogDescription>
                            </DialogHeader>
                            <div className="grid gap-3 px-6 py-5 md:grid-cols-2">
                                <NumberField label="Số kiện" onChange={(packageCount) => setShipForm({ ...shipForm, packageCount })} value={shipForm.packageCount} />
                                <TextField label="Người giao" onChange={(carrierName) => setShipForm({ ...shipForm, carrierName })} value={shipForm.carrierName} />
                                <TextField label="Biển số xe" onChange={(vehiclePlate) => setShipForm({ ...shipForm, vehiclePlate })} value={shipForm.vehiclePlate} />
                                <TextField label="Tracking nội bộ" onChange={(internalTrackingCode) => setShipForm({ ...shipForm, internalTrackingCode })} value={shipForm.internalTrackingCode} />
                                <label className="grid gap-1.5 text-xs font-black text-[#536078] md:col-span-2">
                                    Expected arrival
                                    <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setShipForm({ ...shipForm, expectedArrivalAt: event.currentTarget.value })} type="datetime-local" value={shipForm.expectedArrivalAt} />
                                </label>
                            </div>
                            <DialogFooter>
                                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black" onClick={() => setShipOpen(false)} type="button">Hủy</button>
                                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={ship} type="button">Xác nhận ship</button>
                            </DialogFooter>
                        </DialogContent>
                    </Dialog>

                    <Dialog open={receiveOpen} onOpenChange={setReceiveOpen}>
                        <DialogContent className="max-w-[720px]">
                            <DialogHeader>
                                <DialogTitle>Nhận hàng chuyển kho</DialogTitle>
                                <DialogDescription>Good + damaged + missing phải bằng shipped quantity. Extra được tách riêng và yêu cầu evidence.</DialogDescription>
                            </DialogHeader>
                            <div className="grid max-h-[58vh] gap-3 overflow-y-auto px-6 py-5">
                                {transfer.lines.map((line) => (
                                    <div className="grid gap-2 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-4" key={line.id}>
                                        <Info label="SKU" value={line.skuCode} />
                                        <Info label="Shipped" value={`${formatVietnamNumber(line.shippedQuantity)} ${line.unit}`} />
                                        <Info label="Batch" value={line.allocations.map((item) => item.batchCode).join(", ")} />
                                        <Info label="Gợi ý" value="Nhận đủ hoặc thiếu 1/hỏng 1" />
                                    </div>
                                ))}
                                <TextField label="Evidence nếu có chênh lệch" onChange={setReceiveEvidence} value={receiveEvidence} />
                            </div>
                            <DialogFooter>
                                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black" onClick={() => setReceiveOpen(false)} type="button">Hủy</button>
                                <button className="h-10 rounded-lg border border-amber-200 px-4 text-sm font-black text-amber-700" disabled={submitting} onClick={() => receive(false)} type="button">Nhận thiếu/hỏng</button>
                                <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => receive(true)} type="button">Nhận đủ</button>
                            </DialogFooter>
                        </DialogContent>
                    </Dialog>
                </>
            ) : null}
        </section>
    );
}

function Overview({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3">
            <Info label="Trạng thái" value={transferStatusLabels[transfer.status]} />
            <Info label="Ngày yêu cầu" value={formatVietnamDateTime(transfer.requestedAt)} />
            <Info label="Dự kiến nhận" value={formatVietnamDateTime(transfer.expectedReceiveAt)} />
            <Info label="Người tạo" value={transfer.requestedBy.name} />
            <Info label="Tổng SKU" value={formatVietnamNumber(transfer.totalSku)} />
            <Info label="Tổng số lượng" value={formatVietnamNumber(transfer.totalQuantity)} />
            <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm font-semibold text-[#536078] md:col-span-3">Lý do: {transfer.reason}</div>
        </section>
    );
}

function Lines({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {transfer.lines.map((line) => (
                <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-6" key={line.id}>
                    <Info label="SKU" value={line.skuCode} />
                    <Info label="Requested" value={`${formatVietnamNumber(line.requestedQuantity)} ${line.unit}`} />
                    <Info label="Approved" value={`${formatVietnamNumber(line.approvedQuantity)} ${line.unit}`} />
                    <Info label="Destination available" value={formatVietnamNumber(line.destinationAvailableQuantity)} />
                    <Info label="Destination min" value={formatVietnamNumber(line.destinationMinimumStock)} />
                    <Info label="Source available" value={formatVietnamNumber(line.sourceAvailableQuantity)} />
                </div>
            ))}
        </section>
    );
}

function Reservation({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="rounded-lg bg-sky-50 p-3 text-sm font-bold text-sky-800">Scan batch giả lập: nhập batch code trong bước confirm picking; mock hiện hỗ trợ confirm tất cả batch FEFO.</div>
            {transfer.lines.map((line) => (
                <div className="grid gap-2 rounded-lg bg-[#f7f8fb] p-3" key={line.id}>
                    <strong className="text-sm font-black text-[#172317]">{line.skuCode} · {line.skuName}</strong>
                    {line.allocations.length === 0 ? <p className="text-sm font-semibold text-[#697684]">Chưa reserve FEFO.</p> : null}
                    {line.allocations.map((allocation) => (
                        <div className="grid gap-2 rounded-lg bg-white p-3 md:grid-cols-5" key={allocation.id}>
                            <Info label="Batch" value={allocation.batchCode} />
                            <Info label="HSD" value={formatVietnamDateTime(allocation.expiryDate)} />
                            <Info label="Reserved" value={formatVietnamNumber(allocation.reservedQuantity)} />
                            <Info label="Actual picked" value={formatVietnamNumber(allocation.pickedQuantity)} />
                            <Info label="Đã lấy" value={allocation.picked ? "Có" : "Chưa"} />
                        </div>
                    ))}
                </div>
            ))}
        </section>
    );
}

function Shipping({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-3">
            {transfer.shipment ? (
                <>
                    <Info label="Số kiện" value={formatVietnamNumber(transfer.shipment.packageCount)} />
                    <Info label="Người giao" value={transfer.shipment.carrierName} />
                    <Info label="Biển số xe" value={transfer.shipment.vehiclePlate} />
                    <Info label="Tracking" value={transfer.shipment.internalTrackingCode} />
                    <Info label="Đã ship" value={formatVietnamDateTime(transfer.shipment.shippedAt)} />
                    <Info label="Dự kiến đến" value={formatVietnamDateTime(transfer.shipment.expectedArrivalAt)} />
                    <div className="rounded-lg bg-sky-50 p-3 text-sm font-bold text-sky-800 md:col-span-3">Hàng đang IN_TRANSIT. Tồn chi nhánh chỉ tăng khi receive tạo PNK.</div>
                </>
            ) : <div className="rounded-lg border border-dashed border-[#cfdad2] p-6 text-sm font-bold text-[#697684] md:col-span-3">Chưa ship.</div>}
        </section>
    );
}

function Receiving({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {transfer.lines.map((line) => (
                <div className="grid gap-3 rounded-lg bg-[#f7f8fb] p-3 md:grid-cols-5" key={line.id}>
                    <Info label="SKU" value={line.skuCode} />
                    <Info label="Shipped" value={formatVietnamNumber(line.shippedQuantity)} />
                    <Info label="Good" value={formatVietnamNumber(line.receivedGoodQuantity)} />
                    <Info label="Damaged" value={formatVietnamNumber(line.damagedQuantity)} />
                    <Info label="Missing" value={formatVietnamNumber(line.missingQuantity)} />
                </div>
            ))}
            <TransferDiscrepancyPanel discrepancies={transfer.discrepancies} />
        </section>
    );
}

function Documents({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <DocumentLink code={transfer.issueDocumentCode} icon={<Truck size={18} aria-hidden="true" />} id={transfer.issueDocumentId} label="PXK TRANSFER_ISSUE" />
            <DocumentLink code={transfer.receiptDocumentCode} icon={<PackageCheck size={18} aria-hidden="true" />} id={transfer.receiptDocumentId} label="PNK TRANSFER_RECEIPT" />
        </section>
    );
}

function Audit({ transfer }: { transfer: TransferDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            {transfer.discrepancies.length > 0 ? <ResolveHint transfer={transfer} /> : null}
            {transfer.auditEvents.map((event) => (
                <p className="text-sm font-semibold text-[#536078]" key={event.id}>
                    {formatVietnamDateTime(event.happenedAt)} · {event.actor.name} · {event.note}
                </p>
            ))}
        </section>
    );
}

function ResolveHint({ transfer }: { transfer: TransferDetail }) {
    const open = transfer.discrepancies.find((item) => item.resolutionStatus !== "RESOLVED");
    const [status, setStatus] = useState<TransferDiscrepancyResolutionStatus>("RESOLVED");
    if (!open) return null;
    return (
        <div className="rounded-lg bg-amber-50 p-3 text-sm font-bold text-amber-800">
            Discrepancy workflow: chọn resolution status trong API mock `resolveTransferDiscrepancy`.
            Trạng thái gợi ý: {transferDiscrepancyResolutionLabels[status]}
            <select className="ml-3 h-9 rounded-lg border border-amber-200 bg-white px-3 text-sm font-black" onChange={(event) => setStatus(event.currentTarget.value as TransferDiscrepancyResolutionStatus)} value={status}>
                {Object.entries(transferDiscrepancyResolutionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
        </div>
    );
}

function DocumentLink({ code, icon, id, label }: { code?: string; icon: React.ReactNode; id?: string; label: string }) {
    return (
        <div className="flex items-center justify-between gap-3 rounded-lg bg-[#f7f8fb] p-3">
            <div className="flex items-center gap-2 text-sm font-black text-[#172317]">
                {icon}
                {label}
            </div>
            {id && code ? (
                <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734] hover:underline" to={`/management/inventory/documents/${id}`}>
                    <FileText size={16} aria-hidden="true" />
                    {code}
                </Link>
            ) : <span className="text-sm font-bold text-[#697684]">Chưa tạo</span>}
        </div>
    );
}

function ActionButton({ disabled, label, onClick }: { disabled: boolean; label: string; onClick: () => void }) {
    return <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={disabled} onClick={onClick} type="button">{label}</button>;
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-white px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">{value || "-"}</strong>
        </div>
    );
}

function TextField({ label, onChange, value }: { label: string; onChange: (value: string) => void; value: string }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => onChange(event.currentTarget.value)} value={value} />
        </label>
    );
}

function NumberField({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" min={1} onChange={(event) => onChange(Number(event.currentTarget.value))} type="number" value={value} />
        </label>
    );
}
