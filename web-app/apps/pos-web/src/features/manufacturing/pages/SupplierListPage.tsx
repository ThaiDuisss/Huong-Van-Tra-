import {
    Building2,
    Eye,
    Pencil,
    Plus,
    Search,
    Trash2
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { CatalogStatusBadge } from "../../product/components/CatalogStatusBadge";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { ingredientApi } from "../../product/services/ingredient.api";
import type { Ingredient } from "../../product/types/product.types";
import { Pagination } from "../components/shared/Pagination";
import { SupplierDetailDrawer } from "../components/SupplierDetailDrawer";
import { SupplierFormModal } from "../components/SupplierFormModal";
import { supplierApi } from "../services/supplier.api";
import type {
    SupplierRequest,
    SupplierResponse
} from "../types/supplier.types";

export function SupplierListPage() {
    const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [ingredientFilter, setIngredientFilter] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Ingredient options for filter
    const [allIngredients, setAllIngredients] = useState<Ingredient[]>([]);

    // Modal & Drawer states
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingSupplier, setEditingSupplier] = useState<SupplierResponse | null>(null);
    const [viewingSupplier, setViewingSupplier] = useState<SupplierResponse | null>(null);
    const [deletingSupplier, setDeletingSupplier] = useState<SupplierResponse | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    // Fetch suppliers
    const loadSuppliers = useCallback(async () => {
        try {
            setLoading(true);
            setError("");
            const res = await supplierApi.list({
                page: page - 1,
                size: pageSize,
                search: search.trim() || undefined,
                status: statusFilter,
                ingredientId: ingredientFilter
            });
            const content = Array.isArray(res?.content) ? res.content : [];
            setSuppliers(content.filter((item): item is SupplierResponse => Boolean(item && item.id)));
            setTotalElements(res?.totalElements || 0);
            setTotalPages(res?.totalPages || 1);
        } catch (err: unknown) {
            console.error("Lỗi tải danh sách nhà cung cấp:", err);
            setError(err instanceof Error ? err.message : "Không thể tải danh sách nhà cung cấp");
        } finally {
            setLoading(false);
        }
    }, [page, pageSize, search, statusFilter, ingredientFilter]);

    // Load ingredient filter options
    useEffect(() => {
        ingredientApi
            .list({ page: 0, size: 200, status: "ACTIVE" })
            .then((res) => {
                const content = Array.isArray(res?.content) ? res.content : [];
                setAllIngredients(content.filter((item): item is Ingredient => Boolean(item && item.id)));
            })
            .catch((err) => console.error("Lỗi tải nguyên liệu:", err));
    }, []);

    useEffect(() => {
        void loadSuppliers();
    }, [loadSuppliers]);

    // Handlers
    const handleOpenCreate = () => {
        setEditingSupplier(null);
        setIsFormOpen(true);
    };

    const handleOpenEdit = (sup: SupplierResponse) => {
        setEditingSupplier(sup);
        setIsFormOpen(true);
    };

    const handleFormSubmit = async (data: SupplierRequest) => {
        if (editingSupplier) {
            await supplierApi.update(editingSupplier.id, data);
        } else {
            await supplierApi.create(data);
        }
        await loadSuppliers();
    };

    const handleDeleteConfirm = async () => {
        if (!deletingSupplier) return;
        try {
            setIsDeleting(true);
            await supplierApi.delete(deletingSupplier.id);
            setDeletingSupplier(null);
            await loadSuppliers();
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Xóa nhà cung cấp thất bại");
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <div className="flex flex-col gap-6 p-6">
            {/* Page Header */}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h1 className="text-2xl font-black text-neutral-800 tracking-tight">
                        Nhà cung cấp
                    </h1>
                    <p className="text-sm font-semibold text-neutral-500 mt-1">
                        Quản lý danh sách nhà cung ứng và các nguyên liệu liên kết
                    </p>
                </div>
                <button
                    onClick={handleOpenCreate}
                    className="inline-flex items-center gap-2 h-11 rounded-xl bg-emerald-600 px-5 text-sm font-bold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-700 transition-all cursor-pointer"
                >
                    <Plus size={18} />
                    Thêm nhà cung cấp
                </button>
            </div>

            {/* Filters Bar */}
            <div className="flex flex-col sm:flex-row gap-3 rounded-2xl border border-neutral-200/80 bg-white p-4 shadow-xs">
                {/* Search */}
                <div className="relative flex-1">
                    <Search size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-neutral-400" />
                    <input
                        type="text"
                        value={search}
                        onChange={(e) => {
                            setSearch(e.target.value);
                            setPage(1);
                        }}
                        placeholder="Tìm kiếm theo tên, mã NCC, người liên hệ, SĐT..."
                        className="w-full h-10 rounded-xl border border-neutral-200 pl-10 pr-4 text-sm font-semibold text-neutral-800 placeholder:text-neutral-400 focus:border-emerald-500 focus:outline-hidden transition-all"
                    />
                </div>

                {/* Status Filter */}
                <select
                    value={statusFilter}
                    onChange={(e) => {
                        setStatusFilter(e.target.value);
                        setPage(1);
                    }}
                    className="h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-bold text-neutral-700 bg-white focus:border-emerald-500 focus:outline-hidden transition-all cursor-pointer"
                >
                    <option value="ALL">Tất cả trạng thái</option>
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="INACTIVE">Ngừng hoạt động</option>
                </select>

                {/* Ingredient Filter */}
                <select
                    value={ingredientFilter}
                    onChange={(e) => {
                        setIngredientFilter(e.target.value);
                        setPage(1);
                    }}
                    className="h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-bold text-neutral-700 bg-white focus:border-emerald-500 focus:outline-hidden transition-all cursor-pointer"
                >
                    <option value="ALL">Tất cả nguyên liệu</option>
                    {allIngredients.filter(Boolean).map((ing) => (
                        <option key={ing.id} value={String(ing.id)}>
                            {ing.name || "Nguyên liệu"} ({ing.ingredientCode || (ing as any).code || "NL"})
                        </option>
                    ))}
                </select>
            </div>

            {/* Error banner */}
            {error && (
                <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-600 border border-red-100">
                    {error}
                </div>
            )}

            {/* Suppliers Table */}
            <div className="rounded-2xl border border-neutral-200/80 bg-white shadow-xs overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-neutral-600">
                        <thead className="border-b border-neutral-100 bg-neutral-50/80 text-xs font-black uppercase tracking-wider text-neutral-500">
                            <tr>
                                <th className="px-5 py-3.5">Mã NCC</th>
                                <th className="px-5 py-3.5">Tên nhà cung cấp</th>
                                <th className="px-5 py-3.5">Liên hệ</th>
                                <th className="px-5 py-3.5">Địa chỉ</th>
                                <th className="px-5 py-3.5">Nguyên liệu cung cấp</th>
                                <th className="px-5 py-3.5 text-center">Trạng thái</th>
                                <th className="px-5 py-3.5 text-right">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-neutral-100">
                            {loading ? (
                                <tr>
                                    <td colSpan={7} className="px-5 py-12 text-center text-sm font-semibold text-neutral-400">
                                        Đang tải danh sách nhà cung cấp...
                                    </td>
                                </tr>
                            ) : suppliers.length === 0 ? (
                                <tr>
                                    <td colSpan={7} className="px-5 py-12 text-center">
                                        <div className="flex flex-col items-center justify-center gap-2">
                                            <Building2 size={36} className="text-neutral-300" />
                                            <p className="text-sm font-bold text-neutral-600">
                                                Không tìm thấy nhà cung cấp nào
                                            </p>
                                            <p className="text-xs text-neutral-400">
                                                Thử thay đổi bộ lọc tìm kiếm hoặc tạo mới nhà cung cấp
                                            </p>
                                        </div>
                                    </td>
                                </tr>
                            ) : (
                                suppliers.filter(Boolean).map((sup) => (
                                    <tr key={sup.id} className="hover:bg-neutral-50/70 transition-colors">
                                        {/* Code */}
                                        <td className="px-5 py-4 font-mono text-xs font-bold text-emerald-700 whitespace-nowrap">
                                            {sup.supplierCode}
                                        </td>

                                        {/* Name */}
                                        <td className="px-5 py-4 font-bold text-neutral-800">
                                            <button
                                                onClick={() => setViewingSupplier(sup)}
                                                className="hover:text-emerald-600 text-left transition-colors cursor-pointer"
                                            >
                                                {sup.name}
                                            </button>
                                        </td>

                                        {/* Contact */}
                                        <td className="px-5 py-4 text-xs">
                                            {sup.contactName && (
                                                <p className="font-bold text-neutral-700">{sup.contactName}</p>
                                            )}
                                            {sup.phone ? (
                                                <p className="font-medium text-neutral-500">{sup.phone}</p>
                                            ) : (
                                                !sup.contactName && <span className="text-neutral-300">-</span>
                                            )}
                                        </td>

                                        {/* Address */}
                                        <td className="px-5 py-4 text-xs font-medium text-neutral-600 max-w-[200px] truncate">
                                            {sup.address || <span className="text-neutral-300">-</span>}
                                        </td>

                                        {/* Supplied Ingredients */}
                                        <td className="px-5 py-4">
                                            {!sup.ingredients || sup.ingredients.length === 0 ? (
                                                <span className="text-xs text-neutral-300 font-medium">Chưa có</span>
                                            ) : (
                                                <div className="flex flex-wrap items-center gap-1.5 max-w-xs">
                                                    {sup.ingredients.filter(Boolean).slice(0, 2).map((ing) => (
                                                        <span
                                                            key={ing.id}
                                                            className="inline-flex items-center rounded-md bg-neutral-100 px-2 py-0.5 text-[11px] font-bold text-neutral-700"
                                                        >
                                                            {ing.name}
                                                        </span>
                                                    ))}
                                                    {sup.ingredients.filter(Boolean).length > 2 && (
                                                        <span className="inline-flex items-center rounded-md bg-emerald-50 px-1.5 py-0.5 text-[11px] font-black text-emerald-700">
                                                            +{sup.ingredients.filter(Boolean).length - 2}
                                                        </span>
                                                    )}
                                                </div>
                                            )}
                                        </td>

                                        {/* Status */}
                                        <td className="px-5 py-4 text-center whitespace-nowrap">
                                            <CatalogStatusBadge status={sup.status} />
                                        </td>

                                        {/* Actions */}
                                        <td className="px-5 py-4 text-right whitespace-nowrap">
                                            <div className="inline-flex items-center gap-1">
                                                <button
                                                    onClick={() => setViewingSupplier(sup)}
                                                    title="Xem chi tiết"
                                                    className="rounded-lg p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 transition-colors cursor-pointer"
                                                >
                                                    <Eye size={16} />
                                                </button>
                                                <button
                                                    onClick={() => handleOpenEdit(sup)}
                                                    title="Chỉnh sửa"
                                                    className="rounded-lg p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-emerald-600 transition-colors cursor-pointer"
                                                >
                                                    <Pencil size={16} />
                                                </button>
                                                <button
                                                    onClick={() => setDeletingSupplier(sup)}
                                                    title="Xóa"
                                                    className="rounded-lg p-1.5 text-neutral-400 hover:bg-red-50 hover:text-red-600 transition-colors cursor-pointer"
                                                >
                                                    <Trash2 size={16} />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination */}
                {totalElements > 0 && (
                    <Pagination
                        page={page}
                        pageCount={totalPages}
                        totalItems={totalElements}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={setPageSize}
                        totalItemsLabel="Tổng số nhà cung cấp:"
                    />
                )}
            </div>

            {/* Modals & Drawers */}
            <SupplierFormModal
                isOpen={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleFormSubmit}
                supplier={editingSupplier}
            />

            <SupplierDetailDrawer
                isOpen={Boolean(viewingSupplier)}
                onClose={() => setViewingSupplier(null)}
                supplier={viewingSupplier}
                onEdit={(sup) => {
                    setViewingSupplier(null);
                    handleOpenEdit(sup);
                }}
            />

            {Boolean(deletingSupplier) && (
                <ConfirmDialog
                    error=""
                    submitting={isDeleting}
                    onCancel={() => setDeletingSupplier(null)}
                    onConfirm={handleDeleteConfirm}
                    title="Xác nhận xóa nhà cung cấp"
                    description={`Bạn có chắc chắn muốn xóa nhà cung cấp "${deletingSupplier?.name}" (${deletingSupplier?.supplierCode}) không?`}
                    confirmLabel="Xác nhận xóa"
                    confirmVariant="danger"
                />
            )}
        </div>
    );
}
