export const routePaths = {
    home: "/",
    posSales: "/pos/sales",
    salesHistory: "/sales-history",
    system: "/system",
    orders: "/system/orders",
    invoices: "/system/orders/invoices",
    invoiceDetail: "/system/orders/invoices/:invoiceId",
    returns: "/system/orders/returns",
    refunds: "/system/orders/refunds",
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
    inventoryBalances: "/pos/inventory",
    inventoryStockIn: "/system/inventory/stock-in",
    inventoryAdjustment: "/system/inventory/adjustment",
    inventoryTransactions: "/system/inventory/transactions",
    customers: "/system/customers",
    customerGroups: "/system/customers/groups",
    customerTiers: "/system/customers/tiers",
    bomList: "/system/manufacturing/boms",
    productionOrders:
        "/system/manufacturing/production-orders"
} as const;

