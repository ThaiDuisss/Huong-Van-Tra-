import {
    CalendarDays,
    ChevronLeft,
    MoreVertical,
    Pencil
} from "lucide-react";
import { Link, Navigate, useParams } from "react-router-dom";

import {
    DiscountBenefitCard,
    DiscountConditionsCard,
    DiscountProgramInfoCard,
    DiscountScopesCard
} from "../components/discount-programs/DiscountProgramDetailSections";
import { DiscountStatusBadge } from "../components/discount-programs/DiscountProgramBadges";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { discountPrograms } from "../mock/discount-programs.mock";
import { routePaths } from "../../../routes/path";
import { formatDiscountDateTimeCompact } from "../utils/discount-programs";

export function DiscountProgramDetailPage() {
    const { programId } = useParams();
    const program = discountPrograms.find(
        (item) => item.id === programId
    );

    if (!program) {
        return <Navigate to={routePaths.discountPrograms} replace />;
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.titleBlock}>
                    <nav
                        className="flex items-center gap-2 text-sm"
                        aria-label="Breadcrumb"
                    >
                        <span className="font-bold text-[#009b4d]">
                            Marketing
                        </span>
                        <span aria-hidden="true">›</span>
                        <Link
                            to={routePaths.discountPrograms}
                            className="font-bold text-[#009b4d]"
                        >
                            Chương trình giảm giá
                        </Link>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Chi tiết chương trình
                        </span>
                    </nav>
                    <h1 className={styles.detailTitle}>
                        {program.name}
                    </h1>
                    <div className={styles.metaLine}>
                        <DiscountStatusBadge status={program.status} />
                        <span aria-hidden="true">·</span>
                        <span className={styles.metaItem}>
                            <CalendarDays
                                size={15}
                                aria-hidden="true"
                            />
                            {program.eventName}
                        </span>
                        <span aria-hidden="true">·</span>
                        <span className={styles.metaItem}>
                            <CalendarDays
                                size={15}
                                aria-hidden="true"
                            />
                            {formatDiscountDateTimeCompact(
                                program.startAt
                            )}{" "}
                            -{" "}
                            {formatDiscountDateTimeCompact(program.endAt)}
                        </span>
                    </div>
                </div>

                <div className={styles.actions}>
                    <button
                        className={`${styles.primaryButton} ${styles.compactButton}`}
                        type="button"
                    >
                        <Pencil size={17} aria-hidden="true" />
                        Sửa chương trình
                    </button>
                    <Link
                        to={routePaths.discountPrograms}
                        className={`${styles.secondaryButton} ${styles.compactButton}`}
                    >
                        <ChevronLeft size={17} aria-hidden="true" />
                        Quay lại danh sách
                    </Link>
                    <button
                        className={`${styles.secondaryButton} ${styles.compactButton}`}
                        type="button"
                        aria-label="Mở thao tác khác"
                    >
                        <MoreVertical size={18} aria-hidden="true" />
                    </button>
                </div>
            </header>

            <DiscountProgramInfoCard program={program} />
            <DiscountBenefitCard program={program} />
            <DiscountConditionsCard conditions={program.conditions} />
            <DiscountScopesCard scopes={program.scopes} />
        </section>
    );
}
