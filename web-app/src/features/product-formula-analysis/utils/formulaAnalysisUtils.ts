import * as XLSX from "xlsx-js-style";

import type {
  AiFormulaProposal,
  AnalysisFilters,
  IngredientFrequencyItem,
  IngredientPairItem,
  ProductBomRecord,
} from "../types/product-formula-analysis.types";

export function computeIngredientFrequencies(
  records: ProductBomRecord[],
): IngredientFrequencyItem[] {
  const map = new Map<
    string,
    {
      ingredientId: string;
      ingredientCode: string;
      ingredientName: string;
      unitSymbol: string;
      formulaCount: number;
      weightedSalesVolume: number;
      ratioSum: number;
    }
  >();

  for (const record of records) {
    for (const ing of record.ingredients) {
      const existing = map.get(ing.ingredientId) ?? {
        ingredientId: ing.ingredientId,
        ingredientCode: ing.ingredientCode,
        ingredientName: ing.ingredientName,
        unitSymbol: ing.unitSymbol,
        formulaCount: 0,
        weightedSalesVolume: 0,
        ratioSum: 0,
      };
      existing.formulaCount += 1;
      existing.weightedSalesVolume += record.quantitySold;
      existing.ratioSum += ing.ratioPercent;
      map.set(ing.ingredientId, existing);
    }
  }

  return Array.from(map.values())
    .map((item) => ({
      ingredientId: item.ingredientId,
      ingredientCode: item.ingredientCode,
      ingredientName: item.ingredientName,
      unitSymbol: item.unitSymbol,
      formulaCount: item.formulaCount,
      weightedSalesVolume: item.weightedSalesVolume,
      averageRatioPercent:
        item.formulaCount > 0 ? Math.round((item.ratioSum / item.formulaCount) * 10) / 10 : 0,
    }))
    .sort((a, b) => b.weightedSalesVolume - a.weightedSalesVolume || b.formulaCount - a.formulaCount);
}

export function computeIngredientPairs(records: ProductBomRecord[]): IngredientPairItem[] {
  const pairMap = new Map<string, IngredientPairItem>();

  for (const record of records) {
    const uniqueIngs = Array.from(
      new Map(record.ingredients.map((i) => [i.ingredientId, i])).values(),
    ).sort((a, b) => a.ingredientName.localeCompare(b.ingredientName));

    for (let i = 0; i < uniqueIngs.length; i++) {
      for (let j = i + 1; j < uniqueIngs.length; j++) {
        const a = uniqueIngs[i];
        const b = uniqueIngs[j];
        const pairKey = `${a.ingredientId}__${b.ingredientId}`;
        const existing = pairMap.get(pairKey) ?? {
          pairKey,
          ingredientA: `${a.ingredientName} (${a.ingredientCode})`,
          ingredientB: `${b.ingredientName} (${b.ingredientCode})`,
          coOccurrenceCount: 0,
          combinedSalesVolume: 0,
        };
        existing.coOccurrenceCount += 1;
        existing.combinedSalesVolume += record.quantitySold;
        pairMap.set(pairKey, existing);
      }
    }
  }

  return Array.from(pairMap.values())
    .sort(
      (a, b) =>
        b.combinedSalesVolume - a.combinedSalesVolume ||
        b.coOccurrenceCount - a.coOccurrenceCount,
    )
    .slice(0, 12);
}

export function buildAiFormulaProposals(
  records: ProductBomRecord[],
  topIngredients: IngredientFrequencyItem[],
): AiFormulaProposal[] {
  if (records.length === 0) {
    return [];
  }

  const topRecords = records.slice(0, 3);
  const popularAddon = topIngredients.find((ing) => ing.formulaCount >= 1);

  return topRecords.map((record, index) => {
    const referenceIngredients = record.ingredients.map((ing) => ({
      ingredientCode: ing.ingredientCode,
      ingredientName: ing.ingredientName,
      quantity: ing.quantity,
      unitSymbol: ing.unitSymbol,
      ratioPercent: ing.ratioPercent,
    }));

    const proposedIngredients = record.ingredients.map((ing, ingIdx) => {
      const adjustedQty =
        ingIdx === 0
          ? Math.round(ing.quantity * 0.92 * 10) / 10
          : Math.round(ing.quantity * 1.08 * 10) / 10;
      return {
        ingredientCode: ing.ingredientCode,
        ingredientName: ing.ingredientName,
        quantity: adjustedQty,
        unitSymbol: ing.unitSymbol,
        ratioPercent: ing.ratioPercent,
        diffNote:
          ingIdx === 0
            ? "Giảm nhẹ 8% nền nguyên liệu chính để cân bằng hương vị"
            : "Tăng 8% thành phần hương phụ theo nhóm công thức bán chạy",
      };
    });

    const missingPopular =
      popularAddon &&
      !record.ingredients.some((i) => i.ingredientId === popularAddon.ingredientId)
        ? popularAddon
        : null;

    if (missingPopular) {
      const baseTotal = proposedIngredients.reduce((s, item) => s + item.quantity, 0);
      const addonQty = Math.max(2, Math.round(baseTotal * 0.05 * 10) / 10);
      proposedIngredients.push({
        ingredientCode: missingPopular.ingredientCode,
        ingredientName: missingPopular.ingredientName,
        quantity: addonQty,
        unitSymbol: missingPopular.unitSymbol,
        ratioPercent: 5,
        diffNote: `Bổ sung từ nhóm nguyên liệu phổ biến (${missingPopular.formulaCount} công thức bán chạy)`,
      });
    }

    const totalProposedQty = proposedIngredients.reduce((s, i) => s + i.quantity, 0);
    const normalizedProposed = proposedIngredients.map((item) => ({
      ...item,
      ratioPercent:
        totalProposedQty > 0 ? Math.round((item.quantity / totalProposedQty) * 1000) / 10 : 0,
    }));

    const differences = [
      `Điều chỉnh tỷ lệ định mức dựa trên ${record.productName} (${record.quantitySold} sản phẩm bán ra).`,
      missingPopular
        ? `Kết hợp thêm ${missingPopular.ingredientName} (${missingPopular.ingredientCode}) đang phổ biến ở các BOM bán chạy.`
        : "Tối ưu lại tỷ lệ phân bổ giữa nguyên liệu nền và nguyên liệu tạo hương.",
    ];

    return {
      id: `ai-prop-${record.variantId}`,
      code: `AI-CT-00${index + 1}`,
      proposedName: `${record.productName} - Công thức Tối ưu ${index + 1}`,
      baseProductId: record.productId,
      baseProductName: `${record.productName} (${record.variantName})`,
      baseSku: record.sku,
      referenceFormulaName: record.formulaName,
      reason: `Sản phẩm đạt sản lượng bán ${record.quantitySold} đơn vị. AI kết hợp dữ liệu sản lượng bán và tần suất xuất hiện của nguyên liệu trong các công thức bán chạy để đề xuất phiên bản phối trộn mới.`,
      keyDifferences: differences,
      status: "NEW",
      proposedIngredients: normalizedProposed,
      referenceIngredients,
    };
  });
}

function styleHeaderRow(sheet: XLSX.WorkSheet, colCount: number) {
  for (let col = 0; col < colCount; col++) {
    const cellRef = XLSX.utils.encode_cell({ r: 0, c: col });
    if (sheet[cellRef]) {
      sheet[cellRef].s = {
        font: { bold: true, color: { rgb: "FFFFFF" } },
        fill: { fgColor: { rgb: "1F5B33" } },
        alignment: { horizontal: "center", vertical: "center" },
      };
    }
  }
}

function appendSheet(
  workbook: XLSX.WorkBook,
  name: string,
  rows: Array<Record<string, string | number>>,
) {
  const safeRows = rows.length > 0 ? rows : [{ "Thông báo": "Không có dữ liệu phù hợp bộ lọc" }];
  const sheet = XLSX.utils.json_to_sheet(safeRows);
  const colCount = Object.keys(safeRows[0] ?? {}).length;
  styleHeaderRow(sheet, colCount);
  sheet["!cols"] = Array.from({ length: colCount }, () => ({ wch: 24 }));
  XLSX.utils.book_append_sheet(workbook, sheet, name.slice(0, 31));
}

export function exportProductFormulaExcel(params: {
  filters: AnalysisFilters;
  records: ProductBomRecord[];
  ingredients: IngredientFrequencyItem[];
  pairs: IngredientPairItem[];
}) {
  const workbook = XLSX.utils.book_new();

  appendSheet(
    workbook,
    "1. Sản phẩm & BOM",
    params.records.map((r, index) => ({
      "STT": index + 1,
      "SKU": r.sku,
      "Sản phẩm": `${r.productName} - ${r.variantName}`,
      "Nhóm sản phẩm": r.categoryName,
      "Công thức BOM": r.formulaName,
      "Số nguyên liệu": r.ingredients.length,
      "Sản lượng bán": r.quantitySold,
    })),
  );

  appendSheet(
    workbook,
    "2. Tần suất nguyên liệu",
    params.ingredients.map((ing, index) => ({
      "STT": index + 1,
      "Mã NL": ing.ingredientCode,
      "Tên nguyên liệu": ing.ingredientName,
      "Số công thức dùng": ing.formulaCount,
      "Tỷ lệ TB (%)": ing.averageRatioPercent,
      "Sản lượng gắn kết": ing.weightedSalesVolume,
    })),
  );

  appendSheet(
    workbook,
    "3. Cặp nguyên liệu",
    params.pairs.map((p, index) => ({
      "STT": index + 1,
      "Nguyên liệu 1": p.ingredientA,
      "Nguyên liệu 2": p.ingredientB,
      "Số công thức kết hợp": p.coOccurrenceCount,
      "Tổng sản lượng bán": p.combinedSalesVolume,
    })),
  );

  const recommendHeaders = [
    "Mã đề xuất",
    "Tên công thức đề xuất",
    "Sản phẩm tham chiếu",
    "BOM tham chiếu",
    "Trạng thái",
    "Lý do AI đề xuất",
  ];
  const emptyRecommendSheet = XLSX.utils.aoa_to_sheet([recommendHeaders]);
  styleHeaderRow(emptyRecommendSheet, recommendHeaders.length);
  emptyRecommendSheet["!cols"] = recommendHeaders.map(() => ({ wch: 24 }));
  XLSX.utils.book_append_sheet(workbook, emptyRecommendSheet, "4. AI Đề xuất công thức");

  const fileName = `phan-tich-san-pham-cong-thuc-${params.filters.fromDate || "all"}-${params.filters.toDate || "all"}.xlsx`;
  XLSX.writeFile(workbook, fileName);
}
