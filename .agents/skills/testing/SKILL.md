---
name: testing
description: >-
  Use this skill when running, verifying, debugging, or establishing test suites across the Hương Vân Trà repository.
  Covers Maven microservices tests, Spring Cloud Gateway tests, and Frontend Vitest/lint/build quality gates.
---

# Testing Skill (Hương Vân Trà)

> [!NOTE]
> **Phạm vi kiểm thử hiện tại:** Dự án hiện đang ưu tiên tốc độ triển khai tính năng, do đó **chưa yêu cầu viết mới unit test** cho mỗi màn hình/tính năng.
> Trọng tâm kiểm thử hiện tại là:
> - Đảm bảo Backend biên dịch sạch (`mvn test-compile` hoặc chạy các test hiện có).
> - Đảm bảo Frontend build & lint thành công (`pnpm --filter pos-web build`, `pnpm --filter pos-web lint`).
> - Xác thực luồng nghiệp vụ trên UI và API endpoint.

Tài liệu hướng dẫn quy trình kiểm thử toàn diện, thực thi lệnh test và các quality gates cho cả phía **Backend Microservices (Spring Boot 3.5 / Java 21)** và **Frontend (React 19 / TypeScript / Vitest)**.

---

## 1. Backend Testing Matrix (Maven & Spring Boot)

### A. Chạy Test từng Microservice độc lập (Khuyến nghị)
Thay vì chạy test toàn bộ monorepo (tốn nhiều tài nguyên và thời gian), hãy chỉ định chính xác module cần kiểm thử bằng cờ `-pl` (`--projects`):

```bash
# Chạy test cho một service cụ thể
mvn -pl services/Order-Service test
mvn -pl services/Catalog-Service test
mvn -pl services/Promotion-Service test
mvn -pl services/Customer-Service test
mvn -pl services/Delivery-service test
mvn -pl common/Common-Service test
mvn -pl infra/Api-Gateway test
```

### B. Chạy kiểm thử theo Class hoặc Method cụ thể
Để kiểm tra nhanh tính năng vừa sửa:

```bash
# Chạy 1 Test Class
mvn -pl services/Promotion-Service test -Dtest=PromotionControllerTest

# Chạy 1 Method trong Test Class
mvn -pl services/Promotion-Service test -Dtest=PromotionControllerTest#createPromotion_returnsCreatedBusinessCode

# Chạy theo pattern
mvn -pl services/Order-Service test -Dtest=*Checkout*Test
```

### C. Kiểm tra Biên dịch toàn bộ dự án
Để đảm bảo không bị gãy type/interface giữa các module sau khi refactor:

```bash
mvn test-compile
```

---

## 2. Frontend Testing Matrix (pnpm, Vitest, TypeScript)

Từ thư mục `web-app`:

### A. Chạy Unit Test với Vitest
Kiểm tra các helper, data parser, API client methods:

```bash
# Chạy test toàn bộ workspace
pnpm test

# Chạy test cho một package cụ thể
pnpm --filter @hvt/api-client test
```

### B. Quality Gates kiểm tra Type & Lint
Bắt buộc chạy trước khi commit code hoặc hoàn tất task giao diện:

```bash
# Kiểm tra TypeScript compilation & Vite build
pnpm --filter pos-web build

# Kiểm tra ESLint
pnpm --filter pos-web lint
```

---

## 3. Các quy chuẩn Mocking & Xử lý phụ thuộc khi Test

### A. Backend Dependencies Mocking
- **Kafka Messaging:** Không kết nối Kafka broker thật trong Unit Test. Sử dụng `@Mock` cho `KafkaTemplate` hoặc cấu hình embedded test profile nếu là integration test.
- **Feign Clients:** Mock toàn bộ các Feign Client liên service (ví dụ `CatalogClient`, `InventoryClient`, `CustomerClient`) để đảm bảo test chạy độc lập, không phụ thuộc vào service khác đang sống hay chết.
- **Database (JPA):**
  - Đối với Service Unit Test: Mock `JpaRepository` bằng Mockito (`when(repo.save(any())).thenReturn(...)`).
  - Đối với Repository Integration Test: Sử dụng H2 database hoặc Testcontainers với cấu hình trong `src/test/resources/application-test.yml`.
- **Security Context:** Sử dụng Mock SecurityContext hoặc truyền `InternalUserPrincipal` qua `SecurityContextHolder` khi test các method có gọi `SecurityUtils`.

### B. Frontend Mocking
- Mock module API Client bằng `vi.mock("@/api/api-client")` hoặc vi mock hàm API để kiểm tra UI state loading, success, error.
- Sử dụng `beforeEach(() => { vi.clearAllMocks(); })` để tránh rò rỉ state giữa các test cases.

---

## 4. Xử lý sự cố thường gặp khi Test (Troubleshooting)

| Vấn đề | Nguyên nhân phổ biến | Cách khắc phục |
| :--- | :--- | :--- |
| `BeanDefinitionOverrideException` | Xung đột cấu hình test và main context | Đặt `@ActiveProfiles("test")` và tách biệt config test. |
| `NullPointerException` tại `SecurityUtils.currentUserId()` | Method cần context người dùng nhưng chưa mock security context | Thêm setup `InternalUserPrincipal` vào `SecurityContextHolder` trong `@BeforeEach`. |
| Lỗi build Frontend `Type error: Property 'x' does not exist` | Sai lệch kiểu dữ liệu giữa DTO frontend và backend | Cập nhật lại TypeScript types trong `src/features/<feature>/types/`. |
| Feign Error 4xx/5xx | Chưa mock Feign Client trả về response hợp lệ | Kiểm tra lại `@Mock` FeignClient và stub data tương ứng. |
