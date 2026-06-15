You are a senior Java Spring Boot developer.

Generate a complete Product Management module for a POS/ERP system.

Tech stack:

* Java 21
* Spring Boot 3
* Spring Data JPA
* Hibernate
* MySQL
* Lombok
* Maven
* RESTful API

Generate:

* Entity classes
* Enum classes
* Repository interfaces
* DTO Request/Response classes
* Service interfaces
* Service implementation classes
* REST Controllers
* Mapper classes
* Global exception handling
* Pagination, searching and filtering support

Do not generate Inventory, Order, Payment, Customer, or other unrelated modules.

====================================================
DATABASE TABLES
===============

CATEGORIES

* id BIGINT PK AUTO_INCREMENT
* parent_id BIGINT FK -> CATEGORIES.id
* name VARCHAR(255)
* status VARCHAR(50)

PRODUCTS

* id BIGINT PK AUTO_INCREMENT
* category_id BIGINT FK -> CATEGORIES.id
* name VARCHAR(255)
* description TEXT
* image_url VARCHAR(500)
* status VARCHAR(50)

PRODUCT_VARIANTS

* id BIGINT PK AUTO_INCREMENT
* product_id BIGINT FK -> PRODUCTS.id
* sku VARCHAR(100)
* variant_name VARCHAR(255)
* selling_price DECIMAL(18,2)
* cost_price DECIMAL(18,2)
* inventory_policy VARCHAR(50)
* status VARCHAR(50)

BARCODES

* id BIGINT PK AUTO_INCREMENT
* product_variant_id BIGINT FK -> PRODUCT_VARIANTS.id
* barcode_value VARCHAR(255)
* barcode_type VARCHAR(50)
* is_primary BOOLEAN

UNITS

* id BIGINT PK AUTO_INCREMENT
* name VARCHAR(100)
* symbol VARCHAR(50)
* precision_scale INTEGER

INGREDIENTS

* id BIGINT PK AUTO_INCREMENT
* ingredient_code VARCHAR(100)
* name VARCHAR(255)
* base_unit_id BIGINT FK -> UNITS.id
* status VARCHAR(50)

BOMS

* id BIGINT PK AUTO_INCREMENT
* product_variant_id BIGINT FK -> PRODUCT_VARIANTS.id
* version INTEGER
* status VARCHAR(50)
* effective_from DATETIME
* effective_to DATETIME

BOM_ITEMS

* id BIGINT PK AUTO_INCREMENT
* bom_id BIGINT FK -> BOMS.id
* ingredient_id BIGINT FK -> INGREDIENTS.id
* quantity DECIMAL(18,2)
* unit_id BIGINT FK -> UNITS.id
* waste_rate DECIMAL(5,2)

PRICE_LISTS

* id BIGINT PK AUTO_INCREMENT
* branch_id BIGINT
* name VARCHAR(255)
* effective_from DATETIME
* effective_to DATETIME
* status VARCHAR(50)

PRICE_LIST_ITEMS

* id BIGINT PK AUTO_INCREMENT
* price_list_id BIGINT FK -> PRICE_LISTS.id
* product_variant_id BIGINT FK -> PRODUCT_VARIANTS.id
* selling_price DECIMAL(18,2)

====================================================
ENTITY RELATIONSHIPS
====================

Category:

* Self relationship:

    * parentCategory
    * childCategories

Category 1 - N Product

Product 1 - N ProductVariant

ProductVariant 1 - N Barcode

ProductVariant 1 - N BOM

BOM 1 - N BOMItem

Ingredient 1 - N BOMItem

Unit 1 - N Ingredient

Unit 1 - N BOMItem

PriceList 1 - N PriceListItem

ProductVariant 1 - N PriceListItem

====================================================
ENUMS
=====

Status:

* ACTIVE
* INACTIVE

InventoryPolicy:

* TRACK_STOCK
* DO_NOT_TRACK

BarcodeType:

* EAN13
* QR_CODE
* INTERNAL

====================================================
API REQUIREMENTS
================

1. Category API

GET /api/categories

Support:

* Search by name
* Filter by status
* Pagination

GET /api/categories/{id}

POST /api/categories

PUT /api/categories/{id}

PATCH /api/categories/{id}/status

====================================================

2. Product API

GET /api/products

Support:

* Search by product name
* Filter by category
* Filter by status
* Pagination

GET /api/products/{id}

Response should include:

* Product information
* Category information
* Product variants

POST /api/products

Create:

* Product
* One or many ProductVariants
* Barcode list for each variant

Example:
Tea Oolong
- 100g
- 200g
- 500g

PUT /api/products/{id}

PATCH /api/products/{id}/status

====================================================

3. Product Variant API

GET /api/product-variants

Support:

* Search by SKU
* Search by variant name
* Search by barcode
* Filter by status
* Pagination

GET /api/product-variants/{id}

PATCH /api/product-variants/{id}/status

====================================================

4. Barcode API

GET /api/barcodes

Support:

* Search barcode value

GET /api/barcodes/{value}

Used for POS barcode scanning.

Return:

* ProductVariant information

====================================================

5. Unit API

GET /api/units

POST /api/units

PUT /api/units/{id}

PATCH /api/units/{id}/status

====================================================

6. Ingredient API

GET /api/ingredients

Support:

* Search by name
* Search by ingredient code
* Filter by status
* Pagination

GET /api/ingredients/{id}

POST /api/ingredients

PUT /api/ingredients/{id}

PATCH /api/ingredients/{id}/status

====================================================

7. BOM API

GET /api/boms

Filter:

* Product Variant
* Version
* Status

GET /api/boms/{id}

POST /api/boms

Business rules:

* One Product Variant can have multiple BOM versions.
* Only one BOM version can be ACTIVE at the same time.
* A BOM contains multiple BOM items.
* Store quantity and waste rate for each ingredient.

PUT /api/boms/{id}

PATCH /api/boms/{id}/status

====================================================

8. Price List API

GET /api/price-lists

Filter:

* Branch
* Status
* Effective date

GET /api/price-lists/{id}

POST /api/price-lists

Business rules:

* One Price List contains multiple Price List Items.
* A Product Variant can have different prices in different Price Lists.

PUT /api/price-lists/{id}

PATCH /api/price-lists/{id}/status

====================================================
BUSINESS RULES
==============

* SKU must be unique.
* Barcode value must be unique.
* Only one barcode can be primary for each Product Variant.
* Only one active BOM version is allowed for each Product Variant.
* Selling price must be greater than or equal to zero.
* Cost price must be greater than or equal to zero.
* Quantity in BOM items must be greater than zero.
* Waste rate must be between 0 and 100.
* A Product must have at least one Product Variant.
* A Category cannot be deleted if it contains Products.

Use @Transactional for operations that create or update Product with ProductVariants, Barcodes, BOMs, and Price Lists.

====================================================
PACKAGE STRUCTURE
=================

com.example.product

├── controller
├── dto
│   ├── request
│   └── response
├── entity
├── enums
├── exception
├── mapper
├── repository
├── service
├── service.impl

====================================================
OUTPUT REQUIREMENTS
===================

Generate complete code for:

1. All Entity classes.
2. All Enum classes.
3. Repository interfaces.
4. Request DTO classes.
5. Response DTO classes.
6. Service interfaces.
7. Service implementation classes.
8. REST Controllers.
9. Custom exceptions:

    * ResourceNotFoundException
    * BadRequestException
10. GlobalExceptionHandler.
11. Mapper classes.

Code requirements:

* Use Java 21 features where appropriate.
* Use Lombok annotations.
* Use FetchType.LAZY for relationships.
* Use DTOs instead of returning Entities.
* Use validation annotations for request fields.
* Use JpaRepository.
* Use clean architecture principles.
* Include all required imports.
* The generated code must compile.
* Do not generate unrelated modules.

Important:
Product module only manages business information.

Stock quantity, stock movement, stock reservation, and inventory transactions are handled by Inventory module through InventoryItem.reference_id and InventoryItem.item_type.
Do not create any dependency from Product to Inventory.
