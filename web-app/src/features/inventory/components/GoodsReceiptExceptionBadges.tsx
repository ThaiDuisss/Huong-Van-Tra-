import type { GoodsReceiptLine } from "../types/goods-receipt.types";
import { getLineException } from "../utils/goodsReceiptException";
import { formatVietnamNumber } from "../utils/inventory-format";

/** Huy hiệu Thừa / Thiếu / Hỏng của 1 dòng phiếu nhập; không có ngoại lệ thì hiện "Đủ". */
export function LineExceptionBadges({ line }: { line: GoodsReceiptLine }) {
    const { excess, shortage, damaged } = getLineException(line);

    if (excess === 0 && shortage === 0 && damaged === 0) {
        return <span className="inline-flex rounded bg-green-50 px-2 py-0.5 text-xs font-bold text-green-700">Đủ</span>;
    }

    return (
        <span className="inline-flex flex-wrap gap-1">
            {excess > 0 ? (
                <span className="inline-flex rounded border border-cyan-200 bg-cyan-50 px-2 py-0.5 text-xs font-bold text-cyan-700">
                    Thừa +{formatVietnamNumber(excess)}
                </span>
            ) : null}
            {shortage > 0 ? (
                <span className="inline-flex rounded border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-700">
                    Thiếu −{formatVietnamNumber(shortage)}
                </span>
            ) : null}
            {damaged > 0 ? (
                <span className="inline-flex rounded border border-red-200 bg-red-50 px-2 py-0.5 text-xs font-bold text-red-700">
                    Hỏng {formatVietnamNumber(damaged)}
                </span>
            ) : null}
        </span>
    );
}
