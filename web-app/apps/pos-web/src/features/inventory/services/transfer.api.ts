import {
    transferAudit,
    transferMockData,
    transferMockOptions
} from "../mocks/transfers.mock";
import type {
    TransferActionInput,
    TransferApi,
    TransferApproveInput,
    TransferCreateInput,
    TransferDetail,
    TransferDiscrepancyResolveInput,
    TransferLine,
    TransferListQuery,
    TransferPickingInput,
    TransferReceiveInput,
    TransferShipInput,
    TransferStatus
} from "../types/transfer.types";

const transferCache = [...transferMockData];

function wait<T>(value: T, ms = 350) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function copy(transfer: TransferDetail): TransferDetail {
    return {
        ...transfer,
        auditEvents: [...transfer.auditEvents],
        discrepancies: transfer.discrepancies.map((item) => ({ ...item })),
        lines: transfer.lines.map((line) => ({
            ...line,
            allocations: line.allocations.map((allocation) => ({ ...allocation }))
        })),
        shipment: transfer.shipment ? { ...transfer.shipment } : undefined
    };
}

function findTransfer(transferId: string) {
    const transfer = transferCache.find((item) => item.id === transferId);
    if (!transfer) throw new Error("Không tìm thấy yêu cầu chuyển kho.");
    return transfer;
}

function summaryOf(transfer: TransferDetail) {
    return {
        code: transfer.code,
        destinationLocation: transfer.destinationLocation,
        expectedReceiveAt: transfer.expectedReceiveAt,
        hasDiscrepancy: transfer.hasDiscrepancy,
        id: transfer.id,
        requestedAt: transfer.requestedAt,
        requestedBy: transfer.requestedBy,
        sourceLocation: transfer.sourceLocation,
        status: transfer.status,
        totalQuantity: transfer.totalQuantity,
        totalSku: transfer.totalSku,
        totalValue: transfer.totalValue
    };
}

function recalculate(transfer: TransferDetail) {
    transfer.totalSku = transfer.lines.length;
    transfer.totalQuantity = transfer.lines.reduce(
        (sum, line) => sum + (line.approvedQuantity || line.requestedQuantity),
        0
    );
    transfer.totalValue = transfer.lines.reduce((sum, line) => {
        const sku = transferMockOptions.skus.find((item) => item.id === line.skuId);
        return sum + (line.approvedQuantity || line.requestedQuantity) * (sku?.unitCost ?? 0);
    }, 0);
    transfer.hasDiscrepancy = transfer.discrepancies.some(
        (item) => item.resolutionStatus !== "RESOLVED"
    );
}

function tabMatches(query: TransferListQuery, status: TransferStatus, hasDiscrepancy: boolean) {
    if (query.tab === "ALL") return true;
    if (query.tab === "REQUESTED") return status === "REQUESTED";
    if (query.tab === "PREPARING") return ["APPROVED", "RESERVED", "PICKING"].includes(status);
    if (query.tab === "IN_TRANSIT") return status === "SHIPPED";
    if (query.tab === "AWAITING_RECEIVE") return status === "SHIPPED";
    if (query.tab === "DISCREPANCY") return hasDiscrepancy;
    return status === "RECEIVED";
}

function activeBatches(line: TransferLine) {
    const sku = transferMockOptions.skus.find((item) => item.id === line.skuId);
    return [...(sku?.batches ?? [])]
        .filter((batch) => !batch.blocked && !batch.expired && batch.availableQuantity > 0)
        .sort((a, b) => new Date(a.expiryDate).getTime() - new Date(b.expiryDate).getTime());
}

function assignFefo(line: TransferLine) {
    let remaining = line.approvedQuantity;
    const allocations = [];
    for (const batch of activeBatches(line)) {
        if (remaining <= 0) break;
        const reservedQuantity = Math.min(remaining, batch.availableQuantity);
        allocations.push({
            batchCode: batch.batchCode,
            batchId: batch.id,
            expiryDate: batch.expiryDate,
            id: `alloc-${line.id}-${batch.id}`,
            picked: false,
            pickedQuantity: 0,
            reservedQuantity
        });
        remaining -= reservedQuantity;
    }
    if (remaining > 0) {
        throw new Error(`${line.skuCode} không đủ tồn khả dụng để reserve.`);
    }
    line.allocations = allocations;
}

function discrepancyId(lineId: string, type: string) {
    return `disc-${lineId}-${type}-${Date.now()}`;
}

export const transferApi: TransferApi = {
    approveTransfer(input: TransferApproveInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "REQUESTED") {
            return Promise.reject(new Error("Chỉ duyệt yêu cầu đang chờ duyệt."));
        }
        let reduced = false;
        input.lines.forEach((lineInput) => {
            const line = transfer.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng chuyển kho.");
            if (lineInput.approvedQuantity <= 0) throw new Error("Số lượng duyệt phải lớn hơn 0.");
            if (lineInput.approvedQuantity > line.requestedQuantity) {
                throw new Error("Không được duyệt vượt số lượng yêu cầu.");
            }
            reduced ||= lineInput.approvedQuantity < line.requestedQuantity;
            line.approvedQuantity = lineInput.approvedQuantity;
        });
        if (reduced && !input.note?.trim()) {
            return Promise.reject(new Error("Giảm số lượng duyệt phải nhập ghi chú."));
        }
        transfer.status = "APPROVED";
        recalculate(transfer);
        transfer.auditEvents = [
            transferAudit("APPROVE", "ADMIN", input.note ?? "Duyệt yêu cầu chuyển kho."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    cancelTransfer(input: TransferActionInput) {
        const transfer = findTransfer(input.transferId);
        if (["SHIPPED", "RECEIVED", "PARTIALLY_RECEIVED"].includes(transfer.status)) {
            return Promise.reject(new Error("Không thể hủy yêu cầu đã xuất/nhận hàng."));
        }
        transfer.status = "CANCELLED";
        transfer.auditEvents = [
            transferAudit("CANCEL", "ADMIN", input.note ?? "Hủy yêu cầu chuyển kho."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    confirmTransferPicking(input: TransferPickingInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "PICKING") {
            return Promise.reject(new Error("Chỉ confirm picking khi đang PICKING."));
        }
        input.lines.forEach((lineInput) => {
            const line = transfer.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng picking.");
            lineInput.allocations.forEach((allocationInput) => {
                const allocation = line.allocations.find(
                    (item) => item.id === allocationInput.allocationId
                );
                if (!allocation) throw new Error("Không tìm thấy batch allocation.");
                if (allocationInput.pickedQuantity > allocation.reservedQuantity) {
                    throw new Error("Không được pick vượt số đã reserve.");
                }
                const batch = transferMockOptions.skus
                    .find((item) => item.id === line.skuId)
                    ?.batches.find((item) => item.id === allocation.batchId);
                if (!batch || batch.blocked || batch.expired) {
                    throw new Error("Không được pick batch hết hạn hoặc blocked.");
                }
                allocation.picked = allocationInput.picked;
                allocation.pickedQuantity = allocationInput.pickedQuantity;
            });
        });
        const allPicked = transfer.lines.every((line) =>
            line.allocations.reduce((sum, item) => sum + item.pickedQuantity, 0) ===
            line.approvedQuantity
        );
        if (!allPicked) {
            return Promise.reject(new Error("Tổng actual picked phải bằng số lượng đã duyệt."));
        }
        transfer.auditEvents = [
            transferAudit("APPROVE", "ADMIN", "Confirm picking theo FEFO."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    createTransfer(input: TransferCreateInput) {
        if (input.sourceLocationId === input.destinationLocationId) {
            return Promise.reject(new Error("Kho nguồn và kho đích không được trùng nhau."));
        }
        if (input.lines.length === 0) {
            return Promise.reject(new Error("Yêu cầu chuyển kho phải có ít nhất một SKU."));
        }
        const source = transferMockOptions.locations.find((item) => item.id === input.sourceLocationId);
        const destination = transferMockOptions.locations.find(
            (item) => item.id === input.destinationLocationId
        );
        if (!source || !destination) {
            return Promise.reject(new Error("Vui lòng chọn kho nguồn và kho đích."));
        }
        const lines = input.lines.map((lineInput, index) => {
            const sku = transferMockOptions.skus.find((item) => item.id === lineInput.skuId);
            if (!sku) throw new Error("Không tìm thấy SKU.");
            if (lineInput.requestedQuantity <= 0) {
                throw new Error("Số lượng yêu cầu phải lớn hơn 0.");
            }
            return {
                allocations: [],
                approvedQuantity: 0,
                damagedQuantity: 0,
                destinationAvailableQuantity: sku.destinationAvailableQuantity,
                destinationMinimumStock: sku.destinationMinimumStock,
                extraQuantity: 0,
                id: `transfer-line-${Date.now()}-${index}`,
                missingQuantity: 0,
                note: lineInput.note,
                receivedGoodQuantity: 0,
                requestedQuantity: lineInput.requestedQuantity,
                shippedQuantity: 0,
                skuCode: sku.skuCode,
                skuId: sku.id,
                skuName: sku.name,
                sourceAvailableQuantity: sku.sourceAvailableQuantity,
                unit: sku.unit
            };
        });
        const transfer: TransferDetail = {
            auditEvents: [
                transferAudit(
                    input.submit ? "SUBMIT" : "CREATE_DRAFT",
                    "ADMIN",
                    input.submit ? "Tạo và submit yêu cầu chuyển kho." : "Lưu nháp yêu cầu chuyển kho."
                )
            ],
            code: `TRF-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${String(transferCache.length + 1).padStart(3, "0")}`,
            destinationLocation: destination,
            discrepancies: [],
            expectedReceiveAt: input.expectedReceiveAt,
            hasDiscrepancy: false,
            id: `transfer-${Date.now()}`,
            lines,
            priority: input.priority,
            reason: input.reason,
            requestedAt: new Date().toISOString(),
            requestedBy: transferMockOptions.users[0],
            sourceLocation: source,
            status: input.submit ? "REQUESTED" : "DRAFT",
            totalQuantity: 0,
            totalSku: 0,
            totalValue: 0
        };
        recalculate(transfer);
        transferCache.unshift(transfer);
        return wait(copy(transfer));
    },

    getTransfer(transferId: string) {
        return wait(copy(findTransfer(transferId)));
    },

    getTransferOptions() {
        return wait(transferMockOptions);
    },

    listTransfers(query: TransferListQuery) {
        const filtered = transferCache.filter((transfer) => {
            const matchesText =
                !query.code ||
                transfer.code.toLowerCase().includes(query.code.toLowerCase()) ||
                transfer.lines.some((line) =>
                    line.skuCode.toLowerCase().includes(query.code.toLowerCase())
                );
            const matchesSource =
                query.sourceLocationId === "ALL" ||
                transfer.sourceLocation.id === query.sourceLocationId;
            const matchesDestination =
                query.destinationLocationId === "ALL" ||
                transfer.destinationLocation.id === query.destinationLocationId;
            const matchesStatus =
                query.status === "ALL" || transfer.status === query.status;
            const matchesPriority =
                query.priority === "ALL" || transfer.priority === query.priority;
            const matchesTab = matchesTransferTab(query.tab, transfer);
            return (
                matchesText &&
                matchesSource &&
                matchesDestination &&
                matchesStatus &&
                matchesPriority &&
                matchesTab
            );
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

    receiveTransfer(input: TransferReceiveInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "SHIPPED") {
            return Promise.reject(new Error("Chỉ nhận hàng khi trạng thái là SHIPPED."));
        }
        input.lines.forEach((lineInput) => {
            const line = transfer.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng nhận hàng.");
            const processed = lineInput.receivedGoodQuantity + lineInput.damagedQuantity + lineInput.missingQuantity;
            if (processed !== line.shippedQuantity) {
                throw new Error("Good + damaged + missing phải bằng số lượng đã ship.");
            }
            if (lineInput.extraQuantity > 0 && !lineInput.evidence?.trim()) {
                throw new Error("Hàng thừa phải có evidence để xác minh.");
            }
            line.receivedGoodQuantity = lineInput.receivedGoodQuantity;
            line.damagedQuantity = lineInput.damagedQuantity;
            line.missingQuantity = lineInput.missingQuantity;
            line.extraQuantity = lineInput.extraQuantity;
            if (lineInput.missingQuantity > 0) {
                transfer.discrepancies.push({
                    id: discrepancyId(line.id, "SHORTAGE"),
                    lineId: line.id,
                    quantity: lineInput.missingQuantity,
                    responsibleParty: "Đang xác minh",
                    resolutionStatus: "OPEN",
                    type: "SHORTAGE"
                });
            }
            if (lineInput.damagedQuantity > 0) {
                transfer.discrepancies.push({
                    evidence: lineInput.evidence,
                    id: discrepancyId(line.id, "DAMAGE"),
                    lineId: line.id,
                    quantity: lineInput.damagedQuantity,
                    responsibleParty: "Đang xác minh",
                    resolutionStatus: "OPEN",
                    type: "DAMAGE"
                });
            }
            if (lineInput.extraQuantity > 0) {
                transfer.discrepancies.push({
                    evidence: lineInput.evidence,
                    id: discrepancyId(line.id, "EXTRA"),
                    lineId: line.id,
                    quantity: lineInput.extraQuantity,
                    responsibleParty: "Đang xác minh",
                    resolutionStatus: "INVESTIGATING",
                    type: "EXTRA"
                });
            }
        });
        transfer.receiptDocumentId = `doc-transfer-receipt-${transfer.id}`;
        transfer.receiptDocumentCode = `PNK-${transfer.code}`;
        recalculate(transfer);
        transfer.status = transfer.hasDiscrepancy ? "PARTIALLY_RECEIVED" : "RECEIVED";
        transfer.auditEvents = [
            transferAudit("POST", "ADMIN", "Nhận hàng, giảm IN_TRANSIT và tạo PNK TRANSFER_RECEIPT."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    rejectTransfer(input: TransferActionInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "REQUESTED") {
            return Promise.reject(new Error("Chỉ từ chối yêu cầu đang chờ duyệt."));
        }
        transfer.status = "REJECTED";
        transfer.auditEvents = [
            transferAudit("REJECT", "ADMIN", input.note ?? "Từ chối yêu cầu chuyển kho."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    reserveTransfer(input: TransferActionInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "APPROVED") {
            return Promise.reject(new Error("Chỉ reserve sau khi đã duyệt."));
        }
        transfer.lines.forEach(assignFefo);
        transfer.status = "RESERVED";
        transfer.auditEvents = [
            transferAudit("APPROVE", "ADMIN", "Giữ tồn kho nguồn theo FEFO."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    resolveTransferDiscrepancy(input: TransferDiscrepancyResolveInput) {
        const transfer = findTransfer(input.transferId);
        const discrepancy = transfer.discrepancies.find((item) => item.id === input.discrepancyId);
        if (!discrepancy) return Promise.reject(new Error("Không tìm thấy discrepancy."));
        discrepancy.resolutionStatus = input.resolutionStatus;
        discrepancy.adminNote = input.adminNote;
        recalculate(transfer);
        transfer.auditEvents = [
            transferAudit("APPROVE", "ADMIN", "Cập nhật xử lý chênh lệch chuyển kho."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    shipTransfer(input: TransferShipInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "PICKING") {
            return Promise.reject(new Error("Chỉ ship khi đang PICKING và đã confirm picking."));
        }
        transfer.lines.forEach((line) => {
            const pickedQuantity = line.allocations.reduce((sum, item) => sum + item.pickedQuantity, 0);
            if (pickedQuantity !== line.approvedQuantity) {
                throw new Error("Tất cả SKU phải được pick đủ trước khi ship.");
            }
            line.shippedQuantity = pickedQuantity;
        });
        transfer.issueDocumentId = `doc-transfer-issue-${transfer.id}`;
        transfer.issueDocumentCode = `PXK-${transfer.code}`;
        transfer.shipment = {
            carrierName: input.carrierName,
            expectedArrivalAt: input.expectedArrivalAt,
            internalTrackingCode: input.internalTrackingCode,
            packageCount: input.packageCount,
            shippedAt: new Date().toISOString(),
            vehiclePlate: input.vehiclePlate
        };
        transfer.status = "SHIPPED";
        transfer.auditEvents = [
            transferAudit("POST", "ADMIN", "Xuất kho nguồn, On Hand giảm và IN_TRANSIT tăng."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    startPicking(input: TransferActionInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "RESERVED") {
            return Promise.reject(new Error("Chỉ bắt đầu picking khi đã reserve."));
        }
        transfer.status = "PICKING";
        transfer.auditEvents = [
            transferAudit("APPROVE", "ADMIN", "Bắt đầu picking theo batch allocation."),
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    },

    submitTransfer(input: TransferActionInput) {
        const transfer = findTransfer(input.transferId);
        if (transfer.status !== "DRAFT") {
            return Promise.reject(new Error("Chỉ submit yêu cầu nháp."));
        }
        transfer.status = "REQUESTED";
        transfer.auditEvents = [
            ...transfer.auditEvents
        ];
        return wait(copy(transfer));
    }
};
