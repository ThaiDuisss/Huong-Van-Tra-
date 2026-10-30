# Ingredient PoC — Phase 1A / Phase 1B / Phase 2

Schema nghiên cứu độc lập. Phase 2 bổ sung generator/validator/importer/publisher
SYNTHETIC chỉ trên MySQL local riêng. Phase 3 bổ sung AI research offline tại
[INGREDIENT_RESEARCH_V1.md](INGREDIENT_RESEARCH_V1.md); chưa có API/serving integration.
Migration đã được kiểm chứng bằng Flyway trên MySQL 8.4.11 local ephemeral;
**chưa áp dụng trên recommend_app shared**. DDL canonical: app/ingredient_poc/schema.py,
ddl.py và file SQL được test parity. Metadata riêng, không import vào
app.database.base.Base hoặc app.models.

## Preflight và V1

SELECT mới: recommend_app, MySQL 8.4.11, 28 bảng, 15 bảng lfm_seed_*, chưa có
ingredient_poc_*. Cả 4 checksum lịch sử migration Recommendation khớp source.
Session read-only=0: audit chỉ SELECT allowlist rồi rollback, không tuyên bố account
read-only. Chọn recommend.env tường minh bằng đường dẫn tuyệt đối; không thay
Settings mặc định .env/process và không dùng training.env.

FK nội bộ V1 live: **không có**. UUID read-model là CHAR/VARCHAR(36); một số UUID
staging BINARY(16). Các upstream customer/order/order-item/variant ID không phải
FK sang microservice khác.

| Bảng V1 | Unique/business key live ngoài ID |
|---|---|
| products | product_code |
| product_variants | sku |
| ingredients | ingredient_code |
| product_ingredients | product_id, ingredient_id, source_variant_id, bom_version |
| interaction_events | source_event_id, order_item_id, event_type |
| customer_interactions | PK customer_id, product_id |
| recommendation_snapshots | customer_id, request_key |
| report_purchase_orders | PK id |
| business_report_exports | created_by, request_key |

Index customer/time, product/time, event type/time, source_event và return item giữ
nguyên; metadata đầy đủ trong evidence report. PURCHASE adapter yêu cầu
ORDER_COMPLETED/COMPLETED/PAID, quantity dương, timestamp có timezone.
V1 routing đếm distinct order_id không rỗng; product history vẫn giữ missing-order
event cho preference/exclusion. RETURN adapter chỉ nhận CHANGE_OF_MIND làm
preference-negative, bỏ operational reasons; aggregate mua/trả cộng riêng.

V1 repository/service, sync, interaction aggregation, tooling hiện có, snapshot,
AI Excel/report vẫn đọc bảng cũ. Không thêm cột/index/FK vào bảng V1.

## Identity, kiểu dữ liệu và isolation

PK/FK PoC là UUID nghiên cứu VARCHAR(36), phân biệt upstream UUID. sale.customer_id
trỏ customers.id; customers.customer_id là logical/source key có source_system.
sale.order_id trỏ orders.id; orders.source_order_id mới là UUID đơn gốc.
sale.order_item_id là source line key, hoặc research line key trong SYNTHETIC.
Không nhân đôi customer theo purchase type, không lưu customer_type cố định.

source_product_id/source_variant_id/source_ingredient_id/source_unit_id là
reference có provenance, không phải cross-service FK. SYNTHETIC bắt buộc các cột
source ID này NULL và source_system thuộc research:*. Entity ID nghiên cứu không
được insert vào Catalog/V1. Không map theo tên, thứ tự hoặc default variant.

Mỗi child có id/dataset_id/source_kind/created_at, unique(id,dataset_id).
FK(dataset_id,source_kind) buộc origin khớp dataset; FK giữa children luôn kèm
dataset_id và product/unit/kind khi cần. InnoDB/utf8mb4_bin, case-sensitive IDs/codes.
Các FK dùng mặc định InnoDB RESTRICT/NO ACTION; không CASCADE, không FK tới V1
hoặc database khác. Không khai báo referential action tường minh trên các cột
CHECK để tránh giới hạn tương thích CHECK/FK của MySQL.

Quantity/portion/servings DECIMAL(24,8), factor DECIMAL(18,8), waste DECIMAL(9,4),
time DATETIME(6) UTC, SHA-256 VARCHAR(64), manifest/provenance JSON.
Không float cho định lượng. SQL CHECK hash length không thay validator kiểm
hex/nội dung. Contract chuẩn hóa UUID/Decimal/UTC trước hashing/persistence.

## Bảng và quan hệ

| Bảng ingredient_poc_* | Các cột riêng chính |
|---|---|
| datasets | dataset_name/schema_version/source_kind/source_system/source_snapshot_hash/status/validation_policy_version/manifest_json |
| customers | customer_id, source_system; không PII |
| units | source_unit_id, symbol/dimension/canonical_unit/conversion_factor/rule_version, validation_status/provenance |
| ingredients | source_ingredient_id, ingredient_code/name_snapshot/provenance |
| products | source_product_id, product_code/name_snapshot/provenance |
| variants | source_variant_id, product_id/sku/name_snapshot/sale_unit_id/provenance |
| orders | source_order_id/source_system/customer_id/order_status/payment_status |
| recipes | family_key/version/kind, base_product/variant/sale_unit, portion_quantity/unit_id/unit, servings, recipe_hash/provenance/validation_status |
| recipe_items | recipe/ingredient/code_snapshot/role, original quantity/unit/symbol, canonical quantity/unit/factor, waste/validation_status |
| recipe_snapshots | recipe/order/order_item, source_system/snapshot_id, observed_at/payload_hash/provenance |
| sale_items | customer/order/order_item/type, product/variant/recipe/kind/actual_snapshot, quantity/unit/time/status, source_system/event/revision/hash |
| feedback | sale/source_system/source_feedback_id/type/rating/return_reason/time |

Chi tiết tên cột/type/NULL/default, CHECK/UNIQUE/FK và index nằm trong SQL.
Explicit indexes theo dataset, customer+purchased_at, recipe+purchased_at,
ingredient, recipe_family_key/hash và feedback_time. Các referenced composite
keys đều UNIQUE tường minh, tương thích yêu cầu referenced key của MySQL 8.4.

~~~mermaid
erDiagram
  ingredient_poc_customers ||--o{ ingredient_poc_orders : customer
  ingredient_poc_orders ||--o{ ingredient_poc_sale_items : mixed_lines
  ingredient_poc_products ||--o{ ingredient_poc_variants : variants
  ingredient_poc_variants ||--o{ ingredient_poc_sale_items : product
  ingredient_poc_recipes ||--|{ ingredient_poc_recipe_items : ingredients
  ingredient_poc_ingredients ||--o{ ingredient_poc_recipe_items : ingredient
  ingredient_poc_units ||--o{ ingredient_poc_recipe_items : unit_snapshot
  ingredient_poc_recipes ||--o{ ingredient_poc_recipe_snapshots : observed_actual
  ingredient_poc_recipe_snapshots |o--o{ ingredient_poc_sale_items : exact_line
  ingredient_poc_sale_items ||--o{ ingredient_poc_feedback : feedback
~~~

Mỗi quan hệ cùng dataset; datasets là parent của cả 11 bảng con.

## Product / Custom BOM / Mixed

- PRODUCT bắt buộc exact PoC product+variant cùng product/dataset/sale_unit.
  Recipe tùy chọn; không tự khôi phục variant hoặc recipe thiếu.
- CUSTOM_BOM bắt buộc recipe_kind=CUSTOM/recipe_id, product_id/variant_id NULL.
  Không fake Product ID để biểu diễn công thức.
- Một order có đúng một customer, nhiều lines hai loại. FK sale→order buộc
  customer/status/payment khớp; chỉ COMPLETED/PAID là purchase hợp lệ.
- PRODUCT_ONLY/BOM_ONLY/BOTH tính từ history. Guest chưa xác định identity phải
  quarantine, không tạo một customer giả chung.
- Sale quantity là số đơn vị mua theo sale_unit, không phải gram khách đã uống.

Dataset origin: SYNTHETIC / STANDARD_CURRENT_PROXY / ACTUAL.
Dataset ACTUAL có thể chứa Product purchase thật nhưng feature recipe vẫn là
STANDARD_CURRENT_PROXY; recipe_kind/provenance phải nói rõ proxy.
Dataset STANDARD_CURRENT_PROXY chỉ PRODUCT, không giả định current BOM là
actual CUSTOM_BOM. SYNTHETIC không phụ thuộc 19.138 seed-linked events hiện tại.

## Family, version, content hash và snapshot actual

Family key là business grouping có taxonomy/rule version trong manifest.
Recipe version unique(dataset,family,version); không đồng nhất với family/hash
hay per-order snapshot. Hash chứa canonical Decimal, ingredient namespace/code,
role, unit scale/rule version, original/canonical quantities, waste riêng,
portion/basis/effective_time_unknown. Sort items để order-independent; không hash
family/version labels/research UUID. 10g→20g đổi hash. Cùng content khác
version/provenance có thể cùng hash nên recipe_hash là index, không UNIQUE.

Current BOM provenance phải có effective_time_unknown=true,
bom_observed_at_client/db và source refs/checksum; không backdate. Contract
enforce proxy không được tự tuyên bố historical effective time.

ACTUAL + CUSTOM recipe bắt buộc actual_recipe_snapshot_id. Snapshot ghi
source_snapshot_id và exact order/line; composite FK chống dùng snapshot line
khác hoặc recipe khác. Snapshot chỉ ACTUAL; SYNTHETIC không nhận actual snapshot.
Đây là contract chờ nguồn thật, chưa có actual data tạo trong task.

Tạo recipe envelope+items, validate nonempty/hash/unit/provenance rồi mới gắn
sale/snapshot. MySQL trigger chặn reference recipe chưa VALIDATED/nonempty hoặc
có item chưa VALIDATED. Recipe/items đã reference không được sửa thêm.
Đổi công thức tạo version mới. Snapshot actual/purchase fact không UPDATE:
conflict quarantine hoặc dataset mới. Chưa có full importer/publish validator.

## Unit, waste và feedback

Snapshot unit riêng tránh phụ thuộc Catalog mutable/cycle; chỉ lưu conversion
đã flatten có rule version/provenance, không lưu base-unit graph tự tham chiếu.

- MASS: g×1, kg×1000 →g; VOLUME: ml×1, l/L×1000 →ml.
- COUNT: count×1, không suy hộp24 thành 24 servings.
- Unknown/incompatible giữ conversion/canonical NULL và STAGED/QUARANTINED;
  VALIDATED bắt buộc conversion. Scale/symbol mới cần mở rộng có bằng chứng.
- Item unit/code snapshots và factor có composite FK với parent. Canonical
  quantity=ROUND(original×factor,8), contract HALF_UP; validated item không NULL.
- Không đổi g→ml thiếu density. Không cộng waste vào lượng phục vụ; waste>=0,
  range nghiệp vụ do validator review. Portion/servings có thể NULL, không suy
  từ batch/SKU/tên hộp. Synthetic basis chỉ được điền từ thiết kế rõ ràng.

RATING có rating 1..5. PREFERENCE_DISLIKE tách OPERATIONAL_RETURN; operational
reason chỉ logistics/quality được liệt kê, không được tự mang rating hoặc
preference-negative. Feedback unique(dataset,source_system,source_feedback_id).

## Idempotency, immutable publish và cleanup

- Order unique(dataset,source_system,source_order_id). Sale
  unique(dataset,order_id,order_item_id) độc lập revision/event. Thay event UUID
  hoặc tăng revision không thêm bản sao. Event-line unique là bảo vệ phụ.
- PocRepository chỉ SELECT, luôn explicit dataset/source_kind, research read chỉ
  PUBLISHED. reimport_decision: NEW/UNCHANGED/ImportConflict, không write/commit.
  Importer tương lai cần transaction/unique conflict reconciliation.
- STAGED→VALIDATED/REJECTED; VALIDATED→PUBLISHED/REJECTED. Identity source/name/
  version/hash/created không đổi. Validation/publish cần manifest
  validation_complete=true kiểu JSON boolean. Flag không thay validator chứng
  minh hash/count/content.
- 36 BEFORE triggers: child DML lock parent dataset FOR UPDATE, serialize
  authoring/publish. Chỉ STAGED INSERT/UPDATE; STAGED/REJECTED DELETE.
  VALIDATED/PUBLISHED data frozen; PUBLISHED không đổi/xóa hoặc mở lại.
- Writer phải transaction/lock dataset trước đọc/hash/validate, không dùng read
  snapshot cũ trước authoring. Phase 1B đã test race bằng hai connection thật,
  quan sát InnoDB LOCK WAIT và test forced timeout 1205→rollback→retry thành công.
  Không suy ra mọi workload không thể deadlock; writer tương lai cần retry toàn
  transaction có giới hạn cho 1205/1213, không retry lỗi business 1062/1644.
- Cleanup STAGED/REJECTED: feedback→sale_items→recipe_snapshots→recipe_items
  →recipes→orders→variants→products/ingredients→units/customers→datasets.
  Luôn scope dataset; FK RESTRICT chặn thứ tự sai. VALIDATED có thể reject.
- PUBLISHED rollback chỉ bỏ chọn dataset_id ở research caller/pointer, giữ
  snapshot lịch sử. Pointer chưa triển khai. Không xóa/sửa V1. Runtime writer
  không được có TRUNCATE/ALTER/DROP; DML triggers không chống privileged DDL/DBA.

## Migration và test

flyway/sql/V20261026203228__ingredient_poc_schema.sql: timestamp thực UTC+07,
sau source/live max 20261012194438; outOfOrder=false. Chỉ tạo PoC table/index/
trigger, không ALTER/DROP/V1 data/seed/cross-service FK. Không sửa lịch sử.

Target MySQL >=8.0.16. Phase 1B dùng MySQL 8.4.11 và Flyway CLI 13.7.0 thực thi
fresh 5 migrations và upgrade 4 lịch sử→1 PoC trên hai DB riêng trong container
local. DDL compound body với DELIMITER chạy thành công. History/checksum và
introspection so với metadata được kiểm tra trong runner.
Môi trường ghi sau này cần strict SQL mode và validator Decimal/length/hash;
không dựa vào implicit truncation/coercion của MySQL.
**MySQL/Flyway, constraints/triggers/concurrency: PASS trong isolated test.**
18 integration tests không SKIPPED. Bộ offline vẫn có placeholder skip để không
tự khởi động Docker từ unittest discovery; test MySQL thật chạy bằng runner riêng.
MySQL DDL auto-commit: nếu deployment sau này fail giữa chừng, dừng/review task
riêng; không blind rerun/repair/clean hoặc IF NOT EXISTS che drift.

SQLite memory thực thi CHECK/UNIQUE/FK và repository; static tests parity DDL/
metadata/guard definitions. MySQL thật kiểm thêm trigger và transaction/locking.
Chỉ fixtures kỹ thuật nhỏ, không có dataset 400/80/4000.

### Chạy integration MySQL an toàn

~~~powershell
services/Recommendation-Service/.venv/Scripts/python.exe -B services/Recommendation-Service/scripts/run_ingredient_poc_mysql_tests.py --shared-preflight <external-select-only-preflight.json>
~~~

Runner không nhận host/database/password production. Nó tạo container từ image
mysql:8.4 đã resolve digest, Docker endpoint local, datadir tmpfs, network bridge
mới có nhãn run riêng, inter-container communication tắt và host port chỉ
127.0.0.1. Network riêng này có NAT egress, không phải air-gap; không nối network/
volume production. Các gate kiểm ownership/mount/binding/server UUID trước DDL.
Database chỉ ingredient_poc_fresh_<run>/ingredient_poc_upgrade_<run>, không nhận
recommend_app. Credentials test ngẫu nhiên, private configs/env bị xóa sau run;
container/network/anonymous volumes thuộc run cũng được dọn, không global prune.

Flyway giữ locations/outOfOrder/naming của service nhưng khóa URL, schemas,
defaultSchema vào DB test, createSchemas=false và cleanDisabled=true. Cần override
tường minh vì service flyway.conf có defaultSchema/schemas=recommendation_db;
file cấu hình gốc không được sửa. Test không dùng credentials recommend.env.

Phase 1B phát hiện created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP không hợp lệ
trên MySQL. Đã sửa migration khi chưa áp dụng thành công ở mọi môi trường xác
minh được (live absent, fresh thất bại, instance đó được bỏ hoàn toàn). Default
MySQL nay CURRENT_TIMESTAMP(6); SQLite dùng CURRENT_TIMESTAMP qua compiler riêng.
Không sửa 4 migration lịch sử. Sau lần MySQL apply thành công, checksum migration
PoC được giữ nguyên; thay đổi triển khai tiếp theo phải dùng migration mới.

Introspection: 12 bảng, 146 cột, 42 CHECK, 28 FK, 36 UNIQUE, 17 index explicit,
36 BEFORE triggers. Có thêm 36 index backing UNIQUE và 28 FK-support indexes tự
sinh; không cộng chúng vào con số 17. 12 PRIMARY indexes tính riêng.

~~~powershell
$env:PYTHONPATH = 'services/Recommendation-Service'
services/Recommendation-Service/.venv/Scripts/python.exe -B -m unittest tests.test_ingredient_poc -v
python -B scripts/migrations/check_migration_naming.py --base-ref 7e507ab035af93efb98be2238b654e308f6392b9
python -B -m unittest discover -s scripts/migrations -p "test_*.py"
~~~

Nguồn chính thức:
[MySQL trigger syntax](https://dev.mysql.com/doc/refman/8.4/en/trigger-syntax.html),
[InnoDB locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).
[MySQL CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html).
Nguồn đối chiếu thiết kế; evidence Phase 1B cung cấp kết quả execution thực tế.

## External Service Flyway Warning

**Không tạo hoặc chỉnh sửa Flyway ngoài Recommendation-Service.**

Phase 1A synthetic không cần migration Catalog/Order/Inventory. Actual source
tương lai cần task Order-Service riêng lưu ingredient/quantity/unit/basis/revision/
hash bất biến theo order_item_id. Đường dẫn dự kiến:
services/Order-Service/src/main/resources/db/migration/V<actual_Asia_Bangkok_timestamp>__add_order_item_actual_recipe_snapshots.sql.
Placeholder chưa phải version được cấp; chỉ sinh timestamp khi task được phép.
**WAITING FOR USER APPROVAL** trong task riêng; không chặn synthetic Phase 1A,
nhưng thiếu nguồn đó ACTUAL import chưa sẵn sàng.

Catalog drift/thiếu historical migration từ Phase 0 giữ nguyên; không recovery/
repair để phục vụ task này. Nếu cần chỉnh unit/BOM hoặc recovery thì task riêng.

## Phase 2 — Synthetic Dataset

Generator deterministic, full validator, idempotent importer và publisher nằm
trong synthetic.py, synthetic_validation.py và synthetic_store.py. Hướng dẫn,
artifact contract và giới hạn: [SYNTHETIC_DATASET.md](SYNTHETIC_DATASET.md).
Không đổi schema/migration đã áp dụng. Không import vào recommend_app.

| Cohort | Customers | Product sale rows | Custom BOM sale rows |
|---|---:|---:|---:|
| Product-only | 120 | 720 | 0 |
| BOM-only | 120 | 0 | 1200 |
| Mixed | 160 | 880 | 1200 |
| Tổng | 400 | 1600 | 2400 |

Count là sale rows, không phải orders/quantity. Mixed: mỗi người 5 product+7 BOM,
thêm 1 row mỗi loại cho 80/160 người. Product-only 6 rows/người, BOM-only 10.
Có thể gom nhiều loại vào cùng order/customer; cohorts tính từ history.

80 recipes =20 families×4 content versions, có cùng ingredient khác lượng,
ingredient overlap, head/tail popularity và repeat. Product/variant/ingredient/
unit chỉ research namespace, bridge recipe_id cho Product↔BOM, không fake V1 IDs.

Random seed/generator version/content policy/UUID5 namespace/canonical
serialization ghi manifest để replay; clock không tham gia content deterministic.
Không sao chép PII/seed history hoặc gọi sync/V1 importer.

Temporal: fixed global T1/T2 UTC, whole order trước T1=Train, [T1,T2)=Validation,
sau T2=Test; không order cắt split/future feature leakage. Cold-start là chưa có
Train history, ví dụ 6 product-only+6 BOM-only+8 mixed xuất hiện ở Validation/Test.
Kiểm assertion đúng 400/80/1600/2400 và repeat/head-tail distribution.
Synthetic serving/basis phải được thiết kế tường minh, không mượn Catalog thiếu.

SYNTHETIC không tự trở thành evidence khách thật/actual taste/hiệu quả vận hành.
V1/Excel không đọc metadata này; không thêm API/Kafka/model artifact/serving flag.

## Data dictionary đầy đủ

<!-- GENERATED COLUMN DICTIONARY -->

Danh sách sinh từ metadata Phase 1B; CHECK/FK/index đầy đủ xem migration SQL.

### ingredient_poc_datasets

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_name | VARCHAR(128) | Không |  |
| schema_version | INTEGER | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| source_system | VARCHAR(64) | Không |  |
| source_snapshot_hash | VARCHAR(64) | Không |  |
| status | VARCHAR(16) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| validation_policy_version | VARCHAR(64) | Không |  |
| manifest_json | JSON | Không |  |

Unique/index: uq_ip_datasets_identity, uq_ip_datasets_kind.

### ingredient_poc_customers

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| customer_id | VARCHAR(36) | Không |  |
| source_system | VARCHAR(64) | Không |  |

Unique/index: ix_ip_customers_dataset, uq_ip_customers_identity, uq_ip_customers_tenant.

### ingredient_poc_ingredients

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| source_system | VARCHAR(64) | Không |  |
| source_ingredient_id | VARCHAR(36) | Có |  |
| ingredient_code | VARCHAR(100) | Không |  |
| name_snapshot | VARCHAR(255) | Không |  |
| provenance | JSON | Không |  |

Unique/index: ix_ip_ingredients_dataset, uq_ip_ingredients_code, uq_ip_ingredients_snapshot, uq_ip_ingredients_source, uq_ip_ingredients_tenant.

### ingredient_poc_products

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| source_system | VARCHAR(64) | Không |  |
| source_product_id | VARCHAR(36) | Có |  |
| product_code | VARCHAR(100) | Không |  |
| name_snapshot | VARCHAR(255) | Không |  |
| provenance | JSON | Không |  |

Unique/index: ix_ip_products_dataset, uq_ip_products_code, uq_ip_products_source, uq_ip_products_tenant.

### ingredient_poc_units

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| source_system | VARCHAR(64) | Không |  |
| source_unit_id | VARCHAR(36) | Có |  |
| symbol | VARCHAR(32) | Không |  |
| dimension | VARCHAR(8) | Không |  |
| canonical_unit | VARCHAR(8) | Có |  |
| conversion_factor | NUMERIC(18, 8) | Có |  |
| conversion_rule_version | VARCHAR(64) | Không |  |
| provenance | JSON | Không |  |
| validation_status | VARCHAR(16) | Không |  |

Unique/index: ix_ip_units_dataset, uq_ip_units_conversion, uq_ip_units_snapshot, uq_ip_units_symbol, uq_ip_units_tenant.

### ingredient_poc_orders

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| source_system | VARCHAR(64) | Không |  |
| source_order_id | VARCHAR(36) | Không |  |
| customer_id | VARCHAR(36) | Không |  |
| order_status | VARCHAR(16) | Không |  |
| payment_status | VARCHAR(16) | Không |  |

Unique/index: ix_ip_orders_dataset, uq_ip_orders_business, uq_ip_orders_customer, uq_ip_orders_tenant.

### ingredient_poc_variants

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| source_system | VARCHAR(64) | Không |  |
| source_variant_id | VARCHAR(36) | Có |  |
| product_id | VARCHAR(36) | Không |  |
| sku | VARCHAR(100) | Không |  |
| name_snapshot | VARCHAR(255) | Không |  |
| sale_unit_id | VARCHAR(36) | Không |  |
| provenance | JSON | Không |  |

Unique/index: ix_ip_variants_dataset, uq_ip_variants_product, uq_ip_variants_sku, uq_ip_variants_source, uq_ip_variants_tenant.

### ingredient_poc_recipes

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| recipe_family_key | VARCHAR(128) | Không |  |
| recipe_version | INTEGER | Không |  |
| recipe_kind | VARCHAR(24) | Không |  |
| base_product_id | VARCHAR(36) | Có |  |
| base_variant_id | VARCHAR(36) | Có |  |
| base_sale_unit_id | VARCHAR(36) | Có |  |
| portion_quantity | NUMERIC(24, 8) | Có |  |
| portion_unit_id | VARCHAR(36) | Có |  |
| portion_unit | VARCHAR(32) | Có |  |
| servings_per_recipe | NUMERIC(24, 8) | Có |  |
| recipe_hash | VARCHAR(64) | Không |  |
| provenance | JSON | Không |  |
| validation_status | VARCHAR(16) | Không |  |

Unique/index: ix_ip_recipes_dataset, ix_ip_recipes_family, ix_ip_recipes_hash, uq_ip_recipes_kind, uq_ip_recipes_tenant, uq_ip_recipes_version.

### ingredient_poc_recipe_items

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| recipe_id | VARCHAR(36) | Không |  |
| ingredient_id | VARCHAR(36) | Không |  |
| ingredient_code_snapshot | VARCHAR(100) | Không |  |
| ingredient_role | VARCHAR(32) | Có |  |
| quantity_original | NUMERIC(24, 8) | Không |  |
| unit_id | VARCHAR(36) | Không |  |
| unit_symbol_snapshot | VARCHAR(32) | Không |  |
| quantity_canonical | NUMERIC(24, 8) | Có |  |
| canonical_unit | VARCHAR(8) | Có |  |
| canonical_factor | NUMERIC(18, 8) | Có |  |
| waste_rate | NUMERIC(9, 4) | Có |  |
| validation_status | VARCHAR(16) | Không |  |

Unique/index: ix_ip_recipe_items_dataset, ix_ip_recipe_items_ingredient, uq_ip_recipe_items_ingredient, uq_ip_recipe_items_tenant.

### ingredient_poc_recipe_snapshots

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| recipe_id | VARCHAR(36) | Không |  |
| recipe_kind | VARCHAR(24) | Không | 'CUSTOM' |
| order_id | VARCHAR(36) | Không |  |
| order_item_id | VARCHAR(36) | Không |  |
| source_system | VARCHAR(64) | Không |  |
| source_snapshot_id | VARCHAR(128) | Không |  |
| observed_at | DATETIME(6) | Không |  |
| payload_hash | VARCHAR(64) | Không |  |
| provenance | JSON | Không |  |

Unique/index: ix_ip_recipe_snapshots_dataset, uq_ip_recipe_snapshots_tenant, uq_ip_snapshots_line, uq_ip_snapshots_reference, uq_ip_snapshots_source.

### ingredient_poc_sale_items

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| customer_id | VARCHAR(36) | Không |  |
| order_id | VARCHAR(36) | Không |  |
| order_item_id | VARCHAR(36) | Không |  |
| purchase_type | VARCHAR(16) | Không |  |
| product_id | VARCHAR(36) | Có |  |
| variant_id | VARCHAR(36) | Có |  |
| recipe_id | VARCHAR(36) | Có |  |
| recipe_kind | VARCHAR(24) | Có |  |
| actual_recipe_snapshot_id | VARCHAR(36) | Có |  |
| sale_unit_id | VARCHAR(36) | Không |  |
| quantity | NUMERIC(24, 8) | Không |  |
| purchased_at | DATETIME(6) | Không |  |
| order_status | VARCHAR(16) | Không |  |
| payment_status | VARCHAR(16) | Không |  |
| source_system | VARCHAR(64) | Không |  |
| source_event_id | VARCHAR(36) | Có |  |
| source_revision | INTEGER | Không |  |
| payload_hash | VARCHAR(64) | Không |  |

Unique/index: ix_ip_sale_items_dataset, ix_ip_sales_customer_time, ix_ip_sales_recipe_time, uq_ip_sale_items_tenant, uq_ip_sales_business, uq_ip_sales_event_line.

### ingredient_poc_feedback

| Cột | MySQL type | NULL | Default / key |
|---|---|---|---|
| id | VARCHAR(36) | Không | PK; UUID do caller/Python tạo |
| dataset_id | VARCHAR(36) | Không |  |
| source_kind | VARCHAR(24) | Không |  |
| created_at | DATETIME(6) | Không | CURRENT_TIMESTAMP(6) |
| sale_item_id | VARCHAR(36) | Không |  |
| source_system | VARCHAR(64) | Không |  |
| source_feedback_id | VARCHAR(128) | Không |  |
| feedback_type | VARCHAR(24) | Không |  |
| rating | INTEGER | Có |  |
| return_reason | VARCHAR(64) | Có |  |
| feedback_time | DATETIME(6) | Không |  |

Unique/index: ix_ip_feedback_dataset, ix_ip_feedback_time, uq_ip_feedback_source, uq_ip_feedback_tenant.

