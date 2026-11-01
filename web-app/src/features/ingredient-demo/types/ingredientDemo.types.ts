export type IngredientMode = "PRODUCT" | "CUSTOM_BOM" | "UNIFIED";
export type IngredientPolicy = "DISCOVERY" | "REPEAT";
export type IngredientCohort = "PRODUCT_ONLY" | "BOM_ONLY" | "MIXED" | "NO_HISTORY";

export interface ResearchCustomer {
  customer_id: string;
  cohort: IngredientCohort;
  cohort_as_of: string;
  cold_at_train_cutoff: boolean;
}

export interface ResearchCustomers {
  dataset_id: string;
  source_kind: "SYNTHETIC";
  research_only: true;
  production_approved: false;
  customers: ResearchCustomer[];
}

export interface IngredientQuery {
  customer_id: string;
  as_of: string;
  mode: IngredientMode;
  policy: IngredientPolicy;
  top_k: number;
}

export interface IngredientRecommendation {
  rank: number;
  recommendation_type: "PRODUCT" | "CUSTOM_BOM";
  item_id: string;
  variant_id?: string;
  recipe_id: string;
  recipe_family: string;
  score: number;
  strategy: string;
  reason: string;
  model_version: string;
  ingredient_summary: { ingredient: string; quantity: string; role: string; unit: string }[];
  profile_signals?: { orders: number; product_inferred_orders: number; custom_synthetic_orders: number };
}

export interface IngredientRecommendations {
  customer_id: string;
  dataset_id: string;
  model_version: string;
  source_kind: "SYNTHETIC";
  research_only: true;
  production_approved: false;
  as_of: string;
  mode: IngredientMode;
  policy: IngredientPolicy;
  requested_k: number;
  returned_k: number;
  status: "OK" | "INSUFFICIENT_ELIGIBLE_CANDIDATES";
  fallback: string;
  recommendations: IngredientRecommendation[];
}
