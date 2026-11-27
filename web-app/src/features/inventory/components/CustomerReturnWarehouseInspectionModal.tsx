import { useEffect, useRef, useState } from "react";
import { AlertCircle, RotateCw, X } from "lucide-react";

import { useOrderReturnAccess } from "@/auth/order-return-access";
import { useAuthorization } from "@/auth/useAuthorization";
import { Button, IconButton } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/Field";
import { ModalShell } from "@/components/ui/Overlay";
import { ApiError } from "@/lib/api-client";

import {
    getOrderReturnDetail,
    getReturnInventoryDisposition,
    submitReturnInventoryDisposition
} from "../../order/services/order-return.api";
import { submitVerifiedReturnInventoryDisposition } from "../../order/services/return-inventory-disposition.service";
import type {
    CustomerReturnQcSummaryResponse,
    OrderReturnInventoryDispositionResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    SubmitReturnInventoryDispositionPayload
} from "../../order/types/order-return.types";
import {
    canInspectCustomerReturn,
    formatReturnQuantity,
    getReturnErrorMessage,
    toReturnNumber
} from "../../order/utils/order-return.utils";
import { customerReturnApi, loadCustomerReturnQcLocations } from "../services/customerReturn.api";
import type {
    CustomerReturnInspection,
    CustomerReturnInspectionLine,
    CustomerReturnQcDraft,
    CustomerReturnQcLocation
} from "../types/customer-return-inspection.types";
import { createCustomerReturnQcDraft, getCustomerReturnQcError } from "../utils/customer-return-qc";
import { CustomerReturnInspectionCard } from "./CustomerReturnInspectionCard";
import { DamagedReturnDisposalPanel } from "./DamagedReturnDisposalPanel";
import { InspectionStatusBadge } from "./CustomerReturnInspectionComponents";
import styles from "./CustomerReturnWarehouseInspectionModal.module.css";

export interface CustomerReturnWarehouseInspectionModalProps {
    isOpen: boolean;
    onClose: () => void;
    returnId: string;
    returnCode?: string | null;
    returnItem?: OrderReturnListRow | null;
    inspectionId?: string | null;
    onSuccess?: () => void;
}

type PreparationLine = CustomerReturnQcDraft & { selected: boolean; quantity: string };

interface InspectionProgress {
    preparation: Record<string, PreparationLine>;
    request?: SubmitReturnInventoryDispositionPayload;
    inspectionDraft?: { id: string; lines: Record<string, CustomerReturnQcDraft> };
}

interface InspectionSnapshot {
    detail: OrderReturnResponse | null;
    summary: CustomerReturnQcSummaryResponse | null;
    inspection: CustomerReturnInspection | null;
    command: OrderReturnInventoryDispositionResponse | null;
    commandMissing: boolean;
    error: string;
}

function readProgress(key: string): InspectionProgress {
    try {
        const stored = sessionStorage.getItem(key);
        if (stored) {
            const parsed = JSON.parse(stored) as InspectionProgress | null;
            if (parsed?.preparation && typeof parsed.preparation === "object") return parsed;
        }
    } catch {
        // The server still owns accepted commands and inspection status.
    }
    return { preparation: {} };
}

function isActive(inspection: CustomerReturnInspection | null) {
    return inspection?.status === "CREATED" || inspection?.status === "PROCESSING";
}

const POLL_INTERVAL_MS = 2500;

export function CustomerReturnWarehouseInspectionModal({
    isOpen, onClose, returnId, returnCode, returnItem, inspectionId, onSuccess
}: CustomerReturnWarehouseInspectionModalProps) {
    const { userId } = useAuthorization();
    const { canDisposition } = useOrderReturnAccess();
    const storageKey = `return-inspection:${userId ?? "session"}:${returnId}`;
    const [progress, setProgress] = useState<InspectionProgress>(() => readProgress(storageKey));
    const [snapshot, setSnapshot] = useState<InspectionSnapshot | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [locationRefreshKey, setLocationRefreshKey] = useState(0);
    const [locations, setLocations] = useState<CustomerReturnQcLocation[] | null>(null);
    const [locationError, setLocationError] = useState("");
    const [notice, setNotice] = useState("");
    const [error, setError] = useState("");
    const [attempted, setAttempted] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const actionInFlight = useRef(false);
    const commandId = progress.request?.commandId;

    function saveProgress(next: InspectionProgress) {
        setProgress(next);
        try {
            sessionStorage.setItem(storageKey, JSON.stringify(next));
        } catch {
            // A failed browser write must never trigger a second command.
        }
    }

    useEffect(() => {
        if (!isOpen) return;
        let active = true;
        const commandLookup = commandId
            ? getReturnInventoryDisposition(returnId, commandId).then((command) => ({ command, missing: false }))
                .catch((cause: unknown) => {
                    if (cause instanceof ApiError && cause.status === 404) return { command: null, missing: true };
                    throw cause;
                })
            : Promise.resolve({ command: null, missing: false });

        void Promise.allSettled([
            getOrderReturnDetail(returnId),
            customerReturnApi.getQcSummary(returnId),
            commandLookup
        ]).then(async ([order, qc, status]) => {
            if (!active) return;
            const detail = order.status === "fulfilled" ? order.value : null;
            const summary = qc.status === "fulfilled" ? qc.value : null;
            const command = status.status === "fulfilled" ? status.value.command : null;
            const target = inspectionId ?? summary?.inspectionId ?? command?.inspectionId;
            let inspection: CustomerReturnInspection | null = null;
            let loadError = (!summary || !detail)
                ? "Chưa tải đủ thông tin phiếu hoàn và kiểm định. Vui lòng tải lại."
                : "";
            if (status.status === "rejected") {
                loadError = "Chưa đối chiếu được yêu cầu đã gửi. Vui lòng tải lại để kiểm tra tiến độ.";
            }
            if (target) {
                try {
                    const item = await customerReturnApi.getInspection(target);
                    if (item.returnId !== returnId) throw new Error("Phiếu kiểm định không thuộc phiếu hoàn này.");
                    inspection = item;
                } catch (cause: unknown) {
                    loadError = getReturnErrorMessage(cause, "Không tải được phiếu kiểm định.");
                }
            }
            if (active) setSnapshot({
                detail, summary, inspection, command, error: loadError,
                commandMissing: status.status === "fulfilled" && status.value.missing
            });
        });
        return () => { active = false; };
    }, [commandId, inspectionId, isOpen, refreshKey, returnId]);

    useEffect(() => {
        if (!isOpen || !canDisposition) return;
        let active = true;
        void loadCustomerReturnQcLocations()
            .then((items) => { if (active) { setLocations(items); setLocationError(""); } })
            .catch(() => { if (active) setLocationError("Không tải được vị trí kho. Vui lòng tải lại."); });
        return () => { active = false; };
    }, [canDisposition, isOpen, locationRefreshKey]);

    const detail = snapshot?.detail;
    const summary = snapshot?.summary;
    const inspection = snapshot?.inspection;
    const command = snapshot?.command;
    const row = snapshot ? detail : returnItem;
    const qcLines = summary?.lines ?? [];
    const received = (row?.status ?? summary?.returnStatus) === "RECEIVED";
    const qcStatus = summary?.qcStatus ?? (!snapshot ? returnItem?.qcStatus : null);
    const completed = qcStatus === "COMPLETED" || row?.physicalDone || row?.status === "COMPLETED";
    const failed = !completed && (command?.status === "FAILED" || row?.hasFailedDisposition
        || row?.inventoryProcessingStatus === "FAILED" || row?.displayStatus === "PROCESSING_FAILED");
    const commandPending = command?.status === "PENDING" || row?.isPendingDisposition
        || (row?.isPendingDisposition == null && (row?.inventoryProcessingStatus === "PROCESSING" || row?.displayStatus === "PROCESSING"));
    const pending = !completed && !failed && !isActive(inspection ?? null) && (commandPending
        || summary?.inspectionStatus === "CREATED" || summary?.inspectionStatus === "PROCESSING");
    const remaining = qcLines.filter((line) => toReturnNumber(line.remainingQcQuantity) > 0);
    const canPrepare = Boolean(canDisposition && !inspectionId && !completed && !failed && !pending
        && !isActive(inspection ?? null) && !snapshot?.error && detail && summary?.returnStatus === "RECEIVED"
        && canInspectCustomerReturn({ ...detail, qcStatus: summary.qcStatus }) && remaining.length);
    const canRetry = Boolean(canPrepare && progress.request && snapshot?.commandMissing);
    const editingPreparation = canPrepare && (!progress.request || command?.status === "PROCESSED");
    const canPost = Boolean(canDisposition && received && !failed && !completed && inspection?.status === "CREATED");
    const shouldPoll = Boolean(pending || inspection?.status === "PROCESSING" || (commandPending && inspection?.status === "POSTED"));

    useEffect(() => {
        if (!isOpen || !snapshot || !shouldPoll || submitting) return;
        const timeout = window.setTimeout(() => setRefreshKey((value) => value + 1), POLL_INTERVAL_MS);
        return () => window.clearTimeout(timeout);
    }, [isOpen, shouldPoll, snapshot, submitting]);

    if (!isOpen) return null;

    const activeLocations = locations ?? [];
    const preparingNext = command?.status === "PROCESSED";
    const preparation = preparingNext ? {} : progress.preparation;

    function preparationLine(id: string, maximum: number): PreparationLine {
        return preparation[id] ?? {
            selected: remaining.length === 1, quantity: String(maximum),
            actualCondition: "", result: "", destinationLocationId: "", note: ""
        };
    }

    function editPreparation(id: string, maximum: number, update: Partial<PreparationLine>) {
        saveProgress({
            preparation: { ...preparation, [id]: { ...preparationLine(id, maximum), ...update } }
        });
        setError("");
    }

    function inspectionDraft(inspected: CustomerReturnInspection, line: CustomerReturnInspectionLine): CustomerReturnQcDraft {
        const initial = createCustomerReturnQcDraft(line);
        if (line.posted || inspected.status === "POSTED") return initial;
        const saved = progress.inspectionDraft;
        if (saved?.id === inspected.id && saved.lines[line.id]) return saved.lines[line.id];
        const prepared = commandId && inspected.commandId === commandId ? progress.preparation[line.returnItemId] : null;
        return { ...initial, actualCondition: initial.actualCondition || prepared?.actualCondition || "" };
    }

    async function requestInspection() {
        if (actionInFlight.current || (!editingPreparation && !canRetry)) return;
        let request = canRetry ? progress.request : undefined;
        let submittedPreparation = preparation;
        if (!request) {
            setAttempted(true);
            const selected = remaining.flatMap((line) => {
                const draft = preparationLine(line.returnItemId, toReturnNumber(line.remainingQcQuantity));
                return draft.selected ? [{ line, draft }] : [];
            });
            if (!selected.length) {
                setError("Chọn ít nhất một dòng hàng để kiểm định.");
                return;
            }
            const lines: SubmitReturnInventoryDispositionPayload["lines"] = [];
            for (const { line, draft } of selected) {
                const quantity = Number(draft.quantity);
                const issue = getCustomerReturnQcError({}, draft, activeLocations);
                if (!/^\d+(?:\.\d{1,3})?$/.test(draft.quantity) || quantity <= 0 || quantity > toReturnNumber(line.remainingQcQuantity)) {
                    setError("Số lượng phải lớn hơn 0, có tối đa 3 chữ số thập phân và không vượt quá số lượng còn lại.");
                    return;
                }
                if (issue || !draft.result || draft.result === "QUARANTINE") {
                    setError(issue ?? "Chọn phương án xử lý hợp lệ.");
                    return;
                }
                lines.push({ returnItemId: line.returnItemId, quantity, result: draft.result,
                    destinationLocationId: draft.result === "WRITE_OFF" ? null : draft.destinationLocationId,
                    note: draft.note.trim() || null });
            }
            request = { commandId: crypto.randomUUID(), lines };
            submittedPreparation = Object.fromEntries(selected.map(({ line, draft }) => [line.returnItemId, draft]));
            saveProgress({ preparation: submittedPreparation, request });
        }
        actionInFlight.current = true;
        setSubmitting(true);
        setError("");
        setNotice("");
        try {
            const reply = await (canRetry ? submitReturnInventoryDisposition : submitVerifiedReturnInventoryDisposition)(returnId, request);
            setSnapshot((previous) => previous ? { ...previous, command: reply, commandMissing: false } : previous);
            if (reply.status !== "FAILED") setNotice("Đã gửi yêu cầu. Phiếu kiểm định sẽ hiện tại đây khi kho tiếp nhận.");
            onSuccess?.();
        } catch (cause: unknown) {
            const rejected = cause instanceof ApiError && cause.status >= 400 && cause.status < 500
                && ![408, 429, 499].includes(cause.status);
            if (rejected) saveProgress({ preparation: submittedPreparation });
            setError(getReturnErrorMessage(cause, "Không thể gửi yêu cầu kiểm định."));
        } finally {
            actionInFlight.current = false;
            setSubmitting(false);
            setRefreshKey((value) => value + 1);
        }
    }

    async function postInspection() {
        if (!inspection || !canPost || actionInFlight.current) return;
        setAttempted(true);
        const pendingLines = inspection.lines.filter((line) => !line.posted);
        if (!pendingLines.length) return;
        const lines = pendingLines.map((line) => ({ line, draft: inspectionDraft(inspection, line) }));
        if (lines.some(({ line, draft }) => getCustomerReturnQcError(line, draft, activeLocations))) {
            setError("Hoàn thiện các dòng kiểm định được đánh dấu trước khi xác nhận xử lý.");
            return;
        }
        actionInFlight.current = true;
        setSubmitting(true);
        setError("");
        try {
            const updated = await customerReturnApi.postInspection(inspection.id, { lines: lines.map(({ line, draft }) => ({
                lineId: line.id, returnItemId: line.returnItemId, actualCondition: draft.actualCondition || undefined,
                result: draft.result || undefined,
                destinationLocationId: draft.result === "WRITE_OFF" ? undefined : draft.destinationLocationId,
                note: draft.note.trim()
            })) });
            setSnapshot((previous) => previous ? { ...previous, inspection: updated } : previous);
            saveProgress(updated.status === "POSTED" ? { preparation: {} } : { ...progress, inspectionDraft: undefined });
            setNotice(updated.status === "POSTED" ? "Đã hạch toán phiếu kiểm định." : "Kho đang xử lý kết quả kiểm định.");
            onSuccess?.();
        } catch (cause: unknown) {
            setError(getReturnErrorMessage(cause, "Không thể xác nhận xử lý kiểm định."));
        } finally {
            actionInFlight.current = false;
            setSubmitting(false);
            setRefreshKey((value) => value + 1);
        }
    }

    const itemById = new Map((detail?.items ?? returnItem?.items ?? []).map((item) => [item.id, item]));
    const quantityReturned = summary
        ? qcLines.reduce((sum, line) => sum + toReturnNumber(line.returnedQuantity), 0)
        : row?.requiredQuantity;
    const qcPosted = summary
        ? qcLines.reduce((sum, line) => sum + toReturnNumber(line.postedQcQuantity), 0)
        : !snapshot ? returnItem?.postedQcQuantity : undefined;
    const quantityPosted = qcPosted ?? row?.processedQuantity;
    const quantityRemaining = quantityReturned != null && quantityPosted != null
        ? Math.max(0, toReturnNumber(quantityReturned) - toReturnNumber(quantityPosted))
        : row?.remainingQuantity;
    const hasDiscrepancy = qcPosted != null && row?.processedQuantity != null
        && toReturnNumber(row.processedQuantity) !== toReturnNumber(qcPosted);

    return (
        <ModalShell className="w-[min(1080px,96vw)]" onClose={onClose}>
            <section aria-label="Phiếu kiểm định kho" className={styles.modalCard}>
                <header className={styles.modalHeader}>
                    <div className={styles.headerTitleGroup}>
                        <div>
                            <h2>Kiểm định hàng trả</h2>
                            <p className="text-sm text-slate-600">
                                Phiếu hoàn {summary?.returnCode ?? returnCode ?? returnItem?.returnCode ?? "đang tải"}
                                {(detail?.orderCode ?? summary?.orderCode ?? returnItem?.orderCode)
                                    ? ` · Đơn ${detail?.orderCode ?? summary?.orderCode ?? returnItem?.orderCode}` : ""}
                                {inspection ? ` · ${inspection.inspectionCode}` : ""}
                            </p>
                        </div>
                        {inspection && <InspectionStatusBadge status={inspection.status} />}
                    </div>
                    <div className="flex items-center gap-2">
                        <IconButton aria-label="Tải lại" onClick={() => {
                            setRefreshKey((value) => value + 1);
                            setLocationRefreshKey((value) => value + 1);
                        }}><RotateCw size={17} /></IconButton>
                        <IconButton aria-label="Đóng" onClick={onClose}><X size={19} /></IconButton>
                    </div>
                </header>
                <div className={styles.modalBody}>
                    {!snapshot && <p role="status">Đang tải thông tin kiểm định…</p>}
                    <div className={styles.qcSummaryCard}>
                        <div className={styles.qcSummaryItem}>Tổng SL hàng trả <strong>{quantityReturned == null ? "—" : formatReturnQuantity(quantityReturned)}</strong></div>
                        <div className={styles.qcSummaryItem}>Đã kiểm hàng <strong>{quantityPosted == null ? "—" : formatReturnQuantity(quantityPosted)}</strong></div>
                        <div className={styles.qcSummaryItem}>Còn cần kiểm hàng <strong>{quantityRemaining == null ? "—" : formatReturnQuantity(quantityRemaining)}</strong></div>
                    </div>
                    {hasDiscrepancy && <p role="status">Order và Kho đang có số liệu xử lý khác nhau. Số lượng hiển thị theo dữ liệu kiểm định của Kho.</p>}
                    {notice && <p className="rounded-lg bg-green-50 p-3 text-green-900" role="status">{notice}</p>}
                    {(error || snapshot?.error || locationError) && <div className="flex gap-2 rounded-lg bg-red-50 p-3 text-red-800" role="alert">
                        <AlertCircle size={18} />
                        {error || snapshot?.error || locationError}
                    </div>}
                    {failed && <p className="rounded-lg bg-amber-50 p-4 text-amber-900" role="alert">
                        Yêu cầu xử lý kho thất bại. Cần đối soát trước khi tiếp tục.
                    </p>}
                    {pending && <p className="rounded-lg bg-blue-50 p-4 text-blue-900" role="status">
                        Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho. Trạng thái sẽ tự cập nhật.
                    </p>}
                    {qcStatus === "PARTIAL" && !pending && !failed && <p role="status">Đang kiểm hàng</p>}
                    {inspection?.status === "PROCESSING" && !failed && <p role="status">Kho đang xử lý phiếu kiểm định. Trạng thái sẽ tự cập nhật.</p>}
                    {completed && <p role="status">Hàng trả đã hoàn tất kiểm định.</p>}
                    {snapshot && !inspection && !pending && !failed && !completed && !editingPreparation && !canRetry && !snapshot.error &&
                        <p>Chưa có phiếu kiểm định. {received ? "Chưa đủ điều kiện tạo yêu cầu kiểm định." : "Cần xác nhận nhận hàng trước khi kiểm định."}</p>}
                    {canRetry && <p role="status">Chưa tìm thấy yêu cầu đã gửi. Có thể gửi lại cùng yêu cầu để kiểm tra.</p>}

                    {editingPreparation && <div className={styles.cardsSection}>
                        <p className="text-sm text-slate-600">Chưa có yêu cầu kiểm định cho số lượng còn lại. Chọn hàng và nhập kết quả kiểm tra để tạo phiếu.</p>
                        {remaining.map((line) => {
                            const item = itemById.get(line.returnItemId);
                            const maximum = toReturnNumber(line.remainingQcQuantity);
                            const draft = preparationLine(line.returnItemId, maximum);
                            return <CustomerReturnInspectionCard
                                key={line.returnItemId}
                                line={{ id: line.returnItemId, quantity: maximum }}
                                draft={draft}
                                editable={draft.selected && !submitting}
                                isExpanded
                                attemptedSubmit={attempted && draft.selected}
                                declaredCondition={line.condition}
                                reason={line.reason}
                                productName={item?.productNameSnapshot}
                                sku={item?.skuSnapshot}
                                locations={activeLocations}
                                onChange={(update) => editPreparation(line.returnItemId, maximum, update)}
                                quantityEditor={<div className="flex flex-wrap items-center gap-3 text-sm">
                                    <label className="flex items-center gap-2">
                                        <input checked={draft.selected} disabled={submitting} type="checkbox"
                                            onChange={(event) => editPreparation(line.returnItemId, maximum, { selected: event.target.checked })} />
                                        Chọn dòng kiểm định
                                    </label>
                                    {draft.selected && <label className="flex items-center gap-2">
                                        Số lượng
                                        <TextInput className="w-24" controlSize="sm" disabled={submitting} type="number"
                                            min="0.001" max={maximum} step="0.001" value={draft.quantity}
                                            onChange={(event) => editPreparation(line.returnItemId, maximum, { quantity: event.target.value })} />
                                    </label>}
                                </div>}
                            />;
                        })}
                    </div>}

                    {inspection && !editingPreparation && !pending && <div className={styles.cardsSection}>
                        {inspection.lines.map((line) => {
                            const item = itemById.get(line.returnItemId);
                            const draft = inspectionDraft(inspection, line);
                            return <CustomerReturnInspectionCard
                                key={line.id}
                                line={line}
                                draft={draft}
                                editable={canPost && !submitting && !line.posted}
                                attemptedSubmit={attempted}
                                declaredCondition={item?.condition}
                                reason={line.returnReason ?? item?.reason}
                                productName={item?.productNameSnapshot}
                                sku={item?.skuSnapshot}
                                locations={activeLocations}
                                onChange={(update) => saveProgress({
                                    ...progress,
                                    inspectionDraft: { id: inspection.id, lines: {
                                        ...(progress.inspectionDraft?.id === inspection.id ? progress.inspectionDraft.lines : {}),
                                        [line.id]: { ...draft, ...update }
                                    } }
                                })}
                            />;
                        })}
                    </div>}

                    {inspection && !editingPreparation && !pending && <DamagedReturnDisposalPanel
                        canRequest={canDisposition}
                        inspection={inspection}
                        onCreated={() => setRefreshKey((value) => value + 1)}
                    />}
                </div>
                <footer className={styles.modalFooter}>
                    <div className="ml-auto flex gap-2">
                        <Button onClick={onClose}>Đóng</Button>
                        {(editingPreparation || canRetry) && <Button disabled={submitting}
                            onClick={() => void requestInspection()} variant="primary">
                            {submitting ? "Đang gửi…" : canRetry ? "Gửi lại yêu cầu" : "Tạo phiếu kiểm định"}
                        </Button>}
                        {canPost && <Button disabled={submitting} onClick={() => void postInspection()} variant="primary">
                            {submitting ? "Đang xử lý…" : "Xác nhận xử lý"}
                        </Button>}
                    </div>
                </footer>
            </section>
        </ModalShell>
    );
}
