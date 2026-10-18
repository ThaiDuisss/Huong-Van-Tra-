"""Run exact causal TF-IDF evaluation into a NEW directory under repo/.tmp."""
import argparse
from collections import Counter
from dataclasses import asdict, replace
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

from app.recommendation.v1_model import (
    CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT, PurchaseCount, build_artifact,
)
from offline_benchmark.common.manifest import build_manifest, digest
from offline_benchmark.data.exact_tfidf import acquire, payload, replay
from offline_benchmark.metrics.exact_tfidf import (
    CURRENT, comparisons, metric_rows, policies, policy_rows, select, summarize, windows,
)
from offline_benchmark.metrics.prefix_history import aggregate
from offline_benchmark.models.exact_tfidf import CONTRACT, MODELS, MODES, CausalTfidf, predict, rankings
from offline_benchmark.split.chronological_split import chronological_split
from offline_benchmark.split.prefix_history import build_prefix_cases, causal_cases, customer_orders, verify_cases


SERVICE = Path(__file__).resolve().parents[2]
REPO = SERVICE.parents[1]


def protected_hashes():
    return {p.relative_to(SERVICE).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(SERVICE.rglob('*')) if p.is_file()
            and '.venv' not in p.parts and '__pycache__' not in p.parts and p.suffix != '.pyc'}


def reserve_output(path):
    path = path.resolve()
    temporary = (REPO / '.tmp').resolve()
    if path == temporary or not path.is_relative_to(temporary):
        raise ValueError('Output must be a new task directory under repository/.tmp')
    path.mkdir(parents=True, exist_ok=False)
    (path / '.gitignore').write_text('*\n', encoding='ascii')
    return path


def write(path, value):
    # Never replace existing outputs, including within the task directory.
    with path.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, default=str)


def verify_history(snapshot, cases, historical):
    proof = {'cases': len(cases), 'whole_order_local_prefix': True, 'zero_order_cohort': 'UNAVAILABLE'}
    manifest = SERVICE / 'offline_benchmark/outputs/split_manifest.json'
    if manifest.is_file():
        proof['canonical_manifest_matches'] = json.loads(manifest.read_text(encoding='utf-8-sig')) == build_manifest(snapshot, chronological_split(snapshot.purchases))
    if not historical.is_file():
        proof['historical_prefix_present'] = False
        return proof, {}
    saved = json.loads(historical.read_text(encoding='utf-8-sig'))
    existing = {r['case_id']: r for r in saved['cases']}
    proof['historical_case_ids_match'] = set(existing) == {c.case_id for c in cases}
    proof['historical_prefix_present'] = True
    if not proof['historical_case_ids_match']:
        raise ValueError('Current prefix cases differ from historical snapshot; do not compare different datasets')
    for c in cases:
        r = existing[c.case_id]
        if (r['history_order_ids'] != [o.order_id for o in c.history]
                or r['target_relevant_product_ids'] != sorted({e.product_id for e in c.target.events})
                or r['prediction_time_exclusive'] != c.prediction_time.isoformat()):
            raise ValueError('Historical labels/prefix/cutoffs differ')
    comparison = {}
    for mode, oldmode in zip(MODES, ('exclude_purchased_false', 'exclude_purchased_true')):
        comparison[mode] = {}
        for config in saved['modes'][oldmode]['configurations']:
            if config['configuration'] not in ('Popularity', 'Content', 'CF', 'A_50_50'):
                continue
            rows = config['case_results']
            comparison[mode][config['configuration']] = {
                'overall': aggregate(rows, len(snapshot.products)),
                'groups': {g: aggregate([r for r in rows if r['history_group'] in labels], len(snapshot.products))
                    for g, labels in (('1', {'1'}), ('2-4', {'2', '3', '4'}), ('>=5', {'>=5'}))}}
    return proof, comparison


def regression_probes(products, snapshot, cases):
    chosen = {}
    for c in cases:
        chosen.setdefault(c.group, c)
    chosen['last'] = cases[-1]
    results = []
    for case in chosen.values():
        # Independently rebuild the production artifact from complete causal orders.
        orders = customer_orders(snapshot.purchases)
        counts = Counter((u, e.product_id) for u, group in orders.items() for order in group
                         if max(e.event_time for e in order.events) < case.prediction_time for e in order.events)
        reference = build_artifact(products, tuple(PurchaseCount(u, p, n) for (u, p), n in sorted(counts.items())))
        state = CausalTfidf(products, snapshot.purchases)
        actual = state.advance(case.prediction_time)
        history = Counter(e.product_id for e in case.events)
        if actual.components(history) != reference.components(history) or not (actual.popularity == reference.popularity).all():
            raise ValueError('Optimized CF/content differs from production full builder')
        original, evidence, _ = predict(products, snapshot.purchases, (case,))
        # Mutate target/future labels for ALL customers. Order timestamps/IDs remain stable.
        changed_events = tuple(replace(e, product_id='future-marker') if e.event_time >= case.prediction_time else e
                               for e in snapshot.purchases)
        changed_target = replace(case.target, events=tuple(replace(e, product_id='future-marker') for e in case.target.events))
        changed_case = replace(case, target=changed_target)
        changed, changed_evidence, _ = predict(products, changed_events, (changed_case,))
        if evidence != changed_evidence:
            raise ValueError('Future labels alter earlier state')
        for mode in MODES:
            for model in MODELS:
                if original[mode][model][0][2] != changed[mode][model][0][2]:
                    raise ValueError('Future labels alter earlier ranking')
        reversed_result, reversed_evidence, _ = predict(products, tuple(reversed(snapshot.purchases)), (case,))
        if reversed_evidence != evidence or any(
                reversed_result[mode][model][0][2] != original[mode][model][0][2]
                for mode in MODES for model in MODELS):
            raise ValueError('Input ordering changes deterministic predictions')
        results.append({'history_orders': case.history_order_count, 'production_builder_exact_parity': True,
            'all_customer_future_label_mutation_unchanged': True, 'reversed_input_unchanged': True})
    return results


def table(lines, summary, title, keys):
    lines.extend(['', title, '', '| Window/mode | Model | History | R@5 % | P@5 % | NDCG | MAP | Cov % | Cases/customers | Hits | Unreachable | Short | Flat C/CF | Fallback |',
                  '|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|'])
    for window, mode, models in summary:
        for name in keys:
            s = models[name]
            for group in ('overall', '1', '2-4', '>=5'):
                m = s['overall'] if group == 'overall' else s['groups'][group]
                if m is None:
                    lines.append(f'| {window}/{mode} | {name} | {group} | N/A | N/A | N/A | N/A | N/A | 0/0 | 0 | N/A | N/A | N/A | N/A |')
                    continue
                lines.append(f"| {window}/{mode} | {name} | {group} | {100*m['recall_at_5']:.2f} | {100*m['precision_at_5']:.2f} | {m['ndcg_at_5']:.4f} | {m['map_at_5']:.4f} | {100*m['catalog_coverage_at_5']:.2f} | {m['evaluated_cases']}/{m['unique_customers']} | {m['total_hits']} | {m['unreachable_target_cases']} | {m['candidate_shortage_cases']} | {m['content_flat_cases']}/{m['cf_flat_cases']} | {m['fallback_cases']} |")


def report(data):
    lines = ['# Exact TF-IDF causal benchmark', '',
        '**Offline evidence only; production routing/artifacts/API remain unchanged.**', '',
        f"Dataset: {data['dataset']['purchase_events']} PURCHASE events, {data['dataset']['customers']} customers with purchase history, {data['dataset']['orders']} orders, {data['dataset']['cases']} accepted next-order cases, {data['dataset']['catalog_size']} current active products.",
        '0-order registered cohort is UNAVAILABLE: no accuracy result is invented. Finer groups 2/3/4/5–9/≥10 are in results.json.', '',
        'Whole-order local prefixes and global training; an order enters global counts only when its LAST line event_time is strictly before target MIN(event_time). Guest/RETURN events are not model inputs. Counts are lines, not units. No full-history NPZ is loaded.', '',
        f"Windows: {json.dumps(data['windows'], ensure_ascii=False)}", '',
        'Five adapters: causal named-purchase Popularity; exact V1 name/category/type=1 active-BOM TF-IDF Content; item-item cosine CF; Hybrid50; Hybrid60. Features built once by production builder; CF uses identical sparse operations and sampled full-builder parity checks. Per-component min-max AFTER purchased exclusion; no redistribution if one component is flat. Content/CF-only flat → Popularity; both hybrid components flat → Popularity; empty candidates return empty without fallback. Top-5 score descending/product ID ascending.', '',
        'Labels and existing Precision/Recall/NDCG/MAP/Coverage definitions are unchanged. Precision denominator is always five. Unreachable targets remain in all metrics. Repeat and discovery are separate tasks; scores are not probabilities.', '',
        'Validation selection uses NDCG then MAP/Recall/lexical tie order. Only validation metrics enter selection. frozen-policy.json is written and hashed BEFORE final-test prediction/metrics. Rolling training observes past complete orders in the test window; policy and weights stay frozen, as online updating would require.', '']
    for mode in MODES:
        winners = data['frozen']['modes'][mode]
        lines.append(f"**{mode} — frozen choices:** " + '; '.join(f"{k}={v['key']}" for k, v in winners.items()))
        for name in ('overall', 'three'):
            ci = data['uncertainty'][mode][name]['overall']
            if ci:
                interval = ci['intervals_b_minus_a']['ndcg_at_5']
                lines.append(f"{name} vs current, final-test ΔNDCG {interval['point_estimate']:+.5f}, 95% customer-cluster CI [{interval['lower']:+.5f}, {interval['upper']:+.5f}], {ci['customer_clusters']} clusters.")
        for group, ci in data['uncertainty'][mode]['content_minus_popularity'].items():
            if ci:
                r = ci['intervals_b_minus_a']['ndcg_at_5']
                lines.append(f"Content vs Popularity, group {group}: ΔNDCG {r['point_estimate']:+.5f}, CI [{r['lower']:+.5f}, {r['upper']:+.5f}], clusters={ci['customer_clusters']}.")
        w = data['uncertainty'][mode]['weight_60_minus_50']
        if w:
            r = w['intervals_b_minus_a']['ndcg_at_5']
            lines.append(f"Hybrid60 vs Hybrid50: ΔNDCG {r['point_estimate']:+.5f}, CI [{r['lower']:+.5f}, {r['upper']:+.5f}].")
        three_simple = data['uncertainty'][mode]['three_minus_simple']
        if three_simple:
            r = three_simple['intervals_b_minus_a']['ndcg_at_5']
            lines.append(f"Frozen three-stage vs frozen simpler candidate: ΔNDCG {r['point_estimate']:+.5f}, CI [{r['lower']:+.5f}, {r['upper']:+.5f}].")
        lines.append('')
    table(lines, [(w, mode, data['summaries'][w][mode]) for w in ('validation', 'test') for mode in MODES],
          '## Validation and final test — five exact model adapters', MODELS)
    table(lines, [('test', mode, data['policy_test'][mode]) for mode in MODES],
          '## Final test — current policy and validation-selected candidates', ('current', 'overall', 'three', 'simple'))
    lines.extend(['', '## Same-case comparison with historical binary prefix benchmark', '',
                  'All 8,800 cases (including warmup) for descriptive comparability; this table is NOT used for selection. Canonical 583-customer Recall 42.74% is a different split and is not comparable to this next-order score.', '',
                  '| Mode | Model | History | Historical binary R@5 % | Exact TF-IDF R@5 % |', '|---|---|---|---:|---:|'])
    for mode in MODES:
        for old, new in (('Content', 'Content'), ('CF', 'CF'), ('A_50_50', 'Hybrid50'), ('Popularity', 'Popularity')):
            if old not in data['historical_comparison'].get(mode, {}):
                continue
            for group in ('1', '2-4', '>=5'):
                before = data['historical_comparison'][mode][old]['groups'][group]
                after = data['summaries']['all'][mode][new]['groups'][group]
                if before and after:
                    lines.append(f"| {mode} | {new} | {group} | {before['recall_at_5']*100:.2f} | {after['recall_at_5']*100:.2f} |")
    lines.extend(['', '## Decision and limitations', ''])
    for mode in MODES:
        choice = data['frozen']['modes'][mode]
        lines.append(f"**{mode}:** validation winner is `{choice['overall']['key']}`; best three-stage candidate is `{choice['three']['key']}`. "
                     + ('Three-stage is the validation winner.' if choice['overall']['family'] == 'three'
                        else 'The three-stage hypothesis is NOT the validation winner; adding a Content-only low-history branch is not justified by model selection.'))
        for name in ('overall', 'three'):
            interval = data['uncertainty'][mode][name]['overall']['intervals_b_minus_a']['ndcg_at_5']
            supported = interval['lower'] > 0
            lines.append(f"Held-out {name} vs current: " + ('positive NDCG difference with CI above zero.' if supported
                else 'no conclusive positive NDCG difference; CI includes zero or favors current.'))
        weight_interval = data['uncertainty'][mode]['weight_60_minus_50']['intervals_b_minus_a']['ndcg_at_5']
        weight = choice['weight_only']['key']
        confirmed = (weight == 'Hybrid60' and weight_interval['lower'] > 0) or (weight == 'Hybrid50' and weight_interval['upper'] < 0)
        lines.append(f"Validation-selected weight-only candidate `{weight}`: " + ('held-out NDCG confirms the direction over the alternative.' if confirmed
            else 'held-out NDCG does not distinguish 50/50 from 60/40 conclusively.'))
        lines.append('No deployment threshold is promoted. The threshold in the best three-stage grid candidate is descriptive, not an approved switching rule.')
        lines.append('')
    lines.extend([
        'The frozen best three-stage policy is a hypothesis comparison, not a predefined winner. A positive overall estimate does not prove Content is best at every low-history depth. Treat groups with fewer than 30 unique customers as insufficient evidence. Confidence intervals for paired Recall/NDCG use customer-cluster resampling, retain case weighting, and do not correct for multiple comparisons or cross-customer temporal/shared-state dependence. The same customers can occur across windows; no claim of new-customer generalization is made.', '',
        'No production threshold/weight change is recommended solely from this dataset: current catalog/BOM/status are retrospective; ingestion observed-at/late-arrival history is missing; seed staging leaves provenance unverified; zero-order registered cohort is absent. Reported winners are reproducible conditional offline candidates for review. Independent real-user future windows and historical availability snapshots are needed before promotion.', '',
        'Discovery excludes every previously purchased product, even if later returned. Repeat labels remain unchanged and can become unreachable; review unreachable counts rather than dropping cases. No stock/branch/active-variant availability model or RETURN preference penalty is added in this benchmark.', '',
        f"Regression probes: {len(data['regression_probes'])}; cutoff-state builds={data['state_builds']}. Protected existing source/env/artifacts/historical outputs unchanged={data['protected_unchanged']}.", '',
        'Reproduce using input-snapshot.json into a NEW task directory, as shown in COMMAND.txt. That snapshot and per-case predictions contain private customer/event identifiers: keep local, outside Git/public reports. results.json includes all aggregate/finer-group metrics, validation grid, frozen policy, uncertainty and input/source hashes.'] )
    return '\n'.join(lines) + '\n'


def run(args):
    before = protected_hashes()
    output = reserve_output(args.output_dir)
    print('Loading explicit read-only input...', flush=True)
    snapshot, products, audit = replay(args.snapshot_file) if args.snapshot_file else acquire(args.env_file)
    write(output/'input-snapshot.json', payload(snapshot, products, audit))
    built = build_prefix_cases(snapshot.purchases)
    verify_cases(built, snapshot.purchases)
    cases, rejected = causal_cases(built)
    if not cases:
        raise ValueError('No causal cases')
    partitions, window_contract = windows(cases, args.validation_start, args.test_start)
    historical_proof, old = verify_history(snapshot, cases, args.historical_prefix)
    # Holdout prediction probes also wait until the selection is frozen; pre-freeze
    # regression checks use warmup/validation cases only.
    checks = regression_probes(products, snapshot, partitions['warmup'] + partitions['validation'])
    catalog = tuple(p.product_id for p in products)
    all_rows = {mode: {model: [] for model in MODELS} for mode in MODES}
    summaries, evidence, builds = {}, {}, 0
    selected, grid = {}, {}
    for window in ('warmup', 'validation', 'test'):
        if window == 'test':
            frozen = {'contract': CONTRACT, 'windows': window_contract,
                'input_catalog_sha256': audit['catalog_sha256'], 'input_purchases_sha256': audit['purchases_sha256'],
                'criterion': 'validation only: NDCG, MAP, Recall, lexical key',
                'modes': {mode: {k: asdict(v) for k,v in selected[mode].items()} for mode in MODES}}
            write(output/'frozen-policy.json', frozen)
            frozen_hash = hashlib.sha256((output/'frozen-policy.json').read_bytes()).hexdigest()
            print('Policy frozen before final test: ' + frozen_hash, flush=True)
        print(f'Predicting {window}: {len(partitions[window])} cases', flush=True)
        predictions, state_evidence, state_builds = predict(products, snapshot.purchases, partitions[window],
            lambda done, n, states: print(f'{window}: {done}/{n}; distinct states={states}', flush=True))
        builds += state_builds
        evidence[window] = state_evidence
        rows = {mode: metric_rows(predictions[mode]) for mode in MODES}
        summaries[window] = {mode: {m: summarize(rows[mode][m], catalog, mode) for m in MODELS} for mode in MODES}
        for mode in MODES:
            for model in MODELS:
                all_rows[mode][model].extend(rows[mode][model])
        if window == 'validation':
            for mode in MODES:
                selected[mode], grid[mode] = select(rows[mode], catalog, mode)
        if window == 'test':
            test_rows = rows
    if hashlib.sha256((output/'frozen-policy.json').read_bytes()).hexdigest() != frozen_hash:
        raise ValueError('Frozen policy changed during final test')
    summaries['all'] = {mode: {m: summarize(all_rows[mode][m], catalog, mode) for m in MODELS} for mode in MODES}
    policy_test = {mode: {'current': summarize(policy_rows(test_rows[mode], CURRENT), catalog, mode),
        **{k: summarize(policy_rows(test_rows[mode], p), catalog, mode) for k,p in selected[mode].items()}}
        for mode in MODES}
    uncertainty = {mode: comparisons(test_rows[mode], selected[mode], args.bootstrap_samples) for mode in MODES}
    if before != protected_hashes():
        raise ValueError('Protected source/env/artifacts/historical outputs changed during evaluation')
    data = {'executed_at_utc': datetime.now(timezone.utc).isoformat(), 'contract': CONTRACT,
        'serving_contract': CONTRACT_VERSION, 'feature_contract': FEATURE_CONTRACT,
        'normalization_contract': NORMALIZATION_CONTRACT, 'inputs': audit,
        'source_sha256': {k:v for k,v in before.items() if k.endswith('.py')},
        'dataset': {'purchase_events': len(snapshot.purchases), 'customers': len(customer_orders(snapshot.purchases)),
            'orders': sum(len(o) for o in customer_orders(snapshot.purchases).values()),
            'cases': len(cases), 'rejected_cases': rejected, 'catalog_size': len(catalog),
            'zero_order_registered_cohort': 'UNAVAILABLE'},
        'windows': window_contract, 'historical_verification': historical_proof,
        'historical_comparison': old, 'summaries': summaries, 'validation_policy_grid': grid,
        'frozen': frozen, 'frozen_policy_file_sha256': frozen_hash, 'policy_test': policy_test,
        'uncertainty': uncertainty, 'regression_probes': checks, 'state_builds': builds,
        'state_evidence_sha256': digest(evidence), 'protected_unchanged': True}
    write(output/'results.json', data)
    write(output/'state-evidence.json', evidence)
    write(output/'predictions.json', all_rows)
    (output/'BENCHMARK_REPORT.md').write_text(report(data), encoding='utf-8')
    (output/'COMMAND.txt').write_text(
        'Run from services/Recommendation-Service using existing .venv.\n'
        f'.venv/Scripts/python.exe -B -m offline_benchmark.runners.run_exact_tfidf_benchmark --snapshot-file "{output / "input-snapshot.json"}" --output-dir "<NEW_DIRECTORY_UNDER_REPO_TMP>" --bootstrap-samples {args.bootstrap_samples}\n'
        'Use the same --validation-start and --test-start boundaries recorded in results.json if overriding defaults.\n', encoding='utf-8')
    print(json.dumps({'output': str(output), 'cases': len(cases), 'protected_unchanged': True}), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--env-file', type=Path)
    source.add_argument('--snapshot-file', type=Path)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--validation-start')
    parser.add_argument('--test-start')
    parser.add_argument('--bootstrap-samples', type=int, default=2000)
    parser.add_argument('--historical-prefix', type=Path, default=SERVICE/'offline_benchmark/outputs/prefix_history_benchmark.json')
    args = parser.parse_args()
    if args.bootstrap_samples < 100:
        parser.error('At least 100 bootstrap resamples required')
    try:
        run(args)
    except Exception as exc:
        # DB exception text can contain connection parameters: never print it.
        print(f'Benchmark failed: {type(exc).__name__}. No protected outputs replaced.', file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
