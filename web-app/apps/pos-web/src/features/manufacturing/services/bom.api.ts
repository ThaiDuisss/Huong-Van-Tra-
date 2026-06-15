import { apiClient } from "../../../api/api-client";
import type { BomListItem } from "../types/bom.types";

apiClient.get<BomListItem[]>("/catalog/boms");
