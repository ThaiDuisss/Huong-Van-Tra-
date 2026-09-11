export type ReturnStatus = "CREATED" | "RECEIVED" | "COMPLETED" | "CANCELLED";

export type ReturnReason =
    | "DEFECTIVE"
    | "DAMAGED"
    | "WRONG_ITEM"
    | "QUALITY_ISSUE"
    | "EXPIRED"
    | "CHANGE_OF_MIND"
    | "OTHER";

export type ReturnCondition =
    | "SEALED"
    | "OPENED"
    | "DAMAGED"
    | "SPOILED"
    | "USED";

export interface CreateOrderReturnItemPayload {
    orderItemId: string;
    quantity: number;
    reason: ReturnReason;
    condition: ReturnCondition;
    note?: string | null;
}

export interface CreateOrderReturnPayload {
    items: CreateOrderReturnItemPayload[];
    note?: string | null;
}

export interface OrderReturnItemResponse {
    id?: string | null;
    orderItemId?: string | null;
    quantity?: number | string | null;
    reason?: ReturnReason | null;
    condition?: ReturnCondition | null;
    note?: string | null;
    skuSnapshot?: string | null;
    productNameSnapshot?: string | null;
    variantNameSnapshot?: string | null;
}

export interface OrderReturnResponse {
    id: string;
    returnCode?: string | null;
    orderId?: string | null;
    status?: ReturnStatus | null;
    note?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnSummaryResponse {
    id: string;
    returnCode?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    status?: ReturnStatus | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    itemCount?: number | string | null;
    customerName?: string | null;
    customerPhone?: string | null;
}

export interface OrderReturnListRow extends OrderReturnSummaryResponse {
    note?: string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnQueryParams {
    fromDate?: string;
    keyword?: string;
    status?: ReturnStatus;
    toDate?: string;
    page?: number;
    size?: number;
    sort?: string;
}
