import { Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { BatchDetailDrawer } from "../components/BatchDetailDrawer";
import { ProductInventorySummaryTable } from "../components/ProductInventorySummaryTable";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import type { WarehouseInventorySummary } from "../services/warehouseInventory.api";
import { warehouseInventoryApi } from "../services/warehouseInventory.api";

export function WarehouseInventoryPage() {
    const [products, setProducts] = useState<WarehouseInventorySummary[]>([]);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [filterItemType, setFilterItemType] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Drawer state
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
    const [selectedLocationId, setSelectedLocationId] = useState<string | null>(null);
    const [selectedLocationName, setSelectedLocationName] = useState<string>("");
    const [selectedItemName, setSelectedItemName] = useState<string>("");

    // Load products
    useEffect(() => {
        const loadProducts = async () => {
            setLoading(true);
            try {
                // TODO: Get branchId from context
                const branchId = "00000000-0000-0000-0000-000000000001"; // Placeholder
                const data = await warehouseInventoryApi.getProductInventorySummary(branchId, {
                    keyword: keyword || undefined,
                    itemType: filterItemType || undefined,
                    page: page - 1,
                    size: pageSize
                });
                setProducts(data.content);
                setTotalPages(data.totalPages);
                setTotalElements(data.totalElements);
            } catch (err) {
                console.error("Error loading products:", err);
            } finally {
                setLoading(false);
            }
        };

        loadProducts();
    }, [keyword, filterItemType, page, pageSize]);

    // Load location details when viewing product
    const handleLocationSelect = useCallback(
        async (itemId: string, locationId: string, locationName: string, _locationType: string) => {
            try {
                // Find product
                const product = products.find((p) => p.inventoryItemId === itemId);
                if (!product) return;

                // Open drawer
                setSelectedItemId(itemId);
                setSelectedLocationId(locationId);
                setSelectedLocationName(locationName);
                setSelectedItemName(product.itemName);
                setDrawerOpen(true);
            } catch (err) {
                console.error("Error loading location details:", err);
            }
        },
        [products]
    );

    const handleSearch = (value: string) => {
        setKeyword(value);
        setPage(1);
        setDrawerOpen(false);
    };

    return (
        <section className="grid gap-5 p-5">
            {/* Header */}
            <header className="flex flex-col gap-1">
                <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                    Tồn kho kho tổng
                </h1>
                <p className="text-sm text-gray-500">
                    Theo dõi tồn kho theo mặt hàng, vị trí và lô hàng
                </p>
            </header>

            {/* Search & Filter Card */}
            <div className="rounded-2xl bg-white p-4 shadow-pos-card">
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 justify-between">
                    <div className="relative flex-1">
                        <Search size={16} className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
                        <input
                            type="text"
                            placeholder="Tìm theo SKU hoặc tên..."
                            value={keyword}
                            onChange={(e) => handleSearch(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 border border-pos-border rounded-xl text-sm outline-none focus:border-hvt-green-700"
                        />
                    </div>
                    <div className="min-w-[140px] shrink-0">
                        <select
                            value={filterItemType}
                            onChange={(e) => {
                                setFilterItemType(e.target.value);
                                setPage(1);
                                setDrawerOpen(false);
                            }}
                            className="h-10 w-full border border-pos-border rounded-xl px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317]"
                        >
                            <option value="">Tất cả loại</option>
                            <option value="PRODUCT_VARIANT">Sản phẩm</option>
                            <option value="INGREDIENT">Nguyên liệu</option>
                        </select>
                    </div>
                </div>
            </div>

            {/* Table Card */}
            <div className="rounded-2xl bg-white shadow-pos-card overflow-hidden">
                <ProductInventorySummaryTable
                    products={products}
                    loading={loading}
                    onLocationSelect={handleLocationSelect}
                />

                {/* Pagination */}
                {totalPages > 0 && (
                    <Pagination
                        page={page}
                        pageCount={totalPages}
                        totalItems={totalElements}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={(size) => {
                            setPageSize(size);
                            setPage(1);
                        }}
                        totalItemsLabel="Tổng số mặt hàng/nguyên liệu"
                    />
                )}
            </div>

            {/* Batch Detail Drawer (Slide in từ phải) */}
            {selectedItemId && selectedLocationId && (
                <BatchDetailDrawer
                    open={drawerOpen}
                    onClose={() => setDrawerOpen(false)}
                    itemId={selectedItemId}
                    locationId={selectedLocationId}
                    locationName={selectedLocationName}
                    itemName={selectedItemName}
                />
            )}
        </section>
    );
}
