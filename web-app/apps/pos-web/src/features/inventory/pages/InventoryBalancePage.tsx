import { Eye, History, ArrowUpDown, X, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";
import { StoreInventoryForm } from "../components/StoreInventoryForm";
import { routePaths } from "../../../routes/path";
import { formatInventoryDate, formatQuantity } from "../utils/inventory-labels";
import type { InventoryBalanceViewResponse, InventoryLocationResponse } from "../types/inventory.types";
import type { Category, Unit } from "../../product/types/product.types";
import { categoryApi } from "../../product/services/category.api";
import { unitApi } from "../../product/services/unit.api";

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
    "Giá nhập"?: number;
    "Giá bán"?: number;
    "Ngày cập nhật": string;
    [key: string]: any; // Allow future custom fields
}

export function InventoryBalancePage() {

    // Store & Catalog states
    const [activeStoreId, setActiveStoreId] = useState<string | null>(null);
    const [activeLocationId, setActiveLocationId] = useState<string | null>("");
    const [defaultWarehouseId, setDefaultWarehouseId] = useState<string | null>(null);
    const [loadingStore, setLoadingStore] = useState(true);

    const [locations, setLocations] = useState<InventoryLocationResponse[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [units, setUnits] = useState<Unit[]>([]);

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
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const [sortColumn, setSortColumn] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

    // UI Configuration states
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});
    const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>({});

    // Drawer / Panel state
    const [selectedRow, setSelectedRow] = useState<JoinedRow | null>(null);
    const [activeModal, setActiveModal] = useState<"stock-in" | "adjustment" | null>(null);

    // Dashboard Metrics state (reserved for future KPI display)
    const [_dashboardMetrics, setDashboardMetrics] = useState({
        totalSku: 0,
        totalStock: 0,
        lowStock: 0,
        outOfStock: 0,
        totalValue: 0
    });

    // 1. Resolve Active Store and Load Locations
    useEffect(() => {
        async function initCatalogAndStore() {
            try {
                // Fetch locations list
                const locRes = await inventoryLocationApi.list({ page: 0, size: 500 });
                const locs = locRes.content ?? [];
                setLocations(locs);

                // Fetch categories list
                const catRes = await categoryApi.list({ page: 0, size: 500 });
                setCategories(catRes.content ?? []);

                // Fetch units list
                const unitRes = await unitApi.list({ page: 0, size: 500 });
                setUnits(unitRes.content ?? []);

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

                // Fetch default warehouse from backend
                try {
                    const defaultWarehouse = await inventoryLocationApi.defaultWarehouse(resolvedBranchId);
                    if (defaultWarehouse) {
                        setDefaultWarehouseId(String(defaultWarehouse.id));
                    } else {
                        // Fallback: search locally
                        const warehouseLoc = locs.find(l => l.type === "WAREHOUSE" && String(l.branchId) === resolvedBranchId);
                        if (warehouseLoc) {
                            setDefaultWarehouseId(String(warehouseLoc.id));
                        }
                    }
                } catch (err) {
                    console.error("Failed to fetch default warehouse from backend, falling back to client-side resolution", err);
                    const warehouseLoc = locs.find(l => l.type === "WAREHOUSE" && String(l.branchId) === resolvedBranchId);
                    if (warehouseLoc) {
                        setDefaultWarehouseId(String(warehouseLoc.id));
                    }
                }
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
                sku: searchQuery.trim() || undefined,
                productName: searchQuery.trim() || undefined,
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
    }, [activeStoreId, activeLocationId, page, pageSize, searchQuery, filterCategoryId, filterUnitId]);

    useEffect(() => {
        loadBalances();
    }, [loadBalances]);

    // Fetch dashboard metrics over the entire warehouse location
    useEffect(() => {
        async function fetchDashboardMetrics() {
            if (!activeStoreId) return;
            try {
                const res = await inventoryBalanceApi.view({
                    page: 0,
                    size: 1000,
                    branchId: activeStoreId as any,
                    locationId: activeLocationId ? (activeLocationId as any) : undefined
                });
                const items = res.content ?? [];
                
                const totalSku = items.length;
                let totalStock = 0;
                let lowStock = 0;
                let outOfStock = 0;
                let totalValue = 0;
                
                items.forEach(item => {
                    const qoh = item.quantityOnHand || 0;
                    const avail = item.availableQuantity || 0;
                    const rop = item.reorderPoint !== undefined ? item.reorderPoint : 10;
                    const cost = item.cost_price || 0;
                    
                    totalStock += qoh;
                    totalValue += qoh * cost;
                    
                    if (avail <= 0) {
                        outOfStock += 1;
                    } else if (avail <= rop) {
                        lowStock += 1;
                    }
                });
                
                setDashboardMetrics({
                    totalSku,
                    totalStock,
                    lowStock,
                    outOfStock,
                    totalValue
                });
            } catch (err) {
                console.error("Error fetching dashboard metrics:", err);
            }
        }
        fetchDashboardMetrics();
    }, [activeStoreId, activeLocationId, balances]);

    // Reset page to 1 when filters change
    useEffect(() => {
        setPage(1);
    }, [searchQuery, filterCategoryId, filterUnitId, filterStatus]);

    // 3. Map balances to dynamic row schemas (already aggregated by the backend)
    const joinedRows = useMemo<JoinedRow[]>(() => {
        return (balances as unknown as InventoryBalanceViewResponse[]).map((bal) => {
            const rop = bal.reorderPoint !== undefined ? bal.reorderPoint : 10;
            const isOutOfStock = bal.availableQuantity <= 0;
            const isLowStock = bal.availableQuantity > 0 && bal.availableQuantity <= rop;

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
                "Giá nhập": bal.cost_price,
                "Giá bán": bal.selling_price,
                "Ngày cập nhật": bal.updatedAt,
                isOutOfStock,
                isLowStock
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
        return ["Ảnh sản phẩm", "SKU", "Tên sản phẩm", "Danh mục", "Có thể bán", "Ngày cập nhật"];
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
                </div>
            </header>

            {/* Config & Filters Row */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                {/* Search Bar at the top of the card */}
                <div className="border-b border-[#f3f5f7] pb-3">
                    <div className="relative w-full">
                        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                        <input
                            type="text"
                            placeholder="Tìm theo SKU hoặc tên sản phẩm..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border pl-9 pr-3 text-sm outline-none focus:border-hvt-green-700 bg-white"
                        />
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-x-8 gap-y-[10px] pt-3">
                    {/* Categories filter chips */}
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                            Danh mục:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => setFilterCategoryId("")}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    filterCategoryId === ""
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {categories.map((c) => {
                                const isActive = filterCategoryId === String(c.id);
                                return (
                                    <button
                                        key={c.id}
                                        type="button"
                                        onClick={() => setFilterCategoryId(String(c.id))}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {c.name}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Units filter chips */}
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                            Đơn vị tính:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => setFilterUnitId("")}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    filterUnitId === ""
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {units.map((u) => {
                                const isActive = filterUnitId === String(u.id);
                                return (
                                    <button
                                        key={u.id}
                                        type="button"
                                        onClick={() => setFilterUnitId(String(u.id))}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {u.name}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Status filter chips */}
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                            Trạng thái tồn kho:
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
                                        onClick={() => setFilterStatus(opt.value)}
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
            </div>

            {/* Main Data Grid */}
            <div className="grid gap-4 grid-cols-1">
                <section className="flex flex-col min-h-[580px] min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <div className="flex-1 overflow-x-auto">
                        {loadingBalances ? <InventoryLoading /> : null}
                        {!loadingBalances && error ? <InventoryError message={error} onRetry={loadBalances} /> : null}
                        {!loadingBalances && !error && filteredRows.length === 0 ? <InventoryEmpty /> : null}
                        {!loadingBalances && !error && filteredRows.length > 0 ? (
                            <table className="table-layout-fixed w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-white text-[13px] font-[950] text-[#172317] sticky top-0 z-10">
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
                                        <th className="h-[52px] align-middle px-4 py-0 text-right border-b border-[#edf0f4] sticky right-0 bg-white pr-[18px]" style={{ width: "100px" }}>
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredRows.map((row) => (
                                        <tr
                                            key={row.id}
                                            onClick={() => setSelectedRow(row)}
                                            className={`hover:bg-neutral-50 cursor-pointer transition-all ${selectedRow?.id === row.id ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]" : ""}`}
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
                                            <td className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right sticky right-0 bg-white pr-[18px]" onClick={(e) => e.stopPropagation()}>
                                                <div className="flex justify-end gap-1">
                                                    <button
                                                        type="button"
                                                        onClick={() => setSelectedRow(row)}
                                                        title="Xem chi tiết"
                                                        className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"
                                                    >
                                                        <Eye size={16} />
                                                    </button>
                                                    <Link
                                                        to={`${routePaths.inventoryTransactions}?inventoryItemId=${row.inventoryItemId}`}
                                                        title="Xem lịch sử biến động"
                                                        className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"
                                                    >
                                                        <History size={16} />
                                                    </Link>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
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
                        className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-5"
                        onClick={() => setSelectedRow(null)}
                        role="presentation"
                    >
                        <div
                            aria-modal="true"
                            className="w-full max-w-xl max-h-[85vh] overflow-y-auto"
                            onClick={(event) => event.stopPropagation()}
                            role="dialog"
                        >
                            <DetailPanel title="Thông tin chi tiết" onClose={() => setSelectedRow(null)}>
                                {selectedRow["Ảnh sản phẩm"] ? (
                                    <img
                                        src={selectedRow["Ảnh sản phẩm"]}
                                        alt=""
                                        className="h-40 w-full rounded-2xl object-cover mb-4 border border-pos-border"
                                    />
                                ) : null}

                                <dl className="grid gap-3 border-b border-pos-border pb-4 mb-4 text-sm">
                                    {/* Dynamically render all schema keys (adapts automatically to future schema additions) */}
                                    {Object.entries(selectedRow)
                                        .filter(([key]) => !["id", "inventoryItemId", "locationId", "branchId", "Ảnh sản phẩm", "Tồn thực tế", "Tồn giữ chỗ", "Vị trí"].includes(key))
                                        .map(([key, val]) => (
                                            <div key={key} className="flex justify-between py-1 border-b border-dashed border-neutral-100 last:border-0">
                                                <dt className="text-gray-500 font-medium">{key}</dt>
                                                <dd className="text-gray-900 font-semibold max-w-[200px] truncate text-right">
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
                            </DetailPanel>
                        </div>
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
                            <StoreInventoryForm
                                mode={activeModal}
                                onSuccess={() => loadBalances()}
                                onSubmit={async (input) => {
                                    if (activeModal === "stock-in") {
                                        return inventoryTransactionApi.storeStockIn({
                                            referenceType: input.referenceType,
                                            referenceId: input.referenceId,
                                            lines: input.lines.map((line) => ({
                                                inventoryItemId: line.item.id,
                                                quantity: Number(line.quantity)
                                            }))
                                        });
                                    } else {
                                        return inventoryTransactionApi.storeAdjust({
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
                        </div>
                    </div>
                </div>
            )}

        </section>
    );
}


