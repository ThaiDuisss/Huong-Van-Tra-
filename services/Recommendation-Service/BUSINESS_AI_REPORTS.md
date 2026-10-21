# Business AI Recommendation Excel report

Implemented 2026-10-12. This is a business report of **generated suggestions and
observed completed purchases**, not an impression, probability, conversion or causal
lift report. Original PDF/XLSX import, metadata editing, binding, list and original
download continue on their existing contracts.

## Evidence and snapshot contract

The authorized `GET /recommendations/{customer_id}` persists one immutable snapshot
before returning success. Customer authorization still uses the existing Gateway
customer assertion and request-bound v2 HMAC. The service continues Popularity for
0–1 distinct purchase orders, Hybrid60 for ≥2, existing fallback, DISCOVERY and REPEAT.
No model or artifact changes are involved. Endpoint responses now contain at most
five products and add `recommendationEventId` and UTC `generatedAt`. Inference helpers
used by offline tests/builds do not create online records.

Send a UUID `Idempotency-Key` for each logical recommendation request. Retries must
keep that UUID with the same normalized mode/exclusion/limit and authenticated
customer. The key is scoped to customer; differing parameters produce 409. Concurrent
inserts converge through a unique constraint and return the winning stored response.
Omitting the key means a new logical request each time; callers must adopt the header
for retry protection. Responses are private and non-cacheable.

Each snapshot records UTC generation/observation times, exact ordered response,
model type/contract/source/fallback, normalized parameters, customer, immutable event
ID and the evaluation window effective at generation. Generation time follows
inference so a purchase preceding completion cannot be counted as a later purchase.
There is no client prediction-write endpoint and no product IDs accepted as AI evidence.

**Production recording policy: fail closed.** Persistence failures roll back, log a
sanitized ERROR and return 503. No successful response is issued without a durable
snapshot. This requires applying the Recommendation migration before serving traffic.
A commit followed by a disconnected response may exist: this is explicitly a generated
response, never a verified UI impression. No impression event currently exists.

Legacy `exclude_purchased=false` without a mode retains its include-all ranking
semantics. Its snapshots are labeled `LEGACY_ALL` and excluded from the DISCOVERY/
REPEAT business report instead of mislabeling mixed candidates as REPEAT. Use explicit
`mode=REPEAT` for reportable repeat recommendations. Legacy default exclusion is
reportable DISCOVERY. Explicit modes retain their existing membership rules.

## Whole completed purchase evidence

The existing Order outbox publishes `ORDER_COMPLETED` to `order.completed`, with
`orderStatus=COMPLETED`, `paymentStatus=PAID`, UTC `occurredAt`, order/customer IDs and
complete `items` with order-line IDs, variants/SKUs and quantities. The existing
Purchase adapter verifies those exact states. Ambiguous timestamps without a timezone
and nonfinite quantities are rejected. Cancellation/unpaid/RETURN events cannot
enter the new purchase projection.

Variant ID/SKU is resolved using the existing Catalog read model to **Product.id**;
this is the identity used in AI recommendations. Variants/lines are preserved in
evidence, then grouped by canonical product only when counting products or cups.
Quantities are summed across repeated lines. Guest orders contribute sales but never
customer attribution. PII from the order payload is not copied.

The new `report_purchase_orders` projection captures a complete canonical order,
its original source-event ID, occurred/observed timestamps and semantic SHA-256.
It commits in the **same transaction** as interaction events/aggregates. The Kafka
offset is committed only after that transaction succeeds. A duplicate delivery or
new transport event for the same order cannot create another report sale. Different
order/line evidence for a known identity is rejected and rolled back.

Existing `interaction_events` is not treated as recommendation impressions or
proof of a complete order header. No historical recommendation fabrication or
purchase backfill occurs. Existing interaction-based model routing remains intact.
The new whole-order projection begins with deployment/worker activation. Reports
before that collection period can be empty or incomplete; zero means **zero recorded
qualifying evidence**, not proof that the business made no sales. Workbook notes
state this limitation. Historical sales recovery requires separately reviewed
complete paid-order evidence and is outside this implementation.

## Interim attribution rule v1

No existing approved attribution contract was found. The initial, configurable rule:

1. For each reportable snapshot, find the first eligible whole order for the same
   customer with `generated_at < occurred_at <= generated_at + evaluation_days`.
2. When several snapshots have that same next order, only the nearest prior snapshot
   wins. Equal snapshot times use event UUID descending; equal order times use order
   ID ascending. DISCOVERY and REPEAT compete for one shared purchase, preventing
   double counting across modes.
3. A losing snapshot is `SUPERSEDED`; it never falls forward to the second order.
   Its purchased/matched counts remain unknown, not zero. The workbook states that
   the order was compared with a newer suggestion. Internal evidence retains the
   competing order ID and winning recommendation event ID.
4. Winning snapshots are `MATCHED` or `NO_OVERLAP`, using distinct product intersection.
   Pending snapshots are `NOT_YET_EVALUATED` until an order is observed or the inclusive
   evaluation deadline is reached. After expiration without an observed order they
   are `NO_PURCHASE` ("Chưa có đơn" in the recorded dataset).
5. Every projection uses one UTC observation cutoff; future and subsequently observed
   orders are excluded. Attribution is computed before selected-period filtering,
   including competing snapshots outside the report period. Thus exporting adjacent
   periods cannot attribute a purchase twice. A later export with a new request UUID
   may reflect delayed ingestion; an existing export remains frozen.

`AI_REPORT_EVALUATION_DAYS=7` is the proposed conservative default, range 1–90. The
effective value is saved per snapshot so changing configuration does not retroactively
change existing windows. This rule does not establish that AI caused a purchase.

## Metric glossary and workbook

Exactly these four sheets are generated:

- `TỔNG QUAN BÁO CÁO`: brand, business title, period and generation time; four KPIs;
  product appearances/cups/matches with mathematically valid totals and business notes.
- `CHI TIẾT THEO KHÁCH HÀNG`: one row per snapshot, pseudonymous customer code,
  local suggestion time, Discovery/Repeat label and ordered product names, whole
  attributed purchase quantities and matches. Cell comments link event/order IDs.
- `THỐNG KÊ SẢN PHẨM`: distinct customers per product, recommendation appearances,
  distinct purchase orders per product, sold units. Distinct counts are not summed
  into misleading cross-product customer/order totals.
- `ĐỐI CHIẾU KẾT QUẢ GỢI Ý`: stable event IDs/customer pseudonyms, distinct suggested/
  purchased/overlapping product counts and business statuses. Pending/superseded
  numeric outcomes are blank; evaluated zero overlap is zero.

| Metric | Counting unit and deduplication | Period and eligibility | Missing data |
|---|---|---|---|
| Khách hàng được gợi ý | Distinct customer ID with ≥1 suggested product | Reportable generated snapshots in local calendar period | Empty responses retained for audit, excluded from this customer KPI |
| Lượt gợi ý sản phẩm | One event–product appearance, maximum five per response | Snapshot generation in period; DISCOVERY/REPEAT | No synthetic rows or inferred impressions |
| Số ly bán thực tế | Sum of positive integer quantities across whole order lines | All recorded COMPLETED/PAID orders in period, including guest orders | Projection gaps documented; fractional quantities reject export rather than round |
| Lượt gợi ý trùng mua | One attributed event–overlapping-product pair | Snapshot in period; next order can be after period through its window and observation cutoff | Pending/superseded excluded, never counted as failed predictions |
| Số khách được gợi ý (product) | Distinct customer–product IDs | Same snapshot cohort | No names/phone/email exposed |
| Số đơn có mua (product) | Distinct order–product IDs | Same recorded paid/completed sale period | Repeated lines/units do not increase order count |
| Số món khách mua / trùng | Distinct canonical products in attributed order/intersection | One whole attributed next order | Unknown remains blank |

Dates are inclusive **Asia/Bangkok / UTC+07:00 calendar days** implemented with
half-open UTC boundaries `[from-date midnight, to-date + 1 day midnight)`. Generation
and suggestion times are displayed as Vietnam time. RETURN operational events do
not alter preference matching or this gross completed-sale report; it is not a net
revenue/return report.

Workbooks use Vietnamese headers, integer separators, wrapped names, filters,
frozen headers, consistent green styling, highlighted valid totals, date formatting,
landscape print settings and honest empty states. Customer codes are stable SHA-256
pseudonyms of customer UUIDs; raw customer IDs exist only in protected audit evidence.
Text beginning with `=` is saved as literal text to prevent formula injection.
No ranking scores/probabilities, offline benchmarks, artifact hashes, Kafka offsets
or training diagnostics appear in business sheets. Layout/table implementation
follows [openpyxl worksheet tables](https://openpyxl.readthedocs.io/en/stable/worksheet_tables.html).

## Generated export API and private File-Service storage

Via existing Gateway route and existing seven-role AI Report allowlist:

```text
POST /api/recommendation/ai-reports/exports
{"fromDate":"2026-10-01","toDate":"2026-10-12","requestId":"<UUID>"}

GET /api/recommendation/ai-reports/exports/{exportId}
GET /api/recommendation/ai-reports/exports/{exportId}/download
```

Export responses contain id/fileId, exact period, UTC generatedAt, filename and
SHA-256. Filename: `AI_Report_HuongVanTra_YYYYMMDD_YYYYMMDD.xlsx`. No storage URL
is returned. Original import/download routes and original bindings remain separate.
Future/reversed periods, unknown request fields, excessively large workbooks and
evidence sets are rejected. Defaults: at most 366 days, 20,000 snapshots/orders and
100,000 order lines; 10 MiB workbook. Limits reject, never silently truncate.

Exports use a durable actor/request UUID unique intent with PENDING → PREPARED →
COMPLETE. Preparation locks and refreshes the row to avoid a stale ORM object
overwriting a concurrent winner. Workbook bytes, checksum and exact selected
matching/sales evidence commit before remote operations. Retry returns the same
bytes even if ingestion/catalog/configuration changes or a process restarts. A new
UUID deliberately requests a fresh export. Same UUID with a different period is 409.

Recommendation calls `PUT /internal/file/ai-reports/{exportId}/generated` with
binary bytes and trusted owner/name/checksum metadata. File-Service requires its
existing HMAC authentication plus exact `service:recommendation-service`/SYSTEM
caller. Browser/user principals cannot call this API.

File-Service durably reserves the export UUID as the file UUID, with owner/name/
checksum immutability. It uses only the configured private bucket and deterministic
`ai-report-generated/{UUID}.xlsx` object key. The reservation commits before storage.
A row lock serializes uploads; existing checksum-bearing objects recover a PUT
that succeeded before local finalization failed. Different objects, owner, filename,
checksum or deleted records conflict; originals are never replaced. The object and
ACTIVE file acquire exactly one `AI_REPORT`/`GENERATED` reference. No public URL,
bucket creation or bucket-policy mutation is part of export.

Generated download uses a separate trusted binding operation and the existing
backend storage proxy, validating private metadata, ETag, size, content type and
approved storage origin. Presigned GET capabilities stay between services; backend
auth never goes to storage. Download bytes carry private/no-store headers, and the
frontend verifies SHA-256 before saving.

Cross-service commits are deliberately not presented as one distributed transaction.
Storage/remote-response/local-finalization failures leave durable reservations and
PREPARED bytes for the same explicit retry. Sanitized ERROR logging makes failed
finalization observable. No automatic orphan deletion or original overwrite occurs.
If a caller abandons a failed export, its reservation/bytes require operator retention
review; no retention policy is invented here.

## Frontend and authorization

`/system/ai/reports` keeps its table/design/import and original download. `Xuất báo
cáo AI` opens From/To calendar inputs, validates them, shows generation/download
progress, prevents concurrent submit, downloads the generated workbook and reports
success/failure in Vietnamese. It uses existing authenticated apiClient only.

Retries preserve request UUID and receipt; a download failure retries the existing
download without regeneration. Actor-scoped sessionStorage stores only request UUID
and dates before network work, allowing a closed dialog/reload to resume the same
intent. Resumed receipts are reconfirmed by backend; no secret, bytes or URL are
stored. `Chọn kỳ khác` explicitly starts a fresh intent. Disabled browser storage
reduces recovery to the current mounted dialog's memory.

All generation, metadata and download operations enforce existing
SUPER_ADMIN/ADMIN/MANAGER/INVENTORY_STAFF/QC/ACCOUNTANT/SALE_STAFF allowlist.
CUSTOMER and service principals are denied the human report API. As with original
AI Reports, authorized staff share reports; generic private-file endpoints still
enforce ownership. This is an existing staff-sharing policy, not unrestricted
customer access. Internal File-Service upload is not exposed to browser code.

## Deployment and safe verification

Apply the two new `V20261012194438__business_ai_report_evidence.sql` migrations
separately in Recommendation and File services under approved deployment permissions.
Timestamp was generated at actual file creation in UTC+07:00, checked against each
service's source history. `outOfOrder=false` is explicit in both configurations.
Historical migrations are unchanged. Flyway is never invoked by Python app startup.

2026-10-12 read-only live-history inspection confirmed successful Recommendation
versions 1–3 and File versions 1–6, 20261010221221, 20261011203800. New versions follow
both histories. This is a **history check**, not evidence that new DDL is applied or
has been executed on real MySQL. No shared migration or data/object write was run.

Deploy schema first, then File-Service generated endpoints, then Recommendation app
and purchase worker, then POS. Maintain reviewed HMAC, customer mapping, shared Redis
replay configuration and backend storage-origin allowlist. Migrate the intended
schema: `flyway.conf` has historical recommendation_db defaults while the inspected
runtime environment uses recommend_app; deployment must explicitly align schema
and URL. Do not silently migrate a different database.

Tests use isolated SQLite/H2, synthetic model/catalog/order fixtures, mocked MinIO
and local loopback HTTP. Chrome interaction uses a mock API and synthetic four-sheet
XLSX. These do not establish real MySQL/MinIO/Keycloak/Kafka/browser E2E. Shared E2E
requires separate permission for the exact two migrations, synthetic staff/customer
test records/orders through approved business APIs, private generated objects and
tracked cleanup. No direct Kafka publish or consumer offset changes are authorized.

## Verification results (2026-10-12)

| Check | Result |
|---|---|
| Recommendation Python suite | 173 passed, including 41 new business report tests |
| Offline regression fixtures | 109 passed; no saved benchmark/model artifacts changed |
| File-Service Java | 77 passed, 0 skipped |
| Gateway Java | 19 passed, 0 skipped, including loopback JWT/HMAC smoke |
| Common-Service Java | 6 passed |
| POS Vitest | 95 passed, including original import/download regression |
| POS TypeScript/production build | Passed |
| AI feature ESLint | Passed |
| Full POS ESLint | Existing 142 errors and 3 warnings outside AI feature |
| Interactive Chrome | 8 checks passed; no runtime/console errors after fixing distinct import/export dialog keys |
| Python–Java HTTP | Generated four-sheet private store/binding/download, byte checksum, failed local commit recovery and original PDF/XLSX regressions passed |
| Migration naming | 124 files, 2 new timestamp migrations, no duplicates |
| Migration checker unit suite | 12 passed |
| git diff --check | Passed |

The Chrome test compares actual downloaded bytes with its synthetic workbook;
the API is mocked. The Python–Java test uses real Spring HTTP/auth/JPA and Python
aggregation/file client, with H2/SQLite and mocked MinIO storage. Shared runtime
verification was limited to read-only Flyway metadata; it does not prove shared
write E2E or new migration compatibility on live MySQL.
