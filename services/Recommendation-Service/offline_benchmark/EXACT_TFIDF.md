# Exact serving TF-IDF causal benchmark

This benchmark evaluates the current V1 mathematics without changing production
routing, API, credentials, migrations or model files. It extends the existing
whole-order prefix protocol; historical outputs remain untouched.

Run from `services/Recommendation-Service` with the existing virtual environment:

```powershell
.venv/Scripts/python.exe -B -m offline_benchmark.runners.run_exact_tfidf_benchmark `
  --env-file recommend.env `
  --output-dir E:/Huong-Van-Tra-/.tmp/<new-task-directory>
```

Choose the legitimate existing env file explicitly. Acquisition performs
SELECT-only reads inside a confirmed MySQL read-only consistent transaction,
rolls back, and closes the connection before model evaluation. No Kafka client,
production artifact loader or model publisher is involved. Errors omit connection
exception text to avoid exposing credentials.

Replay without network access:

```powershell
.venv/Scripts/python.exe -B -m offline_benchmark.runners.run_exact_tfidf_benchmark `
  --snapshot-file E:/Huong-Van-Tra-/.tmp/<previous-run>/input-snapshot.json `
  --output-dir E:/Huong-Van-Tra-/.tmp/<another-new-task-directory>
```

Every output directory must be new and under repository `.tmp`; existing files
are never replaced. An ignore-all file keeps private snapshots/per-case customer
identifiers out of Git. Keep these artifacts local. The report contains aggregates.

Contract:

- Reuse exact O1..Ok → O(k+1) cases, with no lifetime-history eligibility filter.
  Local spanning/tied orders are explicitly rejected by the existing causal case
  filter. Global orders enter training only after MAX(line event_time) < cutoff.
- Five adapters: named-purchase Popularity, V1 TF-IDF Content, item-item CF,
  Hybrid50, Hybrid60. TF-IDF is built once by production `build_artifact`; CF uses
  the same sparse count/cosine operations with independent full-builder parity.
  Immutable states are shared at equal cutoffs with catalog, history, event-chain,
  cutoff and scoring-contract hashes proving input equivalence.
- Repeat and discovery modes use identical cases/labels. Normalize components
  after candidate exclusion. Content/CF-only flat scores fall back to Popularity;
  hybrids fall back only when both components are flat. No weight redistribution,
  label removal or Top-5 padding. Tie ordering matches serving.
- Global windows default to 60/80 percentiles of unique prediction timestamps:
  warmup, validation, final test. Optional `--validation-start`/`--test-start`
  accept ISO datetimes matching input time convention. Boundaries do not inspect
  relevance labels. These windows are not the historical per-user 80/20 split.
- Fixed threshold grid 2/3/4/5/6/8/10 compares three-stage Content→Hybrid,
  simpler Popularity→Hybrid and five constant-model baselines against the historical
  pre-update Popularity-through-4/Hybrid60-from-5 baseline. Choose by validation NDCG, MAP, Recall,
  lexical tie rule. Freeze policies before final-test prediction/metrics.
- Training rolls forward using earlier complete orders even within the final
  test window; policy/weights remain frozen. This measures conditional online
  updating with zero event-time delay, not a fixed model trained once at test start.
- Use existing binary relevance metrics, fixed Precision denominator five and
  customer-cluster paired bootstrap (default 2,000 draws, fixed seed). Report all
  models by 0/1/2–4/≥5 and finer groups, unreachable labels, candidate shortages,
  flat-component/fallback counts/rates and identical-candidate assertions.

Limitations are explicit: registered zero-history cohort unavailable, current
catalog/BOM/status retrospective, historical ingestion availability absent,
seed/live provenance unverified, repeated customer cases and residual temporal
dependence. Validation winner is an offline candidate; no production policy is
promoted automatically. Same-case binary comparison changes features,
normalization and fallback, so cannot attribute all differences to TF-IDF alone.

Online routing was subsequently changed by explicit interim business approval to
0–1 completed purchase orders → Popularity, ≥2 → Hybrid60. This is not a claim of
statistical optimality. The offline `CURRENT` policy and saved benchmark results
retain the pre-update five-order baseline for reproducible historical comparison;
they no longer represent the live routing threshold. See the service [README](../README.md).

Outputs: `BENCHMARK_REPORT.md` (one aggregate report), `results.json` (all metrics,
validation grid, frozen choices, uncertainty and input/source hashes),
`frozen-policy.json`, `state-evidence.json`, private `predictions.json` and
`input-snapshot.json`, plus replay command. Production/historical file hashes
are checked before/after execution.

Tests:

```powershell
.venv/Scripts/python.exe -B -m unittest discover -s tests
.venv/Scripts/python.exe -B -m unittest discover -s offline_benchmark/tests
```

Use child-process placeholder/loopback settings for tests; no shared services are
needed. New tests include full scoring/service parity, spanning-order causality,
future-label mutation across customers, exclusion/min-max parity, fallback,
state immutability, metrics and the runner's freeze-before-test orchestration.
