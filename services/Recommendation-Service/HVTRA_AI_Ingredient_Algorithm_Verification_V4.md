# HVTRA AI Ingredient Algorithm Verification V4

**Scope:** Read-only verification of the current serving source on 27 November 2026. Intended for Technical Report V4 Chapters 3, 4, 5.4 and 7.1. Paths below are repository-relative. Constants shown are checked-in defaults. Effective overrides in a running deployment are **UNVERIFIED**.

## 1. Serving entry point and historical evidence

`services/Recommendation-Service/app/api/recommendations.py:166–183`, `recommend_ingredients`, calls `IngredientRecommendationService(get_settings(), get_level2_inputs())`. The route uses the shared-input implementation. Source model version is `hvtra-ingredient-baseline-v1` (`services/Recommendation-Service/app/schemas/ingredient_recommendation.py:30–36`).

**Purchase and return evidence.** `services/Recommendation-Service/app/repositories/customer_order_evidence.py:18–36`, `load_order_evidence`, reads a customer's completed purchase receipts and return lines with both occurrence and observation timestamps at or before the UTC cutoff. It matches the corresponding inbox event and order IDs, completion event type and non-null signing key. `services/Recommendation-Service/app/repositories/ingredient_history_repository.py:70–105`, `retained_from_evidence` and `_retain`, use signed rows in `APPLIED` state. Matching is by order ID, order-item ID, variant ID and purchase type.

```python
returned[(order_id, line_id, variant_id, line_type)] += _quantity(amount)
# Inside add():
quantity = amount - returned.pop((order.id, line_id, variant_id, line_type), Decimal(0))
if quantity < 0 or line_id in seen:
    raise ExploreHistoryError("Invalid retained purchase line")
```

Thus (q_l = q_{purchased,l} - Σ_r q_{returned,r}) for each matching line (l). Unmatched returns, negative residuals and duplicate line evidence fail validation. Fully returned lines contribute zero. The source does not apply a return-reason penalty. The API handles invalid history as HTTP 503.

**Eligibility.** `services/Recommendation-Service/app/service/ingredient_recommendation_service.py:115–128`, `recommend`, counts distinct orders with at least one positive retained PRODUCT line or a positive retained CUSTOM_BUNDLE line with stored components. The threshold is 5 (`services/Recommendation-Service/app/recommendation/taste_ranking_features.py:16`). A PRODUCT receipt can qualify an order before its BOM resolves. Likewise, the eligibility check on bundle components checks presence before mass normalization.

```python
count = len({line.order_id for line in lines if line.quantity > 0
             and (line.purchase_type == "PRODUCT" or line.components)})
```

## 2. Behavioral Score (B)

**Source:** `services/Recommendation-Service/app/service/ingredient_recommendation_service.py:25–76`, `direct_grams`, `recipe_for_line` and `behavioral_profile`. Defaults are defined in `services/Recommendation-Service/app/core/settings.py:37–43`.

```python
age = max(0.0, (cutoff - line.occurred_at).total_seconds() / 86400)
weight = (source_weight * log1p(float(min(line.quantity, settings.ingredient_retained_quantity_cap)))
          * exp2(-age / settings.ingredient_recency_half_life_days))
total = sum(grams.values())
for ingredient, amount in grams.items():
    contribution = weight * float(amount / total)
    if contribution > 0:
        weights[ingredient] += contribution
        evidence[ingredient].add(source)
maximum = max(weights.values(), default=1.0)
return {ingredient: score / maximum for ingredient, score in weights.items()}, evidence
```

- (d_l = max(0, (cutoff - occurredAt_l) / 86400)) in days, with the time difference measured in seconds. (q_l) is retained line quantity in product or bundle units.
- (g_{li}) is ingredient (i)'s mass in grams for line (l). Its share is (h_{li} = g_{li} / Σ_j g_{lj}), using all normalized ingredients in the line, including ingredients outside the retail or in-stock candidate set.
- The default line contribution is `ln(1 + min(q_l, 5)) × 2^(-d_l/60) × h_li × w_l`. The logarithm is natural. The quantity cap is 5 and the half-life is 60 days. The source weight is 1.0 for `PRODUCT_BOM_PROXY` and 1.5 for `DIRECT_CUSTOM_BUNDLE_COMPONENT`.
- For a customer, raw strength (R_i = Σ_l contribution(l,i)). (B_i = R_i / max_j R_j) across that customer's **entire positive behavioral profile**, before stock, retail and selected-ID filtering. A missing ingredient gets (B_i=0) at ranking. Empty history or unusable mass evidence yields an empty profile. Usable mass entries are positive and only positive contributions are stored. The code uses a default maximum of 1.0 for an empty map and performs no division over that empty map.

**Proxy and direct evidence.** PRODUCT history uses the current normalized Catalog recipe for the purchased variant, provided its product ID matches. The service can request history variants missing from the active market listing, but the Catalog recipe endpoint still requires an active, non-deleted commercial variant and product. A missing or mismatched recipe contributes nothing. The evidence source is `PRODUCT_BOM_PROXY`; the response quality label is `CURRENT_BOM_PROXY`. The serving path does not resolve the purchase-time BOM version. See `recipe_for_line` at lines 46–48 and `_with_shared_inputs` at lines 157–177.

CUSTOM_BUNDLE history uses persisted components from the purchase receipt. The projection validates identity and positive quantity and records ingredient ID, code, quantity and unit (`services/Recommendation-Service/app/service/order_inbox_service.py:34–69,118–159`, `_receipt_line`, `_receipt_component`, `_purchase`). The serving code converts `kg`, `g`, `mg`, `µg` and `μg` to grams, case-insensitively, and sums duplicate ingredient IDs. An unsupported unit suppresses the entire line's mass profile. Malformed component evidence raises a history error. For a partial bundle return, the retained **bundle count** changes the logarithmic weight. The component proportions stay those of the stored purchase.

## 3. Pair Score (P) and Catalog BOM Prior (C)

**Source:** `services/Recommendation-Service/app/service/ingredient_recommendation_service.py:79–107`, `build_catalog_signals`, `selected_pair_scores` and `catalog_signals`.

```python
members = sorted(recipe.grams)
frequencies.update(members)
pairs.update(combinations(members, 2))
# After counting all recipes:
peak = max(frequencies.values(), default=1)
prior = {ingredient: count / peak for ingredient, count in frequencies.items()}
# Within the pair loop:
if count >= min_pair_boms:
    score = count / sqrt(frequencies[ingredient] * frequencies[chosen])
    neighbors[chosen][ingredient] = neighbors[ingredient][chosen] = score
# Within the selected-ingredient neighbor loop:
if ingredient not in selected:
    scores[ingredient] = max(scores[ingredient], score)
```

The counting unit is one normalized BOM row per variant. Each ingredient UUID is counted once per row, regardless of mass. Each unordered pair is counted once per row. Different variants with identical compositions still count separately. Both signals use the same BOM corpus.

- **Pair.** (n(i)) and (n(s)) count corpus BOMs containing candidate (i) and selected ingredient (s), respectively. (n(i,s)) counts BOMs containing both. If (n(i,s) ≥ 2) by default, the stored pair score is `n(i,s) / sqrt(n(i) × n(s))`. Otherwise the pair contributes zero. (P_i) is the **maximum** stored score over selected IDs. Missing pairs, unseen IDs and an empty selection yield zero. The code divides only for observed pairs meeting the threshold, where both marginal frequencies are positive.
- **Prior.** (C_i = n(i) / max_j n(j)), using the largest frequency across the entire BOM corpus. The fallback denominator for an empty corpus is 1 and the resulting map is empty. Unseen ingredients receive zero. This is catalog composition frequency. It uses no purchase counts, sales volume, customer frequency or mass weighting.

**BOM corpus and validation.** `services/Recommendation-Service/app/service/level2_serving_inputs.py:151–169`, `ingredient_market` and `_recipes`, list ACTIVE variants of ACTIVE products, excluding `CUSTOM-BUNDLE`, and build the graph from successfully normalized recipe rows. Additional recipe lookups for history do not extend the graph. The Catalog endpoint requires non-deleted active variants and products, excludes custom-bundle variants and requires a valid inspected basis with a positive revision and matching digest. See `services/Catalog-Service/src/main/java/org/hvtra/catalog/controller/InternalBomRecipeController.java:53–75` and `services/Catalog-Service/src/main/java/org/hvtra/catalog/repository/ProductVariantRepository.java:38–44`.

`services/Recommendation-Service/app/clients/bom_catalog_client.py:67–125,128–172`, `_unit_chain`, `normalize_recipe` and `fetch_bom_recipes`, validate the composition hash and variant ID, skip `kind=0`, and normalize active, non-deleted `kind=1` lines to finite positive grams via a shared gram unit root. Duplicate edible ingredient IDs, invalid units and empty edible compositions invalidate the row. Invalid rows are skipped; upstream or batch validation failures propagate as Catalog unavailability. Retail price and warehouse availability do not define this corpus.

## 4. Final ranking, candidate filtering and POS inputs

**Source:** `services/Recommendation-Service/app/service/ingredient_recommendation_service.py:130–179,207–237`, `_with_shared_inputs` and `_rank`. The two weight vectors are configurable and validated to sum to 1 (`services/Recommendation-Service/app/core/settings.py:37–43,104–109`).

```python
if selected:
    bw, pw, cw = cfg.ingredient_selected_weights
else:
    bw, cw = cfg.ingredient_initial_weights
    pw = 0.0
# For each stocked candidate:
b, p, c = behavior.get(ingredient, 0.0), pair.get(ingredient, 0.0), prior.get(ingredient, 0.0)
score = min(1.0, bw * b + pw * p + cw * c)
if score <= 0:
    continue
# After assembling items:
items.sort(key=lambda item: (-item.finalScore, -item.behavioralScore, -item.pairScore, item.ingredientId))
items = items[:limit]
```

Initial ranking is `S = min(1, 0.85B + 0.15C)`. Interactive ranking is `S = min(1, 0.65B + 0.25P + 0.10C)` for any nonempty selected-ID set. Missing signals receive zero without weight redistribution. Positive scores are sorted by descending (S), descending (B), descending (P), then ascending ingredient UUID. The route accepts `limit` 1–10 with default 5. The status is `OK` when a returned Top-K item has positive (B), `FALLBACK_SIMILAR_RETAIL` when returned items have only catalog-derived scores, and `NO_INGREDIENT_EVIDENCE` when the ranker emits no positive item. Earlier gates can return `INSUFFICIENT_VALID_ORDERS`, `NO_AVAILABLE_INGREDIENTS` or `UPSTREAM_UNAVAILABLE`.

| Role | Verified behavior |
|---|---|
| Scoring features | Behavioral strength, BOM co-occurrence and BOM frequency feed the score. |
| Retail gate and operational price | `load_retail_catalog` requires ACTIVE status, type 1, a finite positive retail price, nonblank code and name, and an active Catalog unit with matching symbol. The retail price is returned to POS as operational metadata. `services/Recommendation-Service/app/clients/ingredient_availability_client.py:17–45`. |
| Warehouse gate | `warehouse_ingredient_stock` requires one active Inventory mapping with the same Catalog unit ID. It calls warehouse availability and validates returned identities and quantities. The threshold is `availableQuantity >= 0.001` in the matched unit. Availability is returned as metadata. Inventory resolves an active WAREHOUSE for the supplied branch or the default active WAREHOUSE when branch is absent. `services/Recommendation-Service/app/clients/ingredient_availability_client.py:48–108`; `services/Inventory-Service/src/main/java/org/hvtra/iventoryservice/bundle/service/CustomBundleAvailabilityService.java:84–112`. |
| Selected IDs | `CustomBundleBuilder` takes ingredient IDs from draft lines in AI mode. The hook normalizes the selection and forwards it as `selectedIngredientIds`. The API validates UUIDs, deduplicates them as a set, uses them for pair scoring and excludes them from results. Quantities being edited in the draft are absent from this query. `web-app/src/features/sales/components/custom-bundle/CustomBundleBuilder.tsx:118–128`; `web-app/src/features/product-recommendation/hooks/useIngredientRecommendations.ts:43–47,85–101`; `web-app/src/features/product-recommendation/services/ingredientRecommendation.api.ts:87–109`; `services/Recommendation-Service/app/api/recommendations.py:166–183`. |

Freshness in source uses bounded caches, with default TTLs of 5 seconds for history, 60 seconds for Catalog inputs including retail metadata and price, and 8 seconds for branch-scoped ingredient availability. The stock lookup uses the full retail set before applying selected-ID exclusion. The behavioral profile cache key includes customer ID, retained line and component evidence, and applicable recipe composition digests. With a fixed history, an elapsed interval multiplies all raw strengths by the same decay factor, which cancels under max normalization. See `services/Recommendation-Service/app/core/settings.py:32–35`, `services/Recommendation-Service/app/service/level2_serving_inputs.py:37–51,65–76,129–169` and `services/Recommendation-Service/app/service/ingredient_recommendation_service.py:130–179`.

## 5. Mathematical illustration

Suppose two eligible history lines contribute to ingredients (i) and (j) for a customer who meets the order gate. A PRODUCT line with retained quantity 1, age 0 days and equal mass shares contributes (0.5 ln(2)) to each. A CUSTOM_BUNDLE line with retained quantity 1, age 60 days and shares (1/3) for (i) and (2/3) for (j) contributes (0.25 ln(2)) and (0.5 ln(2)), respectively. If (j) has the largest raw strength, (B_i = 0.75 ln(2) / ln(2) = 0.75).

Suppose (i) and selected ingredient (s) each occur in four BOMs and share two, while the corpus peak is eight. Then (P_i = 2 / sqrt(4 × 4) = 0.50) and (C_i = 4/8 = 0.50). If (i) passes the candidate gates, its initial score is **0.7125** and its interactive score with (s) selected is **0.6625**. These numbers illustrate the equations and represent no evaluation or runtime observation.

## 6. Evidence boundary for Technical Report V4

The report verifies the source algorithm and the two input data paths. Effective production overrides, deployment state, real CUSTOM_BUNDLE coverage, benchmark metrics and the quality of interactive recommendations are **UNVERIFIED** by this audit.
