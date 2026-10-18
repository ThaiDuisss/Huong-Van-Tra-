export interface StockAnalysisItem {
  sku: string;
  name: string;
  category: string;
  onHand: number;
  reserved: number;
  blocked: number;
  warehouse: string;
  unit: string;
}

export interface CategoryColorConfig {
  hex: string;
  bg: string;
  text: string;
  border: string;
  dot: string;
}
