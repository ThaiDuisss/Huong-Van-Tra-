import { Eye, History, ArrowUpDown, ArrowLeftRight, X, MoreHorizontal } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";
import { storeShelfApi } from "../services/storeShelf.api";
import { StoreInventoryForm } from "../components/StoreInventoryForm";
import { routePaths } from "../../../routes/path";
import { formatInventoryDate, formatQuantity } from "../utils/inventory-labels";
import type { InventoryBalanceViewResponse, InventoryLocationResponse } from "../types/inventory.types";
import { categoryApi } from "../../product/services/category.api";
import { unitApi } from "../../product/services/unit.api";
import { productApi } from "../../product/services/product.api";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";

const FALLBACK_STORE_ID = "11111111-1111-1111-1111-111111111111";

interface JoinedRow {
    id: string;
    inventoryItemId: string;
    locationId: string;
    branchId: string;
    "Ảnh sản phẩm": string | null;
    "SKU": string;
    "Tên sản phẩm": string;
    "Danh mục": string;
    "Đơn vị": string;
    "Tồn thực tế": number;
    "Tồn giữ chỗ": number;
    "Có thể bán": number;
    "Vị trí": string;
    "Kệ": string;
    "Giá nhập"?: number;
    "Giá bán"?: number;
    "Ngày cập nhật": string;
    [key: string]: any; // Allow future custom fields
}

export function InventoryBalancePage() {

    // Store & Catalog states
    const [activeStoreId, setActiveStoreId] = useState<string | null>(null);
    const [activeLocationId, setActiveLocationId] = useState<string | null>("");

    const [loadingStore, setLoadingStore] = useState(true);

    const [locations, setLocations] = useState<InventoryLocationResponse[]>([]);

    const [balances, setBalances] = useState<any[]>([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loadingBalances, setLoadingBalances] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Filter, pagination & sorting states
    const [searchQuery, setSearchQuery] = useState("");
    const [filterCategoryId, setFilterCategoryId] = useState("");
    const [filterUnitId, setFilterUnitId] = useState("");
    const [filterStatus, setFilterStatus] = useState("");
    const [filterProductId, setFilterProductId] = useState("");
    const [filterProductName, setFilterProductName] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Searchable dropdown states: Shelf (Location)
    const [shelfOptions, setShelfOptions] = useState<LookupItem[]>([]);
    const [shelfPage, setShelfPage] = useState(0);
    const [shelfHasMore, setShelfHasMore] = useState(true);
    const [shelfLoading, setShelfLoading] = useState(false);
    const [shelfSearchTerm, setShelfSearchTerm] = useState("");

    // Searchable dropdown states: Category
    const [categoryOptions, setCategoryOptions] = useState<LookupItem[]>([]);
    const [categoryPage, setCategoryPage] = useState(0);
    const [categoryHasMore, setCategoryHasMore] = useState(true);
    const [categoryLoading, setCategoryLoading] = useState(false);
    const [categorySearchTerm, setCategorySearchTerm] = useState("");

    // Searchable dropdown states: Unit
    const [unitOptions, setUnitOptions] = useState<LookupItem[]>([]);
    const [unitPage, setUnitPage] = useState(0);
    const [unitHasMore, setUnitHasMore] = useState(true);
    const [unitLoading, setUnitLoading] = useState(false);
    const [unitSearchTerm, setUnitSearchTerm] = useState("");

    // Searchable dropdown states: Product
    const [productOptions, setProductOptions] = useState<LookupItem[]>([]);
    const [productPage, setProductPage] = useState(0);
    const [productHasMore, setProductHasMore] = useState(true);
    const [productLoading, setProductLoading] = useState(false);
    const [productSearchTerm, setProductSearchTerm] = useState("");

    const [sortColumn, setSortColumn] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

    // UI Configuration states
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});
    const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>({});

    // Drawer / Panel state
    const [selectedRow, setSelectedRow] = useState<JoinedRow | null>(null);
    const [activeModal, setActiveModal] = useState<"stock-in" | "adjustment" | "transfer" | null>(null);
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);

    // Dashboard Metrics state (reserved for future KPI display)
    const [_dashboardMetrics] = useState({
        totalSku: 0,
        totalStock: 0,
        lowStock: 0,
        outOfStock: 0,
        totalValue: 0
    });

    // 1. Resolve Active Store and Load Locations
    // Fetch shelf options with infinite scroll
    const loadShelfOptions = useCallback(async (search: string, page: number) => {
        try {
            setShelfLoading(true);
            const res = await inventoryLocationApi.lookup(search, page, 20);
            setShelfOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setShelfHasMore(!res.last);
        } catch (err) {
            setShelfHasMore(false);
        } finally {
            setShelfLoading(false);
        }
    }, []);

    useEffect(() => {
        setShelfPage(0);
        loadShelfOptions(shelfSearchTerm, 0);
    }, [shelfSearchTerm, loadShelfOptions]);

    const handleLoadMoreShelves = useCallback(() => {
        if (!shelfLoading && shelfHasMore) {
            const nextPage = shelfPage + 1;
            setShelfPage(nextPage);
            loadShelfOptions(shelfSearchTerm, nextPage);
        }
    }, [shelfLoading, shelfHasMore, shelfPage, shelfSearchTerm, loadShelfOptions]);

    // Fetch category options with infinite scroll
    const loadCategoryOptions = useCallback(async (search: string, page: number) => {
        try {
            setCategoryLoading(true);
            const res = await categoryApi.lookup(search, page, 20);
            setCategoryOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setCategoryHasMore(!res.last);
        } catch (err) {
            setCategoryHasMore(false);
        } finally {
            setCategoryLoading(false);
        }
    }, []);

    useEffect(() => {
        setCategoryPage(0);
        loadCategoryOptions(categorySearchTerm, 0);
    }, [categorySearchTerm, loadCategoryOptions]);

    const handleLoadMoreCategories = useCallback(() => {
        if (!categoryLoading && categoryHasMore) {
            const nextPage = categoryPage + 1;
            setCategoryPage(nextPage);
            loadCategoryOptions(categorySearchTerm, nextPage);
        }
    }, [categoryLoading, categoryHasMore, categoryPage, categorySearchTerm, loadCategoryOptions]);

    // Fetch unit options with infinite scroll
    const loadUnitOptions = useCallback(async (search: string, page: number) => {
        try {
            setUnitLoading(true);
            const res = await unitApi.lookup(search, page, 20);
            setUnitOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setUnitHasMore(!res.last);
        } catch (err) {
            setUnitHasMore(false);
        } finally {
            setUnitLoading(false);
        }
    }, []);

    useEffect(() => {
        setUnitPage(0);
        loadUnitOptions(unitSearchTerm, 0);
    }, [unitSearchTerm, loadUnitOptions]);

    const handleLoadMoreUnits = useCallback(() => {
        if (!unitLoading && unitHasMore) {
            const nextPage = unitPage + 1;
            setUnitPage(nextPage);
            loadUnitOptions(unitSearchTerm, nextPage);
        }
    }, [unitLoading, unitHasMore, unitPage, unitSearchTerm, loadUnitOptions]);

    // Fetch product options with infinite scroll
    const loadProductOptions = useCallback(async (search: string, page: number) => {
        try {
            setProductLoading(true);
            const res = await productApi.lookup(search, page, 20);
            setProductOptions(prev => page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setProductHasMore(!res.last);
        } catch (err) {
            setProductHasMore(false);
        } finally {
            setProductLoading(false);
        }
    }, []);

    useEffect(() => {
        setProductPage(0);
        loadProductOptions(productSearchTerm, 0);
    }, [productSearchTerm, loadProductOptions]);

    const handleLoadMoreProducts = useCallback(() => {
        if (!productLoading && productHasMore) {
            const nextPage = productPage + 1;
            setProductPage(nextPage);
            loadProductOptions(productSearchTerm, nextPage);
        }
    }, [productLoading, productHasMore, productPage, productSearchTerm, loadProductOptions]);

    // 1. Resolve Active Store and Load Locations
    useEffect(() => {
        async function initCatalogAndStore() {
            try {
                // Fetch locations list
                const locRes = await inventoryLocationApi.list({ page: 0, size: 500 });
                const locs = locRes.content ?? [];
                setLocations(locs);

                // Determine branchId
                let resolvedBranchId = FALLBACK_STORE_ID;
                if (locs.length > 0) {
                    resolvedBranchId = String(locs[0].branchId);
                } else {
                    const balCheck = await inventoryBalanceApi.view({ page: 0, size: 10 });
                    if (balCheck.content && balCheck.content.length > 0) {
                        resolvedBranchId = String(balCheck.content[0].branchId);
                    }
                }
                setActiveStoreId(resolvedBranchId);


            } catch (err) {
                console.error("Initialization error:", err);
                setActiveStoreId(FALLBACK_STORE_ID);
            } finally {
                setLoadingStore(false);
            }
        }
        initCatalogAndStore();
    }, []);

    // Active warehouse location name
    const activeLocationName = useMemo(() => {
        if (activeLocationId) {
            return locations.find(l => String(l.id) === activeLocationId)?.name || "";
        }
        if (balances.length > 0) {
            return balances[0].locationName || "";
        }
        const warehouseLoc = locations.find(l => l.type === "WAREHOUSE" && String(l.branchId) === activeStoreId);
        return warehouseLoc ? warehouseLoc.name : "Cửa hàng";
    }, [activeLocationId, locations, balances, activeStoreId]);



    // 2. Fetch inventory balances filtered by active store, location, and search criteria
    const loadBalances = useCallback(async () => {
        if (!activeStoreId) return;
        setLoadingBalances(true);
        setError(null);
        try {
            const res = await inventoryBalanceApi.view({
                page: page - 1,
                size: pageSize,
                branchId: activeStoreId as any,
                locationId: activeLocationId ? (activeLocationId as any) : undefined,
                keyword: searchQuery.trim() || undefined,
                productName: filterProductName || undefined,
                categoryId: filterCategoryId || undefined,
                unitId: filterUnitId || undefined
            });
            setBalances(res.content ?? []);
            setTotalElements(res.totalElements ?? 0);
            setTotalPages(res.totalPages ?? 1);
        } catch (err: any) {
            setError(err.message || "Không thể tải dữ liệu tồn kho.");
        } finally {
            setLoadingBalances(false);
        }
    }, [activeStoreId, activeLocationId, page, pageSize, searchQuery, filterProductName, filterCategoryId, filterUnitId]);

    useEffect(() => {
        loadBalances();
    }, [loadBalances]);

    // Reset page to 1 when filters change
    useEffect(() => {
        setPage(1);
    }, [searchQuery, filterProductName, filterCategoryId, filterUnitId, filterStatus]);

    // 3. Map balances to dynamic row schemas (already aggregated by the backend)
    const joinedRows = useMemo<JoinedRow[]>(() => {
        return (balances as unknown as InventoryBalanceViewResponse[]).map((bal) => {


            const baseRow: JoinedRow = {
                id: String(bal.inventoryBalanceId),
                inventoryItemId: String(bal.inventoryItemId),
                locationId: String(bal.locationId),
                branchId: String(bal.branchId),
                "Ảnh sản phẩm": bal.imageUrl,
                "SKU": bal.sku || bal.inventoryItemId,
                "Tên sản phẩm": bal.variantName ? `${bal.productName} (${bal.variantName})` : bal.productName,
                "Danh mục": bal.categoryName || "N/A",
                "Đơn vị": bal.unitSymbol ? `${bal.unitName} (${bal.unitSymbol})` : (bal.unitName || "N/A"),
                "Tồn thực tế": bal.quantityOnHand,
                "Tồn giữ chỗ": bal.quantityReserved,
                "Có thể bán": bal.availableQuantity,
                "Vị trí": bal.locationName || `Mã vị trí: #${bal.locationId}`,
                "Kệ": bal.locationName || `Mã vị trí: #${bal.locationId}`,
                "Giá nhập": bal.cost_price,
                "Giá bán": bal.selling_price,
                "Ngày cập nhật": bal.updatedAt,
                // "Hết hàng": isOutOfStock,
                // "Gần hết hàng": isLowStock
            };
            return baseRow;
        });
    }, [balances]);

    // 4. Client-side Filtering & Sorting
    const filteredRows = useMemo(() => {
        let result = [...joinedRows];

        if (filterStatus === "LOW_STOCK") {
            result = result.filter(r => r.isLowStock);
        } else if (filterStatus === "OUT_OF_STOCK") {
            result = result.filter(r => r.isOutOfStock);
        }

        // Sorting logic
        if (sortColumn) {
            result.sort((a, b) => {
                const valA = a[sortColumn];
                const valB = b[sortColumn];

                if (typeof valA === "number" && typeof valB === "number") {
                    return sortDirection === "asc" ? valA - valB : valB - valA;
                }

                const strA = String(valA ?? "").toLowerCase();
                const strB = String(valB ?? "").toLowerCase();
                return sortDirection === "asc" ? strA.localeCompare(strB) : strB.localeCompare(strA);
            });
        }

        return result;
    }, [joinedRows, sortColumn, sortDirection, filterStatus]);

    // 5. Hardcoded columns for the grid (omitting "Đơn vị", "Giá bán", and "Giá nhập")
    const schemaColumns = useMemo(() => {
        return ["Kệ","Ảnh sản phẩm", "Tên sản phẩm",  "Có thể bán"];
    }, []);

    // Set initial visibility mapping if empty
    useEffect(() => {
        if (schemaColumns.length > 0 && Object.keys(columnVisibility).length === 0) {
            const initialVisibility: Record<string, boolean> = {};
            schemaColumns.forEach(col => {
                initialVisibility[col] = true;
            });
            setColumnVisibility(initialVisibility);
        }
    }, [schemaColumns, columnVisibility]);

    // 6. Header Resize Handler (pure React implementation)
    const handleResizeMouseDown = (e: React.MouseEvent, colKey: string) => {
        e.preventDefault();
        const startX = e.clientX;
        const startWidth = columnWidths[colKey] || 140;

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const newWidth = Math.max(60, startWidth + (moveEvent.clientX - startX));
            setColumnWidths(prev => ({ ...prev, [colKey]: newWidth }));
        };

        const handleMouseUp = () => {
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        };

        document.addEventListener("mousemove", handleMouseMove);
        document.addEventListener("mouseup", handleMouseUp);
    };

    // Toggle sorting
    const handleSort = (col: string) => {
        if (sortColumn === col) {
            setSortDirection(prev => (prev === "asc" ? "desc" : "asc"));
        } else {
            setSortColumn(col);
            setSortDirection("asc");
        }
    };



    if (loadingStore) {
        return (
            <div className="grid h-[500px] place-items-center">
                <InventoryLoading />
            </div>
        );
    }

    return (
        <section className="grid gap-5">            
            <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div className="grid gap-0.5 min-w-[200px] shrink-0">
                    <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                        Tồn kho tại: {activeLocationName}
                    </h1>
                    <p className="text-sm text-gray-500">
                        Theo dõi lượng tồn kho chi tiết.
                    </p>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-auto">
                    <button
                        onClick={() => setActiveModal("stock-in")}
                        className="flex items-center justify-center gap-[9px] h-12 rounded-lg bg-[#07883a] text-white px-[22px] font-black shadow-[0_10px_24px_rgba(7,136,58,0.24)] hover:opacity-90 transition-all cursor-pointer"
                    >
                        Nhập hàng
                    </button>
                    <button
                        onClick={() => setActiveModal("adjustment")}
                        className="flex items-center justify-center gap-[9px] h-12 rounded-lg border border-[#dfe6ee] bg-white text-[#172317] px-[18px] font-black hover:bg-neutral-50 transition-all cursor-pointer"
                    >
                        Điều chỉnh kho
                    </button>
                    <button
                        onClick={() => setActiveModal("transfer")}
                        className="flex items-center justify-center gap-[9px] h-12 rounded-lg border border-[#dfe6ee] bg-white text-[#172317] px-[18px] font-black hover:bg-neutral-50 transition-all cursor-pointer"
                    >
                        <ArrowLeftRight size={16} />
                        Chuyển vị trí
                    </button>
                </div>
            </header>

            {/* Config & Filters Row */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                <div className="flex flex-col md:flex-row gap-3 justify-between items-stretch md:items-center">
                    {/* Search SKU & Tên sản phẩm */}
                    <div className="relative flex-1 min-w-[240px]">
                        <SearchBox
                            value={searchQuery}
                            placeholder="Tìm theo SKU, tên sản phẩm..."
                            onChange={setSearchQuery}
                            className="w-full"
                        />
                    </div>
                    
                    <div className="flex flex-wrap gap-3 items-center">
                        {/* Dropdown 1: Kệ hàng */}
                        <SearchableDropdown
                            value={activeLocationId ?? ""}
                            onChange={(val: string) => {
                                setActiveLocationId(val);
                                setPage(1);
                            }}
                            options={shelfOptions}
                            onSearch={setShelfSearchTerm}
                            onLoadMore={handleLoadMoreShelves}
                            hasMore={shelfHasMore}
                            loading={shelfLoading}
                            placeholder="Kệ hàng"
                        />
                        {/* Dropdown 2: Đơn vị tính */}
                        <SearchableDropdown
                            value={filterUnitId}
                            onChange={(val: string) => {
                                setFilterUnitId(val);
                                setPage(1);
                            }}
                            options={unitOptions}
                            onSearch={setUnitSearchTerm}
                            onLoadMore={handleLoadMoreUnits}
                            hasMore={unitHasMore}
                            loading={unitLoading}
                            placeholder="Đơn vị tính"
                        />
                        {/* Dropdown 3: Sản phẩm */}
                        <SearchableDropdown
                            value={filterProductId}
                            onChange={(val: string) => {
                                setFilterProductId(val);
                                const selectedName = productOptions.find(o => o.id === val)?.name || "";
                                setFilterProductName(selectedName);
                                setPage(1);
                            }}
                            options={productOptions}
                            onSearch={setProductSearchTerm}
                            onLoadMore={handleLoadMoreProducts}
                            hasMore={productHasMore}
                            loading={productLoading}
                            placeholder="Sản phẩm"
                        />
                        {/* Dropdown 4: Danh mục */}
                        <SearchableDropdown
                            value={filterCategoryId}
                            onChange={(val: string) => {
                                setFilterCategoryId(val);
                                setPage(1);
                            }}
                            options={categoryOptions}
                            onSearch={setCategorySearchTerm}
                            onLoadMore={handleLoadMoreCategories}
                            hasMore={categoryHasMore}
                            loading={categoryLoading}
                            placeholder="Danh mục"
                        />
                    </div>
                </div>

                {/* Status chips (giữ nguyên như cũ) */}
                <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-[#f3f5f7] mt-2">
                    <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                        Trạng thái tồn:
                    </span>
                    <div className="flex flex-wrap gap-2 items-center">
                        {[
                            { value: "", label: "Tất cả" },
                            { value: "LOW_STOCK", label: "Sắp hết hàng" },
                            { value: "OUT_OF_STOCK", label: "Hết hàng" }
                        ].map((opt) => {
                            const isActive = filterStatus === opt.value;
                            return (
                                <button
                                    key={opt.value}
                                    type="button"
                                    onClick={() => { setFilterStatus(opt.value); setPage(1); }}
                                    className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                        isActive
                                            ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                            : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                    }`}
                                >
                                    {opt.label}
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>

            {/* Main Data Grid */}
            <div className="grid gap-4 grid-cols-1">
                <section className="flex flex-col min-h-[580px] min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <div className="flex-1 overflow-x-auto min-h-[280px]">
                        {loadingBalances ? <InventoryLoading /> : null}
                        {!loadingBalances && error ? <InventoryError message={error} onRetry={loadBalances} /> : null}
                        {!loadingBalances && !error && filteredRows.length === 0 ? <InventoryEmpty /> : null}
                        {!loadingBalances && !error && filteredRows.length > 0 ? (
                            <table className="table-layout-fixed w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900 sticky top-0 z-10">
                                    <tr>
                                        {schemaColumns.map(col => {
                                            if (!columnVisibility[col]) return null;
                                            const width = columnWidths[col] || 150;
                                            return (
                                                <th
                                                    key={col}
                                                    style={{ width: `${width}px` }}
                                                    className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] relative group cursor-pointer select-none first:pl-[18px]"
                                                    onClick={() => handleSort(col)}
                                                >
                                                    <div className="flex items-center gap-1.5">
                                                        <span>{col}</span>
                                                        <ArrowUpDown size={12} className="text-gray-400 opacity-50 group-hover:opacity-100" />
                                                    </div>
                                                    <div
                                                        onMouseDown={(e) => {
                                                            e.stopPropagation();
                                                            handleResizeMouseDown(e, col);
                                                        }}
                                                        className="absolute top-0 right-0 h-full w-1.5 cursor-col-resize hover:bg-hvt-green-700/50 bg-transparent transition-colors"
                                                    />
                                                </th>
                                            );
                                        })}
                                        <th className="h-[52px] align-middle px-4 py-0 text-right border-b border-[#edf0f4] sticky right-0 bg-[#eaf7ec] pr-[18px]" style={{ width: "100px" }}>
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredRows.map((row, idx) => {
                                        const isSelected = selectedRow?.id === row.id;
                                        const isEven = idx % 2 === 1;
                                        const rowBgClass = isSelected
                                            ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                                            : isEven
                                                ? "bg-[#f2faf1]/50"
                                                : "bg-white";

                                        const stickyBgClass = isSelected
                                            ? "bg-[#f0fbef]"
                                            : isEven
                                                ? "bg-[#f7fcf6]"
                                                : "bg-white";

                                        return (
                                            <tr
                                                key={row.id}
                                                onClick={() => setSelectedRow(row)}
                                                className={`hover:bg-neutral-50 cursor-pointer transition-all ${rowBgClass}`}
                                            >
                                                {schemaColumns.map(col => {
                                                    if (!columnVisibility[col]) return null;
                                                    const val = row[col];
                                                    return (
                                                        <td key={col} className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] max-w-[300px] truncate first:pl-[18px]">
                                                            {col === "Ảnh sản phẩm" ? (
                                                                val ? (
                                                                    <img src={val} alt="" className="size-10 rounded-lg object-cover border border-pos-border" />
                                                                ) : (
                                                                    <span className="grid size-10 place-items-center rounded-lg bg-neutral-100 text-gray-400">
                                                                        -
                                                                    </span>
                                                                )
                                                            ) : col === "Ngày cập nhật" ? (
                                                                formatInventoryDate(val)
                                                            ) : typeof val === "number" ? (
                                                                formatQuantity(val)
                                                            ) : (
                                                                String(val ?? "-")
                                                            )}
                                                        </td>
                                                    );
                                                })}
                                                <td className={`h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right sticky right-0 pr-[18px] ${activeActionMenuId === row.id ? "z-30" : "z-20"} ${stickyBgClass}`} onClick={(e) => e.stopPropagation()}>
                                                    <div className="flex justify-end relative">
                                                        <button
                                                            type="button"
                                                            onClick={() => setActiveActionMenuId(activeActionMenuId === row.id ? null : row.id)}
                                                            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 text-gray-500 hover:text-gray-700 cursor-pointer"
                                                        >
                                                            <MoreHorizontal size={16} />
                                                        </button>

                                                        {activeActionMenuId === row.id && (
                                                            <>
                                                                <div className="fixed inset-0 z-30" onClick={() => setActiveActionMenuId(null)} />
                                                                <div className={`absolute right-0 ${idx >= 2 && idx >= filteredRows.length - 2 ? "bottom-8 mb-1" : "top-8 mt-1"} w-44 rounded-xl border border-pos-border bg-white p-1.5 shadow-lg z-40 text-left`}>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => { setSelectedRow(row); setActiveActionMenuId(null); }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <Eye size={14} />
                                                                        Xem chi tiết
                                                                    </button>
                                                                    <Link
                                                                        to={`${routePaths.inventoryTransactions}?inventoryItemId=${row.inventoryItemId}`}
                                                                        onClick={() => setActiveActionMenuId(null)}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <History size={14} />
                                                                        Lịch sử biến động
                                                                    </Link>
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        ) : null}
                    </div>
                    {!loadingBalances && !error ? (
                        <Pagination
                            page={page}
                            pageCount={totalPages}
                            totalItems={totalElements}
                            pageSize={pageSize}
                            onPageChange={setPage}
                            onPageSizeChange={(value) => {
                                setPageSize(value);
                                setPage(1);
                            }}
                        />
                    ) : null}
                </section>

                {/* Dynamic Details Panel */}
                {selectedRow ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-[#152a1c]/45 p-6 backdrop-blur-[2px] transition-opacity duration-300"
                        onClick={() => setSelectedRow(null)}
                    >
                        <section
                            aria-label="Chi tiết tồn kho"
                            aria-modal="true"
                            className="relative flex max-h-[85vh] w-[min(92vw,960px)] flex-col overflow-hidden rounded-2xl border border-[#dfe8e3] bg-white shadow-[0_28px_90px_rgba(21,43,30,0.24)] max-md:h-[92vh] max-md:w-[calc(100vw-24px)]"
                            role="dialog"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <header className="sticky top-0 z-20 flex min-h-14 shrink-0 items-center justify-between gap-3 border-b border-[#e2e9e5] bg-white px-4 py-2.5">
                                <h2 className="truncate text-base font-black text-[#172317]">
                                    Chi tiết tồn kho
                                </h2>
                                <button
                                    aria-label="Đóng"
                                    className="grid size-9 place-items-center rounded-lg text-[#657282] hover:bg-[#eef3f0] hover:text-[#172317] cursor-pointer"
                                    onClick={() => setSelectedRow(null)}
                                    type="button"
                                >
                                    <X size={20} aria-hidden="true" />
                                </button>
                            </header>

                            <main className="grid min-h-0 flex-1 grid-cols-[280px_minmax(0,1fr)] max-md:block max-md:overflow-y-auto">
                                <aside className="min-h-0 border-r border-[#e2e9e5] bg-white max-md:border-b max-md:border-r-0 overflow-y-auto px-6 py-6 flex flex-col gap-5">
                                    <div className="flex items-start gap-4 border-b border-[#eef2f0] pb-6">
                                        {selectedRow["Ảnh sản phẩm"] ? (
                                            <img
                                                src={selectedRow["Ảnh sản phẩm"]}
                                                alt=""
                                                className="size-16 shrink-0 rounded-xl object-cover border border-pos-border ring-1 ring-[#c9ead2]"
                                            />
                                        ) : (
                                            <span className="grid size-16 shrink-0 place-items-center rounded-full bg-[#eaf8ee] text-2xl font-black text-[#116f28] ring-1 ring-[#c9ead2]">
                                                {(selectedRow["Tên mặt hàng"] || selectedRow["Tên sản phẩm"] || "T").charAt(0).toUpperCase()}
                                            </span>
                                        )}
                                        <div className="min-w-0 flex-1">
                                            <strong className="block truncate text-lg font-black text-[#172317]">
                                                {selectedRow["Tên mặt hàng"] || selectedRow["Tên sản phẩm"]}
                                            </strong>
                                            <span className="mt-1 block text-xs font-bold text-[#718078]">
                                                Mặt hàng tồn kho
                                            </span>
                                        </div>
                                    </div>
                                    <div className="grid gap-4">
                                        {selectedRow["SKU"] && (
                                            <div className="grid gap-1">
                                                <span className="text-[11px] font-bold uppercase tracking-wider text-gray-400">SKU</span>
                                                <span className="text-sm font-semibold text-[#172317]">{selectedRow["SKU"]}</span>
                                            </div>
                                        )}
                                        {selectedRow["Đơn vị"] && (
                                            <div className="grid gap-1">
                                                <span className="text-[11px] font-bold uppercase tracking-wider text-gray-400">Đơn vị</span>
                                                <span className="text-sm font-semibold text-[#172317]">{selectedRow["Đơn vị"]}</span>
                                            </div>
                                        )}
                                    </div>
                                </aside>

                                <section className="flex min-h-0 min-w-0 flex-col overflow-y-auto p-6 bg-slate-50/50">
                                    <dl className="grid gap-3 text-sm">
                                        {/* Dynamically render all schema keys */}
                                        {Object.entries(selectedRow)
                                            .filter(([key]) => !["id", "inventoryItemId", "locationId", "branchId", "Ảnh sản phẩm", "Tồn thực tế", "Tồn giữ chỗ", "Vị trí", "Tên mặt hàng", "Tên sản phẩm", "SKU", "Đơn vị"].includes(key))
                                            .map(([key, val]) => (
                                                <div key={key} className="flex justify-between py-2 border-b border-dashed border-neutral-200 last:border-0">
                                                    <dt className="text-gray-500 font-bold uppercase text-xs">{key}</dt>
                                                    <dd className="text-gray-900 font-semibold max-w-[240px] truncate text-right">
                                                        {key === "Ngày cập nhật"
                                                            ? formatInventoryDate(val)
                                                            : (key === "Giá nhập" || key === "Giá bán")
                                                            ? (typeof val === "number" ? `${formatQuantity(val)} đ` : String(val ?? "-"))
                                                            : typeof val === "number"
                                                            ? formatQuantity(val)
                                                            : String(val ?? "-")}
                                                    </dd>
                                                </div>
                                            ))}
                                    </dl>
                                </section>
                            </main>

                            <footer className="sticky bottom-0 z-20 flex min-h-16 shrink-0 items-center gap-3 border-t border-[#e2e9e5] bg-white px-4 py-3">
                                <div className="ml-auto flex shrink-0 gap-2">
                                    <button
                                        className="inline-flex h-10 shrink-0 items-center justify-center rounded-lg border border-[#dce5e1] bg-white px-5 text-sm font-black text-[#536171] hover:bg-[#f4f8f5] cursor-pointer"
                                        onClick={() => setSelectedRow(null)}
                                        type="button"
                                    >
                                        Đóng
                                    </button>
                                </div>
                            </footer>
                        </section>
                    </div>
                ) : null}
            </div>

            {activeModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-0 md:p-6">
                    <div className="flex h-full w-full flex-col bg-white shadow-2xl md:h-[90vh] md:max-h-[850px] md:max-w-[950px] md:rounded-3xl overflow-hidden">
                        {/* Header with Close X */}
                        <header className="flex items-center justify-between border-b border-neutral-100 px-6 py-4">
                            <h2 className="text-lg font-bold text-gray-800">
                                {activeModal === "stock-in" ? "Nhập hàng từ kho tổng" : "Điều chỉnh tồn kho"}
                            </h2>
                            <button
                                onClick={() => setActiveModal(null)}
                                className="rounded-xl p-2 hover:bg-neutral-100 text-neutral-400 hover:text-neutral-600"
                            >
                                <X size={20} />
                            </button>
                        </header>
                        
                        {/* Content Body */}
                        <div className="flex-1 overflow-y-auto p-6">
                            {activeModal === "transfer" ? (
                                <TransferDialog
                                    onSuccess={() => { loadBalances(); setActiveModal(null); }}
                                />
                            ) : (
                                <StoreInventoryForm
                                    mode={activeModal as "stock-in" | "adjustment"}
                                    onSuccess={() => loadBalances()}
                                    onSubmit={async (input) => {
                                        if (activeModal === "stock-in") {
                                            return inventoryTransactionApi.storeStockIn({
                                                shelfId: input.shelfId,
                                                referenceType: input.referenceType,
                                                referenceId: input.referenceId,
                                                lines: input.lines.map((line) => ({
                                                    inventoryItemId: line.item.id,
                                                    quantity: Number(line.quantity)
                                                }))
                                            });
                                        } else {
                                            return inventoryTransactionApi.storeAdjust({
                                                shelfId: input.shelfId,
                                                referenceType: input.referenceType,
                                                referenceId: input.referenceId,
                                                lines: input.lines.map((line) => ({
                                                    inventoryItemId: line.item.id,
                                                    actualQuantity: Number(line.quantity)
                                                }))
                                            });
                                        }
                                    }}
                                />
                            )}
                        </div>
                    </div>
                </div>
            )}

        </section>
    );
}

// ─── Transfer Dialog Component ────────────────────────────────────────────────
interface TransferDialogProps { onSuccess(): void; }

function TransferDialog({ onSuccess }: TransferDialogProps) {
    const [shelves, setShelves] = useState<InventoryLocationResponse[]>([]);
    const [fromShelfId, setFromShelfId] = useState("");
    const [toShelfId, setToShelfId] = useState("");

    // Items at source shelf
    interface ShelfBalance { inventoryBalanceId: string; inventoryItemId: string; productName: string; sku: string; quantityOnHand: number; locationId: string; }
    const [fromBalances, setFromBalances] = useState<ShelfBalance[]>([]);
    const [loadingBalances, setLoadingBalances] = useState(false);

    const [inventoryItemId, setInventoryItemId] = useState("");
    const [quantity, setQuantity] = useState("");
    const [note, setNote] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    // Load shelves on mount
    useEffect(() => {
        storeShelfApi.list({ page: 0, size: 200, status: "ACTIVE" }).then(res => {
            setShelves(res.content ?? []);
        }).catch(console.error);
    }, []);

    // Auto-load balances when fromShelf changes
    useEffect(() => {
        if (!fromShelfId) { setFromBalances([]); setInventoryItemId(""); return; }
        setLoadingBalances(true);
        inventoryBalanceApi.view({ locationId: fromShelfId as any, page: 0, size: 500 })
            .then(res => {
                const items = (res.content ?? []) as InventoryBalanceViewResponse[];
                // Only show items with quantityOnHand > 0
                setFromBalances(items
                    .filter(b => b.quantityOnHand > 0)
                    .map(b => ({
                        inventoryBalanceId: String(b.inventoryBalanceId),
                        inventoryItemId: String(b.inventoryItemId),
                        productName: b.variantName ? `${b.productName} (${b.variantName})` : b.productName,
                        sku: b.sku || "",
                        quantityOnHand: b.quantityOnHand,
                        locationId: String(b.locationId)
                    }))
                );
                setInventoryItemId("");
            })
            .catch(console.error)
            .finally(() => setLoadingBalances(false));
    }, [fromShelfId]);

    const selectedBalance = fromBalances.find(b => b.inventoryItemId === inventoryItemId);
    const maxQty = selectedBalance?.quantityOnHand ?? 0;

    const handleSubmit = async () => {
        if (!fromShelfId || !toShelfId || !inventoryItemId || !quantity) {
            setError("Vui lòng điền đầy đủ thông tin."); return;
        }
        if (fromShelfId === toShelfId) {
            setError("Kệ nguồn và kệ đích phải khác nhau."); return;
        }
        const qty = Number(quantity);
        if (isNaN(qty) || qty <= 0) {
            setError("Số lượng phải lớn hơn 0."); return;
        }
        if (qty > maxQty) {
            setError(`Số lượng vượt quá tồn kho tại kệ nguồn (${maxQty}).`); return;
        }

        const fromName = shelves.find(s => String(s.id) === fromShelfId)?.name;
        const toName = shelves.find(s => String(s.id) === toShelfId)?.name;
        if (!window.confirm(`Xác nhận chuyển ${qty} sản phẩm từ kệ "${fromName}" sang kệ "${toName}"?`)) return;

        setSubmitting(true); setError(""); setSuccess("");
        try {
            const res = await inventoryTransactionApi.storeTransfer({
                fromShelfId,
                toShelfId,
                inventoryItemId,
                quantity: qty,
                note: note || undefined
            });
            setSuccess(`Chuyển vị trí thành công! Mã giao dịch: ${res.transactionCode}`);
            setFromShelfId(""); setToShelfId(""); setInventoryItemId(""); setQuantity(""); setNote("");
            onSuccess();
        } catch (err: any) {
            setError(err.message || "Chuyển vị trí thất bại.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <section className="grid gap-5 rounded-2xl bg-white p-5 shadow-pos-card">
            <h2 className="font-bold text-gray-800 text-base">Chuyển hàng giữa hai kệ</h2>

            <div className="grid gap-4 sm:grid-cols-2">
                {/* Kệ nguồn */}
                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-bold text-gray-700">Kệ nguồn <span className="text-red-500">*</span></label>
                    <select
                        value={fromShelfId}
                        onChange={e => { setFromShelfId(e.target.value); setError(""); }}
                        className="h-10 rounded-xl border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700"
                    >
                        <option value="">-- Chọn kệ nguồn --</option>
                        {shelves.map(s => <option key={s.id} value={String(s.id)}>{s.name}</option>)}
                    </select>
                </div>

                {/* Kệ đích */}
                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-bold text-gray-700">Kệ đích <span className="text-red-500">*</span></label>
                    <select
                        value={toShelfId}
                        onChange={e => { setToShelfId(e.target.value); setError(""); }}
                        className="h-10 rounded-xl border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700"
                    >
                        <option value="">-- Chọn kệ đích --</option>
                        {shelves.filter(s => String(s.id) !== fromShelfId).map(s => (
                            <option key={s.id} value={String(s.id)}>{s.name}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Hàng hóa từ kệ nguồn */}
            <div className="flex flex-col gap-1.5">
                <label className="text-sm font-bold text-gray-700">
                    Hàng hóa tại kệ nguồn <span className="text-red-500">*</span>
                    {loadingBalances && <span className="ml-2 text-gray-400 font-normal text-xs">Đang tải...</span>}
                </label>
                <select
                    value={inventoryItemId}
                    onChange={e => { setInventoryItemId(e.target.value); setQuantity(""); setError(""); }}
                    disabled={!fromShelfId || loadingBalances}
                    className="h-10 rounded-xl border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700 disabled:opacity-50"
                >
                    <option value="">
                        {!fromShelfId ? "Chọn kệ nguồn trước" : fromBalances.length === 0 ? "Không có hàng trên kệ này" : "-- Chọn hàng hóa --"}
                    </option>
                    {fromBalances.map(b => (
                        <option key={b.inventoryItemId} value={b.inventoryItemId}>
                            {b.sku ? `${b.sku} - ` : ""}{b.productName} (tồn: {b.quantityOnHand})
                        </option>
                    ))}
                </select>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
                {/* Số lượng */}
                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-bold text-gray-700">
                        Số lượng chuyển <span className="text-red-500">*</span>
                        {selectedBalance && <span className="ml-2 text-xs font-normal text-gray-500">(tối đa: {maxQty})</span>}
                    </label>
                    <input
                        type="number"
                        min={1}
                        max={maxQty || undefined}
                        value={quantity}
                        onChange={e => { setQuantity(e.target.value); setError(""); }}
                        disabled={!inventoryItemId}
                        placeholder="Nhập số lượng..."
                        className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 disabled:opacity-50"
                    />
                </div>

                {/* Ghi chú */}
                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-bold text-gray-700">Ghi chú</label>
                    <input
                        type="text"
                        value={note}
                        onChange={e => setNote(e.target.value)}
                        placeholder="Ghi chú tùy chọn..."
                        className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700"
                    />
                </div>
            </div>

            {error && <p className="rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{error}</p>}
            {success && <p className="rounded-xl bg-emerald-50 p-3 text-sm font-bold text-emerald-700">{success}</p>}

            <div className="flex justify-end">
                <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={submitting}
                    className="flex items-center justify-center gap-2 h-11 rounded-xl bg-[#07883a] text-white px-6 font-black shadow-[0_4px_14px_rgba(7,136,58,0.28)] hover:opacity-90 transition-all disabled:opacity-60 cursor-pointer"
                >
                    <ArrowLeftRight size={16} />
                    {submitting ? "Đang xử lý..." : "Xác nhận chuyển vị trí"}
                </button>
            </div>
        </section>
    );
}
