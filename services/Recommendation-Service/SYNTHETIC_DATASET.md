# Ingredient PoC — Phase 2 Synthetic Dataset

Generator `synthetic-v2.1.0`, policy `synthetic-validation-v2.1.0`, default seed
20261027. Không có training/serving/API/Kafka hoặc ghi shared recommend_app.
Migration Phase 1A/1B giữ nguyên byte/checksum; không cần migration mới.

## Dataset contract

| Cohort suy từ history | Customers | PRODUCT rows | CUSTOM_BOM rows |
|---|---:|---:|---:|
| Product-only | 120 | 720 | 0 |
| BOM-only | 120 | 0 | 1200 |
| Mixed | 160 | 880 | 1200 |
| Tổng | 400 | 1600 | 2400 |

2520 orders, gồm 880 mixed orders. Một customer identity, không lưu customer_type.
60 products/60 variants, 13 ingredients, 5 unit snapshots, 80 recipes thuộc
20 families ×4 versions. Recipe là CUSTOM có provenance SYNTHETIC, không phải
công thức được cửa hàng phê duyệt. PRODUCT có exact product/variant và recipe
bridge; CUSTOM_BOM không mang product_id/variant_id giả. Tất cả source dimension
IDs NULL, source_system=research:hvtra-synthetic-v2; chỉ research UUID5.

Recipe gồm trà nền (4 loại), hương (5 loại), đường, nước; một số có gừng/vỏ cam.
Các version trong family có cùng ingredient set, đường 5/10/15/20g, hash khác
nhau. g/kg và ml/L canonicalize độc lập, không cộng mass với volume. Nước đầu
vào 400ml; output synthetic được thiết kế 500ml/1 serving, không suy output từ
khối lượng đầu vào. Sale unit=count, quantity là số serving được mua theo thiết
kế; không phải bằng chứng lượng khách thật đã uống. Waste 0,5% lưu riêng, không
nhân vào lượng nguyên liệu phục vụ hoặc hash quantity thay thế.

## Hành vi và evaluation sidecar

Mỗi khách có latent weights liên tục trên strength, sweetness, floral, tỷ trọng
trà nền trong dry ingredients và complexity. Một nhóm có drift ngọt nhẹ sau
Train. Sampling theo utility ×4, head-tail prior và 20% cơ hội exploration trước
override repeat/coverage; tỷ lệ EXPLORE thực tế phải được validator đo.
Không gán family như nhãn sở thích hoàn hảo. Các history khác nhau, có repeat
qua order distinct và mua ngoài utility sampling.

Có 76 anchors cho coverage Train và 80 anchors cho coverage toàn thời gian;
phải báo rõ đây là cấu trúc synthetic có chủ đích. Không dùng các anchors để
tuyên bố hiệu quả thực tế. Head/tail, support và overlap được đo từ history;
validator yêu cầu overlap Train riêng, không dùng toàn lịch sử thay Train.

`evaluation.json` chứa profiles/choice diagnostics, phục vụ đánh giá generator.
Không ghi latent weights/cohort design/choice utility vào tables, recipe features
hoặc manifest feature serving. DB manifest chỉ lưu SHA-256 của sidecar để truy
vết. Utility lift trong report là chẩn đoán sampler, không phải model performance.

## Determinism và artifacts

Identity dùng UUID5 namespace cố định trong code, seed + version + config.
Không dùng wall-clock cho IDs/rows/timestamps. Decimal và UTC canonical JSON,
rows sort theo ID, manifest có table checksums, content fingerprint và sidecar
hash. Source checksum kết hợp fingerprint + evaluation hash. Khác seed có
dataset/entity IDs riêng. Đổi policy/content cần đổi generator version.

Artifact directory được tạo mới ngoài repository, không overwrite:

- dataset.json: dataset envelope + 11 child projections, không latent sidecar.
- evaluation.json: latent profiles/diagnostics nghiên cứu riêng.
- manifest.json, validation.json, artifact-checksums.json.

Dry-run không đọc env/database và không tạo engine:

```powershell
services/Recommendation-Service/.venv/Scripts/python.exe -B services/Recommendation-Service/scripts/generate_ingredient_poc_synthetic.py --dry-run --seed 20261027 --output-dir <new-external-directory>
```

Để replay: đọc dataset.json, gắn evaluation từ evaluation.json rồi gọi
synthetic.loads(canonical_json(payload)); validate lại. Hai file phải giữ cùng
checksum manifest. Sidecar có thể bị tách khỏi feature reader; khi xác minh
nguồn đầy đủ vẫn cần sidecar đúng hash.

## Temporal và feature access

UTC cố định: Train <2026-03-01T00:00:00Z; Validation từ mốc đó đến trước
2026-04-01T00:00:00Z; Test đến trước 2026-05-01T00:00:00Z. Whole order cùng
timestamp/split. 20 cold customers: 6 product-only, 6 BOM-only, 8 mixed;
10 bắt đầu Validation, 10 bắt đầu Test. 4 cold recipes được giới thiệu từ T1,
không có Train purchase. 380 khách warm, 76 recipes available trong Train.

`split_view(bundle, 'TRAIN')` chỉ trả Train sales, recipe/item dimensions available
trước T1, không trả latent sidecar/feedback. Muốn fit profile/popularity/CF phải
dùng view Train; không tự nối Validation/Test vào training. Validation/Test view
không tự chứa history Train. Metadata split không chứng minh một thuật toán
tương lai không leakage; Phase 3 phải giữ và kiểm thử as-of contract này.

## Isolated importer và publisher

Chỉ runner `run_ingredient_poc_mysql_tests.py --synthetic` tạo và xác minh Docker
MySQL riêng. Không có arbitrary-host/import-to-shared CLI. Target chỉ loopback,
port ngẫu nhiên (từ chối 3306/33060), database ingredient_poc_fresh/upgrade_<run>,
server UUID khác shared, test writer username theo run. Connection kiểm identity,
MySQL 8.4 và strict SQL mode trước giao dịch. Container datadir tmpfs, không
production mounts, private bridge, host bind 127.0.0.1. Bridge NAT không air-gap.

Root test fixture chạy Flyway và cấp SELECT/INSERT/UPDATE từng bảng PoC cho
writer. Writer không có DELETE/DDL/V1 access. Không dùng production credentials.
Python guard không phải ranh giới chống DBA; ownership/endpoint được runner
verify trước cấp quyền và trước DDL. Không nhận một shared server schema khác
làm môi trường độc lập.

Importer validate toàn bundle trước network write, transaction lock dataset
trước read/hash/authoring. Thứ tự: dataset STAGED; units/ingredients/products/
customers; variants/orders; recipe envelopes STAGED; recipe items VALIDATED;
recipes VALIDATED; sale items; feedback; readback + full validation. Commit
dataset STAGED sau khi toàn bộ counts/checksums khớp. Không INSERT IGNORE/REPLACE.

Reimport cùng identity/checksum và toàn rows bằng nhau trả UNCHANGED, kể cả
PUBLISHED; khác payload/source/row checksum trả ImportConflict, không overwrite.
Partial/import failure rollback toàn transaction. Không sửa/xóa dataset khác.

Publisher lock cùng dataset, đọc toàn bộ rows từ DB, đối chiếu source/manifest,
chạy full validator rồi chuyển STAGED→VALIDATED→PUBLISHED trong cùng transaction.
Manifest lưu validation_complete boolean và validation_report_hash. Sai row hay
manifest thì không publish, dù đã có validation flag. Dataset frozen giữ đúng
validation receipt; post-publish SELECT kiểm lại source/table checksums.

Retry toàn transaction tối đa 3 lần chỉ lỗi 1205/1213; rollback trước fresh
transaction. 1062/1644 và semantic conflicts không retry thành công bằng cách
bỏ ràng buộc. Không tuyên bố mọi deadlock không thể xảy ra. Trigger freeze của
Phase 1B vẫn giữ nguyên; PUBLISHED không mở lại/chèn/sửa/xóa.

```powershell
services/Recommendation-Service/.venv/Scripts/python.exe -B services/Recommendation-Service/scripts/run_ingredient_poc_mysql_tests.py --shared-preflight <external-select-only-preflight.json> --synthetic
$env:PYTHONPATH = 'services/Recommendation-Service'
services/Recommendation-Service/.venv/Scripts/python.exe -B -m unittest tests.test_ingredient_poc_synthetic -v
```

Runner tạo schema fresh/upgrade, chạy 18 tests Phase 1B rồi full Phase 2 tests,
lưu sanitized evidence và dataset artifacts ngoài repo. Thêm seed khác, rollback
probe và retry fixture chỉ để kiểm isolation; counts 400/80/4000 luôn scope
dataset chính. Cuối run xóa đúng container/network có ownership label; dataset
JSON/checksums còn lại để replay, không giữ một research server chạy sẵn.

## Phase 3

Dataset hỗ trợ nghiên cứu Ingredient Content-Based và Recipe/Family CF với
Train-only support. Phase 3 đã có model Ingredient research V1, CLI Top-5 và
sanity evaluation offline; xem [INGREDIENT_RESEARCH_V1.md](INGREDIENT_RESEARCH_V1.md).
Chưa tích hợp serving production. Data Gate dữ liệu thật Phase 0 vẫn PARTIAL; SYNTHETIC
không giải quyết identity Catalog–Recommendation, serving live hoặc actual
recipe snapshots. Không có migration ngoài Recommendation-Service; upstream
ACTUAL snapshot và Catalog recovery vẫn là task riêng cần phê duyệt.
