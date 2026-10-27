# Ingredient Recommendation research V1 — Phase 3

Model offline chạy thật trên artifacts Phase 2. Module độc lập
app/ingredient_poc/recommendation; không đăng ký vào app.main, public API,
Hybrid Product V1, Kafka hoặc Excel. Chỉ dùng numpy và dependencies hiện có.
Không LightFM/SVD/deep learning, không hyperparameter search.

## Input và lifecycle

ResearchDataset.load yêu cầu dataset.json và SHA-256 tường minh; kiểm checksum
manifest/validation receipt, nguồn SYNTHETIC, table/content fingerprints, recipe
hashes/units/basis và Product→Recipe bridge. Không mở evaluation.json. Validation
receipt chỉ dùng trạng thái/source checksum, không dùng latent quality metrics.

Data envelope STAGED là input replay của Phase 2, không phải bằng chứng dataset
chưa từng publish: Phase 2 đã xác minh PUBLISHED trên isolated MySQL trước cleanup.
Phase 3 chỉ đọc projection đã checksum, không cần khởi động Docker/MySQL.

Model version ingredient-research-v1, feature version role-log-serving-v1,
ranking fixed-ranking-v1. Artifact JSON riêng, không pickle; metadata có dataset
ID/file checksum/source hash, train cutoff/counts/fit-data hash/config và model
fingerprint. Save tạo file mới ngoài repo; cấm tên serving artifacts. Load yêu cầu
model SHA-256 và source/config khớp. Có thể rebuild deterministic từ input bất biến.

## Features và taste profile

Mỗi ingredient không phải nước là một feature dimension. Với lượng canonical
q trên một serving, x = role_weight × log1p(q/role_scale_g).

| Role | Scale | Weight |
|---|---:|---:|
| BASE_TEA | 20g | 1,0 |
| AROMA | 3g | 1,1 |
| SWEETENER | 15g | 0,6 |
| SUPPLEMENT | 2g | 0,8 |
| DILUENT | Bỏ khỏi similarity | 0 |

Quantity canonical /servings_per_recipe; chỉ basis synthetic 500ml/1 serving
được hỗ trợ ở V1 này. Không cộng waste, không đổi g→ml. Summary vẫn giữ nước
400ml để giải thích BOM. Scale là rule vật lý cố định, không fit từ Test/latent.
Không L2-normalize recipe vector: lượng 10g và 20g, kể cả uniform mass multiplier,
vẫn khác representation và distance. Ingredient vắng mặt có feature 0.

Một customer có một profile chung. Mỗi order×recipe lấy signal lớn nhất giữa
PRODUCT suy diễn (confidence 0,75) và CUSTOM_BOM synthetic (1,0), purchase quantity
cap 3. Recipe interest = min(3, log1p(tổng order signals)); profile là trung bình
feature vector theo interest. Repeat tăng quan tâm nhưng bị nén/cap; PRODUCT
không bị diễn giải thành actual custom recipe khách tự chọn.

CB = exp(-0,5 × squared Euclidean distance giữa profile và recipe). Điểm [0,1],
giữ magnitude. Product dùng exact recipe bridge, không suy từ tên hoặc Catalog live.

Feedback chỉ trước as_of và sale liên quan cũng trước as_of. PREFERENCE_DISLIKE
nhân final score recipe và Product bridge tương ứng với 0,5. OPERATIONAL_RETURN
không làm giảm taste; RATING chưa dùng ở V1 này. Không đọc latent preferences.

## CF và unified ranking

CF identity là recipe content instance, không collapse family. Train chỉ PURCHASE
trước 2026-03-01T00:00:00Z: 2660 rows, 380 customers, 76 recipes. Interaction
matrix dùng cùng bounded interest, PRODUCT mapping vẫn là inferred evidence.
Item cosine × shared_customers/(shared_customers+5), support item/pair >=2;
diagonal 0. Không dùng Validation/Test để fit matrix hoặc popularity.

CF query cần >=2 orders và >=2 supported recipe IDs trong history trước as_of.
Raw CF là weighted mean các neighbor similarities. Chia bởi max raw CF của toàn
bộ available recipe set của query, trước mode/policy filter. CB/CF/popularity đều
trên [0,1] trước trộn; không ghép score khác thang một cách tùy tiện.

- Có profile và CF support: 0,70 CB +0,25 CF +0,05 typed Train popularity.
- Có profile nhưng recipe/query không có CF support: 0,95 CB +0,05 popularity.
- Không có history trước as_of: Train popularity.
- Popularity = distinct Train customers mua typed item /max support toàn bộ typed
  items Train. Không lấy lượt mua tương lai để fallback.

Đây là weight riêng cho research, không thay Product Hybrid V1 60/40. Không tối
ưu theo Validation/Test. Fallback calibration là rule cố định, chưa chứng minh
tối ưu; metric thấp được báo nguyên trạng.

Scoring tất cả candidate PRODUCT/CUSTOM_BOM hợp lệ một lần rồi global sort.
UNIFIED giữ candidate score cao hơn cho cùng recipe content, loại duplicate
typed identity, không quota loại. PRODUCT/CUSTOM_BOM mode lọc type trên cùng
scoring scale. DISCOVERY loại content đã mua ở cả hai domain; REPEAT giữ cả seen
và unseen, không bắt buộc chỉ trả item từng mua. Tie-break type/ID ổn định.

Candidate cần VALIDATED, supported source/basis và available_at<=as_of. Model chỉ
dùng ingredient columns đã biết ở Train; ingredient mới ngoài contract cần rebuild.
Kết quả luôn research_only=true/production_approved=false; không phê duyệt bán thật.
Nếu không đủ eligible candidates, trả số thực tế cùng INSUFFICIENT_ELIGIBLE_CANDIDATES,
không fill bằng item sai hoặc NaN.

## CLI

Entry point chặn socket.connect/subprocess startup. Không import ORM/session,
không đọc recommend.env, không nhận DB URL. Phải có as_of timezone-aware,
Train cutoff<=as_of<=2026-05-01 UTC. Customer argument là customers.id nghiên cứu.

```powershell
$ingredientDataset = 'E:/Huong-Van-Tra-Backup/codex-reports/2026-10-27/180150_ingredient-recommendation-phase2-synthetic-dataset-validation-import_evidence/synthetic-dataset/dataset.json'
$ingredientSha = 'c097fc2860a7d5206e0479ac536097ac13fe1517e030148107c996dd0cfe4b6a'
services/Recommendation-Service/.venv/Scripts/python.exe -B services/Recommendation-Service/scripts/run_ingredient_research.py run --dataset $ingredientDataset --dataset-sha256 $ingredientSha --output-dir <new-external-research-directory>
```

run fit một model, save/reload, demo 4 cohort rồi sanity Validation/Test; không
tự chỉnh config theo metrics. build chỉ build artifact. infer, demo, evaluate
load model đã có bằng --model/--model-sha256. Output file mới dùng --output,
mặc định JSON stdout. Ví dụ infer:

```powershell
services/Recommendation-Service/.venv/Scripts/python.exe -B services/Recommendation-Service/scripts/run_ingredient_research.py infer --dataset $ingredientDataset --dataset-sha256 $ingredientSha --model <research-model.json> --model-sha256 <sha256> --customer <research-customer-id> --mode CUSTOM_BOM --top-k 5 --policy DISCOVERY --as-of 2026-04-01T00:00:00Z
```

Mỗi row có customer/type/item/recipe ID, score/rank/reason, model/dataset/as_of,
component scores/profile source counts; BOM có family và ingredient quantity/unit
theo serving. Demo chọn customer từ history, không hardcode IDs trong inference.

## Sanity evaluation và leakage

Frozen-boundary protocol: Validation prediction tại T1, labels [T1,T2); Test tại
T2, labels [T2,2026-05-01). Model CF/popularity vẫn chỉ Train; Test profile có thể
dùng history Validation đã xảy ra trước T2, không dùng Test interactions/feedback.
Không sequential update trong held-out interval. Chỉ một thuật toán/config.

UNIFIED/REPEAT Top-5, strict typed-ID relevance, distinct labels mỗi customer.
Precision chia 5; Recall chia số distinct relevant typed items; NDCG binary;
macro-average khách có labels. Coverage = distinct recommended typed IDs /140
eligible typed candidates. Dedup content không đổi labels thành recipe/family hit.
Cohort full history chỉ phục vụ stratification, không fit hoặc profile feature.

COLD_TRAIN là không có Train history; tại Test một phần nhóm này đã có Validation
history nên fallback rate có thể <100%. Báo rõ denominator. Cross-domain counts
có cả explicit opposite-domain mode và item types xuất hiện trong UNIFIED.

Metrics thấp không chặn Phase 3 nếu inference, provenance và leakage đúng; không
đổi weight/algorithm để tối ưu sau khi xem Test. Dataset synthetic có coverage
anchors và preference sampler, không phải bằng chứng hiệu quả với khách thật.

## Tests và giới hạn

tests/test_ingredient_research.py: end-to-end CLI, profiles/cross-domain/mixed/cold,
quantity/unit/water/waste, CF/fallback, policy/dedup/determinism, future mutations,
feedback, dataset/model guards và sanity metrics. Full regression dùng runner
run_offline_evaluation_tests.py có network guard và external temporary fixtures.

Không tạo/sửa migration, không Flyway ngoài Recommendation-Service; không SQL/DB,
Kafka, serving API, Product artifact hoặc Excel writes. Phase 4/integration cần
task riêng sau khi review nghiên cứu; data gate live Phase 0 vẫn chưa được giải quyết.
