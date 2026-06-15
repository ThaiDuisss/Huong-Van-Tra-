You are a senior React + TypeScript frontend developer.

I already have an existing POS Web application with a completed design system and layouts.

Current application:

* POS Workspace:

  * Sales screen.

* System Workspace:

  * Manufacturing:

    * BOM Management
    * Production Order Management.

Your task is NOT to redesign the application.

You must:

* Reuse existing SystemShell layout.
* Reuse existing sidebar.
* Reuse existing topbar.
* Reuse existing table, filter panel, card, modal, drawer, button, badge, pagination and form components.
* Follow the current feature-based structure.
* Keep the same spacing, colors, typography and Tailwind styling style.

Do not create a new UI framework.

====================================================
ADD NEW SYSTEM MODULE:
PRODUCT MANAGEMENT
==================

Add a new menu inside System Sidebar:

System
|
|-- Product Management
|      |
|      |-- Categories
|      |-- Products
|      |-- Product Variants
|      |-- Price Lists
|
|-- Manufacturing
|
|-- BOM
|-- Production Orders

Create new feature:

src/features/product/

Structure:

product/
├── pages/
├── components/
├── services/
├── types/
├── hooks/
└── utils/

====================================================

1. CATEGORY MANAGEMENT
   ====================================================

Route:

/system/categories

Purpose:
Manage product categories.

Page layout:

* Header:

  * Page title
  * Create Category button

* Filter area:

  * Search category name
  * Filter status

* Table:
  Columns:

* Category Name

* Parent Category

* Status

* Created Date

* Actions

Actions:

* View detail
* Edit
* Change status

Create/Edit Form:

Fields:

* Category Name
* Parent Category
* Status

API:

GET /api/categories
GET /api/categories/{id}
POST /api/categories
PUT /api/categories/{id}
PATCH /api/categories/{id}/status

====================================================
2. PRODUCT MANAGEMENT
=====================

Route:

/system/products

Product is only a product group.

Example:

Product:
Trà Olong

Variants:

* Trà Olong 100g
* Trà Olong 200g
* Trà Olong 500g

Page layout:

Header:

* Page title
* Create Product button

Filters:

* Search product name
* Filter category
* Filter status

Table:

Columns:

* Image
* Product Name
* Category
* Total Variants
* Status
* Actions

Actions:

* View Detail
* Edit Product
* Change Status
* Manage Variants

Product Detail:

Route:

/system/products/{id}

Display:

Product Information Card:

* Image
* Name
* Description
* Category
* Status

Variant List:

* SKU
* Variant Name
* Selling Price
* Status

Buttons:

* Add Variant
* Edit Product

Create/Edit Product Form:

Fields:

* Category
* Name
* Description
* Image Upload
* Status

Business Rules:

* Creating Product only creates Product information.
* Product does not automatically create Product Variant.

====================================================
3. PRODUCT VARIANT MANAGEMENT
=============================

Routes:

/system/product-variants

/system/products/{productId}/variants

Purpose:

Manage actual sellable items.

Example:

Product:
Trà Olong

Variants:

* 100g
* 200g
* 500g

Page:

Filters:

* Search SKU
* Search Variant Name
* Filter Product
* Filter Status

Table:

Columns:

* SKU
* Product Name
* Variant Name
* Selling Price
* Cost Price
* Inventory Policy
* Status
* Actions

Actions:

* View Detail
* Edit
* Change Status

Create Variant Form:

Fields:

* Product
* SKU
* Variant Name
* Selling Price
* Cost Price
* Inventory Policy:

  * TRACK_STOCK
  * DO_NOT_TRACK
* Status

Detail Page:

/system/product-variants/{id}

Display:

* Product Information
* Variant Information
* SKU
* Selling Price
* Cost Price
* Inventory Policy
* Status

Business Rules:

* SKU must be unique.
* Variant lifecycle is independent from Product.

====================================================
4. PRICE LIST MANAGEMENT
========================

Routes:

/system/price-lists

Purpose:

Manage selling price policies.

List Page:

Filters:

* Search price list name
* Filter branch
* Filter status
* Filter effective date

Table:

Columns:

* Price List Name
* Branch
* Effective From
* Effective To
* Status
* Actions

Actions:

* View Detail
* Edit
* Change Status

Create/Edit Price List:

Fields:

* Name
* Branch
* Effective From
* Effective To
* Status

Price List Detail:

Route:

/system/price-lists/{id}

Display:

Price List Information Card.

Price Items Table:

Columns:

* SKU
* Product Name
* Variant Name
* Selling Price

Actions:

* Add Variant Price
* Edit Price
* Remove Price

Business Rules:

* One Price List can contain multiple Product Variant prices.
* A Product Variant can belong to multiple Price Lists with different prices.

====================================================
ROUTING
=======

Update existing route configuration.

Add routes under System routes only.

Do not modify POS routes.

Follow the existing route metadata and navigation pattern.

====================================================
API INTEGRATION
===============

Create service files:

category.api.ts
product.api.ts
productVariant.api.ts
priceList.api.ts

Use existing API client:

@hvt/api-client

Do not create a new Axios instance.

Handle:

* Loading state.
* Empty state.
* Error state.
* Pagination.
* Search and filter query params.

====================================================
UI REQUIREMENTS
===============

Follow existing pages such as:

* BOM List Page.
* Production Order List Page.

Use the same patterns:

* Statistics cards if appropriate.
* Filter section.
* Data table.
* Side detail panel or modal.
* Pagination.
* Confirmation dialog before status changes.
* Toast notification after successful actions.

Do not:

* Change existing layout.
* Create a new sidebar.
* Change theme colors.
* Generate POS screens.
* Generate Inventory screens yet.
* Generate Barcode management.

Only extend the current System Management workspace.
features/
├── sales/
├── manufacturing/
└── product/      <-- thêm mới

và phần menu System sau khi mở rộng sẽ là:

System
├── Product Management
│   ├── Categories
│   ├── Products
│   ├── Product Variants
│   └── Price Lists
│
└── Manufacturing
    ├── BOM
    └── Production Orders