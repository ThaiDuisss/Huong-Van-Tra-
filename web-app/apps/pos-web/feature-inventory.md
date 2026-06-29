You are a senior React + TypeScript frontend developer.

I already have an existing POS Web application with:

* POS Sales workspace
* System Management workspace
* Existing SystemShell layout
* Existing sidebar and topbar
* Existing table, filter, card, modal, drawer, button, badge and pagination styles

Do not redesign the application.
Do not create a new layout.
Do not change the current theme.
Reuse the existing template and make new pages visually consistent with current System pages such as BOM List and Production Order List.

Your task is to add Inventory Management pages for managing shelf stock first.

Do not implement full warehouse management yet.
Do not implement purchase orders.
Do not implement supplier management.
Do not implement BOM or production in this prompt.

All user-facing text must be displayed in Vietnamese.

Keep technical code names in English:

API endpoints
TypeScript types
Component names
Service files
Enum values
Internal variable names

Only translate texts that users can see on the UI.

Examples:

Sidebar menu:

Inventory Management
→ Quản lý tồn kho

Shelf Locations
→ Quản lý kệ hàng

Stock Movement History
→ Lịch sử biến động tồn kho

====================================================

Page titles:

/system/inventory/locations:
"Quản lý kệ hàng"

/system/inventory/balances:
"Tồn kho hiện tại"

/system/inventory/transactions:
"Lịch sử biến động tồn kho"

====================================================

Table column labels:

Code
→ Mã kệ

Location Name
→ Tên vị trí

Type
→ Loại vị trí

Branch
→ Chi nhánh

Parent Location
→ Vị trí cha

Status
→ Trạng thái

Updated At
→ Ngày cập nhật

Actions
→ Thao tác

Inventory Item ID
→ Mã hàng tồn kho

Reference ID
→ Mã tham chiếu

Item Type
→ Loại hàng hóa

On Hand
→ Tồn thực tế

Reserved
→ Tồn giữ chỗ

Available
→ Có thể bán

Transaction Code
→ Mã giao dịch

Reference Type
→ Loại tham chiếu

Created By
→ Người tạo

Created At
→ Thời gian tạo

Quantity
→ Số lượng thay đổi

Before Quantity
→ Tồn trước

After Quantity
→ Tồn sau

====================================================

Form labels:

Create Location
→ Tạo vị trí

Edit Location
→ Chỉnh sửa vị trí

Search
→ Tìm kiếm

Filter
→ Bộ lọc

Select Branch
→ Chọn chi nhánh

Select Location
→ Chọn vị trí

Select Inventory Item
→ Chọn hàng hóa

Quantity
→ Số lượng

Actual Quantity
→ Số lượng kiểm kê thực tế

Difference
→ Chênh lệch

Reason
→ Lý do

Note
→ Ghi chú

Confirm Stock In
→ Xác nhận nhập hàng

Confirm Adjustment
→ Xác nhận điều chỉnh

====================================================

Enum display labels:

InventoryLocationType:

SHELF
→ Kệ hàng

COUNTER
→ Quầy thu ngân

STORAGE
→ Kho chứa

DAMAGED
→ Khu hàng lỗi

InventoryStatus:

ACTIVE
→ Đang hoạt động

INACTIVE
→ Ngừng hoạt động

InventoryTransactionType:

STOCK_IN
→ Nhập hàng

SALE_OUT
→ Bán hàng

RETURN_IN
→ Hoàn hàng nhập lại

ADJUSTMENT
→ Điều chỉnh tồn kho

TRANSFER
→ Chuyển vị trí

====================================================

Messages and notifications:

Success:

"Tạo thành công"
"Cập nhật thành công"
"Thay đổi trạng thái thành công"
"Nhập hàng thành công"
"Điều chỉnh tồn kho thành công"

Confirmation dialogs:

"Bạn có chắc chắn muốn thay đổi trạng thái?"
"Bạn có chắc chắn muốn nhập hàng?"
"Bạn có chắc chắn muốn điều chỉnh tồn kho?"

Empty state:

"Không có dữ liệu"
"Không tìm thấy kết quả phù hợp"

Loading state:

"Đang tải dữ liệu..."

Error state:

"Đã xảy ra lỗi. Vui lòng thử lại sau."

====================================================

IMPORTANT RULE

Do not display raw enum values or English texts on the UI.

Example:

Wrong:
Status: ACTIVE

Correct:
Trạng thái: Đang hoạt động

Wrong:
Type: SHELF

Correct:
Loại vị trí: Kệ hàng

All labels, buttons, titles, dialogs and messages visible to end users must use Vietnamese.

====================================================
ADD NEW MENU TO EXISTING SYSTEM SIDEBAR
====================================================

Do not remove or modify existing System menus.

Only add the following new Inventory Management menu into the current System sidebar:

Inventory Management
|
|-- Shelf Locations
|-- Inventory Balances
|-- Stock In
|-- Stock Adjustment
|-- Stock Movement History


====================================================
CREATE NEW FEATURE MODULE
====================================================

Create new feature:

src/features/inventory/

Structure:

inventory/
├── pages/
├── components/
├── services/
├── types/
├── hooks/
└── utils/


Follow the existing feature-based architecture and keep consistency with current System pages.

====================================================
BUSINESS CONTEXT
================

This phase only manages stock available at the store shelf/counter.

Inventory locations can be:

* SHELF: product display shelf
* COUNTER: cashier counter
* STORAGE: internal storage, optional for now
* DAMAGED: damaged goods location

Main goal:

* Create and manage shelf/counter locations.
* View current stock quantity by location.
* Add stock to shelf/counter.
* Adjust stock after counting.
* View stock movement history.

POS sales will reduce shelf/counter stock later, but do not build POS screen in this prompt.

====================================================

1. SHELF LOCATION MANAGEMENT PAGE
   ====================================================

Route:

/system/inventory/locations

Purpose:

Manage shelf, counter and basic inventory locations.

Page layout:

* Header:

  * Page title: Shelf Locations
  * Create Location button

* Filter area:

  * Search by location code or name
  * Filter by type
  * Filter by status

* Table columns:

  * Code
  * Location Name
  * Type
  * Branch
  * Parent Location
  * Status
  * Updated At
  * Actions

Actions:

* View detail
* Edit location
* Change status

Create/Edit Location Form:

Fields:

* Branch ID
* Parent Location
* Code
* Name
* Type:

  * SHELF
  * COUNTER
  * STORAGE
  * DAMAGED
* Status:

  * ACTIVE
  * INACTIVE

API:

GET /api/inventory/inventory-locations
GET /api/inventory/inventory-locations/{id}
POST /api/inventory/inventory-locations
PUT /api/inventory/inventory-locations/{id}
PATCH /api/inventory/inventory-locations/{id}/status

UI notes:

* Use badge color for location type.
* Use confirmation dialog before changing status.
* Show empty state when no locations exist.

====================================================
2. INVENTORY BALANCE PAGE
=========================

Route:

/system/inventory/balances

Purpose:

View current stock quantity by inventory item and location.

Page layout:

* Header:

  * Page title: Inventory Balances

* KPI cards:

  * Total Items
  * Total On Hand
  * Total Reserved
  * Low Stock Items

* Filter area:

  * Search by inventory item id, reference id, or item type
  * Filter by location
  * Filter by branch
  * Filter by item type
  * Filter by status

* Table columns:

  * Inventory Item ID
  * Reference ID
  * Item Type
  * Location
  * On Hand
  * Reserved
  * Available
  * Updated At
  * Actions

Available = On Hand - Reserved

Actions:

* View detail
* View movement history for this item/location

:

GET /api/inventory/inventory-balances
GET /api/inventory/inventory-balances/{id}
GET /api/inventory/inventory-items/{id}/balances

UI notes:

* AvailableAPI quantity should be highlighted.
* If available <= 0, show danger/low stock style.
* If reserved > 0, show reserved badge.

====================================================
3. STOCK IN PAGE
================

Route:

/system/inventory/stock-in

Purpose:

Add stock quantity to a shelf/counter/location.

Page layout:

* Header:

  * Page title: Stock In

* Form section:

  * Select Branch
  * Select Location
  * Note field

* Item input section:

  * Search/select Inventory Item
  * Quantity input
  * Add item row button

* Item table before submit:

  * Inventory Item ID
  * Reference ID
  * Item Type
  * Quantity
  * Actions

* Submit button:

  * Confirm Stock In

API:

POST /api/inventory/inventory-transactions/stock-in

Request example:

{
"locationId": 1,
"branchId": 1,
"items": [
{
"inventoryItemId": 1,
"quantity": 100
}
]
}

Business rules:

* Quantity must be greater than 0.
* Location must be ACTIVE.
* Inventory item must be ACTIVE.
* Multiple items can be added in one stock-in transaction.

UI notes:

* Show validation errors inline.
* Show confirmation dialog before submit.
* After success, show transaction code and link to transaction detail.

====================================================
4. STOCK ADJUSTMENT PAGE
========================

Route:

/system/inventory/adjustment

Purpose:

Adjust inventory quantity after physical counting.

Page layout:

* Header:

  * Page title: Stock Adjustment

* Form:

  * Select Branch
  * Select Location
  * Reason field

* Item adjustment table:

  * Inventory Item ID
  * Reference ID
  * Item Type
  * Current Quantity
  * Actual Quantity input
  * Difference auto-calculated

* Submit button:

  * Confirm Adjustment

API:

POST /api/inventory/inventory-transactions/adjustment

Request example:

{
"locationId": 1,
"branchId": 1,
"reason": "Kiểm kê thiếu hàng",
"items": [
{
"inventoryItemId": 1,
"actualQuantity": 95
}
]
}

Business rules:

* Actual quantity must be >= 0.
* Difference = Actual Quantity - Current Quantity.
* If difference is negative, show warning style.
* If difference is positive, show success/increase style.
* Adjustment creates inventory transaction with type ADJUSTMENT.

UI notes:

* Difference should be calculated on the frontend for preview.
* Show warning when actual quantity is lower than current quantity.
* Show confirmation dialog before submit.

====================================================
5. STOCK MOVEMENT HISTORY PAGE
==============================

Route:

/system/inventory/transactions

Purpose:

View all stock movement transactions.

Page layout:

* Header:

  * Page title: Stock Movement History

* Filter area:

  * Search by transaction code
  * Filter by transaction type
  * Filter by date range
  * Filter by branch
  * Filter by created by
  * Filter by reference type
  * Filter by reference id

* Table columns:

  * Transaction Code
  * Type
  * Reference Type
  * Reference ID
  * Branch
  * Created By
  * Created At
  * Actions

Actions:

* View detail

Transaction Detail Drawer:

Show:

* Transaction Code
* Type
* Reference Type
* Reference ID
* Branch
* Created By
* Created At

Lines table:

* Inventory Item ID
* Reference ID
* Item Type
* Location
* Quantity
* Before Quantity
* After Quantity

API:

GET /api/inventory/inventory-transactions
GET /api/inventory/inventory-transactions/{id}

Transaction types:

* STOCK_IN
* SALE_OUT
* RETURN_IN
* ADJUSTMENT
* TRANSFER

====================================================
6. INVENTORY ITEM SUPPORT
=========================

Do not create a full Inventory Item management page unless necessary.

Inventory Items are mostly technical records created by Product module or backend.

However, the Inventory UI needs a reusable Inventory Item selector component.

Create component:

InventoryItemSelect

Purpose:

Used in:

* Stock In page
* Stock Adjustment page

Behavior:

* Search inventory items by:

  * inventoryItemId
  * referenceId
  * itemType

* Show result:

  * Inventory Item ID
  * Reference ID
  * Item Type
  * Status

API:

GET /api/inventory/inventory-items

Do not expose Inventory Item as a main sidebar page for now.

====================================================
API SERVICE FILES
=================

Create:

src/features/inventory/services/inventoryLocation.api.ts
src/features/inventory/services/inventoryBalance.api.ts
src/features/inventory/services/inventoryTransaction.api.ts
src/features/inventory/services/inventoryItem.api.ts

Use existing API client:

@hvt/api/inventory-client

Do not create a new Axios instance.

Do not store token manually.

Use the existing authentication flow.

====================================================
TYPES
=====

Create:

src/features/inventory/types/inventory.types.ts

Types should include:

InventoryItemType:

* PRODUCT_VARIANT
* INGREDIENT
* PACKAGING
* COMBO

InventoryLocationType:

* SHELF
* COUNTER
* STORAGE
* DAMAGED

InventoryStatus:

* ACTIVE
* INACTIVE

InventoryTransactionType:

* STOCK_IN
* SALE_OUT
* RETURN_IN
* ADJUSTMENT
* TRANSFER

InventoryBalanceResponse

InventoryLocationResponse

InventoryTransactionResponse

InventoryTransactionLineResponse

StockInRequest

StockAdjustmentRequest

====================================================
UI/UX REQUIREMENTS
==================

* Match the current SystemShell design.
* Reuse existing table and filter patterns from Manufacturing pages.
* Use loading state.
* Use empty state.
* Use error state.
* Use pagination.
* Use query params for filters.
* Use toast notification after successful create/update/status change.
* Use confirmation dialog before Stock In and Adjustment.
* Use drawer or side panel for transaction detail.
* Do not use random new colors.
* Do not redesign sidebar/topbar.
* Do not touch POS Sales page.

====================================================
SAMPLE DATA
===========

Use tea shop sample data:

Inventory Items:

* PRODUCT_VARIANT / referenceId 101 / Trà Olong 100g
* PRODUCT_VARIANT / referenceId 102 / Trà Sen 100g
* PRODUCT_VARIANT / referenceId 103 / Combo Trà Premium
* PACKAGING / referenceId 201 / Túi zip
* PACKAGING / referenceId 202 / Tem nhãn

Locations:

* SHELF_A / Kệ trà Olong / SHELF
* SHELF_B / Kệ trà Sen / SHELF
* COUNTER_01 / Quầy thu ngân / COUNTER
* DAMAGED_01 / Khu hàng lỗi / DAMAGED

Transactions:

* INV-STOCK-IN-000001
* INV-ADJ-000002
* INV-SALE-000003

====================================================
OUTPUT REQUIREMENTS
===================

Generate:

* Routes
* Sidebar menu update
* Pages
* Components
* API service files
* TypeScript types
* Form validation
* Table columns
* Search and filter handling
* Pagination handling
* Loading, empty and error states
* Toast notifications
* Confirmation dialogs
* Detail drawer for transaction history

Do not generate:

* Product management pages.
* POS sales pages.
* Purchase order pages.
* Supplier pages.
* Full warehouse transfer pages.
* Backend code.
