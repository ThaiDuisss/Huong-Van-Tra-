import {
    supplierReturnActor,
    supplierReturnAudit,
    supplierReturnMockData,
    supplierReturnOptions
} from "../mocks/supplier-returns.mock";
import type {
    SupplierReturnActionInput,
    SupplierReturnApi,
    SupplierReturnCreateInput,
    SupplierReturnDetail,
    SupplierReturnLine,
    SupplierReturnListQuery,
    SupplierReturnPrepareInput,
    SupplierReturnRejectInput,
    SupplierReturnSettlementInput,
    SupplierReturnShipInput
} from "../types/supplier-return.types";

const cache = [...supplierReturnMockData];

function wait<T>(value: T, ms = 340) {
    return new Promise<T>((resolve) => window.setTimeout(() => resolve(value), ms));
}

function copy(detail: SupplierReturnDetail): SupplierReturnDetail {
    return { ...detail, auditEvents: [...detail.auditEvents], lines: detail.lines.map((line) => ({ ...line, evidence: [...line.evidence] })) };
}

function findReturn(id: string) {
    const detail = cache.find((item) => item.id === id);
    if (!detail) throw new Error("Không tìm thấy phiếu trả nhà cung cấp.");
    return detail;
}

function summary(detail: SupplierReturnDetail) {
    return {
        code: detail.code,
        createdAt: detail.createdAt,
        createdBy: detail.createdBy,
        creditNoteCode: detail.creditNoteCode,
        id: detail.id,
        receiptCode: detail.receiptCode,
        replacementReceiptCode: detail.replacementReceiptCode,
        resolutionRequested: detail.resolutionRequested,
        status: detail.status,
        supplier: detail.supplier,
        supplierInvoiceCode: detail.supplierInvoiceCode,
        totalQuantity: detail.totalQuantity,
        totalSku: detail.totalSku,
        totalValue: detail.totalValue
    };
}

function recalc(detail: SupplierReturnDetail) {
    detail.totalSku = detail.lines.length;
    detail.totalQuantity = detail.lines.reduce((sum, line) => sum + line.returnQuantity, 0);
    detail.totalValue = detail.lines.reduce((sum, line) => sum + line.estimatedValue, 0);
}

function matchesDate(value: string, from: string, to: string) {
    const time = new Date(value).getTime();
    if (from && time < new Date(`${from}T00:00:00`).getTime()) return false;
    if (to && time > new Date(`${to}T23:59:59`).getTime()) return false;
    return true;
}

export const supplierReturnApi: SupplierReturnApi = {
    approveSupplierReturn(input: SupplierReturnActionInput) {
        const detail = findReturn(input.supplierReturnId);
        if (detail.status !== "SUBMITTED") return Promise.reject(new Error("Chỉ duyệt phiếu đã submit."));
        detail.status = "APPROVED";
        detail.approvedAt = new Date().toISOString();
        detail.approvedBy = supplierReturnActor("ADMIN");
        detail.auditEvents = [supplierReturnAudit("APPROVE", "ADMIN", "Duyệt trả nhà cung cấp."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    cancelSupplierReturn(input: SupplierReturnActionInput) {
        const detail = findReturn(input.supplierReturnId);
        if (["SHIPPED", "CREDIT_PENDING", "REPLACEMENT_PENDING", "COMPLETED"].includes(detail.status)) {
            return Promise.reject(new Error("Không thể hủy phiếu đã ship."));
        }
        detail.status = "CANCELLED";
        detail.auditEvents = [supplierReturnAudit("CANCEL", "ADMIN", input.note ?? "Hủy phiếu trả NCC."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    completeSupplierReturn(input: SupplierReturnActionInput) {
        const detail = findReturn(input.supplierReturnId);
        if (!["CREDIT_PENDING", "REPLACEMENT_PENDING", "SHIPPED"].includes(detail.status)) {
            return Promise.reject(new Error("Chỉ hoàn tất sau khi đã ship và theo dõi settlement."));
        }
        if (detail.resolutionRequested === "CREDIT_NOTE" && !detail.creditNoteCode) {
            return Promise.reject(new Error("Credit note chưa được cập nhật."));
        }
        if (detail.resolutionRequested === "REPLACEMENT" && !detail.replacementReceiptCode) {
            return Promise.reject(new Error("Receipt hàng thay thế chưa được cập nhật."));
        }
        detail.status = "COMPLETED";
        detail.auditEvents = [supplierReturnAudit("POST", "ADMIN", "Hoàn tất theo dõi credit/replacement."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    createSupplierReturn(input: SupplierReturnCreateInput) {
        const supplier = supplierReturnOptions.suppliers.find((item) => item.id === input.supplierId);
        if (!supplier) return Promise.reject(new Error("Vui lòng chọn nhà cung cấp."));
        if (!input.receiptId || !input.supplierInvoiceCode) return Promise.reject(new Error("Bắt buộc liên kết receipt và invoice gốc."));
        if (input.lines.length === 0) return Promise.reject(new Error("Cần chọn ít nhất một batch trả NCC."));
        const lines: SupplierReturnLine[] = input.lines.map((lineInput, index) => {
            const batch = supplierReturnOptions.batches.find((item) => item.id === lineInput.batchId);
            if (!batch) throw new Error("Không tìm thấy batch gốc.");
            if (batch.supplierId !== input.supplierId) throw new Error("Không được chọn batch khác supplier.");
            if (batch.receiptId !== input.receiptId) throw new Error("Batch phải thuộc receipt gốc đã chọn.");
            const returnable = batch.currentAvailableQuantity + batch.currentQuarantineQuantity;
            if (lineInput.returnQuantity <= 0) throw new Error("Return quantity phải lớn hơn 0.");
            if (lineInput.returnQuantity > returnable) throw new Error(`${batch.batchCode}: không được trả quá tồn batch còn lại.`);
            if (!lineInput.reason) throw new Error("Reason bắt buộc.");
            if (lineInput.reason === "SUP-01" && lineInput.evidence.length === 0) throw new Error("Evidence bắt buộc với lỗi chất lượng.");
            return {
                batchCode: batch.batchCode,
                batchId: batch.id,
                currentAvailableQuantity: batch.currentAvailableQuantity,
                currentQuarantineQuantity: batch.currentQuarantineQuantity,
                estimatedValue: lineInput.returnQuantity * batch.unitCost,
                evidence: lineInput.evidence,
                id: `supplier-return-line-${Date.now()}-${index}`,
                preparedQuantity: 0,
                qcResult: batch.qcResult,
                reason: lineInput.reason,
                receivedQuantity: batch.receivedQuantity,
                returnQuantity: lineInput.returnQuantity,
                returnableQuantity: returnable,
                skuCode: batch.skuCode,
                skuId: batch.skuId,
                skuName: batch.skuName,
                unit: batch.unit,
                unitCost: batch.unitCost
            };
        });
        const receipt = supplierReturnOptions.batches.find((item) => item.receiptId === input.receiptId);
        const detail: SupplierReturnDetail = {
            auditEvents: [supplierReturnAudit(input.submit ? "SUBMIT" : "CREATE_DRAFT", "ADMIN", input.submit ? "Submit yêu cầu trả NCC." : "Lưu nháp trả NCC.")],
            code: `SRT-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${String(cache.length + 1).padStart(3, "0")}`,
            createdAt: new Date().toISOString(),
            createdBy: supplierReturnActor("ADMIN"),
            id: `supplier-return-${Date.now()}`,
            lines,
            note: input.note,
            pickupMethod: input.pickupMethod,
            reason: input.reason,
            receiptCode: receipt?.receiptCode ?? input.receiptId,
            receiptId: input.receiptId,
            resolutionRequested: input.resolutionRequested,
            status: input.submit ? "SUBMITTED" : "DRAFT",
            supplier,
            supplierInvoiceCode: input.supplierInvoiceCode,
            totalQuantity: 0,
            totalSku: 0,
            totalValue: 0
        };
        recalc(detail);
        cache.unshift(detail);
        return wait(copy(detail));
    },

    getSupplierReturn(id: string) {
        return wait(copy(findReturn(id)));
    },

    getSupplierReturnOptions() {
        return wait(supplierReturnOptions);
    },

    listSupplierReturns(query: SupplierReturnListQuery) {
        const filtered = cache.filter((detail) => {
            const matchesCode = !query.code || detail.code.toLowerCase().includes(query.code.toLowerCase());
            const matchesSupplier = query.supplierId === "ALL" || detail.supplier.id === query.supplierId;
            const matchesReceipt = !query.receiptCode || detail.receiptCode.toLowerCase().includes(query.receiptCode.toLowerCase());
            const matchesBatch = !query.batchCode || detail.lines.some((line) => line.batchCode.toLowerCase().includes(query.batchCode.toLowerCase()));
            const matchesStatus = query.status === "ALL" || detail.status === query.status;
            const matchesReason = query.reason === "ALL" || detail.reason === query.reason;
            return matchesCode && matchesSupplier && matchesReceipt && matchesBatch && matchesStatus && matchesReason && matchesDate(detail.createdAt, query.createdFrom, query.createdTo);
        });
        const start = query.page * query.size;
        return wait({
            content: filtered.slice(start, start + query.size).map(summary),
            page: query.page,
            size: query.size,
            summary: {
                creditPending: cache.filter((item) => item.status === "CREDIT_PENDING").length,
                preparing: cache.filter((item) => item.status === "PREPARING").length,
                replacementPending: cache.filter((item) => item.status === "REPLACEMENT_PENDING").length,
                shipped: cache.filter((item) => item.status === "SHIPPED").length,
                submitted: cache.filter((item) => item.status === "SUBMITTED").length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    prepareSupplierReturn(input: SupplierReturnPrepareInput) {
        const detail = findReturn(input.supplierReturnId);
        if (detail.status !== "APPROVED") return Promise.reject(new Error("Chỉ chuẩn bị sau khi đã duyệt."));
        input.lines.forEach((lineInput) => {
            const line = detail.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng prepare.");
            if (lineInput.preparedQuantity <= 0 || lineInput.preparedQuantity > line.returnQuantity) throw new Error("Actual prepared quantity không hợp lệ.");
            line.preparedQuantity = lineInput.preparedQuantity;
        });
        detail.packageCount = input.packageCount;
        detail.status = "PREPARING";
        detail.auditEvents = [supplierReturnAudit("APPROVE", "ADMIN", "Picking batch và chuyển sang RETURN_TO_SUPPLIER staging, batch không còn reserve được."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    rejectSupplierReturn(input: SupplierReturnRejectInput) {
        if (!input.reason.trim()) return Promise.reject(new Error("Reject bắt buộc nhập lý do."));
        const detail = findReturn(input.supplierReturnId);
        if (detail.status !== "SUBMITTED") return Promise.reject(new Error("Chỉ reject phiếu đang chờ duyệt."));
        detail.status = "REJECTED";
        detail.rejectedReason = input.reason;
        detail.auditEvents = [supplierReturnAudit("REJECT", "ADMIN", input.reason), ...detail.auditEvents];
        return wait(copy(detail));
    },

    shipSupplierReturn(input: SupplierReturnShipInput) {
        const detail = findReturn(input.supplierReturnId);
        if (detail.status !== "PREPARING") return Promise.reject(new Error("Chỉ ship sau khi chuẩn bị hàng."));
        const notPrepared = detail.lines.some((line) => line.preparedQuantity !== line.returnQuantity);
        if (notPrepared) return Promise.reject(new Error("Tất cả dòng phải chuẩn bị đủ trước khi ship."));
        detail.carrier = input.carrier;
        detail.trackingCode = input.trackingCode;
        detail.shippedAt = input.shippedAt;
        detail.supplierReceiver = input.supplierReceiver;
        detail.shippingAttachment = input.shippingAttachment;
        detail.stockDocumentId = `doc-supplier-return-${detail.id}`;
        detail.stockDocumentCode = `PXK-${detail.code}`;
        detail.status = detail.resolutionRequested === "REPLACEMENT" ? "REPLACEMENT_PENDING" : "CREDIT_PENDING";
        detail.auditEvents = [supplierReturnAudit("POST", "ADMIN", "Tạo PXK SUPPLIER_RETURN, tồn kho giảm và giao hàng cho NCC."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    submitSupplierReturn(input: SupplierReturnActionInput) {
        const detail = findReturn(input.supplierReturnId);
        if (detail.status !== "DRAFT") return Promise.reject(new Error("Chỉ submit phiếu nháp."));
        detail.status = "SUBMITTED";
        detail.auditEvents = [supplierReturnAudit("SUBMIT", "ADMIN", input.note ?? "Submit yêu cầu trả NCC."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    updateSupplierReturnSettlement(input: SupplierReturnSettlementInput) {
        const detail = findReturn(input.supplierReturnId);
        if (!["CREDIT_PENDING", "REPLACEMENT_PENDING", "SHIPPED"].includes(detail.status)) return Promise.reject(new Error("Chỉ cập nhật settlement sau khi ship."));
        detail.creditNoteCode = input.creditNoteCode;
        detail.creditAmount = input.creditAmount;
        detail.creditNoteDate = input.creditNoteDate;
        detail.replacementReceiptCode = input.replacementReceiptCode;
        detail.expectedReplacementDate = input.expectedReplacementDate;
        detail.settlementNote = input.settlementNote;
        detail.settlementAttachment = input.settlementAttachment;
        detail.auditEvents = [supplierReturnAudit("APPROVE", "ADMIN", "Cập nhật credit note/replacement settlement."), ...detail.auditEvents];
        return wait(copy(detail));
    }
};
