import {
    Building2,
    Calendar,
    FileText,
    Layers3,
    Mail,
    MapPin,
    Pencil,
    Phone,
    User,
    X
} from "lucide-react";

import { CatalogStatusBadge } from "../../product/components/CatalogStatusBadge";
import type { SupplierResponse } from "../types/supplier.types";

interface SupplierDetailDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    supplier: SupplierResponse | null;
    onEdit: (supplier: SupplierResponse) => void;
}

export function SupplierDetailDrawer({
    isOpen,
    onClose,
    supplier,
    onEdit
}: SupplierDetailDrawerProps) {
    if (!isOpen || !supplier) return null;

    const formattedDate = supplier.createdAt
        ? new Date(supplier.createdAt).toLocaleDateString("vi-VN", {
              day: "2-digit",
              month: "2-digit",
              year: "numeric",
              hour: "2-digit",
              minute: "2-digit"
          })
        : "-";

    return (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-xs animate-in fade-in duration-200">
            <div className="flex h-full w-full max-w-lg flex-col bg-white shadow-2xl animate-in slide-in-from-right duration-300">
                {/* Header */}
                <header className="flex items-center justify-between border-b border-neutral-100 px-6 py-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
                            <Building2 size={20} />
                        </div>
                        <div>
                            <h2 className="text-base font-black text-neutral-800">{supplier.name}</h2>
                            <p className="text-xs font-bold font-mono text-emerald-600">
                                {supplier.supplierCode}
                            </p>
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="rounded-lg p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-600 transition-colors cursor-pointer"
                        type="button"
                    >
                        <X size={20} />
                    </button>
                </header>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Status & Quick Overview */}
                    <div className="rounded-xl border border-neutral-100 bg-neutral-50/70 p-4 space-y-3">
                        <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-neutral-500">Trạng thái</span>
                            <CatalogStatusBadge status={supplier.status} />
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-neutral-500">Ngày tạo</span>
                            <span className="text-xs font-semibold text-neutral-700 flex items-center gap-1">
                                <Calendar size={13} className="text-neutral-400" />
                                {formattedDate}
                            </span>
                        </div>
                        {supplier.taxCode && (
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-neutral-500">Mã số thuế</span>
                                <span className="text-xs font-mono font-bold text-neutral-800">
                                    {supplier.taxCode}
                                </span>
                            </div>
                        )}
                    </div>

                    {/* Contact Details */}
                    <div>
                        <h3 className="text-xs font-black text-neutral-400 uppercase tracking-wider mb-3">
                            Thông tin liên hệ
                        </h3>
                        <div className="space-y-3">
                            <div className="flex items-start gap-3 text-xs">
                                <User size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                                <div>
                                    <p className="font-bold text-neutral-700">Người liên hệ</p>
                                    <p className="font-medium text-neutral-600">
                                        {supplier.contactName || "Chưa cập nhật"}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-start gap-3 text-xs">
                                <Phone size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                                <div>
                                    <p className="font-bold text-neutral-700">Số điện thoại</p>
                                    <p className="font-medium text-neutral-600">
                                        {supplier.phone ? (
                                            <a
                                                href={`tel:${supplier.phone}`}
                                                className="text-emerald-600 hover:underline font-bold"
                                            >
                                                {supplier.phone}
                                            </a>
                                        ) : (
                                            "Chưa cập nhật"
                                        )}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-start gap-3 text-xs">
                                <Mail size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                                <div>
                                    <p className="font-bold text-neutral-700">Email</p>
                                    <p className="font-medium text-neutral-600">
                                        {supplier.email ? (
                                            <a
                                                href={`mailto:${supplier.email}`}
                                                className="text-emerald-600 hover:underline font-bold"
                                            >
                                                {supplier.email}
                                            </a>
                                        ) : (
                                            "Chưa cập nhật"
                                        )}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-start gap-3 text-xs">
                                <MapPin size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                                <div>
                                    <p className="font-bold text-neutral-700">Địa chỉ</p>
                                    <p className="font-medium text-neutral-600">
                                        {supplier.address || "Chưa cập nhật"}
                                    </p>
                                </div>
                            </div>

                            {supplier.notes && (
                                <div className="flex items-start gap-3 text-xs">
                                    <FileText size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                                    <div>
                                        <p className="font-bold text-neutral-700">Ghi chú</p>
                                        <p className="font-medium text-neutral-600 whitespace-pre-wrap">
                                            {supplier.notes}
                                        </p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Supplied Ingredients List */}
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-xs font-black text-neutral-400 uppercase tracking-wider flex items-center gap-1.5">
                                <Layers3 size={14} />
                                Nguyên liệu cung cấp ({supplier.ingredients?.length || 0})
                            </h3>
                        </div>

                        {!supplier.ingredients || supplier.ingredients.length === 0 ? (
                            <div className="rounded-xl border border-dashed border-neutral-200 p-4 text-center text-xs font-semibold text-neutral-400">
                                Chưa liên kết nguyên liệu nào
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {supplier.ingredients.filter(Boolean).map((ing) => (
                                    <div
                                        key={ing.id}
                                        className="flex items-center justify-between rounded-xl border border-neutral-100 bg-neutral-50/50 p-3"
                                    >
                                        <div>
                                            <p className="text-xs font-bold text-neutral-800">{ing.name}</p>
                                            <p className="text-[11px] font-mono text-neutral-400">
                                                {ing.ingredientCode}
                                            </p>
                                        </div>
                                        <CatalogStatusBadge status={ing.status} />
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <footer className="flex items-center justify-end gap-3 border-t border-neutral-100 p-4 bg-neutral-50">
                    <button
                        onClick={onClose}
                        className="h-10 rounded-xl border border-neutral-200 px-4 text-sm font-bold text-neutral-600 hover:bg-neutral-100 transition-colors cursor-pointer"
                        type="button"
                    >
                        Đóng
                    </button>
                    <button
                        onClick={() => {
                            onClose();
                            onEdit(supplier);
                        }}
                        className="h-10 rounded-xl bg-emerald-600 px-5 text-sm font-bold text-white shadow-sm hover:bg-emerald-700 transition-all flex items-center gap-1.5 cursor-pointer"
                        type="button"
                    >
                        <Pencil size={15} />
                        Chỉnh sửa
                    </button>
                </footer>
            </div>
        </div>
    );
}
