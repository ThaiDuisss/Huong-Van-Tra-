import { PackagePlus } from "lucide-react";

export interface ShelfShortage {
    productVariantId: string;
    name: string;
    sku: string;
    /** Tổng số lượng đang đặt trong giỏ. */
    requestedQuantity: number;
    shelfQuantity: number;
    warehouseQuantity: number;
    /** Số thiếu trên kệ = đặt − trên kệ. */
    missingQuantity: number;
    shelfLocationId?: string | null;
    warehouseLocationId?: string | null;
}

interface ShelfShortageDialogProps {
    shortage: ShelfShortage;
    submitting: boolean;
    error: string;
    /** Tạo phiếu nhập kệ và gửi kho xử lý ngay. */
    onCreateNow(): void;
    /** Lưu phiếu nháp để điền / gửi sau. */
    onSaveDraft(): void;
    /** Chỉ dùng khi tạo phiếu lỗi, để nhân viên vẫn tiếp tục bán được. */
    onDismiss(): void;
}

export function ShelfShortageDialog({
    shortage,
    submitting,
    error,
    onCreateNow,
    onSaveDraft,
    onDismiss
}: ShelfShortageDialogProps) {
    const warehouseShort = shortage.warehouseQuantity < shortage.missingQuantity;

    return (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4" role="presentation">
            <section
                aria-labelledby="shelf-shortage-title"
                aria-modal="true"
                className="grid w-full max-w-[520px] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start gap-3 border-b border-[#e8eef0] px-5 py-4">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-amber-50 text-amber-700">
                        <PackagePlus aria-hidden="true" size={22} />
                    </span>
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-black text-[#172317]" id="shelf-shortage-title">
                            Trên kệ đang thiếu {shortage.missingQuantity}
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            {shortage.name}
                            {shortage.sku ? ` · ${shortage.sku}` : ""}
                        </p>
                    </div>
                </header>

                <div className="grid gap-4 px-5 py-4">
                    <dl className="grid grid-cols-3 gap-2 text-center">
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Khách đặt</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {shortage.requestedQuantity}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Trên kệ</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {shortage.shelfQuantity}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Trong kho</dt>
                            <dd
                                className={`text-lg font-black tabular-nums ${
                                    warehouseShort ? "text-red-700" : "text-[#172317]"
                                }`}
                            >
                                {shortage.warehouseQuantity}
                            </dd>
                        </div>
                    </dl>

                    <p className="text-sm font-semibold leading-6 text-[#536078]">
                        Bạn có muốn làm phiếu nhập kho lên kệ {shortage.missingQuantity} sản phẩm ngay không? Nếu
                        chưa, hệ thống lưu phiếu nháp để điền sau. Khi thanh toán, phần thiếu được xuất từ kho lên
                        kệ ngay để giao cho khách; quản lý duyệt phiếu sau.
                    </p>

                    {warehouseShort ? (
                        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
                            Kho chỉ còn {shortage.warehouseQuantity}, chưa đủ {shortage.missingQuantity}. Đơn sẽ
                            không trừ được tồn khi thanh toán cho tới khi kho có đủ hàng.
                        </p>
                    ) : null}

                    {error ? (
                        <p
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            role="alert"
                        >
                            {error}
                        </p>
                    ) : null}
                </div>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                    {error ? (
                        <button
                            className="mr-auto h-10 rounded-lg px-3 text-sm font-black text-[#536078] hover:bg-neutral-50"
                            disabled={submitting}
                            onClick={onDismiss}
                            type="button"
                        >
                            Bỏ qua
                        </button>
                    ) : null}
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                        disabled={submitting}
                        onClick={onSaveDraft}
                        type="button"
                    >
                        Để sau (lưu nháp)
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={submitting}
                        onClick={onCreateNow}
                        type="button"
                    >
                        {submitting ? "Đang tạo..." : "Tạo phiếu nhập kệ"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
