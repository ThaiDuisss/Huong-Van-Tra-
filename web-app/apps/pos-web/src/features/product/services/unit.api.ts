import { apiClient } from "../../../api/api-client";
import type { Unit } from "../types/product.types";

export const unitApi = {
    list() {
        return apiClient.get<Unit[]>("/catalog/units");
    }
};
