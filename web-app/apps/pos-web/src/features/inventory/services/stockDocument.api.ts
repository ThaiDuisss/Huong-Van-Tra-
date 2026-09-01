import {
    inboundStockDocumentTypes,
    outboundStockDocumentTypes
} from "../constants/stock-document.constants";
import {
    actorForRole,
    stockDocumentMockData,
    stockDocumentMockOptions
} from "../mocks/stock-documents.mock";
import { inventoryMasterDocuments } from "../mocks/inventory-master-data.mock";
import type {
    InventoryAuditEvent,
    StockDocumentAction,
    StockDocumentApi,
    StockDocumentListQuery,
    StockDocumentListResult,
    StockDocumentMutationInput,
    StockDocumentStatus,
    StockDocumentType
} from "../types/stock-document.types";

const stockDocumentCache = [...stockDocumentMockData];

function wait<T>(value: T, ms = 420) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function textIncludes(value: string | undefined, query: string) {
    if (!query.trim()) {
        return true;
    }

    return (value ?? "").toLowerCase().includes(query.trim().toLowerCase());
}

function dateInRange(value: string | undefined, from: string, to: string) {
    if (!value) {
        return !from && !to;
    }

    const time = new Date(value).getTime();
    const fromTime = from ? new Date(`${from}T00:00:00`).getTime() : -Infinity;
    const toTime = to ? new Date(`${to}T23:59:59`).getTime() : Infinity;

    return time >= fromTime && time <= toTime;
}

function updateStatus(
    input: StockDocumentMutationInput,
    action: StockDocumentAction,
    nextStatus: StockDocumentStatus
) {
    const document = stockDocumentCache.find(
        (item) => item.id === input.documentId
    );

    if (!document) {
        return Promise.reject(new Error("Không tìm thấy chứng từ kho."));
    }

    if ((action === "REJECT" || action === "REVERSE") && !input.reason?.trim()) {
        return Promise.reject(new Error("Vui lòng nhập lý do bắt buộc."));
    }

    const actor = actorForRole("ADMIN");
    const auditTypeByAction: Record<StockDocumentAction, InventoryAuditEvent["type"]> = {
        APPROVE: "APPROVE",
        CANCEL: "CANCEL",
        POST: "POST",
        REJECT: "REJECT",
        REVERSE: "REVERSE",
        SUBMIT: "SUBMIT"
    };
    const now = new Date().toISOString();

    document.status = nextStatus;
    document.auditEvents = [
        {
            actor,
            happenedAt: now,
            id: `${auditTypeByAction[action]}-${Date.now()}`,
            note: input.note,
            reason: input.reason,
            roleSnapshot: "ADMIN",
            type: auditTypeByAction[action]
        },
        ...document.auditEvents
    ];

    if (action === "APPROVE") {
        document.approvedBy = actor;
    }

    if (action === "POST") {
        document.postedAt = now;
        document.postedBy = actor;
        document.lines = document.lines.map((line) => ({
            ...line,
            status: "POSTED"
        }));
    }

    if (action === "REVERSE") {
        const reversalId = `stock-doc-reversal-${Date.now()}`;
        const reversalCode = `REV-${document.code}`;
        const reversedDocument = {
            ...document,
            auditEvents: [
                {
                    actor,
                    happenedAt: now,
                    id: `CREATE_DRAFT-${Date.now()}-reversal`,
                    note: "Chứng từ đảo được tạo tự động từ chứng từ gốc.",
                    reason: input.reason,
                    roleSnapshot: "ADMIN",
                    type: "REVERSE" as const
                }
            ],
            code: reversalCode,
            createdAt: now,
            createdBy: actor,
            destinationLocation: document.sourceLocation,
            id: reversalId,
            postedAt: now,
            postedBy: actor,
            referenceCode: document.code,
            referenceId: document.id,
            referenceType: "REVERSAL_DOCUMENT",
            reversedBy: undefined,
            reversedDocumentCode: document.code,
            reversedDocumentId: document.id,
            sourceLocation: document.destinationLocation,
            status: "POSTED" as const
        };

        stockDocumentCache.unshift(reversedDocument);
        document.reversedBy = actor;
        document.reversedDocumentId = reversalId;
        document.reversedDocumentCode = reversalCode;
    }

    return wait({ ...document, lines: [...document.lines] });
}

const linkedDocumentTypes: Record<string, StockDocumentType> = {
    [inventoryMasterDocuments.customBundleIssue.id]: "CUSTOM_BUNDLE_ISSUE",
    [inventoryMasterDocuments.customerReturnReceipt.id]: "CUSTOMER_RETURN_RECEIPT",
    [inventoryMasterDocuments.disposalWriteOff.id]: "WRITE_OFF",
    [inventoryMasterDocuments.goodsReceipt.id]: "PURCHASE_RECEIPT",
    [inventoryMasterDocuments.productionIssue.id]: "PRODUCTION_ISSUE",
    [inventoryMasterDocuments.productionReceipt.id]: "PRODUCTION_RECEIPT",
    [inventoryMasterDocuments.stocktakeAdjustment.id]: "ADJUSTMENT_OUT",
    [inventoryMasterDocuments.supplierReturnIssue.id]: "SUPPLIER_RETURN",
    [inventoryMasterDocuments.transferIssue.id]: "TRANSFER_ISSUE",
    [inventoryMasterDocuments.transferReceipt.id]: "TRANSFER_RECEIPT"
};

const linkedDocumentCodes = Object.values(inventoryMasterDocuments).reduce<Record<string, string>>((accumulator, document) => {
    accumulator[document.id] = document.code;
    return accumulator;
}, {});

function synthesizeLinkedDocument(documentId: string) {
    const type = linkedDocumentTypes[documentId] ??
        (documentId.includes("writeoff") || documentId.includes("disposal") ? "WRITE_OFF" :
            documentId.includes("supplier-return") ? "SUPPLIER_RETURN" :
                documentId.includes("transfer-receipt") ? "TRANSFER_RECEIPT" :
                    documentId.includes("transfer-issue") ? "TRANSFER_ISSUE" :
                        documentId.includes("custom-bundle") ? "CUSTOM_BUNDLE_ISSUE" :
                            documentId.includes("customer-return") ? "CUSTOMER_RETURN_RECEIPT" :
                                documentId.includes("stocktake") ? "ADJUSTMENT_OUT" :
                                    documentId.includes("receipt") ? "PURCHASE_RECEIPT" : undefined);
    if (!type) return null;
    const base = stockDocumentCache.find((item) => item.type === type) ?? stockDocumentCache[0];
    if (!base) return null;
    return {
        ...base,
        code: linkedDocumentCodes[documentId] ?? documentId.toUpperCase(),
        id: documentId,
        referenceCode: linkedDocumentCodes[documentId],
        status: "POSTED" as const,
        type
    };
}

export const stockDocumentApi: StockDocumentApi = {
    approveStockDocument(input) {
        return updateStatus(input, "APPROVE", "APPROVED");
    },

    cancelStockDocument(input) {
        return updateStatus(input, "CANCEL", "CANCELLED");
    },

    getStockDocument(documentId) {
        const document = stockDocumentCache.find(
            (item) => item.id === documentId
        );

        const linkedDocument = document ?? synthesizeLinkedDocument(documentId);

        if (!linkedDocument) {
            return Promise.reject(new Error("Không tìm thấy chứng từ kho."));
        }

        return wait({ ...linkedDocument, lines: [...linkedDocument.lines] });
    },

    getStockDocumentOptions() {
        return wait(stockDocumentMockOptions);
    },

    listStockDocuments(
        query: StockDocumentListQuery
    ): Promise<StockDocumentListResult> {
        const filtered = stockDocumentCache
            .filter((document) => textIncludes(document.code, query.documentCode))
            .filter((document) =>
                textIncludes(document.referenceCode, query.referenceCode)
            )
            .filter((document) =>
                query.type === "ALL" ? true : document.type === query.type
            )
            .filter((document) =>
                query.status === "ALL" ? true : document.status === query.status
            )
            .filter((document) => {
                if (query.direction === "ALL") {
                    return true;
                }

                return query.direction === "IN"
                    ? inboundStockDocumentTypes.includes(document.type)
                    : outboundStockDocumentTypes.includes(document.type);
            })
            .filter((document) => {
                if (query.locationId === "ALL") {
                    return true;
                }

                return (
                    document.sourceLocation?.id === query.locationId ||
                    document.destinationLocation?.id === query.locationId
                );
            })
            .filter((document) =>
                query.sourceLocationId === "ALL"
                    ? true
                    : document.sourceLocation?.id === query.sourceLocationId
            )
            .filter((document) =>
                query.destinationLocationId === "ALL"
                    ? true
                    : document.destinationLocation?.id ===
                      query.destinationLocationId
            )
            .filter((document) =>
                query.createdById === "ALL"
                    ? true
                    : document.createdBy.id === query.createdById
            )
            .filter((document) =>
                dateInRange(document.createdAt, query.createdFrom, query.createdTo)
            )
            .filter((document) =>
                dateInRange(document.postedAt, query.postedFrom, query.postedTo)
            )
            .filter((document) => {
                if (query.hasBatch === "ALL") {
                    return true;
                }

                const hasBatch = document.lines.some(
                    (line) => line.allocations.length > 0
                );

                return query.hasBatch === "YES" ? hasBatch : !hasBatch;
            })
            .sort(
                (first, second) =>
                    new Date(second.createdAt).getTime() -
                    new Date(first.createdAt).getTime()
            );

        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size);
        const today = new Date().toISOString().slice(0, 10);
        const currentMonth = new Date().toISOString().slice(0, 7);

        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            summary: {
                postedToday: filtered.filter(
                    (document) =>
                        document.status === "POSTED" &&
                        document.postedAt?.slice(0, 10) === today
                ).length,
                rejected: filtered.filter(
                    (document) => document.status === "REJECTED"
                ).length,
                reversedThisMonth: filtered.filter(
                    (document) =>
                        document.status === "REVERSED" &&
                        document.auditEvents.some(
                            (event) =>
                                event.type === "REVERSE" &&
                                event.happenedAt.slice(0, 7) === currentMonth
                        )
                ).length,
                waitingApproval: filtered.filter(
                    (document) => document.status === "SUBMITTED"
                ).length,
                waitingPost: filtered.filter(
                    (document) => document.status === "APPROVED"
                ).length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    postStockDocument(input) {
        return updateStatus(input, "POST", "POSTED");
    },

    rejectStockDocument(input) {
        return updateStatus(input, "REJECT", "REJECTED");
    },

    reverseStockDocument(input) {
        return updateStatus(input, "REVERSE", "REVERSED");
    },

    submitStockDocument(input) {
        return updateStatus(input, "SUBMIT", "SUBMITTED");
    }
};
