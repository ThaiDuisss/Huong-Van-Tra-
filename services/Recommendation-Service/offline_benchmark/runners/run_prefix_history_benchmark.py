"""Independent globally event-time-causal next-order evaluation, in both exclusion modes."""
import argparse
from collections import Counter
from dataclasses import asdict, replace
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.metadata
import json
from pathlib import Path
import sys

from offline_benchmark.common.manifest import OUTPUTS, artifact_path, build_manifest, digest
from offline_benchmark.data.prefix_history import load_prefix_context
from offline_benchmark.metrics.prefix_history import (
    METRICS, compare_by_history, evaluate_prefix, signal_analysis,
)
from offline_benchmark.models.prefix_history import MODES, predict_cases
from offline_benchmark.split.chronological_split import chronological_split
from offline_benchmark.split.prefix_history import (
    GROUPS, build_prefix_cases, causal_cases, customer_orders, verify_cases,
)


ROOT = Path(__file__).resolve().parents[2]
NEW_OUTPUTS = {"PREFIX_HISTORY_BENCHMARK.md", "prefix_history_benchmark.json"}
NEW_SOURCES = (
    "offline_benchmark/split/prefix_history.py", "offline_benchmark/models/prefix_history.py",
    "offline_benchmark/data/prefix_history.py", "offline_benchmark/metrics/prefix_history.py",
    "offline_benchmark/runners/run_prefix_history_benchmark.py",
    "offline_benchmark/tests/test_prefix_history.py",
)


def protected_file_hashes():
    files = []
    for folder in ("app", "flyway", "scripts", "offline_benchmark"):
        files.extend(path for path in (ROOT/folder).rglob("*") if path.is_file()
                     and "__pycache__" not in path.parts
                     and not (path.parent == OUTPUTS and path.name in NEW_OUTPUTS))
    return {str(path.relative_to(ROOT)).replace("\\", "/"): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(files)}


def verify_canonical(snapshot):
    """Reproduce canonical manifest/rankings in memory; never republish old outputs."""
    manifest_path = OUTPUTS/"split_manifest.json"
    saved_path = OUTPUTS/"routing_comparison.json"
    proof = {"manifest_present": manifest_path.is_file(), "routing_results_present": saved_path.is_file()}
    splits = chronological_split(snapshot.purchases)
    current = build_manifest(snapshot, splits)
    if manifest_path.is_file():
        if json.loads(manifest_path.read_text(encoding="utf-8")) != current:
            raise ValueError("Existing canonical manifest differs from current DB; not overwritten")
        proof["canonical_manifest_reproduced"] = True
    if saved_path.is_file():
        from offline_benchmark.runners.run_routing_comparison import fit_predict
        predictions, _, _, _ = fit_predict(snapshot.products, splits)
        saved = json.loads(saved_path.read_text(encoding="utf-8"))
        for config in saved["configurations"]:
            key = config["configuration"]
            expected = {row["customer_id"]: tuple(row["top5"]) for row in config["customers"]}
            if expected != predictions[key]:
                raise ValueError("Existing canonical routing predictions cannot be reproduced")
        proof["canonical_routing_all_rankings_reproduced"] = True
    return proof


def label_mutation_probes(snapshot, cases, predictions, diagnostics):
    selected = {}
    for case in cases:
        selected.setdefault(case.group, case)
    selected["last"] = cases[-1]
    evidence = []
    for case in selected.values():
        history_ids = {order.order_id for order in case.history}
        changed_events = tuple(replace(e, product_id="future-label-mutation-marker")
                               if e.customer_id == case.customer_id and e.order_id not in history_ids else e
                               for e in snapshot.purchases)
        changed_target = replace(case.target, events=tuple(replace(e, product_id="future-label-mutation-marker")
                                                           for e in case.target.events))
        changed_case = replace(case, target=changed_target)
        changed, changed_diagnostics, _ = predict_cases(replace(snapshot, purchases=changed_events), (changed_case,))
        for mode in MODES:
            for key in predictions[mode]:
                if changed[mode][key][case.case_id] != predictions[mode][key][case.case_id]:
                    raise ValueError("Target/future label mutation changed prediction")
        if changed_diagnostics[case.case_id] != diagnostics[case.case_id]:
            raise ValueError("Target/future label mutation changed component inputs")
        evidence.append({"case_id": case.case_id, "history_orders": case.history_order_count,
                         "mutated": "all target/future product IDs of this customer in source and case target",
                         "both_modes_all_configurations_predictions_unchanged": True})
    return evidence


def case_evidence(cases, diagnostics, catalog):
    result = []
    for case in cases:
        relevant = sorted({e.product_id for e in case.target.events})
        purchased = {e.product_id for e in case.events}
        result.append({"case_id": case.case_id, "customer_id": case.customer_id,
                       "history_order_count": case.history_order_count, "history_group": case.group,
                       "history_order_ids": [o.order_id for o in case.history],
                       "history_event_ids": [e.event_id for e in case.events],
                       "target_order_id": case.target.order_id,
                       "target_event_ids": [e.event_id for e in case.target.events],
                       "target_relevant_product_ids": relevant,
                       "target_relevant_products": len(relevant),
                       "target_products_previously_purchased": len(set(relevant)&purchased),
                       "target_products_outside_active_catalog": len(set(relevant)-catalog),
                       "production_mode_reachable_target_products": len(set(relevant)&(catalog-purchased)),
                       "prediction_time_exclusive": case.prediction_time.isoformat(),
                       "latest_local_history_event_time": max(e.event_time for e in case.events).isoformat(),
                       **diagnostics[case.case_id]})
    return result


def metric_table(configurations, family):
    lines = ["| History orders | Model | Recall@5 | Precision@5 | NDCG@5 | MAP@5 | Coverage@5 | Cases | Customers | Hits | Mean hits | Unique / catalog |",
             "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for group in GROUPS:
        for row in configurations:
            if row["family"] != family:
                continue
            m = row["history_groups"][group]
            if m is None:
                lines.append(f"| {group} | {row['configuration']} | N/A | N/A | N/A | N/A | N/A | 0 | 0 | 0 | N/A | N/A |")
                continue
            lines.append(f"| {group} | {row['configuration']} | {m['recall_at_5']:.2%} | {m['precision_at_5']:.2%} | "
                         f"{m['ndcg_at_5']:.6f} | {m['map_at_5']:.6f} | {m['catalog_coverage_at_5']:.2%} | "
                         f"{m['evaluated_cases']} | {m['unique_customers']} | {m['total_hits']} | "
                         f"{m['mean_hits_per_case']:.4f} | {m['unique_recommended_products']} / {m['recommendable_catalog_size']} |")
    return "\n".join(lines)


def render_report(result):
    d, verification = result["dataset"], result["verification"]
    lines = ["# Prefix-history next-order benchmark — executed database results", "",
             f"Executed: {result['executed_at_asia_saigon']}. `globally_time_causal = true` for interaction events.", "",
             "## A. Audit result", "",
             "PURCHASE is an order-item interaction, persisted with its real `interaction_events.order_id` from "
             "the incoming canonical interaction. Idempotency key: (source_event_id, order_item_id, event_type). "
             "An order can have multiple events/products/variants; quantity is stored but these offline scores count events, not units. "
             "No timestamp-invented order IDs and no use of `customer_interactions` as the historical event source.", "",
             "Content reuses category + active-BOM recipe ingredients with type=1, binary tokens deduplicated across variants; "
             "L2-normalized item vectors and an event-count-weighted normalized prefix profile produce cosine scores. "
             "CF reuses item-item cosine of the causal customer/product PURCHASE-count matrix, zero diagonal, "
             "then prefix-history vector @ similarity. One order can seed several items; signal depends on causal co-purchase support, "
             "not an imposed minimum order threshold. Popularity counts only globally observed PURCHASE events before the cutoff. "
             "RETURN events and net quantities are not used in this comparison.", "",
             "N(s)=(s-min)/(max-min) separately per component/customer over the full current active catalog. "
             "Flat components normalize to zero; weights are not reassigned. A=wc*N(Content)+wf*N(CF), "
             "B=wp*N(Pop)+wc*N(Content)+wf*N(CF). Grid: A 25/75, 50/50, 75/25; "
             "B Pop/Content/CF 10/45/45, 20/40/40, 20/50/30, 30/40/30, 40/40/20. "
             "Top-5 ties use ascending product ID. Exclusion happens AFTER full-catalog normalization.", "",
             "Production remains LightFM/artifact serving with Popularity fallback and purchased-item exclusion. "
             "These candidates are offline alternatives, not measurements of production LightFM. "
             "The existing canonical split uses >=5 orders, oldest floor(4*n/5) for training and pooled global training; "
             "its routing comparison groups PURCHASE event counts and permits repeats. None of those eligibility/grouping rules "
             "is used here. Canonical artifacts and all production code remain unchanged.", "",
             "## B. Prefix evaluation protocol", "",
             "For every customer, group PURCHASE events by real order_id and sort whole orders by "
             "(MIN(event_time), order_id), with events inside orders sorted by (event_time,event_id). "
             "For every k=1..N-1: input exactly O1..Ok, target only O(k+1). History count is DISTINCT order count. "
             "Eligibility is N>=k+1, with no >=5 prefilter. The >=5 group pools every k>=5 prefix, not just k=5. "
             "Ranking metrics are case-weighted, so frequent customers contribute more cases in that group.", "",
             "Prediction cutoff is target MIN(event_time), exclusive. A chronological sweep consumes only events "
             "with event_time < cutoff, across ALL customers, including single-order/non-evaluated customers. "
             "CF is refit once per distinct cutoff; cases sharing a cutoff share that causal fit. "
             "No daily approximation, pooled future data, or target/future customer orders enter training. "
             "Prefix orders overlapping or tied with the target timestamp are explicitly excluded from the causal cohort "
             "because whole-order inputs cannot simultaneously satisfy strict chronology; counts are reported below.", "",
             "Relevant labels remain all distinct products in the next order, including repeats/unavailable products. "
             "Exclusion mode never removes difficult cases or rewrites labels. Precision uses five slots even when fewer "
             "than five candidates remain. NDCG is binary; AP denominator=min(5, relevant count). "
             "Coverage is the recommendation union / current active catalog size, consistently across both modes; "
             "the eligible catalog varies per case in exclusion mode and is saved in case diagnostics.", "",
             "## C. Dataset counts", "",
             f"Database `{d['database']}`: {d['customers_with_purchase_history']} customers with PURCHASE history, "
             f"{d['purchase_orders']} orders, {d['purchase_events']} PURCHASE events, "
             f"{d['all_event_types']} event counts; {d['active_catalog_products']} active products. "
             f"{d['customers_without_next_order']} single-order customers supply global training but no evaluation case. "
             "The Recommendation DB has no complete zero-order customer registry.", "",
             "| History orders | Required total orders | Constructed cases | Evaluated cases | Unique customers | Causal exclusions |",
             "| --- | --- | ---: | ---: | ---: | ---: |"]
    for group, row in d["history_groups"].items():
        requirement = ">=k+1 for every k>=5" if group==">=5" else f">={int(group)+1}"
        lines.append(f"| {group} | {requirement} | {row['constructed_cases']} | {row['evaluated_cases']} | "
                     f"{row['unique_customers']} | {row['causal_exclusions']} |")
        if row["evaluated_cases"]<30:
            lines.append(f"\n**STRONG WARNING: history {group} has fewer than 30 cases; routing evidence is unreliable.**\n")
    for heading, family in (("D. Popularity results", "Popularity"), ("E. Content-Based results", "Content"),
                            ("F. CF results", "CF"), ("G. Candidate A results", "A"), ("H. Candidate B results", "B")):
        lines.extend(["", f"## {heading}", ""])
        for mode in MODES:
            lines.extend([f"### exclude_purchased = {str(result['modes'][mode]['exclude_purchased']).lower()}", "",
                          metric_table(result["modes"][mode]["configurations"], family), ""])
    lines.extend(["## I. Sparse-history signal analysis", "",
                  "Signal is measured from RAW scores over candidates eligible in that mode, before Top-5 selection. "
                  "Positive signal and non-flat signal are distinct: a constant positive component is positive but normalizes to zero. "
                  "A nonzero score is functional evidence, not evidence of predictive usefulness.", ""])
    for mode in MODES:
        lines.extend([f"### {mode}", "", "| History | Component | Cases | Positive signal cases | Positive % | Flat cases | Mean positive candidates | Median positive candidates |",
                      "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"])
        for group in ("1", "2"):
            row = result["sparse_history_signals"][mode][group]
            for component in ("content", "cf"):
                pct = row[f'percentage_with_positive_{component}_signal']
                mean = row[f'mean_positive_{component}_candidates']
                med = row[f'median_positive_{component}_candidates']
                lines.append(f"| {group} | {component} | {row['evaluated_cases']} | "
                             f"{row[f'cases_with_positive_{component}_signal']} | {pct if pct is not None else 'N/A'} | "
                             f"{row[f'cases_with_flat_{component}_scores']} | {mean if mean is not None else 'N/A'} | {med if med is not None else 'N/A'} |")
    lines.extend(["", "## J. A vs B by history level", "",
                  "Best within each family selected by NDCG@5, then MAP@5, then Recall@5, then configuration key. "
                  "The same fixed rule selects the overall winner. Deltas are B minus A; recall/precision/coverage deltas are percentage points. "
                  "Bootstrap resamples paired CUSTOMER clusters and preserves all their cases; 95% percentile intervals. "
                  "Intervals condition on weights selected using this same evaluation data and do not correct selection bias.", ""])
    for mode in MODES:
        lines.extend([f"### {mode}", "", "| History | Best A | Best B | Δ Recall pp | Δ Precision pp | Δ NDCG | Δ MAP | Δ Coverage pp | NDCG 95% CI | Recall 95% CI |",
                      "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |"])
        for group in GROUPS:
            row = result["modes"][mode]["a_vs_b_by_history"][group]
            if row is None:
                lines.append(f"| {group} | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |")
                continue
            delta, ci = row["deltas_b_minus_a"], row["bootstrap"]["intervals_b_minus_a"]
            nci, rci = ci["ndcg_at_5"], ci["recall_at_5"]
            lines.append(f"| {group} | {row['a']} | {row['b']} | {100*delta['recall_at_5']:+.4f} | "
                         f"{100*delta['precision_at_5']:+.4f} | {delta['ndcg_at_5']:+.6f} | {delta['map_at_5']:+.6f} | "
                         f"{100*delta['catalog_coverage_at_5']:+.4f} | [{nci['lower']:+.6f}, {nci['upper']:+.6f}] | "
                         f"[{rci['lower']:+.6f}, {rci['upper']:+.6f}] |")
    lines.extend(["", "## K. Best model by history size", "",
                  "Winner means highest observed NDCG under the fixed selection rule; not a proven optimal routing threshold.", "",
                  "| History | exclude_purchased=false | exclude_purchased=true |",
                  "| --- | --- | --- |",
                  "| 0 | Popularity fallback; not accuracy-evaluated | Popularity fallback; not accuracy-evaluated |"])
    for group in GROUPS:
        winners = [result["modes"][mode]["best_by_history"][group] for mode in MODES]
        lines.append(f"| {group} | " + " | ".join(w['configuration'] if w else 'N/A' for w in winners) + " |")
    lines.extend(["", "## L. Recommended routing", "",
                  "Production-like exploratory routing proposal (exclude_purchased=true), based on the measured winners:", "",
                  "```text", "0 orders -> Popularity (functional fallback, untested accuracy)"])
    for group in GROUPS:
        winner = result["modes"][MODES[1]]["best_by_history"][group]
        lines.append(f"{group} orders -> {winner['configuration'] if winner else 'No evaluated evidence'}")
    lines.extend(["```", "", "Validate this proposal on a separate future window before changing serving. "
                  "No production routing change is made. A family-B winner in a group supports only the tested grid under "
                  "this catalog/cohort/objective; it does not establish one universal weight vector.", "",
                  "Production exclusion feasibility:", "",
                  "| History | Cases | All target products excluded/unavailable | Previously purchased target-item fraction | Mean eligible candidates |",
                  "| --- | ---: | ---: | ---: | ---: |"])
    for group, row in d["target_feasibility"].items():
        lines.append(f"| {group} | {row['cases']} | {row['cases_with_no_reachable_target']} | "
                     f"{row['repeat_target_item_fraction']:.2%} | {row['mean_eligible_candidates']:.2f} |")
    lines.extend(["", "## M. Limitations", "",
                  "- Sample size: case and unique-customer counts above; >=5 contains correlated repeated cases and is case-weighted. "
                  "Bootstrap clusters by customer. Any <30-case group is strongly flagged.",
                  "- Time causality: `globally_time_causal=true` for PURCHASE event timestamps. Strict event_time < target cutoff; "
                  "no pooled future interactions. Historical ingestion/availability time is not reconstructed, so this is event-time causality, "
                  "not proof that every event was already ingested by that wall-clock time.",
                  "- Retrospective catalog metadata: current status/category/BOM/ingredient projections are reused. "
                  "Historical availability and historical recipe changes are unavailable; this is not a fully historical catalog backtest.",
                  "- Test/validation selection bias: all weights and routing winners are chosen on the same evaluation cases. "
                  "No independent validation/test window and no multiple-comparison correction. Paired CIs do not establish unbiased "
                  "statistical significance after selection. A/B CIs do not test the overall winner against every baseline.",
                  "- Exclude-purchased: results are separate. True mode preserves repeat labels, so some targets are impossible to hit. "
                  "No empty-reachable-target cases are removed. Precision denominator remains five; candidate shortage can reduce slots.",
                  "- These are cosine/count offline candidates, not LightFM production accuracy. Zero-order accuracy is unmeasured; "
                  "future-window and business validation are required.", "",
                  "Verification evidence:", "", "```json", json.dumps(verification, indent=2), "```", "",
                  "DB counts and full-row SHA256 fingerprints (all columns, including RETURN/quantities) match before/after "
                  "across every audited table. MySQL confirmed READ ONLY, no commit or ORM flush was performed. "
                  "Reference component scores match the existing offline models exactly at sampled cutoff/group cases; "
                  "all rankings and diagnostics match an independent repeated sweep. Target/future label probes cover "
                  "each available history group and the latest case; structural cutoff checks run for ALL cases.", "",
                  "## N. Files changed", ""])
    lines.extend(f"- `{path}`" for path in NEW_SOURCES)
    lines.extend(["- `offline_benchmark/outputs/PREFIX_HISTORY_BENCHMARK.md`",
                  "- `offline_benchmark/outputs/prefix_history_benchmark.json`", "",
                  "No existing production/model/split/metric source or canonical output is modified.", "",
                  "## O. Commands to reproduce", "", "```powershell",
                  "Set-Location 'E:\\Huong-Van-Tra-\\services\\Recommendation-Service'",
                  "& '.\\.venv\\Scripts\\python.exe' -B -m unittest offline_benchmark.tests.test_prefix_history -v",
                  "& '.\\.venv\\Scripts\\python.exe' -B -m offline_benchmark.runners.run_prefix_history_benchmark --env-file recommend.env --bootstrap-samples 2000",
                  "& '.\\.venv\\Scripts\\python.exe' -B -m unittest discover -s offline_benchmark/tests -v",
                  "```", "", "Use the existing recommend.env and its configured DB/Tailscale endpoint. "
                  "Do not prepare or refresh the canonical split for this independent benchmark. "
                  "Runner fails closed if DB rows, protected sources or canonical outputs change; it never substitutes sample results."])
    return "\n".join(lines)+"\n"


def run(env_file=None, bootstrap_samples=2000):
    protected_before = protected_file_hashes()
    snapshot, before = load_prefix_context(env_file)
    all_cases = build_prefix_cases(snapshot.purchases)
    verify_cases(all_cases, snapshot.purchases)
    cases, rejected = causal_cases(all_cases)
    if not cases:
        raise ValueError("No globally time-causal next-order cases")
    canonical_proof = verify_canonical(snapshot)
    print(f"Prefix dataset: {len(cases)} cases; {len(snapshot.products)} products; no >=5 eligibility", flush=True)
    predictions, diagnostics, references = predict_cases(
        snapshot, cases, progress=lambda done, total, observed: print(
            f"Causal sweep {done}/{total}; globally observed events={observed}", flush=True),
        verify_reference=True)
    print("Verifying an independent repeated sweep...", flush=True)
    repeated, repeated_diagnostics, _ = predict_cases(snapshot, cases)
    if predictions != repeated or diagnostics != repeated_diagnostics:
        raise ValueError("Repeated sweep changed rankings or component diagnostics")
    del repeated, repeated_diagnostics
    mutations = label_mutation_probes(snapshot, cases, predictions, diagnostics)
    catalog = tuple(sorted(product.product_id for product in snapshot.products))
    modes = compare_by_history(evaluate_prefix(cases, predictions, catalog), bootstrap_samples)
    evidence = case_evidence(cases, diagnostics, set(catalog))
    orders = customer_orders(snapshot.purchases)
    groups = {g: {"constructed_cases": sum(c.group==g for c in all_cases),
                  "evaluated_cases": sum(c.group==g for c in cases),
                  "unique_customers": len({c.customer_id for c in cases if c.group==g}),
                  "causal_exclusions": sum(r['group']==g for r in rejected)} for g in GROUPS}
    feasibility = {}
    for group in GROUPS:
        rows = [r for r in evidence if r['history_group']==group]
        total_relevant = sum(r['target_relevant_products'] for r in rows)
        feasibility[group] = {"cases": len(rows),
                              "cases_with_no_reachable_target": sum(r['production_mode_reachable_target_products']==0 for r in rows),
                              "repeat_target_item_fraction": sum(r['target_products_previously_purchased'] for r in rows)/total_relevant if total_relevant else 0,
                              "mean_eligible_candidates": sum(r['modes'][MODES[1]]['candidate_count'] for r in rows)/len(rows) if rows else 0}
    final_snapshot, after = load_prefix_context(env_file)
    if before != after or final_snapshot != snapshot:
        raise ValueError("Database counts/data/catalog changed during evaluation; results not published")
    if protected_file_hashes() != protected_before:
        raise ValueError("Production/source/canonical files changed; results not published")
    verification = {"globally_time_causal": True, "whole_order_prefix_checks_all_cases": True,
                    "strict_global_cutoff_and_exact_local_prefix_checks_all_cases": True,
                    "both_modes_all_grid_repeated_rankings_and_diagnostics_equal": True,
                    "target_future_label_mutation_probes": mutations,
                    "existing_component_exact_reference_checks": references,
                    "db_before_after_counts_and_full_row_hashes_equal": True,
                    "mysql_server_confirmed_read_only_before_after": True,
                    "protected_production_sources_and_canonical_outputs_unchanged": True,
                    **canonical_proof}
    result = {"executed_at_asia_saigon": datetime.now(timezone(timedelta(hours=7))).isoformat(),
              "protocol": "prefix-next-order-event-time-causal-v1", "globally_time_causal": True,
              "history_unit": "COUNT(DISTINCT real order_id)",
              "normalization": "full-active-catalog per-component min-max, flat->0, before exclusion",
              "selection_rule": "NDCG@5 desc, MAP@5 desc, Recall@5 desc, configuration key asc",
              "bootstrap_samples": bootstrap_samples,
              "dataset": {"database": before['database'], "customers_with_purchase_history": len(orders),
                          "purchase_orders": sum(map(len, orders.values())), "purchase_events": len(snapshot.purchases),
                          "all_event_types": before['event_type_counts'], "active_catalog_products": len(catalog),
                          "customers_without_next_order": sum(len(o)==1 for o in orders.values()),
                          "history_groups": groups, "target_feasibility": feasibility,
                          "history_sha256": digest([asdict(e) for e in snapshot.purchases]),
                          "catalog_sha256": digest([asdict(p) for p in snapshot.products]),
                          "cases_sha256": digest([asdict(c) for c in cases]),
                          "causal_excluded_cases": rejected},
              "modes": modes, "sparse_history_signals": signal_analysis(cases, diagnostics),
              "cases": evidence, "db_before": before, "db_after": after,
              "protected_file_sha256": protected_before, "verification": verification,
              "versions": {package: importlib.metadata.version(package) for package in
                           ('numpy', 'scipy', 'SQLAlchemy', 'PyMySQL', 'pydantic-settings')}}
    markdown = render_report(result)
    artifact_path('prefix_history_benchmark.json').write_text(
        json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(',', ':')), encoding='utf-8')
    artifact_path('PREFIX_HISTORY_BENCHMARK.md').write_text(markdown, encoding='utf-8')
    print(json.dumps({mode: {g: row['configuration'] if row else None for g, row in modes[mode]['best_by_history'].items()}
                      for mode in MODES}, indent=2), flush=True)
    print('PASS: causal cutoff, repeated sweep, label mutation, DB fingerprints, canonical preservation', flush=True)
    print('Report: offline_benchmark/outputs/PREFIX_HISTORY_BENCHMARK.md', flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path)
    parser.add_argument('--bootstrap-samples', type=int, default=2000)
    args = parser.parse_args()
    try:
        run(args.env_file, args.bootstrap_samples)
    except Exception as exc:
        message = str(exc) if isinstance(exc, ValueError) else f'{type(exc).__name__}; check DB access/schema and dependencies'
        print(f'Prefix benchmark stopped: {message}', file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
