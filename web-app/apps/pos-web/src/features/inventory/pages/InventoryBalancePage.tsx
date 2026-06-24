import { Eye, History, ArrowUpDown } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
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
    const [activeLocationId, setActiveLocationId] = useState<string | null>(null);
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
    const [searchSku, setSearchSku] = useState("");
    const [searchName, setSearchName] = useState("");
    const [filterCategoryId, setFilterCategoryId] = useState("");
    const [filterUnitId, setFilterUnitId] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const [sortColumn, setSortColumn] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

    // UI Configuration states
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});
    const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>({});

    // Drawer / Panel state
    const [selectedRow, setSelectedRow] = useState<JoinedRow | null>(null);


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
                const unitRes = await unitApi.list();
                setUnits(unitRes ?? []);

                // Find active location & branch (First location whose type is WAREHOUSE)
                const warehouseLoc = locs.find(l => l.type === "WAREHOUSE");
                if (warehouseLoc) {
                    setActiveStoreId(String(warehouseLoc.branchId));
                    setActiveLocationId(String(warehouseLoc.id));
                } else if (locs.length > 0) {
                    setActiveStoreId(String(locs[0].branchId));
                    setActiveLocationId(String(locs[0].id));
                } else {
                    // Fallback to check balance branch IDs if any
                    const balCheck = await inventoryBalanceApi.view({ page: 0, size: 10 });
                    if (balCheck.content && balCheck.content.length > 0) {
                        setActiveStoreId(String(balCheck.content[0].branchId));
                        setActiveLocationId(String(balCheck.content[0].locationId));
                    } else {
                        setActiveStoreId(FALLBACK_STORE_ID);
                        setActiveLocationId(null);
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
        if (!activeLocationId) return "";
        return locations.find(l => String(l.id) === activeLocationId)?.name || "";
    }, [activeLocationId, locations]);

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
                sku: searchSku.trim() || undefined,
                productName: searchName.trim() || undefined,
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
    }, [activeStoreId, activeLocationId, page, pageSize, searchSku, searchName, filterCategoryId, filterUnitId]);

    useEffect(() => {
        loadBalances();
    }, [loadBalances]);

    // Reset page to 1 when filters change
    useEffect(() => {
        setPage(1);
    }, [searchSku, searchName, filterCategoryId, filterUnitId]);

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
                "Giá nhập": bal.cost_price,
                "Giá bán": bal.selling_price,
                "Ngày cập nhật": bal.updatedAt
            };
            return baseRow;
        });
    }, [balances]);

    // 4. Client-side Sorting
    const filteredRows = useMemo(() => {
        let result = [...joinedRows];

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
    }, [joinedRows, sortColumn, sortDirection]);

    // Metrics based on active branch
    const metrics = useMemo(() => {
        return {
            items: totalElements,
            onHand: filteredRows.reduce((acc, r) => acc + (r["Tồn thực tế"] || 0), 0),
            reserved: filteredRows.reduce((acc, r) => acc + (r["Tồn giữ chỗ"] || 0), 0),
            low: filteredRows.filter(r => r["Có thể bán"] <= 0).length
        };
    }, [totalElements, filteredRows]);

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
            <Breadcrumbs items={["Tồn kho hiện tại"]} />
            
            <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div className="grid gap-1 min-w-[240px]">
                    <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                        Tồn kho tại: {activeLocationName}
                    </h1>
                    <p className="text-sm text-gray-500">
                        Theo dõi lượng tồn kho chi tiết và cấu hình hiển thị linh hoạt.
                    </p>
                </div>
            </header>

            {/* Metrics Summaries */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Metric label="Tổng mặt hàng" value={formatQuantity(metrics.items)} />
                <Metric label="Mặt hàng sắp hết" value={formatQuantity(metrics.low)} danger={metrics.low > 0} />
            </div>

            {/* Config & Filters Row */}
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white p-4 shadow-pos-card">
                <div className="flex flex-wrap items-center gap-3">
                    <div className="w-52">
                        <label className="block text-xs font-bold text-gray-500 mb-1">Tìm theo SKU</label>
                        <input
                            type="text"
                            placeholder="Nhập SKU..."
                            value={searchSku}
                            onChange={(e) => setSearchSku(e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700"
                        />
                    </div>
                    <div className="w-64">
                        <label className="block text-xs font-bold text-gray-500 mb-1">Tìm theo tên sản phẩm</label>
                        <input
                            type="text"
                            placeholder="Nhập tên sản phẩm..."
                            value={searchName}
                            onChange={(e) => setSearchName(e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700"
                        />
                    </div>
                    <div className="w-52">
                        <label className="block text-xs font-bold text-gray-500 mb-1">Danh mục</label>
                        <select
                            value={filterCategoryId}
                            onChange={(e) => setFilterCategoryId(e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white"
                        >
                            <option value="">Tất cả danh mục</option>
                            {categories.map((c) => (
                                <option key={c.id} value={String(c.id)}>
                                    {c.name}
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="w-52">
                        <label className="block text-xs font-bold text-gray-500 mb-1">Đơn vị tính</label>
                        <select
                            value={filterUnitId}
                            onChange={(e) => setFilterUnitId(e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white"
                        >
                            <option value="">Tất cả đơn vị</option>
                            {units.map((u) => (
                                <option key={u.id} value={String(u.id)}>
                                    {u.name} ({u.symbol})
                                </option>
                            ))}
                        </select>
                    </div>
                </div>
            </div>

            {/* Main Data Grid */}
            <div className={`grid gap-4 ${selectedRow ? "grid-cols-1 lg:grid-cols-[1fr_380px]" : "grid-cols-1"}`}>
                <section className="flex flex-col min-h-[580px] min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <div className="flex-1 overflow-x-auto">
                        {loadingBalances ? <InventoryLoading /> : null}
                        {!loadingBalances && error ? <InventoryError message={error} onRetry={loadBalances} /> : null}
                        {!loadingBalances && !error && filteredRows.length === 0 ? <InventoryEmpty /> : null}
                        {!loadingBalances && !error && filteredRows.length > 0 ? (
                            <table className="table-layout-fixed w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-neutral-50 text-xs text-gray-600 font-bold sticky top-0 z-10">
                                    <tr>
                                        {schemaColumns.map(col => {
                                            if (!columnVisibility[col]) return null;
                                            const width = columnWidths[col] || 150;
                                            return (
                                                <th
                                                    key={col}
                                                    style={{ width: `${width}px` }}
                                                    className="px-4 py-3 relative border-b border-pos-border group cursor-pointer select-none"
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
                                        <th className="px-4 py-3 text-right border-b border-pos-border sticky right-0 bg-neutral-50" style={{ width: "100px" }}>
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredRows.map((row) => (
                                        <tr
                                            key={row.id}
                                            onClick={() => setSelectedRow(row)}
                                            className={`hover:bg-neutral-50 cursor-pointer border-b border-pos-border last:border-0 ${selectedRow?.id === row.id ? "bg-hvt-green-50/20" : ""}`}
                                        >
                                            {schemaColumns.map(col => {
                                                if (!columnVisibility[col]) return null;
                                                const val = row[col];
                                                return (
                                                    <td key={col} className="px-4 py-3.5 border-b border-pos-border max-w-[300px] truncate">
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
                                            <td className="px-4 py-3.5 border-b border-pos-border text-right sticky right-0 bg-white" onClick={(e) => e.stopPropagation()}>
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

                        {/* Actions Pane */}
                        {/* <div className="grid gap-2">
                            <button
                                type="button"
                                onClick={handleRequestTransfer}
                                className="flex items-center justify-center gap-2 w-full h-11 rounded-xl bg-hvt-green-900 font-bold text-white hover:bg-hvt-green-950 transition-colors"
                            >
                                <ArrowRightLeft size={16} />
                                Yêu cầu chuyển kho
                            </button>
                            <button
                                type="button"
                                onClick={() => navigate(`${routePaths.inventoryTransactions}?inventoryItemId=${selectedRow.inventoryItemId}`)}
                                className="flex items-center justify-center gap-2 w-full h-11 rounded-xl border border-pos-border font-bold text-gray-700 hover:bg-neutral-50 transition-colors"
                            >
                                <FileSearch size={16} />
                                Xem lịch sử biến động
                            </button>
                        </div> */}
                    </DetailPanel>
                ) : null}
            </div>



        </section>
    );
}

function Metric({ label, value, danger = false }: { label: string; value: string; danger?: boolean }) {
    return (
        <article className="rounded-2xl bg-white p-5 shadow-pos-card border border-pos-border/40">
            <p className="text-sm font-semibold text-gray-500">{label}</p>
            <strong className={`mt-2 block text-3xl ${danger ? "text-red-600" : "text-gray-900"}`}>{value}</strong>
        </article>
    );
}
