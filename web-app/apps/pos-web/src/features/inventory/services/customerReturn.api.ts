import { apiClient } from "../../../api/api-client";

import type {
    CustomerReturnInspection,
    CustomerReturnInspectionListQuery,
    CustomerReturnInspectionListResult
} from "../types/customer-return-inspection.types";

export interface PostCustomerReturnInspectionRequest {
    lines?: Array<{
        lineId?: string;
        returnItemId?: string;
        destinationLocationId?: string;
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
