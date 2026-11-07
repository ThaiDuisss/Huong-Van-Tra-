---
name: implement-ut
description: >-
  Use this skill when implementing, expanding, refactoring unit tests, or drafting Unit Test Excel Reports
  across the Hương Vân Trà ecosystem (specifically for Report-5.1_Unit Test.xlsx). Enforces 100% English documentation,
  module & method hierarchy, 100% service method coverage, strict input parameters formatting (concrete data + regex for random IDs),
  structured 3-part Expected Results (Return, Exception, Log Message), Java style rules (no FQCNs), dynamic Excel file discovery,
  and mandatory Tester identity confirmation with user.
---

# Implement Unit Test (UT) & Test Report Standards

Comprehensive standard guide on **Module & Method hierarchy**, **Unit Test implementation (JUnit 5 + Mockito)**, and **the automated process of discovering, authoring, and maintaining Unit Test Excel Reports (`Report-5.1_Unit Test.xlsx`)** across the entire **Hương Vân Trà** ecosystem.

> [!IMPORTANT]
> **100% ENGLISH ENFORCEMENT:**
> All documentation, sheet titles, module descriptions, unit test case descriptions, input parameter specifications, structured 3-part expected results, and test report contents **MUST BE WRITTEN IN 100% ENGLISH**. No Vietnamese is permitted inside the report sheets or unit test specifications.

---

## 1. End-to-End Workflow

```plaintext
STEP 1: Confirm Tester Identity (MANDATORY TO CONFIRM WITH USER if not already specified)
  └── STEP 2: Audit Service Interface (Account for 100% of defined methods)
        └── STEP 3: Partition Sub-domains & Group Business Modules
              └── STEP 4: Design Test Cases (Concrete Input + Regex, Structured 3-line Expected Results)
                    └── STEP 5: Implement Unit Test Code (JUnit 5 + Mockito, Standard Imports, No FQCNs)
                          └── STEP 6: Execute & Verify `mvn test` achieves 100% Pass
                                └── STEP 7: Automatically Locate Excel Report in Workspace (`*Unit Test*.xlsx`)
                                      ├── Found -> Update Cover, Statistics, and Module sheets
                                      └── Not Found -> STOP & Alert user immediately (DO NOT generate blank files)
```

---

## 2. Tester Identity Rule

> [!CAUTION]
> **NEVER ARBITRARILY INVENT OR INSERT PLACEHOLDER TESTER NAMES:**
> - Strictly prohibited from inserting AI names (e.g., `Antigravity`, `AI Assistant`, `TesterBot`, etc.) or arbitrary names.
> - **MANDATORY USER CONFIRMATION**:
>   - If the user has not yet specified a Tester name in the current conversation, the agent **must pause and prompt the user**:
>     > *"Please provide the Tester name to record in the Excel report (e.g., AnNV, HoangLV, etc.)?"*
>   - Only when the user has explicitly provided or confirmed the name (e.g., *"Tester: AnNV"*), use that exact name for:
>     - `Creator` cell in `Cover` sheet.
>     - `Creator` cell in `Test Statistics` sheet.
>     - `Tester` column (Column H) across all test case rows in all Module sheets.

---

## 3. Module vs Method Hierarchy in Microservices

When analyzing a microservice (e.g., `Catalog-Service`, `Inventory-Service`):

### 3.1. Architectural Hierarchy
- **Service (Microservice)**: Independent standalone application responsible for a bounded context (e.g., `Catalog-Service`).
- **Module**: Functional grouping of closely coupled entities and business logic (e.g., `Product_Module`, `Location_Module`).
- **Feature**: Business capability from user or system perspective (e.g., "Create Product", "Decrement Sellable Stock", "Configure BOM").
- **Method**: Concrete method signature defined on a Service Interface (e.g., `create()`, `findById()`, `decrementSellableQuantity()`).

### 3.2. Sub-domain Grouping Principles
- Closely coupled entities sharing a parent-child lifecycle should be **consolidated into a single Module sheet** rather than fractured into fragmented sheets:
  - *Catalog-Service Example:* Root product (`Product`), variant items (`ProductVariant`), and recipe items (`BomItem`) $\rightarrow$ Consolidated into **`Product_Module`**.
  - *Inventory-Service Example:* Locations (`InventoryLocation`) and shelves (`StoreShelf`) $\rightarrow$ Consolidated into **`Location_Module`**.

### 3.3. 100% Service Method Coverage Rule
> [!IMPORTANT]
> **Never omit any service method.**
> Before authoring tests and populating Excel reports:
> 1. Directly inspect the target Service Interface (`<Entity>Service.java`).
> 2. Enumerate every declared method signature.
> 3. Each method must have at minimum:
>    - 1 Happy-path test case (`Normal`).
>    - Negative/exception test cases (`Abnormal` - Bad Request, Not Found, Duplicate Code, Conflict).
>    - Edge/boundary test cases (`Boundary` - Zero, negative numbers, threshold transitions, empty collections) where conditional logic applies.

---

## 4. Test Case Design Standards (Excel Report Columns)

### 4.1. "Input parameters" Column Specifications
> [!CAUTION]
> **STRICTLY PROHIBITED** from using generic placeholders like `valid`, `invalid`, `non-existent`, `random-uuid`.
> Always pass **concrete, realistic values** mimicking true API payloads.

- **For auto-generated or random identifiers:**
  - Provide a concrete sample value accompanied by its **Regex Pattern**.
  - Standard syntax: `<variable_name>="<sample_value>" (Regex: <pattern>)`

**Standard Project Regex Reference:**
| Data Type | Concrete Sample Value | Standard Regex Pattern |
|:---|:---|:---|
| **UUID v4** | `"550e8400-e29b-41d4-a716-446655440000"` | `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$` |
| **EAN-13 Barcode** | `"8930000000018"` | `^893\d{10}$` |
| **Variant SKU** | `"TRA-HOP-00001"` | `^[A-Z0-9]+-[A-Z0-9]+-\d{5}$` |
| **Product Code** | `"SP-00001"` | `^SP-\d{5}$` |
| **Category Code** | `"CAT-00001"` | `^CAT-\d{5}$` |
| **Ingredient Code** | `"NL-00001"` | `^NL-\d{5}$` |
| **Supplier Code** | `"NCC-00001"` | `^NCC-\d{5}$` |
| **Batch Code** | `"LOT-20261103-001"` | `^LOT-\d{8}-\d{3}$` |
| **Receipt Code** | `"GR-202611-00001"` | `^GR-\d{6}-\d{5}$` |

**Concrete Input Examples:**
- ❌ **Wrong:** `name="Ancient Green Tea", categoryId=valid`
- ✅ **Correct:** `name="Ancient Green Tea", categoryId="550e8400-e29b-41d4-a716-446655440001" (Regex: ^[0-9a-fA-F-]{36}$)`
- ❌ **Wrong:** `id=random-uuid`
- ✅ **Correct:** `id="00000000-0000-0000-0000-000000000000" (non-existent ID)`
- ❌ **Wrong:** `productCode="SP-01" (already in DB)`
- ✅ **Correct:** `productCode="SP-00001" (already exists in DB), name="Oolong Tea"`

---

### 4.2. "Expected Results" Column Specifications
> [!IMPORTANT]
> The **Expected Results** column must strictly be formatted as **3 distinct lines** (separated by `\n`):
> 1. `Return: <Returned Object | null | void execution outcome>`
> 2. `Exception: <ExceptionClass("Message") | N/A>`
> 3. `Log Message: <"Log content" | N/A>`

> [!TIP]
> **Rules for the `Return:` line:**
> - **Method returning an Object:** Output the object representation directly; do NOT prefix with `Pass`, `Fail`, or `returns...`.
> - **Method returning null or throwing Exception:** State `null`.
> - **Void method:** State the persisted entity state or action executed in database; do NOT write `Pass`, `Fail`, or `void`.

#### Standard Structure:
```text
Return: <Concrete Object representation | null | database action of void method>
Exception: <ExceptionClass("Message") | N/A>
Log Message: <"Log content" | N/A>
```

#### Example 1: Object Return (Normal Case)
```text
Return: ProductResponse(id="550e8400-e29b-41d4-a716-446655440000", productCode="SP-00001", name="Ancient Shan Tuyet Tea", status=ACTIVE)
Exception: N/A
Log Message: "Generated productCode=SP-00001 and persisted successfully"
```

#### Example 2: Exception Thrown (Abnormal Case)
```text
Return: null
Exception: ResourceNotFoundException("Category not found with id: 00000000-0000-0000-0000-000000000000")
Log Message: "Failed to create product: Category not found"
```

#### Example 3: Void Method (Normal Case)
```text
Return: Persists isDeleted=true in database successfully
Exception: N/A
Log Message: "Soft deleted product 550e8400-e29b-41d4-a716-446655440000"
```

#### Example 4: Boundary / Threshold Transition (Boundary Case)
```text
Return: SellableQuantityAdjustmentResponse(thresholdCrossed=true, productionThreshold=30.00, productionBatchQuantity=50.00)
Exception: N/A
Log Message: "Production threshold crossed for variant 550e8400-e29b-41d4-a716-446655440004"
```

---

### 4.3. Test Case Classification
- **`Normal`**: Standard business logic execution with valid input payloads (Happy path).
- **`Abnormal`**: Invalid payloads, validation failures, foreign key violations, duplicate codes, exception thrown (Sad path).
- **`Boundary`**: Edge cases (zero, negative values, transition thresholds, empty lists, max/min bounds).

---

## 5. Automated Excel Report Discovery Protocol

> [!IMPORTANT]
> **Never assume the Excel file is permanently located at a hardcoded path.**
> Search dynamically in the repository:
> ```bash
> find . -type f -name "*Unit Test*.xlsx" -o -name "*Report*Unit*.xlsx"
> ```
> - File ending in `.original.xlsx` is the **Master Template** (preserving styles and formulas).
> - File without `.original` is the **Active Report File** to update.
> - **IF NO MATCHING FILE IS FOUND:**
>   - **NEVER create a blank Excel file from scratch** (this destroys all predefined formulas, styles, and alignments).
>   - **STOP IMMEDIATELY AND PROMPT THE USER** for the correct file path or template.

### 5.1. Excel Sheet Update Protocol
1. **Sheet `Cover`**:
   - `Creator`: Confirmed Tester name.
   - `Issue Date`: Report date (`dd/MM/yyyy`).
   - `Record of Change`: Document version and update summary in English.
2. **Sheet `Test Cases`**:
   - Enumerate all Modules with: Index, Module Name, Sheet Name (`<Name>_Module`), Description, and Pre-conditions.
3. **Sheet `Test Statistics`**:
   - Creator: Confirmed Tester name.
   - Module rows must use **dynamic Excel formulas**:
     - Passed: `='<SheetName>'!B5`
     - Failed: `='<SheetName>'!C5`
     - Untested: `='<SheetName>'!D5`
     - Normal: `='<SheetName>'!B9`
     - Abnormal: `='<SheetName>'!C9`
     - Boundary: `='<SheetName>'!D9`
     - Total: `='<SheetName>'!B3`
   - Row `Sub total`: Dynamic formula `=SUM(...)`.
4. **Module Detail Sheets (`<Name>_Module`)**:
   - Inherit formatting from template (`ModuleName1`) to preserve typography, borders, and count formulas in rows 1-11.
   - Row 12+: Method header row (light cyan fill, bold), followed by unit test case rows.
   - All text content must be in **100% English**.

---

## 6. Java Code Style Standards (JUnit 5 + Mockito)

> [!CAUTION]
> **Strictly prohibit Fully Qualified Class Names (FQCNs)** in method signatures, field declarations, generics, or local variables.
> Always declare standard imports at the top of the file and use simple class names.

### 6.1. Standard Test Template
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
        category.setName("Premium Green Tea");

        product = new Product();
        product.setId(productId);
        product.setName("Ancient Shan Tuyet Tea");
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
            ProductRequest request = new ProductRequest(categoryId, null, "Ancient Shan Tuyet Tea", "Description", null, Status.ACTIVE);

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
            ProductRequest request = new ProductRequest(unknownCategoryId, null, "Ancient Shan Tuyet Tea", "Description", null, Status.ACTIVE);

            when(categoryRepository.findById(unknownCategoryId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.create(request));
            verify(repository, never()).save(any());
        }
    }
}
```

---

## 7. Definition of Done Checklist

- [ ] **100% English**: All documentation, sheet titles, module descriptions, unit test cases, inputs, expected results, and exception messages are in English.
- [ ] **Tester Identity**: Confirmed directly with user; populated in Cover, Statistics, and all test case rows.
- [ ] **100% Method Coverage**: Every method declared in the Service Interface has corresponding Normal, Abnormal, and Boundary test cases.
- [ ] **Dynamic Excel Formulas**: All count and summary formulas intact without `#REF!` errors.
- [ ] **Test Execution**: `mvn test` passes 100% with zero failures or errors.
