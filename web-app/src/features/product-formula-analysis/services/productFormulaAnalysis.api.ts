import { apiClient } from "../../../api/api-client";
import type { PageResponse } from "../../inventory/types/inventory.types";
import type {
  AnalysisFilters,
  IngredientFrequencyItem,
  IngredientPairItem,
  LookupOption,
  ProductBomRecord,
} from "../types/product-formula-analysis.types";

export interface ProductFormulaReportResponse {
  fromDate: string;
  toDate: string;
  topProducts: ProductBomRecord[];
  items: PageResponse<ProductBomRecord>;
}

export interface IngredientAnalysisReportResponse {
  fromDate: string;
  toDate: string;
  topIngredients: IngredientFrequencyItem[];
  topPairs: IngredientPairItem[];
  items: PageResponse<IngredientFrequencyItem>;
}

function normalizeProductBomRecord(record: ProductBomRecord): ProductBomRecord {
  return {
    ...record,
    quantitySold: Number(record.quantitySold ?? 0),
    orderCount: Number(record.orderCount ?? 0),
    ingredients: (record.ingredients ?? []).map((ing) => ({
      ...ing,
      quantity: Number(ing.quantity ?? 0),
      wasteRate: Number(ing.wasteRate ?? 0),
      ratioPercent: Number(ing.ratioPercent ?? 0),
    })),
  };
}

function normalizeIngredientFrequency(ing: IngredientFrequencyItem): IngredientFrequencyItem {
  return {
    ...ing,
    formulaCount: Number(ing.formulaCount ?? 0),
    weightedSalesVolume: Number(ing.weightedSalesVolume ?? 0),
    averageRatioPercent: Number(ing.averageRatioPercent ?? 0),
  };
}

function buildFilterQuery(filters: AnalysisFilters, page: number, size: number) {
  return Object.fromEntries(
    Object.entries({
      fromDate: filters.fromDate || undefined,
      toDate: filters.toDate || undefined,
      categoryId: filters.categoryId || undefined,
      productId: filters.productId || undefined,
      page,
      size,
    }).filter(([, value]) => value !== undefined && value !== ""),
  );
}

export const productFormulaAnalysisApi = {
  async lookupCategories(search = "", page = 0, size = 20): Promise<PageResponse<LookupOption>> {
    return apiClient.get<PageResponse<LookupOption>>("/catalog/categories/lookup", {
      query: { search, page, size },
    });
  },

  async lookupProducts(search = "", page = 0, size = 20): Promise<PageResponse<LookupOption>> {
    return apiClient.get<PageResponse<LookupOption>>("/catalog/products/lookup", {
      query: { search, page, size },
    });
  },

  async getProductFormulaReport(
    filters: AnalysisFilters,
    page = 0,
    size = 10,
  ): Promise<ProductFormulaReportResponse> {
    const res = await apiClient.get<ProductFormulaReportResponse>(
      "/orders/reports/management/product-formulas",
      { query: buildFilterQuery(filters, page, size) },
    );
    return {
      fromDate: res.fromDate,
      toDate: res.toDate,
      topProducts: (res.topProducts ?? []).map(normalizeProductBomRecord),
      items: {
        ...res.items,
        content: (res.items?.content ?? []).map(normalizeProductBomRecord),
      },
    };
  },

  async getIngredientAnalysisReport(
    filters: AnalysisFilters,
    page = 0,
    size = 10,
  ): Promise<IngredientAnalysisReportResponse> {
    const res = await apiClient.get<IngredientAnalysisReportResponse>(
      "/orders/reports/management/ingredient-analysis",
      { query: buildFilterQuery(filters, page, size) },
    );
    return {
      fromDate: res.fromDate,
      toDate: res.toDate,
      topIngredients: (res.topIngredients ?? []).map(normalizeIngredientFrequency),
      topPairs: (res.topPairs ?? []).map((pair) => ({
        ...pair,
        coOccurrenceCount: Number(pair.coOccurrenceCount ?? 0),
        combinedSalesVolume: Number(pair.combinedSalesVolume ?? 0),
      })),
      items: {
        ...res.items,
        content: (res.items?.content ?? []).map(normalizeIngredientFrequency),
      },
    };
  },
};
