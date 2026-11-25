---
name: implement-it
description: >-
  Use this skill when implementing, expanding, refactoring integration tests, or drafting Integration Test Excel Reports
  across the Hương Vân Trà ecosystem (specifically for Report-5.2_Integration Test.xlsx). Enforces 100% English documentation,
  1 management domain = 1 feature sheet, structured business functions, concrete procedures with step-by-step payloads & regex
  for random IDs, 5-column test case structure (Procedure, Expected Results, Pre-conditions, Status, Tester), dynamic Excel discovery,
  and mandatory Tester identity confirmation.
---

# Implement Integration Test (IT) & Integration Test Report Standards

Comprehensive standard guide on **Feature & Business Function organization**, **Integration Test implementation (Spring Boot Test, MockMvc, JPA/Database Integration)**, and **the automated process of discovering, authoring, and maintaining Integration Test Excel Reports (`Report-5.2_Integration Test.xlsx`)** across the entire **Hương Vân Trà** ecosystem.

> [!IMPORTANT]
> **100% ENGLISH ENFORCEMENT:**
> All documentation, sheet titles, feature descriptions, test case descriptions, step-by-step procedures, expected results, pre-conditions, and test report contents **MUST BE WRITTEN IN 100% ENGLISH**. No Vietnamese is permitted inside the report sheets or test case specifications.

---

## 1. End-to-End Workflow

```plaintext
STEP 1: Confirm Tester Identity (MANDATORY TO CONFIRM WITH USER if not already specified)
  └── STEP 2: Partition Feature Domains (1 Management Domain = 1 Feature Sheet)
        └── STEP 3: Enumerate Business Functions within each Feature
              └── STEP 4: Design Test Cases (Step-by-step Procedure + Concrete Data & Regex, Direct & Cross-functional Expected Results)
                    └── STEP 5: Implement Integration Test Code (Spring Boot Test / MockMvc / Testcontainers, Standard Imports, No FQCNs)
                          └── STEP 6: Execute & Verify `mvn test` achieves 100% Pass
                                └── STEP 7: Automatically Locate Excel Report in Workspace (`*Integration Test*.xlsx`)
                                      ├── Found -> Update Cover, Test Cases, Test Statistics, and Feature Sheets
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
>     - `Tester` column (Column H for Round 1, Column K for Round 2, Column N for Round 3) across all test case rows in all Feature sheets.

---

## 3. Feature & Function Partitioning Standards

In Integration Testing, the test target is the **interaction and coordination across multiple architectural layers**: `Controller` $\rightarrow$ `Service` $\rightarrow$ `Database` $\rightarrow$ `Audit Trail` $\rightarrow$ `Transaction Rollback` $\rightarrow$ `External Microservice Client / Event Publisher`.

### 3.1. Core Principle: 1 Management Domain = 1 Feature Sheet
> [!IMPORTANT]
> **Do not merge multiple major management domains into a single monolithic feature sheet.**
> Merging distinct domains (such as combining Category, Unit, Product, and Variant into one sheet) causes sheet bloat, entangles entity lifecycles, and obscures test result tracing.
> **Partitioning Rules:**
> - Each primary entity management domain is organized into **1 distinct Feature** $\Leftrightarrow$ **1 dedicated Sheet** in `Report-5.2_Integration Test.xlsx`.
> - Sheet names must use the standardized format: `<Domain>_Management`.

**Standard Feature Sheets for Catalog Service:**
| No | Excel Sheet Name | Feature Title | Domain Scope Description |
|:---:|---|---|---|
| **1** | `Category_Management` | Category Management | Product category tree, parent-child hierarchy, default reorder point |
| **2** | `Unit_Management` | Unit of Measure Management | Base units, conversion units, precision scale, conversion rates |
| **3** | `Product_Management` | Product Management | Master products, business product codes, image assets, status cascade |
| **4** | `Variant_Management` | Product Variant & Barcode Management | Item SKUs, auto-naming, EAN-13 barcodes, POS scanning, sellable quota |
| **5** | `PriceList_Management` | Price List & Audit History Management | Branch price lists, effective date windows, price change history audit logs |
| **6** | `Ingredient_Management` | Raw Material & BOM Management | Recipe raw materials, retail ingredients, Bill of Materials (BOM) formulas |
| **7** | `Supplier_Management` | Supplier Management | Supplier master profiles, supplier-ingredient distribution mapping |
| **8** | `ImportBatch_Management` | Batch Excel Import Management | 2-stage import cycle: Upload/Preview $\rightarrow$ Draft $\rightarrow$ Approve Ingest |

**Standard Feature Sheets for Inventory Service:**
| No | Excel Sheet Name | Feature Title | Domain Scope Description |
|:---:|---|---|---|
| **1** | `Location_Management` | Location & Shelf Management | Warehouses, retail store locations, disposal sites, POS store shelves, capacity |
| **2** | `GoodsReceipt_Management` | Goods Receipt & Batch Management | Inbound supplier receipts, inventory batches, FEFO expiry dates, batch Excel import |
| **3** | `Stock_Management` | Warehouse Stock & Transaction Audit | Real-time on-hand/reserved/available stock, FEFO batch drawer, transaction audit logs |
| **4** | `Replenishment_Management` | Shelf Replenishment Management | Transfer from warehouse to store shelf, reservation locks, shelf capacity constraints |
| **5** | `Fulfillment_Management` | Sale Fulfillment & Reservation | Reservation holds for POS/online orders, fulfillment stock-out, cancellation release |
| **6** | `Production_Management` | Production Order Management | Tea packaging orders via BOM formulas, ingredient consumption, finished goods batch creation |
| **7** | `CustomBundle_Management` | Custom Bundle Management | POS bespoke tea gift sets, component stock holds, packing confirmations |
| **8** | `Stocktake_Management` | Stocktake & Adjustment Management | Periodic inventory counts, physical vs book variance calculation, balance adjustment |
| **9** | `CustomerReturn_Management` | Customer Return & QC Inspection | POS customer return receipts, condition QC inspection, restock vs scrap routing |
| **10** | `Disposal_Management` | Disposal Management | Damaged/expired goods disposal requests, managerial approval, permanent stock write-off |

---

### 3.2. Definition of "Function" in Integration Testing
> [!NOTE]
> **A Function is NOT merely an individual API endpoint.**
> In Integration Testing, a Function represents an end-to-end **Business Function / Use Case**:
> - It can be **a standalone API** if that endpoint encapsulates a complete business workflow (e.g., `Scan barcode at POS checkout`).
> - It can be **a multi-step sequence of APIs** forming a composite workflow (e.g., `Excel Import Flow`: Upload $\rightarrow$ Parse Preview $\rightarrow$ Save Draft $\rightarrow$ Edit Row $\rightarrow$ Approve Ingest).
> - It can be **an action triggering background asynchronous behaviors** (e.g., `Price List Update` automatically capturing snapshot $\rightarrow$ calculating diff $\rightarrow$ writing audit log into `price_change_history`).

In each Feature sheet, Functions are demarcated by **Function Header Rows** (styled with light cyan background and bold text) to group related integration test cases.

---

## 4. Integration Test Case Design Standards

All test cases must strictly be written in **100% English** with the following column structure:

### 4.1. Column `Test Case ID`
- Format: `ITC_<FEATURE>_<NUM>` (e.g., `ITC_CAT_001`, `ITC_LOC_001`, `ITC_RCP_001`, `ITC_STK_001`).
- Numbers must be sequentially incrementing without gaps.

### 4.2. Column `Test Case Description`
- Concise and explicit summary in English:
  - Normal business flow (Happy path).
  - Data integrity and foreign key constraints.
  - Unique business code / barcode enforcement.
  - Transaction atomicity and rollback verification upon error.
  - Concurrency safety and lock verification (preventing overselling / negative stock).

### 4.3. Column `Test Case Procedure` (Step-by-Step)
> [!CAUTION]
> **PROCEDURE SPECIFICATION RULES:**
> 1. Must describe **step-by-step actions** mimicking real user interactions or automated test drivers, using **concrete data**, never generic placeholders like `valid`, `invalid`, or `random-id`.
> 2. **For User Interface (UI) workflows:** Describe navigation, button clicks, field inputs, dropdown selections:
>    ```text
>    1. Go to the Category Management page in System menu (/system/categories).
>    2. Click "Add Category" button.
>    3. Enter Category Name: "Premium Tea Collection".
>    4. Select Parent Category: None (Root Category).
>    5. Enter Default Reorder Point: 15.
>    6. Select Status: "ACTIVE".
>    7. Click "Save" button.
>    ```
> 3. **For Inter-Service / Background / Concurrency API simulations:**
>    - Prefix with: `(Using api to simulate in testing)`
>    - Detail endpoint, HTTP method, JSON payload, and parameters:
>    ```text
>    (Using api to simulate in testing)
>    1. Send HTTP POST to /api/catalog/product-variants/550e8400-e29b-41d4-a716-446655440211/decrement-sellable.
>    2. Payload JSON: {"quantity": 5}.
>    3. Verify inter-service response delivered to Order Service.
>    ```
> 4. **For auto-generated or random identifiers:** Always provide a concrete sample and its **Regex Pattern** (e.g., `id="550e8400-e29b-41d4-a716-446655440001" (Regex: ^[0-9a-fA-F-]{36}$)`).

---

### 4.4. Column `Expected Results` (Direct Results & Cross-Functional Impacts)
> [!IMPORTANT]
> **EXPECTED RESULTS MUST CONTAIN BOTH DIRECT RESULTS AND CROSS-FUNCTIONAL IMPACTS:**
> 1. **Direct Result:**
>    - HTTP Response status code and returned DTO fields (e.g., `System returns 201 Created with CategoryResponse (id=..., categoryCode=CAT-00001, status=ACTIVE)`).
>    - Persistence state in Database (rows inserted/updated/soft-deleted).
> 2. **Cross-functional & End-to-End Impact:**
>    - **View & List Pages:** Master list table immediately reflects the new/modified entity; search filters locate it.
>    - **Dropdown / Selection Modals:** Associated screens (e.g., Product creation form, Goods receipt line item modal) instantly display the new record in their lookup dropdowns.
>    - **Inventory, Balance & Transaction Logs:**
>      + Inbound receipt posting: Automatically increments `quantity_on_hand` in `inventory_balances` and writes `INBOUND_RECEIPT` into `inventory_transactions`.
>      + Shelf replenishment: Decrements warehouse available balance, increments store shelf stock, writes `REPLENISHMENT_OUT/IN`.
>      + Production completion: Deducts raw materials per BOM, generates new finished tea batch, increments finished goods balance.
>    - **Abnormal / Error Flow:** Returns standardized error (`400 Bad Request`, `404 Not Found`), transaction rolls back cleanly, zero dirty or orphan records in database.

**Standard Expected Results Example:**
```text
1. Direct Result: System returns 201 Created and persists CategoryResponse:
   - id="550e8400-e29b-41d4-a716-446655440001" (Regex: ^[0-9a-fA-F-]{36}$)
   - categoryCode="CAT-00001" (Regex: ^CAT-\d{5}$)
   - name="Premium Tea Collection", status=ACTIVE, defaultReorderPoint=15
   - Database: New record created in categories table with is_deleted=false.
2. Cross-functional Impact:
   - Category List page: Instantly displays "Premium Tea Collection" at the top of the table.
   - Product Creation Form: Dropdown "Category" immediately includes "Premium Tea Collection" as a selectable option.
   - Category Lookup API (/lookup): Successfully returns this category for frontend autocomplete.
```

### 4.5. Column `Pre-conditions`
- Clearly specify initial prerequisites in English:
  - Required master data (e.g., `Branch "Hoa Lac Branch" (id="00000000-...") exists and is ACTIVE in DB`).
  - Authenticated user role (e.g., `Authenticated user with ROLE_WAREHOUSE_MANAGER`).
  - Pre-existing state (e.g., `Warehouse inventory balance has at least 50 units available`).

### 4.6. Status, Test Date, and Tester Columns
- `Round 1`: `Passed` (green highlight `00C6EFCE`), `Failed` (red highlight), or `Pending` (yellow highlight `00FFEB9C`).
- `Test date`: Format `dd/MM/yyyy` (e.g., `07/11/2026`).
- `Tester`: User-confirmed tester name (e.g., `AnNV`).
- `Round 2` & `Round 3`: Set to `Pending` with empty dates and tester fields for initial releases.

---

## 5. Sheet Structure in `Report-5.2_Integration Test.xlsx`

Every integration test report file must maintain the following standard sheets:

### 5.1. Sheet `Cover`
- `Project Name`: Hương Vân Trà
- `Project Code`: SEP490_G66
- `Creator`: Confirmed Tester Name (e.g., `AnNV`)
- `Issue Date`: Report date (`dd/MM/yyyy`)
- `Version`: Incremental version (e.g., `1.0`, `1.1`)
- Table `Record of change`: Effective date, version, change item, change description, reference documents.

### 5.2. Sheet `Test Cases` (Master Function Catalog)
Master table cataloging every business function across all microservice domains:
| Column | Column Name | Description | Example |
|:---:|---|---|---|
| **B** | `No` | Function sequence number | `1.0`, `2.0`, `3.0`,... |
| **C** | `Function Name` | English Business Function title | `Function 1: Create Category Master` |
| **D** | `Sheet Name` | Corresponding Feature sheet name | `Category_Management`, `GoodsReceipt_Management` |
| **E** | `Description` | Summary of test coverage | `Verify category creation, CAT-xxxxx auto-generation, parent-child linking` |
| **F** | `Pre-Condition` | General prerequisite | `System operational, authenticated user with ROLE_MANAGER` |

### 5.3. Sheet `Test Statistics` (Automated Summary & Coverage Formulas)
- Summary table listing all Feature sheets:
  - Column B: `No` (`1.0`, `2.0`,...)
  - Column C: `Module code` (Formula referencing Feature sheet title: `='<SheetName>'!B2`)
  - Column D: `Passed` (Formula: `='<SheetName>'!B6`)
  - Column E: `Failed` (Formula: `='<SheetName>'!C6`)
  - Column F: `Pending` (Formula: `='<SheetName>'!D6`)
  - Column G: `N/A` (Formula: `='<SheetName>'!E6`)
  - Column H: `Number of test cases` (Formula: `='<SheetName>'!B4`)
- Row `Sub total`: Dynamic formula `=SUM(D11:D<last>)`, `=SUM(E11:E<last>)`, etc.
- Rows `Test coverage` & `Test successful coverage`: Automated percentage calculation formulas.

### 5.4. Feature Detail Sheets (`<Domain>_Management`)
- **Row 2:** `Feature`: `<Feature Title in English>`
- **Row 3:** `Test requirement`: `<Comprehensive English requirement description>`
- **Row 4:** `Number of TCs`: Formula `=B6+C6+D6+E6`
- **Rows 5-8:** Testing Round statistics table (Round 1, Round 2, Round 3) with dynamic formulas:
  - Passed: `=COUNTIF($F11:$F998, B5)`
  - Failed: `=COUNTIF($F11:$F998, C5)`
  - Pending: `=COUNTIF($F11:$F998, D5)`
  - N/A: `=COUNTIF($F11:$F998, E5)`
- **Row 10:** Column headers (Dark green fill `0076923C`, white bold text, centered).
- **Row 11+:** Alternating Function Header rows (Light cyan fill `00CCFFFF`, bold) and Test Case rows.

---

## 6. Spring Boot Integration Test Code Rules

When writing Java integration test source code:
- **Annotation stack:** `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)` combined with `@AutoConfigureMockMvc`.
- **Transaction isolation:** Annotate test classes or methods with `@Transactional` to guarantee automated database rollback after each test execution.
- **Mock external boundaries:** Use `@MockBean` for third-party services (e.g., Keycloak, external email gateways).
- **No Fully Qualified Class Names (FQCNs):**
  - ❌ **Wrong:** `org.hvtra.inventory.dto.response.PageResponse<org.hvtra.inventory.dto.response.LocationResponse>`
  - ✅ **Correct:** Standard `import org.hvtra.inventory.dto.response.PageResponse;` and use `PageResponse<LocationResponse>`.

---

## 7. Automated Excel File Discovery Protocol

> [!IMPORTANT]
> **Never assume the Excel file is permanently located at a hardcoded path.**
> Search dynamically in the repository:
> ```bash
> find . -type f -name "*Integration Test*.xlsx" -o -name "*Report-5.2*.xlsx"
> ```
> - File ending in `.original.xlsx` is the **Master Template** (preserving styles and formulas).
> - File without `.original` is the **Active Report File** to update.
> - **IF NO MATCHING FILE IS FOUND:**
>   - **NEVER create a blank Excel file from scratch** (this destroys all predefined formulas, styles, and alignments).
>   - **STOP IMMEDIATELY AND PROMPT THE USER** for the correct file path or template.

---

## 8. Definition of Done Checklist

Before concluding any Integration Test task:
- [ ] **100% English:** All sheet titles, function headers, test descriptions, procedures, expected results, and pre-conditions are in English.
- [ ] **Tester Identity:** Confirmed with user and populated across Cover, Test Statistics, and all test case rows.
- [ ] **Domain Partitioning:** Strictly follows 1 Management Domain = 1 Feature Sheet.
- [ ] **Concrete Procedures:** Step-by-step actions with real data payloads and regex for auto-generated IDs.
- [ ] **Comprehensive Expected Results:** Direct HTTP/DB results + Cross-functional/End-to-End impact.
- [ ] **Dynamic Excel Formulas:** All `=COUNTIF(...)`, `=SUM(...)` formulas intact without `#REF!` errors.
- [ ] **Test Execution:** `mvn test` passes 100% with zero failures or errors.
