export type BomStatus =
    | "ACTIVE"
    | "PENDING_APPROVAL"
    | "MATERIAL_SHORTAGE"
    | "INACTIVE";

export interface BomListItem {
    id: string;
    code: string;
    name: string;
    productName: string;
    productSku: string;
    version: string;
    componentCount: number;
    totalCost: number;
    updatedAt: string;
    status: BomStatus;
}