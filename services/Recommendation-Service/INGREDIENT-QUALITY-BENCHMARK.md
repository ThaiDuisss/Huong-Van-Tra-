# HVTRA-AI-INGREDIENT — QUICK QUALITY BENCHMARK

**Kết luận** `DATASET_INSUFFICIENT`  
**Đối soát** 09:33 ngày 27/11/2026 (GMT+7), trên shared `recommend_app` ở chế độ chỉ đọc và Catalog/Inventory live.

## Dataset

| Chỉ số | Kết quả |
|---|---:|
| Khách hàng có receipt đã xác thực | 3 |
| Đơn hoàn tất / đơn còn hợp lệ sau hoàn tại thời điểm đối soát | 18 / 17 |
| Đơn hợp lệ theo khách | 5, 6, 6 |
| Dòng hoàn đã áp dụng | 3 |
| Dòng PRODUCT / dòng PRODUCT có tham chiếu phiên bản BOM | 19 / 0 |
| Đơn CUSTOM_BUNDLE có components / nguyên liệu được khách chọn trực tiếp | 0 / 0 |
| Nguyên liệu Catalog / đủ điều kiện bán lẻ / có tồn WAREHOUSE hiện tại | 83 / 23 / 20 |

## Phương pháp và cơ hội đánh giá

Dùng đơn kế tiếp của từng khách làm nhãn. Cutoff đặt ngay trước lúc đơn đó hoàn tất. Lịch sử đầu vào chỉ gồm receipt có thời điểm hoàn tất **và** thời điểm được Recommendation ghi nhận trước cutoff. Trừ các khoản hoàn đã được ghi nhận trước cutoff, rồi áp ngưỡng 5 đơn còn hiệu lực. Baseline serving là `0.85B + 0.15C`, với `B` là Behavioral Score và `C` là Catalog BOM Prior.

- **3** đơn thứ sáu có ít nhất 5 đơn hoàn tất trước đó. **2** đơn đạt ngưỡng tại cutoff. Ở trường hợp còn lại, cả 5 receipt trước đó chưa được Recommendation ghi nhận tại cutoff.
- **BOM proxy** có 2 giao dịch đích PRODUCT qua ngưỡng. Cả hai thiếu tham chiếu phiên bản BOM, và dữ liệu đối soát thiếu snapshot BOM, giá bán lẻ và tồn WAREHOUSE theo cutoff. Dùng Catalog hiện tại cho lịch sử hoặc nhãn sẽ đưa dữ liệu sau cutoff vào phép đo. **0 ca** đủ điều kiện đánh giá.
- **Lựa chọn trực tiếp** có **0 ca** từ `CUSTOM_BUNDLE.components`. Vì thiếu nhãn lựa chọn trực tiếp, benchmark dừng ở initial ranking.

## Metric và so sánh baseline

| Phương pháp | Precision@5 | Recall@5 | NDCG@5 | MAP@5 | HitRate@5 | Ingredient Coverage |
|---|---|---|---|---|---|---|
| Serving `0.85B + 0.15C` | N/A | N/A | N/A | N/A | N/A | N/A |
| Popularity Only `C` | N/A | N/A | N/A | N/A | N/A | N/A |
| Behavioral Score Only `B` | N/A | N/A | N/A | N/A | N/A | N/A |

`N/A` nghĩa là 0 cơ hội đánh giá hợp lệ. Ba phương pháp cần cùng tập ứng viên tại từng cutoff. Ingredient Coverage dùng hợp các nguyên liệu đủ điều kiện tại các cutoff làm mẫu số. Hàm tính metric xếp hạng hiện có đã được đối chiếu và chưa chạy trên tập rỗng. [Hàm metric](offline_benchmark/metrics/ranking_metrics.py)

## Giới hạn và kết luận

18 receipt tập trung trong khoảng 00:46–00:59 ngày 26/11/2026 (GMT+7). Log gợi ý Ingredient sớm nhất vào 01:26 ngày 27/11/2026, sau toàn bộ giao dịch trong mẫu. Các số liệu Catalog/Inventory mô tả trạng thái lúc đối soát. Kết luận dùng cho Technical Report là **`DATASET_INSUFFICIENT`**. Hai ca đạt ngưỡng lịch sử vẫn thiếu dữ liệu theo cutoff để tái lập ranking và ground truth BOM. Chất lượng dự đoán lựa chọn trực tiếp chưa đo được.
