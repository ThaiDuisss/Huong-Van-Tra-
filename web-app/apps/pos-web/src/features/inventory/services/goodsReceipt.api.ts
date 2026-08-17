import {
    calculateFinalUnitCost,
    calculateLineNetAmount
} from "../constants/goods-receipt.constants";
import {
    goodsReceiptActorForRole,
    goodsReceiptMockData,
    goodsReceiptMockOptions
} from "../mocks/goods-receipts.mock";
import type {
    GoodsReceiptActionInput,
    GoodsReceiptApi,
    GoodsReceiptCostInput,
    GoodsReceiptCreateInput,
    GoodsReceiptDetail,
    GoodsReceiptLine,
    GoodsReceiptListQuery,
    GoodsReceiptListResult,
    GoodsReceiptQcInput,
    GoodsReceiptStatus,
    QcResult
} from "../types/goods-receipt.types";
import type { InventoryAuditEvent } from "../types/stock-document.types";

const goodsReceiptCache = [...goodsReceiptMockData];

function wait<T>(value: T, ms = 430) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function copy(detail: GoodsReceiptDetail): GoodsReceiptDetail {
    return {
        ...detail,
        auditEvents: [...detail.auditEvents],
        evidence: [...detail.evidence],
        lines: detail.lines.map((line) => ({
            ...line,
            cost: { ...line.cost },
            qc: { ...line.qc }
        }))
    };
}

function audit(
    input: GoodsReceiptActionInput | GoodsReceiptQcInput | GoodsReceiptCostInput,
    type: InventoryAuditEvent["type"],
    note?: string,
    reason?: string
): InventoryAuditEvent {
    const actor = goodsReceiptActorForRole(input.actorRole);

    return {
        actor,
        happenedAt: new Date().toISOString(),
        id: `${type}-${Date.now()}`,
        note,
        reason,
        roleSnapshot: actor.role,
        type
    };
}

function findReceipt(receiptId: string) {
    const receipt = goodsReceiptCache.find((item) => item.id === receiptId);

    if (!receipt) {
        throw new Error("Không tìm thấy phiếu nhập nguyên liệu.");
    }

    return receipt;
}

function summarizeQc(lines: GoodsReceiptLine[]): QcResult {
    if (lines.every((line) => line.qc.result === "PENDING")) {
        return "PENDING";
    }

    if (lines.every((line) => line.qc.failedQuantity === line.receivedQuantity)) {
        return "FAILED";
    }

    if (
        lines.some(
            (line) =>
                line.qc.quarantinedQuantity > 0 || line.qc.failedQuantity > 0
        )
    ) {
        return "PARTIALLY_PASSED";
    }

    return "PASSED";
}

function statusAfterQc(result: QcResult): GoodsReceiptStatus {
    if (result === "FAILED") {
        return "REJECTED";
    }

    if (result === "PARTIALLY_PASSED") {
        return "PARTIALLY_PASSED";
    }

    if (result === "PASSED") {
        return "AWAITING_COST";
    }

    return "AWAITING_QC";
}

function summaryOf(detail: GoodsReceiptDetail) {
    return {
        code: detail.code,
        costStatus: detail.costStatus,
        createdBy: detail.createdBy,
        hasVariance: detail.hasVariance,
        id: detail.id,
        purchaseOrderCode: detail.purchaseOrderCode,
        qcResult: detail.qcResult,
        receivedAt: detail.receivedAt,
        receivingLocation: detail.receivingLocation,
        status: detail.status,
        stockDocumentCode: detail.stockDocumentCode,
        stockDocumentId: detail.stockDocumentId,
        supplier: detail.supplier,
        totalLines: detail.totalLines,
        totalReceivedQuantity: detail.totalReceivedQuantity
    };
}

function validateQc(input: GoodsReceiptQcInput, receipt: GoodsReceiptDetail) {
    input.lines.forEach((lineInput) => {
        const line = receipt.lines.find((item) => item.id === lineInput.lineId);

        if (!line) {
            throw new Error("Không tìm thấy dòng QC.");
        }

        const total =
            lineInput.passedQuantity +
            lineInput.quarantinedQuantity +
            lineInput.failedQuantity;

        if (total !== lineInput.receivedQuantity) {
            throw new Error(
                "Tổng số đạt, cách ly và không đạt phải bằng số lượng nhận."
            );
        }

        if (lineInput.receivedQuantity > line.receivedQuantity) {
            throw new Error("Không được QC số lượng vượt số nhận.");
        }

        if (lineInput.failedQuantity > 0 && !lineInput.reasonCode) {
            throw new Error("Dòng có hàng không đạt phải chọn lý do.");
        }

        if (
            lineInput.quarantinedQuantity > 0 &&
            !lineInput.inspectionNote?.trim()
        ) {
            throw new Error("Dòng có hàng cách ly phải nhập ghi chú xử lý tiếp.");
        }
    });
}

function updateReceiptStatus(
    input: GoodsReceiptActionInput,
    status: GoodsReceiptStatus,
    note?: string
) {
    const receipt = findReceipt(input.receiptId);
    receipt.status = status;
    receipt.auditEvents = [audit(input, "APPROVE", note), ...receipt.auditEvents];
    return wait(copy(receipt));
}

export const goodsReceiptApi: GoodsReceiptApi = {
    cancelGoodsReceipt(input) {
        return updateReceiptStatus(input, "CANCELLED", input.note ?? "Hủy phiếu nhập.");
    },

    createGoodsReceipt(input: GoodsReceiptCreateInput) {
        const supplier = goodsReceiptMockOptions.suppliers.find(
            (item) => item.id === input.supplierId
        );
        const location = goodsReceiptMockOptions.locations.find(
            (item) => item.id === input.receivingLocationId
        );

        if (!supplier || !location) {
            return Promise.reject(new Error("Vui lòng chọn nhà cung cấp và kho nhận."));
        }

        if (input.outsidePo && !input.outsidePoReason?.trim()) {
            return Promise.reject(new Error("Nhập ngoài PO phải có lý do."));
        }

        const duplicateKey = new Set<string>();
        for (const line of input.lines) {
            const key = `${line.materialId}-${line.supplierLot}`;
            if (duplicateKey.has(key) && !line.duplicateReason?.trim()) {
                return Promise.reject(
                    new Error("Không được trùng SKU + supplier lot nếu chưa nhập lý do.")
                );
            }
            duplicateKey.add(key);

            if (new Date(line.expiryDate) <= new Date(line.manufacturedAt)) {
                return Promise.reject(
                    new Error("Hạn sử dụng phải sau ngày sản xuất.")
                );
            }
        }

        const lines = input.lines.map((lineInput, index) => {
            const material = goodsReceiptMockOptions.materials.find(
                (item) => item.id === lineInput.materialId
            );

            if (!material) {
                throw new Error("Không tìm thấy nguyên liệu.");
            }

            const shortageQuantity = lineInput.orderedQuantity
                ? Math.max(0, lineInput.orderedQuantity - lineInput.receivedQuantity)
                : 0;
            const surplusQuantity = lineInput.orderedQuantity
                ? Math.max(0, lineInput.receivedQuantity - lineInput.orderedQuantity)
                : 0;

            return {
                batchStatus: "AWAITING_QC" as const,
                conversionPreview: `1 ${material.unit} = 1 ${material.unit} nhập kho`,
                cost: {
                    acceptedQuantity: 0,
                    allocatedShippingCost: 0,
                    currency: "VND" as const,
                    discount: 0,
                    finalUnitCost: 0,
                    lineNetAmount: 0,
                    otherCost: 0,
                    purchaseUnitPrice: 0,
                    status: "PENDING" as const,
                    tax: 0
                },
                countryOfOrigin: lineInput.countryOfOrigin,
                expiryDate: lineInput.expiryDate,
                id: `gr-new-line-${Date.now()}-${index}`,
                initialDamagedQuantity: lineInput.initialDamagedQuantity,
                internalBatchCode: `HVT-${material.skuCode}-${Date.now()}-${index}`,
                manufacturedAt: lineInput.manufacturedAt,
                materialId: material.id,
                materialName: material.name,
                orderedQuantity: lineInput.orderedQuantity,
                qc: {
                    failedQuantity: 0,
                    passedQuantity: 0,
                    quarantinedQuantity: 0,
                    receivedQuantity: lineInput.receivedQuantity,
                    result: "PENDING" as const
                },
                qualityCertificate: lineInput.qualityCertificate,
                receivedQuantity: lineInput.receivedQuantity,
                shortageQuantity,
                skuCode: material.skuCode,
                storageCondition: lineInput.storageCondition,
                supplierLot: lineInput.supplierLot,
                surplusQuantity,
                unit: material.unit
            };
        });
        const receipt: GoodsReceiptDetail = {
            auditEvents: [
                audit(
                    { actorRole: "WAREHOUSE", receiptId: "new" },
                    "CREATE_DRAFT",
                    input.submit ? "Tạo và submit phiếu nhập." : "Lưu draft phiếu nhập."
                )
            ],
            code: `GR-2026-${String(goodsReceiptCache.length + 1).padStart(5, "0")}`,
            costStatus: "PENDING",
            createdBy: goodsReceiptActorForRole("WAREHOUSE"),
            deliveryPerson: input.deliveryPerson,
            evidence: input.evidence,
            hasVariance: lines.some(
                (line) =>
                    line.shortageQuantity > 0 ||
                    line.surplusQuantity > 0 ||
                    line.initialDamagedQuantity > 0
            ),
            id: `goods-receipt-${Date.now()}`,
            invoiceNumber: input.invoiceNumber,
            lines,
            note: input.note,
            outsidePo: input.outsidePo,
            outsidePoReason: input.outsidePoReason,
            purchaseOrderCode: input.purchaseOrderCode,
            qcResult: "PENDING",
            receivedAt: input.receivedAt,
            receivingLocation: location,
            status: input.submit ? "AWAITING_QC" : "DRAFT",
            supplier,
            totalLines: lines.length,
            totalReceivedQuantity: lines.reduce(
                (sum, line) => sum + line.receivedQuantity,
                0
            ),
            vehiclePlate: input.vehiclePlate
        };

        goodsReceiptCache.unshift(receipt);
        return wait(copy(receipt));
    },

    finalizeGoodsReceiptCost(input) {
        return goodsReceiptApi.saveGoodsReceiptCost({
            ...input,
            finalize: true
        });
    },

    getGoodsReceipt(receiptId) {
        try {
            return wait(copy(findReceipt(receiptId)));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    getGoodsReceiptOptions() {
        return wait(goodsReceiptMockOptions);
    },

    listGoodsReceipts(query: GoodsReceiptListQuery): Promise<GoodsReceiptListResult> {
        const filtered = goodsReceiptCache
            .filter((receipt) =>
                receipt.code
                    .toLowerCase()
                    .includes(query.receiptCode.trim().toLowerCase())
            )
            .filter((receipt) =>
                query.supplierId === "ALL"
                    ? true
                    : receipt.supplier.id === query.supplierId
            )
            .filter((receipt) =>
                receipt.purchaseOrderCode
                    ?.toLowerCase()
                    .includes(query.purchaseOrderCode.trim().toLowerCase()) ??
                query.purchaseOrderCode.trim() === ""
            )
            .filter((receipt) =>
                query.status === "ALL" ? true : receipt.status === query.status
            )
            .filter((receipt) =>
                query.qcResult === "ALL"
                    ? true
                    : receipt.qcResult === query.qcResult
            )
            .filter((receipt) =>
                query.costStatus === "ALL"
                    ? true
                    : receipt.costStatus === query.costStatus
            )
            .filter((receipt) =>
                query.receivingLocationId === "ALL"
                    ? true
                    : receipt.receivingLocation.id === query.receivingLocationId
            )
            .filter((receipt) => {
                const time = new Date(receipt.receivedAt).getTime();
                const from = query.receivedFrom
                    ? new Date(`${query.receivedFrom}T00:00:00`).getTime()
                    : -Infinity;
                const to = query.receivedTo
                    ? new Date(`${query.receivedTo}T23:59:59`).getTime()
                    : Infinity;

                return time >= from && time <= to;
            })
            .filter((receipt) =>
                query.hasVariance === "ALL"
                    ? true
                    : query.hasVariance === "YES"
                      ? receipt.hasVariance
                      : !receipt.hasVariance
            )
            .sort(
                (first, second) =>
                    new Date(second.receivedAt).getTime() -
                    new Date(first.receivedAt).getTime()
            );
        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size).map(summaryOf);
        const today = new Date().toISOString().slice(0, 10);

        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            summary: {
                awaitingCost: filtered.filter(
                    (receipt) => receipt.status === "AWAITING_COST"
                ).length,
                awaitingQc: filtered.filter(
                    (receipt) => receipt.status === "AWAITING_QC"
                ).length,
                draft: filtered.filter((receipt) => receipt.status === "DRAFT")
                    .length,
                postedToday: filtered.filter(
                    (receipt) =>
                        receipt.status === "POSTED" &&
                        receipt.receivedAt.slice(0, 10) === today
                ).length,
                readyToPost: filtered.filter(
                    (receipt) => receipt.status === "READY_TO_POST"
                ).length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    performGoodsReceiptQc(input: GoodsReceiptQcInput) {
        try {
            const receipt = findReceipt(input.receiptId);
            validateQc(input, receipt);
            receipt.lines = receipt.lines.map((line) => {
                const lineInput = input.lines.find(
                    (item) => item.lineId === line.id
                );

                if (!lineInput) {
                    return line;
                }

                return {
                    ...line,
                    batchStatus:
                        lineInput.failedQuantity === lineInput.receivedQuantity
                            ? "RETURN_TO_SUPPLIER"
                            : lineInput.quarantinedQuantity > 0
                              ? "QUARANTINED"
                              : "AWAITING_QC",
                    qc: {
                        failedQuantity: lineInput.failedQuantity,
                        inspectionNote: lineInput.inspectionNote,
                        passedQuantity: lineInput.passedQuantity,
                        quarantinedQuantity: lineInput.quarantinedQuantity,
                        reasonCode: lineInput.reasonCode,
                        receivedQuantity: lineInput.receivedQuantity,
                        result:
                            lineInput.passedQuantity === 0 &&
                            lineInput.failedQuantity > 0
                                ? "FAILED"
                                : lineInput.quarantinedQuantity > 0 ||
                                    lineInput.failedQuantity > 0
                                  ? "PARTIALLY_PASSED"
                                  : "PASSED"
                    }
                };
            });
            receipt.qcResult = summarizeQc(receipt.lines);
            receipt.status = statusAfterQc(receipt.qcResult);
            receipt.auditEvents = [
                audit(input, "APPROVE", "Hoàn tất QC phiếu nhập."),
                ...receipt.auditEvents
            ];

            return wait(copy(receipt));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    postGoodsReceipt(input) {
        try {
            const receipt = findReceipt(input.receiptId);
            const passedQuantity = receipt.lines.reduce(
                (sum, line) => sum + line.qc.passedQuantity,
                0
            );

            if (receipt.qcResult === "PENDING" || passedQuantity <= 0) {
                throw new Error("Phiếu chưa đủ điều kiện QC để post.");
            }

            if (receipt.costStatus !== "FINAL") {
                throw new Error("Giá nhập phải FINAL trước khi post.");
            }

            receipt.status = "POSTED";
            receipt.stockDocumentId = `stock-doc-gr-${Date.now()}`;
            receipt.stockDocumentCode = `PNK-${receipt.code}`;
            receipt.lines = receipt.lines.map((line) => ({
                ...line,
                batchStatus:
                    line.qc.passedQuantity > 0 ? "AVAILABLE" : line.batchStatus
            }));
            receipt.movementSummary = {
                availableQuantity: receipt.lines.reduce(
                    (sum, line) => sum + line.qc.passedQuantity,
                    0
                ),
                quarantineQuantity: receipt.lines.reduce(
                    (sum, line) => sum + line.qc.quarantinedQuantity,
                    0
                ),
                returnToSupplierQuantity: receipt.lines.reduce(
                    (sum, line) => sum + line.qc.failedQuantity,
                    0
                ),
                totalInventoryValue: receipt.lines.reduce(
                    (sum, line) => sum + line.cost.lineNetAmount,
                    0
                )
            };
            receipt.auditEvents = [
                audit(input, "POST", "Post phiếu nhập và tạo chứng từ PNK."),
                ...receipt.auditEvents
            ];
            return wait(copy(receipt));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    saveGoodsReceiptCost(input: GoodsReceiptCostInput) {
        try {
            const receipt = findReceipt(input.receiptId);
            receipt.lines = receipt.lines.map((line) => {
                const lineInput = input.lines.find(
                    (item) => item.lineId === line.id
                );

                if (!lineInput) {
                    return line;
                }

                if (lineInput.acceptedQuantity <= 0) {
                    throw new Error("Số lượng accepted phải lớn hơn 0 để tính giá.");
                }

                const costInput = {
                    acceptedQuantity: lineInput.acceptedQuantity,
                    allocatedShippingCost: lineInput.allocatedShippingCost,
                    discount: lineInput.discount,
                    otherCost: lineInput.otherCost,
                    purchaseUnitPrice: lineInput.purchaseUnitPrice,
                    tax: lineInput.tax
                };

                return {
                    ...line,
                    cost: {
                        ...costInput,
                        currency: "VND",
                        finalUnitCost: calculateFinalUnitCost(costInput),
                        lineNetAmount: calculateLineNetAmount(costInput),
                        status: input.finalize ? "FINAL" : "PROVISIONAL"
                    }
                };
            });
            receipt.costStatus = input.finalize ? "FINAL" : "PROVISIONAL";
            receipt.status = input.finalize ? "READY_TO_POST" : "AWAITING_COST";
            receipt.auditEvents = [
                audit(
                    input,
                    "APPROVE",
                    input.finalize ? "Xác nhận giá FINAL." : "Lưu giá PROVISIONAL."
                ),
                ...receipt.auditEvents
            ];

            return wait(copy(receipt));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    submitGoodsReceipt(input) {
        return updateReceiptStatus(input, "AWAITING_QC", input.note ?? "Submit phiếu nhập.");
    },

    updateGoodsReceipt(receiptId, input) {
        return goodsReceiptApi.createGoodsReceipt(input).then((receipt) => {
            const index = goodsReceiptCache.findIndex((item) => item.id === receiptId);
            if (index >= 0) {
                goodsReceiptCache[index] = { ...receipt, id: receiptId };
                return copy(goodsReceiptCache[index]);
            }

            return receipt;
        });
    }
};
