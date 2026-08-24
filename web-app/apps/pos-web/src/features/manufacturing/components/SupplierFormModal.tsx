import { Check, Plus, Search, X } from "lucide-react";
import { useEffect, useState, type FormEvent, type MouseEvent } from "react";

import { ingredientApi } from "../../product/services/ingredient.api";
import type { Ingredient } from "../../product/types/product.types";
import type { SupplierRequest, SupplierResponse, SupplierStatus } from "../types/supplier.types";

interface SupplierFormModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (data: SupplierRequest) => Promise<void>;
    supplier?: SupplierResponse | null;
}

export function SupplierFormModal({
    isOpen,
    onClose,
    onSubmit,
    supplier
}: SupplierFormModalProps) {
    const isEdit = Boolean(supplier);

    const [name, setName] = useState("");
    const [contactName, setContactName] = useState("");
    const [phone, setPhone] = useState("");
    const [email, setEmail] = useState("");
    const [address, setAddress] = useState("");
    const [taxCode, setTaxCode] = useState("");
    const [notes, setNotes] = useState("");
    const [status, setStatus] = useState<SupplierStatus>("ACTIVE");
    const [selectedIngredientIds, setSelectedIngredientIds] = useState<string[]>([]);

    const [allIngredients, setAllIngredients] = useState<Ingredient[]>([]);
    const [ingredientSearch, setIngredientSearch] = useState("");
    const [loadingIngredients, setLoadingIngredients] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    // Load all ingredients for selection
    useEffect(() => {
        if (!isOpen) return;

        setLoadingIngredients(true);
        ingredientApi
            .list({ page: 0, size: 200, status: "ACTIVE" })
            .then((res) => {
                setAllIngredients(res.content || []);
            })
            .catch((err) => {
                console.error("Lỗi tải danh sách nguyên liệu:", err);
            })
            .finally(() => {
                setLoadingIngredients(false);
            });
    }, [isOpen]);

    // Populate data when editing
    useEffect(() => {
        if (supplier) {
            setName(supplier.name || "");
            setContactName(supplier.contactName || "");
            setPhone(supplier.phone || "");
            setEmail(supplier.email || "");
            setAddress(supplier.address || "");
            setTaxCode(supplier.taxCode || "");
            setNotes(supplier.notes || "");
            setStatus(supplier.status || "ACTIVE");
            setSelectedIngredientIds(supplier.ingredients ? supplier.ingredients.map((i) => i.id) : []);
        } else {
            setName("");
            setContactName("");
            setPhone("");
            setEmail("");
            setAddress("");
            setTaxCode("");
            setNotes("");
            setStatus("ACTIVE");
            setSelectedIngredientIds([]);
        }
        setError("");
        setIngredientSearch("");
    }, [supplier, isOpen]);

    if (!isOpen) return null;

    const filteredIngredients = allIngredients.filter((ing) => {
        const query = ingredientSearch.toLowerCase().trim();
        if (!query) return true;
        return (
            ing.name.toLowerCase().includes(query) ||
            ing.ingredientCode.toLowerCase().includes(query)
        );
    });

    const toggleIngredient = (id: string) => {
        setSelectedIngredientIds((prev) =>
            prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
        );
    };

    const removeIngredient = (id: string, e: MouseEvent) => {
        e.stopPropagation();
        setSelectedIngredientIds((prev) => prev.filter((item) => item !== id));
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!name.trim()) {
            setError("Vui lòng nhập tên nhà cung cấp");
            return;
        }

        try {
            setSubmitting(true);
            setError("");
            await onSubmit({
                name: name.trim(),
                contactName: contactName.trim() || undefined,
                phone: phone.trim() || undefined,
                email: email.trim() || undefined,
                address: address.trim() || undefined,
                taxCode: taxCode.trim() || undefined,
                notes: notes.trim() || undefined,
                status,
                ingredientIds: selectedIngredientIds
            });
            onClose();
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu nhà cung cấp";
            setError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs animate-in fade-in duration-200">
            <div className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white shadow-2xl overflow-hidden">
                {/* Header */}
                <header className="flex items-center justify-between border-b border-neutral-100 px-6 py-4">
                    <div>
                        <h2 className="text-lg font-black text-neutral-800">
                            {isEdit ? "Cập nhật Nhà cung cấp" : "Thêm mới Nhà cung cấp"}
                        </h2>
                        {isEdit && supplier?.supplierCode && (
                            <p className="text-xs font-bold text-neutral-400 mt-0.5">
                                Mã: <span className="font-mono text-emerald-600">{supplier.supplierCode}</span>
                            </p>
                        )}
                    </div>
                    <button
                        onClick={onClose}
                        className="rounded-lg p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-600 transition-colors cursor-pointer"
                        type="button"
                    >
                        <X size={20} />
                    </button>
                </header>

                {/* Body Form */}
                <form onSubmit={handleSubmit} className="flex flex-col overflow-y-auto p-6 gap-5">
                    {error && (
                        <div className="rounded-xl bg-red-50 p-3.5 text-sm font-bold text-red-600 border border-red-100">
                            {error}
                        </div>
                    )}

                    {/* Basic Info */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="md:col-span-2">
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Tên nhà cung cấp <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="Ví dụ: Công ty TNHH Trà Thái Nguyên"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Người liên hệ
                            </label>
                            <input
                                type="text"
                                value={contactName}
                                onChange={(e) => setContactName(e.target.value)}
                                placeholder="Nguyễn Văn A"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Số điện thoại
                            </label>
                            <input
                                type="tel"
                                value={phone}
                                onChange={(e) => setPhone(e.target.value)}
                                placeholder="0987 654 321"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Email
                            </label>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                placeholder="supplier@example.com"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Mã số thuế
                            </label>
                            <input
                                type="text"
                                value={taxCode}
                                onChange={(e) => setTaxCode(e.target.value)}
                                placeholder="0102345678"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        <div className="md:col-span-2">
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Địa chỉ
                            </label>
                            <input
                                type="text"
                                value={address}
                                onChange={(e) => setAddress(e.target.value)}
                                placeholder="Số 123 đường ABC, Quận XYZ, TP. Hà Nội"
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Trạng thái
                            </label>
                            <select
                                value={status}
                                onChange={(e) => setStatus(e.target.value as SupplierStatus)}
                                className="w-full h-10 rounded-xl border border-neutral-200 px-3.5 text-sm font-bold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all bg-white cursor-pointer"
                            >
                                <option value="ACTIVE">Đang hoạt động</option>
                                <option value="INACTIVE">Ngừng hoạt động</option>
                            </select>
                        </div>

                        <div className="md:col-span-2">
                            <label className="block text-xs font-black text-neutral-600 uppercase tracking-wider mb-1.5">
                                Ghi chú
                            </label>
                            <textarea
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                rows={2}
                                placeholder="Thông tin bổ sung về nhà cung cấp..."
                                className="w-full rounded-xl border border-neutral-200 p-3 text-sm font-semibold text-neutral-800 focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>
                    </div>

                    {/* Multi-select Ingredient Selection */}
                    <div className="rounded-xl border border-neutral-200 p-4 bg-neutral-50/50">
                        <div className="flex items-center justify-between mb-2">
                            <label className="text-xs font-black text-neutral-700 uppercase tracking-wider flex items-center gap-1.5">
                                Nguyên liệu cung cấp
                                <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-black text-emerald-700">
                                    Đã chọn: {selectedIngredientIds.length}
                                </span>
                            </label>
                            {selectedIngredientIds.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setSelectedIngredientIds([])}
                                    className="text-xs font-bold text-neutral-400 hover:text-red-500 transition-colors cursor-pointer"
                                >
                                    Bỏ chọn tất cả
                                </button>
                            )}
                        </div>

                        {/* Selected chips */}
                        {selectedIngredientIds.length > 0 && (
                            <div className="flex flex-wrap gap-1.5 mb-3 max-h-24 overflow-y-auto p-1">
                                {selectedIngredientIds.map((id) => {
                                    const ing = allIngredients.find((i) => String(i.id) === id);
                                    const label = ing ? `${ing.name} (${ing.ingredientCode})` : id;
                                    return (
                                        <span
                                            key={id}
                                            className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 border border-emerald-200 px-2.5 py-1 text-xs font-bold text-emerald-800"
                                        >
                                            {label}
                                            <button
                                                type="button"
                                                onClick={(e) => removeIngredient(id, e)}
                                                className="hover:bg-emerald-200/60 rounded-full p-0.5 text-emerald-600 transition-colors cursor-pointer"
                                            >
                                                <X size={12} />
                                            </button>
                                        </span>
                                    );
                                })}
                            </div>
                        )}

                        {/* Search & Pick Box */}
                        <div className="relative mb-2">
                            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" />
                            <input
                                type="text"
                                value={ingredientSearch}
                                onChange={(e) => setIngredientSearch(e.target.value)}
                                placeholder="Tìm kiếm nguyên liệu theo tên hoặc mã..."
                                className="w-full h-9 rounded-lg border border-neutral-200 pl-9 pr-3 text-xs font-semibold text-neutral-800 bg-white focus:border-emerald-500 focus:outline-hidden transition-all"
                            />
                        </div>

                        {/* List options */}
                        <div className="max-h-40 overflow-y-auto rounded-lg border border-neutral-200 bg-white divide-y divide-neutral-100">
                            {loadingIngredients ? (
                                <div className="p-3 text-center text-xs font-semibold text-neutral-400">
                                    Đang tải danh sách nguyên liệu...
                                </div>
                            ) : filteredIngredients.length === 0 ? (
                                <div className="p-3 text-center text-xs font-semibold text-neutral-400">
                                    Không tìm thấy nguyên liệu phù hợp
                                </div>
                            ) : (
                                filteredIngredients.map((ing) => {
                                    const ingId = String(ing.id);
                                    const isSelected = selectedIngredientIds.includes(ingId);
                                    return (
                                        <div
                                            key={ingId}
                                            onClick={() => toggleIngredient(ingId)}
                                            className={`flex items-center justify-between px-3 py-2 text-xs cursor-pointer transition-colors ${
                                                isSelected
                                                    ? "bg-emerald-50 text-emerald-900 font-bold"
                                                    : "hover:bg-neutral-50 text-neutral-700 font-medium"
                                            }`}
                                        >
                                            <div className="flex flex-col">
                                                <span className="font-bold">{ing.name}</span>
                                                <span className="text-[11px] text-neutral-400 font-mono">
                                                    {ing.ingredientCode}
                                                </span>
                                            </div>
                                            <div
                                                className={`flex h-5 w-5 items-center justify-center rounded border transition-colors ${
                                                    isSelected
                                                        ? "border-emerald-600 bg-emerald-600 text-white"
                                                        : "border-neutral-300 bg-white"
                                                }`}
                                            >
                                                {isSelected ? <Check size={12} /> : <Plus size={12} className="text-neutral-400" />}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>

                    {/* Footer Actions */}
                    <div className="flex justify-end gap-3 pt-2 border-t border-neutral-100">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={submitting}
                            className="h-10 rounded-xl border border-neutral-200 px-5 text-sm font-bold text-neutral-600 hover:bg-neutral-50 transition-colors cursor-pointer"
                        >
                            Hủy
                        </button>
                        <button
                            type="submit"
                            disabled={submitting}
                            className="h-10 rounded-xl bg-emerald-600 px-6 text-sm font-bold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-700 transition-all disabled:opacity-50 cursor-pointer"
                        >
                            {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo nhà cung cấp"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
