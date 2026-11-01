import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";
import type { RoleKey } from "../../../auth/roles";

export type CustomerReturnStatus =
    | "DRAFT"
    | "REQUESTED"
    | "PENDING_MANAGER_REVIEW"
    | "ACCEPTED"
    | "RECEIVED_IN_QUARANTINE"
    | "PENDING_QC"
    | "RESTOCK_APPROVED"
    | "DISPOSAL_REQUIRED"
    | "COMPLETED"
    | "REJECTED"
    | "CANCELLED";

/** Vai trò người thao tác — dùng role chuẩn của hệ thống. */
export type CustomerReturnRole = RoleKey;

export type CustomerReturnReason =
    | "CHANGE_MIND"
    | "WRONG_ITEM"
    | "PACKAGING_DEFECT"
    | "QUALITY"
    | "DAMAGED"
    | "NEAR_EXPIRY"
    | "OTHER";

export type CustomerReturnCondition =
    | "SEALED"
    | "OPENED"
    | "DAMAGED"
    | "WET"
    | "MOULDY"
    | "NEAR_EXPIRY";

export type CustomerReturnResolution = "REFUND" | "EXCHANGE" | "STORE_CREDIT";
export type CustomerReturnRefundStatus =
    | "NOT_REQUESTED"
    | "REQUESTED"
    | "PROCESSING"
    | "REFUNDED"
    | "EXCHANGE_ORDER_CREATED";

export interface ReturnableOrderLine {
    id: string;
    productId: string;
    skuCode: string;
    productName: string;
    unit: string;
    purchasedQuantity: number;
    previouslyReturnedQuantity: number;
    unitPrice: number;
    batchCode?: string;
    expiryDate?: string;
}

export interface ReturnableOrder {
    id: string;
    code: string;
    invoiceCode: string;
    customerName: string;
    customerPhone: string;
    purchasedAt: string;
    branch: InventoryLocationSummary;
    paymentStatus: "PAID" | "PARTIALLY_REFUNDED" | "REFUNDED";
    lines: ReturnableOrderLine[];
}

export interface CustomerReturnLine {
    id: string;
    orderLineId: string;
    productId: string;
    skuCode: string;
    productName: string;
    unit: string;
    purchasedQuantity: number;
    previouslyReturnedQuantity: number;
    requestedQuantity: number;
    acceptedQuantity: number;
    receivedQuantity: number;
    passedQuantity: number;
    failedQuantity: number;
    unitPrice: number;
    reason: CustomerReturnReason;
    condition: CustomerReturnCondition;
    receivedCondition?: CustomerReturnCondition;
    qcCondition?: CustomerReturnCondition;
    batchCode?: string;
    expiryDate?: string;
    unknownBatch: boolean;
    customerNote?: string;
}

export interface CustomerReturnSummary {
    id: string;
    code: string;
    orderCode: string;
    invoiceCode: string;
    customerName: string;
    customerPhone: string;
    branch: InventoryLocationSummary;
    status: CustomerReturnStatus;
    requestedAt: string;
    requestedBy: UserSnapshot;
    totalQuantity: number;
    totalValue: number;
    reason: CustomerReturnReason;
    refundStatus: CustomerReturnRefundStatus;
}

export interface CustomerReturnDetail extends CustomerReturnSummary {
    order: ReturnableOrder;
    desiredResolution: CustomerReturnResolution;
    customerNote?: string;
    evidence: string[];
    managerReview?: {
        reviewedBy: UserSnapshot;
        reviewedAt: string;
        accepted: boolean;
        note?: string;
        rejectReason?: string;
        quantityChangeReason?: string;
    };
    receiving?: {
        receivedBy: UserSnapshot;
        receivedAt: string;
        note?: string;
    };
    qc?: {
        inspectedBy: UserSnapshot;
        inspectedAt: string;
        remainingShelfLifeDays: number;
        sealStatus: "SEALED" | "BROKEN" | "UNKNOWN";
        note?: string;
        evidence?: string;
    };
    paymentReference?: string;
    exchangeOrderCode?: string;
    stockDocumentId?: string;
    stockDocumentCode?: string;
    disposalRequestId?: string;
    disposalRequestCode?: string;
    auditEvents: InventoryAuditEvent[];
    lines: CustomerReturnLine[];
}

export interface CustomerReturnListQuery {
    page: number;
    size: number;
    tab:
        | "MANAGER_REVIEW"
        | "AWAITING_RECEIVE"
        | "PENDING_QC"
        | "RESTOCK"
        | "DISPOSAL"
        | "COMPLETED";
    code: string;
    orderCode: string;
    customer: string;
    branchId: "ALL" | string;
    status: "ALL" | CustomerReturnStatus;
    reason: "ALL" | CustomerReturnReason;
    requestedFrom: string;
    requestedTo: string;
}

export interface CustomerReturnListResult {
    content: CustomerReturnSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface CustomerReturnOptions {
    branches: InventoryLocationSummary[];
    users: UserSnapshot[];
}

export interface CreateCustomerReturnInput {
    orderId: string;
    desiredResolution: CustomerReturnResolution;
    customerNote?: string;
    evidence: string[];
    lines: Array<{
        orderLineId: string;
        quantity: number;
        reason: CustomerReturnReason;
        condition: CustomerReturnCondition;
        customerNote?: string;
    }>;
}

export interface CustomerReturnActionInput {
    returnId: string;
    note?: string;
}

export interface ReviewCustomerReturnInput extends CustomerReturnActionInput {
    accepted: boolean;
    rejectReason?: string;
    quantityChangeReason?: string;
    lines: Array<{ lineId: string; acceptedQuantity: number }>;
}

export interface ReceiveCustomerReturnInput extends CustomerReturnActionInput {
    lines: Array<{
        lineId: string;
        receivedQuantity: number;
        receivedCondition: CustomerReturnCondition;
    }>;
}

export interface InspectCustomerReturnInput extends CustomerReturnActionInput {
    remainingShelfLifeDays: number;
    sealStatus: "SEALED" | "BROKEN" | "UNKNOWN";
    evidence?: string;
    lines: Array<{
        lineId: string;
        passedQuantity: number;
        failedQuantity: number;
        qcCondition: CustomerReturnCondition;
    }>;
}

export interface CustomerReturnApi {
    searchReturnableOrders(keyword: string): Promise<ReturnableOrder[]>;
    createCustomerReturn(input: CreateCustomerReturnInput): Promise<CustomerReturnDetail>;
    getCustomerReturn(returnId: string): Promise<CustomerReturnDetail>;
    listCustomerReturns(query: CustomerReturnListQuery): Promise<CustomerReturnListResult>;
    reviewCustomerReturn(input: ReviewCustomerReturnInput): Promise<CustomerReturnDetail>;
    receiveCustomerReturn(input: ReceiveCustomerReturnInput): Promise<CustomerReturnDetail>;
    inspectCustomerReturn(input: InspectCustomerReturnInput): Promise<CustomerReturnDetail>;
    approveCustomerReturnRestock(input: CustomerReturnActionInput): Promise<CustomerReturnDetail>;
    requestCustomerReturnDisposal(input: CustomerReturnActionInput): Promise<CustomerReturnDetail>;
    cancelCustomerReturn(input: CustomerReturnActionInput): Promise<CustomerReturnDetail>;
    getCustomerReturnOptions(): Promise<CustomerReturnOptions>;
}

export type AppRole = CustomerReturnRole;
