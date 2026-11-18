# Kế hoạch thu gọn scope Customer-Service

> Trạng thái: **Đã triển khai** FE (mục 6b) và BE (Customer-Service + Order-Service). Migration V14 chỉ đổi cấu trúc vì dữ liệu sẽ được clear.
> Còn mở: luồng giao hàng trên POS vẫn dùng địa chỉ khách (xem mục 10).
> Quyết định đã chốt:
> - **Không giao hàng**: bỏ địa chỉ.
> - **Không hạ hạng**: hạng chỉ nâng lên.
> - **Giữ "Nhập dữ liệu"** (import khách bằng Excel), bỏ cột "Loại khách hàng".
> - **Giữ "Số đơn tối thiểu"**: hạng xét theo cả chi tiêu và số đơn tích lũy.
> - **Không thêm cột Điểm** vào bảng danh sách khách.

## 1. Mục tiêu

Customer-Service chỉ trả lời hai câu hỏi: **khách là ai** và **khách có bao nhiêu điểm**. Hạng thành viên là cấu hình nhỏ, không phải một hệ thống xét hạng riêng.

## 2. Hiện trạng và các phát hiện

| # | Phát hiện | Vị trí |
|---|---|---|
| 1 | FE (`web-app/src/features/customer`, `features/sales`) dùng gần hết API customer, gồm tier-progress, tier-history, tier-metrics, purchase-transactions, tier-adjustments và import. Order-Service dùng 3 endpoint: `GET /api/customer?q=`, `GET /api/customer/{id}`, `PUT /api/customer/purchases/{orderId}`. | `web-app/src/features/customer/services/customer.api.ts`, `Order-Service/.../client/CustomerClient.java` |
| 2 | Hai consumer refund (`order.partially-refunded`, `order.fully-refunded`) **không bao giờ chạy**: Order-Service không phát các topic này. Refund chỉ phát `notification.return.refund-completed`. | `OrderOutboxTopicResolver.java`, `OrderRefundedConsumer.java` |
| 3 | Mua hàng được ghi nhận **qua 2 đường**: Feign `updateCustomerPurchase` và Kafka `order.completed`. Topic `customer.purchase-update.requested` được phát nhưng **không ai nghe**. | `OrderPaymentEventServiceImpl:188`, `OrderBackorderServiceImpl:192`, `OrderOutboxPublisher:60` |
| 4 | **Chưa chắc consumer `order.completed` đọc đúng payload.** `OrderCompletedEvent` khai báo `occurredAt` kiểu `LocalDateTime`, còn Order-Service gửi `occurredAt`/`completedAt` kiểu `Instant` qua Debezium. Có thể từ trước tới nay khách chỉ được cập nhật nhờ đường Feign. | `integration/event/OrderCompletedEvent.java`, `OrderOutboxPublisher:495+` |
| 5 | Điểm chỉ được cộng trong `updatePurchase` (Feign), không qua Kafka. Refund không trừ điểm. | `CustomerServiceImpl.updatePurchase` |
| 6 | Công thức tính điểm gọi `BigDecimal.divide()` không có scale/rounding, nên ném `ArithmeticException` khi chia ra số thập phân vô hạn. | `CustomerServiceImpl.updatePurchase` |
| 7 | Chưa có endpoint `lookup` gọn theo quy ước dự án. | `CustomerController` |

## 3. Phạm vi sau khi thu gọn

| Phần | Nội dung |
|---|---|
| Khách hàng | Thêm, sửa, xóa mềm, đổi trạng thái, tìm kiếm. Thêm `GET /lookup`. Giữ `totalSpent`, `totalOrderCount`, `lastPurchaseDate`, `tier_id`, và thêm `point_balance`. |
| Hạng | `customer_tiers`: `code`, `name`, `minSpending`, `minOrderCount`, `colorCode`, `pointMultiplier`, `status`. Hạng xét theo **tổng chi tiêu và số đơn tích lũy**, **chỉ lên, không xuống**. |
| Import khách | Giữ `POST /api/customer/import` (dry-run rồi import), bỏ cột loại khách. |
| Điểm | Số dư `point_balance` trên `customers`. Sổ cái `loyalty_transactions` gồm `EARN`, `REVERSE`, `ADJUST` (và `REDEEM` nếu chốt). Tỷ lệ quy đổi để trong `application.yml`. |
| Cập nhật mua hàng | Chỉ qua Kafka (`order.completed`, `customer.order-refunded`), idempotent bằng inbox (`BaseInbox`). |

**Sau khi gọn:** 4 bảng (`customers`, `customer_tiers`, `loyalty_transactions`, inbox), 3 controller, 2 consumer, không scheduler, không Feign.

## 4. Giai đoạn 1: Migration `V14__simplify_customer_scope.sql`

> Backup DB trước khi chạy. **Không sửa V1–V13.**

1. `customers`
   - Thêm `point_balance INT NOT NULL DEFAULT 0` và lấy giá trị từ `loyalty_accounts.point_balance`.
   - Xóa cột `customer_type`.
2. `loyalty_transactions`
   - Thêm `customer_id` và điền giá trị qua `loyalty_accounts`.
   - Bỏ FK và cột `loyalty_account_id`.
   - Thêm `balance_after INT`, `reference_code VARCHAR(50)` (mã đơn / mã phiếu hoàn để hiển thị), `reason VARCHAR(500)`.
   - Thêm unique `(type, reference_type, reference_id)`.
3. `customer_tiers`
   - Thêm `point_multiplier DECIMAL(5,2) NOT NULL DEFAULT 1`.
   - **Giữ** `min_order_count`.
4. Tạo bảng inbox theo `BaseInbox` của Common-Service.
5. DROP các bảng:
   - `customer_addresses`
   - `loyalty_accounts`
   - `loyalty_point_rules`
   - `customer_purchase_transactions`
   - `customer_tier_yearly_metrics`
   - `customer_tier_assignments`

## 5. Giai đoạn 2: Code trong Customer-Service

### 5.1 Xóa

| Nhóm | File / mục |
|---|---|
| Controller | `CustomerAddressController`, `CustomerLoyaltyTierController` |
| Entity + Repository | `CustomerAddress`, `CustomerPurchaseTransaction`, `CustomerTierAssignment`, `CustomerTierYearlyMetric`, `LoyaltyAccount`, `LoyaltyPointRule` |
| Service (+Impl) | `CustomerAddressService`, `LoyaltyAccountService`, `LoyaltyPointRuleService`, `LoyaltyTierService`, `TierAssignmentService`, `TierQualificationService`, `YearlyTierReviewService` |
| Enum | `CustomerType`, `CustomerPurchaseTransactionType`, `CustomerTierAssignmentReason`, `CustomerTierAssignmentStatus`, `LoyaltyPointRuleStatus` |
| DTO | `CustomerAddressRequest/Response`, `CustomerPurchaseTransactionResponse`, `CustomerTierAssignmentResponse`, `CustomerTierYearlyMetricResponse`, `TierProgressResponse`, `ManualTierAdjustmentRequest`, `LoyaltyAccountRequest/Response`, `LoyaltyPointRuleRequest/Response` |
| Hạ tầng | `scheduler/TierYearlyReviewScheduler`, `infrastructure/**` (OrderClient, OrderGateway, resilience predicates), `config/LoyaltyTierProperties` |
| Event / consumer | `OrderPartiallyRefundedEvent`, `OrderFullyRefundedEvent`, `OrderRefundedConsumer` |
| Endpoint | `PUT /api/customer/purchases/{orderId}` |
| Cấu hình | `application.yml`: các khối `spring.cloud.openfeign`, `resilience4j`, `loyalty.tier`; `pom.xml`: dependency resilience4j; `CustomerApplication`: `@EnableScheduling`, `@EnableFeignClients` |

### 5.2 Sửa / thêm

- **`Customer`**: thêm `pointBalance` và các hàm `addPurchase(amount, at)`, `applyRefund(amount, fullRefund)`, `changePoints(delta)` (số dư không âm).
- **`LoyaltyTransaction`**: đổi `@ManyToOne LoyaltyAccount` thành `@ManyToOne Customer`.
- **`LoyaltyTransactionType`**: `EARN`, `REVERSE`, `ADJUST` (+ `REDEEM`).
- **`LoyaltyReferenceType`**: `ORDER`, `REFUND`, `MANUAL`.
- **`CustomerTier`**: thêm `pointMultiplier`, giữ `minOrderCount`.
- **`CustomerRequest`**: bỏ `customerType`, `tierId`, `totalSpent`, `lastPurchaseDate`. Hạng và số liệu mua hàng do hệ thống tính, không nhận từ form.
- **Import khách**: giữ `CustomerImportService`, bỏ field `customerType` trong `CustomerImportRowRequest`.
- **`LoyaltyProperties`** (mới): `loyalty.points.spend-per-point` (ví dụ `10000`).
- **`CustomerPurchaseService`** (mới):
  - `onOrderCompleted(event)`:
    1. Kiểm tra inbox. Nếu event đã xử lý thì bỏ qua.
    2. Cộng `totalSpent` và `totalOrderCount`, cập nhật `lastPurchaseDate`.
    3. Lấy hạng cao nhất có `minSpending <= totalSpent` **và** `minOrderCount <= totalOrderCount`. Nếu hạng đó cao hơn hạng hiện tại thì nâng (không bao giờ hạ).
    4. Điểm = `floor(finalAmount / spendPerPoint) × tier.pointMultiplier`, dùng `RoundingMode.DOWN`. Ghi `EARN` với tham chiếu `ORDER/orderId`.
  - `onOrderRefunded(event)`:
    1. Kiểm tra inbox.
    2. Trừ `totalSpent`. Nếu `fullRefund` thì trừ thêm 1 đơn.
    3. Điểm trừ lại = `floor(earnedPoints × refundAmount / finalAmount)`, ghi `REVERSE` với tham chiếu `REFUND/refundId`. Số dư không xuống dưới 0.
    4. **Hạng giữ nguyên.**
- **`OrderCompletedConsumer`**: đọc payload đúng định dạng Order-Service gửi (envelope Debezium, `Instant`). Có thể dùng `DebeziumOutboxEventPayloadParser`.
- **`OrderRefundedConsumer`** (mới): nghe `customer.order-refunded`.
- **`CustomerController`**:
  - `GET /api/customer/lookup?q=` trả `id`, `name`, `code`, `phone`.
  - `GET /{id}` trả thêm `totalOrderCount`, `pointBalance`, `nextTier`, `remainingSpending`, `remainingOrderCount` (chi tiết ở mục 6b).
- **`LoyaltyController`**:
  - `GET /api/customer/{customerId}/loyalty-transactions`
  - `POST /api/customer/{customerId}/loyalty-adjustments` (bắt buộc `reason`)
- Dọn `CustomerMapper`, `ApiPath`, `ApiParam`, `Message`, `ErrorCode`, `CustomerSecurityConfig`.

## 6. Giai đoạn 3: Order-Service

1. Xóa lời gọi `orderCustomerService.updateCustomerPurchase(...)` ở:
   - `OrderPaymentEventServiceImpl:188`
   - `OrderBackorderServiceImpl:192`
2. Xóa `updateCustomerPurchase` trong `CustomerClient`, `OrderCustomerService(Impl)` cùng hàm fallback; xóa DTO `CustomerPurchaseResponse`; xóa cấu hình circuit breaker `customerService` nếu không còn chỗ khác dùng.
3. Bỏ `CUSTOMER_PURCHASE_UPDATE_REQUESTED` (event type, topic resolver và chỗ publish ở `OrderOutboxPublisher:60`).
4. Thêm event `ORDER_REFUNDED`, gửi lên topic `customer.order-refunded` khi phiếu hoàn tiền xong (cùng chỗ gọi `publishReturnRefundCompletedNotification`). Payload:
   ```json
   {
     "orderId": "uuid",
     "customerId": "uuid",
     "refundId": "uuid",
     "refundAmount": 0,
     "fullRefund": false,
     "refundedAt": "instant"
   }
   ```
   Chỉ phát khi đơn có `customerId`.
5. Chuyển `CustomerClient.getCustomers(q)` (dùng ở `OrderImportServiceImpl:460` để tìm khách theo SĐT) sang `GET /api/customer/lookup`.

## 6b. Frontend (đã sửa)

### Hợp đồng API FE đang dùng

| Endpoint | Ghi chú |
|---|---|
| `GET /api/customer`, `GET /api/customer/{id}` | `CustomerResponse` thêm `totalOrderCount`, `pointBalance`. Riêng `GET /{id}` có thêm `nextTier` (`CustomerTierResponse` hoặc `null`), `remainingSpending`, `remainingOrderCount`. Bỏ `customerType`. |
| `POST /api/customer`, `PUT /api/customer/{id}` | Body chỉ còn `customerCode`, `fullName`, `phone`, `email`, `dateOfBirth`, `status`. |
| `POST /api/customer/import` | Giữ nguyên; dòng import không còn `customerType`. |
| `GET/POST/PUT /api/customer/tiers`, `PATCH /tiers/{id}/status`, `GET /tiers/counts` | `CustomerTierResponse` / `CustomerTierRequest` thêm `pointMultiplier`. |
| `GET /api/customer/{id}/loyalty-transactions?page&size` | Trả `PageResponse<LoyaltyTransactionResponse>`. Mỗi dòng gồm `id`, `customerId`, `type` (`EARN`/`REVERSE`/`ADJUST`/`REDEEM`), `points` (có dấu), `balanceAfter`, `referenceType` (`ORDER`/`REFUND`/`MANUAL`), `referenceCode`, `reason`, `createdAt`. |
| `POST /api/customer/{id}/loyalty-adjustments` | Body `{ points, reason }`: `points` khác 0, âm là trừ. Trả `LoyaltyTransactionResponse`. |

FE **không còn gọi** `tier-progress`, `tier-history`, `tier-metrics`, `purchase-transactions`, `tier-adjustments`.

### Thay đổi theo màn

| Màn / file | Thay đổi |
|---|---|
| Form khách (`CustomerCreateDialog`) | Bỏ "Loại khách hàng" và "Hạng thành viên"; bỏ prop `tiers` ở `CustomerListPage` và `CustomerCard` (POS). |
| Chi tiết khách (`CustomerDetailDrawer`) | Còn 2 tab: **Hạng & điểm** (hạng hiện tại, điểm, tiến độ chi tiêu và số đơn đến hạng kế tiếp, tổng hợp mua hàng) và **Lịch sử điểm**. Bỏ tab "Lịch sử hạng", bỏ thông tin doanh nghiệp và địa chỉ. "Điều chỉnh hạng" → **"Điều chỉnh điểm"** (cộng/trừ, bắt buộc lý do, chỉ hiện khi `canManage`). |
| Quản lý hạng (`CustomerTierDialog`, `CustomerTierManagementPage`) | Thêm trường và cột **Hệ số tích điểm**; giữ "Số đơn tối thiểu". |
| Import (`customer-import.utils`) | Bỏ cột "Loại khách hàng" trong file mẫu và khi đọc file. |
| Route | Bỏ `/system/customers/groups` (chỉ render lại trang danh sách). |
| POS (`posCustomer.api`, `CustomerSearchDialog`, `sale.types`) | `points` lấy từ `pointBalance`, `orderCount` lấy từ `totalOrderCount`; badge hạng dùng `tierColor` thay cho màu hardcode theo tên. |

## 7. Giai đoạn 4: Kiểm tra

- [ ] `mvn -pl services/Customer-Service,services/Order-Service -am test-compile`
- [ ] Chạy Flyway trên DB trống.
- [ ] Chạy Flyway trên bản sao DB có dữ liệu. Đối chiếu tổng `point_balance` và số `loyalty_transactions` trước và sau.
- [ ] End-to-end: tạo đơn có khách, thanh toán, rồi xác nhận `totalSpent` và điểm tăng, hạng được nâng khi đủ ngưỡng.
- [ ] Gửi lại cùng event `order.completed`, xác nhận không bị cộng hai lần.
- [ ] Hoàn tiền một phần, xác nhận chi tiêu và điểm bị trừ, hạng giữ nguyên.
- [ ] Hoàn tiền toàn bộ, xác nhận `totalOrderCount` giảm 1.
- [ ] `graphify update .`

## 8. Câu hỏi cần chốt

| # | Câu hỏi | Đề xuất |
|---|---|---|
| 1 | Có cho dùng điểm để thanh toán (REDEEM) không? Nếu có, cần thêm endpoint đổi điểm và một bước trong saga đơn hàng. | **Chưa làm**, chỉ giữ `REDEEM` trong enum. |
| 2 | Tỷ lệ quy đổi điểm để trong `application.yml` hay cho admin sửa trên giao diện? | **`application.yml`.** |

## 9. Rủi ro

- **Payload `order.completed` không khớp** (phát hiện #4). Nếu consumer hiện đang lỗi thì bỏ Feign trước khi sửa consumer sẽ làm khách ngừng được cộng mua hàng. → Làm giai đoạn 2 (sửa consumer) và kiểm tra **trước** giai đoạn 3.
- **Mất dữ liệu khi DROP bảng**: lịch sử hạng, chỉ số năm, giao dịch mua và địa chỉ. → Backup trước, và xác nhận không còn báo cáo nào đọc các bảng này.
- **Dữ liệu điểm cũ**: các giao dịch tham chiếu `loyalty_account_id` phải được map sang `customer_id` đầy đủ trước khi bỏ cột.

## 10. Ghi chú khi triển khai

- Consumer `order.completed` cũ **chưa từng chạy**: `kafka-config.enabled: false`, `batch-listener: true` + `JsonDeserializer` nhưng listener nhận object đơn, và ack MANUAL không được gọi. Trước đây khách chỉ được cập nhật qua Feign. Consumer mới dùng `StringDeserializer`, không batch, parse bằng `DebeziumOutboxEventPayloadParser`, ack thủ công và inbox `inbox_events`.
- Ngoài 2 chỗ ghi trong plan, `OrderServiceImpl` còn 2 lời gọi `updateCustomerPurchase` (thanh toán chuyển khoản và POS trực tiếp); cả 4 đã bỏ. Mọi chỗ đều ngay trước `publishPaymentCompletedOrchestration`, nơi phát `ORDER_COMPLETED` khi đơn hoàn tất; đơn giao hàng phát khi chuyển COMPLETED.
- Điểm tích theo hạng **trước** đơn đó; đơn giúp lên hạng thì từ đơn sau mới hưởng hệ số mới.
- `loyalty_transactions.order_id` lưu đơn gốc của EARN / REVERSE để biết đã trừ lại bao nhiêu điểm của đơn.
- `fullRefund` chỉ true ở lần hoàn làm đơn chuyển sang `REFUNDED`, để mỗi đơn chỉ bị trừ 1 lần trong số đơn.
- **Mâu thuẫn chưa xử lý:** POS (`PosSalePage`, `delivery.api.ts`) và Delivery-Service (`Shipment.customerAddressId`) vẫn có luồng giao hàng dùng địa chỉ khách, trong khi V14 đã drop `customer_addresses`. Đường dẫn FE gọi (`/customer/addresses/{customerId}`, `/customer/{id}/addresses`) vốn không khớp controller địa chỉ cũ, nên tính năng này trước đó cũng chưa chạy được.
