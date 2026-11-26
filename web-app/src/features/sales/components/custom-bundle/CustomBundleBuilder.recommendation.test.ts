import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { RecommendedBom } from "../../../product-recommendation/services/bomRecommendation.api";
import { CustomBundleBuilder } from "./CustomBundleBuilder";

describe("prefilling the existing bundle builder", () => {
    it("renders the validated composition without submitting a bundle", () => {
        const recommendation: RecommendedBom = {
            rank: 1,
            variantId: "1",
            sku: "fixture",
            compositionRevision: 2,
            compositionSha256: "a".repeat(64),
            ingredientSimilarity: 1,
            tasteStatus: "PREPARATION_UNVERIFIED",
            finalScore: 1,
            matchingIngredients: ["Trà xanh"],
            reason: "Trùng nguyên liệu",
            quantityBasis: { kind: "CATALOG_BOM_ORIGINAL_INPUT", ingredientInputGrams: "12.5" },
            availability: { enough: true, lines: [] },
            bundlePrefill: {
                bundleName: "Công thức thử",
                lines: [{
                    ingredientId: "2",
                    ingredientCode: "fixture",
                    ingredientName: "Trà xanh",
                    quantity: "12.5",
                    unitId: "3",
                    unitSymbol: "g",
                    retailPricePerUnit: "2000",
                }],
            },
        };
        const submit = vi.fn();
        const page = renderToStaticMarkup(
            createElement(CustomBundleBuilder, {
                branchId: null, submitLoading: false, recommendation, onSubmit: submit, onCancel: vi.fn(),
            }),
        );
        expect(page).toContain("Công thức thử");
        expect(page).toContain("Trà xanh");
        expect(page).toContain('value="12.5"');
        expect(page).toContain("g nguyên liệu đầu vào");
        expect(submit).not.toHaveBeenCalled();
    });
});
