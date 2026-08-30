import { apiClient } from "../../../api/api-client";
import { supplierApi } from "../../manufacturing/services/supplier.api";
import { ingredientApi } from "../../product/services/ingredient.api";
import { inventoryItemApi } from "./inventoryItem.api";
import { inventoryLocationApi } from "./inventoryLocation.api";
import type {
    GoodsReceiptActionInput,
    GoodsReceiptApi,
    GoodsReceiptCostInput,
    GoodsReceiptCreateInput,
    GoodsReceiptDetail,
    GoodsReceiptLine,
    GoodsReceiptListQuery,
    GoodsReceiptListResult,
    GoodsReceiptOptions,
    GoodsReceiptQcInput,
    GoodsReceiptStatus,
    GoodsReceiptSummary,
    QcResult
} from "../types/goods-receipt.types";
import type { SupplierResponse } from "../../manufacturing/types/supplier.types";
import type { PageResult } from "../../product/types/product.types";
import type { InventoryAuditEvent } from "../types/stock-document.types";
import type { InventoryItemResponse, InventoryLocationResponse, PageResponse } from "../types/inventory.types";

interface InventoryDocumentLineBackendResponse {
    id: string;
    lineNo: number;
    inventoryItemId: string;
    itemSku: string;
    itemName: string;
    unitId: string;
    unitName: string;
    orderedQuantity: number;
    receivedQuantity: number;
    shortageQuantity: number;
    excessQuantity: number;
    acceptedQuantity: number;
    rejectedQuantity: number;
    note?: string;
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
    approvedBy?: string;
    approvedAt?: string;
    approvalNote?: string;
    rejectedBy?: string;
    rejectedAt?: string;
    rejectionReason?: string;
    receivedBy?: string;
    receivedAt?: string;
    qcBy?: string;
    qcAt?: string;
    postedBy?: string;
    postedAt?: string;
    lines: InventoryDocumentLineBackendResponse[];
    totalLines: number;
    totalOrderedQuantity: number;
    totalReceivedQuantity: number;
    totalAcceptedQuantity: number;
    totalRejectedQuantity: number;
}

function mapDocToSummary(doc: InventoryDocumentBackendResponse): GoodsReceiptSummary {
    const totalLines = doc.lines?.length || doc.totalLines || 0;
    const totalReceived = doc.totalReceivedQuantity || 0;

    let qcResult: QcResult = "PENDING";
    if (doc.status === "QC_COMPLETED" || doc.status === "POSTED") {
        if (doc.totalRejectedQuantity > 0 && doc.totalAcceptedQuantity > 0) {
            qcResult = "PARTIALLY_PASSED";
        } else if (doc.totalRejectedQuantity > 0) {
            qcResult = "FAILED";
        } else {
            qcResult = "PASSED";
        }
    }

    return {
        code: doc.documentCode,
        costStatus: "FINAL",
        createdBy: {
            id: doc.createdBy || "staff",
            name: doc.createdBy || "Nhân viên kho",
            role: "WAREHOUSE"
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
            manufacturedAt: batchAlloc?.manufactureDate || "",
            materialId: line.inventoryItemId,
            materialName: line.itemName || line.itemSku || "Nguyên liệu",
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
            unit: line.unitName || "kg"
        };
    });

    const auditEvents: InventoryAuditEvent[] = [];
    if (doc.createdAt) {
        auditEvents.push({
            actor: summary.createdBy,
            happenedAt: doc.createdAt,
            id: `create-${doc.id}`,
            note: doc.note || "Tạo phiếu nhập",
            roleSnapshot: "WAREHOUSE",
            type: "CREATE_DRAFT"
        });
    }
    if (doc.approvedAt) {
        auditEvents.push({
            actor: {
                id: doc.approvedBy || "manager",
                name: "Quản lý kho",
                role: "BRANCH_MANAGER"
            },
            happenedAt: doc.approvedAt,
            id: `approve-${doc.id}`,
            note: doc.approvalNote || "Duyệt phiếu nhập",
            roleSnapshot: "BRANCH_MANAGER",
            type: "APPROVE"
        });
    }
    if (doc.rejectedAt) {
        auditEvents.push({
            actor: {
                id: doc.rejectedBy || "manager",
                name: "Quản lý kho",
                role: "BRANCH_MANAGER"
            },
            happenedAt: doc.rejectedAt,
            id: `reject-${doc.id}`,
            note: doc.rejectionReason || "Từ chối phiếu nhập",
            reason: doc.rejectionReason,
            roleSnapshot: "BRANCH_MANAGER",
            type: "REJECT"
        });
    }
    if (doc.receivedAt) {
        auditEvents.push({
            actor: {
                id: doc.receivedBy || "staff",
                name: "Nhân viên kho",
                role: "WAREHOUSE"
            },
            happenedAt: doc.receivedAt,
            id: `receive-${doc.id}`,
            note: "Kiểm đếm thực tế hoàn tất",
            roleSnapshot: "WAREHOUSE",
            type: "SUBMIT"
        });
    }
    if (doc.qcAt) {
        auditEvents.push({
            actor: {
                id: doc.qcBy || "qc",
                name: "Nhân viên QC",
                role: "QC_STAFF"
            },
            happenedAt: doc.qcAt,
            id: `qc-${doc.id}`,
            note: "Kiểm tra chất lượng QC hoàn tất",
            roleSnapshot: "QC_STAFF",
            type: "APPROVE"
        });
    }
    if (doc.postedAt) {
        auditEvents.push({
            actor: summary.createdBy,
            happenedAt: doc.postedAt,
            id: `post-${doc.id}`,
            note: "Ghi sổ nhập kho thành công - Cập nhật tồn khả dụng",
            roleSnapshot: "WAREHOUSE",
            type: "POST"
        });
    }

    return {
        ...summary,
        auditEvents,
        deliveryPerson: "Đại diện giao hàng",
        evidence: [],
        lines,
        note: doc.note,
        outsidePo: false,
        purchaseOrderCode: doc.referenceId || "",
        vehiclePlate: ""
    };
}

export const goodsReceiptApi: GoodsReceiptApi = {
    async listGoodsReceipts(query: GoodsReceiptListQuery): Promise<GoodsReceiptListResult> {
        const params: Record<string, string | number | boolean | undefined> = {
            documentType: "PURCHASE_RECEIPT",
            page: query.page,
            size: query.size
        };

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

        try {
            const res = await apiClient.get<PageResponse<InventoryDocumentBackendResponse>>(
                "/inventory/documents",
                { query: params }
            );

            const content = (res.content || []).map(mapDocToSummary);
            return {
                content,
                first: res.first,
                last: res.last,
                page: res.page ?? query.page,
                size: res.size ?? query.size,
                summary: {
                    awaitingCost: 0,
                    awaitingQc: content.filter((r) => r.status === "QC_PENDING" || r.status === "AWAITING_QC").length,
                    draft: content.filter((r) => r.status === "DRAFT").length,
                    postedToday: content.filter((r) => r.status === "POSTED").length,
                    readyToPost: content.filter((r) => r.status === "QC_COMPLETED" || r.status === "READY_TO_POST").length
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
                summary: { awaitingCost: 0, awaitingQc: 0, draft: 0, postedToday: 0, readyToPost: 0 },
                totalElements: 0,
                totalPages: 0
            };
        }
    },

    async getGoodsReceipt(receiptId: string): Promise<GoodsReceiptDetail> {
        const res = await apiClient.get<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(receiptId)}`
        );
        return mapDocToDetail(res);
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
            "/inventory/documents",
            payload
        );

        if (input.submit) {
            const submitted = await apiClient.post<InventoryDocumentBackendResponse>(
                `/inventory/documents/${doc.id}/submit`
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
            `/inventory/documents/${encodeURIComponent(receiptId)}`,
            payload
        );

        if (input.submit) {
            const submitted = await apiClient.post<InventoryDocumentBackendResponse>(
                `/inventory/documents/${doc.id}/submit`
            );
            return mapDocToDetail(submitted);
        }

        return mapDocToDetail(doc);
    },

    async submitGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/submit`
        );
        return mapDocToDetail(res);
    },

    async approveGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/approve`,
            { approvalNote: input.note }
        );
        return mapDocToDetail(res);
    },

    async rejectGoodsReceipt(input: GoodsReceiptActionInput & { reason: string }): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/reject`,
            { rejectionReason: input.reason }
        );
        return mapDocToDetail(res);
    },

    async startReceivingGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/start-receiving`
        );
        return mapDocToDetail(res);
    },

    async confirmReceivingGoodsReceipt(
        receiptId: string,
        items: Array<{ lineId: string; receivedQuantity: number; note?: string }>
    ): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(receiptId)}/receive`,
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
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/qc`,
            payload
        );
        return mapDocToDetail(res);
    },

    async postGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/post`
        );
        return mapDocToDetail(res);
    },

    async cancelGoodsReceipt(input: GoodsReceiptActionInput): Promise<GoodsReceiptDetail> {
        const res = await apiClient.post<InventoryDocumentBackendResponse>(
            `/inventory/documents/${encodeURIComponent(input.receiptId)}/cancel`,
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
        let suppRes: unknown = null;
        let locRes: unknown = null;
        let itemRes: unknown = null;
        let ingRes: unknown = null;

        try {
            suppRes = await supplierApi.list({ page: 0, size: 100 });
        } catch (e) {
            console.warn("[GoodsReceipt] Lỗi supplierApi.list, thử fallback trực tiếp:", e);
            try {
                suppRes = await apiClient.get<PageResult<SupplierResponse>>("/catalog/suppliers", {
                    query: { page: 0, size: 100 }
                });
            } catch (e2) {
                console.error("[GoodsReceipt] Lỗi gọi trực tiếp /catalog/suppliers:", e2);
            }
        }

        try {
            locRes = await inventoryLocationApi.list({ page: 0, size: 100 });
        } catch (e) {
            console.warn("[GoodsReceipt] Lỗi inventoryLocationApi.list, thử fallback trực tiếp:", e);
            try {
                locRes = await apiClient.get<PageResponse<InventoryLocationResponse>>("/inventory/inventory-locations", {
                    query: { page: 0, size: 100 }
                });
            } catch (e2) {
                console.error("[GoodsReceipt] Lỗi gọi trực tiếp /inventory/inventory-locations:", e2);
            }
        }

        try {
            itemRes = await inventoryItemApi.list({ itemType: "INGREDIENT", page: 0, size: 200, status: "ACTIVE" });
        } catch (e) {
            console.warn("[GoodsReceipt] Lỗi inventoryItemApi.list:", e);
        }

        if (supplierId) {
            try {
                ingRes = await ingredientApi.list({ page: 0, size: 200, status: "ACTIVE", supplierId });
            } catch (e) {
                console.warn("[GoodsReceipt] Lỗi ingredientApi.list:", e);
            }
        }

        const extractArray = <T>(res: unknown): T[] => {
            if (!res) return [];
            let arr: unknown[] = [];
            if (Array.isArray(res)) {
                arr = res;
            } else if (typeof res === "object" && res !== null) {
                const obj = res as Record<string, unknown>;
                if (Array.isArray(obj.content)) arr = obj.content;
                else if (Array.isArray(obj.data)) arr = obj.data;
                else if (Array.isArray(obj.result)) arr = obj.result;
                else if (obj.data && typeof obj.data === "object" && obj.data !== null && Array.isArray((obj.data as Record<string, unknown>).content)) {
                    arr = (obj.data as Record<string, unknown>).content as unknown[];
                } else if (obj.result && typeof obj.result === "object" && obj.result !== null && Array.isArray((obj.result as Record<string, unknown>).content)) {
                    arr = (obj.result as Record<string, unknown>).content as unknown[];
                }
            }
            return arr.filter((item): item is T => item !== null && item !== undefined);
        };

        const rawSuppliers = extractArray<Record<string, unknown>>(suppRes);
        const suppliers = rawSuppliers
            .filter((s): s is Record<string, unknown> => Boolean(s))
            .map((s) => ({
                code: String(s.supplierCode || s.code || "SUP"),
                id: String(s.id || ""),
                name: String(s.name || "Nhà cung cấp"),
                suppliedIngredientNames: (extractArray<Record<string, unknown>>(s.ingredients) || [])
                    .filter((ing): ing is Record<string, unknown> => Boolean(ing))
                    .map((ing) => String(ing.name || ""))
                    .filter(Boolean)
            }));

        const rawLocations = extractArray<Record<string, unknown>>(locRes);
        const locations = rawLocations
            .filter((l): l is Record<string, unknown> => Boolean(l))
            .map((l) => ({
                code: String(l.code || "LOC"),
                id: String(l.id || ""),
                isCentral: Boolean(l.isCentral),
                name: String(l.name || "Kho hàng")
            }));

        const validIngredientIds = new Set<string>();
        const validIngredientCodes = new Set<string>();
        const ingredientMap = new Map<string, { name: string; code: string; unit: string }>();

        let rawIngredients = supplierId
            ? extractArray<Record<string, unknown>>(ingRes).filter((ing): ing is Record<string, unknown> => Boolean(ing))
            : [];

        // Fallback: nếu supplierId có nhưng ingRes rỗng, tìm trong supplier.ingredients hoặc gọi supplierApi.getById
        if (supplierId && rawIngredients.length === 0) {
            const matchedSupp = rawSuppliers.find((s) => String(s.id) === String(supplierId));
            if (matchedSupp && Array.isArray(matchedSupp.ingredients) && matchedSupp.ingredients.length > 0) {
                rawIngredients = extractArray<Record<string, unknown>>(matchedSupp.ingredients)
                    .filter((ing): ing is Record<string, unknown> => Boolean(ing));
            } else {
                try {
                    const suppDetail = await supplierApi.getById(supplierId);
                    if (suppDetail?.ingredients?.length) {
                        rawIngredients = extractArray<Record<string, unknown>>(suppDetail.ingredients)
                            .filter((ing): ing is Record<string, unknown> => Boolean(ing));
                    }
                } catch (e) {
                    console.warn("[GoodsReceipt] Lỗi supplierApi.getById fallback:", e);
                }
            }
        }

        rawIngredients.forEach((ing) => {
            if (!ing) return;
            const unitObjName = typeof ing.unit === "object" && ing.unit !== null
                ? ((ing.unit as Record<string, unknown>).symbol || (ing.unit as Record<string, unknown>).name)
                : undefined;
            const unit = String(
                ing.unitSymbol ||
                ing.unitName ||
                ing.unit_name ||
                unitObjName ||
                "kg"
            );
            const info = {
                code: String(ing.ingredientCode || ing.code || "NL"),
                name: String(ing.name || "Nguyên liệu"),
                unit
            };
            if (ing.id !== undefined && ing.id !== null) {
                const idStr = String(ing.id);
                validIngredientIds.add(idStr);
                ingredientMap.set(idStr, info);
            }
            if (ing.ingredientCode) {
                const codeStr = String(ing.ingredientCode);
                validIngredientCodes.add(codeStr);
                ingredientMap.set(codeStr, info);
            }
        });

        const rawItems = supplierId
            ? extractArray<InventoryItemResponse>(itemRes).filter((i) => {
                if (!i) return false;
                if (i.itemType && i.itemType !== "INGREDIENT") return false;
                const refIdStr = i.referenceId !== undefined && i.referenceId !== null ? String(i.referenceId) : undefined;
                return (refIdStr && validIngredientIds.has(refIdStr)) || (Boolean(i.referenceCode) && validIngredientCodes.has(String(i.referenceCode)));
            })
            : [];

        const materials = !supplierId
            ? []
            : (rawItems.length > 0
                ? rawItems.map((i) => {
                    const refIdStr = i.referenceId !== undefined && i.referenceId !== null ? String(i.referenceId) : undefined;
                    const matched = (refIdStr ? ingredientMap.get(refIdStr) : undefined)
                        ?? (i.referenceCode ? ingredientMap.get(i.referenceCode) : undefined);

                    return {
                        id: String(i.id),
                        name: matched?.name || i.referenceCode || "Nguyên liệu",
                        shelfLifeWarningDays: 30,
                        skuCode: matched?.code || i.referenceCode || "NL",
                        unit: matched?.unit || "kg"
                    };
                })
                : rawIngredients.map((ing) => {
                    const unitObjName = typeof ing.unit === "object" && ing.unit !== null
                        ? ((ing.unit as Record<string, unknown>).symbol || (ing.unit as Record<string, unknown>).name)
                        : undefined;
                    return {
                        id: String(ing.id || ""),
                        name: String(ing.name || "Nguyên liệu"),
                        shelfLifeWarningDays: 30,
                        skuCode: String(ing.ingredientCode || ing.code || "NL"),
                        unit: String(
                            ing.unitSymbol ||
                            ing.unitName ||
                            ing.unit_name ||
                            unitObjName ||
                            "kg"
                        )
                    };
                }));

        const finalResult: GoodsReceiptOptions = {
            locations: locations.length > 0 ? locations : [
                { code: "KHO-TONG", id: "1", isCentral: true, name: "Kho Tổng Hương Vân Trà" }
            ],
            materials,
            suppliers
        };

        console.log("[GoodsReceipt API] Options loaded:", {
            suppRes,
            suppliers,
            locRes,
            locations
        });

        return finalResult;
    }
};
