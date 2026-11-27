import { defaultBundleQuantity } from "../../services/posCustomBundle.api";

export type WeightUnit = "g" | "kg";

export interface ParsedWeightResult {
  ok: true;
  enteredQuantity: number;
  enteredUnit: WeightUnit | null;
  quantityKg: number | null;
  baseQuantity: number;
  lineTotal: number;
}

export interface ParsedWeightError {
  ok: false;
  error: string;
}

export type WeightParseOutcome = ParsedWeightResult | ParsedWeightError;

const DECIMAL_TOKEN = /^([+-]?\d+(?:[.,]\d+)?)\s*([a-zA-ZÀ-ỹ]+)?$/;

export function isWeightUnit(unitSymbol?: string | null): WeightUnit | null {
  const normalized = unitSymbol?.trim().toLowerCase();
  if (normalized === "g" || normalized === "kg") {
    return normalized;
  }
  return null;
}

export function formatKgPriceMeta(
  retailPricePerUnit: number,
  unitSymbol?: string | null,
): { price: number; unitLabel: string; isKgNormalized: boolean } {
  const weightUnit = isWeightUnit(unitSymbol);
  if (weightUnit === "g") {
    return {
      price: Math.round(retailPricePerUnit * 1000 * 100) / 100,
      unitLabel: "kg",
      isKgNormalized: true,
    };
  }
  if (weightUnit === "kg") {
    return {
      price: retailPricePerUnit,
      unitLabel: "kg",
      isKgNormalized: true,
    };
  }
  return {
    price: retailPricePerUnit,
    unitLabel: unitSymbol?.trim() || "đơn vị",
    isKgNormalized: false,
  };
}

/** Chuyển số thập phân sang (integerMantissa, scale) để nhân/chia không dính sai số float IEEE-754. */
function parseDecimalParts(token: string): { mantissa: number; scale: number } | null {
  const normalized = token.replace(",", ".");
  if (normalized.startsWith("-")) {
    return null;
  }
  const unsigned = normalized.startsWith("+") ? normalized.slice(1) : normalized;
  if (!/^\d+(?:\.\d+)?$/.test(unsigned)) {
    return null;
  }
  const [whole, frac = ""] = unsigned.split(".");
  if (frac.length > 6) {
    return null;
  }
  const digits = `${whole}${frac}`;
  const mantissa = Number(digits);
  if (!Number.isSafeInteger(mantissa)) {
    return null;
  }
  return { mantissa, scale: frac.length };
}

function fromScaled(mantissa: number, scale: number): number {
  if (scale <= 0) return mantissa * 10 ** Math.abs(scale);
  return Number((mantissa / 10 ** scale).toFixed(scale));
}

function formatStockNumber(quantity: number): string {
  return quantity.toLocaleString("vi-VN", { maximumFractionDigits: 3 });
}

export function defaultFlexibleWeightText(unitSymbol?: string | null): string {
  const weightUnit = isWeightUnit(unitSymbol);
  if (weightUnit === "g") return "10g";
  if (weightUnit === "kg") return "0.1kg";
  return String(defaultBundleQuantity(unitSymbol));
}

export function addFlexibleWeightStep(currentText: string, unitSymbol?: string | null): string {
  const weightUnit = isWeightUnit(unitSymbol);
  if (!weightUnit) {
    const current = Number(currentText.trim().replace(",", ".") || 0);
    const step = defaultBundleQuantity(unitSymbol);
    const next = (Number.isFinite(current) && current > 0 ? current : 0) + step;
    return String(Number(next.toFixed(3)));
  }

  const parsed = parseFlexibleWeightInput(currentText, unitSymbol);
  if (!parsed.ok) {
    return defaultFlexibleWeightText(unitSymbol);
  }
  if (parsed.enteredUnit === "kg") {
    const nextKg = Math.round((parsed.enteredQuantity + 0.1) * 1000) / 1000;
    return `${nextKg}kg`;
  }
  const nextG = Math.round((parsed.enteredQuantity + 10) * 1000) / 1000;
  return `${nextG}g`;
}

/**
 * Parse và validate khối lượng linh hoạt (10g, 20g, 100g, 0.5kg, 0,5kg, 1kg, kèm khoảng trắng)
 * cho chế độ AI gợi ý nguyên liệu; tự quy đổi về đơn vị gốc của Catalog/Inventory (scale <= 3).
 */
export function parseFlexibleWeightInput(
  rawInput: string,
  baseUnitSymbol?: string | null,
  retailPricePerUnit = 0,
  availableQuantity?: number | null,
): WeightParseOutcome {
  const trimmed = rawInput.trim();
  if (!trimmed) {
    return { ok: false, error: "Vui lòng nhập khối lượng (vd: 10g, 0.5kg)." };
  }

  const match = DECIMAL_TOKEN.exec(trimmed);
  if (!match) {
    return { ok: false, error: "Định dạng không hợp lệ. Nhập số kèm đơn vị g hoặc kg (vd: 10g, 0.5kg)." };
  }

  const [, numPart, rawUnit] = match;
  if (numPart.startsWith("-")) {
    return { ok: false, error: "Khối lượng không được là số âm." };
  }

  const parts = parseDecimalParts(numPart);
  if (!parts) {
    return { ok: false, error: "Khối lượng không hợp lệ." };
  }
  if (parts.mantissa === 0) {
    return { ok: false, error: "Khối lượng phải lớn hơn 0." };
  }

  const enteredQuantity = fromScaled(parts.mantissa, parts.scale);
  const baseWeightUnit = isWeightUnit(baseUnitSymbol);

  if (!baseWeightUnit) {
    if (rawUnit && rawUnit.trim().toLowerCase() !== baseUnitSymbol?.trim().toLowerCase()) {
      return { ok: false, error: `Đơn vị không được hỗ trợ cho nguyên liệu này (${baseUnitSymbol ?? "đơn vị"}).` };
    }
    if (parts.scale > 3) {
      return { ok: false, error: "Số lượng chỉ hỗ trợ tối đa 3 chữ số thập phân." };
    }
    if (availableQuantity != null && enteredQuantity > availableQuantity) {
      return {
        ok: false,
        error: `Vượt tồn kho (${formatStockNumber(availableQuantity)} ${baseUnitSymbol ?? ""}).`,
      };
    }
    const lineTotal = Math.round(enteredQuantity * retailPricePerUnit * 100) / 100;
    return {
      ok: true,
      enteredQuantity,
      enteredUnit: null,
      quantityKg: null,
      baseQuantity: enteredQuantity,
      lineTotal,
    };
  }

  if (!rawUnit) {
    return { ok: false, error: "Vui lòng nhập kèm đơn vị g hoặc kg (vd: 10g, 0.5kg)." };
  }

  const enteredUnitLower = rawUnit.trim().toLowerCase();
  if (enteredUnitLower !== "g" && enteredUnitLower !== "kg") {
    return { ok: false, error: "Đơn vị không được hỗ trợ. Chỉ hỗ trợ g hoặc kg." };
  }
  const enteredUnit: WeightUnit = enteredUnitLower;

  // Quy đổi chính xác sang kg và về đơn vị gốc của nguyên liệu
  const kgScale = enteredUnit === "kg" ? parts.scale : parts.scale + 3;
  const baseScale =
    enteredUnit === baseWeightUnit
      ? parts.scale
      : enteredUnit === "kg"
        ? parts.scale - 3
        : parts.scale + 3;

  if (baseScale > 3) {
    return {
      ok: false,
      error:
        baseWeightUnit === "kg"
          ? "Nguyên liệu lưu theo kg tối thiểu 1g (0.001kg) và tối đa 3 số thập phân."
          : "Khối lượng quy đổi chỉ hỗ trợ tối đa 3 chữ số thập phân.",
    };
  }

  const quantityKg = fromScaled(parts.mantissa, kgScale);
  const baseQuantity = fromScaled(parts.mantissa, baseScale);

  if (!Number.isFinite(baseQuantity) || baseQuantity <= 0) {
    return { ok: false, error: "Khối lượng phải lớn hơn 0." };
  }

  if (availableQuantity != null && baseQuantity > availableQuantity) {
    return {
      ok: false,
      error: `Vượt tồn kho hiện có (${formatStockNumber(availableQuantity)} ${baseUnitSymbol ?? baseWeightUnit}).`,
    };
  }

  const pricePerKg = baseWeightUnit === "g" ? retailPricePerUnit * 1000 : retailPricePerUnit;
  const lineTotal = Math.round((parts.mantissa * pricePerKg) / 10 ** kgScale * 100) / 100;

  return {
    ok: true,
    enteredQuantity,
    enteredUnit,
    quantityKg,
    baseQuantity,
    lineTotal,
  };
}
