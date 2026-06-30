import { MoreVertical } from "lucide-react";
import { Link } from "react-router-dom";

import { MarketingPagination } from "../MarketingPagination";
import {
    ApplicationTypeBadge,
    DiscountStatusBadge
} from "./DiscountProgramBadges";
import styles from "./DiscountProgramPage.module.css";
import type { DiscountProgram } from "../../types/discount-program.types";
import { formatDiscountDateTime } from "../../utils/discount-programs";
import { routePaths } from "../../../../routes/path";

interface DiscountProgramTableProps {
    programs: DiscountProgram[];
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
}

export function DiscountProgramTable({
    programs,
    page,
    pageCount,
    totalItems,
    pageSize,
    onPageChange,
    onPageSizeChange
}: DiscountProgramTableProps) {
    return (
        <section className={styles.tableCard}>
            <div className={styles.tableScroll}>
                <table className={styles.table}>
                    <thead>
                        <tr>
                            <th>Tên chương trình</th>
                            <th>Sự kiện</th>
                            <th>Cách áp dụng</th>
                            <th>Thời gian</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        {programs.map((program) => (
                            <tr key={program.id}>
                                <td>
                                    <Link
                                        to={routePaths.discountProgramDetail.replace(
                                            ":programId",
                                            program.id
                                        )}
                                        className={styles.programName}
                                    >
                                        {program.name}
                                    </Link>
                                </td>
                                <td>
                                    <span className={styles.eventLink}>
                                        {program.eventName}
                                    </span>
                                </td>
                                <td>
                                    <ApplicationTypeBadge
                                        type={program.applicationType}
                                    />
                                </td>
                                <td>
                                    <span className={styles.timeRange}>
                                        <span>
                                            {formatDiscountDateTime(
                                                program.startAt
                                            )}{" "}
                                            -
                                        </span>
                                        <span>
                                            {formatDiscountDateTime(
                                                program.endAt
                                            )}
                                        </span>
                                    </span>
                                </td>
                                <td>
                                    <DiscountStatusBadge
                                        status={program.status}
                                    />
                                </td>
                                <td className={styles.actionCell}>
                                    <button
                                        className={styles.iconButton}
                                        type="button"
                                        aria-label={`Mở thao tác cho ${program.name}`}
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

            {programs.length === 0 ? (
                <div className={styles.empty}>
                    Không tìm thấy chương trình giảm giá phù hợp.
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
