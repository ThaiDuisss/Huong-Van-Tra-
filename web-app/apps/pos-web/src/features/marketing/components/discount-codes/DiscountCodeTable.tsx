import { MoreVertical } from "lucide-react";

import { MarketingPagination } from "../MarketingPagination";
import {
    DiscountCodeScopeBadge,
    DiscountCodeStatusBadge
} from "./DiscountCodeBadges";
import styles from "../discount-programs/DiscountProgramPage.module.css";
import type { DiscountCode } from "../../types/discount-code.types";
import { formatDiscountCodeDate } from "../../utils/discount-codes";

interface DiscountCodeTableProps {
    codes: DiscountCode[];
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
}

export function DiscountCodeTable({
    codes,
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange
}: DiscountCodeTableProps) {
    return (
        <section className={styles.tableCard}>
            <div className={styles.tableScroll}>
                <table className={styles.table}>
                    <thead>
                        <tr>
                            <th>Mã coupon</th>
                            <th>Chương trình giảm giá</th>
                            <th>Phạm vi</th>
                            <th>Thời hạn</th>
                            <th>Lượt dùng</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        {codes.map((code) => (
                            <tr key={code.id}>
                                <td>
                                    <span className={styles.codeText}>
                                        {code.code}
                                    </span>
                                    <span className={styles.codeMeta}>
                                        {code.issueType}
                                    </span>
                                </td>
                                <td>
                                    <span
                                        className={styles.programName}
                                    >
                                        {code.programName}
                                    </span>
                                </td>
                                <td>
                                    <DiscountCodeScopeBadge
                                        scope={code.scope}
                                    />
                                </td>
                                <td>
                                    {formatDiscountCodeDate(
                                        code.startAt
                                    )}{" "}
                                    -{" "}
                                    {formatDiscountCodeDate(code.endAt)}
                                </td>
                                <td>
                                    <strong>
                                        {code.usedCount} /{" "}
                                        {code.usageLimit}
                                    </strong>
                                </td>
                                <td>
                                    <DiscountCodeStatusBadge
                                        status={code.status}
                                    />
                                </td>
                                <td className={styles.actionCell}>
                                    <button
                                        className={styles.iconButton}
                                        type="button"
                                        aria-label={`Mở thao tác cho mã ${code.code}`}
                                    >
                                        <MoreVertical
                                            size={19}
                                            aria-hidden="true"
                                        />
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {codes.length === 0 ? (
                <div className={styles.empty}>
                    Không tìm thấy mã giảm giá phù hợp.
                </div>
            ) : null}

            <MarketingPagination
                page={page}
                pageCount={pageCount}
                totalItems={totalItems}
                pageSize={pageSize}
                onPageChange={onPageChange}
                onPageSizeChange={onPageSizeChange}
            />
        </section>
    );
}
