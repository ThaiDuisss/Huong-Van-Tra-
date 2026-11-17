import { StatusBadge } from "@/components/ui/StatusBadge";

import type { GoodsReceiptLine } from "../types/goods-receipt.types";
import { getLineException } from "../utils/goodsReceiptException";
import { formatVietnamNumber } from "../utils/inventory-format";

/** Huy hiệu Thừa / Thiếu / Hỏng của 1 dòng phiếu nhập; không có ngoại lệ thì hiện "Đủ". */
export function LineExceptionBadges({ line }: { line: GoodsReceiptLine }) {
    const { excess, shortage, damaged } = getLineException(line);

    if (excess === 0 && shortage === 0 && damaged === 0) {
        return <StatusBadge shape="tag" tone="success">Đủ</StatusBadge>;
    }

    return (
        <span className="inline-flex flex-wrap gap-1">
            {excess > 0 ? <StatusBadge shape="tag" tone="cyan">Thừa +{formatVietnamNumber(excess)}</StatusBadge> : null}
            {shortage > 0 ? <StatusBadge shape="tag" tone="warning">Thiếu −{formatVietnamNumber(shortage)}</StatusBadge> : null}
            {damaged > 0 ? <StatusBadge shape="tag" tone="danger">Hỏng {formatVietnamNumber(damaged)}</StatusBadge> : null}
        </span>
    );
}
