import type { SaleProductPreview } from "../../sales/types/sale.types";

export type RecommendationMode = "DISCOVERY" | "REPEAT";
export interface ProductRecommendation {
  rank: number;
  productId: string;
  productCode: string;
  name: string;
  score: number;
}
export interface ProductRecommendations {
  customerId: string | null;
  source: "MODEL" | "HYBRID" | "POPULARITY" | "FALLBACK";
  modelType?: string | null;
  fallbackReason?: string | null;
  items: ProductRecommendation[];
}
export interface RecommendedProductView {
  recommendation: ProductRecommendation;
  variants: SaleProductPreview[];
  error?: string;
}
