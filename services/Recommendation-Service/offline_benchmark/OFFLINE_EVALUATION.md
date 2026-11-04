> Internal evaluation/build tooling. Web users use the single existing AI report export. Deployment/source selection: [REPORT_EXPORT_INTEGRATION.md](REPORT_EXPORT_INTEGRATION.md).

# Offline Evaluation Excel V1

This CLI generates a technical ARCHIVE evaluation, never an operational sales report.
It does not start FastAPI/Kafka, call File-Service, write MySQL or load the serving NPZ.

## Run

From Recommendation-Service, using its existing Python environment:

~~~powershell
.venv/Scripts/python.exe -B scripts/export_offline_evaluation.py --snapshot E:/Huong-Van-Tra-Backup/codex-reports/2026-10-18/190100_tmp-backup/recommendation-exact-tfidf-20261012/input-snapshot.json --snapshot-sha256 636e4576650e9889abfecb8778984e3c15520c866888ef838179a70866f7c0b9 --quantity-env-file recommend.env
~~~

The explicit trusted archive hash, internal snapshot fingerprints and the original
canonical manifest must all agree. A mismatch fails; no manifest refresh occurs.
The default output root is E:/Huong-Van-Tra-Backup/codex-reports. Each run creates
an exclusive Asia/Bangkok date/time directory. Writes need filesystem permission;
there is no fallback into the repository. Existing artifacts are never overwritten.

## Contract

- Original per-customer whole-order 80/20 membership is preserved.
- One customer-holdout opportunity per eligible customer; K=5.
- Mode is UNFILTERED_BENCHMARK: mode=None, exclude_purchased=False.
- Cutoff is completion MAX(event_time) of the final own TRAIN order.
- All state comes from complete canonical TRAIN orders ending <= cutoff.
- Canonical TEST events of every customer are excluded, even if earlier than cutoff.
- Other-user future/spanning TRAIN orders are excluded whole and counted in diagnostics.
- Ambiguous own TRAIN/TEST boundaries fail before inference.
- Existing V1 build_artifact and RecommendationService provide features, CF,
  normalization, weights, routing, tie-breaks and Train-only fallback popularity.
- Predictions are immutable and fingerprinted before ground truth is attached.
- Relevance is unique canonical products over the customer's entire TEST horizon.
- Precision denominator remains five, including candidate shortages. Empty ground
  truth is retained in reports with undefined accuracy metrics, not treated as zero.

## Artifacts

The run contains the four-sheet XLSX, manifest.json, metrics.json, predictions.json,
evaluation.json, membership.json, state-evidence.json, verification.json and
checksums.sha256. A separate Markdown run report is saved through scripts/codex/save_report.py.
Predictions use customer pseudonyms; membership retains event/order identities for
traceability and should be access controlled. Never upload these through operational exports.

No persisted model NPZ is produced. Intermediate model matrices live only in memory.
State fingerprints omit model creation clocks, so repeated predictions are deterministic.
Artifact build timestamps retain their real execution time and are never backdated.

## Limitations

The catalog/BOM/status/name snapshot is retrospective. Source event timestamps are
naive and their timezone is unverified; raw simulated cutoffs are comments, not real
recommendation dates. Period cells show source-calendar bounds without conversion.
Historical ingestion availability, transaction provenance and source completeness
are not established. The canonical Test has previously been used for research.

Quantity remains internally NOT_VERIFIED; the Sale/POS workbook displays —. The optional bounded SELECT-only probe checks current
history fingerprint and one-to-one event/variant/source mapping, but cannot certify
units or convert packaged units into cups. Connection failures are sanitized and
never prevent prediction/matching export from the verified archive.

## Tests

~~~powershell
.venv/Scripts/python.exe -B scripts/run_offline_evaluation_tests.py
~~~

The runner uses dummy loopback settings, disables initializers, denies outbound
connections and places legacy test report fixtures in an external temporary root.
No project .tmp report directory is created. Tests include prediction-label/quantity
separation, future data exclusion, V1 parity, metric examples and workbook layout parity.
