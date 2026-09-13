import {
    customerReturnActor,
    customerReturnAudit,
    customerReturnMockData,
    customerReturnOptions,
    returnableOrders
} from "../mocks/customer-returns.mock";
import type {
    CreateCustomerReturnInput,
    CustomerReturnActionInput,
    CustomerReturnApi,
    CustomerReturnDetail,
    CustomerReturnLine,
    CustomerReturnListQuery,
    InspectCustomerReturnInput,
    ReceiveCustomerReturnInput,
    ReviewCustomerReturnInput
} from "../types/customer-return.types";

const returnCache = [...customerReturnMockData];

function wait<T>(value: T, ms = 340) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function copy(detail: CustomerReturnDetail): CustomerReturnDetail {
    return {
        ...detail,
        auditEvents: [...detail.auditEvents],
        evidence: [...detail.evidence],
        lines: detail.lines.map((line) => ({ ...line })),
        managerReview: detail.managerReview ? { ...detail.managerReview } : undefined,
        qc: detail.qc ? { ...detail.qc } : undefined,
        receiving: detail.receiving ? { ...detail.receiving } : undefined
    };
}

function findReturn(returnId: string) {
    const item = returnCache.find((detail) => detail.id === returnId);
    if (!item) throw new Error("Không tìm thấy yêu cầu trả hàng.");
    return item;
}

function summaryOf(detail: CustomerReturnDetail) {
    return {
        branch: detail.branch,
        code: detail.code,
        customerName: detail.customerName,
        customerPhone: detail.customerPhone,
        id: detail.id,
        invoiceCode: detail.invoiceCode,
        orderCode: detail.orderCode,
        reason: detail.reason,
        refundStatus: detail.refundStatus,
        requestedAt: detail.requestedAt,
        requestedBy: detail.requestedBy,
        status: detail.status,
        totalQuantity: detail.totalQuantity,
        totalValue: detail.totalValue
    };
}

function recalculate(detail: CustomerReturnDetail) {
    detail.totalQuantity = detail.lines.reduce((sum, line) => sum + line.requestedQuantity, 0);
    detail.totalValue = detail.lines.reduce((sum, line) => sum + line.requestedQuantity * line.unitPrice, 0);
    detail.reason = detail.lines[0]?.reason ?? "OTHER";
}

function tabMatches(query: CustomerReturnListQuery, detail: CustomerReturnDetail) {
    if (query.tab === "MANAGER_REVIEW") return detail.status === "PENDING_MANAGER_REVIEW";
    if (query.tab === "AWAITING_RECEIVE") return detail.status === "ACCEPTED";
    if (query.tab === "PENDING_QC") return detail.status === "RECEIVED_IN_QUARANTINE" || detail.status === "PENDING_QC";
    if (query.tab === "RESTOCK") return detail.status === "RESTOCK_APPROVED";
    if (query.tab === "DISPOSAL") return detail.status === "DISPOSAL_REQUIRED";
    return detail.status === "COMPLETED";
}

export const customerReturnApi: CustomerReturnApi = {
    approveCustomerReturnRestock(input: CustomerReturnActionInput) {
        const detail = findReturn(input.returnId);
        const passed = detail.lines.reduce((sum, line) => sum + line.passedQuantity, 0);
        if (passed <= 0) return Promise.reject(new Error("Không có quantity đạt QC để nhập lại kho."));
        const unknownBatch = detail.lines.some((line) => line.passedQuantity > 0 && line.unknownBatch);
        if (unknownBatch) return Promise.reject(new Error("Không xác định lô: không tự động đưa về AVAILABLE, cần Manager/QC quyết định."));
        detail.stockDocumentId = `doc-customer-return-${detail.id}`;
        detail.stockDocumentCode = `PNK-${detail.code}`;
        detail.status = "COMPLETED";
        detail.auditEvents = [customerReturnAudit("POST", "ADMIN", "Tạo PNK CUSTOMER_RETURN_RECEIPT và đưa hàng đạt về AVAILABLE."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    cancelCustomerReturn(input: CustomerReturnActionInput) {
        const detail = findReturn(input.returnId);
        if (["COMPLETED", "RESTOCK_APPROVED", "DISPOSAL_REQUIRED"].includes(detail.status)) {
            return Promise.reject(new Error("Không thể hủy yêu cầu đã xử lý inventory."));
        }
        detail.status = "CANCELLED";
        detail.auditEvents = [customerReturnAudit("CANCEL", "ADMIN", input.note ?? "Hủy yêu cầu trả hàng."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    createCustomerReturn(input: CreateCustomerReturnInput) {
        const order = returnableOrders.find((item) => item.id === input.orderId);
        if (!order) return Promise.reject(new Error("Không tìm thấy order gốc."));
        if (input.lines.length === 0) return Promise.reject(new Error("Cần chọn ít nhất một dòng trả."));
        const lines: CustomerReturnLine[] = input.lines.map((lineInput, index) => {
            const orderLine = order.lines.find((line) => line.id === lineInput.orderLineId);
            if (!orderLine) throw new Error("Không tìm thấy dòng order.");
            const remaining = orderLine.purchasedQuantity - orderLine.previouslyReturnedQuantity;
            if (lineInput.quantity <= 0) throw new Error("Return quantity phải lớn hơn 0.");
            if (lineInput.quantity > remaining) throw new Error(`${orderLine.skuCode}: số lượng trả vượt số lượng còn được trả.`);
            return {
                acceptedQuantity: 0,
                batchCode: orderLine.batchCode,
                condition: lineInput.condition,
                customerNote: lineInput.customerNote,
                expiryDate: orderLine.expiryDate,
                failedQuantity: 0,
                id: `cr-line-${Date.now()}-${index}`,
                orderLineId: orderLine.id,
                passedQuantity: 0,
                previouslyReturnedQuantity: orderLine.previouslyReturnedQuantity,
                productId: orderLine.productId,
                productName: orderLine.productName,
                purchasedQuantity: orderLine.purchasedQuantity,
                reason: lineInput.reason,
                receivedQuantity: 0,
                requestedQuantity: lineInput.quantity,
                skuCode: orderLine.skuCode,
                unit: orderLine.unit,
                unitPrice: orderLine.unitPrice,
                unknownBatch: !orderLine.batchCode
            };
        });
        const detail: CustomerReturnDetail = {
            auditEvents: [customerReturnAudit("SUBMIT", "ADMIN", "Cashier tạo yêu cầu trả hàng, chờ Manager review.")],
            branch: order.branch,
            code: `CRT-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${String(returnCache.length + 1).padStart(3, "0")}`,
            customerName: order.customerName,
            customerNote: input.customerNote,
            customerPhone: order.customerPhone,
            desiredResolution: input.desiredResolution,
            evidence: input.evidence,
            id: `customer-return-${Date.now()}`,
            invoiceCode: order.invoiceCode,
            lines,
            order,
            orderCode: order.code,
            paymentReference: `PAY-${order.code}`,
            reason: lines[0]?.reason ?? "OTHER",
            refundStatus: input.desiredResolution === "REFUND" ? "REQUESTED" : "NOT_REQUESTED",
            requestedAt: new Date().toISOString(),
            requestedBy: customerReturnActor("ADMIN"),
            status: "PENDING_MANAGER_REVIEW",
            totalQuantity: 0,
            totalValue: 0
        };
        recalculate(detail);
        returnCache.unshift(detail);
        return wait(copy(detail));
    },

    getCustomerReturn(returnId: string) {
        return wait(copy(findReturn(returnId)));
    },

    getCustomerReturnOptions() {
        return wait(customerReturnOptions);
    },

    inspectCustomerReturn(input: InspectCustomerReturnInput) {
        const detail = findReturn(input.returnId);
        if (!["RECEIVED_IN_QUARANTINE", "PENDING_QC"].includes(detail.status)) {
            return Promise.reject(new Error("Chỉ QC sau khi hàng vào CUSTOMER_RETURN_QUARANTINE."));
        }
        input.lines.forEach((lineInput) => {
            const line = detail.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng QC.");
            if (lineInput.passedQuantity + lineInput.failedQuantity !== line.receivedQuantity) {
                throw new Error("Passed + failed phải bằng số lượng đã nhận.");
            }
            line.passedQuantity = lineInput.passedQuantity;
            line.failedQuantity = lineInput.failedQuantity;
            line.qcCondition = lineInput.qcCondition;
        });
        detail.qc = {
            evidence: input.evidence,
            inspectedAt: new Date().toISOString(),
            inspectedBy: customerReturnActor("ADMIN"),
            note: input.note,
            remainingShelfLifeDays: input.remainingShelfLifeDays,
            sealStatus: input.sealStatus
        };
        const failed = detail.lines.reduce((sum, line) => sum + line.failedQuantity, 0);
        const passed = detail.lines.reduce((sum, line) => sum + line.passedQuantity, 0);
        detail.status = failed > 0 && passed === 0 ? "DISPOSAL_REQUIRED" : "RESTOCK_APPROVED";
        detail.auditEvents = [customerReturnAudit("APPROVE", "ADMIN", failed > 0 ? "QC không đạt, chuyển DAMAGED/disposal." : "QC đạt, chờ nhập lại kho."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    listCustomerReturns(query: CustomerReturnListQuery) {
        const filtered = returnCache.filter((detail) => {
            const text = `${detail.code} ${detail.orderCode} ${detail.customerName} ${detail.customerPhone}`.toLowerCase();
            const matchesText = (!query.code || detail.code.toLowerCase().includes(query.code.toLowerCase())) &&
                (!query.orderCode || detail.orderCode.toLowerCase().includes(query.orderCode.toLowerCase())) &&
                (!query.customer || text.includes(query.customer.toLowerCase()));
            const matchesBranch = query.branchId === "ALL" || detail.branch.id === query.branchId;
            const matchesStatus = query.status === "ALL" || detail.status === query.status;
            const matchesReason = query.reason === "ALL" || detail.reason === query.reason;
            return matchesText && matchesBranch && matchesStatus && matchesReason && tabMatches(query, detail);
        });
        const start = query.page * query.size;
        return wait({
            content: filtered.slice(start, start + query.size).map(summaryOf),
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    receiveCustomerReturn(input: ReceiveCustomerReturnInput) {
        const detail = findReturn(input.returnId);
        if (detail.status !== "ACCEPTED") return Promise.reject(new Error("Chỉ nhận hàng sau khi Manager accept."));
        input.lines.forEach((lineInput) => {
            const line = detail.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng nhận hàng.");
            if (lineInput.receivedQuantity > line.acceptedQuantity) throw new Error("Không được nhận vượt số lượng đã accept.");
            line.receivedQuantity = lineInput.receivedQuantity;
            line.receivedCondition = lineInput.receivedCondition;
        });
        detail.status = "RECEIVED_IN_QUARANTINE";
        detail.receiving = { receivedAt: new Date().toISOString(), receivedBy: customerReturnActor("ADMIN"), note: input.note ?? "Hàng vào CUSTOMER_RETURN_QUARANTINE, chưa cộng available." };
        detail.auditEvents = [customerReturnAudit("APPROVE", "ADMIN", "Nhận hàng thực tế vào CUSTOMER_RETURN_QUARANTINE."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    requestCustomerReturnDisposal(input: CustomerReturnActionInput) {
        const detail = findReturn(input.returnId);
        const failed = detail.lines.reduce((sum, line) => sum + line.failedQuantity, 0);
        if (failed <= 0) return Promise.reject(new Error("Không có hàng fail QC để tạo Disposal Request."));
        detail.disposalRequestId = `disposal-${detail.id}`;
        detail.disposalRequestCode = `DSP-${detail.code}`;
        detail.status = "COMPLETED";
        detail.auditEvents = [customerReturnAudit("POST", "ADMIN", "Tạo Disposal Request cho hàng trả không đạt QC."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    reviewCustomerReturn(input: ReviewCustomerReturnInput) {
        const detail = findReturn(input.returnId);
        if (detail.status !== "PENDING_MANAGER_REVIEW") return Promise.reject(new Error("Chỉ review yêu cầu đang chờ Manager."));
        if (!input.accepted && !input.rejectReason?.trim()) return Promise.reject(new Error("Reject bắt buộc nhập lý do."));
        if (!input.accepted) {
            detail.status = "REJECTED";
            detail.managerReview = { accepted: false, rejectReason: input.rejectReason, reviewedAt: new Date().toISOString(), reviewedBy: customerReturnActor("ADMIN") };
            detail.auditEvents = [customerReturnAudit("REJECT", "ADMIN", input.rejectReason ?? "Từ chối trả hàng."), ...detail.auditEvents];
            return wait(copy(detail));
        }
        let reduced = false;
        input.lines.forEach((lineInput) => {
            const line = detail.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng review.");
            if (lineInput.acceptedQuantity <= 0 || lineInput.acceptedQuantity > line.requestedQuantity) throw new Error("Số lượng accept không hợp lệ.");
            reduced ||= lineInput.acceptedQuantity < line.requestedQuantity;
            line.acceptedQuantity = lineInput.acceptedQuantity;
        });
        if (reduced && !input.quantityChangeReason?.trim()) {
            return Promise.reject(new Error("Chỉnh quantity accepted phải ghi lý do."));
        }
        detail.status = "ACCEPTED";
        detail.managerReview = { accepted: true, quantityChangeReason: input.quantityChangeReason, reviewedAt: new Date().toISOString(), reviewedBy: customerReturnActor("ADMIN"), note: input.note };
        detail.auditEvents = [customerReturnAudit("APPROVE", "ADMIN", "Manager accept điều kiện trả hàng."), ...detail.auditEvents];
        return wait(copy(detail));
    },

    searchReturnableOrders(keyword: string) {
        const normalized = keyword.trim().toLowerCase();
        if (!normalized) return wait(returnableOrders);
        return wait(returnableOrders.filter((order) => `${order.code} ${order.invoiceCode} ${order.customerPhone} ${order.customerName}`.toLowerCase().includes(normalized)));
    }
};
