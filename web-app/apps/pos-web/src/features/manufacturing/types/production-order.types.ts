export type ProductionOrderStatus =
    | "DRAFT"
    | "IN_PROGRESS"
    | "WAITING_MATERIAL"
    | "NEAR_COMPLETION"
    | "COMPLETED"
    | "OVERDUE"
    | "CANCELLED";

export type ProductionWorkshop =
    | "PACKAGING"
    | "MIXING"
    | "GIFT_ASSEMBLY";

export type ProductionOrderDateRange =
    | "ALL"
    | "TODAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "CUSTOM";

export interface ProductionMaterialUsage {
    id: string;
    name: string;
    required: string;
    issued: string;
}

export interface ProductionLogEntry {
    id: string;
    time: string;
    message: string;
    actor: string;
}

export interface ProductionOrderListItem {
    id: string;
    code: string;
    productName: string;
    productSku: string;
    productImageUrl: string;
    bomCode: string;
    plannedQuantity: number;
    completedQuantity: number;
    unit: string;
    startDate: string;
    dueDate: string;
    status: ProductionOrderStatus;
    owner: string;
    workshop: ProductionWorkshop;
    materialUsage: ProductionMaterialUsage[];
    logs: ProductionLogEntry[];
}

export interface ProductionOrderFiltersState {
    statuses: ProductionOrderStatus[];
    workshops: ProductionWorkshop[];
    dateRange: ProductionOrderDateRange;
}
