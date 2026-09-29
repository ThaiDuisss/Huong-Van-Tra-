import { Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { BatchDetailDrawer } from "../components/BatchDetailDrawer";
import { ProductInventorySummaryTable } from "../components/ProductInventorySummaryTable";
import type { LocationInventoryDetail, WarehouseInventorySummary } from "../services/warehouseInventory.api";
import { warehouseInventoryApi } from "../services/warehouseInventory.api";

export function WarehouseInventoryPage() {
    const [products, setProducts] = useState<WarehouseInventorySummary[]>([]);
    const [locationDetails, setLocationDetails] = useState<Record<string, LocationInventoryDetail[]>>({});
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    // Drawer state
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
    const [selectedLocationId, setSelectedLocationId] = useState<string | null>(null);
    const [selectedLocationName, setSelectedLocationName] = useState<string>("");
    const [selectedProductName, setSelectedProductName] = useState<string>("");

    // Load products
    useEffect(() => {
        const loadProducts = async () => {
            setLoading(true);
            try {
                // TODO: Get branchId from context
                const branchId = "00000000-0000-0000-0000-000000000001"; // Placeholder
                const data = await warehouseInventoryApi.getProductInventorySummary(branchId, {
                    keyword: keyword || undefined,
                    page,
                    size: 20
                });
                setProducts(data.content);
                setTotalPages(data.totalPages);
            } catch (err) {
                console.error("Error loading products:", err);
            } finally {
                setLoading(false);
            }
        };

        loadProducts();
    }, [keyword, page]);

    // Load location details when viewing product
    const handleLocationSelect = useCallback(
        async (itemId: string, locationId: string, locationName: string) => {
            try {
                // Load location details if not already loaded
                if (!locationDetails[itemId]) {
                    const details = await warehouseInventoryApi.getProductLocationInventory(itemId);
                    setLocationDetails((prev) => ({
                        ...prev,
                        [itemId]: details
                    }));
                }

                // Find product name
                const product = products.find((p) => p.inventoryItemId === itemId);

                // Open drawer
                setSelectedItemId(itemId);
                setSelectedLocationId(locationId);
                setSelectedLocationName(locationName);
                setSelectedProductName(product?.productName || "");
                setDrawerOpen(true);
            } catch (err) {
                console.error("Error loading location details:", err);
            }
        },
        [products, locationDetails]
    );

    const handleSearch = (value: string) => {
        setKeyword(value);
        setPage(0);
    };

    return (
        <div className="space-y-4 p-4">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Quản lý tồn kho kho tổng</h1>
                <p className="text-sm text-gray-600">Xem chi tiết tồn kho sản phẩm theo vị trí (Kệ POS / Kho tổng) và danh sách lô hàng</p>
            </div>

            {/* Search */}
            <div className="flex gap-2">
                <div className="flex-1 relative">
                    <Search size={16} className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
                    <input
                        type="text"
                        placeholder="Tìm mã/tên sản phẩm..."
                        value={keyword}
                        onChange={(e) => handleSearch(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:border-blue-500"
                    />
                </div>
            </div>

            {/* Product Table */}
            <ProductInventorySummaryTable
                products={products}
                loading={loading}
                onLocationSelect={handleLocationSelect}
                locationDetails={locationDetails}
            />

            {/* Pagination */}
            {totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 mt-4">
                    <button
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                        className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50"
                    >
                        Trước
                    </button>
                    <span className="text-sm text-gray-600">
                        Trang {page + 1} / {totalPages}
                    </span>
                    <button
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50"
                    >
                        Sau
                    </button>
                </div>
            )}

            {/* Batch Detail Drawer */}
            {selectedItemId && selectedLocationId && (
                <BatchDetailDrawer
                    open={drawerOpen}
                    onClose={() => setDrawerOpen(false)}
                    itemId={selectedItemId}
                    locationId={selectedLocationId}
                    locationName={selectedLocationName}
                    productName={selectedProductName}
                />
            )}
        </div>
    );
}
