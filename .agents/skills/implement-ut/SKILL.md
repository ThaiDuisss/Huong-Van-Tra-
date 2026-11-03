---
name: implement-ut
description: >-
  Use this skill when implementing, expanding, refactoring unit tests, or drafting Unit Test Excel Reports
  across the Hương Vân Trà ecosystem. Enforces module & method hierarchy, 100% service method coverage,
  strict input parameters formatting (concrete data + regex for random IDs), structured 3-part Expected Results
  (Return, Exception, Log Message), Java style rules (no FQCNs), dynamic Excel file discovery, and mandatory
  Tester identity confirmation with user.
---

# Implement Unit Test (UT) & Test Report Standards

Tài liệu quy chuẩn toàn diện về **phương pháp chia Module & Method**, **viết mã nguồn Unit Test (JUnit 5 + Mockito)** và **quy trình tự động tìm kiếm, lập/cập nhật tài liệu Báo cáo Kiểm thử Unit Test (Excel Report)** trong toàn bộ hệ sinh thái **Hương Vân Trà**.

---

## 1. Quy Trình Tổng Thể (End-to-End Workflow)

```plaintext
BƯỚC 1: Xác nhận Tên Tester (BẮT BUỘC HỎI NGƯỜI DÙNG nếu chưa có)
  └── BƯỚC 2: Rà soát Service Interface (Đếm đủ 100% methods)
        └── BƯỚC 3: Phân chia Module & Gom nhóm miền nghiệp vụ
              └── BƯỚC 4: Thiết kế Test Cases (Input cụ thể + Regex, Expected 3 dòng)
                    └── BƯỚC 5: Viết Code Unit Test (JUnit 5 + Mockito, No FQCN)
                          └── BƯỚC 6: Chạy xác minh `mvn test` đạt 100% Pass
                                └── BƯỚC 7: Tự tìm kiếm File Excel Báo cáo trong Workspace
                                      ├── Tìm thấy -> Cập nhật Cover, Stats & Module sheets
                                      └── Không thấy -> DỪNG & Báo ngay cho người dùng
```

---

## 2. Quy Định Về Tên Tester (Tester Identity Rule)

> [!CAUTION]
> **TUYỆT ĐỐI KHÔNG TỰ Ý GHI TÊN TESTER LINH TINH:**
> - Nghiêm cấm tự tiện ghi tên AI (như `Antigravity`, `AI Assistant`, `TesterBot`, v.v.) hoặc tự bịa một cái tên bất kỳ vào file báo cáo.
> - **BẮT BUỘC HỎI NGƯỜI DÙNG**:
>   - Nếu trong phiên làm việc người dùng chưa từng chỉ định tên Tester, agent **bắt buộc phải dừng lại và hỏi người dùng**:
>     > *"Vui lòng cho tôi biết tên Tester phụ trách để ghi vào file báo cáo Excel (ví dụ: AnNV, HoangLV,...)?"*
>   - Chỉ khi người dùng đã trả lời hoặc đã chỉ định rõ (ví dụ: *"Tester để AnNV"*), mới được dùng tên đó để ghi vào:
>     - Ô `Creator` ở sheet `Cover`.
>     - Ô `Creator` ở sheet `Test Statistics`.
>     - Cột `Tester` (Cột H) ở tất cả các dòng Test Case trong các sheet Module chi tiết.

---

## 3. Quy Chuẩn Phân Chia Module vs Method Trong Microservice

Khi tiếp cận một Microservice (như `Catalog-Service`, `Inventory-Service`):

### 3.1. Phân biệt các cấp độ cấu trúc
- **Service (Microservice)**: Ứng dụng độc lập chịu trách nhiệm một miền lớn (ví dụ: `Catalog-Service`).
- **Module (Phân hệ)**: Nhóm chức năng gom toàn bộ code xử lý một miền nghiệp vụ gắn kết (ví dụ: `Product_Module`, `Ingredient_Module`).
- **Feature (Tính năng)**: Trải nghiệm nghiệp vụ từ góc nhìn người dùng (ví dụ: "Tạo sản phẩm", "Trừ tồn bán được", "Cấu hình định lượng BOM").
- **Method (Phương thức)**: Hành động cụ thể gắn liền với đối tượng/Service Interface (ví dụ: `create()`, `findById()`, `decrementSellableQuantity()`).

### 3.2. Nguyên tắc gom nhóm Module (Sub-domain Grouping)
- Các thực thể có **quan hệ phụ thuộc trực tiếp (Parent - Child)** và chia sẻ chung vòng đời nghiệp vụ nên được **gộp chung vào 1 Module** thay vì xé nhỏ thành quá nhiều sheet rời rạc:
  - *Ví dụ trong Catalog-Service:* Sản phẩm gốc (`Product`), Biến thể mặt hàng (`ProductVariant`) và Định lượng công thức (`BomItem`) có quan hệ mật thiết -> **Gộp chung vào `Product_Module`**.
  - *Ví dụ trong Inventory-Service:* Vị trí kho (`Location`), Kệ quầy (`StoreShelf`) và Mặt hàng kho (`InventoryItem`) -> **Gộp chung vào `Location_Item_Module`**.

### 3.3. Quy tắc rà soát 100% Method Coverage
> [!IMPORTANT]
> **Tuyệt đối không được bỏ sót method.**
> Trước khi viết test và lập báo cáo:
> 1. Mở trực tiếp file Service Interface (`<Entity>Service.java`).
> 2. Liệt kê toàn bộ các phương thức (method signature) được định nghĩa trong Interface.
> 3. Mỗi method bắt buộc có tối thiểu:
>    - 1 Test case luồng chuẩn (`Normal` - Happy path).
>    - Các Test case luồng ngoại lệ (`Abnormal` - Bad request, Not found, Conflict, Duplicate).
>    - Các Test case giá trị biên (`Boundary` - Zero, số âm, ngưỡng threshold, chuyển giao trạng thái) nếu có logic so sánh/tính toán.

---

## 4. Quy Chuẩn Thiết Kế Test Case (Excel Report Columns)

### 4.1. Quy chuẩn Cột "Input parameters"
> [!CAUTION]
> **TUYỆT ĐỐI KHÔNG** dùng các từ chung chung như `valid`, `invalid`, `non-existent`, `random-uuid`.
> Phải truyền **dữ liệu cụ thể (concrete values)** mô phỏng đúng payload thực tế.

- **Đối với các ID/Mã tự sinh hoặc sinh ngẫu nhiên:**
  - Phải chỉ định một giá trị mẫu cụ thể và kèm theo **biểu thức chính quy (Regex Pattern)** để mô tả định dạng sinh ngẫu nhiên.
  - Cú pháp chuẩn: `<tên_biến>="<giá_trị_mẫu>" (Regex: <pattern>)`

**Bảng tra cứu định dạng Regex chuẩn trong dự án:**
| Loại dữ liệu | Ví dụ dữ liệu cụ thể | Định dạng Regex chuẩn |
|:---|:---|:---|
| **UUID v4** | `"550e8400-e29b-41d4-a716-446655440000"` | `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$` |
| **Mã vạch EAN-13** | `"8930000000018"` | `^893\d{10}$` |
| **Mã SKU Biến thể** | `"TRA-HOP-00001"` | `^[A-Z0-9]+-[A-Z0-9]+-\d{5}$` |
| **Mã Sản phẩm** | `"SP-00001"` | `^SP-\d{5}$` |
| **Mã Danh mục** | `"CAT-00001"` | `^CAT-\d{5}$` |
| **Mã Nguyên liệu** | `"NL-00001"` | `^NL-\d{5}$` |
| **Mã Nhà cung cấp** | `"NCC-00001"` | `^NCC-\d{5}$` |
| **Mã Lô hàng** | `"LOT-20261103-001"` | `^LOT-\d{8}-\d{3}$` |
| **Mã Token / Khóa ngẫu nhiên** | `"a1B2c3D4"` | `^[a-zA-Z0-9]{8,16}$` |

**Ví dụ thực tế trong cột Input parameters:**
- ❌ **Sai:** `name="Trà Shan Tuyết", categoryId=valid`
- ✅ **Đúng:** `name="Trà Shan Tuyết", categoryId="550e8400-e29b-41d4-a716-446655440001" (Regex: ^[0-9a-fA-F-]{36}$)`
- ❌ **Sai:** `id=random-uuid`
- ✅ **Đúng:** `id="00000000-0000-0000-0000-000000000000" (ID không tồn tại)`
- ❌ **Sai:** `productCode="SP-01" (đã có trong DB)`
- ✅ **Đúng:** `productCode="SP-00001" (đã tồn tại trong DB), name="Trà Ô Long"`

---

### 4.2. Quy chuẩn Cột "Expected Results"
> [!IMPORTANT]
> Cột **Expected Results** bắt buộc phải trình bày thành **3 dòng riêng biệt** (ngăn cách bằng dấu xuống dòng `\n`):
> 1. `Return: <Object trả về | null | hành động của hàm void>`
> 2. `Exception: <Tên Exception Class và Message> (hoặc N/A nếu thành công)`
> 3. `Log Message: <Nội dung log ghi nhận> (hoặc N/A nếu không log)`

> [!TIP]
> **Quy tắc ghi dòng `Return:`**
> - **Hàm có trả về Object:** Ghi trực tiếp Object đó, KHÔNG ghi thêm `Pass`, `Fail` hay `trả về...` rườm rà.
> - **Hàm không trả về Object (hoặc test ném Exception):** Ghi rõ `null`.
> - **Hàm `void`:** Ghi trực tiếp trạng thái/hành động được thực thi (những gì lưu vào DB/hệ thống), KHÔNG ghi `Pass`, `Fail` hay `void`.

#### Cấu trúc chuẩn:
```text
Return: <Object cụ thể theo input | null | hành động/trạng thái DB của hàm void>
Exception: <Tên Exception("Nội dung thông báo") | N/A>
Log Message: <"Nội dung log" | N/A>
```

#### Ví dụ hàm trả về Object (Normal Case):
```text
Return: ProductResponse(id="550e8400-e29b-41d4-a716-446655440000", productCode="SP-00001", name="Trà Shan Tuyết Cổ Thụ", status=ACTIVE)
Exception: N/A
Log Message: "Generated productCode=SP-00001 and persisted successfully"
```

#### Ví dụ hàm ném ngoại lệ / không trả về Object (Abnormal Case):
```text
Return: null
Exception: ResourceNotFoundException("Category not found with id: 00000000-0000-0000-0000-000000000000")
Log Message: "Failed to create product: Category not found"
```

#### Ví dụ hàm `void` (Normal Case):
```text
Return: isDeleted=true lưu vào DB thành công
Exception: N/A
Log Message: "Soft deleted product 550e8400-e29b-41d4-a716-446655440000"
```

#### Ví dụ hàm kiểm tra biên (Boundary Case):
```text
Return: SellableQuantityAdjustmentResponse(thresholdCrossed=true, productionThreshold=30.00, productionBatchQuantity=50.00)
Exception: N/A
Log Message: "Production threshold crossed for variant 550e8400-e29b-41d4-a716-446655440004"
```

---

### 4.3. Quy chuẩn Phân loại Test Case Type
- **`Normal`**: Luồng xử lý nghiệp vụ chuẩn, dữ liệu hợp lệ (Happy path).
- **`Abnormal`**: Dữ liệu sai, vi phạm validation, vi phạm khóa ngoại, trùng mã, ném Exception (Sad path).
- **`Boundary`**: Giá trị biên (bằng 0, số âm, ngưỡng chuyển trạng thái, tập rỗng, tối đa/tối thiểu).

---

## 5. Quy Trình Tự Động Tìm Kiếm & Cập Nhật File Báo Cáo Excel

Khi hoàn thành viết Unit Test cho một Service hoặc Module, agent/tester phải thực hiện đúng quy trình tìm kiếm và xử lý file:

### 5.1. Tự tìm kiếm File Báo Cáo Trong Workspace (Dynamic File Discovery)
> [!IMPORTANT]
> **Không bao giờ giả định file luôn cố định ở một thư mục duy nhất.**
> File báo cáo có thể nằm ở `.local/doc/`, `docs/`, `doc/`, `reports/`, hoặc thư mục con khác do người dùng chỉ định.

**Quy trình tìm kiếm:**
1. Thực hiện quét tìm kiếm file Excel có tên khớp với các mẫu sau:
   - `*Unit Test*.xlsx`
   - `*Report*Unit*.xlsx`
   - `Report-5.1*.xlsx`
2. Ví dụ câu lệnh tìm kiếm:
   ```bash
   find . -type f -name "*Unit Test*.xlsx" -o -name "*Report*Unit*.xlsx"
   ```
3. Phân biệt file:
   - File có đuôi `.original.xlsx` là **File Template gốc** (chứa định dạng chuẩn, các công thức `=COUNTIF`, `=SUM`, màu sắc, font chữ).
   - File không có chữ `.original` là **File Báo Cáo hiện tại** cần chỉnh sửa/ghi đè.

### 5.2. Xử lý kịch bản KHÔNG TÌM THẤY FILE
> [!CAUTION]
> **NẾU QUÉT TOÀN BỘ REPO MÀ KHÔNG TÌM THẤY BẤT KỲ FILE BÁO CÁO HAY TEMPLATE NÀO:**
> - **TUYỆT ĐỐI KHÔNG tự tiện tạo file Excel trắng từ đầu** (vì sẽ làm mất sạch font chữ, viền bảng, merge cell và toàn bộ các công thức `=COUNTIF`, `=SUM` phức tạp của template dự án).
> - **DỪNG LẠI NGAY LẬP TỨC VÀ BÁO CHO NGƯỜI DÙNG:**
>   > *"Tôi đã tìm kiếm file tài liệu kiểm thử Unit Test (theo mẫu `*Unit Test*.xlsx`) trong toàn bộ thư mục dự án nhưng không tìm thấy file nào. Vui lòng cho tôi biết đường dẫn chính xác của file báo cáo hoặc cung cấp file template để tôi tiến hành điền kết quả kiểm thử."*

### 5.3. Cập nhật các Sheet trong File Excel (Khi Đã Tìm Thấy File)
1. **Sheet `Cover`**:
   - `Creator`: Điền tên Tester (đã xác nhận với người dùng).
   - `Issue Date`: Ngày lập báo cáo (định dạng `dd/MM/yyyy`).
   - `Record of Change`: Ghi rõ nội dung cập nhật và phiên bản.
2. **Sheet `Test Cases`**:
   - Khai báo đầy đủ danh sách Module với: Số thứ tự, Tên Module, Tên Sheet (`<Name>_Module`), Mô tả và Pre-conditions.
3. **Sheet `Test Statistics`**:
   - Creator: Điền tên Tester.
   - Các dòng thống kê của từng Module phải dùng **công thức Excel động trỏ sang Sheet tương ứng**:
     - Passed: `='<SheetName>'!B5`
     - Failed: `='<SheetName>'!C5`
     - Untested: `='<SheetName>'!D5`
     - Normal: `='<SheetName>'!B9`
     - Abnormal: `='<SheetName>'!C9`
     - Boundary: `='<SheetName>'!D9`
     - Total: `='<SheetName>'!B3`
   - Dòng `Sub total` dùng hàm `=SUM(...)` trên các cột tương ứng.
   - Các chỉ số đo độ bao phủ (`Test coverage`, `Normal case`, `Abnormal case`, `Boundary case`) tính toán theo công thức tự động.
4. **Các Sheet Chi tiết Module (`<Name>_Module`)**:
   - Copy từ sheet template `ModuleName1` để giữ nguyên style, màu sắc, border và công thức ở header (rows 1 đến 11).
   - Đặt tiêu đề `Code Module` tại `B2`.
   - Bắt đầu ghi từ row 12: Dòng tiêu đề Method (nền xám/xanh nhạt), tiếp theo là các dòng UTC.
   - Cột `Tester`: Điền tên Tester đã được người dùng chỉ định.
   - Cột `Round 1`: Điền `Passed` (nền xanh lá nhạt).
   - Cột `Test date`: Ngày test thực tế (`dd/MM/yyyy`).

---

## 6. Quy Tắc Bắt Buộc Về Java Code Style (JUnit 5 + Mockito)

> [!CAUTION]
> **Tuyệt đối không dùng Fully Qualified Class Names (FQCN)** trong chữ ký hàm, trường dữ liệu, generics hoặc biến cục bộ (ví dụ: cấm viết `PageResponse<org.hvtra.catalog.dto.response.LookupResponse>`).
> Luôn dùng `import` chuẩn ở đầu file và sử dụng tên class ngắn gọn (ví dụ: `import org.hvtra.catalog.dto.response.LookupResponse;` và `PageResponse<LookupResponse>`).

### 6.1. Template Mẫu Chuẩn Cho Service Test
```java
package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper mapper;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @InjectMocks
    private ProductServiceImpl service;

    private UUID productId;
    private UUID categoryId;
    private Category category;
    private Product product;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        categoryId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        category = new Category();
        category.setId(categoryId);
        category.setName("Trà Thượng Hạng");

        product = new Product();
        product.setId(productId);
        product.setName("Trà Shan Tuyết Cổ Thụ");
        product.setProductCode("SP-00001");
        product.setStatus(Status.ACTIVE);
        product.setCategory(category);
    }

    @Nested
    @DisplayName("create tests")
    class CreateTests {

        @Test
        @DisplayName("create with valid category generates code and returns response")
        void create_WhenValidCategory_ShouldGenerateCodeAndSave() {
            ProductRequest request = new ProductRequest(categoryId, null, "Trà Shan Tuyết Cổ Thụ", "Mô tả", null, Status.ACTIVE);

            BusinessSequence seq = new BusinessSequence();
            seq.setCurrentValue(1L);

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")).thenReturn(Optional.of(seq));
            when(repository.existsByProductCode("SP-00001")).thenReturn(false);
            when(repository.save(any(Product.class))).thenReturn(product);
            when(mapper.toResponse(product)).thenReturn(productResponse);

            ProductResponse result = service.create(request);

            assertNotNull(result);
            verify(repository).save(any(Product.class));
        }

        @Test
        @DisplayName("create when category not found throws ResourceNotFoundException")
        void create_WhenCategoryNotFound_ShouldThrowResourceNotFoundException() {
            UUID unknownCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            ProductRequest request = new ProductRequest(unknownCategoryId, null, "Trà Shan Tuyết Cổ Thụ", "Mô tả", null, Status.ACTIVE);

            when(categoryRepository.findById(unknownCategoryId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.create(request));
            verify(repository, never()).save(any());
        }
    }
}
```

### 6.2. Lưu ý bẫy `Optional.map(mapper::toResponse)` khi Mock
Trong Spring Boot Service thường có pattern:
```java
return repository.findById(id)
        .map(mapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Entity", id));
```
Khi mock method này, `when(mapper.toResponse(any())).thenReturn(...)` **bắt buộc phải trả về một instance non-null**. Nếu để mặc định `null`, `Optional.map` sẽ trả về `Optional.empty()` và kích hoạt `orElseThrow()`, gây fail test giả tạo.

---

## 7. Checklist Trước Khi Hoàn Thành Nhiệm Vụ Unit Test

- [ ] **Tên Tester**: Đã hỏi và xác nhận với người dùng, không tự ý ghi tên AI hay tên bừa bãi.
- [ ] **Bao phủ Method**: Đã rà soát đủ 100% method trong Service Interface tương ứng.
- [ ] **Chạy test**: Chạy `mvn test` trên microservice tương ứng đạt **100% Pass (0 Failures, 0 Errors)**.
- [ ] **Tìm kiếm file Excel**: Đã tự tìm kiếm trong repo (`*Unit Test*.xlsx`):
  - Nếu có file: Đã điền đầy đủ, đúng công thức, đúng format 3 dòng, đúng Regex.
  - Nếu không tìm thấy file nào: Đã thông báo rõ ràng cho người dùng biết, không tự tạo file trắng.
- [ ] **Kiểm tra Migration**: Đã chạy script kiểm tra migration naming của dự án:
  `python3 -B scripts/migrations/check_migration_naming.py`
  `python3 -B -m unittest discover -s scripts/migrations -p "test_*.py"`
