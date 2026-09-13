import {
    disposalRequests,
    qualityActor,
    qualityAudit,
    qualityBatches,
    qualityLocations
} from "../mocks/quality.mock";
import type {
    BatchQualityDetail,
    DisposalActionInput,
    DisposalCompleteInput,
    DisposalCreateInput,
    DisposalListQuery,
    QualityActionInput,
    QualityApi,
    QualityBatchQuery,
    QualityCase
} from "../types/quality.types";

function wait<T>(value: T, ms = 320) {
    return new Promise<T>((resolve) => window.setTimeout(() => resolve(value), ms));
}

function cloneBatch(batch: BatchQualityDetail): BatchQualityDetail {
    return { ...batch, auditEvents: [...batch.auditEvents], documentLinks: [...batch.documentLinks], locationBalances: batch.locationBalances.map((item) => ({ ...item })), movements: batch.movements.map((item) => ({ ...item })), qualityEvents: batch.qualityEvents.map((item) => ({ ...item, evidence: [...item.evidence] })), reservations: batch.reservations.map((item) => ({ ...item })) };
}

function cloneDisposal(id: string) {
    const disposal = disposalRequests.find((item) => item.id === id);
    if (!disposal) throw new Error("Không tìm thấy yêu cầu tiêu hủy.");
    return { ...disposal, auditEvents: [...disposal.auditEvents], lines: disposal.lines.map((line) => ({ ...line, evidence: [...line.evidence] })), witnesses: [...disposal.witnesses] };
}

function findBatch(batchId: string) {
    const batch = qualityBatches.find((item) => item.id === batchId);
    if (!batch) throw new Error("Không tìm thấy batch.");
    return batch;
}

function daysRemaining(expiryDate: string) {
    return Math.ceil((new Date(expiryDate).getTime() - Date.now()) / 86400000);
}

export const qualityApi: QualityApi = {
    approveDisposalRequest(input: DisposalActionInput) {
        const disposal = disposalRequests.find((item) => item.id === input.disposalId);
        if (!disposal) return Promise.reject(new Error("Không tìm thấy yêu cầu tiêu hủy."));
        disposal.status = "APPROVED";
        disposal.approvedAt = new Date().toISOString();
        disposal.approvedBy = qualityActor("ADMIN");
        disposal.auditEvents = [qualityAudit("APPROVE", "ADMIN", "Duyệt yêu cầu tiêu hủy."), ...disposal.auditEvents];
        return wait(cloneDisposal(disposal.id));
    },

    blockBatch(input: QualityActionInput) {
        const batch = findBatch(input.batchId);
        batch.status = "BLOCKED";
        batch.blockedQuantity = batch.onHandQuantity;
        batch.availableQuantity = 0;
        batch.auditEvents = [qualityAudit("APPROVE", "ADMIN", input.note ?? "Block batch khỏi available."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    completeDisposalRequest(input: DisposalCompleteInput) {
        const disposal = disposalRequests.find((item) => item.id === input.disposalId);
        if (!disposal) return Promise.reject(new Error("Không tìm thấy yêu cầu tiêu hủy."));
        if (!["APPROVED", "SCHEDULED"].includes(disposal.status)) return Promise.reject(new Error("Chỉ complete yêu cầu đã duyệt/lên lịch."));
        input.lines.forEach((lineInput) => {
            const line = disposal.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng tiêu hủy.");
            if (line.reservedQuantity > 0) throw new Error("Không tiêu hủy hàng vẫn đang reserved.");
            if (lineInput.actualDisposedQuantity > line.quantity) throw new Error("Actual disposed không được vượt quantity.");
            line.actualDisposedQuantity = lineInput.actualDisposedQuantity;
            const batch = findBatch(line.batchId);
            batch.onHandQuantity = Math.max(0, batch.onHandQuantity - lineInput.actualDisposedQuantity);
            batch.blockedQuantity = Math.max(0, batch.blockedQuantity - lineInput.actualDisposedQuantity);
            if (batch.onHandQuantity === 0) batch.status = "DISPOSED";
        });
        disposal.status = "COMPLETED";
        disposal.completedAt = input.completedAt;
        disposal.completedBy = qualityActor("ADMIN");
        disposal.certificate = input.certificate;
        disposal.evidenceAfterDisposal = input.evidenceAfterDisposal;
        disposal.witnesses = input.witnesses;
        disposal.stockDocumentId = `doc-writeoff-${disposal.id}`;
        disposal.stockDocumentCode = `PXK-WRITEOFF-${disposal.code}`;
        disposal.auditEvents = [qualityAudit("POST", "ADMIN", "Complete disposal, tạo PXK WRITE_OFF và giảm tồn vật lý."), ...disposal.auditEvents];
        return wait(cloneDisposal(disposal.id));
    },

    createDisposalRequest(input: DisposalCreateInput) {
        const location = qualityLocations.find((item) => item.id === input.locationId) ?? qualityLocations[0];
        const lines = input.lines.map((lineInput, index) => {
            const batch = findBatch(lineInput.batchId);
            if (batch.reservedQuantity > 0) throw new Error(`${batch.batchCode}: cần release/cancel reservation trước khi tiêu hủy.`);
            const disposable = Math.min(batch.onHandQuantity - batch.reservedQuantity, Math.max(batch.blockedQuantity, batch.damagedQuantity));
            if (lineInput.quantity <= 0 || lineInput.quantity > disposable) throw new Error(`${batch.batchCode}: quantity không được vượt blocked/damaged.`);
            return { actualDisposedQuantity: 0, batchCode: batch.batchCode, batchId: batch.id, estimatedValue: lineInput.quantity * batch.unitCost, evidence: lineInput.evidence, id: `dsp-line-${Date.now()}-${index}`, quantity: lineInput.quantity, reason: lineInput.reason, reservedQuantity: batch.reservedQuantity, skuCode: batch.skuCode, skuName: batch.skuName, unit: batch.unit, unitCost: batch.unitCost };
        });
        const disposal = { auditEvents: [qualityAudit("SUBMIT", "ADMIN", "Tạo yêu cầu tiêu hủy.")], code: `DSP-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${String(disposalRequests.length + 1).padStart(3, "0")}`, id: `disposal-${Date.now()}`, lines, location, method: input.method, note: input.note, reason: input.reason, requestedAt: new Date().toISOString(), requestedBy: qualityActor("ADMIN"), scheduledAt: input.scheduledAt, status: "SUBMITTED" as const, witnesses: input.witnesses };
        disposalRequests.unshift(disposal);
        return wait(cloneDisposal(disposal.id));
    },

    createQualityCase(input: Omit<QualityCase, "id" | "code" | "status">) {
        const batch = findBatch(input.batchId);
        batch.qualityEvents.unshift({ ...input, code: `QCASE-${batch.qualityEvents.length + 1}`, id: `qcase-${Date.now()}`, status: "OPEN" });
        batch.auditEvents = [qualityAudit("SUBMIT", "ADMIN", "Tạo quality case."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    getBatchQualityDetail(batchId: string) {
        return wait(cloneBatch(findBatch(batchId)));
    },

    getDisposalRequest(disposalId: string) {
        return wait(cloneDisposal(disposalId));
    },

    listDisposalRequests(query: DisposalListQuery) {
        const filtered = disposalRequests.filter((item) =>
            (query.status === "ALL" || item.status === query.status) &&
            (!query.code || item.code.includes(query.code)) &&
            (query.locationId === "ALL" || item.location.id === query.locationId) &&
            (!query.reason || item.reason.toLowerCase().includes(query.reason.toLowerCase()))
        );
        const start = query.page * query.size;
        return wait({
            content: filtered.slice(start, start + query.size).map((item) => cloneDisposal(item.id)),
            page: query.page,
            size: query.size,
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    listQualityBatches(query: QualityBatchQuery) {
        const content = qualityBatches.filter((batch) => {
            const tab =
                query.tab === "NEAR_EXPIRY"
                    ? daysRemaining(batch.expiryDate) > 0 && daysRemaining(batch.expiryDate) <= 30
                    : query.tab === "EXPIRED"
                    ? daysRemaining(batch.expiryDate) <= 0
                    : query.tab === "QUARANTINE"
                    ? batch.status === "QUARANTINE"
                    : query.tab === "DAMAGED"
                    ? batch.status === "DAMAGED"
                    : query.tab === "DISPOSED"
                    ? batch.status === "DISPOSED"
                    : disposalRequests.some((item) => item.status === "SUBMITTED" && item.lines.some((line) => line.batchId === batch.id));
            return (
                tab &&
                (query.locationId === "ALL" || batch.location.id === query.locationId) &&
                (!query.sku || `${batch.skuCode} ${batch.skuName}`.toLowerCase().includes(query.sku.toLowerCase())) &&
                (!query.batchCode || batch.batchCode.toLowerCase().includes(query.batchCode.toLowerCase())) &&
                (!query.supplier || batch.supplier.toLowerCase().includes(query.supplier.toLowerCase())) &&
                (query.status === "ALL" || batch.status === query.status) &&
                batch.estimatedValue >= query.minValue
            );
        });
        return wait({
            content: content.map((batch) => ({ ...batch })),
            summary: {
                damagedValue: qualityBatches.filter((item) => item.status === "DAMAGED").reduce((sum, item) => sum + item.estimatedValue, 0),
                expiredUnhandled: qualityBatches.filter((item) => daysRemaining(item.expiryDate) <= 0 && item.status !== "DISPOSED").length,
                expiry30Days: qualityBatches.filter((item) => daysRemaining(item.expiryDate) > 0 && daysRemaining(item.expiryDate) <= 30).length,
                expiry7Days: qualityBatches.filter((item) => daysRemaining(item.expiryDate) > 0 && daysRemaining(item.expiryDate) <= 7).length,
                pendingDisposalApprovals: disposalRequests.filter((item) => item.status === "SUBMITTED").length,
                quarantineOverSla: qualityBatches.filter((item) => item.status === "QUARANTINE").length
            }
        });
    },

    markBatchDamaged(input: QualityActionInput) {
        const batch = findBatch(input.batchId);
        batch.status = "DAMAGED";
        batch.damagedQuantity = batch.onHandQuantity;
        batch.blockedQuantity = batch.onHandQuantity;
        batch.availableQuantity = 0;
        batch.auditEvents = [qualityAudit("APPROVE", "ADMIN", input.note ?? "Mark damaged."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    moveBatchToQuarantine(input: QualityActionInput) {
        const batch = findBatch(input.batchId);
        batch.status = "QUARANTINE";
        batch.quarantineSince = new Date().toISOString();
        batch.blockedQuantity = batch.onHandQuantity;
        batch.availableQuantity = 0;
        batch.auditEvents = [qualityAudit("APPROVE", "ADMIN", "Move to quarantine."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    rejectDisposalRequest(input: DisposalActionInput) {
        if (!input.reason?.trim()) return Promise.reject(new Error("Reject bắt buộc reason."));
        const disposal = disposalRequests.find((item) => item.id === input.disposalId);
        if (!disposal) return Promise.reject(new Error("Không tìm thấy yêu cầu tiêu hủy."));
        disposal.status = "REJECTED";
        disposal.rejectedReason = input.reason;
        disposal.auditEvents = [qualityAudit("REJECT", "ADMIN", input.reason), ...disposal.auditEvents];
        return wait(cloneDisposal(disposal.id));
    },

    releaseBatchFromQuarantine(input: QualityActionInput) {
        const batch = findBatch(input.batchId);
        if (batch.status !== "QUARANTINE") return Promise.reject(new Error("Batch không ở quarantine."));
        if (batch.qcResult !== "PASSED") return Promise.reject(new Error("Chưa có QC result PASSED, không thể release quarantine."));
        batch.status = "AVAILABLE";
        batch.availableQuantity = batch.onHandQuantity - batch.reservedQuantity;
        batch.blockedQuantity = 0;
        batch.auditEvents = [qualityAudit("APPROVE", "ADMIN", "Release from quarantine."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    unblockBatch(input: QualityActionInput) {
        const batch = findBatch(input.batchId);
        if (daysRemaining(batch.expiryDate) <= 0 && !input.adminOverride) return Promise.reject(new Error("Không được unblock batch hết hạn nếu không có ADMIN override."));
        batch.status = "AVAILABLE";
        batch.blockedQuantity = 0;
        batch.availableQuantity = batch.onHandQuantity - batch.reservedQuantity;
        batch.auditEvents = [qualityAudit("APPROVE", "ADMIN", "Unblock batch."), ...batch.auditEvents];
        return wait(cloneBatch(batch));
    },

    updateQualityCase(caseId: string, patch: Partial<QualityCase>) {
        const batch = qualityBatches.find((item) => item.qualityEvents.some((event) => event.id === caseId));
        if (!batch) return Promise.reject(new Error("Không tìm thấy quality case."));
        batch.qualityEvents = batch.qualityEvents.map((event) => event.id === caseId ? { ...event, ...patch } : event);
        return wait(cloneBatch(batch));
    }
};
