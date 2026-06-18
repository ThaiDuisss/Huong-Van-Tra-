export const routePaths = {
    home: "/",
    posSales: "/pos/sales",
    system: "/system",
    categories: "/system/categories",
    products: "/system/products",
    productDetail: "/system/products/:id",
    productVariants: "/system/product-variants",
    productVariantsByProduct:
        "/system/products/:productId/variants",
    productVariantDetail: "/system/product-variants/:id",
    priceLists: "/system/price-lists",
    priceListDetail: "/system/price-lists/:id",
    inventoryLocations: "/system/inventory/locations",
    inventoryBalances: "/system/inventory/balances",
    inventoryStockIn: "/system/inventory/stock-in",
    inventoryAdjustment: "/system/inventory/adjustment",
    inventoryTransactions: "/system/inventory/transactions",
    customers: "/system/customers",
    customerGroups: "/system/customers/groups",
    bomList: "/system/manufacturing/boms",
    productionOrders:
        "/system/manufacturing/production-orders"
} as const;
