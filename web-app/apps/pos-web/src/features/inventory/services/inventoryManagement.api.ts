import {
    inventoryMockBatches,
    inventoryMockLocations,
    inventoryMockRecords,
    inventoryMockSkus
} from "../mocks/inventory-management.mock";
import type {
    InventoryActionInput,
    InventoryCreateDraftInput,
    InventoryManagementApi,
    InventoryManagementListResult,
    InventoryManagementQuery,
    InventoryManagementRecord,
    InventorySortField,
    InventoryWorkspaceId
} from "../types/inventory-management.types";

const records = [...inventoryMockRecords];

function wait<T>(value: T) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), 260);
    });
}

function compareByField(
    first: InventoryManagementRecord,
    second: InventoryManagementRecord,
    sortBy: InventorySortField
) {
    if (sortBy === "quantity") {
        return first.quantity - second.quantity;
    }

    if (sortBy === "value") {
        return first.value - second.value;
    }

    if (sortBy === "code") {
        return first.code.localeCompare(second.code, "vi");
    }

    return (
        new Date(first.updatedAt).getTime() -
        new Date(second.updatedAt).getTime()
    );
}

export const inventoryManagementApi: InventoryManagementApi = {
    createDraft(input: InventoryCreateDraftInput) {
        const sku = inventoryMockSkus.find((item) => item.id === input.skuId);
        const location = inventoryMockLocations.find(
            (item) => item.id === input.locationId
        );
        const record: InventoryManagementRecord = {
            activities: [
                {
                    actor: "Người dùng mô phỏng",
                    at: new Date().toISOString(),
                    description: input.note || "Tạo nháp chứng từ kho.",
                    id: `act-${Date.now()}`,
                    title: "Tạo mới"
                }
            ],
            approvals: [
                {
                    id: `approval-${Date.now()}`,
                    label: "Chờ quản lý kho duyệt",
                    role: "INVENTORY_MANAGER",
                    status: "WAITING"
                }
            ],
            batchAllocations: [],
            canEdit: true,
            code: `INV-DRAFT-${String(records.length + 1).padStart(4, "0")}`,
            description: input.note || "Chứng từ nháp từ giao diện mock.",
            documentType: "STOCK_RECEIPT",
            evidenceCount: 0,
            id: `draft-${Date.now()}`,
            itemName: sku?.name ?? "Mặt hàng chưa xác định",
            locationId: location?.id ?? input.locationId,
            locationName: location?.name ?? "Kho chưa xác định",
            owner: "Người dùng mô phỏng",
            quantity: input.quantity,
            reasonCode: input.reasonCode,
            referenceCode: "MOCK-DRAFT",
            sku: sku?.sku ?? input.skuId,
            status: "DRAFT",
            title: input.title,
            unit: input.unit,
            updatedAt: new Date().toISOString(),
            value: input.quantity * 100000,
            workspaceId: input.workspaceId
        };

        records.unshift(record);
        return wait(record);
    },

    getOptions() {
        return wait({
            batches: inventoryMockBatches,
            locations: inventoryMockLocations,
            skus: inventoryMockSkus
        });
    },

    list(
        workspaceId: InventoryWorkspaceId,
        query: InventoryManagementQuery
    ): Promise<InventoryManagementListResult> {
        const normalizedSearch = query.search.trim().toLowerCase();
        const filtered = records
            .filter((record) => record.workspaceId === workspaceId)
            .filter((record) =>
                query.status === "ALL"
                    ? true
                    : record.status === query.status
            )
            .filter((record) =>
                query.locationId === "ALL"
                    ? true
                    : record.locationId === query.locationId
            )
            .filter((record) => {
                if (!normalizedSearch) {
                    return true;
                }

                return [
                    record.code,
                    record.title,
                    record.sku,
                    record.itemName,
                    record.locationName,
                    record.referenceCode ?? ""
                ]
                    .join(" ")
                    .toLowerCase()
                    .includes(normalizedSearch);
            })
            .sort((first, second) => {
                const value = compareByField(first, second, query.sortBy);
                return query.sortDirection === "asc" ? value : -value;
            });

        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size);
        const totalValue = filtered.reduce(
            (sum, record) => sum + record.value,
            0
        );
        const totalQuantity = filtered.reduce(
            (sum, record) => sum + record.quantity,
            0
        );

        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            summary: {
                pendingApproval: filtered.filter(
                    (record) => record.status === "PENDING_APPROVAL"
                ).length,
                totalQuantity,
                totalRecords: filtered.length,
                totalValue,
                warningRecords: filtered.filter(
                    (record) =>
                        record.status === "WARNING" ||
                        record.status === "BLOCKED"
                ).length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    runAction(input: InventoryActionInput) {
        const record = records.find(
            (item) => String(item.id) === String(input.recordId)
        );

        if (!record) {
            return Promise.reject(new Error("Không tìm thấy chứng từ kho."));
        }

        const nextStatusByAction = {
            APPROVE: "APPROVED",
            CANCEL: "CANCELLED",
            COMPLETE: "COMPLETED",
            CREATE: record.status,
            EXPORT: record.status
        } as const;

        record.status = nextStatusByAction[input.action];
        record.updatedAt = new Date().toISOString();
        record.activities = [
            {
                actor: input.role ? `Vai trò ${input.role}` : "Quản trị viên",
                at: record.updatedAt,
                description: input.note || "Thao tác được ghi nhận từ mock API.",
                id: `act-${Date.now()}`,
                title: `Thực hiện ${input.action}`
            },
            ...record.activities
        ];

        return wait(record);
    }
};
