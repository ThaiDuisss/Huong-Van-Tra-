import { productionCompletionTolerancePercent } from "../constants/production-order.constants";
import {
    finishedSkus,
    materialSkus,
    productionActor,
    productionOrderMockData,
    productionOrderMockOptions
} from "../mocks/production-orders.mock";
import type {
    ProductionActionInput,
    ProductionCompletionInput,
    ProductionOrder,
    ProductionOrderApi,
    ProductionOrderCreateInput,
    ProductionOrderListQuery,
    ProductionOrderListResult,
    ProductionOrderStatus
} from "../types/production-order.types";
import type { InventoryAuditEvent } from "../types/stock-document.types";

const productionCache = [...productionOrderMockData];
const completedOperations = new Set<string>();

function wait<T>(value: T, ms = 430) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function clone(order: ProductionOrder): ProductionOrder {
    return {
        ...order,
        auditEvents: [...order.auditEvents],
        completions: [...order.completions],
        materials: order.materials.map((material) => ({
            ...material,
            allocations: material.allocations.map((allocation) => ({
                ...allocation
            }))
        })),
        outputs: order.outputs.map((output) => ({ ...output }))
    };
}

function findOrder(id: string) {
    const order = productionCache.find((item) => item.id === id);
    if (!order) {
        throw new Error("Không tìm thấy lệnh sản xuất.");
    }
    return order;
}

function audit(
    input: ProductionActionInput | ProductionCompletionInput,
    type: InventoryAuditEvent["type"],
    note?: string,
    reason?: string
): InventoryAuditEvent {
    const actor = productionActor(input.actorRole);
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

function summary(order: ProductionOrder) {
    return {
        code: order.code,
        createdAt: order.createdAt,
        createdBy: order.createdBy,
        dueAt: order.dueAt,
        hasShortage: order.hasShortage,
        id: order.id,
        location: order.location,
        outputs: order.outputs,
        owner: order.owner,
        plannedOutputQuantity: order.outputs.reduce(
            (sum, output) => sum + output.plannedQuantity,
            0
        ),
        priority: order.priority,
        progressPercent: order.progressPercent,
        status: order.status,
        title: order.title
    };
}

function setStatus(
    input: ProductionActionInput,
    status: ProductionOrderStatus,
    note?: string
) {
    const order = findOrder(input.productionOrderId);
    order.status = status;
    order.auditEvents = [audit(input, "APPROVE", note), ...order.auditEvents];
    return wait(clone(order));
}

function buildMaterials(outputQuantity: number) {
    return materialSkus.map((material, index) => {
        const required = index === 0 ? outputQuantity * 0.18 : index === 1 ? outputQuantity * 0.06 : outputQuantity;
        return {
            allocations: [
                {
                    allocatedQuantity: required,
                    availableQuantity: required + 20,
                    batchCode: `BATCH-AUTO-${Date.now()}-${index}`,
                    blocked: false,
                    expiryDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString(),
                    id: `alloc-auto-${Date.now()}-${index}`,
                    locationName: "Kho nguyên liệu A",
                    remainingAfterAllocation: 20
                }
            ],
            availableQuantity: required + 20,
            baseUnit: material.specification.includes("kg") ? "kg" : "cái",
            bomVersionSource: "BOM active",
            consumedBaseQuantity: 0,
            conversionFactor: material.conversionFactor,
            hasAlternative: index !== 2,
            materialProductId: material.id,
            materialProductName: material.name,
            preferredSkuCode: material.skuCode,
            requiredBaseQuantity: required,
            requiredSkuQuantity: required / material.conversionFactor,
            reservedBaseQuantity: 0,
            selectedSkuCode: material.skuCode,
            selectedSkuId: material.id,
            specification: material.specification,
            usedByOutputs: ["Output đã chọn"],
            wasteTolerancePercent: index === 2 ? 2 : 5
        };
    });
}

export const productionOrderApi: ProductionOrderApi = {
    approveProductionOrder(input) {
        return setStatus(input, "RELEASED", "Approve và giữ nguyên liệu.");
    },

    cancelProductionOrder(input) {
        return setStatus(input, "CANCELLED", input.note ?? "Hủy lệnh sản xuất.");
    },

    closeProductionOrderRemainingQuantity(input) {
        return setStatus(
            input,
            "COMPLETED",
            input.note ?? "Đóng phần còn lại của lệnh sản xuất."
        );
    },

    completeProductionOrder(input) {
        try {
            const order = findOrder(input.productionOrderId);
            if (completedOperations.has(input.idempotencyKey)) {
                return wait(clone(order));
            }
            const planned = order.outputs.reduce(
                (sum, output) => sum + output.plannedQuantity,
                0
            );
            const completed = order.outputs.reduce(
                (sum, output) => sum + output.completedQuantity,
                0
            );
            const remaining = planned - completed;
            if (input.goodQuantity + input.rejectedQuantity <= 0) {
                throw new Error("Không thể complete khi mọi output bằng 0.");
            }
            if (input.goodQuantity + input.rejectedQuantity > remaining) {
                throw new Error("Actual good + rejected không được vượt remaining.");
            }
            const reserved = order.materials.reduce(
                (sum, material) =>
                    sum +
                    Math.max(
                        0,
                        material.reservedBaseQuantity -
                            material.consumedBaseQuantity
                    ),
                0
            );
            if (input.actualMaterialConsumption > reserved) {
                throw new Error("Actual material không được vượt reserved.");
            }
            const plannedConsumption = Math.max(1, remaining * 0.2);
            const variancePercent =
                Math.abs(input.actualMaterialConsumption - plannedConsumption) /
                plannedConsumption *
                100;
            if (
                variancePercent > productionCompletionTolerancePercent &&
                !input.varianceReason?.trim()
            ) {
                throw new Error("Hao hụt vượt tolerance phải có lý do.");
            }
            if (
                variancePercent > productionCompletionTolerancePercent * 2 &&
                !["ADMIN", "DIRECTOR"].includes(input.actorRole)
            ) {
                throw new Error("Variance vượt ngưỡng cần ADMIN hoặc DIRECTOR duyệt.");
            }
            if (new Date(input.expiryDate) <= new Date(input.manufacturingDate)) {
                throw new Error("Batch expiry phải sau manufacturing date.");
            }

            const completionNumber = order.completions.length + 1;
            order.completions = [
                ...order.completions,
                {
                    completedAt: new Date().toISOString(),
                    completedBy: productionActor(input.actorRole),
                    completionNumber,
                    expiryDate: input.expiryDate,
                    goodQuantity: input.goodQuantity,
                    id: `completion-${Date.now()}`,
                    manufacturingDate: input.manufacturingDate,
                    materialConsumedQuantity: input.actualMaterialConsumption,
                    newBatchCode: `FG-${order.code}-${completionNumber}`,
                    operationId: input.idempotencyKey,
                    qcNote: input.qcNote,
                    rejectedQuantity: input.rejectedQuantity,
                    wasteQuantity: input.wasteQuantity
                }
            ];
            order.outputs = order.outputs.map((output, index) =>
                index === 0
                    ? {
                          ...output,
                          completedQuantity:
                              output.completedQuantity + input.goodQuantity,
                          rejectedQuantity:
                              output.rejectedQuantity + input.rejectedQuantity
                      }
                    : output
            );
            order.materials = order.materials.map((material) => ({
                ...material,
                consumedBaseQuantity:
                    material.consumedBaseQuantity +
                    input.actualMaterialConsumption / order.materials.length
            }));
            const newCompleted = completed + input.goodQuantity;
            order.progressPercent = Math.min(
                100,
                Math.round((newCompleted / planned) * 100)
            );
            order.status =
                order.progressPercent >= 100 || !input.partial
                    ? "COMPLETED"
                    : "PARTIALLY_COMPLETED";
            if (order.status === "COMPLETED") {
                order.materialIssueDocumentId = `stock-doc-prod-issue-${Date.now()}`;
                order.materialIssueDocumentCode = `PXK-SX-${order.code}`;
                order.finishedReceiptDocumentId = `stock-doc-prod-receipt-${Date.now()}`;
                order.finishedReceiptDocumentCode = `PNK-SX-${order.code}`;
            }
            order.auditEvents = [
                audit(input, "POST", "Complete Production."),
                ...order.auditEvents
            ];
            completedOperations.add(input.idempotencyKey);
            return wait(clone(order));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    createProductionOrder(input: ProductionOrderCreateInput) {
        const location = productionOrderMockOptions.locations.find(
            (item) => item.id === input.locationId
        );
        const owner = productionOrderMockOptions.users.find(
            (item) => item.id === input.ownerId
        );
        if (!location || !owner) {
            return Promise.reject(new Error("Vui lòng chọn kho và người phụ trách."));
        }
        const outputs = input.outputs.map((output) => {
            const sku = finishedSkus.find((item) => item.id === output.finishedSkuId);
            if (!sku?.activeBomVersionId) {
                throw new Error("Không được chọn SKU không có BOM Active.");
            }
            if (output.plannedQuantity <= 0) {
                throw new Error("Planned quantity phải lớn hơn 0.");
            }
            if (new Date(output.plannedExpiryAt) <= new Date()) {
                throw new Error("Planned expiry date không được ở quá khứ.");
            }
            return {
                bomVersionId: sku.activeBomVersionId,
                bomVersionLabel: sku.activeBomVersionLabel ?? "BOM Active",
                completedQuantity: 0,
                finishedSkuId: sku.id,
                packageSize: sku.packageSize,
                plannedExpiryAt: output.plannedExpiryAt,
                plannedQuantity: output.plannedQuantity,
                rejectedQuantity: 0,
                skuCode: sku.skuCode,
                skuName: sku.skuName,
                unit: sku.unit
            };
        });
        const plannedTotal = outputs.reduce(
            (sum, output) => sum + output.plannedQuantity,
            0
        );
        const order: ProductionOrder = {
            auditEvents: [
                audit(
                    { actorRole: "WAREHOUSE", productionOrderId: "new" },
                    "CREATE_DRAFT",
                    input.submit ? "Tạo và submit LSX." : "Lưu draft LSX."
                )
            ],
            code: `LSX-2026-${String(productionCache.length + 1).padStart(5, "0")}`,
            completions: [],
            cost: {
                actualGoodOutput: plannedTotal,
                allocatedOverhead: 0,
                directProductionCost: 0,
                materialCost: 0,
                packagingCost: 0,
                totalProductionCost: 0,
                unitProductionCost: 0
            },
            createdAt: new Date().toISOString(),
            createdBy: productionActor("WAREHOUSE"),
            dueAt: input.dueAt,
            hasShortage: false,
            id: `production-order-${Date.now()}`,
            location,
            materials: buildMaterials(plannedTotal),
            note: input.note,
            outputs,
            owner,
            plannedStartAt: input.plannedStartAt,
            priority: input.priority,
            progressPercent: 0,
            replenishmentReference: input.replenishmentReference,
            status: input.submit ? "PENDING_APPROVAL" : "DRAFT",
            title: input.title
        };
        productionCache.unshift(order);
        return wait(clone(order));
    },

    getProductionOrder(id) {
        try {
            return wait(clone(findOrder(id)));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    getProductionOrderOptions() {
        return wait(productionOrderMockOptions);
    },

    listProductionOrders(query: ProductionOrderListQuery): Promise<ProductionOrderListResult> {
        const filtered = productionCache
            .filter((order) =>
                order.code.toLowerCase().includes(query.code.trim().toLowerCase())
            )
            .filter((order) =>
                query.locationId === "ALL"
                    ? true
                    : order.location.id === query.locationId
            )
            .filter((order) =>
                query.outputSkuId === "ALL"
                    ? true
                    : order.outputs.some(
                          (output) => output.finishedSkuId === query.outputSkuId
                      )
            )
            .filter((order) =>
                query.status === "ALL" ? true : order.status === query.status
            )
            .filter((order) =>
                query.createdById === "ALL"
                    ? true
                    : order.createdBy.id === query.createdById
            )
            .filter((order) =>
                query.hasShortage === "ALL"
                    ? true
                    : query.hasShortage === "YES"
                      ? order.hasShortage
                      : !order.hasShortage
            )
            .sort(
                (first, second) =>
                    new Date(second.createdAt).getTime() -
                    new Date(first.createdAt).getTime()
            );
        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size).map(summary);
        const nowTime = Date.now();
        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            summary: {
                draft: filtered.filter((order) => order.status === "DRAFT").length,
                inProgress: filtered.filter((order) => order.status === "IN_PROGRESS").length,
                overdue: filtered.filter(
                    (order) => order.dueAt && new Date(order.dueAt).getTime() < nowTime && order.status !== "COMPLETED"
                ).length,
                partiallyCompleted: filtered.filter((order) => order.status === "PARTIALLY_COMPLETED").length,
                pendingApproval: filtered.filter((order) => order.status === "PENDING_APPROVAL").length,
                reserved: filtered.filter((order) => order.status === "RELEASED").length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    rejectProductionOrder(input) {
        return setStatus(input, "FAILED", input.note ?? "Reject lệnh sản xuất.");
    },

    releaseProductionOrder(input) {
        const order = findOrder(input.productionOrderId);
        order.materials = order.materials.map((material) => ({
            ...material,
            reservedBaseQuantity: Math.min(
                material.requiredBaseQuantity,
                material.availableQuantity
            )
        }));
        order.status = "RELEASED";
        order.auditEvents = [
            audit(input, "APPROVE", "Release và reservation nguyên liệu."),
            ...order.auditEvents
        ];
        return wait(clone(order));
    },

    startProductionOrder(input) {
        return setStatus(input, "IN_PROGRESS", "Start Production.");
    },

    submitProductionOrder(input) {
        return setStatus(input, "PENDING_APPROVAL", "Submit for Approval.");
    },

    updateProductionOrder(id, input) {
        return productionOrderApi.createProductionOrder(input).then((order) => {
            const index = productionCache.findIndex((item) => item.id === id);
            if (index >= 0) {
                productionCache[index] = { ...order, id };
                return clone(productionCache[index]);
            }
            return order;
        });
    }
};
