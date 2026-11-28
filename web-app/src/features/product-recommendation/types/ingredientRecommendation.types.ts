export type IngredientRecommendationStatus =
  | "OK"
  | "INSUFFICIENT_VALID_ORDERS"
  | "NO_INGREDIENT_EVIDENCE"
  | "NO_AVAILABLE_INGREDIENTS"
  | "FALLBACK_SIMILAR_RETAIL"
  | "UPSTREAM_UNAVAILABLE";

export type IngredientEvidenceSource =
  | "PRODUCT_BOM_PROXY"
  | "DIRECT_CUSTOM_BUNDLE_COMPONENT"
  | "CATALOG_BOM_CO_OCCURRENCE"
  | "CATALOG_BOM_PRIOR";

export interface RecommendedIngredient {
  ingredientId: string;
  ingredientCode: string;
  ingredientName: string;
  unitId: string;
  unitSymbol: string;
  retailPricePerUnit: number;
  availableQuantity: number;
  behavioralScore: number;
  pairScore: number;
  priorScore: number;
  finalScore: number;
  reason: string;
  evidenceSources: IngredientEvidenceSource[];
  sourceQuality?: "CURRENT_BOM_PROXY";
}

export interface IngredientRecommendations {
  customerId: string;
  status: IngredientRecommendationStatus;
  validOrderCount: number;
  modelVersion: string;
  items: RecommendedIngredient[];
  unavailableSource?: "CATALOG" | "INVENTORY";
}
