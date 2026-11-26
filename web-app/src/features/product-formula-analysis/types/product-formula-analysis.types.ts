export type AnalysisTab = "overview" | "ingredients" | "ai-recommend";

export interface AnalysisFilters {
  fromDate: string;
  toDate: string;
  categoryId: string;
  productId: string;
}

export interface LookupOption {
  id: string;
  name: string;
  code?: string;
}

export interface BomIngredientRatio {
  ingredientId: string;
  ingredientCode: string;
  ingredientName: string;
  quantity: number;
  unitSymbol: string;
  wasteRate: number;
  ratioPercent: number;
}

export interface ProductBomRecord {
  variantId: string;
  productId: string;
  sku: string;
  productName: string;
  variantName: string;
  categoryName: string;
  formulaName: string;
  quantitySold: number;
  orderCount: number;
  ingredients: BomIngredientRatio[];
}

export interface IngredientFrequencyItem {
  ingredientId: string;
  ingredientCode: string;
  ingredientName: string;
  unitSymbol: string;
  formulaCount: number;
  weightedSalesVolume: number;
  averageRatioPercent: number;
}

export interface IngredientPairItem {
  pairKey: string;
  ingredientA: string;
  ingredientB: string;
  coOccurrenceCount: number;
  combinedSalesVolume: number;
}

export interface QuantityTrendPoint {
  date: string;
  label: string;
  quantity: number;
}

export type ProposalStatus = "NEW" | "SAVED" | "TESTING" | "APPROVED";

export interface AiFormulaProposalIngredient {
  ingredientCode: string;
  ingredientName: string;
  quantity: number;
  unitSymbol: string;
  ratioPercent: number;
  diffNote?: string;
}

export interface AiFormulaProposal {
  id: string;
  code: string;
  proposedName: string;
  baseProductId: string;
  baseProductName: string;
  baseSku: string;
  referenceFormulaName: string;
  reason: string;
  keyDifferences: string[];
  status: ProposalStatus;
  proposedIngredients: AiFormulaProposalIngredient[];
  referenceIngredients: AiFormulaProposalIngredient[];
}
