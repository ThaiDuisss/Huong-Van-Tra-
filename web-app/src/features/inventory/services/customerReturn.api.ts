import { apiClient } from "../../../api/api-client";

import type {
    CustomerReturnInspection,
    CustomerReturnInspectionListQuery,
    CustomerReturnInspectionListResult
} from "../types/customer-return-inspection.types";
import type { CustomerReturnActualCondition, CustomerReturnQcResult, CustomerReturnQcLocation } from "../types/customer-return-inspection.types";
import { INVENTORY_LOCATION_TYPES } from "../types/inventory.types";
import type { InventoryLocationType } from "../types/inventory.types";
import { inventoryLocationApi } from "./inventoryLocation.api";

export interface PostCustomerReturnInspectionRequest {
    lines?: Array<{
        lineId?: string;
        returnItemId?: string;
        destinationLocationId?: string;
        actualCondition?: CustomerReturnActualCondition;
        result?: CustomerReturnQcResult;
        note?: string;
    }>;
}

export const customerReturnApi = {
    getInspection(inspectionId: string) {
        return apiClient.get<CustomerReturnInspection>(
            `/inventory/customer-return-inspections/${encodeURIComponent(inspectionId)}`
        );
    },

    listInspections(query: CustomerReturnInspectionListQuery) {
        return apiClient.get<CustomerReturnInspectionListResult>(
            "/inventory/customer-return-inspections",
            {
                query: {
                    fromDate: query.fromDate,
                    orderCode: query.orderCode || undefined,
                    page: query.page,
                    returnCode: query.returnCode || undefined,
                    size: query.size,
                    status: query.status === "ALL" ? undefined : query.status,
                    toDate: query.toDate
                }
            }
        );
    },

    postInspection(inspectionId: string, body?: PostCustomerReturnInspectionRequest) {
        return apiClient.post<CustomerReturnInspection, PostCustomerReturnInspectionRequest | undefined>(
            `/inventory/customer-return-inspections/${encodeURIComponent(inspectionId)}/post`,
            body
        );
    }
};

export async function loadCustomerReturnQcLocations(): Promise<CustomerReturnQcLocation[]> {
    const byType = await Promise.all(INVENTORY_LOCATION_TYPES.map(async (type) => {
        const locations: CustomerReturnQcLocation[] = [];
        let page = 0;
        let totalPages: number;
        do {
            const response = await inventoryLocationApi.lookupLocations("", page, 100, { type, status: "ACTIVE" });
            locations.push(...response.content.map((location) => ({ ...location, type })));
            totalPages = response.totalPages;
            page += 1;
        } while (page < totalPages);
        return locations;
    }));
    return byType.flat();
}
