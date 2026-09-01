import {
    alertLocations,
    alertSkus,
    balances,
    makeAlertDetail,
    purchaseRequestDrafts,
    reorderPolicies,
    stockAlertUsers,
    stockAlerts
} from "../mocks/stock-alerts.mock";
import { inventoryMasterLocations } from "../mocks/inventory-master-data.mock";
import type {
    ReorderPolicy,
    ReorderPolicyBulkUpdateInput,
    ReorderPolicyQuery,
    ReorderPolicyUpdateInput,
    StockAlert,
    StockAlertActionInput,
    StockAlertApi,
    StockAlertListQuery,
    StockAlertRole,
    StockAlertSeverity,
    StockAlertType,
    SuggestedActionType
} from "../types/stock-alert.types";

function wait<T>(value: T, ms = 260) {
    return new Promise<T>((resolve) => window.setTimeout(() => resolve(value), ms));
}

function user(role: StockAlertRole) {
    return stockAlertUsers[role];
}

function canSeeLocation(role: StockAlertRole, locationId: string) {
    if (role === "ADMIN" || role === "ACCOUNTANT" || role === "DIRECTOR") return true;
    if (role === "INVENTORY_STAFF") return locationId === "loc-central";
    if (role === "BRANCH_MANAGER") return locationId === inventoryMasterLocations.branchCauGiay.id;
    return false;
}

function clonePolicy(policy: ReorderPolicy): ReorderPolicy {
    return { ...policy };
}

function cloneAlert(alert: StockAlert): StockAlert {
    return {
        ...alert,
        actionReference: alert.actionReference ? { ...alert.actionReference } : undefined,
        balance: { ...alert.balance },
        location: { ...alert.location },
        policy: { ...alert.policy },
        sku: { ...alert.sku }
    };
}

function severityFor(type: StockAlertType, available: number, reorderPoint: number): StockAlertSeverity {
    if (type === "OUT_OF_STOCK" || type === "NEGATIVE_AVAILABLE_TO_PROMISE") return "CRITICAL";
    if (available < reorderPoint * 0.5) return "HIGH";
    if (type === "HIGH_RESERVATION") return "MEDIUM";
    return "LOW";
}

function suggestedAction(locationId: string, skuId: string, shortage: number): SuggestedActionType {
    const internalSource = balances.find((item) => item.locationId !== locationId && item.skuId === skuId && item.balance.available > shortage + 20);
    return internalSource ? "TRANSFER" : "PURCHASE";
}

function upsertGeneratedAlert(input: {
    balanceIndex: number;
    explanation: string;
    suggestedQuantity: number;
    type: StockAlertType;
}) {
    const item = balances[input.balanceIndex];
    const policy = reorderPolicies.find((candidate) => candidate.locationId === item.locationId && candidate.skuId === item.skuId);
    const location = alertLocations.find((candidate) => candidate.id === item.locationId);
    const sku = alertSkus.find((candidate) => candidate.id === item.skuId);
    if (!policy || !location || !sku || !policy.enabled) return;

    const existing = stockAlerts.find((alert) => alert.location.id === location.id && alert.sku.id === sku.id && alert.type === input.type && alert.status === "OPEN");
    const now = new Date().toISOString();
    const payload = {
        balance: { ...item.balance },
        calculationExplanation: input.explanation,
        location,
        policy: clonePolicy(policy),
        severity: severityFor(input.type, item.balance.available, policy.reorderPoint),
        sku,
        suggestedAction: suggestedAction(location.id, sku.id, input.suggestedQuantity),
        suggestedQuantity: input.suggestedQuantity,
        updatedAt: now
    };

    if (existing) {
        Object.assign(existing, payload);
        return;
    }

    stockAlerts.unshift({
        ...payload,
        code: `ALT-${String(stockAlerts.length + 1).padStart(4, "0")}`,
        createdAt: now,
        id: `alert-${location.id}-${sku.id}-${input.type}`,
        slaDueAt: new Date(Date.now() + 36 * 60 * 60 * 1000).toISOString(),
        status: "OPEN",
        type: input.type
    });
}

function refreshGeneratedAlerts() {
    balances.forEach((item, index) => {
        const policy = reorderPolicies.find((candidate) => candidate.locationId === item.locationId && candidate.skuId === item.skuId);
        if (!policy?.enabled) return;
        const shortage = Math.max(policy.standardReorderQuantity, policy.reorderPoint - item.balance.available);
        if (item.balance.available <= 0) {
            upsertGeneratedAlert({ balanceIndex: index, explanation: "Available <= 0 nên tạo cảnh báo hết hàng. Tồn vật lý không bị xóa tự động.", suggestedQuantity: shortage, type: "OUT_OF_STOCK" });
        }
        if (item.balance.available < policy.minimumStock) {
            upsertGeneratedAlert({ balanceIndex: index, explanation: `Available ${item.balance.available} thấp hơn minimum stock ${policy.minimumStock}.`, suggestedQuantity: shortage, type: "BELOW_MINIMUM" });
        }
        if (item.balance.available < policy.reorderPoint) {
            const actionType = suggestedAction(item.locationId, item.skuId, shortage) === "TRANSFER" ? "TRANSFER_RECOMMENDED" : "PURCHASE_RECOMMENDED";
            upsertGeneratedAlert({ balanceIndex: index, explanation: `Available ${item.balance.available} thấp hơn reorder point ${policy.reorderPoint}.`, suggestedQuantity: shortage, type: "BELOW_REORDER_POINT" });
            upsertGeneratedAlert({ balanceIndex: index, explanation: "Nguồn bổ sung được đề xuất dựa trên tồn khả dụng sau safety stock ở kho khác.", suggestedQuantity: shortage, type: actionType });
        }
        if (item.balance.availableToPromise < 0) {
            upsertGeneratedAlert({ balanceIndex: index, explanation: "Available to promise âm do reservation/open demand vượt available.", suggestedQuantity: Math.abs(item.balance.availableToPromise), type: "NEGATIVE_AVAILABLE_TO_PROMISE" });
        }
        if (item.balance.onHand > 0 && item.balance.reserved / item.balance.onHand >= 0.5) {
            upsertGeneratedAlert({ balanceIndex: index, explanation: "Reservation chiếm từ 50% tồn vật lý trở lên.", suggestedQuantity: Math.max(1, item.balance.reserved), type: "HIGH_RESERVATION" });
        }
    });
}

function findAlert(alertId: string, role: StockAlertRole) {
    refreshGeneratedAlerts();
    const alert = stockAlerts.find((item) => item.id === alertId);
    if (!alert) throw new Error("Không tìm thấy cảnh báo tồn kho.");
    if (!canSeeLocation(role, alert.location.id)) throw new Error("Vai trò hiện tại không được xem cảnh báo này.");
    return alert;
}

function validatePolicy(input: ReorderPolicyUpdateInput) {
    if (input.minimumStock < 0) throw new Error("Minimum stock phải >= 0.");
    if (input.safetyStock < 0) throw new Error("Safety stock phải >= 0.");
    if (input.reorderPoint < input.minimumStock) throw new Error("Reorder point phải >= minimum stock.");
    if (input.maximumStock !== undefined && input.maximumStock <= input.reorderPoint) throw new Error("Maximum stock phải lớn hơn reorder point.");
    if (input.standardReorderQuantity <= 0) throw new Error("Standard reorder quantity phải lớn hơn 0.");
    if ((input.supplierLeadTimeDays ?? 0) < 0) throw new Error("Lead time phải >= 0.");
    const autoPoint = (input.averageDailyConsumption ?? 0) * (input.supplierLeadTimeDays ?? 0) - input.safetyStock;
    if (Math.round(autoPoint) !== input.reorderPoint && !input.overrideReason?.trim()) throw new Error("Override reorder point bắt buộc nhập lý do.");
}

export const stockAlertApi: StockAlertApi = {
    acknowledgeStockAlert(input: StockAlertActionInput) {
        const alert = findAlert(input.alertId, "ADMIN");
        alert.status = "ACKNOWLEDGED";
        alert.acknowledgedAt = new Date().toISOString();
        alert.acknowledgedBy = user("ADMIN");
        return wait(makeAlertDetail(cloneAlert(alert)));
    },

    bulkUpdateReorderPolicies(input: ReorderPolicyBulkUpdateInput) {
        const policies = reorderPolicies.filter((policy) => {
            const sku = alertSkus.find((item) => item.id === policy.skuId);
            return policy.locationId === input.locationId && sku?.category === input.category;
        });
        if (!input.previewOnly) {
            policies.forEach((policy) => Object.assign(policy, input.patch));
        }
        return wait(policies.map(clonePolicy));
    },

    createPurchaseRequestFromAlert(input: StockAlertActionInput) {
        const alert = findAlert(input.alertId, "ADMIN");
        const id = `purchase-draft-${Date.now()}`;
        const code = `PR-DRAFT-${String(purchaseRequestDrafts.length + 1).padStart(3, "0")}`;
        purchaseRequestDrafts.unshift({ alertId: alert.id, code, id, status: "DRAFT" });
        alert.status = "ACTION_CREATED";
        alert.actionReference = { code, id, status: "DRAFT", type: "PURCHASE_REQUEST" };
        return wait(makeAlertDetail(cloneAlert(alert)));
    },

    createTransferFromAlert(input: StockAlertActionInput) {
        const alert = findAlert(input.alertId, "ADMIN");
        const id = `transfer-draft-${Date.now()}`;
        const code = `TRF-DRAFT-${String(stockAlerts.filter((item) => item.actionReference?.type === "TRANSFER_REQUEST").length + 1).padStart(3, "0")}`;
        const source = balances.find((item) => item.locationId !== alert.location.id && item.skuId === alert.sku.id && item.balance.available > alert.suggestedQuantity + 20);
        const sourceLocationId = source?.locationId ?? inventoryMasterLocations.central.id;
        const url = `/management/inventory/transfers/new?sourceLocationId=${sourceLocationId}&destinationLocationId=${alert.location.id}&skuId=${alert.sku.id}&quantity=${alert.suggestedQuantity}&reason=${encodeURIComponent(`Bổ sung theo cảnh báo ${alert.code}`)}`;
        alert.status = "ACTION_CREATED";
        alert.actionReference = { code, id, status: "DRAFT", type: "TRANSFER_REQUEST", url };
        return wait(makeAlertDetail(cloneAlert(alert)));
    },

    dismissStockAlert(input: StockAlertActionInput) {
        if (!input.reason?.trim()) return Promise.reject(new Error("Dismiss bắt buộc nhập lý do."));
        const alert = findAlert(input.alertId, "ADMIN");
        alert.status = "DISMISSED";
        alert.dismissedAt = new Date().toISOString();
        alert.dismissedReason = input.reason;
        alert.dismissUntil = input.dismissUntil;
        return wait(makeAlertDetail(cloneAlert(alert)));
    },

    getStockAlert(alertId: string, role?: StockAlertRole) {
        return wait(makeAlertDetail(cloneAlert(findAlert(alertId, role ?? "ADMIN"))));
    },

    listReorderPolicies(query: ReorderPolicyQuery) {
        const filtered = reorderPolicies.filter((policy) => {
            const sku = alertSkus.find((item) => item.id === policy.skuId);
            return (query.locationId === "ALL" || policy.locationId === query.locationId) &&
                (!query.skuOrCategory || `${sku?.skuCode} ${sku?.skuName} ${sku?.category}`.toLowerCase().includes(query.skuOrCategory.toLowerCase())) &&
                (query.enabled === "ALL" || (query.enabled === "YES" ? policy.enabled : !policy.enabled));
        });
        return wait(filtered.map(clonePolicy));
    },

    listStockAlerts(query: StockAlertListQuery) {
        refreshGeneratedAlerts();
        const visible = stockAlerts.filter((alert) => canSeeLocation(query.role, alert.location.id));
        const filtered = visible.filter((alert) => (query.type === "ALL" || alert.type === query.type) &&
            (query.severity === "ALL" || alert.severity === query.severity) &&
            (query.locationId === "ALL" || alert.location.id === query.locationId) &&
            (!query.skuOrCategory || `${alert.sku.skuCode} ${alert.sku.skuName} ${alert.sku.category}`.toLowerCase().includes(query.skuOrCategory.toLowerCase())) &&
            (query.status === "ALL" || alert.status === query.status) &&
            (!query.createdFrom || new Date(alert.createdAt) >= new Date(query.createdFrom)) &&
            (query.hasAction === "ALL" || (query.hasAction === "YES" ? Boolean(alert.actionReference) : !alert.actionReference)));
        const start = query.page * query.size;
        return wait({
            content: filtered.slice(start, start + query.size).map(cloneAlert),
            page: query.page,
            size: query.size,
            summary: {
                belowReorderPoint: visible.filter((item) => item.type === "BELOW_REORDER_POINT" && item.status === "OPEN").length,
                critical: visible.filter((item) => item.severity === "CRITICAL" && item.status === "OPEN").length,
                outOfStock: visible.filter((item) => item.type === "OUT_OF_STOCK" && item.status === "OPEN").length,
                overSla: visible.filter((item) => item.status === "OPEN" && new Date(item.slaDueAt) < new Date()).length,
                purchaseNeeded: visible.filter((item) => item.suggestedAction === "PURCHASE" && item.status === "OPEN").length,
                transferNeeded: visible.filter((item) => item.suggestedAction === "TRANSFER" && item.status === "OPEN").length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    resolveStockAlert(input: StockAlertActionInput) {
        const alert = findAlert(input.alertId, "ADMIN");
        if (alert.balance.available <= alert.policy.reorderPoint && !input.adminOverride) {
            return Promise.reject(new Error("Chỉ resolve khi available vượt threshold hoặc ADMIN override."));
        }
        alert.status = "RESOLVED";
        alert.resolvedAt = new Date().toISOString();
        alert.resolvedReason = input.reason ?? "Available đã vượt threshold.";
        return wait(makeAlertDetail(cloneAlert(alert)));
    },

    updateReorderPolicy(input: ReorderPolicyUpdateInput) {
        validatePolicy(input);
        const policy = reorderPolicies.find((item) => item.id === input.policyId);
        if (!policy) return Promise.reject(new Error("Không tìm thấy reorder policy."));
        Object.assign(policy, {
            averageDailyConsumption: input.averageDailyConsumption,
            enabled: input.enabled,
            maximumStock: input.maximumStock,
            minimumStock: input.minimumStock,
            overrideReason: input.overrideReason,
            reorderPoint: input.reorderPoint,
            safetyStock: input.safetyStock,
            standardReorderQuantity: input.standardReorderQuantity,
            supplierLeadTimeDays: input.supplierLeadTimeDays
        });
        return wait(clonePolicy(policy));
    }
};
