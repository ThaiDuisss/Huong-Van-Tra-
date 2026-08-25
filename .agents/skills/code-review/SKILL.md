---
name: code-review
description: >-
  Use this skill when reviewing code, verifying pull requests, or auditing code quality across
  the Hương Vân Trà codebase. Covers Java style rules, JPA optimization, API standard responses,
  microservice boundaries, security checks, and frontend guidelines.
---

# Code Review Skill (Hương Vân Trà)

Bộ tiêu chí và checklist toàn diện phục vụ việc kiểm tra, đánh giá chất lượng mã nguồn (Code Review) trước khi merge hoặc triển khai cho hệ thống **Hương Vân Trà**.

---

## 1. Java Code Style & Project Rules Checklist (Bắt buộc)

- [ ] **Quy tắc Import & Tên Class ([`.agents/AGENTS.md`](file:///Users/nguyenan/Documents/Spring%20boot/Huong-Van-Tra-/.agents/AGENTS.md)):**
  - **KHÔNG sử dụng Fully Qualified Class Names (FQCN)** trong method signature, fields, parameters, generics hoặc khai báo biến (ví dụ: `PageResponse<org.hvtra.catalog.dto.response.LookupResponse>` là **SAI**).
  - Luôn sử dụng `import org.hvtra.catalog.dto.response.LookupResponse;` và khai báo ngắn gọn `PageResponse<LookupResponse>`.
  - *Ngoại lệ:* Cho phép FQCN trong chuỗi JPQL `@Query("SELECT new org.hvtra.catalog.dto...")`.
- [ ] **Lombok & Clean Code:**
  - Sử dụng `@RequiredArgsConstructor` cho constructor injection thay cho `@Autowired` trên field.
  - Sử dụng `@FieldDefaults(level = AccessLevel.PRIVATE)` khi phù hợp để giữ tính đóng gói.
  - Tránh dùng `@Data` trên JPA Entities (nguy cơ lỗi `equals`/`hashCode` lặp vô tận với quan hệ hai chiều); nên dùng `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.

---

## 2. Kiến trúc & Ranh giới Microservices

- [ ] **Tách biệt DTO và Entity:**
  - Tuyệt đối không để JPA Entity lọt ra tầng Controller hoặc trả trực tiếp về Client.
  - Sử dụng MapStruct (hoặc custom Mapper) để chuyển đổi giữa Entity $\leftrightarrow$ Request/Response DTO.
- [ ] **Giao tiếp liên Service:**
  - Đồng bộ: Sử dụng Spring Cloud OpenFeign qua các Client Interface, tích hợp `CommonFeignErrorDecoder`.
  - Bất đồng bộ: Sử dụng Kafka Producer/Consumer với Schema rõ ràng cho các tác vụ sự kiện (Notification, Cập nhật tồn kho, Hóa đơn).
- [ ] **Tính độc lập dữ liệu:** Không thực hiện join trực tiếp giữa các bảng thuộc database của service khác.

---

## 3. Chuẩn hóa API & Xử lý Ngoại lệ

- [ ] **Định dạng Envelope Response:**
  - Sử dụng `ApiResponse<T>` hoặc `PageResponse<T>` chuẩn từ `Common-Service`.
  - Đảm bảo các trường `status`, `code`, `message`, `data` trả về đúng định dạng.
- [ ] **Mã lỗi & Exception Handling:**
  - Sử dụng các exception có sẵn: `ResourceNotFoundException`, `ResourceAlreadyExistsException`, `BusinessException`, `AppException`.
  - Không bắt ngoại lệ chung chung `catch (Exception e)` rồi nuốt lỗi mà không log hoặc rethrow.
  - Khai báo mã lỗi tập trung trong `ErrorEnum`.

---

## 4. Tối ưu hóa JPA / Hibernate & Cơ sở dữ liệu

- [ ] **Phòng chống lỗi $N+1$ Query:**
  - Kiểm tra các quan hệ `@ManyToOne`, `@OneToMany`, `@ManyToMany`.
  - Sử dụng `JOIN FETCH`, `@EntityGraph`, hoặc DTO Projection khi truy vấn danh sách kèm quan hệ.
- [ ] **Quản lý Transaction:**
  - Sử dụng `@Transactional(readOnly = true)` cho các method chỉ đọc/query để tối ưu hiệu năng và snapshot context của Hibernate.
  - Sử dụng `@Transactional` có rollback rõ ràng cho các tác vụ ghi/thay đổi trạng thái nhiều bảng.
- [ ] **Flyway Migration:**
  - Mọi thay đổi schema DB (tạo bảng, thêm cột, đổi kiểu dữ liệu, index, foreign key) đều **phải có file Flyway migration mới** (`V<NextNumber>__<description>.sql`).
  - Tuyệt đối không để xảy ra tình trạng sửa JPA Entity mà không có script migration tương ứng.
- [ ] **Phân trang dữ liệu:** Mọi API trả về danh sách đều phải hỗ trợ `Pageable` và giới hạn kích thước trang (`PageableDefault(size = 20)`).
- [ ] **Auditing:** Các entity kế thừa từ `AuditableEntity` hoặc `BaseEntity` để tự động ghi nhận `createdAt`, `updatedAt`, `createdBy`, `updatedBy`.

---

## 5. Bảo mật & Kiểm tra phân quyền (Security Checklist)

- [ ] **Xác thực & Lấy thông tin User:**
  - Sử dụng `SecurityUtils.currentUserId()` để lấy ID người dùng thực thi, không tin cậy `userId` do client gửi trong request body (chống IDOR).
- [ ] **Kiểm tra vai trò:** Sử dụng `SecurityUtils.hasRole("...")`, `SecurityUtils.isAdmin()`, `SecurityUtils.isStaff()` hoặc `@PreAuthorize` cho các endpoint nhạy cảm.
- [ ] **Bean Validation:** Đảm bảo có `@Valid` ở tham số Controller và đầy đủ `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max` trong Request DTO.

---

## 6. Frontend Code Review (`apps/pos-web`)

- [ ] **Ngôn ngữ hiển thị:** Toàn bộ text giao diện (labels, messages, placeholders, modal titles) là **Tiếng Việt 100%**. Tên biến/types là **Tiếng Anh**.
- [ ] **Layout Workspace:**
  - POS Bán hàng: Nằm trong `PosShell`, full viewport `h-dvh`, 3 cột, desktop-first.
  - Quản trị: Nằm trong `SystemShell`, sidebar + topbar + bộ lọc + bảng phân trang + detail panel.
- [ ] **API Client:** Sử dụng `apiClient` từ `@hvt/api-client`, không gọi `fetch`/`axios` trực tiếp trong component con.
- [ ] **Trạng thái UI:** Xử lý đầy đủ 4 trạng thái: Loading (Skeleton/Spinner), Empty (`EmptyState`), Error (Toast/Alert), Data Success.

---

## 7. Quality Gates & Verification

Trước khi approve hoặc hoàn tất task:
- [ ] Backend: `mvn test-compile` thành công, không gãy interface/API giữa các module.
- [ ] Frontend: `pnpm --filter pos-web build` và `pnpm --filter pos-web lint` không có lỗi.
- [ ] Giao diện người dùng và API hoạt động chính xác với đầy đủ các trạng thái (Loading, Empty, Error, Success).
- [ ] *(Chỉ khi có yêu cầu cụ thể)*: Có bổ sung Unit Test cho logic nghiệp vụ.
