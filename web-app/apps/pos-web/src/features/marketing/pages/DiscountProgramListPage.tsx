import { Plus, RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";

import { DiscountProgramFilters } from "../components/discount-programs/DiscountProgramFilters";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { DiscountProgramTable } from "../components/discount-programs/DiscountProgramTable";
import { discountPrograms } from "../mock/discount-programs.mock";
import {
    defaultDiscountProgramFilters,
    filterDiscountPrograms,
    getUniqueEvents
} from "../utils/discount-programs";

export function DiscountProgramListPage() {
    const [query, setQuery] = useState("");
    const [filters, setFilters] = useState(
        defaultDiscountProgramFilters
    );
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const eventOptions = useMemo(
        () => getUniqueEvents(discountPrograms),
        []
    );

    const filteredPrograms = useMemo(
        () => filterDiscountPrograms(discountPrograms, query, filters),
        [query, filters]
    );

    const pageCount = Math.max(
        1,
        Math.ceil(filteredPrograms.length / pageSize)
    );
    const safePage = Math.min(page, pageCount);
    const pagedPrograms = filteredPrograms.slice(
        (safePage - 1) * pageSize,
        safePage * pageSize
    );

    function updateQuery(value: string) {
        setQuery(value);
        setPage(1);
    }

    function updateFilters(
        nextFilters: typeof defaultDiscountProgramFilters
    ) {
        setFilters(nextFilters);
        setPage(1);
    }

    function updatePageSize(nextPageSize: number) {
        setPageSize(nextPageSize);
        setPage(1);
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
                        <span className="font-bold text-[#17213a]">
                            Chương trình giảm giá
                        </span>
                    </nav>
                    <h1 className={styles.title}>
                        Chương trình giảm giá
                    </h1>
                    <p className={styles.description}>
                        Quản lý tất cả chương trình khuyến mãi trong
                        hệ thống.
                    </p>
                </div>

                <div className={styles.actions}>
                    <button className={styles.primaryButton} type="button">
                        <Plus size={18} aria-hidden="true" />
                        Tạo chương trình giảm giá
                    </button>
                    <button
                        className={styles.secondaryButton}
                        type="button"
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Tải lại
                    </button>
                </div>
            </header>

            <DiscountProgramFilters
                query={query}
                filters={filters}
                eventOptions={eventOptions}
                onQueryChange={updateQuery}
                onFiltersChange={updateFilters}
            />

            <DiscountProgramTable
                programs={pagedPrograms}
                page={safePage}
                pageCount={pageCount}
                totalItems={filteredPrograms.length}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={updatePageSize}
            />
        </section>
    );
}
