You are a senior Java Spring Boot developer.

Generate a complete Spring Boot 3 Inventory Management module for a POS/ERP system.

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
* Service implementations
* REST Controllers
* Mapper classes
* Global exception handling
* Pagination, search, and filtering support

Do not generate Product module, Order module, or other unrelated modules.

====================================================
DATABASE TABLES
===============

INVENTORY_ITEMS

* id BIGINT PK AUTO_INCREMENT
* reference_id BIGINT
* item_type VARCHAR(50)
* base_unit_id BIGINT
* status VARCHAR(50)

INVENTORY_LOCATIONS

* id BIGINT PK AUTO_INCREMENT
* branch_id BIGINT
* parent_location_id BIGINT FK -> INVENTORY_LOCATIONS.id
* code VARCHAR(100)
* name VARCHAR(255)
* type VARCHAR(50)
* status VARCHAR(50)

INVENTORY_BALANCES

* id BIGINT PK AUTO_INCREMENT
* inventory_item_id FK -> INVENTORY_ITEMS.id
* location_id FK -> INVENTORY_LOCATIONS.id
* quantity_on_hand DECIMAL(18,2)
* quantity_reserved DECIMAL(18,2)
* version BIGINT
* updated_at DATETIME

INVENTORY_TRANSACTIONS

* id BIGINT PK AUTO_INCREMENT
* transaction_code VARCHAR(100)
* type VARCHAR(50)
* reference_type VARCHAR(50)
* reference_id BIGINT
* branch_id BIGINT
* created_by BIGINT
* created_at DATETIME

INVENTORY_TRANSACTION_LINES

* id BIGINT PK AUTO_INCREMENT
* transaction_id FK -> INVENTORY_TRANSACTIONS.id
* inventory_item_id FK -> INVENTORY_ITEMS.id
* location_id FK -> INVENTORY_LOCATIONS.id
* quantity DECIMAL(18,2)
* before_quantity DECIMAL(18,2)
* after_quantity DECIMAL(18,2)

====================================================
ENTITY RELATIONSHIPS
====================

InventoryItem 1 - N InventoryBalance

InventoryLocation 1 - N InventoryBalance

InventoryTransaction 1 - N InventoryTransactionLine

InventoryItem 1 - N InventoryTransactionLine

InventoryLocation 1 - N InventoryTransactionLine

InventoryLocation has self relationship:

* parentLocation
* childLocations

Important:

* INVENTORY_ITEMS.reference_id is a polymorphic reference.
* Do not create JPA relationship with Product, ProductVariant, Ingredient, or any external module.
* Use reference_id + item_type to identify the real business object.
* INVENTORY_TRANSACTIONS.reference_type and reference_id are also polymorphic references.
* Do not create JPA foreign keys for them.
* Use FetchType.LAZY for all relationships.
* Use BigDecimal for quantity fields.
* Use LocalDateTime for date fields.
* Use DTOs instead of returning Entity from API.

====================================================
ENUMS
=====

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

InventoryReferenceType:

* MANUAL
* ORDER
* RETURN_ORDER
* STOCK_TRANSFER
* STOCK_COUNT

====================================================
API REQUIREMENTS
================

1. Inventory Item API

GET /api/inventory-items

Support:

* Search by referenceId
* Filter by itemType
* Filter by status
* Pagination

GET /api/inventory-items/{id}

POST /api/inventory-items

Create a new inventory item with:

* referenceId
* itemType
* baseUnitId
* status

PUT /api/inventory-items/{id}

PATCH /api/inventory-items/{id}/status

====================================================

2. Inventory Location API

GET /api/inventory-locations

Support:

* Filter by branchId
* Filter by type
* Filter by status

GET /api/inventory-locations/{id}

POST /api/inventory-locations

Create:

* Shelf
* Counter
* Storage
* Damaged location

PUT /api/inventory-locations/{id}

PATCH /api/inventory-locations/{id}/status

====================================================

3. Inventory Balance API

GET /api/inventory-balances

Support:

* Filter by inventoryItemId
* Filter by locationId
* Filter by branchId
* Pagination

GET /api/inventory-balances/{id}

GET /api/inventory-items/{id}/balances

Return all balances of an inventory item across locations.

====================================================

4. Stock In API

POST /api/inventory-transactions/stock-in

Business Logic:

* Validate inventory item exists and active.
* Validate location exists and active.
* Quantity must be greater than 0.
* Create InventoryTransaction with type STOCK_IN.
* Create InventoryTransactionLine.
* If balance does not exist, create a new balance.
* Increase quantity_on_hand.
* Store before_quantity and after_quantity.
* Generate transaction_code automatically.

====================================================

5. Stock Adjustment API

POST /api/inventory-transactions/adjustment

Business Logic:

* Find current balance.
* Calculate difference:
  difference = actualQuantity - currentQuantity
* Update quantity_on_hand.
* Create transaction type ADJUSTMENT.
* Save before_quantity and after_quantity.

====================================================

6. Inventory Transaction History API

GET /api/inventory-transactions

Support filters:

* transactionCode
* type
* fromDate
* toDate
* createdBy
* branchId
* referenceType
* referenceId
* Pagination

GET /api/inventory-transactions/{id}

Response should include:

* Transaction information.
* List of transaction lines.
* Inventory item id.
* Location id.
* Quantity changed.
* Before quantity.
* After quantity.

====================================================
BUSINESS RULES
==============

* quantity_on_hand cannot be negative.
* quantity_reserved cannot be negative.
* Available quantity = quantity_on_hand - quantity_reserved.
* Cannot create stock movement for inactive inventory items.
* Cannot create stock movement for inactive locations.
* Stock in quantity must be greater than 0.
* Adjustment actual quantity must be greater than or equal to 0.
* Use @Transactional for all inventory movement operations.

====================================================
PACKAGE STRUCTURE
=================

com.example.inventory

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
│   ├── InventoryItemService
│   └── InventoryLocationService
│   └── InventoryBalanceService
│   └── InventoryTransactionService
├── service.impl

====================================================
OUTPUT REQUIREMENTS
===================

Generate complete code for:

1. All Entities.
2. All Enums.
3. Repository interfaces.
4. Request DTOs.
5. Response DTOs.
6. Service interfaces.
7. Service Implementations.
8. REST Controllers.
9. Custom exceptions:

    * ResourceNotFoundException
    * BadRequestException.
10. GlobalExceptionHandler.
11. Mapper classes.

Code requirements:

* Use clean architecture principles.
* Use JpaRepository.
* Use @Transactional where necessary.
* Use Lombok annotations.
* Use proper validation annotations.
* Include all imports.
* The code must compile.
* Do not generate any Product, ProductVariant, Ingredient, or Order classes.
