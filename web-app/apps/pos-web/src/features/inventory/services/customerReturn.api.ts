import { apiClient } from "../../../api/api-client";

import type {
    CustomerReturnInspection,
    CustomerReturnInspectionListQuery,
    CustomerReturnInspectionListResult
} from "../types/customer-return-inspection.types";

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
                    orderCode: query.orderCode || undefined,
                    page: query.page,
                    returnCode: query.returnCode || undefined,
                    size: query.size,
                    status: query.status === "ALL" ? undefined : query.status
                }
            }
        );
    },

    postInspection(inspectionId: string) {
        return apiClient.post<CustomerReturnInspection>(
            `/inventory/customer-return-inspections/${encodeURIComponent(inspectionId)}/post`
        );
    }
};
