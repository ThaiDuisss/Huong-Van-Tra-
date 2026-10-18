import { inventoryWorkspaceConfigs } from "../constants/inventory-management.constants";
import type {
    InventoryApprovalStep,
    InventoryBatchOption,
    InventoryLocationOption,
    InventoryManagementRecord,
    InventoryRecordStatus,
    InventorySkuOption,
    InventoryWorkspaceConfig
} from "../types/inventory-management.types";

const now = new Date("2026-08-02T09:00:00+07:00").getTime();

export const inventoryMockLocations: InventoryLocationOption[] = [
    { id: "loc-central", name: "Kho trung tâm", type: "CENTRAL" },
    { id: "loc-branch-1", name: "Chi nhánh Quận 1", type: "BRANCH" },
    { id: "loc-branch-7", name: "Chi nhánh Quận 7", type: "BRANCH" },
    { id: "loc-damaged", name: "Khu cách ly hàng lỗi", type: "DAMAGED" }
];

export const inventoryMockSkus: InventorySkuOption[] = [
    {
        id: "sku-olong",
        name: "Trà ô long rang",
        sku: "TRA-OLONG-250",
        unit: "gói"
    },
    {
        id: "sku-sen",
        name: "Trà sen Tây Hồ",
        sku: "TRA-SEN-100",
        unit: "hộp"
    },
    {
        id: "sku-matcha",
        name: "Bột matcha nguyên chất",
        sku: "NL-MATCHA-01",
        unit: "kg"
    },
    {
        id: "sku-box",
        name: "Hộp giấy quà tặng",
        sku: "BB-HOP-QUA",
        unit: "cái"
    }
];

export const inventoryMockBatches: InventoryBatchOption[] = [
    {
        availableQuantity: 120,
        code: "BATCH-OL-0826",
        expiryDate: "2026-12-20T08:00:00+07:00",
        id: "batch-olong-0826",
        skuId: "sku-olong"
    },
    {
        availableQuantity: 64,
        code: "BATCH-SEN-0726",
        expiryDate: "2026-09-15T08:00:00+07:00",
        id: "batch-sen-0726",
        skuId: "sku-sen"
    },
    {
        availableQuantity: 32,
        code: "BATCH-MT-0626",
        expiryDate: "2026-08-18T08:00:00+07:00",
        id: "batch-matcha-0626",
        skuId: "sku-matcha"
    }
];

const statusCycle: InventoryRecordStatus[] = [
    "DRAFT",
    "PENDING_APPROVAL",
    "APPROVED",
    "IN_PROGRESS",
    "COMPLETED",
    "WARNING",
    "BLOCKED"
];

function createApprovals(status: InventoryRecordStatus): InventoryApprovalStep[] {
    return [
        {
            actor:
                status === "PENDING_APPROVAL" || status === "DRAFT"
                    ? undefined
                    : "Phạm Thu Hương",
            at:
                status === "PENDING_APPROVAL" || status === "DRAFT"
                    ? undefined
                    : "2026-08-01T15:20:00+07:00",
            id: "approval-manager",
            label: "Duyệt quản lý kho",
            role: "MANAGER",
            status:
                status === "REJECTED"
                    ? "REJECTED"
                    : status === "PENDING_APPROVAL" || status === "DRAFT"
                      ? "WAITING"
                      : "APPROVED"
        },
        {
            actor: status === "COMPLETED" ? "Nguyễn Minh Anh" : undefined,
            at: status === "COMPLETED" ? "2026-08-02T08:35:00+07:00" : undefined,
            id: "approval-operator",
            label: "Xác nhận xử lý kho",
            role: "INVENTORY_STAFF",
            status: status === "COMPLETED" ? "APPROVED" : "WAITING"
        }
    ];
}

function recordForConfig(
    config: InventoryWorkspaceConfig,
    index: number
): InventoryManagementRecord {
    const sku = inventoryMockSkus[index % inventoryMockSkus.length];
    const location = inventoryMockLocations[index % inventoryMockLocations.length];
    const targetLocation =
        config.id === "branch-transfers"
            ? inventoryMockLocations[(index + 1) % inventoryMockLocations.length]
            : undefined;
    const status = statusCycle[index % statusCycle.length];
    const updatedAt = new Date(now - index * 4 * 60 * 60 * 1000).toISOString();
    const quantity = 12 + index * 7;

    return {
        activities: [
            {
                actor: "Hệ thống kho",
                at: updatedAt,
                description: "Cập nhật trạng thái và đồng bộ số lượng liên quan.",
                id: `act-${config.id}-${index}-1`,
                title: "Cập nhật chứng từ"
            },
            {
                actor: index % 2 === 0 ? "Phạm Thu Hương" : "Nguyễn Minh Anh",
                at: new Date(now - index * 5 * 60 * 60 * 1000).toISOString(),
                description: "Ghi nhận ghi chú nghiệp vụ và bằng chứng đi kèm.",
                id: `act-${config.id}-${index}-2`,
                title: "Bổ sung hồ sơ"
            }
        ],
        approvals: createApprovals(status),
        batchAllocations: [
            {
                batchCode:
                    inventoryMockBatches[index % inventoryMockBatches.length].code,
                batchId:
                    inventoryMockBatches[index % inventoryMockBatches.length].id,
                expiryDate:
                    inventoryMockBatches[index % inventoryMockBatches.length]
                        .expiryDate,
                quantity: Math.max(1, Math.round(quantity * 0.6))
            }
        ],
        canEdit: status === "DRAFT" || status === "PENDING_APPROVAL",
        code: `${config.documentType ?? "INV"}-${String(index + 1).padStart(
            4,
            "0"
        )}`,
        description: `${config.description} Dòng dữ liệu mẫu số ${index + 1}.`,
        documentType: config.documentType ?? "STOCK_RECEIPT",
        dueAt: new Date(now + (index + 2) * 24 * 60 * 60 * 1000).toISOString(),
        evidenceCount: index % 3,
        id: `${config.id}-${index + 1}`,
        itemName: sku.name,
        locationId: location.id,
        locationName: location.name,
        owner: index % 2 === 0 ? "Nguyễn Minh Anh" : "Trần Bảo Ngọc",
        quantity,
        reasonCode:
            config.id === "waste-handling"
                ? "EXPIRED"
                : config.id === "stock-counts"
                  ? "STOCK_COUNT_DIFF"
                  : config.id === "customer-returns"
                    ? "CUSTOMER_RETURN"
                    : "SUPPLIER_DELIVERY",
        referenceCode: `REF-${String(2600 + index)}`,
        sku: sku.sku,
        status,
        targetLocationName: targetLocation?.name,
        title: `${config.title} ${index + 1}`,
        unit: sku.unit,
        updatedAt,
        value: quantity * (85000 + index * 12500),
        workspaceId: config.id
    };
}

export const inventoryMockRecords: InventoryManagementRecord[] =
    inventoryWorkspaceConfigs.flatMap((config) =>
        Array.from({ length: config.id === "roles" ? 3 : 9 }, (_, index) =>
            recordForConfig(config, index)
        )
    );
