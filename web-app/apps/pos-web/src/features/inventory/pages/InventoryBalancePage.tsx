import { ArrowLeftRight, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { InventoryResourceManagement, TableCell } from "../components/InventoryResourceManagement";
import { InventoryLoading } from "../components/InventoryState";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";
import { storeShelfApi } from "../services/storeShelf.api";
import { StoreInventoryForm } from "../components/StoreInventoryForm";
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
    isOutOfStock: boolean;
    isLowStock: boolean;
    [key: string]: any;
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

    // Drawer / Panel state
    const [activeModal, setActiveModal] = useState<"stock-in" | "adjustment" | "transfer" | null>(null);

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

    // Fetch shelf options with infinite scroll
    const loadShelfOptions = useCallback(async (search: string, pageNum: number) => {
        try {
            setShelfLoading(true);
            const res = await inventoryLocationApi.lookup(search, pageNum, 20);
            setShelfOptions(prev => pageNum === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
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
    const loadCategoryOptions = useCallback(async (search: string, pageNum: number) => {
        try {
            setCategoryLoading(true);
            const res = await categoryApi.lookup(search, pageNum, 20);
            setCategoryOptions(prev => pageNum === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
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
    const loadUnitOptions = useCallback(async (search: string, pageNum: number) => {
        try {
            setUnitLoading(true);
            const res = await unitApi.lookup(search, pageNum, 20);
            setUnitOptions(prev => pageNum === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
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
    const loadProductOptions = useCallback(async (search: string, pageNum: number) => {
        try {
            setProductLoading(true);
            const res = await productApi.lookup(search, pageNum, 20);
            setProductOptions(prev => pageNum === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
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

    const activeLocationName = useMemo(() => {
        if (activeLocationId) {
            const loc = locations.find(l => String(l.id) === activeLocationId);
            if (loc) return loc.name;
            const shelf = shelfOptions.find(s => s.id === activeLocationId);
            if (shelf) return shelf.name;
            return `Vị trí #${activeLocationId}`;
        }
        const warehouseLoc = locations.find(l => l.type === "WAREHOUSE" && String(l.branchId) === activeStoreId);
        return warehouseLoc ? warehouseLoc.name : "Cửa hàng";
    }, [activeLocationId, locations, activeStoreId, shelfOptions]);

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
                unitId: filterUnitId || undefined,
                status: filterStatus || undefined
            });
            setBalances(res.content ?? []);
            setTotalElements(res.totalElements ?? 0);
            setTotalPages(res.totalPages ?? 1);
        } catch (err: any) {
            setError(err.message || "Không thể tải dữ liệu tồn kho.");
        } finally {
            setLoadingBalances(false);
        }
    }, [activeStoreId, activeLocationId, page, pageSize, searchQuery, filterProductName, filterCategoryId, filterUnitId, filterStatus]);

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
            const isOutOfStock = bal.quantityOnHand <= 0;
            const isLowStock = bal.quantityOnHand > 0 && bal.quantityOnHand <= (bal.reorderPoint || 5);

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
                isOutOfStock,
                isLowStock
            };
            return baseRow;
        });
    }, [balances]);

    if (loadingStore) {
        return (
            <div className="grid h-[500px] place-items-center">
                <InventoryLoading />
            </div>
        );
    }

    return (
        <div className="grid gap-5">
            <InventoryResourceManagement
                title={`Tồn kho tại: ${activeLocationName}`}
                description="Theo dõi lượng tồn kho chi tiết."
                items={joinedRows}
                loading={loadingBalances}
                error={error || ""}
                page={page}
                pageCount={totalPages}
                totalItems={totalElements}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={(val) => {
                    setPageSize(val);
                    setPage(1);
                }}
                onRefresh={loadBalances}
                query={searchQuery}
                onQueryChange={(val) => {
                    setSearchQuery(val);
                    setPage(1);
                }}
                searchPlaceholder="Tìm theo SKU, tên sản phẩm..."
                status={filterStatus}
                onStatusChange={(val) => {
                    setFilterStatus(val);
                    setPage(1);
                }}
                statusOptions={[
                    { label: "Tất cả", value: "ALL" as any },
                    { label: "Sắp hết hàng", value: "LOW_STOCK" },
                    { label: "Hết hàng", value: "OUT_OF_STOCK" }
                ]}
                totalItemsLabel="Tổng số mặt hàng tồn"
                columns={[
                    { key: "Kệ", label: "Kệ" },
                    { key: "Ảnh sản phẩm", label: "Ảnh sản phẩm" },
                    { key: "Tên sản phẩm", label: "Tên sản phẩm" },
                    { key: "Có thể bán", label: "Có thể bán", align: "right" }
                ]}
                renderCells={(item) => (
                    <>
                        <TableCell>{item["Kệ"]}</TableCell>
                        <TableCell>
                            {item["Ảnh sản phẩm"] ? (
                                <img src={item["Ảnh sản phẩm"]} alt="" className="size-10 rounded-lg object-cover border border-pos-border" />
                            ) : (
                                <span className="grid size-10 place-items-center rounded-lg bg-neutral-100 text-gray-400">
                                    -
                                </span>
                            )}
                        </TableCell>
                        <TableCell strong>{item["Tên sản phẩm"]}</TableCell>
                        <TableCell className="text-right pr-[18px]">{formatQuantity(item["Có thể bán"])}</TableCell>
                    </>
                )}
                renderDetail={(item) => (
                    <dl className="grid gap-3 text-sm">
                        {Object.entries(item)
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
                )}
                extraHeaderActions={
                    <div className="flex items-center gap-3">
                        <ActionButton
                            variant="primary"
                            onClick={() => setActiveModal("stock-in")}
                        >
                            Nhập hàng
                        </ActionButton>
                        <ActionButton
                            variant="secondary"
                            onClick={() => setActiveModal("adjustment")}
                        >
                            Điều chỉnh kho
                        </ActionButton>
                        <ActionButton
                            variant="secondary"
                            onClick={() => setActiveModal("transfer")}
                        >
                            <ArrowLeftRight size={16} />
                            Chuyển vị trí
                        </ActionButton>
                    </div>
                }
                filters={[
                    {
                        label: "Kệ hàng",
                        value: activeLocationId ?? "",
                        options: [],
                        onChange: () => {},
                        render: (
                            <SearchableDropdown
                                value={activeLocationId ?? ""}
                                onChange={(val) => {
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
                        )
                    },
                    {
                        label: "Đơn vị tính",
                        value: filterUnitId,
                        options: [],
                        onChange: () => {},
                        render: (
                            <SearchableDropdown
                                value={filterUnitId}
                                onChange={(val) => {
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
                        )
                    },
                    {
                        label: "Sản phẩm",
                        value: filterProductId,
                        options: [],
                        onChange: () => {},
                        render: (
                            <SearchableDropdown
                                value={filterProductId}
                                onChange={(val) => {
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
                        )
                    },
                    {
                        label: "Danh mục",
                        value: filterCategoryId,
                        options: [],
                        onChange: () => {},
                        render: (
                            <SearchableDropdown
                                value={filterCategoryId}
                                onChange={(val) => {
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
                        )
                    }
                ]}
            />

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
        </div>
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
