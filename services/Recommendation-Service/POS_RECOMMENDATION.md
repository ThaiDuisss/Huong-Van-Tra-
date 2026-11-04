# Product V1 trong POS

Source hỗ trợ entry **AI gợi ý sản phẩm → Xem sản phẩm gợi ý** dưới khách hàng
ở cả bán tại quầy và giao hàng. Popup dùng đúng khách/tab hiện tại, tải khi mở,
không lưu recommendation vào localStorage. Màn riêng là
`/system/ai/product-recommendations`; route `/system/ai/ingredient-demo` redirect
sang màn mới. Backend/libraries/artifacts nghiên cứu BOM và official AI Reports
vẫn giữ nguyên.

## API và bảo mật

- Thành viên: GET `/api/recommendation/recommendations/{customer_uuid}?mode=DISCOVERY&limit=5`.
  JWT qua shared client; Gateway kiểm JWT và ký HMAC v2; `authorize_customer`
  giữ ownership hoặc staff allowlist hiện hữu. Mỗi logical fetch có UUID
  Idempotency-Key; refresh/mở lại tạo key mới. Transport retry cùng fetch phải
  giữ key. GET thành viên ghi snapshot trước response như trước.
- Khách lẻ: GET `/api/recommendation/recommendations/guest?limit=5`.
  Static route đứng trước UUID route. `authorize_pos_actor` dùng `verify_context`
  và giao giữa role trong JWT, `RECOMMENDATION_STAFF_ROLES` và các role POS hiện có
  `SALE_STAFF,MANAGER,SUPER_ADMIN`. Không nhận customerId và không bỏ HMAC.
  Chưa cấu hình grant thì fail closed. Không cấp quyền chỉ vì JWT đăng nhập hợp lệ.
- Guest chỉ SELECT global `V1RecommendationRepository.popularity(db,set(),limit)`;
  không đọc customer history, không tạo khách/snapshot, không commit DML.
  Limit tối đa 5, tối thiểu 1; lỗi DB trả 503 đã sanitize.

Ví dụ response guest (items có thể rỗng hoặc ít hơn 5):

```json
{
  "customerId": null,
  "source": "POPULARITY",
  "modelType": "popularity",
  "items": []
}
```

Mỗi item giữ contract V1: `rank`, `productId`, `productCode`, `name`, `score`.
Không dùng score làm phần trăm phù hợp. Không thay response thành viên, ngưỡng
2 PURCHASE orders, công thức Hybrid 60/40, DISCOVERY/REPEAT hoặc fallback.

## Cấu hình operator cần xác nhận

Không có thay đổi `.env` tự động. Người vận hành cần xác nhận và áp dụng cấu hình
server-side cho các role POS được phép; nếu cả ba role đã được duyệt thì dùng:

```text
RECOMMENDATION_STAFF_ROLES=SALE_STAFF,MANAGER,SUPER_ADMIN
```

Không tạo role mới. Gateway phải nhận đúng realm roles trong JWT, JWT subject UUID,
issuer/JWK/expiry hợp lệ. Cần upstream Recommendation đúng (`RECOMMENDATION_SERVICE_URL`),
secret server-side explicit đủ mạnh và khớp hai bên, đồng bộ đồng hồ, Redis replay
reachable (production bắt buộc Redis). Không đặt HMAC secret vào VITE hoặc browser.
Actor POS không cần mượn customer claim của khách được chọn. Ownership cho actor khác
vẫn giữ nguyên. Không bật internal caller/sync/Kafka để vượt lỗi hoặc lấy dữ liệu.

Sau thay đổi source/config, operator cần nạp lại đúng tiến trình API theo quy trình
được phép; task triển khai không tự restart shared service. Không bật migrations,
SQL/storage init khi chỉ xác minh runtime. Không đổi credentials hoặc database để
giả thành công nếu DB chưa kết nối.

## Dữ liệu và thêm hàng

Recommendation DB tiếp tục là bộ dữ liệu demo do người dùng nạp thủ công.
UUID customer phải trùng UUID Customer/POS; PURCHASE events có distinct order IDs
thật trong hợp đồng dữ liệu demo, events/aggregates nhất quán. Parent product và
variant IDs phải khớp catalog POS. Không mapping bằng tên hoặc SKU gần đúng.
Artifact/catalog fingerprint cần hợp lệ; không train lại trong task tích hợp.

V1 trả parent productId. FE lấy `/pos/products?productId=...` (tối đa 50 variants)
và recheck chi tiết variant trước Add. Một biến thể bán được thì chọn sẵn; nhiều
biến thể thì phải chọn rõ đơn vị/giá; không có giá hợp lệ hoặc hết hàng thì disable
Add từ AI. Giá/ảnh lấy từ POS. Request metadata dùng `cache:no-store` và `_fresh=1`
để bỏ qua PWA catalog cache; normal offline catalog search vẫn dùng policy cũ.
Khi triển khai FE cần cập nhật service worker/bundle mới trước khi kiểm chứng.

Add gọi action POS hiện hữu, giữ tính giá, stock/backorder/checkout/payment/loyalty
phía POS/BE. Lock đồng bộ chặn thay khách/tab, tạo/đóng tab và checkout khi Add đang
chờ. Response Add không tự retry. Khi timeout/outcome chưa rõ, cần đọc lại đơn
trước khi chủ động thêm lại; không coi thông báo lỗi là bằng chứng mutation chưa xảy ra.

## Chạy thử thủ công sau khi runtime/dataset được xác nhận

1. Đăng nhập bằng account có role POS đã cấp grant; mở POS. Không cần demo-token.
2. Không chọn khách → mở popup → Popularity. Chưa bấm thì không có request AI.
3. Tìm tên/SĐT ở picker hiện hữu (server `q`, page size 20, Xem thêm), chọn khách
   0/1/2 đơn trong dataset → mở popup, đối chiếu source/fallback thực tế.
4. Chọn nhiều tab/khách, refresh, đóng tab cuối, hoàn tất hóa đơn: popup cũ phải
   đóng và kết quả cũ không được phục hồi nhầm. Add pending thì không đổi context.
5. Chọn biến thể, kiểm giá/tồn rồi Add. Hóa đơn nhận đúng variant. Kiểm timeout,
   401/403/503/empty, sản phẩm không còn bán; lỗi AI không khóa thanh toán.
6. Màn Product riêng có thể chọn khách lẻ hoặc DISCOVERY/REPEAT cho thành viên.
   Không có Add vào một hóa đơn không xác định từ màn quản trị riêng.
7. Kiểm xuất Excel PRODUCT/CUSTOM_BOM như trước. Guest không được đưa vào member
   snapshot metrics; generated response cũng không tự chứng minh impression/conversion.

Các thao tác tạo snapshot/giỏ/đơn trên shared DB chỉ chạy sau khi được xác nhận
phạm vi ghi. Unit/integration tests phải dùng SQLite/mocks/temp fixtures và
`APP_ENVIRONMENT=test`, replay memory, secret fixture riêng, datasource loopback cô lập.
