import { useCallback, useEffect, useState } from "react";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { NativeSelect } from "../../../components/ui/Field";
import { SearchInput } from "../../../components/ui/SearchInput";
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
        async (itemId: string, locationId: string, locationName: string) => {
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
        <section className="grid gap-5">
            {/* Header */}
            <ListPageHeader
                description="Theo dõi tồn kho theo mặt hàng, vị trí và lô hàng"
                title="Tồn kho kho tổng"
            />

            {/* Search & Filter Card */}
            <div className={`rounded-2xl bg-white p-4 shadow-pos-card transition-all duration-300 ${drawerOpen ? "opacity-50 pointer-events-none" : ""}`}>
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 justify-between">
                    <SearchInput
                        onChange={handleSearch}
                        placeholder="Tìm theo SKU hoặc tên..."
                        type="text"
                        value={keyword}
                    />
                    <div className="min-w-[140px] shrink-0">
                        <NativeSelect
                            value={filterItemType}
                            onChange={(e) => {
                                setFilterItemType(e.target.value);
                                setPage(1);
                                setDrawerOpen(false);
                            }}
                        >
                            <option value="">Tất cả loại</option>
                            <option value="PRODUCT_VARIANT">Sản phẩm</option>
                            <option value="INGREDIENT">Nguyên liệu</option>
                        </NativeSelect>
                    </div>
                </div>
            </div>

            {/* Table Card */}
            <div className={`rounded-2xl bg-white shadow-pos-card overflow-hidden transition-all duration-300 ${drawerOpen ? "opacity-50 pointer-events-none" : ""}`}>
                <ProductInventorySummaryTable
                    products={products}
                    loading={loading}
                    onLocationSelect={handleLocationSelect}
                    isDetailPanelOpen={drawerOpen}
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
