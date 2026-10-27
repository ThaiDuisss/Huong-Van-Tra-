import type { StockAnalysisItem, CategoryColorConfig } from "../types/stock-analysis.types";

/* ───────────────────────────── Category Color System ───────────────────────────── */

export const CATEGORY_COLOR_MAP: Record<string, CategoryColorConfig> = {
  "Nguyên liệu chính": {
    hex: "#10b981",
    bg: "bg-emerald-50",
    text: "text-emerald-700",
    border: "border-emerald-200",
    dot: "bg-emerald-500",
  },
  "Thảo mộc & Hoa": {
    hex: "#a855f7",
    bg: "bg-purple-50",
    text: "text-purple-700",
    border: "border-purple-200",
    dot: "bg-purple-500",
  },
  "Bao bì & Đóng gói": {
    hex: "#3b82f6",
    bg: "bg-blue-50",
    text: "text-blue-700",
    border: "border-blue-200",
    dot: "bg-blue-500",
  },
  "Tem nhãn": {
    hex: "#f97316",
    bg: "bg-orange-50",
    text: "text-orange-700",
    border: "border-orange-200",
    dot: "bg-orange-500",
  },
  "Phụ liệu": {
    hex: "#ec4899",
    bg: "bg-pink-50",
    text: "text-pink-700",
    border: "border-pink-200",
    dot: "bg-pink-500",
  },
};

export const ALL_CATEGORIES = Object.keys(CATEGORY_COLOR_MAP);

const FALLBACK_COLOR: CategoryColorConfig = {
  hex: "#94a3b8",
  bg: "bg-slate-100",
  text: "text-slate-600",
  border: "border-slate-200",
  dot: "bg-slate-400",
};

export function getCategoryColor(category: string): CategoryColorConfig {
  return CATEGORY_COLOR_MAP[category] ?? FALLBACK_COLOR;
}

/* ───────────────────────────── Mock Inventory Data (15 records) ───────────────────────────── */

export const mockInventoryData: StockAnalysisItem[] = [
  // ── Nguyên liệu chính (4 items) ──
  {
    sku: "NL-OL-01",
    name: "Trà Ô Long Đài Loan",
    category: "Nguyên liệu chính",
    onHand: 128_000,
    reserved: 2_000,
    blocked: 1_000,
    warehouse: "HN-01",
    unit: "kg",
  },
  {
    sku: "NL-TL-02",
    name: "Trà Lài Thái Nguyên",
    category: "Nguyên liệu chính",
    onHand: 95_000,
    reserved: 3_500,
    blocked: 500,
    warehouse: "HN-01",
    unit: "kg",
  },
  {
    sku: "NL-SX-03",
    name: "Trà Sen Xứ Huế",
    category: "Nguyên liệu chính",
    onHand: 72_000,
    reserved: 1_800,
    blocked: 200,
    warehouse: "HCM-01",
    unit: "kg",
  },
  {
    sku: "NL-HM-04",
    name: "Hồng Trà Mộc Premium",
    category: "Nguyên liệu chính",
    onHand: 48_500,
    reserved: 4_200,
    blocked: 800,
    warehouse: "HN-02",
    unit: "kg",
  },

  // ── Thảo mộc & Hoa (3 items) ──
  {
    sku: "TM-CC-01",
    name: "Hoa Cúc La Mã",
    category: "Thảo mộc & Hoa",
    onHand: 35_000,
    reserved: 1_200,
    blocked: 300,
    warehouse: "HN-01",
    unit: "kg",
  },
  {
    sku: "TM-HL-02",
    name: "Hoa Lài Khô Bình Dương",
    category: "Thảo mộc & Hoa",
    onHand: 22_000,
    reserved: 800,
    blocked: 0,
    warehouse: "HCM-01",
    unit: "kg",
  },
  {
    sku: "TM-AH-03",
    name: "Atiso Hoa Đà Lạt",
    category: "Thảo mộc & Hoa",
    onHand: 18_500,
    reserved: 2_500,
    blocked: 400,
    warehouse: "HN-02",
    unit: "kg",
  },

  // ── Bao bì & Đóng gói (3 items) ──
  {
    sku: "BB-HG-01",
    name: "Hộp giấy cao cấp 250g",
    category: "Bao bì & Đóng gói",
    onHand: 140_000,
    reserved: 5_000,
    blocked: 2_000,
    warehouse: "HN-01",
    unit: "hộp",
  },
  {
    sku: "BB-TL-02",
    name: "Túi lọc vải cotton",
    category: "Bao bì & Đóng gói",
    onHand: 85_000,
    reserved: 3_000,
    blocked: 0,
    warehouse: "HCM-01",
    unit: "cái",
  },
  {
    sku: "BB-TN-03",
    name: "Túi niêm phong zipper 500g",
    category: "Bao bì & Đóng gói",
    onHand: 60_000,
    reserved: 1_500,
    blocked: 500,
    warehouse: "HN-02",
    unit: "cái",
  },

  // ── Tem nhãn (2 items) ──
  {
    sku: "TN-OL-01",
    name: "Tem nhãn Ô Long Premium",
    category: "Tem nhãn",
    onHand: 110_000,
    reserved: 4_000,
    blocked: 1_500,
    warehouse: "HN-01",
    unit: "tờ",
  },
  {
    sku: "TN-TL-02",
    name: "Tem nhãn Trà Lài đặc biệt",
    category: "Tem nhãn",
    onHand: 75_000,
    reserved: 2_200,
    blocked: 0,
    warehouse: "HCM-01",
    unit: "tờ",
  },

  // ── Phụ liệu (3 items) ──
  {
    sku: "PL-DT-01",
    name: "Dây thắt nơ lụa đỏ",
    category: "Phụ liệu",
    onHand: 50_000,
    reserved: 1_000,
    blocked: 0,
    warehouse: "HN-01",
    unit: "cuộn",
  },
  {
    sku: "PL-GT-02",
    name: "Giấy tissue gói quà",
    category: "Phụ liệu",
    onHand: 32_000,
    reserved: 500,
    blocked: 200,
    warehouse: "HN-02",
    unit: "tờ",
  },
  {
    sku: "PL-TQ-03",
    name: "Thẻ tag quà tặng",
    category: "Phụ liệu",
    onHand: 25_000,
    reserved: 3_000,
    blocked: 1_000,
    warehouse: "HCM-01",
    unit: "cái",
  },
];
