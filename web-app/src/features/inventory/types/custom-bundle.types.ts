/** Yêu cầu đóng trà theo yêu cầu bên Inventory (custom_bundle_packings). */
export type CustomBundlePackingStatus = "WAITING_MATERIAL" | "AWAITING_PACKING" | "PACKED" | "CANCELLED";

export interface CustomBundlePackingLine {
    id: string;
    ingredientId: string;
    ingredientCode?: string | null;
    ingredientName?: string | null;
    requiredQuantity: number;
    picked: boolean;
}

export interface CustomBundlePacking {
    id: string;
    packingCode: string;
    orderId: string;
    orderCode?: string | null;
    orderItemId: string;
    bundleName?: string | null;
    branchId?: string | null;
    locationId: string;
    status: CustomBundlePackingStatus;
    expectedTotalWeight: number;
    actualTotalWeight?: number | null;
    packagingType?: string | null;
    numberOfPackages?: number | null;
    packingNote?: string | null;
    expectedAvailableAt?: string | null;
    reservedAt?: string | null;
    packedBy?: string | null;
    packedAt?: string | null;
    issueTransactionCode?: string | null;
    cancelledAt?: string | null;
    cancelReason?: string | null;
    createdAt?: string | null;
    /** Rỗng ở danh sách, đầy đủ ở chi tiết. */
    lines: CustomBundlePackingLine[];
}

export interface CustomBundlePackingPage {
    content: CustomBundlePacking[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface CustomBundlePackingQuery {
    status?: CustomBundlePackingStatus;
    search?: string;
    page: number;
    size: number;
}

export interface ConfirmCustomBundlePackingInput {
    pickedLineIds: string[];
    packagingType?: string;
    numberOfPackages?: number;
    actualTotalWeight?: number;
    note?: string;
}
