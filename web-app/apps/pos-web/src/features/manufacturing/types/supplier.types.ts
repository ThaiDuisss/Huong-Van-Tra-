export type SupplierStatus = "ACTIVE" | "INACTIVE";

export interface IngredientLookup {
    id: string;
    ingredientCode: string;
    name: string;
    unitName?: string | null;
    unitSymbol?: string | null;
    status: SupplierStatus;
}

export interface SupplierResponse {
    id: string;
    supplierCode: string;
    name: string;
    contactName?: string | null;
    phone?: string | null;
    email?: string | null;
    address?: string | null;
    taxCode?: string | null;
    notes?: string | null;
    status: SupplierStatus;
    ingredients: IngredientLookup[];
    createdAt: string;
    updatedAt?: string | null;
}

export interface SupplierRequest {
    name: string;
    contactName?: string;
    phone?: string;
    email?: string;
    address?: string;
    taxCode?: string;
    notes?: string;
    status?: SupplierStatus;
    ingredientIds?: string[];
}

export interface SupplierFilterParams {
    page?: number;
    size?: number;
    search?: string;
    status?: string;
    ingredientId?: string;
}
