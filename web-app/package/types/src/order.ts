export interface OrderItem {
    productVariantId: string;
    productName: string;
    quantity: number;
    unitPrice: number;
    discountAmount: number;
    totalAmount: number;
}

export interface Order {
    id: string;
    code: string;
    status: OrderStatus;
    items: OrderItem[];
    subtotal: number;
    discountAmount: number;
    totalAmount: number;
}

export type OrderStatus =
    | "DRAFT"
    | "PENDING_PAYMENT"
    | "PAID"
    | "COMPLETED"
    | "CANCELLED";