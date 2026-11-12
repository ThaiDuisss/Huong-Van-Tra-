import { Check, Plus, X } from "lucide-react";
import { useEffect, useState, type FormEvent, type MouseEvent } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import { Field, NativeSelect, TextArea, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
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
                const content = Array.isArray(res?.content) ? res.content : [];
                setAllIngredients(content.filter((item): item is Ingredient => Boolean(item && item.id)));
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
            setSelectedIngredientIds(
                Array.isArray(supplier.ingredients)
                    ? supplier.ingredients.filter(Boolean).map((i) => String(i.id))
                    : []
            );
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

    const filteredIngredients = allIngredients.filter(Boolean).filter((ing) => {
        const query = ingredientSearch.toLowerCase().trim();
        if (!query) return true;
        const name = (ing.name || "").toLowerCase();
        const code = (ing.ingredientCode || (ing as any).code || "").toLowerCase();
        return name.includes(query) || code.includes(query);
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
        <ModalShell
            className="w-[min(672px,calc(100vw-32px))]"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="supplier-form-title"
            onClose={onClose}
        >
            <div className="flex max-h-[85vh] flex-col overflow-hidden">
                {/* Header */}
                <header className="flex items-center justify-between border-b border-neutral-100 px-6 py-4">
                    <div>
                        <h2 className="text-lg font-black text-neutral-800" id="supplier-form-title">
                            {isEdit ? "Cập nhật Nhà cung cấp" : "Thêm mới Nhà cung cấp"}
                        </h2>
                        {isEdit && supplier?.supplierCode && (
                            <p className="text-xs font-bold text-neutral-400 mt-0.5">
                                Mã: <span className="font-mono text-emerald-600">{supplier.supplierCode}</span>
                            </p>
                        )}
                    </div>
                    <IconButton aria-label="Đóng" onClick={onClose} size="md">
                        <X size={20} />
                    </IconButton>
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
                        <Field className="md:col-span-2" label="Tên nhà cung cấp" required>
                            <TextInput
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="Ví dụ: Công ty TNHH Trà Thái Nguyên"
                                required
                            />
                        </Field>

                        <Field label="Người liên hệ">
                            <TextInput
                                type="text"
                                value={contactName}
                                onChange={(e) => setContactName(e.target.value)}
                                placeholder="Nguyễn Văn A"
                            />
                        </Field>

                        <Field label="Số điện thoại">
                            <TextInput
                                type="tel"
                                value={phone}
                                onChange={(e) => setPhone(e.target.value)}
                                placeholder="0987 654 321"
                            />
                        </Field>

                        <Field label="Email">
                            <TextInput
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                placeholder="supplier@example.com"
                            />
                        </Field>

                        <Field label="Mã số thuế">
                            <TextInput
                                type="text"
                                value={taxCode}
                                onChange={(e) => setTaxCode(e.target.value)}
                                placeholder="0102345678"
                            />
                        </Field>

                        <Field className="md:col-span-2" label="Địa chỉ">
                            <TextInput
                                type="text"
                                value={address}
                                onChange={(e) => setAddress(e.target.value)}
                                placeholder="Số 123 đường ABC, Quận XYZ, TP. Hà Nội"
                            />
                        </Field>

                        <Field label="Trạng thái">
                            <NativeSelect
                                value={status}
                                onChange={(e) => setStatus(e.target.value as SupplierStatus)}
                            >
                                <option value="ACTIVE">Đang hoạt động</option>
                                <option value="INACTIVE">Ngừng hoạt động</option>
                            </NativeSelect>
                        </Field>

                        <Field className="md:col-span-2" label="Ghi chú">
                            <TextArea
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                rows={2}
                                placeholder="Thông tin bổ sung về nhà cung cấp..."
                            />
                        </Field>
                    </div>

                    {/* Multi-select Ingredient Selection */}
                    <div className="rounded-xl border border-neutral-200 p-4 bg-neutral-50/50">
                        <div className="flex items-center justify-between mb-2">
                            <label className="text-xs font-black text-neutral-700 uppercase tracking-wider flex items-center gap-1.5">
                                Nguyên liệu cung cấp
                                <StatusBadge tone="success">Đã chọn: {selectedIngredientIds.length}</StatusBadge>
                            </label>
                            {selectedIngredientIds.length > 0 && (
                                <Button onClick={() => setSelectedIngredientIds([])} size="sm" variant="link">
                                    Bỏ chọn tất cả
                                </Button>
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
                        <div className="mb-2 flex">
                            <SearchInput
                                onChange={setIngredientSearch}
                                placeholder="Tìm kiếm nguyên liệu theo tên hoặc mã..."
                                type="text"
                                value={ingredientSearch}
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
                        <Button disabled={submitting} onClick={onClose}>
                            Hủy
                        </Button>
                        <Button disabled={submitting} type="submit" variant="primary">
                            {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo nhà cung cấp"}
                        </Button>
                    </div>
                </form>
            </div>
        </ModalShell>
    );
}
