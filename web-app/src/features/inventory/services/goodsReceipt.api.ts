import { apiClient } from "../../../api/api-client";
import type {
    GoodsReceiptActionInput,
    GoodsReceiptApi,
    GoodsReceiptCostInput,
    GoodsReceiptCreateInput,
    GoodsReceiptDashboardSummary,
    GoodsReceiptDetail,
    GoodsReceiptExportRow,
    GoodsReceiptLine,
    GoodsReceiptListItem,
    GoodsReceiptListQuery,
    GoodsReceiptListResult,
    GoodsReceiptOptions,
    GoodsReceiptPostInput,
    GoodsReceiptQcInput,
    GoodsReceiptStatus,
    GoodsReceiptSummary,
    QcResult,
    ReceiptExcessResolution,
    ReceiptShortageResolution
} from "../types/goods-receipt.types";
import type { InventoryAuditEvent } from "../types/stock-document.types";
import type { PageResponse } from "../types/inventory.types";

interface InventoryDocumentLineBackendResponse {
    id: string;
    lineNo: number;
    inventoryItemId: string;
    itemSku: string;
    itemName: string;
    itemType?: string;
    unitId: string;
    unitName: string;
    orderedQuantity: number;
    receivedQuantity: number;
    shortageQuantity: number;
    excessQuantity: number;
    acceptedQuantity: number;
    rejectedQuantity: number;
    note?: string;
    excessResolution?: ReceiptExcessResolution;
    shortageResolution?: ReceiptShortageResolution;
    returnedQuantity?: number;
    postedQuantity?: number;
    resolutionNote?: string;
    batchAllocations?: Array<{
        id: string;
        inventoryBatchId: string;
        batchCode: string;
        supplierLot?: string;
        manufactureDate?: string;
        expiryDate?: string;
        quantity: number;
    }>;
}

interface InventoryDocumentBackendResponse {
    id: string;
    documentCode: string;
    documentType: string;
    status: GoodsReceiptStatus;
    branchId?: string;
    locationId: string;
    locationName?: string;
    locationCode?: string;
    supplierId?: string;
    supplierName?: string;
    referenceType?: string;
    referenceId?: string;
    expectedDate?: string;
    note?: string;
    createdAt: string;
    updatedAt: string;
    createdBy?: string;
    createdByName?: string;
    approvedBy?: string;
    approvedByName?: string;
    approvedAt?: string;
    approvalNote?: string;
    rejectedBy?: string;
    rejectedByName?: string;
    rejectedAt?: string;
    rejectionReason?: string;
    receivedBy?: string;
    receivedByName?: string;
    receivedAt?: string;
    qcBy?: string;
    qcByName?: string;
    qcAt?: string;
    postedBy?: string;
    postedByName?: string;
    postedAt?: string;
    lines: InventoryDocumentLineBackendResponse[];
    totalLines: number;
    totalOrderedQuantity: number;
    totalReceivedQuantity: number;
    totalAcceptedQuantity: number;
    totalRejectedQuantity: number;
    exceptionApprovedBy?: string;
    exceptionApprovedByName?: string;
    exceptionApprovedAt?: string;
    exceptionApprovalNote?: string;
}

interface InventoryItemLookupBackendResponse {
    id: string;
    name?: string;
    code?: string;
    unitName?: string;
}

interface InventoryDocumentListItemBackendResponse {
    id: string;
    documentCode: string;
    status: GoodsReceiptStatus;
    supplierId?: string;
    supplierName?: string;
    locationId?: string;
    locationName?: string;
    totalReceivedQuantity: number;
    receivedAt?: string;
    createdBy?: string;
}

interface InventoryDocumentExportBackendResponse {
    documentCode: string;
    referenceId?: string;
    supplierName?: string;
    locationName?: string;
    status: GoodsReceiptStatus;
    totalLines: number;
    totalReceivedQuantity: number;
    totalAcceptedQuantity: number;
    totalRejectedQuantity: number;
    receivedAt?: string;
    createdAt?: string;
    createdByName?: string;
}

function resolveQcResult(status: GoodsReceiptStatus, totalAccepted: number, totalRejected: number): QcResult {
    if (status !== "PENDING_EXCEPTION_APPROVAL" && status !== "POSTED") {
        return "PENDING";
    }
    if (totalRejected > 0 && totalAccepted > 0) {
        return "PARTIALLY_PASSED";
    }
    return totalRejected > 0 ? "FAILED" : "PASSED";
}

function buildDocumentQueryParams(query: GoodsReceiptListQuery): Record<string, string | number | boolean | undefined> {
    const params: Record<string, string | number | boolean | undefined> = {};

    if (query.status && query.status !== "ALL") {
        params.status = query.status;
    }
    if (query.receivingLocationId && query.receivingLocationId !== "ALL") {
        params.locationId = query.receivingLocationId;
    }
    if (query.supplierId && query.supplierId !== "ALL") {
        params.supplierId = query.supplierId;
    }
    if (query.receiptCode?.trim()) {
        params.query = query.receiptCode.trim();
    } else if (query.purchaseOrderCode?.trim()) {
        params.query = query.purchaseOrderCode.trim();
    }
    if (query.receivedFrom) {
        params.fromDate = `${query.receivedFrom}T00:00:00Z`;
    }
    if (query.receivedTo) {
        params.toDate = `${query.receivedTo}T23:59:59Z`;
    }
    return params;
}

function mapDocToSummary(doc: InventoryDocumentBackendResponse): GoodsReceiptSummary {
    const totalLines = doc.lines?.length || doc.totalLines || 0;
    const totalReceived = doc.totalReceivedQuantity || 0;

    const qcResult = resolveQcResult(doc.status, doc.totalAcceptedQuantity, doc.totalRejectedQuantity);

    return {
        code: doc.documentCode,
        costStatus: "FINAL",
        createdBy: {
            id: doc.createdBy || "staff",
            name: doc.createdByName || "Không rõ",
            role: "INVENTORY_STAFF"
        },
        hasVariance: (doc.lines || []).some(
            (l) => (l.shortageQuantity || 0) > 0 || (l.excessQuantity || 0) > 0 || (l.rejectedQuantity || 0) > 0
        ),
        id: doc.id,
        purchaseOrderCode: doc.referenceId || "",
        qcResult,
        receivedAt: doc.receivedAt || doc.createdAt || new Date().toISOString(),
        receivingLocation: {
            code: doc.locationCode || "KHO-TONG",
            id: doc.locationId,
            isCentral: true,
            name: doc.locationName || "Kho nhận"
        },
        status: doc.status,
        stockDocumentCode: doc.documentCode,
        stockDocumentId: doc.id,
        supplier: {
            code: "SUP",
            id: doc.supplierId || "",
            name: doc.supplierName || "Nhà cung cấp"
        },
        totalLines,
        totalReceivedQuantity: totalReceived
    };
}

const globalIngredientNameMap = new Map<string, string>();

function resolveMaterialName(name?: string, sku?: string, id?: string): string {
    if (id && globalIngredientNameMap.has(String(id))) {
        return globalIngredientNameMap.get(String(id))!;
    }
    if (sku && globalIngredientNameMap.has(String(sku))) {
        return globalIngredientNameMap.get(String(sku))!;
    }
    if (name && globalIngredientNameMap.has(String(name))) {
        return globalIngredientNameMap.get(String(name))!;
    }
    if (name && !name.startsWith("NL-") && !name.startsWith("MAT-") && !name.startsWith("PKG-") && !name.startsWith("FG-")) {
        return name;
    }
    return name || sku || "Nguyên liệu";
}

function mapDocToDetail(doc: InventoryDocumentBackendResponse): GoodsReceiptDetail {
    const summary = mapDocToSummary(doc);
    const lines: GoodsReceiptLine[] = (doc.lines || []).map((line, index) => {
        const batchAlloc = line.batchAllocations?.[0];
        return {
            batchStatus: doc.status === "POSTED" ? "AVAILABLE" : "AWAITING_QC",
            conversionPreview: `1 ${line.unitName || "kg"} = 1 ${line.unitName || "kg"} nhập kho`,
            cost: {
                acceptedQuantity: line.acceptedQuantity || 0,
                allocatedShippingCost: 0,
                currency: "VND",
                discount: 0,
                finalUnitCost: 0,
                lineNetAmount: 0,
                otherCost: 0,
                purchaseUnitPrice: 0,
                status: "FINAL",
                tax: 0
            },
            countryOfOrigin: "Việt Nam",
            expiryDate: batchAlloc?.expiryDate || "",
            id: line.id || `line-${index}`,
            initialDamagedQuantity: 0,
            internalBatchCode: batchAlloc?.batchCode || "",
            itemType: line.itemType,
            manufacturedAt: batchAlloc?.manufactureDate || "",
            materialId: line.inventoryItemId,
            materialName: resolveMaterialName(line.itemName, line.itemSku, line.inventoryItemId),
            orderedQuantity: line.orderedQuantity,
            qc: {
                failedQuantity: line.rejectedQuantity || 0,
                inspectionNote: line.note,
                passedQuantity: line.acceptedQuantity || 0,
                quarantinedQuantity: 0,
                receivedQuantity: line.receivedQuantity || 0,
                result:
                    line.rejectedQuantity > 0 && line.acceptedQuantity > 0
                        ? "PARTIALLY_PASSED"
                        : line.rejectedQuantity > 0
                            ? "FAILED"
                            : line.acceptedQuantity > 0
                                ? "PASSED"
                                : "PENDING"
            },
            qualityCertificate: "",
            receivedQuantity: line.receivedQuantity || 0,
            shortageQuantity: line.shortageQuantity || 0,
            skuCode: line.itemSku || "NL",
            storageCondition: "Nơi khô ráo",
            supplierLot: batchAlloc?.supplierLot || "",
            surplusQuantity: line.excessQuantity || 0,
            unit: line.unitName || "kg",
            excessResolution: line.excessResolution,
            shortageResolution: line.shortageResolution,
            returnedQuantity: line.returnedQuantity || 0,
            postedQuantity: line.postedQuantity ?? undefined,
            resolutionNote: line.resolutionNote
        };
    });

    const auditEvents: InventoryAuditEvent[] = [];
    if (doc.createdAt) {
        auditEvents.push({
            actor: summary.createdBy,
            happenedAt: doc.createdAt,
            id: `create-${doc.id}`,
            note: doc.note || "Tạo phiếu nhập",
            roleSnapshot: "INVENTORY_STAFF",
            type: "CREATE_DRAFT"
        });
    }
    if (doc.approvedAt) {
        auditEvents.push({
            actor: {
                id: doc.approvedBy || "manager",
                name: doc.approvedByName || "Quản lý kho",
                role: "MANAGER"
            },
            happenedAt: doc.approvedAt,
            id: `approve-${doc.id}`,
            note: doc.approvalNote || "Duyệt phiếu nhập",
            roleSnapshot: "MANAGER",
            type: "APPROVE"
        });
    }
    if (doc.rejectedAt) {
        auditEvents.push({
            actor: {
                id: doc.rejectedBy || "manager",
                name: doc.rejectedByName || "Quản lý kho",
                role: "MANAGER"
            },
            happenedAt: doc.rejectedAt,
            id: `reject-${doc.id}`,
            note: doc.rejectionReason || "Từ chối phiếu nhập",
            reason: doc.rejectionReason,
            roleSnapshot: "MANAGER",
            type: "REJECT"
        });
    }
    if (doc.receivedAt) {
        auditEvents.push({
            actor: {
                id: doc.receivedBy || "staff",
                name: doc.receivedByName || "Nhân viên kho",
                role: "INVENTORY_STAFF"
            },
            happenedAt: doc.receivedAt,
            id: `receive-${doc.id}`,
            note: "Kiểm đếm thực tế hoàn tất",
            roleSnapshot: "INVENTORY_STAFF",
            type: "SUBMIT"
        });
    }
    if (doc.qcAt) {
        auditEvents.push({
            actor: {
                id: doc.qcBy || "qc",
                name: doc.qcByName || "Nhân viên QC",
                role: "QC_STAFF"
            },
            happenedAt: doc.qcAt,
            id: `qc-${doc.id}`,
            note: "Kiểm tra chất lượng QC hoàn tất",
            roleSnapshot: "QC_STAFF",
            type: "QC"
        });
    }
    if (doc.exceptionApprovedAt) {
        auditEvents.push({
            actor: {
                id: doc.exceptionApprovedBy || "manager",
                name: doc.exceptionApprovedByName || "Quản lý kho",
                role: "MANAGER"
            },
            happenedAt: doc.exceptionApprovedAt,
            id: `approve-exception-${doc.id}`,
            note: doc.exceptionApprovalNote
                ? `Duyệt ngoại lệ: ${doc.exceptionApprovalNote}`
                : "Duyệt ngoại lệ (thừa / thiếu / hỏng)",
            roleSnapshot: "MANAGER",
            type: "APPROVE"
        });
    }
    if (doc.postedAt) {
        auditEvents.push({
            actor: doc.postedBy
                ? { id: doc.postedBy, name: doc.postedByName || "Nhân viên kho", role: "INVENTORY_STAFF" }
                : summary.createdBy,
            happenedAt: doc.postedAt,
            id: `post-${doc.id}`,
            note: "Ghi sổ nhập kho thành công - Cập nhật tồn khả dụng",
            roleSnapshot: "INVENTORY_STAFF",
            type: "POST"
        });
    }

    return {
        ...summary,
        auditEvents,
        deliveryPerson: "Đại diện giao hàng",
        evidence: [],
        expectedDate: doc.expectedDate,
        lines,
        note: doc.note,
        outsidePo: false,
        purchaseOrderCode: doc.referenceId || "",
        vehiclePlate: "",
        referenceType: doc.referenceType,
        referenceId: doc.referenceId,
        postedAt: doc.postedAt,
        postedByName: doc.postedByName,
        exceptionApprovedByName: doc.exceptionApprovedByName,
        exceptionApprovedAt: doc.exceptionApprovedAt,
        exceptionApprovalNote: doc.exceptionApprovalNote
    };
}

export const goodsReceiptApi: GoodsReceiptApi = {
    async getGoodsReceiptSummaryCounts(): Promise<GoodsReceiptDashboardSummary> {
        try {
            const res = await apiClient.get<{
                draft: number;
                pendingApproval: number;
                approved: number;
                receiving: number;
                pendingExceptionApproval: number;
                awaitingQc: number;
                readyToPost: number;
                posted: number;
                postedToday: number;
                rejected: number;
                cancelled: number;
                total: number;
            }>("/inventory/goods-receipts/summary-counts");
            return {
                approved: Number(res.approved || 0),
                awaitingCost: 0,
                awaitingQc: Number(res.awaitingQc || 0),
                cancelled: Number(res.cancelled || 0),
                draft: Number(res.draft || 0),
                pendingApproval: Number(res.pendingApproval || 0),
                pendingExceptionApproval: Number(res.pendingExceptionApproval || 0),
                posted: Number(res.posted || 0),
                postedToday: Number(res.postedToday || 0),
                readyToPost: Number(res.readyToPost || 0),
                receiving: Number(res.receiving || 0),
                rejected: Number(res.rejected || 0),
                total: Number(res.total || 0)
            };
        } catch (error) {
            console.error("Failed to get goods receipts summary counts:", error);
            return {
                approved: 0,
                awaitingCost: 0,
                awaitingQc: 0,
                cancelled: 0,
                draft: 0,
                pendingApproval: 0,
                pendingExceptionApproval: 0,
                posted: 0,
                postedToday: 0,
                readyToPost: 0,
                receiving: 0,
                rejected: 0,
                total: 0
            };
        }
    },

    async listGoodsReceipts(query: GoodsReceiptListQuery): Promise<GoodsReceiptListResult> {
        const params = {
            ...buildDocumentQueryParams(query),
            page: query.page,
            size: query.size
        };

        try {
            const res = await apiClient.get<PageResponse<InventoryDocumentListItemBackendResponse>>(
                "/inventory/goods-receipts",
                { query: params }
            );

            const content: GoodsReceiptListItem[] = (res.content || []).map((item) => ({
                code: item.documentCode,
                createdById: item.createdBy,
                id: item.id,
                receivedAt: item.receivedAt,
                receivingLocationName: item.locationName || "Kho nhận",
                status: item.status,
                supplierName: item.supplierName || "Nhà cung cấp",
                totalReceivedQuantity: item.totalReceivedQuantity || 0
            }));
            return {
                content,
                first: res.first,
                last: res.last,
                page: res.page ?? query.page,
                size: res.size ?? query.size,
                summary: {
                    approved: content.filter((r) => r.status === "APPROVED").length,
                    awaitingCost: 0,
                    awaitingQc: 0,
                    cancelled: content.filter((r) => r.status === "CANCELLED").length,
                    draft: content.filter((r) => r.status === "DRAFT").length,
                    pendingApproval: content.filter((r) => r.status === "PENDING_APPROVAL").length,
                    pendingExceptionApproval: content.filter((r) => r.status === "PENDING_EXCEPTION_APPROVAL").length,
                    posted: content.filter((r) => r.status === "POSTED").length,
                    postedToday: content.filter((r) => r.status === "POSTED").length,
                    readyToPost: 0,
                    receiving: content.filter((r) => r.status === "RECEIVING").length,
                    rejected: content.filter((r) => r.status === "REJECTED").length,
                    total: res.totalElements || content.length
                },
                totalElements: res.totalElements,
                totalPages: res.totalPages
            };
        } catch (error) {
            console.error("Failed to list goods receipts from backend:", error);
            return {
                content: [],
                first: true,
                last: true,
                page: 0,
                size: query.size,
                summary: {
                    approved: 0,
                    awaitingCost: 0,
                    awaitingQc: 0,
                    cancelled: 0,
                    draft: 0,
                    pendingApproval: 0,
                    pendingExceptionApproval: 0,
                    posted: 0,
                    postedToday: 0,
                    readyToPost: 0,
                    receiving: 0,
                    rejected: 0,
                    total: 0
                },
                totalElements: 0,
                totalPages: 0
            };
        }
    },

    async getGoodsReceipt(receiptId: string): Promise<GoodsReceiptDetail> {
        // Tên nguyên liệu đã có sẵn trong response (itemName/itemSku) — không cần tải trước danh sách NCC/kho/nguyên liệu
        const res = await apiClient.get<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(receiptId)}`
        );
        return mapDocToDetail(res);
    },

    async exportGoodsReceipts(query: GoodsReceiptListQuery): Promise<GoodsReceiptExportRow[]> {
        const rows = await apiClient.get<InventoryDocumentExportBackendResponse[]>(
            "/inventory/goods-receipts/export",
            { query: buildDocumentQueryParams(query) }
        );
        return (rows || []).map((row) => ({
            code: row.documentCode,
            createdByName: row.createdByName || "Không rõ",
            purchaseOrderCode: row.referenceId || "",
            qcResult: resolveQcResult(row.status, row.totalAcceptedQuantity, row.totalRejectedQuantity),
            receivedAt: row.receivedAt,
            receivingLocationName: row.locationName || "",
            status: row.status,
            supplierName: row.supplierName || "",
            totalLines: row.totalLines || 0,
            totalReceivedQuantity: row.totalReceivedQuantity || 0
        }));
    },

    async deleteGoodsReceipt(receiptId: string): Promise<void> {
        await apiClient.delete<void>(`/inventory/goods-receipts/${encodeURIComponent(receiptId)}`);
    },

    async createGoodsReceipt(input: GoodsReceiptCreateInput): Promise<GoodsReceiptDetail> {
        const payload = {
            expectedDate: input.receivedAt ? `${input.receivedAt}T00:00:00Z` : undefined,
            items: input.lines.map((line) => ({
                inventoryItemId: line.materialId,
                note: line.supplierLot,
                orderedQuantity: line.orderedQuantity || line.receivedQuantity
            })),
            locationId: input.receivingLocationId,
            note: input.note,
            referenceId: input.purchaseOrderCode,
            referenceType: "PURCHASE_ORDER",
            supplierId: input.supplierId
        };

        const doc = await apiClient.post<InventoryDocumentBackendResponse>(
            "/inventory/goods-receipts",
            payload
        );

        if (input.submit) {
            const submitted = await apiClient.post<InventoryDocumentBackendResponse>(
                `/inventory/goods-receipts/${doc.id}/submit`
            );
            return mapDocToDetail(submitted);
        }

        return mapDocToDetail(doc);
    },

    async updateGoodsReceipt(receiptId: string, input: GoodsReceiptCreateInput): Promise<GoodsReceiptDetail> {
        const payload = {
            expectedDate: input.receivedAt ? `${input.receivedAt}T00:00:00Z` : undefined,
            items: input.lines.map((line) => ({
                inventoryItemId: line.materialId,
                note: line.supplierLot,
                orderedQuantity: line.orderedQuantity || line.receivedQuantity
            })),
            locationId: input.receivingLocationId,
            note: input.note,
            referenceId: input.purchaseOrderCode,
            referenceType: "PURCHASE_ORDER",
            supplierId: input.supplierId
        };

        const doc = await apiClient.put<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(receiptId)}`,
            payload
        );

        if (input.submit) {
            const submitted = await apiClient.post<InventoryDocumentBackendResponse>(
                `/inventory/goods-receipts/${doc.id}/submit`
            );
            return mapDocToDetail(submitted);
        }

        return mapDocToDetail(doc);
    },

    async submitGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/submit`
        );
        return mapDocToDetail(res);
    },

    async approveGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/approve`,
            { approvalNote: input.note }
        );
        return mapDocToDetail(res);
    },

    async rejectGoodsReceipt(input: GoodsReceiptActionInput & { reason: string }): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/reject`,
            { rejectionReason: input.reason }
        );
        return mapDocToDetail(res);
    },

    async startReceivingGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/start-receiving`
        );
        return mapDocToDetail(res);
    },

    async confirmReceivingGoodsReceipt(
        receiptId: string,
        items: Array<{ lineId: string; receivedQuantity: number; note?: string }>
    ): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(receiptId)}/receive`,
            { items }
        );
        return mapDocToDetail(res);
    },

    async performGoodsReceiptQc(input: GoodsReceiptQcInput): Promise<GoodsReceiptDetail> {
        const payload = {
            items: input.lines.map((line) => {
                const batches = (line as unknown as { batches?: Array<{ batchCode: string; manufactureDate: string; expiryDate: string; quantity: number; supplierLot?: string }> }).batches;
                return {
                    acceptedQuantity: line.passedQuantity,
                    batches: batches && batches.length > 0
                        ? batches
                        : line.passedQuantity > 0
                            ? [
                                {
                                    batchCode: `LOT-${Date.now()}`,
                                    expiryDate: "2027-12-31",
                                    manufactureDate: "2026-01-01",
                                    quantity: line.passedQuantity,
                                    supplierLot: ""
                                }
                            ]
                            : [],
                    lineId: line.lineId,
                    rejectedQuantity: line.failedQuantity
                };
            })
        };

        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/qc`,
            payload
        );
        return mapDocToDetail(res);
    },

    async completeGoodsReceiptInspection(
        receiptId: string,
        receiveItems: Array<{ lineId: string; receivedQuantity: number; note?: string }>,
        input: GoodsReceiptQcInput
    ): Promise<GoodsReceiptDetail> {
        const qcPayload = input.lines.map((line) => {
            const batches = (line as unknown as { batches?: Array<{ batchCode: string; manufactureDate: string; expiryDate: string; quantity: number; supplierLot?: string }> }).batches;
            return {
                lineId: line.lineId,
                acceptedQuantity: line.passedQuantity,
                rejectedQuantity: line.failedQuantity,
                batches: batches && batches.length > 0
                    ? batches
                    : line.passedQuantity > 0
                        ? [
                            {
                                batchCode: `LOT-${Date.now()}`,
                                expiryDate: "2027-12-31",
                                manufactureDate: "2026-01-01",
                                quantity: line.passedQuantity,
                                supplierLot: ""
                            }
                        ]
                        : []
            };
        });

        const payload = {
            receiveItems: receiveItems,
            qcItems: qcPayload
        };

        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(receiptId)}/qc`,
            payload
        );
        return mapDocToDetail(res);
    },

    async postGoodsReceipt(input: GoodsReceiptPostInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/post`,
            { approvalNote: input.approvalNote, lines: input.lines }
        );
        return mapDocToDetail(res);
    },

    async cancelGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/goods-receipts/${encodeURIComponent(input.receiptId)}/cancel`,
            undefined,
            { query: { note: input.note } }
        );
        return mapDocToDetail(res);
    },

    async saveGoodsReceiptCost(input: GoodsReceiptCostInput): Promise<GoodsReceiptDetail> {
        return this.getGoodsReceipt(input.receiptId);
    },

    async finalizeGoodsReceiptCost(input: GoodsReceiptCostInput): Promise<GoodsReceiptDetail> {
        return this.getGoodsReceipt(input.receiptId);
    },

    async getGoodsReceiptOptions(supplierId?: string): Promise<GoodsReceiptOptions> {
        if (!supplierId) {
            return { locations: [], materials: [], suppliers: [] };
        }

        // 1 request: backend lấy nguyên liệu của NCC (Catalog) và quy đổi sang mặt hàng tồn kho (id = inventory_items.id)
        const items = await apiClient.get<InventoryItemLookupBackendResponse[]>(
            "/inventory/inventory-items/lookup",
            { query: { supplierId } }
        );
        const materials = (items || []).map((item) => ({
            id: item.id,
            name: item.name || item.code || "Nguyên liệu",
            shelfLifeWarningDays: 30,
            skuCode: item.code || "NL",
            unit: item.unitName || "kg"
        }));

        materials.forEach((m) => {
            globalIngredientNameMap.set(m.id, m.name);
            if (m.skuCode) globalIngredientNameMap.set(m.skuCode, m.name);
        });

        return { locations: [], materials, suppliers: [] };
    }
};
