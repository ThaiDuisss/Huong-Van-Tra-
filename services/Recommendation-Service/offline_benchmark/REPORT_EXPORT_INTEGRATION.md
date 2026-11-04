# Existing AI Excel export: configured data source

There is one public generated-report flow: AI → Báo cáo AI → Xuất Excel. The frontend sends POST /api/recommendation/ai-reports/exports through Gateway, then GET /api/recommendation/ai-reports/exports/{id}/download. No additional page, button, source selector or endpoint is introduced. The imported-report original-file download remains a separate existing document-management action.

## Configuration

New export intents require explicit AI_REPORT_DATA_SOURCE=OPERATIONAL or OFFLINE_EVALUATION. Missing/invalid configuration fails; evidence-table contents never select a source. Existing COMPLETE or PREPARED exports remain readable/retryable without a working bundle or current source selection.

Operational deployments set:

~~~dotenv
APP_ENVIRONMENT=production
AI_REPORT_DATA_SOURCE=OPERATIONAL
~~~

Development/demo deployments set, through their service environment (not the browser):

~~~dotenv
APP_ENVIRONMENT=development
AI_REPORT_DATA_SOURCE=OFFLINE_EVALUATION
AI_REPORT_OFFLINE_BUNDLE_PATH=/opt/hvtra/evaluation/evaluation-bundle.json
AI_REPORT_OFFLINE_BUNDLE_SHA256=<trusted-packager-output-sha256>
~~~

Mount that exact immutable bundle read-only at a path accessible to the Recommendation-Service process/container. Windows and Linux paths are deployment choices. No application code contains an E: archive path. Changing the env example does not configure an already running process: supply these variables to that process and restart it. This change does not deploy or restart shared services. Production explicitly rejects OFFLINE_EVALUATION even if selected. Switching back to OPERATIONAL requires only configuration and a new requestId; operational tables are not populated by the demo.

Keep bundles server-private: the embedded verified source contains identifiers needed for split validation. They are never returned by the report API. Excel uses existing stable pseudonyms and contains no raw customer identifiers or customer contact information. Do not expose the bundle mount as static web content.

## Prepare an immutable bundle (internal tool)

First create/verify a causal evaluation run using the existing internal scripts/export_offline_evaluation.py and its documented archive/manifest checks. This is a research/build tool, not a public download alternative. It remains necessary to produce a new verified frozen run when prediction sources or data change. Existing historical artifacts are retained.

Then run from Recommendation-Service:

~~~powershell
.venv/Scripts/python.exe -B scripts/prepare_ai_report_bundle.py --run-directory <verified-run-directory> --snapshot <verified-input-snapshot.json> --snapshot-sha256 <trusted-archive-sha256> --run-manifest-sha256 <trusted-producer-manifest-sha256> --output <external-new-bundle.json>
~~~

The command validates the pinned producer manifest, prediction/evaluation/membership/state/verification artifact hashes, exact archive, original chronological split, current prediction-source hashes and model/protocol/mode contracts. It re-evaluates frozen predictions and independently reconstructs all canonical Train cutoff memberships without model fitting. Output creation is exclusive, outside project worktrees under repository report policy. There is no automatic acceptance of an input file's own digest as its trusted pin.

Producer paths are normalized to / so a Windows-created manifest can be deployed cross-platform. Prediction source byte hashes must match; checkout line-ending changes require a compatible verified run. Renderer source changes alone do not invalidate predictions: the bundle is explicitly a prediction/evaluation schema, not pre-rendered XLSX. Serving artifact files/NPZ, caches, SQL repositories and Kafka are never prediction inputs to this loader. It cannot rebuild predictions as a fallback.

At request time the provider reads and hashes the configured file on every preparation, including cache hits. Fully verified immutable objects are cached by bundle hash and current prediction-source hashes (two entries, guarded by a lock). Missing, oversized, corrupt, stale or incompatible bundles return 503 with rebuild guidance; they never yield a successful empty/zero report. Bundle validity is semantic/version validity, not a claim that frozen data is current.

## Reporting period

The source timestamps are naive and their timezone is unverified. Date inputs select original source-calendar dates with no timezone conversion. Select only canonical Test orders whose earliest AND latest event dates lie inside the inclusive requested range. Orders spanning a period boundary are excluded whole and counted in provenance; orders outside the period are excluded.

Each customer with at least one selected Test order retains exactly one original frozen prediction. That prediction's simulated cutoff is after the customer's final original Train order and may lie outside the requested period. It is not an exposure timestamp and is not shown as a real recommendation date. The unchanged prediction is evaluated against unique canonical products in that customer's selected Test orders. Every KPI, rank metric, product order count and matching row is recomputed for that subset. Precision keeps denominator 5. No inference is run, no labels alter predictions, and no operational next-order attribution is applied. An empty selection returns 422, with a message to choose a period containing complete Test orders.

The normal API future-date and maximum 366-day constraints still apply. The verified baseline's complete Test horizon is 2025-10-08 through 2026-09-19 (347 days), giving all 583 opportunities. The example 2026-03-29 through 2026-10-21 intentionally selects 563 opportunities/1,866 Test orders rather than silently reporting the full baseline. A selected subset is an evaluation against a shorter target horizon, not the original full-horizon benchmark.

The existing workbook helpers, four sheet names/order, header positions, green/Calibri styles, borders, gridlines, filters, continuation rows, print setup and freeze panes are reused. Offline-only widths accommodate eight product columns and shorter display IDs. Title/metadata identify DỮ LIỆU KIỂM THỬ. The existing methodology area explains historical source and period semantics in business language. Unknown quantity is displayed as — (internal status NOT_VERIFIED); interaction lines and packaged units are not cups.

## Durable intent and storage

New intent evidence JSON pins the source descriptor before the first commit: source kind, bundle schema/digest, original split digest, original prediction digest, model contract, protocol, mode and period policy. After acquiring the existing preparation lock, a pending offline intent must match that pinned descriptor; another bundle returns 409. Restore its original bundle or create a new requestId. An OPERATIONAL pending intent stays operational. Legacy pending intents with no source descriptor are operational by origin. No schema migration is needed.

The existing requestId actor scope, unique constraint, row lock/refresh, frozen PREPARED bytes committed before remote storage, PRIVATE File-Service binding and COMPLETE response behavior remain. Once prepared, retries send the same bytes regardless of source/bundle configuration. COMPLETE exports never re-render. A new bundle/source requires a new requestId; old files are not overwritten.

Authentication stays on the existing signed Gateway user context. The actual current backend policy admits seven staff roles, including MANAGER, and denies CUSTOMER/unsigned/forged contexts; it is not manager-only. File-Service authorization, signed internal requests, private binding, allowed storage origins and streamed download remain unchanged. The client cannot supply a source or artifact path.

## Verify without shared writes

~~~powershell
.venv/Scripts/python.exe -B scripts/run_offline_evaluation_tests.py
.venv/Scripts/python.exe -B scripts/verify_ai_report_export.py --bundle <bundle.json> --bundle-sha256 <trusted-sha256> --from-date 2025-10-08 --to-date 2026-09-19 --output-directory <external-new-directory>
~~~

The second command invokes the existing POST/export and GET/download routes with real report authentication, BusinessReportService and FileClient. SQLite is in memory; File-Service/storage HTTP use MockTransport, with signatures, owner/digest binding and storage credential isolation checked. It saves the downloaded bytes and provenance/checksums outside the project. Inference functions are poisoned; fake snapshot/purchase tables remain empty. It also verifies denied contexts and frozen retry after broken source configuration. This proves an isolated API integration, not a live browser→Gateway→File-Service end-to-end run. No shared DB, File-Service, object store or Kafka is contacted.

## Cleanup and limits

KEEP causal split/evaluation, anti-leakage checks and ranking metrics. REUSE the existing offline workbook adapter as the configured source adapter within BusinessReportService. RETAIN AS INTERNAL TOOL the evaluation producer, bundle packager, artifact verifier and isolated API verification. Audit found no redundant new public offline UI/API to delete. Do not remove imported-original downloads or historical XLSX archives.

The frozen source is historical, Test was previously used in research, catalog/BOM/status are retrospective, observed-at/source timezone are unverified, and quantity remains unverified (— in the workbook). This is technical simulation, not evidence of actual recommendation exposure, conversion, revenue or AI impact. Native Excel desktop rendering and shared runtime deployment are separate checks.

## Sale/POS presentation contract

The offline renderer keeps the four KPI rows (customers, recommendation items, matched pairs, overall matched/recommended percentage), so the overview product table still starts at row9. Products add matched opportunity-product pairs and matched/recommended percentage; distinct purchase-order counts are separate. Comparisons show numeric percentages instead of technical statuses. Store fractions0–1 with invariant Excel format0.00%; never divide order count by recommendation count or average opportunity percentages for the overall rate. A zero denominator is a readable unavailable value; a valid zero is numeric0%. Product order remains by name, without implying a low-sample100% result is the best product.

KH-001/GY-001 aliases follow immutable prediction order within this workbook. They are display labels, not keys or stable cross-workbook customer identities. The protected BusinessReportExport.evidence.presentation records schema, display-to-original reference mapping, simulated cutoff/state references and rate contract. Neither original pseudonyms, technical IDs nor model terminology appear in the business cells/comments. Existing bundle/metrics/protocol provenance stays internal. Recommendation date and unverified quantity show —; no date, exposure or quantity is invented. Existing PREPARED/COMPLETE bytes and OPERATIONAL rendering are unchanged.


Customer details append a sixth column, Tỷ lệ gợi ý trùng mua (%), using the very same opportunity-reference-keyed display value as comparisons. Only the main physical row carries the percentage; continuation rows leave it blank. The read-back verifier joins display aliases through protected stable opportunity references, so it does not assume identical Excel row order across the two sheets. The customer AutoFilter/print area includes column F.

Presentation schema sale-pos-ai-report-v2 has three short business notes: historical matching is not AI-generated sales, the matched/recommended formula, and the missing exposure/quantity/date limitations. Detailed period selection notes are stored in protected evidence.methodology_notes; source_timezone and customer_exposure remain explicitly NOT_VERIFIED. No technical lineage is removed, and operational notes/helpers remain unchanged.
